package family.crypto.libsodium

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal actual object HmacSha256 {
    actual fun mac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
