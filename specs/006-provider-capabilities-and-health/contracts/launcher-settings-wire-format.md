# Contract: `LauncherSettings` Wire Format

**Version:** 1.0.0 · **Status:** Stable from спек 006 · **Owner:** spec 006
**Type:** [`LauncherSettings`](../data-model.md#6-launchersettings) · **Test:** `LauncherSettingsWireFormatTest`
**Fixtures:** `core/src/commonTest/resources/fixtures/launcher-settings/`

---

## Purpose

JSON serialization of user-toggleable banner preferences. Primary consumer в спеке 006 — local DataStore. Future consumer (спек 008): cloud sync via `/config` with conflict resolution «local override wins, admin proposes».

---

## Schema (v1)

```json
{
  "schemaVersion": 1,
  "banners": {
    "airplane": true,
    "mute": true
  }
}
```

### Field reference

| Field | Type | Required | Default | Constraint |
|-------|------|----------|---------|------------|
| `schemaVersion` | Int | ✓ | 1 | ≥ 1 |
| `banners` | object | ✓ | `{}` | nested toggles |
| `banners.airplane` | Bool | ✗ | false | preset-aware default applied at construction (см. §Defaults) |
| `banners.mute` | Bool | ✗ | false | same |

---

## Defaults (corruption recovery, FR-051)

When DataStore file is missing or corrupted, defaults are applied per active preset:

| Preset slug | banners.airplane | banners.mute |
|-------------|------------------|--------------|
| `simple-launcher` | true | true |
| `workspace` | false | false |
| `launcher` | false | false |

Implemented in `LauncherSettings.defaultsForPreset(presetSlug)`.

---

## Reserved fields (для будущих спеков)

Для не-переделки wire-format при расширении в спеке 013 / 008, **резервируем** следующие имена полей (могут появиться в v1.x как additive optional fields, без bump schemaVersion):

| Field | Spec | Default if added |
|-------|------|------------------|
| `banners.offline` | спек 013 | false для всех пресетов |
| `raiseRingerOnLongOffline` | спек 013 | true для simple-launcher |
| `escalation.firstStepMinutes` | спек 013 | 60 |
| `escalation.subsequentStepMinutes` | спек 013 | 30 |
| `escalation.stepPercent` | спек 013 | 20 |

Reservation = добавление future-нашими спеками НЕ конфликтует с парсингом v1.0.0 (spec 006 reader просто игнорирует).

---

## Forward / backward compatibility policy

Same as [Capability wire format](./capability-wire-format.md#forward--backward-compatibility-policy).

---

## Roundtrip test contract

`LauncherSettingsWireFormatTest` MUST cover:
1. Roundtrip all 4 combinations of `banners.{airplane, mute}`.
2. Default construction `LauncherSettings.defaultsForPreset("simple-launcher")` matches expected JSON.
3. Default construction `LauncherSettings.defaultsForPreset("workspace")` matches expected JSON.
4. Forward-compat fixture с `schemaVersion: 999` + reserved fields populated → parses, only known fields populate.
5. Corruption recovery: simulate corrupt JSON → parser returns defaults без exception (FR-51).

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Контракт wire-format `LauncherSettings` v1.0.0 — пользовательские toggle для двух баннеров: `{schemaVersion, banners: {airplane, mute}}`. Хранится в `com.launcher.settings.banners_v1` Preferences DataStore. В спеке 008 — будет синхронизироваться с облаком через `/config`.

**Конкретика, которую стоит запомнить:**
- 2 toggle: `banners.airplane`, `banners.mute`. **Default = false для всех пресетов кроме `simple-launcher`** (там оба true).
- При corruption или пустом файле — `LauncherSettings.defaultsForPreset(slug)` возвращает preset-aware defaults (FR-51).
- **Зарезервированы** имена полей для спека 013 (`banners.offline`, `raiseRingerOnLongOffline`, `escalation.*`) — добавление будет non-breaking. Reader спека 006 их просто игнорирует.

**На что смотреть с осторожностью:**
- При выборе имени **нового** поля в будущих спеках (008, 013) — проверить чтобы оно НЕ конфликтовало с зарезервированными именами выше (или оно есть в reserved списке = OK).
- Defaults **зависят от preset slug** — если в будущем добавится preset, надо обновить `defaultsForPreset` в data model.
- DataStore key — `com.launcher.settings.banners_v1`. Суффикс `_v1` готовит почву для **fresh key** при schemaVersion bump (вместо in-place migration).
