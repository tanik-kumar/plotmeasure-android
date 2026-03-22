package com.tanik.biharmapmeasure.plotmeasure.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import java.io.Closeable
import kotlin.math.max
import kotlin.math.roundToInt

data class PageRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val width: Double
        get() = (right - left).coerceAtLeast(0.0)

    val height: Double
        get() = (bottom - top).coerceAtLeast(0.0)
}

data class PdfDocumentDescriptor(
    val uri: Uri,
    val displayName: String,
    val pageCount: Int,
)

data class RenderedPdfTile(
    val bitmap: Bitmap,
    val pageWidth: Int,
    val pageHeight: Int,
    val region: PageRect,
) {
    val minDensity: Double
        get() = minOf(
            bitmap.width.toDouble() / region.width.coerceAtLeast(1.0),
            bitmap.height.toDouble() / region.height.coerceAtLeast(1.0),
        )

    fun contains(rect: PageRect): Boolean {
        return rect.left >= region.left &&
            rect.top >= region.top &&
            rect.right <= region.right &&
            rect.bottom <= region.bottom
    }
}

class PdfRenderEngine(
    private val context: Context,
) : Closeable {
    private var currentUri: Uri? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    @Synchronized
    fun openDocument(uri: Uri): PdfDocumentDescriptor {
        if (currentUri != uri || renderer == null) {
            close()
            val descriptor =
                requireNotNull(context.contentResolver.openFileDescriptor(uri, "r")) {
                    "Unable to open PDF file."
                }
            fileDescriptor = descriptor
            renderer = PdfRenderer(descriptor)
            currentUri = uri
        }
        val displayName = DocumentFile.fromSingleUri(context, uri)?.name ?: "Selected PDF"
        return PdfDocumentDescriptor(
            uri = uri,
            displayName = displayName,
            pageCount = requireNotNull(renderer).pageCount,
        )
    }

    @Synchronized
    fun renderPage(
        pageIndex: Int,
        maxDimension: Int = DEFAULT_FULL_PAGE_EDGE,
    ): RenderedPdfTile {
        val pdfRenderer = requireNotNull(renderer) { "Open a PDF first." }
        require(pageIndex in 0 until pdfRenderer.pageCount) { "Page index is out of bounds." }

        pdfRenderer.openPage(pageIndex).use { page ->
            val width = page.width
            val height = page.height
            val scale = maxDimension.toFloat() / max(width, height).toFloat()
            val bitmapWidth = (width * scale).roundToInt().coerceAtLeast(1)
            val bitmapHeight = (height * scale).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val matrix =
                Matrix().apply {
                    setScale(scale, scale)
                }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return RenderedPdfTile(
                bitmap = bitmap,
                pageWidth = width,
                pageHeight = height,
                region = PageRect(0.0, 0.0, width.toDouble(), height.toDouble()),
            )
        }
    }

    @Synchronized
    fun renderRegion(
        pageIndex: Int,
        region: PageRect,
        targetWidth: Int,
        targetHeight: Int,
    ): RenderedPdfTile {
        val pdfRenderer = requireNotNull(renderer) { "Open a PDF first." }
        require(pageIndex in 0 until pdfRenderer.pageCount) { "Page index is out of bounds." }

        pdfRenderer.openPage(pageIndex).use { page ->
            val safeRegion =
                PageRect(
                    left = region.left.coerceIn(0.0, page.width.toDouble()),
                    top = region.top.coerceIn(0.0, page.height.toDouble()),
                    right = region.right.coerceIn(0.0, page.width.toDouble()),
                    bottom = region.bottom.coerceIn(0.0, page.height.toDouble()),
                ).takeIf { it.width > 0.0 && it.height > 0.0 }
                    ?: PageRect(0.0, 0.0, page.width.toDouble(), page.height.toDouble())

            val outWidth = targetWidth.coerceAtLeast(1)
            val outHeight = targetHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            val scaleX = outWidth.toFloat() / safeRegion.width.toFloat()
            val scaleY = outHeight.toFloat() / safeRegion.height.toFloat()
            val matrix =
                Matrix().apply {
                    setScale(scaleX, scaleY)
                    postTranslate(
                        (-safeRegion.left * scaleX).toFloat(),
                        (-safeRegion.top * scaleY).toFloat(),
                    )
                }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            return RenderedPdfTile(
                bitmap = bitmap,
                pageWidth = page.width,
                pageHeight = page.height,
                region = safeRegion,
            )
        }
    }

    @Synchronized
    override fun close() {
        renderer?.close()
        fileDescriptor?.close()
        renderer = null
        fileDescriptor = null
        currentUri = null
    }

    companion object {
        const val DEFAULT_FULL_PAGE_EDGE = 2048
    }
}
