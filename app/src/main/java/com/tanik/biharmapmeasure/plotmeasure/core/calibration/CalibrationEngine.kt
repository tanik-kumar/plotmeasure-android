package com.tanik.biharmapmeasure.plotmeasure.core.calibration

import com.tanik.biharmapmeasure.plotmeasure.model.CalibrationMethod
import com.tanik.biharmapmeasure.plotmeasure.model.CalibrationMetadata
import com.tanik.biharmapmeasure.plotmeasure.model.CalibrationProfile
import com.tanik.biharmapmeasure.plotmeasure.model.LinearUnit
import com.tanik.biharmapmeasure.plotmeasure.model.ManualReferenceType
import java.util.UUID

object CalibrationEngine {
    private const val METERS_PER_PDF_POINT = 0.0254 / 72.0

    fun manualCalibration(
        name: String,
        pageDistance: Double,
        realDistance: Double,
        realUnit: LinearUnit,
        referenceType: ManualReferenceType,
        createdAt: Long,
    ): CalibrationProfile {
        require(name.isNotBlank()) { "Calibration name is required." }
        require(pageDistance > 0.0) { "Select two distinct calibration points on the map." }
        require(realDistance > 0.0) { "Reference distance must be greater than zero." }

        return CalibrationProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            method =
                if (referenceType == ManualReferenceType.SCALE_BAR) {
                    CalibrationMethod.SCALE_BAR
                } else {
                    CalibrationMethod.MANUAL
                },
            metersPerPageUnit = realUnit.toMeters(realDistance) / pageDistance,
            metadata =
                CalibrationMetadata(
                    knownPageDistance = pageDistance,
                    knownRealDistance = realDistance,
                    knownRealUnit = realUnit,
                    referenceType = referenceType,
                ),
            createdAt = createdAt,
        )
    }

    fun ratioCalibration(
        name: String,
        denominator: Double,
        createdAt: Long,
    ): CalibrationProfile {
        require(name.isNotBlank()) { "Calibration name is required." }
        require(denominator > 0.0) { "Scale denominator must be greater than zero." }

        return CalibrationProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            method = CalibrationMethod.RATIO,
            metersPerPageUnit = denominator * METERS_PER_PDF_POINT,
            metadata = CalibrationMetadata(ratioDenominator = denominator),
            createdAt = createdAt,
        )
    }

    fun textScaleCalibration(
        name: String,
        mapDistanceValue: Double,
        mapDistanceUnit: LinearUnit,
        groundDistanceValue: Double,
        groundDistanceUnit: LinearUnit,
        createdAt: Long,
    ): CalibrationProfile {
        require(name.isNotBlank()) { "Calibration name is required." }
        require(mapDistanceValue > 0.0) { "Map distance must be greater than zero." }
        require(groundDistanceValue > 0.0) { "Ground distance must be greater than zero." }

        val physicalMapMeters = mapDistanceUnit.toMeters(mapDistanceValue)
        val groundMeters = groundDistanceUnit.toMeters(groundDistanceValue)
        val pageUnitsForMapDistance = physicalMapMeters / METERS_PER_PDF_POINT

        return CalibrationProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            method = CalibrationMethod.TEXT_SCALE,
            metersPerPageUnit = groundMeters / pageUnitsForMapDistance,
            metadata =
                CalibrationMetadata(
                    mapDistanceValue = mapDistanceValue,
                    mapDistanceUnit = mapDistanceUnit,
                    groundDistanceValue = groundDistanceValue,
                    groundDistanceUnit = groundDistanceUnit,
                    ratioDenominator = groundMeters / physicalMapMeters,
                ),
            createdAt = createdAt,
        )
    }
}
