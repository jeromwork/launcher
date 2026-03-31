# Contract: Project-level events

**contractId**: `launcher.events`  
**majorVersion**: `1` (MVP)

## Producer

- **SystemEventBridge** → normalizes OS signals  
- **EventRouter** → fan-out to subscribers  
- **ProfileEngine** / **ModuleRegistry** → may emit `ProfileChanged`, `ModuleGraphChanged`

## Subscriber rules

- **`feature-*` modules** and **`app` shell** MUST subscribe only through **EventRouter** API.
- Raw `BroadcastReceiver` / `PackageManager` listeners in feature code are **forbidden** (spec + constitution).

## MVP event types (extensible sealed set)

| Type | When emitted | Payload summary |
|------|----------------|-----------------|
| `PackageSetChanged` | After package add/remove/update affecting launcher visibility | Empty or reason enum; consumers re-query **AppIndex** |
| `ProfileChanged` | After effective profile or fallback switch | New `profileGeneration` |
| `ModuleGraphChanged` | After module registration or degradation | List of `moduleId` affected |

## Delivery semantics

- **Main-safe** observation: subscribers receive on Main unless documented otherwise.
- **Coalescing**: EventRouter MAY coalesce burst `PackageSetChanged` within a short debounce window (document constant in implementation; target ≤ 200 ms debounce for UX responsiveness, not polling).

## Battery / threading documentation (MVP listener)

See `plan.md` → SystemEventBridge table; each new event type requires the same fields before merge.
