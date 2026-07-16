# Checklist: preset-readiness

Applied: 2026-07-15
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

Configs touched:
- **Profile** (persisted per-device user config) — inherited from TASK-120, extended with `Component.tags`.
- **pool.json** (bundled build-time template pool) — extended with optional `"tags": [...]` override.

## Wire format

- [x] CHK001 Both Profile + pool.json are JSON wire formats (per TASK-120).
- [x] CHK002 Profile schemaVersion bumped v2→v3 (FR-004). pool.json inherits or bumped (FR-003).
- [x] CHK003 Roundtrip test mandated in SC-003.
- [x] CHK004 Backward-compat: v2 fixture → read → defaults populated (SC-003 + NFR-004).

## Anonymization

- [x] CHK005 No UID / token in Tag enum, Component.tags, or query API.
- [ ] CHK006 PARTIAL — `AppTile` references `packageName` (e.g. `"com.android.settings"`) inherited from TASK-120; NOT introduced by this spec. Rule 9 concern predates this task.
- [x] CHK007 No PII in Tag or Component.tags.
- [x] CHK008 No blob references.
- [ ] CHK009 PARTIAL — AppTile fallback on missing package inherited from TASK-120; not addressed here.

## Adapter pattern

- [x] CHK010 `ProfileStore` / `ConfigSource` port pattern inherited from TASK-120. This spec adds `FlowRepository` binding to `ProfileBackedFlowRepository`.
- [x] CHK011 `BundledSource` pattern inherited from TASK-120.
- [ ] CHK012 PARTIAL — no explicit inline TODO(shareability) mandated in FR list for `ComponentDeclaration.tags` override. Recommend adding.
- [x] CHK013 Callers depend on FlowRepository port (FR-006/FR-007).

## Cross-device contract

- [x] CHK014 v2 → v3 migration writer explicit (FR-004) — v(N-1) Profile accepted on v(N) device.
- [ ] CHK015 PARTIAL — reverse direction (v3 Profile on v2 device) not addressed. Acceptable for on-device-only Profile in MVP (not shared cross-device yet), but recommend documenting.
- [x] CHK016 Tag enum is locale-independent (English constant names, not user-language strings).

## Privacy by design

- [x] CHK017 Tag enum + Component.tags are semantic markers, structurally cannot embed identity.
- [x] CHK018 No telemetry / device fingerprint introduced.

## Acceptance evidence

- [x] CHK019 SC-003 roundtrip + backward-compat mandated.
- [x] CHK020 Uses existing `ProfileStore` port; no new port to add. `FakeProfileStore` implied for tests.

**Result**: 16/20 passed, 4 open items:
- CHK006/CHK009 (packageName + fallback) inherited from TASK-120, not this spec's problem to fix.
- CHK012 recommend inline `TODO(shareability)` for ComponentDeclaration.tags override in plan.
- CHK015 v3→v2 backward reject path — acceptable to defer since Profile is device-local.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Проверили что Profile + pool.json готовы к share/export/re-import по CLAUDE.md rule 9 (portable shareable artifact с day 1). 16/20 pass. Wire-format bump v2→v3 корректный (`schemaVersion` + roundtrip + backward-compat). 3 из 4 open items — унаследованы от TASK-120 или откладываются осознанно.

**Конкретика, которую стоит запомнить:**
- CHK006/CHK009 — `packageName` (например `"com.android.settings"`) внутри `AppTile` — TASK-120 issue, не наша задача fix'ить в TASK-127.
- CHK012 — рекомендация inline `// TODO(shareability): future ConfigSource adapters` на месте `ComponentDeclaration.tags` override — не забыть в plan.
- CHK015 — v3 Profile на v2 device (backward reject) — deferred, поскольку Profile is device-local (не sharing'ится cross-device в MVP).
- Tag enum + `Component.tags` — anonymous by construction (нет UID/PII/token'ов, нельзя случайно вшить identity).

**На что смотреть с осторожностью:**
- Наследованные open items (packageName) должны быть закрыты в TASK-120 до Verification TASK-127, иначе всплывут снова.
- При добавлении shareability адаптеров в будущем — не забыть про `Tag` enum как closed set (unknown tag на другом device'е должен fail-closed, не skip).
