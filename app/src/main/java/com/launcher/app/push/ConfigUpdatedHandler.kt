package com.launcher.app.push

import android.util.Log
import family.keys.api.ConfigSaver
import family.keys.api.Outcome
import family.push.api.PushHandler
import family.push.api.PushPayload
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * T121-T122 — handler for `config-updated` push event. Per spec 019 FR-043, FR-044.
 *
 * Flow:
 *   1. Extract `configName` из payload.fields. Missing → log + drop.
 *   2. Compute namespace: if `payload.ownerUid == currentUid()` → invoke
 *      [ConfigSaver.loadOwn]; else → [ConfigSaver.loadForOther].
 *   3. Debounce by `payload.triggerId` (per FR-044, SC-006): same triggerId
 *      within 2s window → at-most-one load invocation (FCM may deliver duplicates).
 *
 * Idempotency: handler MUST tolerate duplicate FCM deliveries (FR-044). Even
 * без debounce, [ConfigSaver.loadOwn] is idempotent — но we add debounce to
 * avoid hammering remote storage с burst saves.
 *
 * **Note on debounce scope**: in-memory, per-process. Process death loses
 * dedup state. Acceptable: process restart unlikely within 2-sec window;
 * Worker idempotency layer dedupes at server (FR-010).
 *
 * UI refresh: out of scope для этого handler. ConfigSaver.loadOwn return value
 * is discarded — caller's responsibility to observe RemoteStorage state through
 * existing F-5b observers. F-5c just **triggers** the load; consumer of cached
 * config picks up change.
 */
class ConfigUpdatedHandler(
    private val configSaver: ConfigSaver,
    private val currentUidSupplier: suspend () -> String?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val debounceWindowMillis: Long = DEFAULT_DEBOUNCE_WINDOW_MILLIS,
) : PushHandler {

    private val lock = Mutex()
    private val recentTriggers: MutableMap<String, Long> = mutableMapOf()

    override suspend fun handle(payload: PushPayload) {
        val configName = payload.fields["configName"]
        if (configName.isNullOrEmpty()) {
            Log.w(TAG, "config-updated push без configName field — drop")
            return
        }

        if (shouldDebounce(payload.triggerId)) {
            Log.i(
                TAG,
                "debounced duplicate triggerId=${payload.triggerId} (within ${debounceWindowMillis}ms)",
            )
            return
        }

        val currentUid = currentUidSupplier()
        val ownerUid = payload.ownerUid

        val outcome = when {
            ownerUid == null || currentUid == ownerUid ->
                configSaver.loadOwn(configName)
            else ->
                configSaver.loadForOther(ownerUid, configName)
        }

        when (outcome) {
            is Outcome.Success -> Log.i(
                TAG,
                "config-updated loaded: owner=${ownerUid ?: "self"} name=$configName bytes=${outcome.value.size}",
            )
            is Outcome.Failure -> Log.w(
                TAG,
                "config-updated load failed: owner=${ownerUid ?: "self"} name=$configName error=${outcome.error}",
            )
        }
    }

    private suspend fun shouldDebounce(triggerId: String): Boolean = lock.withLock {
        val now = nowMillis()
        // Cheap GC — drop entries older than window.
        recentTriggers.entries.removeAll { (_, ts) -> now - ts > debounceWindowMillis }
        val last = recentTriggers[triggerId]
        if (last != null && now - last <= debounceWindowMillis) {
            return@withLock true
        }
        recentTriggers[triggerId] = now
        false
    }

    companion object {
        private const val TAG = "ConfigUpdatedHandler"

        /** Per FR-044 — 2 seconds. */
        const val DEFAULT_DEBOUNCE_WINDOW_MILLIS: Long = 2_000L
    }
}
