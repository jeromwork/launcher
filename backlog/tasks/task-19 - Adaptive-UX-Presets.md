---
id: TASK-19
title: Adaptive UX Presets
status: Draft
assignee: []
created_date: '2026-06-23 05:38'
updated_date: '2026-06-23 06:21'
labels:
  - phase-3
  - p-spec
  - p-4
  - accessibility
  - adaptive
  - senior-friendly
milestone: m-2
dependencies:
  - TASK-16
  - TASK-17
priority: medium
ordinal: 19000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

5 готовых preset'ов под разные ограничения возможностей бабушки: лёгкий тремор, сильный тремор, проблемы с восприятием, слабое зрение. Admin выбирает подходящий — UX устройства адаптируется.

**Что происходит по шагам (выбор adaptive preset):**
1. Admin в Admin App создаёт config для устройства бабушки.
2. Видит вопрос «Есть ли особенности у пользователя?»:
   - Нет — обычный senior-safe (default).
   - Лёгкий тремор рук — `tremor-mild`.
   - Сильный тремор — `tremor-severe`.
   - Проблемы с восприятием — `perception-impaired`.
   - Слабое зрение — `vision-impaired`.
3. Выбирает «лёгкий тремор» → preset применяется.
4. Бабушка получает обновлённый config.

**Что меняется в UX (на примере «лёгкий тремор»):**
- Размер плиток ≥ 72dp (вместо обычных 56dp).
- Тап игнорируется если палец двигается во время нажатия (debounce 500ms).

**Сильный тремор:**
- Для активации плитки нужно держать палец 1 секунду (long-press 1s).
- Все swipe-жесты отключены полностью.

**Слабое зрение:**
- Экстра контраст (чёрно-белая тема).
- Голосовое озвучивание плиток при тапе.
- Большой шрифт.

## Зачем

У 30%+ пожилых есть тремор / слабое зрение. Без adaptive preset'ов приложение не подходит — бабушка не может «попасть пальцем в плитку», или не видит, что на ней написано.

## Что входит технически (для AI-агента)

- 5 bundled adaptive presets как `.json` файлы.
- Каждый preset содержит `adaptiveProfile` поле (`default` / `tremor-mild` / `tremor-severe` / `perception-impaired` / `vision-impaired`).
- Runtime `AdaptiveTouchBehavior` сервис: debounce / long-press / dwell-to-activate timings берёт из profile.
- Accessibility audit per preset (через `checklist-accessibility` skill).
- Admin может выбрать preset в TASK-18 P-3 authoring UI.

## Состояние

**Planned.** Зависит от TASK-16 (P-1 v2 schema с `adaptiveProfile` полем) + TASK-17 (P-2 для accessibility-related steps).

---

## Готовый промт для `/speckit.specify`

```
Реализуй P-4: Adaptive UX Presets.

ЧТО СТРОИМ:
5 bundled adaptive presets для пользователей с ограниченными возможностями: default, tremor-mild (≥72dp targets, 500ms debounce), tremor-severe (long-press 1s, swipes disabled), perception-impaired (dwell-to-activate, minimal screen elements), vision-impaired (extra contrast + TTS). Runtime AdaptiveTouchBehavior service берёт timings из profile. Admin выбирает в TASK-18 authoring UI.

ЗАЧЕМ:
30%+ пожилых имеют тремор / слабое зрение. Без adaptive UX приложение не подходит.

SCOPE ВКЛЮЧАЕТ:
- 5 bundled adaptive presets .json (default, tremor-mild, tremor-severe, perception-impaired, vision-impaired).
- adaptiveProfile field в ConfigDocumentV2 (TASK-16).
- AdaptiveTouchBehavior service: debounce / long-press / dwell-to-activate.
- TTS integration для vision-impaired (Android system TTS).
- Accessibility audit per preset (через checklist-accessibility skill).
- Integration в TASK-18 admin authoring UI (selector «есть ли особенности»).

SCOPE НЕ ВКЛЮЧАЕТ:
- Machine learning для авто-определения тремора (post-MVP, требует accelerometer ML).
- Speech-to-text input (post-MVP).
- Eye-tracking / head-gesture (post-MVP, требует special hardware).

DEPENDENCIES:
- TASK-16 (P-1 v2 schema с adaptiveProfile).
- TASK-17 (P-2 Android steps как пример accessibility integration).

ACCEPTANCE CRITERIA:
- Admin выбрал tremor-mild для бабушки → плитки на её устройстве стали ≥72dp.
- Бабушка пытается тапнуть плитку, палец дрожит — тап игнорируется если палец двигался при нажатии (debounce 500ms).
- tremor-severe → активация только через long-press 1s, swipe-жесты не работают.
- vision-impaired → черно-белая тема + при тапе голос произносит имя плитки.
- Accessibility audit (skill checklist-accessibility) проходит для всех 5 preset'ов.
- Admin может сменить adaptiveProfile в любой момент → бабушке прилетает обновление за <10 секунд.

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 — manual проверка размеров и debounce.
- Unit-tests AdaptiveTouchBehavior с simulated touch events.
- TalkBack test на эмуляторе для vision-impaired.

CONSTITUTION GATES:
- Rule 5 (wire format): adaptiveProfile внутри ConfigDocumentV2 (TASK-16 уже регистрирует).
- Rule 9 (shareability): adaptive presets — обезличенные artefacts.

EFFORT: Medium (~2 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 AdaptiveTouchBehavior (debounce/long-press/dwell)
- [ ] #2 Accessibility audit per preset
- [ ] #3 Admin может выбрать в P-3 authoring UI
- [ ] #4 Admin выбрал tremor-mild для бабушки → плитки на её устройстве стали ≥72dp
- [ ] #5 Бабушка тапает дрожащим пальцем — тап игнорируется если палец двигался при нажатии (debounce 500ms)
- [ ] #6 tremor-severe → активация только через long-press 1s, swipe-жесты не работают
- [ ] #7 vision-impaired → чёрно-белая тема + голос произносит имя плитки при тапе
- [ ] #8 Accessibility audit (skill checklist-accessibility) проходит для всех 5 preset'ов
- [ ] #9 Admin сменил adaptiveProfile → бабушке прилетает обновление за <10 секунд
<!-- AC:END -->
