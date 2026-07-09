---
id: TASK-71
title: Wizard Hidden Steps and Defaults
status: Draft
assignee: []
created_date: '2026-06-30 20:00'
updated_date: '2026-07-09 10:58'
labels:
  - phase-2
  - foundation
  - wizard
  - follows-task-65
milestone: m-1
dependencies:
  - TASK-65
  - TASK-120
priority: low
ordinal: 71000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Preset author (тот, кто собирает preset) хочет некоторые шаги wizard'а **скрыть** и **применить дефолтное значение автоматически**. Пример: «размер шрифта 16pt — не спрашивай пользователя, применяй сразу». При этом в Settings та же настройка остаётся visible (пользователь может позже поменять).

TASK-71 добавляет:
- Per-pool-entry метаданные: `hideInWizard: Boolean`, `defaultValue: ...`, `showInSettings: Boolean` (это и для TASK-69 актуально).
- Логика wizard'а: если `hideInWizard=true` + есть `defaultValue` → apply silently при wizard start, не показывать step.
- Логика settings: если `showInSettings=true` → entry виден в Settings даже если скрыт в wizard.

## Зачем

Сейчас wizard показывает все required настройки. Это слишком много для UX-настроек, у которых есть разумный default. Workspace может хотеть «применить default font scale без спроса» — сейчас это требует hardcode.

## Что входит технически (для AI-агента)

- Расширение `StepEntry` / `PoolEntry` метаданными.
- `WizardEngine.computePending` учитывает `hideInWizard` + `defaultValue` → автоматически apply, не возвращает в pending.
- `SettingsScreenComposer` (TASK-69) учитывает `showInSettings`.
- Validation: нельзя `hideInWizard=true` без `defaultValue`.

## Состояние

**Planned.** Возникла в clarify-фазе TASK-65. Не в scope TASK-65 (TASK-65 строит только generic engine + один pool entry с UIFont для demo, без визибильность-flags).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Будут заданы при /speckit.specify запуске
<!-- AC:END -->
