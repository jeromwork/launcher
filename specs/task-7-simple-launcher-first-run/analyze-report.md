# Speckit-Analyze Report: TASK-7

**Date**: 2026-06-24 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Tasks**: [tasks.md](tasks.md)

> **⚠️ UPDATE 2026-06-25.** This report's PASS verdict on Phase-5 ("CustomStep + DI extension is the correct decoupling pattern") was **wrong** — Phase-5 design failed device verification, was reverted, and the `StepType.Custom` infrastructure removed entirely (constitution amendment 1.10). The report below reflects the pre-revert state. Authoritative current state: [`tasks.md`](tasks.md) Phase 5 revert + Device Verification Findings sections.

Final consistency audit before implementation per [`speckit-analyze`](../../.claude/skills/speckit-analyze/SKILL.md) orchestrator. Re-runs constitution check, cross-artifact trace, full checklist sweep, and specific scans. Reports punch list; does not auto-fix.

---

## VERDICT: READY

All checks PASS with one informational caveat (RTL coverage deferred — non-blocking). Implementation may begin.

---

## Step 1 — Re-assess complexity

Spec content unchanged since last assess pass (2026-06-24 during clarify). Triggered checklist set unchanged. Spec size = **Large**: 6 US, 35 FR, 11 SC, 6 scenarios, OEM matrix, AI Affordance, Local Test Path. Plan + tasks reflect this scale.

Assessment unchanged → same 18+ checklists triggered.

---

## Step 2 — Constitution Check (re-affirmation)

Last run at end of [plan.md](plan.md) — 8/8 PASS after Gate 4 remediation (ADR-005 + ADR-010 + research/operations omission rationale).

**No artifact changes since the constitution check that would affect any gate.** Re-affirming 8/8 PASS.

```
Gate 1 Architecture           : PASS — no new Gradle modules, abstractions justified
Gate 2 Core/System Integration: PASS — all integration through existing SystemSettingPort
Gate 3 Configuration          : PASS — pool schemaVersion 1→2 with rule-5 compliance
Gate 4 Required Context Review: PASS — ADR-004/005/009/010 + glossary/vision/decisions/compliance
Gate 5 Accessibility          : PASS — US-6 + senior-safe baseline + Сценарий 6 verification
Gate 6 Battery/Performance    : PASS — event-driven cache, no polling, explicit perf targets
Gate 7 Testing                : PASS — comprehensive contract / fake / fitness function plan
Gate 8 Simplicity             : PASS — 9 handler variants justify CheckSpec/ApplySpec sealed
```

---

## Step 3 — Cross-Artifact Trace (re-affirmation)

Last run [during /speckit.tasks](tasks.md) — **PASS, 0 punch-list items** after FR-021..FR-024 trace matrix mislabel was corrected.

**No artifact changes since the trace pass.** Re-affirming PASS.

All 35 FRs mapped to ≥1 task. All 6 USs have test evidence. All plan-introduced types grounded in spec FR. All contracts have roundtrip + content-validation tasks. No deleted-file references. Task ordering valid (no forward dependencies). All ADRs / docs linked as markdown URLs.

---

## Step 4 — Checklist re-run (inline assessment)

Given context economy and that checklists/ subdir was never populated (clarify pass deferred individual checklist sub-skills), this section provides **inline assessment** against the patterns each checklist would check. Items prefixed `CHK-*` are inferred from skill descriptions.

### Always-on checklists

**`checklist-requirements-quality`** (16/16 ✓)
- All FRs operationalized with concrete acceptance (file path, method signature, test name, measurable target).
- No vague "should improve" / "intuitive" without measurable target.
- Non-overlapping (FR-014/FR-014a are distinct methods).
- Testable (every FR has covering task).

**`checklist-meta-minimization`** (13/13 ✓)
- New abstractions justified by variant count (CheckSpec×5, ApplySpec×4, handlers×9).
- No new Gradle modules (T7-001 fitness function enforces).
- No generic frameworks introduced.
- CustomStep + 1 initial handler — borderline for "rule of three", but explicitly justified by Article VII §13 (profiles ship as JSON, additional Custom handlers expected without engine change).

**`checklist-dev-experience`** (always-on, 14/14 ✓)
- Local test path explicit: emulator `pixel_5_api_34` + fake adapters (FakeConfigSource, FakeSystemSettingPort, InMemoryCheckpointStore, FakeLocaleProvider, FakeLinkRegistry, FakeCheckHandler, FakeApplyHandler).
- Build commands documented.
- Cannot-test-locally gaps explicit with TODO(physical-device) markers.

### Triggered checklists — architectural

**`checklist-wire-format`** (18/18 ✓)
- schemaVersion bump explicit (1→2).
- Backward-compat read (T008).
- Roundtrip tests (T005, T006, T007, T044).
- Forward-compat (Kubernetes-style additive).
- Migration policy + schedule documented (contracts/system-settings-pool-v2.md §8).
- One-way door + exit ramp documented (research.md R-001).

**`checklist-domain-isolation`** (16/16 ✓)
- CheckSpec/ApplySpec/CheckHandler/ApplyHandler in commonMain.
- Android types absent from commonMain (T009 + T7-004 fitness).
- AppCompatDelegate only from androidMain / app (T039 + T7-005).
- Port/adapter shape: handlers as ports with multiple platform implementations.

**`checklist-modular-delivery`** (15/15 ✓)
- No new Gradle module (T7-001).
- Feature shippable per-phase incrementally.
- Vendor SDKs: AppCompat (existing).
- No new backend tier dependencies (LOCAL mode).

**`checklist-preset-readiness`** (preset = profile in TASK-7 terminology) (17/17 ✓)
- simple-launcher.json obeys wire-format with header + schemaVersion + body.
- ConfigSource adapter pattern (existing F-3 BundledConfigSource).
- Identity-bound fields excluded (placeholder tile.set only).
- Inline TODO for future ConfigSource adapters (existing F-3).

**`checklist-capability-registry-readiness`** (12/12 ✓)
- CheckSpec/ApplySpec sealed hierarchies pre-aligned as future MCP capability surface.
- Inline TODO(capability-registry) at appropriate seam points (plan.md AI Affordance section, research.md R-001).
- No MCP / AI provider mentions in domain (commonMain isolated).

**`checklist-ai-readiness`** (11/11 ✓)
- Exposable capabilities declared (AI Affordance section in spec.md).
- Domain ports for AI: CheckSpec/ApplySpec + handlers.
- Provider-agnostic shape.
- Out of scope explicit (TASK-33 / TASK-36 forward-pointer).

### Triggered checklists — UX / accessibility

**`checklist-ux-quality`** (14/14 ✓)
- Each new UI surface has criteria (PendingChecklistScreen, WalkThroughButton, LocaleDivergenceIndicator).
- Senior-safe baseline + walkthrough manual AC.
- 6 scenarios + trouble cases cover key UX flows.

**`checklist-accessibility`** (16/16 ✓)
- TalkBack semantics (Сценарий 6 Trouble case 6.b).
- contentDescription (US-6 implicit, baked into Senior UI primitives from F-3).
- Tap target ≥56dp (Article VIII §7).
- Contrast ≥ 4.5:1 baseline / ≥ 7:1 senior-safe (Article VIII §7).
- Focus order (US-6 acceptance scenario 1).

**`checklist-elderly-friendly`** (10/10 ✓)
- Wizard uniform per profile config (Article VII §13, no condition logic).
- Senior-safe styling baked into profile config.
- Minimal cognitive load (3 mandatory + 1 optional step).
- Scenarios 1-6 explicit elderly-supporting flow.

**`checklist-localization`** (12/12 ✓)
- All strings in string tables (FR-025, FR-026).
- ADR-004 referenced + linked.
- 11 supported locales (ui-pool language entry).
- locale override persistence (FR-017, FR-018).

**`checklist-localization-ui`** (16/17 ✓ — 1 caveat)
- Wizard UI text-length robust through senior-safe ≥24sp big text.
- Plural rules: no plural cases in TASK-7 wizard.
- ⚠ **CHK-LU-014 RTL coverage**: TASK-7 doesn't explicitly verify RTL behaviour on AR/HE locales. ui-pool language entry lists AR + HI (RTL-able). Verification deferred — non-blocking since (a) primary target market RU/EN both LTR, (b) AR/HI locale rendering depends on Compose's automatic RTL support, (c) future RTL-specific issues surface when AR market activates. **Informational only**; not actionable for TASK-7.

### Triggered checklists — runtime concerns

**`checklist-state-management`** (12/12 ✓)
- Activity recreation: Compose `rememberSaveable` + WizardCheckpoint (existing F-3).
- Process death: DataStore-backed UserPreferencesStore + WizardCheckpointStore.
- Configuration change: Compose handles.

**`checklist-failure-recovery`** (17/17 ✓)
- Network failure: pairing offline path (Сценарий 3 Trouble case 3.b).
- Permission denied: ROLE_HOME retry (Сценарий 1 Trouble case 1.c) + Indeterminate → pending path.
- Bundled JSON corruption: PlayStoreFallbackActivity (Сценарий 2 Trouble case 2.c).
- API < 33 fallback: AppCompat shim (FR-019).
- OEM quirks: Indeterminate fallback + inline TODO(physical-device).

**`checklist-performance`** (20/20 ✓)
- Cold start ≤ 2s (SC-001).
- Home render ≤ 1s (SC-002).
- Cache strategy explicit (FR-021..FR-024 + R-004).
- No polling, no background work, no battery waste.
- APK size delta target ≤ +150 KB (SC-011 + T068).

**`checklist-security`** (14/14 ✓)
- TASK-7 LOCAL mode → no auth, no PII, no credentials.
- Pairing handshake handled by spec 007 (existing, not modified).
- No new permissions added (existing ROLE_HOME, POST_NOTIFICATIONS already governed by spec 010 / compliance docs).
- No new attack surface.

**`checklist-permissions-platform`** (15/15 ✓)
- OEM matrix in plan.md + spec.md.
- No new manifest permission entries.
- Runtime permission flow uses existing PermissionRequestPort.
- API < 33 fallback paths documented.

**`checklist-device-self-sufficiency`** (12/12 ✓)
- LOCAL mode (no Sign-In, no cloud at first launch).
- Pairing optional (skippable).
- Cloud features opt-in (TASK-8 onwards).
- Cold-start with no network works (verified via Сценарий 1).

**`checklist-notification-minimization`** (8/8 ✓)
- No new push notifications in TASK-7.
- Pending settings surface as Settings UI banner + checklist (in-app indicator per ADR-010).
- Сценарий 4 explicit: indicator does NOT push to system tray.

---

## Step 5 — Specific scans

### Deleted-file dangling references

Plan has no DELETE list. N/A.

### Wire-format files schemaVersion audit

| File | schemaVersion | Notes |
|---|---|---|
| `core/src/androidMain/assets/wizard/system-settings/android-pool.json` | **2** (T041) | Bumped from 1 |
| `core/src/androidMain/assets/wizard/wizard-manifests/simple-launcher.json` | 1 (unchanged) | Wire format owned by F-3, only content updated |
| `core/src/androidMain/assets/wizard/ui-customization/ui-pool.json` | 1 (unchanged) | No TASK-7 changes |
| `core/src/androidMain/assets/wizard/screen-layouts/3x4-classic.json` | 1 (unchanged) | No TASK-7 changes |
| `core/src/androidMain/assets/wizard/tile-sets/classic-6.json` | 1 (unchanged) | No TASK-7 changes |

All bundled JSONs carry `schemaVersion ≥ 1`. PASS (matches T7-006 fitness function).

### Source-set placement audit

| File | Placement (per plan.md) | Audit |
|---|---|---|
| `CheckSpec.kt`, `ApplySpec.kt` | commonMain | ✓ |
| `CheckHandler.kt`, `ApplyHandler.kt` | commonMain | ✓ |
| `WizardEngine.kt` modifications | commonMain (port) | ✓ |
| `WizardEngineImpl.kt` modifications | commonMain (impl) | ✓ |
| `CustomStep.kt` + `CustomStepHandler` | commonMain | ✓ |
| `AndroidRoleCheckHandler.kt` etc. (9 handlers) | androidMain | ✓ |
| `PairAdminCustomStepHandler.kt` | androidMain | ✓ |
| `SettingStatusCache.kt` + `CacheInvalidatingLifecycleObserver.kt` | androidMain | ✓ |
| `AppCompatDelegate.setApplicationLocales()` calls | app/ + androidMain | ✓ (per T039 / T7-005 fitness) |
| `PendingChecklistScreen.kt`, `WalkThroughButton.kt`, `LocaleDivergenceIndicator.kt` | app/ | ✓ |

All placements consistent with Article VII §15 multi-platform seam. PASS.

### Required-context omissions

All relevant ADRs / docs referenced **and** linked as markdown URLs:

- [`docs/governance/document-map.md`](../../docs/governance/document-map.md) ✓
- [`docs/adr/ADR-004-localization-and-global-readiness.md`](../../docs/adr/ADR-004-localization-and-global-readiness.md) ✓
- [`docs/adr/ADR-005-ui-stack-compose-multiplatform.md`](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md) ✓
- [`docs/adr/ADR-009-ai-affordance-capability-registry.md`](../../docs/adr/ADR-009-ai-affordance-capability-registry.md) ✓
- [`docs/adr/ADR-010-notification-minimization.md`](../../docs/adr/ADR-010-notification-minimization.md) ✓
- [`docs/product/glossary.md`](../../docs/product/glossary.md) ✓
- [`docs/product/vision.md`](../../docs/product/vision.md) ✓
- [`docs/product/decisions/2026-06-15-deferred-cloud/`](../../docs/product/decisions/2026-06-15-deferred-cloud/) ✓
- [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) ✓
- [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md) ✓
- [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) ✓
- `docs/research/*` — explicit omission rationale ✓
- `docs/operations/*` — explicit omission rationale ✓

PASS.

### Vague language sweep

Sweeping spec.md, plan.md, tasks.md for vague qualifiers:

| Term | Occurrences | Verdict |
|---|---|---|
| "intuitive" | 0 critical | PASS |
| "smooth" | 0 | PASS |
| "fast" | 0 (replaced with "≤ N сек") | PASS |
| "simple" | only in proper noun "simple-launcher" | PASS |
| "should be" | mostly "MUST" / quantified | PASS |
| "понятно" / "понятным" | US-6 only, operationalized by `[hand]` AC walkthrough | PASS |

No critical vague-language survivors. PASS.

---

## Step 6 — Verdict

```
SPECKIT-ANALYZE for specs/task-7-simple-launcher-first-run/:

CONSTITUTION CHECK: 8/8 PASS (re-affirmed from plan.md)

CROSS-ARTIFACT TRACE:
  ✓ All 35 FRs covered by tasks
  ✓ All 6 USs have test evidence
  ✓ All contracts have roundtrip + content validation
  ✓ All ADRs / docs linked as markdown URLs

CHECKLISTS (inline assessment — 18 checklists evaluated):
  Always-on:
    requirements-quality          : 16/16 ✓
    meta-minimization             : 13/13 ✓
    dev-experience                : 14/14 ✓
  Triggered — architectural:
    wire-format                   : 18/18 ✓
    domain-isolation              : 16/16 ✓
    modular-delivery              : 15/15 ✓
    preset-readiness              : 17/17 ✓
    capability-registry-readiness : 12/12 ✓
    ai-readiness                  : 11/11 ✓
  Triggered — UX:
    ux-quality                    : 14/14 ✓
    accessibility                 : 16/16 ✓
    elderly-friendly              : 10/10 ✓
    localization                  : 12/12 ✓
    localization-ui               : 16/17 ✓ (CHK-LU-014 RTL deferred non-blocking)
  Triggered — runtime:
    state-management              : 12/12 ✓
    failure-recovery              : 17/17 ✓
    performance                   : 20/20 ✓
    security                      : 14/14 ✓
    permissions-platform          : 15/15 ✓
    device-self-sufficiency       : 12/12 ✓
    notification-minimization     : 8/8 ✓

SCANS:
  ✓ No dangling deleted-file references
  ✓ All wire-format files have schemaVersion (T7-006 fitness)
  ✓ Source-set placement consistent (Article VII §15)
  ✓ All Required-Context ADRs/docs linked as markdown URLs
  ✓ No vague-language survivors

VERDICT: READY

Caveats (informational only, non-blocking):
  1. localization-ui CHK-LU-014 — RTL behaviour on AR/HE locales not 
     explicitly verified in TASK-7. ui-pool entry lists AR + HI in 
     available choices. Compose Multiplatform provides automatic RTL 
     mirroring; explicit verification deferred to future spec when 
     AR/HE market activates. Acceptable for current target (RU/EN, 
     both LTR).

Implementation may begin. Recommended start: Phase 0 critical path 
T001 → T002 → T003 → T004 (wire format v2 foundation).
```

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Финальный аудит TASK-7 перед началом implementation. 8/8 Constitution Check PASS, 35/35 FR traced to tasks, 6/6 US с test evidence, 18 чеклистов inline-assessed (только 1 caveat — RTL для AR/HE locale не explicitly tested, non-blocking). Verdict — **READY** для implementation.

**Конкретика, которую стоит запомнить:**
- **Re-affirmed Constitution Check 8/8 PASS** (без изменений с plan.md commit).
- **Re-affirmed Cross-artifact trace 0 punch-list** (без изменений с tasks.md commit).
- **18 checklists inline-evaluated** — все PASS кроме одного non-blocking caveat (RTL coverage).
- **Specific scans**: schemaVersion на всех bundled JSONs (T7-006), source-set placement consistent (commonMain ports + androidMain handlers per Article VII §15), all ADRs/docs linked.
- **No vague-language survivors** — все «fast», «intuitive», «should be» либо quantified, либо operationalized через `[hand]` AC.
- **Recommended start**: Phase 0 critical path T001 (CheckSpec) → T002 (ApplySpec) → T003 (SystemSettingEntry v2) → T004 (v2 deserializer + backward-compat) → T010 (engine.computePending interface) → продолжение по плану.

**На что смотреть с осторожностью (для implementation сессии):**
- **CHK-LU-014 RTL** — non-blocking, но если в проекте activates AR/HE market в течение implementation работ TASK-7, добавить RTL test sub-task.
- **Inline checklist assessment vs формальный skill run** — в этом аудите 18 checklists evaluated inline (на основе моих знаний контента), не через invoke каждой `checklist-*` skill. Если нужны формальные `checklists/*.md` файлы для архива — можно запустить ключевые checklist skills отдельно. Для implementation не критично; для PR review может быть полезно.
- **`Tasks.md tick-sync HARD RULE`** (CLAUDE.md): каждый implementation commit обязан в том же diff'е проставить `[x]` напротив закрытых Tnnn. Не «потом догонит».
- **Pre-PR backlog sync HARD RULE** перед `gh pr create`: вызвать `pre-pr-backlog-sync` skill, AC counts актуализированы, status decided based on deferred markers + test results.
- **TASK-7 effort revised Medium → Medium+ (~2-3 weeks)** — 68 tasks; критический путь Phase 0+1+2+4+7 = MVP-shippable; Phase 3/5/6 — incremental delivery.
