package com.tanik.biharmapmeasure.data

import android.content.Context
import android.net.Uri
import com.tanik.biharmapmeasure.model.DistanceUnit
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

data class CalibrationRecord(
    val metersPerPageUnit: Double,
    val referenceDistance: Double,
    val unit: DistanceUnit,
    val pageUnitDistance: Double,
)

class CalibrationStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCalibration(uri: Uri, pageIndex: Int, record: CalibrationRecord) {
        val prefix = calibrationPrefix(uri, pageIndex)
        prefs.edit()
            .putString("${prefix}_meters_per_page_unit", record.metersPerPageUnit.toString())
            .putString("${prefix}_reference_distance", record.referenceDistance.toString())
            .putString("${prefix}_page_unit_distance", record.pageUnitDistance.toString())
            .putString("${prefix}_unit", record.unit.name)
            .apply()
    }

    fun loadCalibration(
        uri: Uri,
        pageIndex: Int,
        pageWidth: Int,
        pageHeight: Int,
    ): CalibrationRecord? {
        val prefix = calibrationPrefix(uri, pageIndex)
        val metersPerPageUnit =
            prefs.getString("${prefix}_meters_per_page_unit", null)?.toDoubleOrNull()
        val referenceDistance = prefs.getString("${prefix}_reference_distance", null)?.toDoubleOrNull()
        val pageUnitDistance = prefs.getString("${prefix}_page_unit_distance", null)?.toDoubleOrNull()
        val unit = DistanceUnit.fromName(prefs.getString("${prefix}_unit", null))
        if (
            metersPerPageUnit != null &&
            referenceDistance != null &&
            pageUnitDistance != null
        ) {
            return CalibrationRecord(
                metersPerPageUnit = metersPerPageUnit,
                referenceDistance = referenceDistance,
                unit = unit,
                pageUnitDistance = pageUnitDistance,
            )
        }

        val legacyMetersPerPixel =
            prefs.getString("${prefix}_meters_per_pixel", null)?.toDoubleOrNull()
        val legacyPixelDistance =
            prefs.getString("${prefix}_pixel_distance", null)?.toDoubleOrNull()
        if (
            legacyMetersPerPixel == null ||
            referenceDistance == null ||
            legacyPixelDistance == null
        ) {
            return null
        }

        val legacyScale =
            LEGACY_RENDER_MAX_DIMENSION.toFloat() / max(pageWidth, pageHeight).toFloat()
        val legacyRenderWidth = (pageWidth * legacyScale).roundToInt().coerceAtLeast(1)
        val pixelsPerPageUnit = legacyRenderWidth.toDouble() / pageWidth.toDouble()

        return CalibrationRecord(
            metersPerPageUnit = legacyMetersPerPixel * pixelsPerPageUnit,
            referenceDistance = referenceDistance,
            unit = unit,
            pageUnitDistance = legacyPixelDistance / pixelsPerPageUnit,
        )
    }

    fun saveLastDocument(uri: Uri?, pageIndex: Int) {
        prefs.edit()
            .putString(KEY_LAST_URI, uri?.toString())
            .putInt(KEY_LAST_PAGE, pageIndex)
            .apply()
    }

    fun loadLastDocumentUri(): Uri? {
        val value = prefs.getString(KEY_LAST_URI, null) ?: return null
        return Uri.parse(value)
    }

    fun loadLastPageIndex(): Int = prefs.getInt(KEY_LAST_PAGE, 0)

    fun clearLastDocument() {
        prefs.edit()
            .remove(KEY_LAST_URI)
            .remove(KEY_LAST_PAGE)
            .apply()
    }

    private fun calibrationPrefix(uri: Uri, pageIndex: Int): String {
        val source = "${uri}|$pageIndex"
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        val hex = digest.joinToString(separator = "") { "%02x".format(it) }
        return "calibration_$hex"
    }

    private companion object {
        const val LEGACY_RENDER_MAX_DIMENSION = 4096
        const val PREFS_NAME = "bihar_map_measure_prefs"
        const val KEY_LAST_URI = "last_document_uri"
        const val KEY_LAST_PAGE = "last_document_page"
    }
}
