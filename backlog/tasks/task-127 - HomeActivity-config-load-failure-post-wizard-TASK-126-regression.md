---
id: TASK-127
title: HomeActivity config-load failure + LauncherPresentationBuilder bridge
status: In Progress
assignee: []
created_date: '2026-07-13'
updated_date: '2026-07-14'
labels:
  - phase-2
  - home-screen
  - bug
  - regression
  - blocker
  - architecture
milestone: m-1
dependencies:
  - TASK-126
references:
  - verification-evidence/task-128-xiaomi-fresh-07.png
  - verification-evidence/task-128-xiaomi-blocker-logcat.txt
priority: high
ordinal: 127000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Регрессия TASK-52 (ранее закрыт Done): после прохождения wizard'а на реальном устройстве Xiaomi Redmi Note 11 (Android, MIUI, adb id `17f33878`) `HomeActivity` НЕ загружает preset config и показывает Error UI «Не удалось загрузить настройки» с кнопками «Попробовать снова» / «Сбросить настройки и пройти заново».

TASK-52 закрыл этот класс багов через детерминированную state machine `HomeLoadingState` (Loading/Ready/Error) в `HomeComponent`. TASK-126 (Wizard runtime migration to Preset composition foundation) сломал контракт между новым wizard runtime и existing HomeActivity/HomeComponent — активный preset не поставлен или FlowRepository не получает config к моменту загрузки Home.

**Repro шаги (2026-07-13, ветка `task-126-wizard-runtime-migration`, commit f8a4d8b):**
1. `adb uninstall com.launcher.app.mock`
2. `./gradlew :app:installMockBackendDebug` — success.
3. Запустить app → FirstLaunchActivity → выбрать «Лаунчер для пожилого».
4. Auth choice step → «Настроить с нуля» (blank profile, local mode).
5. HOME role step → «Позже».
6. Новый wizard runtime показывает 4 шага с **нелокализованными** ключами: `wizard_step_of`, `wizard_component_font_size`, `wizard_component_sos`, `wizard_confirm`. Тапаем «wizard_confirm» через каждый шаг.
7. HomeActivity открывается → **Error UI «Не удалось загрузить настройки»** мгновенно, не Loading→Ready.

**Evidence:**
- `verification-evidence/task-128-xiaomi-fresh-07.png` — Error UI на Xiaomi.
- `verification-evidence/task-128-xiaomi-blocker-logcat.txt` — full logcat (43k строк).

**Side finding (отдельный от блокера, но зафиксировать):** новый wizard runtime показывает raw string keys вместо локализованных строк (`wizard_step_of`, `wizard_component_font_size`, `wizard_component_sos`, `wizard_confirm`). Локализация manifest-driven wizard'а не подключена — вероятно нужен entry в `strings_wizard.xml` или другой resource path.

## Зачем

Блокирует TASK-128 verification bucket #1 и #2 (item #2 недостижим без прохождения через HomeActivity в Settings). Блокирует merge TASK-126 в main — фича «wizard runtime migration» формально работает (wizard steps проходятся), но end-to-end путь «install → wizard → usable home screen» сломан. Повторяет паттерн TASK-52.

## Что входит технически (для AI-агента)

### Root cause (подтверждён диагностикой 2026-07-14)

`WizardHostActivity.onCompleted` вызывает `postWizardKioskApply.applyKiosk(profile)` и сразу открывает `HomeActivity` — но **ConfigDocument в `/config/current` не сидируется**. `ConfigBackedFlowRepository` читает через `configEditor.observeAppliedConfig(linkId)` — возвращает пустой список если ConfigDocument не записан. `HomeComponent` → Error state.

`InMemoryHomeScreenFacade` — подтверждённый dead-end: `MutableStateFlow<tiles>` никто не observes. `AppTileProvider.apply()` пишет в него, но до HomeScreen данные никогда не доходят.

### Phase 1 — LauncherPresentationBuilder port (core domain)

Новый порт в `core/src/commonMain/kotlin/com/launcher/preset/port/`:

```kotlin
interface LauncherPresentationBuilder {
    fun buildInitialConfig(profile: Profile, pool: Pool): ConfigDocument
}
```

**SlotKind — доступные значения** (`core/src/commonMain/kotlin/com/launcher/api/config/SlotKind.kt`):
- `Call(wireValue="call")` — звонок, args.contactId → /config.contacts[].id
- `Sms(wireValue="sms")` — SMS, args.contactId → /config.contacts[].id
- `OpenApp(wireValue="open-app")` — открыть приложение, args.packageName

**Маппинг `Profile.activeComponents → ConfigDocument` (MVP scope):**
- `Component.AppTile(packageName=X)` → `Slot(kind=SlotKind.OpenApp, args={"packageName": X})` ✅ прямой маппинг
- `Component.Sos(...)` → ⚠️ нет SlotKind.Sos. MVP: пропустить или `// TODO(sos-slot): SlotKind.Sos добавить в следующем PR, args={number: emergencyPhone}`. Sos-плитка в ConfigDocument = контакт с `Call` slot, но contactId нужно резолвить.
- `Component.FontSize(scale)` → `ConfigDocument.presetOverrides` (проверить наличие fontScale поля при реализации)
- `Component.Toolbar(...)` → не попадает в `flows[].slots[]`, отдельный механизм — `// TODO(toolbar-slot)` MVP deferred

Слоты группируются в `Flow(id="main-flow", title="", slots=[...])`.

**Inline decision — ConfigDocument priority policy:**
```kotlin
// TODO(admin-priority): local seed используется ТОЛЬКО при first launch (нет admin ConfigDocument).
// Post-pairing: admin ConfigDocument через Firestore имеет приоритет и перетирает local seed.
```

### Phase 2 — ConfigDocument seeding после wizard

`WizardHostActivity.onCompleted` (или `PostWizardKioskApply.applyKiosk`):
1. Вызвать `LauncherPresentationBuilder.buildInitialConfig(profile, pool)` → `configDocument`
2. Записать через `configEditor.updateDraft(linkId, configDocument)` + `configEditor.pushPending(linkId)`
3. `FakeConfigEditor` (`app/mock`) хранит in-memory, поддерживает write path
4. `ConfigBackedFlowRepository.observeAppliedConfig(linkId)` подхватывает автоматически
5. `HomeComponent` → `LoadingState.Ready`

`postWizardKioskApply.applyKiosk()` — диагностировать: делает ли что-то с ConfigDocument уже? Если да — интегрировать, не дублировать.

### Phase 3 — Локализация wizard strings

`strings_wizard.xml`: добавить ключи для `wizard_step_of`, `wizard_component_font_size`, `wizard_component_sos`, `wizard_confirm`. AndroidLocalizedResources уже существует как adapter.

### Затронутые файлы

- `core/src/commonMain/kotlin/com/launcher/preset/port/LauncherPresentationBuilder.kt` (новый)
- `app/.../preset/LauncherPresentationBuilderImpl.kt` (новый, Android adapter)
- `app/.../wizard/WizardHostActivity.kt` — добавить ConfigDocument seeding
- `app/.../preset/PostWizardKioskApply.kt` — диагностировать, интегрировать
- `app/src/main/res/values/strings_wizard.xml` — добавить wizard string keys
- `core/.../adapters/config/ConfigBackedFlowRepository.kt` — только чтение, не менять
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 [hand] Fresh install + wizard on Xiaomi Redmi Note 11 → HomeActivity показывает preset content (плитки), не Error UI. Regression от TASK-52 закрыт для нового wizard runtime.
- [ ] #2 [hand] Wizard runtime строки локализованы через `strings_wizard.xml` (нет raw keys `wizard_*` в UI).
- [ ] #3 [hand] `HomeComponentLoadingStateTest` расширен новым сценарием: post-manifest-wizard reconcile → Ready state (не Error), покрывает регрессию.
- [ ] #4 [hand] `LauncherPresentationBuilder` port объявлен в `core/preset/port/` (pure Kotlin, zero Android imports). Domain isolation checklist: нет `import android.*` в порте.
- [ ] #5 [hand] `LauncherPresentationBuilderImpl` конвертирует `Profile.activeComponents` → валидный `ConfigDocument` с непустым `flows[]`. Покрыт unit тестом: profile с AppTile + FontSize → ConfigDocument с Slot(APP) + presetOverrides.fontScale.
- [ ] #6 [hand] После `WizardHostActivity` completion ConfigDocument записан через `ConfigEditor` до `startActivity(HomeActivity)`. Проверяется: FakeConfigEditor.server не пуст после wizard flow в Robolectric тесте.
<!-- AC:END -->

## Definition of Done

`In Progress → Done` через `pre-pr-backlog-sync` после fix + verification на Xiaomi 11 (тот же adb id `17f33878`, свежая установка). AC #4-#6 verifiable via Robolectric/unit — не требуют physical device.
