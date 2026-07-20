package family.crypto.libsodium

import family.crypto.exception.CryptoException

/**
 * TODO(pre-release-audit): iOS HMAC-SHA256 — заменить на CCHmac(kCCHmacAlgSHA256, ...)
 * из CommonCrypto (системная iOS библиотека, нулевые зависимости).
 * См. docs/dev/crypto-review.md §A1.
 */
internal actual object HmacSha256 {
    actual fun mac(key: ByteArray, data: ByteArray): ByteArray =
        throw CryptoException.NotImplementedOnIos(
            "HmacSha256 iOS actual — wire CommonCrypto when V-1 (iOS Admin Preset) ships"
        )
}
