# Contract: `:crypto-ffi` MLS verb surface + snapshot

**Interface, not a persisted wire format.** These are the `#[uniffi::export]` verbs added to `crypto-ffi/src/mls.rs`, plus the snapshot convention. Versioned by UniFFI lockstep (0.28.3) + `openmls =0.8.1` pin, not by a `schemaVersion` field (Clarification #3/#4). The MLS payloads crossing here are raw RFC 9420 bytes.

## Stateless convention (every verb)

- **IN**: `state: Vec<u8>` (serialized `StorageProvider` snapshot; empty/`None` for `create_group`) + `group_id: Vec<u8>` + operation params.
- **OUT**: a record carrying `updated_state: Vec<u8>` + operation result bytes.
- Rust internally: deserialize `state` → `OpenMlsProvider` over `InMemoryStorageProvider` → `MlsGroup::load(provider, group_id)` (or create) → op → merge if applicable → serialize provider storage → return.
- All verbs have **non-throwing-to-abort** signatures returning `Result` mapped by UniFFI to Kotlin exceptions (panic contract, TASK-122; skill `crypto-ffi-panic-check`).

## Verbs (indicative — exact Rust types settled at implementation against openmls 0.8.1)

| Verb | Params (beyond state, group_id) | Returns |
|------|--------------------------------|---------|
| `create_group` | `credential: Vec<u8>` (ephemeral signer built in-Rust) | `updated_state` |
| `add_members` | `key_packages: Vec<Vec<u8>>` | `{ updated_state, commit, welcome }` |
| `remove_members` | `member_identities: Vec<Vec<u8>>` (resolved to leaf indices in-Rust) | `{ updated_state, commit }` |
| `self_update` | — | `{ updated_state, commit }` |
| `commit_to_pending_proposals` | — | `{ updated_state, commit } \| none` |
| `merge_pending_commit` | — | `{ updated_state }` |
| `process_message` | `message: Vec<u8>` | `{ updated_state, kind: Application\|StagedCommit\|Proposal, payload }` |
| `merge_staged_commit` | `commit: Vec<u8>` | `{ updated_state }` |
| `encrypt` (`create_message`) | `plaintext: Vec<u8>` | `{ updated_state, ciphertext }` |
| `decrypt` | `ciphertext: Vec<u8>` | via `process_message` → `Application` payload |
| `generate_key_package` | `last_resort: bool` | `{ updated_state, key_package }` |

## Kotlin mapping (adapter, `family.crypto.mls`)

- `updated_state` → written back into `SnapshotStore[GroupId]`.
- `add_members` → `CommitBundle(Commit(commit), welcome)`; `remove_members`/`self_update` → `CommitBundle(Commit(commit), welcome=null)`.
- `process_message.kind` → `ProcessedMessage.{ApplicationMessage|StagedCommit|Proposal}`.
- `encrypt` → `Ciphertext`; `decrypt` → `ByteArray`.
- `generate_key_package` → `KeyPackage`; `last_resort=true` → also tracked as `LastResortKey`.

## Roundtrip / compat tests (FR-015)

- `MlsMessageRoundtripTest`: `encrypt → serialize → deserialize → decrypt` == original.
- `GroupStateRoundtripTest`: snapshot `serialize → deserialize → op` produces same result as no-roundtrip.
- **No backward-compat corpus of ours** — MLS bytes are RFC-9420-versioned externally; we pin exact openmls 0.8.1. Cross-version at-rest compat is TASK-125's concern (SQLCipher schema).

---

## TL;DR для новичка

Это список «команд», которые Kotlin посылает в Rust-крипту: создать группу, добавить/удалить участника, зашифровать/расшифровать сообщение, обработать входящее, сделать визитку-ключ. Правило одно для всех: Kotlin шлёт снимок состояния + команду, Rust возвращает новый снимок + результат. Никакого «своего формата с номером версии» мы не придумываем — гоняем стандартные MLS-байты. Точные типы уточнятся при написании кода (компилятор Rust — финальная проверка).
