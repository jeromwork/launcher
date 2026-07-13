# Checklist: wire-format

Applied to: `specs/task-126-wizard-runtime-migration/spec.md`

Wire formats in scope (TASK-126 introduces or modifies):
1. **`pool.json`** — schemaVersion bump 1 → 2 (new `requires: List<ComponentId>?` + `required: Boolean` on `ComponentDeclaration`)
2. **`preset/*.json`** — schemaVersion already at 2 (set by TASK-120); TASK-126 adds `hintFlow: List<HintFlowEntry>?` + `wizardPresentation` field (new additive fields)
3. **`hint-pool.json`** — **NEW** wire format introduced by TASK-126 (loaded by `HintPoolLoader`)
4. **`WizardStore`** — **NEW** DataStore (device-local, `lastCompletedStepIndex: Int`)
5. **`Profile`** — schemaVersion 2 (unchanged by TASK-126; `ProfileStore` receives no new fields)

Reference: [CLAUDE.md](../../../CLAUDE.md) rule 5; constitution Article VII §3.

---

## Schema version

- [x] **CHK001** Every wire format carries an explicit `schemaVersion: Int` field from its first commit.
  - `pool.json`: `Pool.CURRENT_SCHEMA_VERSION = 1` → spec FR-014 mandates bump to 2 with `requires` addition.
  - `preset.json`: `Preset.CURRENT_SCHEMA_VERSION = 2` already set; `hintFlow` addition is additive, no version bump required per FR-014 ("v1 readers ignore new fields; v2 readers default missing fields").
  - `hint-pool.json`: FR-007 defines `HintFlowEntry` shape but **does not declare a `schemaVersion` field** for `hint-pool.json` itself. This is a gap — new wire format introduced without version field. **FAIL** (see note below).
  - `WizardStore`: stores a single `lastCompletedStepIndex: Int` scalar; FR-008 does not specify a `schemaVersion` for `WizardStore`. Simple scalar DataStore Preferences key is not a structured wire format, so versioning is N/A as long as the key is namespaced. Mark N/A for `WizardStore`.

- [x] **CHK002** `schemaVersion` field is **read first** during deserialization (so unsupported versions can be detected before parsing the rest).
  - `PresetValidator.validate()` (existing code): checks `preset.schemaVersion > supportedSchemaVersion` before any field-level validation (confirmed in source). ✓
  - `BundledPoolSource` (existing): no explicit pre-check of `schemaVersion` before full `json.decodeFromString<Pool>()`. However kotlinx.serialization reads all fields simultaneously; no early-exit on version. This was flagged as open in TASK-120's wire-format checklist (CHK002). TASK-126 does not close this gap — pool schema bumps to v2 but the schema-peek pattern is still absent.
  - `hint-pool.json`: no pre-check possible since there's no `schemaVersion` field at all (see CHK001 gap).
  - **Partial** — pool deserialization still lacks pre-peek; hint-pool has no version at all.

- [x] **CHK003** Currently-supported `schemaVersion` constant is documented in code (single source of truth).
  - `Pool.CURRENT_SCHEMA_VERSION = 1` (constant exists, in `Pool.kt`). ✓
  - `Preset.CURRENT_SCHEMA_VERSION = 2` (constant exists, in `Preset.kt`). ✓
  - `Profile.CURRENT_SCHEMA_VERSION = 2` (constant exists, in `Profile.kt`). ✓
  - `hint-pool.json`: no constant because no `schemaVersion`. Gap inherits from CHK001.
  - Overall: PASS for existing formats; gap only on `hint-pool.json`.

## Backward compatibility

- [x] **CHK004** Reads of **previous** schema versions remain possible for at least one major release.
  - Pool v1 → v2: FR-014 states "v1 readers ignore new fields; v2 readers default missing `requires` to `null`". kotlinx.serialization defaults handle this automatically. ✓
  - Preset: `hintFlow` is `List<HintFlowEntry>?` nullable with default empty — additive, no breakage. ✓
  - Profile: no changes in TASK-126. ✓
  - `hint-pool.json`: new format, no prior version to be backward-compatible with. N/A for now, but needs `schemaVersion` from commit 1 per rule 5.

- [x] **CHK005** Adding a field is allowed; the deserializer handles missing fields with documented defaults.
  - Pool `ComponentDeclaration.requires: List<ComponentId>? = null` and `required: Boolean = false` — both have Kotlin defaults, kotlinx.serialization fills missing JSON field with default. ✓
  - Preset `hintFlow: List<HintFlowEntry>? = null` (nullable with default). ✓
  - `wizardPresentation` field on Preset: FR-003 defines it as optional field (`no` in contract). ✓

- [x] **CHK006** Renaming or removing a field requires a versioned migration **written before the breaking change ships**.
  - FR-014 explicitly: "Both changes MUST be backward-compatible: v1 readers ignore new fields; v2 readers default missing fields." No renames or removals. ✓
  - FR-017: legacy wizard JSON assets deleted outright — but these are legacy assets with zero production users (D1 decision). Not a persisted wire format that needs migration. ✓

- [x] **CHK007** Migration code is **scoped** — `migrateLegacy(json): Action` style — not branching `if (version == 1) ... else ...` everywhere.
  - No migration writer required for TASK-126 (all changes additive, D1 decision). N/A for this migration. Future v2→v3 migration writer shape is a plan-level concern inherited from TASK-120 CHK007. ✓ (N/A)

## Forward compatibility

- [x] **CHK008** Reading **newer** schema versions is handled gracefully.
  - Pool: `PresetValidator` checks `preset.schemaVersion > supportedSchemaVersion` → `SchemaVersionUnsupported` error. Pool does not have equivalent pre-check; forward-compat behavior for unknown pool schemaVersion is unspecified in TASK-126 spec. **Partial** — pool reader relies on kotlinx.serialization ignoring unknown fields, but no explicit fail-fast for `pool.schemaVersion > CURRENT`.
  - Preset: `PresetValidator.validate()` checks and returns `SchemaVersionUnsupported`. ✓
  - `hint-pool.json`: undefined (no schemaVersion means no forward-compat check possible).

- [x] **CHK009** If discriminator (`kind: "..."`) is open: an unknown value yields `Failure("unknown kind")`, not a crash.
  - Component sealed class uses kotlinx.serialization `@Serializable` + `@SerialName` discriminator `"type"`. Unknown `type` value → `SerializationException` at decode. `PresetValidator` / `ProfileFactory` catches this per Edge Cases: "Preset ссылается на `poolRef` которого нет → ProfileFactory пропускает шаг". ✓
  - New Component subtypes (`LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`) added as new sealed subclasses — additive, does not break existing discriminator handling. ✓

## Tests

- [ ] **CHK010** Roundtrip test exists for every wire-format type: write → read → assertEquals.
  - Pool/Preset/Profile roundtrip: covered by existing `BundledAssetsLoadTest` (validates JSON → model parse) and TASK-120 SC-005 roundtrip test. ✓
  - `hint-pool.json`: **no roundtrip test declared** in spec or tasks.md for TASK-126. FR-007 specifies the format but no test is cited. **FAIL**.
  - `WizardStore` (`lastCompletedStepIndex`): FR-008 specifies behavior but no explicit roundtrip / persist-and-restore unit test is declared in spec. The "Robolectric" test in US-3 tests wizard resume behavior but does not explicitly test DataStore persistence of the index. **Partial**.
  - New Component subtypes: FR-001/FR-002/FR-003/FR-004/FR-005 each cite unit tests (`LauncherRoleProviderTest`, `StatusBarPolicyProviderTest`, etc.) but none explicitly test JSON roundtrip for the new sealed subclasses. **Partial**.

- [ ] **CHK011** Backward-compat test exists: a fixture from previous schema version reads successfully.
  - Pool v1 → v2 backward-compat test: **not declared in TASK-126 spec/tasks**. FR-014 states backward-compatibility requirement but no corresponding test fixture or test is cited. The existing `BundledAssetsLoadTest.poolJson_loadsAndParses()` tests current v1 pool — it does not test reading old v1 JSON under new v2 reader (since pool bumps to v2). **FAIL**.
  - Preset backward-compat (v1 reader ignores `hintFlow`): FR-014 mentions old reader ignores new fields, but no explicit test fixture at `preset-v1-no-hintflow.json` is declared. **FAIL** (same gap as TASK-120 CHK011 for new additions).
  - `hint-pool.json`: no prior version, N/A.

- [x] **CHK012** Test fixtures are **stored as files** in `commonTest/resources/` (not literal strings in test code).
  - Existing pool/preset fixtures: `pool.json` in `app/src/main/assets/preset/pool.json` (also serves as production asset); `simple-launcher-profile-golden.json` in `core/src/androidUnitTest/resources/fixtures/`. Pattern established. ✓
  - `hint-pool.json` fixture: none declared yet (CHK010 gap), but once added should follow the same pattern.
  - For the test gaps flagged in CHK010/CHK011, fixtures must be stored in `core/src/commonTest/resources/fixtures/preset/` per project convention.

## Persistence specifics

- [x] **CHK013** SharedPreferences/DataStore: keys namespaced (`<domain>.<feature>.<key>`), not bare strings.
  - `WizardStore` (FR-008): "separate `WizardStore` (DataStore, device-local) MUST store `lastCompletedStepIndex: Int`". Key naming is not specified in the spec. Implementation MUST namespace the key (e.g. `wizard.progress.lastCompletedStepIndex`) — not a bare `"lastCompletedStepIndex"`. Gap at spec level; must be enforced in plan.md. **Partial** — not explicitly mandated in spec.
  - `ProfileStore` (existing): implemented via DataStore; single blob not per-key fan-out. N/A.

- [x] **CHK014** SQLDelight: every migration script has a corresponding test.
  - N/A: no SQLDelight used. DataStore only.

- [x] **CHK015** If a stored type is **removed** entirely: one-shot cleanup written.
  - `WizardCheckpointStore` is **deleted** per FR-011. FR-011 mandates all callers migrate to `ProfileStore.setPreWizardSnapshot()`. A one-shot cleanup of stale `WizardCheckpointStore` DataStore file should be documented; FR-011 does not specify it. **Partial** — migration of callers is required but stale DataStore file cleanup is not mentioned.

## Deep-link / QR / exported config

- [x] **CHK016** URL/QR payload embeds `schemaVersion` in the path or first JSON field.
  - Preset is the shareable artifact per rule 9; `schemaVersion` is top-level field. ✓ (Shareability adapters are additive future; core format prepared.)
  - `hint-pool.json` is not shareable (bundled only per FR-007). N/A for deep-link.

- [x] **CHK017** Truncated/corrupted payload yields user-facing error, not crash.
  - `PresetValidator` → `PresetValidationException` before wizard starts (Edge Cases). ✓
  - `hint-pool.json` absent: "hintFlow defaults to empty list, никаких crashes" (Edge Cases). ✓

## Contract folder

- [x] **CHK018** If `contracts/` exists: each contract file lists its semantic version, breaking-change policy, and a link to roundtrip test fixture.
  - TASK-120 created `specs/task-120-preset-composition-foundation/contracts/` with `pool.md`, `preset.md`, `profile.md`, `provider-port.md`, `capability-ports.md`. These cover the existing formats. ✓
  - TASK-126 does NOT update the contracts folder for: (a) pool v2 new fields (`requires`, `required`), (b) preset's new `hintFlow` and `wizardPresentation` fields, (c) `hint-pool.json` (no contract file). **Gap** — contract files need updating to reflect TASK-126 additions.

---

## Summary

Total gates: 18. **Passed: 12. Open issues: 6.**

| Gate | Status | Issue |
|------|--------|-------|
| CHK001 | FAIL | `hint-pool.json` has no `schemaVersion` field declared |
| CHK002 | PARTIAL | Pool deserialization has no pre-peek version check; hint-pool has no version |
| CHK010 | FAIL | No roundtrip test for `hint-pool.json`; WizardStore persist-restore not explicitly tested; new Component subtypes lack JSON roundtrip tests |
| CHK011 | FAIL | No backward-compat fixture/test declared for pool v1→v2 or preset hintFlow-absent reads |
| CHK013 | PARTIAL | `WizardStore` DataStore key naming not specified — bare string risk |
| CHK015 | PARTIAL | `WizardCheckpointStore` deletion lacks stale-DataStore-file cleanup step |
| CHK018 | PARTIAL | TASK-120 contracts folder not updated for pool v2 fields, `hintFlow`, `hint-pool.json` |

**Blockers (must fix before Phase 1 ships)**:
- CHK001: Add `schemaVersion: Int` to `hint-pool.json` schema in FR-007 or contracts.
- CHK010 + CHK011: Add backward-compat fixtures (pool-v1-legacy.json, preset-v2-no-hintflow.json) and tests in plan.md.

**Plan-level items (carry to plan.md)**:
- CHK002: Pool schema version pre-peek in `BundledPoolSource`.
- CHK013: Specify `WizardStore` DataStore key naming convention.
- CHK015: One-shot cleanup of stale `WizardCheckpointStore` DataStore file.
- CHK018: Update `specs/task-120-preset-composition-foundation/contracts/pool.md` and `preset.md` with TASK-126 additions; create `hint-pool.md` contract stub.
