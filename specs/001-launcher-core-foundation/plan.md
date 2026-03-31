# Implementation Plan: Launcher Core Foundation

**Branch**: `001-launcher-core-foundation` | **Date**: 2026-03-28 | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/001-launcher-core-foundation/spec.md`

**Note**: This plan establishes the **greenfield** Android module layout, Core components, contracts, and test strategy for the accessibility-first launcher platform. The repo currently contains specs and `.specify` only; implementation tasks will create Gradle projects.

## Summary

Deliver a **minimal, production-minded Core** in a **`core`** Gradle module with a thin **`app`** shell hosting **HomeActivity**, satisfying **spec.md** ownership table and extension rules. Core owns **SystemEventBridge**, **EventRouter**, **ModuleRegistry**, **ProfileEngine**, **AppIndex**, **ActionDispatcher**, fallback hierarchy, and published contracts documented under `contracts/`. **View + XML only**; **no Compose**. **No feature Gradle module** in the first increment—`feature-*` is prepared via documentation and `settings.gradle.kts` template to satisfy Article V restraint. System integration is **event-driven**, **no polling**, **no `BOOT_COMPLETED`** in MVP unless a future spec requires it.

## Technical Context

**Language/Version**: Kotlin 2.0.x, JDK 17  
**Primary Dependencies**: AndroidX (AppCompat, Core KTX, Lifecycle, Activity), Kotlin coroutines + Flow; no Compose BOM  
**Storage**: Bundled profile JSON (assets) for MVP; optional DataStore noted in research for later  
**Testing**: JUnit, MockK, Robolectric (`:core` unit tests); Android instrumented tests only where `PackageManager` behavior demands  
**Target Platform**: Android — `minSdk 26`, `targetSdk 35`, `compileSdk 35`  
**Project Type**: Mobile app (multi-module Android launcher)  
**SDK roles (read with spec)**: **`minSdk`** = минимальный API для **установки** и гарантий спеки (**API 26**, см. **spec.md → § Device and API compatibility**, FR-029–FR-038). **`compileSdk` / `targetSdk`** = сборка и политика платформы/магазина; сами по себе **не** отрезают старые устройства, пока не поднимают **`minSdk`**.  
**Manual device matrix (MVP)**: Проверять на эмуляторе/устройстве с **API 26** (нижняя граница спеки) и на образе с **API 35** (совпадает с **targetSdk** в Gradle). Так устраняется расхождение с формулировками «текущий API» без привязки к целевому SDK.  
**Performance Goals**: Cold start: non-blocking home shell; AppIndex rebuild off main thread; debounced package events — целевой debounce для серии `PackageSetChanged` согласовать с [contracts/project-events.md](./contracts/project-events.md) (ориентир **≤ 200 ms**, не polling).  
**Constraints**: Spec — View/XML, OS listeners only in Core, no default background services, deterministic fallback hierarchy; Constitution — battery, a11y, modular growth  
**Scale/Scope**: MVP Core + shell only; first real `feature-*` with next feature spec

### Test matrix (spec FR-028)

Эта таблица фиксирует, **чем** покрываются части фундамента (обязательство **FR-028**). Обновлять при добавлении сценариев.

| Область | Contract / JVM (Robolectric) | Integration (JVM/Android) | E2E / instrumented |
|--------|------------------------------|----------------------------|----------------------|
| LauncherCore — smoke start/stop | Robolectric (`LauncherCoreTest`) | — | — |
| ProfileEngine — fallback, invalid JSON | Unit (`ProfileEngineTest`) | — | Опционально: подмена assets на устройстве |
| CompositionResolver — порядок precedence | Unit (`CompositionResolverTest`) | — | — |
| ModuleRegistry — деградация контрактов | Unit (`ModuleRegistryTest`) | — | — |
| EventRouter — дедуп / порядок событий | Unit (`EventRouterTest`) | — | — |
| AppIndex — снимок каталога | Robolectric + mock `PackageManager` (`AppIndexTest`) | — | **Отложено:** реальные ограничения видимости пакетов, если Robolectric недостаточен |
| ActionDispatcher — policy / blocked | Unit (`ActionDispatcherTest`) | — | — |
| Home shell — старт после cold start | — | — | Ручной прогон на API 26 и API 35 |
| Accessibility (конституция Art. VIII) | Поля в моделях каталога | — | **MVP:** ручной чеклист по layout (`contentDescription`, размер таргетов); автоматизация (Espresso a11y и т.д.) — в следующих фичах |

**Instrumented backlog**: см. **T042** в [tasks.md](./tasks.md) (отложено до необходимости); если сценарий не делается — зафиксировать это в матрице ниже.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Architecture Gate

| Question | Result |
|----------|--------|
| Layered architecture? | **Pass.** `app` = UI shell; `core` = orchestration + platform integration + “domain” services (AppIndex, dispatch, profile); future data sources stay behind Core internals. |
| New Gradle modules justified? | **Pass.** Two modules: `core` isolates OS listeners and contracts; `app` owns Activity/XML. Third `feature-*` **deferred** per research (Article V)—documented template only. |
| Boundaries explicit? | **Pass.** Ownership table in spec mirrored; `contracts/` lists `contractId` + majorVersion. |

### Core / System Integration Gate

| Question | Result |
|----------|--------|
| System events required? | **Yes** — package changes for AppIndex (MVP). |
| Centralized in Core? | **Pass.** **SystemEventBridge** sole registrar for MVP broadcasts; features use **EventRouter** only. |
| Per-listener documentation? | **Pass.** Каноническое описание MVP-слушателей — [research.md](./research.md) §6; оперативная копия для ревью — `PLATFORM_EVENTS.md` (создаётся по [tasks.md](./tasks.md) US3); при изменении править **сначала** research §6, затем синхронизировать остальное. |

### Configuration Gate

| Question | Result |
|----------|--------|
| Profiles / schema / migrations? | **Pass.** Profile bootstrap contract + `data-model.md`; MVP uses `schemaVersion` + safe default fallback; field-level schema deferred per spec non-goals. |
| Validation / compatibility? | **Pass.** ProfileEngine validates; ModuleRegistry checks `requiredContracts`; conflict order in `data-model.md`. |

### Accessibility Gate

| Question | Result |
|----------|--------|
| Elderly / a11y users? | **Pass.** Catalog contract requires labels and content descriptions; Home shell XML must use large touch targets and sufficient contrast—enforced in **implementation review** and future UI spec; Core must not block large-text/high-contrast profiles (spec Assumptions). |
| Acceptance criteria? | **Pass.** Observable **CatalogSnapshot** and **ProfileSnapshot** for tests; a11y fields mandatory on entries exposed to UI. Для **FR-028 / Art. VIII.6** в MVP добавлен **ручной** приёмочный путь в матрице тестов выше; автотесты a11y не блокируют первый инкремент. |

### Battery / Performance Gate

| Question | Result |
|----------|--------|
| Background cost? | **Pass.** No `BOOT_COMPLETED` in MVP; no polling; package-driven refresh only; debounce coalescing in EventRouter. |
| Startup? | **Pass.** Defer AppIndex full rebuild to IO after shell shows safe empty/loading state per implementation tasks. |

### Testing Gate

| Question | Result |
|----------|--------|
| Contract / integration / regression? | **Pass.** См. **§ Test matrix (FR-028)**; JVM/Robolectric покрывают основной риск MVP. Instrumented — см. отложенную строку в матрице. |
| Failure modes? | **Pass.** Invalid profile, missing module, contract mismatch, permission-blocked dispatch—each maps to spec edge cases. |

### Simplicity Gate

| Question | Result |
|----------|--------|
| Speculative abstraction? | **Pass.** No Hilt/Dagger in MVP; no extra modules; no Compose. |
| Reducible? | **Pass.** Flat Core packages by responsibility (bridge, router, registry, profile, index, dispatch) before any unnecessary layering. |

---

### Constitution Check — Post Phase 1 design (re-evaluation)

All gates **remain Pass.** Phase 1 added `data-model.md`, `contracts/*`, and `research.md` without widening OS listener scope or adding modules. **No violations** → Complexity Tracking table left empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-launcher-core-foundation/
├── plan.md
├── tasks.md
├── research.md
├── data-model.md
├── quickstart.md
├── EXTENSION_GUIDE.md
├── PLATFORM_EVENTS.md
├── contracts/
│   ├── README.md
│   ├── profile-bootstrap.md
│   ├── project-events.md
│   ├── app-index.md
│   ├── actions.md
│   └── module-registration.md
├── spec.md
└── checklists/
```

### Source Code (repository root) — target after implementation

```text
settings.gradle.kts          # include(":app", ":core") + comment template for :feature:*
gradle/
build.gradle.kts             # or libs.versions.toml
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/launcher/app/HomeActivity.kt
    │   ├── java/com/launcher/app/LauncherApplication.kt
    │   └── res/layout/activity_home.xml
    └── androidTest/         # as needed
core/
├── build.gradle.kts
└── src/
    ├── main/java/com/launcher/
    │   ├── api/             # published contract types (com.launcher.api)
    │   └── core/            # com.launcher.core.*
    │       ├── LauncherCore.kt
    │       ├── bridge/SystemEventBridge.kt
    │       ├── events/EventRouter.kt
    │       ├── modules/ModuleRegistry.kt
    │       ├── profile/ProfileEngine.kt
    │       ├── catalog/AppIndex.kt
    │       └── actions/ActionDispatcher.kt
    └── test/java/com/launcher/core/   # unit + contract-style tests
```

**Structure Decision**: Standard Android **two-module** MVP (`app` + `core`). **Published API** (контрактные типы для модулей): пакет **`com.launcher.api`**, каталог `core/src/main/java/com/launcher/api/`. Реализация Core: пакеты **`com.launcher.core.*`**. Application shell: **`com.launcher.app.*`**. Имена согласованы с [tasks.md](./tasks.md). First **`feature-*`** module added when a vertical feature spec is approved; `settings.gradle.kts` will document the naming pattern.

## Complexity Tracking

> No constitution violations requiring justification.

## Phase 0: Research (complete)

**Output**: [research.md](./research.md) — toolchain, MVP listener set, DI stance, testing, module graph deferral.

## Phase 1: Design (complete)

**Output**:

- [data-model.md](./data-model.md)
- [contracts/](./contracts/)
- [quickstart.md](./quickstart.md)

**Agent context**: Run `update-agent-context.ps1 -AgentType cursor-agent` after plan save.

## Phase 2 — Tasks (complete)

**Output**: [tasks.md](./tasks.md) — исполняемый backlog (T001–T042, **T042** опционально), синхронизирован с этим планом и **§ Test matrix**. При смене стратегии тестов обновлять и план (матрицу), и задачи.
