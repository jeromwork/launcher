package family.pairing.api

// Opaque handle к приватному Ed25519 ключу. API 31+ — native Keystore Ed25519;
// API 30 — AES-wrap fallback (см. ADR-007). NOT Serializable. NOT extractable.
sealed interface SigningPrivateKey {
    val alias: String
}
