package family.pairing.api

// TASK-141 — this crypto module carries no schema version (rule 1 crypto
// exception). SUPPORTED_SCHEMA_VERSION moved out to the adapters that own each
// wire format (e.g. FirestoreDeviceIdentityRepository.WIRE_SCHEMA_VERSION). The
// EncryptedEnvelope-only constants (CIPHER_SUITE_ID_V1 / XCHACHA20_NONCE_SIZE /
// POLY1305_MAC_SIZE / SEALED_CEK_SIZE) were removed with that wire format. The
// remaining constants are key/signature sizes used by the libsodium adapters.
const val X25519_KEY_SIZE: Int = 32
const val ED25519_KEY_SIZE: Int = 32
const val ED25519_SIGNATURE_SIZE: Int = 64
const val HASH_OUTPUT_SIZE: Int = 32
const val CEK_SIZE: Int = 32
