package com.familyvoice.app

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 주소(글자)를 QR 이미지로 바로 그려준다.
 * 외부 QR 생성 사이트나 사람이 손으로 만드는 과정이 필요 없다 — uploadUrl만 넘기면 끝.
 *
 * 화면을 카메라로 찍어서 읽는 상황이라, 잘 읽히게 두 가지를 잡아 둔다.
 *   - 오류 정정 수준을 올려서(M) 화면 반사·흔들림에 덜 민감하게
 *   - 테두리 흰 여백(margin)을 넉넉히: 여백이 없으면 카메라가 QR 경계를 못 찾는다
 */
fun qrBitmap(text: String, sizePx: Int = 900): Bitmap {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2
    )
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val offset = y * w
        for (x in 0 until w) {
            pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, w, 0, 0, w, h)
    }
}
