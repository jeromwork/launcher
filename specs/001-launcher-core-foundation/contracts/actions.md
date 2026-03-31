# Contract: Actions (dispatch)

**contractId**: `launcher.actions`  
**majorVersion**: `1` (MVP)

## Owner

- **ActionDispatcher** (`core`)

## Purpose

Single pipeline for **OpenApplication**, **OpenSystemSettings** (as needed), and internal **navigation commands** so features do not construct competing launch paths (spec FR-005).

## Published API (conceptual)

- `dispatch(request: ActionRequest): DispatchResult`
- Sealed `DispatchResult`: `Ok`, `BlockedByPolicy`, `Failure(reason)`

## Rules

1. Validate `ActionRequest` against current **AppIndex** snapshot or policy before starting activities.
2. Log or surface **safe** user-visible errors only through shell policy (copy not in Core Foundation scope).
3. Denied permissions: return `BlockedByPolicy` with stable reason code for testing.

## Security note

- No implicit **implicit intents** to arbitrary URIs from feature-provided strings without validation layer (future hardening); MVP allows only catalog-bound and enumerated system settings targets.
