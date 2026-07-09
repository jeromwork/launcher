---
id: TASK-69
title: Settings as Profile View
status: Draft
assignee: []
created_date: '2026-06-30 20:00'
updated_date: '2026-07-09 10:58'
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

- `SettingsViewModel` читает текущий Profile через `ProfileRepository`.
- `SettingsScreenComposer` строит UI динамически: для каждого pool entry в `preset.requires` + `preset.picks` рендерит карточку.
- Per-pool-entry метаданные: `showInSettings: Boolean`, `editableInSettings: Boolean`, `hideAfterApply: Boolean`.
- Reuse `WizardEngine.computePending` для status check.
- Wizard и Settings — разные projections одного source.

## Состояние

**Planned.** Возникла в clarify-фазе TASK-65 как natural extension. Не в scope TASK-65 (там только banner-reminders в Settings, не полная replacement существующего Settings экрана).

Contextual notes от TASK-65 clarify:
- Settings = view на тот же Profile что и Wizard.
- Per-entry метаданные позволяют Preset author настраивать что показывать в Settings vs скрывать (по аналогии с wizard hidden-steps в TASK-71).
- Settings UI должен поддерживать **в будущем** remote management (admin меняет настройки primary user'а через свой app) — TASK-70 + TASK-67 territory.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Будут заданы при /speckit.specify запуске
<!-- AC:END -->
