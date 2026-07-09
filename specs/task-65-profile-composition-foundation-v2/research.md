# Research: TASK-65 — Decisions с альтернативами

Each decision documents (a) what was decided, (b) alternatives considered, (c) rationale, (d) regret conditions (exit ramps per CLAUDE.md rule 3).

---

## R1 — ConfigKind extension strategy

**Decision**: Add `Preset` as 6th variant of existing `ConfigKind` enum.

**Alternatives**:
- (a) Separate `PresetSource` port (parallel to `ConfigSource`). Rejected: duplication, breaks Article VII §10 evolution rule.
- (b) Embed Preset as `WizardManifest` extension. Rejected: violates separation of concerns (manifest = wizard scenario; preset = top-level composition).

**Rationale**: Article VII §10 explicitly allows adding kinds. `ConfigSource` already abstract. Adding variant = additive, no breaking change.

**Exit ramp**: If we want to remove `ConfigKind.Preset` later — replace with separate port, migrate consumers. ~1 day.

---

## R2 — Existing `ProfileSnapshot` type — rename or delete?

**Decision**: Inventory grep on implementation phase; if **0 consumers** → delete; if **≥1 consumer** → rename to `ResolvedPresetSnapshot` and keep semantically as «runtime view after composition».

**Alternatives**:
- (a) Keep as-is, add new `Preset` рядом. Rejected: confusing namespace; semantic clash with new term.
- (b) Rename to new `Profile` semantics. Rejected: existing `ProfileSnapshot` fields (`moduleFlags`, `accessibilityPreset`, `layoutHints`) don't match new Profile structure (`layout`, `bindings`, `settings`).

**Rationale**: Existing type pre-dates Amendment 1.11 (naming inversion). Likely dead code from earlier iteration; if alive — represents resolved/composed state, fits `ResolvedPresetSnapshot` name.

**Exit ramp**: Verbatim — `git mv` + Edit consumers OR `git rm`. ~30 minutes.

---

## R3 — ProfileStore serialization — composite key strategy

**Decision**: **Composite string key `"<uid>::<version>"`** in flat JSON map. Single DataStore Preferences string value `profile.store.json` contains entire serialized `ProfileStore`.

**JSON shape**:
```json
{
  "schemaVersion": 1,
  "activePresetRef": "com.launcher.preset.simple-launcher::1",
  "profiles": {
    "com.launcher.preset.simple-launcher::1": { "layout": {...}, "bindings": [...], "settings": [...] },
    "com.launcher.preset.workspace::1": { ... }
  }
}
```

**Alternatives evaluated**:

| Option | Pro | Con | Verdict |
|---|---|---|---|
| (a) **Composite string key in JSON** (chosen) | KMP-friendly, simple, single Preferences value, easy migration to `multiplatform-settings` | Need escaping for `::` if appears in uid; need parse helpers | ✅ |
| (b) Proto DataStore с nested message | Type-safe, no parse helpers | Heavy: requires `.proto` files, codegen, adds protobuf dep. Not strictly KMP without extra setup. | ❌ |
| (c) One Preferences key per profile (`profile.<uid>.<version>.json`) | Lazy load, smaller writes | Need second key для list of profiles (or scan Preferences keys — fragile). Complex coordination. | ❌ |
| (d) SQLDelight table | Strong typing, indexed | Massive overhead для 3-10 entries. Already chose Preferences DataStore in stack. | ❌ |

**Rationale for (a)**:
- KMP без extra setup (`kotlinx.serialization-json` уже в проекте).
- Single write = single atomic operation.
- Easy server sync (TASK-70): blob = JSON string, encrypt-then-upload.
- `::` separator: uid validation rule — uid MUST NOT contain `::` (validated in `PresetRef.parse()`). Constraint трivial.

**Escape rule**: If uid contains `::` → reject at parse-time (`PresetRef.parse()` throws). Bundled presets controlled by us; sharing presets — validation in import path (TASK-35 territory).

**Exit ramp**: Migration to Proto/SQLDelight = write reader for legacy JSON format → migrate on app upgrade. ~1 day. JSON format also keeps option open for `multiplatform-settings` if/when iOS target ships.

---

## R4 — Boot path: synchronous callbacks or async with banner update?

**Decision**: **Synchronous in `Boot.onCreate()` initially**. Если `BootBenchmarkTest` показывает > 1.5s P95 на pixel_5_api_34 → switch to async path.

**Synchronous path** (default):
```kotlin
override fun onCreate(...) {
    val profile = profileStore.loadActive()
    val pending = wizardEngine.computePending(profile.settings)  // <100ms expected for ~10 entries
    val critical = pending.filter { it.criticality == Required }
    startHomeActivity(banner = if (critical.isNotEmpty()) BannerData(critical) else null)
}
```

**Async fallback** (if benchmark fails):
```kotlin
override fun onCreate(...) {
    val profile = profileStore.loadActive()
    startHomeActivity(banner = null)  // show immediately
    lifecycleScope.launch {
        val pending = wizardEngine.computePending(profile.settings)
        val critical = pending.filter { it.criticality == Required }
        if (critical.isNotEmpty()) homeViewModel.showBanner(BannerData(critical))
    }
}
```

**Rationale**: Synchronous is simpler, predictable, no race conditions. ~100ms for 10 callbacks is well within SC-007 budget (1.5s). Async только если actually needed — premature optimization otherwise.

**Banner dismissibility**:
- HomeBanner: "Настроить" (→ mini-wizard) / "Позже" (dismiss until next boot OR state change). Stored in `lifecycleScope` ViewModel state, не в persisted preferences (per-boot decision).
- Settings reminder banners (FR-016): no explicit dismiss (persists while requirement missing). Disappears automatically on next `onResume` re-check after fix.

**Exit ramp**: Switching sync → async = ~half day refactor. Bookmark via comment в `PresetBootRouter`: `// TODO(perf): if BootBenchmarkTest fails SC-007, switch to async path (see plan.md R4)`.

---

## R5 — Detekt vs Android Lint для fitness functions

**Decision**: **Detekt** в новом `lint-rules/` Gradle module.

**Alternatives**:
- (a) Android Lint (UAST scanner). Rejected: Android Lint scans `commonMain` weakly (configured via androidLint Gradle plugin); custom UAST detectors verbose; harder to write rule tests.
- (b) ktlint custom rule. Rejected: ktlint focused on formatting, not architectural rules. Limited AST access.

**Rationale**:
- Detekt scans `commonMain` Kotlin natively (KMP-friendly per spec cross-app vision).
- Custom rules API lightweight (extend `Rule`, override `visitCallExpression` etc.).
- Built-in test framework (`KtTestCompiler`) для positive/negative cases.
- Already used в Kotlin ecosystem widely.

**Setup cost**: New Gradle module `lint-rules/` + Detekt config in root `build.gradle.kts`. Detailed в [`quickstart.md`](quickstart.md).

**Exit ramp**: Migrate to Android Lint = rewrite 2 rules in UAST API. ~1 day. Detekt module remains in repo for potential dual-rule run.

---

## R6 — Migration writer pattern для `presetId` removal

**Decision**: **Scoped function `migrateLegacyWizardManifest(json: JsonObject): JsonObject`** в `core/commonMain/api/wizard/data/ConfigParser.kt` (existing file extended).

**Flow**:
```kotlin
fun parseWizardManifest(raw: String): Result<WizardManifest> {
    val json = Json.parseToJsonElement(raw).jsonObject
    val version = json["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
    val migrated = when (version) {
        1 -> migrateLegacyWizardManifest(json)  // removes presetId, bumps version
        2 -> json  // current
        else -> return Result.failure(IncompatibleVersionException(version, CURRENT))
    }
    return Result.success(Json.decodeFromJsonElement(migrated))
}

private fun migrateLegacyWizardManifest(v1: JsonObject): JsonObject = buildJsonObject {
    v1.forEach { (k, v) -> if (k != "body" || v !is JsonObject) put(k, v) else {
        put("body", buildJsonObject {
            v.jsonObject.forEach { (bk, bv) -> if (bk != "presetId") put(bk, bv) }
        })
    } }
    put("schemaVersion", JsonPrimitive(2))
}
```

**Alternatives**:
- (a) Inline `if (version == 1) { ... } else { ... }` scattered. Rejected: doesn't scale per CLAUDE.md wire-format checklist CHK007.
- (b) Separate migration registry с `Migration` interface. Rejected: rule 4 MVA — overkill для one migration.

**Rationale**: Single function = scoped, testable in isolation, easy to add v2→v3 migration later (composable: `migrateV1to2(json).let(::migrateV2to3)`).

**Test**: `WizardManifestBackwardCompatTest` с fixture `legacy-with-app-family-id.json`.

**Exit ramp**: Scope grows → refactor to `Migration` interface registry. ~half day.

---

## R7 — Logs strategy

**Decision**: Per-service Logcat tag + structured fields via `kotlin.io.Log` wrapper.

**Tags**:
- `PresetBoot` — `PresetBootRouter` (boot path).
- `PresetSwitch` — `PresetSwitchService` (settings switch flow).
- `PresetReminder` — `PresetReminderService` (banner + reminders).
- `PresetSelect` — `PresetSelectionService` (first-launch flow).
- `ConfigSource` — `BundledConfigSource` (parse / load).
- `PoolSource` — `HardcodedPoolSource` / `JsonAssetPoolSource`.
- `ProfileStore` — `PreferencesProfileStore` (read/write).

**Structured fields** (через `Log.i(TAG, "operation=X presetRef=Y duration_ms=Z")` format):
- `presetRef` = `"<uid>::<version>"` (consistent с serialization).
- `operation` = enum-like string (`load`, `switch`, `migrate`, `check`, `apply`).
- `duration_ms` = where relevant.
- `outcome` = `success / failure(reason)`.

**Mandatory failure logs**:
- Settings callback throws → `Log.w("PresetBoot", "callback_failed entry=X kind=Y reason=Z")`.
- Persistence write fails → `Log.e("ProfileStore", "write_failed presetRef=X reason=Y")`.
- Mini-wizard launch fails → `Log.e("PresetReminder", "wizard_launch_failed presetRef=X reason=Y")`.
- Migration failures → `Log.e("ConfigSource", "migration_failed from=X to=Y reason=Z")`.

**No PII in logs**: presetRef does not contain user data (per Clarification #13 — uid is preset author identifier, not user).

**Rationale**: Structured fields позволяют grep-friendly debugging without external log analyzer dependency. Logcat tags help filter в `adb logcat -s PresetBoot`.

**Exit ramp**: Switch to dedicated logging lib (Timber, kermit) = wrap calls in extension function. ~1 hour.

---

## Summary

| ID | Decision | One-way door? | Exit ramp cost |
|---|---|---|---|
| R1 | ConfigKind.Preset 6th variant | Yes (wire format) | ~1 day via separate port |
| R2 | ProfileSnapshot rename or delete (TBD on grep) | No | ~30 min |
| R3 | Composite string key `"uid::version"` в JSON map | Yes (DataStore format) | ~1 day via reader migration |
| R4 | Synchronous boot callbacks (fallback async if SC-007 fails) | No | ~half day refactor |
| R5 | Detekt в `lint-rules/` module | Borderline (rules invested) | ~1 day rewrite to Lint |
| R6 | Scoped `migrateLegacyWizardManifest` function | No | ~half day to Migration registry |
| R7 | Logcat tags + structured fields | No | ~1 hour to Timber/kermit |

---

## Plain Russian summary (для не-разработчика владельца)

**Это файл — где зафиксированы все важные технические решения**, которые приняты на стадии плана. Для каждого решения объяснено что выбрали, что было альтернативой и сколько стоит откатиться если решение окажется неверным.

**Самые важные решения**:

1. **ProfileStore хранится как один JSON-файл в DataStore** с композитным ключом `"uid::version"`. Альтернативой был Protobuf (тяжело) и отдельный файл на каждый preset (сложно координировать). Откат стоит ~1 день.
2. **Boot-проверка настроек синхронная** (быстрая, ms). Если benchmark покажет что медленно — переключаемся на async (показать главный экран сразу, banner подгружается фоном). Откат ~полдня.
3. **Detekt — отдельный gradle модуль**, не вписан в существующие. Это позволяет в будущем переиспользовать его в messenger / photo приложениях без вытаскивания. Откат ~1 день.
4. **Migration существующих файлов** (удаление `presetId`) — отдельная функция, не разбросанные `if version == 1`. Тестируется отдельно с pre-TASK-65 фикстурой.
5. **Логи** — каждый компонент имеет свой Logcat тег (`PresetBoot`, `PresetSwitch` и т.д.), структурированные поля для grep'а.

**Существующий тип ProfileSnapshot** в коде (был до TASK-65) — решим что с ним при имплементации (если никто не использует — удалим; если используется — переименуем).
