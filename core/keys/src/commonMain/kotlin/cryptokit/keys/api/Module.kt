package cryptokit.keys.api

/**
 * Marker for the F-5 + F-5b public API surface of `:core:keys`.
 *
 * Caller-facing ports:
 *  - [RemoteStorage] — namespace+key encrypted storage facade. All envelope
 *    encryption / multi-recipient handling is hidden behind this surface.
 *  - [ConfigSaver] — product-domain convenience wrapper around
 *    [RemoteStorage] (`saveOwn / loadOwn / saveForOther / loadForOther`).
 *  - [EnvelopeBootstrap] — post-Sign-In publication of this device's public
 *    key into the directory.
 *  - [RootKeyManager] — root key per [AuthIdentity], wraps via
 *    [family.crypto.api.SecureKeyStore]. Used by recovery flow.
 *  - [IdentityProof] — abstraction over F-4 AuthProvider.
 *  - [RecoveryKeyBackup] — Firestore-backed passphrase-encrypted root key
 *    backup.
 *
 * Internal-to-keys SPI (backend adapter contracts):
 *  - [cryptokit.keys.api.internal.ConfigCipher2] — hybrid envelope crypto.
 *  - [cryptokit.keys.api.internal.RecipientResolver].
 *  - [cryptokit.keys.api.internal.EnvelopeStorage].
 *  - [cryptokit.keys.api.internal.PublicKeyDirectory].
 *  - [cryptokit.keys.api.internal.DeviceIdentity].
 *
 * All cryptographic primitives are reused from `:core:crypto` (F-CRYPTO).
 * No vendor SDK / Firebase / Compose dependencies live here — those are
 * adapters in `:app`.
 */
internal object Module
