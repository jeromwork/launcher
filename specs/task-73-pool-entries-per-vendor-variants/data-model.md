# Data Model: Vendor-aware dispatch for OEM-sensitive Providers (TASK-73)

Canonical ECS types (`Component`, `Provider`, `Outcome`, `FailReason`, `LifecycleState`, `Vendor`) are defined in [`ecs.md`](../../docs/architecture/ecs.md) / real code — **not restated here**. This file covers only the **new types this task introduces**. `VendorRecipeCatalogue` is the only persisted/wire type; everything else is runtime-only.

## Wire format (new)

```kotlin
// core/src/commonMain/kotlin/com/launcher/preset/model/VendorRecipeCatalogue.kt  (domain)
@Serializable
data class VendorRecipeCatalogue(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    // key = Component's existing @SerialName discriminator, e.g. "LauncherRole"
    val entries: Map<String, Map<String, VendorOverride>> = emptyMap(),
    // outer key = componentType discriminator; inner key = Vendor.name (raw string,
    // parsed leniently via Vendor.entries.find{it.name==key}, unknown -> skipped, FR-007)
) {
    companion object { const val CURRENT_SCHEMA_VERSION: Int = 1 }
}

@Serializable
data class VendorOverride(
    val intentAction: String? = null,
    val intentPackage: String? = null,
    val intentClassName: String? = null,
    val intentCategory: String? = null,
    val fallbackTextKey: String? = null,
)
```

- Mirrors the existing `Pool`/`Preset` pattern exactly (`schemaVersion` + companion `CURRENT_SCHEMA_VERSION` const, see `Pool.kt:28-40`).
- `entries` outer key reuses `Component`'s own `@SerialName` discriminator — no new id scheme (FR-006). Only `"LauncherRole"` is populated in v1 (FR-008).
- Unknown outer/inner keys (unrecognized `componentType` or `Vendor.name`) are dropped during parse, not fatal (FR-007) — same intent as TASK-131's lenient-reader principle, applied pre-emptively since this is a brand-new format's first version.
- No `check` fields — v1 only needs `apply()`-time intent selection; `check()` stays platform-generic (Xiaomi/Huawei/Samsung do not need a different *check* strategy for HOME role, only a different *apply* path). If a future vendor needs a check override, add `checkX` fields additively — no current consumer, not built now (rule 4 Test 1).

## Ports (new)

```kotlin
// core/src/commonMain/kotlin/com/launcher/preset/port/VendorDetector.kt  (domain)
interface VendorDetector {
    fun detect(): Vendor
}
```
```kotlin
// core/src/commonMain/kotlin/com/launcher/preset/port/VendorRecipeSource.kt  (domain)
interface VendorRecipeSource {
    suspend fun loadCatalogue(): VendorRecipeCatalogue
}
```

- `VendorDetector.detect()` is synchronous — `Build.MANUFACTURER` is a static field, no I/O.
- Both ports mirror the existing one-port-one-artifact convention (`PoolSource`, `PresetSource`, `HintPoolSource`) — no shared `ConfigSource` abstraction invented (see research.md R1 context, and `docs/architecture/pool-naming.md:1-6` which already flags `ConfigSource` as non-existent superseded vocabulary).

## Adapters (new, androidMain / app)

```kotlin
// core/src/androidMain/kotlin/com/launcher/preset/adapter/AndroidVendorDetector.kt
class AndroidVendorDetector : VendorDetector {
    private val aliasTable = mapOf(
        "redmi" to Vendor.Xiaomi,
        "poco" to Vendor.Xiaomi,
        // extend additively as new sub-brands are confirmed (Clarifications #2)
    )
    override fun detect(): Vendor {
        val manufacturer = Build.MANUFACTURER.lowercase()
        aliasTable[manufacturer]?.let { return it }
        return Vendor.entries.find { it.name.equals(manufacturer, ignoreCase = true) }
            ?: Vendor.GenericAndroid
    }
}
```
```kotlin
// app/src/main/java/com/launcher/app/preset/task120/adapter/BundledVendorRecipeSource.kt
class BundledVendorRecipeSource(private val context: Context) : VendorRecipeSource {
    // same json { classDiscriminator="type"; ignoreUnknownKeys=true } + assets read
    // pattern as BundledPoolSource.kt:16-29; unknown Vendor.name / componentType keys
    // dropped in a post-decode filter step (FR-007), not by ignoreUnknownKeys alone
    // (that only covers unknown *fields*, not unknown *map keys*).
}
```

## `LauncherRoleProvider` — extended, not replaced

```kotlin
// app/src/main/java/com/launcher/app/preset/task120/provider/LauncherRoleProvider.kt
class LauncherRoleProvider(
    private val context: Context,
    private val currentActivity: () -> Activity? = { null },
    private val vendorDetector: VendorDetector,        // NEW
    private val vendorRecipes: VendorRecipeSource,      // NEW
    private val gmsAvailability: GmsAvailabilityPort,   // NEW — Huawei GMS-branch only
) : Provider<Component.LauncherRole> {
    // check(): unchanged logic, still returns Outcome.Ok / Outcome.NeedsApply.
    // apply(): NEW fallback order —
    //   1. vendorRecipes.loadCatalogue().entries["LauncherRole"]?.get(vendorDetector.detect().name)
    //      -> build Intent from intentAction/intentPackage/intentClassName/intentCategory
    //   2. existing generic RoleManager / ACTION_MAIN+CATEGORY_HOME path (unchanged)
    //   3. resolveActivity() == null on both -> Outcome.Failed(FailReason.InternalError(
    //        messageKey = override?.fallbackTextKey ?: "launcher_role.fallback.generic",
    //      ))
}
```

- No new `Outcome`/`FailReason` variant (research.md R2) — reuses `FailReason.InternalError`.
- `gmsAvailability` consulted only inside the Huawei branch, per Clarifications #3 — not part of `Vendor` derivation.

## What is NOT introduced

- **No `ConfigSource`/`ConfigKind`** — doesn't exist in code; not invented here (research.md R1 context).
- **No new `Outcome`/`FailReason` variant** — `InternalError(messageKey)` already fits (research.md R2).
- **No new `Component` subtype** — `LauncherRole` is unchanged; only its `Provider` gains dependencies.
- **No `HandlerKey.vendor`/`runtimeVendor` wiring change** — stays `null`, untouched (research.md R1 decision).
- **No AlertDialog / new UI widget** — reuses TASK-69's `ApplyResult.Failed` → Settings row rendering.

## Invariants honoured

- **ecs.md §4 item 7**: no edits to `ReconcileEngine`/`ProviderRegistry`/`ProfileFactory`.
- **ecs.md §10**: UI depends only on ports; `Provider` is an adapter, never shows UI itself (research.md R2).
- **Rule 5** (wire format versioning): `VendorRecipeCatalogue.schemaVersion` present from commit 1.
- **Rule 1/2** (domain isolation / ACL): `Build.MANUFACTURER` read confined to `AndroidVendorDetector`; `LauncherRoleProvider` remains the sole ACL wrapper around `RoleManager`/`Intent`.

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Коротко для владельца

Новые типы этой задачи (сама ECS-модель — в `ecs.md`, не дублируем):
- **`VendorRecipeCatalogue`** — файл `vendor-recipes.json`: для каждого типа настройки (`"LauncherRole"`) и каждого производителя (`Xiaomi`/`Huawei`/`Samsung`) — какой intent запускать и какой текст показать, если ничего не сработало.
- **`VendorDetector`** — определяет производителя устройства (`Build.MANUFACTURER`), с таблицей алиасов (Redmi/POCO считаются Xiaomi).
- **`VendorRecipeSource`** — читает `vendor-recipes.json` из assets (по образцу того, как уже читаются `pool.json`/`preset.json`).
- **`LauncherRoleProvider`** — существующий класс, просто получает две новые зависимости и пробует: свой intent для vendor'а → обычный Android-путь → честная ошибка с текстом, если ничего не сработало.

Важное: **никакого нового способа показать диалог не придумываем** — текст ошибки идёт через уже существующий канал (`FailReason.InternalError` → тот же путь, что Settings-экран из TASK-69 уже использует для показа «не получилось, вот что делать»). Вендорский диспетчер `ProviderRegistry` (уже есть в коде, но выключен) **не трогаем** — он для другого случая (когда нужна совсем другая реализация под vendor, не просто другой intent).
<!-- NOVICE-SUMMARY:END -->
