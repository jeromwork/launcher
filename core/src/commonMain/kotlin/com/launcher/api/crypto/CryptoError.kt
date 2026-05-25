package com.launcher.api.crypto

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Typed error hierarchy. Все криптооперации возвращают Outcome<T, CryptoError>,
// никогда не throw. Sub-cases несут categorical metadata (alias / uuid / deviceId / cause),
// но никаких plaintext / key bytes.
sealed interface CryptoError {
    data class KeyNotFound(val alias: String, val cause: Throwable? = null) : CryptoError

    @OptIn(ExperimentalUuidApi::class)
    data class MacFailed(val uuid: Uuid? = null) : CryptoError

    @OptIn(ExperimentalUuidApi::class)
    data class BlobMissing(val uuid: Uuid) : CryptoError

    data class CipherSuiteUnsupported(val suiteId: String) : CryptoError

    data class RecipientNotFound(val deviceId: DeviceId) : CryptoError

    data class SignatureVerifyFailed(val deviceId: DeviceId? = null, val reason: String? = null) : CryptoError

    @OptIn(ExperimentalUuidApi::class)
    data class MalformedEnvelope(val uuid: Uuid? = null, val cause: Throwable? = null) : CryptoError

    data class StorageFailure(val cause: Throwable) : CryptoError

    data class KeystoreFailure(val cause: Throwable) : CryptoError
}
