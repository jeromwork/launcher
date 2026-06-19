package family.keys.api

/**
 * Marker for the F-5 (spec 018) public API surface of `:core:keys`.
 *
 * This module exposes:
 *  • [RootKeyManager] — root key per [family.keys.api.AuthIdentity], wraps via SecureKeyStore.
 *  • [KeyRegistry] — DEK registry partitioned by identity.
 *  • [IdentityProof] — abstraction over F-4 AuthProvider.
 *  • [RecoveryKeyVault] — Firestore-backed passphrase-encrypted root key vault.
 *  • [ConfigCipher] — XChaCha20-Poly1305 encryption for ConfigDocument.
 *
 * All cryptographic primitives are reused from `:core:crypto` (F-CRYPTO). No
 * vendor SDK / Firebase / Compose dependencies live here — those are adapters
 * in `:app`.
 */
internal object Module
