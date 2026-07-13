package cryptokit.keys.api

/**
 * Opaque wrapper над HKDF output — ключ, выведенный из [RootKey] для конкретной цели
 * (FR-002, data-model.md §4).
 *
 * **Derivation**: `DerivedKey = HKDF(rootKey, salt=stableId, info=purpose)`.
 * Один [RootKey] → N `DerivedKey`s (по одному на каждый `purpose`).
 *
 * **Не toString-able**: переопределённый `toString()` возвращает константу
 * `"DerivedKey(***)"` — случайный `Log.d("dk=$dk")` не утечёт plaintext в logcat.
 *
 * **ByteArray mutability**: как и в [RootKey], `bytes` — not immutable; caller может
 * обнулить буфер через `.fill(0)` после использования. Метод [wipe] предоставлен
 * для явного обнуления.
 *
 * **Размер**: определяется HKDF output length из [KeyRegistry.derive]; обычно
 * 32 байта (256-bit) для симметричных шифров.
 *
 * @see KeyRegistry
 * @see RootKey
 */
class DerivedKey(val bytes: ByteArray) {

    override fun toString(): String = "DerivedKey(***)"

    override fun equals(other: Any?): Boolean =
        other is DerivedKey && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    /**
     * Обнулить underlying buffer in-place. После вызова [bytes] содержит только нули.
     * Должен вызываться после завершения операции, для которой был выведен ключ.
     */
    fun wipe() {
        bytes.fill(0)
    }
}
