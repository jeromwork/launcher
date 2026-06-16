# Research: F-3 Library Choices Spike (A/B Methodology)

**Date**: 2026-06-16 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Status**: Pending execution (2-day spike) before `/speckit.tasks`

---

## Why a spike

Per Clarifications C-30 (Q-5 confirmed (b) 2026-06-16), F-3 baking in **moko-resources** + **Konsist** без proof-of-concept = risk of 1-week rework during implementation если incompatible.

**Spike** = 2 дня controlled comparison, head-to-head, на minimal KMP-проекте. **A/B testing** = pick one **WIN** per rivalry, decision logged here.

---

## Day 1 — String tables library

### Variant A: moko-resources

- **Repository**: [icerockdev/moko-resources](https://github.com/icerockdev/moko-resources)
- **Version target**: 1.6+ (latest stable as of 2026-06)
- **License**: Apache 2.0
- **Maturity**: 5+ years, production-tested by 100+ projects
- **iOS support**: native Swift accessors generated
- **Plural support**: ICU-based via `<plural>` xml resources

### Variant B: Compose Multiplatform Resources

- **Source**: built into JetBrains Compose Multiplatform 1.6+
- **License**: Apache 2.0
- **Maturity**: Stable since CMP 1.6 (early 2024), but adoption ramping
- **iOS support**: native, integrated с CMP iOS target
- **Plural support**: ICU MessageFormat via `compose.components.resources.plural`

### Decision criteria (А vs B head-to-head)

Each variant must satisfy **all 4 mandatory** criteria + score on **4 weighted** criteria:

**Mandatory (must pass)**:
1. **Build successfully** в minimal KMP проекте (commonMain + androidMain). FAIL → eliminate.
2. **Resolve 11 locales** (en/ru/es/zh/ar/hi/pt/de/fr/ja/kk-Latn). Missing locale support → eliminate.
3. **Plural support** для русского (one/few/many/other) и арабского (zero/one/two/few/many/other). No plural API → eliminate.
4. **Typed accessor generation** в Kotlin (либо `MR.strings.foo`, либо `Res.string.foo`). Untyped только String keys → eliminate (loses compile-time safety).

**Weighted (score 1-5)**:

| Criterion | Weight | Notes |
|---|---|---|
| Setup overhead (lines of config) | 2× | Меньше — лучше |
| Build time impact (cold + incremental) | 2× | Меньше — лучше |
| iOS readiness (без custom setup) | 1× | Применимо когда iOS launcher появится (per C-7) |
| Error message clarity при missing key / typo | 1× | Лучше DX = меньше debug time |

### Spike protocol Day 1

**Setup (30 min)**: Create blank KMP project `spike-strings/` с `commonMain + androidMain` source sets. Add both variants as separate branches (`spike-moko`, `spike-compose-res`).

**Task А (90 min)** — moko-resources branch:
1. Add moko-resources Gradle plugin + dependency.
2. Create `src/commonMain/resources/MR/base/strings.xml` с 3 ключами: `hello`, `next_step`, `step_n_of_m` (plural).
3. Add 2 переводов: `MR/ru/strings.xml`, `MR/ar/strings.xml`.
4. Verify generated `MR.strings.hello` typed accessor compiles.
5. Verify plural resolves correctly: `MR.plurals.step_n_of_m(3, 5)` returns правильную форму для RU + AR.
6. Measure: setup overhead (config lines), build time (`./gradlew :spike-strings:clean :spike-strings:assemble`).

**Task B (90 min)** — Compose Resources branch:
Repeat same 6 steps using Compose Multiplatform Resources API (`Res.string.hello`, `Res.plurals.step_n_of_m`).

**Comparison (30 min)**:
- Run mandatory criteria check on both.
- Score weighted criteria.
- Compute total: `score_total = sum(criterion_score × weight)`.
- Pick winner: highest total + zero eliminations.

**Tie-breaker** (если scores within 5%): **moko-resources** wins (more production-tested, larger community).

### Documentation output

Write `research-day1-strings.md` с:
- Final scores table.
- Mandatory criteria checks (pass/fail per variant).
- Decision: **A** or **B** + reason.
- Update [`spec.md` C-8](spec.md#clarifications) с final library choice.
- Update [`plan.md` Technical Context](plan.md#2-technical-context) + Dependency Impact таблицу.

---

## Day 2 — Architecture lint tool

### Variant A: Konsist

- **Repository**: [LemonAppDev/konsist](https://github.com/LemonAppDev/konsist)
- **Version target**: 0.13+ (latest stable 2026-06)
- **License**: Apache 2.0
- **Style**: Kotlin DSL, rules expressed as JUnit-style tests
- **Maturity**: 3+ years, growing community

### Variant B: ArchUnit-kotlin

- **Repository**: [TNG/ArchUnit](https://github.com/TNG/ArchUnit) (Java; Kotlin via interop)
- **License**: Apache 2.0
- **Style**: Java-style fluent API
- **Maturity**: 8+ years, very stable, enterprise-grade

### Decision criteria

**Mandatory (must pass)**:
1. **Rule executes as part of `./gradlew check`** (FR-038 + FR-041). Manual script-only → eliminate.
2. **Detects forbidden import**: тестовый файл с `import com.eastclinic.app.foo` в `core/wizard/` → test fails.
3. **Detects allowed import**: тестовый файл с `import com.eastclinic.localization.foo` в `core/wizard/` → test passes (per FR-038a directional graph).
4. **Failure message содержит 4 элемента FR-039** (file path + imported class + rationale + suggested fix). Missing any → eliminate (poor DX).

**Weighted (score 1-5)**:

| Criterion | Weight | Notes |
|---|---|---|
| Rule syntax simplicity (LOC per rule) | 2× | DRY rules = easier maintenance |
| IDE support (autocompletion, refactoring) | 2× | Kotlin-native tools = better DX |
| Error message default clarity | 2× | Если default clear — не нужно custom messages |
| Multi-module support (`core/wizard/`, `core/localization/`, `core/ui-senior/` все три проверяются одним rule set) | 1× | Меньше boilerplate per module |

### Spike protocol Day 2

**Setup (30 min)**: Reuse `spike-strings/` project, добавить 3 fake modules: `:spike-core-wizard`, `:spike-core-ui-senior`, `:spike-app`. В `:spike-core-wizard` создать файл, который импортирует из `:spike-app` (нарушение).

**Task А (90 min)** — Konsist branch:
1. Add Konsist test dependency.
2. Write rule в `:spike-core-wizard/src/test/kotlin/ImportGuardTest.kt`:
   ```kotlin
   class ImportGuardTest {
     @Test fun `core wizard MUST NOT import from app`() {
       Konsist.scopeFromModule("spike-core-wizard")
         .imports
         .assertFalse { it.name.startsWith("com.eastclinic.spike-app") }
     }
   }
   ```
3. Run `./gradlew :spike-core-wizard:test` → expect FAIL.
4. Remove forbidden import → expect PASS.
5. Verify failure message contains 4 elements (FR-039).

**Task B (90 min)** — ArchUnit-kotlin branch:
Repeat same 5 steps using ArchUnit fluent API:
```kotlin
@Test fun coreWizardDoesNotDependOnApp() {
  noClasses().that().resideInAPackage("..core.wizard..")
    .should().dependOnClassesThat().resideInAPackage("..app..")
    .check(allClasses)
}
```

**Comparison (30 min)**:
- Mandatory checks.
- Weighted scoring.
- Pick winner.

**Tie-breaker**: **Konsist** wins (Kotlin-native, expected better long-term DX).

### Documentation output

Write `research-day2-lint.md` с:
- Final scores.
- Decision + reason.
- Update C-15 + plan.md Technical Context.

---

## What if BOTH variants in a category fail mandatory criteria?

**Day 1 fallback** (both string libs eliminate):
- Pause spike. Document failure mode.
- Open **fresh clarify session** with user: investigate **Lyricist** (Compose-only) or **custom Gradle resource generator**.
- Decision postponed; F-3 spec gets `[NEEDS CLARIFICATION]` marker reopened.

**Day 2 fallback** (both lint tools eliminate):
- Same protocol — fresh clarify session.
- Fallback to **custom Gradle task** (manual Kotlin source scanner) as last resort.

This blocks `/speckit.tasks` — implementation cannot proceed without library choices.

---

## Output deliverables после 2-day spike

| File | Content |
|---|---|
| `research-day1-strings.md` | Day 1 results table + decision |
| `research-day2-lint.md` | Day 2 results table + decision |
| `research.md` (this file) | Updated с executive summary at top: «Decisions: strings = X, lint = Y» |
| [`spec.md` C-8 + C-15](spec.md#clarifications) | Updated с final library choices (или `[NEEDS CLARIFICATION]` if failure) |
| [`plan.md` §2 Technical Context + §8 Dependency Impact](plan.md) | Updated с final libraries |
| `spike-strings/` repository | Disposable demo project (delete after research.md written; or keep as reference) |

---

## Краткое содержание простым русским языком

Этот документ — **методология предварительной проверки библиотек** перед началом реализации.

**Проблема**: в спеке мы выбрали две сторонние библиотеки (moko-resources для переводов, Konsist для проверки архитектурных правил) **по знакомости**, без реальной проверки. Если они не подойдут — потеряем неделю на переписывание.

**Решение**: 2 дня предварительной проверки. По дню на каждую библиотеку. Сравниваем **два варианта** (A vs B), как в маркетинговом A/B тесте, по чётким критериям:

- **День 1**: moko-resources vs Compose Multiplatform Resources — для переводов.
- **День 2**: Konsist vs ArchUnit-kotlin — для архитектурных правил.

**Критерии**:
- **Обязательные** (нет = не подходит): билдится, поддерживает 11 локалей, поддерживает множественные формы (1 шаг / 2 шага / 5 шагов), генерирует типизированный код.
- **Взвешенные** (оценка 1-5): сколько строк настройки, скорость билда, готовность к iOS, понятность ошибок.

**Результат**: после 2 дней — **один выбранный вариант** в каждой паре, записывается в план и спеку. После этого можно безопасно начинать `/speckit.tasks`.

**Если оба варианта не подходят**: пауза, новая сессия с пользователем — выбираем другие альтернативы (Lyricist для строк, custom Gradle task для lint).

**Что это даёт**: 2 дня сейчас экономят до недели потом.
