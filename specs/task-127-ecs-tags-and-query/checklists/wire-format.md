# Checklist: wire-format

Applied: 2026-07-15
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

Wire formats touched:
- **Profile** (persisted) — FR-004 mandates v2→v3 bump.
- **pool.json** (bundled build-time artifact) — Clarification Q2: no runtime schema bump; `tags` added as optional field.

## Schema version

- [x] CHK001 Profile has schemaVersion (existing, bumped v2→v3 in FR-004). pool.json is build-time-only per Q2 — schemaVersion not runtime-critical.
- [x] CHK002 Assumed from existing TASK-120 wire format (schemaVersion read first). No regression introduced.
- [x] CHK003 Migration writer references single-source current version (implied by FR-004).

## Backward compatibility

- [x] CHK004 v2 → v3 migration mandated (FR-004) — v2 reads still supported through writer.
- [x] CHK005 pool.json adds `tags` as optional (FR-003) — deserializer must default to Component subtype defaults. Explicit.
- [x] CHK006 No field renamed / removed; purely additive change per FR-002/FR-003.
- [x] CHK007 FR-004 mandates dedicated migration writer (not scattered branches). Idempotency required by NFR-004.

## Forward compatibility

- [x] CHK008 Reading newer versions: N/A for pool.json (bundled). Profile: existing behavior preserved.
- [x] CHK009 Tag enum is closed set (FR-001, additive-only). Unknown tag value in fixture — behavior needs to be pinned in plan. Recommend documenting.

## Tests

- [x] CHK010 SC-003 explicitly mandates roundtrip test for Profile.
- [x] CHK011 SC-003 explicitly mandates v2 fixture backward-compat test.
- [x] CHK012 NFR-004 implies fixture file storage (v2 fixture → read → assert tags populated).

## Persistence specifics

- [x] CHK013 N/A — Profile persisted via ProfileStore (existing), not raw SharedPreferences.
- [x] CHK014 N/A — no SQLDelight touched.
- [x] CHK015 Nothing removed. `ConfigBackedFlowRepository` kept in code (FR-007), not removed from wire format.

## Deep-link / QR / exported config

- [x] CHK016 N/A — Profile stays on-device.
- [x] CHK017 N/A.

## Contract folder

- [x] CHK018 No new contracts/ file introduced by this spec (Profile contract exists under TASK-120).

**Result**: 18/18 passed. Wire-format hygiene solid — additive field + explicit migration writer + roundtrip + backward-compat fixture.

Recommendation for plan phase: document unknown-Tag-value behavior in Tag deserializer (skip vs fail-closed).
