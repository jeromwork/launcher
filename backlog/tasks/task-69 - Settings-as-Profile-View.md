---
id: TASK-69
title: Settings as Profile View
status: Verification
assignee: []
created_date: '2026-06-30 20:00'
updated_date: '2026-07-18 21:30'
labels:
  - phase-2
  - foundation
  - settings
  - ui
  - follows-task-65
milestone: m-1
dependencies:
  - TASK-65
  - TASK-120
  - TASK-136
references:
  - specs/task-69-settings-as-profile-view/
priority: medium
ordinal: 69000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Сейчас Settings UI приложения — отдельный hardcoded экран. TASK-69 превращает его в **второй view на тот же Profile** (первый view — Wizard). Settings показывает применённые настройки + позволяет менять то, что разрешено менять; Wizard ведёт по тем, что ещё не настроены. Оба — projection одного Profile (=applied Preset + bindings + cache).

**Что происходит**:
1. Открыл Settings → читается текущий Profile.
2. Для каждой настройки из applied Preset рендерится строка / карточка с current value + кнопка «Изменить».
3. Изменение → mini-wizard на этот один шаг (если требует Android-action) либо inline edit (UI-customization).
4. Per-entry метаданные говорят какую настройку показывать в Settings vs скрывать (Preset author может настраивать).

## Зачем

Сейчас Settings растёт hardcoded, каждое поле — Compose-функция. Когда добавится workspace preset с другим набором настроек — Settings либо станет «общим винегретом», либо потребует кода под каждый preset (= нарушение Article VII §13). TASK-69 решает проблему до того, как она усугубится.

## Что входит технически (для AI-агента)

Модель ECS не описываем здесь — источник истины [`docs/architecture/ecs.md`](../../docs/architecture/ecs.md) (+ скилл `ecs`). Финальная архитектура задачи (после speckit-цикла + mentor-сессии 2026-07-18):

- **Порт `SettingsGateway`** (`observe(): Flow<SettingsView>` + `apply(poolRef, params)`) — VM зависит только от него, `ReconcileEngine` живёт **за** портом как адаптер `EngineSettingsGateway`. Экран движок напрямую не дёргает.
- **`SettingsPresentationBuilder`** (домен): `Profile + Preset.settingsMap → SettingsView` (проекция). Сделан по форме будущего общего Home+Settings слоя, но **Home не трогаем** (unification — additive позже).
- **`SettingsView` / `SettingRow` / `AppOperation`** — готовый дескриптор для экрана (значение, статус `LifecycleState`, выведенная editability, + app-операции). Runtime, не сохраняется.
- **Editability выводится** (есть ли in-app провайдер), **не хранится** — поле `editableInSettings` отклонено (rule 4); wire-формат `settingsMap` НЕ меняется.
- **Профиль читает Settings как проекцию**; презентация (`settingsMap`) берётся из пресета в рантайме (инвариант I2 ecs.md — профиль самодостаточен для поведения+home, но не для презентации).
- **Поглощение legacy**: старый `SettingsScreen` (Decompose) сводится к одному экрану на `SettingsActivity`; не-профильные пункты (смена пресета, pairing-QR, сопряжённые устройства, сброс данных) переносятся как `AppOperation`-действия, не как компоненты.
- **JSON-driven рендер отложен в TASK-133**; экран пока обычный Compose поверх готового `SettingsView`.

## Состояние

**Verification — имплементация завершена и запушена 2026-07-18** (34/34 задачи tasks.md, 7 фаз, 4 коммита на ветке `task-69-settings-as-profile-view`).

- **Constitution 8/8 PASS**, cross-artifact trace чист, чек-листы чисты. Analyze verdict был READY-WITH-CAVEATS — оба caveat закрыты в коде: T069-020 (одна навигация — `SettingsActivity`, legacy Decompose-экран удалён) и эмуляторный прогон (см. ниже).
- **Эмуляторная проверка пройдена** (`Medium_Phone_API_36.1`, 2026-07-18): нашла и починила 2 реальных бага — краш вложенного `LazyColumn` (`PendingChecklistScreen`) и неверное расположение строк `StringResolver` (были в `composeResources`, реальный адаптер читает `app/res`; заодно починен тот же старый баг у трёх pre-existing ключей). Живьём подтверждено: один экран, проекция профиля с верными i18n/состояниями, in-app правка шрифта и панели инструментов (live round-trip), честный показ Failed-состояния без «мёртвой» кнопки, app-операции, TalkBack-семантика.
- **Открытый разрыв, обнаруженный на эмуляторе (не баг кода TASK-69)**: ни один из 3 bundled-пресетов не ссылается на `LauncherRole`/`StatusBarPolicy`/`Language` в своём `settingsMap`/`activeComponents` — соответствующие строки физически не могут появиться на экране сегодня. Значит AC #2 (тема/язык) и AC #3 (системный диалог) не проверяются end-to-end до тех пор, пока эти записи не появятся в bundled-контенте — отдельная follow-up задача по контенту, не по коду. Детали — `specs/task-69-settings-as-profile-view/tasks.md` Phase 7 findings.
- Остаётся: `T069-034` [deferred-physical-device] — OEM launcher-role/status-bar на Xiaomi 11T.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] Экран настроек показывает то, что реально настроено на устройстве: для каждого пресета — свой набор строк, с текущими значениями, без правок кода под конкретный пресет
- [ ] #2 [hand] Пользователь меняет размер шрифта (тему, язык, состав панели) прямо в настройках; изменение видно сразу и сохраняется после перезапуска — шрифт и панель подтверждены живьём (live round-trip); тема/язык не проверены (нет таких строк ни в одном bundled-пресете); сохранение-после-перезапуска не пере-проверено этой сессией (архитектурно гарантировано DataStore-хранилищем `ProfileStore`, но не показано вручную)
- [ ] #3 [hand] Настройка, требующая системного диалога, открывается одним шагом, а не прогоном всего визарда; отмена не ломает прежнее состояние — код/тесты готовы (тот же путь `RunMode.Single`, что и у визарда), но не воспроизводимо на реальном устройстве: ни один bundled-пресет не создаёт `LauncherRole`/`StatusBarPolicy` в профиле
- [x] #4 [hand] Незавершённые и сбойные настройки видны на экране: пользователь понимает, что не доделано, и может доделать это отсюда
- [x] #5 [hand] Убрав запись из settingsMap пресета, строка исчезает с экрана — без изменений в коде
- [ ] #6 [auto:deferred-physical-device] OEM launcher-role / status-bar на Xiaomi 11T (T069-034)
<!-- AC:END -->

<!-- SECTION:VERIFICATION_PENDING:BEGIN -->
PR открыт 2026-07-18 (branch `task-69-settings-as-profile-view`, 4 коммита). Pending AC:
- #2 (частично) — тема/язык + explicit restart-persistence re-check.
- #3 — системный диалог: нужен bundled-пресет со строкой `LauncherRole`/`StatusBarPolicy` в `settingsMap`, чтобы вообще было что тапать (follow-up контент-задача, не код).
- #6 (auto:deferred-physical-device) — Xiaomi 11T прогон, TASK-128.

Recovery: как только появится bundled-пресет с этими записями (или мы решим, что MVP не требует демонстрации всех трёх in-app типов) — #2/#3 закрываются повторным emulator-прогоном; #6 — на физическом устройстве.
<!-- SECTION:VERIFICATION_PENDING:END -->
