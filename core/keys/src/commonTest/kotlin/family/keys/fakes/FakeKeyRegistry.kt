package family.keys.fakes

import family.keys.api.KeyRegistry
import family.keys.api.KeyRegistryError
import family.keys.api.Outcome

/**
 * In-memory [KeyRegistry] для тестов где shifrowanie config'а через ConfigCipher
 * нужно протестировать без Keystore wrap'а.
 *
 * Хранит DEK'и **в plaintext** (не secure для production, но это test fake).
 * Identity isolation НЕ реализована — это просто `Map<name, ByteArray>`.
 * Тесты, где нужна identity-partitioning (T102 MultiIdentityIsolationTest),
 * создают отдельные instance'ы `FakeKeyRegistry` per identity.
 *
 * Per CLAUDE.md rule 6 mock-first development.
 */
class FakeKeyRegistry : KeyRegistry {

    private val deks = mutableMapOf<String, ByteArray>()

    /** Test introspection. */
    fun size(): Int = deks.size

    override suspend fun registerDek(name: String, dekMaterial: ByteArray): Outcome<Unit, KeyRegistryError> {
        deks[name] = dekMaterial.copyOf()
        return Outcome.Success(Unit)
    }

    override suspend fun getDek(name: String): Outcome<ByteArray, KeyRegistryError> {
        val dek = deks[name] ?: return Outcome.Failure(KeyRegistryError.NotFound)
        return Outcome.Success(dek.copyOf())
    }

    override suspend fun hasDek(name: String): Boolean = name in deks
}
