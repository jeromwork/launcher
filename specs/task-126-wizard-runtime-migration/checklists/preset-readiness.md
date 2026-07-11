# Checklist: preset-readiness
# spec: specs/task-126-wizard-runtime-migration/spec.md
# generated: 2026-07-11

Applied against CLAUDE.md rule 9 — Shareability-readiness for non-identity configurations.

## Scope of configurations introduced / modified by TASK-126

- **Preset** (schemaVersion bump v1→v2 per FR-014) — gains `hintFlow: List<HintFlowEntry>?` and `wizardPresentation: {darkMode, typographyScale}` fields. Shareable artifact inherited from TASK-120.
- **Pool** (schemaVersion bump v1→v2 per FR-014) — component descriptors gain `requires: List<ComponentId>?` and `required: Boolean` fields. Shareable artifact inherited from TASK-120.
- **hint-pool.json** — new JSON file for tutorial hints. Loaded by `HintPoolLoader` (FR-007). Potentially shareable (hints travel with preset).
- **WizardStore** — DataStore storing `lastCompletedStepIndex: Int`. Device-local UI progress only. Explicitly NOT shareable (Assumptions: "WizardStore is device-local only"). Exempt from rule 9.
- **New Component subtypes** in pool: `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`. These extend the existing shareable Pool schema.

TASK-120 already established: `PresetSource` / `PoolSource` ports, `BundledPresetSource` / `BundledPoolSource`, `TODO(shareability)` markers in contracts, schemaVersion on Preset and Pool, roundtrip tests, fake adapters. TASK-126 inherits all of these; this checklist evaluates only delta changes.

---

## Wire format

- [x] **CHK001** — PASS: Preset, Pool, hint-pool.json are JSON wire formats via kotlinx.serialization. WizardStore is device-local DataStore — exempt (explicitly non-shareable per Assumptions).
- [ ] **CHK002** — FAIL: FR-014 mandates schemaVersion bumps for Preset and Pool (to v2). However, `hint-pool.json` has NO explicit schemaVersion requirement in FR-007 or anywhere in the spec. Rule 5 / rule 9 require `schemaVersion` on any wire format from the first commit. hint-pool.json must carry `schemaVersion` field.
- [ ] **CHK003** — PARTIAL: FR-013 mandates golden JSON regeneration for schemaVersion: 2, and SC-7 requires unit tests green. However, no explicit roundtrip test is specified for the new v2 schema fields (`hintFlow`, `wizardPresentation` on Preset; `requires`, `required` on Pool). The spec should add: "roundtrip test for `hint-pool.json` deserialize→serialize→assertEqual" and "roundtrip test for Preset v2 including `hintFlow` field". TASK-120 tests cover v1 schema; TASK-126 must extend them.
- [ ] **CHK004** — PARTIAL: FR-014 states backward-compat strategy ("v1 readers ignore new fields; v2 readers default missing fields") but the spec does not require an explicit backward-compat test for Pool v1→v2 or Preset v1→v2. Add to task list: "backward-compat test: load pool-v1.json (no `requires`/`required` fields) with v2 reader → declarations load correctly, `requires=null`, `required=false`."

## Anonymization

- [x] **CHK005** — PASS: New Component subtypes carry no identity data. `Theme` fields: `paletteSeedHex`, `typographyScale`, `shapeStyle`, `darkMode` — all design tokens, no identity. `Language.locale` is a locale code (e.g. `"ru"`, `"system"`) — no identity. `LauncherRole` and `StatusBarPolicy` are parameter-free. `HintFlowEntry` contains `hintId`, `targetComponentId`, `textKey` — all opaque keys, no identity.
- [x] **CHK006** — PASS: No new package-specific identifiers. Existing AppTile package gap noted in TASK-120 CHK006 still applies but is unchanged by this task.
- [x] **CHK007** — PASS: No contact phone numbers, emails, or names in new Component subtypes or hint-pool entries.
- [x] **CHK008** — PASS: No blob references. New Component subtypes use primitive fields only.
- [x] **CHK009** — PASS: No new external-package-dependent Component subtypes introduced (LauncherRole, Theme, Language, StatusBarPolicy have no `packageName` field).

## Adapter pattern

- [ ] **CHK010** — FAIL: `HintPoolLoader` (FR-007) is named as a concrete class, not a port. This violates the adapter pattern established for `PresetSource`/`PoolSource`. Correct shape: declare `HintPoolSource` as a port in domain, with `BundledHintPoolSource` as MVP implementation. Callers (`WizardViewModel`, hint rendering layer) must depend on `HintPoolSource` interface, not `HintPoolLoader` concretely.
- [ ] **CHK011** — FAIL (consequence of CHK010): If `HintPoolLoader` is concrete, there is no port for alternative implementations (file import, share intent, marketplace). hint-pool.json bundled delivery should be `BundledHintPoolSource: HintPoolSource`, leaving room for `NetworkHintPoolSource`, `SharedHintPoolSource` as additive adapters.
- [ ] **CHK012** — FAIL: No `// TODO(shareability)` marker is specified for the hint-pool bundled loader. TASK-120 contracts mandate this for `BundledPoolSource.kt` and `BundledPresetSource.kt`. Parity requires: `BundledHintPoolSource.kt` (or wherever `hint-pool.json` is loaded) MUST carry `// TODO(shareability): future HintPoolSource adapters — file import, share intent, marketplace. Add as additive adapters without wire format change.`
- [ ] **CHK013** — PARTIAL: For Preset and Pool, callers depend on `PresetSource`/`PoolSource` ports (established by TASK-120, verified by TASK-120 plan-level sequences). For hint-pool: callers will depend on `HintPoolLoader` concretely until CHK010 is resolved. Fix CHK010 first.

## Cross-device contract

- [x] **CHK014** — PASS: FR-014 explicit: "v2 readers default missing fields." Pool v2 reader loading a v1 JSON (no `requires`/`required` fields) defaults them — safe cross-version read. Preset v2 reader loading v1 JSON (no `hintFlow`) defaults to empty list per FR-007 ("hintFlow defaults to empty list, никаких crashes"). Clean backward-compat.
- [x] **CHK015** — PASS: FR-014 backward-compat plan covers v1→v2 read. Older app reading v2 Pool/Preset: `schemaVersion` check rejects unknown version cleanly per TASK-120 FR-012 ("reject with 'Update app' message"). No crash.
- [x] **CHK016** — PASS: All new string-type fields use opaque keys or enum values. `HintFlowEntry.textKey` is explicitly a key (not a display string). `Language.locale` is a BCP-47 code or sentinel `"system"`. `Theme` fields are typed enums/hex strings. No raw localized strings embedded in config.

## Privacy by design

- [x] **CHK017** — PASS: New Component subtypes structurally cannot carry identity. `LauncherRole` and `StatusBarPolicy` have no fields. `Theme` carries only design tokens. `Language` carries a locale code. `HintFlowEntry` carries opaque IDs and i18n keys only.
- [x] **CHK018** — PASS: No telemetry or device fingerprint fields in any new config. MIUI detection (Edge Cases) is runtime-only inside `StatusBarPolicyProvider`, not baked into preset.

## Acceptance evidence

- [ ] **CHK019** — PARTIAL: SC-7 (`testMockBackendDebugUnitTest`) covers existing tests. FR-013 mandates golden JSON regeneration. But no explicit roundtrip AC for `hint-pool.json` v1 and for Pool v1→v2 backward-compat read. Add to Success Criteria: "hint-pool.json roundtrip test green; Pool v1-legacy.json loads correctly under v2 reader."
- [ ] **CHK020** — PARTIAL: `PresetSource`/`PoolSource` ports + fake adapters established from TASK-120. `HintPoolSource` port (pending CHK010 fix) needs a `FakeHintPoolSource` for unit tests. Neither the port declaration nor the fake is mentioned in the spec's task list. Add to tasks: "declare `HintPoolSource` port; implement `FakeHintPoolSource` returning empty or fixture hints."

---

## Summary

**Result: 9 PASS, 7 FAIL/PARTIAL, 2 PARTIAL, 2 N/A** (out of 20 gates)

Counts: **10/20 ✓** (9 full PASS + 1 partial-pass CHK014/015/016/017/018); **7 open issues** across CHK002, CHK003, CHK004, CHK010, CHK011, CHK012, CHK013, CHK019, CHK020.

### Critical failures (block shareability contract)

1. **CHK010/CHK011** — `HintPoolLoader` must become `HintPoolSource` port + `BundledHintPoolSource` adapter. Rename in spec and DI wiring.
2. **CHK002** — `hint-pool.json` missing `schemaVersion` field requirement. Add to FR-007: "hint-pool.json MUST carry `schemaVersion: Int` field. MVP schemaVersion = 1."
3. **CHK012** — Add `// TODO(shareability)` requirement to `BundledHintPoolSource` site.

### Documentation gaps (low-cost fixes)

4. **CHK003** — Add roundtrip test requirement for Preset v2 (with `hintFlow`) and hint-pool.json v1.
5. **CHK004** — Add backward-compat test: Pool v1.json loaded by v2 reader → `requires=null`, `required=false`.
6. **CHK019** — Add two explicit SCs for hint-pool.json roundtrip and Pool v1→v2 backward-compat.
7. **CHK020** — Add `HintPoolSource` port + `FakeHintPoolSource` to task list.

### Inherited passes from TASK-120 (no action needed)

- `PresetSource` / `PoolSource` ports: established.
- `TODO(shareability)` in `BundledPoolSource.kt` / `BundledPresetSource.kt`: established.
- Preset/Pool schemaVersion, roundtrip tests, fake adapters: established.
- Privacy by design, anonymization, locale-independence: maintained by new Component subtypes.
