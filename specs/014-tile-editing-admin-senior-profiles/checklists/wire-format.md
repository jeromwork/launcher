# Wire Format — spec 014

Generated: 2026-05-29.

Wire formats touched/extended by F-014:
1. **ConfigDocument** (existing, спека 008) — extended with named-config fields в F-014.1.
2. **Local DataStore** (F-014.0) — для named configs persistence на устройстве.
3. **Firestore documents** (F-014.1) — `/admin-self-configs/{adminUid}/configs/{configName}/current`.

## Schema version

- [⚠️] **CHK001** `schemaVersion` для ConfigDocument existing (спека 008). §Extends section явно говорит "schemaVersion bump → 2 для named-config полей". OK для ConfigDocument. **Local DataStore** wire-format не упомянут с `schemaVersion` явно — improvement для plan.md.
- [⚠️] **CHK002** Read-first behavior — implied для ConfigDocument через спека 008. F-014 не меняет.
- [⚠️] **CHK003** Currently-supported version constant — должен быть в коде. Plan.md должен specify const location.

## Backward compatibility

- [x] **CHK004** §Extends явно: "Backward-compat read v1 (plain) сохраняется". PASS.
- [x] **CHK005** Adding fields — F-014.1 добавляет `configName`, `description`, `isDefault`, `activeDeviceIds`, `orphanedAt`. Defaults documented: legacy v1 reads as ConfigDocument с `isDefault=true` implicit (single config, no name) — implied per progressive disclosure FR-003d State 0/1.
- [x] **CHK006** Никаких renames/removes в F-014. Pure additive.
- [x] **CHK007** Migration scoped — schema bump 1→2 происходит **между** F-014.0 (local-only, v1 shape без named-config) и F-014.1 (server backup, v2). При F-014.1 migration code должен быть отдельный `migrateLegacy` function. **Improvement**: plan.md должен specify migration entry point.

## Forward compatibility

- [⚠️] **CHK008** Reading newer schema versions — не специфицировано в F-014. Если v3 ConfigDocument (future) на server, F-014.1 user reads — что? Скип unknown fields рекомендуется. **Improvement** для plan.md.
- [⚠️] **CHK009** Unknown discriminator behavior: F-014 не вводит discriminator. `PickerType` (App/Contact/Document/Widget/Action) — sealed class в domain, не wire-format discriminator. OK.

## Tests

- [⚠️] **CHK010** Roundtrip test для extended ConfigDocument не listed в §Local Test Path. Existing roundtrip из спеки 008 покрывает v1 shape. **Improvement**: при F-014.1 нужен `ConfigDocumentV2RoundtripTest`.
- [⚠️] **CHK011** Backward-compat test для v1→v2 не listed. **Improvement**: fixture `simple-launcher-3-tiles.v1.json` + test что v2 deserializer reads v1.
- [x] **CHK012** §Local Test Path fixtures как files в `core/src/test/resources/fixtures/`. PASS.

## Persistence specifics

- [⚠️] **CHK013** DataStore key namespacing для F-014.0 named configs не специфицирован. Plan.md должен specify (например `f014.named_configs.{configName}`).
- [N/A] **CHK014** Нет SQLDelight в F-014.
- [N/A] **CHK015** Нет removed types в F-014.

## Deep-link / QR / exported config

- [N/A] **CHK016-CHK017** F-014.0 не вводит deep-link/QR/exported config. TODO-FUTURE-SPEC-007 (named config export/import as shareable preset) — отложено за пределы F-014.

## Contract folder

- [N/A] **CHK018** Нет `contracts/` папки в spec 014. F-014 переиспользует existing ConfigDocument contracts из спеки 008.

## Open items

1. **CHK001**: DataStore wire-format `schemaVersion` явно не упомянут. **Severity: medium** — plan.md должен specify.
2. **CHK007**: Migration entry point для v1→v2 не specified. **Severity: medium** — plan.md.
3. **CHK008**: Forward-compat policy (skip unknown vs fail) не выбрана. **Severity: low** — F-014.0 single-version, отложить до F-014.1.
4. **CHK010-CHK011**: Roundtrip + backward-compat tests для v2 не listed. **Severity: high** для F-014.1 (per CLAUDE.md rule 5).
5. **CHK013**: DataStore key namespacing — plan.md.

**Verdict**: PASS для F-014.0 scope. **5 medium/high open items для F-014.1 plan**. Acceptable: spec правильно phasing'ует, wire-format concerns concentrate'ятся в F-014.1.
