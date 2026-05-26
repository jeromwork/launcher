package com.launcher.fake.media

import com.launcher.api.media.MediaPickResult
import com.launcher.api.media.MediaPicker
import com.launcher.api.media.MediaPickerError
import com.launcher.api.result.Outcome
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spec 012 fake — in-memory queue-based [MediaPicker] для tests / mockBackend variant.
 *
 * Usage:
 * ```
 * val picker = FakeMediaPicker()
 * picker.enqueueResult(bytes = jpegBytes, mimeType = "image/jpeg")
 * val result = picker.pick(MediaPicker.Kind.Image)
 * ```
 *
 * Per CLAUDE.md §6 (mock-first development).
 *
 * Task: T1215 (Phase 2).
 */
class FakeMediaPicker : MediaPicker {
    private val mutex = Mutex()
    private val queue = ArrayDeque<Outcome<List<MediaPickResult>, MediaPickerError>>()

    /** Append a successful pick result to the queue. */
    suspend fun enqueueResult(
        bytes: ByteArray,
        mimeType: String = "image/jpeg",
        sourceLabel: String? = "fake",
    ) {
        mutex.withLock {
            queue.addLast(Outcome.Success(listOf(MediaPickResult(bytes, mimeType, sourceLabel))))
        }
    }

    /** Append a failure to the queue (e.g. user cancellation, IO error). */
    suspend fun enqueueFailure(error: MediaPickerError) {
        mutex.withLock {
            queue.addLast(Outcome.Failure(error))
        }
    }

    override suspend fun pick(
        kind: MediaPicker.Kind,
        maxItems: Int,
        mode: MediaPicker.Mode,
    ): Outcome<List<MediaPickResult>, MediaPickerError> = mutex.withLock {
        if (queue.isEmpty()) {
            // Default behaviour когда тест забыл enqueue — return Cancelled (silent),
            // не бросать (тесты падают less предсказуемо если throw'ить).
            Outcome.Failure(MediaPickerError.Cancelled)
        } else {
            queue.removeFirst()
        }
    }

    /** Test helper — verify все enqueued results были consumed. */
    suspend fun isEmpty(): Boolean = mutex.withLock { queue.isEmpty() }
}
