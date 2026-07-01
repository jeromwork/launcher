# Contract: `ProfileStore` DataStore format

**Semantic version**: 1 (`schemaVersion: 1`).
**Storage**: `androidx.datastore.preferences.Preferences` — single string key `"profile.store.json"`.
**Owner**: TASK-65.
**Breaking-change policy**: bump `schemaVersion` + migration writer ships in same commit. **This format syncs to server (TASK-70 territory)**, so backward-compat is strict.

---

## Purpose

`ProfileStore` хранит per-device personal data:
- `activePresetRef` — pointer на текущий active preset.
- `profiles` — Map ProfileData per preset (full history; switching back restores prior state).

**This is what syncs to server** (zero-knowledge, encrypted via pairing keys per TASK-70 / TASK-67). Therefore it's a wire format with strict versioning.

---

## Schema

DataStore Preferences value (key = `"profile.store.json"`, type = `String`) — single JSON document:

```jsonc
{
  "schemaVersion": 1,
  "activePresetRef": "com.launcher.preset.simple-launcher::1",   // composite key string, OR null
  "profiles": {
    "com.launcher.preset.simple-launcher::1": {
      "layout": { "screens": [...], "grid": {...}, "toolbarTop": [], "toolbarBottom": [...] },
      "bindings": [
        { "slotPosition": 0, "targetPackage": "com.whatsapp", "contactRef": "local-handle-abc" }
      ],
      "settings": [
        {
          "config": { /* full Config snapshot from preset at activation time */ },
          "state": { "type": "Applied" }
        },
        {
          "config": { ... },
          "state": { "type": "NotApplied" }
        }
      ],
      "unassigned": []
    },
    "com.launcher.preset.workspace::1": { ... }
  }
}
```

---

## Composite key format

Map keys use `"<uid>::<version>"` format (per research R3).

**Parse / serialize**:
```kotlin
PresetRef.parseCompositeKey("com.launcher.preset.simple-launcher::1")
  // → PresetRef(uid="com.launcher.preset.simple-launcher", version=1)

PresetRef("com.launcher.preset.simple-launcher", 1).toCompositeKey()
  // → "com.launcher.preset.simple-launcher::1"
```

**Constraint enforced by `PresetRef.init`**: `uid` MUST NOT contain `::`. Validated at construction time. Bundled presets are controlled by us; imported/shared presets validated at import path (TASK-35).

---

## DataStore Preferences keys (namespacing)

| Key | Type | Purpose |
|---|---|---|
| `profile.store.json` | String | Entire `ProfileStoreState` serialized as JSON (single atomic write). |
| `profile.store.legacy.wizard_done` | Boolean | Legacy pre-TASK-65 marker for migration detection (FR-015). Read-only after migration. |
| `profile.store.legacy.applied_preset_id` | String? | Legacy pre-TASK-65 marker (was `null` since field didn't exist). Used by migration trigger. |

Naming convention: `<feature>.<area>.<key>` per CLAUDE.md wire-format CHK013.

---

## AppliedState (sealed) serialization

`AppliedState` is sealed class with kotlinx.serialization polymorphism via `type` discriminator:

```jsonc
{ "type": "NotApplied" }
{ "type": "Applied" }
{ "type": "WithValue", "value": "1.5" }
{ "type": "Indeterminate" }
```

`Indeterminate` covers:
- Callback threw exception (Article VII §15 graceful degradation).
- Unknown `CheckSpec.kind` variant (handler not registered).
- Permission check returned unknown / undefined state.

---

## Migration

**v0 (pre-TASK-65) → v1**: triggered by FR-015 condition:
```
if (legacy.wizard_done == true && legacy.applied_preset_id == null) {
    create ProfileStoreState(
        schemaVersion = 1,
        activePresetRef = PresetRef(uid = "com.launcher.preset.simple-launcher", version = 1),
        profiles = mapOf(
            "com.launcher.preset.simple-launcher::1" to ProfileData(/* defaults from bundled simple-launcher preset */)
        )
    )
}
```

Idempotent: if `activePresetRef != null` already → migration no-op.

**Future migrations** (v1 → v2): scoped function `migrateProfileStoreV1toV2(json: JsonObject) → JsonObject`. Composable: `migrateV0toV1.let(::migrateV1toV2)`.

---

## Forward compatibility

**Reading newer schemaVersion**: `ProfileStore.load()` returns `Result.failure(IncompatibleVersionException(found, known))`. Caller (PresetBootRouter) shows error screen «Update app to access this profile» — do NOT auto-overwrite or repair.

**Reading newer Config / SettingEntry fields**: unknown fields ignored (kotlinx-serialization `ignoreUnknownKeys = true`). Old code skips new fields, but preserves them on next write? **NO** — ignored AND dropped on write. Forward compat NOT preserved at config level — bump `schemaVersion` for changes that need cross-version round-trip.

---

## Encryption (TASK-70 territory, not implemented in TASK-65)

When syncing to server, entire `ProfileStoreState` JSON gets encrypted via:
- Symmetric key derived from pairing keys (TASK-67 territory).
- Encrypted blob uploaded to server (zero-knowledge).
- Server stores opaque bytes + version + last-modified timestamp.

TASK-65 produces unencrypted local-only storage. TASK-70 wraps with encryption + sync layer **without changing wire format**.

---

## Tests

| Test | What it verifies |
|---|---|
| `ProfileStoreSerializationTest` (JVM) | write `ProfileStoreState` with ≥2 entries in `profiles` map → JSON → read → assertEquals. |
| `ProfileStoreCompositeKeyTest` (JVM) | Map key string format `"uid::version"` matches PresetRef parse/serialize roundtrip. |
| `ProfileStoreAppliedStateRoundtripTest` (JVM) | All 4 `AppliedState` variants serialize/deserialize. |
| `ProfileStoreIncompatibleVersionTest` (JVM) | Reading v99 JSON returns Failure, not crash. |
| `ProfileStoreMigrationFromLegacyTest` (JVM) | Legacy state `(wizard_done=true, applied_preset_id=null)` → migrates to v1 with `simple-launcher::1` active. |
| `ProfileStoreMigrationIdempotentTest` (JVM) | Running migration on already-migrated state = no-op. |

Fixtures in `core/src/test/resources/fixtures/profile-store/`:
- `valid-v1-empty.json` (just schemaVersion + null activePresetRef + empty profiles map)
- `valid-v1-active-simple-launcher.json`
- `valid-v1-two-profiles-with-history.json`
- `valid-v1-applied-states-all-variants.json`
- `incompatible-future-version.json`

---

## Owner-readable summary

`ProfileStore` — это **файл настроек на телефоне**, который содержит:
- **Какой preset активен сейчас** (`activePresetRef`).
- **Историю всех preset'ов** (`profiles` Map). Если переключился с simple-launcher на workspace, а потом обратно — твои настройки simple-launcher на месте.

**Главные правила**:
- **Один файл = вся история** (а не отдельный файл на каждый preset). Простое, атомарное, легко синхронизируется на сервер.
- **Ключ Map'а — `"uid::version"`** (composite). Это защищает от collision (workspace v1 vs workspace v6 — разные ключи).
- **Это и есть то что синхронизируется на сервер** (TASK-70). Шифруется через pairing keys, сервер видит только зашифрованный blob.
- **Версионируется**: каждое breaking изменение = bump schemaVersion + migration writer.

**Главное обещание**: переключение между preset'ами **никогда не теряет** твои настройки. Они сохраняются в Map history.
