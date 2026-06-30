# Checklist: wire-format — TASK-65 (re-run after revised model)

Applied: 2026-06-30 (2nd pass). Wire formats: **`preset.json` v1** (NEW), **`wizard.manifest` bump** (appFamilyId removal), **per-pool schemaVersion**, **ProfileStore DataStore** (persists across app versions + syncs encrypted to server).

## Schema version

- [x] CHK001 Explicit schemaVersion from first commit — **yes**. FR-001 (preset.json), per-pool (FR-005), ProfileStore — **NEW concern**: spec does not explicitly mention `ProfileStore.schemaVersion`. **Surface to plan**: добавить — ProfileStore sync to server (FR-018) делает его wire format.
- [ ] CHK002 schemaVersion read first during deserialization — **defer to plan**.
- [ ] CHK003 Const single-source — **defer to plan**.

## Backward compatibility

- [x] CHK004 Read previous versions — **yes**.
- [x] CHK005 Add field with default — **yes**.
- [x] CHK006 Rename/remove → migration before — **yes**, FR-002 explicit для appFamilyId.
- [ ] CHK007 Scoped migration code — **defer to plan**.

## Forward compatibility

- [x] CHK008 Newer schemaVersion graceful — **yes**, ConfigSource.IncompatibleVersion result.
- [x] CHK009 Unknown discriminator → Failure — **yes**, Article VII §15 Indeterminate.

## Tests

- [x] CHK010 Roundtrip test — **partial**. FR-027 PoolSource roundtrip. **Preset wire format roundtrip — NEW gap**: с composite PresetRef нужен specific test (`PresetRef.fromJson(preset.serialize()) == preset.ref`). **Surface to plan**.
- [ ] CHK011 Backward-compat read test — **defer to plan**. **Specifically**: read pre-TASK-65 wizard.manifest with `appFamilyId` field + assert migration produces valid post-TASK-65 state.
- [x] CHK012 Fixtures as files — **yes**.

## Persistence specifics

- [ ] CHK013 DataStore keys namespaced — **defer to plan**. **NEW concern**: ProfileStore Map<PresetRef, ProfileData> serialization — как сериализовать PresetRef как key? Protobuf nested message или composite string `"uid::version"`? **Surface to plan critical decision**.
- [x] CHK014 SQLDelight N/A.
- [x] CHK015 Removed types cleanup — **partial**. appFamilyId removal — migration writer. ProfileSnapshot existing — decision в plan.

## Deep-link / QR

- [x] CHK016-017 N/A (sharing UI deferred).

## Contract folder

- [ ] CHK018 contracts/ — **partial**. `contracts/pool-naming.md` (FR-028). **NEW need**: `contracts/preset-wire-format.md` + `contracts/profile-store-format.md` (because syncs to server). **Surface to plan**.

---

**Total**: 9/14 ✓, 5 «surface to plan»
**Red-only summary**: wire-format: 9/14 ✓, FAIL: CHK002 (read order), CHK003 (const single-source), CHK007 (migration scoping), CHK010 (preset roundtrip + PresetRef serialization test), CHK011 (backward-compat fixture pre-TASK-65), CHK013 (ProfileStore DataStore key serialization для composite PresetRef — **critical decision**), CHK018 (preset-wire-format.md + profile-store-format.md missing). Все defer to plan, не блокеры spec.
