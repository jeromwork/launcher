# Wire Format Contract: pool.json

**Schema version**: 1
**Physical location**: `app/androidMain/assets/pool.json`
**Loaded via**: `PoolSource` port, MVP implementation `BundledPoolSource`.
**Language**: English (this contract is AI-only; user-facing text via i18n keys resolved elsewhere).

---

## Purpose

Catalog of parameterized `ComponentDeclaration` entries. Preset entries reference declarations by `id`.

## Schema

```json
{
  "schemaVersion": 1,
  "declarations": [
    {
      "id": "font-tile",
      "component": {
        "type": "FontSize",
        "scale": 1.6
      },
      "wizardBehavior": "Interactive",
      "critical": false,
      "descriptionKey": "pool.font.description"
    },
    {
      "id": "tile-whatsapp",
      "component": {
        "type": "AppTile",
        "packageName": "com.whatsapp",
        "labelKey": "pool.tile.whatsapp.label",
        "iconKey": "pool.tile.whatsapp.icon",
        "pinProtected": false
      },
      "wizardBehavior": "AutoApply",
      "critical": false,
      "descriptionKey": "pool.tile.whatsapp.description"
    },
    {
      "id": "sos-main",
      "component": {
        "type": "Sos",
        "shareLocation": true,
        "autoAnswer": true
      },
      "wizardBehavior": "Interactive",
      "critical": true,
      "descriptionKey": "pool.sos.description"
    },
    {
      "id": "toolbar-minimal",
      "component": {
        "type": "Toolbar",
        "items": ["call", "sos", "clock"],
        "layoutKey": "layout.toolbar.minimal"
      },
      "wizardBehavior": "InitialDefault",
      "critical": false,
      "descriptionKey": "pool.toolbar.description"
    }
  ]
}
```

## Field semantics

| Field | Type | Required | Notes |
|---|---|---|---|
| `schemaVersion` | Int | yes | Currently 1. Bumped only on breaking change (rule 5). |
| `declarations` | Array | yes | Ordered by `id` for reproducibility (not runtime-significant). |
| `declarations[].id` | String | yes | Unique within pool. Preset `poolRef` matches this. |
| `declarations[].component` | Object | yes | Polymorphic sealed payload, discriminator `"type"`. |
| `declarations[].component.type` | String | yes | Component subtype `@SerialName`: `AppTile` \| `FontSize` \| `Sos` \| `Toolbar` (MVP). |
| `declarations[].wizardBehavior` | Enum String | no, default `AutoApply` | `Interactive` \| `AutoApply` \| `InitialDefault`. |
| `declarations[].critical` | Boolean | no, default `false` | `true` = participates in `RunMode.BootCheck`. |
| `declarations[].descriptionKey` | String | no | i18n key (resolved for pool docs / admin UI). |

## User-facing strings — i18n keys ONLY (FR-026, fitness #10)

Literal strings in `labelKey`, `descriptionKey`, `iconKey` fields → build error via JSON validator. Values are resolved through `LocalizedResources` port.

**Key naming pattern**: `<domain>.<component_area>.<field>` — examples:
- `pool.tile.jitsi.label`
- `pool.font.description`
- `pool.sos.description`
- `outcome.failed.permission_denied`

## Additive-only growth (FR-023)

Rules for evolving `pool.json` across app releases:

- ✅ **Add new declaration** — always OK.
- ✅ **Add new optional field to Component subtype** (with default value) — additive.
- ✅ **Add new Component sealed subtype** — additive; existing code reading old declarations remains valid.
- ❌ **Remove declaration** — forbidden; breaks presets referencing it.
- ❌ **Rename declaration `id`** — same as removal.
- ❌ **Change `component.type` of existing declaration** — same as removal.
- ⚠️ **Change field default** — allowed but announced in migration notes.

Field additions that require bumping `schemaVersion` from 1 → 2:
- Removing a field from a Component subtype (breaking read).
- Changing a required field to a different type.

Migration writer for schemaVersion bumps lives in `PoolSource` implementation.

## Identity-free discipline (rule 9)

Pool declarations MUST be identity-free — no `pairingId`, `phoneNumber`, `userUid`, `accountEmail`, or other identity-bound values. All identity resolution happens at Provider apply-time via dedicated ports:

- `PairingService.currentAdmin(): PairingId?` — Sos target, admin push routing.
- `CapabilityQuery.isActive(CloudSession): Boolean` — cloud-gated Components (SignInGoogle emits, HealthForward requires).
- Future: `ContactsResolver`, `SubscriptionEntitlement`, etc.

Pool declarations describe **capability** (e.g. «has SOS with location sharing on») not **binding** (e.g. «SOS to +7-123»). Bindings live in Profile (device-local, identity-carrying) and are resolved at apply-time by Providers querying the appropriate port.

**Anti-pattern**: `{"type":"Sos","targetPairingId":"PAIR-PLACEHOLDER"}` — placeholder strings in Pool. Instead: `Sos` has no target field; `SosProvider.apply()` queries `PairingService.currentAdmin()`.

## Anti-explosion limit (FR-025, fitness #8)

- Soft warn if pool contains 4-5 declarations per Component subtype in MVP.
- Error if pool contains 6+ declarations per Component subtype in MVP.
- Rationale: parametrization via preset `paramsOverride` should cover variations, not new pool entries.

## Roundtrip test (SC-005, fitness #4)

```kotlin
val poolJson = readResource("fixtures/pool-mvp-v1.json")
val pool = json.decodeFromString<Pool>(poolJson)
val roundtripped = json.encodeToString(pool)
val poolAgain = json.decodeFromString<Pool>(roundtripped)
assertEquals(pool, poolAgain)
```

## Backward-compat test (SC-008, fitness #5)

When schemaVersion=2 arrives:

```kotlin
val v1Json = readResource("fixtures/pool-v1-legacy.json")
val poolLoaded = poolSource.load(v1Json)   // migration writer applied
assertEquals(2, poolLoaded.schemaVersion)
assertNotNull(poolLoaded.declarations.find { it.id == "font-tile" })
```

## TODO markers

Implementation files:
- `BundledPoolSource.kt`: `// TODO(shareability): future PoolSource adapters — file import, share intent, marketplace. Add as new adapter classes without changing existing wire format.` (rule 9)
