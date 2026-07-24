# Tasks: openmls integration — real MLS adapter (in-memory)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Backlog**: TASK-124
**Trace legend**: `(FR-xxx, US-x, §plan)` — every task traces to spec/plan.
**Tick-sync rule**: each implementation commit closing a `Tnnn` MUST flip its `[ ]`→`[x]` in the same diff (CLAUDE.md HARD RULE).

---

## Phase 1 — Foundation

- [x] **T001** Add `openmls =0.8.1`, `openmls_traits 0.5.0`, `openmls_rust_crypto 0.5.1` to `crypto-ffi/Cargo.toml`; `cargo build --release` green; `Cargo.lock` committed. (FR-001, §Dependency impact)
- [x] **T002** [P] Link `:core:crypto` androidMain → `:crypto-ffi` in `core/crypto/build.gradle.kts`; confirm `verifyCryptoIsolation` still green (crypto-ffi is not a launcher module). (§Architecture)

## Phase 2 — Rust FFI MLS surface (`crypto-ffi/src/`)

- [x] **T003** `storage.rs`: `InMemoryStorageProvider` implementing openmls `StorageProvider<VERSION>` over `HashMap<Vec<u8>, Vec<u8>>`; serialize/deserialize the whole map (serde+bincode) for the snapshot. Unit-test snapshot roundtrip in Rust. (FR-002, FR-003, requires: T001)
- [x] **T004** `mls.rs`: `create_group` verb (`new_with_group_id`, ephemeral `SignatureKeyPair` + `CredentialWithKey` built in-Rust) → returns snapshot. Stateless pattern: load-or-create → op → serialize storage. (FR-003, FR-004, FR-010-signer, requires: T003)
- [x] **T005** `mls.rs`: `add_members` (→ commit+welcome) and `remove_members` (resolve `IdentityKey → LeafNodeIndex` from roster in-Rust; unknown member → error). (FR-004, FR-007, requires: T004)
- [x] **T006** `mls.rs`: `self_update`, `commit_to_pending_proposals`, `merge_pending_commit`, `merge_staged_commit`, `process_message` (→ Application/StagedCommit/Proposal kind). (FR-004, requires: T004)
- [x] **T007** `mls.rs`: `encrypt` (`create_message`, guard: error if pending proposals) and `decrypt` (via `process_message` → Application payload). (FR-004, FR-009, requires: T004)
- [x] **T008** [P] `mls.rs`: `generate_key_package` verb with `last_resort: bool` (`KeyPackage::builder().mark_as_last_resort()`). (FR-004, requires: T004)
- [x] **T009** Run skill `crypto-ffi-panic-check` on every new `#[uniffi::export]` verb — all non-throwing-to-abort signatures, panics map to Kotlin exceptions. (§Test strategy, requires: T005-T008)

## Phase 3 — Kotlin adapters (`family.crypto.mls`, androidMain)

- [ ] **T010** Internal plumbing: `SnapshotStore` (`MutableMap<GroupId, ByteArray>`), result→domain mappers, `withContext(Dispatchers.IO)` wrapping. (FR-005, FR-007, FR-011, requires: T009)
- [ ] **T011** `OpenMlsGroupPort` implements `GroupPort` via `uniffi.crypto_ffi`: createGroup/addMembers/removeMembers/selfUpdate/commitToPendingProposals/mergePendingCommit/processMessage; map to `CommitBundle`/`ProcessedMessage`. (FR-005, FR-006, FR-008, US-1, requires: T010)
- [ ] **T012** `OpenMlsCryptoPort` implements `CryptoPort` (encryptMessage/decryptMessage) sharing epoch state with `OpenMlsGroupPort` (same SnapshotStore). (FR-005, FR-006, US-1, US-2, requires: T010)
- [ ] **T013** `OpenMlsKeyPackagePort` implements `KeyPackagePort` local-only: publish/claim(one-time→last-resort→Empty, never throws)/localCount; ephemeral in-adapter signer + `// TODO(task-112)` + `// TODO(server-roadmap)`. (FR-010, US-3, requires: T010)

## Phase 4 — Tests (contract + roundtrip + property + forward secrecy)

- [ ] **T014** Subclass `GroupPortContract` (androidUnitTest) with `createGroupPort() = OpenMlsGroupPort`; all inherited assertions green, no test code moved. (SC-001, US-1, requires: T011)
- [ ] **T015** Subclass `CryptoPortContract` returning shared `OpenMlsCryptoPort`+`OpenMlsGroupPort`; roundtrip + cross-group + prior-epoch assertions green. (SC-001, US-1, US-2, requires: T011, T012)
- [ ] **T016** Subclass `KeyPackagePortContract` on `OpenMlsKeyPackagePort`; one-time-once + last-resort-reuse + empty-no-throw. (SC-006, US-3, requires: T013)
- [ ] **T017** `MlsMessageRoundtripTest` (encrypt→serialize→deserialize→decrypt == original) + `GroupStateRoundtripTest` (snapshot serialize→deserialize→op equivalence). Contract: [mls-ffi-surface.md](contracts/mls-ffi-surface.md). (FR-015, SC-002, requires: T011, T012)
- [ ] **T018** [P] Property-based (kotest-property Arb): 100 random sequences create+add+encrypt+remove+processCommit → no exception, member set consistent. (SC-004, requires: T014)
- [ ] **T019** [P] Forward-secrecy assertions: double `encrypt(m)` → different ciphertexts; prior-epoch ciphertext undecryptable after `selfUpdate+mergePendingCommit`. (SC-003, requires: T015)

## Phase 5 — Fitness & DI wiring

- [ ] **T020** DI: wire `OpenMls*Port` into `backendModule` (androidRealBackend) vs fakes (androidMockBackend); confirm `assertNoFakeCryptoInRelease` trips on a fake MLS port in release graph. (FR-012, SC-008, requires: T011-T013)
- [ ] **T021** Fitness: confirm `PortsNoVendorImportTest` FORBIDDEN list catches `openmls`/`uniffi` in `family.crypto.ports`; `verifyUniffiVersions` (0.28.3) green after MLS bindings. (FR-013, FR-014, SC-007, requires: T011)

## Phase 6 — Emulator smoke

> **[deferred-local-emulator]** T022 requires a running AVD the AI session can't reliably verify; owner runs via skill `android-emulator` on `pixel_5_api_34`.

- [ ] **T022** [deferred-local-emulator] Smoke: create 3-member group, encrypt+decrypt 10 messages, all decrypt correctly on `pixel_5_api_34`. (SC-005, requires: T011, T012)

## Phase 7 — Docs

- [ ] **T023** Update [crypto-mls.md](../../docs/architecture/crypto-mls.md) frontmatter status (group wrapper: implemented YYYY-MM-DD) + add MLS section with examples to `core/crypto/README.md`. Verify no impl↔arch-pack drift (rule 14). (FR-016, SC-009, requires: T014-T019)

---

## Coverage notes

- **Contracts gate**: [mls-ffi-surface.md](contracts/mls-ffi-surface.md) → roundtrip = T017. **Backward-compat corpus N/A** (documented in contract): MLS bytes are RFC-9420-versioned externally, exact openmls 0.8.1 pin; our cross-version at-rest compat is TASK-125's concern.
- **Ports/fakes gate**: `OpenMls*Port` are the REAL adapters; fakes already exist (TASK-123) — no new fake task.
- **New-module gate**: none (reuse `:core:crypto` + `:crypto-ffi`) — no Konsist boundary task beyond existing T021 fitness.
- **Deferred**: only T022 (`[deferred-local-emulator]`). Everything else closeable in-session.

---

## TL;DR для новичка

23 задачи, 7 фаз, идут по порядку:
1. **Фундамент** — подключить крипто-библиотеку openmls к Rust и связать два модуля.
2. **Rust-крипта** — написать «команды» (создать группу, добавить/удалить участника, шифровать…) поверх openmls, с хранилищем-в-памяти.
3. **Kotlin-адаптеры** — три «переходника», которые втыкают Rust-крипту в готовые розетки.
4. **Тесты** — старые контракты из прошлой задачи гоняем на новом настоящем адаптере + forward-secrecy + 100 случайных сценариев.
5. **Проверки-предохранители** — DI (настоящий/игрушечный) + фитнес-правила (чтобы vendor-типы не протекли).
6. **Дымовой тест на эмуляторе** — 3 участника, 10 сообщений (владелец запускает сам — помечено `deferred-local-emulator`).
7. **Документация** — отметить в арх-паке, что MLS-движок реализован.

Почти всё закрывается в сессии; на железе/эмуляторе — только один пункт (T022).
