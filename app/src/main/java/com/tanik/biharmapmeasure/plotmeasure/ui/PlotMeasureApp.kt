package com.tanik.biharmapmeasure.plotmeasure.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import com.tanik.biharmapmeasure.plotmeasure.core.export.ExportFormat
import com.tanik.biharmapmeasure.plotmeasure.core.geometry.GeometryEngine
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementMode
import com.tanik.biharmapmeasure.plotmeasure.ui.components.CalibrationDialog
import com.tanik.biharmapmeasure.plotmeasure.ui.components.PagePickerDialog
import com.tanik.biharmapmeasure.plotmeasure.ui.components.PdfPlotViewer
import com.tanik.biharmapmeasure.plotmeasure.ui.components.PlotSidePanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotMeasureApp(viewModel: PlotMeasureViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val openDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                val displayName = DocumentFile.fromSingleUri(context, uri)?.name ?: "Plot Map.pdf"
                viewModel.importPdf(uri, displayName)
            }
        }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            val pendingExport = uiState.pendingExport
            if (uri != null && pendingExport != null) {
                viewModel.exportToUri(uri, pendingExport.format)
            } else {
                viewModel.clearPendingExport()
            }
        }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    LaunchedEffect(uiState.pendingExport) {
        val request = uiState.pendingExport ?: return@LaunchedEffect
        exportLauncher.launch(request.suggestedFileName)
    }

    if (uiState.pagePickerOpen) {
        PagePickerDialog(
            pageCount = uiState.pageCount,
            selectedPageIndex = uiState.project?.selectedPageIndex ?: 0,
            onDismiss = viewModel::closePagePicker,
            onSelectPage = viewModel::selectPage,
        )
    }

    if (uiState.crashReport != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCrashReport,
            title = { Text("Previous Crash Report") },
            text = {
                Text(
                    text = uiState.crashReport ?: "",
                    modifier =
                        Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissCrashReport) {
                    Text("Close")
                }
            },
        )
    }

    val currentProject = uiState.project
    if (uiState.calibrationDialogOpen && currentProject != null) {
        val captureDistance =
            uiState.calibrationCapturePoints
                .takeIf { it.size == 2 }
                ?.let { GeometryEngine.distance(it[0], it[1]) }
        CalibrationDialog(
            project = currentProject,
            capturePoints = uiState.calibrationCapturePoints,
            selectedCaptureIndex = uiState.selectedCalibrationPointIndex,
            capturePointsCount = uiState.calibrationCapturePoints.size,
            capturePointsDistance = captureDistance,
            nudgeStepPageUnits = uiState.pointNudgeStepPageUnits,
            onDismiss = viewModel::closeCalibrationDialog,
            onStartManualCapture = viewModel::startCalibrationCapture,
            onResumeManualCapture = viewModel::resumeCalibrationCapture,
            onSelectCapturePoint = viewModel::selectCalibrationPoint,
            onNudgeCapturePoint = viewModel::nudgeCalibrationPoint,
            onApplyManual = viewModel::applyManualCalibration,
            onApplyRatio = viewModel::applyRatioCalibration,
            onApplyTextScale = viewModel::applyTextScaleCalibration,
            onApplyPreset = viewModel::applyCalibrationPreset,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PlotMeasure v1.1.0", fontWeight = FontWeight.Bold)
                            Text(
                                text =
                                    uiState.project?.pdfDisplayName
                                        ?: "Measure land plots directly on cadastral PDFs",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { openDocumentLauncher.launch(arrayOf("application/pdf")) },
                        label = { Text("Open") },
                    )
                    AssistChip(
                        onClick = viewModel::openPagePicker,
                        enabled = uiState.pageCount > 1,
                        label = { Text("Page") },
                    )
                    AssistChip(
                        onClick = viewModel::openCalibrationDialog,
                        enabled = uiState.hasDocument,
                        label = { Text("Calibrate") },
                    )
                    AssistChip(
                        onClick = viewModel::toggleLargeView,
                        enabled = uiState.hasDocument,
                        label = { Text(if (uiState.isLargeViewEnabled) "Panel" else "Large") },
                    )
                    AssistChip(
                        onClick = { viewModel.prepareExport(ExportFormat.PDF) },
                        enabled = uiState.currentPageState != null,
                        label = { Text("PDF") },
                    )
                    AssistChip(
                        onClick = { viewModel.prepareExport(ExportFormat.JSON) },
                        enabled = uiState.currentPageState != null,
                        label = { Text("JSON") },
                    )
                    AssistChip(
                        onClick = { viewModel.prepareExport(ExportFormat.CSV) },
                        enabled = uiState.currentPageState != null,
                        label = { Text("CSV") },
                    )
                    AssistChip(
                        onClick = viewModel::undo,
                        enabled = uiState.project != null || uiState.calibrationCapturePoints.isNotEmpty(),
                        label = { Text("Undo") },
                    )
                    AssistChip(
                        onClick = viewModel::redo,
                        enabled = uiState.project != null || uiState.calibrationRedoAvailable,
                        label = { Text("Redo") },
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        MeasurementMode.DISTANCE to "Distance",
                        MeasurementMode.PATH to "Path",
                        MeasurementMode.AREA to "Area",
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = uiState.currentMode == mode,
                            onClick = { viewModel.setMode(mode) },
                            label = { Text(label) },
                        )
                    }
                    FilterChip(
                        selected = uiState.isEditMode,
                        onClick = viewModel::toggleEditMode,
                        label = { Text(if (uiState.isEditMode) "Editing" else "Edit") },
                    )
                    AssistChip(onClick = viewModel::createNewPlot, label = { Text("New plot") })
                    AssistChip(
                        onClick = viewModel::closePolygon,
                        label = { Text("Close polygon") },
                    )
                }
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val isTabletLayout = maxWidth >= 980.dp
            if (uiState.isLargeViewEnabled) {
                ViewerPane(
                    modifier = Modifier.fillMaxSize(),
                    uiState = uiState,
                    viewModel = viewModel,
                )
            } else if (isTabletLayout) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ViewerPane(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        uiState = uiState,
                        viewModel = viewModel,
                    )
                    PlotSidePanel(
                        modifier = Modifier.width(380.dp).fillMaxHeight(),
                        uiState = uiState,
                        onSelectTab = viewModel::setPanelTab,
                        onSelectPlot = viewModel::selectPlot,
                        onCreatePlot = viewModel::createNewPlot,
                        onDeletePlot = viewModel::deleteCurrentPlot,
                        onSelectPoint = viewModel::selectPoint,
                        onDeleteSelectedPoint = viewModel::deleteSelectedPoint,
                        onNudgeSelectedPoint = viewModel::nudgeSelectedPoint,
                        onLoadProject = viewModel::loadProject,
                        onUpdateAreaUnitSettings = viewModel::updateAreaUnitSettings,
                        onUpdateViewerSettings = viewModel::updateViewerSettings,
                        onUpdateLayerVisibility = { visibility ->
                            viewModel.updateLayerVisibility { visibility }
                        },
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ViewerPane(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        uiState = uiState,
                        viewModel = viewModel,
                    )
                    PlotSidePanel(
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        uiState = uiState,
                        onSelectTab = viewModel::setPanelTab,
                        onSelectPlot = viewModel::selectPlot,
                        onCreatePlot = viewModel::createNewPlot,
                        onDeletePlot = viewModel::deleteCurrentPlot,
                        onSelectPoint = viewModel::selectPoint,
                        onDeleteSelectedPoint = viewModel::deleteSelectedPoint,
                        onNudgeSelectedPoint = viewModel::nudgeSelectedPoint,
                        onLoadProject = viewModel::loadProject,
                        onUpdateAreaUnitSettings = viewModel::updateAreaUnitSettings,
                        onUpdateViewerSettings = viewModel::updateViewerSettings,
                        onUpdateLayerVisibility = { visibility ->
                            viewModel.updateLayerVisibility { visibility }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerPane(
    modifier: Modifier,
    uiState: PlotMeasureUiState,
    viewModel: PlotMeasureViewModel,
) {
    Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (uiState.baseTile == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Import a cadastral PDF to start measuring.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "PlotMeasure keeps points in PDF page coordinates, supports multi-point plots, and exports reports.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.widthIn(max = 340.dp),
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                PdfPlotViewer(
                    modifier = Modifier.fillMaxSize(),
                    baseTile = uiState.baseTile,
                    detailTile = uiState.detailTile,
                    pageState = uiState.currentPageState,
                    activePlot = uiState.activePlot,
                    plotComputation = uiState.plotComputation,
                    currentMode = uiState.currentMode,
                    isEditMode = uiState.isEditMode,
                    isCapturingCalibration = uiState.isCapturingCalibration,
                    calibrationCapturePoints = uiState.calibrationCapturePoints,
                    selectedPointId = uiState.selectedPointId,
                    settings = uiState.settings,
                    zoomFactor = uiState.viewerZoomFactor,
                    onViewportChanged = viewModel::onViewportChanged,
                    onAddPoint = viewModel::addPoint,
                    onCaptureCalibrationPoint = viewModel::captureCalibrationPoint,
                    onClosePolygon = viewModel::closePolygon,
                    onSelectPoint = viewModel::selectPoint,
                    onBeginPointDrag = viewModel::beginPointDrag,
                    onUpdatePointPosition = viewModel::updatePointPosition,
                    onEndPointDrag = viewModel::endPointDrag,
                    onInsertPoint = viewModel::insertPointAt,
                )
                CalibrationAdjustOverlay(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    uiState = uiState,
                    onOpenCalibrationDialog = viewModel::openCalibrationDialog,
                    onResumeCalibrationCapture = viewModel::resumeCalibrationCapture,
                    onSelectCalibrationPoint = viewModel::selectCalibrationPoint,
                    onNudgeCalibrationPoint = viewModel::nudgeCalibrationPoint,
                )
                PointAdjustOverlay(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    uiState = uiState,
                    onNudgeSelectedPoint = viewModel::nudgeSelectedPoint,
                    onNudgeLastPoint = viewModel::nudgeLastPoint,
                )
            }
        }
    }
}

@Composable
private fun CalibrationAdjustOverlay(
    modifier: Modifier,
    uiState: PlotMeasureUiState,
    onOpenCalibrationDialog: () -> Unit,
    onResumeCalibrationCapture: () -> Unit,
    onSelectCalibrationPoint: (Int) -> Unit,
    onNudgeCalibrationPoint: (Int, Double, Double) -> Unit,
) {
    val capturePoints = uiState.calibrationCapturePoints
    if (uiState.calibrationDialogOpen || capturePoints.isEmpty()) {
        return
    }
    val selectedIndex = uiState.selectedCalibrationPointIndex.coerceIn(0, capturePoints.lastIndex)
    val step = uiState.pointNudgeStepPageUnits
    Card(
        modifier = modifier.widthIn(max = 420.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Calibration Adjust", fontWeight = FontWeight.Bold)
                    Text("Move points and watch the map update live", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onOpenCalibrationDialog) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Open calibration details")
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                capturePoints.forEachIndexed { index, point ->
                    FilterChip(
                        selected = index == selectedIndex,
                        onClick = { onSelectCalibrationPoint(index) },
                        label = {
                            Text(
                                "C${index + 1} (${formatCoordinate(point.x)}, ${formatCoordinate(point.y)})",
                            )
                        },
                    )
                }
            }
            Text("Step: ${formatCoordinate(step)} page units (2 px)", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { onNudgeCalibrationPoint(selectedIndex, 0.0, -step) }) {
                    Text("Up")
                }
                TextButton(onClick = { onNudgeCalibrationPoint(selectedIndex, 0.0, step) }) {
                    Text("Down")
                }
                TextButton(onClick = { onNudgeCalibrationPoint(selectedIndex, -step, 0.0) }) {
                    Text("Left")
                }
                TextButton(onClick = { onNudgeCalibrationPoint(selectedIndex, step, 0.0) }) {
                    Text("Right")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (capturePoints.size < 2) {
                    TextButton(onClick = onResumeCalibrationCapture) {
                        Text("Continue Capture")
                    }
                } else {
                    Spacer(modifier = Modifier)
                }
                TextButton(onClick = onOpenCalibrationDialog) {
                    Text("Enter Distance")
                }
            }
        }
    }
}

@Composable
private fun PointAdjustOverlay(
    modifier: Modifier,
    uiState: PlotMeasureUiState,
    onNudgeSelectedPoint: (Double, Double) -> Unit,
    onNudgeLastPoint: (Double, Double) -> Unit,
) {
    if (!uiState.isLargeViewEnabled || uiState.isCapturingCalibration) {
        return
    }
    val plot = uiState.activePlot ?: return
    val points = plot.points
    val selectedIndex = points.indexOfFirst { it.id == uiState.selectedPointId }
    val hasSelectedPoint = selectedIndex >= 0
    val targetPoint = if (hasSelectedPoint) points[selectedIndex] else points.lastOrNull() ?: return
    val pointLabel = if (hasSelectedPoint) "P${selectedIndex + 1}" else "P${points.size}"
    val title = if (hasSelectedPoint) "Adjust Selected Point" else "Adjust Last Point"
    val helperText =
        if (hasSelectedPoint) {
            "Selected on map. Use Edit mode to choose another point."
        } else {
            "No point selected. Adjusting the latest point."
        }
    val step = uiState.pointNudgeStepPageUnits
    val nudge: (Double, Double) -> Unit =
        if (hasSelectedPoint) {
            onNudgeSelectedPoint
        } else {
            onNudgeLastPoint
        }
    Card(
        modifier = modifier.widthIn(max = 260.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                "$pointLabel  X ${formatCoordinate(targetPoint.x)}  Y ${formatCoordinate(targetPoint.y)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(helperText, style = MaterialTheme.typography.bodySmall)
            Text("Step: ${formatCoordinate(step)} page units (2 px)", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { nudge(0.0, -step) }) {
                    Text("Up")
                }
                TextButton(onClick = { nudge(0.0, step) }) {
                    Text("Down")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { nudge(-step, 0.0) }) {
                    Text("Left")
                }
                TextButton(onClick = { nudge(step, 0.0) }) {
                    Text("Right")
                }
            }
        }
    }
}
