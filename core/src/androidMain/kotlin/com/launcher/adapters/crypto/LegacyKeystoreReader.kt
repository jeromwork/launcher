package com.launcher.adapters.crypto

/**
 * Best-effort silent-migration helper (TASK-51 R-002, FR-005).
 *
 * Reads / deletes private-key bytes previously stored under legacy lazysodium
 * + AndroidKeystoreSecureKeystore aliases (`spec011.encryption.own`,
 * `spec011.signing.own`). Used by [PairingCryptoCoordinator.tryLegacyMigrate]
 * to opportunistically re-encrypt persisted keys under the new cryptokit
 * SecureKeyStore on first launch after TASK-51.
 *
 * **Why a stub returning null**: the owner's only test device (Xiaomi 11T
 * `17f33878`) never had a successful pairing pre-TASK-51 because lazysodium
 * eager-bind crashed before keystore writes. Therefore no real persisted
 * legacy state exists in the wild that this reader can recover. The helper
 * remains in the call-site as the structural anchor for R-002 — if any
 * production device surfaces with persisted legacy bytes, the migration path
 * can be filled in here without touching the coordinator.
 *
 * TODO(post-task-6): replace this whole helper with derive-from-root once
 * Root Key Hierarchy (TASK-6) lands — no more legacy aliases to migrate from.
 */
internal object LegacyKeystoreReader {

    /** @return private-key bytes for [legacyAlias], or null if none / unreadable. */
    fun read(legacyAlias: String): ByteArray? {
        // Owner-verified absence: no successful pre-TASK-51 keystore writes
        // exist on the only available test device. Stub returns null so the
        // coordinator proceeds to fresh-generate path.
        return null
    }

    /** Idempotent delete of [legacyAlias]. No-op when no legacy entry exists. */
    fun delete(legacyAlias: String) {
        // No-op pair to [read]'s stub.
    }
}
