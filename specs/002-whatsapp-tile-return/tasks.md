# Tasks: WhatsApp Contact Tiles via Communication Shell

**Input**: Design documents from `/specs/002-whatsapp-tile-return/`  
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Tests are required by spec (`FR-040`) and included per user story.  
**Organization**: Tasks are grouped by user story so each story can be implemented and validated independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Task can run in parallel (different files, no dependency on incomplete tasks)
- **[Story]**: User story label (`[US1]`, `[US2]`, `[US3]`, `[US4]`)
- Every task includes an explicit file path

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare minimal scaffolding for communication flow implementation.

- [X] T001 Add core test dependencies (`junit4`, `mockk`, `robolectric`) required for communication feature tests in `core/build.gradle.kts`
- [X] T002 [P] Add app test dependencies (`junit4`, `mockk`, `robolectric`, `androidx.test`) required for communication UI/flow tests in `app/build.gradle.kts`
- [X] T003 [P] Add mock WhatsApp tile configuration asset in `core/src/main/assets/whatsapp_tiles_mock.json`
- [X] T004 [P] Add base localization keys for communication flow in `app/src/main/res/values/strings.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core contracts and plumbing required by all user stories.

**⚠️ CRITICAL**: No user story work starts before this phase is complete.

- [X] T005 Create communication domain models (`ActionType`, handoff request/result, return context) in `core/src/main/java/com/launcher/api/CommunicationModels.kt`
- [X] T006 [P] Add communication diagnostic event types in `core/src/main/java/com/launcher/api/ProjectEvent.kt`
- [X] T007 Implement single-record return-context persistence (`SharedPreferences` JSON) in `core/src/main/java/com/launcher/core/actions/ReturnContextStore.kt`
- [X] T008 [P] Implement per-cycle duplicate launch guard in `core/src/main/java/com/launcher/core/actions/ActionCycleGuard.kt`
- [X] T009 Implement communication diagnostics emitter and reason-code mapping in `core/src/main/java/com/launcher/core/events/CommunicationDiagnostics.kt`
- [X] T010 Implement setup-time mock configuration validator that rejects known unsupported contact/action pairs in `core/src/main/java/com/launcher/core/actions/CommunicationConfigValidator.kt`
- [X] T011 Integrate foundational communication pipeline entry points in `core/src/main/java/com/launcher/core/actions/ActionDispatcher.kt`

**Checkpoint**: Foundation complete; user stories can proceed.

---

## Phase 3: User Story 1 - Start WhatsApp from Contact Tile (Priority: P1) 🎯 MVP

**Goal**: User can open confirmation and launch WhatsApp call/video for selected contact tile.

**Independent Test**: Tap tile `Call`/`Video`, confirm, verify transition attempt to WhatsApp with selected contact/action.

### Tests for User Story 1

- [X] T012 [P] [US1] Add handoff contract tests for launch-start and duplicate-cycle outcomes in `core/src/test/java/com/launcher/core/actions/WhatsAppHandoffContractTest.kt`
- [X] T013 [P] [US1] Add launcher flow test for tile tap -> confirmation -> confirm in `app/src/test/java/com/launcher/app/HomeWhatsAppLaunchFlowTest.kt`
- [X] T014 [P] [US1] Add setup-time configuration validation test that rejects unsupported pairs before tile render in `core/src/test/java/com/launcher/core/actions/CommunicationConfigValidatorTest.kt`

### Implementation for User Story 1

- [X] T015 [US1] Implement WhatsApp launchability resolver in `core/src/main/java/com/launcher/core/actions/WhatsAppLaunchabilityResolver.kt`
- [X] T016 [US1] Implement handoff execution path (confirm-only launch) in `core/src/main/java/com/launcher/core/actions/ActionDispatcher.kt`
- [X] T017 [P] [US1] Add communication UI model and mapping in `app/src/main/java/com/launcher/app/communication/CommunicationTileUiModel.kt`
- [X] T018 [US1] Add contact tile layout with photo/name and two large buttons in `app/src/main/res/layout/view_contact_tile.xml`
- [X] T019 [US1] Add confirmation UI layout with explicit confirm/cancel actions and launcher-owned success cue before handoff in `app/src/main/res/layout/view_whatsapp_confirmation.xml`
- [X] T020 [US1] Wire tile and confirmation flow into home screen in `app/src/main/java/com/launcher/app/HomeActivity.kt`

**Checkpoint**: User Story 1 is independently functional and testable.

---

## Phase 4: User Story 4 - Warn Instead of Failing Silently (Priority: P1)

**Goal**: Missing WhatsApp or invalid contact/action always yields launcher-owned readable warning.

**Independent Test**: Simulate unavailable WhatsApp and unsupported action; verify Launcher stays foreground and shows warning.

### Tests for User Story 4

- [X] T021 [P] [US4] Add warning-path integration tests for unavailable app and unsupported action in `app/src/test/java/com/launcher/app/HomeWhatsAppWarningFlowTest.kt`
- [X] T022 [P] [US4] Add diagnostics tests for failure reason-code emission in `core/src/test/java/com/launcher/core/events/CommunicationDiagnosticsTest.kt`

### Implementation for User Story 4

- [X] T023 [US4] Implement warning-state model and launcher-owned reason mapping in `app/src/main/java/com/launcher/app/communication/WarningState.kt`
- [X] T024 [US4] Add large readable warning layout in `app/src/main/res/layout/view_whatsapp_warning.xml`
- [X] T025 [US4] Add non-technical warning and dismiss localized copy in `app/src/main/res/values/strings.xml`
- [X] T026 [US4] Connect handoff failures to warning rendering in `app/src/main/java/com/launcher/app/HomeActivity.kt`

**Checkpoint**: User Story 4 is independently functional and testable.

---

## Phase 5: User Story 2 - Preserve Return Context (Priority: P2)

**Goal**: Confirmed handoff stores minimal context and restores same or nearest stable home state on return.

**Independent Test**: Launch from tile, return via Back/Home/app switcher, verify exact restore or deterministic nearest-stable fallback.

### Tests for User Story 2

- [X] T027 [P] [US2] Add return-context lifecycle tests (save/replace/clear/stale) in `core/src/test/java/com/launcher/core/actions/ReturnContextStoreTest.kt`
- [X] T028 [P] [US2] Add return restore integration tests for exact and fallback outcomes in `app/src/test/java/com/launcher/app/HomeReturnRestoreFlowTest.kt`

### Implementation for User Story 2

- [X] T029 [US2] Save minimal return context on confirmed handoff in `core/src/main/java/com/launcher/core/actions/ReturnContextStore.kt`
- [X] T030 [US2] Implement restore outcome evaluator (`RESTORED_EXACT_HOME`, `RESTORED_NEAREST_STABLE_HOME`, `NO_VALID_CONTEXT`) in `core/src/main/java/com/launcher/core/actions/RestoreOutcomeEvaluator.kt`
- [X] T031 [US2] Apply return restore handling on launcher resume/entry in `app/src/main/java/com/launcher/app/HomeActivity.kt`
- [X] T032 [US2] Emit restore success/fallback diagnostics events in `core/src/main/java/com/launcher/core/events/CommunicationDiagnostics.kt`

**Checkpoint**: User Story 2 is independently functional and testable.

---

## Phase 6: User Story 3 - Cancel Before Leaving Launcher (Priority: P3)

**Goal**: User can cancel confirmation and stay on Launcher without external app transition.

**Independent Test**: Tap tile action, cancel, verify no WhatsApp launch and stable home screen continuity.

### Tests for User Story 3

- [X] T033 [P] [US3] Add cancel-flow regression test in `app/src/test/java/com/launcher/app/HomeCancelFlowTest.kt`

### Implementation for User Story 3

- [X] T034 [US3] Implement explicit cancel transition handling in `app/src/main/java/com/launcher/app/HomeActivity.kt`
- [X] T035 [US3] Clear transient in-flight cycle state on cancel path in `core/src/main/java/com/launcher/core/actions/ActionCycleGuard.kt`

**Checkpoint**: User Story 3 is independently functional and testable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final hardening across all user stories.

- [X] T036 [P] Add accessibility semantics/content descriptions for tile, confirmation, and warning in `app/src/main/java/com/launcher/app/HomeActivity.kt`
- [X] T037 [P] Add long-localization regression test data in `app/src/test/resources/localization/whatsapp_long_strings.properties`
- [X] T038 Add permission regression check for communication feature in `specs/002-whatsapp-tile-return/checklists/security.md`
- [X] T039 Validate no new broad permissions in app manifest for this feature in `app/src/main/AndroidManifest.xml`
- [X] T040 [P] Validate no new broad permissions in core manifest for this feature in `core/src/main/AndroidManifest.xml`
- [X] T041 Update communication contract version references in `core/src/main/java/com/launcher/core/contracts/CoreContractVersions.kt`
- [X] T042 Add explicit Android-now/iPhone-later parity disclosure note in `docs/product/context-decisions-and-open-questions.md`
- [X] T043 Execute quickstart verification updates in `specs/002-whatsapp-tile-return/quickstart.md`
- [X] T044 Record verification evidence and unresolved risks in `specs/002-whatsapp-tile-return/checklists/verification-report.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies.
- **Phase 2 (Foundational)**: Depends on Phase 1; blocks all user stories.
- **Phase 3 (US1)**: Depends on Phase 2; MVP phase.
- **Phase 4 (US4)**: Depends on Phase 2; can run in parallel with US1 once foundational pieces are merged.
- **Phase 5 (US2)**: Depends on Phase 2 and requires US1 handoff path available.
- **Phase 6 (US3)**: Depends on Phase 3 confirmation flow.
- **Phase 7 (Polish)**: Depends on all selected user story phases.

### User Story Dependencies

- **US1 (P1)**: No dependency on other stories after foundation.
- **US4 (P1)**: No dependency on other stories after foundation.
- **US2 (P2)**: Depends on US1 confirmed launch pipeline.
- **US3 (P3)**: Depends on US1 confirmation UI pipeline.

### Within Each User Story

- Tests first, then implementation.
- Core validation/logic before UI wiring where both are involved.
- Story-specific diagnostics before phase checkpoint.

### Parallel Opportunities

- Setup tasks marked `[P]` can run together.
- Foundational tasks `T006`, `T008`, `T009` can run in parallel after `T005`.
- US1 tasks `T012`, `T013`, `T014`, `T017` can run in parallel.
- US4 tasks `T021`, `T022`, `T024`, `T025` can run in parallel.
- US2 tasks `T027` and `T028` can run in parallel.

---

## Parallel Example: User Story 1

```bash
# Run in parallel after foundational phase:
Task: "T012 [US1] Add handoff contract tests in core/src/test/java/com/launcher/core/actions/WhatsAppHandoffContractTest.kt"
Task: "T013 [US1] Add launcher flow test in app/src/test/java/com/launcher/app/HomeWhatsAppLaunchFlowTest.kt"
Task: "T014 [US1] Add setup-time config validation test in core/src/test/java/com/launcher/core/actions/CommunicationConfigValidatorTest.kt"
Task: "T017 [US1] Add communication UI model in app/src/main/java/com/launcher/app/communication/CommunicationTileUiModel.kt"
```

## Parallel Example: User Story 4

```bash
# Run in parallel after US1 core flow exists:
Task: "T021 [US4] Add warning-path integration tests in app/src/test/java/com/launcher/app/HomeWhatsAppWarningFlowTest.kt"
Task: "T022 [US4] Add diagnostics tests in core/src/test/java/com/launcher/core/events/CommunicationDiagnosticsTest.kt"
Task: "T024 [US4] Add warning layout in app/src/main/res/layout/view_whatsapp_warning.xml"
Task: "T025 [US4] Add warning strings in app/src/main/res/values/strings.xml"
```

---

## Implementation Strategy

### MVP First (US1)

1. Complete Phase 1 and Phase 2.
2. Complete Phase 3 (US1).
3. Validate US1 independently.
4. Demo or ship MVP slice.

### Incremental Delivery

1. Add US4 for failure transparency.
2. Add US2 for return continuity.
3. Add US3 for cancel robustness.
4. Finish with Polish phase and full quickstart verification.

### Parallel Team Strategy

1. Team completes Setup + Foundational together.
2. Then split: one engineer on US1/US2 core, one on US4 UI/warnings, one on test automation.
3. Merge at story checkpoints to keep each story independently releasable.

---

## Notes

- `[P]` tasks are limited to tasks touching different files or independent test artifacts.
- Keep diffs minimal and trace each task back to `FR-001..FR-040`.
- Commit after each logical completed task group per constitution Article XVIII.
