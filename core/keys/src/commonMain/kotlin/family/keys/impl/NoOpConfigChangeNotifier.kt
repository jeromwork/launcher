package family.keys.impl

import family.keys.api.ConfigChangeNotifier

/**
 * No-op default. Used in mockBackend flavor + as safety fallback if production DI
 * forgot to wire a real impl (so missing wiring degrades gracefully — config save
 * still works, just no push trigger).
 */
class NoOpConfigChangeNotifier : ConfigChangeNotifier {
    override suspend fun onConfigSaved(ownerUid: String, storageKey: String) = Unit
}
