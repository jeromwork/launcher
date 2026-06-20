# Contract: `ConfigCipher` port + `SealedConfig` wire-format v1 (LEGACY)

> ## ⚠️ Deprecated — replaced by [envelope-v1.md](envelope-v1.md) on 2026-06-20
>
> Этот контракт описывает **legacy** single-recipient symmetric pattern,
> который был удалён в commit `d135216` (Batch 6). Реальная wire-format'а
> сейчас — `Envelope` per [envelope-v1.md](envelope-v1.md).
>
> Файл сохранён только для трассируемости commits до pivot'а 2026-06-20.
> Не используйте этот контракт в новой работе.

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **FRs**: FR-015, FR-016, FR-017, FR-018, FR-019, FR-020, FR-029, FR-032

Первый потребитель `KeyRegistry` — порт шифрования ConfigDocument'а перед отправкой в Firestore.

---

## Kotlin declaration (commonMain)

```kotlin
package com.launcher.api.keys.api

public interface ConfigCipher {

    /**
     * Шифрует ConfigDocument под DEK "config-cipher-aead-v1" из KeyRegistry.
     *
     * @param configBytes сериализованный ConfigDocument (JSON). MUST не превышать 256 KiB (FR-029).
     * @param uid текущая identity для AAD binding (FR-020).
     * @return SealedConfig — wire-format envelope, ready to push в Firestore.
     */
    public suspend fun seal(
        configBytes: ByteArray,
        uid: String,
    ): Outcome<SealedConfig, CryptoError>

    /**
     * Расшифровывает SealedConfig. Валидирует AAD (uid должен совпадать с текущим).
     *
     * @return Outcome.Success(configBytes) или CryptoError.AeadAuthFailed при tampering.
     */
    public suspend fun open(
        sealed: SealedConfig,
        uid: String,
    ): Outcome<ByteArray, CryptoError>
}

// CryptoError re-uses F-CRYPTO sealed type (FR-025, FR-026):
// - CryptoError.AeadAuthFailed
// - CryptoError.ConfigTooLarge  (NEW в F-5, или добавляется в F-CRYPTO сейчас?)
// - CryptoError.AlgorithmUnsupported

// TODO(capability-registry): ConfigCipher.open exposes ConfigDocument to potential AI
// affordance layer — AI must run client-side only, never server-side processing.

// TODO(future-spec algorithm-migration): когда XChaCha20-Poly1305 устаревает —
// отдельная спека: (a) re-derive root key, (b) re-wrap DEKs, (c) re-encrypt cloud blobs,
// (d) backward-compat read for not-yet-migrated users. См. SRV-CRYPTO-008.
```

---

## Wire-format: `SealedConfig`

### Kotlin shape

```kotlin
public data class SealedConfig(
    val schemaVersion: Int,
    val algorithm: String,
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val aad: ByteArray,
    val recipientMasterSignature: ByteArray? = null,
)
```

### JSON example

```json
{
  "schemaVersion": 1,
  "algorithm": "xchacha20poly1305-v1",
  "ciphertext": "ZmFrZS1jaXBoZXJ0ZXh0LWZvci1pbGx1c3RyYXRpb24tb25seQ==",
  "nonce": "B+gWQpA9Fk1tR8nXmYzQwLpEsT2cD5fH",
  "aad": "eyJ1aWQiOiJ4S20wMTAxIiwic2NoZW1hVmVyc2lvbiI6MX0=",
  "recipientMasterSignature": null
}
```

`aad` content (decoded из Base64):
```json
{"uid":"xKm0101","schemaVersion":1}
```

### Field semantics

| Field                       | Type        | Notes                                                                            |
|-----------------------------|-------------|----------------------------------------------------------------------------------|
| `schemaVersion`             | Int         | currently `1`.                                                                    |
| `algorithm`                 | String      | `"xchacha20poly1305-v1"`. String для additive future algorithms.                  |
| `ciphertext`                | bytes       | ConfigDocument (JSON), encrypted AEAD под DEK `config-cipher-aead-v1`.            |
| `nonce`                     | bytes       | 24 bytes для XChaCha20-Poly1305 (random per seal).                                |
| `aad`                       | bytes       | Associated Authenticated Data — JSON `{"uid":"...", "schemaVersion":N}` (FR-020). |
| `recipientMasterSignature`  | bytes?      | **Nullable**. MVP всегда `null`. Future cross-signing задел (FR-017).             |

### Backward-compat rules
- `recipientMasterSignature` начинает как nullable → future spec заполняет → additive (без bump'а schemaVersion).
- Adding новое optional поле — additive.
- Removing / renaming — bump schemaVersion.

---

## Semantics

### seal
- **Pre**: `KeyRegistry.hasDek("config-cipher-aead-v1")` == true (зарегистрирован при первом setup, FR-015).
- **Validation**:
  - `configBytes.size <= 256 * 1024` иначе → `CryptoError.ConfigTooLarge`.
- **AAD construction**: `aad = utf8("""{"uid":"$uid","schemaVersion":1}""")`. Деterministic — для совместимости двух independent клиентов.
- **Nonce**: random 24 bytes per seal.
- **Errors**: `ConfigTooLarge`, `AlgorithmUnsupported` (если KeyRegistry возвращает нечитаемый DEK).

### open
- **Validation**: parse `aad` JSON → check `uid` field matches caller's `uid` parameter. Mismatch → `AeadAuthFailed`.
- **Returns**: расшифрованный `configBytes`, byte-equal to original.
- **Errors**: `AeadAuthFailed` (tampered / wrong DEK / cross-identity replay), `AlgorithmUnsupported` (clients reading newer algorithm).

---

## Invariants

- **INV-1**: один DEK на жизнь identity, не ротируется per push (FR-019).
- **INV-2**: ConfigDocument на сервере только encrypted (FR-018). Локальный app cache — plaintext (accepted, allowBackup=false).
- **INV-3**: AAD включает UID — cross-identity replay невозможен (FR-020).
- **INV-4**: 256 KiB hard limit на plaintext (FR-029).

---

## Tests required

- `ConfigCipherRoundtripTest`: seal → open → byte-equal с original.
- `SealedConfigBackwardCompatTest`: read fixture `sealed-config-v1.json` → parse OK → `recipientMasterSignature` is null (forward-compat readiness).
- Negative: tampered ciphertext → `AeadAuthFailed`.
- Negative: тот же sealed, но другой `uid` параметр в open → `AeadAuthFailed` (cross-identity replay protection).
- Negative: configBytes > 256 KiB → `ConfigTooLarge`.
- Privacy test (`SC-001`): seal config с fixture именем `"Bobby Tables 555-1234"` → проверить, что ciphertext + nonce + aad НЕ содержат substring `"Bobby"` или `"555-1234"`.

---

## Краткое резюме

Контракт шифрования конфига для облака. Две операции — `seal` и `open` — обе принимают UID для биндинга и защиты от cross-identity replay. Wire-format с обязательными `schemaVersion`, `algorithm`, `ciphertext`, `nonce`, `aad` и опциональным `recipientMasterSignature` (задел на будущее, MVP всегда null). Hard-limit 256 КБ на plaintext; один DEK на жизнь identity (не ротация на каждое сохранение). Privacy-тест проверяет, что имена/телефоны не «протекают» в ciphertext.
