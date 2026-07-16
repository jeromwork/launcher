# Wire Format Contract: Profile v3

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Data model**: [../data-model.md](../data-model.md)

Persisted Profile JSON serialised to disk under `ProfileStore`. Schema bumped v2 → v3 by this spec (TASK-127) to add `Component.tags: Set<Tag>` field.

Per CLAUDE.md rule §5 (wire-format versioning): explicit `schemaVersion` field, backward-compatible reads for one major release, migration writer produced BEFORE the breaking change ships.

---

## Location on disk

- Android: `/data/data/com.launcher.app/files/profile/current.json` (path per `ProfileStore` androidMain adapter, unchanged from TASK-120).
- Testing: `FakeProfileStore` holds in-memory JSON string.

---

## Schema v3

Top-level Profile document:

```json
{
  "schemaVersion": 3,
  "presetId": "senior-safe-classic",
  "components": [ ... ]
}
```

Each Component in `components` array carries a `"type"` discriminator and its own fields. NEW field `"tags"` — array of Tag string names.

### Example — v3 Profile with AppTile + Sos + Toolbar

```json
{
  "schemaVersion": 3,
  "presetId": "senior-safe-classic",
  "components": [
    {
      "type": "AppTile",
      "id": "tile-settings",
      "packageName": "com.android.settings",
      "label": "Настройки",
      "tags": ["Presentation", "Tile"]
    },
    {
      "type": "Sos",
      "id": "sos-primary",
      "targetPhone": "+79991234567",
      "tags": ["Presentation", "Tile", "Safety", "Emergency"]
    },
    {
      "type": "Toolbar",
      "id": "toolbar-bottom",
      "buttons": [
        { "kind": "Home" },
        { "kind": "Back" }
      ],
      "tags": ["Presentation", "Toolbar"]
    }
  ]
}
```

### Tag values

Closed set of 10 string names, must match `com.launcher.preset.model.Tag` enum:

`"Presentation"`, `"Appearance"`, `"System"`, `"Safety"`, `"Capabilities"`, `"Communication"`, `"Accessibility"`, `"Emergency"`, `"Tile"`, `"Toolbar"`.

Serialisation: kotlinx.serialization `enum` default (name string). Additive-only — new values can be added in future schema versions.

---

## Backward compat: reading v2

v2 Profile documents:
```json
{
  "schemaVersion": 2,
  "presetId": "senior-safe-classic",
  "components": [
    { "type": "AppTile", "id": "tile-settings", "packageName": "com.android.settings", "label": "Настройки" }
  ]
}
```

Field `"tags"` absent on Components. `ProfileSerializer` reads v2 successfully:

1. Detects `schemaVersion == 2` in top-level JSON.
2. Deserialises Component fields present in v2 (no `tags`).
3. Invokes `ProfileMigrationV2toV3.migrate(v2Profile)` — writer fills `tags` from Component-subtype default mapping.
4. Returns `ProfileV3` in-memory. Disk write happens lazily on next `ProfileStore.save()`.

**Idempotency**: applying migration to already-v3 Profile is prevented at the serializer layer (`schemaVersion == 3 → pass through`). Verified by `ProfileMigrationV2toV3RoundtripTest`.

---

## Forward compat: reading v4+ (not yet defined)

v3 reader MUST NOT crash on unknown fields. kotlinx.serialization config: `ignoreUnknownKeys = true`. Future v4 additions (e.g., new Component subtypes, new Tag values) may be silently dropped on v3 read — acceptable per rule 5 (backward-compat guarantee is one direction: newer readers understand older writes).

---

## Roundtrip guarantee

- `serialize(profile) → JSON` → `deserialize(JSON) → profile'` MUST produce `profile == profile'` byte-equal (data class equality).
- Verified by `ProfileWireFormatV3ContractTest` on `profile-v3-sample.json` fixture.

## Migration roundtrip guarantee

- `v2 JSON` → `deserialize` → `v3 Profile` → `serialize` → `v3 JSON` → `deserialize` → `v3 Profile'`.
- Second `v3 Profile'` MUST equal first `v3 Profile` (data class equality) — idempotency proof.
- Verified by `ProfileMigrationV2toV3RoundtripTest`.

---

## Breaking changes deferred

Not part of this contract:
- Removing a Tag value — breaking, requires v3 → v4 migration.
- Removing a Component subtype — breaking, requires v3 → v4 migration.
- Changing field name on existing Component subtype — breaking, requires v3 → v4 migration.

Additive changes NOT breaking:
- New Component subtype (v3 readers see it as unknown `type` — behaviour: skip or fail-loud, decision deferred to first case).
- New Tag value (v3 readers deserialise it via kotlinx.serialization; migration writer needs no update).
- New optional field on existing Component subtype (v3 readers ignore via `ignoreUnknownKeys`).

---

## Contract test surface

- `ProfileWireFormatV3ContractTest` — roundtrip v3 byte-equal.
- `ProfileMigrationV2toV3RoundtripTest` — v2 → v3 with tags populated per defaults; idempotency.
- `ProfileMigrationV2toV3BackwardCompatTest` — v2 fixture reads without exception.

Fixtures in `core/src/commonTest/resources/fixtures/`:
- `profile-v2-sample.json` — v2 baseline (no `tags` fields).
- `profile-v3-sample.json` — v3 baseline (all `tags` populated).

---

## TL;DR для владельца

- **Формат хранения** — JSON-файл на диске устройства, куда пишутся все настройки пользователя (плитки, тема, шрифт).
- **Что меняется**: у каждого компонента в JSON появляется поле `"tags"` — список ярлыков (`["Presentation", "Tile"]` и т.д.).
- **Что происходит со старыми данными**: приложение читает старый файл (v2), автоматически проставляет теги по типу компонента, работает дальше. На диск v3 запишется когда пользователь что-то поменяет (лениво).
- **One-way door**: после релиза v3 старая версия приложения не сможет прочитать новый файл. Стандартно для мобильных приложений — apk не откатывается.
- **Гарантии**: два теста — roundtrip (v3 → JSON → v3 без потерь) и миграция (v2 → v3 → v3 → v3 не меняет данные при повторе).
