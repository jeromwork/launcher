package cryptokit.pairing.api

// Symmetric key для одного blob'a (XChaCha20-Poly1305). Bytes стираются в close()
// — Kotlin idiom: использовать через use { } extension. NOT Serializable.
class ContentEncryptionKey(internal val bytes: ByteArray) : AutoCloseable {
    private var closed: Boolean = false

    init {
        require(bytes.size == CEK_SIZE) { "CEK must be $CEK_SIZE bytes, got ${bytes.size}" }
    }

    internal fun bytesOrThrow(): ByteArray {
        check(!closed) { "CEK already closed (zeroized)" }
        return bytes
    }

    override fun close() {
        if (!closed) {
            bytes.fill(0)
            closed = true
        }
    }
}

inline fun <T> ContentEncryptionKey.use(block: (ContentEncryptionKey) -> T): T =
    try { block(this) } finally { this.close() }
