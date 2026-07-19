---
id: TASK-137
title: VERIFY OEM-matrix — vendor-aware LauncherRole dispatch (TASK-73)
status: Draft
assignee: []
created_date: '2026-07-19'
updated_date: '2026-07-19'
labels:
  - phase-3
  - verification
  - manual-gate
  - area-oem
  - xiaomi
  - huawei
  - samsung
milestone: m-2
dependencies:
  - TASK-73
references:
  - specs/task-73-pool-entries-per-vendor-variants/
priority: high
ordinal: 137000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

TASK-73 сделал `LauncherRoleProvider` vendor-aware в коде (сначала пробует intent, специфичный для Xiaomi/Huawei/Samsung из `vendor-recipes.json`, потом старый generic-путь, потом честная текстовая инструкция). Логика диспетча покрыта unit-тестами и эмуляторным смоком (generic-путь на `Medium_Phone_API_36.1` не сломан). Но **реальное поведение OEM-скинов** нельзя воспроизвести на AOSP-эмуляторе — образы эмуляторов не несут MIUI/EMUI/One UI.

Эта задача собирает то, что осталось проверить **на железе / в облаке устройств**:

1. **Xiaomi (MIUI)**: тап «Сделать launcher по умолчанию» открывает MIUI-специфичный экран (Настройки → Приложения → По умолчанию → Домашний экран), а не generic ROLE-диалог, который на MIUI не применяется после тапа «Да».
2. **Huawei без GMS (EMUI/HarmonyOS)**: ни один вызов `check()`/`apply()` не роняет приложение; если системный путь недоступен — пользователь видит легибельную текстовую инструкцию (fallback-строка), а не тихий no-op.
3. **Samsung One UI**: generic fallback приемлем (Knox-специфичный путь вне scope TASK-73), проверить что ничего не ломается.
4. **Firebase Test Lab CI**: job по PR-метке `oem-matrix-required`, прогоняющий инструментальные тесты на Pixel 8 + Samsung Galaxy S24 + Xiaomi Redmi (нужен GCP-проект + биллинг — операционный шаг владельца).

## Зачем

TASK-73 закрыл код и локально-проверяемые гейты, но три задачи в его `tasks.md` помечены deferred (`T073-032` external / `T073-033` local-emulator / `T073-034` physical-device). Local-emulator прогнан в сессии реализации; external (Firebase Test Lab) и physical-device требуют железа/облака, которых у AI-сессии нет. Эта задача — их выделенный дом, чтобы TASK-73 (код) закрылся, а физическая verification не потерялась.

## Что входит технически (для AI-агента)

- Прогнать инструментальный сценарий `LauncherRoleProvider.apply()` на реальных Xiaomi/Huawei(без GMS)/Samsung — проверить фактический intent target против `vendor-recipes.json`.
- Настроить Firebase Test Lab: GCP-проект, биллинг, CI-job (`oem-matrix-required` метка), 3 устройства. Job-определение (YAML/Gradle) уже частично специфицировано в `T073-032`.
- Зафиксировать evidence (скриншоты + логи) в `verification-evidence/`.

## Состояние

- Draft. Разблокируется после merge TASK-73. Владелец гоняет физические устройства (bucket-стиль, как TASK-128 для Xiaomi); Firebase Test Lab — отдельный операционный шаг (GCP-биллинг).

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 [hand] Xiaomi (MIUI, реальное устройство): тап «Сделать launcher по умолчанию» открывает MIUI-специфичный экран (Настройки → Приложения → По умолчанию → Домашний экран), не generic ROLE-диалог. Evidence: скриншот + adb id + commit hash.
- [ ] #2 [hand] Huawei без GMS (реальное устройство EMUI/HarmonyOS): `check()`/`apply()` не роняют приложение (0 необработанных исключений в логах прогона); при недоступности системного пути видна легибельная текстовая инструкция. Evidence: скриншот + logcat.
- [ ] #3 [hand] Samsung One UI (реальное устройство): generic fallback работает, ничего не ломается. Evidence: скриншот + adb id.
- [ ] #4 [auto:deferred-external] Firebase Test Lab OEM-matrix CI job (`oem-matrix-required` метка) проходит или явно inconclusive на Pixel 8 + Samsung Galaxy S24 + Xiaomi Redmi (T073-032). Требует GCP-проект + биллинг.
<!-- AC:END -->
