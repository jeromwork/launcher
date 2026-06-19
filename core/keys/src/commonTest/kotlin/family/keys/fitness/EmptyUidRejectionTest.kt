package family.keys.fitness

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import family.crypto.api.KeyStoreContext
import family.crypto.api.SecureKeyStore
import family.crypto.libsodium.LibsodiumAeadCipher
import family.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.AuthIdentity
import family.keys.impl.KeyHierarchy
import family.keys.impl.KeyRegistryImpl
import family.keys.impl.RootKeyManagerImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Fitness function — H-4 finding (T122d).
 *
 * Empty uid / AuthIdentity.stableId MUST быть отвергнут на construction time,
 * чтобы избежать cross-UID alias formation race в KeyRegistryImpl.
 *
 * Если бы пустой UID был разрешён — все DEK'и пользователей без stableId
 * попадали бы в один namespace `config-dek--{name}` (double-hyphen), и
 * первый бы видел DEK'и второго.
 */
class EmptyUidRejectionTest {

    @Test
    fun keyHierarchyRejectsEmptyUid() = runTest {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        assertFailsWith<IllegalArgumentException> {
            KeyHierarchy(
                uid = "",
                secureKeyStore = SecureKeyStore(KeyStoreContext()),
                aead = LibsodiumAeadCipher(),
                random = LibsodiumRandomSource()
            )
        }
    }

    @Test
    fun keyRegistryImplRejectsEmptyUid() = runTest {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        assertFailsWith<IllegalArgumentException> {
            KeyRegistryImpl(
                uid = "",
                rootKeyProvider = { null },
                secureKeyStore = SecureKeyStore(KeyStoreContext()),
                aead = LibsodiumAeadCipher()
            )
        }
    }

    @Test
    fun rootKeyManagerRejectsEmptyStableId() = runTest {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        val mgr = RootKeyManagerImpl(
            secureKeyStore = SecureKeyStore(KeyStoreContext()),
            random = LibsodiumRandomSource(),
            aead = LibsodiumAeadCipher()
        )
        assertFailsWith<IllegalArgumentException> {
            mgr.getOrCreate(AuthIdentity("", null, null))
        }
    }
}
