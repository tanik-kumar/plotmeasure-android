package com.tanik.biharmapmeasure.plotmeasure.core.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tanik.biharmapmeasure.plotmeasure.core.geometry.GeometryEngine
import com.tanik.biharmapmeasure.plotmeasure.core.geometry.PlotComputation
import com.tanik.biharmapmeasure.plotmeasure.model.CalibrationMethod
import com.tanik.biharmapmeasure.plotmeasure.model.CalibrationProfile
import com.tanik.biharmapmeasure.plotmeasure.model.ExportPlotReport
import com.tanik.biharmapmeasure.plotmeasure.model.ExportReport
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementMode
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPoint
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPolygon
import com.tanik.biharmapmeasure.plotmeasure.model.PdfProject
import com.tanik.biharmapmeasure.plotmeasure.model.PdfPageState
import com.tanik.biharmapmeasure.plotmeasure.model.PlotMeasureSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class ExportFormat(
    val extension: String,
    val mimeType: String,
) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
    PDF("pdf", "application/pdf"),
}

private data class RenderedExportPage(
    val bitmap: Bitmap,
    val pageWidth: Int,
    val pageHeight: Int,
)

private data class PlotExportSnapshot(
    val plot: MeasurementPolygon,
    val computation: PlotComputation,
)

private data class AnnotationStyle(
    val polygonStrokeWidth: Float,
    val pointOuterRadius: Float,
    val pointInnerRadius: Float,
    val pointStrokeWidth: Float,
    val centroidRadius: Float,
    val calibrationStrokeWidth: Float,
    val labelTextSize: Float,
    val smallTextSize: Float,
    val labelPadding: Float,
    val chipCornerRadius: Float,
)

class ReportExporter(
    private val context: Context,
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
        project: PdfProject,
        pageState: PdfPageState,
        settings: PlotMeasureSettings,
    ) {
        when (format) {
            ExportFormat.JSON -> writeTextToUri(uri, toJson(report))
            ExportFormat.CSV -> writeTextToUri(uri, toCsv(report))
            ExportFormat.PDF -> exportAnnotatedPdf(uri, report, project, pageState, settings)
        }
    }

    private fun writeTextToUri(
        uri: Uri,
        content: String,
    ) {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(content)
        } ?: error("Unable to open export destination.")
    }

    private fun exportAnnotatedPdf(
        uri: Uri,
        report: ExportReport,
        project: PdfProject,
        pageState: PdfPageState,
        settings: PlotMeasureSettings,
    ) {
        val renderedPage = renderPageForExport(Uri.parse(project.pdfUri), pageState.pageIndex)
        val plotSnapshots =
            pageState.plots.map { plot ->
                PlotExportSnapshot(
                    plot = plot,
                    computation = GeometryEngine.computePlot(plot, pageState.calibration, settings),
                )
            }

        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val document = PdfDocument()
                try {
                    addAnnotatedMapPage(
                        document = document,
                        renderedPage = renderedPage,
                        pageState = pageState,
                        plotSnapshots = plotSnapshots,
                    )
                    addDetailsPage(
                        document = document,
                        report = report,
                        pageState = pageState,
                        plotSnapshots = plotSnapshots,
                    )
                    document.writeTo(outputStream)
                } finally {
                    document.close()
                }
            } ?: error("Unable to open export destination.")
        } finally {
            if (!renderedPage.bitmap.isRecycled) {
                renderedPage.bitmap.recycle()
            }
        }
    }

    private fun addAnnotatedMapPage(
        document: PdfDocument,
        renderedPage: RenderedExportPage,
        pageState: PdfPageState,
        plotSnapshots: List<PlotExportSnapshot>,
    ) {
        val pageInfo =
            PdfDocument.PageInfo
                .Builder(renderedPage.pageWidth, renderedPage.pageHeight, 1)
                .create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val pageRect = RectF(0f, 0f, renderedPage.pageWidth.toFloat(), renderedPage.pageHeight.toFloat())
        val style = annotationStyle(renderedPage.pageWidth, renderedPage.pageHeight)

        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(renderedPage.bitmap, null, pageRect, null)

        plotSnapshots.forEach { snapshot ->
            drawPlotAnnotation(
                canvas = canvas,
                pageRect = pageRect,
                plot = snapshot.plot,
                computation = snapshot.computation,
                renderStyle = style,
            )
        }
        drawCalibrationAnnotation(
            canvas = canvas,
            pageRect = pageRect,
            calibration = pageState.calibration,
            renderStyle = style,
        )
        drawExportFooter(canvas, pageRect, reportLabel = "PlotMeasure annotated export", style = style)
        document.finishPage(page)
    }

    private fun addDetailsPage(
        document: PdfDocument,
        report: ExportReport,
        pageState: PdfPageState,
        plotSnapshots: List<PlotExportSnapshot>,
    ) {
        val pageInfo =
            PdfDocument.PageInfo
                .Builder(DETAILS_PAGE_WIDTH, DETAILS_PAGE_HEIGHT, 2)
                .create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val width = pageInfo.pageWidth.toFloat()
        val margin = 34f
        val bodyWidth = (width - margin * 2).roundToInt()
        var cursorY = 34f

        val titlePaint = textPaint(16f, isBold = true, color = Color.BLACK)
        val sectionPaint = textPaint(11.5f, isBold = true, color = Color.BLACK)
        val bodyPaint = textPaint(8.75f, color = Color.argb(255, 35, 35, 35))
        val notePaint = textPaint(8.25f, color = Color.argb(255, 70, 70, 70))

        canvas.drawColor(Color.WHITE)
        canvas.drawText("PlotMeasure export details", margin, cursorY, titlePaint)
        cursorY += 18f
        cursorY =
            drawWrappedParagraph(
                canvas = canvas,
                text = "Project ${report.projectName} | PDF ${report.pdfName} | Page ${report.pageNumber} | Exported ${formatTimestamp(report.exportedAtEpochMillis)}",
                left = margin,
                top = cursorY,
                width = bodyWidth,
                paint = bodyPaint,
            ) + 12f

        cursorY = drawSectionTitle(canvas, "Calibration", margin, cursorY, sectionPaint)
        cursorY =
            drawWrappedParagraph(
                canvas = canvas,
                text = calibrationSummaryText(pageState.calibration),
                left = margin,
                top = cursorY,
                width = bodyWidth,
                paint = bodyPaint,
            ) + 10f

        cursorY = drawSectionTitle(canvas, "Plots", margin, cursorY, sectionPaint)
        if (plotSnapshots.isEmpty()) {
            cursorY =
                drawWrappedParagraph(
                    canvas = canvas,
                    text = "No plots were present on this page at export time.",
                    left = margin,
                    top = cursorY,
                    width = bodyWidth,
                    paint = notePaint,
                ) + 6f
        } else {
            plotSnapshots.forEachIndexed { index, snapshot ->
                if (cursorY > pageInfo.pageHeight - 80f) {
                    cursorY =
                        drawWrappedParagraph(
                            canvas = canvas,
                            text = "Additional plot details were omitted to keep the export to one extra page.",
                            left = margin,
                            top = cursorY,
                            width = bodyWidth,
                            paint = notePaint,
                        ) + 6f
                    return@forEachIndexed
                }
                cursorY =
                    drawWrappedParagraph(
                        canvas = canvas,
                        text = plotSummaryText(index + 1, snapshot),
                        left = margin,
                        top = cursorY,
                        width = bodyWidth,
                        paint = bodyPaint,
                    ) + 10f
            }
        }

        document.finishPage(page)
    }

    private fun renderPageForExport(
        pdfUri: Uri,
        pageIndex: Int,
    ): RenderedExportPage {
        val descriptor =
            requireNotNull(context.contentResolver.openFileDescriptor(pdfUri, "r")) {
                "Unable to open PDF for export."
            }
        descriptor.use { fileDescriptor ->
            PdfRenderer(fileDescriptor).use { renderer ->
                require(pageIndex in 0 until renderer.pageCount) { "Page index is out of bounds." }
                renderer.openPage(pageIndex).use { page ->
                    val scale =
                        minOf(
                            MAX_EXPORT_RENDER_SCALE,
                            MAX_EXPORT_BITMAP_EDGE.toFloat() / max(page.width, page.height).toFloat(),
                        ).coerceAtLeast(1f)
                    val bitmapWidth = (page.width * scale).roundToInt().coerceAtLeast(1)
                    val bitmapHeight = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    val matrix =
                        Matrix().apply {
                            setScale(scale, scale)
                        }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return RenderedExportPage(bitmap = bitmap, pageWidth = page.width, pageHeight = page.height)
                }
            }
        }
    }

    private fun drawPlotAnnotation(
        canvas: Canvas,
        pageRect: RectF,
        plot: MeasurementPolygon,
        computation: PlotComputation,
        renderStyle: AnnotationStyle,
    ) {
        if (plot.points.isEmpty()) {
            return
        }

        val strokeColor = plot.strokeColorArgb.toInt()
        val fillColor = withAlpha(plot.fillColorArgb.toInt(), 52)
        val polygonPath =
            Path().apply {
                val first = plot.points.first()
                moveTo(first.x.toFloat(), first.y.toFloat())
                plot.points.drop(1).forEach { point ->
                    lineTo(point.x.toFloat(), point.y.toFloat())
                }
                if (plot.mode == MeasurementMode.AREA && plot.points.size >= 3) {
                    close()
                }
            }

        if (plot.mode == MeasurementMode.AREA && plot.points.size >= 3) {
            canvas.drawPath(
                polygonPath,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = fillColor
                    this.style = Paint.Style.FILL
                },
            )
        }

        canvas.drawPath(
            polygonPath,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = strokeColor
                this.style = Paint.Style.STROKE
                strokeWidth = renderStyle.polygonStrokeWidth
            },
        )

        val pointPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.style = Paint.Style.FILL
            }
        val pointStrokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = strokeColor
                this.style = Paint.Style.STROKE
                strokeWidth = renderStyle.pointStrokeWidth
            }
        val pointInnerPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = strokeColor
                this.style = Paint.Style.FILL
            }
        val labelPaint = textPaint(renderStyle.labelTextSize, color = Color.BLACK)
        val segmentPaint = textPaint(renderStyle.smallTextSize, color = Color.BLACK)

        plot.points.forEachIndexed { index, point ->
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            canvas.drawCircle(x, y, renderStyle.pointOuterRadius, pointPaint)
            canvas.drawCircle(x, y, renderStyle.pointOuterRadius, pointStrokePaint)
            canvas.drawCircle(x, y, renderStyle.pointInnerRadius, pointInnerPaint)
            drawClampedLabel(
                canvas = canvas,
                text = "P${index + 1}",
                proposedLeft = x + renderStyle.labelPadding * 1.6f,
                proposedTop = y - renderStyle.labelTextSize - renderStyle.labelPadding * 1.2f,
                pageRect = pageRect,
                paint = labelPaint,
                backgroundColor = Color.argb(232, 255, 255, 255),
                cornerRadius = renderStyle.chipCornerRadius,
                padding = renderStyle.labelPadding,
            )
        }

        computation.segments.forEachIndexed { index, segment ->
            val start = plot.points.getOrNull(index) ?: return@forEachIndexed
            val end =
                plot.points.getOrNull(index + 1)
                    ?: if (plot.mode == MeasurementMode.AREA) {
                        plot.points.firstOrNull()
                    } else {
                        null
                    }
                    ?: return@forEachIndexed
            val midX = ((start.x + end.x) / 2.0).toFloat()
            val midY = ((start.y + end.y) / 2.0).toFloat()
            drawClampedLabel(
                canvas = canvas,
                text = formatSegmentLength(segment.lengthMeters, segment.lengthPageUnits),
                proposedLeft = midX + renderStyle.labelPadding,
                proposedTop = midY + renderStyle.labelPadding,
                pageRect = pageRect,
                paint = segmentPaint,
                backgroundColor = Color.argb(214, 255, 255, 255),
                cornerRadius = renderStyle.chipCornerRadius,
                padding = renderStyle.labelPadding,
            )
        }

        computation.centroid?.let { centroid ->
            val centroidPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(255, 55, 153, 146)
                    this.style = Paint.Style.FILL
                }
            canvas.drawCircle(centroid.x.toFloat(), centroid.y.toFloat(), renderStyle.centroidRadius, centroidPaint)
            drawClampedLabel(
                canvas = canvas,
                text = "C",
                proposedLeft = centroid.x.toFloat() + renderStyle.labelPadding * 1.5f,
                proposedTop = centroid.y.toFloat() - renderStyle.labelTextSize,
                pageRect = pageRect,
                paint = segmentPaint,
                backgroundColor = Color.argb(210, 255, 255, 255),
                cornerRadius = renderStyle.chipCornerRadius,
                padding = renderStyle.labelPadding,
            )
        }
    }

    private fun drawCalibrationAnnotation(
        canvas: Canvas,
        pageRect: RectF,
        calibration: CalibrationProfile?,
        renderStyle: AnnotationStyle,
    ) {
        val calibrationPoints = calibration?.metadata?.referencePoints.orEmpty()
        if (calibrationPoints.size != 2) {
            return
        }
        val currentCalibration = calibration ?: return

        val linePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(255, 40, 112, 180)
                this.style = Paint.Style.STROKE
                strokeWidth = renderStyle.calibrationStrokeWidth
            }
        val pointPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(255, 40, 112, 180)
                this.style = Paint.Style.FILL
            }
        val labelPaint = textPaint(renderStyle.smallTextSize, color = Color.BLACK)
        val start = calibrationPoints[0]
        val end = calibrationPoints[1]
        val midX = ((start.x + end.x) / 2.0).toFloat()
        val midY = ((start.y + end.y) / 2.0).toFloat()

        canvas.drawLine(start.x.toFloat(), start.y.toFloat(), end.x.toFloat(), end.y.toFloat(), linePaint)
        canvas.drawCircle(start.x.toFloat(), start.y.toFloat(), renderStyle.pointOuterRadius, pointPaint)
        canvas.drawCircle(end.x.toFloat(), end.y.toFloat(), renderStyle.pointOuterRadius, pointPaint)
        drawClampedLabel(
            canvas = canvas,
            text = start.labelOverride ?: "C1",
            proposedLeft = start.x.toFloat() + renderStyle.labelPadding * 1.4f,
            proposedTop = start.y.toFloat() - renderStyle.labelTextSize,
            pageRect = pageRect,
            paint = labelPaint,
            backgroundColor = Color.argb(224, 255, 255, 255),
            cornerRadius = renderStyle.chipCornerRadius,
            padding = renderStyle.labelPadding,
        )
        drawClampedLabel(
            canvas = canvas,
            text = end.labelOverride ?: "C2",
            proposedLeft = end.x.toFloat() + renderStyle.labelPadding * 1.4f,
            proposedTop = end.y.toFloat() - renderStyle.labelTextSize,
            pageRect = pageRect,
            paint = labelPaint,
            backgroundColor = Color.argb(224, 255, 255, 255),
            cornerRadius = renderStyle.chipCornerRadius,
            padding = renderStyle.labelPadding,
        )
        drawClampedLabel(
            canvas = canvas,
            text = calibrationOverlayText(currentCalibration),
            proposedLeft = midX + renderStyle.labelPadding,
            proposedTop = midY + renderStyle.labelPadding,
            pageRect = pageRect,
            paint = labelPaint,
            backgroundColor = Color.argb(230, 255, 255, 255),
            cornerRadius = renderStyle.chipCornerRadius,
            padding = renderStyle.labelPadding,
        )
    }

    private fun drawExportFooter(
        canvas: Canvas,
        pageRect: RectF,
        reportLabel: String,
        style: AnnotationStyle,
    ) {
        drawClampedLabel(
            canvas = canvas,
            text = reportLabel,
            proposedLeft = style.labelPadding * 2f,
            proposedTop = pageRect.bottom - style.smallTextSize - style.labelPadding * 3f,
            pageRect = pageRect,
            paint = textPaint(style.smallTextSize, color = Color.BLACK),
            backgroundColor = Color.argb(200, 255, 255, 255),
            cornerRadius = style.chipCornerRadius,
            padding = style.labelPadding,
        )
    }

    private fun drawClampedLabel(
        canvas: Canvas,
        text: String,
        proposedLeft: Float,
        proposedTop: Float,
        pageRect: RectF,
        paint: TextPaint,
        backgroundColor: Int,
        cornerRadius: Float,
        padding: Float,
    ) {
        if (text.isBlank()) {
            return
        }
        val metrics = paint.fontMetrics
        val textWidth = paint.measureText(text)
        val textHeight = metrics.descent - metrics.ascent
        val maxLeft = (pageRect.right - textWidth - padding * 2f).coerceAtLeast(pageRect.left + padding)
        val maxTop = (pageRect.bottom - textHeight - padding * 2f).coerceAtLeast(pageRect.top + padding)
        val safeLeft = proposedLeft.coerceIn(pageRect.left + padding, maxLeft)
        val safeTop = proposedTop.coerceIn(pageRect.top + padding, maxTop)
        val rect =
            RectF(
                safeLeft - padding,
                safeTop - padding,
                safeLeft + textWidth + padding,
                safeTop + textHeight + padding,
            )
        canvas.drawRoundRect(
            rect,
            cornerRadius,
            cornerRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = backgroundColor
                style = Paint.Style.FILL
            },
        )
        canvas.drawText(text, safeLeft, safeTop - metrics.ascent, paint)
    }

    private fun drawSectionTitle(
        canvas: Canvas,
        title: String,
        left: Float,
        top: Float,
        paint: TextPaint,
    ): Float {
        canvas.drawText(title, left, top, paint)
        return top + 14f
    }

    private fun drawWrappedParagraph(
        canvas: Canvas,
        text: String,
        left: Float,
        top: Float,
        width: Int,
        paint: TextPaint,
    ): Float {
        val layout =
            StaticLayout
                .Builder
                .obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(0f, 1.05f)
                .build()
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
        return top + layout.height
    }

    private fun plotSummaryText(
        ordinal: Int,
        snapshot: PlotExportSnapshot,
    ): String {
        val plot = snapshot.plot
        val computation = snapshot.computation
        val lines = mutableListOf<String>()
        lines += "Plot $ordinal: ${plot.name}"
        lines += "Mode: ${plot.mode.name}"
        lines += "Points: ${plot.points.size} | Closed: ${plot.isClosed}"
        lines +=
            if (computation.isSelfIntersecting) {
                "Status: Invalid self-intersecting polygon"
            } else {
                "Status: Valid"
            }
        lines += "Perimeter: ${formatDistanceValue(computation.perimeterMeters, computation.perimeterPageUnits)}"
        if (plot.mode == MeasurementMode.AREA) {
            lines +=
                computation.area?.let { area ->
                    buildString {
                        append("Area: ")
                        append("${shortNumber.format(area.squareMeters)} sq m")
                        append(" | ${shortNumber.format(area.squareFeet)} sq ft")
                        append(" | ${preciseNumber.format(area.acres)} acre")
                        append(" | ${preciseNumber.format(area.hectares)} hectare")
                        append(" | ${shortNumber.format(area.decimal)} decimal")
                        append(" | ${shortNumber.format(area.bigha)} bigha")
                        append(" | ${shortNumber.format(area.kattha)} kattha")
                        append(" | ${shortNumber.format(area.dhur)} dhur")
                    }
                } ?: "Area: blocked until the polygon is calibrated and valid"
        }
        if (computation.segments.isNotEmpty()) {
            lines +=
                "Segments: " +
                    computation.segments.joinToString(", ") { segment ->
                        "${segment.startPointLabel}-${segment.endPointLabel} ${formatSegmentLength(segment.lengthMeters, segment.lengthPageUnits)}"
                    }
        }
        lines +=
            "Coordinates: " +
                plot.points.joinToString(", ") { point ->
                    "${point.labelOverride ?: pointLabel(plot, point)}(${preciseNumber.format(point.x)}, ${preciseNumber.format(point.y)})"
                }
        return lines.joinToString("\n")
    }

    private fun pointLabel(
        plot: MeasurementPolygon,
        point: MeasurementPoint,
    ): String {
        val index = plot.points.indexOfFirst { it.id == point.id }
        return if (index >= 0) "P${index + 1}" else "P?"
    }

    private fun calibrationSummaryText(calibration: CalibrationProfile?): String {
        calibration ?: return "No calibration was applied on this page."
        val metadata = calibration.metadata
        val lines = mutableListOf<String>()
        lines += "Name: ${calibration.name}"
        lines += "Method: ${calibration.method.name.replace('_', ' ')}"
        lines += "Meters per page unit: ${preciseNumber.format(calibration.metersPerPageUnit)}"
        when (calibration.method) {
            CalibrationMethod.MANUAL,
            CalibrationMethod.SCALE_BAR,
            -> {
                lines += "Reference: ${metadata.referenceType?.name?.replace('_', ' ') ?: "KNOWN_LINE"}"
                metadata.knownRealDistance?.let { realDistance ->
                    lines += "Known distance: ${preciseNumber.format(realDistance)} ${metadata.knownRealUnit?.label.orEmpty()}"
                }
                metadata.knownPageDistance?.let { lines += "Measured page distance: ${preciseNumber.format(it)} page units" }
            }
            CalibrationMethod.RATIO -> {
                metadata.ratioDenominator?.let { lines += "Scale ratio: 1:${shortNumber.format(it)}" }
            }
            CalibrationMethod.TEXT_SCALE -> {
                val mapText =
                    buildString {
                        append(metadata.mapDistanceValue?.let(preciseNumber::format) ?: "--")
                        append(' ')
                        append(metadata.mapDistanceUnit?.label ?: "")
                    }.trim()
                val groundText =
                    buildString {
                        append(metadata.groundDistanceValue?.let(preciseNumber::format) ?: "--")
                        append(' ')
                        append(metadata.groundDistanceUnit?.label ?: "")
                    }.trim()
                lines += "Scale text: $mapText = $groundText"
                metadata.ratioDenominator?.let { lines += "Equivalent ratio: 1:${shortNumber.format(it)}" }
            }
            CalibrationMethod.PRESET -> {
                metadata.ratioDenominator?.let { lines += "Preset ratio: 1:${shortNumber.format(it)}" }
            }
        }
        if (metadata.referencePoints.size == 2) {
            lines +=
                "Reference points: " +
                    metadata.referencePoints.joinToString(" | ") { point ->
                        "${point.labelOverride ?: "C"}(${preciseNumber.format(point.x)}, ${preciseNumber.format(point.y)})"
                    }
        }
        return lines.joinToString("\n")
    }

    private fun calibrationOverlayText(calibration: CalibrationProfile): String {
        val metadata = calibration.metadata
        val referenceText =
            metadata.knownRealDistance?.let { distance ->
                "${preciseNumber.format(distance)} ${metadata.knownRealUnit?.label.orEmpty()}".trim()
            }
        return buildString {
            append("Calibration")
            if (!referenceText.isNullOrBlank()) {
                append(": ")
                append(referenceText)
            }
        }
    }

    private fun formatSegmentLength(
        meters: Double?,
        pageUnits: Double,
    ): String = meters?.let(::formatDistanceMeters) ?: "${preciseNumber.format(pageUnits)} u"

    private fun formatDistanceValue(
        meters: Double?,
        pageUnits: Double,
    ): String = meters?.let(::formatDistanceMeters) ?: "${preciseNumber.format(pageUnits)} page units"

    private fun formatDistanceMeters(meters: Double): String {
        return if (meters >= 1000.0) {
            "${shortNumber.format(meters / 1000.0)} km"
        } else {
            "${shortNumber.format(meters)} m"
        }
    }

    private fun annotationStyle(
        pageWidth: Int,
        pageHeight: Int,
    ): AnnotationStyle {
        val scale = (max(pageWidth, pageHeight) / 900f).coerceIn(0.8f, 1.35f)
        return AnnotationStyle(
            polygonStrokeWidth = 1.25f * scale,
            pointOuterRadius = 4.1f * scale,
            pointInnerRadius = 2.2f * scale,
            pointStrokeWidth = 1.1f * scale,
            centroidRadius = 2.7f * scale,
            calibrationStrokeWidth = 1.35f * scale,
            labelTextSize = 8.2f * scale,
            smallTextSize = 7.6f * scale,
            labelPadding = 3.2f * scale,
            chipCornerRadius = 5f * scale,
        )
    }

    private fun textPaint(
        textSize: Float,
        isBold: Boolean = false,
        color: Int,
    ): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            this.color = color
            typeface = if (isBold) android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) else android.graphics.Typeface.DEFAULT
        }

    private fun escape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun withAlpha(
        color: Int,
        alpha: Int,
    ): Int = Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun formatTimestamp(epochMillis: Long): String {
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return formatter.format(Date(epochMillis))
    }

    companion object {
        private const val MAX_EXPORT_BITMAP_EDGE = 3200
        private const val MAX_EXPORT_RENDER_SCALE = 4f
        private const val DETAILS_PAGE_WIDTH = 595
        private const val DETAILS_PAGE_HEIGHT = 842

        private val shortNumber = DecimalFormat("#,##0.##")
        private val preciseNumber = DecimalFormat("#,##0.####")
    }
}
