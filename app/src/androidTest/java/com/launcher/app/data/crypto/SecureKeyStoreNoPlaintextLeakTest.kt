package com.launcher.app.data.crypto

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.launcher.adapters.crypto.FileKeyBlobStore
import family.crypto.api.KeyStoreContext
import family.crypto.api.SecureKeyStore
import family.crypto.api.values.KeyId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Spec 016 SC-009 + Сценарий 4: the persisted .blob file MUST NOT contain raw plaintext
 * private key bytes (the wrap pattern's whole point). End-to-end across the real
 * SecureKeyStore (TEE wrap) + the production FileKeyBlobStore (:core), which is why it
 * moved here from :core:crypto in TASK-141 — the file adapter lives above crypto now.
 *
 * Scans the blob file for any 4-byte subsequence of the original secret.
 */
@RunWith(AndroidJUnit4::class)
class SecureKeyStoreNoPlaintextLeakTest {

    private val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = SecureKeyStore(KeyStoreContext(appContext, FileKeyBlobStore(appContext)))
    private val testKeyId = KeyId("__internal-leak-test-v1")

    @After
    fun cleanup() = runTest { store.delete(testKeyId) }

    @Test
    fun blobFileMustNotContainAny4BytePlaintextSubsequence() = runTest {
        val secret = ByteArray(32) { (0x80 + it).toByte() }
        store.store(testKeyId, secret)

        val blobFile = File(appContext.filesDir, "keys/${testKeyId.raw}.blob")
        assert(blobFile.exists()) { "blob file must exist after store()" }
        val blobBytes = blobFile.readBytes()

        for (i in 0..secret.size - 4) {
            val window = secret.copyOfRange(i, i + 4)
            val foundAt = indexOfSubsequence(blobBytes, window)
            assert(foundAt < 0) {
                "plaintext leak: secret[$i..${i + 3}] appeared in blob at offset $foundAt"
            }
        }
    }

    private fun indexOfSubsequence(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
