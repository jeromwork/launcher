package family.keys.api

/**
 * Optional notifier invoked **after** [RemoteStorage.put] successfully persisted
 * bytes in given namespace/key. Allows higher layers (F-5c push module, future
 * audit logging, future analytics) to fire side-effects **without** coupling
 * `:core:keys` to those concerns.
 *
 * Default impl: [family.keys.impl.NoOpConfigChangeNotifier]. Production wiring
 * (realBackend flavor) supplies an adapter that translates to PushTrigger.trigger(
 * EventType.ConfigUpdated, ...).
 *
 * **Why this port instead of direct PushTrigger dep in :core:keys**: CLAUDE.md
 * rule 1 (domain isolation) + verifyKeysIsolation fitness function. :core:keys
 * MUST depend only on :core:crypto. Notifier port keeps the boundary clean —
 * push module wires the adapter, :core:keys never imports family.push.
 *
 * Failure semantics: invoked from background worker (WorkManagerAsyncConfigPushQueue
 * EnvelopeAsyncPushWorker). Adapter MUST NOT throw — exceptions caught и logged
 * by caller. Notifier failure must NOT affect storage operation outcome (idempotent
 * fire-and-forget per FR-031).
 */
fun interface ConfigChangeNotifier {

    /**
     * Called after RemoteStorage.put succeeded.
     *
     *  • [ownerUid] — namespace argument from `put` (Firebase UID owning the config).
     *  • [storageKey] — full storage key (e.g. `"config/main"`). Caller decomposes
     *    via [ConfigSaver.configNameOf] if event payload needs configName.
     */
    suspend fun onConfigSaved(ownerUid: String, storageKey: String)
}
