package com.tanik.biharmapmeasure.plotmeasure.ui.components

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tanik.biharmapmeasure.plotmeasure.core.geometry.GeometryEngine
import com.tanik.biharmapmeasure.plotmeasure.core.geometry.PlotComputation
import com.tanik.biharmapmeasure.plotmeasure.core.pdf.EdgeSnapper
import com.tanik.biharmapmeasure.plotmeasure.core.pdf.PageRect
import com.tanik.biharmapmeasure.plotmeasure.core.pdf.RenderedPdfTile
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementMode
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPoint
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPolygon
import com.tanik.biharmapmeasure.plotmeasure.model.PdfPageState
import com.tanik.biharmapmeasure.plotmeasure.model.PlotMeasureSettings
import com.tanik.biharmapmeasure.plotmeasure.ui.ViewerViewport
import com.tanik.biharmapmeasure.plotmeasure.ui.formatDistance
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PdfPlotViewer(
    modifier: Modifier = Modifier,
    baseTile: RenderedPdfTile?,
    detailTile: RenderedPdfTile?,
    pageState: PdfPageState?,
    activePlot: MeasurementPolygon?,
    plotComputation: PlotComputation?,
    currentMode: MeasurementMode,
    isEditMode: Boolean,
    isCapturingCalibration: Boolean,
    calibrationCapturePoints: List<MeasurementPoint>,
    selectedPointId: String?,
    settings: PlotMeasureSettings,
    zoomFactor: Double,
    onViewportChanged: (ViewerViewport) -> Unit,
    onAddPoint: (MeasurementPoint) -> Unit,
    onCaptureCalibrationPoint: (MeasurementPoint) -> Unit,
    onClosePolygon: () -> Unit,
    onSelectPoint: (String?) -> Unit,
    onBeginPointDrag: (String) -> Unit,
    onUpdatePointPosition: (String, MeasurementPoint) -> Unit,
    onEndPointDrag: () -> Unit,
    onInsertPoint: (Int, MeasurementPoint) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val latestBaseTile by rememberUpdatedState(baseTile)
    val latestDetailTile by rememberUpdatedState(detailTile)
    val latestPageState by rememberUpdatedState(pageState)
    val latestActivePlot by rememberUpdatedState(activePlot)
    val latestPlotComputation by rememberUpdatedState(plotComputation)
    val latestIsEditMode by rememberUpdatedState(isEditMode)
    val latestIsCapturingCalibration by rememberUpdatedState(isCapturingCalibration)
    val latestCalibrationPoints by rememberUpdatedState(calibrationCapturePoints)
    val latestSettings by rememberUpdatedState(settings)
    val density = LocalDensity.current
    val pointSelectionRadiusPx = with(density) { 28.dp.toPx() }
    val segmentInsertRadiusPx = with(density) { 24.dp.toPx() }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var baseScale by remember(baseTile?.bitmap) { mutableFloatStateOf(1f) }
    var userZoom by remember(baseTile?.bitmap) { mutableFloatStateOf(1f) }
    var offsetX by remember(baseTile?.bitmap) { mutableFloatStateOf(0f) }
    var offsetY by remember(baseTile?.bitmap) { mutableFloatStateOf(0f) }
    var draggingPointId by remember { mutableStateOf<String?>(null) }
    var pendingDragPointId by remember { mutableStateOf<String?>(null) }
    var touchDownScreen by remember { mutableStateOf<Offset?>(null) }
    var loupeAnchorScreen by remember { mutableStateOf<Offset?>(null) }
    var loupeAnchorPage by remember { mutableStateOf<MeasurementPoint?>(null) }
    var cursorPagePoint by remember { mutableStateOf<MeasurementPoint?>(null) }

    val baseImage = remember(baseTile?.bitmap) { baseTile?.bitmap?.asImageBitmap() }
    val detailImage = remember(detailTile?.bitmap) { detailTile?.bitmap?.asImageBitmap() }
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    fun currentScale(): Float = baseScale * userZoom

    fun fitToPage() {
        val tile = latestBaseTile ?: return
        if (containerSize.width == 0 || containerSize.height == 0) return
        baseScale = containerSize.width.toFloat() / tile.pageWidth.toFloat()
        userZoom = 1f
        offsetX = (containerSize.width - tile.pageWidth * currentScale()) / 2f
        val scaledHeight = tile.pageHeight * currentScale()
        offsetY =
            if (scaledHeight < containerSize.height) {
                (containerSize.height - scaledHeight) / 2f
            } else {
                0f
            }
        notifyViewport(onViewportChanged, tile, containerSize, offsetX, offsetY, currentScale(), userZoom)
    }

    fun clampOffsets() {
        val tile = latestBaseTile ?: return
        val scaledWidth = tile.pageWidth * currentScale()
        val scaledHeight = tile.pageHeight * currentScale()
        offsetX =
            if (scaledWidth <= containerSize.width) {
                (containerSize.width - scaledWidth) / 2f
            } else {
                offsetX.coerceIn(containerSize.width - scaledWidth, 0f)
            }
        offsetY =
            if (scaledHeight <= containerSize.height) {
                (containerSize.height - scaledHeight) / 2f
            } else {
                offsetY.coerceIn(containerSize.height - scaledHeight, 0f)
            }
    }

    fun pageToScreen(point: MeasurementPoint): Offset =
        Offset(
            x = point.x.toFloat() * currentScale() + offsetX,
            y = point.y.toFloat() * currentScale() + offsetY,
        )

    fun screenToPage(point: Offset): MeasurementPoint? {
        val tile = latestBaseTile ?: return null
        val scale = currentScale()
        if (scale <= 0f) return null
        val pageX = (point.x - offsetX) / scale
        val pageY = (point.y - offsetY) / scale
        if (pageX !in 0f..tile.pageWidth.toFloat() || pageY !in 0f..tile.pageHeight.toFloat()) {
            return null
        }
        return MeasurementPoint(
            id = "page-point",
            x = pageX.toDouble(),
            y = pageY.toDouble(),
        )
    }

    fun snap(point: MeasurementPoint): MeasurementPoint {
        val tile = latestDetailTile ?: latestBaseTile
        return if (latestSettings.snapToEdgeEnabled && tile?.bitmap?.isRecycled == false) {
            runCatching {
                EdgeSnapper.refinePoint(point, tile)
            }.getOrElse { point }
        } else {
            point
        }
    }

    fun nearestPointAtScreen(screenPoint: Offset): String? {
        val points = latestActivePlot?.points.orEmpty()
        if (points.isEmpty()) {
            return null
        }
        var bestPointId: String? = null
        var bestDistance = pointSelectionRadiusPx
        points.forEach { point ->
            val pointScreen = pageToScreen(point)
            val distance = (pointScreen - screenPoint).getDistance()
            if (distance <= bestDistance) {
                bestDistance = distance
                bestPointId = point.id
            }
        }
        return bestPointId
    }

    fun pointHitThreshold(): Double = pointSelectionRadiusPx.toDouble() / currentScale().coerceAtLeast(0.25f)

    LaunchedEffect(baseTile?.bitmap, containerSize) {
        if (baseTile != null && containerSize.width > 0 && containerSize.height > 0) {
            fitToPage()
        }
    }

    val context = LocalContext.current
    val dragSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }
    val scaleDetector =
        remember(context) {
            ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val tile = latestBaseTile ?: return false
                        val oldScale = currentScale()
                        val focusPageX = (detector.focusX - offsetX) / oldScale
                        val focusPageY = (detector.focusY - offsetY) / oldScale
                        userZoom = (userZoom * detector.scaleFactor).coerceIn(1f, MAX_ZOOM)
                        val newScale = currentScale()
                        offsetX = detector.focusX - focusPageX * newScale
                        offsetY = detector.focusY - focusPageY * newScale
                        clampOffsets()
                        loupeAnchorScreen = Offset(detector.focusX, detector.focusY)
                        loupeAnchorPage = MeasurementPoint("focus", focusPageX.toDouble(), focusPageY.toDouble())
                        notifyViewport(onViewportChanged, tile, containerSize, offsetX, offsetY, currentScale(), userZoom)
                        return true
                    }
                },
            )
        }

    val gestureDetector =
        remember(context) {
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean = true

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val tile = latestBaseTile ?: return false
                        val oldScale = currentScale()
                        val focusPageX = (e.x - offsetX) / oldScale
                        val focusPageY = (e.y - offsetY) / oldScale
                        userZoom =
                            if (userZoom >= MAX_ZOOM) {
                                1f
                            } else {
                                (userZoom * 2f).coerceAtMost(MAX_ZOOM)
                            }
                        if (userZoom == 1f) {
                            fitToPage()
                        } else {
                            val newScale = currentScale()
                            offsetX = e.x - focusPageX * newScale
                            offsetY = e.y - focusPageY * newScale
                            clampOffsets()
                            notifyViewport(onViewportChanged, tile, containerSize, offsetX, offsetY, currentScale(), userZoom)
                        }
                        return true
                    }

                    override fun onScroll(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        distanceX: Float,
                        distanceY: Float,
                    ): Boolean {
                        val tile = latestBaseTile ?: return false
                        if (draggingPointId != null || pendingDragPointId != null || scaleDetector.isInProgress) return false
                        offsetX -= distanceX
                        offsetY -= distanceY
                        clampOffsets()
                        notifyViewport(onViewportChanged, tile, containerSize, offsetX, offsetY, currentScale(), userZoom)
                        return true
                    }

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        val pagePoint = screenToPage(Offset(e.x, e.y)) ?: return false
                        if (latestIsCapturingCalibration) {
                            val snappedPoint = snap(pagePoint)
                            cursorPagePoint = snappedPoint
                            onCaptureCalibrationPoint(snappedPoint)
                            return true
                        }
                        if (latestIsEditMode) {
                            val selected = nearestPointAtScreen(Offset(e.x, e.y))
                            onSelectPoint(selected)
                            cursorPagePoint =
                                latestActivePlot?.points?.firstOrNull { it.id == selected }
                                    ?: pagePoint
                            return true
                        }
                        val active = latestActivePlot
                        if (active != null &&
                            active.mode == MeasurementMode.AREA &&
                            active.points.size >= 3
                        ) {
                            val first = active.points.first()
                            val distanceToFirst =
                                GeometryEngine.distance(
                                    first,
                                    pagePoint,
                                )
                            if (distanceToFirst <= pointHitThreshold()) {
                                cursorPagePoint = first
                                onClosePolygon()
                                return true
                            }
                        }
                        val snappedPoint = snap(pagePoint)
                        cursorPagePoint = snappedPoint
                        onAddPoint(snappedPoint)
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        if (!latestIsEditMode) return
                        val plot = latestActivePlot ?: return
                        val pagePoint = screenToPage(Offset(e.x, e.y)) ?: return
                        val candidate =
                            GeometryEngine.nearestInsertSegment(
                                plot = plot,
                                targetX = pagePoint.x,
                                targetY = pagePoint.y,
                                threshold = segmentInsertRadiusPx.toDouble() / currentScale().coerceAtLeast(0.25f),
                            ) ?: return
                        onInsertPoint(candidate.insertIndex, snap(pagePoint))
                    }
                },
            )
        }

    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
                .onSizeChanged { containerSize = it }
                .pointerInteropFilter { event ->
                    val handledScale = scaleDetector.onTouchEvent(event)
                    val handledGesture = gestureDetector.onTouchEvent(event)
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            val screenPoint = Offset(event.x, event.y)
                            val pagePoint = screenToPage(screenPoint)
                            touchDownScreen = screenPoint
                            loupeAnchorScreen = screenPoint
                            loupeAnchorPage = pagePoint
                            cursorPagePoint = pagePoint
                            if (latestIsEditMode && pagePoint != null) {
                                val hitId = nearestPointAtScreen(screenPoint)
                                if (hitId != null) {
                                    pendingDragPointId = hitId
                                    onSelectPoint(hitId)
                                    cursorPagePoint = latestActivePlot?.points?.firstOrNull { it.id == hitId } ?: pagePoint
                                }
                            }
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val currentScreen = Offset(event.x, event.y)
                            val pagePoint = screenToPage(currentScreen)
                            val currentTouchDown = touchDownScreen
                            loupeAnchorScreen = currentScreen
                            loupeAnchorPage = pagePoint
                            if (pagePoint != null) {
                                cursorPagePoint = pagePoint
                            }
                            if (
                                draggingPointId == null &&
                                pendingDragPointId != null &&
                                currentTouchDown != null &&
                                (currentScreen - currentTouchDown).getDistance() >= dragSlop
                            ) {
                                draggingPointId = pendingDragPointId
                                pendingDragPointId = null
                                draggingPointId?.let(onBeginPointDrag)
                            }
                            val activeDragId = draggingPointId
                            if (activeDragId != null && pagePoint != null) {
                                val snappedPoint = snap(pagePoint)
                                cursorPagePoint = snappedPoint
                                onUpdatePointPosition(activeDragId, snappedPoint)
                                return@pointerInteropFilter true
                            }
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (draggingPointId != null) {
                                draggingPointId = null
                                onEndPointDrag()
                            }
                            pendingDragPointId = null
                            touchDownScreen = null
                            loupeAnchorScreen = null
                            loupeAnchorPage = null
                        }
                    }
                    handledScale || handledGesture || draggingPointId != null || pendingDragPointId != null
                },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val tile = baseTile ?: return@Canvas
            val scale = currentScale()
            val pageTopLeft = Offset(offsetX, offsetY)
            val pageSize = Size(tile.pageWidth * scale, tile.pageHeight * scale)
            val visiblePageRect =
                PageRect(
                    left = ((0f - offsetX) / scale).coerceIn(0f, tile.pageWidth.toFloat()).toDouble(),
                    top = ((0f - offsetY) / scale).coerceIn(0f, tile.pageHeight.toFloat()).toDouble(),
                    right = ((size.width - offsetX) / scale).coerceIn(0f, tile.pageWidth.toFloat()).toDouble(),
                    bottom = ((size.height - offsetY) / scale).coerceIn(0f, tile.pageHeight.toFloat()).toDouble(),
                )

            if (baseImage != null) {
                drawTileImage(
                    image = baseImage,
                    tile = tile,
                    visiblePageRect = visiblePageRect,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                )
            }
            if (detailTile != null && detailImage != null) {
                drawTileImage(
                    image = detailImage,
                    tile = detailTile,
                    visiblePageRect = visiblePageRect,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                )
            }

            drawRect(
                color = outlineColor,
                topLeft = pageTopLeft,
                size = pageSize,
                style = Stroke(width = 2f),
            )

            val plots = latestPageState?.plots.orEmpty()
            plots.forEach { plot ->
                val isActive = plot.id == latestPageState?.selectedPlotId || (latestPageState?.selectedPlotId == null && plot == plots.lastOrNull())
                drawPlot(
                    plot = plot,
                    isActive = isActive,
                    computation = if (isActive) latestPlotComputation else null,
                    selectedPointId = selectedPointId,
                    settings = latestSettings,
                    textMeasurer = textMeasurer,
                    pageToScreen = ::pageToScreen,
                    onSurfaceColor = onSurfaceColor,
                    tertiaryColor = tertiaryColor,
                    secondaryColor = secondaryColor,
                )
            }

            drawCalibrationOverlay(
                calibrationCapturePoints = latestCalibrationPoints,
                pageToScreen = ::pageToScreen,
                textMeasurer = textMeasurer,
            )

            drawCursorOverlay(
                cursorPoint = cursorPagePoint,
                pageToScreen = ::pageToScreen,
            )
        }

        ViewerChips(
            zoomFactor = zoomFactor,
            currentMode = currentMode,
            isEditMode = isEditMode,
            isCapturingCalibration = isCapturingCalibration,
            hasCalibration = pageState?.calibration != null,
        )

        val currentLoupeScreen = loupeAnchorScreen
        val currentLoupePage = loupeAnchorPage
        if (settings.showLoupe && currentLoupeScreen != null && currentLoupePage != null && baseTile != null) {
            Loupe(
                screenAnchor = currentLoupeScreen,
                pageAnchor = currentLoupePage,
                containerSize = containerSize,
                baseTile = baseTile,
                detailTile = detailTile,
                scale = currentScale(),
            )
        }
    }
}

@Composable
private fun BoxScope.ViewerChips(
    zoomFactor: Double,
    currentMode: MeasurementMode,
    isEditMode: Boolean,
    isCapturingCalibration: Boolean,
    hasCalibration: Boolean,
) {
    Row(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
    ) {
        AssistChip(
            onClick = {},
            label = { Text("${(zoomFactor * 100).roundToInt()}%") },
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        )
        AssistChip(
            onClick = {},
            label = { Text(currentMode.name.lowercase().replaceFirstChar(Char::uppercase)) },
            modifier = Modifier.padding(start = 8.dp),
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        )
        AssistChip(
            onClick = {},
            label = {
                Text(
                    when {
                        isCapturingCalibration -> "Calibrating"
                        hasCalibration -> "Calibrated"
                        else -> "Uncalibrated"
                    },
                )
            },
            modifier = Modifier.padding(start = 8.dp),
            colors =
                AssistChipDefaults.assistChipColors(
                    containerColor =
                        if (hasCalibration) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                        },
                ),
        )
        if (isEditMode) {
            AssistChip(
                onClick = {},
                label = { Text("Edit") },
                modifier = Modifier.padding(start = 8.dp),
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f)),
            )
        }
    }
}

@Composable
private fun BoxScope.Loupe(
    screenAnchor: Offset,
    pageAnchor: MeasurementPoint,
    containerSize: IntSize,
    baseTile: RenderedPdfTile,
    detailTile: RenderedPdfTile?,
    scale: Float,
) {
    val baseImage = remember(baseTile.bitmap) { baseTile.bitmap.asImageBitmap() }
    val detailImage = remember(detailTile?.bitmap) { detailTile?.bitmap?.asImageBitmap() }
    val loupeSize = 148.dp
    val density = LocalDensity.current
    val pixelSize = with(density) { loupeSize.toPx() }
    val offsetAfterFinger = with(density) { 28.dp.toPx() }
    val offsetAboveFinger = with(density) { 18.dp.toPx() }
    val desiredX = (screenAnchor.x + offsetAfterFinger).roundToInt()
    val desiredY = (screenAnchor.y - pixelSize - offsetAboveFinger).roundToInt()
    val clampedX = desiredX.coerceIn(12, (containerSize.width - pixelSize.roundToInt() - 12).coerceAtLeast(12))
    val clampedY = desiredY.coerceIn(12, (containerSize.height - pixelSize.roundToInt() - 12).coerceAtLeast(12))
    val loupeSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val loupeOutline = MaterialTheme.colorScheme.outline
    val loupePrimary = MaterialTheme.colorScheme.primary

    Canvas(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(clampedX, clampedY) }
                .size(loupeSize)
                .clip(CircleShape)
                .background(loupeSurface)
                .border(2.dp, loupeOutline, CircleShape),
    ) {
        val zoomScale = scale * 2.5f
        val center = Offset(size.width / 2f, size.height / 2f)
        val pageTopLeft =
            Offset(
                x = center.x - pageAnchor.x.toFloat() * zoomScale,
                y = center.y - pageAnchor.y.toFloat() * zoomScale,
            )
        if (baseImage.width > 0 && baseImage.height > 0) {
            drawImage(
                image = baseImage,
                dstOffset = IntOffset(pageTopLeft.x.roundToInt(), pageTopLeft.y.roundToInt()),
                dstSize = IntSize((baseTile.pageWidth * zoomScale).roundToInt(), (baseTile.pageHeight * zoomScale).roundToInt()),
            )
        }
        if (detailTile != null && detailImage != null) {
            val region = detailTile.region
            val absoluteTopLeft =
                Offset(
                    x = pageTopLeft.x + region.left.toFloat() * zoomScale,
                    y = pageTopLeft.y + region.top.toFloat() * zoomScale,
                )
            drawImage(
                image = detailImage,
                dstOffset = IntOffset(absoluteTopLeft.x.roundToInt(), absoluteTopLeft.y.roundToInt()),
                dstSize = IntSize((region.width.toFloat() * zoomScale).roundToInt(), (region.height.toFloat() * zoomScale).roundToInt()),
            )
        }
        drawCircle(
            color = loupePrimary.copy(alpha = 0.18f),
            radius = 10.dp.toPx(),
            center = center,
        )
        drawLine(
            color = loupePrimary,
            start = Offset(center.x - 14.dp.toPx(), center.y),
            end = Offset(center.x + 14.dp.toPx(), center.y),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = loupePrimary,
            start = Offset(center.x, center.y - 14.dp.toPx()),
            end = Offset(center.x, center.y + 14.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlot(
    plot: MeasurementPolygon,
    isActive: Boolean,
    computation: PlotComputation?,
    selectedPointId: String?,
    settings: PlotMeasureSettings,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    pageToScreen: (MeasurementPoint) -> Offset,
    onSurfaceColor: Color,
    tertiaryColor: Color,
    secondaryColor: Color,
) {
    val strokeColor = Color(plot.strokeColorArgb)
    val fillColor = Color(plot.fillColorArgb)
    val screenPoints = plot.points.map(pageToScreen)
    if (screenPoints.size >= 2) {
        val path = Path().apply {
            moveTo(screenPoints.first().x, screenPoints.first().y)
            screenPoints.drop(1).forEach { lineTo(it.x, it.y) }
            if (plot.mode == MeasurementMode.AREA && screenPoints.size >= 3) {
                close()
            }
        }
        if (settings.layerVisibility.showPolygonFill && plot.mode == MeasurementMode.AREA && screenPoints.size >= 3) {
            drawPath(
                path = path,
                color = if (isActive) fillColor else fillColor.copy(alpha = 0.3f),
            )
        }
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = if (isActive) 5.dp.toPx() else 3.dp.toPx()),
        )
    }

    if (isActive && settings.layerVisibility.showSegments) {
        computation?.segments?.forEachIndexed { index, segment ->
            val start = plot.points.getOrNull(index) ?: return@forEachIndexed
            val end =
                plot.points.getOrNull(index + 1)
                    ?: if (plot.mode == MeasurementMode.AREA) {
                        plot.points.firstOrNull()
                    } else {
                        null
                    }
                    ?: return@forEachIndexed
            val midPoint =
                MeasurementPoint(
                    id = "mid-$index",
                    x = (start.x + end.x) / 2.0,
                    y = (start.y + end.y) / 2.0,
                )
            val screenMid = pageToScreen(midPoint)
            drawMeasuredLabel(
                textMeasurer = textMeasurer,
                text =
                    formatDistance(segment.lengthMeters).takeIf { segment.lengthMeters != null }
                        ?: "${segment.lengthPageUnits.roundToInt()} u",
                proposedTopLeft = Offset(screenMid.x + 6.dp.toPx(), screenMid.y + 4.dp.toPx()),
                style = TextStyle(color = onSurfaceColor),
            )
        }
    }

    if (settings.layerVisibility.showPoints) {
        screenPoints.forEachIndexed { index, point ->
            val isSelected = plot.points[index].id == selectedPointId
            val outerRadius = if (isSelected) 18.dp.toPx() else 11.dp.toPx()
            val innerRadius = if (isSelected) 9.dp.toPx() else 7.dp.toPx()
            if (isSelected) {
                drawCircle(
                    color = tertiaryColor.copy(alpha = 0.18f),
                    radius = 24.dp.toPx(),
                    center = point,
                )
            }
            drawCircle(
                color = Color.White,
                radius = outerRadius,
                center = point,
            )
            drawCircle(
                color = if (isSelected) tertiaryColor else strokeColor,
                radius = outerRadius,
                center = point,
                style = Stroke(width = if (isSelected) 3.dp.toPx() else 2.dp.toPx()),
            )
            drawCircle(
                color = if (isSelected) tertiaryColor else strokeColor,
                radius = innerRadius,
                center = point,
            )
            if (settings.layerVisibility.showLabels) {
                drawMeasuredLabel(
                    textMeasurer = textMeasurer,
                    text = "P${index + 1}",
                    proposedTopLeft = Offset(point.x + 10.dp.toPx(), point.y - 18.dp.toPx()),
                    style = TextStyle(color = onSurfaceColor),
                )
            }
        }
    }

    val centroidPoint = computation?.centroid
    if (isActive && settings.layerVisibility.showCentroid && centroidPoint != null) {
        val centroid = pageToScreen(centroidPoint)
        drawCircle(
            color = secondaryColor,
            radius = 7.dp.toPx(),
            center = centroid,
        )
        drawMeasuredLabel(
            textMeasurer = textMeasurer,
            text = "C",
            proposedTopLeft = Offset(centroid.x + 8.dp.toPx(), centroid.y - 16.dp.toPx()),
            style = TextStyle(color = secondaryColor),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCalibrationOverlay(
    calibrationCapturePoints: List<MeasurementPoint>,
    pageToScreen: (MeasurementPoint) -> Offset,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    if (calibrationCapturePoints.isEmpty()) return
    val points = calibrationCapturePoints.map(pageToScreen)
    if (points.size == 2) {
        drawLine(
            color = Color(0xFFB55836),
            start = points[0],
            end = points[1],
            strokeWidth = 4.dp.toPx(),
        )
    }
    points.forEachIndexed { index, point ->
        drawCircle(color = Color(0xFFB55836), radius = 8.dp.toPx(), center = point)
        drawMeasuredLabel(
            textMeasurer = textMeasurer,
            text = "C${index + 1}",
            proposedTopLeft = Offset(point.x + 8.dp.toPx(), point.y - 18.dp.toPx()),
            style = TextStyle(color = Color.White),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCursorOverlay(
    cursorPoint: MeasurementPoint?,
    pageToScreen: (MeasurementPoint) -> Offset,
) {
    val point = cursorPoint ?: return
    val screenPoint = pageToScreen(point)
    val cursorColor = Color(0xFFB55836)
    drawCircle(
        color = cursorColor.copy(alpha = 0.14f),
        radius = 22.dp.toPx(),
        center = screenPoint,
    )
    drawCircle(
        color = cursorColor,
        radius = 11.dp.toPx(),
        center = screenPoint,
        style = Stroke(width = 2.dp.toPx()),
    )
    drawLine(
        color = cursorColor,
        start = Offset(screenPoint.x - 18.dp.toPx(), screenPoint.y),
        end = Offset(screenPoint.x + 18.dp.toPx(), screenPoint.y),
        strokeWidth = 2.dp.toPx(),
    )
    drawLine(
        color = cursorColor,
        start = Offset(screenPoint.x, screenPoint.y - 18.dp.toPx()),
        end = Offset(screenPoint.x, screenPoint.y + 18.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
    )
}

private fun notifyViewport(
    onViewportChanged: (ViewerViewport) -> Unit,
    tile: RenderedPdfTile,
    containerSize: IntSize,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    zoomFactor: Float,
) {
    if (scale <= 0f || containerSize.width == 0 || containerSize.height == 0) return
    val left = ((0f - offsetX) / scale).coerceIn(0f, tile.pageWidth.toFloat())
    val top = ((0f - offsetY) / scale).coerceIn(0f, tile.pageHeight.toFloat())
    val right = ((containerSize.width - offsetX) / scale).coerceIn(0f, tile.pageWidth.toFloat())
    val bottom = ((containerSize.height - offsetY) / scale).coerceIn(0f, tile.pageHeight.toFloat())
    onViewportChanged(
        ViewerViewport(
            visiblePageRect = PageRect(left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble()),
            screenPixelsPerPageUnit = scale.toDouble(),
            zoomFactor = zoomFactor.toDouble(),
            baseDensity = tile.minDensity,
        ),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMeasuredLabel(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    proposedTopLeft: Offset,
    style: TextStyle,
) {
    val layoutResult =
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = style,
        )
    val labelWidth = layoutResult.size.width.toFloat()
    val labelHeight = layoutResult.size.height.toFloat()
    if (labelWidth <= 0f || labelHeight <= 0f) {
        return
    }

    val completelyOutside =
        proposedTopLeft.x >= size.width ||
            proposedTopLeft.y >= size.height ||
            proposedTopLeft.x + labelWidth <= 0f ||
            proposedTopLeft.y + labelHeight <= 0f
    if (completelyOutside) {
        return
    }

    val maxX = (size.width - labelWidth).coerceAtLeast(0f)
    val maxY = (size.height - labelHeight).coerceAtLeast(0f)
    val safeTopLeft =
        Offset(
            x = proposedTopLeft.x.coerceIn(0f, maxX),
            y = proposedTopLeft.y.coerceIn(0f, maxY),
        )
    drawText(
        textLayoutResult = layoutResult,
        topLeft = safeTopLeft,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTileImage(
    image: androidx.compose.ui.graphics.ImageBitmap,
    tile: RenderedPdfTile,
    visiblePageRect: PageRect,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
) {
    if (image.width <= 0 || image.height <= 0) {
        return
    }
    val intersection =
        PageRect(
            left = maxOf(tile.region.left, visiblePageRect.left),
            top = maxOf(tile.region.top, visiblePageRect.top),
            right = minOf(tile.region.right, visiblePageRect.right),
            bottom = minOf(tile.region.bottom, visiblePageRect.bottom),
        )
    if (intersection.width <= 0.0 || intersection.height <= 0.0) {
        return
    }

    val tileRegionWidth = tile.region.width
    val tileRegionHeight = tile.region.height
    if (tileRegionWidth <= 0.0 || tileRegionHeight <= 0.0) {
        return
    }

    val srcLeft =
        ((intersection.left - tile.region.left) / tileRegionWidth * image.width)
            .roundToInt()
            .coerceIn(0, image.width - 1)
    val srcTop =
        ((intersection.top - tile.region.top) / tileRegionHeight * image.height)
            .roundToInt()
            .coerceIn(0, image.height - 1)
    val srcRight =
        ((intersection.right - tile.region.left) / tileRegionWidth * image.width)
            .roundToInt()
            .coerceIn(srcLeft + 1, image.width)
    val srcBottom =
        ((intersection.bottom - tile.region.top) / tileRegionHeight * image.height)
            .roundToInt()
            .coerceIn(srcTop + 1, image.height)

    val dstLeft = (offsetX + intersection.left.toFloat() * scale).roundToInt()
    val dstTop = (offsetY + intersection.top.toFloat() * scale).roundToInt()
    val dstWidth = (intersection.width.toFloat() * scale).roundToInt().coerceAtLeast(1)
    val dstHeight = (intersection.height.toFloat() * scale).roundToInt().coerceAtLeast(1)

    drawImage(
        image = image,
        srcOffset = IntOffset(srcLeft, srcTop),
        srcSize = IntSize(srcRight - srcLeft, srcBottom - srcTop),
        dstOffset = IntOffset(dstLeft, dstTop),
        dstSize = IntSize(dstWidth, dstHeight),
    )
}

private const val MAX_ZOOM = 64f
