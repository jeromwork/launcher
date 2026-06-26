package cryptokit.pairing.api

// Resolves recipients for a given link. В 011 — 1 entry (peer device).
// Single-impl exception документирован Article XVII §3 / spec.md C-8 —
// 3 будущие реализации в спеках 013/014/015.
fun interface RecipientResolver {
    suspend fun resolveRecipients(linkId: String): List<DeviceIdentity>
}
