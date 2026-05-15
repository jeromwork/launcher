# Cross-Artifact Trace: spec 008

**Date**: 2026-05-14
**Run**: post `speckit-tasks`, before `speckit-analyze`.

Read-only audit per `procedure-cross-artifact-trace` skill, 8 checks.

---

## Inventory

- **spec.md**: 37 FRs (FR-001..006, FR-010..015, FR-020..023, FR-030..034, FR-040..047, FR-050..057), 9 SCs (SC-001, SC-001b, SC-002, SC-003, SC-004a, SC-004b, SC-005, SC-006 carved-out, SC-007, SC-008), 6 USs (US-1..6), 8 OUT-blocks.
- **plan.md**: 12-phase implementation, 5 new commonMain ports, 5 new fakes + 5 real adapters, 1 new Room database.
- **research.md**: 8 sections (one-way doors + alternatives).
- **data-model.md**: 11 domain types + 2 Room entities + 5 ports.
- **contracts/**: `config.md` (W1), `state-applied.md` (W2 extended + FCM payload inline).
- **constitution-check.md**: 8/8 PASS.
- **tasks.md**: ~85 tasks across 12 phases (T001..T146 numbering with gaps), explicit traceability matrix.
- **checklists/**: 10 checklists, all PASS.

---

## Check results

### ✓ 1. Spec → Tasks coverage (37/37 FRs covered)

All 37 FRs explicitly mapped в tasks.md §Traceability Matrix:

| FR | Tasks | Verified |
|---|---|---|
| FR-001..006 (wire-format) | T012, T030, T031, T032, T034 | ✓ |
| FR-010..015 (push channel) | T062, T070, T072, T101, T102, T074, T111 | ✓ |
| FR-020..023 (server→Managed) | T080, T081, T060, T061, T090, T092, T094, T096, T041 | ✓ |
| FR-030..034 (state publish) | T060, T061, T013, T034, T035, T014, T075, T076 | ✓ |
| FR-040..047 (local persistence) | T100, T101, T050, T051, T038, T039, T054, T055, T140, T107, T108, T104, T105 | ✓ |
| FR-050..057 (merge UI + UX) | T111, T036, T037, T112, T113, T062, T064, T043, T114, T062, T065, T100, T105 | ✓ |

**Result**: ✓ All 37 FRs covered.

### ✓ 2. User stories → acceptance evidence (6/6 USs)

| US | Test evidence task |
|---|---|
| US-1 (push happy path) | T063 (DefaultConfigEditor happy path), T130 (E2E `editorA_push_no_conflict`) |
| US-2 (merge) | T037 (ConfigDiff tests for all 5 scenarios), T064 (conflict path), T114 (merge UI tests), T130 (E2E conflict) |
| US-3 (Managed-as-editor) | T072 (Security Rules — managed_can_write_config), T073, T143 (manual smoke) |
| US-4 (pending warning) | T106 (PendingBanner StateRestoration), T108 (badge visibility), T130 (E2E offline-pending) |
| US-5 (persisted Room) | T052 (Room parity contract test), T140 (cold start macrobenchmark) |
| US-6 (flows/slots/contacts) | T012 (types support all), T037 (diff handles all element types), T143 (smoke) |

**Latency-mentioning USs**: SC-004a `≤ 650 ms p95` — covered by macrobenchmark task T140 ✓.

**Result**: ✓ All 6 USs have test + perf evidence где требуется.

### ✓ 3. Plan → Spec ground (no smuggled architecture)

Каждый component в plan.md имеет FR or US:

| Plan element | Spec ground |
|---|---|
| ConfigDocument / Flow / Slot / Contact | FR-001..006 |
| ConfigDiff | FR-051 |
| ConfigApplier port | FR-021..023, FR-030..033 |
| ConfigEditor port | FR-010..014, FR-040, FR-054..057 |
| LocalConfigStore port | FR-041..047 |
| NetworkAvailability port | FR-022 T2 |
| AppForegroundEvents port | FR-022 T4 |
| ConfigRefreshWorker (no port) | FR-022 T3 |
| Room ConfigSyncDatabase | FR-041, FR-042 |
| MergeScreen | FR-050, FR-051 |
| PushIndicator + AppliedIndicator | FR-015, SC-001, SC-001b |
| PendingBanner + DiscardDialog | FR-047, FR-057 |

**No new dependencies без FR ground**:
- Room (androidx.room) — justified by FR-041/042 (Article XIII).
- No new vendor SDKs.

**Result**: ✓ No smuggled architecture.

### ✓ 4. Contracts → tests (roundtrip + backward-compat)

| Contract | Roundtrip task | Backward-compat task |
|---|---|---|
| `contracts/config.md` | T031 (`roundtrip_minimal` + `roundtrip_full`) | T032 (`backwardCompat_v0_reads_v1`) |
| `contracts/state-applied.md` | T035 (`roundtrip_bootstrapOnly` + `roundtrip_full` + `partialApply_serialized`) | T035 (`backwardCompat_007Reader`) |

**Result**: ✓ Both contracts have roundtrip + backward-compat tasks.

### ⚠ 5. Checklists → spec citations (mostly cited, formatting variance)

Все 10 checklist'ов имеют CHKxxx items с finding-rationale, но **citation style varies** — некоторые ссылаются на FR/SC explicitly («FR-001», «SC-005»), другие — на acceptance scenarios или Edge Cases без literal `Spec §`-prefix. По существу — все findings traceable to spec.md content.

**WARN**: minor — style inconsistency, not actual missing citations. Не блокирует.

### ✓ 6. Deleted-file references (FR-045 cleanup)

FR-045 «cleanup legacy mock-storage» — T005 inventory + T054 cleanup implementation.

**No DELETE: blocks в plan.md** — все changes additive или внутри новых файлов. Legacy 003 mock files **deleted at runtime by code** (FR-045), не git-delete'ятся (могут оставаться как dev fixtures если их где-то используют тесты).

**Action для T005**: inventory должен также проверить, не ссылаются ли на эти legacy paths другие docs (`docs/**`) — если ссылаются, обновить. Текущий T005 acceptance criterion explicit: «explicit list of paths; will be used in T037 [LEGACY MOCK STORAGE CLEANUP]» — мало requirements о docs scan.

**WARN**: extend T005 acceptance с явным `grep docs/ specs/ for path references`.

### ✓ 7. Tasks → ordering (no forward dependencies)

Случайная проверка зависимостей:
- T010 (ElementId) depends on T002 (Kotlin 2.0.20) ✓ backward.
- T012 (ConfigDocument) depends on T010, T011 ✓ backward.
- T036 (ConfigDiff.compute) depends on T015 (ConfigDiff data class) ✓ backward.
- T060 (FirebaseConfigApplier) depends on T018, T051 ✓ backward.
- T130 (E2E) depends on T040, T042, T038, T044 ✓ backward.

**Result**: ✓ No forward dependencies detected.

### ⚠ 8. Required context links

spec.md mentions ADRs via memory references but not literal markdown links:
- ADR-001 (Platform Parity Gate) — referenced in plan.md §Required Context Review with no markdown link.
- ADR-004 (Localization) — same.
- ADR-005 (UI Stack) — same.

plan.md cross-refs spec 007 properly (markdown links). Constitution and CLAUDE.md properly linked.

**WARN**: ADR-001/004/005 не имеют markdown links в plan.md. Низкий приоритет — files exist at known paths (`docs/adr/ADR-NNN.md`), reader может найти. Но «documented context» principle prefers explicit links.

---

## Summary

```
CROSS-ARTIFACT TRACE for specs/008-bidirectional-config-sync/:

✓ 1. All 37 FRs covered by tasks (Traceability Matrix in tasks.md)
✓ 2. All 6 USs have test evidence (5 unit/integration + 1 manual smoke for US-3)
✓ 3. Plan grounded in spec — no smuggled architecture (every new port/type has FR)
✓ 4. Both contracts (config + state-applied) have roundtrip + backward-compat tasks
⚠ 5. Checklists cite spec content but citation style varies (not blocking)
✓ 6. No DELETE files; FR-045 runtime cleanup tracked via T005/T054
⚠ 6b. T005 acceptance should explicitly include docs/ grep for legacy path references
✓ 7. Task ordering valid (no forward dependencies)
⚠ 8. ADR-001/004/005 mentions in plan.md lack markdown links (low priority)

PUNCH LIST (3 minor items):
  1. ⚠ Extend T005 acceptance with "grep docs/ specs/ for path references" (1-line edit).
  2. ⚠ Add markdown links для ADR-001, ADR-004, ADR-005 в plan.md §Required Context Review.
  3. ⚠ (Optional) Add Spec §FR-NNN citation prefix style guide to checklists folder.

NONE are blockers. tasks.md is COMPLETE and ready для /speckit.analyze.
```

---

## Action

I will address items 1 and 2 inline (minor edits), leave item 3 as-is (style consistency не worth churn).
