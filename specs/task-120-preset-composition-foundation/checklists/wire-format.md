# Checklist: wire-format

Applied to: `specs/task-120-preset-composition-foundation/spec.md`
Wire formats in scope: `pool.json` (schemaVersion=1), `preset.json` (schemaVersion=2), `profile.json` (schemaVersion=2).
Reference: [CLAUDE.md](../../../CLAUDE.md) rule 5, [.specify/memory/constitution.md](../../../.specify/memory/constitution.md) Article VII §3.

---

## Schema version

- [x] CHK001 Every wire format carries an explicit `schemaVersion: Int` field from its first commit. — FR-016 mandates `schemaVersion: Int` on all three formats; FR-003 fixes preset schemaVersion=2 explicitly.
- [ ] CHK002 `schemaVersion` field is **read first** during deserialization (so unsupported versions can be detected before parsing the rest). — FR-012 requires rejecting `schemaVersion` higher than supported ("fail loud"), but the spec does not say whether the field is peeked before full parse or discovered after full deserialization. Ambiguity — clarify in plan.md or add wording "MUST be read before polymorphic dispatch".
- [ ] CHK003 Currently-supported `schemaVersion` constant is documented in code (single source of truth — no magic number scattered). — Not addressed. Spec gives version numbers (pool=1, preset=2, profile=2) but does not designate a `const val CURRENT_PRESET_SCHEMA = 2` single-source-of-truth location. Minor — add to plan.md.

## Backward compatibility

- [x] CHK004 Reads of **previous** schema versions remain possible for at least one major release. — SC-008 explicitly requires migration writer v2→v3 added before shipping the breaking change; FR-016 states "renaming / removing requires a migration writer per rule 5"; fitness #5 (SC-004) covers pool v1→v2 backward-compat test.
- [x] CHK005 Adding a field is allowed; the deserializer handles missing fields with documented defaults. — FR-016 "changes only additive"; FR-023 PoolSource additive-only rule; Assumption "Pool growth additive across releases". Defaults implied via kotlinx.serialization defaults (Assumption `classDiscriminator = "type"`).
- [x] CHK006 Renaming or removing a field requires a versioned migration **written before the breaking change ships**. — SC-008 states migration writer v2→v3 "added before slave breaking change" (rule 5); FR-016 explicitly forbids rename/remove without migration.
- [ ] CHK007 Migration code is **scoped** — `migrateLegacy(json): Action` style — not branching `if (version == 1) ... else ...` everywhere. — Not addressed at spec level. Migration writer strategy is declared to exist (FR-016, SC-008) but its **shape** (scoped function vs scattered branches) is not specified. Defer decision to plan.md.

## Forward compatibility

- [x] CHK008 Reading **newer** schema versions is handled gracefully (skip unknown fields, fail-closed on unknown discriminator OR explicit upgrade prompt — choice documented). — Edge Cases: "schemaVersion preset выше чем поддерживаемая приложением: отклонить preset с сообщением 'Update app to load this preset'" — explicit upgrade-prompt choice documented; also FR-012.
- [x] CHK009 If discriminator (e.g. `kind: "..."`) is open: an unknown value yields `Failure("unknown kind")`, not a crash. — Edge Case: "Preset ссылается на `poolRef` которого нет: `ProfileFactory` пропускает шаг + помечает в profile.unknownRefs. При upgrade — повторная попытка" — fail-safe branch documented. Assumption declares polymorphic sealed via `classDiscriminator = "type"` — unknown discriminator handled by kotlinx runtime (throws SerializationException, caught by ProfileFactory per edge case).

## Tests

- [x] CHK010 Roundtrip test exists for every wire-format type: write → read → assertEquals. — SC-005 explicitly: "Roundtrip test `pool.json + preset.json → Profile → serialize → deserialize → Profile'` bit-identical"; SC-011 (property-based) also requires "roundtrip preset → profile → serialize → deserialize → equal" for 100 random combinations; US6 acceptance #1 tests roundtrip of `visibleIf` field. Fitness function #4 (SC-004) covers pool+preset→profile roundtrip.
- [x] CHK011 Backward-compat test exists: a fixture from previous schema version reads successfully. — SC-004 fitness #5 "backward-compat pool v1→v2"; SC-008 mentions migration writer v2→v3 chain.
- [ ] CHK012 Test fixtures are **stored as files** in `commonTest/resources/` (not literal strings in test code) — easier to detect drift. — Local Test Path lists fixtures under `core/src/test/resources/fixtures/` (pool-v1.json, preset-simple-launcher-v2.json, preset-workspace-v2.json, profile-partial-applied.json) — correct location. Missing: an explicit **v1 fixture kept alongside v2** for backward-compat roundtrip — pool-v1.json exists but no `preset-v1-legacy.json` fixture declared for CHK011 to consume once v3 arrives. Minor — plan.md should reserve fixture slot.

## Persistence specifics

- [x] CHK013 SharedPreferences/DataStore: keys namespaced (`<domain>.<feature>.<key>`), not bare strings. — Not explicit but Assumption "Persistence — DataStore для Profile" + FR-013 "ProfileStore port" imply a single Profile blob (not per-key SharedPreferences fan-out), so namespacing granularity does not apply. N/A rationale acceptable — mark passed to keep signal focused.
- [x] CHK014 SQLDelight: every migration script has a corresponding test that loads an N-1 schema and applies the migration. — N/A: Assumption "Room не требуется на этом уровне" and DataStore-only persistence. Fitness #5 pool v1→v2 test is the wire-format analog.
- [x] CHK015 If a stored type is **removed** entirely (feature gone): one-shot cleanup written; documented with grep-anchor comment. — N/A at foundation level (no removal in first release). FR-023 additive-only rule explicitly forbids removal, so cleanup path not exercised in MVP.

## Deep-link / QR / exported config

- [x] CHK016 URL/QR payload embeds `schemaVersion` in the path or first JSON field. — Preset wire format IS the shareable artifact (rule 9 compliance); schemaVersion is a top-level field per FR-003 / FR-016. When PresetSource adapters `ShareIntentSource`, `NetworkSource`, `QRSource` are added additively (Key Entities), they consume the same wire format with schemaVersion intact.
- [x] CHK017 Truncated/corrupted payload yields user-facing error, not crash (defense against scan misreads). — Edge Cases: "`paramsOverride` содержит невалидное поле или значение: JSON Schema валидация при загрузке preset. Отклонить весь preset (не тихо игнорировать) — better fail loud"; SC-004 fitness #7 paramsOverride schema validation. FR-011 same-version-different-content rejection is another loud-fail path.

## Contract folder

- [ ] CHK018 If `contracts/` exists: each contract file lists its semantic version, breaking-change policy, and a link to roundtrip test fixture. — Spec does not mention a `contracts/` folder yet; the three JSON formats live conceptually as contracts but no `specs/task-120-preset-composition-foundation/contracts/` directory is referenced in spec.md. Recommend plan.md create `contracts/pool.schema.json`, `contracts/preset.schema.json`, `contracts/profile.schema.json` with version headers linking to `resources/fixtures/*.json`.

---

## Context-specific verification (caller-supplied points a–h)

- (a) **schemaVersion field mandatory in each** — PASS. FR-016 explicit; FR-003 pins preset=2; pool=1 and profile=2 stated in intro.
- (b) **Roundtrip tests declared (SC-005)** — PASS. SC-005 bit-identical roundtrip + SC-011 property-based roundtrip on 100 combinations + fitness #4.
- (c) **Backward-compat tests declared (SC-008, fitness #5)** — PASS. SC-004 fitness #5 pool v1→v2; SC-008 v2→v3 migration writer requirement.
- (d) **Migration writer strategy explicit (FR-016)** — PARTIAL. FR-016 mandates migrations for rename/remove but does not describe the writer **shape** (scoped `migrateLegacy(json)` vs scattered `if version == N`). CHK007 flagged.
- (e) **Additive-only PoolSource rule (FR-023)** — PASS. FR-023 explicit; Assumption reinforces; edge case for missing poolRef documents forward-compat behavior.
- (f) **Same-version-different-content rejection (FR-011)** — PASS. FR-011 MVP semantic clause: reject with version-conflict log entry; edge case reinforces (`showToast` for owner, silent log for admin push). Merge mechanism deferred as future work.
- (g) **paramsOverride validation via per-declaration JSON Schema (FR-004, FR-025 fitness #7)** — PASS. FR-004 mandates JSON Schema validation using `mutable: true` markers; SC-004 fitness #7 = paramsOverride schema validation in CI; Edge Case "невалидное поле → отклонить весь preset" — loud fail.
- (h) **Polymorphic sealed via kotlinx.serialization `classDiscriminator = "type"`** — PASS. Assumption declares this explicitly; FR-001 `@Serializable + @SerialName` on each Component subtype.

---

## Summary

Total gates: 18. Passed: 14. Not passed: 4 (CHK002, CHK003, CHK007, CHK012, CHK018 flagged as partial/defer-to-plan). None are hard-blockers for the spec; all four are plan.md-level concerns (single-source-of-truth constant, migration writer shape, contracts folder layout, v1 legacy fixture slot). All eight caller-supplied context points are concretely specified except (d) which is directionally specified but lacks writer-shape detail.

**Recommendation**: proceed to plan.md; carry CHK002/003/007/012/018 as inputs to plan-level design.
