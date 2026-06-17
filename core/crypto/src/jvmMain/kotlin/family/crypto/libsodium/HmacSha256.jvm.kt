package family.crypto.libsodium

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal actual object HmacSha256 {
    actual fun mac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // RFC 2104: if key is empty, HMAC still defined — JCA rejects zero-length SecretKeySpec.
        // RFC 5869 §2.2 lets `salt` be empty (HKDF uses `HashLen` zero bytes); that case is
        // handled in [LibsodiumKeyDerivation.derive] before reaching here.
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
