# Data Model: WhatsApp Contact Tiles via Communication Shell

**Feature**: `002-whatsapp-tile-return`  
**Spec**: [spec.md](./spec.md)

---

## ContactTile

**Purpose**: Launcher home representation of one configured communication target.

| Field | Type | Rules |
|------|------|-------|
| `tileId` | String | Stable launcher-owned id; required. |
| `displayName` | LocalizedTextRef | Must resolve to readable localized string. |
| `photoRef` | String? | Optional media reference; fallback avatar required. |
| `contactRef` | String | Mock-configured target id; required. |
| `allowedActions` | Set<ActionType> | Must include only `CALL`, `VIDEO`; no hidden actions. |
| `isEnabled` | Boolean | Disabled tile cannot start handoff and should show warning guidance. |

**Validation**:

- Reject configuration with empty `contactRef`.
- Reject any action outside `CALL` and `VIDEO`.
- Setup-time known unsupported pair must not produce active tile (FR-024).

---

## ActionType

| Value | Meaning |
|------|---------|
| `CALL` | Start voice call handoff intent via WhatsApp flow. |
| `VIDEO` | Start video call handoff intent via WhatsApp flow. |

---

## ActionConfirmationSession

**Purpose**: Launcher-owned pending decision before external handoff.

| Field | Type | Rules |
|------|------|-------|
| `sessionId` | String | Unique per attempt; may equal `actionCycleId`. |
| `tileId` | String | Must reference existing tile at session creation time. |
| `contactRef` | String | Copied from tile/config to avoid drift during session. |
| `selectedAction` | ActionType | Required. |
| `status` | Enum | `PENDING`, `CONFIRMED`, `CANCELED`, `FAILED`. |
| `createdAtEpochMs` | Long | For stale-session handling and diagnostics. |

**State transitions**:

- `PENDING -> CONFIRMED` (user confirms, launch pipeline starts)
- `PENDING -> CANCELED` (user cancels, stay in Launcher)
- `PENDING -> FAILED` (runtime validation fails before handoff)
- Terminal states are immutable.

---

## ReturnContext

**Purpose**: Minimal launcher-owned state for restoring home continuity after returning from external app.

| Field | Type | Rules |
|------|------|-------|
| `schemaVersion` | Int | Required for migration compatibility. |
| `initiatingTileRef` | String | Required, FR-011 field. |
| `homeSurfaceRef` | String | Required, FR-011 field. |
| `actionCycleRef` | String | Required, FR-011 field; used for dedupe/stale checks. |
| `savedAtEpochMs` | Long | Required for freshness checks and diagnostics. |
| `expiresAtEpochMs` | Long? | Optional policy-based staleness boundary. |

**Policy constraints**:

- Exactly one active record exists at a time (FR-012).
- New confirmed launch replaces previous record (FR-013).
- Must never include message/chat/typed text or unnecessary personal data (FR-010).

---

## RestoreOutcome

| Value | Meaning |
|------|---------|
| `RESTORED_EXACT_HOME` | Expected home surface restored directly. |
| `RESTORED_NEAREST_STABLE_HOME` | Exact state unavailable; nearest stable home used. |
| `NO_VALID_CONTEXT` | No active valid context to apply. |

---

## WarningState

**Purpose**: Large readable launcher-owned interruption with non-technical explanation.

| Field | Type | Rules |
|------|------|-------|
| `warningCode` | Enum | `WHATSAPP_UNAVAILABLE`, `ACTION_NOT_SUPPORTED`, `HANDOFF_LAUNCH_FAILED`, `RESTORE_FALLBACK_USED`. |
| `titleTextRef` | LocalizedTextRef | Must be localized and readable for elderly-first UI. |
| `bodyTextRef` | LocalizedTextRef | Must explain next step in plain language. |
| `dismissAction` | Enum | Always returns user to stable home state. |
| `relatedTileRef` | String? | Optional for context-specific guidance. |

---

## DiagnosticEvent

**Purpose**: Lightweight launcher-owned troubleshooting hooks.

| Field | Type | Rules |
|------|------|-------|
| `eventType` | Enum | `WHATSAPP_LAUNCH_CONFIRMED`, `WHATSAPP_LAUNCH_FAILED`, `RETURN_RESTORE_SUCCESS`, `RETURN_RESTORE_FALLBACK`, `CONFIG_INVALID_OR_CAPABILITY_FAILED`. |
| `occurredAtEpochMs` | Long | Required. |
| `actionCycleRef` | String? | Included where flow cycle exists. |
| `tileRef` | String? | Launcher-owned tile reference only. |
| `actionType` | ActionType? | Present for action-bound events. |
| `reasonCode` | String? | Stable non-PII reason taxonomy. |

**Privacy constraints**:

- No message content, chat identifiers, typed text, or other unnecessary communication data.

---

## MockConfigurationEntry (feature scope)

**Purpose**: Source-of-truth mock setup for contact tile behavior in this release.

| Field | Type | Rules |
|------|------|-------|
| `tileId` | String | Required and unique. |
| `contactRef` | String | Required. |
| `displayNameKey` | String | Localized resource key reference. |
| `photoRef` | String? | Optional. |
| `capability` | Set<ActionType> | Allowed runtime launch actions for this contact. |

**Constraint**:

- Feature implementation must use mock configuration only (FR-023).
