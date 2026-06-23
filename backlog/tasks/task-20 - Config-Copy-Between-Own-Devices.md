---
id: TASK-20
title: Config Copy Between Own Devices
status: Draft
assignee: []
created_date: '2026-06-23 05:38'
updated_date: '2026-06-23 06:22'
labels:
  - phase-3
  - p-spec
  - p-5
  - multi-device
  - config-management
milestone: m-2
dependencies:
  - TASK-16
priority: medium
ordinal: 20000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

У admin'а есть несколько устройств в семье (свой телефон, планшет, телевизор в зале, телевизор на кухне). Эта задача — скопировать готовую настройку (config) с одного устройства на другое с минимальными изменениями.

**Что происходит по шагам:**
1. Admin в Admin App видит список своих устройств:
   - «Мой телефон»
   - «Планшет жены»
   - «Телевизор в зале»
2. Хочет настроить новый телевизор на кухне аналогично залу.
3. Тапает на «Телевизор в зале» → меню → «Создать копию».
4. Получает копию `tv-living-room (copy)` в библиотеке config'ов.
5. Открывает копию, редактирует: убирает 2 приложения, переименовывает в `tv-kitchen`.
6. Применяет к новому устройству (кухонный телевизор).

**Что если копируем между разными типами устройств:**
1. Admin копирует с телефона на телевизор (разные platform).
2. Копируется только **общая часть** (`platformAgnostic`) — контакты, темы.
3. **НЕ копируется** `platformSpecific.android` (специфика телефона).
4. На телевизоре admin допроходит «настройка для TV» wizard.

## Зачем

Без этой задачи каждое новое устройство настраивается с нуля. С ней — admin делает 1 config «эталонный» и распространяет на похожие устройства за 1 минуту.

## Что входит технически (для AI-агента)

- Clone operation в Admin UI + автоматическое имя «(copy)».
- Cross-platform handling: копируется только `platformAgnostic`, `platformSpecific.*` остаётся пустым.
- Apply to device через push в namespace (TASK-5 F-5c).
- Tests: phone-to-phone, phone-to-TV (стрипнутый platformSpecific).

## Состояние

**Planned.** Зависит от TASK-16 (P-1 v2 schema разделяет platformAgnostic / platformSpecific).

---

## Готовый промт для `/speckit.specify`

```
Реализуй P-5: Config Copy Between Own Devices.

ЧТО СТРОИМ:
Clone operation в Admin App для копирования config'а между своими устройствами. Cross-platform copy: копируется только platformAgnostic (TASK-16 v2 schema), platformSpecific.* стрипается (target device passes through TV/phone-specific wizard). Apply через push в namespace.

ЗАЧЕМ:
Admin не настраивает каждое устройство с нуля; делает 1 «эталонный» config и распространяет.

SCOPE ВКЛЮЧАЕТ:
- Clone operation + UI (выбрать source, имя нового).
- Cross-platform handling: strip platformSpecific при копировании на другой form-factor.
- Apply to device через push в namespace (TASK-5 F-5c).
- Tests: phone-to-phone (full copy), phone-to-TV (platformAgnostic only).

SCOPE НЕ ВКЛЮЧАЕТ:
- Marketplace sharing (TASK-18 P-3 export или TASK-35 L-2).
- Multi-admin merge при concurrent edits (TASK-46 L-13).

DEPENDENCIES:
- TASK-16 (P-1 v2 schema с platformAgnostic/Specific разделением).

ACCEPTANCE CRITERIA:
- Admin тапнул «Создать копию» на существующем config → получил новый с именем «(copy)».
- Переименовал → отредактировал → применил к другому устройству → за <10 секунд устройство получило новый config.
- Phone-to-phone copy: всё содержимое включая platformSpecific.android копируется.
- Phone-to-TV copy: platformSpecific.android стрипнут; на TV запускается TV-specific wizard для дозаполнения.
- Применил к 3 устройствам подряд — все 3 получили обновление.

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 для Admin App.
- Unit-tests platformSpecific strip logic.
- E2E: эмулятор-phone + эмулятор-TV (если доступен).

CONSTITUTION GATES:
- Rule 5 (wire format): использует TASK-16 schema.
- Rule 9 (shareability): copy сохраняет identity-isolation (нет recipient'ов в platformAgnostic).

EFFORT: Small (~1 week).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Cross-platform handling: platformAgnostic копируется, platformSpecific остаётся пустым
- [ ] #2 Apply to device через push в namespace
- [ ] #3 Tests: phone-to-phone, phone-to-TV
- [ ] #4 Admin тапнул 'Создать копию' на существующем config → получил новый с именем '(copy)'
- [ ] #5 Переименовал → отредактировал → применил к другому устройству → за <10 секунд устройство получило новый config
- [ ] #6 Phone-to-phone copy: всё содержимое включая platformSpecific.android копируется
- [ ] #7 Phone-to-TV copy: platformSpecific.android стрипнут; на TV запускается TV-specific wizard для дозаполнения
- [ ] #8 Применил к 3 устройствам подряд — все 3 получили обновление
<!-- AC:END -->
