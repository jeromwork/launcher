# Analyze Report: Canonical ECS foundational model (TASK-136)

**Date**: 2026-07-18 | **Skill**: speckit-analyze | **Artifacts**: spec.md · plan.md · tasks.md · data-model.md · contracts/{profile-serialization,ecs-world-core,query-api}.md
**Backlog authority**: [task-136 Decision block](../../backlog/tasks/task-136%20-%20Decision-ECS-canonical-foundational-model.md)
**Verdict**: **READY-WITH-CAVEATS** — 4 completeness gaps in the consumer inventory / task decomposition; constitution + FR/SC/contract traceability all pass; the big-bang build gate (T136-044) is a backstop but the artifacts state scope inaccurately and leave the fix untracked.

---

## Step 1 — Complexity re-assessment (procedure-assess-spec-complexity)

Pure-domain / model refactor in `core/commonMain`. No new UI, no permission surface, no server endpoint, no push, no billing, no lifecycle change.

- **Always-on**: `checklist-requirements-quality`, `checklist-meta-minimization`, `checklist-dev-experience`.
- **Triggered**: `checklist-domain-isolation` (commonMain, ports, adapters), `checklist-wire-format` (JSON serialization, schemaVersion, contracts, persistence), `checklist-preset-readiness` (bundled presets, `pool.json`, `BundledSource`/`ConfigSource`), `checklist-performance` (light — `<1 ms` linear scan, `BootCheck` cold-start).
- **Skipped (N/A, no signal)**: state-management, security, permissions-platform, ux-quality, accessibility, elderly-friendly, localization(-ui), server-hardening, zero-knowledge-server, capability-registry-readiness, ai-readiness, notification-minimization, tamper-resistance, backend-substitution (Profile is an opaque blob; no server touch), core-quality, modular-delivery, device-self-sufficiency.

---

## Step 2 — Constitution check (procedure-constitution-check against plan.md)

```
CONSTITUTION CHECK for specs/task-136-ecs-canonical-foundational-model/plan.md:
  Gate 1 Architecture           : PASS  — in-place rewrite in core/preset/* + one new sub-package preset/ecs/; no new gradle module (Article V §3, none of 5 criteria met); arrows-downward diagram; ports unchanged.
  Gate 2 Core/System Integration: N/A   — no BroadcastReceiver / boot / lifecycle introduced; ReconcileEngine+Provider roles unchanged (FR-011).
  Gate 3 Configuration          : PASS  — wire-format change (free-bag entity, entity-grouped, Bundle) under Article XX pre-MVP override (verified present in constitution.md §§576-598): schemaVersion field present, value=2, no bump/migrator/backward-compat, explicitly licensed. Roundtrip + at-most-one-per-type + coverage contract tests present.
  Gate 4 Required Context Review: PASS  — CLAUDE.md rules (1,3,4,5,9,11,13), Article XX/XI/XVI/XVII, ADR-012, preset-model.md, TASK-136/120/127, TASK-127 plan all linked; omissions (compliance/server/product) explained.
  Gate 5 Accessibility          : PASS  — no new UI surface; render gating (Failed/Skipped hidden) preserved via LifecycleState — the "no dead button" elderly invariant kept, not weakened.
  Gate 6 Battery/Performance    : PASS  — no background work, no polling, no new deps; query linear scan <1 ms at ~20-40 entities (NFR-002); BootCheck still skips Unverifiable.
  Gate 7 Testing                : PASS  — contract (entity-grouped roundtrip, fail-loud pins), unit (composition/query/spawn/hierarchy/engine-state), fitness (coverage, at-most-one-per-type, tag-consistency, import-guard, LOC budget). Ports keep fake+real. Backward-compat test intentionally removed (Article XX — documented, not a gap).
  Gate 8 Simplicity             : PASS-with-documented-deviation — own core minimal (Family DSL + compose ops, ≤400 LOC, no scheduler, concrete-to-Component). Multi-data-component capability has no current product consumer — consciously accepted (Decision Rationale 6) and recorded as an Article XVII deviation with a removal condition. Legitimate per Article XVII.

OVERALL: 6 PASS, 1 N/A, 1 PASS-with-deviation, 0 FAIL — plan is COMPLETE. NOT stop-the-line.
```

Concur with plan's self-check (plan §Constitution Check reports 7 PASS / 1 N/A). Article XX and Article XVII both verified present in `.specify/memory/constitution.md`, so the two load-bearing overrides (no-migrator; documented deviation) are genuinely licensed, not asserted.

---

## Step 3 — Cross-artifact trace (procedure-cross-artifact-trace)

```
CROSS-ARTIFACT TRACE:
  ✓ Spec→Tasks: all FR-001..FR-021 + FR-STATE + NFR-001..004 covered by ≥1 task (trace map below).
  ✓ Success Criteria SC-001..SC-011 each covered by ≥1 task.
  ✓ Contracts→tests: profile-serialization → T136-017/018/019/033/034; ecs-world-core → T136-028/034/035/037; query-api → T136-029/031. (Backward-compat test intentionally deleted per Article XX — documented in contract §"Removed vs TASK-127", not a dangling gap.)
  ✓ Plan→Spec ground: every plan module (preset/ecs/, LifecycleState, Family DSL) traces to an FR (FR-012/FR-STATE/FR-008). No smuggled architecture.
  ✓ Task ordering: no forward dependency references (spot-checked T136-008/034/044).
  ✗ Deleted-symbol consumer inventory INCOMPLETE — 4 production sites reference deleted symbols (Entity.status / ComponentStatus) but are absent from plan's Consumer inventory AND have no covering migration task (see Step 5 scan + Punch list #1-#2).
  ⚠ ADR-012 referenced in prose in spec several times; linked once (in plan). Minor (WARN).
```

**FR → task trace (verified complete)**: FR-001→T004/006 · FR-002→T002 · FR-003→T002/004 · FR-004→T005 · FR-005→T007/011 · FR-006→T008 · FR-007→T008 · FR-008→T009/014 · FR-009→T015 · FR-010→T016 · FR-011→T012 · FR-STATE→T003/012/032 · FR-012→T010/037 · FR-013→T004 · FR-014→T013/031 · FR-015a→T033 / b→T038 / c→T036 / d→T034 / e→T035 · FR-016→T001/022-024/044 · FR-017→T039 · FR-018→T040 · FR-019→T041 · FR-020→T020/025-027 · FR-021→T042 · NFR-001→T038 · NFR-002→T029 · NFR-003→T037 · NFR-004→T021.

---

## Step 4 — Checklists (chat-only, red-only per ADR-011 §5; no persisted files)

```
checklist-requirements-quality : PASS — spec content-complete, testable; developer-facing capability stories intentional (spec §"Тип фичи").
checklist-meta-minimization    : PASS-with-flagged-tension — multi-DATA-component capability has no current product consumer; consciously accepted as Article XVII deviation (Decision Rationale 6) with a documented removal condition. Not a fail — the anti-bloat concern is surfaced and owned, not hidden.
checklist-dev-experience       : PASS — Local Test Path lists concrete `./gradlew :core:test` commands; zero prod-account / real-device dependency; one [deferred-local-emulator] smoke correctly marked.
checklist-domain-isolation     : PASS — Entity/Component/Blueprint/preset/ecs pure Kotlin; import-guard fitness (FR-015b/SC-008) + source-set placement; arrows-downward diagram (plan §Layered shape).
checklist-wire-format          : FAIL: CHK — Preset wire-format change unacknowledged. `Preset.ActiveComponentEntry.status: ComponentStatus` (Preset.kt:53) is a @Serializable wire field; deleting ComponentStatus (T136-003) changes the Preset JSON shape, yet tasks.md T136-021 asserts "Preset is NOT reshaped" and no roundtrip/fixture task covers the Preset change. (Profile/Blueprint/Pool wire changes themselves are correctly handled under Article XX.)
checklist-preset-readiness     : PASS — BundledSource-as-one-of-many + `// TODO(shareability)` seam explicitly preserved (FR-020, R-8, T136-025); schemaVersion field retained.
checklist-performance          : PASS — NFR-002 <1 ms linear scan; no background work / polling / new deps; cold-start unchanged.
```

Grey items to fold into artifacts before implementation: the wire-format CHK above = same root cause as Punch list #1 (Preset status field).

---

## Step 5 — Specific scans

### 5.1 Deleted-symbol dangling references (grep against real code)

Deletions per data-model.md §"Delete/rename summary": `ComponentStatus` enum · `Entity.status` field · `Entity.component` · `Blueprint.component` · `Component.tags` · `Profile.mark`/`replaceComponent`.

**Covered consumers (in plan inventory + have tasks)** — `ProfileQuery.kt`, `ProfileFactory.kt`, `ReconcileEngine.kt`, `PresetValidator.kt`, `PresetDiff.kt`, `ProfileBackedFlowRepository.kt`, `Profile.kt`, `WizardScreen.kt`, `PostWizardKioskApply.kt`. Decompose false-positives (`RootComponent.kt`, `RootContent.kt` — navigation `component`) correctly excluded. `.status` false-positives on GMS (`GmsStatus.kt`, `AuthAdapterSelector.kt`, `GmsHardBlockScreen.kt`) confirmed unrelated. `ProfileEngine.kt` (androidMain, `com.launcher.core.profile`, `SUPPORTED_SCHEMA=1`) is a **different** config-Profile model — unrelated, not a consumer.

**UNCOVERED genuine consumers of deleted symbols (NOT in plan inventory, NO covering task)** — this is real drift, not "old-world still present":
1. **`core/.../preset/model/Preset.kt:53`** — `ActiveComponentEntry.status: ComponentStatus = ComponentStatus.Pending`. Won't compile once `ComponentStatus` is deleted. Directly contradicts tasks.md T136-021 ("`Preset` is not reshaped") + data-model.md (omits this field from the delete/rename summary). Also a Preset wire-format change (see checklist-wire-format FAIL).
2. **`app/.../wizard/WizardViewModel.kt`** (production) — reads `Entity.status` (line 101), `deny(component: Entity)`, `askUser(component: Entity): Component?`. No Phase-6 task migrates it.
3. **`app/.../firstlaunch/FirstLaunchActivity.kt:647`** — `pc.status != ComponentStatus.Applied`. No task.
4. **`app/.../settings/PendingChecklistViewModel.kt`** — `Entity.status`, `ComponentStatus` (lines 4,15-16,54,62). No task.

Phase 6 ("Consumers") migrates only 3 files (T136-022 ProfileBackedFlowRepository, T136-023 WizardScreen, T136-024 PostWizardKioskApply). The `status`→`LifecycleState` consumers above are the ones the LifecycleState decision (the single point the plan finalized) most directly breaks, and they are untracked. T136-044 (`:app` compile) + T136-001 ("record newly-discovered callsites in the PR") form a backstop, so the big-bang won't *ship* broken — but the decomposition is incomplete and two artifacts (T136-021, data-model summary) misstate scope.

### 5.2 Wire-format schemaVersion audit
Profile serialization contract: `schemaVersion` present, value=2, no bump/migrator/backward-compat — licensed by Article XX (verified). PASS. **Exception**: Preset wire format changes silently (5.1 #1) — flagged.

### 5.3 Source-set placement audit
All new domain files (`preset/ecs/*`, `LifecycleState`) declared in `commonMain` (plan §Module map); consumers in `app/androidMain`; test fakes (`TestFlag`) in `commonTest`. Consistent with declarations. PASS.

### 5.4 Required-context omissions
Plan links CLAUDE.md rules, constitution Articles XX/XI/XVI/XVII, ADR-012, preset-model.md, TASK-136/120/127, TASK-127 plan; omissions (compliance/server/product) explicitly explained. `docs/architecture/preset-model.md` + `docs/adr/ADR-012-*.md` confirmed to exist; TASK-120/127 + downstream TASK-19/68/69/71 files confirmed to exist. PASS. Minor WARN: some ADR-012 spec mentions are bare text (cosmetic).

### 5.5 Vague-language sweep
Grep of spec for `intuitive|smooth|seamless|fast|snappy|should be|user-friendly|as needed|etc.` → **no survivors** (only "simple-launcher" preset id, not vague-language). PASS.

---

## Step 6 — Verdict

```
SPECKIT-ANALYZE for specs/task-136-ecs-canonical-foundational-model/:

CONSTITUTION CHECK: 6 PASS, 1 N/A, 1 PASS-with-Article-XVII-deviation, 0 FAIL — COMPLETE (not stop-the-line).

CROSS-ARTIFACT TRACE:
  ✓ All FR-001..021 + FR-STATE + NFR covered by tasks
  ✓ All SC-001..011 covered
  ✓ All 3 contracts have test tasks (backward-compat waived per Article XX — documented)
  ✓ Task ordering valid; no smuggled architecture
  ✗ Deleted-symbol consumer inventory incomplete (4 uncovered production sites)
  ⚠ ADR-012 mostly bare-text mentions in spec (cosmetic)

CHECKLISTS (chat-only):
  requirements-quality : PASS
  meta-minimization    : PASS (multi-data-component tension consciously accepted, Article XVII)
  dev-experience       : PASS
  domain-isolation     : PASS
  wire-format          : FAIL: CHK — Preset wire-format change unacknowledged (Preset.kt:53 status field)
  preset-readiness     : PASS
  performance          : PASS

SCANS:
  ✗ Deleted-symbol: 4 uncovered consumers (Preset.kt, WizardViewModel, FirstLaunchActivity, PendingChecklistViewModel)
  ✓ schemaVersion (Profile) present=2, no migrator (Article XX)
  ✗ schemaVersion (Preset) shape changes silently — unacknowledged
  ✓ Source-set placement consistent
  ✓ Required-context links present; files exist
  ✓ No vague-language survivors

VERDICT: READY-WITH-CAVEATS
```

### Punch list (address or accept-as-risk before implementation)

1. **Correct the Preset scope statement + track its change.** `Preset.ActiveComponentEntry.status: ComponentStatus` (Preset.kt:53) references a deleted enum. Either (a) remove/replace the field (Preset IS partially reshaped) or (b) justify retention — but tasks.md T136-021's flat claim "`Preset` is not reshaped" is false as written. Add a task (or fold into T136-011/013) to migrate `ActiveComponentEntry.status`, and acknowledge it as a Preset wire-format change (add to data-model.md delete/rename summary + a Preset-fixture roundtrip touch). This resolves the checklist-wire-format FAIL.
2. **Add Phase-6 tasks (or expand the inventory) for the 3 app-layer status consumers**: `WizardViewModel.kt`, `FirstLaunchActivity.kt:647`, `PendingChecklistViewModel.kt` — all read `Entity.status`/`ComponentStatus` and must move to `entity.get<LifecycleState>()`. Also update the plan §Consumer inventory + data-model.md §7 table, which currently omit them. (Their app-layer unit tests — `PendingChecklistViewModelTest`, `task126/*` — will also need the reshape; note them in the same task.)
3. **(Minor) Verify the `:app` build gate covers these.** T136-044 requires `:app` mock/real *compile*; confirm the migrated app-layer status-consumers are exercised (compile is sufficient for the fix, but the app unit tests referencing ComponentStatus need updating to keep `:app` test targets green if run).
4. **(Cosmetic) Convert bare-text ADR-012 mentions in spec to markdown links** for required-context traceability (WARN only).

**Not blockers** (correctly handled, listed for the record): no migrator / no schemaVersion bump / no backward-compat test (Article XX); multi-data-component with no consumer (Article XVII deviation, removal condition documented); docs/ADR-012 still describing the old model + TASK-120/127 lacking `superseded-by` (scheduled cleanup T136-039..042, not drift).

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Кратко по-русски (для владельца и будущего AI)

**Что это за файл.** Финальная проверка перед тем как писать код (шаг `/speckit.analyze`). Смотрит, согласованы ли между собой спека, план, задачи и контракты, и не забыто ли что-нибудь.

**Итог: READY-WITH-CAVEATS** — «можно идти дальше, но сначала закрыть несколько дыр». Фундамент решения здоровый: проверка конституции пройдена (8 ворот, 0 провалов), все требования (FR) и критерии (SC) покрыты задачами, у всех трёх контрактов есть тесты. Отказа миграции и не-двигающейся версии формата — это законно (правило Article XX «до релиза мигратор не пишем», я проверил что статья реально есть).

**Главная находка (что надо поправить до кода).** Мы удаляем поле `status` у сущности и enum `ComponentStatus` (статус становится компонентом `LifecycleState`). Но есть **4 места в реальном коде**, которые ещё читают старый `status`, и они **не попали ни в список потребителей плана, ни в одну задачу**:
1. `Preset.kt` (строка 53) — у записи пресета есть поле `status: ComponentStatus`. А в задачах прямо написано «Preset не трогаем» — это **неправда**, его придётся тронуть, и это ещё и меняет формат пресета.
2. `WizardViewModel.kt` — экран мастера, читает `status`.
3. `FirstLaunchActivity.kt` — первый запуск, читает `status`.
4. `PendingChecklistViewModel.kt` — список «что осталось настроить», читает `status`.

Сборка всё равно не даст выпустить сломанное (есть ворота «`:app` должен компилироваться» + задача T136-001 «записать все найденные места»), поэтому это **не катастрофа** — но план и задачи сейчас **неверно описывают объём**, и это надо исправить, чтобы будущий агент не поверил ложному «Preset не трогаем».

**Что делать (punch list):** (1) честно записать, что `Preset` меняется, и добавить задачу на его поле `status`; (2) добавить задачи на 3 экранных потребителя `status`; (3) убедиться что сборка их ловит; (4) косметика — оформить ссылки на ADR-012.

**Что НЕ является проблемой** (специально отмечаю, чтобы не пугало): старые доки и ADR-012 всё ещё описывают прошлую модель, а у TASK-120/127 нет пометки «заменено» — это **запланированная уборка** (задачи T136-039..042), а не рассинхрон.
<!-- NOVICE-SUMMARY:END -->
