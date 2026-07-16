# Checklist: ux-quality

Applied: 2026-07-15
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

Note: primarily a data-layer refactor. UX surfaces touched: (a) HomeScreen post-wizard (must show tiles, not Error), (b) Wizard strings (readable Russian, not raw keys). No new screens introduced.

## Completeness — coverage of screens

- [x] CHK001 Screens implicated: HomeActivity (existing) + WizardHostActivity (existing per TASK-126). No new screens.
- [x] CHK002 UX states: HomeLoadingState.Loading / Ready / Error explicit per TASK-52 contract. Edge case "empty Profile → Ready with empty screen" specified.
- [x] CHK003 Navigation: wizard → HomeActivity (existing flow, unbroken).
- [x] CHK004 No new overlays.

## Clarity — terminology and rules

- [x] CHK005 Terms consistent: Tag, Component, Profile, FlowDescriptor, HomeLoadingState — all defined in Key Entities / referenced tasks.
- [x] CHK006 No vague qualifiers. "under 1ms" (NFR-003), specific test cases.
- [x] CHK007 Action vocabulary: N/A (no gesture UX added).
- [x] CHK008 Button labels: FR-008 enumerates exact string keys (`wizard_step_of`, `wizard_confirm`, etc.).

## Consistency

- [x] CHK009 In-Scope / FR alignment consistent — no orphan FR.
- [x] CHK010 N/A — no new confirmation flow.
- [x] CHK011 N/A.

## Acceptance — measurability

- [x] CHK012 All 3 US have Given/When/Then scenarios.
- [x] CHK013 NFR-003 query performance 1ms; SC-001 user-visible tiles rendering.
- [x] CHK014 US1 scenario #3 covers second-launch (Profile edited via Settings → new emit without Activity restart).

## Coverage — alternative paths

- [x] CHK015 Negative paths in Edge Cases (0-tile Profile, null ProfileStore, empty tags).
- [x] CHK016 N/A — only wizard → home entry.
- [x] CHK017 N/A — no long-pause return UX changes.

## Non-functional UX

- [x] CHK018 A11y: separate checklist-elderly-friendly runs.
- [x] CHK019 Localization: separate checklist-localization runs; FR-008 mandates it.
- [x] CHK020 N/A — no tracking / analytics UX introduced.

## Dependencies / assumptions

- [x] CHK021 UX doesn't depend on out-of-scope. Wizard already works per TASK-126.
- [x] CHK022 Mock-data limitations N/A — real Profile via ProfileStore.

**Result**: 22/22 passed. UX surface intentionally minimal (fix HomeScreen regression + wizard string readability). No new UX contracts introduced.
