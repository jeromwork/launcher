package com.launcher.api.push

import com.launcher.api.result.Outcome
import kotlinx.serialization.json.JsonObject

/**
 * Port (spec 007 §FR-036) for triggering a push notification on a paired
 * link. Called by the admin app **after a successful Firestore write** so
 * Managed wakes up (FCM data-message) and refetches.
 *
 *  - `WorkerPushSender` (androidMain, `realBackend`) — HTTPS POST to the
 *    Cloudflare Worker `/notify` endpoint with `Authorization: Bearer
 *    <Firebase-ID-token>` header (contracts/worker-notify.md).
 *  - `FakePushSender` (commonTest) — counter increment, no network (FR-035).
 *
 * Idempotency: callers may retry on [PushError.NetworkUnavailable] /
 * [PushError.WorkerError]; the Worker is idempotent for the same payload.
 */
interface PushSender {
    suspend fun notify(
        linkId: String,
        type: PushType,
        extra: JsonObject? = null,
    ): Outcome<Unit, PushError>
}
