# Checklist: meta-minimization — TASK-120 spec.md

**Target**: specs/task-120-preset-composition-foundation/spec.md
**Date**: 2026-07-10
**Stage**: post-clarify, pre-scenarios/plan

## Results

### New abstractions

- [x] **CHK001** Every new port has a concrete in-spec consumer: `PoolSource` (ProfileFactory + BundledPoolSource), `PresetSource` (WizardVM SEQ-1), `ProfileStore` (SEQ-1/SEQ-2 save), `Provider` (ReconcileEngine dispatch), `ProviderRegistry` (engine.resolve), `InteractionSink` (Wizard Interactive step in SEQ-1), `PackageManagerFacade` (AppTile FR-014 + SC-007), `ConditionEvaluator` (US6 P3 hardcoded MVP consumer). All exercised by SC-001..SC-013 in this spec.
- [x] **CHK002** Single-impl interfaces are justified by port shape: `PoolSource`/`PresetSource`/`ProfileStore` need fakes for JVM unit tests (Local Test Path lists FakePoolSource, FakePresetSource, FakeProfileStore); `Provider` has per-Component impls + NoOp fallback (platform asymmetry Android/iOS placeholder per Assumptions); `PackageManagerFacade` wraps Android SDK (rule 1 domain isolation). `ConditionEvaluator` justified as schema seam BUT flagged in CHK010.
- [x] **CHK003** `ReconcileEngine` is a genuine orchestrator with data transformation (resolve → check → sink dispatch → apply → status mark → persist), not pass-through. `ProfileFactory` transforms preset+pool → Profile (expand poolRef, apply override, init statuses). `PresetDiff` computes Added/Removed/ParamsChanged classification. `ProviderRegistry` performs fallback resolution ((type,platform,vendor)→...→NoOp) — non-trivial dispatch.
- [x] **CHK004** No custom DSL. `pool.json`/`preset.json` use kotlinx.serialization polymorphic sealed (Assumptions confirms — not a custom parser). "Registry" here is Hilt `@IntoMap` binding — standard DI, not plugin system. No plugin loader.

### New modules / packages

- [x] **CHK005** `core/preset/` module is justified by Article V §3: ownership boundary (pure Kotlin KMP commonMain, zero Android — enforces rule 1 domain isolation), material testability (JVM-only unit tests without Android runtime, per Local Test Path), stable API (wire format schemaVersion=2 crossed device/versions). Android adapters live separately in `app/androidMain/provider/*`.
- [x] **CHK006** Implicit answer: package is not enough because Article V §3 boundary is enforced by KMP source set (commonMain vs androidMain) which is a module-level concept, not package-level. Fitness function #1 (import guard) is easier to enforce at module boundary.
- [x] **CHK007** No utils/common/helpers module. Adapter code is clearly named (`androidMain/provider/`, `assets/pool.json`).

### New configuration

- [x] **CHK008** Every wire-format field has a current FR consuming it: `wizardFlow`/`settingsMap`/`activeComponents` (FR-003, consumed by ReconcileEngine RunMode branches FR-010), `paramsOverride` (FR-004 + SC-005 roundtrip), `WizardBehavior` (FR-009, US1 acceptance), `critical` (FR-006/FR-010 BootCheck), `visibleIf` (FR-020, US6 P3 seam — has minimal MVP consumer `device.hasGms` hardcoded evaluator), `preWizardSnapshot` (FR-024, US1 acceptance #4, SC-013). No orphan fields.
- [x] **CHK009** Defaults: `haltOnFailure: bool = false` explicit default in Q7; `visibleIf: JsonLogicExpression?` nullable; `WizardBehavior` explicit enum. Backward-compat: FR-016 mandates schemaVersion + additive-only changes; FR-023 pool additive-only; FR-012 rejects schemaVersion above supported (fail loud). Migration policy stated (rule 5 referenced).

### CLAUDE.md rule 4 self-test

- [ ] **CHK010** Test 1 (inline test) partially documented. Most seams justified — engine/registry/factory pass inlining test trivially (US2 explicitly requires the seam for "add feature without touching core"). But `ConditionEvaluator` (FR-020) is a **schema seam with only a hardcoded `device.hasGms` MVP consumer** — if inlined into WizardEngine, only "future JsonLogic runtime optionality" is lost. FR-020 acknowledges "seam, deferred runtime". Session 2.5 explicitly labeled it a reserved seam. Owner-directive says leave it, but per rule 4 Test 1 this is exactly "single-implementation interface with no port-shaped seam" that CHK002/refuse-pattern-9 would flag if the wire-format field weren't already committed. Mitigation: `visibleIf` field itself IS load-bearing wire-format (roundtrip test SC #4 in US6 acceptance) — that's justified per rule 5 additive design. The **interface** `ConditionEvaluator` is the questionable piece. Recommend: reduce to a top-level `fun evaluateVisibleIf(expr, device): Boolean` free function in MVP; introduce port only when second impl arrives. Fix before /speckit.plan: either add second consumer or downgrade to free function.
- [x] **CHK011** Test 2 (deprecation swap cost): Hilt swap (Assumptions: alternative Koin rejected) — >1 day (touches every module-binding), seam justified. kotlinx.serialization swap — >1 day (wire format bound to polymorphic discriminator), keep. DataStore swap — swap ProfileStore adapter only, ~half day; ProfileStore port is still justified because tests need FakeProfileStore (port-shape need per CHK002). `PackageManagerFacade` swap — trivial adapter, but port justified by rule 1 domain isolation. All seams survive Test 2 or CHK002. Explicit rejected seams (FR-021 SosDispatcher, FR-022 Provider.rollback) properly deferred with fitness-function guard — good hygiene.

### Removal validation

- [N/A] **CHK012** Spec does not remove existing abstractions in this repo (foundation build-out). Legacy `FirstLaunchActivity` hardcoded Sign-In removal is explicitly deferred to draft-1 downstream task per Downstream contract section — not this spec's removal.
- [N/A] **CHK013** No "deprecated, will remove later" markers in spec. Deferred items (`Provider.rollback` FR-022, full JsonLogic runtime FR-020, `SosDispatcher` FR-021) are properly labeled seam/deferred with fitness-function or additive-extension paths, not deprecations.

---

## Summary
- **Passed**: 10/13
- **Failed**: [CHK010]
- **N/A**: [CHK012, CHK013]
- **Blockers before /speckit.plan**:
  - CHK010 — `ConditionEvaluator` port has only one hardcoded MVP consumer and its "second impl" (full JsonLogic runtime) is deferred with unclear trigger. Either (a) collapse to top-level function in MVP, extract to port when second real caller lands, or (b) document explicit trigger condition in FR-020 stating when the port becomes load-bearing. Wire-format field `visibleIf` itself is fine per rule 5.
- **Follow-ups**:
  - Anti-explosion FR-025 + SC-012 (fitness #8, ≤3 declarations per Component subtype) is well-shaped — owner Q1-driven, not premature. Keeps the seam disciplined.
  - Session 2.5 rejection of `SosDispatcher` (FR-021) and `Provider.rollback` (FR-022) is correctly captured as fitness-function-guarded absence — model example of "seam reserved via guard, not implemented".
  - Consider adding an explicit "trigger for elevating ConditionEvaluator from free function to port" note in FR-020 if CHK010 is resolved by option (a).
