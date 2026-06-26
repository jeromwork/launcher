# Contract: Ciphertext (TASK-51 namespace rename)

**Wire format**: low-level AEAD encryption result, internal to `cryptokit.crypto.*`
**Schema version**: implicit (raw byte concatenation, no JSON wrapper)
**Source spec**: spec 016 (F-CRYPTO)
**Туда переезжает**: `family.crypto.api.values.Ciphertext` → `cryptokit.crypto.api.values.Ciphertext`

---

## Byte layout

`Ciphertext` — value class над `ByteArray` который содержит **concatenated**:

```text
nonce(24) || ciphertext(N) || mac(16)
```

То есть raw bytes без дополнительных заголовков. Длина = `24 + N + 16`.

## Kotlin declaration

```kotlin
package cryptokit.crypto.api.values

@JvmInline
value class Ciphertext(val bytes: ByteArray) {
    init {
        require(bytes.size >= 24 + 16) { "Ciphertext must be at least 40 bytes (nonce+mac)" }
    }
}
```

**Note**: не `@Serializable` — это internal binary blob, не передаётся через JSON. Используется как **возврат** из AEAD encrypt и **аргумент** AEAD decrypt.

## Field invariants

| Field | Validation |
|---|---|
| `bytes[0..23]` | Nonce (24 bytes, XChaCha20) |
| `bytes[24..(size-17)]` | Ciphertext |
| `bytes[(size-16)..]` | Poly1305 MAC (16 bytes) |
| `bytes.size` | ≥ 40 (минимум nonce + empty ciphertext + mac) |

## Encryption / Decryption

```kotlin
package cryptokit.crypto.api

interface AeadCipher {
    /** @throws CryptoException.AeadException on encrypt failure */
    suspend fun encrypt(plaintext: ByteArray, key: ByteArray, aad: ByteArray = ByteArray(0)): Ciphertext

    /** @throws CryptoException.AeadException on MAC mismatch or invalid layout */
    suspend fun decrypt(ciphertext: Ciphertext, key: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray
}
```

## Namespace migration risk

**Low.** `Ciphertext` — value class, used internally. Не сериализуется через kotlinx.serialization. Single rename `family.* → cryptokit.*` через find-replace.

Verification: `EnvelopeConfigCipherRoundtripTest` использует `AeadCipher.encrypt/decrypt` → `Ciphertext` — golden vectors остаются byte-equal (SC-013).

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Internal-use byte container для результата AEAD-шифрования (XChaCha20-Poly1305). Concatenation `nonce(24) || ciphertext || mac(16)`. Один value class в `cryptokit.crypto.api.values`. Не сериализуется через JSON.

**Конкретика, которую стоит запомнить:**
- **40 байт минимум** (24 nonce + 16 MAC + 0 ciphertext).
- **Не `@Serializable`** — namespace rename safe через простой find-replace.
- **Возврат `encrypt()` и аргумент `decrypt()`** — единственное где появляется.
- **Используется `EnvelopeConfigCipherRoundtripTest`** (golden vectors) → SC-013 covers binary compat.

**На что смотреть с осторожностью:**
- **Init validator** `bytes.size >= 40` — нельзя ослабить (защищает от malformed AeadException downstream).
- **MAC расположен в конце**, не в начале. Если кто-то добавит «магическую цифру» в начало — сломает existing data. Не трогать layout.
