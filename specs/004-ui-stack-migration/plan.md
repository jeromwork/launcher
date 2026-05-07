# Implementation Plan: UI Stack Migration (Spec 004)

**Branch**: `004-ui-stack-migration` | **Date**: 2026-05-07 | **Spec**: [spec.md](./spec.md)

## Summary

Реализовать обязательный по ADR-005 UI-стек (Compose Multiplatform + Material 3 + KMP + Decompose + Koin + SQLDelight) и мигрировать все экраны spec 003 с View System на Composable. Закрыть T072 spec 003 на двух эмуляторах. iOS-таргет создаётся как пустой source-set без реального bootstrap'а — отдельный spec инициирует iOS-разработку позже.

## Technical Context

- Kotlin 2.0.21+, JDK 17, AGP 8.7.3+
- **Новый стек:** Compose Multiplatform 1.7.x, Material 3, Decompose 3.x, Koin 4.x, SQLDelight 2.x, multiplatform-settings 1.2.x, Coil 3, kotlinx-coroutines, kotlinx-serialization.
- **Удаляемое:** Android View System в `app/src/main/res/layout/`, все Fragment'ы в `:app`, ViewBinding, FragmentManager-навигация, Robolectric Activity-тесты для UI.
- **Сохраняемое:** AccessibilityService и связанная Android-only логика остаются в `androidMain`. HOME-intent в манифесте `:app` — без изменений. Текущий `:core` бизнес-код переезжает в `commonMain` без переписывания (constructor-injection форма сохраняется).
- Storage в этом spec'е не меняется (mock JSON в assets через `MockFlowRepository` + `multiplatform-settings` для preset preference). SQLDelight подключается, но используется в spec 008.
- Testing: `kotlin.test` для commonMain, `createComposeRule` для Compose UI tests в androidUnitTest.
- Targets: Android minSdk 26, targetSdk 35; iOS — компилируется в пустой framework (deferred).

## Constitution Check

### Architecture Gate — PASS
Проект остаётся в layered Android architecture (Article IV): UI (Compose), data/domain orchestration (`:core`/commonMain), data sources (`androidMain` actuals). Никаких новых Gradle-модулей сверх существующих `:core` и `:app` (Article V §2 — initial structure compact). KMP-структура — не новый модуль, а внутренняя реструктуризация source-sets. Layered boundaries сохраняются: feature-код (UI) не дёргает Android API напрямую, только через `commonMain` интерфейсы и Koin.

### Core/System Integration Gate — PASS
`:core/androidMain` остаётся единственным owner-ом Android-системных API (Article VI). AppIndex, SystemEventBridge, WhatsAppLaunchabilityResolver, ReturnContextStore переезжают как `actual`-реализации, но их роль не меняется. UI-слой видит их только через интерфейсы из `commonMain`. Никаких новых listener'ов / receiver'ов / services не добавляется.

### Configuration Gate — PASS
`flows_mock.json` schema не меняется. `MockFlowRepository` переезжает в `commonMain` без изменения API. `DataStorePresetRepository` заменяется на `MultiplatformSettingsPresetRepository` — это рефакторинг под интерфейс из spec 003, schema preset preference остаётся та же (один key-value: `active_preset` = `workspace|launcher|simple-launcher`). Migration-обвязка не нужна (тот же ключ, тот же тип).

### Required Context Review Gate — PASS
Reviewed:
- `.specify/memory/constitution.md` — Articles XI §1, XIII §4 — exceptions documented по Article XVII §3 в ADR-005.
- `docs/governance/document-map.md` — ADR-005 уже добавлен.
- `docs/adr/ADR-001-cross-platform-strategy.md` — partially superseded by ADR-005; reaffirmed parts (Decision §3, §4, §5) учтены.
- `docs/adr/ADR-005-ui-stack-compose-multiplatform.md` — основа этого spec'а; все mandatory gates ниже выполняются.
- `docs/adr/ADR-004-localization-and-global-readiness.md` — strings локализуемы из commonMain.
- `docs/dev/emulators.md` — процедура двух эмуляторов для T072.
- `docs/product/roadmap.md` — этот spec встаёт в позицию 004, разблокирует 005–010.
- `docs/product/senior-safe-launcher-plan.md` — accessibility/UX target определяет senior-safe override темы.
- `specs/001-launcher-core-foundation/plan.md` — контрактная модель CoreContractVersions сохраняется.
- `specs/003-ui-skeleton/spec.md`, `tasks.md` — мигрируется функциональность.

Not directly impacted: ADR-002 (entitlement), ADR-003 (monetization), `docs/compliance/*` — этот spec не добавляет permissions, payment flow, distribution-changes.

### Accessibility Gate — PASS
- Все Composable'ы используют `MaterialTheme` с senior-safe override: ≥18sp body, ≥56dp tap-target, контраст ≥4.5:1.
- TalkBack паттерны: `Modifier.semantics { contentDescription = ... }` на всех интерактивных элементах. Acceptance — путь до primary action ≤ 3 свайпа.
- Поведение пресетов сохраняется: `simple-launcher` — крупнее шрифты и тапы, `workspace` — стандартный, `launcher` — промежуточный (per Theme.Launcher.* из spec 003 переходит в density-modifier для Compose).
- Animation policy (Article VIII §5): анимации опциональны, читаемость приоритет — `AnimatedContent` используется только в wizards, не в основной навигации.

### Battery/Performance Gate — PASS
- CMP runtime запускается один раз в `LauncherApplication.onCreate()` через Koin; нет polling, нет repeated init.
- Cold start targets per ADR-005 (`HomeActivity` ≤ 600 мс, `FirstLaunchActivity` ≤ 700 мс) — обязательны, проверяются в Phase 5 (T072 + perf measurement).
- 0 dropped frames на скроллах — проверяется через `Choreographer.FrameCallback` или `JankStats` API в perf-checkpoint.
- Никакой новой системной активности (broadcasts, services, work).

### Testing Gate — PASS
- **Contract**: `MockFlowRepositoryTest` остаётся, переезжает в `:core/commonTest`.
- **Integration**: `PresetRepositoryTest` (multiplatform-settings backend) — новый, в commonTest.
- **UI tests**: Compose UI tests для каждого экрана через `createComposeRule` (FirstLaunchScreen, HomeScreen, FlowScreen, SettingsScreen, wizard'ы) — `:core/androidUnitTest`.
- **Smoke E2E**: T072 — full-path manual run на двух эмуляторах per `docs/dev/emulators.md`.
- Old Robolectric Activity tests из `:app` удаляются (Activities становятся тонкими wrappers без логики для теста).

### Simplicity Gate — PASS
- Никаких новых абстракций сверх стека ADR-005. Decompose `RootComponent` — единственная точка root-routing, не дублируется. Koin-модулей минимум: один core-module + один platform-module (per Article V §6 «no utility dumping ground»).
- Composable-компоненты в `commonMain/ui/components/` создаются **только** при втором использовании паттерна — никаких speculative wrapper'ов «на будущее» (Article XI §2).
- Tests не пишутся для UI-helpers, которые тривиально дёргают Material 3 примитивы (Article X §2 — tests selected by risk).

### Cross-Platform Implementation Gate (per ADR-005) — DOCUMENTED

| Экран / компонент | Source-set | Обоснование |
|---|---|---|
| FirstLaunchScreen, HomeScreen, FlowScreen, SettingsScreen, wizard'ы, AdminDevicesScreen | `commonMain` | UI стандартный Material 3, не зависит от платформы |
| AppButton, PresetCard, TileCard, BottomFlowBar, ConfirmationOverlay, WarningOverlay | `commonMain` | Composable-компоненты без платформенного API |
| RootComponent + Decompose Config | `commonMain` | Decompose работает на обеих платформах |
| Koin-модули (`coreModule`, `repositoryModule`) | `commonMain` | Регистрация common-классов |
| Android-platform Koin-модуль | `androidMain` | actual'ы для AppIndex и т.п. — Android-specific |
| iOS-platform Koin-модуль | `iosMain` (stub) | пустой; заполнится в iOS-spec'е |
| Models, repositories, ProfileEngine, ActionDispatcher, MockFlowRepository | `commonMain` | уже pure Kotlin, переезжает 1:1 |
| AppIndex, SystemEventBridge, WhatsAppLaunchabilityResolver, ReturnContextStore | `androidMain` (`actual class`) | Android API direct |
| MultiplatformSettingsPresetRepository | `commonMain` (использует `multiplatform-settings`) | KMP-обёртка над DataStore (Android) и UserDefaults (iOS) |
| HomeActivity, FirstLaunchActivity, LauncherApplication, AccessibilityService | `:app/src/main` (Android only) | Activity и Application — Android-API entry points |

### Documented Platform Asymmetry (per ADR-005) — DOCUMENTED

| Asymmetry | Платформа | Причина | Обработка |
|---|---|---|---|
| HOME-intent (ROLE_HOME, default-launcher selection) | Android only | iOS не позволяет заменять home screen | На Android: launcher работает в HOME-mode после ROLE_HOME grant. На iOS: те же экраны (Workspace/Settings/etc.) запускаются как обычное приложение. UI идентичен. |
| AccessibilityService и WhatsApp-return AccessibilityEvent listener (из spec 002) | Android only | Нет аналога в iOS; Apple даёт другие deep-link-back механизмы | В этом spec'е не трогаем — AccessibilityService остаётся в `:app/androidMain` как был. iOS — будущая задача. |
| `Theme.Launcher.SimpleLauncher` density override | Both | Senior-safe override в дизайн-системе работает на обеих платформах через `MaterialTheme` density-modifier | Один источник правды в `:core/commonMain/ui/theme/` |

### Resource Budget Delta (per ADR-005)

| Dimension | Impact | Notes |
|---|---|---|
| Permissions | **0 новых** | Этот spec не запрашивает permissions |
| APK debug | **+5–10 МБ** | CMP runtime + Material 3 + Decompose + Koin + SQLDelight + Coil 3 + multiplatform-settings. ADR-005 budget: ≤ 18 МБ debug. Текущий ~4 МБ → ожидаемо ~10–12 МБ. |
| APK release | **+3–6 МБ** | После R8/ProGuard. Budget: ≤ 12 МБ release. |
| RAM при активном UI | **+30–80 МБ** | Compose runtime overhead. Acceptable per ADR-005. |
| Battery | **0 delta** | Нет нового background work |
| Storage | **0 delta** | flows_mock.json остаётся; preset preference переезжает на multiplatform-settings (тот же DataStore-файл под капотом на Android) |
| Network | **0 delta** | |
| Cold start `HomeActivity` | **+200–500 мс** | Compose first-frame overhead. Budget: ≤ 600 мс на medium-tier. Текущий ~150–250 мс → ожидаемо ~400–550 мс. |

### Platform Parity Gate (ADR-001, reaffirmed by ADR-005) — DOCUMENTED

- **Android**: реализуется в этом spec'е.
- **iOS**: source-set заводится, компилируется. UI-код 100% переносится. Реальный iOS bootstrap (Xcode project, AppDelegate, distribution) — отдельный spec.
- **Asymmetries**: HOME-mode (выше), AccessibilityService (выше). Документировано.
- **Promises**: к моменту, когда iOS-разработка стартует, требуется только подключить `:iosApp` модуль и реализовать iosMain `actual`'ы — UI полностью готов.

## Project Structure

```
specs/004-ui-stack-migration/
├── spec.md
├── plan.md          ← этот файл
└── tasks.md

docs/dev/
├── emulators.md         (создан ранее)
└── design-system.md     (создаётся в Phase 1)

core/
├── build.gradle.kts        (KMP plugin, compose plugin)
├── src/
│   ├── commonMain/
│   │   ├── kotlin/com/launcher/
│   │   │   ├── api/                  (модели, репозитории-интерфейсы — переезд из текущего :core)
│   │   │   ├── core/                 (ProfileEngine, ActionDispatcher и т.п. — переезд)
│   │   │   ├── ui/
│   │   │   │   ├── theme/            (LauncherTheme, senior-safe override)
│   │   │   │   ├── components/       (AppButton, TileCard, BottomFlowBar, overlays, ...)
│   │   │   │   └── screens/          (FirstLaunchScreen, HomeScreen, FlowScreen, ...)
│   │   │   ├── navigation/           (RootComponent, Config sealed class)
│   │   │   └── di/                   (Koin modules — coreModule, repositoryModule)
│   │   ├── composeResources/
│   │   │   └── values/strings.xml    (локализуемые строки)
│   │   └── sqldelight/               (схемы для будущих spec'ов; в этом spec'е пусто)
│   ├── androidMain/
│   │   └── kotlin/com/launcher/
│   │       ├── core/                 (AppIndex, SystemEventBridge, WhatsAppLaunchabilityResolver, ReturnContextStore — actual'ы)
│   │       └── di/                   (androidPlatformModule)
│   ├── iosMain/
│   │   └── kotlin/com/launcher/
│   │       ├── core/                 (stubs: throw NotImplementedError или заглушки до iOS-spec'а)
│   │       └── di/                   (iosPlatformModule — пустой)
│   ├── commonTest/
│   │   └── kotlin/com/launcher/      (MockFlowRepositoryTest, PresetRepositoryTest, ProfileEngineTest, ...)
│   └── androidUnitTest/
│       └── kotlin/com/launcher/ui/   (Compose UI tests через createComposeRule)
│
app/
├── build.gradle.kts        (compose plugin, depends on :core)
├── src/main/
│   ├── kotlin/com/launcher/app/
│   │   ├── LauncherApplication.kt    (стартует Koin, начальная инициализация)
│   │   ├── HomeActivity.kt            (ComponentActivity wrapper → ChildStack render через Decompose)
│   │   ├── FirstLaunchActivity.kt     (ComponentActivity wrapper → FirstLaunchScreen)
│   │   ├── service/                   (AccessibilityService и связанная Android-only логика)
│   │   └── di/appModule.kt            (Application-уровень DI: Koin start)
│   └── AndroidManifest.xml            (без изменений по intents/permissions)
├── src/main/res/                       (минимум: app icon, themes.xml для splash, никаких layouts)
├── (удалено) src/main/res/layout/*
├── (удалено) src/main/java/com/launcher/app/{flow,settings,wizard,admin,communication}/*
└── (удалено) ViewBinding obsolete code
```

## Open decisions deferred to first use

- Точная JSON-структура `data class Config` Decompose — наколлектится в Phase 3 при реализации RootComponent.
- Стартовая seed-color Material 3 — выбирается в Phase 1 при создании `design-system.md` (тёплый, дружелюбный, контрастный — kandidate: `#5D4037` brown / `#3E5F8A` blue).
- Конкретные имена Koin-модулей — стандартный набор (coreModule, repositoryModule, androidPlatformModule, iosPlatformModule) фиксируется в Phase 2.
