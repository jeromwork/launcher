package family.keys.api

/**
 * Sealed error type returned by the envelope cipher
 * ([family.keys.api.internal.ConfigCipher2]).
 *
 * Surfaces at adapter boundaries only — caller-side code does not see this type;
 * [family.keys.api.RemoteStorage] / [family.keys.api.ConfigSaver] translate
 * these into [StorageError].
 *
 *  - [AeadAuthFailed] — tampered ciphertext, wrong key, or AAD mismatch
 *    (context-confusion defence). Surfaces identically for all three causes
 *    so the failure mode is indistinguishable to an attacker.
 *  - [ConfigTooLarge] — plaintext > 256 KB.
 *  - [AlgorithmUnsupported] — envelope.algorithm or schemaVersion not
 *    understood by this build; UI surfaces "please update" instead of
 *    crypto-failure.
 *  - [SchemaDowngradeDetected] — envelope.schemaVersion < last-seen
 *    (Trust-On-Last-Use, H-2 defence).
 *  - [KeyUnavailable] — caller has no signed-in identity / device keypair.
 *  - [NotARecipient] — this device's [DeviceId] is not in
 *    [Envelope.recipientKeys]; either it was excluded at write time or
 *    the device is post-reinstall with a new identity.
 *  - [InvalidInput] — IO / serialization error, malformed wire-format.
 */
sealed class CipherError {
    object AeadAuthFailed : CipherError()
    object ConfigTooLarge : CipherError()
    object AlgorithmUnsupported : CipherError()
    object SchemaDowngradeDetected : CipherError()
    object KeyUnavailable : CipherError()
    object NotARecipient : CipherError()
    data class InvalidInput(val cause: Throwable) : CipherError()
}
