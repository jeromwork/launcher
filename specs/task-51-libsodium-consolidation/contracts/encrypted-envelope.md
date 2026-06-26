# Contract: EncryptedEnvelope (TASK-51 namespace migration)

**Wire format**: encrypted blob, передаётся между устройствами через Firestore + Storage (spec 011 FR-030..033)
**Schema version**: `1` (**unchanged** в TASK-51)
**Source spec**: spec 011 (e2e-crypto-foundation)
**Туда переезжает**: `com.launcher.api.crypto.EncryptedEnvelope` → `cryptokit.pairing.api.EncryptedEnvelope`

---

## Byte layout (kotlinx.serialization binary / JSON)

```text
EncryptedEnvelope {
    schemaVersion: 1                         // u32 / "schemaVersion": 1
    recipients: List<Recipient>              // for each peer device — one Recipient
    nonce: ByteArray(24)                     // XChaCha20-Poly1305 nonce
    ciphertext: ByteArray(N)                 // AEAD ciphertext (length = plaintext.length)
    mac: ByteArray(16)                       // Poly1305 MAC
    aad: ByteArray?                          // optional additional authenticated data
}

Recipient {
    deviceId: String                         // UUIDv4 36 chars
    sealedCEK: ByteArray(80)                 // X25519 sealed-box of CEK:
                                             // ephemeralPub(32) || ciphertext(32) || mac(16)
}
```

## Kotlin declaration

```kotlin
package cryptokit.pairing.api

@Serializable
@SerialName("EncryptedEnvelope")
data class EncryptedEnvelope(
    val schemaVersion: Int = 1,
    val recipients: List<Recipient>,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val mac: ByteArray,
    val aad: ByteArray? = null,
)

@Serializable
@SerialName("Recipient")
data class Recipient(
    val deviceId: DeviceId,
    val sealedCEK: ByteArray,
)
```

## Encryption flow

```text
1. Generate CEK (32 bytes) via SecureRandom
2. AEAD-encrypt plaintext под CEK через XChaCha20-Poly1305:
   nonce = random(24)
   (ciphertext, mac) = encrypt(plaintext, CEK, nonce, aad)
3. For each recipient peer:
   sealedCEK = X25519_seal(CEK, recipient.publicKey)   // 80 bytes
4. Zeroize CEK from memory (`cek.close()`)
5. Assemble EncryptedEnvelope { schemaVersion=1, recipients, nonce, ciphertext, mac, aad }
```

## Decryption flow

```text
1. Read EncryptedEnvelope from Storage
2. Find own Recipient by deviceId
3. Decrypt sealedCEK с own X25519 private key:
   CEK = X25519_open_sealed(sealedCEK, ownPriv)
4. AEAD-decrypt ciphertext с CEK + nonce + aad → plaintext
   → on MAC mismatch: throws CryptoException.AeadException
5. Zeroize CEK from memory
```

## Field invariants

| Field | Validation |
|---|---|
| `schemaVersion` | MUST be `1`. Reader rejects unknown versions. |
| `recipients` | At least 1, at most ~10 (spec 011 small group, future spec 014 для больших групп). |
| `recipients[i].deviceId` | UUIDv4 36 chars. |
| `recipients[i].sealedCEK` | Exactly 80 bytes. |
| `nonce` | Exactly 24 bytes (XChaCha20). MUST be unique per (CEK, message). |
| `ciphertext` | Exact match plaintext length (XChaCha20 is stream cipher). |
| `mac` | Exactly 16 bytes (Poly1305). |
| `aad` | Optional. If present, MUST match exactly at decrypt-time. |

## Security properties

- **Confidentiality**: AEAD encryption под CEK (XChaCha20-Poly1305).
- **Per-recipient access**: каждый peer имеет свою `sealedCEK` копию (1-to-N broadcast model).
- **Integrity**: Poly1305 MAC over ciphertext + aad (любой tamper → AeadException at decrypt).
- **No sender-identity**: sealed-box (anonymous) — recipient не знает кто отправил. Identity передаётся через **отдельный** signed payload в parent envelope (если applicable).

## Namespace migration risk

См. `device-identity.md` — те же риски. `@SerialName("EncryptedEnvelope")` и `@SerialName("Recipient")` обязательны.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Контракт `EncryptedEnvelope` — формат зашифрованного контейнера для 1-to-N broadcast (один отправитель шифрует один payload, каждый peer-получатель имеет свою копию CEK seald под свой публичный ключ). В TASK-51 переезжает в `cryptokit.pairing.api.*` без изменения byte-layout.

**Конкретика, которую стоит запомнить:**
- **Структура**: `schemaVersion=1`, `recipients[]` (по одному на peer), `nonce(24)`, `ciphertext`, `mac(16)`, `aad?`.
- **`Recipient`**: `deviceId` (UUIDv4) + `sealedCEK` (80 байт: ephemeralPub32 + ct32 + mac16).
- **Crypto**: XChaCha20-Poly1305 AEAD под CEK; CEK раздаётся через X25519 sealed-box каждому recipient'у.
- **`@SerialName` обязателен** на `EncryptedEnvelope` и `Recipient` — без них namespace rename сломает совместимость.
- **Zeroize CEK после encrypt/decrypt** — security pattern, не потерять при rewrite.

**На что смотреть с осторожностью:**
- **Nonce uniqueness** — критично для XChaCha20-Poly1305. Любой repeat под одним CEK = catastrophic key recovery attack. Random nonce достаточен (24 байта).
- **Sealed-box anonymity** — отправитель неизвестен по design. Если нужна authentication отправителя — это **отдельный signed payload**, не часть `EncryptedEnvelope`.
- **Group size growth** — текущий формат O(N) на recipients. Для больших групп (>10) — spec 014 / TASK-42 (MLS).
