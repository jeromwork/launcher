# Contract: `preset.json` wire format

**Semantic version**: 1 (`schemaVersion: 1`).
**File location**: `core/src/androidMain/assets/presets/*.preset.json` (bundled) or external sources (future).
**Owner**: TASK-65.
**Breaking-change policy**: bump `schemaVersion` + migration writer ships in same commit (per CLAUDE.md rule 5).

---

## Purpose

`Preset` — shareable, self-contained composition of configuration documents. Identified by composite `(uid, version)`. Embedded snapshot of pool entries — receiving device does NOT need pool catalogue access to read a preset, only to author one.

**Not in this format**: personal data (contacts, photos, tokens, identity). `abstractProfile.bindings` contain placeholder packages only.

---

## Schema

```jsonc
{
  "schemaVersion": 1,
  "uid": "com.launcher.preset.simple-launcher",        // REQUIRED, globally unique
  "version": 1,                                         // REQUIRED, ≥ 1, bump on breaking
  "slug": "simple-launcher",                            // REQUIRED, human-readable (NOT identity)
  "label": "preset_simple_launcher_label",              // REQUIRED, i18n key
  "description": "preset_simple_launcher_description",  // REQUIRED, i18n key
  "configs": [                                          // REQUIRED, may be empty list
    {
      "id": "android-role-home",
      "poolId": "system-settings",
      "poolVersion": 3,
      "entryId": "android.role.home",
      "title": "settings_role_home_title",
      "description": "settings_role_home_description",
      "check": { "kind": "android-role", "role": "HOME" },
      "apply": { "kind": "settings-deep-link", "action": "..." },
      "criticality": "Required",
      "hideInWizard": false,
      "showInSettings": true
    }
  ],
  "abstractProfile": {                                  // OPTIONAL — may be omitted entirely
    "layout": {
      "screens": [...],
      "grid": { "rows": 2, "columns": 3 },
      "toolbarTop": [],
      "toolbarBottom": [...]
    },
    "bindings": [                                       // placeholder packages, no PII
      { "slotPosition": 0, "targetPackage": "com.google.android.youtube" }
    ]
  },
  "requiredModules": [],                                // per Article VII §8
  "optionalModules": [],
  "pickEnabled": true                                   // OPTIONAL, default true; if false → picker skips
}
```

---

## Field constraints

| Field | Type | Required | Constraints |
|---|---|---|---|
| `schemaVersion` | Int | yes | Currently 1. Read first. |
| `uid` | String | yes | Globally unique. Reverse-DNS (`com.example.preset.x`) or UUID. **MUST NOT contain `::`** (Map key separator, per R3). Non-blank. |
| `version` | Int | yes | ≥ 1. Bump on breaking content changes (new required config, removed config, etc). Non-breaking edits (i18n label refresh) — no bump. |
| `slug` | String | yes | Human-readable. Used in UI, Detekt lint detection (`PresetIdBranchingDetector`), debugging. **NOT identity** — may repeat between different `uid`. |
| `label`, `description` | String | yes | i18n key (not raw text). Resolved via `composeResources/values*/strings_*.xml`. |
| `configs` | Array | yes | May be empty. Each item must conform to Config schema (см. ниже). |
| `abstractProfile` | Object | no | Optional. If absent → user gets empty screen with «+» on first activation. |
| `requiredModules` | Array | no | Default empty. Per Article VII §8. |
| `optionalModules` | Array | no | Default empty. Per Article VII §8. |
| `pickEnabled` | Boolean | no | Default `true`. If `false` AND only preset available → picker skipped, auto-activate. |

---

## Config sub-schema

```jsonc
{
  "id": "<unique within preset.configs>",
  "poolId": "<source pool id>",
  "poolVersion": <int>,        // pool version at pick-time
  "entryId": "<pool entry id>",
  "title": "<i18n key>",
  "description": "<i18n key>",
  "check": { "kind": "<one of registered CheckSpec variants>", ... },
  "apply": { "kind": "<one of registered ApplySpec variants>", ... },
  "criticality": "Required" | "Optional",   // default Optional
  "defaultValue": "<optional string>",        // for hideInWizard hook (TASK-71)
  "hideInWizard": false,                      // hook for TASK-71
  "showInSettings": true                      // hook for TASK-71
}
```

`check.kind` valid values (TASK-65 + existing):
- `android-role` — Android role (HOME, ASSISTANT, ...)
- `android-permission` — runtime permission
- `android-special-permission` — special access (overlay, accessibility, ...)
- `android-accessibility-service` — accessibility service registration
- `android-package-home` — current default HOME package check
- `ui-font` — UI font scale (NEW в TASK-65)

Future kinds added via sealed hierarchy extension (additive, no breaking change).

`apply.kind` valid values (existing):
- `settings-deep-link` — `Intent` to specific Settings screen
- `request-runtime-permission` — `ActivityResultLauncher` for `RequestPermission`
- (additive expansion)

---

## AbstractProfile sub-schema

```jsonc
{
  "layout": {
    "screens": [
      { "id": "main", "slots": [{ "position": 0, "kind": null }, ...] }
    ],
    "grid": { "rows": 2, "columns": 3 },
    "toolbarTop": [],
    "toolbarBottom": [{ "position": 0, "kind": "settings" }]
  },
  "bindings": [
    { "slotPosition": 0, "targetPackage": "com.google.android.youtube" }
  ]
}
```

**Bindings constraints** (per Clarification #12 + CLAUDE.md rule 9 shareability):
- `targetPackage` — package name only. App may not be installed on target device — rendering fallback ("установите приложение") in TASK-68/71.
- `contactRef` — MUST be opaque local handle, NEVER phone number / email / contact name.
- `url` — public URL allowed.

---

## Backward compatibility

**Reading newer version**:
- If `schemaVersion > 1` → `ConfigSource.load()` returns `IncompatibleVersion(found, known)`. Picker filters such presets, shows banner «Preset X requires app update».
- No crash, no partial parse.

**Reading older version**:
- N/A — `schemaVersion=1` is initial. Future migrations: writer `migrateV1to2(json) → json` ships in commit that introduces `schemaVersion=2`.

**Unknown discriminator** in `check.kind` / `apply.kind`:
- Per Article VII §15: `Indeterminate` state, engine treats setting as pending. No crash. Setting won't be applied (no handler), but app continues to work.

---

## Tests

| Test | What it verifies |
|---|---|
| `PresetWireFormatRoundtripTest` (JVM) | write `Preset` → JSON string → read → `assertEquals(original, parsed)`. Covers `PresetRef` (uid + version). |
| `PresetWireFormatFieldDefaultsTest` (JVM) | JSON without `abstractProfile`, `requiredModules`, `pickEnabled` — parses with documented defaults. |
| `PresetWireFormatUidValidationTest` (JVM) | uid containing `::` rejected at parse-time. |
| `PresetWireFormatIncompatibleVersionTest` (JVM) | JSON with `schemaVersion=99` returns `IncompatibleVersion(99, 1)`, not crash. |
| `BundledPresetsParseTest` (JVM, build-time) | all `*.preset.json` в androidMain/assets parsable, schema valid. |

Fixtures live in `core/src/test/resources/fixtures/preset/`:
- `valid-simple-launcher-v1.json`
- `valid-workspace-v1.json`
- `valid-no-abstract-profile.json`
- `invalid-uid-with-colons.json`
- `incompatible-future-version.json`

---

## Cross-version examples

**Adding a new optional field в v2** (non-breaking): bump `schemaVersion=2`, document default. Old code reads v2 ignoring new field. New code reads v1 applying default. No migration writer needed.

**Removing a field в v2** (breaking): bump `schemaVersion=2`, write `migrateV1to2(json) → json` removing field, ship migrator in same commit. Old code (v1 reader) chokes on v2 → `IncompatibleVersion`.

**Renaming a field** (breaking): same as remove + add. Migrator does both.

---

## Owner-readable summary

`preset.json` — это **JSON-файл, который описывает один вариант приложения** (simple-launcher, workspace, и т.д.). Самосодержащий — можно отдать другому человеку или загрузить с сервера, и он будет работать без обращения к пулу настроек.

Главные обещания:
- **Никакой личной информации внутри** (контактов, паролей, имён). Только placeholder ссылки на app'ы типа YouTube.
- **Identity = uid + version**, не строка-название. Поэтому `workspace v1` от одного автора и `workspace v6` от другого автора не путаются.
- **Версионируется**: меняешь breaking — bump version + migration writer.
- **Расширяется без перехвата кода**: новые типы настроек (например, новая CheckSpec `bluetooth-state`) добавляются через sealed hierarchy без правок engine'а.
