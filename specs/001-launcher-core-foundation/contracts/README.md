# Contracts: Launcher Core Foundation

Architectural contracts between **`core`** and **`feature-*`** / **`app`** shell. These are **specifications for implementers**; runtime types live in `core` (published API package) per plan.

| Document | Purpose |
|----------|---------|
| [profile-bootstrap.md](./profile-bootstrap.md) | Profile load, validation, fallback, versioning |
| [project-events.md](./project-events.md) | Project-level event taxonomy (post–SystemEventBridge) |
| [app-index.md](./app-index.md) | Read-only catalog query surface |
| [actions.md](./actions.md) | Action request and dispatch pipeline |
| [module-registration.md](./module-registration.md) | Module descriptor and registry behavior |

**Compatibility**: Each contract doc states `contractId` and **majorVersion** expectations for MVP.

**Platform API assumptions**: If a contract’s behavior depends on specific Android API levels, document them in that contract file (see **spec.md** FR-037, FR-010).
