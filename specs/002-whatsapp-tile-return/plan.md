# Implementation Plan: WhatsApp Contact Tiles via Communication Shell

**Branch**: `002-whatsapp-tile-return` | **Date**: 2026-04-03 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/002-whatsapp-tile-return/spec.md`

## Summary

Deliver a launcher-owned WhatsApp handoff flow for person tiles with two explicit actions (`Call`, `Video`), a mandatory confirmation step, single-cycle handoff protection, and deterministic return-state restoration to Launcher home. The implementation keeps platform-specific launch mechanics behind Core contracts, stores only minimal return context, shows large readable warnings for unavailable paths, and preserves cross-platform product meaning for future iPhone parity documentation.

## Technical Context

**Language/Version**: Kotlin 2.0.21, JDK 17, Android Gradle Plugin 8.7.3  
**Primary Dependencies**: AndroidX Core/AppCompat/Activity/Lifecycle, Kotlin coroutines/Flow, existing `:core` launcher contracts  
**Storage**: Single active return context persisted in launcher-owned `SharedPreferences` JSON record, plus in-memory in-flight cycle state  
**Testing**: JUnit4 + MockK + Robolectric for Core and action pipelines; focused integration tests for handoff/restore flow; manual instrumentation checks for accessibility/readability on launcher-owned screens  
**Target Platform**: Android (`minSdk 26`, `targetSdk 35`, `compileSdk 35`) with documented parity constraints for future iPhone  
**Project Type**: Mobile app (multi-module Android launcher: `app` + `core`)  
**Performance Goals**: 95% confirmed launches reach external app transition within 3 seconds (SC-002); avoid duplicate launch overlap within one action cycle  
**Constraints**: No heavy background observers; no silent fallback launch; no additional broad permissions; localization-ready from first release; launcher-owned diagnostics only  
**Scale/Scope**: First communication feature slice for mock-configured WhatsApp contact tiles and launcher-owned return behavior

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Architecture Gate

| Question | Result |
|----------|--------|
| Does feature fit layered architecture? | **Pass.** UI remains in `app`; orchestration and external-app dispatch contracts remain in `core`. |
| Is new module required? | **Pass.** No new Gradle module required; feature implemented inside existing module boundaries per Article V restraint. |
| Are boundaries explicit and minimal? | **Pass.** Launcher-owned flow states, return context, and warnings are explicit models/contracts; messenger internals remain outside scope. |

### Core/System Integration Gate

| Question | Result |
|----------|--------|
| Are system/external integrations required? | **Yes.** External app invocation and app-availability/capability checks are required. |
| Are integrations centralized in Core boundary? | **Pass.** Handoff dispatch and runtime launchability checks are routed through Core action interfaces; feature UI does not create ad hoc global listeners. |
| Are event contracts documented? | **Pass.** Diagnostic and flow event contracts are defined under `contracts/`. |

### Configuration Gate

| Question | Result |
|----------|--------|
| Does feature affect profile/config schema? | **Pass with scoped change.** Adds mock communication tile configuration schema for WhatsApp target + allowed action set. |
| Are validation and compatibility covered? | **Pass.** Unsupported contact/action pairs are blocked at setup/runtime with warning state and stable reason codes. |

### Required Context Review Gate

Reviewed sources and impact:

- `docs/governance/document-map.md`: confirms mandatory context chain for plan completeness.
- `docs/product/context-decisions-and-open-questions.md`: confirms communication flow is strategic and parity/resource gates are mandatory.
- `docs/product/feature-priorities.md`: confirms messenger call/video path is must-have, layout experimentation is secondary.
- `docs/adr/ADR-001-cross-platform-strategy.md`: enforces shared product model with platform parity disclosure.
- `docs/adr/ADR-004-localization-and-global-readiness.md`: localization impact must be explicit from first release.
- `docs/compliance/permissions-and-resource-budget.md`: requires explicit resource-budget review.
- `docs/compliance/store-policy-register.md`: relevant for external app invocation and privacy-safe behavior disclosure.
- `docs/compliance/distribution-channel-register.md`: no channel-specific behavior divergence in this slice; baseline official stores assumed.
- `docs/research/messenger-calling-research.md`: confirms required launchability checks, fallback behavior, and parity framing.
- `docs/operations/support-and-feedback-ops.md`: diagnostic ownership and triage path required for release readiness.

Normally relevant but not directly impacted in this plan:

- `docs/adr/ADR-002-licensing-and-anti-abuse.md`: no licensing/anti-abuse mechanics introduced by this feature.
- `docs/adr/ADR-003-monetization-entitlements.md`: no billing or entitlements changes.
- `docs/compliance/country-legal-tax-register.md`: no country-specific pricing/tax behavior.
- `docs/compliance/partner-distribution-model.md`: partner channel packaging unchanged.
- `docs/research/market-and-channel-research.md`: no channel expansion in this feature slice.
- `docs/research/subscription-model-research.md`: no subscription model changes.

### Accessibility Gate

| Question | Result |
|----------|--------|
| Elderly/accessibility impact addressed? | **Pass.** Large tile actions, readable confirmation, and prominent non-technical warnings are first-class acceptance requirements. |
| Acceptance criteria include accessibility/localization? | **Pass with follow-up.** FR-027..FR-032 and FR-040 are covered by implementation phases and verification steps below; quickstart includes localization/a11y validation path. |

### Battery/Performance Gate

| Question | Result |
|----------|--------|
| Background/runtime cost controlled? | **Pass.** No polling or heavy observers; handoff and restore are foreground flow events with one active context record. |
| Startup impact controlled? | **Pass.** Return-context read is lightweight and scoped to launcher return path only. |

### Testing Gate

| Question | Result |
|----------|--------|
| Contract/integration/regression coverage defined? | **Pass.** Contracts for handoff/return/diagnostics plus focused tests for success, cancel, failure, stale context, and duplicate taps. |
| Failure modes covered? | **Pass.** Missing app, invalid action capability, stale tile, failed restore, and duplicate action-cycle overlap are explicit scenarios. |

### Simplicity Gate

| Question | Result |
|----------|--------|
| Any speculative abstraction? | **Pass.** Reuses existing action dispatch/event infrastructure; adds only feature-specific models and contracts needed now. |
| Can design be reduced further safely? | **Pass.** Single active context, `SharedPreferences` persistence, and mock configuration keep scope minimal while satisfying all FRs. |

### Platform Parity Gate

| Question | Result |
|----------|--------|
| Is parity promise honest? | **Pass.** Plan commits only to launcher-owned behavior parity, not messenger internal UI parity (FR-025/FR-026). |
| Is current parity gap documented? | **Pass.** Android implementation now; iPhone deferred with preserved product semantics and equivalent warning/return concepts. |

### Restore Fidelity Gate

| Question | Result |
|----------|--------|
| Is restore state defined concretely enough? | **Pass with bounded scope.** `homeSurfaceRef` is limited to launcher-owned home destination identity plus the initiating tile reference; exact scroll/transient UI state is not guaranteed unless already owned by existing home-state APIs. |
| Is fallback behavior explicit? | **Pass.** If tile/home arrangement changed while user was away, Launcher restores nearest stable home state, optionally re-anchors to the initiating tile when still present, and otherwise shows launcher-owned explanation without surprise rerouting. |

### Resource Budget Review

| Dimension | Decision |
|----------|----------|
| Permissions | No new broad permissions; package visibility remains minimal for launcher-owned intent resolution only. |
| Battery | No background monitoring of WhatsApp sessions; no polling. |
| Memory/Storage | Single active return-context record and lightweight transient state only. |
| Network | No feature-owned network traffic required. |
| Observability | Lightweight launcher-owned diagnostics events only, privacy-safe payloads only. |

### Support/Error/Feedback Ownership

- Feature owner: Launcher communication flow team (same module owners as Home + Core actions).
- Error intake: launcher diagnostics event stream + crash pipeline.
- User feedback routing: existing support/feedback loop from `docs/operations/support-and-feedback-ops.md`.
- Triage: classify handoff failure vs restore fallback vs config invalidity by reason code.

---

### Constitution Check — Post Phase 1 Design (re-evaluation)

All gates remain **Pass** after creation of `research.md`, `data-model.md`, `contracts/*`, and `quickstart.md`. No constitutional violations require exception handling.

## Project Structure

### Documentation (this feature)

```text
specs/002-whatsapp-tile-return/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── README.md
│   ├── whatsapp-handoff.md
│   ├── return-context.md
│   └── diagnostics-events.md
└── tasks.md
```

### Source Code (repository root) — target after implementation

```text
app/
├── src/main/java/com/launcher/app/
│   ├── HomeActivity.kt
│   └── [communication UI package for tile + confirmation + warning]
└── src/main/res/
    ├── layout/
    └── values/ (localized strings)

core/
├── src/main/java/com/launcher/api/
│   ├── ActionModels.kt
│   ├── ProjectEvent.kt
│   └── [communication handoff models]
└── src/main/java/com/launcher/core/
    ├── actions/ (dispatch + launchability checks)
    ├── events/ (diagnostic/event routing)
    └── [return-context persistence/orchestration]

core/src/test/java/com/launcher/core/
app/src/test/java/com/launcher/app/
```

**Structure Decision**: Keep existing `:app` and `:core` module layout. `app` owns tile/confirmation/warning surfaces and restore-entry handling; `core` owns external-app dispatch rules, return-context persistence contracts, and diagnostic event emission.

## Complexity Tracking

No constitution violations require exception handling.

Complexity intentionally rejected:

- No new Gradle module.
- No repository/use-case split beyond existing `app` / `core` boundaries unless existing code already requires it.
- No DataStore migration for one small active record; `SharedPreferences` is sufficient for this feature slice.
- No background observers, polling, session tracking, or analytics platform expansion.
- No attempt to normalize Android and future iPhone implementation mechanics; only launcher-owned product meaning is shared.

## Phase 0: Research (complete)

**Output**: [research.md](./research.md)

Key outcomes:

- Chosen Android handoff mechanism and launchability checks.
- Single active return-context model with action-cycle id.
- Duplicate launch prevention approach.
- Localization/accessibility and diagnostics decisions.

## Phase 1: Design & Contracts (complete)

**Output**:

- [data-model.md](./data-model.md)
- [contracts/](./contracts/)
- [quickstart.md](./quickstart.md)

**Agent context update**: run `.specify/scripts/powershell/update-agent-context.ps1 -AgentType codex`.

## Phase 2: Core Implementation

Goal: implement the smallest launcher-owned handoff and restore pipeline inside existing `:core` boundaries.

Scope:

- Add typed WhatsApp handoff request/result handling under existing Core action interfaces.
- Validate launchability before leaving Launcher: package available, requested action supported, no overlapping cycle for the same tile.
- Validate mock configuration at setup/load time so known unsupported contact/action pairs are rejected before tile presentation.
- Persist exactly one active return-context record in `SharedPreferences` JSON using only `initiatingTileRef`, `homeSurfaceRef`, `actionCycleRef`, and freshness metadata.
- Emit lightweight diagnostic events for launch confirmed, launch failure, restore success, restore fallback, and invalid runtime/config state.

Constraints:

- No new broad permissions.
- No new process-wide listeners or observers.
- No storage of message content, chat history, typed text, or unnecessary personal data.

Verification:

- Core tests cover request validation, duplicate-cycle rejection, stale-context replacement, restore outcome classification, and privacy-safe diagnostics payloads.

## Phase 3: App/UI Implementation

Goal: add the launcher-owned UI flow without expanding product scope.

Scope:

- Render enlarged person tile with photo/name and exactly two large actions: `Call` and `Video`.
- Add confirmation screen with clear contact/action identity, explicit confirm/cancel actions, and launcher-owned visible success cue before external transition.
- Add large readable warning state for `whatsapp_unavailable`, `action_not_supported`, `handoff_launch_failed`, and restore fallback explanation.
- Restore user to the same launcher-owned home destination when valid context exists; if exact state cannot be restored, open nearest stable home state and keep orientation clear.

Restore definition for this slice:

- `homeSurfaceRef` means launcher-owned home destination identity already supported by the app.
- The plan restores the same home level and source-tile orientation when those references remain valid.
- The plan does not introduce new infrastructure for exact transient UI reconstruction if the current app does not already own it.

Constraints:

- No hidden actions beyond `Call` and `Video`.
- No silent fallback launch to other apps.
- All strings must be resource-based and localization-ready from first release.

Verification:

- App/UI tests cover confirmation open/cancel flow, successful handoff entry, warning rendering, duplicate-tap suppression at UI boundary, and restore to exact/fallback home state.

## Phase 4: End-to-End Verification

Goal: verify risky behavior across the launcher-owned flow and validate platform promises honestly.

Coverage:

- Confirmed handoff success.
- Cancel before leaving Launcher.
- WhatsApp unavailable at runtime.
- Contact/action capability invalid at runtime.
- Duplicate rapid taps on same tile.
- Return via Back, Home, and app switcher.
- Return after tile moved/removed or home state changed.
- Long localized strings and assistive navigation semantics on launcher-owned screens.
- Manifest-level permission regression check confirms no new broad permissions were introduced.

Success checks:

- SC-002 and SC-003 measured through focused test runs and manual verification on representative Android devices/emulators.
- FR-032 and FR-040 verified at launcher-owned surfaces before implementation is considered complete.

## Phase 5: Release Readiness

Goal: ship the feature as a narrow Android slice without overstating parity.

Scope:

- Confirm user-visible copy is localized and non-technical.
- Confirm support triage can distinguish launch failure, invalid capability/configuration, and restore fallback by reason code.
- Confirm docs and release notes describe Android support now and future iPhone parity as product intent only, not as implemented platform support.

Exit criteria:

- No open constitutional violations.
- No known silent-failure path for launcher-owned flow.
- No unresolved ambiguity about stored state, permissions, or restore behavior.
