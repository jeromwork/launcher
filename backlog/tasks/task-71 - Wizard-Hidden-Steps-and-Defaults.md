---
id: TASK-71
title: Wizard Hidden Steps and Defaults
status: Done
assignee: []
created_date: '2026-06-30 20:00'
updated_date: '2026-07-19'
labels:
  - phase-2
  - foundation
  - wizard
  - follows-task-65
milestone: m-1
dependencies:
  - TASK-65
  - TASK-120
  - TASK-136
priority: low
ordinal: 71000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Sync 2026-07-19 (clean-in-place, owner directive):** исходная формулировка задачи писалась в TASK-120-эпоху и предлагала навесить на pool-entry булевы флаги `hideInWizard` / `showInSettings` + `defaultValue`. После перехода на канонический ECS (TASK-136 / ADR-013) это ретировано — ровно то же поведение уже выражается штатной моделью, а булевы флаги были бы «conflation smell» (см. [ecs.md §2](../../docs/architecture/ecs.md)). Ниже — сверка со фактическим кодом.

## Что это простыми словами

Автор пресета хочет некоторые шаги wizard'а **скрыть** и **применить дефолт автоматически** (пример: «размер шрифта 16pt — не спрашивай, применяй сразу»), но при этом оставить ту же настройку **редактируемой в Settings**.

**Это поведение уже полностью реализовано** — не через флаги, а через две ортогональные оси канонической модели:

1. **Показывать шаг или применить молча** = ось [`WizardBehavior`](../../core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt#L6), которую разбирает [`ReconcileEngine.runWizard`](../../core/src/commonMain/kotlin/com/launcher/preset/engine/ReconcileEngine.kt#L56-L75):
   - `Interactive` → показывает шаг (`sink.askUser`);
   - `AutoApply` → применяет **без вопроса** (это и есть «скрытый шаг + дефолт»);
   - `InitialDefault` → сразу помечает `Applied`.
   «Дефолт» = `paramsOverride: JsonObject?` на записи (уже есть).
2. **Видно ли в Settings** = присутствие записи в списке `Preset.settingsMap` (ортогонален `wizardFlow`). Присутствие в списке *и есть* «showInSettings». Пайплайн Settings строит TASK-69.

## Зачем

Убрать из wizard'а шаги для настроек с разумным дефолтом, не хардкодя. **Уже достижимо** авторингом пресета: положить запись в `activeComponents`/`wizardFlow` с `behavior=AutoApply`+`paramsOverride`, и (опционально) в `settingsMap` — без единой строки нового кода.

## Что входит технически (для AI-агента)

Сверка исходного scope против реального кода (all merged):

| Исходно хотели (TASK-120-эпоха) | Как выражается в каноническом ECS | Статус |
|---|---|---|
| `hideInWizard: Boolean` | `WizardBehavior.AutoApply` / `InitialDefault` | ✅ есть ([ReconcileEngine](../../core/src/commonMain/kotlin/com/launcher/preset/engine/ReconcileEngine.kt#L59-L71)) |
| `defaultValue` | `paramsOverride: JsonObject?` на entry | ✅ есть ([Preset.kt](../../core/src/commonMain/kotlin/com/launcher/preset/model/Preset.kt)) |
| `showInSettings: Boolean` | членство в `Preset.settingsMap` | ✅ есть (TASK-69, Verification) |
| «нельзя hideInWizard без defaultValue» | `Component` = data-class с non-null полями → `AutoApply` всегда имеет значение; частный «пустой» случай ловит [`NullLocale`](../../core/src/commonMain/kotlin/com/launcher/preset/engine/PresetValidator.kt#L141) | ✅ покрыто типами + case-валидатором; generic-правило = абстракция без нужды (rule 4) |

**Реального остатка кода нет.** Опциональный (не обязательный) хвост, если владелец захочет:
- образец в bundled-пресете, демонстрирующий «шрифт применён молча + редактируется в Settings» — авторская полнота, не функциональность;
- 2-3 строки в доке авторинга пресетов про семантику `WizardBehavior` (можно вписать в [ecs.md](../../docs/architecture/ecs.md) tag/behaviour-глоссарий на следующем touch).

## Состояние

**Subsumed by canonical ECS.** Задача возникла в clarify TASK-65 против старой модели; её содержательная часть закрыта фундаментом (TASK-136) + wizard-механикой (TASK-126/127) + Settings-пайплайном (TASK-69). Синкнута 2026-07-19 на текущую правду. Рекомендация: закрыть как Done c пометкой subsumed (кода писать не нужно); опциональный doc/пример-пресет — отдельным low-priority follow-up при следующем касании авторинга.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] «Скрытый шаг + дефолт» выражается через `WizardBehavior.AutoApply`/`InitialDefault` + `paramsOverride` — подтверждено в `ReconcileEngine.runWizard` (без нового кода)
- [x] #2 [hand] «Видно в Settings» выражается членством в `Preset.settingsMap` — пайплайн строит TASK-69 (без нового кода)
- [x] #3 [hand] Требование «нельзя авто-применить без значения» покрыто типами (`Component` non-null поля) + `NullLocale`-валидатором; отдельная generic-валидация не добавляется (rule 4)
- [N/A] #4 [hand] (опциональный доп-эффект, не блокирует) образец bundled-пресета «скрытый шаг + дефолт + редактируем в Settings» — уже покрыт `tile-whatsapp` (AutoApply) в `launcher.json`; строки про семантику `WizardBehavior` в доку авторинга допишутся на следующем касании авторинга пресетов (без отдельной карточки, rule 4)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
**Done 2026-07-19 — closed as subsumed (no code).** Синк-only: карточка переписана под каноническую ECS (TASK-136 / [ADR-013](../../docs/adr/ADR-013-canonical-ecs.md)); исходная TASK-120-эпоха «булевы флаги на pool-entry» ретирована как conflation smell ([ecs.md §2](../../docs/architecture/ecs.md)).

Функциональность, которую просила задача, уже целиком в продукте и проверена — писать код не потребовалось:
- **«скрытый шаг + дефолт»** = `WizardBehavior.AutoApply` / `InitialDefault` + `paramsOverride`, разбирается в [`ReconcileEngine.runWizard`](../../core/src/commonMain/kotlin/com/launcher/preset/engine/ReconcileEngine.kt#L56-L75); живой пример — `tile-whatsapp` (AutoApply) в [launcher.json](../../app/src/main/assets/preset/bundled-presets/launcher.json);
- **«видно в Settings»** = членство в `Preset.settingsMap` (пайплайн — TASK-69);
- **«нельзя авто-применить без значения»** покрыто типами (`Component` non-null поля) + `NullLocale`-валидатором; отдельная generic-валидация не добавляется (rule 4);
- проверено: unit — [`ReconcileEngineTest`](../../core/src/commonTest/kotlin/com/launcher/preset/engine/ReconcileEngineTest.kt#L33-L47); эмулятор — TASK-136 AC #5 smoke (wizard 9 шагов, 2026-07-19).

AC: #1–3 `[hand]` зелёные (уже реализовано фундаментом), #4 `[N/A]` (опциональный док, без отдельной карточки). Нового кода, wire-format-изменений и физических гейтов нет → сразу Done, не Verification.
<!-- SECTION:FINAL_SUMMARY:END -->
