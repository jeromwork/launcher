# Checklist: requirements-quality — TASK-120 spec.md

**Target**: specs/task-120-preset-composition-foundation/spec.md
**Date**: 2026-07-10
**Stage**: post-clarify, pre-scenarios/plan

## Results

### Content Quality
- [ ] **CHK001** Spec is heavily saturated with implementation details: Kotlin `sealed class`, `@Serializable`/`@SerialName`, Hilt `@IntoMap`, DataStore, kotlinx.serialization `classDiscriminator`, `assets/pool.json`, WorkManager/AlarmManager, `PackageManager.FEATURE_LEANBACK`, `Build.MANUFACTURER`, module paths `core/preset/`, `androidMain/provider/`, `./gradlew` commands, class names (`ReconcileEngine`, `ProviderRegistry`, `ProfileFactory`, etc.). These belong in plan.md. Fix: move technology-specific naming and framework choices to plan.md; keep spec.md at behavior/contract level (e.g., "system exposes a Provider port" rather than "Kotlin sealed class with @Serializable").
- [ ] **CHK002** Focus mixes user value with technical "how". User Stories 1, 3, 4 do carry user value, but US2 ("Developer extensibility") is framed around code structure (files touched, imports, when-guards) rather than user/business need. Fix: recast US2 as a business outcome (velocity / defect rate / onboarding) with the file-count metric moved to Success Criteria or plan.md.
- [ ] **CHK003** A non-technical stakeholder cannot validate large parts of this spec. Terms like `paramsOverride`, `PoolSource`, `ProviderRegistry`, `HandlerKey`, `RunMode.BootCheck`, `sealed subtype`, `NoOp fallback`, `roundtrip test`, `Hilt @IntoMap`, `kotlinx.serialization` are unavoidable for the owner-facing sections (User Scenarios, Requirements). The MENTOR-DETAIL blocks help but cannot fully substitute. Fix: rewrite FRs and US bodies in domain language; leave the technical vocabulary to Key Entities / plan.md.
- [x] **CHK004** All mandatory sections present: User Scenarios & Testing (6 US + Edge Cases), Requirements (FR-001..FR-025 + Key Entities), Success Criteria (SC-001..SC-013), plus Assumptions, Local Test Path, AI Affordance, OEM Matrix, Sequences, Clarifications, Downstream contract. In/Out scope is expressed through FR-020..FR-022 seams and the Downstream contract rather than a discrete "Out of Scope" heading, but coverage is present.

### Requirement Completeness
- [x] **CHK005** No `[NEEDS CLARIFICATION]` markers remain — Clarifications section documents 8 resolved questions from 2026-07-10 pass.
- [x] **CHK006** Every FR is testable — each maps to an observable assertion (Provider contract, fallback resolution, wire-format roundtrip, fitness function). SCs enumerate concrete verifications.
- [ ] **CHK007** Ambiguity in a few requirements: FR-006 says "Никакого постоянного background loop внутри apply" — "постоянного" is not operationalised (max duration? bounded by what?). Edge case "preWizardSnapshot старше 7 суток" uses a specific 7-day number (good), but FR-024 also references "soft-limit" without defining threshold behavior beyond the edge case. FR-011's "single-shot install" clause is dense but comprehensible. Fix: replace "постоянный loop" with a concrete bound (e.g., "apply() MUST return within N seconds; long-running side effects delegated to WorkManager").
- [ ] **CHK008** Success criteria mix measurable and non-measurable. Measurable: SC-001 ("4 files"), SC-004 ("7 fitness functions green"), SC-011 ("N=100 random combinations"), SC-012 ("≤3 declarations per subtype"). Non-quantified: SC-002 ("without errors"), SC-003 ("zero special-logic in engine" — how measured?), SC-005 ("bit-identical" is measurable), SC-006/SC-007 (yes/no behavioural). Missing performance/time bounds entirely (no cold-start budget for Wizard, no reconcile latency target, no memory ceiling for Profile). Fix: add at least one time-bound SC (e.g., "ReconcileEngine.run(BootCheck) completes within X ms on baseline device").
- [ ] **CHK009** Success criteria are NOT technology-agnostic. SC-001 names specific files (`Component.kt`, `HandlerModule.kt`, `assets/pool.json`, `ProviderRegistry.kt`), SC-003 names `ReconcileEngine` and `RunMode` enum, SC-004 names `./gradlew :core:test --tests *FitnessTest`, SC-010 embeds Kotlin reflection code `Component::class.sealedSubclasses.all { ... }`, SC-011 names `kotest-property Arb.preset()`. All of these are plan-level. Fix: rephrase in terms of observable outcomes ("adding a new configurable feature touches at most 4 distinct files"; "reconcile behavior is uniform across modes").
- [x] **CHK010** Acceptance scenarios use Given/When/Then throughout US1-US6. All six user stories carry 3 numbered scenarios each.
- [x] **CHK011** Edge Cases section is thorough — covers empty/missing (poolRef missing, Provider missing, package not installed), error state (Failed outcome, malformed override, schemaVersion too high), retry (Wizard resume, boot-check reapply), double-action (same-version preset re-arrival), plus additional cases (Wizard cancel, snapshot expiry, activeComponents referencing deprecated Component).
- [ ] **CHK012** Scope boundary is partially bounded. In-scope is expressed via FRs; explicit "Out of Scope" is scattered: FR-020..FR-022 note "deferred runtime" for ConditionEvaluator, SosDispatcher, Provider.rollback; FR-014 defers MessengerTile to task-121; US5 caveats "schema-only in MVP". No consolidated "Out of Scope" heading — an owner scanning for boundaries has to piece it together. Fix: add a short "## Out of Scope" section listing: full JsonLogic runtime, SosDispatcher, Provider.rollback, MessengerTile, multi-preset, admin-push transport, iOS provider implementations.
- [x] **CHK013** Dependencies and assumptions explicit in Assumptions section (Hilt, kotlinx.serialization, DataStore, no ECS, Vendor detection strategy, one-active-preset) and Downstream contract enumerates dependent tasks. Backlog task linked in header.

### Feature Readiness
- [x] **CHK014** Each FR maps to at least one US or SC. FR-001..FR-010 covered by US1-US3 acceptance scenarios; FR-011 → US5; FR-014 → US1 + SC-007; FR-019 → SC-004; FR-020 → US6; FR-024 → US1 scenario #4 + SC-013; FR-025 → SC-012.
- [x] **CHK015** User scenarios cover primary flows AND error paths. US1 scenario #4 covers cancel/undo; US4 scenario #3 covers Provider Failed; US5 scenario #3 covers Removed (rollback deferred). Edge Cases enumerate additional error branches (missing package, malformed override, schema mismatch).
- [ ] **CHK016** Most SCs trace to an FR producing the measurement, but SC-003 ("zero special-logic in engine") lacks a corresponding measurable FR — it is an architectural invariant with no defined pass/fail criterion in the spec. SC-008 references rule 5 backward-compat but no FR explicitly requires migration writer artifact. Fix: either add an FR making these invariants machine-checkable (e.g., "engine MUST NOT contain conditional branches keyed on RunMode enum name") or reframe SC-003 as an architectural constraint in plan.md.

---

## Summary
- **Passed**: 8/16
- **Failed**: [CHK001 (implementation details in spec), CHK002 (US2 framed technically), CHK003 (non-technical stakeholder cannot validate), CHK007 (unquantified "postoянного loop"), CHK008 (missing time/perf SCs), CHK009 (SCs name concrete files/tools), CHK012 (no consolidated Out-of-Scope section), CHK016 (SC-003 has no FR-backing measurement)]
- **N/A**: []
- **Blockers before /speckit.plan**: None hard-blocking — the spec is complete and testable. But CHK001/CHK003/CHK009 (technology leakage) are significant: the spec pre-commits to Kotlin/Hilt/Gradle at the specification layer, so plan.md will inherit no design freedom. Owner should decide whether to accept this coupling (foundation is Kotlin-KMP anyway per Assumptions) or refactor spec.md to keep implementation names out.
- **Follow-ups**:
  - Add consolidated "## Out of Scope" section (CHK012).
  - Quantify FR-006 apply() duration bound (CHK007).
  - Add at least one time-bounded performance SC (CHK008).
  - Consider extracting implementation vocabulary (class names, file paths, Gradle commands) from spec.md into plan.md (CHK001/CHK003/CHK009). If accepted as-is, document the deliberate coupling in Assumptions.
  - Recast US2 acceptance in user/business outcome terms; keep the file-count metric under SC (CHK002).
  - Provide an FR that makes SC-003's "single engine, no special logic" machine-checkable, or move it to plan.md as an architectural invariant (CHK016).
