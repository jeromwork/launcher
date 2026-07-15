# Checklist: domain-isolation

Applied: 2026-07-15
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

## Vendor SDKs

- [x] CHK001 No vendor SDK types introduced. All new types (`Tag`, `Profile.query`, `ProfileBackedFlowRepository`) are pure Kotlin.
- [x] CHK002 No new external SDK — reuses existing `FlowRepository` port.
- [x] CHK003 Vendor disappearance: N/A — no vendor.

## Transport types

- [x] CHK004 No transport types in domain. `Profile` / `Component` wire format is domain-owned per TASK-120.
- [x] CHK005 pool.json / Profile JSON serialization stays in `core/preset/wire/` (adapter concern), domain has plain data classes.

## Platform types

- [x] CHK006 NFR-001 explicitly mandates zero Android imports for all new types.
- [x] CHK007 `Tag` is pure enum, no platform-derived data.

## Ports

- [x] CHK008 `FlowRepository` port already exists; `ProfileBackedFlowRepository` is new adapter — no new port introduced.
- [x] CHK009 Port shape (`FlowRepository`) is domain-driven, unchanged.
- [x] CHK010 Fakes available: `FakeProfileStore` implied by NFR-002; test path uses fakes.
- [x] CHK011 Real adapter = `ProfileBackedFlowRepository` bound in DI (FR-007). Note: adapter is in `core/adapters/` — placement OK if it stays commonMain-compatible (pure Kotlin over `ProfileStore` port).
- [x] CHK012 DI wiring split mockBackend + realBackend flavor per FR-007.

## Source-set placement

- [x] CHK013 FR-001 places `Tag` in `core/preset/model/Enums.kt` — commonMain by convention. Query API on `Profile` — same commonMain. Adapter in `core/adapters/` — commonMain if it depends only on `ProfileStore` port.
- [x] CHK014 Default commonMain preserved.

## Existing-code regressions

- [x] CHK015 No vendor type reintroduced.
- [x] CHK016 No new expect/actual — pure Kotlin sufficient.

**Result**: 16/16 passed. Spec is domain-isolation clean by design — additive Kotlin-only types + reuse of existing port.

Note: NFR-001 explicitly cites this checklist as fitness function for enforcement.
