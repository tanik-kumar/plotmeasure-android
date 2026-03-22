package com.tanik.biharmapmeasure.plotmeasure.core.geometry

import com.tanik.biharmapmeasure.plotmeasure.model.AreaBreakdown
import com.tanik.biharmapmeasure.plotmeasure.model.CalibrationProfile
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementMode
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPoint
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPolygon
import com.tanik.biharmapmeasure.plotmeasure.model.PlotMeasureSettings
import com.tanik.biharmapmeasure.plotmeasure.model.SegmentMeasurement
import kotlin.math.abs
import kotlin.math.hypot

data class PlotComputation(
    val segments: List<SegmentMeasurement>,
    val perimeterPageUnits: Double,
    val perimeterMeters: Double? = null,
    val areaPageUnits: Double? = null,
    val area: AreaBreakdown? = null,
    val centroid: MeasurementPoint? = null,
    val isSelfIntersecting: Boolean = false,
    val validationMessage: String? = null,
)

data class SegmentInsertionCandidate(
    val insertIndex: Int,
    val distanceToSegment: Double,
)

object GeometryEngine {
    fun computePlot(
        plot: MeasurementPolygon,
        calibration: CalibrationProfile?,
        settings: PlotMeasureSettings,
    ): PlotComputation {
        val points = plot.points
        val includeClosingSegment = plot.mode == MeasurementMode.AREA && points.size >= 3
        val segments =
            buildSegments(
                points = points,
                includeClosingSegment = includeClosingSegment,
                calibration = calibration,
            )
        val perimeterPageUnits = segments.sumOf { it.lengthPageUnits }
        val perimeterMeters = calibration?.metersPerPageUnit?.let { perimeterPageUnits * it }

        if (plot.mode != MeasurementMode.AREA || points.size < 3) {
            return PlotComputation(
                segments = segments,
                perimeterPageUnits = perimeterPageUnits,
                perimeterMeters = perimeterMeters,
                validationMessage =
                    if (plot.mode == MeasurementMode.AREA && points.size < 3) {
                        "At least 3 points are required for area."
                    } else {
                        null
                    },
            )
        }

        val isSelfIntersecting = isSelfIntersecting(points)
        if (isSelfIntersecting) {
            return PlotComputation(
                segments = segments,
                perimeterPageUnits = perimeterPageUnits,
                perimeterMeters = perimeterMeters,
                isSelfIntersecting = true,
                validationMessage = "Polygon is self-intersecting. Area is blocked until the boundary is valid.",
            )
        }

        val areaPageUnits = polygonArea(points)
        val centroid = polygonCentroid(points)
        val area =
            calibration?.metersPerPageUnit?.let { metersPerPageUnit ->
                val squareMeters = areaPageUnits * metersPerPageUnit * metersPerPageUnit
                toAreaBreakdown(squareMeters, settings)
            }

        return PlotComputation(
            segments = segments,
            perimeterPageUnits = perimeterPageUnits,
            perimeterMeters = perimeterMeters,
            areaPageUnits = areaPageUnits,
            area = area,
            centroid = centroid,
            isSelfIntersecting = false,
        )
    }

    fun distance(start: MeasurementPoint, end: MeasurementPoint): Double {
        return hypot(end.x - start.x, end.y - start.y)
    }

    fun nearestPointId(
        points: List<MeasurementPoint>,
        targetX: Double,
        targetY: Double,
        maxDistance: Double,
    ): String? {
        var bestId: String? = null
        var bestDistance = maxDistance
        points.forEach { point ->
            val distance = hypot(point.x - targetX, point.y - targetY)
            if (distance <= bestDistance) {
                bestDistance = distance
                bestId = point.id
            }
        }
        return bestId
    }

    fun nearestInsertSegment(
        plot: MeasurementPolygon,
        targetX: Double,
        targetY: Double,
        threshold: Double,
    ): SegmentInsertionCandidate? {
        val points = plot.points
        if (points.size < 2) {
            return null
        }
        var bestCandidate: SegmentInsertionCandidate? = null
        val segmentCount =
            if (plot.mode == MeasurementMode.AREA && points.size >= 3) {
                points.size
            } else {
                points.lastIndex
            }
        for (index in 0 until segmentCount) {
            val start = points[index]
            val end = points[(index + 1) % points.size]
            val distance = pointToSegmentDistance(targetX, targetY, start, end)
            if (distance <= threshold) {
                val candidate = SegmentInsertionCandidate(index + 1, distance)
                if (bestCandidate == null || candidate.distanceToSegment < bestCandidate.distanceToSegment) {
                    bestCandidate = candidate
                }
            }
        }
        return bestCandidate
    }

    fun polygonArea(points: List<MeasurementPoint>): Double {
        if (points.size < 3) {
            return 0.0
        }
        var total = 0.0
        for (index in points.indices) {
            val next = points[(index + 1) % points.size]
            val current = points[index]
            total += current.x * next.y - next.x * current.y
        }
        return abs(total) / 2.0
    }

    fun polygonCentroid(points: List<MeasurementPoint>): MeasurementPoint? {
        if (points.size < 3) {
            return null
        }
        var areaAccumulator = 0.0
        var cx = 0.0
        var cy = 0.0
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            val factor = current.x * next.y - next.x * current.y
            areaAccumulator += factor
            cx += (current.x + next.x) * factor
            cy += (current.y + next.y) * factor
        }
        val signedArea = areaAccumulator / 2.0
        if (signedArea == 0.0) {
            return null
        }
        return MeasurementPoint(
            id = "centroid",
            x = cx / (6.0 * signedArea),
            y = cy / (6.0 * signedArea),
        )
    }

    fun isSelfIntersecting(points: List<MeasurementPoint>): Boolean {
        if (points.size < 4) {
            return false
        }
        for (firstIndex in points.indices) {
            val firstEndIndex = (firstIndex + 1) % points.size
            for (secondIndex in firstIndex + 1 until points.size) {
                val secondEndIndex = (secondIndex + 1) % points.size
                if (firstIndex == secondIndex ||
                    firstIndex == secondEndIndex ||
                    firstEndIndex == secondIndex ||
                    firstEndIndex == secondEndIndex
                ) {
                    continue
                }
                if (firstIndex == 0 && secondIndex == points.lastIndex) {
                    continue
                }
                if (segmentsIntersect(points[firstIndex], points[firstEndIndex], points[secondIndex], points[secondEndIndex])) {
                    return true
                }
            }
        }
        return false
    }

    private fun buildSegments(
        points: List<MeasurementPoint>,
        includeClosingSegment: Boolean,
        calibration: CalibrationProfile?,
    ): List<SegmentMeasurement> {
        if (points.size < 2) {
            return emptyList()
        }

        val segments = mutableListOf<SegmentMeasurement>()
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            val lengthPageUnits = distance(start, end)
            segments +=
                SegmentMeasurement(
                    index = segments.size + 1,
                    startPointLabel = labelForPoint(index),
                    endPointLabel = labelForPoint(index + 1),
                    lengthPageUnits = lengthPageUnits,
                    lengthMeters = calibration?.metersPerPageUnit?.let { lengthPageUnits * it },
                )
        }
        if (includeClosingSegment) {
            val start = points.last()
            val end = points.first()
            val lengthPageUnits = distance(start, end)
            segments +=
                SegmentMeasurement(
                    index = segments.size + 1,
                    startPointLabel = labelForPoint(points.lastIndex),
                    endPointLabel = labelForPoint(0),
                    lengthPageUnits = lengthPageUnits,
                    lengthMeters = calibration?.metersPerPageUnit?.let { lengthPageUnits * it },
                )
        }
        return segments
    }

    private fun toAreaBreakdown(
        squareMeters: Double,
        settings: PlotMeasureSettings,
    ): AreaBreakdown {
        val squareFeet = squareMeters / 0.09290304
        return AreaBreakdown(
            squareMeters = squareMeters,
            squareFeet = squareFeet,
            acres = squareMeters / 4_046.8564224,
            hectares = squareMeters / 10_000.0,
            decimal = squareMeters / 40.468564224,
            bigha = squareFeet / settings.squareFeetPerBigha,
            kattha = squareFeet / settings.squareFeetPerKattha,
            dhur = squareFeet / settings.squareFeetPerDhur,
        )
    }

    private fun labelForPoint(index: Int): String = "P${index + 1}"

    private fun pointToSegmentDistance(
        px: Double,
        py: Double,
        start: MeasurementPoint,
        end: MeasurementPoint,
    ): Double {
        val dx = end.x - start.x
        val dy = end.y - start.y
        if (dx == 0.0 && dy == 0.0) {
            return hypot(px - start.x, py - start.y)
        }
        val projection =
            ((px - start.x) * dx + (py - start.y) * dy) /
                (dx * dx + dy * dy)
        val clampedProjection = projection.coerceIn(0.0, 1.0)
        val closestX = start.x + dx * clampedProjection
        val closestY = start.y + dy * clampedProjection
        return hypot(px - closestX, py - closestY)
    }

    private fun segmentsIntersect(
        a1: MeasurementPoint,
        a2: MeasurementPoint,
        b1: MeasurementPoint,
        b2: MeasurementPoint,
    ): Boolean {
        val o1 = orientation(a1, a2, b1)
        val o2 = orientation(a1, a2, b2)
        val o3 = orientation(b1, b2, a1)
        val o4 = orientation(b1, b2, a2)

        if (o1 != o2 && o3 != o4) {
            return true
        }
        if (o1 == 0 && onSegment(a1, b1, a2)) return true
        if (o2 == 0 && onSegment(a1, b2, a2)) return true
        if (o3 == 0 && onSegment(b1, a1, b2)) return true
        if (o4 == 0 && onSegment(b1, a2, b2)) return true
        return false
    }

    private fun orientation(
        first: MeasurementPoint,
        second: MeasurementPoint,
        third: MeasurementPoint,
    ): Int {
        val value =
            (second.y - first.y) * (third.x - second.x) -
                (second.x - first.x) * (third.y - second.y)
        return when {
            value > 0 -> 1
            value < 0 -> -1
            else -> 0
        }
    }

    private fun onSegment(
        start: MeasurementPoint,
        point: MeasurementPoint,
        end: MeasurementPoint,
    ): Boolean {
        return point.x <= maxOf(start.x, end.x) &&
            point.x >= minOf(start.x, end.x) &&
            point.y <= maxOf(start.y, end.y) &&
            point.y >= minOf(start.y, end.y)
    }
}
