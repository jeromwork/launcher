package cryptokit.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import cryptokit.crypto.api.values.KeyId
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 016 FR-010 edge case: KeyId construction with an unknown namespace prefix
 * fails at compile-time-equivalent (value class `init`), not at store/load time.
 */
@RunWith(AndroidJUnit4::class)
class SecureKeyStoreInvalidPrefixTest {

    @Test(expected = IllegalArgumentException::class)
    fun keyIdWithoutKnownPrefixThrows() {
        KeyId("photo-album-v1")
    }
}
