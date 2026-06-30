---
id: TASK-58
title: Huawei / non-GMS device smoke gates
status: Draft
assignee: []
created_date: '2026-06-30 08:30'
updated_date: '2026-06-30 08:45'
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

## Description

Зонтичная задача: сюда складываются ручные проверки на устройствах **без Google Mobile Services**. В первую очередь Huawei (после Trump-эры идут без Play Store / Firebase / Credential Manager) — но также любой Android-телефон где user сам выключил GMS, AOSP-устройства, китайские небрендовые телефоны.

Активируется когда у владельца **появится не-GMS устройство** (купить б/у Huawei на Avito — самый дешёвый способ ~3-5k руб).

## Что это простыми словами

**Простая аналогия:** в инструкции к чайнику написано «работает от 220В». Если кто-то живёт в стране с 110В, чайник должен **graceful degrade** — не взрываться, не плавиться, а просто **не греть** и показать понятное сообщение. Так и у нас: на Huawei без Google облачные функции **не работают**, но приложение **не падает**, не зависает, не показывает страшные ошибки — оно работает в **местном (local-only) режиме**.

**Что происходит по шагам (для каждого AC):**
1. Берёшь не-GMS телефон (Huawei P30 / Honor / Pixel с выключенным GMS / любой AOSP).
2. Устанавливаешь приложение через `adb install` (Play Store нет, поэтому только sideload).
3. Проходишь scenario из AC ниже.
4. Если приложение работает **локально** и **не падает** — `[x]`.
5. Когда все AC `[x]` — задача в Done.

## Зачем

Часть user-базы senior-launcher живёт на Huawei (особенно в Восточной Европе и Азии). Если cloud-фичи валятся вместо graceful degrade — это user-visible bug, который обнаружится только на physical Huawei.

Spec'и формально декларируют local-mode fallback, но без physical устройства это **untested claim**.

## Что входит технически (для AI-агента)

Эта задача — **не feature**. Здесь нет своего кода, своего spec'а, своего `/speckit.specify`. Она — **gate-collector**.

**Правила для AI-агента:**
- Когда feature-task имеет AC требующий проверку на non-GMS → добавь сюда **новой `[ ]` строкой** в Acceptance Criteria.
- Ссылайся на feature-task: `[hand] TASK-N SC-X — <описание> ...`
- В feature-task пометь AC как `[→ TASK-58 AC #M]` или `[N/A — перенесён в TASK-58]`.
- НЕ создавай spec, plan, tasks для TASK-58. Только AC + опционально notes.

**Доступ к устройству:** у владельца сейчас Huawei нет.

## Состояние

**Draft.** Ждёт физического Huawei (или эквивалентного non-GMS устройства).

Сейчас собран один AC из TASK-6 (Huawei local-only graceful degrade) + один cross-platform AC (Huawei + GMS-device пара). Будут добавляться по мере роста.

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 [hand] **TASK-6 SC-003** — Huawei без GMS, local-only mode.
  **Действия:**
  1. На Huawei (без Google Services) выполни `adb install app-realBackend-debug.apk`.
  2. Запусти приложение, пройди wizard.
  3. На cloud-checkpoint (Шаг 2 «Настройка приложения») — должна быть **только** кнопка «Настроить с нуля» (без «Войти в Google», т.к. Credential Manager отсутствует) ИЛИ кнопка «Войти в Google» должна показать понятное сообщение «недоступно на этом устройстве».
  4. Пройди wizard полностью (без cloud).
  5. Проверь что плитки, контакты, темы работают локально.
  6. Проверь logcat — никаких `CRASH`, никаких попыток вызвать `firestore.googleapis.com` / `fcm.googleapis.com` / нашего Worker'а.
- [ ] #2 [hand] **Cross-pair: Huawei + GMS device.**
  **Контекст:** проверка что senior+admin пара (TASK-3 / TASK-4 спецификации) работает когда одно из устройств без GMS.
  **Действия:**
  1. Senior на Huawei (local-only) + admin на Samsung/Pixel (GMS). По дизайну admin **не может** ничего удалённо настроить — Huawei не получает push'ей. Senior работает с локальными плитками. Проверь что admin UI понятно говорит «устройство недоступно в облаке» вместо silent failure.
  2. Senior на GMS-device + admin на Huawei. Admin **может** отправлять команды (его роль = send-only). Проверь что админский UI на Huawei корректно отправляет push (если есть HMS-альтернатива для FCM trigger'а) ИЛИ показывает «push отправка недоступна, попробуй с GMS-устройства».
<!-- AC:END -->

## Implementation Notes

Это gate-only задача. Никаких code-changes.

При добавлении нового AC от другой feature-task:
- Обновить `updated_date`.
- В `dependencies` добавить новую feature-task.
- Добавить новую `[ ]` строку в Acceptance Criteria с явным `**TASK-N <SC-or-name>**` префиксом.

**Hardware acquisition notes:**
- Huawei P30 / P30 Pro б/у — ~3-5k руб на Avito (Москва/СПб 2026).
- Honor — после развода с Huawei формально опять с GMS, не подходит для этой задачи. Бери только до-2020 модели без HMS-замены.
- Альтернатива: на Pixel `adb shell pm disable-user com.google.android.gms` отключает GMS — но это не идентично Huawei (system services частично остаются). Acceptable для smoke, не для release verification.
