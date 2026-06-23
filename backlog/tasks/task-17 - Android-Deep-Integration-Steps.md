---
id: TASK-17
title: Android Deep Integration Steps
status: Draft
assignee: []
created_date: '2026-06-23 05:38'
updated_date: '2026-06-23 06:20'
labels:
  - phase-3
  - p-spec
  - p-2
  - android
  - deep-integration
  - accessibility
milestone: m-2
dependencies:
  - TASK-16
priority: high
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Дополнительные шаги wizard'а для глубокой настройки Android-устройства бабушки: отключить шторку уведомлений, заблокировать переключение между экранами свайпом, спрятать Settings за 7-tap, и т.п. Это «затягивает» интерфейс так, чтобы бабушка случайным жестом не вышла из приложения и не запуталась.

**Что происходит по шагам (admin настраивает):**
1. Admin в TASK-13 (config editor) открывает wizard manifest бабушки.
2. Видит список доступных шагов «глубокой интеграции»:
   - Заблокировать шторку уведомлений (чтобы бабушка случайно не нажимала там что-то).
   - Заблокировать свайп между экранами (бабушка не вылезет с главного экрана).
   - Скрыть Settings (нужно тапнуть 7 раз быстро в углу — секретный жест для admin'а).
   - Скрыть widget'ы на lock screen.
   - Ограничить видимость списка приложений.
3. Выбирает нужные → save → push к бабушке.

**Что происходит на устройстве бабушки:**
1. Получает новый wizard manifest.
2. Wizard запускается заново → проводит через выбранные шаги.
3. Для большинства шагов нужно дать разрешение Accessibility Service (один раз).
4. Если ОС обновилась → проверяется, всё ли осталось включено; если что-то слетело — wizard спрашивает заново.

**Что НЕ делается:**
- На iOS / TV / Wear эти шаги не работают (только Android). iOS-варианты — TASK-26 V-1.

## Зачем

Без этих шагов бабушка часто «теряется»: открыла что-то случайно, не понимает где она. С ними — телефон становится максимально предсказуемым и безопасным.

## Что входит технически (для AI-агента)

- 5+ reusable Android Deep Integration WizardStep implementations:
  - `BlockNotificationDrawerStep` (через Accessibility Service).
  - `DisableHorizontalSwipeStep` (overlay).
  - `HideSettingsBehind7TapStep`.
  - `DisableLockscreenWidgetsStep`.
  - `RestrictAppListVisibilityStep`.
- Permissions wizard для Accessibility Service (одно разрешение покрывает большинство шагов).
- Settings deep-links для OS-level настроек.
- State reconciliation после OS update (после Android upgrade проверить — не слетели ли настройки).
- OEM-quirk tests: Samsung One UI / OEM с custom skin (currently Xiaomi MIUI) / Huawei EMUI (у каждого свой странности).

## Состояние

**Planned.** Зависит от TASK-16 (schema v2 с novel wizard steps).

---

## Готовый промт для `/speckit.specify`

```
Реализуй P-2: Android Deep Integration Steps.

ЧТО СТРОИМ:
5+ reusable Android-specific WizardStep implementations для полноценного «безопасного пространства пожилого»: BlockNotificationDrawerStep, DisableHorizontalSwipeStep, HideSettingsBehind7TapStep, DisableLockscreenWidgetsStep, RestrictAppListVisibilityStep. Каждый — WizardStep, использует Accessibility Service или Android system intent.

ЗАЧЕМ:
Без этих шагов бабушка теряется в случайно открытых приложениях / системных экранах. С ними — телефон максимально предсказуем.

SCOPE ВКЛЮЧАЕТ:
- 5+ reusable Android Deep Integration WizardStep classes.
- Accessibility Service wrapper (одно разрешение для большинства шагов).
- Settings deep-links для OS-level настроек.
- State reconciliation после OS update (post-upgrade check).
- OEM matrix tests: Samsung One UI / OEM с custom skin (currently Xiaomi MIUI) / Huawei EMUI.
- Per-OEM workarounds (например, MIUI requires additional autostart permission).

SCOPE НЕ ВКЛЮЧАЕТ:
- iOS варианты (TASK-26 V-1 в Phase 4).
- TV варианты (TASK-29 V-4 в Phase 4).
- Wearable (TASK-30 V-5).

DEPENDENCIES:
- TASK-16 (P-1 schema v2 с novel wizard step types).

ACCEPTANCE CRITERIA:
- Admin включил «заблокировать шторку» → wizard запустился → попросил Accessibility разрешение → шторка отключена.
- Бабушка свайпает вниз с верху экрана → ничего не происходит (шторка заблокирована).
- Admin включил «скрыть Settings» → Settings недоступны через шторку / launcher → доступны только через 7-tap в углу.
- OS обновилась (Android upgrade) → автоматическая проверка показала «всё ОК» или «нужно настроить заново».
- На Samsung / Xiaomi / Huawei все 5 шагов работают (через OEM-quirk workarounds).

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 для baseline.
- Manual testing на physical device #1 (currently physical device #1 (currently Xiaomi 11T)) для MIUI quirks.
- Unit-tests state reconciliation с fake AccessibilityService states.

CONSTITUTION GATES:
- Rule 1 (domain isolation): WizardStep — port; Accessibility-specific code в adapter.
- Rule 2 (ACL): AccessibilityService API не вытекает в domain.

EFFORT: Large (~3 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Permissions wizard + Settings deep-links
- [ ] #2 State reconciliation после OS update
- [ ] #3 OEM-specific quirks тесты (Samsung, OEM с custom skin (currently Xiaomi MIUI))
- [ ] #4 Admin включил 'заблокировать шторку' → wizard запустился → попросил Accessibility-разрешение → шторка отключена
- [ ] #5 Бабушка свайпает вниз с верху экрана → ничего не происходит (шторка заблокирована)
- [ ] #6 Admin включил 'скрыть Settings' → Settings недоступны через шторку / launcher, доступны только через 7-tap в углу
- [ ] #7 OS обновилась (Android upgrade) → автоматическая проверка показала 'всё ОК' или 'нужно настроить заново'
- [ ] #8 На Samsung / Xiaomi / Huawei все 5 шагов работают (через OEM-quirk workarounds)
<!-- AC:END -->
