# Feature Specification: WhatsApp Contact Tiles via Communication Shell

**Feature Branch**: `002-whatsapp-tile-return`  
**Created**: 2026-04-01  
**Status**: Draft  
**Input**: User description: "WhatsApp tiles via communication shell: person tile in Launcher, action confirmation screen, transition to WhatsApp, preserving return context, restoring Launcher home screen after return."

## Goal

Implement the first communication feature for Launcher as a controlled WhatsApp handoff flow.

## Scope

### In Scope

- Person tile on Launcher home
- Enlarged contact tile layout: photo + name on top, two large action buttons on bottom (Call, Video)
- Pre-handoff confirmation screen
- Controlled launch into WhatsApp scenario
- Mock configuration only
- Save return context before leaving Launcher
- Restore Launcher home state on return

### Out of Scope

- Embedding external app UI
- Full cross-app control
- Device readiness screen
- Background communication tracking
- Incoming WhatsApp event detection
- Enterprise managed-device mode as part of this feature
- Complex relative-side tile management

## Constraints

- Launcher owns entry UI and return context
- Real communication happens in external messenger
- Minimal permissions
- No heavy background observers
- Cross-platform promises must remain honest

## Clarifications

### Session 2026-04-02

- Q: How should call/message action be selected for a contact tile? -> A: Enlarged contact tile with photo + name on top and two large buttons below: Call and Video.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Start WhatsApp from Contact Tile (Priority: P1)

A user uses a specific person's enlarged tile on the Launcher home screen and starts either a Call or Video action through WhatsApp.

**Why this priority**: This is the primary user value: fast access to a conversation with the intended contact.

**Independent Test**: Tap a contact tile and verify that, after confirmation, WhatsApp opens for the selected contact.

**Acceptance Scenarios**:

1. **Given** the user is on the Launcher home screen and sees a contact tile, **When** the user taps `Call` or `Video` on that tile, **Then** an action confirmation screen opens showing the selected contact and action.
2. **Given** the action confirmation screen is open for a selected contact and action, **When** the user confirms, **Then** the system transitions the user to WhatsApp for that contact and action.

---

### User Story 2 - Preserve Return Context (Priority: P2)

Before transitioning to WhatsApp, the system preserves launch context so the user does not lose Launcher state when returning.

**Why this priority**: Without preserved context, the user loses continuity and must manually recover the expected Launcher state.

**Independent Test**: Launch WhatsApp from a tile, return to Launcher, and verify the expected home screen state is restored.

**Acceptance Scenarios**:

1. **Given** the user confirms a WhatsApp launch from a tile, **When** the system transitions to the external app, **Then** it stores return context tied to the source tile and home screen state.
2. **Given** return context is stored, **When** the user returns from WhatsApp, **Then** Launcher restores the home screen to the expected state.

---

### User Story 3 - Cancel Before Leaving Launcher (Priority: P3)

A user can cancel on the confirmation screen and remain in Launcher with no external app transition.

**Why this priority**: Reduces accidental app launches and improves behavior predictability.

**Independent Test**: Tap a tile, choose cancel on the confirmation screen, and verify WhatsApp is not opened and Launcher remains usable in place.

**Acceptance Scenarios**:

1. **Given** the action confirmation screen is open, **When** the user cancels, **Then** Launcher closes confirmation and does not open WhatsApp.
2. **Given** the user canceled, **When** they continue using Launcher, **Then** the home screen remains available for further tile actions.

### Edge Cases

- User taps the same contact tile repeatedly in quick succession.
- User returns from WhatsApp after a long delay.
- User returns from WhatsApp via Back, Home, or app switcher.
- The selected tile's WhatsApp action cannot be started.
- Launcher is backgrounded while confirmation is open, then resumed.
- Home screen state changes while user is in WhatsApp.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display a dedicated tile for each configured WhatsApp contact on the Launcher home screen.
- **FR-002**: System MUST render each contact as an enlarged tile with photo and name in the top area.
- **FR-003**: System MUST render exactly two large action buttons in the bottom area of each contact tile: `Call` and `Video`.
- **FR-004**: Confirmation screen MUST clearly identify the selected contact and intended action.
- **FR-005**: Users MUST be able to confirm or cancel the action from the confirmation screen.
- **FR-006**: System MUST transition to WhatsApp only after explicit user confirmation.
- **FR-007**: System MUST capture and store return context before transitioning to WhatsApp.
- **FR-008**: System MUST restore the Launcher home screen using the saved return context when the user comes back from WhatsApp.
- **FR-009**: If the user cancels on the confirmation screen, system MUST keep the user in Launcher and MUST NOT start WhatsApp.
- **FR-010**: If WhatsApp transition cannot be completed, system MUST keep the user in Launcher and provide clear recovery guidance.
- **FR-011**: System MUST prevent duplicate launches caused by repeated taps on the same contact tile during one action cycle.
- **FR-012**: System MUST keep restoration behavior consistent regardless of return method (Back, Home, or app switcher).
- **FR-013**: System MUST use mock configuration as the only source for contact/action setup in this feature.
- **FR-014**: System MUST NOT claim unsupported cross-platform behavior and MUST limit user-facing behavior to verified launcher-managed handoff and return flow.

### Key Entities *(include if feature involves data)*

- **Contact Tile**: A person entry on Launcher home screen with display label, visual identifier, and linked communication target.
- **Action Confirmation Session**: A pending decision state for a selected tile, including selected contact, requested action, and decision outcome (pending, confirmed, canceled).
- **Return Context**: Data required to restore Launcher home screen state after returning from WhatsApp, including source tile and state reference.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 95% of users start a WhatsApp conversation with the intended contact from a tile in one attempt.
- **SC-002**: At least 95% of confirmed launches reach WhatsApp within 3 seconds from confirmation.
- **SC-003**: At least 98% of returns from WhatsApp restore Launcher home screen to the expected state without manual recovery steps.
- **SC-004**: At least 90% of users report that confirmation flow and return behavior are predictable in usability testing.

## Assumptions

- Launcher already supports home-screen tiles and app-to-app transitions.
- WhatsApp is installed and available for users included in this feature rollout.
- Contact-to-WhatsApp mapping for tiles is provided via mock configuration in this feature phase.
- This feature covers a single-contact direct communication action per tile in the first release.
- Existing Launcher permissions and user identity context are sufficient; no new user role model is introduced.
