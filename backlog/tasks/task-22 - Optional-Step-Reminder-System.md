---
id: TASK-22
title: Optional Step Reminder System
status: Draft
assignee: []
created_date: '2026-06-23 05:38'
updated_date: '2026-06-23 06:23'
labels:
  - phase-3
  - p-spec
  - p-7
  - wizard
  - settings
  - notification-discipline
milestone: m-2
dependencies:
  - TASK-16
  - TASK-17
priority: medium
ordinal: 22000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Wizard'у можно ставить не только обязательные шаги (нужно сделать сразу), но и **необязательные** (можно отложить). Если admin отложил настройку — в Settings висит **тихое напоминание** (badge с цифрой), но без push-уведомлений.

**Что происходит по шагам:**
1. Admin настраивает бабушке config через TASK-13 S-8 editor.
2. Manifest wizard'а имеет:
   - Обязательные: язык, разрешения, тема.
   - Необязательные: подключить календарь, добавить фото-плитки, настроить SOS-контакты.
3. Бабушка прошла wizard, обязательные сделала, необязательные пропустила (5 шагов).
4. На главном экране внизу появляется крошечный счётчик: «5 не настроено».
5. Бабушка тапает счётчик → открывается список:
   - ☐ Подключить календарь
   - ☐ Добавить фото
   - ☐ Настроить SOS-контакты
   - ...
6. Тапает «Подключить календарь» → запускается соответствующий wizard step.

**Что НЕ происходит (важно!):**
- НЕТ push-уведомлений «у вас есть несделанные настройки!» (это спам — нарушает CLAUDE.md rule 10).
- Badge всегда виден, но не мигает, не блокирует основной flow.

## Зачем

Без этого admin вынужден все настройки делать обязательными → wizard становится 15 шагов → бабушка бросает или родственник раздражается. С этим — обязательное минимум, остальное можно потом, без давления.

## Что входит технически (для AI-агента)

- `OptionalStepTracker` — отслеживает completion для каждого optional step.
- Badge в Settings показывает «N не настроенных шагов».
- Tap на badge → список с прогрессом.
- Tap на step → запускается соответствующий WizardStep.
- НЕТ push reminders (rule 10 compliance).

## Состояние

**Planned.** Использует wire format discipline из TASK-16 (versioning convention + fitness rule). `optionalSteps` field в profile — owned этим task'ом при implementation. Также зависит от TASK-17 (P-2 examples of optional Android steps).

---

## Готовый промт для `/speckit.specify`

```
Реализуй P-7: Optional Step Reminder System.

ЧТО СТРОИМ:
В profile (versioning per TASK-16 wire format discipline) есть optionalSteps. OptionalStepTracker отслеживает completion. В Settings показывает badge «N не настроенных шагов». Tap на badge → список с прогрессом. Tap на step → запускает WizardStep. ЯВНО без push-уведомлений (rule 10 compliance).

ЗАЧЕМ:
Без этого все шаги обязательные → wizard 15 шагов → бабушка бросает. С этим — обязательный минимум, остальное на потом без давления.

SCOPE ВКЛЮЧАЕТ:
- OptionalStepTracker (отслеживает completion для каждого optional step).
- Settings badge «N не настроенных шагов».
- Список optional steps с прогрессом / completed marker.
- Tap → запуск соответствующего WizardStep.
- Frequency cap: badge всегда виден, но НЕ мигает / НЕ блокирует.
- НЕТ push reminders (rule 10 — optional steps не actionable + не time-sensitive).

SCOPE НЕ ВКЛЮЧАЕТ:
- Push reminders (запрещены).
- Email reminders (запрещены).
- Gamification («achievement: setup complete!») — запрещено.
- Optional steps для cloud features — нужны через TASK-15 S-10 entitlement check (отдельно).

DEPENDENCIES:
- TASK-16 (wire format discipline; `optionalSteps` field added by this task at implementation).
- TASK-17 (P-2 examples of optional Android steps).

ACCEPTANCE CRITERIA:
- Wizard manifest содержит 5 optional steps → бабушка прошла обязательные → в Settings висит badge «5».
- Тапнула badge → увидела список с 5 шагами и описанием каждого.
- Тапнула «Подключить календарь» → запустился соответствующий wizard step.
- Завершила шаг → badge стал «4».
- НИ ОДНОГО push-уведомления про настройки не пришло (manual проверка).
- Badge всегда виден в Settings, не мигает, не закрывает контент.

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 — manual проверка badge updates.
- Unit-tests OptionalStepTracker state machine.
- Privacy test: assertion что нет push registrations про optional steps.

CONSTITUTION GATES:
- Rule 1 (domain isolation): OptionalStepTracker — pure domain.
- Rule 10 (notification minimization): explicit NO push для optional steps.

EFFORT: Small (~1 week).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Badge в Settings показывает 'N не настроенных шагов'
- [ ] #2 Tap на step запускает wizard step
- [ ] #3 НЕТ push reminders (rule 10 compliance)
- [ ] #4 Wizard manifest содержит 5 optional steps → бабушка прошла обязательные → в Settings висит badge '5'
- [ ] #5 Тапнула badge → увидела список с 5 шагами и описанием каждого
- [ ] #6 Тапнула 'Подключить календарь' → запустился соответствующий wizard step
- [ ] #7 Завершила шаг → badge стал '4'
- [ ] #8 НИ ОДНОГО push-уведомления про настройки не пришло (manual проверка)
- [ ] #9 Badge всегда виден в Settings, не мигает, не закрывает контент
<!-- AC:END -->
