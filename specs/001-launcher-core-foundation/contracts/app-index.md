# Contract: App index (catalog)

**contractId**: `launcher.appindex`  
**majorVersion**: `1` (MVP)

## Owner

- **AppIndex** (`core`)

## Consumer obligations

- Features MUST obtain launchable items only through this contract (spec FR-004, FR-018).

## Published operations (conceptual)

- `snapshot(): CatalogSnapshot` — consistent point-in-time view for UI iteration (immutable).
- `observe(): Flow<CatalogSnapshot>` or callback equivalent — emits when index rebuild completes.
- `refresh(reason: RefreshReason)` — MAY be called from Core only; features request refresh via **project event** handling or shell policy, not direct OS hooks.

## Semantics under permission degradation

- When package visibility or query permissions are restricted, snapshot MUST still be **consistent**: either filtered list with stable empty-state rules, or explicit **degraded** state with reason (no undefined exceptions surfacing to UI).

## Accessibility

- Every **Catalog entry** exposed here MUST carry display and a11y fields per `data-model.md`.
