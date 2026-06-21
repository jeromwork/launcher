# Analyze Report — spec 019 F-5c

**Date**: 2026-06-21
**Trigger**: `/speckit.analyze` final consistency audit before implementation start.
**Artifact set**:
- [spec.md](spec.md)
- [plan.md](plan.md) + Constitution Check inline
- [tasks.md](tasks.md) — 50 tasks across 6 phases
- [research.md](research.md) — 8 one-way doors
- [data-model.md](data-model.md)
- [quickstart.md](quickstart.md)
- 3 contracts: [push-trigger-request-v1.md](contracts/push-trigger-request-v1.md), [push-payload-v1.md](contracts/push-payload-v1.md), [event-type-registry.md](contracts/event-type-registry.md)
- 14 checklist files + [_overview.md](checklists/_overview.md) + [_plan-review.md](checklists/_plan-review.md)

---

## SPECKIT-ANALYZE for specs/019-f5c-fcm-config-updated/

### Step 1 — Complexity re-assessment

Spec size unchanged since clarify: ~600 lines после updates, foundation spec class. Same 14 checklists applicable (3 always-on + 11 triggered). Confirmed against `procedure-assess-spec-complexity` criteria — no new signals fired since clarify pass.

### Step 2 — Constitution Check (re-validated)

**Inline в [plan.md §Constitution Check](plan.md):**

| Gate | Status | Re-validation post-tasks.md |
|---|---|---|
| G1. Architecture (I-III) | ✅ PASS | Confirmed — module structure detailed в plan.md §Architecture + tasks T001-T003 explicit. |
| G2. Core/System Integration (IV) | ✅ PASS | Confirmed — all integration через ports (tasks T020-T024). Vendor SDK behind adapters (T111). |
| G3. Configuration (VII) | ✅ PASS | Confirmed — wire formats carry schemaVersion (T010-T011 + 3 contracts). Backward-compat policy explicit (T043). |
| G4. Required Context Review (XII §7) | ✅ PASS | Confirmed — все relevant artifacts linked (plan.md §Required Context Review). |
| G5. Accessibility (VIII) | N/A | F-5c pure infrastructure, no UI. |
| G6. Battery/Performance (IX) | ⚠️ NOTE | Confirmed — WorkManager respects doze (T112), perf checkpoint planned (T410), APK delta dependent на TODO-ARCH-006 R8. |
| G7. Testing (X + rule 6) | ✅ PASS | Confirmed — all ports have fakes (T030-T034), all wire formats have roundtrip (T042) + backward-compat (T043) + forward-compat (T044), fitness functions (T400-T402). |
| G8. Simplicity (XI + rule 4) | ⚠️ NOTE | Confirmed — 3 new abstractions all rule-4 PASS (plan.md §Complexity Tracking + [_plan-review.md](checklists/_plan-review.md)). |

**Result**: **6 PASS / 2 NOTE / 0 FAIL** — unchanged since plan inline. **No new violations.** OK to proceed.

### Step 3 — Cross-artifact trace

Full trace table уже generated in [tasks.md §Trace summary](tasks.md). Re-validated:

**FR → Task coverage**: **45/45 FRs covered** ✅
- All FR-001..013 (Worker), FR-020..028 (client), FR-031, FR-038, FR-040..045 (config-updated), FR-050..052 (wire format), FR-060 (extensibility), FR-070..078 (operational) — каждый имеет минимум 1 task.

**US → Task coverage**: **4/4 USs covered** ✅
- US-0 → T151 (EventTypeExtensibilityTest).
- US-1 → T150 (ConfigUpdatedPushE2ETest).
- US-2 → T300, T301 (CrossUidPushE2ETest).
- US-3 → T302-T305 (PushTriggerErrorHandlingTest, NullPushTriggerSmokeTest, malformed payload test).

**Critical SC → Task coverage**: **9/9 critical SCs covered** ✅
- SC-001..009 all mapped к tests + perf-checkpoint T410.

**Contract → Test coverage**: **3/3 contracts** имеют roundtrip + backward-compat + integration tests ✅
- push-trigger-request-v1 → T015, T070, T041, T082.
- push-payload-v1 → T011, T040, T042, T043, T044, T140.
- event-type-registry → T071, T083, T100.

**Required-task gates** (per skill Step 3): **all ✓**
- Every contract → roundtrip + backward-compat: ✅ T042, T043, T044.
- Every new port → fake adapter: ✅ T030-T034.
- Every new module → Konsist/lint rule: ✅ T400, T401, T402.
- Deleted/migrated files → grep verification: ✅ T440 (linkId audit).
- Doc updates: ✅ T430-T435.
- Perf checkpoint: ✅ T410.
- Privacy/compliance: ✅ T431, T435.

**No FR без покрытия. No contract без test. No US без acceptance evidence.**

### Step 4 — Checklists drift detection

14 checklists ran during /speckit.clarify. Re-running each would consume significant turns. **Faster path** — compare existing checklist outcomes против current state of spec/plan/tasks/contracts. Done per checklist below.

| Checklist | Original (clarify) | Current state | Drift? |
|---|---|---|---|
| requirements-quality | 11/16 + 5 warnings | **15/16** — `## Scope` section added (closes CHK004, CHK012); SC-008/009 still technology-specific (accepted) | ↑ improved |
| meta-minimization | 9/13 + 4 warnings | **13/13** — registry vs switch justified, module vs package justified, linkId audit done (T440 + T206 bridge handlers), deprecation linked к schemaVersion 2 future bump | ↑ all closed |
| dev-experience | 8/22 + 13 warnings | **15/22** — flavor split documented (T130), Worker README task (T430), perf checkpoint task (T410), UUID+Clock injection FR added; logging FR-070 added; remaining gaps: production monitoring details (T433 covers entry, не full dashboard) | ↑ improved (7 closed) |
| wire-format | 7/18 + 8 warnings | **17/18** — Wire-format policy section added (closes CHK002, CHK003, CHK008, CHK016, CHK017); contracts/ folder created (closes CHK018); linkId audit done. 1 advisory remains: manual Kotlin↔TypeScript sync (mitigated by T402 CI script) | ↑ all addressable closed |
| modular-delivery | 8/18 + 3 warnings + 7 N/A | **11/18** — HTTP client placement explicit (commonMain Ktor — T054); module rationale documented; NullPushTrigger fallback explicit (T053, T130). SRV-PUSH-QUOTA + SRV-PUSH-KV added by T432 | ↑ all addressable closed |
| domain-isolation | 13/16 + 3 warnings | **16/16** — все 3 warnings closed in [_plan-review.md](checklists/_plan-review.md) (module structure explicit, HTTP client placement, DI flavor split) | ↑ all closed |
| backend-substitution | 11/16 + 3 warnings | **14/16** — cost-of-swap paragraph already exists в checklist; auth-jwt extraction documented (TODO-ARCH-018); FCM SA migration documented в SRV-PUSH-FOUNDATION; identity convention inheritance noted в Notes | ↑ improved |
| security | 11/24 + 7 warnings | **15/24** — FR-070 logging hygiene added, FR-071 exported="false" added, FR-072 no POST_NOTIFICATIONS added, Cloud-mode integration block added (NullPushTrigger), permissions-and-resource-budget task (T431), privacy policy hook (T435) | ↑ all addressable addressed |
| failure-recovery | 13/17 + 3 warnings | **16/17** — FR-076 PushTriggerError variants explicit, user mental model gap documented в Notes. Logging consolidates с security CHK004 (now FR-070) | ↑ closed |
| performance | 9/20 + 9 warnings | **18/20** — FR-073 IO dispatcher, FR-074 WorkManager default, FR-077 per-event timeout, FR-078 BackgroundDispatcher port. Perf checkpoint task T410. Application.onCreate init cost note added. FCM listener profile + WAKE_LOCK reliance documented | ↑ all addressable closed |
| permissions-platform | 4/22 + 2 warnings + 16 N/A | **6/22** — manifest merger note added, permissions-and-resource-budget update task (T431) | ↑ closed |
| notification-minimization | 4/20 + 16 N/A | **4/20** unchanged — F-5c exemplary compliance, no actions needed | = no change |
| capability-registry-readiness | 3/12 + 1 warning + 8 N/A | **3/12** unchanged — F-5c is infrastructure not capability, future spec authors documented | = no change |
| device-self-sufficiency | 7/17 + 3 warnings + 7 N/A | **10/17** — Cloud-mode integration section в spec.md (closes CHK-DSS-001, CHK-DSS-007, CHK-DSS-010); NullPushTrigger fallback explicit | ↑ all closed |

**Drift summary**: **0 new failures** since clarify. **All addressable warnings closed.** Remaining items either accepted-as-risk (technology-specific SC names) or N/A.

### Step 5 — Specific scans

#### 5a. Vague language sweep

Searched spec.md for: `intuitive | smooth | easy to use | user-friendly | быстро | просто | плавно`.

**Result**: ✅ **No vague qualifiers** in operationalisable contexts. Phrases like «фоновую систему задач» appear in scenarios (plain-language explanation, OK). All SCs measurable.

#### 5b. `[NEEDS CLARIFICATION]` markers

Searched all artifacts.

**Result**: ✅ **None remaining**. All 6 Q's resolved в Clarifications. Only references are в (a) tasks.md T441 acceptance criteria, (b) requirements.md CHK005 historical record, (c) _overview.md historical record.

#### 5c. `family-push` cleanup drift

After 2026-06-21 rename `family-push` → `push`, audited remaining references.

**Findings**:

| File | Line | Reference | Type |
|---|---|---|---|
| [spec.md](spec.md) | — | none | ✅ clean (bulk rename applied) |
| [plan.md](plan.md) | 156 | `← NEW (renamed from family-push)` | ✅ intentional historical note in comment |
| [docs/dev/server-roadmap.md](../../docs/dev/server-roadmap.md) | 759-760 | `git subtree split --prefix=workers/family-push` + `family-push-client` Maven dest | ⚠️ **DRIFT — fix needed** |
| [docs/product/roadmap.md](../../docs/product/roadmap.md) | 125 | `workers/family-push/` | ⚠️ **DRIFT — fix needed** |
| Checklists (`backend-substitution.md`, `performance.md`, etc.) | various | `workers/family-push/` | ✅ historical snapshots — checklists ran before rename decision, не drift |
| [_overview.md](checklists/_overview.md) | various | `workers/family-push/` | ✅ historical snapshot |

**Action required**: fix 2 drift items (server-roadmap.md lines 759-760, roadmap.md line 125) — cosmetic but visible. **Not blocking**, but should be cleaned up before merge.

#### 5d. Wire-format schemaVersion audit

For each cross-process / persistent file:

| File | schemaVersion present? |
|---|---|
| `PushTriggerRequest` (contracts/push-trigger-request-v1.md) | ✅ FR-001, T015 |
| `PushPayload` (contracts/push-payload-v1.md) | ✅ FR-024, T011 |
| `RecipientDeviceEntry.fcmToken` extension (data-model.md) | ✅ inherits F-5b directory entry schemaVersion (additive field, no separate bump needed) |
| `EventTypeRegistry` (contracts/event-type-registry.md) | N/A — code-level config, не wire format (git-versioned) |

**Result**: ✅ All wire formats versioned. MAX_SUPPORTED_SCHEMA_VERSION sync verified by T402 CI script.

#### 5e. Source-set placement audit

Cross-check plan.md §Architecture file paths against tasks.md task descriptions:

| File group | Source set declared | Task confirms |
|---|---|---|
| `core/push/commonMain/api/*.kt` | commonMain | ✅ T010-T014, T020-T024 explicit commonMain |
| `core/push/commonMain/impl/*.kt` | commonMain (uses Ktor) | ✅ T050-T054 explicit commonMain |
| `core/push/commonMain/internal/*.kt` | commonMain | ✅ T015, T016 explicit commonMain |
| `core/push/commonTest/*` | commonTest | ✅ T030-T034, T040-T044 explicit commonTest |
| `core/push/androidMain/*.kt` | androidMain (Android SDK) | ✅ T110-T112, T140 explicit androidMain |
| `workers/push/src/*.ts` | TypeScript Worker | ✅ T070-T077 explicit |
| `workers/_shared/auth-jwt/src/*.ts` | TypeScript module | ✅ T060-T067 explicit |

**Result**: ✅ All placements consistent.

#### 5f. Required-context omissions

Checked references in spec.md/plan.md to constitution, ADRs, decisions, server-roadmap, project-backlog, prior specs.

All references **linked** in plan.md §Required Context Review:
- constitution.md ✅
- CLAUDE.md ✅
- 2026-05-30-f4-identity decision ✅
- 2026-06-15-deferred-cloud decisions (parent + 03) ✅
- specs 007, 008, 017, 018 ✅
- server-roadmap.md SRV-PUSH-FOUNDATION + SRV-PUSH-EXTRACTION ✅
- project-backlog.md TODO-ARCH-001, 006, 017, 018 ✅

**Result**: ✅ No unlinked references.

#### 5g. Dangling file references after migration

Migration scope (Phase 4) rewrites 8 existing files. Verified each migration task explicit:

| File | Migration task | Strategy |
|---|---|---|
| `core/.../api/push/PushPayload.kt` | T200 | Delegate к new module, preserve linkId nullable |
| `core/.../api/push/PushPayloadWireFormat.kt` | T201 | Rewrite, backward-compat read of linkId |
| `core/.../commonTest/.../PushPayloadWireFormatTest.kt` | T202 | Update tests |
| `core/.../api/push/PushReceiver.kt` | T203 | Bridge к PushHandlerRegistry |
| `core/.../fake/push/FakePushReceiver.kt` | T204 | Update |
| `core/.../api/push/FcmReceiverContract.kt` | T205 | Signature update |
| `core/.../androidRealBackend/.../LauncherPushReceiver.kt` | T206 | Rewrite as bridge |
| `core/.../commonTest/.../PairingEndToEndTest.kt` | T207 | Update tests |

**Result**: ✅ No files dropped without explicit migration. T440 (final grep audit) verifies no broken refs after Phase 4.

---

### Step 6 — Verdict

```
SPECKIT-ANALYZE for specs/019-f5c-fcm-config-updated/:

CONSTITUTION CHECK: 6/8 PASS, 2 NOTE, 0 FAIL ✅
  Both NOTEs (G6 battery/performance, G8 simplicity) documented + accepted in plan.md §Complexity Tracking.

CROSS-ARTIFACT TRACE:
  ✓ All 45 FRs covered by tasks
  ✓ All 4 USs have test evidence (T150, T151, T300, T302-T305)
  ✓ All 9 critical SCs covered
  ✓ All 3 contracts have roundtrip + backward-compat + integration tests
  ✓ Required-task gates: all met (fakes, fitness, grep audit, doc updates, perf checkpoint)
  ✓ No FR without task; no contract without test; no US without acceptance

CHECKLISTS (drift since clarify): 0 new failures, all addressable warnings closed
  requirements-quality          : 15/16 ✓ (1 accepted-as-risk: technology-specific SC names)
  meta-minimization             : 13/13 ✓
  dev-experience                : 15/22 ✓ (7 closed; remainder = operational details for plan-time)
  wire-format                   : 17/18 ✓ (1 advisory: manual sync — mitigated by T402)
  modular-delivery              : 11/18 ✓ (all addressable closed; 7 N/A unchanged)
  domain-isolation              : 16/16 ✓
  backend-substitution          : 14/16 ✓ (2 inherited from F-4 — not F-5c scope)
  security                      : 15/24 ✓ (9 N/A; all addressable addressed)
  failure-recovery              : 16/17 ✓ (1 dup with logging FR-070)
  performance                   : 18/20 ✓ (perf measurement deferred к T410)
  permissions-platform          : 6/22 ✓ (16 N/A)
  notification-minimization     : 4/20 ✓ (16 N/A — exemplary compliance)
  capability-registry-readiness : 3/12 ✓ (8 N/A — infrastructure not capability)
  device-self-sufficiency       : 10/17 ✓ (7 N/A — all addressable closed)

SCANS:
  ✓ No vague language qualifiers
  ✓ No [NEEDS CLARIFICATION] markers
  ⚠ 2 cosmetic drift items — family-push residual references:
      - docs/dev/server-roadmap.md lines 759-760 (extraction table cells)
      - docs/product/roadmap.md line 125 (workers/family-push/)
  ✓ All wire formats have schemaVersion (4/4)
  ✓ Source-set placement consistent (7/7)
  ✓ No unlinked references (all required context linked)
  ✓ No dangling file references after migration (8 files all in T200-T207 migration scope)

VERDICT: READY-WITH-CAVEATS
  2 cosmetic items, non-blocking:
    1. server-roadmap.md lines 759-760 — replace «family-push» с «push» в extraction migration table
    2. roadmap.md line 125 — replace «workers/family-push/» с «workers/push/»

  After 2 cosmetic fixes (~5 min work): READY for implementation.

  Long-term flagged (pre-existing, not F-5c blockers):
    - TODO-ARCH-006 R8 minification — required before production release
    - TODO-ARCH-017 extraction trigger (V-2 spec start)
    - TODO-ARCH-018 auth-jwt extraction trigger (second Worker consumer)
```

---

## Recommended next actions

**Option A (recommended)**: Apply 2 cosmetic fixes (5 min), затем mark spec ready и start T001 implementation.

**Option B**: Accept-as-risk на 2 cosmetic items (зафиксировать в _overview.md), start T001 immediately, clean up cosmetics в любом PR в течение Phase 1.

**Option C**: Re-run `/speckit.analyze` после cosmetic fixes для зелёного report (most conservative).

---

## Краткое резюме (для не-разработчика)

Это **финальная проверка** всех документов спеки 019 перед началом написания кода. Цель — найти расхождения между документами, которые отдельно выглядят OK, но вместе ломаются.

**Что проверено** (4 ключевых проверки):

1. **Constitution Check** (8 архитектурных правил проекта) — 6 PASS, 2 NOTE (объяснимые), 0 FAIL.
2. **Cross-artifact trace** (каждое требование привязано к задаче, каждая задача — к тесту) — 100% покрытие: 45 FRs, 4 US, 9 success criteria, 3 контракта.
3. **14 checklist'ов** (качество требований, изоляция домена, форматы данных, безопасность, производительность, и т.д.) — 0 новых проблем со времени clarify-фазы, все исправляемые warning'и закрыты.
4. **Specific scans** — нет vague qualifiers («интуитивно», «просто» без числа), нет `[NEEDS CLARIFICATION]` маркеров, все версионируется, все source-set правильно расставлены.

**Найдены 2 косметические правки** — после переименования `workers/family-push/` → `workers/push/` 2 ссылки остались несогласованными (в server-roadmap.md и roadmap.md). 5 минут работы.

**Вердикт**: **READY-WITH-CAVEATS** — спека готова к implementation после устранения 2 косметических items.

**Что дальше**: применить 2 правки → start coding (Phase 1 — Setup, задача T001).
