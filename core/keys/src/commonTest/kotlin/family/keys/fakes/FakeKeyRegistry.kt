package family.keys.fakes

import family.keys.api.DerivedKey
import family.keys.api.KeyRegistry
import family.keys.api.Outcome
import family.keys.api.RootKeyError
import family.keys.api.StableId
import java.security.MessageDigest

/**
 * In-memory [KeyRegistry] для тестов (FR-022, CLAUDE.md rule 6).
 *
 * **Deterministic derivation**: SHA-256 от `"$stableId|$purpose"` как test material.
 * Гарантирует: те же входы → тот же DerivedKey; разные purpose → разные ключи.
 * Не использует настоящий HKDF — это намеренно (тесты должны быть быстрыми и детерминированными).
 *
 * **Namespace isolation**: Map<StableId, Map<String, DerivedKey>> — wipe одного stableId
 * не затрагивает другие (SC-012, T621).
 *
 * @see KeyRegistry
 * @see FakeRootKeyManager
 */
class FakeKeyRegistry : KeyRegistry {

    // namespace → purpose → bytes
    private val store: MutableMap<StableId, MutableMap<String, DerivedKey>> = mutableMapOf()

    /** Test introspection: количество namespace'ов. */
    fun namespaceCount(): Int = store.size

    /** Test introspection: список purpose'ов для данного stableId. */
    fun purposesFor(stableId: StableId): Set<String> = store[stableId]?.keys?.toSet() ?: emptySet()

    override suspend fun derive(stableId: StableId, purpose: String): Outcome<DerivedKey, RootKeyError> {
        val key = store.getOrPut(stableId) { mutableMapOf() }
            .getOrPut(purpose) {
                DerivedKey(deterministicBytes(stableId, purpose))
            }
        return Outcome.Success(key)
    }

    override suspend fun wipeAll(stableId: StableId): Outcome<Unit, RootKeyError> {
        store.remove(stableId)
        return Outcome.Success(Unit)
    }

    override suspend fun list(stableId: StableId): Outcome<List<String>, RootKeyError> {
        return Outcome.Success(store[stableId]?.keys?.toList() ?: emptyList())
    }

    // --- helpers ---

    private fun deterministicBytes(stableId: StableId, purpose: String): ByteArray {
        val input = "$stableId|$purpose"
        return MessageDigest.getInstance("SHA-256").digest(input.encodeToByteArray())
    }
}
