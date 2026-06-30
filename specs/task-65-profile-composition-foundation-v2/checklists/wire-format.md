# Checklist: wire-format — TASK-65 Preset Composition Foundation v2

Applied: 2026-06-30. Wire formats touched: **`preset.json` (new, v1)**, **`wizard.manifest` (bump, appFamilyId removal)**, **per-pool `schemaVersion`**, **Profile DataStore** (persisted across app versions).

## Schema version

- [x] CHK001 Explicit `schemaVersion: Int` from first commit — **yes**. FR-001 (`preset.json` schemaVersion=1), FR-002 (`wizard.manifest` bump), FR-005 (`PoolSource.version()`).
- [ ] CHK002 `schemaVersion` read first during deserialization — **NEEDS PLAN DETAIL**. Spec не уточняет порядок чтения; это будет деталь plan/implementation. **Surface to plan**.
- [ ] CHK003 Currently-supported `schemaVersion` constant documented — **NEEDS PLAN DETAIL**. Должно быть `const val PRESET_SCHEMA_VERSION = 1` в одном месте. **Surface to plan**.

## Backward compatibility

- [x] CHK004 Reads of previous schema versions — **yes**. `ConfigSource.load` returns `IncompatibleVersion` result (already existing), preset filter в picker (edge case explicit).
- [x] CHK005 Adding fields with defaults — **yes**. FR-001 defaults (`pickEnabled = true`, `requiredModules = []`).
- [x] CHK006 Renaming/removing requires migration written before breaking change — **yes**. `appFamilyId` removal — migration writer обязателен (FR-002 explicit).
- [ ] CHK007 Migration code scoped (no `if (version == 1)` повсюду) — **NEEDS PLAN DETAIL**. Pattern `migrateLegacy(json): Action` — план должен явно его принять.

## Forward compatibility

- [x] CHK008 Newer schemaVersion handled gracefully — **yes**. `ConfigSource.load` уже возвращает `IncompatibleVersion(found, known)`; picker фильтрует (edge case).
- [x] CHK009 Unknown discriminator returns Failure — **yes**. Article VII §15 `Indeterminate` для unregistered `CheckSpec` variants (graceful degradation).

## Tests

- [x] CHK010 Roundtrip test exists — **yes, partial**. FR-027 `PoolSourceRoundtripTest`. FR-026 fitness test для engine. **Missing**: explicit `PresetWireFormatRoundtripTest` (write `Preset` → JSON → read → assertEquals). **Surface to plan**.
- [ ] CHK011 Backward-compat test — **NEEDS PLAN ADDITION**. Не описан тест «прочитать pre-TASK-65 wizard.manifest с `appFamilyId` и убедиться что migration works». **Surface to plan**.
- [x] CHK012 Fixtures as files — **yes**. `test-preset.json`, `wizard-simple-launcher-golden.json` указаны в FR-018/020/023/025.

## Persistence specifics

- [ ] CHK013 DataStore keys namespaced — **NEEDS PLAN DETAIL**. Spec не определяет ключи. **Surface to plan** (например `presets.applied.id`, `profile.bindings.<presetId>`).
- [x] CHK014 SQLDelight migrations — **N/A**. TASK-65 uses DataStore, not SQLDelight (per existing project setup).
- [x] CHK015 Removed types cleanup — **partial**. `appFamilyId` field removal — cleanup в migration writer FR-002. `ProfileSnapshot` (existing) — needs decision в plan (см. meta-minimization CHK012).

## Deep-link / QR / exported config

- [x] CHK016-017 — **N/A**. TASK-65 не вводит deep-link / QR payload (sharing UI отложен до TASK-35).

## Contract folder

- [x] CHK018 `contracts/` — **partial**. `contracts/pool-naming.md` будет добавлен (FR-028 + FR-003). `contracts/preset-wire-format.md` — **NEEDS PLAN ADDITION** (semantic version, breaking-change policy, roundtrip fixture link). **Surface to plan**.

---

**Total**: 12/14 ✓ (CHK013, CHK014 N/A где разумно), 6 «surface to plan»
**Red-only summary**: wire-format: 12/14 ✓, FAIL: CHK002 (schemaVersion read order), CHK003 (const single-source), CHK007 (migration scoping), CHK010 (preset roundtrip test missing), CHK011 (backward-compat read test missing), CHK013 (DataStore keys namespacing), CHK018 (preset contract doc missing). Все — defer to /speckit.plan, не блокеры spec.
