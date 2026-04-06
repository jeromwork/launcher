# Research: WhatsApp Contact Tiles via Communication Shell

**Feature**: `002-whatsapp-tile-return`  
**Date**: 2026-04-03  
**Spec**: [spec.md](./spec.md)

All technical unknowns from planning were resolved below.

---

## 1. WhatsApp handoff mechanism on Android

**Decision**: Model launcher handoff as a typed `WhatsappHandoffRequest` (`contactId`, `actionType`, `actionCycleId`) and resolve an explicit launch intent targeted to WhatsApp package (`com.whatsapp`) through Core dispatch. Validate launchability before leaving Launcher.

**Rationale**: Keeps external invocation behind one launcher-owned boundary, prevents ad hoc intent creation in UI, and aligns with FR-007/FR-017/FR-018/FR-019.

**Alternatives considered**:

- Direct UI-level intent launch from `HomeActivity`: rejected because it bypasses Core policy and diagnostics boundaries.
- Generic implicit intent without package targeting: rejected due to predictability/security and weaker failure classification.

---

## 2. Return context persistence model

**Decision**: Keep exactly one active `ReturnContext` record with fields constrained to FR-011 (`initiatingTileRef`, `homeSurfaceRef`, `actionCycleRef`) plus timestamps and schema version for staleness handling. Persist in launcher-owned local store and mirror in-memory during active cycle.

**Rationale**: Satisfies minimum-state requirements (FR-009..FR-013), supports process recreation safety, and avoids any communication-content storage risk.

**Alternatives considered**:

- In-memory only context: rejected because process recreation can break restore continuity.
- Multi-record history: rejected as unnecessary complexity and larger privacy surface.

---

## 3. Duplicate launch and overlap protection

**Decision**: Introduce one action-cycle lock per initiating tile interaction. Confirm path atomically marks cycle `in_progress`; further taps for same cycle return `duplicate_ignored` result and do not launch a second handoff.

**Rationale**: Directly addresses FR-020 and FR-021 for rapid repeated taps and action switch races (`Call` then `Video`).

**Alternatives considered**:

- UI debounce only: rejected because lifecycle/recreation paths could still produce overlap.
- Global app-wide lock for all tiles: rejected as over-restrictive and user-hostile.

---

## 4. Warning-state strategy

**Decision**: Standardize launcher-owned warning states with stable reason codes:

- `whatsapp_unavailable`
- `action_not_supported`
- `handoff_launch_failed`
- `restore_fallback_used`

Each warning includes a large readable headline, plain-language explanation, and explicit dismiss action back to stable home state.

**Rationale**: Meets FR-017..FR-019 and FR-031; keeps failure communication predictable and testable.

**Alternatives considered**:

- Silent fallback to generic dialer/video app: rejected by clarified requirement.
- Technical error dialogs with stack-like details: rejected for elderly-first UX.

---

## 5. Localization and accessibility implementation direction

**Decision**: All user-facing strings for tile actions, confirmation, warning copy, and dismiss guidance must be resource-based from initial implementation. Layouts must support longer strings via wrapping/adaptive constraints and preserve large tap targets.

**Rationale**: Required by FR-027..FR-030 and ADR-004.

**Alternatives considered**:

- Shipping English-only strings initially: rejected by specification.
- Truncating primary labels without semantic backup: rejected because it can hide action meaning.

---

## 6. Diagnostics hooks (lightweight only)

**Decision**: Emit launcher-owned diagnostic events only at flow milestones:

- `whatsapp_launch_confirmed`
- `whatsapp_launch_failed`
- `return_restore_success`
- `return_restore_fallback`
- `configuration_invalid_or_capability_failed`

Payload excludes message/chat content and includes only technical reason codes, tile id hash/reference, action type, and cycle id.

**Rationale**: Meets FR-034..FR-036 and support-ops triage requirements.

**Alternatives considered**:

- No diagnostics in first slice: rejected because troubleshooting requirement is explicit.
- Heavy session monitoring/background observers: rejected by scope and battery constraints.

---

## 7. Permissions and resource budget

**Decision**: No new broad runtime permissions are required for this feature. Keep package visibility and intent checks limited to launcher-owned dispatch path; no background polling or long-running observers.

**Rationale**: Aligns with FR-038 and `docs/compliance/permissions-and-resource-budget.md`.

**Alternatives considered**:

- Additional contact/telephony permissions for richer capability probing: rejected as unnecessary and scope-expanding.

---

## 8. Test strategy for this feature slice

**Decision**: Use layered tests:

- Contract tests for handoff request/response and return-context schema constraints.
- Integration tests for confirm success, cancel path, missing WhatsApp, invalid capability, duplicate tap suppression, return restore success/fallback.
- UI verification for localized long strings and large-target warning/confirmation behavior.

**Rationale**: Covers high-risk behavior regressions while keeping tooling consistent with existing stack.

**Alternatives considered**:

- UI-only coverage: rejected because it misses core flow and persistence rules.
- Unit-only coverage: rejected because cross-layer behavior is central to this feature.
