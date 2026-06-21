package family.push.api

/**
 * T014 — Typed error variants для [PushTrigger.trigger] failure channel.
 * Per spec 019 FR-076, data-model.md §PushTriggerError.
 *
 * Не throw — каждый variant return через Outcome.Failure. Caller использует
 * exhaustive `when` (sealed). Adding новый variant = additive change (FR-051).
 */
sealed class PushTriggerError(open val message: String) {

    /**
     * Worker returned 401 — Firebase ID-token expired / invalid signature /
     * wrong issuer / wrong audience. Caller should re-acquire token via F-4
     * AuthIdentity.currentIdToken() (re-issued each call by Firebase SDK).
     */
    data object Unauthorized : PushTriggerError("Worker rejected Firebase ID-token")

    /**
     * Worker returned 429 — per-UID per-event rate limit exceeded. Per FR-006.
     * Caller can wait OR drop (push = fire-and-forget; eventually-consistent
     * via pull-on-app-open). Worker отправляет `Retry-After` header
     * (но client не auto-retries — FR-026).
     */
    data object RateLimited : PushTriggerError("Per-UID rate limit exceeded")

    /**
     * Network failure — no connectivity, timeout, TLS error, DNS failure.
     * Eventually consistent via pull-on-app-open safety net (FR-038).
     */
    data class NetworkFailure(override val message: String) : PushTriggerError(message)

    /**
     * Worker 4xx (non-401/429) или 5xx, или FCM dispatch failed inside Worker.
     * Per FR-009 (Worker bounded retry FCM); client never retries (FR-026).
     */
    data class Backend(override val message: String) : PushTriggerError(message)
}
