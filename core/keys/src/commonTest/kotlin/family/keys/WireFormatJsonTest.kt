package family.keys

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * After TASK-141, no `:core:keys` type is @Serializable — both wire formats that
 * used to live here (Envelope, RecoveryKeyBackupBlob) are now pure crypto types
 * whose wire lives in adapters:
 *  • Envelope → `FirestoreEnvelopeStorage` + `EnvelopeConfigCipherRoundtripTest`
 *    + `EnvelopeBackwardCompatTest`.
 *  • RecoveryKeyBackupBlob → `RecoveryBlobJsonCodec` (:app) + `RecoveryBlobJsonCodecTest`.
 *
 * What remains here is the RootKey secret-masking check (not a wire test).
 */
class WireFormatJsonTest {

    @Test
    fun rootKeyToStringDoesNotLeakBytes() {
        val rk = family.keys.api.RootKey(ByteArray(32) { 0xAB.toByte() })
        val str = rk.toString()
        assertTrue("***" in str, "RootKey.toString must mask bytes")
        assertTrue("ab" !in str.lowercase(), "RootKey.toString must NOT leak hex bytes")
    }
}
