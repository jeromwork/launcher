# Tasks: F-CRYPTO — domain ports + fakes (TASK-123)

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Data model**: [data-model.md](data-model.md)
**Verification (all)**: `./gradlew :core:crypto:commonTest` — pure JVM, no device/network. **No `[deferred-*]` tasks** — everything closes in-session.

> Tick-sync HARD RULE: the implementation commit that closes a `Tnnn` MUST flip `[ ]`→`[x]` in the SAME diff.

## Phase 1 — Foundation

- [x] **T001** [P] Create packages `family.crypto.ports` (`commonMain`), `family.crypto.fake` + `family.crypto.contracts` (`commonTest`); confirm `:core:crypto` build has `kotlinx.coroutines` and **no** serialization plugin (must stay removed — TASK-146). *(FR-001, FR-002, Plan §2/§5)* — **Done when**: dirs exist, `./gradlew :core:crypto:compileKotlinMetadata` green, `grep serialization core/crypto/build.gradle.kts` shows only the removal comment.

## Phase 2 — Domain value / result types (`family.crypto.ports`, commonMain)

- [x] **T002** [P] Add opaque value types: `GroupId`, `IdentityKey`, `KeyPackage`, `KeyPackageId`, `LastResortKey`, `Commit` — `value class` over `ByteArray`/`String`, **no `@Serializable`**. **Reuse** `Ciphertext` from `family.crypto.api.values` (do NOT create a second). *(FR-001, FR-002, FR-006, data-model.md)* — **Done when**: types compile; `grep -r "class Ciphertext" family/crypto/ports` = empty (reused, not redefined).
- [x] **T003** [P] Add composite/result types: `CommitBundle` (`data class {commit, welcome?}`), `ProcessedMessage` (`sealed`: `ApplicationMessage`|`StagedCommit`|`Proposal`), `ClaimResult` (`sealed`: `Claimed(keyPackage, isLastResort)`|`Empty`). No `@Serializable`; **no `GroupState`**. *(FR-007, FR-009, FR-011, data-model.md)* — **Done when**: compiles; `grep -r "GroupState" family/crypto/ports` = empty.

## Phase 3 — Ports (interfaces, `family.crypto.ports`, commonMain)

- [x] **T004** Define `GroupPort` (all `suspend`): `createGroup(GroupId)`, `addMembers(GroupId, List<KeyPackage>) → CommitBundle`, `removeMembers(GroupId, List<IdentityKey>) → CommitBundle`, `selfUpdate(GroupId) → CommitBundle`, `commitToPendingProposals(GroupId) → CommitBundle?`, `mergePendingCommit(GroupId)`, `processMessage(GroupId, ByteArray) → ProcessedMessage`. Wire CoreCrypto two-phase shape; **no** authorization method (ML6). *(FR-009, US1; requires T002, T003)*
- [x] **T005** Define `CryptoPort` (`suspend`): `encryptMessage(GroupId, ByteArray) → Ciphertext`, `decryptMessage(GroupId, Ciphertext) → ByteArray`. *(FR-009, US1; requires T002, T003)*
- [x] **T006** Define `KeyPackagePort` (`suspend`): `publish(List<KeyPackage>, isLastResort: Boolean)`, `claim(IdentityKey) → ClaimResult`, `localCount() → Int`. Last-resort first-class (FR-011). **No `KeyVault` import.** *(FR-011, FR-008, US1; requires T002, T003)*

## Phase 4 — Fakes (`family.crypto.fake`, commonTest)

- [x] **T007** `FakeGroupPort` — in-memory pending/merge state machine, monotonic epoch counter; unknown `GroupId` → deterministic error; double `createGroup` → error. *(FR-003, US1, spec §Edge Cases; requires T004)*
- [x] **T008** `FakeCryptoPort` — intentionally insecure (xor + counter-ratchet, spec Assumption); post-`encrypt`+`merge` prior epoch key unreproducible (forward-secrecy invariant on fake); cross-group `decrypt` → error. *(FR-003, spec §Edge Cases; requires T005, T007)*
- [x] **T009** `FakeKeyPackagePort` — in-memory pool; `publish`; atomic one-time `claim`; empty pool → `ClaimResult.Empty` (no throw); last-resort fallback when pool empty; `localCount` drives client refill semantics. *(FR-003, FR-011, spec §Edge Cases; requires T006)*

## Phase 5 — Contract tests (`family.crypto.contracts`, commonTest — abstract + factory, reused by TASK-124)

- [x] **T010** Abstract `GroupPortContract` — 5–7 invariants (create; add→CommitBundle; process→correct `ProcessedMessage` variant; merge advances epoch; unknown-group error; double-create error) + `abstract fun createGroupPort(): GroupPort`; concrete `FakeGroupPortContractTest` binds the fake. *(FR-004, US3, SC-002, SC-005; requires T007)*
- [x] **T011** Abstract `CryptoPortContract` (roundtrip encrypt→decrypt; cross-group decrypt fails; forward-secrecy) + concrete fake subclass. *(FR-004, US3, SC-002, SC-005; requires T008, T010)*
- [x] **T012** Abstract `KeyPackagePortContract` (publish→claim pops one; second claim on empty → `Empty`; last-resort returned when pool empty; one-time-use) + concrete fake subclass. *(FR-004, FR-011, US3, SC-002, SC-005; requires T009)*

## Phase 6 — Fitness (konsist, commonTest)

- [x] **T013** Konsist test: no file in package `family.crypto.ports` imports `openmls`, `uniffi`, `cryptokit`, `android.`, `okhttp`, `firebase`. Assert **both** directions — planted forbidden import fails the test, clean tree passes. Template: `NoLegacyFamilyNamespaceTest`. *(FR-005, US2, SC-003; requires T004, T005, T006)*

## Phase 7 — Consumer snippet + verification

- [x] **T014** Consumer usage snippet in `core/crypto/README.md` + a `commonTest` that compiles & runs it: `createGroup → addMembers → encryptMessage → decryptMessage` on fakes, green with zero external deps. *(FR-010, US1, SC-001, SC-004; requires T007, T008, T009)*
- [x] **T015** End-to-end gate: `./gradlew :core:crypto:commonTest` all green; verify `@Serializable`/vendor absent from `family.crypto.ports`, `Ciphertext` reused (no 2nd type), `GroupState` never a return type, `KeyVault` not imported in ports. *(FR-002, FR-006, FR-007, FR-008, SC-002, SC-006; requires T010, T011, T012, T013, T014)*

---

## Traceability

| FR | Tasks | · | US | Tasks (test evidence) | · | SC | Tasks |
|---|---|---|---|---|---|---|---|
| FR-001 | T002, T004–T006 | | US1 | T007–T009, T014 | | SC-001 | T014 |
| FR-002 | T001, T002, T015 | | US2 | T013 | | SC-002 | T010–T012, T015 |
| FR-003 | T007–T009 | | US3 | T010–T012 | | SC-003 | T013 |
| FR-004 | T010–T012 | | | | | SC-004 | T014 |
| FR-005 | T013 | | | | | SC-005 | T010–T012 |
| FR-006 | T002, T015 | | | | | SC-006 | T015 |
| FR-007 | T003, T015 | | | | | | |
| FR-008 | T006, T015 | | | | | | |
| FR-009 | T004, T005 | | | | | | |
| FR-010 | T014 | | | | | | |
| FR-011 | T006, T009, T012 | | | | | | |

**Gates**: no `contracts/` wire formats → no roundtrip/backcompat task (N/A, plan §4). Every port has a fake (T007–T009 ✓). Boundary fitness present (T013 ✓). No DELETE list, no UI, no perf, no device → no deletion/screenshot/perf/deferred tasks.

---

## Для новичка (простыми словами)

Это **пошаговый список работ** (15 маленьких шагов, каждый ≤ полдня, ≤ несколько файлов), разбитый на 7 фаз: сначала «коробочки с данными» (шаги T002–T003), потом сами «розетки»-интерфейсы (T004–T006), потом их пустышки-заглушки (T007–T009), потом тесты-договоры, которые проверяют, что заглушка ведёт себя правильно и которые **потом переиспользует настоящий движок** (T010–T012), потом машинная проверка чистоты (T013) и, наконец, пример для разработчиков + финальная сборка (T014–T015). Всё проверяется одной командой на компьютере, без телефона и интернета. Никаких «отложенных на железо» шагов тут нет — всё закрывается сразу. Таблица внизу — гарантия, что каждое требование из спеки покрыто хотя бы одним шагом (ничего не забыли).
