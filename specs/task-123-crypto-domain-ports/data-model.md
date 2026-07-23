# Data model: F-CRYPTO domain ports (TASK-123)

All types live in `family.crypto.ports` (`:core:crypto` `commonMain`). **All are opaque and carry NO `@Serializable`** (FR-002; `:core:crypto` is serialization-free since TASK-146 — wire encoding is the TASK-124 adapter's job). Bytes semantics follow openmls / RFC 9420; the domain treats them as opaque.

## Value types

| Type | Kotlin shape | Meaning | Notes |
|---|---|---|---|
| `GroupId` | `value class GroupId(val value: String)` | opaque group handle | not the MLS group_id bytes exposed to server (rule 13) — a client-side handle |
| `IdentityKey` | `value class IdentityKey(val bytes: ByteArray)` | member identity (Ed25519 signature pubkey) | **new** — reuse impossible (`PublicKey` is in `:core:pairing`, wrong dependency direction); typed for misuse-resistance (P4); NOT a dup of pairing `PublicKey` (different layer) |
| `KeyPackage` | `value class KeyPackage(val bytes: ByteArray)` | opaque MLS KeyPackage payload | `expiresAt` carried alongside where needed (see below) |
| `KeyPackageId` | `value class KeyPackageId(val value: String)` | opaque pool entry id | server-facing id is opaque (rule 13); domain only holds the handle |
| `LastResortKey` | `value class LastResortKey(val bytes: ByteArray)` | reusable fallback KeyPackage | RFC 9750 §5.1 first-class |
| `Commit` | `value class Commit(val bytes: ByteArray)` | opaque MLS Commit message | |
| `Ciphertext` | **reused** `family.crypto.api.values.Ciphertext` | AEAD envelope | FR-006 — no second type |

## Composite / result types

| Type | Kotlin shape | Meaning |
|---|---|---|
| `CommitBundle` | `data class CommitBundle(val commit: Commit, val welcome: ByteArray?)` | result of `addMembers`/`removeMembers`/`selfUpdate`/`commitToPendingProposals` — NOT `GroupState` (FR-007) |
| `ProcessedMessage` | `sealed interface` → `ApplicationMessage(val plaintext: ByteArray)` \| `StagedCommit(val commit: Commit)` \| `Proposal(val raw: ByteArray)` | result of `processMessage` (openmls `ProcessedMessage`/`StagedCommit` shape) |
| `ClaimResult` | `sealed interface` → `Claimed(val keyPackage: KeyPackage, val isLastResort: Boolean)` \| `Empty` | result of `KeyPackagePort.claim` — empty pool returns `Empty`, never throws (spec §Edge Cases) |
| `KeyPackageEntry` | `data class KeyPackageEntry(val keyPackage: KeyPackage, val expiresAt: Instant)` | published pool entry (if expiry surfaced at the port; else `expiresAt` stays adapter-internal — finalized in impl) |

## Explicitly NOT modelled here

- **`GroupState`** — opaque, lives in the openmls `StorageProvider` (TASK-124/125); never a returned domain type (FR-007). On the fake, group state is hidden behind the impl.
- **`KeyVault` / `Purpose`** — `:core:keys`, NOT imported by these ports (FR-008); the real adapter calls `KeyVault.exportDerivedKey(MLS_SIGNATURE, …)` internally (TASK-124).
- **Any DTO / wire type with `schemaVersion`** — TASK-124 adapter concern.

## Invariants encoded by types

- Opaque `ByteArray` wrappers → the domain cannot inspect or forge MLS internals (rule 1, ML-leak prevention).
- Typed keys (`IdentityKey`) → "pass arbitrary bytes as a member key" is unrepresentable (P4).
- `sealed` results (`ProcessedMessage`, `ClaimResult`) → the compiler forces callers to handle empty-pool / commit-vs-application-message branches (spec §Edge Cases, no silent success).

---

## Для новичка (простыми словами)

Это список «коробочек с данными», которыми обмениваются крипто-порты. Почти все — просто **обёртки над байтами** с понятным именем: `IdentityKey` — «кто ты» (публичный ключ участника), `KeyPackage` — «приглашение-ключ», `Commit` — «изменение группы», `Ciphertext` — «зашифрованное сообщение». Обёртки нужны, чтобы нельзя было случайно перепутать одни байты с другими. Две «умные» коробочки — `ProcessedMessage` (результат «разбери сообщение»: это обычное сообщение / изменение группы / предложение) и `ClaimResult` (забрал ключ / пул пуст) — заставляют код обработать все случаи, а не забыть про пустой пул. Важное «чего тут НЕ будет»: состояние группы наружу не отдаётся (оно живёт в настоящем движке позже), и сериализации (превращения в JSON) тут нет — это работа следующей задачи (TASK-124).
