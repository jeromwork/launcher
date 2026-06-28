# Cross-artifact trace — F-5 (task-6)

**Generated**: 2026-06-28
**Skill**: `procedure-cross-artifact-trace`
**Scope**: spec.md ↔ plan.md ↔ tasks.md ↔ data-model.md ↔ contracts/{recovery-key-backup-v1.md, worker-api-v1.md}

---

## Inventory

- **FRs in spec.md**: FR-001..FR-023 (23 total; FR-011 and FR-012 explicitly marked removed/simplified, retained for trace continuity).
- **User Stories**: US-1..US-6 (6 total).
- **Success Criteria**: SC-001..SC-013 (13 total).
- **Contracts**: 2 (`recovery-key-backup-v1.md`, `worker-api-v1.md`).
- **Plan-introduced ports**: 4 (`KeyRegistry`, `RootKeyManager`, `RecoveryKeyBackup`, `AuthAvailability`).
- **Plan-introduced modules**: 1 (`core/keys/` KMP module).
- **Plan §Constraints closure items**: `android:allowBackup="false"` + `data_extraction_rules.xml`; `docs/compliance/permissions-and-resource-budget.md` update.

---

## 1. FR → Task coverage matrix

| FR | Implementing tasks | Verified |
|---|---|---|
| FR-001 | T601, T602, T606-T609 | ✓ |
| FR-002 | T612, T632, T634 | ✓ |
| FR-003 | T604, T613, T633 | ✓ |
| FR-004 | T614, T635 | ✓ |
| FR-005 | T606, T607, T615 | ✓ |
| FR-006 | T603, T610, T611, T622-T625, T628 | ✓ |
| FR-007 | T612, T613, T614, T635, T660 | ✓ (inline-TODOs in port files) |
| FR-008 | T632, T642 | ✓ |
| FR-009 | T633, T643 | ✓ |
| FR-010 | T635, T639, T640, T655, T670 | ✓ |
| FR-011 (removed) | T648 (DI confirms absence) | ✓ (negative coverage acceptable for removed FR) |
| FR-012 (simplified) | T648 (no Selector in DI) | ✓ |
| FR-013 | T638, T641 | ✓ |
| FR-014 | T644, T645, T649 | ✓ |
| FR-015 | T636, T646, T650 | ✓ |
| FR-016 | T647, T651 | ✓ |
| FR-017 | T644, T652 | ✓ |
| FR-018 | T630, T637, T656, T657 | ✓ |
| FR-019 | T627, T648 (event listener wiring) | ✓ |
| FR-020 | T658, T671 | ✓ |
| FR-021 | T659 | ✓ |
| FR-022 | T616-T619 | ✓ |
| FR-023 | T620-T627 | ✓ |

**FR coverage: 23 / 23.**

## 2. US → Test-evidence coverage

| US | Test/verification tasks | Verified |
|---|---|---|
| US-1 (setup happy path) | T620, T622, T626, T645, T649, T666 | ✓ |
| US-2 (cross-device recovery) | T621-T625, T626, T646, T650, T655, T666 | ✓ |
| US-3 (Fallback) | T627, T647, T651, T667 | ✓ |
| US-4 (no-identity local mode) | T606, T607, T615, T619, T638, T641 | ✓ |
| US-5 (spec 018 migration) | T630, T637, T656, T657 | ✓ |
| US-6 (provider-agnostic fitness) | T624-T627, T631 | ✓ |

**US coverage: 6 / 6.** Each US has at least one test-task; perf-sensitive US-2 (recovery cold-start ≤ 5s) has perf checkpoint via T643/T669.

## 3. SC → Verifying-task coverage

| SC | Verifying tasks |
|---|---|
| SC-001 [backlog] | T626 (Fake), T655 (wrangler), T666 (real device) |
| SC-002 [backlog] | T651, T667 |
| SC-003 [backlog] | T641, T649-T651 |
| SC-004 [backlog] | T657 |
| SC-005 [backlog] | T668 (real device only) |
| SC-006 [backlog] | T658, T671 |
| SC-007 | T631 (Konsist) |
| SC-008 | T624 |
| SC-009 | T626 |
| SC-010 | T643, T669 |
| SC-011 | T636, T650 |
| SC-012 | T627 |
| SC-013 | T620-T627 |

**SC coverage: 13 / 13.**

## 4. Contracts → Test-task coverage

### `recovery-key-backup-v1.md`

| Required | Task | Status |
|---|---|---|
| Roundtrip test | T622 | ✓ |
| Backward-compat test | T623 | ✓ |
| Forward-compat / unsupported schema | T625 | ✓ |
| Provider-agnostic test | T624 | ✓ |
| Fixture file | T628 (v1), T629 (v2 synthetic) | ✓ |

### `worker-api-v1.md`

| Required | Task | Status |
|---|---|---|
| Integration test (Android-side) | T655 | ✓ (deferred-local-emulator) |
| Real-Worker E2E | T670 | ✓ (deferred-physical-device) |
| Worker-side test files (6 per contract §10) | T653 scope (parallel-track TASK-X) | ⚠ scoped-out (see Open Items §1) |

**Note**: Worker-side contract tests (`auth-jwt.test.ts`, `stable-id-check.test.ts`, `idempotency.test.ts`, `rate-limit.test.ts`, `r2-roundtrip.test.ts`, `no-logging.test.ts`) are explicitly delegated to TASK-X per plan.md §Phase 4 — legitimate scoping, not a gap.

## 5. Plan-introduced ports → Fake-adapter coverage

| Port | Fake task |
|---|---|
| `KeyRegistry` | T616 (`FakeKeyRegistry`) ✓ |
| `RootKeyManager` | T617 (`FakeRootKeyManager`) ✓ |
| `RecoveryKeyBackup` | T618 (`FakeRecoveryKeyBackup`) ✓ |
| `AuthAvailability` | T619 (`FakeAuthAvailability`) ✓ |

**Port fake coverage: 4 / 4.**

## 6. Plan modules → Fitness-function coverage

| Module | Fitness rule | Task |
|---|---|---|
| `core/keys/src/commonMain/` | Konsist grep for forbidden tokens (Google/Firebase/OAuth/Apple/Phone/Email/Sub/IdToken/Cloudflare/Worker) | T631 ✓ |

**Module fitness coverage: 1 / 1.**

## 7. Plan §Constraints closure

| Constraint | Closure task |
|---|---|
| `android:allowBackup="false"` | T661 ✓ |
| `data_extraction_rules.xml` exclude rules | T662 ✓ |
| `docs/compliance/permissions-and-resource-budget.md` update | T660 ✓ |
| Konsist forbidden-token rule | T631 ✓ |
| `CharArray` passphrase wipe (FR-009 inherited) | T633 (impl) ✓ |

**Constraint closure: 5 / 5.**

## 8. Cross-artifact consistency

- ✓ Plan introduces no module/port without spec FR grounding (KeyRegistry → FR-002; RootKeyManager → FR-003; RecoveryKeyBackup → FR-004; AuthAvailability → FR-005).
- ✓ Wire-format `RecoveryKeyBackupBlob` carries `SCHEMA_VERSION_V1` constant from first commit (rule 5).
- ✓ No dangling deleted-file references (plan.md "DELETE" list empty — origin/020 legacy not migrated per spec Notes).
- ✓ Task ordering valid: dependency chain T601 → T610-T611 → T622-T625; T612-T615 → T632-T635, T644; T635 → T655/T670; T653 (TASK-X) → T654/T670.
- ✓ Inline-TODOs declared in spec FR-007 are owned by T612, T613, T614, T635, T660.
- ⚠ US-1 lacks an explicit `### User Story 1` heading in spec.md (content starts ~line 308); US-2..US-6 use the standard heading. **Cosmetic only** — content is present and US-1 is universally referenced. **Not a coverage gap.**

## 9. False-positive coverage check

Spot-checked the following potentially-loose mappings:

- **T648 (DI module) for FR-011 / FR-019 cascade wipe**: T648 only sets up bindings; FR-019 cascade-wipe event-listener wiring is implicit. Tasks.md note "(event listener wiring)" is honest about this. **Verified the FR is closed by combination of T627 (forget flow test) + T648 (DI binding) + spec FR-019 explicit signOut→wipe contract.** No false positive — but T648 description could spell out the listener registration explicitly. **Minor**.
- **T626 covering SC-001 + SC-009**: legitimately the same fitness-function test (provider-agnostic acceptance) serves both. ✓
- **T631 (Konsist) → SC-007**: direct 1:1, ✓.

No false-positive coverage detected.

## 10. Required-task gates self-check (validating tasks.md §"Required-task gates")

| Gate | Tasks.md claim | Verified |
|---|---|---|
| Every contract has roundtrip + backward-compat | recovery-key-backup-v1 → T622+T623+T624+T625; worker-api-v1 → T655+T670 (Android side) | ✓ |
| Every new port has a fake | KeyRegistry/RootKeyManager/RecoveryKeyBackup/AuthAvailability → T616-T619 | ✓ |
| New module has fitness rule | `core/keys/` → T631 | ✓ |
| No dangling deleted-file references | none in plan DELETE list | ✓ |
| Docs impacted have tasks | recovery-flow.md/key-hierarchy.md/permissions-budget/AndroidManifest/data_extraction_rules → T658-T662 | ✓ |
| UI features have UI tests + smoke | T649-T652 + T666-T668 | ✓ |
| Perf-sensitive features have benchmarks | T643 (emulator) + T669 (real device) | ✓ |

Tasks.md self-check is **accurate**.

---

## Verdict

**PASS** — full coverage matrix is closed.

- FR coverage: **23 / 23**
- US coverage: **6 / 6**
- SC coverage: **13 / 13**
- Contract test coverage: **2 / 2** (Worker-side tests legitimately scoped to TASK-X parallel track)
- Port fake coverage: **4 / 4**
- Module fitness coverage: **1 / 1**
- Plan constraint closure: **5 / 5**
- Gaps: **0**
- False-positive coverage: **0**
- Cosmetic observations: **2** (US-1 heading style; T648 description could itemize event-listener wiring)

No remediation required before `/speckit.implement`. The two cosmetic observations are non-blocking and can be addressed opportunistically on next touch.
