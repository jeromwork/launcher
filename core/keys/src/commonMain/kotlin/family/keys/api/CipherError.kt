package family.keys.api

/**
 * Ошибки [ConfigCipher.seal] / [ConfigCipher.open] (FR-025, FR-026, FR-029).
 *
 *  • [AeadAuthFailed] — tampered ciphertext (MAC mismatch), wrong key, или wrong AAD
 *    (например, attempt'нул open под другим UID — FR-020 identity binding rejection).
 *  • [ConfigTooLarge] — plaintext > 256 KB (FR-029).
 *  • [AlgorithmUnsupported] — SealedConfig.algorithm не recognised — clear separation
 *    от AEAD failure, чтобы UI показал «обновите приложение» а не «неверный пароль».
 *  • [SchemaDowngradeDetected] — SealedConfig.schemaVersion < last seen (TOLU, H-2).
 *  • [InvalidInput] — IO/serialization error при serialize/deserialize blob'а.
 *  • [KeyUnavailable] — DEK не зарегистрирован (recovery flow или OOO).
 */
sealed class CipherError {
    object AeadAuthFailed : CipherError()
    object ConfigTooLarge : CipherError()
    object AlgorithmUnsupported : CipherError()
    object SchemaDowngradeDetected : CipherError()
    object KeyUnavailable : CipherError()
    /** Envelope did not include this device's [DeviceId] as a recipient. */
    object NotARecipient : CipherError()
    data class InvalidInput(val cause: Throwable) : CipherError()
}
