package com.tanik.biharmapmeasure.plotmeasure.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanik.biharmapmeasure.plotmeasure.core.UndoManager
import com.tanik.biharmapmeasure.plotmeasure.core.calibration.CalibrationEngine
import com.tanik.biharmapmeasure.plotmeasure.core.export.ExportFormat
import com.tanik.biharmapmeasure.plotmeasure.core.export.ReportExporter
import com.tanik.biharmapmeasure.plotmeasure.core.geometry.GeometryEngine
import com.tanik.biharmapmeasure.plotmeasure.core.pdf.PageRect
import com.tanik.biharmapmeasure.plotmeasure.core.pdf.PdfRenderEngine
import com.tanik.biharmapmeasure.plotmeasure.core.pdf.RenderedPdfTile
import com.tanik.biharmapmeasure.plotmeasure.data.JsonProjectRepository
import com.tanik.biharmapmeasure.plotmeasure.data.CrashReportStore
import com.tanik.biharmapmeasure.plotmeasure.data.SettingsRepository
import com.tanik.biharmapmeasure.plotmeasure.model.CalibrationProfile
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementMode
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPoint
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPolygon
import com.tanik.biharmapmeasure.plotmeasure.model.PdfPageState
import com.tanik.biharmapmeasure.plotmeasure.model.PdfProject
import com.tanik.biharmapmeasure.plotmeasure.model.PlotMeasureSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

class PlotMeasureViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    private val pdfRenderEngine = PdfRenderEngine(application)
    private val projectRepository = JsonProjectRepository(application, json)
    private val settingsRepository = SettingsRepository(application, json)
    private val crashReportStore = CrashReportStore(application)
    private val reportExporter = ReportExporter(application.contentResolver, json)
    private val undoManager = UndoManager<PdfProject>()
    private val calibrationRedoStack = mutableListOf<MeasurementPoint>()

    private val _uiState = MutableStateFlow(PlotMeasureUiState(isLoading = true))
    val uiState: StateFlow<PlotMeasureUiState> = _uiState.asStateFlow()

    private var detailRenderJob: Job? = null
    private var dragSnapshotCommitted = false

    init {
        viewModelScope.launch {
            runCatching {
                val settings = settingsRepository.loadSettings()
                val projects = projectRepository.loadProjects().map(::sanitizeProject)
                val crashReport = crashReportStore.consumeCrashReport()
                val selectedProject =
                    projects.firstOrNull { it.id == settings.lastProjectId } ?: projects.firstOrNull()
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        settings = settings,
                        projectSummaries = projects.map(::toSummary),
                        crashReport = crashReport,
                    )
                if (selectedProject != null) {
                    loadProject(selectedProject)
                }
            }.onFailure { error ->
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        message = error.message ?: "Recovered from invalid saved app data.",
                    )
            }
        }
    }

    fun importPdf(
        uri: Uri,
        displayName: String,
    ) {
        viewModelScope.launch {
            runCatching {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val descriptor =
                    withContext(Dispatchers.IO) {
                        pdfRenderEngine.openDocument(uri)
                    }
                val existing =
                    projectRepository.loadProjects()
                        .map(::sanitizeProject)
                        .firstOrNull { it.pdfUri == uri.toString() }
                val now = System.currentTimeMillis()
                val project = sanitizeProject(
                    existing?.copy(
                        pdfDisplayName = displayName,
                        pageCount = descriptor.pageCount,
                        updatedAt = now,
                    ) ?: createProject(
                        uri = uri,
                        displayName = displayName,
                        pageCount = descriptor.pageCount,
                        createdAt = now,
                    ),
                    enforcedPageCount = descriptor.pageCount,
                )
                projectRepository.saveProject(project)
                saveSettings(_uiState.value.settings.copy(lastProjectId = project.id))
                undoManager.reset()
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        project = project,
                        pageCount = descriptor.pageCount,
                        pagePickerOpen = descriptor.pageCount > 1,
                        currentMode = project.preferredMode,
                        currentPageState = project.currentPageOrNull(),
                        activePlot = project.currentPageOrNull()?.selectedPlot(),
                        plotComputation = project.currentPageOrNull()?.selectedPlot()?.let {
                            GeometryEngine.computePlot(it, project.currentPageOrNull()?.calibration, _uiState.value.settings)
                        },
                        message = if (descriptor.pageCount > 1) "Choose the PDF page to work on." else null,
                        projectSummaries = projectRepository.loadProjects().map(::toSummary),
                    )
                if (descriptor.pageCount == 1) {
                    renderCurrentPage(project.selectedPageIndex)
                }
            }.onFailure { error ->
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        message = error.message ?: "Unable to import PDF.",
                    )
            }
        }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.loadProject(projectId)?.let(::sanitizeProject)?.let(::loadProject)
        }
    }

    fun openPagePicker() {
        _uiState.value = _uiState.value.copy(pagePickerOpen = true)
    }

    fun closePagePicker() {
        _uiState.value = _uiState.value.copy(pagePickerOpen = false)
    }

    fun selectPage(pageIndex: Int) {
        val project = _uiState.value.project ?: return
        val updatedProject =
            project.copy(
                selectedPageIndex = pageIndex.coerceIn(0, project.pageCount - 1),
                updatedAt = System.currentTimeMillis(),
            )
        persistProject(updatedProject, keepUndo = false)
        _uiState.value = _uiState.value.copy(pagePickerOpen = false)
        renderCurrentPage(updatedProject.selectedPageIndex)
    }

    fun setMode(mode: MeasurementMode) {
        val project = _uiState.value.project ?: return
        val updatedProject =
            project.copy(
                preferredMode = mode,
                updatedAt = System.currentTimeMillis(),
            )
        persistProject(updatedProject, keepUndo = false)
        updateStateFromProject(updatedProject, baseTile = _uiState.value.baseTile, detailTile = _uiState.value.detailTile)
    }

    fun setPanelTab(tab: PanelTab) {
        _uiState.value = _uiState.value.copy(panelTab = tab)
    }

    fun toggleLargeView() {
        _uiState.value = _uiState.value.copy(isLargeViewEnabled = !_uiState.value.isLargeViewEnabled)
    }

    fun toggleEditMode() {
        _uiState.value = _uiState.value.copy(isEditMode = !_uiState.value.isEditMode)
    }

    fun selectPlot(plotId: String) {
        val project = _uiState.value.project ?: return
        val pageState = project.currentPageOrNull() ?: return
        if (pageState.selectedPlotId == plotId) {
            return
        }
        mutateCurrentPage(project) { page ->
            page.copy(selectedPlotId = plotId)
        }
    }

    fun createNewPlot() {
        val project = _uiState.value.project ?: return
        val pageState = project.currentPageOrNull() ?: return
        val nextIndex = pageState.plots.size + 1
        val mode = _uiState.value.currentMode
        val now = System.currentTimeMillis()
        val newPlot =
            MeasurementPolygon(
                id = UUID.randomUUID().toString(),
                name = "${mode.name.lowercase().replaceFirstChar(Char::uppercase)} $nextIndex",
                mode = mode,
                createdAt = now,
                updatedAt = now,
                strokeColorArgb = colorForIndex(nextIndex),
                fillColorArgb = colorForIndex(nextIndex, alpha = 0x55),
            )
        mutateCurrentPage(project) { page ->
            page.copy(
                plots = page.plots + newPlot,
                selectedPlotId = newPlot.id,
            )
        }
    }

    fun deleteCurrentPlot() {
        val project = _uiState.value.project ?: return
        val selectedPlotId = _uiState.value.currentPageState?.selectedPlotId ?: return
        mutateCurrentPage(project) { page ->
            val remainingPlots = page.plots.filterNot { it.id == selectedPlotId }
            page.copy(
                plots = remainingPlots,
                selectedPlotId = remainingPlots.lastOrNull()?.id,
            )
        }
    }

    fun selectPoint(pointId: String?) {
        _uiState.value = _uiState.value.copy(selectedPointId = pointId)
    }

    fun addPoint(point: MeasurementPoint) {
        val project = _uiState.value.project ?: return
        val preparedProject = ensureActivePlot(project)
        val activePlot = preparedProject.currentPageOrNull()?.selectedPlot() ?: return
        val updatedPoints =
            when {
                activePlot.mode == MeasurementMode.DISTANCE && activePlot.points.size >= 2 -> listOf(point)
                activePlot.mode == MeasurementMode.AREA && activePlot.isClosed -> listOf(point)
                else -> activePlot.points + point.copy(id = UUID.randomUUID().toString())
            }
        replaceActivePlot(preparedProject) { plot ->
            plot.copy(
                points = updatedPoints,
                isClosed = false,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun closePolygon() {
        val project = _uiState.value.project ?: return
        val activePlot = _uiState.value.activePlot ?: return
        if (activePlot.mode != MeasurementMode.AREA || activePlot.points.size < 3) {
            return
        }
        replaceActivePlot(project) { plot ->
            plot.copy(isClosed = true, updatedAt = System.currentTimeMillis())
        }
    }

    fun beginPointDrag(pointId: String) {
        dragSnapshotCommitted = false
        selectPoint(pointId)
    }

    fun updatePointPosition(
        pointId: String,
        point: MeasurementPoint,
    ) {
        val project = _uiState.value.project ?: return
        if (!dragSnapshotCommitted) {
            undoManager.record(project)
            dragSnapshotCommitted = true
        }
        replaceActivePlot(
            project,
            recordUndo = false,
        ) { plot ->
            plot.copy(
                points =
                    plot.points.map { existing ->
                        if (existing.id == pointId) {
                            existing.copy(x = point.x, y = point.y)
                        } else {
                            existing
                        }
                    },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun endPointDrag() {
        dragSnapshotCommitted = false
        persistCurrentState()
    }

    fun insertPointAt(
        insertIndex: Int,
        point: MeasurementPoint,
    ) {
        val project = _uiState.value.project ?: return
        replaceActivePlot(project) { plot ->
            val nextPoints = plot.points.toMutableList()
            nextPoints.add(insertIndex.coerceIn(0, nextPoints.size), point.copy(id = UUID.randomUUID().toString()))
            plot.copy(points = nextPoints, updatedAt = System.currentTimeMillis())
        }
    }

    fun deleteSelectedPoint() {
        val project = _uiState.value.project ?: return
        val selectedPointId = _uiState.value.selectedPointId ?: return
        replaceActivePlot(project) { plot ->
            val updatedPoints = plot.points.filterNot { it.id == selectedPointId }
            plot.copy(
                points = updatedPoints,
                isClosed = if (updatedPoints.size >= 3) plot.isClosed else false,
                updatedAt = System.currentTimeMillis(),
            )
        }
        selectPoint(null)
    }

    fun nudgeSelectedPoint(
        deltaX: Double,
        deltaY: Double,
    ) {
        if (deltaX == 0.0 && deltaY == 0.0) {
            return
        }
        val project = _uiState.value.project ?: return
        val selectedPointId = _uiState.value.selectedPointId ?: return
        val pageWidth = _uiState.value.baseTile?.pageWidth?.toDouble()
        val pageHeight = _uiState.value.baseTile?.pageHeight?.toDouble()
        replaceActivePlot(project) { plot ->
            plot.copy(
                points =
                    plot.points.map { existing ->
                        if (existing.id == selectedPointId) {
                            existing.copy(
                                x = (existing.x + deltaX).let { nextX -> pageWidth?.let { nextX.coerceIn(0.0, it) } ?: nextX },
                                y = (existing.y + deltaY).let { nextY -> pageHeight?.let { nextY.coerceIn(0.0, it) } ?: nextY },
                            )
                        } else {
                            existing
                        }
                    },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun nudgeLastPoint(
        deltaX: Double,
        deltaY: Double,
    ) {
        if (deltaX == 0.0 && deltaY == 0.0) {
            return
        }
        val project = _uiState.value.project ?: return
        val activePlot = _uiState.value.activePlot ?: return
        val lastPointId = activePlot.points.lastOrNull()?.id ?: return
        val pageWidth = _uiState.value.baseTile?.pageWidth?.toDouble()
        val pageHeight = _uiState.value.baseTile?.pageHeight?.toDouble()
        replaceActivePlot(project) { plot ->
            val targetPointId = plot.points.lastOrNull()?.id ?: return@replaceActivePlot plot
            plot.copy(
                points =
                    plot.points.map { existing ->
                        if (existing.id == targetPointId) {
                            existing.copy(
                                x = (existing.x + deltaX).let { nextX -> pageWidth?.let { nextX.coerceIn(0.0, it) } ?: nextX },
                                y = (existing.y + deltaY).let { nextY -> pageHeight?.let { nextY.coerceIn(0.0, it) } ?: nextY },
                            )
                        } else {
                            existing
                        }
                    },
                updatedAt = System.currentTimeMillis(),
            )
        }
        _uiState.value = _uiState.value.copy(selectedPointId = lastPointId)
    }

    fun undo() {
        if (undoCalibrationCapture()) {
            return
        }
        val current = _uiState.value.project ?: return
        val restored = undoManager.undo(current) ?: return
        updateStateAfterHistoryRestore(restored)
    }

    fun redo() {
        if (redoCalibrationCapture()) {
            return
        }
        val current = _uiState.value.project ?: return
        val restored = undoManager.redo(current) ?: return
        updateStateAfterHistoryRestore(restored)
    }

    fun startCalibrationCapture() {
        clearCalibrationRedo()
        _uiState.value =
            _uiState.value.copy(
                calibrationDialogOpen = false,
                isCapturingCalibration = true,
                calibrationCapturePoints = emptyList(),
                calibrationRedoAvailable = false,
                selectedCalibrationPointIndex = 0,
                isLargeViewEnabled = true,
                message = "Tap two points on the known scale reference.",
            )
    }

    fun resumeCalibrationCapture() {
        val currentPoints = _uiState.value.calibrationCapturePoints
        _uiState.value =
            _uiState.value.copy(
                calibrationDialogOpen = false,
                isCapturingCalibration = true,
                isLargeViewEnabled = true,
                message =
                    if (currentPoints.isEmpty()) {
                        "Tap two points on the known scale reference."
                    } else {
                        "Tap the second point on the same scale reference."
                    },
            )
    }

    fun openCalibrationDialog() {
        _uiState.value = _uiState.value.copy(calibrationDialogOpen = true)
    }

    fun closeCalibrationDialog() {
        _uiState.value =
            _uiState.value.copy(
                calibrationDialogOpen = false,
                isCapturingCalibration = false,
            )
    }

    fun selectCalibrationPoint(index: Int) {
        val points = _uiState.value.calibrationCapturePoints
        if (points.isEmpty()) {
            return
        }
        _uiState.value =
            _uiState.value.copy(
                selectedCalibrationPointIndex = index.coerceIn(0, points.lastIndex),
            )
    }

    fun captureCalibrationPoint(point: MeasurementPoint) {
        clearCalibrationRedo()
        val currentPoints = _uiState.value.calibrationCapturePoints
        val nextPoints =
            if (currentPoints.size >= 2) {
                listOf(point.copy(id = UUID.randomUUID().toString()))
            } else {
                currentPoints + point.copy(id = UUID.randomUUID().toString())
            }
        val hasTwoPoints = nextPoints.size == 2
        _uiState.value =
            _uiState.value.copy(
                calibrationCapturePoints = nextPoints,
                isCapturingCalibration = !hasTwoPoints,
                calibrationDialogOpen = false,
                calibrationRedoAvailable = false,
                selectedCalibrationPointIndex = nextPoints.lastIndex.coerceAtLeast(0),
                message =
                    if (hasTwoPoints) {
                        "Calibration points captured. Use Enter Distance to finish."
                    } else {
                        "Tap the second point on the same scale reference."
                    },
            )
    }

    fun nudgeCalibrationPoint(
        pointIndex: Int,
        deltaX: Double,
        deltaY: Double,
    ) {
        if (deltaX == 0.0 && deltaY == 0.0) {
            return
        }
        val currentPoints = _uiState.value.calibrationCapturePoints
        if (pointIndex !in currentPoints.indices) {
            return
        }
        val pageWidth = _uiState.value.baseTile?.pageWidth?.toDouble()
        val pageHeight = _uiState.value.baseTile?.pageHeight?.toDouble()
        val keepDialogOpen = _uiState.value.calibrationDialogOpen
        val updatedPoints =
            currentPoints.mapIndexed { index, point ->
                if (index == pointIndex) {
                    point.copy(
                        x = (point.x + deltaX).let { nextX -> pageWidth?.let { nextX.coerceIn(0.0, it) } ?: nextX },
                        y = (point.y + deltaY).let { nextY -> pageHeight?.let { nextY.coerceIn(0.0, it) } ?: nextY },
                    )
                } else {
                    point
                }
            }
        _uiState.value =
            _uiState.value.copy(
                calibrationCapturePoints = updatedPoints,
                calibrationDialogOpen = keepDialogOpen,
                isCapturingCalibration = updatedPoints.size < 2,
                selectedCalibrationPointIndex = pointIndex.coerceIn(0, updatedPoints.lastIndex),
            )
    }

    fun applyManualCalibration(
        calibrationName: String,
        realDistance: Double,
        realUnit: com.tanik.biharmapmeasure.plotmeasure.model.LinearUnit,
        referenceType: com.tanik.biharmapmeasure.plotmeasure.model.ManualReferenceType,
        saveAsPreset: Boolean,
    ) {
        val capturePoints = _uiState.value.calibrationCapturePoints
        if (capturePoints.size != 2) {
            _uiState.value = _uiState.value.copy(message = "Select two calibration points first.")
            return
        }
        val pageDistance = GeometryEngine.distance(capturePoints[0], capturePoints[1])
        val calibration =
            runCatching {
                CalibrationEngine.manualCalibration(
                    name = calibrationName,
                    pageDistance = pageDistance,
                    realDistance = realDistance,
                    realUnit = realUnit,
                    referenceType = referenceType,
                    createdAt = System.currentTimeMillis(),
                )
            }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(message = error.message)
                return
            }
        applyCalibration(calibration, saveAsPreset)
    }

    fun applyRatioCalibration(
        calibrationName: String,
        denominator: Double,
        saveAsPreset: Boolean,
    ) {
        val calibration =
            runCatching {
                CalibrationEngine.ratioCalibration(
                    name = calibrationName,
                    denominator = denominator,
                    createdAt = System.currentTimeMillis(),
                )
            }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(message = error.message)
                return
            }
        applyCalibration(calibration, saveAsPreset)
    }

    fun applyTextScaleCalibration(
        calibrationName: String,
        mapDistanceValue: Double,
        mapDistanceUnit: com.tanik.biharmapmeasure.plotmeasure.model.LinearUnit,
        groundDistanceValue: Double,
        groundDistanceUnit: com.tanik.biharmapmeasure.plotmeasure.model.LinearUnit,
        saveAsPreset: Boolean,
    ) {
        val calibration =
            runCatching {
                CalibrationEngine.textScaleCalibration(
                    name = calibrationName,
                    mapDistanceValue = mapDistanceValue,
                    mapDistanceUnit = mapDistanceUnit,
                    groundDistanceValue = groundDistanceValue,
                    groundDistanceUnit = groundDistanceUnit,
                    createdAt = System.currentTimeMillis(),
                )
            }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(message = error.message)
                return
            }
        applyCalibration(calibration, saveAsPreset)
    }

    fun applyCalibrationPreset(presetId: String) {
        val project = _uiState.value.project ?: return
        val preset = project.calibrationPresets.firstOrNull { it.id == presetId } ?: return
        applyCalibration(
            preset.copy(
                id = UUID.randomUUID().toString(),
                method = com.tanik.biharmapmeasure.plotmeasure.model.CalibrationMethod.PRESET,
                createdAt = System.currentTimeMillis(),
            ),
            saveAsPreset = false,
        )
    }

    fun updateLayerVisibility(
        transform: (com.tanik.biharmapmeasure.plotmeasure.model.LayerVisibility) -> com.tanik.biharmapmeasure.plotmeasure.model.LayerVisibility,
    ) {
        val updated = _uiState.value.settings.copy(layerVisibility = transform(_uiState.value.settings.layerVisibility))
        saveSettings(updated)
        _uiState.value = _uiState.value.copy(settings = updated)
        refreshDerivedState()
    }

    fun updateAreaUnitSettings(
        squareFeetPerBigha: Double,
        squareFeetPerKattha: Double,
        squareFeetPerDhur: Double,
    ) {
        val updated =
            _uiState.value.settings.copy(
                squareFeetPerBigha = squareFeetPerBigha,
                squareFeetPerKattha = squareFeetPerKattha,
                squareFeetPerDhur = squareFeetPerDhur,
            )
        saveSettings(updated)
        _uiState.value = _uiState.value.copy(settings = updated)
        refreshDerivedState()
    }

    fun updateViewerSettings(
        snapToEdgeEnabled: Boolean,
        showLoupe: Boolean,
    ) {
        val updated =
            _uiState.value.settings.copy(
                snapToEdgeEnabled = snapToEdgeEnabled,
                showLoupe = showLoupe,
            )
        saveSettings(updated)
        _uiState.value = _uiState.value.copy(settings = updated)
    }

    fun onViewportChanged(viewport: ViewerViewport) {
        _uiState.value =
            _uiState.value.copy(
                viewerZoomFactor = viewport.zoomFactor,
                screenPixelsPerPageUnit = viewport.screenPixelsPerPageUnit,
            )
        val project = _uiState.value.project ?: return
        val baseTile = _uiState.value.baseTile ?: return
        if (!HIGH_DETAIL_RENDERING_ENABLED) {
            detailRenderJob?.cancel()
            if (_uiState.value.detailTile != null) {
                replaceTiles(baseTile = baseTile, detailTile = null)
            }
            return
        }
        val requiredDensity = viewport.screenPixelsPerPageUnit * DETAIL_OVERSAMPLE
        if (requiredDensity <= viewport.baseDensity * DETAIL_TRIGGER_RATIO) {
            replaceTiles(baseTile = baseTile, detailTile = null)
            return
        }
        val existingTile = _uiState.value.detailTile
        if (existingTile != null &&
            existingTile.contains(viewport.visiblePageRect) &&
            existingTile.minDensity >= requiredDensity * DETAIL_EXISTING_TILE_RATIO
        ) {
            return
        }

        val region = expandRegion(viewport.visiblePageRect, baseTile.pageWidth.toDouble(), baseTile.pageHeight.toDouble())
        val unclampedDensity = requiredDensity
        val rawWidth = (region.width * unclampedDensity).toInt().coerceAtLeast(MIN_DETAIL_EDGE)
        val rawHeight = (region.height * unclampedDensity).toInt().coerceAtLeast(MIN_DETAIL_EDGE)
        val scaleDown =
            if (maxOf(rawWidth, rawHeight) > MAX_DETAIL_EDGE) {
                MAX_DETAIL_EDGE.toDouble() / maxOf(rawWidth, rawHeight).toDouble()
            } else {
                1.0
            }
        val targetWidth = (rawWidth * scaleDown).toInt().coerceAtLeast(MIN_DETAIL_EDGE)
        val targetHeight = (rawHeight * scaleDown).toInt().coerceAtLeast(MIN_DETAIL_EDGE)

        detailRenderJob?.cancel()
        detailRenderJob =
            viewModelScope.launch {
                delay(DETAIL_DEBOUNCE_MS)
                val currentProject = _uiState.value.project ?: return@launch
                if (currentProject.id != project.id) return@launch
                runCatching {
                    withContext(Dispatchers.IO) {
                        pdfRenderEngine.renderRegion(
                            pageIndex = currentProject.selectedPageIndex,
                            region = region,
                            targetWidth = targetWidth,
                            targetHeight = targetHeight,
                        )
                    }
                }.onSuccess { tile ->
                    replaceTiles(baseTile = _uiState.value.baseTile, detailTile = tile)
                }.onFailure { error ->
                    if (error is CancellationException) {
                        return@onFailure
                    }
                    replaceTiles(baseTile = _uiState.value.baseTile, detailTile = null)
                }
            }
    }

    fun prepareExport(format: ExportFormat) {
        val project = _uiState.value.project ?: return
        val fileBaseName = project.name.replace(Regex("[^A-Za-z0-9_-]+"), "_")
        _uiState.value =
            _uiState.value.copy(
                pendingExport = PendingExportRequest(format, "$fileBaseName-page-${project.selectedPageIndex + 1}.${format.extension}"),
            )
    }

    fun clearPendingExport() {
        _uiState.value = _uiState.value.copy(pendingExport = null)
    }

    fun exportToUri(
        uri: Uri,
        format: ExportFormat,
    ) {
        val project = _uiState.value.project ?: return
        val pageState = _uiState.value.currentPageState ?: return
        viewModelScope.launch {
            runCatching {
                val report = reportExporter.buildReport(project, pageState, _uiState.value.settings)
                withContext(Dispatchers.IO) {
                    reportExporter.exportToUri(uri, format, report)
                }
            }.onSuccess {
                _uiState.value =
                    _uiState.value.copy(
                        pendingExport = null,
                        message = "Export complete.",
                    )
            }.onFailure { error ->
                _uiState.value =
                    _uiState.value.copy(
                        pendingExport = null,
                        message = error.message ?: "Export failed.",
                    )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun dismissCrashReport() {
        _uiState.value = _uiState.value.copy(crashReport = null)
    }

    override fun onCleared() {
        detailRenderJob?.cancel()
        recycleTile(_uiState.value.baseTile)
        recycleTile(_uiState.value.detailTile)
        pdfRenderEngine.close()
        super.onCleared()
    }

    private fun loadProject(project: PdfProject) {
        viewModelScope.launch {
            runCatching {
                _uiState.value = _uiState.value.copy(isLoading = true)
                withContext(Dispatchers.IO) {
                    pdfRenderEngine.openDocument(Uri.parse(project.pdfUri))
                }
                updateStateFromProject(project, baseTile = null, detailTile = null)
                renderCurrentPage(project.selectedPageIndex)
            }.onFailure { error ->
                updateStateFromProject(project, baseTile = null, detailTile = null)
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        message = error.message ?: "Unable to reopen saved project.",
                    )
            }
        }
    }

    private fun renderCurrentPage(pageIndex: Int) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            runCatching {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val tile =
                    withContext(Dispatchers.IO) {
                        pdfRenderEngine.openDocument(Uri.parse(project.pdfUri))
                        pdfRenderEngine.renderPage(pageIndex)
                    }
                replaceTiles(baseTile = tile, detailTile = null)
                updateStateFromProject(project, baseTile = tile, detailTile = null)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }.onFailure { error ->
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        message = error.message ?: "Unable to render selected page.",
                    )
            }
        }
    }

    private fun applyCalibration(
        calibration: CalibrationProfile,
        saveAsPreset: Boolean,
    ) {
        val project = _uiState.value.project ?: return
        val updatedProject =
            mutateProject(
                project,
                keepUndo = true,
            ) { current ->
                val updatedPages =
                    current.pages.mapIndexed { index, page ->
                        if (index == current.selectedPageIndex) {
                            page.copy(calibration = calibration)
                        } else {
                            page
                        }
                    }
                current.copy(
                    pages = updatedPages,
                    calibrationPresets =
                        if (saveAsPreset) {
                            current.calibrationPresets + calibration
                        } else {
                            current.calibrationPresets
                        },
                    updatedAt = System.currentTimeMillis(),
                )
            }
        _uiState.value =
            _uiState.value.copy(
                calibrationDialogOpen = false,
                isCapturingCalibration = false,
                calibrationCapturePoints = emptyList(),
                calibrationRedoAvailable = false,
                selectedCalibrationPointIndex = 0,
                message = "Calibration saved for this page.",
            )
        updateStateFromProject(updatedProject, baseTile = _uiState.value.baseTile, detailTile = _uiState.value.detailTile)
    }

    private fun replaceActivePlot(
        project: PdfProject,
        recordUndo: Boolean = true,
        transform: (MeasurementPolygon) -> MeasurementPolygon,
    ) {
        val updatedProject =
            mutateProject(project, keepUndo = recordUndo) { current ->
                val currentPage = current.currentPageOrNull() ?: return@mutateProject current
                val selectedPlot = currentPage.selectedPlot() ?: return@mutateProject current
                val updatedPlots =
                    currentPage.plots.map { plot ->
                        if (plot.id == selectedPlot.id) {
                            transform(plot)
                        } else {
                            plot
                        }
                    }
                current.withUpdatedPage(
                    currentPage.copy(plots = updatedPlots),
                )
            }
        updateStateFromProject(updatedProject, baseTile = _uiState.value.baseTile, detailTile = _uiState.value.detailTile)
    }

    private fun mutateCurrentPage(
        project: PdfProject,
        transform: (PdfPageState) -> PdfPageState,
    ) {
        val updatedProject =
            mutateProject(project, keepUndo = true) { current ->
                val currentPage = current.currentPageOrNull() ?: return@mutateProject current
                current.withUpdatedPage(transform(currentPage))
            }
        updateStateFromProject(updatedProject, baseTile = _uiState.value.baseTile, detailTile = _uiState.value.detailTile)
    }

    private fun mutateProject(
        project: PdfProject,
        keepUndo: Boolean,
        transform: (PdfProject) -> PdfProject,
    ): PdfProject {
        if (keepUndo) {
            undoManager.record(project)
        }
        val updatedProject =
            transform(project).copy(
                updatedAt = System.currentTimeMillis(),
            )
        persistProject(updatedProject, keepUndo = false)
        return updatedProject
    }

    private fun persistProject(
        project: PdfProject,
        keepUndo: Boolean,
    ) {
        val settings = _uiState.value.settings.copy(lastProjectId = project.id)
        if (keepUndo) {
            undoManager.record(_uiState.value.project ?: project)
        }
        saveSettings(settings)
        viewModelScope.launch {
            projectRepository.saveProject(project)
            val projects = projectRepository.loadProjects()
            _uiState.value =
                _uiState.value.copy(
                    settings = settings,
                    projectSummaries = projects.map(::toSummary),
                )
        }
        _uiState.value = _uiState.value.copy(project = project, settings = settings)
    }

    private fun persistCurrentState() {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            projectRepository.saveProject(project)
            _uiState.value =
                _uiState.value.copy(
                    projectSummaries = projectRepository.loadProjects().map(::toSummary),
                )
        }
    }

    private fun updateStateAfterHistoryRestore(project: PdfProject) {
        persistProject(project, keepUndo = false)
        updateStateFromProject(project, baseTile = _uiState.value.baseTile, detailTile = _uiState.value.detailTile)
    }

    private fun updateStateFromProject(
        project: PdfProject,
        baseTile: RenderedPdfTile?,
        detailTile: RenderedPdfTile?,
    ) {
        val pageState = project.currentPageOrNull()
        val activePlot = pageState?.selectedPlot()
        val computation =
            activePlot?.let { plot ->
                GeometryEngine.computePlot(plot, pageState.calibration, _uiState.value.settings)
            }
        _uiState.value =
            _uiState.value.copy(
                isLoading = false,
                project = project,
                currentPageState = pageState,
                activePlot = activePlot,
                plotComputation = computation,
                currentMode = project.preferredMode,
                pageCount = project.pageCount,
                baseTile = baseTile,
                detailTile = detailTile,
                warningMessage = computation?.validationMessage,
            )
    }

    private fun refreshDerivedState() {
        val project = _uiState.value.project ?: return
        updateStateFromProject(project, baseTile = _uiState.value.baseTile, detailTile = _uiState.value.detailTile)
    }

    private fun ensureActivePlot(project: PdfProject): PdfProject {
        val currentPage = project.currentPageOrNull() ?: return project
        val selectedPlot = currentPage.selectedPlot()
        val currentMode = _uiState.value.currentMode
        val shouldCreateNew =
            selectedPlot == null ||
                selectedPlot.mode != currentMode ||
                (selectedPlot.mode == MeasurementMode.DISTANCE && selectedPlot.points.size >= 2) ||
                (selectedPlot.mode == MeasurementMode.AREA && selectedPlot.isClosed)

        if (!shouldCreateNew) {
            return project
        }

        val nextIndex = currentPage.plots.size + 1
        val now = System.currentTimeMillis()
        val newPlot =
            MeasurementPolygon(
                id = UUID.randomUUID().toString(),
                name = "${currentMode.name.lowercase().replaceFirstChar(Char::uppercase)} $nextIndex",
                mode = currentMode,
                createdAt = now,
                updatedAt = now,
                strokeColorArgb = colorForIndex(nextIndex),
                fillColorArgb = colorForIndex(nextIndex, alpha = 0x55),
            )
        val updatedProject =
            mutateProject(project, keepUndo = true) { current ->
                val safePage = current.currentPageOrNull() ?: return@mutateProject current
                current.withUpdatedPage(
                    safePage.copy(
                        plots = safePage.plots + newPlot,
                        selectedPlotId = newPlot.id,
                    ),
                )
            }
        updateStateFromProject(updatedProject, baseTile = _uiState.value.baseTile, detailTile = _uiState.value.detailTile)
        return updatedProject
    }

    private fun expandRegion(
        viewport: PageRect,
        pageWidth: Double,
        pageHeight: Double,
    ): PageRect {
        val paddingX = viewport.width * 0.18
        val paddingY = viewport.height * 0.18
        return PageRect(
            left = (viewport.left - paddingX).coerceIn(0.0, pageWidth),
            top = (viewport.top - paddingY).coerceIn(0.0, pageHeight),
            right = (viewport.right + paddingX).coerceIn(0.0, pageWidth),
            bottom = (viewport.bottom + paddingY).coerceIn(0.0, pageHeight),
        )
    }

    private fun saveSettings(settings: PlotMeasureSettings) {
        viewModelScope.launch {
            settingsRepository.saveSettings(settings)
        }
    }

    private fun replaceTiles(
        baseTile: RenderedPdfTile?,
        detailTile: RenderedPdfTile?,
    ) {
        _uiState.value = _uiState.value.copy(baseTile = baseTile, detailTile = detailTile)
    }

    private fun recycleTile(tile: RenderedPdfTile?) {
        tile?.bitmap?.takeIf { !it.isRecycled }?.recycle()
    }

    private fun undoCalibrationCapture(): Boolean {
        val currentPoints = _uiState.value.calibrationCapturePoints
        if (currentPoints.isEmpty()) {
            return false
        }
        calibrationRedoStack += currentPoints.last()
        val nextPoints = currentPoints.dropLast(1)
        _uiState.value =
            _uiState.value.copy(
                calibrationCapturePoints = nextPoints,
                isCapturingCalibration = true,
                calibrationDialogOpen = false,
                calibrationRedoAvailable = calibrationRedoStack.isNotEmpty(),
                selectedCalibrationPointIndex = nextPoints.lastIndex.coerceAtLeast(0),
                message =
                    if (nextPoints.isEmpty()) {
                        "Tap two points on the known scale reference."
                    } else {
                        "Tap the second point on the same scale reference."
                    },
            )
        return true
    }

    private fun redoCalibrationCapture(): Boolean {
        if (calibrationRedoStack.isEmpty()) {
            return false
        }
        val currentPoints = _uiState.value.calibrationCapturePoints
        if (currentPoints.size >= 2) {
            return false
        }
        val restoredPoint = calibrationRedoStack.removeAt(calibrationRedoStack.lastIndex)
        val nextPoints = currentPoints + restoredPoint
        val hasTwoPoints = nextPoints.size == 2
        _uiState.value =
            _uiState.value.copy(
                calibrationCapturePoints = nextPoints,
                isCapturingCalibration = !hasTwoPoints,
                calibrationDialogOpen = false,
                calibrationRedoAvailable = calibrationRedoStack.isNotEmpty(),
                selectedCalibrationPointIndex = nextPoints.lastIndex.coerceAtLeast(0),
                message =
                    if (hasTwoPoints) {
                        "Calibration points captured. Use Enter Distance to finish."
                    } else {
                        "Tap the second point on the same scale reference."
                    },
            )
        return true
    }

    private fun clearCalibrationRedo() {
        calibrationRedoStack.clear()
    }

    private fun createProject(
        uri: Uri,
        displayName: String,
        pageCount: Int,
        createdAt: Long,
    ): PdfProject {
        return PdfProject(
            id = UUID.randomUUID().toString(),
            name = displayName.substringBeforeLast('.'),
            pdfUri = uri.toString(),
            pdfDisplayName = displayName,
            pageCount = pageCount,
            selectedPageIndex = 0,
            preferredMode = MeasurementMode.AREA,
            pages =
                (0 until pageCount).map { pageIndex ->
                    PdfPageState(pageIndex = pageIndex)
                },
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }

    private fun toSummary(project: PdfProject): ProjectSummary =
        ProjectSummary(
            id = project.id,
            name = project.name,
            pdfName = project.pdfDisplayName,
            updatedAt = project.updatedAt,
        )

    private fun colorForIndex(
        index: Int,
        alpha: Int = 0xFF,
    ): Long {
        val palette =
            listOf(
                0xB55836,
                0x2E6A57,
                0x375A7F,
                0x8C5A2B,
                0xA33D59,
                0x466A2C,
            )
        val color = palette[(index - 1).mod(palette.size)]
        return ((alpha.toLong() and 0xFF) shl 24) or color.toLong()
    }

    private fun PdfProject.currentPageOrNull(): PdfPageState? {
        if (pages.isEmpty()) {
            return null
        }
        val safeIndex = selectedPageIndex.coerceIn(0, pages.lastIndex)
        return pages.getOrNull(safeIndex)
    }

    private fun PdfPageState.selectedPlot(): MeasurementPolygon? {
        return plots.firstOrNull { it.id == selectedPlotId } ?: plots.lastOrNull()
    }

    private fun PdfProject.withUpdatedPage(updatedPage: PdfPageState): PdfProject {
        return copy(
            pages =
                pages.map { page ->
                    if (page.pageIndex == updatedPage.pageIndex) {
                        updatedPage
                    } else {
                        page
                    }
                },
        )
    }

    private fun sanitizeProject(
        project: PdfProject,
        enforcedPageCount: Int? = null,
    ): PdfProject {
        val normalizedPageCount =
            maxOf(
                enforcedPageCount ?: 0,
                project.pageCount,
                (project.pages.maxOfOrNull { it.pageIndex } ?: -1) + 1,
                1,
            )
        val existingByIndex = project.pages.associateBy { it.pageIndex }
        val normalizedPages =
            (0 until normalizedPageCount).map { pageIndex ->
                val page = existingByIndex[pageIndex] ?: PdfPageState(pageIndex = pageIndex)
                val selectedPlotId =
                    page.selectedPlotId.takeIf { selectedId ->
                        page.plots.any { it.id == selectedId }
                    } ?: page.plots.lastOrNull()?.id
                page.copy(selectedPlotId = selectedPlotId)
            }
        return project.copy(
            pageCount = normalizedPageCount,
            selectedPageIndex = project.selectedPageIndex.coerceIn(0, normalizedPages.lastIndex),
            pages = normalizedPages,
        )
    }

    companion object {
        private const val HIGH_DETAIL_RENDERING_ENABLED = true
        private const val DETAIL_DEBOUNCE_MS = 140L
        private const val DETAIL_OVERSAMPLE = 1.15
        private const val DETAIL_TRIGGER_RATIO = 1.18
        private const val DETAIL_EXISTING_TILE_RATIO = 0.92
        private const val MAX_DETAIL_EDGE = 1280
        private const val MIN_DETAIL_EDGE = 384
    }
}
