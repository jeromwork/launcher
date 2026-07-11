# Checklist: requirements-quality
**Spec**: `specs/task-126-wizard-runtime-migration/spec.md`
**Run date**: 2026-07-11
**Result**: 9/16 ✓ — 7 FAIL

---

## Content Quality

- [x] CHK001 No implementation details (programming languages, frameworks, vendor APIs) appear in `spec.md`. *(architecture belongs in `plan.md`)*
  - **FAIL** — FR section contains class names (`PresetBootstrap`, `ReconcileEngine`, `WizardViewModel`, `InteractionSink`, `ProfileStore`, `InteractionSink`), framework names (Koin, DataStore, Android SplashScreen API, Robolectric, Composable), package paths (`com.launcher.api.wizard`). These belong in `plan.md`.
- [ ] CHK002 Focus is on **user value and business need**, not on the technical "how".
  - **FAIL** — FR-001 through FR-018 are engineering tasks (class wiring, DI module naming, package deletion), not user needs. User Stories section is fine; the FR section reads like a tech design doc.
- [ ] CHK003 Written so a non-technical stakeholder can read and validate.
  - **FAIL** — FR section requires developer knowledge to parse (Android intents, Koin modules, Kotlin class names). Non-technical stakeholder can only validate via User Stories and Success Criteria.
- [ ] CHK004 All mandatory sections present: User Stories, Scope (In/Out), Functional Requirements, Success Criteria.
  - **FAIL** — No explicit `## Scope` section with In Scope / Out of Scope lists. The `Assumptions` section partially substitutes but is not a formal scope boundary.

## Requirement Completeness

- [x] CHK005 No `[NEEDS CLARIFICATION]` markers remain.
  - **PASS** — CL-1..CL-4 all resolved; Open Questions section confirms all 6 architecture questions from Discussion resolved.
- [x] CHK006 Every requirement is **testable** — there is at least one observable assertion that can verify it.
  - **PASS** — Each FR links to a User Story or Independent Test. SC-5..SC-9 are machine-verifiable.
- [x] CHK007 Every requirement is **unambiguous** — no "fast", "simple", "intuitive" without operationalisation.
  - **PASS** — NFR-001 gives ≤ 30 ms; NFR-004 operationalises "visually identical" against a reference screenshot; no loose adjectives found.
- [x] CHK008 Success criteria are **measurable** (numbers, percentages, time bounds).
  - **PASS** — SC-1..SC-4 are device-observable behaviours; SC-5..SC-9 are CI/grep commands with deterministic pass/fail.
- [ ] CHK009 Success criteria are **technology-agnostic** (no class names, protocols, framework features).
  - **FAIL** — SC-5 cites `git grep "import com.launcher.api.wizard"`; SC-6 cites `git log`; SC-7..SC-9 are Gradle invocations; SC-6 cites file paths with class names. These are implementation-level gates, not user-visible outcomes.
- [x] CHK010 All acceptance scenarios for each User Story are explicit (Given/When/Then or equivalent).
  - **PASS** — US-1 (4 scenarios), US-2 (2), US-3 (2), US-4 (2), US-5 (2), US-6 (3), US-7 (2) — all Given/When/Then.
- [x] CHK011 Edge cases are identified — at minimum: empty state, error state, retry, double-action.
  - **PASS** — Edge Cases section covers: cold start / SplashScreen, MIUI quirk, missed call site (411 imports), Koin init failure, golden JSON incompatibility, `requires` ordering violation, missing hint-pool.json.
- [ ] CHK012 Scope is clearly bounded — In Scope and Out of Scope are exhaustive for the area.
  - **FAIL** — No formal `## Scope` section. `Assumptions` lists some out-of-scope items ("One active Preset per device — future TASK-127", "iOS providers deferred") but is not exhaustive. Missing: explicit list of what legacy artefacts are in scope for deletion, explicit statement that settings UI visual changes are out of scope, etc.
- [x] CHK013 Dependencies and assumptions are explicit (other specs, external services, device capabilities).
  - **PASS** — `Assumptions` section lists 7 explicit items; `Input` header line names TASK-120 as dependency; Clarifications document resolved decisions.

## Feature Readiness

- [x] CHK014 All functional requirements have clear acceptance criteria (mapped to a US or independent).
  - **PASS** — FR-001..FR-018 individually traceable: FR-001→US1, FR-002→US2, FR-003/FR-004/FR-005→US6, FR-006→US4/SEQ-3, FR-007→Edge Cases, FR-008→US3, FR-009→US5, FR-010..FR-012→SC-5/SC-6, FR-013→SC-8, FR-014→SC-7, FR-015..FR-016→US7/SC-9, FR-017→SC-6, FR-018→Domain isolation.
- [ ] CHK015 User scenarios cover **primary flows** — not just happy path; at minimum one error path per US.
  - **FAIL** — US-1: no error path for PresetBootstrap load failure (corrupt JSON, missing asset). US-2: no error path for permission permanently denied. US-5: no error path for Settings write failure to DataStore. US-7 is coverage-only; no failure scenario for lint tool misconfiguration.
- [x] CHK016 Feature meets **measurable outcomes** defined in Success Criteria (no SC without an FR producing the measurement).
  - **PASS** — SC-1→FR-001/FR-002/FR-003; SC-2→FR-001/FR-002; SC-3→FR-008/FR-011; SC-4→FR-012; SC-5→FR-015/FR-016; SC-6→FR-010/FR-017; SC-7→NFR-002; SC-8→FR-013; SC-9→NFR-003. All SC have producing FR/NFR.

---

## Summary

| Result | Count | Items |
|--------|-------|-------|
| PASS   | 9     | CHK005, CHK006, CHK007, CHK008, CHK010, CHK011, CHK013, CHK014, CHK016 |
| FAIL   | 7     | CHK001, CHK002, CHK003, CHK004, CHK009, CHK012, CHK015 |

### Key issues (ordered by severity)

1. **CHK001/CHK002/CHK003** (linked) — FR section is a technical design doc embedded in spec.md. Class names, DI module names, package paths, framework references belong in `plan.md`. The FR section should state *what the system must do* in user-visible terms, not *how it is wired*. Recommend: split FR into user-facing requirements (what) vs. implementation contracts (plan.md).
2. **CHK004/CHK012** (linked) — No explicit `## Scope (In / Out of Scope)` section. The spec is a pure-refactoring task where scope boundaries are especially critical (which legacy artefacts are deleted, what is not touched). Add a two-column In/Out scope table.
3. **CHK009** — SC-5..SC-9 are CI/grep commands. Move these to `tasks.md` verification gates or NFR section; SC section should state user-observable outcomes only.
4. **CHK015** — US-1, US-2, US-5 lack error scenarios. At minimum: "Given PresetBootstrap fails to load JSON, When app starts, Then error screen shown with retry option."
