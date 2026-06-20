# Contract: `RecoveryKeyVault` port + `RecoveryVaultBlob` wire-format v1

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **FRs**: FR-008, FR-009, FR-010, FR-022

Port для cloud-storage encrypted RootKey + wire-format blob'а.

---

## Kotlin declaration (commonMain)

```kotlin
package com.launcher.api.keys.api

public interface RecoveryKeyVault {

    /**
     * Забрать vault для данной identity. Если запись отсутствует — VaultError.NotFound.
     *
     * Pre: пользователь authenticated в IdentityProof, identity.stableId == uid.
     */
    public suspend fun fetchVault(uid: String): Outcome<RecoveryVaultBlob, VaultError>

    /**
     * Сохранить vault. Idempotent — replace existing.
     */
    public suspend fun storeVault(uid: String, blob: RecoveryVaultBlob): Outcome<Unit, VaultError>

    /**
     * Удалить vault — для optional factory-reset / explicit user action.
     */
    public suspend fun deleteVault(uid: String): Outcome<Unit, VaultError>
}

public sealed class VaultError {
    public data object Unauthorized : VaultError()           // request.auth.uid != uid в Firestore Rules
    public data object NotFound : VaultError()               // запись не существует
    public data class Network(val cause: Throwable) : VaultError()
    public data class Conflict(val message: String) : VaultError()
    public data class Malformed(val cause: Throwable) : VaultError()  // shape валидации не прошёл
}

// TODO(future-spec V-2/V-3/P-10): cross-app root key sharing via broker pattern
// (preferred per owner decision 2026-06-19) — see docs/product/future/multi-app-cohabitation.md.
// Format RecoveryVaultBlob уже app-agnostic (FR-022) — broker pattern не потребует breaking change.
```

---

## Wire-format: `RecoveryVaultBlob`

### Kotlin shape

```kotlin
public data class RecoveryVaultBlob(
    val schemaVersion: Int,
    val algorithm: String,
    val kdfSalt: ByteArray,
    val kdfParams: PassphraseKdfParams,
    val wrappedRootKey: ByteArray,
    val nonce: ByteArray,
    val createdAt: Long,
)

public data class PassphraseKdfParams(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
)
```

### JSON example (то, что лежит в Firestore)

```json
{
  "schemaVersion": 1,
  "algorithm": "argon2id-xchacha20poly1305-v1",
  "kdfSalt": "Tx5LqK8mZ3JpV2cB+gWQpA==",
  "kdfParams": {
    "memoryKiB": 65536,
    "iterations": 3,
    "parallelism": 1
  },
  "wrappedRootKey": "P3vG2X5n0K8mZ3JpV2cB+gWQpA9Fk1tR8nXmYzQwLpEsT2cD5fH...",
  "nonce": "B+gWQpA9Fk1tR8nXmYzQwLpEsT2cD5fH==",
  "createdAt": 1718800123456
}
```

### Field semantics

| Field             | Type    | Notes                                                                            |
|-------------------|---------|----------------------------------------------------------------------------------|
| `schemaVersion`   | Int     | currently `1`. Bump только при breaking change (rule 5).                          |
| `algorithm`       | String  | `"argon2id-xchacha20poly1305-v1"`. String для additive future algorithms (FR-032). |
| `kdfSalt`         | bytes   | 16 random bytes per setup (R-1, INV-2). Уникальный per identity-setup.            |
| `kdfParams`       | object  | Argon2id параметры — записываются при setup, читаются клиентом recovery'а.        |
| `wrappedRootKey`  | bytes   | RootKey (32 bytes), encrypted XChaCha20-Poly1305 под passphrase-derived key.      |
| `nonce`           | bytes   | 24 bytes для XChaCha20-Poly1305.                                                  |
| `createdAt`       | Long    | Unix milliseconds — debugging / audit. Не используется в decryption.              |

### Backward-compat rules
- Adding new optional field — additive, без bump'а `schemaVersion`.
- Renaming / removing field — bump `schemaVersion` + миграционная спека (rule 5).
- `algorithm` string — добавление новых значений additive, клиент решает по строке.

---

## Semantics

### fetchVault
- **Returns**: `RecoveryVaultBlob` если существует, `VaultError.NotFound` если нет.
- **Errors**: `Unauthorized` (Firestore Rules rejected — несоответствие UID), `Network`, `Malformed` (read shape не соответствует schema).

### storeVault
- **Behavior**: idempotent — replace existing entry. Firestore Rules валидируют shape (R-3).
- **Errors**: `Unauthorized`, `Network`, `Conflict` (rare — write race; retry strategy на caller).

### deleteVault
- **Use-case**: explicit user action «настроить как новое устройство» (после 3 неверных passphrase, acceptance scenario 2.4).
- **Errors**: `Unauthorized`, `Network`. `NotFound` — silent success (idempotent).

---

## MVP adapter — `FirestoreRecoveryKeyVault`

**Path**: `users/{uid}/recovery-key/main` (singleton document).

**Implementation notes**:
- ByteArray поля сериализуются через `com.google.firebase.firestore.Blob` (Firestore native bytes type), не Base64 string.
- `kdfParams` — nested object (Firestore native map).
- `useEmulator("10.0.2.2", 8080)` в debug builds.

**Никаких Firebase-типов в `commonMain`** (rule 1 ACL): Firestore adapter живёт **только** в `app/src/main/kotlin/com/launcher/data/recovery/FirestoreRecoveryKeyVault.kt`.

---

## Tests required

- `RecoveryVaultRoundtripTest`: store → fetch → assert equal (`FakeRecoveryKeyVault` in-memory).
- `RecoveryVaultBackwardCompatTest`: read fixture `recovery-vault-v1.json` → parse без ошибок → fields соответствуют ожиданию.
- Firestore Emulator integration test: real `FirestoreRecoveryKeyVault` → store → fetch → assert equal.
- Negative tests: `Unauthorized` (mismatched UID), `Malformed` (битый JSON в Firestore).

---

## Краткое резюме

Контракт port'а для хранения зашифрованной копии главного ключа в облаке (MVP — Firestore). Три операции — `fetch`, `store`, `delete`; пять видов ошибок (включая `Unauthorized` и `Malformed`). Wire-format — JSON с обязательными полями `schemaVersion`, `algorithm`, `kdfSalt`, `kdfParams`, `wrappedRootKey`, `nonce`, `createdAt`; всё кроме `createdAt` участвует в расшифровке. Будущая поддержка broker pattern для cross-app sharing — без breaking change, помечено inline TODO.
