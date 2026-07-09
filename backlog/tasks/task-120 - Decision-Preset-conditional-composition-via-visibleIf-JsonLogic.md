---
id: TASK-120
title: 'Decision: Preset conditional composition via visibleIf + JsonLogic'
status: Discussion
assignee: []
created_date: '2026-07-09 10:55'
updated_date: '2026-07-09 13:24'
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

**Discussion, session 2 закрыта 2026-07-09 PM.**

Scope расширен: из «visibleIf/JsonLogic conditional inclusion» → **«Component/Preset/Profile foundational model»**. Rename задачи ждёт следующего touch'а. `visibleIf` сохраняется как поле в `presentation.wizard` секции ComponentDeclaration.

Финализировано на слух владельца: **Component / Preset / Profile / Provider / ProviderRegistry / Pool / Outcome**. Отвергнуто: Step/StepType/StepHandler (wizard-language, некорректно), Resource (Android R.* collision), Unit (Kotlin `Unit` reserved), Trait/Facet (weaker recognition).

Индустриальные параллели зафиксированы: IaC (Terraform/Puppet/Ansible reconcile-паттерн), ECS (Unity/Bevy Component-Archetype-Entity model).

Open questions OQ-1..OQ-11 переходят в session 3. Details — в `SECTION:DISCUSSION` ниже.

**Next work session** (при возврате на рабочее место): продолжить с OQ-6 (Component granularity, exact list) → OQ-11 → заполнить Decision block → status Draft → `/speckit.specify`.

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

**Applies to**: preset.json schemaVersion=2, WizardEngine, ConditionEvaluator port, JsonLogic adapter, plus foundational Component/Preset/Profile model per session 2 (see below).

**Trade-offs**: TBD.

**Exit ramp**: swap JsonLogic → CEL through ConditionEvaluator port (single adapter module change, per rule 2 ACL). Wire format context whitelist stays; expression syntax changes require preset migration.

---

### Session 2 mentor — foundational rescope (2026-07-09, PM)

**Context shift**: во время подготовки к `/speckit.specify` владелец переформулировал scope. TASK-120 (visibleIf/JsonLogic) оказался **частным случаем** более фундаментального вопроса — форма Pool / Preset / Profile / Component / Provider. Обсуждение вышло далеко за пределы conditional inclusion; фиксируем состояние здесь чтобы не потерять при переключении на рабочее место.

#### Владелец сформулировал модель

1. **Preset ≠ Wizard.** «В Settings нет step'ов, есть равноценные части приложения». Модель должна обслуживать 4 потребителей: Wizard, Settings, BootRouter (drift check), Admin remote (future). Один domain, разные UI над ним.
2. **Порядок зависимостей**: Pool → Preset → Profile → всё остальное (backup / pairing / sync / recovery / E2E / server). Без profile foundation нет смысла в downstream фичах.
3. **Pool = bassein равноценных настраиваемых частей** приложения. Preset = pick из pool с параметризацией. Profile = runtime state устройства (snapshot copy preset + user data + provider results).
4. **Preset — просто JSON**, шарящийся. Приходит из bundled seed (3 стартовых чтобы приложение не было пустым), из файла, из share intent, из URL, из QR. `PresetSource` port + адаптеры.
5. **Разделить declaration от execution**: Component (что настраивается) отдельно от Provider (как настраивается на конкретной платформе с конкретным вендором).
6. **Fallback chain provider**: vendor-specific → platform-generic → no-op.
7. **Snapshot copy** для profile (не live reference на preset). Preset после активации не читается; profile содержит клон + user choices + provider results.

#### Индустриальные параллели (найдены владельцем)

Модель дословно повторяет паттерны из нескольких доменов — это подтверждает корректность:

- **Infrastructure-as-Code** (Terraform Resource+Provider, Puppet Resource+Provider с confine/defaultfor, Ansible Task+Module, Chef Resource+Provider, Kubernetes reconcile-controller, systemd Unit).
  - Terraform `plan` = наш `check()`, `apply` = наш `apply()`.
  - Puppet provider selection (vendor→platform→no-op) — дословно наша fallback chain.
- **ECS (Entity-Component-System)** — Unity DOTS, Bevy, Unreal.
  - Component = равноценная характеристика объекта (тема, layout, permission, AppPresence).
  - Archetype = шаблон объекта (= наш **Preset**).
  - Entity = runtime instance (= наш **Profile**).
- **Home Assistant** — Entity/Platform/Integration, vendor-integrations с fallback. Меньше подходит как язык (терминология device-centric).

#### Terminology decision

Финализировано на слух владельца («У Profile есть **компоненты** — тема, layout, permissions»):

| Роль | Имя | Замечание |
|---|---|---|
| Sealed hierarchy того что настраивается | **Component** | В коде — `com.launcher.preset.Component` (не конфликтует с Compose/Hilt по import) |
| Параметризованный экземпляр в Pool | **ComponentDeclaration** | Data class с params |
| Реестр всех известных declarations | **Pool** | Registry в core/preset (source может быть code/JSON/hybrid — OQ) |
| Shareable JSON template | **Preset** | = archetype в ECS-терминах, оставлено owner-friendly имя |
| Runtime state на устройстве | **Profile** | = entity в ECS-терминах, оставлено owner-friendly имя |
| Per-platform реализация check + apply | **Provider** | Из Terraform/Puppet, replaces «Handler» (Handler — размытое) |
| DI lookup + fallback chain | **ProviderRegistry** | vendor → platform → no-op |
| Результат check/apply | **Outcome** | Applied \| NotApplied(reason) \| Indeterminate (Article VII §15) |

Отвергнуто:
- **Step / StepType / StepHandler** — wizard-language, некорректно (в Settings нет step'ов).
- **Resource** — правильная семантика (Terraform), но collision с Android `R.*` резources.
- **Unit** — семантически хорошо, но `Unit` — reserved type в Kotlin.
- **Trait** — нейтрально, без коллизий, но не активирует ECS-паттерн у читателя.
- **Facet** — точно, но слабый reference material.
- **Handler** для Provider — размытое, много context'ов (event/error/message handler).

#### Presentation hints — где живут (open question, склон к D)

Каждая ComponentDeclaration несёт metadata для каждого потребителя отдельно от domain-logic:
```
presentation: {
  wizard: { include: true, order: 3, title, description } | null
  settings: { include: true, category, sensitive } | null
  boot: { critical: true, silentDegrade: false } | null
  admin: { remotelyEditable: false } | null
}
```
Отсутствие секции = «этот потребитель не показывает». `visibleIf` (исходный scope TASK-120) — **одно из полей внутри presentation.wizard**, а не самостоятельный concern.

**OQ presentation**: A (в ComponentType hardcoded) / B (в pool declaration) / C (в preset override) / **D (composition A default + C override)** — склон к D.

#### Component granularity — open question, склон к средней

- Мелкие (~30-50): `Permission.ReadContacts`, `Permission.Camera`, `HomeRole`, `DefaultDialer`, `AppPresence`, `LayoutSlot`, `ThemeColor`, `FontScale`, `SignInGoogle`.
- **Средние (~10-15)** — склон: `Permission(what)`, `SystemClaim(kind)` где `kind = HomeRole|DefaultDialer|AccessibilityService`, `AppPresence(pkg, required)`, `LayoutBinding(...)`, `Theme(name)`, `SignIn(provider)`, `FontScale(min)`.
- Крупные (~5-7): `Permission(...)`, `SystemClaim(...)`, `AppOperation(...)`, `UISetting(k, v)`, `Auth(...)`.

Fitness rule (кандидат): «Provider не может делать `when` по параметру внутри — параметризация в data, dispatch по type».

#### Vendor dispatch — open question, склон к A (explicit enum)

- **A (склон)**: `enum Vendor { Xiaomi, Samsung, Huawei, GoogleTV, GenericAndroid, iOS }`. Provider регистрируется с явным `@Provider(Vendor.Xiaomi)`. Fallback: Xiaomi → GenericAndroid → NoOp.
- B: Predicate-based (`applies(deviceContext): Boolean`). Более гибко, harder to reason.
- C: Capability-based (provider декларирует что умеет). Абстрактнее, ближе к «доступные колбеки» идее владельца.

#### Preset ↔ Pool composition — open question, склон к A2

- A1: pure reference `{ poolRef: "app-install-jitsi" }`. Pool должен предусмотреть все параметризации.
- **A2 (склон)**: reference + override `{ poolRef: "app-install", params: {...} }`. Pool = шаблон, preset дописывает params.
- A3: reference + inline exception (позволить preset нести vendor-specific declaration).

#### Directory model

Preset **не сидит в `assets/presets/`** как dogma. Это просто JSON.
```
core/preset/               ← Component, ComponentDeclaration, Pool, Preset, Profile, ports, Outcome
core/preset/provider/      ← Provider port, ProviderRegistry port
app/androidMain/provider/  ← ContactPermissionProvider, XiaomiHomeRoleProvider, GenericHomeRoleProvider, ...
app/iosMain/provider/      ← iOS providers
assets/bundled-presets/    ← ТОЛЬКО 3 seed JSON'а (simple-launcher, launcher, workspace) чтобы app не был пустым при первом install
```
Все остальные источники (file / share / network / QR) — `PresetSource` adapters. `BundledPresetSource` — одна из реализаций.

#### Consumers reconcile-loop (ключевая архитектурная мысль)

```
Wizard      → показать Component'ы где check() = NotApplied
Settings    → показать ВСЕ Component'ы active preset, дать apply() с новыми params
BootRouter  → periodic check на critical Component'ы; drift → banner
Admin remote → присылает новый Preset; локальный reconcile-loop приводит Profile к desired state
```

Один reconcile-loop, четыре UI над ним.

#### Что решено про TASK-120 scope

- **TASK-120 расширяется** из «Preset conditional composition via visibleIf + JsonLogic» до **«Component/Preset/Profile foundational model»**. Rename при следующем touch.
- `visibleIf` + JsonLogic **сохраняется** как часть Decision — оно становится полем в `presentation.wizard` секции ComponentDeclaration, не отдельный concern.
- Alternative — создать TASK-121 «foundational model» и закрыть TASK-120 как superseded. Owner склон **расширить TASK-120**, не разбивать (session 2 conclusion).

#### Что решено про TASK-65 backward-compat

- TASK-65 **не откатывается**. Многое сохранится: `ProfileStore`, `CopyOnActivateStrategy`, `PoolSource` port.
- Terminology rename на next touch: `ConfigKind` → `Component`, `ConfigSpec` → `ComponentDeclaration`, `CheckSpec/ApplySpec` → провайдерская сторона.
- Handler layer добавляется отдельным port (`Provider`/`ProviderRegistry`).

#### Open questions for session 3 (next work session)

1. **OQ-6 (Component granularity)** — финализировать sealed hierarchy границы. Middle path ~10-15 склон, но нужно перечислить точный список.
2. **OQ-7 (Provider dispatch)** — финализировать A vs C. A склон, но owner мог упустить downside.
3. **OQ-8 (Presentation hints location)** — D склон (default + override), но нужна exact schema.
4. **OQ-9 (Pool source model)** — Kotlin object / JSON asset / hybrid. TASK-73 (per-vendor variants) намекает на hybrid.
5. **OQ-10 (Preset ↔ Pool composition)** — A2 склон, финализировать schema.
6. **OQ-11 (Profile snapshot migration)** — как мигрируем profile v1→v2 когда preset schema обновляется. Rule 5 wire-format-versioning applies.
7. **OQ-1..OQ-5** из session 1 (Google region-block, per-preset cloud state, whitelist safety, context deprecation, preview tool) — остаются open.

#### Готовность к next steps

- **Session 3** (следующая работа): закрыть OQ-6..OQ-11 + OQ-1..OQ-5 → заполнить Decision block → status Discussion → Draft.
- **Session 4**: `/speckit.specify` — переработка `core/preset/` (частично из TASK-65) + новые ports Provider/ProviderRegistry/PresetSource/AssetResolver + wire format preset.json v2.
- **Downstream tasks** (draft-1 Wizard refactoring, TASK-71, TASK-69, TASK-68, TASK-19) — уведомлены о `dependencies: [TASK-120]` уже 2026-07-09.

<!-- SECTION:DISCUSSION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 Decision block заполнен (Choice / Rationale / Applies to / Trade-offs / Exit ramp) на English перед status → Draft
- [ ] #2 Open questions OQ-1..OQ-11 (OQ-1..OQ-5 из session 1 + OQ-6..OQ-11 из session 2) либо resolved в Decision, либо явно deferred в spec-time
- [x] #3 Downstream tasks (draft-1 Wizard manifest-driven refactoring, TASK-71 hidden steps, TASK-69 Settings as Profile View, TASK-68 workspace preset, TASK-19 Adaptive UX Presets) добавлены `dependencies: [TASK-120]` 2026-07-09
- [x] #4 Session 2 mentor discussion зафиксирован в SECTION:DISCUSSION (foundational rescope: Component/Preset/Profile/Provider terminology + industrial parallels + OQ-6..OQ-11)
- [ ] #5 Task rename при следующем touch: title → "Decision: Component/Preset/Profile foundational model" (расширенный scope, visibleIf становится частью presentation.wizard)
<!-- AC:END -->

## Definition of Done

Status → Draft, когда Decision block filled + AC #1-3 зелёные. Implementation отслеживается через downstream feature-tasks, не через этот decision-task.
