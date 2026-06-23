---
id: TASK-29
title: Android TV Preset
status: Draft
assignee: []
created_date: '2026-06-23 05:40'
updated_date: '2026-06-23 06:29'
labels:
  - phase-4
  - v-spec
  - v-4
  - android-tv
  - leanback
  - form-factor
milestone: m-3
dependencies:
  - TASK-7
  - TASK-9
  - TASK-10
priority: medium
ordinal: 29000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Версия приложения для **Android TV** (телевизор с Android — Sony, Xiaomi, Mecool, и т.п.). Бабушка с пультом ДУ, не с тачскрином. Большие плитки на экране телевизора, голосовое управление, возможность принять видео-звонок из мессенджера прямо на телевизоре.

**Что происходит по шагам (бабушка):**
1. Бабушка купила Android TV, поставила приложение.
2. На экране большие крупные плитки (адаптировано под просмотр с дивана).
3. Управляет пультом (стрелочки + OK).
4. Если у TV есть микрофон в пульте — может сказать «позвонить дочке» голосом.
5. Дочка инвайтит в видео-звонок → на TV всплывает уведомление «Приём!», бабушка тапает OK на пульте → видео на полный экран.

**Что происходит по шагам (admin настройка):**
1. Дочка в Admin App видит «Бабушка-TV-зал» как paired device.
2. Pairing TV было через короткий 6-значный код на экране (`RemoteCodePairingChannel`), а не QR (TV нечем сканировать).
3. Настраивает плитки специально для TV-формата (другая сетка, иконки большего размера).

**Ambient mode (опционально):**
- Когда TV не используется и кто-то из семьи дома → на экране заставка «семья сейчас дома» с маленькими аватарками.
- Снижает изоляцию пожилого.

## Зачем

TV для бабушек — главное устройство (50%+ времени смотрят TV). Превратить пассивный приёмник в активный коммуникационный центр — большой UX-win.

## Что входит технически (для AI-агента)

- TV-specific UI (Leanback library или Compose for TV).
- Voice navigation через Android TV system Voice Input API.
- Big tiles + DPad-friendly focus management.
- `RemoteCodePairingChannel` adapter — TV показывает 6-значный код, бабушка/admin вводит на телефоне.
- Family call quick-join (TASK-27 V-2 messenger integration).
- Ambient family presence mode (optional opt-in).

## Состояние

**Planned.** Зависит от TASK-7 (Simple Launcher как basis), TASK-9 (Contact Tiles), TASK-10 (SOS — на TV специфичен).

---

## Готовый промт для `/speckit.specify`

```
Реализуй V-4: Android TV Preset.

ЧТО СТРОИМ:
TV-specific UI (Leanback или Compose for TV). Voice navigation через Android TV Voice Input. Big tiles, DPad-friendly focus. RemoteCodePairingChannel adapter (TV показывает 6-значный код, бабушка/admin вводит на телефоне — TV нечем сканировать QR). Family call quick-join (TASK-27 messenger integration). Ambient family presence mode (optional).

ЗАЧЕМ:
TV — главное устройство для пожилых (50%+ времени). Превращаем пассивный приёмник в коммуникационный центр.

SCOPE ВКЛЮЧАЕТ:
- TV-specific Compose UI (либо Leanback fallback).
- DPad focus management (4 направления + OK + Back).
- Voice input integration (Android TV Voice API).
- RemoteCodePairingChannel adapter.
- Big tiles preset (другая grid, другие icon sizes).
- Family call incoming UI (full screen + accept/decline by remote).
- Ambient mode (optional): family presence widget когда TV idle.

SCOPE НЕ ВКЛЮЧАЕТ:
- Управление через жесты (TV ремоут не поддерживает).
- Custom TV remote app — не приоритет.
- DVR / recording — не приоритет.
- Wear OS / smartwatch (TASK-30 V-5).

DEPENDENCIES:
- TASK-7 (S-1 Simple Launcher как basis).
- TASK-9 (S-3 Contact Tiles).
- TASK-10 (S-4 SOS — на TV специфичный flow без physical button).
- TASK-27 (V-2 messenger) — для family call integration (если ready).

ACCEPTANCE CRITERIA:
- Бабушка с пультом видит большие плитки на TV-экране, может навигировать стрелочками.
- Сказала «позвонить дочке» в пульт-микрофон → запустился звонок.
- Pairing TV: на экране 6-значный код → admin ввёл в Admin App → paired.
- Дочка инвайтит в group call → на TV полноэкранное уведомление с accept-by-remote.
- Ambient mode: TV idle 5 минут → появляется заставка «семья онлайн» с аватарами.
- Admin создаёт плитки специально для TV grid (не такие же что на телефоне).

LOCAL TEST PATH:
- Android TV emulator (API 34, 1080p).
- Manual test на физическом Android TV (если доступен).
- DPad navigation unit-tests.

CONSTITUTION GATES:
- Rule 1 (domain isolation): TV UI — adapter.
- Rule 9 (preset shareability): TV preset = обезличенный shareable.

EFFORT: Large (~2-3 months).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 RemoteCodePairingChannel adapter
- [ ] #2 Voice navigation интеграция
- [ ] #3 Ambient family presence mode
- [ ] #4 Бабушка с пультом видит большие плитки на TV-экране, может навигировать стрелочками
- [ ] #5 Сказала 'позвонить дочке' в пульт-микрофон → запустился звонок
- [ ] #6 Pairing TV: на экране 6-значный код → admin ввёл в Admin App → paired
- [ ] #7 Дочка инвайтит в group call → на TV полноэкранное уведомление с accept по пульту
- [ ] #8 Ambient mode: TV idle 5 минут → появляется заставка 'семья онлайн' с аватарами
- [ ] #9 Admin создаёт плитки специально для TV grid (не такие же что на телефоне)
<!-- AC:END -->
