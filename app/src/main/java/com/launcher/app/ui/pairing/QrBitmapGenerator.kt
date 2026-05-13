package com.launcher.app.ui.pairing

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * ZXing-backed QR encoder (spec 007 FR-004).
 *
 * Encodes the pairing deep-link (`launcher://pair?token=XXX&v=1`) at error
 * correction level **M** per contracts/qr-deeplink.md §QR encoding — balance
 * between scan reliability and module density.
 *
 * **Lives in `:app/` not `:core/api/qr/`** because [Bitmap] is an Android
 * platform type that must not leak into commonMain (CLAUDE.md §1). When iOS
 * support arrives, the spec-aligned move is:
 *
 *   1. Add `:core/api/qr/QrEncoder.kt` port (returns a domain bitmap
 *      type or per-pixel byte array — NOT [Bitmap]).
 *   2. Implement androidMain adapter that wraps this file.
 *   3. Implement iosMain adapter via CoreImage's `CIQRCodeGenerator`.
 *
 * Until iOS lands, an Android-only generator with a domain-typed input
 * (`String` URI) is sufficient and keeps the seam at the right layer.
 */
object QrBitmapGenerator {

    private const val DEFAULT_SIZE_PX = 512
    private const val MARGIN_MODULES = 1
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun generate(content: String, sizePx: Int = DEFAULT_SIZE_PX): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to MARGIN_MODULES,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) BLACK else WHITE
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
