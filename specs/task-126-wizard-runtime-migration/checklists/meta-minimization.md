# Checklist: meta-minimization
**Spec**: `specs/task-126-wizard-runtime-migration/spec.md`
**Run date**: 2026-07-11
**Result**: 9/11 ✓ (2 N/A skipped), 1 FAIL, 2 WARN

---

## New abstractions

- [x] CHK001 Every new interface/port has at least one concrete consumer **in this spec** (not "in spec 008 we'll need it").
  - **PASS** — `InteractionSink` → `WizardViewModel` + `WizardScreen` (FR-008); `HintPoolLoader` → hint overlay rendering (FR-007); `PresetValidator` → `PresetBootstrap` at deserialization (FR-006); `SystemPermissionProvider` facade → migrated Providers (FR-018); `WizardStore` → `WizardViewModel` resume logic (FR-008); `wizardPresentation` field → `WizardScreen` at start (FR-003). All have current-spec consumers.

- [x] CHK002 If a new interface has only one implementation: justified by port-shape need (DI, fakes, platform asymmetry) — not by "extensibility".
  - **PASS** — `InteractionSink` (one impl: `WizardViewModel`): justified by testability — `ReconcileEngine` unit tests need a fake sink without UI coupling. `HintPoolLoader` (one impl: bundled assets): justified by rule 9 `ConfigSource` adapter pattern — future `NetworkSource` or `FileImportSource` is additive per the shareability rule, not speculative extensibility. `SystemPermissionProvider` (one impl: Android adapter): mandated by CLAUDE.md rule 2 (ACL) — platform types must not reach domain.

- [x] CHK003 Mediator/orchestrator/manager class is justified by data transformation, not by pass-through.
  - **PASS** — `PresetBootstrap`: loads JSON → resolves polymorphic `ComponentType` → runs `PresetValidator` → returns typed domain objects; non-trivial transformation. `WizardViewModel`: translates `ReconcileState` to `StateFlow<WizardUiState>`, mediates coroutine-suspended `ReconcileEngine` with UI `InteractionSink.answer()` calls; non-trivial coordination, not pass-through.

- [x] CHK004 No custom DSL, registry, or plugin system unless simpler composition has been tried and documented as failing.
  - **PASS** — `hint-pool.json` + `hintFlow` is data, not a runtime registry. `PresetValidator` with `requires` is a declarative ordering constraint, not a plugin system. No DSL introduced.

## New modules / packages

- [N/A] CHK005 New gradle module satisfies at least one of Article V §3 criteria.
  - **N/A** — No new Gradle module introduced. Spec consolidates: `Spec015Module` + `Task65Module` merged into renamed `PresetModule` (existing `task120Module`).

- [N/A] CHK006 If new module is added: plan answers "Why is a package not enough?" explicitly.
  - **N/A** — No new module. See CHK005.

- [x] CHK007 No "utils" / "common" / "helpers" dumping ground module created.
  - **PASS** — No such module introduced. Consolidation reduces module count.

## New configuration

- [x] CHK008 New config field has a current FR consuming it (not "future feature might use it").
  - **PASS** — `hintFlow: List<HintFlowEntry>?` → FR-007 (HintPoolLoader + UI rendering, in-spec). `wizardPresentation: { darkMode, typographyScale }` → FR-003 (WizardScreen applies at wizard start). `requires: List<ComponentId>?` on pool descriptor → FR-006 (PresetValidator ordering check). `required: Boolean` on pool descriptor → FR-006 (wizard completion gate). `lastCompletedStepIndex` in WizardStore → FR-008 (ReconcileEngine resume). All fields consumed by current-spec FR.

- [x] CHK009 Config field defaults documented; backward-compat policy defined; migration path documented if non-trivial structure.
  - **WARN** — FR-014 explicitly states schemaVersion bumps to 2 on both `Preset` and `Pool`, with backward-compat policy: "v1 readers ignore new fields; v2 readers default missing fields." Defaults specified: `requires → null`, `required → false` (FR-006), `hintFlow → null` (FR-007). **Gap**: `wizardPresentation` field (FR-003) has no stated default — if a v1 Preset loaded by v2 reader lacks this field, what theme/typography does the wizard use? Recommend: add `wizardPresentation: null` → fallback to system default, documented in FR-003.

## CLAUDE.md rule 4 self-test

- [x] CHK010 **Test 1** applied: if abstraction were inlined, what would be lost? Answer documented for each new abstraction. If answer is only "future optionality" — abstraction is removed.
  - **WARN** — `design.md` D1-D9 blocks provide strong rationale for each decision (e.g., D5: "TutorialHint has no check()/apply() — forcing it into Component model would pollute ReconcileEngine with a special case"; D6: "Provider-inline permissions prevent orphan Components"). However, CLAUDE.md rule 4 Test 1 framing ("if removed and inlined, what would be lost?") is not explicitly stated per abstraction in spec or design. Rationale is present in spirit; the explicit Test 1 answer is implicit. Low-risk gap — intent is clear — but worth making explicit for `InteractionSink` and `HintPoolLoader` in plan.md.

- [x] CHK011 **Test 2** applied: if dependency on the other side doubled in price / was deprecated / violated privacy, how long to swap?
  - **PASS** — All new seams wrap single-class dependencies: `StatusBarPolicyProvider` wraps `WindowInsetsControllerCompat` (swap = one Provider class, < 1 day); `WizardStore` / `ProfileStore` are ports (swap DataStore adapter = < 1 day); `HintPoolLoader` behind interface (swap bundled → network = new adapter, < 1 day). Koin is pre-existing, not introduced by this spec. All new seam-swap costs ≤ 1 day.

## Removal validation

- [ ] CHK012 If spec removes existing abstractions/modules: dangling references in `docs/**`, `specs/**` audited.
  - **FAIL** — This spec deletes: `com.launcher.api.wizard.*` (26 files, 411 import sites), `com.launcher.api.preset.*`, `com.launcher.api.profile.*`, `com.launcher.api.switchstrategy.*`, `adapters/wizard/`, `Spec015Module`, `Task65Module`, `WizardCheckpointStore`, legacy `assets/wizard/`, AccessibilityService manifest entry. Fitness function FF-011 (FR-015) catches production source import regressions. **However**: spec does not audit `docs/**` (e.g., `docs/dev/server-roadmap.md`, `docs/architecture/*.md`) or other `specs/**` files for prose references to deleted types. Other specs (e.g., spec/015, spec/065) may contain `WizardEngine`, `ConfigKind`, `CheckHandler`/`ApplyHandler` references in description, assumptions, or plan sections. Recommend: add Phase 6 checklist item — `git grep -r "WizardEngine\|ConfigKind\|CheckHandler\|ApplyHandler\|Spec015Module\|Task65Module" docs/ specs/` → must return zero or annotate-as-historical.

- [x] CHK013 If spec marks code "deprecated, will remove later" — there is a concrete removal task in `tasks.md` of this or next spec, not "eventually".
  - **PASS** — No deprecation markers used. All deletions are outright and phased (Phase 6), with concrete checklist items in FR-016/FR-017. No "eventually" deferrals.

---

## Summary

| Result | Count | Items |
|--------|-------|-------|
| PASS   | 9     | CHK001, CHK002, CHK003, CHK004, CHK007, CHK008, CHK011, CHK012 (partial), CHK013 |
| WARN   | 2     | CHK009 (`wizardPresentation` default unspecified), CHK010 (rule-4 Test 1 implicit not explicit) |
| FAIL   | 1     | CHK012 (dangling refs in `docs/`+`specs/` not audited) |
| N/A    | 2     | CHK005, CHK006 (no new module) |

### Key issues (ordered by severity)

1. **CHK012 FAIL** — `docs/` and `specs/` not audited for references to deleted types/modules. At minimum add to Phase 6 checklist: `git grep -r "WizardEngine\|ConfigKind\|CheckHandler\|ApplyHandler\|Spec015Module\|Task65Module" docs/ specs/` → zero or annotated. FF-011 only guards production source, not documentation.

2. **CHK009 WARN** — `wizardPresentation` field default missing. If Preset v1 (no `wizardPresentation`) loaded by v2 reader, behavior undefined. Add to FR-003: "If `wizardPresentation` absent, wizard uses system dark mode + default typography scale."

3. **CHK010 WARN** — Rule 4 Test 1 ("if removed and inlined, what lost?") not explicitly answered in spec for `InteractionSink` and `HintPoolLoader`. Design.md rationale covers the intent. Recommend explicit Test 1/Test 2 answers in `plan.md` Architecture Decisions section to satisfy CLAUDE.md rule 4 audit trail.
