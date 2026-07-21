package family.pairing.api

const val SUPPORTED_SCHEMA_VERSION: Int = 1

// TASK-141 — CIPHER_SUITE_ID_V1 / XCHACHA20_NONCE_SIZE / POLY1305_MAC_SIZE /
// SEALED_CEK_SIZE were consumed only by the removed EncryptedEnvelope + Recipient
// wire format. The remaining constants are key/signature sizes still used by the
// libsodium adapters and pairing.
const val X25519_KEY_SIZE: Int = 32
const val ED25519_KEY_SIZE: Int = 32
const val ED25519_SIGNATURE_SIZE: Int = 64
const val HASH_OUTPUT_SIZE: Int = 32
const val CEK_SIZE: Int = 32
