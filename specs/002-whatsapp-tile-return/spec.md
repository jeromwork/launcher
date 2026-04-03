# Feature Specification: WhatsApp Contact Tiles via Communication Shell

**Feature Branch**: `002-whatsapp-tile-return`  
**Created**: 2026-04-01  
**Updated**: 2026-04-02  
**Status**: Final Draft  
**Input**: User description: "WhatsApp tiles via communication shell: person tile in Launcher, action confirmation screen, transition to WhatsApp, preserving return context, restoring Launcher home screen after return."

## Goal

Implement the first communication feature for Launcher as a controlled WhatsApp handoff flow that feels simple, predictable, and elderly-friendly, while keeping platform-specific behavior hidden behind one consistent user experience.

## Product Principles

- Launcher owns the entry flow, warning flow, and return experience.
- Real communication happens in the external messenger.
- The user experience must remain conceptually the same across Android and future iPhone support, even if platform mechanics differ internally.
- The feature must prefer predictable behavior over flexibility.
- The feature must minimize stored state, permissions, and background activity.
- All user-facing text must be localizable from the first release.

## Scope

### In Scope

- Person tile on Launcher home
- Enlarged contact tile layout: photo + name on top, two large action buttons on bottom (`Call`, `Video`)
- Pre-handoff confirmation screen
- Controlled launch into WhatsApp scenario
- Mock configuration only
- Save minimal return context before leaving Launcher
- Restore Launcher home state on return
- Large warning state when WhatsApp is unavailable or the selected action can no longer be started
- Localization-ready UX and copy from the first release
- Elderly-friendly readability and tap-target requirements
- Lightweight diagnostic hooks sufficient for troubleshooting failed handoff or failed restore, without heavy background observation
- Future-proof cross-platform product behavior constraints for later iPhone implementation

### Out of Scope

- Embedding external app UI
- Full cross-app control
- Device readiness screen
- Background communication tracking
- Incoming WhatsApp event detection
- Enterprise managed-device mode as part of this feature
- Complex relative-side tile management
- In-flow settings editor for contacts or actions
- Multi-step recovery wizards
- Rich analytics platform implementation
- iPhone implementation in this feature release

## Constraints

- Launcher owns entry UI, warning UI, and return context
- Real communication happens in external messenger
- Minimal permissions only
- No heavy background observers
- No hidden alternative actions beyond the tile's visible actions
- Cross-platform promises must remain honest: Launcher may guarantee only launcher-managed behavior, not WhatsApp internal behavior
- The same product meaning must be preserved on Android and on future iPhone builds, even if technical launch/return mechanisms differ
- Localized copy must remain readable for elderly users even when strings grow significantly in some languages

## Clarifications

### Session 2026-04-02

- Q: How should action be selected for a contact tile? -> A: Enlarged contact tile with photo + name on top and exactly two large buttons below: `Call` and `Video`.
- Q: What should happen if WhatsApp is not installed after a tile was already configured? -> A: Launcher must not attempt silent fallback. It must stay in Launcher and show a large readable warning.
- Q: What should happen if contact capability changes and the selected action can no longer be started? -> A: Launcher must stay in Launcher and show a large readable warning.
- Q: What is the preferred return behavior? -> A: Return must feel stable and predictable, with no unexpected jumps, re-routing, or surprise context changes.
- Q: How should localization be treated? -> A: Localization is a first-release requirement and must be considered in UI structure, copy length, and acceptance testing.
- Q: Should logging/diagnostics be part of this feature? -> A: Only lightweight, launcher-owned diagnostic hooks needed for troubleshooting; heavy monitoring remains out of scope.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Start WhatsApp from Contact Tile (Priority: P1)

A user uses a specific person's enlarged tile on the Launcher home screen and starts either a Call or Video action through WhatsApp.

**Why this priority**: This is the primary user value: fast access to a conversation with the intended contact.

**Independent Test**: Tap a contact tile action and verify that, after confirmation, WhatsApp opens for the selected contact and selected action.

**Acceptance Scenarios**:

1. **Given** the user is on the Launcher home screen and sees a contact tile, **When** the user taps `Call` or `Video` on that tile, **Then** an action confirmation screen opens showing the selected contact and selected action.
2. **Given** the action confirmation screen is open for a selected contact and selected action, **When** the user confirms, **Then** the system transitions the user to WhatsApp for that contact and that action.
3. **Given** localized copy is longer than the default language, **When** the tile and confirmation screen are rendered, **Then** the selected action remains clear and the layout remains readable without truncating meaning.

---

### User Story 2 - Preserve Return Context (Priority: P2)

Before transitioning to WhatsApp, the system preserves only the minimum launcher state needed so the user does not lose continuity when returning.

**Why this priority**: Without preserved context, the user loses continuity and must manually recover the expected Launcher state.

**Independent Test**: Launch WhatsApp from a tile, return to Launcher, and verify that the same home surface is restored without unexpected jumps.

**Acceptance Scenarios**:

1. **Given** the user confirms a WhatsApp launch from a tile, **When** the system transitions to the external app, **Then** it stores minimal return context tied to the source tile and home state.
2. **Given** valid return context is stored, **When** the user returns from WhatsApp, **Then** Launcher restores the same home surface in a stable way without redirecting the user to another screen.
3. **Given** the initiating tile still exists, **When** the user returns from WhatsApp, **Then** Launcher keeps the user on the same home level and may visually re-anchor attention to the initiating tile without forcing extra interaction.
4. **Given** the initiating tile no longer exists or the previous home state cannot be fully restored, **When** the user returns from WhatsApp, **Then** Launcher opens the nearest stable home state and provides a clear non-technical explanation.

---

### User Story 3 - Cancel Before Leaving Launcher (Priority: P3)

A user can cancel on the confirmation screen and remain in Launcher with no external app transition.

**Why this priority**: Reduces accidental app launches and improves behavior predictability.

**Independent Test**: Tap a tile action, choose cancel on the confirmation screen, and verify WhatsApp is not opened and Launcher remains usable in place.

**Acceptance Scenarios**:

1. **Given** the action confirmation screen is open, **When** the user cancels, **Then** Launcher closes confirmation and does not open WhatsApp.
2. **Given** the user canceled, **When** they continue using Launcher, **Then** the same home screen remains available for further tile actions without layout jumps or hidden state changes.

---

### User Story 4 - Warn Instead of Failing Silently (Priority: P1)

A user receives a large readable warning when WhatsApp is missing or when the configured contact/action can no longer be used.

**Why this priority**: Silent failure is especially harmful for elderly users and makes the product feel broken or confusing.

**Independent Test**: Simulate unavailable WhatsApp or unavailable selected action and verify Launcher remains in place and shows a clear warning.

**Acceptance Scenarios**:

1. **Given** a tile exists but WhatsApp is no longer available on the device, **When** the user attempts the action, **Then** Launcher stays in Launcher and shows a large readable warning with clear next-step guidance.
2. **Given** the selected contact/action pairing is no longer launchable, **When** the user confirms the action, **Then** Launcher stays in Launcher and shows a large readable warning with clear next-step guidance.
3. **Given** the warning is shown, **When** the user dismisses it, **Then** they return to a stable home state and can continue using Launcher.

### Edge Cases

- User taps the same contact action repeatedly in quick succession.
- User taps `Call` and then quickly taps `Video` for the same tile.
- User returns from WhatsApp after a long delay.
- User returns from WhatsApp via Back, Home, app switcher, or platform-equivalent return mechanism.
- The selected tile's WhatsApp action cannot be started.
- WhatsApp has been removed after initial tile setup.
- Contact metadata or capability changed after tile setup.
- Launcher is backgrounded while confirmation is open, then resumed.
- Home screen state changes while user is in WhatsApp.
- The source tile is removed, renamed, or moved before the user returns.
- Localized text becomes much longer than in the source language.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display a dedicated tile for each configured WhatsApp contact on the Launcher home screen.
- **FR-002**: System MUST render each contact as an enlarged tile with photo and name in the top area.
- **FR-003**: System MUST render exactly two large action buttons in the bottom area of each contact tile: `Call` and `Video`.
- **FR-004**: System MUST NOT expose hidden or additional communication actions from this tile beyond `Call` and `Video`.
- **FR-005**: Confirmation screen MUST clearly identify the selected contact and intended action.
- **FR-006**: Users MUST be able to confirm or cancel the action from the confirmation screen.
- **FR-007**: System MUST transition to WhatsApp only after explicit user confirmation.
- **FR-008**: System MUST capture and store return context before transitioning to WhatsApp.
- **FR-009**: System MUST store only the minimum return context required to restore launcher state.
- **FR-010**: Return context MUST be used only for restoring Launcher state and MUST NOT contain message content, chat history, typed text, or unnecessary sensitive data.
- **FR-011**: Return context MUST contain only launcher-owned restoration fields: initiating tile reference, home surface reference, and one action-cycle reference needed to prevent duplicate launches and stale restores.
- **FR-012**: System MUST keep at most one active return context for this feature at a time.
- **FR-013**: System MUST clear or replace stale return context when a new confirmed handoff starts or when the previous context is no longer valid.
- **FR-014**: System MUST restore the Launcher home screen using the saved return context when the user comes back from WhatsApp.
- **FR-015**: The expected restored state MUST preserve the same home level, tile arrangement context, and visual stability, and MUST avoid unexpected jumps to unrelated screens.
- **FR-016**: If the user cancels on the confirmation screen, system MUST keep the user in Launcher and MUST NOT start WhatsApp.
- **FR-017**: If WhatsApp transition cannot be completed, system MUST keep the user in Launcher and provide clear recovery guidance in a large readable warning state.
- **FR-018**: If WhatsApp is unavailable because it was removed after setup, system MUST keep the user in Launcher and provide a large readable warning state instead of a fallback launch.
- **FR-019**: If the selected contact or selected action is no longer valid, system MUST keep the user in Launcher and provide a large readable warning state instead of a fallback launch.
- **FR-020**: System MUST prevent duplicate launches caused by repeated taps on the same contact tile during one action cycle.
- **FR-021**: System MUST ensure that once one action cycle is in progress, no second action cycle from the same tile can create an overlapping handoff.
- **FR-022**: System MUST keep restoration behavior consistent regardless of return method (Back, Home, app switcher, or platform-equivalent return path).
- **FR-023**: System MUST use mock configuration as the only source for contact/action setup in this feature.
- **FR-024**: System MUST ensure that configuration does not create a tile for a contact/action pairing that is known to be unsupported at setup time.
- **FR-025**: System MUST NOT claim unsupported cross-platform behavior and MUST limit user-facing behavior to verified launcher-managed handoff and return flow.
- **FR-026**: User-visible behavior for this feature MUST remain conceptually equivalent across Android and future iPhone support, even if platform-specific implementation differs.
- **FR-027**: All user-visible strings in this feature MUST be localizable from the first release and MUST NOT be hard-coded only in one language.
- **FR-028**: The feature UX MUST tolerate longer localized strings without losing action meaning or making primary controls unreadable.
- **FR-029**: If a full text label cannot remain readable in a target language within the elderly-friendly layout, the product MAY use a simplified visual treatment, but the action meaning MUST remain explicit through localized supporting text or localized accessibility text.
- **FR-030**: Tile actions, confirmation actions, warnings, and dismiss actions MUST use elderly-friendly readability and tap-target sizing.
- **FR-031**: Warnings for missing WhatsApp or invalid contact/action MUST be visually prominent, readable at a glance, and written in non-technical language.
- **FR-032**: The feature MUST support keyboard/switch/assistive navigation semantics for launcher-owned screens where the platform supports them.
- **FR-033**: Launcher MUST provide visible confirmation of success, cancellation, or failure at launcher-owned points in the flow without relying on WhatsApp internals.
- **FR-034**: Launcher MUST define lightweight diagnostic events for confirmed launch attempt, launch failure, return restoration success, return restoration fallback, and invalid configuration/runtime capability failure.
- **FR-035**: Diagnostic support for this feature MUST remain launcher-owned and MUST NOT require heavy background observation of WhatsApp.
- **FR-036**: Any logging or diagnostics added for this feature MUST exclude message content and other unnecessary personal communication data.
- **FR-037**: Security and privacy responsibility boundaries MUST be explicit: Launcher is responsible only for launcher-owned UI, launcher-owned state, and launcher-owned warnings; WhatsApp remains responsible for its internal communication screens and behavior.
- **FR-038**: Minimal permissions for this feature MUST mean no additional broad permissions beyond those strictly needed for launcher-owned tile presentation, explicit handoff initiation, and launcher-owned restore behavior.
- **FR-039**: If the platform cannot guarantee an exact technical return path, the feature MUST still return the user to the nearest stable launcher-owned home state with equivalent product meaning.
- **FR-040**: Localization, accessibility, and warning states for this feature MUST be included in feature-level acceptance testing and MUST NOT be postponed to a later unspecified phase.

### Key Entities *(include if feature involves data)*

- **Contact Tile**: A person entry on Launcher home screen with display label, visual identifier, and linked communication target.
- **Action Confirmation Session**: A pending decision state for a selected tile, including selected contact, requested action, and decision outcome (`pending`, `confirmed`, `canceled`, `failed`).
- **Return Context**: Minimal launcher-owned restoration data used only to restore launcher state after a handoff attempt. It contains the initiating tile reference, home surface reference, and one action-cycle reference. It excludes message content, chat content, typed text, and unnecessary contact data.
- **Stable Home State**: The nearest launcher-owned home presentation that preserves user orientation, avoids unrelated screen changes, and minimizes surprise if exact restoration is unavailable.
- **Warning State**: A launcher-owned large readable interruption state explaining that the intended action could not start and what the user can do next.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 95% of users start a WhatsApp conversation with the intended contact and intended action from a tile in one attempt.
- **SC-002**: At least 95% of confirmed launches reach WhatsApp within 3 seconds from confirmation.
- **SC-003**: At least 98% of returns from WhatsApp restore Launcher home screen to the expected state or the nearest stable home state without manual recovery steps.
- **SC-004**: At least 90% of users report that confirmation flow, warnings, and return behavior are predictable in usability testing.
- **SC-005**: At least 95% of failure scenarios covered by this feature produce a launcher-owned warning instead of silent failure or an unexpected screen change.
- **SC-006**: At least 95% of localized test runs for supported languages preserve readable primary actions and warning comprehension in task-based testing.
- **SC-007**: 100% of launcher-owned diagnostic events defined for this feature exclude message content and unnecessary personal communication data.

## Assumptions

- Launcher already supports home-screen tiles and app-to-app transitions on Android.
- This feature release does not implement iPhone behavior, but the product meaning and specification constraints must be compatible with future iPhone support.
- WhatsApp is expected to be available for users included in this feature rollout, but runtime absence or runtime capability change must still be handled safely.
- Contact-to-WhatsApp mapping for tiles is provided via mock configuration in this feature phase.
- This feature covers a single-contact direct communication action per tile in the first release.
- Existing Launcher permissions and user identity context are sufficient; no new user role model is introduced.
- Platform-specific localization resources may differ internally, but the product must maintain one shared set of string meanings and UX intent across platforms.
- A broader diagnostics or analytics feature may be implemented later; this feature only requires lightweight hooks sufficient to validate and troubleshoot its own flow.
