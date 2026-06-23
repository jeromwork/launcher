---
id: TASK-16
title: Preset Schema v2 + Wizard Engine
status: Planned
assignee: []
created_date: '2026-06-23 05:38'
updated_date: '2026-06-23 06:19'
labels:
  - phase-3
  - p-spec
  - p-1
  - wire-format
  - schema-bump
milestone: m-2
dependencies:
  - TASK-7
  - TASK-8
  - TASK-9
  - TASK-10
  - TASK-11
  - TASK-12
  - TASK-13
priority: high
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Обновление формата файлов настройки (preset) до второй версии. Это нужно для новых функций Phase 3, которые не помещаются в старый формат. Старые устройства (с приложением Phase 2) продолжают работать.

**Что происходит по шагам:**
1. Существующий формат preset (`schemaVersion: 1`) описывает плитки, контакты, тему.
2. Phase 3 хочет добавить: обязательные / необязательные шаги в wizard, специфичные для платформы настройки (Android / TV / iOS отдельно), adaptive UX-профили (для людей с тремором, слабовидящих).
3. Создаётся `schemaVersion: 2` со всеми этими новыми полями.
4. Приложение Phase 3 умеет читать оба: и старый v1 (автоматически преобразуя в v2 «на лету»), и новый v2.
5. Приложение Phase 2 на устройстве бабушки получает v2 → сервер на лету «убирает» новые поля → даёт бабушке только то что она понимает (v1-shape).

**Зачем wizard manifest эволюционирует:**
- В v1 wizard описывает только обязательные шаги.
- В v2 wizard описывает обязательные + необязательные (бабушка может пропустить, в Settings висит напоминание — TASK-22 P-7).

## Зачем

**Без этого Phase 3 нельзя начать** — все P-задачи опираются на новые поля в v2. Backward-compat обязательна (бабушка с Phase 2 не должна сломаться при появлении admin'а с Phase 3).

## Что входит технически (для AI-агента)

- `ConfigDocumentV2` wire format с roundtrip + cross-version tests.
- Алгоритм `lift v1 → v2` с fixture-based тестом.
- `WizardManifest` schema с mandatory/optional разделением.
- Phase 2 reader downgrade test (читает v2, видит только platformAgnostic секцию).
- Inline TODO про server-side downgrade endpoint (для устройств на старой версии).

## Состояние

**Planned.** Зависит от завершения Phase 2 (минимум TASK-7..13).

---

## Готовый промт для `/speckit.specify`

```
Реализуй P-1: Preset Schema v2 + Wizard Engine.

ЧТО СТРОИМ:
Bump preset wire format с schemaVersion=1 на schemaVersion=2. ConfigDocumentV2 с новыми полями (mandatory/optional steps, platformAgnostic / platformSpecific, adaptiveProfile). Lift v1→v2 algorithm с roundtrip tests. Phase 2 reader получает v2 со стрипнутыми novel-полями (server-side downgrade или client-side strip).

ЗАЧЕМ:
Без этого Phase 3 нельзя начать (P-2/3/4 все требуют v2 поля). Backward-compat обязательна для Phase 2 devices.

SCOPE ВКЛЮЧАЕТ:
- ConfigDocumentV2 wire format (schemaVersion=2) с явными разделами:
  - platformAgnostic (shared между Android / iOS / TV).
  - platformSpecific.android / platformSpecific.androidTv / etc.
  - wizardManifest с mandatorySteps + optionalSteps.
  - adaptiveProfile (default / tremor-mild / tremor-severe / perception-impaired / vision-impaired).
- Lift v1 → v2 algorithm + fixture-based test.
- Phase 2 reader downgrade test (читает v2, видит только platformAgnostic).
- Cross-version roundtrip tests (v1→v2→v1 lossy документирован).
- Inline TODO про server-side downgrade endpoint (если client strip недостаточен).

SCOPE НЕ ВКЛЮЧАЕТ:
- Конкретные Android intents (TASK-17 P-2).
- Adaptive presets (TASK-19 P-4).
- Authoring UI (TASK-18 P-3).

DEPENDENCIES:
- Phase 2 завершена (минимум TASK-7..13).

ACCEPTANCE CRITERIA:
- Существующий v1-config открывается приложением Phase 3 → автоматически преобразуется в v2 shape без потерь.
- Сохранил config как v2 → Phase 2 device получил его, видит только знакомые поля, не упал.
- Roundtrip test v2→serialize→deserialize→v2 — byte-equal.
- Lift v1→v2 на 5 разных fixture'ах — все проходят assertion.
- Backward-compat test: всё что было в v1 spec — есть в v2 (новых обязательных полей нет).

LOCAL TEST PATH:
- Unit-tests с fixture v1-configs из spec 008 / 014 history.
- Property-based tests на random configs.
- Cross-spec test с устройством на Phase 2 build.

CONSTITUTION GATES:
- Rule 5 (wire format): schemaVersion=2 explicit + migration writer ДО shipping breaking change.
- Rule 1 (domain isolation): ConfigDocument — pure domain.

EFFORT: Medium (~2 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Lift v1 → v2 алгоритм с тестом fixture-based
- [ ] #2 WizardManifest schema с mandatory/optional разделением
- [ ] #3 Phase 2 reader downgrade test (читает v2, видит только platformAgnostic)
- [ ] #4 Существующий v1-config открывается приложением Phase 3 → автоматически преобразуется в v2 shape без потерь
- [ ] #5 Сохранил config как v2 → Phase 2 device получил его, видит только знакомые поля, не упал
- [ ] #6 Roundtrip test v2 → serialize → deserialize → v2 даёт byte-equal результат
- [ ] #7 Lift v1→v2 на 5 разных fixture'ах — все проходят assertion
- [ ] #8 Backward-compat test: всё что было в v1 spec — есть в v2 (новых обязательных полей не добавлено)
<!-- AC:END -->
