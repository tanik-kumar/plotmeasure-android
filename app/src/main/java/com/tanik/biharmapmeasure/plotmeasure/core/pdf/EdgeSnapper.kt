package com.tanik.biharmapmeasure.plotmeasure.core.pdf

import com.tanik.biharmapmeasure.plotmeasure.model.MeasurementPoint
import kotlin.math.max

object EdgeSnapper {
    fun refinePoint(
        pagePoint: MeasurementPoint,
        tile: RenderedPdfTile?,
        searchRadiusPixels: Int = 8,
    ): MeasurementPoint {
        tile ?: return pagePoint
        if (!tile.contains(PageRect(pagePoint.x, pagePoint.y, pagePoint.x, pagePoint.y))) {
            return pagePoint
        }

        return runCatching {
            val bitmap = tile.bitmap
            if (bitmap.isRecycled || bitmap.width < 3 || bitmap.height < 3) {
                return pagePoint
            }
            if (tile.region.width <= 0.0 || tile.region.height <= 0.0) {
                return pagePoint
            }

            val unclampedX = ((pagePoint.x - tile.region.left) / tile.region.width * bitmap.width).toInt()
            val unclampedY = ((pagePoint.y - tile.region.top) / tile.region.height * bitmap.height).toInt()
            val localX = unclampedX.coerceIn(1, bitmap.width - 2)
            val localY = unclampedY.coerceIn(1, bitmap.height - 2)

            var bestX = localX
            var bestY = localY
            var bestScore = -1

            val minX = max(localX - searchRadiusPixels, 1)
            val maxX = minOf(localX + searchRadiusPixels, bitmap.width - 2)
            val minY = max(localY - searchRadiusPixels, 1)
            val maxY = minOf(localY + searchRadiusPixels, bitmap.height - 2)

            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    val score = gradientMagnitude(bitmap, x, y)
                    if (score > bestScore) {
                        bestScore = score
                        bestX = x
                        bestY = y
                    }
                }
            }

            val refinedPageX = tile.region.left + bestX.toDouble() / bitmap.width.toDouble() * tile.region.width
            val refinedPageY = tile.region.top + bestY.toDouble() / bitmap.height.toDouble() * tile.region.height
            pagePoint.copy(x = refinedPageX, y = refinedPageY)
        }.getOrElse {
            pagePoint
        }
    }

    private fun gradientMagnitude(
        bitmap: android.graphics.Bitmap,
        x: Int,
        y: Int,
    ): Int {
        val centerLeft = luminance(bitmap.getPixel(x - 1, y))
        val centerRight = luminance(bitmap.getPixel(x + 1, y))
        val centerTop = luminance(bitmap.getPixel(x, y - 1))
        val centerBottom = luminance(bitmap.getPixel(x, y + 1))
        return kotlin.math.abs(centerRight - centerLeft) + kotlin.math.abs(centerBottom - centerTop)
    }

    private fun luminance(color: Int): Int {
        val red = android.graphics.Color.red(color)
        val green = android.graphics.Color.green(color)
        val blue = android.graphics.Color.blue(color)
        return (red * 299 + green * 587 + blue * 114) / 1000
    }
}
