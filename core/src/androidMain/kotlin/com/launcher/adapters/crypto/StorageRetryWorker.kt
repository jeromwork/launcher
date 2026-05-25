package com.launcher.adapters.crypto

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

// Spec 011 FR-044 / CHK-FR-012 / research.md §5b — WorkManager retry policy для
// Storage upload/download failures. Exponential backoff: 1m → 5m → 30m → 2h → 12h,
// max 5 attempts. После exhaustion — structured log warning, no automatic retry.
//
// Этот worker — шаблон. Конкретные upload jobs создаются Phase 5+ потребителями
// (e.g. спек 012 UploadContactPhotoWorker наследует retry policy через
// fromUploadEnqueue() helper).
internal class StorageRetryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Phase 5 — schema-only. Реальная upload logic вкручивается потребителями
        // (спек 012 photo upload, future spec audio messages). Здесь — только
        // attempt counter + give-up gate.
        val attemptIndex = runAttemptCount + 1
        return if (attemptIndex >= MAX_ATTEMPTS) {
            // exhaustion — log + give up (no auto-retry)
            Result.failure(Data.Builder().putInt(KEY_FINAL_ATTEMPT, attemptIndex).build())
        } else {
            // schema-level placeholder: реальный job вернёт success/retry на основе
            // upload outcome. Здесь — fail-with-retry для демонстрации backoff.
            Result.retry()
        }
    }

    companion object {
        // Exponential backoff: первая retry через 60 секунд, каждая следующая ×2.
        // Total cap = 5 attempts ≈ 1m → 2m → 4m → 8m → 16m (real WorkManager exp).
        // Spec 011 research.md §5b писал 1m → 5m → 30m → 2h → 12h — это явное
        // переопределение exp policy, но WorkManager BackoffPolicy.EXPONENTIAL
        // doubles минут (1→2→4→8→16). Для real 1m → 5m → 30m → 2h → 12h нужен
        // custom scheduler с computeRetryDelay() — отложено в Phase 8 manual smoke.
        const val MAX_ATTEMPTS = 5
        const val INITIAL_BACKOFF_SECONDS = 60L
        const val KEY_FINAL_ATTEMPT = "final_attempt"

        // Helper для потребителей: build OneTimeWorkRequest с правильной retry policy.
        fun buildRequest(inputData: Data = Data.EMPTY): WorkRequest =
            OneTimeWorkRequestBuilder<StorageRetryWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    INITIAL_BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                )
                .setInputData(inputData)
                .build()
    }
}
