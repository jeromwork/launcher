# Contract: Communication Flow Diagnostics Events

**contractId**: `launcher.communication.diagnostics`  
**majorVersion**: `1`

## Purpose

Define lightweight launcher-owned diagnostics for troubleshooting handoff and restore failures without heavy background monitoring.

## Event schema (conceptual)

`CommunicationDiagnosticEvent`

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `eventType` | Enum | Yes | Event taxonomy below. |
| `occurredAtEpochMs` | Long | Yes | UTC timestamp. |
| `actionCycleRef` | String? | No | Included for cycle-bound events. |
| `tileRef` | String? | No | Launcher-owned tile id/reference. |
| `actionType` | Enum(`CALL`,`VIDEO`)? | No | Included when action-specific. |
| `reasonCode` | String? | No | Stable low-cardinality code for triage. |

## Event taxonomy

| Event | Trigger |
|------|---------|
| `WHATSAPP_LAUNCH_CONFIRMED` | User confirms action and launch pipeline begins. |
| `WHATSAPP_LAUNCH_FAILED` | Handoff cannot launch or is rejected by policy/runtime. |
| `RETURN_RESTORE_SUCCESS` | Return context restored expected launcher home state. |
| `RETURN_RESTORE_FALLBACK` | Restore used nearest stable home fallback path. |
| `CONFIG_INVALID_OR_CAPABILITY_FAILED` | Configuration/runtime capability invalid for requested action. |

## Rules

1. Emission must occur at launcher-owned control points only.
2. Events are append-only and not used for real-time cross-app surveillance.
3. Reason codes must remain stable and documented for support triage.

## Privacy constraints

Events must exclude:

- message or chat content,
- phone numbers or contact names in clear text when avoidable,
- any payload not required for troubleshooting this feature.
