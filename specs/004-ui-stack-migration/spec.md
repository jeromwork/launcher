# Spec 004: UI Stack Migration to Compose Multiplatform + Material 3

**Status**: Active | **Date**: 2026-05-07 | **Author**: project owner

## Overview

Перевести существующий UI-каркас, реализованный в spec 003 на классическом Android View System (XML + Fragments + ViewBinding), на стек, обязательный по ADR-005:

- **Compose Multiplatform** + **Material 3** для UI-слоя,
- **Kotlin Multiplatform** для domain (`:core` становится KMP-модулем),
- **Decompose** для навигации,
- **Koin** для DI,
- **SQLDelight** + **multiplatform-settings** для persistence (когда понадобится в будущих spec'ах),
- **Coil 3** для изображений.

В рамках этого spec'а подключается стек, мигрирует UI спецификации 003, удаляется legacy-код, и **закрывается T072** (smoke-check двух пресетов на эмуляторе) — последняя несданная задача spec 003.

iOS-bootstrap (создание `:iosApp` модуля) **не входит** в этот spec — он откладывается до отдельного spec'а, инициирующего iOS-разработку. На этом этапе создаётся только пустой `iosMain` source-set, чтобы будущая iOS-сторона не требовала повторной структурной перестройки.

## Problem Statement

После spec 003 текущий UI-стек проекта — Android View System + Fragments. Это даёт работающий каркас, но:

1. **Не переносится на iOS.** ADR-005 фиксирует Compose Multiplatform как обязательный стек для обеих платформ. Нынешний XML-based UI на iOS не работает.
2. **Дизайн «убогий».** Базовая `Theme.Launcher.Workspace` без дизайн-системы — visual quality продукта далёк от ожидаемого для production-приложения для пожилых пользователей.
3. **Дублирование кода в будущем.** Любая фича, добавляемая сейчас в `app/`, потом потребует переписывания на CMP — и это уже видно в плане specs 005–010.

ADR-005 (2026-05-07) принят как ответ на эти проблемы. Этот spec — первая фича, реализующая ADR-005 на практике, и одновременно — gateway для всех последующих spec'ов.

## User Stories

| ID | Story | Acceptance Criteria |
|----|-------|---------------------|
| US-401 | Пожилой пользователь видит экраны с современной типографикой и крупными элементами | Все user-facing экраны используют Material 3 + senior-safe override (≥18sp body, ≥56dp tap-target, контраст ≥4.5:1). Visual diff с 003 показывает заметно более современный вид. |
| US-402 | Пользователь сохраняет всю функциональность, реализованную в spec 003 | T072 smoke-check проходит: FirstLaunch picker → выбор preset → Home → Flow grid → tile tap → confirmation → handoff (mock). Settings → preset switch → recreate. Все 7 user stories spec 003 (US-301..US-307) работают. |
| US-403 | Производительность не деградирует относительно 003 | Cold start `HomeActivity` ≤ 600 мс на medium-tier (Pixel 4a class). Cold start `FirstLaunchActivity` ≤ 700 мс. 0 dropped frames на скролле Workspace grid. |
| US-404 | Команда (или AI agent) может добавлять новые экраны на одном UI-стеке для обеих платформ | После закрытия этого spec'а — все новые экраны (specs 005–010) пишутся как Composable в `:core/commonMain/ui/`. Ничего нового не добавляется в View System. |

## Scope

### In Scope

- Подключение KMP plugin, Compose Multiplatform plugin, Material 3, Decompose, Koin, SQLDelight, multiplatform-settings, Coil 3, kotlinx-coroutines, kotlinx-serialization в `gradle/libs.versions.toml` и `build.gradle.kts`.
- Превращение `:core` в KMP-модуль:
  - `commonMain` — текущий pure-Kotlin code (модели, репозитории-интерфейсы, ProfileEngine, ActionDispatcher, EventRouter, MockFlowRepository, InMemoryPresetRepository, валидаторы);
  - `androidMain` — `actual` для AppIndex, SystemEventBridge, WhatsAppLaunchabilityResolver, ReturnContextStore, DataStorePresetRepository (последний переезжает на `multiplatform-settings`);
  - `iosMain` — пустой stub для будущего.
- `docs/dev/design-system.md` — Material 3 seed-color, типографика (15 ролей), shapes, elevation, senior-safe override.
- `:core/commonMain/ui/` — Composable экраны: `FirstLaunchScreen`, `HomeScreen`, `FlowScreen`, `SettingsScreen`, `AddFlowWizardScreen`, `AddSlotWizardScreen`, `AdminDevicesScreen`.
- `:core/commonMain/ui/components/` — общие Composable: `AppButton`, `PresetCard`, `TileCard`, `BottomFlowBar`, `ConfirmationOverlay`, `WarningOverlay`, `WizardStep`, `ListSection`.
- `:core/commonMain/navigation/` — `RootComponent` (Decompose), `Config` sealed class, `StackNavigation<Config>`.
- `:core/commonMain/di/` — Koin-модули.
- `:app` — тонкий Android entry: `LauncherApplication` (стартует Koin), `HomeActivity` и `FirstLaunchActivity` как ComponentActivity-wrapper'ы вокруг Compose.
- Удаление: все XML layouts из `app/src/main/res/layout/`, все Fragment-классы в `:app`, ViewBinding-обвязка, `FragmentManager` навигация, старые Robolectric Activity-тесты.
- Замена тестов: Compose UI tests через `createComposeRule` в `:core/androidUnitTest`.
- T072 smoke-check (закрытие spec 003) — двух эмуляторов, оба пресета (workspace + simple-launcher).
- Обновление `specs/003-ui-skeleton/tasks.md`: T072 `[X]`, change-log про CMP-миграцию.

### Out of Scope

- Реальный iOS bootstrap (создание `:iosApp` модуля, Xcode-проект, AppDelegate). Откладывается до отдельного spec'а.
- Реальный action dispatch (это spec 005 — `action-architecture-v2`).
- Cupertino-look на iOS (фиксировано: Material 3 на обеих платформах per ADR-005 Amendment 2026-05-07b).
- Реальная backend/sync функциональность (это specs 007–008).
- Изменения в `specs/001-*` и `specs/002-*` — они исторически зафиксированы.

## Related Project Context

- `.specify/memory/constitution.md` — binding governance (Articles I, II, V, VI, VIII, IX, XI, XIII, XV, XVI, XVII).
- `docs/governance/document-map.md` — навигация по документам.
- `docs/adr/ADR-001-cross-platform-strategy.md` (partially superseded by ADR-005) — Platform Parity Gate, isolation principle (reaffirmed).
- `docs/adr/ADR-005-ui-stack-compose-multiplatform.md` — обязательный UI и domain stack; constitutional exceptions; mandatory gates; performance targets.
- `docs/adr/ADR-004-localization-and-global-readiness.md` — все строки локализуемы.
- `docs/dev/design-system.md` — создаётся в этом spec'е.
- `.claude/skills/android-emulator/SKILL.md` — единственный источник процедур запуска эмуляторов, install/screenshot/T072.
- `docs/product/roadmap.md` — этот spec — позиция 004; specs 005–010 после него зависят от завершения 004.
- `docs/product/senior-safe-launcher-plan.md` — целевая аудитория (определяет senior-safe override).
- `docs/compliance/permissions-and-resource-budget.md` — этот spec не добавляет новых разрешений; ресурсный delta фиксируется в `plan.md`.
- `specs/003-ui-skeleton/spec.md`, `tasks.md` — функциональный source of truth, который мигрируется (US-301..US-307 должны продолжать работать).

## Non-Functional Requirements

| Категория | Требование |
|-----------|------------|
| Accessibility | Material 3 + senior-safe override: ≥18sp body, ≥56dp tap-target, контраст ≥4.5:1, иконки primary-actions с подписями. TalkBack — путь до primary action ≤ 3 свайпа на каждом экране. |
| Performance | Cold start `HomeActivity` ≤ 600 мс на medium-tier; `FirstLaunchActivity` ≤ 700 мс; 0 dropped frames на основных скроллах (per ADR-005 required performance targets). |
| APK size | Debug ≤ 18 МБ; release ≤ 12 МБ (per ADR-005). |
| Battery | Никакого нового background-кода. CMP runtime не вводит фоновых задач сверх существующих. |
| Локализация | Все user-facing строки в `:core/commonMain/composeResources/values/strings.xml` (per ADR-004). |
| Cross-platform readiness | 100% UI-кода в `commonMain`. Android-only платформенные API только в `androidMain` через `expect`/`actual`. iOS source-set заводится пустым, но компилируется. |
| Visual consistency | Один `MaterialTheme` на всё приложение; все размеры/цвета/формы из `MaterialTheme.colorScheme/typography/shapes`, никаких литералов в экранах. |
