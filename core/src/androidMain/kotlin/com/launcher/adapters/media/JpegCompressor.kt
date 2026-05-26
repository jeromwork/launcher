package com.launcher.adapters.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.launcher.api.media.MediaPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Spec 012 — admin-side JPEG compression перед upload'ом через
 * PrivateMediaUploader.
 *
 * Algorithm (research.md R7):
 *  1. Decode bytes → Bitmap с inSampleSize'ом, downscale до max 2048×2048.
 *  2. Compress JPEG quality=80 → byte stream.
 *  3. If size > [MediaPicker.SIZE_CAP_BYTES] (500 KB) → re-compress quality=60.
 *  4. If still > cap → re-decode с inSampleSize=2 + recompress quality=80.
 *  5. If still > cap → return [CompressionError.TooLarge].
 *
 * Runs в [Dispatchers.Default] (CPU-bound).
 *
 * Task: T1252 (Phase 7). FR-006 (upstream prep), edge case 1 ("Фото
 * слишком большое").
 */
class JpegCompressor {

    suspend fun compress(rawBytes: ByteArray): Result = withContext(Dispatchers.Default) {
        try {
            // Pass 1: quality 80, downscale to max 2048.
            val firstPass = compressOnce(rawBytes, quality = 80, maxDimension = 2048)
                ?: return@withContext Result.Failure(CompressionError.DecodeFailed)
            if (firstPass.size <= MediaPicker.SIZE_CAP_BYTES) {
                return@withContext Result.Success(firstPass)
            }

            // Pass 2: quality 60, same dimension.
            val secondPass = compressOnce(rawBytes, quality = 60, maxDimension = 2048)
                ?: return@withContext Result.Failure(CompressionError.DecodeFailed)
            if (secondPass.size <= MediaPicker.SIZE_CAP_BYTES) {
                return@withContext Result.Success(secondPass)
            }

            // Pass 3: aggressive downscale, quality 80.
            val thirdPass = compressOnce(rawBytes, quality = 80, maxDimension = 1024)
                ?: return@withContext Result.Failure(CompressionError.DecodeFailed)
            if (thirdPass.size <= MediaPicker.SIZE_CAP_BYTES) {
                return@withContext Result.Success(thirdPass)
            }

            Result.Failure(CompressionError.TooLarge(actualBytes = thirdPass.size.toLong()))
        } catch (e: OutOfMemoryError) {
            Result.Failure(CompressionError.OutOfMemory(e))
        } catch (e: Throwable) {
            Result.Failure(CompressionError.DecodeFailed)
        }
    }

    private fun compressOnce(bytes: ByteArray, quality: Int, maxDimension: Int): ByteArray? {
        // Read bounds first для inSampleSize calculation.
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(
            srcWidth = boundsOptions.outWidth,
            srcHeight = boundsOptions.outHeight,
            maxDimension = maxDimension,
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null

        return try {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Pick power-of-2 inSampleSize such that both dimensions <= maxDimension.
     * Standard Android pattern.
     */
    private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, maxDimension: Int): Int {
        var sample = 1
        var w = srcWidth
        var h = srcHeight
        while (w > maxDimension || h > maxDimension) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    sealed interface Result {
        data class Success(val compressedBytes: ByteArray) : Result {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Success) return false
                return compressedBytes.contentEquals(other.compressedBytes)
            }
            override fun hashCode(): Int = compressedBytes.contentHashCode()
        }
        data class Failure(val error: CompressionError) : Result
    }

    sealed interface CompressionError {
        /** Bytes не decode'ились as image (corrupt? not an image?). */
        data object DecodeFailed : CompressionError

        /** Out of memory during decode (very large image; rare on modern devices). */
        data class OutOfMemory(val cause: OutOfMemoryError) : CompressionError

        /** Even after aggressive compression, still > SIZE_CAP_BYTES. */
        data class TooLarge(val actualBytes: Long) : CompressionError
    }
}
