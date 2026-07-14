package cryptokit.keys.impl.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cryptokit.keys.api.vault.Purpose
import cryptokit.keys.api.vault.RecoveryStrategy
import cryptokit.keys.api.vault.canonicalAad
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import cryptokit.crypto.api.KeyStoreContext
import cryptokit.crypto.api.SecureKeyStore

/**
 * FR-011 / SC-004 cross-platform vector parity — same inputs on Android/libsodium-via-JNI
 * MUST produce byte-equal output with the JVM run of
 * `cryptokit.keys.vault.CrossPlatformVectorTest`.
 *
 * ### Phase-1 scope (this file)
 * Roundtrip proof only. Freezing expected bytes into `resources/vectors/v1.json` is deferred to
 * phase-2 finalisation once the JVM+Android runs have been executed side-by-side and the
 * outputs manually cross-checked.
 */
private class FixedRootStrategy(private val root: ByteArray) : RecoveryStrategy() {
    override suspend fun deriveRoot(): ByteArray = root.copyOf()
    override suspend fun verifyUnlock(candidateRoot: ByteArray) { /* no-op for parity test */ }
}

@RunWith(AndroidJUnit4::class)
class CrossPlatformVectorAndroidTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() = runBlocking {
        val ks = SecureKeyStore(KeyStoreContext(context.applicationContext))
        try { ks.delete(cryptokit.crypto.api.values.KeyId(AndroidKeyVault.ROOT_KEY_ID)) } catch (_: Throwable) {}
        context.getSharedPreferences(AndroidValidationBlobStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test
    fun vector_v1_seal_open_roundtrip_on_android() = runBlocking {
        val vault = AndroidKeyVault(context.applicationContext)
        // Pre-seed a validation blob under a known root would require deeper hooks; for the
        // parity test we just prove that on the same libsodium primitives the roundtrip holds.
        // Full byte-for-byte comparison with JVM is done in the phase-2 vector-freeze pass.
        vault.unlock(FixedRootStrategy(ByteArray(32) { it.toByte() }))
        val aad = canonicalAad("ns1", 1, 1)
        val ct = vault.aeadSeal(Purpose.CONFIG, "hello".encodeToByteArray(), aad)
        val out = vault.aeadOpen(Purpose.CONFIG, ct, aad)
        assertArrayEquals("hello".encodeToByteArray(), out)
    }
}
