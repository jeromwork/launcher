# Contract: Profile bootstrap

**contractId**: `launcher.profile`  
**majorVersion**: `1` (MVP)

## Consumer

- **ProfileEngine** (owner, `core`)
- **ModuleRegistry** (reads effective module flags after resolution)

## Obligations

1. Load active profile from bundled assets and/or future persistent store (implementation).
2. Validate `schemaVersion` and required keys; on failure apply **bundled safe default** profile.
3. Emit **ProfileChanged** project-level event (see `project-events.md`) after successful load or fallback.
4. Apply **conflict resolution order** from `data-model.md` when combining profile intent with module/permission/contract state.

## Published surface (conceptual)

- `effectiveProfile(): ProfileSnapshot` — immutable snapshot for the current process generation.
- `profileGeneration: Int` — increments when profile or resolution changes (for UI observation).

## Non-goals (MVP)

- Full JSON schema for all profile fields (per spec non-goals); only **structural** version + module flags + minimal presets required for foundation tests.
