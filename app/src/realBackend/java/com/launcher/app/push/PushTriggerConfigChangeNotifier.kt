package com.launcher.app.push

import android.util.Log
import cryptokit.keys.api.ConfigChangeNotifier
import cryptokit.keys.api.ConfigSaver
import cryptokit.keys.api.IdentityProof
import family.push.api.EventType
import family.push.api.Outcome
import family.push.api.PushTrigger
import family.push.api.TargetScope

/**
 * Adapter: maps `:core:keys` [ConfigChangeNotifier] → `:core:push` [PushTrigger].
 *
 * Wired в realBackend flavor only — mockBackend uses [cryptokit.keys.impl.NoOpConfigChangeNotifier].
 *
 * Invoked from F-5b EnvelopeAsyncPushWorker after successful storage.put. Determines
 * `ownerUid` для push trigger:
 *  • If `notifiedNamespace == currentUid` (own save) — push owner = currentUid.
 *  • If `notifiedNamespace != currentUid` (saveForOther) — push owner = notifiedNamespace.
 *
 * Per FR-031 fire-and-forget: failures logged, NOT propagated.
 */
class PushTriggerConfigChangeNotifier(
    private val pushTrigger: PushTrigger,
    private val identity: IdentityProof,
) : ConfigChangeNotifier {

    override suspend fun onConfigSaved(ownerUid: String, storageKey: String) {
        val configName = ConfigSaver.configNameOf(storageKey)
        if (configName == null) {
            // Not a config-prefixed key (e.g. future photo storage). Skip.
            return
        }

        // ownerUid arg from EnvelopeAsyncPushWorker = namespace passed to
        // RemoteStorage.put. For saveOwn это currentUid; для saveForOther — другой uid.
        // Both cases: push targets OwnAndGrants на ownerUid namespace.
        val outcome = pushTrigger.trigger(
            eventType = EventType.ConfigUpdated,
            targetScope = TargetScope.OwnAndGrants,
            ownerUid = ownerUid,
            payload = mapOf("configName" to configName),
        )

        when (outcome) {
            is Outcome.Success -> {
                // FR-031 — silent success. No user-visible indicator.
            }
            is Outcome.Failure -> Log.w(
                TAG,
                "push trigger failed (eventually consistent via pull-on-app-open): ${outcome.error}",
            )
        }
    }

    companion object {
        private const val TAG = "PushTriggerNotifier"
    }
}
