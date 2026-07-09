---
id: TASK-120
title: 'Decision: Preset conditional composition via visibleIf + JsonLogic'
status: Discussion
assignee: []
created_date: '2026-07-09 10:55'
labels:
  - phase-2
  - foundation
  - preset
  - wizard
  - decision
  - one-way-door
milestone: m-1
dependencies:
  - TASK-65
decision-supersedes: []
superseded-by: null
priority: high
ordinal: 120000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Preset — это JSON-декларация «что должно быть настроено на устройстве» (какие плитки, какие permissions, войти ли в Google-аккаунт, etc.). Сейчас все шаги wizard'а — плоский список, всегда все показываются. Проблема: **Sign-In** (войти в Google) и **Recovery Setup** (задать пароль восстановления) сейчас захардкожены в `FirstLaunchActivity` (TASK-119 tech-debt) и **не выражены** в preset.

Владелец хочет: положить Sign-In и Recovery в preset как обычные шаги, но с условиями «показывать только если preset требует cloud». То есть — **условное включение шагов** (conditional inclusion).

Три варианта решения:
1. **Axis-based** — фиксированный набор типизированных полей (`cloudRequirement`, `cloudBundle`), runtime собирает шаги из них.
2. **Full DSL** — встроенный expression language (CEL / Rego), preset автор пишет свои `if/then/else`.
3. **Гибрид** (рекомендация research): axis для основных параметров + **`visibleIf`** (JsonLogic expression) на каждом step'е для тонких conditional cases.

Решение принципиальное (one-way door) — оно определяет форму preset-файлов на много лет вперёд.

## Зачем

- Убрать hardcoded Sign-In flow из `FirstLaunchActivity` (TASK-119 палатив, draft-1 techdebt).
- Дать разным preset'ам разное отношение к cloud (workspace = required, simple-launcher = opt-in).
- Открыть путь для будущих conditional шагов (recovery только если backup включён; adaptive UX preset показывает разные шаги для tremor/vision).
- Сохранить principle rule 9 (shareability) — все conditional logic в декларативном JSON, без code changes.

## Что входит в решение (для AI-агента)

- **Wire format**: `preset.json` schemaVersion=1→2, добавляется опциональное поле `visibleIf: JsonLogicExpression?` на каждом элементе `configs[]`.
- **Порт**: `ConditionEvaluator` в `core/preset/` domain (KMP common). Метод `evaluate(rule: JsonElement, ctx: EvaluationContext): Boolean`.
- **Adapter**: `JsonLogicConditionEvaluator` (через `allegro/json-logic-kmp`, MIT, KMP, production в Allegro).
- **Whitelist context** (wire format sam po sobie, schemaVersion=1): `preset.*` (значения самого preset'а), `device.hasGms`, `device.locale`, `signInResult.completed` (если Auth шаг был), `signInResult.failed`.
- **Engine integration**: `WizardEngine.computePending()` перед возвратом step'а вызывает `ConditionEvaluator.evaluate(step.visibleIf, ctx)` — если false, step скипается.
- **Migration writer**: `preset.json` v1→v2 (v1 без `visibleIf` → v2 с опциональным полем, дефолт `null` = всегда true).
- **Roundtrip test + backward-compat test** — rule 5.
- **JSON Schema validator** на preset load (draft 2020-12) — belt-and-suspenders с JsonLogic parse-time check.

## Что НЕ входит (deferred additions, каждое — additive не rewrite)

- **`onSuccess` / `onFailure` / `maxAttempts`** — retry/branch logic. Дефер: делать через callback в runtime, не в JSON. Добавляется как новые опциональные поля позже.
- **Axis-поля (`cloudRequirement`, `cloudBundle`, `reminderPolicy`)** — top-level enum knobs preset'а. Дефер: три стартовых preset'а справятся через `visibleIf` + hardcoded runtime дефолты. Axis добавляются когда 4-й preset реально попросит.
- **Sub-flows / вложенность (`ConfigKind.SubFlow`)** — вложенные wizard chains. Дефер: пока плоский список работает.
- **JSON Pointer (RFC 6901) cross-references** — step-2 ссылается на выбор step-1. Дефер: не нужно для стартовых 3 preset'ов.
- **JSON Patch (RFC 6902) incremental preset updates** — miграция preset v1→v2 не патчем а monolithic transformer. Дефер: работает для 1 breaking change; когда пойдёт v2→v3→v4 подряд — вернёмся.
- **CEL как альтернатива JsonLogic** — swap через adapter, не рерайт callsite'ов.

## Состояние

**Discussion.** Создана 2026-07-09 из mentor-сессии в контексте TASK-65 закрытия. Research собран (два agent report'а: наш код + индустрия). Модель предложена, требует зафиксировать Decision block перед началом implementation.

<!-- SECTION:DESCRIPTION:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Research context

**Existing code (Explore agent, 2026-07-09):**
- Foundation готов 80%: `CheckSpec`/`ApplySpec` sealed hierarchy ([core/src/commonMain/kotlin/com/launcher/api/wizard/data/CheckSpec.kt](../../core/src/commonMain/kotlin/com/launcher/api/wizard/data/CheckSpec.kt), 6 variants), `Pool` architecture ([core/src/commonMain/kotlin/com/launcher/api/pools/PoolSource.kt](../../core/src/commonMain/kotlin/com/launcher/api/pools/PoolSource.kt)), `WizardEngine.computePending()` ([core/src/commonMain/kotlin/com/launcher/ui/wizard/WizardEngineImpl.kt](../../core/src/commonMain/kotlin/com/launcher/ui/wizard/WizardEngineImpl.kt#L75)).
- Что отсутствует: conditional branching, step dependencies, `cloudRequirement` field, integration FirstLaunchActivity → WizardEngine (draft-1 tech-debt).
- Constitution Article VII §16 запрещает `StepType.Custom` — всё через generic types + declarative specs. Наша модель этому соответствует.

**Industry review (general-purpose agent, 2026-07-09):**
- Paywall vendors (Superwall, Adapty, GrowthBook, LaunchDarkly) converged на `axis + narrow conditional expression`. Никто не даёт preset авторам full DSL.
- **JsonLogic** ([allegro/json-logic-kmp](https://github.com/allegro/json-logic-kmp)) — non-Turing-complete by construction (нет loops/recursion → infinite loop физически невозможен), KMP-ready, MIT, production Allegro, ~50KB. Кастомные operators = natural sandbox.
- **CEL** — золотой стандарт, но overkill (protobuf runtime, нет first-class KMP). Держим как exit ramp через swap adapter'а.
- **Rego (OPA)** — нарушение rule 4 MVA at max volume (WASM runtime в APK для wizard branching).
- **JSON Schema `if/then/else`** — про validation, не runtime dispatch. Используем по прямому назначению: валидировать preset при загрузке.
- **SurveyJS `visibleIf` pattern** — expression как поле на step'е, не отдельный flow-control language. 10 лет production. Copy this pattern.

### Ключевые trade-offs

- **`visibleIf` покрывает ~80% случаев** — включать/не включать шаг. Не покрывает: cycles/retry, sub-flows, cross-step references, ветвление flow (recovery blob найден → Entry, не найден → Setup).
- Владелец решил (2026-07-09): retry — через callback в runtime, не в JSON. Ветвление recovery blob — hardcoded runtime (одна ветка, не вариативно). Sub-flows — additive позже.
- Это делает `visibleIf` минимальным достаточным механизмом.

### Open questions (переносим на моменты spec/implementation)

- **OQ-1**: Google region-block fallback. `cloudRequirement="required"` + Google blocked in RU → fail-open (degrade до banner) или fail-closed (blocking screen)? Или fallback provider (yandex/vk)? Пока склоняемся к fail-open, но зафиксировать.
- **OQ-2**: Один Google UID per device (техническая невозможность двух аккаунтов) но разное поведение per preset — preset A знает «backup включён», preset B знает «backup выключен» с тем же UID. Как хранить: `Map<PresetRef, PresetCloudState>` в ProfileStore? Уточнить при spec.
- **OQ-3**: Preset author safety — whitelist expression context должен быть **строго ограничен** enum'ом. Если preset автор напишет `{"var":"secrets.something"}` — должно отклоняться build-time. Как реализовать: JSON Schema validator + JsonLogic operations-api custom operator list.
- **OQ-4**: Deprecation policy для полей контекста. Что если удаляем `signInResult.completed` из context v1→v2? Все preset'ы с этой переменной ломаются. Нужна migration policy на уровне context wire format.
- **OQ-5**: `visibleIf` тестирование — как preset автор проверяет что его условие работает? Нужен ли preview tool (post-MVP)?

### Decision (English) — TO BE FILLED before status → Draft

**Choice**: TBD after mentor session concludes and owner approves.

**Rationale**: TBD.

**Applies to**: preset.json schemaVersion=2, WizardEngine, ConditionEvaluator port, JsonLogic adapter.

**Trade-offs**: TBD.

**Exit ramp**: swap JsonLogic → CEL through ConditionEvaluator port (single adapter module change, per rule 2 ACL). Wire format context whitelist stays; expression syntax changes require preset migration.

<!-- SECTION:DISCUSSION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 Decision block заполнен (Choice / Rationale / Applies to / Trade-offs / Exit ramp) на English перед status → Draft
- [ ] #2 Open questions OQ-1..OQ-5 либо resolved в Decision, либо явно deferred в spec-time
- [x] #3 Downstream tasks (draft-1 Wizard manifest-driven refactoring, TASK-71 hidden steps, TASK-69 Settings as Profile View, TASK-68 workspace preset, TASK-19 Adaptive UX Presets) добавлены `dependencies: [TASK-120]` 2026-07-09
<!-- AC:END -->

## Definition of Done

Status → Draft, когда Decision block filled + AC #1-3 зелёные. Implementation отслеживается через downstream feature-tasks, не через этот decision-task.
