package com.tanik.biharmapmeasure.plotmeasure.model

import kotlinx.serialization.Serializable

@Serializable
enum class MeasurementMode {
    DISTANCE,
    PATH,
    AREA,
}

@Serializable
enum class CalibrationMethod {
    MANUAL,
    RATIO,
    TEXT_SCALE,
    SCALE_BAR,
    PRESET,
}

@Serializable
enum class ManualReferenceType {
    KNOWN_LINE,
    SCALE_BAR,
}

@Serializable
enum class LinearUnit(
    val label: String,
    val metersPerUnit: Double,
) {
    METER("m", 1.0),
    FOOT("ft", 0.3048),
    INCH("in", 0.0254),
    MILE("mile", 1609.344),
    CHAIN("chain", 20.1168),
    ZARIB("zarib", 20.1168),
    ;

    fun toMeters(value: Double): Double = value * metersPerUnit

    companion object {
        fun defaultGroundUnits(): List<LinearUnit> =
            listOf(METER, FOOT, MILE, CHAIN, ZARIB)

        fun defaultMapUnits(): List<LinearUnit> =
            listOf(INCH, FOOT, METER)
    }
}

@Serializable
data class MeasurementPoint(
    val id: String,
    val x: Double,
    val y: Double,
    val labelOverride: String? = null,
)

@Serializable
data class MeasurementPolygon(
    val id: String,
    val name: String,
    val mode: MeasurementMode,
    val points: List<MeasurementPoint> = emptyList(),
    val isClosed: Boolean = false,
    val strokeColorArgb: Long = 0xFFB55836,
    val fillColorArgb: Long = 0x66E8B99F,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class CalibrationMetadata(
    val knownPageDistance: Double? = null,
    val knownRealDistance: Double? = null,
    val knownRealUnit: LinearUnit? = null,
    val ratioDenominator: Double? = null,
    val mapDistanceValue: Double? = null,
    val mapDistanceUnit: LinearUnit? = null,
    val groundDistanceValue: Double? = null,
    val groundDistanceUnit: LinearUnit? = null,
    val referenceType: ManualReferenceType? = null,
    val referencePoints: List<MeasurementPoint> = emptyList(),
)

@Serializable
data class CalibrationProfile(
    val id: String,
    val name: String,
    val method: CalibrationMethod,
    val metersPerPageUnit: Double,
    val metadata: CalibrationMetadata,
    val createdAt: Long,
)

@Serializable
data class PdfPageState(
    val pageIndex: Int,
    val calibration: CalibrationProfile? = null,
    val plots: List<MeasurementPolygon> = emptyList(),
    val selectedPlotId: String? = null,
)

@Serializable
data class PdfProject(
    val id: String,
    val name: String,
    val pdfUri: String,
    val pdfDisplayName: String,
    val pageCount: Int,
    val selectedPageIndex: Int = 0,
    val preferredMode: MeasurementMode = MeasurementMode.AREA,
    val pages: List<PdfPageState> = emptyList(),
    val calibrationPresets: List<CalibrationProfile> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class SegmentMeasurement(
    val index: Int,
    val startPointLabel: String,
    val endPointLabel: String,
    val lengthPageUnits: Double,
    val lengthMeters: Double? = null,
)

@Serializable
data class AreaBreakdown(
    val squareMeters: Double,
    val squareFeet: Double,
    val acres: Double,
    val hectares: Double,
    val decimal: Double,
    val bigha: Double,
    val kattha: Double,
    val dhur: Double,
)

@Serializable
data class ExportPlotReport(
    val plotId: String,
    val plotName: String,
    val mode: MeasurementMode,
    val isClosed: Boolean,
    val pointCount: Int,
    val isSelfIntersecting: Boolean,
    val points: List<MeasurementPoint>,
    val segments: List<SegmentMeasurement>,
    val perimeterMeters: Double? = null,
    val area: AreaBreakdown? = null,
)

@Serializable
data class ExportReport(
    val projectName: String,
    val pdfName: String,
    val pageNumber: Int,
    val calibrationMethod: CalibrationMethod? = null,
    val calibrationName: String? = null,
    val metersPerPageUnit: Double? = null,
    val plots: List<ExportPlotReport>,
    val exportedAtEpochMillis: Long,
)

@Serializable
data class LayerVisibility(
    val showPoints: Boolean = true,
    val showLabels: Boolean = true,
    val showPolygonFill: Boolean = true,
    val showSegments: Boolean = true,
    val showCentroid: Boolean = true,
)

@Serializable
data class PlotMeasureSettings(
    val squareFeetPerBigha: Double = 27_220.0,
    val squareFeetPerKattha: Double = 1_361.0,
    val squareFeetPerDhur: Double = 68.05,
    val snapToEdgeEnabled: Boolean = false,
    val showLoupe: Boolean = false,
    val layerVisibility: LayerVisibility = LayerVisibility(),
    val lastProjectId: String? = null,
)
