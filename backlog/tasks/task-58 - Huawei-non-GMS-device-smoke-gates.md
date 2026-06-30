---
id: TASK-58
title: Huawei / non-GMS device smoke gates
status: Draft
assignee: []
created_date: '2026-06-30 08:30'
updated_date: '2026-06-30 08:30'
labels:
  - verification
  - non-gms
  - huawei
  - phase-7
  - manual-smoke
milestone: m-0
dependencies: []
priority: medium
ordinal: 58000
---

## Что это простыми словами

Сборник ручных проверок что launcher работает корректно на устройствах **без Google Mobile Services**. Это в первую очередь Huawei (после Trump-era санкций они идут без Play Store / Firebase / Credential Manager) — но также любой Android device где user сам выключил GMS / Microsoft phones / любое OEM-кастомное Android-устройство.

## Зачем

Часть user-базы senior-launcher живёт на Huawei. Если cloud-фичи (recovery, push, config sync) **падают** на не-GMS устройстве вместо graceful degrade в local mode — это user-visible bug. Spec task-6 + spec 010 + spec 019 формально декларируют local-mode fallback, но требуют физической проверки.

## Что входит технически (для AI-агента)

Эта задача — **место сбора AC**, она не имеет своего кода. Каждый AC ниже:
- Описывает scenario на конкретном non-GMS устройстве.
- Ссылается на feature-task который должен gracefully degrade.
- Помечается `[x]` владельцем после успешного прохождения.

**Доступ к устройству:** у владельца сейчас Huawei нет. AC активируются когда устройство появится (Huawei P30 / Honor / любой EMUI device без GMS).

## Состояние

**Draft.** Ждёт физического устройства.

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 [hand] **TASK-6 SC-003** — Huawei без GMS, local-only mode. На Huawei устройстве установить realBackend APK → wizard запускается → cloud-checkpoint показывает **только** «Настроить с нуля» (skip Sign-In), без «Войти в Google» (Credential Manager недоступен) → wizard полностью проходит → плитки/контакты/темы работают локально → **никаких crash'ей**, **никаких попыток вызвать FCM/Firestore/Worker**. Owner attests.
- [ ] #2 [hand] **Cross-device:** один Huawei + один Samsung / Pixel — owner подтверждает что пара senior+admin работает когда одно из устройств без GMS. Senior на Huawei (local-only) — admin не может ничего удалённо настроить (это by design). Senior на GMS-device + admin на Huawei — admin может настраивать (его роль = send commands, не receive cloud state).
<!-- AC:END -->

---

## Готовый промт для `/speckit.specify`

Эта задача — gate, не feature. `/speckit.specify` не нужен. Каждый AC — конкретный ручной тест после получения Huawei устройства.
