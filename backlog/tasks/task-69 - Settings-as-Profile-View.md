---
id: TASK-69
title: Settings as Profile View
status: In Progress
assignee: []
created_date: '2026-06-30 20:00'
updated_date: '2026-07-17 04:23'
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

**In Progress — speckit-цикл пройден 2026-07-18** (specify → clarify → scenarios → plan → tasks → analyze). Спека `specs/task-69-settings-as-profile-view/` (spec.md, plan.md, data-model.md, tasks.md — 34 задачи / 7 фаз, analyze-report.md).

- **Constitution 8/8 PASS**, cross-artifact trace чист, чек-листы чисты. **Analyze verdict: READY-WITH-CAVEATS.**
- Caveat (не блокер): свести две навигации к одной (legacy на Decompose vs новый на Activity) — прописано в T069-020.
- Deferred: эмуляторные прогоны (US3 диалог, смена языка, TalkBack) + OEM Xiaomi — задача останется в Verification, пока их не закроют на железе.
- **Код — в отдельной сессии** (правило для крупных one-way-door фич). Depends: TASK-136 (ECS foundation, в Verification).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Экран настроек показывает то, что реально настроено на устройстве: для каждого пресета — свой набор строк, с текущими значениями, без правок кода под конкретный пресет
- [ ] #2 Пользователь меняет размер шрифта (тему, язык, состав панели) прямо в настройках; изменение видно сразу и сохраняется после перезапуска
- [ ] #3 Настройка, требующая системного диалога, открывается одним шагом, а не прогоном всего визарда; отмена не ломает прежнее состояние
- [ ] #4 Незавершённые и сбойные настройки видны на экране: пользователь понимает, что не доделано, и может доделать это отсюда
- [ ] #5 Убрав запись из settingsMap пресета, строка исчезает с экрана — без изменений в коде
<!-- AC:END -->
