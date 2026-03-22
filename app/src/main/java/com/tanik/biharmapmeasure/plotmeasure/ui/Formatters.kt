package com.tanik.biharmapmeasure.plotmeasure.ui

import com.tanik.biharmapmeasure.plotmeasure.model.AreaBreakdown
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val shortNumber = DecimalFormat("#,##0.##")
private val preciseNumber = DecimalFormat("#,##0.####")

fun formatDistance(meters: Double?): String {
    meters ?: return "--"
    return if (meters >= 1000.0) {
        "${shortNumber.format(meters / 1000.0)} km"
    } else {
        "${shortNumber.format(meters)} m"
    }
}

fun formatMapUnits(value: Double?): String {
    value ?: return "--"
    return "${preciseNumber.format(value)} map units"
}

fun formatAreaBreakdown(area: AreaBreakdown?): List<Pair<String, String>> {
    area ?: return emptyList()
    return listOf(
        "Square meter" to shortNumber.format(area.squareMeters),
        "Square feet" to shortNumber.format(area.squareFeet),
        "Acre" to preciseNumber.format(area.acres),
        "Hectare" to preciseNumber.format(area.hectares),
        "Decimal" to shortNumber.format(area.decimal),
        "Bigha" to shortNumber.format(area.bigha),
        "Kattha" to shortNumber.format(area.kattha),
        "Dhur" to shortNumber.format(area.dhur),
    )
}

fun formatCoordinate(value: Double): String = preciseNumber.format(value)

fun formatTimestamp(epochMillis: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
