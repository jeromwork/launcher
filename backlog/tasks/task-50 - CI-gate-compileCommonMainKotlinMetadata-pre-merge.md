---
id: TASK-50
title: CI gate compileCommonMainKotlinMetadata pre-merge
status: Draft
assignee: []
created_date: '2026-06-24 22:25'
updated_date: '2026-06-24 22:25'
labels:
  - phase-5
  - build
  - ci
  - hygiene
milestone: m-4
dependencies: []
references: []
priority: medium
ordinal: 50000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Сейчас в нашем CI (GitHub Actions) перед merge'ом PR гоняется только **Android-таргет** — `:core:compileDebugUnitTestKotlinAndroid` и эквиваленты. **Общий код (`commonMain`)**, который должен работать на всех платформах (Android сегодня, iOS / desktop / web — в будущем), отдельно не проверяется.

Почему это плохо стало видно 2026-06-24 на TASK-7: в `main` пролежал больше месяца сломанный `commonMain` (commit `a9ee31a`, spec 010 Phase 3 wizard) — два файла использовали JVM-only API (`String.format`, `@Volatile` без явного import). Android-сборка их не замечала (там JVM stdlib есть транзитивно), но `:core:compileCommonMainKotlinMetadata` падал. TASK-7 Phase 1+ верификация была заблокирована, пока не починили отдельным chore-PR #29.

**Что происходит по шагам (что хотим добавить):**
1. PR создаётся → CI workflow триггерится.
2. Среди existing checks (lint, Android unit test, Detekt и т.д.) добавляется **новый shape**: `./gradlew :core:compileCommonMainKotlinMetadata`.
3. Если задача падает (JVM-only тип просочился в commonMain, expect/actual не сошлись, multiplatform API изменился) — PR помечается как failed, merge заблокирован.

**Что происходит при попытке добавить JVM-only код в commonMain:**
1. Разработчик / AI-агент пишет `import kotlin.jvm.Volatile` (или вообще без import) в файле под `core/src/commonMain/`.
2. Локально Android-build идёт зелёный (мы часто бежим `:core:testMockBackendDebugUnitTest`).
3. На PR — `compileCommonMainKotlinMetadata` падает с понятным сообщением.
4. Развороачиваемся, исправляем (либо multiplatform-import, либо выносим в `androidMain/`).

## Зачем

**Боль**: pre-existing main breakage не виден до тех пор, пока новая фича не упрётся в него (как TASK-7 Phase 0). Каждый такой "невидимый" break задерживает следующую фичу на отдельный chore-PR + ревью.

**Результат**: всё что пометит себя как кросс-платформенное, **физически не пройдёт в main** без compile-check этой кросс-платформенности.

**Strategic**: TASK-26 (iOS adapter) / TASK-29 (desktop / web) в roadmap'е. Когда iOS реально появится, `commonMain` станет ездить на трёх таргетах. Если до этого момента в `commonMain` накопится JVM-долг, миграция превратится в кошмар. Этот gate — превентивная мера.

## Что входит технически (для AI-агента)

- Найти actual CI workflow file (вероятно `.github/workflows/*.yml`). Если CI пока локальный (нет workflow в репо) — зафиксировать пути и инструменты как часть Output: Decision.
- Добавить gradle invocation `./gradlew :core:compileCommonMainKotlinMetadata` в pre-merge required check matrix.
  - Опция расширения: `./gradlew :core:compileKotlinMetadata` (компилирует все source-sets `commonMain` + `androidMain` метаданные).
- Cache strategy: gradle-build-action с cache (key основан на `gradle/wrapper/gradle-wrapper.properties` + `gradle/libs.versions.toml`).
- Проверить branch protection rules — required status check добавить вручную в GitHub Settings (или Terraform, если используется).
- Опционально: pre-commit hook (через `.pre-commit-config.yaml` или git hook), который бежит ту же задачу локально перед commit. **Не блокировать** — только warning, чтобы dev workflow не страдал.

**Out of scope**: переписывать существующий `commonMain` под более строгий contract (это уже сделано в PR #29 ad-hoc). Только prevention.

## Состояние

Draft. Триггер — incident 2026-06-24 при работе над TASK-7 Phase 0. Не блокирует никакие активные task'и. Может быть подобран любым AI-агентом или владельцем в Phase 5 / parking-lot окно.

---

## Готовый промт для `/speckit.specify`

```
ЧТО СТРОИМ:
CI gate, который перед merge PR гоняет `./gradlew :core:compileCommonMainKotlinMetadata`
(или эквивалентный multiplatform compile target) и блокирует merge при провале.
Опционально — pre-commit warning для разработчика.

ЗАЧЕМ:
Pre-existing main breakage в commonMain не виден через текущий Android-only CI
(JVM stdlib маскирует JVM-only API). Incident 2026-06-24 (TASK-7 Phase 0 заблокирован
до chore-PR #29) — последнее напоминание. Подготовка к TASK-26 (iOS) — после iOS
сломанный commonMain парализует разработку.

SCOPE ВКЛЮЧАЕТ:
- GitHub Actions workflow update (добавить required check).
- Branch protection rules update (required status).
- (Опционально) git pre-commit hook с тем же compile target в warning-mode.
- Документация в docs/dev/build-gates.md (если файл существует) — что именно
  валидируется и почему.

SCOPE НЕ ВКЛЮЧАЕТ:
- Переписывание commonMain под более строгий contract (rule: только prevention,
  не cleanup).
- iOS / desktop / web таргеты в CI (отдельные tasks 26 / 29).
- Linting / detekt дополнительные правила.

DEPENDENCIES:
- Существующий GitHub Actions setup (если есть).
- Branch protection access (admin rights на repo).

ACCEPTANCE CRITERIA:
- `[hand]` Создаю PR, который специально ломает commonMain
  (например, добавляю `String.format` в файл `core/src/commonMain/.../FakeForCheck.kt`)
  — CI помечает PR красным, merge заблокирован.
- `[hand]` PR без поломки commonMain — CI зелёный, merge возможен.
- `[hand]` Время выполнения нового gate'а ≤ 60s (warm cache) / ≤ 3min (cold cache).
- `[hand]` Логи Gradle падения читаемы — указывают конкретный файл + строку.

LOCAL TEST PATH:
- act / nektos для локального прогона workflow'ов (опц.).
- Локальный прогон task'и без CI: `./gradlew :core:compileCommonMainKotlinMetadata`.

CONSTITUTION GATES:
- Article XVI Required Context Review: docs/dev/build-gates.md обновлён.
- Engineering rule 7 (Fitness functions): это пример fitness function на инфра-уровне.

EFFORT:
1-2 часа на single committer + ревью branch protection settings.
```

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 [hand] PR с заведомо broken commonMain (тестовый файл с JVM-only API) — CI помечает красным, merge заблокирован.
- [ ] #2 [hand] PR с валидным commonMain — CI зелёный, merge возможен.
- [ ] #3 [hand] Gate выполняется ≤ 60s (warm cache) / ≤ 3min (cold cache); не замедляет PR-cycle сильнее существующих checks.
- [ ] #4 [hand] Логи провала Gradle указывают конкретный файл + строку (читаемо без --info / --debug).
- [ ] #5 [hand] Branch protection rules в GitHub Settings обновлены: новый check помечен как required.
<!-- AC:END -->

<!-- SECTION:DESCRIPTION:END -->
