# Research: F-3 Library Choices

**Date**: 2026-06-16 (REVISED 2026-06-17 post pre-flight) | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Status**: **CLOSED** — all library choices fixed by existing project, no spike needed.

---

## TL;DR

Original plan (2026-06-16) called for **2-day A/B spike** comparing:
- moko-resources vs Compose Multiplatform Resources (string tables)
- Konsist vs ArchUnit-kotlin (architecture lint)

**Pre-flight check 2026-06-17** обнаружил, что **все эти решения уже зафиксированы** существующим проектом per [ADR-005](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md) Amendment 2026-05-07a + `core/build.gradle.kts` + `libs.versions.toml`:

| Question | Existing project choice | Spike needed? |
|---|---|---|
| String tables | **Compose Multiplatform Resources** (`compose.components.resources` уже в `core:commonMain`) | ❌ NO |
| Architecture lint | **Konsist** (`libs.versions.toml` + `core:androidUnitTest`, declared в spec 005 §8) | ❌ NO |
| DI framework | **Koin** (per ADR-005 Amendment, уже в core) | ❌ NO |
| Navigation | **Decompose** (per ADR-005 Amendment, уже в core) | ❌ NO |
| Persistence (structured) | **SQLDelight** (per spec 008 `LocalConfigStore`) | ❌ NO |
| Persistence (key-value) | **DataStore Preferences** (уже в `core:androidMain`) | ❌ NO |
| Screenshot tests | **Roborazzi** (decision 2026-06-17, new dep needed) | minor — verify integration |

**Result**: spike eliminated, replaced by single 30-minute verification task T001 (per plan.md §10 REVISED).

---

## Historical context — why spike was originally planned

При написании F-3 spec'и (2026-06-16) я (Claude) не делал pre-flight check существующего кода — это была ошибка. План предполагал, что F-3 — greenfield foundation, нужно выбрать stack:

- Compose Resources считался «не таким зрелым» как moko-resources на iOS.
- Konsist vs ArchUnit-kotlin — выбор по «знакомости».
- DI и navigation отложены в plan.md как «implementation detail».

После запуска `/speckit.analyze` 2026-06-17 user попросил pre-flight check. Grep `core/build.gradle.kts` + `libs.versions.toml` + `docs/adr/` показал, что:
1. Compose Multiplatform — обязательный стек (ADR-005), iOS targets уже включены.
2. Все библиотеки выбраны Amendment 2026-05-07a (Koin + Decompose).
3. SQLDelight + DataStore patterns установлены спеками 006, 008.
4. Konsist declared spec 005 §8 fitness functions.

Spike отменён, plan.md + tasks.md + spec.md переписаны под реальный стек.

---

## What replaces the spike: 30-min verification (T001-T003)

См. [tasks.md Phase 0](tasks.md). Three smoke-verification tasks:

1. **T001** — Create empty packages in `core/src/commonMain/kotlin/com/launcher/{api,ui}/{wizard,localization,senior}/`. Verify `./gradlew :core:build` passes.
2. **T002** — Smoke-test Konsist in `core/src/androidUnitTest/kotlin/com/launcher/arch/SmokeArchitectureTest.kt`. Verify `./gradlew :core:testRealBackendUnitTest` runs Konsist successfully.
3. **T003** — Smoke-test Compose Resources: add `wizard.test_string` to `composeResources/values/strings.xml` + `values-ru/strings.xml`, write commonTest verifying resolution.

If any fails — pause + fresh clarify session on what's missing.

---

## Краткое содержание простым русским языком

Этот документ — **исторический контекст** про library spike, который мы **отменили**.

**Что случилось**: я (Claude) в начале написал план так, будто проект пустой — нужно с нуля выбрать библиотеки для переводов, для проверки архитектуры и т.д. Я предложил 2-дневный эксперимент (spike), чтобы выбрать лучшие.

**Что выяснилось при проверке кода**: проект **не пустой**. Все эти библиотеки **уже выбраны** в проекте раньше другими спеками. Конкретно:
- Для переводов — **Compose Multiplatform Resources** (от Google).
- Для проверки архитектуры — **Konsist** (уже подключён).
- Для DI и навигации — **Koin** и **Decompose** (per [ADR-005](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md)).
- Для хранилищ — **DataStore Preferences** (простые) + **SQLDelight** (сложные).

**Результат**: 2-дневный spike **не нужен**. Заменён 30-минутной проверкой (T001-T003), что эта существующая инфраструктура реально работает для F-3 use case.

**Урок**: всегда делать pre-flight check перед написанием плана. План должен следовать существующим решениям проекта, не выдумывать новые.
