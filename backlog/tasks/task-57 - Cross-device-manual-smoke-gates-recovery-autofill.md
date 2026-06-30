---
id: TASK-57
title: Cross-device manual smoke gates (recovery + autofill + future)
status: Draft
assignee: []
created_date: '2026-06-30 08:30'
updated_date: '2026-06-30 08:30'
labels:
  - verification
  - cross-device
  - phase-7
  - manual-smoke
milestone: m-0
dependencies:
  - TASK-6
priority: high
ordinal: 57000
---

## Что это простыми словами

Сборник ручных проверок которые требуют **двух физических устройств** (или factory-reset одного устройства). Создаётся отдельно, чтобы основные feature-задачи (вроде TASK-6) могли закрыться в Done — а cross-device verification идёт здесь как самостоятельный gate.

## Зачем

В разработке feature-задачи делают upload-half или single-device flow + код для recovery / cross-device сценариев. Само cross-device verification требует физического второго устройства, которого может не быть прямо сейчас. Откладывание этого gate'а в feature-задачу останавливает её Done.

## Что входит технически (для AI-агента)

Эта задача — **место сбора AC**, она не имеет своего кода. Каждый AC ниже:
- Описывает что нужно сделать вручную.
- Ссылается на feature-task который произвёл код.
- Помечается `[x]` владельцем после успешного прохождения.

Когда все AC `[x]` — задача → Done. Если приходит новый cross-device AC из другой feature-task — добавляется новой строкой.

## Состояние

**Draft.** Активируется когда первая feature-task закрывается с unresolved cross-device AC.

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 [hand] **TASK-6 SC-001** — cross-device recovery byte-equal. На Xiaomi 11T (или другое устройство A) уже выполнен upload через wizard (зашёл через Google + придумал пароль). **Действия:**
  1. Возьми второе устройство (или сделай factory-reset того же).
  2. Установи realBackend APK (`./gradlew :app:installRealBackendDebug`).
  3. Пройди wizard до Sign-In, выбери **тот же Google-аккаунт** (`g.jeromwork@gmail.com`).
  4. Должен появиться экран ввода пароля для восстановления (Entry screen, не Setup).
  5. Введи **тот же** пароль (`correct-horse-battery-staple` если использовал его при первом upload).
  6. Должно быть успешно — wizard продолжается. Проверь что настройки восстановились (плитки/контакты те же что на A).
- [ ] #2 [hand] **TASK-6 SC-005** — Autofill cross-device. На устройстве A через Google Password Manager сохранён пароль (если предложил при Setup). На устройстве B — на экране Entry **Google Password Manager должен сам подставить** этот пароль (не нужно вводить вручную). Проверить можно нажав на password field — должно появиться предложение «autofill from <account>».
<!-- AC:END -->

---

## Готовый промт для `/speckit.specify`

Эта задача — gate, не feature. `/speckit.specify` не нужен. Каждый AC — конкретный ручной тест.
