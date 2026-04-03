# Contract: WhatsApp Handoff

**contractId**: `launcher.communication.whatsapp.handoff`  
**majorVersion**: `1`

## Purpose

Define launcher-owned behavior from confirmation decision to external app transition, including policy failures that must remain in Launcher.

## Request model (conceptual)

`WhatsappHandoffRequest`

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `tileId` | String | Yes | Initiating launcher tile reference. |
| `contactRef` | String | Yes | Mock-configured contact target id. |
| `actionType` | Enum(`CALL`,`VIDEO`) | Yes | Visible action only. |
| `actionCycleId` | String | Yes | Unique cycle id for dedupe and return linkage. |
| `homeSurfaceRef` | String | Yes | Captured before external transition. |

## Response model (conceptual)

`WhatsappHandoffResult`

| Value | Meaning |
|------|---------|
| `LAUNCH_STARTED` | External transition triggered successfully. |
| `REJECTED_DUPLICATE_CYCLE` | Duplicate attempt during active cycle ignored. |
| `WHATSAPP_UNAVAILABLE` | Required app not launchable on device. |
| `ACTION_NOT_SUPPORTED` | Contact/action pairing currently invalid. |
| `LAUNCH_FAILED` | Launch attempted but failed due to policy/runtime error. |

## Rules

1. `LAUNCH_STARTED` is allowed only after explicit user confirmation.
2. On non-success result, Launcher remains foreground and shows warning state.
3. Runtime validation checks must execute before leaving Launcher:
   - package availability,
   - contact/action capability validity,
   - duplicate action-cycle overlap.
4. Contract must not expose alternative hidden actions beyond `CALL` and `VIDEO`.
5. Result reason codes must be stable for diagnostics and support triage.

## Privacy/Security

- Request and result payloads exclude message content and chat content.
- Contact reference is launcher-owned identifier, not exported chat transcript data.
