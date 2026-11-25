package com.forensim.app.feature.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.objdetect.Objdetect
import java.io.File
import java.io.FileOutputStream

/**
 * The printable Forensim marker: one A4 page with the ArUco marker at exactly
 * [MarkerTracker.MARKER_SIZE_M] (10 cm), a 10 cm ruler to check it printed at 100 %, and short
 * instructions. Opened in Android's print screen, which can also "Save as PDF".
 */
object MarkerSheet {
    private const val PT_PER_MM = 72f / 25.4f
    private const val A4_W = 595
    private const val A4_H = 842

    /** The marker image (black square with its pattern and a white quiet border is added on the page). */
    fun markerBitmap(px: Int = 600): Bitmap {
        val mat = Mat()
        Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50).generateImageMarker(MarkerTracker.MARKER_ID, px, mat, 1)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bmp)
        mat.release()
        return bmp
    }

    fun writePdf(file: File) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create())
        val c: Canvas = page.canvas
        c.drawColor(Color.WHITE)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f; isFakeBoldText = true }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 11f }
        c.drawText("Forensim AR marker", 56f, 70f, text)
        listOf(
            "Print at 100% (actual size, no \"fit to page\"). The black square must measure 10 cm.",
            "Lay it flat on a table, open Evidence lab > Open camera and point the phone at it.",
            "The specimen stands on the marker at true size. Walk around it; keep the marker in view.",
        ).forEachIndexed { i, line -> c.drawText(line, 56f, 96f + i * 16f, small) }

        // The marker, exactly 10 cm, nearest-neighbour so its edges stay sharp.
        val side = (MarkerTracker.MARKER_SIZE_M * 1000).toFloat() * PT_PER_MM
        val left = (A4_W - side) / 2
        val top = 190f
        val crisp = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        c.drawBitmap(markerBitmap(), null, RectF(left, top, left + side, top + side), crisp)

        // 10 cm ruler with centimetre ticks, to check the print scale.
        val rulerY = top + side + 60f
        val line = Paint().apply { color = Color.BLACK; strokeWidth = 1.2f }
        c.drawLine(left, rulerY, left + side, rulerY, line)
        for (cm in 0..10) {
            val x = left + cm * 10 * PT_PER_MM
            c.drawLine(x, rulerY, x, rulerY - if (cm % 5 == 0) 10f else 6f, line)
        }
        c.drawText("10 cm: measure this line; if it isn't 10 cm, print again at 100 %", left, rulerY + 18f, small)
        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    /** Opens the system print screen for the marker sheet. */
    fun print(context: Context) {
        val file = File(context.cacheDir, "forensim-ar-marker.pdf")
        writePdf(file)
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print("Forensim AR marker", object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?,
                                  callback: LayoutResultCallback, extras: Bundle?) {
                if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
                callback.onLayoutFinished(PrintDocumentInfo.Builder("forensim-ar-marker.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(), true)
            }

            override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
                                 cancel: CancellationSignal?, callback: WriteResultCallback) {
                try {
                    file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}
