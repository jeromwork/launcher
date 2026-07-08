---
id: TASK-118
title: Cross-device manual smoke gates (recovery + autofill + future)
status: Draft
assignee: []
created_date: '2026-06-30 08:30'
updated_date: '2026-07-08'
labels:
  - verification
  - cross-device
  - phase-7
  - manual-smoke
milestone: m-0
dependencies:
  - TASK-6
priority: high
ordinal: 118000
---

> **Renumbered 2026-07-08 from TASK-57 → TASK-118**. Was ID collision с [TASK-57 Zero-Knowledge Server Architecture audit](task-57%20-%20Zero-Knowledge-Server-Architecture-audit-Article-XX-adoption.md) (2026-06-26 task). Both files had same id: TASK-57 в frontmatter. Renamed this file (created later, 2026-06-30) to next free number.

## Description

Зонтичная задача: сюда складываются все ручные проверки, которые требуют **два физических устройства** или **factory-reset одного устройства**. Когда какая-то feature-task (TASK-6, TASK-7, и т.д.) добавляет такой AC — он переезжает сюда; feature-task закрывается в Done без ожидания второго телефона.

Активируется когда у владельца **появится второе устройство** (либо когда он решит делать factory-reset).

## Что это простыми словами

**Простая аналогия:** у тебя в квартире один холодильник, но рецепт мороженого требует **двух холодильников** (заморозил → переставил → проверил консистенцию). Один телефон есть — рецепт частично работает, но финальная проверка отложена до второго холодильника. Чтобы кулинарная книга не висела «в работе» вечно, выносим «второй холодильник» в отдельный список и закрываем основную работу.

**Что происходит по шагам (для каждого AC внутри этой задачи):**
1. Берёшь второй телефон (свой старый, или взаймы у близкого, или factory-reset того же).
2. Устанавливаешь приложение командой `./gradlew :app:installRealBackendDebug` (с подключенным USB).
3. Проходишь конкретный сценарий из AC ниже.
4. Если успешно — ставишь `[x]` напротив AC.
5. Когда все AC `[x]` — задача переходит в Done.

**Что происходит при добавлении нового сценария от другой feature-task:**
- Feature-task (например TASK-7 завтра) производит код для cross-device flow.
- AI-агент добавляет сюда новую `[ ]` строку с описанием действий + ссылкой на feature-task.
- Feature-task закрывается в Done со ссылкой «cross-device verification → TASK-57».

## Зачем

Без этой задачи каждая feature-task застревает в Verification на месяцы, ожидая второго устройства. Это создаёт визуальное ощущение «ничего не доделано», хотя код production-ready. Отделяя hardware-gated verification от feature-кода:
- Feature-task'и идут в Done своевременно.
- Cross-device тесты накапливаются в одном месте — когда второй телефон появится, прогоняются батчем за один вечер.
- Видно сколько всего отложено.

## Что входит технически (для AI-агента)

Эта задача — **не feature**. Здесь нет своего кода, своего spec'а, своего `/speckit.specify`. Она — **gate-collector**.

**Правила для AI-агента:**
- Когда feature-task имеет AC требующий 2 устройств → добавь сюда **новой `[ ]` строкой** в Acceptance Criteria с inline-описанием действий.
- Ссылайся на feature-task: `[hand] TASK-N SC-X — <описание> ...`
- В feature-task пометь AC как `[→ TASK-57 AC #M]` или `[N/A — перенесён в TASK-57]`.
- НЕ создавай spec, plan, tasks для TASK-57. Только AC + опционально implementation notes если есть конкретные tools/scripts.

## Состояние

**Draft.** Активируется когда первая feature-task закрывается с unresolved cross-device AC.

Сейчас собраны AC из TASK-6 (recovery byte-equal + Autofill cross-device). Будут добавляться по мере роста.

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 [hand] **TASK-6 SC-001** — cross-device recovery byte-equal.
  **Контекст:** на Xiaomi 11T уже выполнен upload через wizard (зашёл через Google + придумал пароль `correct-horse-battery-staple` 2026-06-30 — blob лежит в KV `backup/GUOJK0YapWNTgOi2twrDr0I3ov43/v1.json`).
  **Действия:**
  1. Возьми второе устройство (или сделай factory-reset Xiaomi).
  2. Установи realBackend APK: `./gradlew :app:installRealBackendDebug` с подключенным USB.
  3. Пройди wizard до Sign-In, выбери **тот же** Google-аккаунт (`g.jeromwork@gmail.com`).
  4. Должен появиться экран **ввода** пароля для восстановления (Entry screen, не Setup).
  5. Введи **тот же** пароль (`correct-horse-battery-staple`).
  6. Должно быть успешно — wizard продолжается. Проверь что настройки/контакты восстановились.
- [ ] #2 [hand] **TASK-6 SC-005** — Autofill cross-device.
  **Контекст:** при первом Setup на устройстве A Google Password Manager должен был предложить сохранить пароль (если ты согласился).
  **Действия:**
  1. На устройстве A (Xiaomi) — убедись что GPM сохранил пароль (Settings → Passwords → Launcher).
  2. На устройстве B — пройди wizard до Entry screen.
  3. Тапни на password field — должно появиться предложение «Автозаполнение из <account>» (GPM bottomsheet).
  4. Выбери — пароль должен подставиться сам.
<!-- AC:END -->

## Implementation Notes

Это gate-only задача. Никаких code-changes.

При добавлении нового AC от другой feature-task:
- Обновить `updated_date` в frontmatter.
- В `dependencies` добавить новую feature-task.
- Добавить новую `[ ]` строку в Acceptance Criteria с явным `**TASK-N <SC-or-name>**` префиксом.
