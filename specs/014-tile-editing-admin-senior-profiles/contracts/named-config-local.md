# Contract: NamedConfig (local DataStore)

**Phase**: F-014.0
**Semantic version**: 1
**Breaking-change policy**: per CLAUDE.md rule 5 — adding fields allowed; renaming/removal requires migration.
**Roundtrip test**: `data/src/test/kotlin/com/launcher/adapter/edit/NamedConfigsLocalStoreTest.kt`
**Backward-compat fixture**: N/A (F-014.0 is v1 initial schema; v0 doesn't exist).

---

## Wire format

JSON serialized в DataStore Preferences под key `f014.named_configs.v1`.

```json
{
  "schemaVersion": 1,
  "configs": [
    {
      "schemaVersion": 1,
      "configName": "default",
      "description": "",
      "isDefault": true,
      "presetId": "workspace",
      "deviceClass": "phone",
      "activeDeviceIds": ["device-uuid-xxx"],
      "orphanedAt": null
    }
  ]
}
```

## Field semantics

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `schemaVersion` | Int | yes | 1 | Read FIRST during deserialization (CHK002 wire-format). Used to gate v1/v2 parsing. |
| `configName` | String | yes | — | NFC normalized, 1..32 chars, matches `^[\p{L}\p{N} -]+$`. Case-insensitive unique per admin. |
| `description` | String | no | `""` | 0..200 chars. User free text. |
| `isDefault` | Boolean | yes | — | Exactly one config has `true` per admin. Enforced atomically by store. |
| `presetId` | String | yes | — | Compatibility key. Built-in: `"workspace"` / `"simple-launcher"` / future enum values. |
| `deviceClass` | String | yes | — | Compatibility key. F-014.0: `"phone"`. Future: `"tablet"`, `"tv"`, `"foldable"`. |
| `activeDeviceIds` | Set<String> | yes | `{thisDeviceId}` | F-014.0: contains exactly `thisDeviceId` from Firebase Installations (per TODO-RESEARCH-009). F-014.1 multi-device. |
| `orphanedAt` | Instant? | no | `null` | If `activeDeviceIds.isEmpty()` becomes true, set to `Clock.System.now()`. Used for UI marker countdown (FR-003b). |

## Invariants (enforced at store boundary)

1. `0 < configs.size <= 5`.
2. Exactly one config with `isDefault == true`.
3. All `configName` values are unique case-insensitive after NFC normalization.
4. Each config: `activeDeviceIds.size >= 1` OR `orphanedAt != null`.
5. `schemaVersion <= CURRENT_SCHEMA_VERSION` — reading newer version → fail-closed with `StoreError.UnsupportedSchemaVersion`.

## Validation rules (configName)

- **Length**: 1..32 characters after NFC normalization.
- **Allowed characters**: Unicode letters (`\p{L}`), digits (`\p{N}`), space, hyphen.
- **Uniqueness**: case-insensitive, NFC-normalized.
- **Reserved names**: none in F-014.0.
- **Validation error variants**: `EmptyName`, `TooLong`, `InvalidChars`, `NameAlreadyExists`.

## Backward compatibility (future-facing)

- **Reading v2** (when F-014.1 ships): if `schemaVersion == 2` is read on F-014.0 app, return `StoreError.UnsupportedSchemaVersion`. UI shows "Update app to use this config".
- **Reading v1** on F-014.1 app: parse with defaults for new fields (multi-device fields).

## Forward compatibility

Reading newer schemaVersion → **fail-closed** with explicit error. Not skip-unknown (named configs are too consequential to silently lose fields).

## Default config bootstrap

On first launch of F-014.0 (empty DataStore):
- Create one config: `NamedConfig(configName = "default", isDefault = true, presetId = currentPreset, deviceClass = currentDeviceClass, activeDeviceIds = setOf(thisDeviceId))`.
- Per FR-003d State 0/1: UI hides the existence of this config.

## Atomic operations

- `markDefault(configName)`: set `isDefault=true` for target, `false` for all others. Wrapped в DataStore `updateData { }` block — atomic per DataStore guarantees.
- `create(config)`: check 5-limit + uniqueness + default invariant, append, persist — atomic.
- `removeFromCurrentDevice(configName)`: remove `thisDeviceId` from `activeDeviceIds`, set `orphanedAt` if empty, persist — atomic. **Refuse** if target's `isDefault==true` (FR-003a).

## Tests

- **Roundtrip**: write NamedConfig → read → assertEquals.
- **Invariant 1** (size): create 6th → expect `StoreError.LimitReached`.
- **Invariant 2** (default): markDefault flips single config; trying to remove last default → `StoreError.DefaultMustExist`.
- **Invariant 3** (uniqueness): create two configs with same configName (case-insensitive) → `StoreError.NameAlreadyExists`.
- **Invariant 4** (lifecycle): removeFromCurrentDevice setting empty activeDeviceIds → `orphanedAt` set.
- **Invariant 5** (unsupported version): inject JSON with `schemaVersion: 99` → `StoreError.UnsupportedSchemaVersion`.
- **Validation**: invalid configName variants — `EmptyName`, `TooLong`, `InvalidChars`.

## Process death / persistence

DataStore persists across process death (managed by Android framework). No additional persistence layer needed. Test: `NamedConfigsProcessDeathTest` (kill process between create() and read on restart).

---

## TL;DR на русском

**Где хранится**: DataStore Preferences под ключом `f014.named_configs.v1` — JSON со списком NamedConfig.

**Поля каждого config**: `configName` (имя 1-32 символа, Unicode letters), `description` (опционально), `isDefault` (ровно у одного true), `presetId` + `deviceClass` (для проверки совместимости при future sharing), `activeDeviceIds` (для F-014.0 — один thisDeviceId), `orphanedAt` (метка lifecycle).

**Главные инварианты**: не больше 5 конфигов; ровно один isDefault; имена case-insensitive уникальны; нельзя удалить последний default.

**Forward-compat policy**: если приложение читает JSON со `schemaVersion` старше текущего — **fail-closed** (NamedConfig слишком важен чтобы скипать unknown поля). UI показывает «обнови приложение».

**На что F-014.0 не влияет**: ConfigDocument schema (он остаётся v1, апгрейд 1→2 — F-014.1 work).
