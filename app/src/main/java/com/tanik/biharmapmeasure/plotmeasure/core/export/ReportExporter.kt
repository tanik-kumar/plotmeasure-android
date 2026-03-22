package com.tanik.biharmapmeasure.plotmeasure.core.export

import android.content.ContentResolver
import android.net.Uri
import com.tanik.biharmapmeasure.plotmeasure.core.geometry.GeometryEngine
import com.tanik.biharmapmeasure.plotmeasure.model.ExportPlotReport
import com.tanik.biharmapmeasure.plotmeasure.model.ExportReport
import com.tanik.biharmapmeasure.plotmeasure.model.PdfProject
import com.tanik.biharmapmeasure.plotmeasure.model.PdfPageState
import com.tanik.biharmapmeasure.plotmeasure.model.PlotMeasureSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedWriter

enum class ExportFormat(
    val extension: String,
    val mimeType: String,
) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
}

class ReportExporter(
    private val contentResolver: ContentResolver,
    private val json: Json,
) {
    fun buildReport(
        project: PdfProject,
        pageState: PdfPageState,
        settings: PlotMeasureSettings,
    ): ExportReport {
        val plots =
            pageState.plots.map { plot ->
                val computation = GeometryEngine.computePlot(plot, pageState.calibration, settings)
                ExportPlotReport(
                    plotId = plot.id,
                    plotName = plot.name,
                    mode = plot.mode,
                    isClosed = plot.isClosed,
                    pointCount = plot.points.size,
                    isSelfIntersecting = computation.isSelfIntersecting,
                    points = plot.points,
                    segments = computation.segments,
                    perimeterMeters = computation.perimeterMeters,
                    area = computation.area,
                )
            }

        return ExportReport(
            projectName = project.name,
            pdfName = project.pdfDisplayName,
            pageNumber = pageState.pageIndex + 1,
            calibrationMethod = pageState.calibration?.method,
            calibrationName = pageState.calibration?.name,
            metersPerPageUnit = pageState.calibration?.metersPerPageUnit,
            plots = plots,
            exportedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    fun toJson(report: ExportReport): String = json.encodeToString(report)

    fun toCsv(report: ExportReport): String {
        val header =
            listOf(
                "project_name",
                "pdf_name",
                "page_number",
                "calibration_method",
                "calibration_name",
                "plot_name",
                "mode",
                "point_count",
                "is_closed",
                "self_intersecting",
                "perimeter_m",
                "area_sq_m",
                "area_sq_ft",
                "acre",
                "hectare",
                "decimal",
                "bigha",
                "kattha",
                "dhur",
                "points",
                "segments_m",
            )
        val rows =
            report.plots.map { plot ->
                listOf(
                    escape(report.projectName),
                    escape(report.pdfName),
                    report.pageNumber.toString(),
                    report.calibrationMethod?.name.orEmpty(),
                    escape(report.calibrationName.orEmpty()),
                    escape(plot.plotName),
                    plot.mode.name,
                    plot.pointCount.toString(),
                    plot.isClosed.toString(),
                    plot.isSelfIntersecting.toString(),
                    plot.perimeterMeters?.toString().orEmpty(),
                    plot.area?.squareMeters?.toString().orEmpty(),
                    plot.area?.squareFeet?.toString().orEmpty(),
                    plot.area?.acres?.toString().orEmpty(),
                    plot.area?.hectares?.toString().orEmpty(),
                    plot.area?.decimal?.toString().orEmpty(),
                    plot.area?.bigha?.toString().orEmpty(),
                    plot.area?.kattha?.toString().orEmpty(),
                    plot.area?.dhur?.toString().orEmpty(),
                    escape(plot.points.joinToString(" | ") { "${it.x},${it.y}" }),
                    escape(plot.segments.joinToString(" | ") { it.lengthMeters?.toString() ?: it.lengthPageUnits.toString() }),
                ).joinToString(",")
            }
        return buildString {
            appendLine(header.joinToString(","))
            rows.forEach(::appendLine)
        }
    }

    fun exportToUri(
        uri: Uri,
        format: ExportFormat,
        report: ExportReport,
    ) {
        val content =
            when (format) {
                ExportFormat.JSON -> toJson(report)
                ExportFormat.CSV -> toCsv(report)
            }
        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(content)
        } ?: error("Unable to open export destination.")
    }

    private fun escape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
