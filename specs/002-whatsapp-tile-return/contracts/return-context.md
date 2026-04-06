# Contract: Return Context and Restore

**contractId**: `launcher.communication.return-context`  
**majorVersion**: `1`

## Purpose

Define minimal persisted context required for restoring launcher home continuity after returning from external messenger handoff.

## Stored record

`ReturnContextRecord`

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `schemaVersion` | Int | Yes | For backward-compatible parsing. |
| `initiatingTileRef` | String | Yes | FR-011 required field. |
| `homeSurfaceRef` | String | Yes | FR-011 required field. |
| `actionCycleRef` | String | Yes | FR-011 required field. |
| `savedAtEpochMs` | Long | Yes | For stale-context handling. |

## Lifecycle rules

1. At most one active context record is allowed.
2. New confirmed handoff replaces any existing record.
3. Context is consumed on restore attempt and then cleared or replaced.
4. If exact restore cannot be applied, contract must return fallback outcome and stable reason.

## Restore API (conceptual)

- `saveContext(record): SaveResult`
- `loadActiveContext(): ReturnContextRecord?`
- `applyRestore(record): RestoreOutcome`
- `clearContext(actionCycleRef?): ClearResult`

## Restore outcomes

| Outcome | Meaning |
|--------|---------|
| `RESTORED_EXACT_HOME` | Saved home surface restored as expected. |
| `RESTORED_NEAREST_STABLE_HOME` | Nearest stable home used due to missing/changed source state. |
| `NO_VALID_CONTEXT` | No valid active context. |

## Prohibited data

The record must not include:

- message text,
- chat history,
- typed but unsent text,
- unnecessary personal communication data.
