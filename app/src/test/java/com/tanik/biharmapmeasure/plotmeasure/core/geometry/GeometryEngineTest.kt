package com.tanik.biharmapmeasure.plotmeasure.core.geometry

import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementMode
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPoint
import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPolygon
import com.tanik.biharmapmeasure.plotmeasure.model.PlotMeasureSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryEngineTest {
    @Test
    fun squarePolygonProducesExpectedAreaPerimeterAndCentroid() {
        val plot = polygon("square", listOf(point(0.0, 0.0), point(10.0, 0.0), point(10.0, 10.0), point(0.0, 10.0)))

        val result = GeometryEngine.computePlot(plot, calibration = null, settings = PlotMeasureSettings())

        assertEquals(40.0, result.perimeterPageUnits, 0.0001)
        assertEquals(100.0, result.areaPageUnits ?: 0.0, 0.0001)
        assertNotNull(result.centroid)
        assertEquals(5.0, result.centroid?.x ?: 0.0, 0.0001)
        assertEquals(5.0, result.centroid?.y ?: 0.0, 0.0001)
        assertFalse(result.isSelfIntersecting)
    }

    @Test
    fun rectanglePolygonAreaMatchesShoelaceFormula() {
        val plot = polygon("rectangle", listOf(point(0.0, 0.0), point(8.0, 0.0), point(8.0, 3.0), point(0.0, 3.0)))

        val result = GeometryEngine.computePlot(plot, calibration = null, settings = PlotMeasureSettings())

        assertEquals(22.0, result.perimeterPageUnits, 0.0001)
        assertEquals(24.0, result.areaPageUnits ?: 0.0, 0.0001)
    }

    @Test
    fun trianglePolygonAreaIsCorrect() {
        val plot = polygon("triangle", listOf(point(0.0, 0.0), point(5.0, 0.0), point(0.0, 5.0)))

        val result = GeometryEngine.computePlot(plot, calibration = null, settings = PlotMeasureSettings())

        assertEquals(12.5, result.areaPageUnits ?: 0.0, 0.0001)
    }

    @Test
    fun irregularPolygonIsMeasuredCorrectly() {
        val plot =
            polygon(
                "irregular",
                listOf(
                    point(0.0, 0.0),
                    point(4.0, 0.0),
                    point(6.0, 2.0),
                    point(3.0, 5.0),
                    point(0.0, 3.0),
                ),
            )

        val result = GeometryEngine.computePlot(plot, calibration = null, settings = PlotMeasureSettings())

        assertEquals(20.5, result.areaPageUnits ?: 0.0, 0.0001)
        assertFalse(result.isSelfIntersecting)
    }

    @Test
    fun concavePolygonIsSupported() {
        val plot =
            polygon(
                "concave",
                listOf(
                    point(0.0, 0.0),
                    point(6.0, 0.0),
                    point(6.0, 6.0),
                    point(3.0, 3.0),
                    point(0.0, 6.0),
                ),
            )

        val result = GeometryEngine.computePlot(plot, calibration = null, settings = PlotMeasureSettings())

        assertEquals(27.0, result.areaPageUnits ?: 0.0, 0.0001)
        assertFalse(result.isSelfIntersecting)
    }

    @Test
    fun selfIntersectingPolygonIsRejectedForArea() {
        val plot =
            polygon(
                "invalid",
                listOf(
                    point(0.0, 0.0),
                    point(4.0, 4.0),
                    point(0.0, 4.0),
                    point(4.0, 0.0),
                ),
            )

        val result = GeometryEngine.computePlot(plot, calibration = null, settings = PlotMeasureSettings())

        assertTrue(result.isSelfIntersecting)
        assertNull(result.areaPageUnits)
        assertNotNull(result.validationMessage)
    }

    private fun point(
        x: Double,
        y: Double,
    ): MeasurementPoint = MeasurementPoint(id = "$x-$y", x = x, y = y)

    private fun polygon(
        name: String,
        points: List<MeasurementPoint>,
    ): MeasurementPolygon =
        MeasurementPolygon(
            id = name,
            name = name,
            mode = MeasurementMode.AREA,
            points = points,
            isClosed = true,
            createdAt = 0L,
            updatedAt = 0L,
        )
}
