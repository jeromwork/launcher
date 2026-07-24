# Data Model: openmls in-memory adapter

No persistent or wire-format types of **ours** are introduced (Clarification #3/#4). This file documents the internal state shapes the adapter and FFI move around, for implementer clarity.

## Domain types (already defined — TASK-123, unchanged)

From `family.crypto.ports.Values` / `Results`:
- `GroupId(String)`, `IdentityKey(ByteArray)`, `KeyPackage(ByteArray)`, `KeyPackageId(String)`, `LastResortKey(ByteArray)`, `Commit(ByteArray)`.
- `CommitBundle(commit: Commit, welcome: ByteArray?)`.
- `ProcessedMessage = ApplicationMessage(ByteArray) | StagedCommit(Commit) | Proposal(ByteArray)`.
- `ClaimResult = Claimed(KeyPackage, isLastResort) | Empty`.
- `Ciphertext` (from `family.crypto.api.values`).

## Internal adapter state (NEW, `family.crypto.mls`, in-memory only)

| Shape | Type | Role | Lifetime |
|-------|------|------|----------|
| `SnapshotStore` | `MutableMap<GroupId, ByteArray>` | serialized `StorageProvider` snapshot per group | in-memory, lost on reboot |
| KeyPackage pool | `ArrayDeque<KeyPackage>` + nullable last-resort | local-only publish/claim (US3) | in-memory |
| Signing key | ephemeral openmls `SignatureKeyPair` | in-adapter, per identity | in-memory (Clarification #1) |
| `LeafIndexResolver` | derives `IdentityKey → LeafNodeIndex` from group roster | needed because `remove_members` takes leaf indices | per-call, from loaded group |

## FFI-crossing shapes (NEW, `:crypto-ffi`)

- **StorageProvider snapshot**: `Vec<u8>` — a serialized `HashMap<Vec<u8>, Vec<u8>>` (openmls entity keys → serialized entities), via `serde`+`bincode` (or equivalent) inside Rust. Opaque to Kotlin — treated as bytes.
- **Verb results**: small UniFFI records, e.g. `AddMemberResult { updated_state: Vec<u8>, commit: Vec<u8>, welcome: Vec<u8> }`, `EncryptResult { updated_state, ciphertext }`, `ProcessResult { updated_state, kind: enum, payload }`. Exact shapes in [contracts/mls-ffi-surface.md](contracts/mls-ffi-surface.md).

## Not modeled here

- MLS message internals (ciphertext/commit/welcome/KeyPackage) — external RFC 9420, opaque bytes to us.
- At-rest persistence schema — TASK-125 (SQLCipher), out of scope.

---

## TL;DR для новичка

Никаких новых «форматов данных, которые мы придумали» тут нет. Внутри адаптер держит в памяти: словарь «группа → её снимок», локальный пул ключей-визиток, временный ключ подписи и переводчик «ключ участника → номер места в дереве». Через мостик в Rust гоняются просто «мешки байтов» (снимок хранилища + результаты операций). Всё это живёт только в памяти и теряется при перезапуске — долговременное хранение будет отдельной задачей.
