package com.tanik.biharmapmeasure.model

data class MeasurementSnapshot(
    val mode: MeasurementMode,
    val pointCount: Int,
    val pixelLength: Double = 0.0,
    val pixelArea: Double = 0.0,
    val pixelPerimeter: Double = 0.0,
)
