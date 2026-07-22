package family.pairing.api

// Internal in-memory реализации sealed PrivateKey/SigningPrivateKey интерфейсов.
// Используются:
//   1) Fake-адаптерами в тестах (commonTest) — нельзя extend sealed в другом
//      compilation unit, поэтому реализации живут здесь под `internal`.
//   2) В будущем (если понадобится) — JVM-only path или demo-сборки без Keystore.
//
// `internal` модификатор гарантирует, что extraction-paths не утекают за модуль
// core. CLAUDE.md rule 1 (opaque PrivateKey) сохранён: ни один out-of-module
// caller не может получить bytes.
//
// Реальная Android Keystore-backed имплементация — отдельная (in adapter
// module under androidMain в Phase 3), также `internal`.

internal data class InMemoryPrivateKey(
    override val alias: String,
    internal val bytes: ByteArray,
) : PrivateKey {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InMemoryPrivateKey) return false
        return alias == other.alias && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * alias.hashCode() + bytes.contentHashCode()
}

internal data class InMemorySigningPrivateKey(
    override val alias: String,
    internal val bytes: ByteArray,
) : SigningPrivateKey {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InMemorySigningPrivateKey) return false
        return alias == other.alias && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * alias.hashCode() + bytes.contentHashCode()
}
