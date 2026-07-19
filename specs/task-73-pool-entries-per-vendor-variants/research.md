# Research: Vendor-aware dispatch for OEM-sensitive Providers (TASK-73)

Two real decisions were made while grounding this spec against the current codebase (`docs/architecture/ecs.md` + direct code inspection). Both qualify as one-way-door-adjacent per CLAUDE.md rule 3: reversible, but expensive to reverse once `vendor-recipes.json` ships and providers depend on the chosen shape.

## R1 — Where does vendor variance live: `HandlerKey.vendor` tier, or inside the `Provider`?

**Context**: `ProviderRegistry`/`HandlerKey` already implement a 3-tier fallback — `(type, platform, vendor) → (type, platform, null) → (type, null, null) → NoOp` (`core/src/commonMain/kotlin/com/launcher/preset/port/ProviderRegistry.kt:16-30`). The vendor tier is real, compiled, tested code — but currently unpopulated: `PresetModule.kt:121` wires `DefaultProviderRegistry(handlers, runtimePlatform = "Android", runtimeVendor = null)`, and no `HandlerKey` anywhere registers a non-null `vendor`.

### Option A — Compiled `Provider` per `(Component, Vendor)`, using the existing `HandlerKey.vendor` tier

`XiaomiLauncherRoleProvider`, `HuaweiLauncherRoleProvider`, `SamsungLauncherRoleProvider` — each a full `Provider<Component.LauncherRole>` implementation, registered via Hilt `@IntoMap` under `HandlerKey(Component.LauncherRole::class, "Android", Vendor.Xiaomi)` etc. "Finishes" wiring that already half-exists.

**Rejected because**: every new `(Component, Vendor)` override requires a new compiled class + DI registration + APK release. This directly contradicts spec.md FR-005/SC-003 ("a new override for an already-known `Vendor` ships without a code change") — a requirement the owner explicitly reaffirmed during `/speckit.clarify` (Clarification #1: "SC-003 applies to new overrides of already-known vendors, not to adding a brand-new vendor value"). Also in tension with the project's general anti-catalogue-explosion stance (`PoolAntiExplosionTest`: "≥6 declarations per Component subtype fails — use `paramsOverride` instead of a catalogue").

### Option B — Recipe data consulted *inside* one compiled `Provider` (chosen)

`LauncherRoleProvider` stays a single class. It gets two new constructor dependencies — `VendorDetector` (current `Vendor`) and `VendorRecipeSource` (parsed `vendor-recipes.json`) — and consults them at the top of `apply()`/`check()` to pick an intent. Adding a new vendor override for `LauncherRole` = one new JSON entry, zero Kotlin.

**Why this wins**:
- Satisfies FR-005/SC-003 exactly as clarified.
- `docs/architecture/ecs.md` §4 item 7 ("Adding a new Component" checklist): *"No changes in `ReconcileEngine`/`ProviderRegistry`/`ProfileFactory` — if you must edit engine code, the abstraction is broken."* Option A would require editing `PresetModule.kt`'s `DefaultProviderRegistry` wiring (populating `runtimeVendor`) and adding new `HandlerKey` entries — touching exactly the layer §4 says not to touch for this kind of change. Option B touches only the `Provider` implementation, matching the checklist's intended shape (step 3: "Implement `MyNewTypeProvider`").
- No fitness test currently penalizes internal vendor-branching inside a `Provider` (checked: no `PeripheralAdapterRegistry`/`vendorApp` machinery exists in real code — that pattern, documented in the superseded `specs/task-120-.../contracts/provider-port.md`, was declared for a *different*, unimplemented axis — component-embedded peripheral-SDK selection, e.g. a hypothetical `BloodPressureDevice.vendorApp` field — not device/OEM vendor).

**Exit ramp** (if Option B turns out wrong — e.g. a future vendor needs a *structurally different* apply strategy, not just a different intent target): the `HandlerKey.vendor` tier is untouched and still available; a future task can register a compiled `Provider` for that one `(Component, Vendor)` pair without touching `LauncherRoleProvider`'s recipe-consulting code path. Coexistence is free — Option B does not foreclose Option A for a future component that genuinely needs it. Cost to add: one new class + one DI line, same cost it would have cost today.

## R2 — How does the fallback instruction text reach the user?

**Context**: `Provider.apply()` returns `Outcome`, nothing else (`core/src/commonMain/kotlin/com/launcher/preset/port/Provider.kt:7-9`). A `Provider` implementation lives in `app/` (Android-touching) but is architecturally an **adapter**, not a UI layer — it cannot itself pop a `Dialog` and suspend for a user tap without inventing a continuation-bridging pattern that has no precedent anywhere in this codebase.

### Option A — `Provider.apply()` shows an `AlertDialog` directly (spec.md's original framing, now rejected)

Requires wrapping `AlertDialog.show()` + `setOnClickListener` in a `suspendCancellableCoroutine`, holding a live `Activity` reference across a suspend boundary. No precedent; real complexity; blurs the adapter/UI boundary `ecs.md` §10 draws ("UI ↓ depends only on PORTS").

**Rejected because**: no existing `Provider` in this codebase renders UI. `Outcome.NeedsUserConfirmation` (`core/src/commonMain/kotlin/com/launcher/preset/model/Outcome.kt:18`) already exists precisely for "provider fired its intent but can't confirm — the *interactive path* asks the human" — the doc comment on that exact type says the UI-facing conversation is the caller's job, not the Provider's.

### Option B — Reuse `Outcome.Failed(FailReason.InternalError(messageKey))` → existing `ApplyResult.Failed` channel (chosen)

`FailReason.InternalError(messageKey: String, args: Map<String,String>)` (`core/src/commonMain/kotlin/com/launcher/preset/model/FailReason.kt:31-35`) already exists and is designed to carry an i18n key end-to-end (`toI18nKey()` returns `messageKey` verbatim for this variant). TASK-69 already wired `EngineSettingsGateway.apply()` to return `ApplyResult.Failed(reason: FailReason)` **immediately** on the interactive (`RunMode.Single`) path (`specs/task-69-settings-as-profile-view/data-model.md:14-18`). So: `apply()` returns `Outcome.Failed(FailReason.InternalError(fallbackTextKey))` → `ReconcileEngine` → `LifecycleState.Failed(reason)` on the entity (persisted path) **and** `ApplyResult.Failed(reason)` returned synchronously to the Settings ViewModel (interactive path) → the ViewModel resolves `reason.toI18nKey()` via `LocalizedResources` and shows it (snackbar/inline — a `tasks.md`-level UI decision, not an architecture one).

**Why this wins**: zero new UI machinery, zero new `Outcome`/`FailReason` variants, reuses a channel TASK-69 already built and tested for exactly this "tell the user what to do next" purpose.

**Exit ramp**: if `FailReason.InternalError.messageKey` ever proves too narrow (e.g. needs richer structured data than a key + string-map args), it is a `FailReason` subtype used only by this and other `Provider`s — widening it is a normal additive change (new field with default), not a wire-format break, since `LifecycleState.Failed`/`FailReason` are already `@Serializable` and additive-field-safe per rule 5.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Две развилки, обе решены в пользу «не трогать/не изобретать новое, переиспользовать то, что уже есть в коде».

**Конкретика, которую стоит запомнить:**
- **R1**: vendor-логика — внутри `LauncherRoleProvider` (recipe-данные), а НЕ через уже существующий, но выключенный `HandlerKey.vendor`-tier в `ProviderRegistry`. Причина: тот tier требует нового скомпилированного класса на каждый vendor = новый релиз, что ломает FR-005/SC-003 («новый override без пересборки APK»).
- Отказ обоснован цитатой из `ecs.md` §4 пункт 7: «никаких правок в `ReconcileEngine`/`ProviderRegistry`/`ProfileFactory`».
- Найденный в старом контракте TASK-120 «peripheral-vendor nested pattern» (`vendorApp`-поле, запрещённый `when`) — это **другая** ось (какой SDK внешнего устройства использовать), не про OEM-производителя; в реальном коде вообще не реализован, не мешает R1.
- **R2**: текст fallback-инструкции идёт через уже существующий `Outcome.Failed(FailReason.InternalError(messageKey))` → `LifecycleState.Failed` → `ApplyResult.Failed` (TASK-69), а НЕ через новый `AlertDialog`, показанный из `Provider` — тот не UI-слой и не может сам рисовать диалоги.
- Обе развилки имеют явный exit ramp: `HandlerKey.vendor`-tier остаётся доступным для будущего компонента, которому реально нужна другая реализация (не просто другой intent); `FailReason.InternalError` можно расширить полями аддитивно, если понадобится.

**На что смотреть с осторожностью:**
- Не путай `HandlerKey.vendor` (не используется этой задачей) с новыми `VendorDetector`/`VendorRecipeSource` (используются) — три разных «vendor»-механизма упомянуты в этом файле, легко перепутать при беглом чтении.
