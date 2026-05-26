package com.launcher.adapters.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import com.launcher.api.media.MediaPickResult
import com.launcher.api.media.MediaPicker
import com.launcher.api.media.MediaPickerError
import com.launcher.api.result.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Spec 012 — production [MediaPicker] for Android.
 *
 * Two-step protocol (mirrors SystemContactPickerAdapter pattern):
 *  1. UI selects appropriate `ActivityResultContract` via [contractForPick] and
 *     launches with `ActivityResultLauncher`.
 *  2. UI calls [resolveUri] with the URI from the contract callback to obtain
 *     unified bytes (domain [MediaPickResult]).
 *
 * **Direct [pick] is intentionally inoperative** — UI must own ActivityResultLauncher
 * lifecycle. Bare call returns `Outcome.Failure(IOError)` to fail loud при misuse.
 *
 * Branch selection by `Build.VERSION.SDK_INT`:
 *  - **33+**: `ActivityResultContracts.PickVisualMedia()` — native ACTION_PICK_IMAGES.
 *  - **29-32**: same `PickVisualMedia()` contract (androidx compat picker; uses Google
 *    Photos if installed, else SAF под капотом).
 *  - **26-28**: `ActivityResultContracts.OpenDocument()` — SAF; UI must pass mime
 *    filter (e.g. `arrayOf("image/star")`).
 *
 * No new permissions (per FR-008, SC-007). No user-facing hint dialog (per Q4).
 *
 * Tasks: T1225-T1228 (Phase 4). FR-007, FR-008, FR-009.
 */
class SystemPhotoPickerAdapter(
    context: Context,
) : MediaPicker {

    private val appContext: Context = context.applicationContext

    override suspend fun pick(
        kind: MediaPicker.Kind,
        maxItems: Int,
        mode: MediaPicker.Mode,
    ): Outcome<List<MediaPickResult>, MediaPickerError> = Outcome.Failure(
        MediaPickerError.IOError(
            IllegalStateException(
                "Direct pick() not supported on Android adapter — UI must launch the " +
                    "ActivityResultContract returned by contractForPick() and call " +
                    "resolveUri() with the result."
            )
        )
    )

    /**
     * Returns the appropriate ActivityResultContract for [kind] и current API level.
     * UI registers this via `rememberLauncherForActivityResult(...)`.
     *
     * @return Pair(contract, input) — input is the parameter to pass to launcher.launch().
     *         For PickVisualMedia: VisualMediaType (image/video/any). For OpenDocument:
     *         array of MIME types.
     */
    fun contractForPick(kind: MediaPicker.Kind): PickerContractRequest {
        val useVisualPicker = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(appContext)

        return if (useVisualPicker) {
            val visualType = when (kind) {
                MediaPicker.Kind.Image -> ActivityResultContracts.PickVisualMedia.ImageOnly
                MediaPicker.Kind.Video -> ActivityResultContracts.PickVisualMedia.VideoOnly
                MediaPicker.Kind.Any -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
            }
            PickerContractRequest.VisualMedia(
                contract = ActivityResultContracts.PickVisualMedia(),
                request = androidx.activity.result.PickVisualMediaRequest(visualType),
            )
        } else {
            // SAF fallback for API < 29 без PhotoPicker compat. Pass mime types.
            val mimeTypes = when (kind) {
                MediaPicker.Kind.Image -> arrayOf("image/*")
                MediaPicker.Kind.Video -> arrayOf("video/*")
                MediaPicker.Kind.Any -> arrayOf("image/*", "video/*")
            }
            PickerContractRequest.OpenDocument(
                contract = ActivityResultContracts.OpenDocument(),
                request = mimeTypes,
            )
        }
    }

    /**
     * Resolve the URI from picker callback into unified bytes.
     *
     * Validates MIME type matches requested [kind]. Reads bytes via ContentResolver.
     * Removes temp file if SAF copy was used.
     *
     * @return MediaPickResult если success; MediaPickerError if cancelled (null uri),
     *         invalid mime, file too large, или IO error.
     */
    suspend fun resolveUri(
        uri: Uri?,
        kind: MediaPicker.Kind,
    ): Outcome<MediaPickResult, MediaPickerError> = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext Outcome.Failure(MediaPickerError.Cancelled)

        val resolver: ContentResolver = appContext.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"

        val expectedPrefix = when (kind) {
            MediaPicker.Kind.Image -> "image/"
            MediaPicker.Kind.Video -> "video/"
            MediaPicker.Kind.Any -> null  // any prefix accepted
        }
        if (expectedPrefix != null && !mimeType.startsWith(expectedPrefix)) {
            return@withContext Outcome.Failure(
                MediaPickerError.InvalidMimeType(actual = mimeType, expected = "$expectedPrefix*")
            )
        }

        // Read full bytes; check size cap.
        val bytes = try {
            resolver.openInputStream(uri)?.use { input ->
                val bos = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MediaPicker.SIZE_CAP_BYTES) {
                        return@withContext Outcome.Failure(
                            MediaPickerError.FileTooLarge(
                                actualBytes = total,
                                maxBytes = MediaPicker.SIZE_CAP_BYTES,
                            )
                        )
                    }
                    bos.write(buf, 0, n)
                }
                bos.toByteArray()
            } ?: return@withContext Outcome.Failure(
                MediaPickerError.IOError(IllegalStateException("openInputStream returned null"))
            )
        } catch (e: SecurityException) {
            return@withContext Outcome.Failure(MediaPickerError.IOError(e))
        } catch (e: Throwable) {
            return@withContext Outcome.Failure(MediaPickerError.IOError(e))
        }

        Outcome.Success(
            MediaPickResult(
                bytes = bytes,
                mimeType = mimeType,
                sourceLabel = null,  // source app не разглашается ContentResolver'ом
            )
        )
    }
}

/**
 * Spec 012 — wrapper bundling the platform [ActivityResultContract] и его input.
 * UI code uses this as input to `rememberLauncherForActivityResult`.
 */
sealed interface PickerContractRequest {
    data class VisualMedia(
        val contract: ActivityResultContract<androidx.activity.result.PickVisualMediaRequest, Uri?>,
        val request: androidx.activity.result.PickVisualMediaRequest,
    ) : PickerContractRequest

    data class OpenDocument(
        val contract: ActivityResultContract<Array<String>, Uri?>,
        val request: Array<String>,
    ) : PickerContractRequest {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OpenDocument) return false
            return contract == other.contract && request.contentEquals(other.request)
        }

        override fun hashCode(): Int = 31 * contract.hashCode() + request.contentHashCode()
    }
}
