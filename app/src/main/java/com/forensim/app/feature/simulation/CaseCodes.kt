package com.forensim.app.feature.simulation

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Case codes: 6 characters without look-alikes (no I, O, 0, 1). QR payload "FORENSIM:CASE:<code>". */
object CaseCode {
    const val LENGTH = 6
    private const val PREFIX = "FORENSIM:CASE:"
    private val ALLOWED = Regex("^[A-HJ-NP-Z2-9]{6}$")

    fun qrContent(code: String) = PREFIX + code

    /** Cleans typed or scanned text into a code, or null if it isn't one. */
    fun parse(raw: String?): String? {
        val text = raw?.trim()?.uppercase() ?: return null
        val body = if (text.startsWith(PREFIX)) text.removePrefix(PREFIX) else text
        val code = body.filter { it.isLetterOrDigit() }
        return code.takeIf { ALLOWED.matches(it) }
    }

    /** Typed input: uppercase, letters/digits only, max length. */
    fun clean(input: String) = input.uppercase().filter { it.isLetterOrDigit() }.take(LENGTH)
}

/** Black-on-white QR for [content] with a quiet zone; [sizePx] square. */
fun qrImage(content: String, sizePx: Int = 720): ImageBitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx) { i -> if (matrix[i % sizePx, i / sizePx]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Opens Google's code scanner (runs in Play services: no camera permission, nothing to bundle).
 * [onCode] gets the parsed case code, or null when the QR isn't a ForenSim case.
 */
fun scanCaseQr(context: Context, onCode: (String?) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { onCode(CaseCode.parse(it.rawValue)) }
        .addOnFailureListener { onError("The scanner isn't available on this phone. Type the code instead.") }
}
