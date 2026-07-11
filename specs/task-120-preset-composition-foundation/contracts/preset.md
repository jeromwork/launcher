# Wire Format Contract: preset.json

**Schema version**: 2
**Physical location**: `app/androidMain/assets/bundled-presets/*.json` (bundled seeds); future dynamic sources: file import, share intent, network, QR.
**Loaded via**: `PresetSource` port. MVP implementations: `BundledPresetSource`.
**Language**: English (contract). Human-readable text via i18n keys.

---

## Purpose

Shareable JSON template describing what should be configured on a device. **Three orthogonal fields** consumed by different consumers:
- `wizardFlow` — linear scenario for first-run / re-run Wizard.
- `settingsMap` — categorical index for Settings UI.
- `activeComponents` — reconcile-engine input (source of truth for BootCheck, RemotePush).

## Schema

```json
{
  "schemaVersion": 2,
  "presetId": "simple-launcher",
  "version": 1,
  "layoutKey": "layout.grid.2x3",
  "wizardFlow": [
    {
      "poolRef": "font-tile",
      "order": 1,
      "wizardTitleKey": "wizard.font.title",
      "wizardIntroKey": "wizard.font.intro",
      "behavior": "Interactive",
      "paramsOverride": { "scale": 1.6 }
    },
    {
      "poolRef": "sos-main",
      "order": 2,
      "wizardTitleKey": "wizard.sos.title",
      "behavior": "Interactive"
    },
    {
      "poolRef": "tile-whatsapp",
      "order": 3,
      "wizardTitleKey": "wizard.apptile.title",
      "behavior": "AutoApply"
    },
    {
      "poolRef": "toolbar-minimal",
      "order": 4,
      "wizardTitleKey": "wizard.toolbar.title",
      "behavior": "InitialDefault"
    }
  ],
  "settingsMap": [
    {
      "poolRef": "font-tile",
      "categoryKey": "settings.category.vision",
      "sensitivity": "Normal"
    },
    {
      "poolRef": "sos-main",
      "categoryKey": "settings.category.safety",
      "sensitivity": "High"
    },
    {
      "poolRef": "tile-whatsapp",
      "categoryKey": "settings.category.apps",
      "sensitivity": "Normal"
    },
    {
      "poolRef": "toolbar-minimal",
      "categoryKey": "settings.category.layout",
      "sensitivity": "Admin"
    }
  ],
  "activeComponents": [
    { "poolRef": "font-tile", "paramsOverride": { "scale": 1.6 } },
    { "poolRef": "sos-main" },
    { "poolRef": "tile-whatsapp" },
    { "poolRef": "toolbar-minimal" }
  ]
}
```

## Field semantics

### Top level

| Field | Type | Required | Notes |
|---|---|---|---|
| `schemaVersion` | Int | yes | Must be 2 in current release. |
| `presetId` | String | yes | Unique preset identifier. Owner-facing slug. |
| `version` | Int | yes | Preset revision counter. Bumped by admin/owner on edit. FR-011: same-version-different-content rejected. |
| `layoutKey` | String | yes | i18n / layout resolver key (e.g. `layout.grid.2x3`). Rendered by UI, engine ignores. |
| `wizardFlow` | Array | no, default `[]` | Linear Wizard sequence (empty = no Wizard shown). |
| `settingsMap` | Array | no, default `[]` | Categorical Settings mapping (empty = Settings screen empty). |
| `activeComponents` | Array | yes | Source of truth for reconcile. Empty = nothing to reconcile (still valid). |

### WizardFlowEntry

| Field | Type | Required | Notes |
|---|---|---|---|
| `poolRef` | String | yes | Must resolve in current Pool (else `ValidationError.UnknownPoolRef`). |
| `order` | Int | yes | Wizard step order. Ties broken by `poolRef` alphabetically. |
| `wizardTitleKey` | String | yes | i18n key for step title (fitness #10 rejects literal). |
| `wizardIntroKey` | String | no | i18n key for step intro paragraph. |
| `behavior` | Enum String | yes | `Interactive` \| `AutoApply` \| `InitialDefault`. |
| `paramsOverride` | Object | no | Per-declaration mutable field allowlist (fitness #7). |
| `visibleIf` | JsonElement | no | MVP: only `{"var": "profile.state.<flag>"}`; other JsonLogic ops deferred. |

### SettingsMapEntry

| Field | Type | Required | Notes |
|---|---|---|---|
| `poolRef` | String | yes | Must resolve. |
| `categoryKey` | String | yes | i18n key (fitness #10 rejects literal). |
| `settingsIcon` | String | no | Icon resource key. |
| `sensitivity` | Enum String | no, default `Normal` | `Normal` \| `High` \| `Admin`. `Admin` requires elevated auth (future). |
| `paramsOverride` | Object | no | Same allowlist as WizardFlowEntry. |

### ActiveComponentEntry

| Field | Type | Required | Notes |
|---|---|---|---|
| `poolRef` | String | yes | Must resolve. |
| `paramsOverride` | Object | no | Persisted state overrides. |
| `status` | Enum String | no, default `Pending` | `Pending` \| `Applied` \| `Failed` \| `Skipped`. Only used inside `Profile.components` (which shares this shape). |

## Additive-only growth

Same as pool.md rules.

## Validation (PresetValidator, FR-027, US 5.5)

Runs **before** `ReconcileEngine.run(RunMode.Wizard)`. Checks:

1. **UnknownPoolRef** — every `poolRef` in wizardFlow / settingsMap / activeComponents resolves in current Pool.
2. **SchemaVersionUnsupported** — `schemaVersion` ≤ current app's supported version.
3. **CapabilityMissing** — walk wizardFlow ordered by `order`, accumulate `provides` per `CapabilityContract`, ensure each `requires` covered before its step.
4. **CircularOrdering** — no cyclic `visibleIf` references (MVP: not reachable since only `profile.state.<flag>` var supported).
5. **paramsOverride schema** — validates against per-declaration JSON Schema `mutable: true` allowlist.

Failed validation → `List<ValidationError>` returned; Wizard does NOT start; UI shows localized message via `LocalizedResources.resolve(err.toI18nKey())`.

## Bundled seed presets (MVP)

Three seeds in `app/androidMain/assets/bundled-presets/`:

- `simple-launcher.json` — senior-focused: FontSize Interactive, Sos Interactive, AppTile examples AutoApply, Toolbar InitialDefault.
- `launcher.json` — regular user: same 4 Components, different defaults (scale=1.2 vs 1.6).
- `workspace.json` — B2B skeleton: Toolbar minimal AutoApply, Sos AutoApply. Scope for full workspace preset in TASK-68.

Bundled seeds are LOCAL — NO CapabilityFlag requirements. Foundation Mode: LOCAL per FR-028.

## Roundtrip test (SC-005)

```kotlin
val presetJson = readResource("fixtures/preset-simple-launcher-v2.json")
val preset = json.decodeFromString<Preset>(presetJson)
val roundtripped = json.encodeToString(preset)
val presetAgain = json.decodeFromString<Preset>(roundtripped)
assertEquals(preset, presetAgain)
```

## Property-based test (SC-011)

`kotest-property`:

```kotlin
val pool = readMvpPool()
val arbPreset = arbitrary { rs -> Preset(...random valid composition from pool...) }
arbPreset.take(100).forEach { preset ->
    val validator = PresetValidator(fakeContract)
    val errors = validator.validate(preset, pool)
    if (errors.isEmpty()) {
        val profile = factory.create(preset, pool)
        engine.run(RunMode.Wizard, cannedSink(preset))
        // assert: every ProfileComponent reaches terminal status
        // assert: no exception
    }
}
```

## TODO markers

- `BundledPresetSource.kt`: `// TODO(shareability): future PresetSource adapters — file import (Intent.ACTION_VIEW), share intent (ACTION_SEND), network fetch, QR-scan. Add as additive adapters without wire format change.` (rule 9)
