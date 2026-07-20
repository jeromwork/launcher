package family.pairing.api

const val SUPPORTED_SCHEMA_VERSION: Int = 1

const val CIPHER_SUITE_ID_V1: String = "xchacha20poly1305_x25519_sealed_v1"

const val XCHACHA20_NONCE_SIZE: Int = 24

const val POLY1305_MAC_SIZE: Int = 16

const val X25519_KEY_SIZE: Int = 32
const val ED25519_KEY_SIZE: Int = 32
const val ED25519_SIGNATURE_SIZE: Int = 64
const val SEALED_CEK_SIZE: Int = 80
const val HASH_OUTPUT_SIZE: Int = 32
const val CEK_SIZE: Int = 32
