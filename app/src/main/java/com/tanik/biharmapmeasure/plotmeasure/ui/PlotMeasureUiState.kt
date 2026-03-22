package com.tanik.biharmapmeasure.plotmeasure.ui

import android.net.Uri
import com.tanik.biharmapmeasure.plotmeasure.core.geometry.PlotComputation
import com.tanik.biharmapmeasure.plotmeasure.core.pdf.PageRect
import com.tanik.biharmapmeasure.plotmeasure.core.pdf.RenderedPdfTile
import com.tanik.biharmapmeasure.plotmeasure.core.export.ExportFormat
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPoint
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPolygon
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementMode
import com.tanik.biharmapmeasure.plotmeasure.model.PdfPageState
import com.tanik.biharmapmeasure.plotmeasure.model.PdfProject
import com.tanik.biharmapmeasure.plotmeasure.model.PlotMeasureSettings

enum class PanelTab {
    RESULTS,
    POINTS,
    SEGMENTS,
    SETTINGS,
    PROJECTS,
}

data class ProjectSummary(
    val id: String,
    val name: String,
    val pdfName: String,
    val updatedAt: Long,
)

data class ViewerViewport(
    val visiblePageRect: PageRect,
    val screenPixelsPerPageUnit: Double,
    val zoomFactor: Double,
    val baseDensity: Double,
)

data class PendingExportRequest(
    val format: ExportFormat,
    val suggestedFileName: String,
)

data class PlotMeasureUiState(
    val isLoading: Boolean = false,
    val projectSummaries: List<ProjectSummary> = emptyList(),
    val project: PdfProject? = null,
    val currentPageState: PdfPageState? = null,
    val activePlot: MeasurementPolygon? = null,
    val plotComputation: PlotComputation? = null,
    val settings: PlotMeasureSettings = PlotMeasureSettings(),
    val pageCount: Int = 0,
    val baseTile: RenderedPdfTile? = null,
    val detailTile: RenderedPdfTile? = null,
    val currentMode: MeasurementMode = MeasurementMode.AREA,
    val isEditMode: Boolean = false,
    val selectedPointId: String? = null,
    val pagePickerOpen: Boolean = false,
    val calibrationDialogOpen: Boolean = false,
    val isCapturingCalibration: Boolean = false,
    val calibrationCapturePoints: List<MeasurementPoint> = emptyList(),
    val message: String? = null,
    val warningMessage: String? = null,
    val panelTab: PanelTab = PanelTab.RESULTS,
    val pendingExport: PendingExportRequest? = null,
    val viewerZoomFactor: Double = 1.0,
    val screenPixelsPerPageUnit: Double = 1.0,
    val calibrationRedoAvailable: Boolean = false,
    val selectedCalibrationPointIndex: Int = 0,
    val isLargeViewEnabled: Boolean = false,
    val crashReport: String? = null,
) {
    val hasDocument: Boolean
        get() = project != null && baseTile != null

    val pointNudgeStepPageUnits: Double
        get() = if (screenPixelsPerPageUnit > 0.0) 2.0 / screenPixelsPerPageUnit else 2.0
}
