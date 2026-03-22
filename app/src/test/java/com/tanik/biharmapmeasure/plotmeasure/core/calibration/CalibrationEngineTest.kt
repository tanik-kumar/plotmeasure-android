package com.tanik.biharmapmeasure.plotmeasure.core.calibration

import com.tanik.biharmapmeasure.plotmeasure.model.LinearUnit
import com.tanik.biharmapmeasure.plotmeasure.model.ManualReferenceType
import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationEngineTest {
    @Test
    fun manualCalibrationComputesMetersPerPageUnit() {
        val calibration =
            CalibrationEngine.manualCalibration(
                name = "manual",
                pageDistance = 100.0,
                realDistance = 50.0,
                realUnit = LinearUnit.METER,
                referenceType = ManualReferenceType.KNOWN_LINE,
                createdAt = 0L,
            )

        assertEquals(0.5, calibration.metersPerPageUnit, 0.000001)
    }

    @Test
    fun ratioCalibrationUsesPdfPointAsMapUnit() {
        val calibration =
            CalibrationEngine.ratioCalibration(
                name = "1:4000",
                denominator = 4000.0,
                createdAt = 0L,
            )

        assertEquals(1.411111111, calibration.metersPerPageUnit, 0.000001)
    }

    @Test
    fun textScaleCalibrationMatchesEquivalentRatio() {
        val calibration =
            CalibrationEngine.textScaleCalibration(
                name = "16 inches = 1 mile",
                mapDistanceValue = 16.0,
                mapDistanceUnit = LinearUnit.INCH,
                groundDistanceValue = 1.0,
                groundDistanceUnit = LinearUnit.MILE,
                createdAt = 0L,
            )

        assertEquals(1.397, calibration.metersPerPageUnit, 0.001)
    }
}
