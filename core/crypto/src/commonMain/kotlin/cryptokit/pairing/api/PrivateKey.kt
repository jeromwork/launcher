package cryptokit.pairing.api

// Opaque handle к приватному X25519 ключу. Реальные bytes резидентны в Android Keystore
// (через AES-wrap strategy — X25519 не поддерживается Keystore нативно, см. ADR-007).
// NOT Serializable. NOT extractable. Никаких bytes accessor'ов.
sealed interface PrivateKey {
    val alias: String
}
