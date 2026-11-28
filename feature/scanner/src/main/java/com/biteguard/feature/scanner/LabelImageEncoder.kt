package com.biteguard.feature.scanner

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Android-layer bitmap encoder that converts a captured camera [Bitmap] into platform-agnostic
 * byte payloads before crossing the `:core:domain` boundary.
 */
internal data class EncodedLabelImage(
    val aiJpegBytes: ByteArray?,
    val thumbnailWebpBytes: ByteArray?
)

internal object LabelImageEncoder {

    fun encode(bitmap: Bitmap?): EncodedLabelImage {
        if (bitmap == null) return EncodedLabelImage(null, null)
        return EncodedLabelImage(
            aiJpegBytes = encodeForCloudAi(bitmap),
            thumbnailWebpBytes = encodeThumbnail(bitmap)
        )
    }

    private fun encodeForCloudAi(bitmap: Bitmap): ByteArray? = runCatching {
        val maxDim = 768
        val scale = (maxDim.toFloat() / maxOf(bitmap.width, bitmap.height, 1)).coerceAtMost(1f)
        val target = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        try {
            ByteArrayOutputStream().use { bos ->
                target.compress(Bitmap.CompressFormat.JPEG, 72, bos)
                bos.toByteArray()
            }
        } finally {
            if (target !== bitmap) target.recycle()
        }
    }.getOrNull()

    private fun encodeThumbnail(bitmap: Bitmap): ByteArray? = runCatching {
        val targetSize = 480
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val width = if (ratio >= 1f) targetSize else (targetSize * ratio).toInt().coerceAtLeast(1)
        val height = if (ratio >= 1f) (targetSize / ratio).toInt().coerceAtLeast(1) else targetSize
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            ByteArrayOutputStream().use { bos ->
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 78, bos)
                bos.toByteArray()
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }.getOrNull()
}
