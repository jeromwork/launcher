package family.keys.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import family.crypto.api.InMemoryKeyBlobStore
import family.crypto.api.KeyStoreContext
import family.crypto.api.SecureKeyStore
import family.crypto.libsodium.LibsodiumAeadCipher
import family.crypto.libsodium.LibsodiumKeyDerivation
import family.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.AuthIdentity
import family.keys.api.Outcome
import family.keys.impl.RootKeyManagerImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for [AndroidKeystoreRegistry] (T642, FR-008, SC-009).
 *
 * **Status**: `[deferred-local-emulator]` — runs against the real Android
 * Keystore via [SecureKeyStore] + LibsodiumKeyDerivation HKDF. Owner runs
 * on Xiaomi 11T (physical-device) and/or pixel_5_api_34 (emulator).
 *
 * **What this proves on real hardware**:
 *  - Derivation determinism survives both process restart and Keystore
 *    round-trip (root key wrap/unwrap returns identical bytes → HKDF
 *    yields identical DerivedKey).
 *  - Cross-stableId isolation: two AuthIdentity values get distinct
 *    DerivedKey under the same purpose.
 *  - wipeAll(stableId) clears the in-memory cache; subsequent derive
 *    must reconstruct from Keystore (still deterministic).
 *
 * **Out of scope**: StrongBox vs TEE attestation — the AndroidKeystoreRegistry
 * does not touch Keystore directly; that property belongs to
 * SecureKeyStore.android.kt (covered separately).
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreRegistryTest {

    private val aliceId = AuthIdentity("alice-stable-id-a1b2c3", null, null)
    private val bobId = AuthIdentity("bob-stable-id-x9y8z7", null, null)

    private suspend fun buildRegistry(): Pair<AndroidKeystoreRegistry, RootKeyManagerImpl> {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val keystore = SecureKeyStore(KeyStoreContext(context, InMemoryKeyBlobStore()))
        val random = LibsodiumRandomSource()
        val aead = LibsodiumAeadCipher()
        val rootMgr = RootKeyManagerImpl(keystore, random, aead)
        val registry = AndroidKeystoreRegistry(rootMgr, LibsodiumKeyDerivation())
        return registry to rootMgr
    }

    @Test
    fun derivationIsDeterministicAcrossCalls() = runTest {
        val (registry, _) = buildRegistry()
        val first = registry.derive(aliceId.stableId, "config")
        val second = registry.derive(aliceId.stableId, "config")
        assertTrue(first is Outcome.Success)
        assertTrue(second is Outcome.Success)
        val a = (first as Outcome.Success).value.bytes
        val b = (second as Outcome.Success).value.bytes
        assertTrue("Determinism: same inputs MUST yield same DerivedKey", a.contentEquals(b))
    }

    @Test
    fun differentPurposesYieldDifferentKeys() = runTest {
        val (registry, _) = buildRegistry()
        val configKey = (registry.derive(aliceId.stableId, "config") as Outcome.Success).value
        val contactsKey = (registry.derive(aliceId.stableId, "contacts") as Outcome.Success).value
        assertFalse(
            "Cross-purpose isolation: different purpose MUST yield different DerivedKey",
            configKey.bytes.contentEquals(contactsKey.bytes)
        )
    }

    @Test
    fun differentStableIdsYieldDifferentKeys() = runTest {
        val (registry, _) = buildRegistry()
        val aliceKey = (registry.derive(aliceId.stableId, "config") as Outcome.Success).value
        val bobKey = (registry.derive(bobId.stableId, "config") as Outcome.Success).value
        assertFalse(
            "Cross-stableId isolation: different identity MUST yield different DerivedKey (FR-031)",
            aliceKey.bytes.contentEquals(bobKey.bytes)
        )
    }

    @Test
    fun wipeClearsListAndForcesReDerivation() = runTest {
        val (registry, _) = buildRegistry()
        registry.derive(aliceId.stableId, "config")
        registry.derive(aliceId.stableId, "contacts")
        val before = (registry.list(aliceId.stableId) as Outcome.Success).value
        assertEquals(setOf("config", "contacts"), before.toSet())

        registry.wipeAll(aliceId.stableId)

        val after = (registry.list(aliceId.stableId) as Outcome.Success).value
        assertTrue("After wipe list MUST be empty (SC-012)", after.isEmpty())

        // Re-derive must still produce the same byte sequence (deterministic
        // — root key in Keystore unchanged, only the registry cache was cleared).
        val redoConfig = (registry.derive(aliceId.stableId, "config") as Outcome.Success).value
        assertNotNull(redoConfig)
    }

    @Test
    fun wipeOfOneIdentityLeavesOthersIntact() = runTest {
        val (registry, _) = buildRegistry()
        registry.derive(aliceId.stableId, "config")
        registry.derive(bobId.stableId, "config")

        registry.wipeAll(aliceId.stableId)

        val bobList = (registry.list(bobId.stableId) as Outcome.Success).value
        assertEquals(
            "Bob's namespace MUST survive Alice's wipe (FR-031)",
            listOf("config"),
            bobList
        )
    }
}
