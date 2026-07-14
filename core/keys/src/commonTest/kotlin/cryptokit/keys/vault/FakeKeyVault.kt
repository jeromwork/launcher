package cryptokit.keys.vault

import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.AsymmetricCrypto
import cryptokit.crypto.api.KeyDerivation
import cryptokit.crypto.libsodium.LibsodiumAeadCipher
import cryptokit.crypto.libsodium.LibsodiumAsymmetricCrypto
import cryptokit.crypto.libsodium.LibsodiumKeyDerivation
import cryptokit.keys.api.vault.KeyVault
import cryptokit.keys.impl.vault.InMemoryValidationBlobStore
import cryptokit.keys.impl.vault.KeyVaultCore
import cryptokit.keys.impl.vault.ValidationBlobStore

/**
 * Deterministic in-memory [KeyVault] for `:core:keys` port-contract tests and downstream
 * feature tests (Rule 6 mock-first + FR-014).
 *
 * All crypto ops use REAL libsodium (via `:core:crypto`) — the "fake" bit is that `root_key`
 * lives in an in-memory field, not in Android Keystore. This keeps the wire format identical
 * to the future Android adapter — cross-platform vectors work byte-equally (SC-004).
 */
class FakeKeyVault(
    aead: AeadCipher = LibsodiumAeadCipher(),
    kdf: KeyDerivation = LibsodiumKeyDerivation(),
    asym: AsymmetricCrypto = LibsodiumAsymmetricCrypto(),
    store: ValidationBlobStore = InMemoryValidationBlobStore(),
) : KeyVault by KeyVaultCore(aead, kdf, asym, store)
