package family.keys.api

/**
 * Opaque ownership-wrapper над 32 байтами root key material (FR-013).
 *
 * **Не toString-able**: переопределённый `toString()` возвращает константу
 * `"RootKey(***)"` чтобы случайный `Log.d("rk=$rootKey")` не утёк plaintext в logcat.
 *
 * Сама ByteArray — не immutable; caller, держащий ссылку, может её модифицировать
 * (включая обнуление). Это намеренно: F-5 wrap/unwrap operations должны иметь
 * возможность обнулить буфер после использования.
 *
 * Per CLAUDE.md rule 4 (MVA) — не value class из-за `bytes: ByteArray`
 * (Kotlin value class не разрешает ByteArray в backing field на JVM до K2 stable
 * inline classes). Если в будущем targeting K2 stable + Kotlin 2.0+ permits —
 * можно конвертировать в value class.
 */
class RootKey(val bytes: ByteArray) {
    init {
        require(bytes.size == SIZE) {
            "RootKey must be exactly $SIZE bytes, got ${bytes.size}"
        }
    }

    override fun toString(): String = "RootKey(***)"

    override fun equals(other: Any?): Boolean =
        other is RootKey && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    /**
     * Обнулить underlying buffer in-place. После вызова [bytes] содержит только
     * нули; повторный wrap/unwrap с тем же экземпляром невозможен.
     */
    fun wipe() {
        bytes.fill(0)
    }

    companion object {
        const val SIZE: Int = 32
    }
}
