---
id: TASK-127
title: HomeActivity config-load failure post-wizard (TASK-126 regression)
status: In Progress
assignee: []
created_date: '2026-07-13'
updated_date: '2026-07-13'
labels:
  - phase-2
  - home-screen
  - bug
  - regression
  - blocker
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

- **Диагностика:** grep logcat на `com.launcher.app` тэгах между FirstLaunchActivity finish и HomeActivity Error state; определить куда именно попадает загрузка config'а (`ConfigBackedFlowRepository`, `PresetRepository.getActivePreset()`, `HomeComponent.loadingState`).
- **Возможные root causes** (нужно уточнить через logs):
  - (а) новый `WizardViewModel.finish()` (TASK-126 T054/T055) НЕ вызывает `presetRepository.setActivePreset(...)` перед `startActivity(HomeActivity)`;
  - (б) manifest-driven wizard не создаёт ConfigDocument в `/config/current`;
  - (в) reconcile применил preset в неполной форме (`ReconcileState` неверно закоммитил);
  - (г) HomeComponent init читает preset до того как wizard-transaction commit'нулся.
- **Fix**: обеспечить что после `WizardHostActivity` completion — active preset persisted, ConfigDocument seeded (classic-6 или соответствующий для senior preset), HomeComponent Loading → Ready.
- **Локализация wizard'а**: отдельный AC — все user-visible строки нового wizard runtime резолвятся через `strings_wizard.xml` (не raw keys).

**Затронутые файлы (predicted):**
- `app/src/main/java/com/launcher/app/wizard/WizardHostActivity.kt` (создан в TASK-126 phase 2)
- `core/src/commonMain/kotlin/com/launcher/ui/wizard/WizardViewModel.kt` (TASK-126 T001/T050/T051)
- `core/src/commonMain/kotlin/com/launcher/domain/ReconcileState.kt`
- `core/src/commonMain/kotlin/com/launcher/adapters/config/ConfigBackedFlowRepository.kt`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 [hand] Fresh install + wizard on Xiaomi Redmi Note 11 → HomeActivity показывает preset content (плитки), не Error UI. Regression от TASK-52 закрыт для нового wizard runtime.
- [ ] #2 [hand] Wizard runtime строки локализованы через `strings_wizard.xml` (нет raw keys `wizard_*` в UI).
- [ ] #3 [hand] `HomeComponentLoadingStateTest` расширен новым сценарием: post-manifest-wizard reconcile → Ready state (не Error), покрывает регрессию.
<!-- AC:END -->

## Definition of Done

`In Progress → Done` через `pre-pr-backlog-sync` после fix + verification на Xiaomi 11 (тот же adb id `17f33878`, свежая установка).
