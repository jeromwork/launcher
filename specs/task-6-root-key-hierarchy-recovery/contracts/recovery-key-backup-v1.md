# Contract: `RecoveryKeyBackupBlob` wire-format v1

**Name**: `RecoveryKeyBackupBlob`
**schemaVersion**: `1`
**Status**: Draft
**Created**: 2026-06-28
**Spec**: [../spec.md](../spec.md) — FR-006, FR-022, FR-023, SC-008, SC-013
**Plan**: [../plan.md](../plan.md)
**Family**: F-5 Root Key Hierarchy & Owner Recovery (sibling of [recovery-vault-v1](../../018-f5-config-e2e-encryption/contracts/recovery-vault-v1.md)).

Wire format for the JSON envelope stored by the device through `RecoveryKeyBackup.uploadBlob(...)` in our Cloudflare Worker (`workers/backup/`, R2 storage), and read back by `fetchBlob(stableId)` during cross-device recovery (SEQ-2). Provider-agnostic by construction (constitution.md Article XIV §7 (c) — no cross-user correlation surface).

---

## 1. Schema constant

The schema version is a **first-class declared constant** on both ends. Closes CHK003 (explicit schemaVersion declaration with code-level constant).

### Kotlin (`core/keys/src/commonMain/`)

```kotlin
package com.launcher.api.keys.wire

/**
 * Wire-format version for [RecoveryKeyBackupBlob].
 *
 * Bump = breaking change requiring migration spec (CLAUDE.md rule 5).
 * Old clients MUST be able to read v1 blobs for at least one major release
 * after a bump (backward-compat policy below).
 */
public const val SCHEMA_VERSION_V1: Int = 1

public const val MAX_SUPPORTED_SCHEMA_VERSION: Int = SCHEMA_VERSION_V1
```

### JSON

The literal `"schemaVersion": 1` MUST appear as the first field in every emitted blob (canonical key ordering for deterministic round-trip serialization).

---

## 2. Canonical example

```json
{
  "schemaVersion": 1,
  "stableId": "00000000-0000-4000-8000-000000000001",
  "salt": "Tx5LqK8mZ3JpV2cBgWQpA9Fk1tR8nXmYzQwLpEsT2cE=",
  "kdfParams": {
    "algorithm": "Argon2id",
    "iterations": 3,
    "memoryKb": 65536,
    "parallelism": 1
  },
  "ciphertext": "P3vG2X5n0K8mZ3JpV2cBgWQpA9Fk1tR8nXmYzQwLpEsT2cD5fHaB+cD/eFgHiJkLmNoPqRsTuVwXyZ0123==",
  "nonce": "B+gWQpA9Fk1tR8nXmYzQwLpEsT2cD5fH",
  "createdAt": "2026-06-28T10:00:00Z"
}
```

Notes on the example:
- `stableId` — UUID v4 lowercase canonical form (`8-4-4-4-12` hex).
- `salt` — base64-encoded 32 raw bytes.
- `nonce` — base64-encoded 24 raw bytes (XChaCha20 nonce width).
- `ciphertext` — XChaCha20-Poly1305 AEAD ciphertext over the 32-byte `RootKey` material; minimum length 32 + 16 = 48 bytes raw before base64.
- `createdAt` — ISO-8601 with explicit `Z` suffix (UTC). Client-set; server MAY ignore for ordering.

---

## 3. Field reference

| Field           | Type   | Required | Format                                | Notes                                                                                                |
|-----------------|--------|----------|---------------------------------------|------------------------------------------------------------------------------------------------------|
| `schemaVersion` | Int    | yes      | integer (`1`)                         | First commit ships v1; bump = breaking change requiring migration                                    |
| `stableId`      | String | yes      | UUID v4 lowercase                     | Provider-agnostic identifier; NO Google sub / email / phone                                          |
| `salt`          | String | yes      | base64 (32 bytes raw)                 | Per-identity, random, fixed at setup time                                                            |
| `kdfParams`     | Object | yes      | see KdfParams sub-table               | Argon2id parameters captured at setup, replayed on recovery                                          |
| `ciphertext`    | String | yes      | base64                                | XChaCha20-Poly1305 AEAD output, ≥ 16 bytes (AEAD tag)                                                |
| `nonce`         | String | yes      | base64 (24 bytes raw)                 | XChaCha20 nonce; unique per encryption op                                                            |
| `createdAt`     | String | yes      | ISO-8601 with `Z`                     | Set by client; server may ignore for ordering                                                        |

### KdfParams sub-table

| Field         | Type   | Required | Format             | Notes                                                |
|---------------|--------|----------|--------------------|------------------------------------------------------|
| `algorithm`   | String | yes      | enum `{"Argon2id"}`| Future enum extension via schemaVersion bump        |
| `iterations`  | Int    | yes      | ≥ 1                | MVP = 3 (OWASP 2024 interactive)                     |
| `memoryKb`    | Int    | yes      | ≥ 1024             | MVP = 65536 (64 MiB)                                 |
| `parallelism` | Int    | yes      | ≥ 1                | MVP = 1                                              |

---

## 4. Forbidden fields

The following keys MUST NOT appear in any v1 blob (fitness function = `RecoveryKeyBackupBlobProviderAgnosticTest` parses the top-level keys and asserts absence). Closes SC-008.

- Identity-provider leakage: `googleSub`, `googleAccountId`, `firebaseUid`, `providerKind`, `providerId`.
- PII: `email`, `phoneNumber`, `displayName`.
- Cross-user correlation: `recipientId`, `groupId` (per constitution.md Article XIV §7 (c) — no cross-user correlation surface).

Any present forbidden field MUST cause the contract test to fail, and at runtime SHOULD cause `BackupError.Malformed`.

---

## 5. Versioning policy

Closes CHK005 (versioning policy explicit) and CHK008 (forward-compat read strategy explicit).

### Additive fields (no schemaVersion bump)

New **optional** fields are acceptable **without** a `schemaVersion` bump, provided:
- A sane default exists for old readers (they ignore the field).
- Reading or omitting the field causes no behaviour change in v1 logic.

Example (hypothetical): `clientPlatform: String?` (`"android"` / `"ios"` / `"web"`) for diagnostics is additive — old readers ignore it, no semantic change.

### Required-field changes / removals

Renaming, removing, or changing the type of any field listed in Section 3 MUST bump `schemaVersion` (v1 → v2). Old clients MUST keep being able to read v1 blobs for at least one major release (CLAUDE.md rule 5 backward-compat window).

### Forward-compat read strategy (CHK008)

The client reads `schemaVersion` first. Behaviour:

| Read `schemaVersion`               | Action                                                                                          |
|------------------------------------|-------------------------------------------------------------------------------------------------|
| `1` (== `MAX_SUPPORTED_SCHEMA_VERSION`) | Parse normally                                                                                  |
| `> 1` (e.g., `2` after v2 ships)        | Reject with `BackupError.UnsupportedSchema(version)`; UI prompts owner to update the app       |
| Missing / not an Int               | Reject with `BackupError.Malformed`; do NOT attempt partial parse                               |

**No partial parse** of unknown schemaVersion ever happens — we never reconstruct a downgraded v1 view of a v2 blob. The risk of silently dropping fields that affect decryption (e.g., a new mandatory `aad` field in v2) is unacceptable.

### Field rename

Prohibited as a same-version change. Renaming any field MUST bump `schemaVersion` and ship a written migration **before** the breaking change reaches production.

---

## 6. Algorithm change policy

`KdfParams.algorithm` is a string-typed enum. Per SRV-CRYPTO-008:

| Change                                                      | Bump schemaVersion? | Notes                                                                                            |
|-------------------------------------------------------------|---------------------|--------------------------------------------------------------------------------------------------|
| Add a new enum value (e.g., `"Argon2id+SHA3"`)              | **No** — additive   | Old readers detect unknown value and refuse with `BackupError.UnsupportedAlgorithm`. Never silently degrade. |
| Remove an enum value (e.g., drop `"Argon2id"` post-quantum) | **Yes** — bump      | Removing an algorithm = breaking; old data needs migration before the algorithm is dropped       |
| Change the byte-layout of an existing algorithm             | **Yes** — bump      | Same-name + different layout = backward-compat hazard; forbidden as same-version change          |

**Why old readers refuse rather than degrade**: the algorithm field directly governs key derivation. Silently treating an unknown algorithm as Argon2id would derive the wrong key and brick recovery. Fail-closed is the only safe stance.

---

## 7. Test contracts

Mandatory tests per spec FR-023. All live in `core/keys/src/commonTest/kotlin/com/launcher/keys/wire/`.

- **`RecoveryKeyBackupBlobRoundtripTest`** — write a `RecoveryKeyBackupBlob` → serialize to JSON → deserialize → assert structural equality. Catches kotlinx-serialization drift, field ordering, base64 round-trips.
- **`RecoveryKeyBackupBlobBackwardCompatTest`** — read fixture `recovery-blob-v1-sample.json` (committed once, never modified) → parse succeeds → fields match expected golden values. Catches future accidental breakage of v1 readers.
- **`RecoveryKeyBackupBlobProviderAgnosticTest`** — parse the JSON top-level key set → assert NO forbidden field (Section 4) is present. This is the fitness function from FR-006 and SC-008.
- **`RecoveryKeyBackupBlobUnsupportedSchemaTest`** — read fixture with `"schemaVersion": 2` → expect `BackupError.UnsupportedSchema(version=2)`; assert NO partial-parse leakage (no fields surface to caller).

---

## 8. Fixture

**Path**: `core/keys/src/commonTest/resources/fixtures/recovery-blob-v1-sample.json`

This file is committed once and treated as immutable. Any change to it requires either a schemaVersion bump or a new fixture file with a different name (`recovery-blob-v2-sample.json`, etc.).

Canonical content:

```json
{
  "schemaVersion": 1,
  "stableId": "00000000-0000-4000-8000-000000000001",
  "salt": "Tx5LqK8mZ3JpV2cBgWQpA9Fk1tR8nXmYzQwLpEsT2cE=",
  "kdfParams": {
    "algorithm": "Argon2id",
    "iterations": 3,
    "memoryKb": 65536,
    "parallelism": 1
  },
  "ciphertext": "P3vG2X5n0K8mZ3JpV2cBgWQpA9Fk1tR8nXmYzQwLpEsT2cD5fHaB+cD/eFgHiJkLmNoPqRsTuVwXyZ0123==",
  "nonce": "B+gWQpA9Fk1tR8nXmYzQwLpEsT2cD5fH",
  "createdAt": "2026-06-28T10:00:00Z"
}
```

A second fixture `recovery-blob-v2-sample.json` (used only by `UnsupportedSchemaTest`) carries `"schemaVersion": 2` and is otherwise structurally identical to v1.

---

## 9. Privacy assertions

Per constitution.md Article XIV §7 (data minimization, no cross-user correlation surface):

- **No PII in any field.** Section 4 enumerates every forbidden identifier; the schema admits only opaque cryptographic material plus `stableId` and a timestamp.
- **`stableId` is an opaque UUID.** It is not derived from, and does not contain, any identity-provider account identifier. Reverse mapping `stableId → identity-provider-account` lives in a separate, access-controlled store (Firestore `/identity-links/`, scoped per-identity by Security Rules) — never in this blob.
- **Minimal server correlation surface.** The Worker's access logs see only `/backup/{stableId}` + timestamp + request IP. No email, no phone, no provider account, no group membership. Cross-user correlation requires correlating IPs across requests, which is the lowest practical correlation surface available given that the blob must transit our infrastructure.
- **No room for accidental PII insertion.** The schema is a closed set (Section 3 fields only); kotlinx-serialization in strict mode rejects unknown fields at parse time, so a future code change that adds, e.g., `displayName` would be caught at compile/test time before it could ship.

Closes CHK018 (privacy posture documented).

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Кратко для владельца (не разработчика)

**Что это за файл.** Это **формальный контракт** на JSON-конверт, в котором лежит зашифрованная копия главного ключа пользователя в нашем облачном Worker'е (Cloudflare). Контракт = «что внутри JSON'а, какие правила нельзя нарушать, какими тестами это проверяется». Сам ключ (то, чем шифруются конфиги/контакты/фотографии) внутри этого конверта зашифрован под пароль пользователя — Worker видит только непрозрачные байты, а не сам ключ.

**Что в конверте лежит** (7 полей):
1. Номер версии формата (`schemaVersion: 1`).
2. Стабильный анонимный идентификатор пользователя (`stableId`, UUID) — **не Google-аккаунт, не email, не телефон**.
3. Соль для шифрования пароля (`salt`).
4. Параметры функции хэширования пароля (`kdfParams` — Argon2id, итерации, память).
5. Сам зашифрованный главный ключ (`ciphertext`).
6. Nonce для шифрования (`nonce`).
7. Когда сделан (`createdAt`).

**Чего в конверте быть НЕ должно** (запрещено инструментально, не «по договорённости»):
- Никаких Google sub / Firebase UID / provider-specific полей.
- Никаких email / телефонов / отображаемых имён.
- Никаких ID получателей или групп.
Это проверяется автоматическим тестом `RecoveryKeyBackupBlobProviderAgnosticTest` — он парсит JSON-ключи и валит сборку, если что-то запрещённое внутри.

**Почему правила про версии.**
- *Добавить поле* (например, для диагностики) — можно без подъёма версии. Старые читатели его проигнорируют.
- *Удалить или переименовать поле* — нельзя без подъёма версии. Старые конверты v1 должны читаться ещё хотя бы одну major-версию приложения (правило 5 CLAUDE.md).
- *Старая версия приложения видит конверт новой версии* — приложение **отказывается читать** и просит обновиться. Никакого «прочту что смогу, остальное пропущу» — потому что недопрочитанный конверт даст неправильный ключ и кирпич восстановления.

**Почему `stableId` — UUID, а не Google sub.**
Чтобы Worker (и его логи, и его база) не знал кто конкретно лежит за этим бэкапом. UUID создаётся при первом входе, привязка `UUID ↔ Google аккаунт` хранится отдельно в защищённом хранилище и Worker'у недоступна. Это даёт минимальную поверхность корреляции на стороне сервера — если завтра логи утекут, по ним нельзя будет восстановить «у кого какой бэкап».

**Тесты, которые этот контракт обязан иметь** (4 шт.):
1. Round-trip — записал, прочитал, всё совпало.
2. Backward-compat — старый зафиксированный fixture продолжает читаться.
3. Provider-agnostic — запрещённых полей внутри нет.
4. Unsupported-schema — конверт v2 на v1-клиенте отказывается читаться (не выдаёт частичные данные).
<!-- NOVICE-SUMMARY:END -->
