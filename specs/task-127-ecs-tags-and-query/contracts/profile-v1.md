# Wire Format Contract: Profile v1

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Data model**: [../data-model.md](../data-model.md)

Persisted Profile JSON serialised to disk under `ProfileStore`. **`schemaVersion: 1`** — стартовая версия с первого коммита. MVP не релизнут, никаких предыдущих версий Profile файлов в природе не существует.

Per CLAUDE.md rule §5: explicit `schemaVersion` field with day-1 (yes — value = 1). Backward-compatible reads for future breaking changes — **написание migration writer'ов отложено до production релиза** per owner decision 2026-07-16 (Clarification Q6, rule 4 MVA).

---

## Location on disk

- Android: `/data/data/com.launcher.app/files/profile/current.json` (path per `ProfileStore` androidMain adapter, unchanged from TASK-120).
- Testing: `FakeProfileStore` holds in-memory JSON string.

---

## Schema v1

Top-level Profile document:

```json
{
  "schemaVersion": 1,
  "presetId": "senior-safe-classic",
  "components": [ ... ]
}
```

Each Component in `components` array carries a `"type"` discriminator and its own fields. Optional field `"tags"` — array of Tag string names. Если поле отсутствует — `kotlinx.serialization` подставляет constructor-default из `Component` subtype.

### Example — v1 Profile with AppTile + Sos + Toolbar

```json
{
  "schemaVersion": 1,
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

### Example — v1 Profile without explicit `tags` (constructor-defaults kick in)

```json
{
  "schemaVersion": 1,
  "presetId": "senior-safe-classic",
  "components": [
    {
      "type": "AppTile",
      "id": "tile-settings",
      "packageName": "com.android.settings",
      "label": "Настройки"
    }
  ]
}
```

Deserialized `AppTile` в этом случае получит `tags = setOf(Tag.Presentation, Tag.Tile)` из constructor-default (единственный источник истины).

### Tag values

Closed set of 10 string names, must match `com.launcher.preset.model.Tag` enum:

`"Presentation"`, `"Appearance"`, `"System"`, `"Safety"`, `"Capabilities"`, `"Communication"`, `"Accessibility"`, `"Emergency"`, `"Tile"`, `"Toolbar"`.

Serialisation: kotlinx.serialization `enum` default (name string). Additive-only — new values can be added in future schema versions.

---

## Migration policy (pre-release)

**Пока `schemaVersion: 1` и MVP не релизнут**:

- Никакой migration writer не пишется.
- Каждое breaking изменение полей Component = переписываем dev fixture'ы, `schemaVersion` остаётся `1`.
- Dev `ProfileStore` на устройствах разработчиков может быть сброшен (`adb uninstall` / clear data).
- Единственный источник истины для tags-defaults — конструкторы `Component` subtypes.

**После production релиза первый breaking change**:

- Bump `schemaVersion: 1 → 2`.
- Пишем `ProfileMigrationV1toV2` object с exhaustive `when` over sealed hierarchy.
- Roundtrip + backward-compat test обязательны.
- До этого момента — упражнение впустую (нет потребителя миграции).

---

## Forward compat

v1 reader MUST NOT crash on unknown fields. kotlinx.serialization config: `ignoreUnknownKeys = true`. Позволяет добавлять optional поля без bump'а schemaVersion (например будущий `iconOverride: String? = null` на AppTile).

---

## Roundtrip guarantee

- `serialize(profile) → JSON` → `deserialize(JSON) → profile'` MUST produce `profile == profile'` byte-equal (data class equality).
- Verified by `ProfileWireFormatV1ContractTest` on `profile-v1-sample.json` fixture.

---

## Breaking changes (deferred to post-release)

Not part of this contract (появятся когда потребуются):
- Removing a Tag value — breaking, потребует v1 → v2 migration после релиза.
- Removing a Component subtype — breaking, аналогично.
- Renaming field on existing Component subtype — breaking, аналогично.

Additive changes NOT breaking:
- New Component subtype (v1 readers see it as unknown `type` — behaviour: skip or fail-loud, decision deferred to first case).
- New Tag value (v1 readers deserialise it via kotlinx.serialization; constructor-defaults не затрагиваются).
- New optional field on existing Component subtype (v1 readers ignore via `ignoreUnknownKeys`).

---

## Contract test surface

- `ProfileWireFormatV1ContractTest` — roundtrip v1 byte-equal.
- **REMOVED**: `ProfileMigrationV2toV3RoundtripTest`, `ProfileMigrationV2toV3BackwardCompatTest` — нет migration writer, нет тестов миграции. Появятся когда первый migration напишется post-release.

Fixtures в `core/src/commonTest/resources/fixtures/`:
- `profile-v1-sample.json` — v1 baseline (with explicit `tags` fields on all Components).

---

## TL;DR для владельца

- **Формат хранения** — JSON-файл на диске устройства, куда пишутся все настройки пользователя (плитки, тема, шрифт).
- **`schemaVersion: 1`** — стартовая версия. Пока MVP не релизнут, номер не растёт.
- **Никакой миграции сейчас** (решение владельца 2026-07-16): нет релизнутых профилей — нет потребителя миграции. Каждый раз когда меняем формат в dev — сбрасываем dev `ProfileStore` (`adb uninstall`).
- **Что происходит если в JSON нет поля `tags`**: kotlinx.serialization подставляет default из конструктора Component (например, `AppTile → {Presentation, Tile}`). Единственный источник истины — конструкторы, не отдельный mapping-файл.
- **После production релиза**: первый breaking change = первый migration writer + `schemaVersion: 1 → 2`. До того — упражнение впустую.
- **Гарантия**: один тест — roundtrip (v1 → JSON → v1 без потерь).
