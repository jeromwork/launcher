# Platform events (MVP)

Authoritative listener and routing story for **Launcher Core Foundation**. **Change [research.md](./research.md) §6 first**, then mirror updates here so reviewers have a single operational view.

## Ownership

| Stage | Component | Responsibility |
|-------|-----------|----------------|
| Intake | `SystemEventBridge` | Register OS broadcasts, normalize to `ProjectEvent.PackageSetChanged` |
| Route | `EventRouter` | Fan-out; coalesce burst `PackageSetChanged` (≤ 200 ms debounce, see implementation) |
| Consumers | `AppIndex`, shell, future `feature-*` | Subscribe only via `EventRouter.events`; no raw package listeners in features |

## MVP listeners (from research §6)

| Signal (conceptual) | Source | Frequency | Thread | Power note | Fallback |
|---------------------|--------|-----------|--------|------------|----------|
| Package set changes | `ACTION_PACKAGE_*` / `ACTION_MY_PACKAGE_REPLACED` as applicable | Low (user installs/updates) | Main receiver → Core hands off to catalog refresh work | Event-driven, no polling | If delivery delayed, **AppIndex** refreshes on next cold start or when Home resumes |
| Timezone / locale (optional) | `ACTION_*` if catalog labels depend on it | Rare | Main → debounced | Low | Skip in absolute MVP if not needed |

## Explicit non-goals (this milestone)

- **`BOOT_COMPLETED`**: not registered for Foundation unless a later spec proves cold-start catalog is insufficient (see research §6).
- **Polling `PackageManager`**: rejected; catalog refresh is event- or lifecycle-driven.

## Related contracts and code

- [contracts/project-events.md](./contracts/project-events.md) — event types and subscriber rules.
- `com.launcher.core.bridge.SystemEventBridge` — broadcast registration.
- `com.launcher.core.events.EventRouter` — debounce and merge.
