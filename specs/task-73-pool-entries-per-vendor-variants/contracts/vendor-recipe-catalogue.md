# Wire Contract: `vendor-recipes.json` (`VendorRecipeCatalogue`)

> **Versioning statements below are historical (pre-2026-07-20).** They quote a version of CLAUDE.md rule 5 that no longer holds and reinterpret it locally. The authoritative discipline is [`docs/architecture/wire-format.md`](../../../docs/architecture/wire-format.md); this format converts to it on next touch (TASK-138). Read the rest of this contract for the format's *shape*, not for versioning rules.

**Location**: bundled asset, `app/src/main/assets/preset/vendor-recipes.json`, loaded via `VendorRecipeSource` port / `BundledVendorRecipeSource` adapter.
**Schema owner**: `core/src/commonMain/kotlin/com/launcher/preset/model/VendorRecipeCatalogue.kt`.
**Current version**: `schemaVersion = 1` (first commit of this format — no predecessor exists).

## Shape

```jsonc
{
  "schemaVersion": 1,
  "entries": {
    "LauncherRole": {
      "Xiaomi": {
        "intentAction": "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
        "intentPackage": "com.android.settings",
        "intentClassName": "com.android.settings.Settings$ManageDefaultAppsActivitiesActivity",
        "fallbackTextKey": "launcher_role.fallback.xiaomi"
      },
      "Huawei": {
        "intentAction": "android.settings.HOME_SETTINGS",
        "fallbackTextKey": "launcher_role.fallback.huawei"
      },
      "Samsung": {
        "intentPackage": "com.android.settings",
        "intentClassName": "com.android.settings.Settings$DefaultAppSettingsActivity",
        "fallbackTextKey": "launcher_role.fallback.samsung"
      }
    }
  }
}
```

- `entries` outer key = a `Component`'s existing `@SerialName` discriminator (`"LauncherRole"` today; **not** a newly invented id — see `Component.kt`'s `@SerialName` annotations for the authoritative list of valid keys).
- `entries.*` inner key = `Vendor.name` (`"Xiaomi"`, `"Samsung"`, `"Huawei"`, `"GoogleTV"`, `"GenericAndroid"`, `"iOS"` — see `Enums.kt:62`). `"GenericAndroid"`/`"iOS"`/`"GoogleTV"` keys are structurally valid but have no v1 content (no current need — rule 4).
- All `VendorOverride` fields are optional; a partial override (e.g. `fallbackTextKey` only, no intent fields) is valid — `apply()` falls through to the existing generic Android intent for the intent-selection step and only uses the override for the fallback message.

## Read semantics

1. Read `schemaVersion` first (top-level field, decoded before `entries` is inspected in code — the field ordering in this doc is documentation only; `kotlinx.serialization`'s `Json{ignoreUnknownKeys=true}` decodes the whole object regardless of key order, but the reading code's **first branch** MUST check `schemaVersion` before trusting `entries`).
2. `schemaVersion` unsupported (> `CURRENT_SCHEMA_VERSION`) → treat the whole catalogue as absent, log a warning, `LauncherRoleProvider` falls back to its pre-TASK-73 generic-only behaviour. This is the safe direction (never trust a format newer than what this build understands).
3. Unknown outer key (`componentType` not among `Component`'s discriminators) → drop that entry, keep parsing the rest.
4. Unknown inner key (`Vendor` name not in the current enum) → drop that entry, keep parsing the rest.
5. Missing file / malformed JSON at the top level → `VendorRecipeSource.loadCatalogue()` returns an empty `VendorRecipeCatalogue()` (schemaVersion=1, entries=emptyMap()) rather than throwing — `LauncherRoleProvider` treats "no recipe found" identically to "recipe explicitly empty".

## Versioning

- **This is the first version.** No backward-compat fixture exists yet — there is no `schemaVersion=0` to be compatible with. A backward-compat test (read a `schemaVersion=1` fixture with a future `schemaVersion=2` reader) becomes mandatory **when `schemaVersion=2` is introduced**, not before (CLAUDE.md rule 5 read literally: "Backward-compatible reads MUST be possible for at least one major release" — a promise about the future, not a test that can exist before a second version does).
- **When to bump `schemaVersion`**: a required field added to `VendorOverride` without a default, or a structural change to the `entries` nesting. **When NOT to bump**: adding a new outer key (new `Component` coverage) or new inner key (new `Vendor` coverage) — both are pure data additions, old readers of a newer file simply see entries for component/vendor combinations they don't have code paths for yet (harmless, per §"Read semantics" unknown-key handling — though in practice a *newer* app build produces the file, so this direction rarely triggers; the defensive read path exists mainly for cross-branch/CI skew, not a real user-facing scenario).

## Tests (required)

- **Roundtrip**: `VendorRecipeCatalogue` with all three v1 vendor entries → encode → decode → `assertEquals`.
- **Lenient read — unknown component key**: fixture with an extra `"SomeFutureComponent": {...}` entry → parses successfully, that entry absent from the result, no exception.
- **Lenient read — unknown vendor key**: fixture with an extra `"Oppo": {...}` entry under `"LauncherRole"` → parses successfully, that entry absent, no exception.
- **Missing file**: `BundledVendorRecipeSource` pointed at a non-existent asset path → returns empty catalogue, no exception.
- Fixture file: `core/src/commonTest/resources/fixtures/vendor-recipes-v1.json` (checked-in, not a literal string in test code).

## Not a shareable/user-facing artifact (CLAUDE.md rule 9 — does not apply)

Unlike `Preset` (rule 9 shareability-readiness applies — a user/admin composes and might share a preset), `vendor-recipes.json` is **infrastructure data** describing OEM intent targets — no end user or preset-author ever creates or hand-edits it, and it carries no identity-bound or preset-specific content. It follows rule 5 (schemaVersion) because it's a wire format that persists across app versions (bundled asset read on every launch), but the `ConfigSource`/`BundledSource`/`TODO(shareability)` adapter-pattern requirement of rule 9 is **not applicable** — there's nothing here a marketplace or share-intent would ever import. Same category as `pool.json` itself, which also carries no `TODO(shareability)` marker.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Формат bundled-файла `vendor-recipes.json`, `schemaVersion=1` (первая версия, предшественника нет). Для каждого типа `Component` (пока только `"LauncherRole"`) и каждого `Vendor` (`Xiaomi`/`Huawei`/`Samsung` заполнены в v1) — какой intent запускать и какой текст показать, если ничего не сработало.

**Конкретика, которую стоит запомнить:**
- Ключ верхнего уровня `entries` — это `@SerialName` самого `Component` (например `"LauncherRole"`), не выдуманный id.
- Все поля `VendorOverride` опциональны — можно задать только `fallbackTextKey`, без intent-полей.
- Незнакомый `componentType` или незнакомое имя `Vendor` в файле — просто отбрасывается при чтении, не роняет парсинг остального.
- Файл отсутствует/битый → `VendorRecipeSource` возвращает пустой каталог (`schemaVersion=1, entries={}`), не exception.
- Backward-compat тест (чтение предыдущей версии) **сознательно не пишется сейчас** — появится только когда родится `schemaVersion=2`, раньше нечего тестировать.
- Rule 9 (shareability) **не применяется** — это не пользовательский конфиг, а инфраструктурные данные, как `pool.json`.

**На что смотреть с осторожностью:**
- `schemaVersion` должен читаться/проверяться ПЕРВЫМ в коде чтения, до доверия остальным полям — если версия неподдерживаемая, весь каталог трактуется как отсутствующий (безопасное направление).
- Требуется отдельный тест на «неизвестный ключ игнорируется, не роняет всё чтение» — и для `componentType`, и для `Vendor`-имени отдельно (два теста, не один).
