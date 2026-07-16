# Wire Format Contract: Profile v2 (+ optional `tags` per Component)

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Data model**: [../data-model.md](../data-model.md)

Persisted Profile JSON serialised to disk under `ProfileStore`. **`schemaVersion: 2`** — matches shipped code (`Profile.CURRENT_SCHEMA_VERSION = 2`, [Profile.kt:39](../../../core/src/commonMain/kotlin/com/launcher/preset/model/Profile.kt#L39)) and the immutable TASK-120 Decision (Profile v2). TASK-127 adds an **optional `tags` array inside each Component object** — an additive change per rule 5, therefore **no schemaVersion bump** and **no migration writer** (Clarification Q6 essence preserved; the earlier "reset to 1" wording was corrected 2026-07-16 — resetting would contradict shipped code and TASK-120's archival Decision for zero benefit).

---

## Location on disk

- Android: Preferences DataStore `task120_profile`, string key `profile_json_v2` — adapter [DataStoreProfileStore.kt](../../../app/src/main/java/com/launcher/app/preset/task120/adapter/DataStoreProfileStore.kt) (NOT a standalone file `files/profile/current.json` — earlier revision of this contract was wrong).
- Testing: [FakeProfileStore.kt](../../../core/src/commonTest/kotlin/com/launcher/preset/fakes/FakeProfileStore.kt) (in-memory).

## Serializer configuration (normative)

Producer and readers MUST use identical kotlinx.serialization settings (as in `DataStoreProfileStore`):

```kotlin
Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}
```

Contract tests MUST construct their `Json` with these exact settings — a drift between test config and adapter config voids the roundtrip guarantee.

---

## Schema v2

Top-level Profile document (real shape per `Profile.kt` / `ProfileComponent`):

```json
{
  "schemaVersion": 2,
  "basedOnPreset": "<preset id>",
  "presetVersion": 2,
  "layoutKey": "<layout key>",
  "components": [ <ProfileComponent…> ],
  "preWizardSnapshot": null,
  "snapshotTimestamp": null,
  "unknownRefs": [],
  "state": { "opaque": {} }
}
```

Each entry in `components` is a **ProfileComponent** wrapper — `id`, `wizardBehavior`, `critical`, `status` live here, NOT on the Component itself:

```json
{
  "id": "tile-settings",
  "component": { "type": "AppTile", … },
  "wizardBehavior": "AutoApply",
  "critical": false,
  "status": "Applied"
}
```

The inner `component` object carries a `"type"` discriminator and the subtype's own fields. **New in TASK-127**: optional `"tags"` array of Tag names. If absent — kotlinx.serialization substitutes the constructor-default of the Component subtype (single source of truth).

### Example — v2 Profile with AppTile + Sos + Toolbar (explicit tags)

```json
{
  "schemaVersion": 2,
  "basedOnPreset": "elder-basic",
  "presetVersion": 2,
  "layoutKey": "grid",
  "components": [
    {
      "id": "tile-settings",
      "component": {
        "type": "AppTile",
        "packageName": "com.android.settings",
        "labelKey": "tile_settings",
        "iconKey": null,
        "pinProtected": false,
        "tags": ["Presentation", "Tile"]
      },
      "wizardBehavior": "AutoApply",
      "critical": false,
      "status": "Applied"
    },
    {
      "id": "sos-primary",
      "component": {
        "type": "Sos",
        "shareLocation": true,
        "autoAnswer": true,
        "tags": ["Presentation", "Tile", "Safety", "Emergency"]
      },
      "wizardBehavior": "Interactive",
      "critical": true,
      "status": "Applied"
    },
    {
      "id": "toolbar-bottom",
      "component": {
        "type": "Toolbar",
        "items": ["home", "sos"],
        "layoutKey": "bottom-bar",
        "tags": ["Presentation", "Toolbar"]
      },
      "wizardBehavior": "AutoApply",
      "critical": false,
      "status": "Applied"
    }
  ],
  "preWizardSnapshot": null,
  "snapshotTimestamp": null,
  "unknownRefs": [],
  "state": { "opaque": {} }
}
```

Notes vs the earlier (wrong) revision of this contract:
- No `presetId` top-level field — real fields are `basedOnPreset` + `presetVersion` + `layoutKey`.
- `AppTile` has `labelKey` (localization resource key), NOT a raw `label` string.
- `Sos` has `shareLocation` / `autoAnswer`. **No `targetPhone`** — contact targets are device-local state and MUST NOT enter Component params (rule 9: Components flow into shareable Presets; phone numbers are PII).
- `Toolbar` has `items: List<String>` + `layoutKey`, not `buttons`.

### Example — Component without explicit `tags` (constructor-defaults kick in)

```json
{
  "id": "tile-settings",
  "component": {
    "type": "AppTile",
    "packageName": "com.android.settings",
    "labelKey": "tile_settings"
  },
  "wizardBehavior": "AutoApply",
  "critical": false
}
```

Deserialized `AppTile` gets `tags = setOf(Tag.Presentation, Tag.Tile)` from the constructor default. (With `encodeDefaults = true`, re-serialization writes `tags` explicitly — the missing-tags form is read-compatible input, not the canonical output.)

### Tag values

Closed set of 10 string names, must match `com.launcher.preset.model.Tag` enum:

`"Presentation"`, `"Appearance"`, `"System"`, `"Safety"`, `"Capabilities"`, `"Communication"`, `"Accessibility"`, `"Emergency"`, `"Tile"`, `"Toolbar"`.

Serialisation: kotlinx.serialization enum default (name string).

---

## Forward compat — honest statement (corrected 2026-07-16)

- **Unknown JSON keys** → ignored (`ignoreUnknownKeys = true`). Adding a new *optional field* to an existing subtype is safe for old readers.
- **Unknown Tag value inside `tags` array** → **`SerializationException` (read failure, fail-loud)**. `ignoreUnknownKeys` covers keys only; kotlinx.serialization has no per-element leniency for enum collections ([kotlinx.serialization #1113](https://github.com/Kotlin/kotlinx.serialization/issues/1113)). The earlier claim "v1 readers deserialise future Tag values" was **false**.
- **Unknown Component `"type"` discriminator** → **`SerializationException` (read failure, fail-loud)** — same mechanism.

Consequence: **adding a Tag value or Component subtype is additive for writers but breaking for older readers.** Pre-release, same-device, same-version — acceptable (writer and reader are always the same binary). **Trigger for the lenient path**: before ANY cross-device / cross-version artifact ships (admin push per spec-009, preset import/share per rule 9), implement a lenient `Set<Tag>` serializer (skip unknown names) + unknown-`type` skip policy, with contract tests. Tracked in plan.md Risks (R-8).

Contract tests MUST pin the *current* fail-loud behavior explicitly (see test surface below) so the deferral stays a documented decision, not an accident.

---

## Migration policy (pre-release)

**While MVP is unreleased**:

- No migration writer (Clarification Q6, rule 4 MVA — no consumer exists).
- `tags` addition is additive → schemaVersion **stays 2**.
- A future *breaking* change (rename/remove field) pre-release = reset dev `ProfileStore` (`adb uninstall` / clear data), still no writer.
- Single source of truth for tags-defaults: `Component` subtype constructors.

**First post-release breaking change**:

- Bump `schemaVersion: 2 → 3`, write `ProfileMigrationV2toV3`, roundtrip + backward-compat tests mandatory (rule 5).

---

## Roundtrip guarantee

- `serialize(profile) → JSON → deserialize → profile'` MUST produce `profile == profile'` (data class equality).
- Verified by `ProfileSchemaV2RoundtripTest` (naming follows existing `core/src/commonTest/kotlin/com/launcher/preset/wire/` pattern: `PoolSchemaV2RoundtripTest`, `PresetSchemaV2RoundtripTest`) on `profile-v2-sample.json` fixture.

## Contract test surface

Location: `core/src/commonTest/kotlin/com/launcher/preset/wire/`.

- `ProfileSchemaV2RoundtripTest` — roundtrip v2 with explicit `tags` on all Components.
- Missing-`tags` case — fixture omits `tags` → deserialized Component equals constructor-default tags.
- Unknown-Tag case — fixture with `"tags": ["FutureTag"]` → assert `SerializationException` (pins fail-loud until lenient serializer ships).
- Unknown-`type` case — fixture with `"type": "FutureComponent"` → assert `SerializationException` (same).

Fixtures in `core/src/commonTest/resources/fixtures/profile-wire-format/`:
- `profile-v2-sample.json` — baseline (explicit `tags` everywhere).
- `profile-v2-no-tags.json` — `tags` omitted (constructor-defaults case).

---

## TL;DR для владельца

- **Формат хранения** — JSON-строка в локальном хранилище устройства (DataStore), куда мастер настройки записывает собранный профиль.
- **`schemaVersion: 2`** — как в уже написанном коде TASK-120. Раннее решение «сбросить на 1» отменено: код и зафиксированное решение TASK-120 уже говорят «2», а добавление `tags` — аддитивное изменение, номер поднимать не нужно.
- **Никакой миграции сейчас** (суть решения Q6 сохранена): релизнутых профилей нет — писать мигратор не для кого.
- **Если в JSON нет поля `tags`** — подставляется значение по умолчанию из кода компонента (например, `AppTile → {Presentation, Tile}`).
- **Честное ограничение**: старая версия приложения, встретив НЕЗНАКОМЫЙ тег или незнакомый тип компонента, упадёт при чтении с понятной ошибкой. Пока профиль живёт только на одном устройстве и читается тем же приложением, которое его записало — это безопасно. Перед тем как профили начнут ходить между устройствами (админ-пуш, обмен пресетами) — обязателен «снисходительный» читатель, который пропускает незнакомое. Это записано как явный триггер, тесты фиксируют текущее поведение.
- **Гарантия**: roundtrip-тест (профиль → JSON → профиль без потерь) + три теста на краевые случаи.
