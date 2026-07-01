---
id: TASK-73
title: Pool entries per-vendor variants — CheckSpec/ApplySpec dispatch
status: Draft
assignee: []
created_date: '2026-07-01 04:15'
labels:
  - phase-3
  - area-preset
  - area-oem
  - foundation-followup
milestone: m-2
dependencies:
  - TASK-65
references:
  - specs/task-65-profile-composition-foundation-v2/
priority: high
ordinal: 73000
---

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Сейчас у нас есть **пул настроек** (pool) — список того, что должно быть настроено на устройстве, чтобы preset (простой лаунчер, workspace, и т.д.) заработал. У каждой настройки в пуле есть две части:

1. **CheckSpec** — «как проверить, что настройка включена?» (например: «спроси у RoleManager, кто default HOME»)
2. **ApplySpec** — «как эту настройку включить?» (например: «открой системный диалог выбора default launcher через `RoleManager.createRequestRoleIntent`»)

**Проблема:** и check, и apply — **разные на разных Android-устройствах**:

- На чистом Pixel — `RoleManager` работает штатно.
- На **Xiaomi MIUI** — `RoleManager` есть, но есть ещё свой поверх него экран «Разрешения / Приложения по умолчанию», и часто через intent от Google открывается **не туда**.
- На **Huawei без GMS** (EMUI/HarmonyOS) — `RoleManager` может быть, но нет Google-путей, свои системные диалоги, другой namespace для permission strings.
- На **Samsung One UI** — есть Knox-специфичные пути (device policy) параллельно с обычным API.
- На **Oppo/Vivo/OnePlus/Honor** — свои настройки уведомлений, отдельные экраны battery optimization, свой autostart manager.

Что происходит по шагам сейчас (нормальный сценарий, Pixel):
1. Preset ссылается на pool entry `android.role.home`.
2. `CheckSpec.AndroidRole("android.app.role.HOME")` спрашивает `RoleManager.isRoleHeld(HOME)` — работает.
3. Если not held → `ApplySpec.AndroidRoleRequest(HOME)` открывает системный диалог — работает.

Что происходит **на Xiaomi**:
1. Тот же pool entry.
2. Check работает.
3. Apply открывает диалог, но пользователь тапает «Да» — MIUI не всегда его применяет; нужен fallback на MIUI-специфичный intent или toast с текстовой инструкцией.

Что происходит **на Huawei без GMS**:
1. Тот же pool entry.
2. Check может throw или вернуть Indeterminate.
3. Apply intent может не resolve'иться → сейчас мы просто показываем toast, но текст generic — пользователь не знает куда идти на своём устройстве.

**Что должно быть:**
- Один и тот же pool entry `android.role.home` (сохраняем идентичность и переносимость preset'ов).
- **Vendor-специфичные варианты** check и apply, dispatch по `Build.MANUFACTURER` / `Build.BRAND` / GMS availability.
- Fallback chain: сначала пробуем vendor-native → если нет → generic Android API → если и это нет → structured user-facing instruction.
- Всё это загружается **как данные** (recipe catalogue), а не hardcode в APK — иначе каждый новый Android release / OEM update требует новой сборки.

## Зачем

- **UX real-world**: сейчас preset'ы «работают» только на устройствах, похожих на Pixel. Xiaomi/Huawei/Samsung — большинство рынка senior-family target audience — получают broken UX без обратной связи.
- **Foundation completeness**: TASK-65 задекларировал generic engine, но engine работает над данными; если данные vendor-blind, engine бесполезен.
- **Community-authored presets**: любой автор preset (внешний, из marketplace будущего) не должен думать про OEM matrix — только про поведение. OEM matrix — забота инфраструктуры, не автора.

## Что входит технически (для AI-агента)

- **`VendorProfile` sealed value** в domain — `Pixel`, `Xiaomi`, `Huawei`, `Samsung`, `Generic` (fallback), derived from `Build.MANUFACTURER` + optional GMS presence.
- **`CheckSpec` и `ApplySpec` sealed hierarchy расширяется variant polymorphism**: каждая variant может нести `perVendor: Map<VendorProfile, SpecVariantOverride>` — если vendor override есть, dispatch туда, иначе default.
  - Пример: `CheckSpec.AndroidRole(role, perVendor = mapOf(Xiaomi to CheckSpec.MiuiRoleCheck(...)))`.
- **Handler registry** (`CheckHandler`, `ApplyHandler`) уже есть — расширить dispatch через `VendorProfile`.
- **Recipe catalogue wire format** — `vendor-recipes.json` который shipping'ся отдельно от APK, загружается через существующий `ConfigSource` port (rule 9 shareability, ConfigKind.VendorRecipes). Recipe = `Map<PoolEntryId, Map<VendorProfile, {check, apply, fallbackText}>>`.
- **Fallback chain** в `ApplyHandler`: try vendor-specific intent → if `resolveActivity == null` → try generic → if all fail → show `AlertDialog` with текстовой инструкцией (localized, per-vendor).
- **Detection cost**: `Build.MANUFACTURER` дешёвый; GMS check уже есть через `GmsAvailabilityPort`.
- **CI OEM matrix**: Firebase Test Lab job (`gcloud firebase test android run`) на minimum {Pixel 8, Samsung Galaxy S24, Xiaomi Redmi Note 13} на любой PR trigger'ится через label `oem-matrix-required`. Non-blocking для draft PR, mandatory для merge to main.

## Состояние

- **Draft** — задача сформулирована после TASK-65 review (2026-07-01) когда выяснилось что pool CheckSpec/ApplySpec — vendor-blind по дизайну.
- Не блокирует ни один текущий feature — TASK-65 foundation работает; TASK-73 добавляет vendor dispatch слой поверх.
- Логическое место в roadmap — **Phase 3** (после того как preset composition foundation стабилизируется и появятся первые reports «на Xiaomi не открывается settings screen»).

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 На новом OEM (Huawei без GMS, Samsung One UI, Xiaomi MIUI) CheckSpec отвечает корректно, а не throws
- [ ] #2 ApplySpec запускает правильный Settings screen на каждом OEM, иначе fallback UI с текстовой инструкцией
- [ ] #3 Один pool entry (например `android.role.home`) может иметь per-vendor override без дублирования всей записи
- [ ] #4 Скачивание vendor-специфичных вариантов (recipe-catalogue style) без выкатки нового APK
- [ ] #5 OEM matrix test в CI прогоняет минимум 3 vendor через Firebase Test Lab на любое изменение pool
<!-- AC:END -->

---

## Готовый промт для `/speckit.specify`

```
Разработать TASK-73: Pool entries per-vendor variants — CheckSpec/ApplySpec dispatch.

ЧТО СТРОИМ
Расширить CheckSpec/ApplySpec sealed hierarchy vendor-aware variants и добавить
VendorProfile-driven dispatch в CheckHandler/ApplyHandler. Vendor-специфичные
overrides shipping через отдельный wire format (vendor-recipes.json) поверх
существующего ConfigSource port.

ЗАЧЕМ
После TASK-65 pool entries работают на устройствах, похожих на Pixel. Xiaomi
MIUI, Huawei без GMS, Samsung One UI, Oppo/Vivo/OnePlus — большинство senior
target audience — получают broken UX (settings screen не открывается или
открывается не туда, permission dialog silent-deny, RoleManager throws).
Community-authored presets в будущем marketplace должны быть vendor-blind;
OEM matrix — забота инфраструктуры.

SCOPE ВКЛЮЧАЕТ
- VendorProfile sealed value в domain (Pixel / Xiaomi / Huawei / Samsung /
  Oppo / Vivo / OnePlus / Honor / Generic).
- VendorProfileProvider port + Android adapter через Build.MANUFACTURER +
  GmsAvailabilityPort.
- CheckSpec.<Variant>(perVendor: Map<VendorProfile, CheckSpec>? = null).
- ApplySpec.<Variant>(perVendor: Map<VendorProfile, ApplySpec>? = null, fallbackTextKey: String? = null).
- CheckHandler / ApplyHandler dispatch: try vendor override → try default → for
  ApplyHandler additionally fall through to structured AlertDialog with
  localized instruction text.
- ConfigKind.VendorRecipes + BundledConfigSource assets/vendor-recipes/ path.
- Recipe wire format: schemaVersion + Map<poolEntryId, Map<vendorId, VendorOverride>>.
- Firebase Test Lab CI job on PR label `oem-matrix-required` running instrumentation
  tests on Pixel 8 + Samsung Galaxy S24 + Xiaomi Redmi.
- Minimum recipe coverage for TASK-65 pool entries: android.role.home (3 vendors),
  android.permission.POST_NOTIFICATIONS (3 vendors), ui.font.large (single —
  Android API stable).

SCOPE НЕ ВКЛЮЧАЕТ
- Автоматическое определение при runtime какие recipe качать (сначала bundle все,
  оптимизация — отдельная задача).
- Vendor override для сторонних SDK (только для системных Android surfaces).
- Physical device shelf procurement (owner decision).

DEPENDENCIES
- TASK-65 (foundation for CheckSpec/ApplySpec + ConfigSource extensibility).
- TASK-49 (GmsAvailabilityPort — используется для GMS-aware vendor detection).

ACCEPTANCE CRITERIA
- [ ] На Xiaomi Redmi (MIUI) tap на «Настроить HOME launcher» открывает
      Settings → Приложения → По умолчанию → Домашний экран (не generic ROLE dialog).
- [ ] На Huawei без GMS (emulator / Firebase Test Lab с EMUI image) CheckSpec.AndroidRole
      возвращает NotApplied вместо throw; ApplySpec показывает AlertDialog с текстом
      «Откройте Настройки → Приложения → По умолчанию».
- [ ] `vendor-recipes.json` может добавить override для нового vendor без изменения
      Kotlin кода.
- [ ] Firebase Test Lab CI job на 3 устройствах проходит для TASK-65 pool entries
      после добавления recipes.
- [ ] Roundtrip test для vendor-recipes wire format (write → read → equals).

LOCAL TEST PATH
- Unit test: VendorProfileProvider dispatches правильно given Build.MANUFACTURER stub.
- Unit test: CheckHandler pipeline picks vendor override когда есть, default когда нет.
- Instrumentation test (Xiaomi лично): full flow ROLE_HOME + POST_NOTIFICATIONS с
  MIUI-specific recipes.
- Firebase Test Lab job: 3 OEM smoke.

CONSTITUTION GATES
- Article VII §16 (domain isolation) — VendorProfile в domain, `Build.MANUFACTURER`
  чтение в androidMain adapter.
- Article XI (Minimum Viable Architecture) — если только 1 vendor override нужен,
  всё равно architecture есть под будущие; но не строить полный marketplace UI сейчас.
- CLAUDE.md rule 9 (shareability) — vendor-recipes.json ships как portable artifact.

EFFORT
S/M (1-2 недели) — 20-30 tasks; wire format + dispatch + 3 vendor overrides + CI.
```
