---
id: TASK-7
title: Simple Launcher first-run + Setup Wizard
status: Draft
assignee: []
created_date: '2026-06-23 05:36'
updated_date: '2026-06-23 06:54'
labels:
  - phase-2
  - s-spec
  - s-1
  - ui
  - wizard
milestone: m-1
dependencies:
  - TASK-1
priority: high
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Первый экран, который бабушка видит после установки приложения — небольшой мастер настройки (wizard), проводящий через несколько обязательных шагов до рабочего главного экрана.

**Что происходит по шагам:**
1. Бабушка устанавливает приложение и открывает его.
2. Появляется приветственный экран и автоопределение языка (русский / английский / другой по настройкам Android).
3. Приложение просит разрешение стать главным экраном Android (на техническом языке — `ROLE_HOME` permission). Это нужно чтобы при нажатии кнопки «Home» открывалось наше приложение, а не стандартный лаунчер.
4. Приложение просит разрешение показывать уведомления (`POST_NOTIFICATIONS`, обязательно с Android 13+).
5. Бабушка (или родственник, который помогает) выбирает тему — тёплая светлая или тёплая тёмная.
6. Выбирает готовую раскладку плиток: 6 плиток (2×3), 9 (3×3) или 12 (3×4).
7. Опционально: соединиться с устройством админа (родственника) по QR-коду — это можно пропустить и сделать позже.
8. Wizard заканчивается → бабушка видит главный экран с плитками (не пустой).

**Что НЕ происходит в этой задаче:**
- Никакого Google-вход в этой версии (local mode — устройство самодостаточно, работает без интернета).
- Контакты-плитки пока заглушки (реальные контакты приходят в TASK-9).
- Кнопка SOS — отдельная задача TASK-10.

## Зачем

Это **первый видимый MVP-демо** — поставил приложение → wizard → главный экран с плитками. Без этого нет демонстрируемого продукта. Закрывает основную боль «у бабушки пустой экран после установки».

## Что входит технически (для AI-агента)

- `SimpleLauncherWizardManifest` — декларация шагов wizard'а для Simple Launcher preset.
- 5 обязательных шагов через Wizard Module из TASK-1 (F-3).
- Autohints через `TutorialHintManager` для необязательного (размер шрифта, выбор сетки, picker template).
- 3 bundled `ConfigTemplate` JSON-файла через `BundledConfigSource`: `6tiles-classic.json`, `9tiles-with-calendar.json`, `12tiles-dense.json`.
- Home screen renderer читает из `/config/current` (не mock — закрывает старую заметку ARCH-016).
- Skip-with-banner pattern: пропустил обязательный шаг → в Settings висит баннер «настрой это».
- Расширение существующей спеки 010 setup-assistant в полный preset через wizard module.

## Состояние

**Planned.** Зависит от TASK-1 (F-3 wizard module + локализация — уже Done). Готов к старту после закрытия TASK-6 (F-5 Root Key).

---

## Готовый промт для `/speckit.specify`

```
Реализуй S-1: Simple Launcher First-Run + Setup Wizard.

ЧТО СТРОИМ:
Первый visible MVP — Simple Launcher preset для Managed-устройства. После установки app → wizard через язык / permissions / тему / раскладку плиток / опциональный pairing → home screen с реальным config'ом (не пустой).
LOCAL mode: без Google Sign-In, без cloud. Cloud features подключаются позже (TASK-8 onwards).

ЗАЧЕМ:
Первый демонстрируемый продукт. Закрывает D-5 boli «empty top-level screen at launch».

SCOPE ВКЛЮЧАЕТ:
- SimpleLauncherWizardManifest (5 mandatory steps + autohints через TutorialHintManager).
- 3 bundled ConfigTemplate JSON-файла (6tiles-classic, 9tiles-with-calendar, 12tiles-dense) через BundledConfigSource из F-3.
- Home screen renderer из /config/current (расширение spec 003 UI skeleton).
- Skip-with-banner pattern + Settings reminders для skipped mandatory steps.
- Расширение spec 010 setup-assistant.
- Localization: все strings через string tables (procedure-translate-spec-strings уже работает).

SCOPE НЕ ВКЛЮЧАЕТ:
- Admin App preset / Admin wizard (TASK-8 S-2).
- Contact tiles content (TASK-9 S-3 — здесь плитки-заглушки).
- SOS configuration (TASK-10 S-4).
- Photo upload / display (TASK-11 S-5).
- Caregiver invite (TASK-31 V-6).
- Dwell-to-activate (TASK-19 P-4).
- Google Sign-In step (deferred per decision 2026-06-15-deferred-cloud/01).

DEPENDENCIES:
- TASK-1 (F-3 Wizard Module + Localization) — done.

ACCEPTANCE CRITERIA:
- Установил приложение на эмулятор → wizard появился сразу.
- Прошёл все 5 шагов без подсказок → главный экран с плитками отрисовался.
- Перезагрузил устройство → wizard не повторяется, состояние сохранено.
- Пропустил необязательный шаг → в Settings висит баннер с напоминанием.
- Отказал в ROLE_HOME → wizard повторно вежливо спрашивает через системные Settings.
- Изменил системную локаль Android → строки wizard'а сменились после перезапуска.
- Senior-safe walkthrough на эмуляторе (через skill android-emulator) — пожилой человек проходит без затруднений.

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 — fresh install → wizard → home screen.
- Restart device → state persistent test.
- Locale switching test.
- Skip optional step → banner check.

CONSTITUTION GATES:
- Rule 1 (domain isolation): WizardManifest, ConfigSource — ports в core/wizard/, adapters в android/.
- Rule 5 (wire format): ConfigTemplate JSON с schemaVersion=1, roundtrip test.
- Rule 6 (mock-first): FakeConfigSource для tests.
- Rule 9 (shareability): ConfigTemplate обезличены, без identity-bound fields.

EFFORT: Large (~3 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 3 bundled ConfigTemplate (6tiles-classic, 9tiles-with-calendar, 12tiles-dense)
- [ ] #2 Home screen рендерит из /config/current (не mock)
- [ ] #3 Skip-with-banner pattern для пропущенных steps
- [ ] #4 Senior-safe walkthrough на эмуляторе через android-emulator skill
- [ ] #5 Установил приложение на эмулятор → wizard появился сразу
- [ ] #6 Прошёл все 5 шагов без подсказок → главный экран с плитками отрисовался
- [ ] #7 Перезагрузил устройство → wizard не повторяется, состояние сохранено
- [ ] #8 Пропустил необязательный шаг → в Settings висит баннер 'настрой это'
- [ ] #9 Отказал в разрешении 'главный экран' (ROLE_HOME) → wizard вежливо просит ещё раз через Settings, не падает
- [ ] #10 Изменил язык Android → строки wizard'а переключились после перезапуска
- [ ] #11 Senior-safe walkthrough на эмуляторе пройден (пожилой человек проходит без затруднений)
<!-- AC:END -->
