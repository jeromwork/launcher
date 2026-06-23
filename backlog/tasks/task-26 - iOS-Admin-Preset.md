---
id: TASK-26
title: iOS Admin Preset
status: Planned
assignee: []
created_date: '2026-06-23 05:39'
updated_date: '2026-06-23 06:27'
labels:
  - phase-4
  - v-spec
  - v-1
  - ios
  - platform-expansion
milestone: m-3
dependencies:
  - TASK-3
  - TASK-8
priority: medium
ordinal: 25000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Admin App для iPhone (iOS). Сейчас Admin App работает только на Android. Эта задача — портирование для пользователей с iPhone, чтобы дочка-родственник с iPhone тоже могла удалённо помогать бабушке.

**Что происходит по шагам:**
1. Дочка с iPhone заходит в App Store, скачивает Admin App.
2. Видит знакомый интерфейс (Compose Multiplatform — общий с Android).
3. Логинится через **Apple Sign-In** (или Google — оба доступны).
4. Дальше всё как на Android: сканирует QR бабушкиного телефона, paired, может редактировать config удалённо.

**Что технически разное:**
- iOS-специфичные deep links (как открыть WhatsApp на iOS, например).
- Apple Sign-In дополнительно к Google.
- iOS share intents (другой API, чем Android share).
- App Store submission flow (отличается от Google Play).

**Что общее:**
- Domain логика (та же что в `core/`).
- UI (Compose Multiplatform работает на iOS).
- Crypto (core/crypto/ кросс-платформенно).
- Wire format (тот же — конфиги совместимы между Android Admin и iOS Admin).

## Зачем

В России iPhone 20% доли, в Европе/США 40-60%. Без iOS Admin App — половина потенциальных admin'ов исключена.

## Что входит технически (для AI-агента)

- Compose Multiplatform iosMain builds.
- Apple Sign-In adapter в `AuthProvider` (из TASK-3 F-4 — port расширяется).
- iOS deep links + share intents adapters.
- App Store submission готов (sign + provisioning + screenshots + privacy manifest).
- iOS-specific OEM quirks (нет ROLE_HOME эквивалента — не нужен, iOS managed mode другой механизм).

## Состояние

**Planned.** Зависит от TASK-3 (F-4 AuthProvider — port расширяется) + TASK-8 (S-2 Admin App Android — портируем).

---

## Готовый промт для `/speckit.specify`

```
Реализуй V-1: iOS Admin Preset.

ЧТО СТРОИМ:
Admin App для iOS (iPhone). Compose Multiplatform iosMain implementations. AuthProvider port (TASK-3) расширяется Apple Sign-In adapter. iOS-specific deep links + share intents. App Store submission flow.

ЗАЧЕМ:
20-60% admin'ов потенциально на iPhone (зависит от региона). Без iOS Admin App — исключаем половину рынка.

SCOPE ВКЛЮЧАЕТ:
- Compose Multiplatform iosMain builds (core / wizard / admin app modules).
- AppleSignInAuthAdapter (Sign In with Apple) в core/auth/iosMain/.
- iOS DeepLinkResolver adapter (URL-scheme handling).
- iOS ShareIntentAdapter (UIActivityViewController).
- App Store submission: code signing, provisioning profile, screenshots, privacy manifest (privacy nutrition label).
- iOS-specific OEM quirks: notification permission flow (iOS asks differently).
- Wire format compatibility test: Android Admin создаёт config → iOS Admin читает → byte-equal.

SCOPE НЕ ВКЛЮЧАЕТ:
- iOS Managed (для бабушек с iPhone) — отдельная V-x в Phase 5+ (немного бабушек на iPhone).
- iOS TV (tvOS) — не приоритет.
- macOS Admin — не приоритет.

DEPENDENCIES:
- TASK-3 (F-4 AuthProvider) — port расширяется.
- TASK-8 (S-2 Admin App Android) — портируется.

ACCEPTANCE CRITERIA:
- Compose Multiplatform iosMain собирается без ошибок.
- Дочка с iPhone установила Admin App из TestFlight (pre-AppStore) → залогинилась через Apple Sign-In.
- Просканировала QR бабушки (Android Simple Launcher) → paired успешно.
- Изменила конфиг бабушки на iPhone → бабушка получила обновление за <10 секунд.
- iOS share intent работает (сохранить preset как файл, отправить через iMessage).
- App Store submission прошла ревью (manual milestone — после внутренней готовности).

LOCAL TEST PATH:
- Xcode simulator iPhone 15 Pro для baseline.
- Manual test на реальном iPhone (если доступен) для App Store-specific quirks.
- E2E с Android Simple Launcher → iOS Admin App.

CONSTITUTION GATES:
- Rule 1 (domain isolation): iosMain code — только в adapters, не в domain.
- Rule 2 (ACL): UIKit / SwiftUI не вытекает в domain.
- Rule 5 (wire format): byte-equal compatibility с Android.

EFFORT: Very Large (~3-4 months).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Apple Sign-In adapter в AuthProvider
- [ ] #2 iOS deep links + share intents
- [ ] #3 App Store submission готов
- [ ] #4 Compose Multiplatform iosMain собирается без ошибок
- [ ] #5 Дочка с iPhone установила Admin App из TestFlight → залогинилась через Apple Sign-In
- [ ] #6 Просканировала QR бабушки (Android Simple Launcher) → paired успешно
- [ ] #7 Изменила конфиг бабушки на iPhone → бабушка получила обновление за <10 секунд
- [ ] #8 iOS share intent работает (сохранить preset как файл, отправить через iMessage)
- [ ] #9 App Store submission прошла ревью
<!-- AC:END -->
