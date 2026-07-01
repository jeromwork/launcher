# Checklist: wire-format — TASK-65 (re-run after revised model)

Applied: 2026-06-30 (2nd pass). Wire formats: **`preset.json` v1** (NEW), **`wizard.manifest` bump** (appFamilyId removal), **per-pool schemaVersion**, **ProfileStore DataStore** (persists across app versions + syncs encrypted to server).

## Schema version

- [x] CHK001 Explicit schemaVersion from first commit — **yes**. FR-001 (preset.json), per-pool (FR-005), ProfileStore — **NEW concern**: spec does not explicitly mention `ProfileStore.schemaVersion`. **Surface to plan**: добавить — ProfileStore sync to server (FR-018) делает его wire format.
- [x] CHK002 schemaVersion read first during deserialization — ConfigParser.parsePreset reads schemaVersion before body decode, returns IncompatibleVersion if > PRESET_SCHEMA_VERSION; migrateLegacyWizardManifest reads version before applying migration.
- [x] CHK003 Const single-source — PRESET_SCHEMA_VERSION (Preset.kt), PROFILE_STORE_SCHEMA_VERSION (ProfileStoreState.kt), POOL_VERSION (HardcodedPoolSource.kt) — each declared once as `const val`.

## Backward compatibility

- [x] CHK004 Read previous versions — **yes**.
- [x] CHK005 Add field with default — **yes**.
- [x] CHK006 Rename/remove → migration before — **yes**, FR-002 explicit для appFamilyId.
- [x] CHK007 Scoped migration code — migrateLegacyWizardManifest lives in dedicated WizardManifestMigration.kt (not scattered across parser); PreferencesProfileStore.maybeMigrateLegacy is a single private helper.

## Forward compatibility

- [x] CHK008 Newer schemaVersion graceful — **yes**, ConfigSource.IncompatibleVersion result.
- [x] CHK009 Unknown discriminator → Failure — **yes**, Article VII §15 Indeterminate.

## Tests

- [x] CHK010 Roundtrip test — **partial**. FR-027 PoolSource roundtrip. **Preset wire format roundtrip — NEW gap**: с composite PresetRef нужен specific test (`PresetRef.fromJson(preset.serialize()) == preset.ref`). **Surface to plan**.
- [x] CHK011 Backward-compat read test — WizardManifestBackwardCompatTest reads pre-TASK-65 fixture (androidUnitTest/assets/wizard-manifests/legacy-with-app-family-id.json) through the migrator + parser and asserts schemaVersion=2 + null appFamilyId. Idempotent-on-v2 case also covered.
- [x] CHK012 Fixtures as files — **yes**.

## Persistence specifics

- [x] CHK013 DataStore keys namespaced — resolved by decision R3: composite string `"uid::version"` via PresetRef.toCompositeKey()/parseCompositeKey() with reserved-separator validation. Single DataStore key `profile.store.json` holds the whole state blob. Covered by ProfileStoreSerializationTest + PreferencesProfileStoreTest.
- [x] CHK014 SQLDelight N/A.
- [x] CHK015 Removed types cleanup — **partial**. appFamilyId removal — migration writer. ProfileSnapshot existing — decision в plan.

## Deep-link / QR

- [x] CHK016-017 N/A (sharing UI deferred).

## Contract folder

- [x] CHK018 contracts/ — all three shipped in specs/task-65-profile-composition-foundation-v2/contracts/: pool-naming.md, preset-wire-format.md, profile-store-format.md.

---

**Total**: 9/14 ✓, 5 «surface to plan»
**Red-only summary**: wire-format: 9/14 ✓, FAIL: CHK002 (read order), CHK003 (const single-source), CHK007 (migration scoping), CHK010 (preset roundtrip + PresetRef serialization test), CHK011 (backward-compat fixture pre-TASK-65), CHK013 (ProfileStore DataStore key serialization для composite PresetRef — **critical decision**), CHK018 (preset-wire-format.md + profile-store-format.md missing). Все defer to plan, не блокеры spec.
