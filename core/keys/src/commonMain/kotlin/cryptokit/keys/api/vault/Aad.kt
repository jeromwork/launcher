package cryptokit.keys.api.vault

import kotlin.jvm.JvmInline

/**
 * Associated Authenticated Data for [KeyVault.aeadSeal] / [KeyVault.aeadOpen].
 *
 * MUST use the canonical length-prefixed layout produced by [canonicalAad]. Callers who need
 * to pass raw pre-encoded AAD (for cross-platform vector tests) can construct via the
 * primary constructor, but MUST NOT hand-craft new layouts — that defeats tamper detection
 * (SC-007).
 */
@JvmInline
value class Aad(val bytes: ByteArray)

/**
 * Canonical AAD layout per FR-004:
 *
 * ```
 * namespace_id_len (2b BE) || namespace_id_bytes || schema_version (2b BE) || blob_version (2b BE)
 * ```
 *
 * @param namespaceId opaque namespace identifier (typically the `nsId` from the config-sync
 *   layer — never a raw Google `sub` / phone number per CLAUDE.md rule 13).
 * @param schemaVersion wire-format version of the *plaintext* payload structure (independent of
 *   the vault blob's own `format_version` in [BlobHeader]).
 * @param blobVersion monotonic version of the individual blob (protects against downgrade /
 *   replay of older blobs under the same key epoch).
 */
fun canonicalAad(namespaceId: String, schemaVersion: Int, blobVersion: Int): Aad {
    val nsBytes = namespaceId.encodeToByteArray()
    require(nsBytes.size <= 0xFFFF) { "namespaceId too long: ${nsBytes.size} > 65535 bytes" }
    require(schemaVersion in 0..0xFFFF) { "schemaVersion out of range: $schemaVersion" }
    require(blobVersion in 0..0xFFFF) { "blobVersion out of range: $blobVersion" }

    val out = ByteArray(2 + nsBytes.size + 2 + 2)
    var p = 0
    out[p++] = ((nsBytes.size ushr 8) and 0xFF).toByte()
    out[p++] = (nsBytes.size and 0xFF).toByte()
    nsBytes.copyInto(out, p); p += nsBytes.size
    out[p++] = ((schemaVersion ushr 8) and 0xFF).toByte()
    out[p++] = (schemaVersion and 0xFF).toByte()
    out[p++] = ((blobVersion ushr 8) and 0xFF).toByte()
    out[p] = (blobVersion and 0xFF).toByte()
    return Aad(out)
}
