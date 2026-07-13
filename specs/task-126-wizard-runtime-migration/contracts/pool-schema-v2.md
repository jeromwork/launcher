# Contract: Pool JSON Schema v2

**Spec**: FR-006 | **Plan**: [plan.md](../plan.md) | **Data model**: [data-model.md](../data-model.md)

---

## Schema version history

| Version | Change | Backward-compat |
|---------|--------|----------------|
| 1 | Initial (TASK-120) | — |
| 2 | Added `requires`, `required` to each component declaration | ✓ new fields optional/defaulted |

---

## Wire format (JSON)

```json
{
  "schemaVersion": 2,
  "declarations": [
    {
      "id": "launcher-role",
      "component": { "type": "LauncherRole" },
      "wizardBehavior": "AutoApply",
      "critical": true,
      "descriptionKey": "pool_launcher_role_desc",
      "requires": null,
      "required": true
    },
    {
      "id": "font-size-large",
      "component": { "type": "FontSize", "scale": 1.3 },
      "wizardBehavior": "Interactive",
      "critical": false,
      "descriptionKey": "pool_font_size_desc",
      "requires": null,
      "required": false
    },
    {
      "id": "app-tile-whatsapp",
      "component": {
        "type": "AppTile",
        "packageName": "com.whatsapp",
        "labelKey": "app_whatsapp_label"
      },
      "wizardBehavior": "AutoApply",
      "critical": false,
      "requires": ["launcher-role"],
      "required": false
    },
    {
      "id": "theme-warm",
      "component": {
        "type": "Theme",
        "paletteSeedHex": "#FF7043",
        "typographyScale": "Large",
        "shapeStyle": "Rounded",
        "darkMode": false
      },
      "wizardBehavior": "AutoApply",
      "critical": false,
      "requires": null,
      "required": false
    },
    {
      "id": "language-system",
      "component": { "type": "Language", "locale": "system" },
      "wizardBehavior": "Interactive",
      "critical": false,
      "requires": null,
      "required": false
    },
    {
      "id": "status-bar-hidden",
      "component": { "type": "StatusBarPolicy" },
      "wizardBehavior": "AutoApply",
      "critical": false,
      "requires": ["launcher-role"],
      "required": false
    }
  ]
}
```

---

## Field specification

### Pool object

| Field | Type | Required | Default | Notes |
|-------|------|---------|---------|-------|
| `schemaVersion` | Int | Yes | — | MUST be 2 for v2 writers |
| `declarations` | Array | Yes | — | All known component descriptors |

### ComponentDeclaration object

| Field | Type | Required | Default | Notes |
|-------|------|---------|---------|-------|
| `id` | String | Yes | — | Stable identifier; referenced by `Preset.wizardFlow[].poolRef` |
| `component` | Object | Yes | — | Polymorphic; `type` discriminator field |
| `wizardBehavior` | Enum | No | `"AutoApply"` | `"AutoApply"` / `"Interactive"` / `"InitialDefault"` |
| `critical` | Boolean | No | `false` | If true, re-applied at every BootCheck |
| `descriptionKey` | String? | No | `null` | Localization key for human-readable description |
| `requires` | String[]? | **No** | `null` | **v2 addition**: IDs that must appear earlier in `wizardFlow`; null = no dependencies |
| `required` | Boolean | **No** | `false` | **v2 addition**: wizard complete when all `required=true` are Applied |

---

## `requires` validation semantics (FR-006, D7)

`PresetValidator.validate(preset, pool)` checks:

For each entry `E` in `preset.wizardFlow` (in order):
- Resolve `E.poolRef` → `ComponentDeclaration D` in pool
- For each id in `D.requires` (if not null):
  - If that id does NOT appear at an earlier position in `preset.wizardFlow` → `ValidationError.RequiresOrderViolation(offenderId = E.poolRef, missingId)`

Result: non-empty list → wizard MUST NOT start; `PresetBootstrap` returns `BootstrapOutcome.ValidationFailed`.

Example violation: `app-tile-whatsapp` (requires `["launcher-role"]`) appears at position 0, `launcher-role` at position 1 → violation.

---

## Component type discriminators

| `type` value | Kotlin class | New in TASK-126 |
|-------------|-------------|----------------|
| `"AppTile"` | `Component.AppTile` | No |
| `"FontSize"` | `Component.FontSize` | No |
| `"Sos"` | `Component.Sos` | No |
| `"Toolbar"` | `Component.Toolbar` | No |
| `"LauncherRole"` | `Component.LauncherRole` | **Yes** |
| `"Theme"` | `Component.Theme` | **Yes** |
| `"Language"` | `Component.Language` | **Yes** |
| `"StatusBarPolicy"` | `Component.StatusBarPolicy` | **Yes** |

---

## Backward compatibility

v1 readers (reading a v2 pool JSON):
- `requires` field → **ignored** (kotlinx.serialization: unknown keys skipped)
- `required` field → **ignored**
- New `type` values (`LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`) → deserialization error unless v1 code registers them; v1 code should not encounter v2 pool files (assets are app-version-gated)

v2 readers reading a v1 pool JSON:
- `requires` → `null` → no ordering constraints
- `required` → `false` → wizard can complete regardless

---

## Roundtrip test requirement (CLAUDE.md rule 5)

`PoolSchemaVersionTest` MUST verify:
1. v2 JSON → deserialize → serialize → re-deserialize → fields equal
2. v1 JSON (no `requires`, no `required`) → deserialize into v2 model → `requires == null`, `required == false`
3. Pool with `requires` violation → `PresetValidator.validate()` returns `ValidationError.RequiresOrderViolation`

---

## Novice Summary (для владельца)

**Что такое этот файл?**

Это «договор» о том, как выглядит файл `pool.json` — каталог всех возможных «блоков настройки» (компонентов). Каждый компонент может теперь сказать: «мне нужно чтобы сначала был применён вот этот другой компонент» (поле `requires`).

**Пример**: «Установить WhatsApp как плитку» (`app-tile-whatsapp`) требует, чтобы «Сделать лаунчером по умолчанию» (`launcher-role`) уже шёл раньше в списке. Если порядок неправильный — система сообщает об ошибке до старта wizard'а.

**Новое поле `required`**: если `true` — wizard считается завершённым только когда этот шаг выполнен. Если `false` — шаг можно пропустить и всё равно попасть на домашний экран.
