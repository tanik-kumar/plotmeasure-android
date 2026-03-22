package com.tanik.biharmapmeasure.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import kotlin.math.max
import kotlin.math.roundToInt

data class PdfPageGeometry(
    val width: Int,
    val height: Int,
)

data class RenderedPdfRegion(
    val bitmap: Bitmap,
    val pageWidth: Int,
    val pageHeight: Int,
    val regionLeft: Float,
    val regionTop: Float,
    val regionWidth: Float,
    val regionHeight: Float,
) {
    val densityX: Float
        get() = bitmap.width.toFloat() / regionWidth.coerceAtLeast(1f)

    val densityY: Float
        get() = bitmap.height.toFloat() / regionHeight.coerceAtLeast(1f)

    val minDensity: Float
        get() = minOf(densityX, densityY)

    fun contains(rect: RectF): Boolean {
        return rect.left >= regionLeft &&
            rect.top >= regionTop &&
            rect.right <= regionLeft + regionWidth &&
            rect.bottom <= regionTop + regionHeight
    }
}

class PdfPageRenderer(private val context: Context) : Closeable {
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var currentUri: Uri? = null

    @Synchronized
    fun getPageCount(uri: Uri): Int {
        val descriptor =
            requireNotNull(context.contentResolver.openFileDescriptor(uri, "r")) {
                "Unable to open document"
            }
        descriptor.use { parcelFileDescriptor ->
            PdfRenderer(parcelFileDescriptor).use { renderer ->
                return renderer.pageCount
            }
        }
    }

    @Synchronized
    fun open(uri: Uri): Int {
        if (currentUri != uri || pdfRenderer == null) {
            close()
            val descriptor =
                requireNotNull(
                    context.contentResolver.openFileDescriptor(uri, "r"),
                ) {
                    "Unable to open document"
                }
            fileDescriptor = descriptor
            pdfRenderer = PdfRenderer(descriptor)
            currentUri = uri
        }
        return requireNotNull(pdfRenderer).pageCount
    }

    @Synchronized
    fun getPageGeometry(pageIndex: Int): PdfPageGeometry {
        val renderer = requireNotNull(pdfRenderer) { "Document is not open" }
        require(pageIndex in 0 until renderer.pageCount) { "Page index out of bounds" }

        renderer.openPage(pageIndex).use { page ->
            return PdfPageGeometry(
                width = page.width,
                height = page.height,
            )
        }
    }

    @Synchronized
    fun renderPage(pageIndex: Int, maxDimension: Int = 4096): RenderedPdfRegion {
        val renderer = requireNotNull(pdfRenderer) { "Document is not open" }
        require(pageIndex in 0 until renderer.pageCount) { "Page index out of bounds" }

        renderer.openPage(pageIndex).use { page ->
            val sourceWidth = page.width
            val sourceHeight = page.height
            val scale = maxDimension.toFloat() / max(sourceWidth, sourceHeight).toFloat()
            val outWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
            val outHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            val matrix =
                Matrix().apply {
                    setScale(scale, scale)
                }
            page.render(
                bitmap,
                null,
                matrix,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
            )
            return RenderedPdfRegion(
                bitmap = bitmap,
                pageWidth = sourceWidth,
                pageHeight = sourceHeight,
                regionLeft = 0f,
                regionTop = 0f,
                regionWidth = sourceWidth.toFloat(),
                regionHeight = sourceHeight.toFloat(),
            )
        }
    }

    @Synchronized
    fun renderPageRegion(
        pageIndex: Int,
        region: RectF,
        targetWidth: Int,
        targetHeight: Int,
    ): RenderedPdfRegion {
        val renderer = requireNotNull(pdfRenderer) { "Document is not open" }
        require(pageIndex in 0 until renderer.pageCount) { "Page index out of bounds" }

        renderer.openPage(pageIndex).use { page ->
            val sourceWidth = page.width.toFloat()
            val sourceHeight = page.height.toFloat()
            val safeRegion =
                RectF(
                    region.left.coerceIn(0f, sourceWidth),
                    region.top.coerceIn(0f, sourceHeight),
                    region.right.coerceIn(0f, sourceWidth),
                    region.bottom.coerceIn(0f, sourceHeight),
                )
            if (safeRegion.width() <= 0f || safeRegion.height() <= 0f) {
                safeRegion.set(0f, 0f, sourceWidth, sourceHeight)
            }

            val outWidth = targetWidth.coerceAtLeast(1)
            val outHeight = targetHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            val scaleX = outWidth / safeRegion.width()
            val scaleY = outHeight / safeRegion.height()
            val matrix =
                Matrix().apply {
                    setScale(scaleX, scaleY)
                    postTranslate(-safeRegion.left * scaleX, -safeRegion.top * scaleY)
                }

            page.render(
                bitmap,
                null,
                matrix,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
            )

            return RenderedPdfRegion(
                bitmap = bitmap,
                pageWidth = page.width,
                pageHeight = page.height,
                regionLeft = safeRegion.left,
                regionTop = safeRegion.top,
                regionWidth = safeRegion.width(),
                regionHeight = safeRegion.height(),
            )
        }
    }

    @Synchronized
    override fun close() {
        pdfRenderer?.close()
        fileDescriptor?.close()
        pdfRenderer = null
        fileDescriptor = null
        currentUri = null
    }
}
