package family.crypto.libsodium

import family.crypto.exception.CryptoException

internal actual object HmacSha256 {
    actual fun mac(key: ByteArray, data: ByteArray): ByteArray =
        throw CryptoException.NotImplementedOnIos(
            "HmacSha256 iOS actual — wire CommonCrypto when V-1 (iOS Admin Preset) ships"
        )
}
