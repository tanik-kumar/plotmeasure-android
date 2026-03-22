package com.tanik.biharmapmeasure.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.tanik.biharmapmeasure.R
import com.tanik.biharmapmeasure.model.MeasurementMode
import com.tanik.biharmapmeasure.model.MeasurementSnapshot
import com.tanik.biharmapmeasure.pdf.RenderedPdfRegion
import kotlin.math.abs
import kotlin.math.hypot

class MapMeasureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class ViewportState(
        val visiblePageRect: RectF,
        val screenPixelsPerPageUnit: Float,
        val zoomFactor: Float,
        val baseRenderDensity: Float,
    )

    interface Listener {
        fun onSelectionChanged(snapshot: MeasurementSnapshot)
        fun onCalibrationReady(pageDistance: Double)
        fun onViewportChanged(state: ViewportState)
    }

    var listener: Listener? = null

    private var pageRegion: RenderedPdfRegion? = null
    private var detailRegion: RenderedPdfRegion? = null
    private var measurementMode: MeasurementMode = MeasurementMode.DISTANCE

    private val measurementPoints = mutableListOf<PointF>()
    private val calibrationPoints = mutableListOf<PointF>()

    private var baseScale = 1f
    private var zoomFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var fitPending = true

    private val bitmapPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
    private val pointFillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.terracotta_600)
            style = Paint.Style.FILL
        }
    private val pointStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 28f
            isFakeBoldText = true
        }
    private val measurementLinePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.forest_600)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 6f
        }
    private val calibrationLinePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.terracotta_600)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 6f
        }
    private val polygonFillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.terracotta_200)
            style = Paint.Style.FILL
            alpha = 170
        }
    private val imageFramePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.slate_300)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    performClick()
                    return addPointAt(e.x, e.y)
                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float,
                ): Boolean {
                    if (pageRegion == null || scaleDetector.isInProgress) {
                        return false
                    }
                    offsetX -= distanceX
                    offsetY -= distanceY
                    clampOffsets()
                    notifyViewportChanged()
                    invalidate()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val targetZoomFactor =
                        if (zoomFactor >= MAX_ZOOM_FACTOR) {
                            1f
                        } else {
                            (zoomFactor * 2f).coerceAtMost(MAX_ZOOM_FACTOR)
                        }
                    if (targetZoomFactor == 1f) {
                        fitToScreen()
                    } else {
                        zoomTo(e.x, e.y, targetZoomFactor)
                    }
                    return true
                }
            },
        )

    private val scaleDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val currentState = currentViewportState() ?: return false
                    val oldScale = currentState.screenPixelsPerPageUnit
                    if (oldScale <= 0f) {
                        return false
                    }
                    val focusPageX = (detector.focusX - offsetX) / oldScale
                    val focusPageY = (detector.focusY - offsetY) / oldScale
                    zoomFactor = (zoomFactor * detector.scaleFactor).coerceIn(1f, MAX_ZOOM_FACTOR)
                    val newScale = currentScale()
                    offsetX = detector.focusX - focusPageX * newScale
                    offsetY = detector.focusY - focusPageY * newScale
                    clampOffsets()
                    notifyViewportChanged()
                    invalidate()
                    return true
                }
            },
        )

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handledScale = scaleDetector.onTouchEvent(event)
        val handledGesture = gestureDetector.onTouchEvent(event)
        return handledScale || handledGesture || super.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (fitPending) {
            fitToScreen()
        } else {
            clampOffsets()
            notifyViewportChanged()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val basePage = pageRegion ?: return
        val scale = currentScale()
        val pageRect =
            RectF(
                offsetX,
                offsetY,
                offsetX + basePage.pageWidth * scale,
                offsetY + basePage.pageHeight * scale,
            )

        canvas.drawBitmap(basePage.bitmap, null, pageRect, bitmapPaint)

        detailRegion?.let { tile ->
            val tileRect =
                RectF(
                    offsetX + tile.regionLeft * scale,
                    offsetY + tile.regionTop * scale,
                    offsetX + (tile.regionLeft + tile.regionWidth) * scale,
                    offsetY + (tile.regionTop + tile.regionHeight) * scale,
                )
            canvas.drawBitmap(tile.bitmap, null, tileRect, bitmapPaint)
        }

        canvas.drawRect(pageRect, imageFramePaint)

        val activePoints = currentPoints()
        if (activePoints.isEmpty()) {
            return
        }

        val screenPoints = activePoints.map { toScreenPoint(it) }
        val linePaint =
            if (measurementMode == MeasurementMode.CALIBRATION) {
                calibrationLinePaint
            } else {
                measurementLinePaint
            }

        if (measurementMode == MeasurementMode.AREA && screenPoints.size >= 3) {
            val path =
                Path().apply {
                    moveTo(screenPoints.first().x, screenPoints.first().y)
                    for (index in 1 until screenPoints.size) {
                        lineTo(screenPoints[index].x, screenPoints[index].y)
                    }
                    close()
                }
            canvas.drawPath(path, polygonFillPaint)
        }

        if (screenPoints.size >= 2) {
            for (index in 0 until screenPoints.lastIndex) {
                canvas.drawLine(
                    screenPoints[index].x,
                    screenPoints[index].y,
                    screenPoints[index + 1].x,
                    screenPoints[index + 1].y,
                    linePaint,
                )
            }
            if (measurementMode == MeasurementMode.AREA && screenPoints.size >= 3) {
                canvas.drawLine(
                    screenPoints.last().x,
                    screenPoints.last().y,
                    screenPoints.first().x,
                    screenPoints.first().y,
                    linePaint,
                )
            }
        }

        val radius = 14f
        screenPoints.forEachIndexed { index, point ->
            canvas.drawCircle(point.x, point.y, radius, pointFillPaint)
            canvas.drawCircle(point.x, point.y, radius, pointStrokePaint)
            canvas.drawText((index + 1).toString(), point.x, point.y + 10f, labelPaint)
        }
    }

    fun setPage(newPage: RenderedPdfRegion?) {
        if (pageRegion !== newPage) {
            recycleRegion(pageRegion)
        }
        pageRegion = newPage
        recycleRegion(detailRegion)
        detailRegion = null
        measurementPoints.clear()
        calibrationPoints.clear()
        fitPending = true
        if (newPage != null && width > 0 && height > 0) {
            fitToScreen()
        } else {
            notifyViewportChanged()
            invalidate()
        }
        notifySelectionChanged()
    }

    fun setDetailRegion(newDetailRegion: RenderedPdfRegion?) {
        if (detailRegion === newDetailRegion) {
            return
        }
        recycleRegion(detailRegion)
        detailRegion = newDetailRegion
        invalidate()
    }

    fun clearDetailRegion() {
        setDetailRegion(null)
    }

    fun currentDetailRegion(): RenderedPdfRegion? = detailRegion

    fun setMeasurementMode(mode: MeasurementMode) {
        if (measurementMode == mode) {
            return
        }
        measurementMode = mode
        measurementPoints.clear()
        calibrationPoints.clear()
        notifySelectionChanged()
        invalidate()
    }

    fun snapshot(): MeasurementSnapshot {
        val activePoints = currentPoints()
        return when (measurementMode) {
            MeasurementMode.CALIBRATION -> {
                MeasurementSnapshot(
                    mode = measurementMode,
                    pointCount = activePoints.size,
                    pixelLength =
                        if (activePoints.size == 2) {
                            distanceBetween(activePoints[0], activePoints[1])
                        } else {
                            0.0
                        },
                )
            }

            MeasurementMode.DISTANCE -> {
                MeasurementSnapshot(
                    mode = measurementMode,
                    pointCount = activePoints.size,
                    pixelLength =
                        if (activePoints.size == 2) {
                            distanceBetween(activePoints[0], activePoints[1])
                        } else {
                            0.0
                        },
                )
            }

            MeasurementMode.PATH -> {
                MeasurementSnapshot(
                    mode = measurementMode,
                    pointCount = activePoints.size,
                    pixelLength = polylineLength(activePoints),
                )
            }

            MeasurementMode.AREA -> {
                MeasurementSnapshot(
                    mode = measurementMode,
                    pointCount = activePoints.size,
                    pixelLength = polylineLength(activePoints),
                    pixelArea =
                        if (activePoints.size >= 3) {
                            polygonArea(activePoints)
                        } else {
                            0.0
                        },
                    pixelPerimeter =
                        if (activePoints.size >= 3) {
                            polygonPerimeter(activePoints)
                        } else {
                            0.0
                        },
                )
            }
        }
    }

    fun undoLastPoint() {
        val activePoints = mutablePoints()
        if (activePoints.isNotEmpty()) {
            activePoints.removeLast()
            notifySelectionChanged()
            invalidate()
        }
    }

    fun clearPoints() {
        measurementPoints.clear()
        calibrationPoints.clear()
        notifySelectionChanged()
        invalidate()
    }

    fun fitToScreen() {
        val basePage = pageRegion ?: return
        if (width == 0 || height == 0) {
            fitPending = true
            return
        }
        baseScale = width.toFloat() / basePage.pageWidth.toFloat()
        zoomFactor = 1f
        offsetX = (width - basePage.pageWidth * currentScale()) / 2f
        val scaledHeight = basePage.pageHeight * currentScale()
        offsetY =
            if (scaledHeight <= height) {
                (height - scaledHeight) / 2f
            } else {
                0f
            }
        fitPending = false
        notifyViewportChanged()
        invalidate()
    }

    fun hasMap(): Boolean = pageRegion != null

    fun currentViewportState(): ViewportState? {
        val basePage = pageRegion ?: return null
        val scale = currentScale()
        if (scale <= 0f || width == 0 || height == 0) {
            return null
        }
        val visibleLeft = ((0f - offsetX) / scale).coerceIn(0f, basePage.pageWidth.toFloat())
        val visibleTop = ((0f - offsetY) / scale).coerceIn(0f, basePage.pageHeight.toFloat())
        val visibleRight = ((width - offsetX) / scale).coerceIn(0f, basePage.pageWidth.toFloat())
        val visibleBottom = ((height - offsetY) / scale).coerceIn(0f, basePage.pageHeight.toFloat())
        return ViewportState(
            visiblePageRect = RectF(visibleLeft, visibleTop, visibleRight, visibleBottom),
            screenPixelsPerPageUnit = scale,
            zoomFactor = zoomFactor,
            baseRenderDensity = basePage.minDensity,
        )
    }

    private fun addPointAt(screenX: Float, screenY: Float): Boolean {
        val pagePoint = toPagePoint(screenX, screenY) ?: return false
        val activePoints = mutablePoints()
        val maxPoints =
            when (measurementMode) {
                MeasurementMode.DISTANCE,
                MeasurementMode.CALIBRATION,
                -> 2

                MeasurementMode.PATH,
                MeasurementMode.AREA,
                -> Int.MAX_VALUE
            }
        if (activePoints.size >= maxPoints) {
            activePoints.clear()
        }
        activePoints += pagePoint
        notifySelectionChanged()
        invalidate()
        if (measurementMode == MeasurementMode.CALIBRATION && activePoints.size == 2) {
            listener?.onCalibrationReady(distanceBetween(activePoints[0], activePoints[1]))
        }
        return true
    }

    private fun mutablePoints(): MutableList<PointF> {
        return if (measurementMode == MeasurementMode.CALIBRATION) calibrationPoints else measurementPoints
    }

    private fun currentPoints(): List<PointF> = mutablePoints()

    private fun currentScale(): Float = baseScale * zoomFactor

    private fun toPagePoint(screenX: Float, screenY: Float): PointF? {
        val basePage = pageRegion ?: return null
        val scale = currentScale()
        if (scale <= 0f) {
            return null
        }
        val pageX = (screenX - offsetX) / scale
        val pageY = (screenY - offsetY) / scale
        if (pageX !in 0f..basePage.pageWidth.toFloat() || pageY !in 0f..basePage.pageHeight.toFloat()) {
            return null
        }
        return PointF(pageX, pageY)
    }

    private fun toScreenPoint(pagePoint: PointF): PointF {
        val scale = currentScale()
        return PointF(
            pagePoint.x * scale + offsetX,
            pagePoint.y * scale + offsetY,
        )
    }

    private fun notifySelectionChanged() {
        listener?.onSelectionChanged(snapshot())
    }

    private fun notifyViewportChanged() {
        val state = currentViewportState() ?: return
        listener?.onViewportChanged(state)
    }

    private fun clampOffsets() {
        val basePage = pageRegion ?: return
        val scale = currentScale()
        val scaledWidth = basePage.pageWidth * scale
        val scaledHeight = basePage.pageHeight * scale

        offsetX =
            if (scaledWidth <= width) {
                (width - scaledWidth) / 2f
            } else {
                offsetX.coerceIn(width - scaledWidth, 0f)
            }
        offsetY =
            if (scaledHeight <= height) {
                (height - scaledHeight) / 2f
            } else {
                offsetY.coerceIn(height - scaledHeight, 0f)
            }
    }

    private fun zoomTo(
        focusX: Float,
        focusY: Float,
        targetZoomFactor: Float,
    ) {
        val oldScale = currentScale()
        if (oldScale <= 0f) {
            return
        }
        val focusPageX = (focusX - offsetX) / oldScale
        val focusPageY = (focusY - offsetY) / oldScale
        zoomFactor = targetZoomFactor.coerceIn(1f, MAX_ZOOM_FACTOR)
        val newScale = currentScale()
        offsetX = focusX - focusPageX * newScale
        offsetY = focusY - focusPageY * newScale
        clampOffsets()
        notifyViewportChanged()
        invalidate()
    }

    private fun recycleRegion(region: RenderedPdfRegion?) {
        val bitmap = region?.bitmap ?: return
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun distanceBetween(start: PointF, end: PointF): Double {
        return hypot(
            (end.x - start.x).toDouble(),
            (end.y - start.y).toDouble(),
        )
    }

    private fun polylineLength(points: List<PointF>): Double {
        if (points.size < 2) {
            return 0.0
        }
        var total = 0.0
        for (index in 0 until points.lastIndex) {
            total += distanceBetween(points[index], points[index + 1])
        }
        return total
    }

    private fun polygonPerimeter(points: List<PointF>): Double {
        if (points.size < 3) {
            return 0.0
        }
        var total = 0.0
        for (index in points.indices) {
            val nextIndex = (index + 1) % points.size
            total += distanceBetween(points[index], points[nextIndex])
        }
        return total
    }

    private fun polygonArea(points: List<PointF>): Double {
        if (points.size < 3) {
            return 0.0
        }
        var total = 0.0
        for (index in points.indices) {
            val nextIndex = (index + 1) % points.size
            total +=
                (points[index].x * points[nextIndex].y - points[nextIndex].x * points[index].y)
                    .toDouble()
        }
        return abs(total) / 2.0
    }

    private companion object {
        const val MAX_ZOOM_FACTOR = 64f
    }
}
