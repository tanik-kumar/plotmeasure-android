package com.tanik.biharmapmeasure.model

enum class DistanceUnit(
    val displayName: String,
    val shortLabel: String,
    val metersPerUnit: Double,
) {
    METER("Meter (m)", "m", 1.0),
    KILOMETER("Kilometer (km)", "km", 1000.0),
    FOOT("Foot (ft)", "ft", 0.3048),
    CHAIN("Chain", "chain", 20.1168),
    LINK("Link", "link", 0.201168),
    ;

    fun toMeters(value: Double): Double = value * metersPerUnit

    companion object {
        fun fromDisplayName(value: String?): DistanceUnit {
            return entries.firstOrNull { it.displayName == value } ?: METER
        }

        fun fromName(value: String?): DistanceUnit {
            return entries.firstOrNull { it.name == value } ?: METER
        }
    }
}
