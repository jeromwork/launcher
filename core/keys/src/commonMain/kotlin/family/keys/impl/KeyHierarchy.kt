package family.keys.impl

import family.crypto.api.AeadCipher
import family.crypto.api.RandomSource
import family.crypto.api.SecureKeyStore
import family.keys.api.AuthIdentity
import family.keys.api.KeyRegistry
import family.keys.api.Outcome
import family.keys.api.RootKey
import family.keys.api.RootKeyError
import family.keys.api.RootKeyManager

/**
 * Factory + bootstrap helper для F-5 ports. Связывает [RootKeyManagerImpl] и
 * [KeyRegistryImpl] в единую иерархию и решает chicken-and-egg dependency
 * (KeyRegistry нужен RootKey, который создаётся RootKeyManager'ом).
 *
 * Также реализует **auto-register `config-cipher-aead-v1` DEK** (FR-015) при
 * первом getOrCreate root key.
 *
 * Использование (в DI / тестах):
 * ```
 * val hierarchy = KeyHierarchy(uid, secureKeyStore, aead, random)
 * hierarchy.rootKeyManager.getOrCreate(identity) // создаёт root + auto-registers config DEK
 * val cipher = AeadConfigCipherImpl(aead, hierarchy.keyRegistry)
 * ```
 */
class KeyHierarchy(
    private val uid: String,
    secureKeyStore: SecureKeyStore,
    private val aead: AeadCipher,
    private val random: RandomSource
) {
    init {
        require(uid.isNotEmpty())
    }

    val rootKeyManager: RootKeyManager = RootKeyManagerImpl(secureKeyStore, random, aead)

    val keyRegistry: KeyRegistry = KeyRegistryImpl(
        uid = uid,
        rootKeyProvider = ::cachedRootKey,
        secureKeyStore = secureKeyStore,
        aead = aead
    )

    private var cachedRoot: RootKey? = null

    private suspend fun cachedRootKey(): RootKey? = cachedRoot

    /**
     * One-shot bootstrap: getOrCreate root + auto-register `config-cipher-aead-v1` DEK
     * если ещё не зарегистрирован.
     *
     * Идемпотентно: повторный вызов не пересоздаёт root, не перерегистрирует DEK.
     */
    suspend fun bootstrap(identity: AuthIdentity): Outcome<RootKey, RootKeyError> {
        require(identity.stableId == uid) {
            "KeyHierarchy bound to uid '$uid' but bootstrap called with identity.stableId='${identity.stableId}' (FR-031 identity isolation)"
        }
        val rootOutcome = rootKeyManager.getOrCreate(identity)
        if (rootOutcome is Outcome.Success) {
            cachedRoot = rootOutcome.value
            // Auto-register config DEK если не существует (FR-015).
            if (!keyRegistry.hasDek(AeadConfigCipherImpl.DEK_NAME)) {
                val freshDek = random.nextBytes(KeyRegistryImpl.DEK_SIZE)
                try {
                    keyRegistry.registerDek(AeadConfigCipherImpl.DEK_NAME, freshDek)
                } finally {
                    freshDek.fill(0)
                }
            }
        }
        return rootOutcome
    }
}
