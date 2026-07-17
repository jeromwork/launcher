---
id: TASK-133
title: Configurable Wizard/Settings presentation via JSON (разморозка hardcoded вида)
status: Draft
assignee: []
created_date: '2026-07-16 11:29'
updated_date: '2026-07-17 04:06'
labels:
  - phase-5
  - preset
  - ui
  - preset-authoring
milestone: m-4
dependencies: []
priority: low
ordinal: 133000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Сегодня внешний вид Wizard и Settings в основном зашит в коде: какие шаги — из пресета, но «как это выглядит» (тема, кнопки, компоновка списка настроек) — hardcoded. Владелец хочет: отображение Wizard и Settings тоже описывается JSON'ом и может отличаться от пресета к пресету. Стратегия: «заморозить базу сейчас, разморозить через JSON, когда понадобится» — этот task и есть точка разморозки.

## Зачем

Пресет «simple launcher» и пресет «клиника» могут требовать разного вида мастера и настроек (крупнее шрифты, меньше шагов на экран, другой тон). Если вид зашит — каждый вариант = правка кода; если в JSON — это часть shareable пресета (rule 9).

## Что входит технически (для AI-агента)

- **Заготовка уже есть**: `Preset.wizardPresentation` ([WizardPresentation.kt](../../core/src/commonMain/kotlin/com/launcher/preset/model/WizardPresentation.kt): darkMode, typographyScale — v2, TASK-126) и `settingsMap` (categoryKey, settingsIcon, sensitivity). Задача — расширять их **аддитивно**, не изобретая второй механизм.
- Расширение `WizardPresentation` (по мере надобности: плотность, размер кнопок, интро-экраны).
- Новый `SettingsPresentation` блок в Preset (аддитивное nullable-поле, как wizardPresentation в v2) — категории, порядок, иконки, предупреждения для critical.
- Рендереры Wizard/Settings читают presentation-блоки; отсутствие блока = текущий hardcoded дефолт (обратная совместимость автоматом).
- Rule 9: schemaVersion у Preset уже есть; новые поля nullable = без bump.

## Состояние

Draft. Осознанный hardcode сейчас (rule 4 MVA — не строить UI-конфигурируемость без потребителя). Триггер: второй пресет с иными требованиями к виду, либо UI-конструктор пресетов.

---

## Готовый промт для `/speckit.specify`

```
ЧТО СТРОИМ: JSON-настраиваемое отображение Wizard и Settings как часть Preset (расширение wizardPresentation + новый settingsPresentation, аддитивно).
ЗАЧЕМ: вид мастера/настроек различается между пресетами и должен шариться вместе с пресетом (rule 9), без правок кода.
SCOPE ВКЛЮЧАЕТ: presentation-поля в Preset, чтение их рендерерами, дефолт = текущий вид, roundtrip-тесты.
SCOPE НЕ ВКЛЮЧАЕТ: полноценный theme-engine, UI-конструктор.
DEPENDENCIES: TASK-126, TASK-127.
ACCEPTANCE: пресет с кастомным presentation-блоком меняет вид Wizard/Settings; пресет без блока выглядит как сейчас; старые пресеты читаются.
EFFORT: M.
```
<!-- SECTION:DESCRIPTION:END -->
