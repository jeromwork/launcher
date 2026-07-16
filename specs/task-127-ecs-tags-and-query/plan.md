# Implementation Plan: ECS Tags Foundation + HomeScreen Query Rewire

**Branch**: `task-127-home-config-load-fix` | **Date**: 2026-07-16 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/task-127-ecs-tags-and-query/spec.md`
**Backlog task**: [task-127](../../backlog/tasks/task-127%20-%20HomeActivity-config-load-failure-post-wizard-TASK-126-regression.md)

---

## Summary

Extend TASK-120 domain model with tagged-component-model patterns (add `Component.tags: Set<Tag>` + `Profile.query`), replace HomeScreen's `ConfigBackedFlowRepository` binding with a new `ProfileBackedFlowRepository` that reads `Profile` directly through the query API. Fixes the TASK-52 regression introduced by TASK-126 (wizard writes `Profile`, HomeScreen reads `ConfigDocument` — architectural gap → Error UI). The regression fires in **`HomeComponent.launchLoadFlows()` → `loadFlows()`** (one-shot, 3s timeout → `Error`), so the new adapter implements **all four `FlowRepository` methods** — `loadFlows` awaits the first non-null Profile; `observeFlows` is the hot path. **Никакого migration writer**: MVP не релизнут; `tags` — additive optional field, поэтому **`schemaVersion` остаётся 2** (значение из shipped кода `Profile.CURRENT_SCHEMA_VERSION = 2` и immutable TASK-120 Decision; раннее «reset to 1» отменено 2026-07-16), tags-defaults живут только в constructor-defaults на Component subtypes (per Clarification Q6, rule 4 MVA). `ConfigDocument` code stays for future admin push scenarios but is no longer bound to `FlowRepository`.

---

## Technical Context

**Language/Version**: Kotlin 2.0 (Multiplatform: `commonMain` + `androidMain`).
**Primary Dependencies**: kotlinx.serialization (Profile JSON), kotlinx.coroutines (Flow API on `ProfileStore.observe()`). No new deps.
**Storage**: `ProfileStore` = local file-based JSON on device (existing from TASK-120). Pool = bundled asset (`pool.json` in APK).
**Testing**: JVM unit tests (`./gradlew :core:test`), Robolectric instrumented (`:core:connectedAndroidTest`), manual on emulator + Xiaomi Redmi Note 11 physical device.
**Target Platform**: Android (minSdk 24, target 34). Zero Android imports in Query API / Tag / `ProfileBackedFlowRepository` per rule 1 (domain isolation).
**Project Type**: Mobile app (Android launcher). This spec touches `core` KMP module + `app` Android DI wiring.
**Performance Goals**: `profile.query(...)` under 1 ms at MVP scale (~20 Components in Profile). Linear scan; indexing deferred (see research.md).
**Constraints**: `schemaVersion: 2` — unchanged (additive `tags` field, no bump; matches `Profile.CURRENT_SCHEMA_VERSION = 2` + TASK-120 Decision). Никакого migration writer per Clarification Q6 (MVP не релизнут — нет потребителя миграции per rule 4 MVA). Migration writer добавляется post-release при первом breaking change (v2 → v3). Zero Android imports in domain layer.
**Scale/Scope**: MVP Profile ~10-20 Components. HomeScreen renders 3-8 tiles + 1 Toolbar. Query invoked on every Profile emission (rare — user edits) + on cold start.

---

## Architecture

### Module map

```
core/src/commonMain/kotlin/com/launcher/
├── preset/
│   ├── model/
│   │   ├── Enums.kt                     [MODIFIED] add @Serializable Tag enum
│   │   ├── Component.kt                 [MODIFIED] add abstract tags: Set<Tag>; 8 subtypes
│   │   │                                  (incl. Language, StatusBarPolicy); LauncherRole +
│   │   │                                  StatusBarPolicy: object → data class (wire-compatible)
│   │   ├── Preset.kt                    [MODIFIED] doc-comment ref preset-model.md
│   │   └── Profile.kt                   [UNCHANGED] (query API lives in extensions, not here)
│   └── query/
│       └── ProfileQuery.kt              [NEW] extension functions on Profile
├── api/
│   └── FlowRepository.kt                [UNCHANGED] existing port (real location — NOT adapters/flow/)
└── adapters/
    ├── config/
    │   └── ConfigBackedFlowRepository.kt [MODIFIED] add TODO(config-deprecation) comment (real location)
    └── flow/
        └── ProfileBackedFlowRepository.kt [NEW] reads ProfileStore, projects via query

core/src/androidMockBackend/kotlin/com/launcher/di/
└── BackendInit.kt                       [MODIFIED] rebind single<FlowRepository> (~line 165)
core/src/androidRealBackend/kotlin/com/launcher/di/
└── BackendInit.kt                       [MODIFIED] rebind single<FlowRepository> (~line 275)

docs/
├── architecture/
│   └── preset-model.md                  [NEW] two-dimensions doc (already merged)
└── dev/
    └── server-roadmap.md                [MODIFIED] SRV-CONFIG-DEPRECATION entry (already merged)
```

> Note (audit 2026-07-16): earlier revision listed fictional paths — `core/adapters/flow/FlowRepository.kt`, `preset/serialization/ProfileSerializer.kt`, `app/.../di/{Mock,Real}BackendModule.kt`. None exist. There is no `ProfileSerializer` class — kotlinx Json config lives inline in `DataStoreProfileStore` (app module) and must be mirrored exactly by contract tests (see contract § Serializer configuration).

### Port-adapter shape

```
                     ┌───────────────────────────────────┐
HomeComponent ──────►│  FlowRepository (port, UNCHANGED) │
                     │  loadFlows(): List<FlowDescriptor>│  ← one-shot path: here the
                     │  observeFlows(): Flow<…>          │    TASK-52 regression fires
                     │  availableTemplates(presetId)     │
                     │  addFlow(templateId)              │
                     └───────────────────────────────────┘
                                  ▲
                                  │ implements
                     ┌────────────┴──────────────┐
                     │ ProfileBackedFlowRepository│
                     │  (NEW — bound in DI)       │
                     └────────────┬──────────────┘
                                  │ reads
                     ┌────────────▼──────────────┐
                     │  ProfileStore (existing)   │
                     │  observe(): Flow<Profile?> │
                     └────────────────────────────┘

ConfigBackedFlowRepository stays in codebase (unbound from FlowRepository).
Marked // TODO(config-deprecation): SRV-CONFIG-DEPRECATION for future removal.
```

### Data flow — happy path (SEQ-1)

```
WizardHostActivity
  ↓ onConfirmLastStep
WizardViewModel
  ↓ engine.run(RunMode.Wizard)
ReconcileEngine
  ↓ profileStore.save(finalProfile)   ← Profile becomes source of truth
ProfileStore (disk write)
  ↓ startActivity(HomeActivity)
HomeActivity → HomeComponent
  ↓ launchLoadFlows(): loadFlows() [3s timeout] + observeFlows() [hot]
ProfileBackedFlowRepository
  ↓ profileStore.observe().filterNotNull()  (loadFlows: .first(); observe: .map)
  ↓ profile.homeScreenTiles()          ← query API (excludes Failed/Skipped)
List<FlowDescriptor> / Flow<List<FlowDescriptor>>
  ↓
HomeLoadingState.Ready(flows)
```

**Behaviour of the remaining port methods** (parity, no scope growth): `availableTemplates(presetId)` keeps the existing static template-catalogue semantics (as in `ConfigBackedFlowRepository`); `addFlow(templateId)` throws `error(...)` exactly like the only existing implementation does today (`ConfigBackedFlowRepository.addFlow`, line ~95) — Profile-based addFlow is a separate future task (`TODO(profile-add-flow)`).

**Persistently absent Profile** (wizard skipped / crashed pre-save): `loadFlows()` suspends on `.first()` → HomeComponent's existing 3s timeout → `Error` + Retry (TASK-52 UX, unchanged). Deliberate: no eternal-Loading gap.

### Rule 1 verification (domain isolation)

All new files in `core/preset/model/`, `core/preset/query/`, `core/preset/serialization/` are pure Kotlin — zero `import android.*`. `ProfileBackedFlowRepository` in `core/adapters/flow/` also zero Android imports (`FlowRepository` port is domain-level; `ProfileStore` is a port too, not a vendor SDK). Verified by `checklist-domain-isolation` on plan-level review + build-time fitness function (source-set placement).

---

## Data model

See [`data-model.md`](data-model.md).

Key types:
- `Tag` enum (10 values including `Tag.Toolbar` for query-based Toolbar lookup, additive-only per rule 5 — with the honest unknown-value caveat in the contract).
- `Component.tags: Set<Tag>` with per-subtype defaults (single source of truth: constructors). 8 real subtypes incl. `Language` + `StatusBarPolicy`; `LauncherRole`/`StatusBarPolicy` object → data class (wire-compatible).
- `Profile` query API surface (extension functions). `homeScreenTiles()` excludes `ComponentStatus.Failed/Skipped` (render gating — see data-model.md).
- **No migration writer** — per Clarification Q6 (MVP не релизнут; `tags` additive → `schemaVersion` остаётся 2).

---

## Wire formats

- [`contracts/profile-v2.md`](contracts/profile-v2.md) — Profile serialized JSON `schemaVersion: 2` (значение из shipped кода; `tags` — additive, без bump'а). Documents real Profile/ProfileComponent shape, `tags` field per Component, constructor-defaults для отсутствующего поля, **честный** forward-compat (unknown key → ignored; unknown Tag value / unknown Component type → fail-loud `SerializationException`, lenient serializer required before cross-device artifacts ship — Risk R-8). **Никакой migration writer**: MVP не релизнут, отложено до post-release breaking change.

Not touched:
- `pool.json` — bundled build-time artefact, additive optional `tags` field on `ComponentDeclaration`; no schemaVersion bump (per Clarification Q2).
- `ConfigDocument` — unchanged (stable for admin push scenarios).

---

## Dependency impact

**No new gradle dependencies.** Feature is pure Kotlin extensions on existing types + one new adapter class (no migration writer per Q6). Reuses existing kotlinx.serialization + kotlinx.coroutines Flow already in `core` module.

Per Article XIII (Dependency Restraint): the "add nothing" outcome is preferred; documented explicitly.

---

## Test strategy

Per CLAUDE.md rules §6 (mock-first) + §7 (fitness functions).

### Contract tests (`core/src/commonTest/kotlin/com/launcher/preset/wire/` — existing wire-test package)

- **`ProfileSchemaV2RoundtripTest`** (naming per existing `PoolSchemaV2RoundtripTest` / `PresetSchemaV2RoundtripTest` pattern): roundtrip Profile v2 → JSON → Profile v2. Json settings MUST mirror `DataStoreProfileStore` exactly (`classDiscriminator="type"`, `ignoreUnknownKeys=true`, `encodeDefaults=true`). Fixtures: `core/src/commonTest/resources/fixtures/profile-wire-format/profile-v2-sample.json` + `profile-v2-no-tags.json`.
- **Fail-loud pins** (per contract § Forward compat): unknown Tag value → `SerializationException`; unknown Component `type` → `SerializationException`. Keeps the lenient-serializer deferral a documented decision.
- **REMOVED**: `ProfileMigrationV2toV3RoundtripTest` + `ProfileMigrationV2toV3BackwardCompatTest` — нет migration writer, нечего тестировать. Constructor-defaults покрыты через `ComponentTagsFitnessTest` (см. Fitness functions).

### Unit tests

- **`ProfileQueryTest`**: `byTag` (single), `byAllTags` (AND), `byAnyTag` (OR), `byNotTag`, `homeScreenTiles`, `toolbar`, empty result, tag-not-present, empty Profile, **render gating** (`Failed`/`Skipped` excluded from `homeScreenTiles`, `Pending` included).
- **`ProfileBackedFlowRepositoryTest`** (JVM, uses existing `core/src/commonTest/kotlin/com/launcher/preset/fakes/FakeProfileStore.kt`):
  - `loadFlows()` — Profile already saved → returns mapped `homeScreenTiles()` immediately (the regression path).
  - `loadFlows()` — store stays null → suspends (caller timeout responsibility; test with runTest + advanceTime).
  - `observeFlows()` — non-null Profile → emits list; null emission upstream → no downstream emission (`filterNotNull`).
  - Profile update sequence (two saves) → repository emits two lists.
  - `addFlow` → throws (parity pin with `ConfigBackedFlowRepository`).
- **`ComponentTagsFitnessTest`**: reflection walk over `Component` sealed subclasses; instantiate each (dummy values for required params like `AppTile.packageName`); assert `tags` default is non-empty. Prevents accidental `emptySet()` regressions.

### Integration tests (JVM commonTest — real location `core/src/commonTest/kotlin/com/launcher/ui/navigation/HomeComponentLoadingStateTest.kt`, NOT `:core:androidTest`)

- **`HomeComponentLoadingStateTest`** (existing test class from TASK-52) — add new scenario:
  - `postManifestWizardReconcile_profileSeeded_homeReady`: seed `ProfileStore` with one `AppTile(Settings)`, wire `ProfileBackedFlowRepository`, verify `HomeComponent` transitions `Loading → Ready` with non-empty flows.
  - Existing config-based scenarios stay green (`ConfigBackedFlowRepository` not removed).

### Fitness functions

- **Component-tags non-empty check**: `ComponentTagsFitnessTest` (see above).
- **Source-set placement**: JVM tests import only `commonMain` sources for `Tag`, `Component.tags`, `Profile.query`, `ProfileBackedFlowRepository`. Compilation fails if Android import sneaks in.
- **Query performance**: `ProfileQueryBenchmarkTest` — micro-benchmark on 20-Component fixture, assert p95 < 1 ms (SC-008).

### Manual verification

- Emulator smoke (pixel_5_api_34 AVD): fresh install → wizard → HomeScreen shows tiles.
- Physical Xiaomi Redmi Note 11 (adb id `17f33878`): same flow — SC-001 and SC-002.

---

## Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-----------|--------|------------|
| R-1 | Component subtype declared without `tags` default (empty set) → invisible to queries | Medium | High | `ComponentTagsFitnessTest` (reflection walk) catches this at build time. **Single source of truth** — constructor-defaults, no separate migration mapping to keep in sync. |
| R-2 | ~~Migration writer race~~ | — | — | **REMOVED**: нет migration writer. `schemaVersion: 2` unchanged (`tags` additive). |
| R-3 | ~~`ProfileStore.observe()` semantic differs from assumption~~ **VERIFIED 2026-07-16**: `ProfileStore.observe(): Flow<Profile?>` confirmed ([ProfileStore.kt:7](../../core/src/commonMain/kotlin/com/launcher/preset/port/ProfileStore.kt#L7)); DataStore emits `null` while key absent. | — | — | Closed. `filterNotNull` + `loadFlows().first()` design stands. |
| R-4 | Query linear scan performance degrades if Profile grows past MVP scale (100+ Components) | Low (MVP) | Low (MVP) | Documented exit ramp in research.md § "Query indexing" — indexed lookup as additive change; no wire-format break required. |
| R-5 | Xiaomi MIUI kills HomeActivity between wizard finish and HomeComponent init | Low | High | `HomeComponent` reads `Profile` from disk on init (not from in-memory hand-off) — surviving process death by design (state-management pattern from TASK-52). |
| R-6 | `Toolbar` Component in Profile but `Profile.toolbar()` returns null (e.g., someone sets `tags = emptySet()`) | Low | Low | Covered by `ComponentTagsFitnessTest`; runtime null tolerated (Toolbar is optional UI element). |
| R-7 | Post-release breaking change полей Component без migration writer → users lose data on upgrade | High | High | **Deferred to post-release**: pre-release (сейчас) — `schemaVersion: 2` unchanged, каждое breaking change = сброс dev `ProfileStore`. Первый post-release breaking change = обязательный migration writer (`ProfileMigrationV2toV3`) + bump. Owner aware через Clarification Q6. |
| R-8 | Unknown Tag value / Component type crashes older reader (`SerializationException` — kotlinx enum collections have no per-element leniency) → cross-device artifact exchange breaks rule 5 backward-read guarantee | Low (pre-release, same-device) | High (post-sharing) | Contract states fail-loud honestly + contract tests pin it. **Hard trigger**: lenient `Set<Tag>` serializer + unknown-`type` skip policy MUST ship before admin push (spec-009) or preset import/share (rule 9) — recorded in contract § Forward compat. |

---

## Required Context Review

- [CLAUDE.md](../../CLAUDE.md) — rules §1 (domain isolation), §4 (MVA), §5 (wire-format versioning), §9 (shareability-readiness).
- [ADR-011](../../docs/adr/ADR-011-ai-owner-collaboration-conventions.md) — sequences + checklist-in-chat convention (used in this spec).
- [ADR-012](../../docs/adr/ADR-012-tagged-component-model-vs-canonical-ecs.md) — vocabulary + design deviation from canonical ECS (Bevy/Flecs/Unity DOTS). Latent one-way door: single-component-per-entity model blocks future multi-component composition.
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md) — Article XI (meta-minimization), Article XVI (Constitution Check), Article XVII (deviations).
- [docs/architecture/preset-model.md](../../docs/architecture/preset-model.md) — two-dimensions model (lifecycle vs semantic). Just merged into this branch.
- [docs/dev/server-roadmap.md § SRV-CONFIG-DEPRECATION](../../docs/dev/server-roadmap.md) — future ConfigDocument removal path.
- [backlog/tasks/task-120](../../backlog/tasks/task-120%20-%20Decision-Component-Preset-Profile-foundational-model.md) — Decision block on which this plan builds additively.
- [specs/task-49-cloud-feature-inventory-offline-first/](../../specs/task-49-cloud-feature-inventory-offline-first/) — reference for backend substitution style (not directly touched here, but architectural precedent).

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Run by `procedure-constitution-check` 2026-07-16 against this plan.

| Gate | Status | Justification |
|------|--------|---------------|
| G-1 Architecture | **PASS** | Extension methods + one new adapter class in existing `core/preset/*` and `core/adapters/flow/`. No new gradle module — Article V §3 criteria none apply (ownership / build-isolation / independent enable / stable API / testability all N/A for extension functions + one adapter). Port + implementation shape preserved. |
| G-2 Core / System Integration | **N/A** | No new system events, no `BroadcastReceiver`, no lifecycle callbacks. Feature is data-model + adapter change. |
| G-3 Configuration | **PASS** | `schemaVersion: 2` — matches shipped code + TASK-120 Decision; `tags` additive, no bump (rule 5). Migration writer **отсутствует** per Clarification Q6 (MVP не релизнут, нет потребителя миграции; rule 4 MVA). Roundtrip test scoped (`ProfileSchemaV2RoundtripTest`) + fail-loud pins (unknown Tag/type, R-8). Constructor-defaults на Component subtypes покрывают отсутствие `tags` в JSON — единственный источник истины, R-1 risk устранён. Pool.json addition is additive per Clarification Q2 (via embedded `component` object — no `ComponentDeclaration` change). Post-release breaking change plan: R-7 в Risks. |
| G-4 Required Context Review | **PASS** | Links present: CLAUDE.md (rules 1, 4, 5, 9), ADR-011, constitution.md (XI, XVI, XVII), preset-model.md, server-roadmap.md, TASK-120 Decision, task-49 as precedent. No permissions change (no compliance doc needed). |
| G-5 Accessibility | **PASS** | US-3 (wizard localization) → readable strings for senior users; SC-002 verifies. No new UI surfaces below 56dp — only existing HomeScreen (senior-safe per TASK-52) + wizard (senior-safe per TASK-126). `FontSize` Component carries `Tag.Accessibility` — fitness function verifies. |
| G-6 Battery / Performance | **PASS** | Event-driven (`ProfileStore.observe()` emission on user edit); no polling. Никакой migration cost (нет migration writer). Perf target NFR-003 (< 1 ms) + SC-008 benchmark. Zero new deps. |
| G-7 Testing | **PASS** | Contract (roundtrip v2, missing-tags defaults, fail-loud pins — no migration tests per Q6); unit (Query API + render gating, Profile-backed repo incl. `loadFlows`, fitness); integration (`HomeComponentLoadingStateTest` extended, JVM commonTest); fitness function (`ComponentTagsFitnessTest` reflection walk); micro-benchmark. Existing `FakeProfileStore` for adapter testing. |
| G-8 Simplicity | **PASS** | `ProfileQueryService` explicitly rejected (research.md R-1). Migration writer rejected in favour of kotlinx.serialization constructor-defaults per Clarification Q6 (R-2 revised 2026-07-16). Linear scan over index (R-4). Rule 4 Test 1: inlining Query API loses closed-set Tag type-safety + convenience selectors — kept. Test 2: swap to member methods = ~1 hour. |

**OVERALL: 7 PASS, 1 N/A, 0 FAIL — plan is COMPLETE.**

No Complexity Tracking entries needed.

---

## Project Structure

### Documentation (this feature)

```
specs/task-127-ecs-tags-and-query/
├── spec.md              # /speckit.specify → /speckit.clarify → /speckit.scenarios output
├── plan.md              # this file (/speckit.plan)
├── research.md          # Phase 0 output (query indexing exit ramp, migration writer approach)
├── data-model.md        # Phase 1 output (Tag / Component.tags / migration mapping)
├── contracts/
│   └── profile-v2.md    # Phase 1 output (Profile wire-format schemaVersion=2 + optional tags, no migration writer per Q6)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code

```
core/src/commonMain/kotlin/com/launcher/
├── preset/model/            [Component.kt, Preset.kt, Enums.kt modified; Profile.kt unchanged]
├── preset/query/            [ProfileQuery.kt new]
├── adapters/config/         [ConfigBackedFlowRepository.kt annotated — existing location]
└── adapters/flow/           [ProfileBackedFlowRepository.kt new]

core/src/commonTest/kotlin/com/launcher/
├── preset/query/            [ProfileQueryTest.kt, ProfileQueryBenchmarkTest.kt new]
├── preset/model/            [ComponentTagsFitnessTest.kt new]
├── preset/wire/             [ProfileSchemaV2RoundtripTest.kt new — existing wire-test package]
├── adapters/flow/           [ProfileBackedFlowRepositoryTest.kt new]
└── ui/navigation/           [HomeComponentLoadingStateTest.kt extended — existing location (JVM, not androidTest)]

core/src/commonTest/resources/fixtures/profile-wire-format/
├── profile-v2-sample.json   [new — roundtrip fixture]
└── profile-v2-no-tags.json  [new — constructor-defaults fixture]

core/src/androidMockBackend/kotlin/com/launcher/di/
└── BackendInit.kt           [modified — rebind FlowRepository]
core/src/androidRealBackend/kotlin/com/launcher/di/
└── BackendInit.kt           [modified — rebind FlowRepository]

app/src/main/java/com/launcher/app/
└── (WizardViewModel.kt debug logging removed as fix stabilises)

core/src/commonMain/composeResources/values/
└── strings_wizard.xml       [modified — EXISTING file; add missing wizard keys found by grep]
```

**Structure Decision**: Extend existing `core` KMP module with new files under `preset/query/` and `preset/serialization/`. No new gradle module — per rule 4 (MVA), extension methods + one adapter class do not warrant module split. Per Article V §3 criteria (ownership boundary, build isolation, independent enable/disable, stable API, material testability gain): none apply, so a new module would be premature abstraction.

---

## Complexity Tracking

*Fill ONLY if Constitution Check has violations. None anticipated for this plan — Query API is additive, no new modules, no new external deps.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none) | — | — |

---

## Rollout / verification

1. **Build gates**: `./gradlew :core:test` green, `./gradlew :app:assembleMockBackendDebug` green.
2. **Fitness gates**: `ComponentTagsFitnessTest` (subtypes with non-empty tags), `ProfileQueryBenchmarkTest` (p95 < 1 ms), source-set placement compile check.
3. **Contract gate**: `ProfileSchemaV2RoundtripTest` roundtrip + missing-tags + fail-loud pins.
4. **Integration gate**: `HomeComponentLoadingStateTest.postManifestWizardReconcile_profileSeeded_homeReady` green.
5. **Emulator smoke**: pixel_5_api_34 AVD — fresh install → wizard → HomeScreen shows tiles + localized wizard strings.
6. **Physical smoke**: Xiaomi Redmi Note 11 (adb id `17f33878`) — same flow. Closes SC-001 + SC-002.
7. **Backlog sync**: `pre-pr-backlog-sync` — verify `[auto:deferred-local-emulator]` and `[auto:deferred-physical-device]` gates, status → Verification (PR merged) → Done (physical smoke passes).

---

## TL;DR для владельца

**Что делаем**: расширяем модель TASK-120 (Component / Profile) двумя ECS-паттернами — тегами и запросом по тегам, — и переключаем HomeScreen на чтение `Profile` напрямую (сейчас он читает старую модель `ConfigDocument`, отсюда и «Не удалось загрузить настройки» после мастера).

**Что нового в коде**:
- `Tag` enum (10 значений: `Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency, Tile, Toolbar`).
- `Component.tags: Set<Tag>` — новое поле на **всех 8** субтипах компонентов (включая `Language` и `StatusBarPolicy`, которые раньше в документах пропускались). Дефолты: плитка приложения → `{Presentation, Tile}`, кнопка SOS → `{Presentation, Tile, Safety, Emergency}`, тулбар → `{Presentation, Toolbar}`. Два «синглтона» (`LauncherRole`, `StatusBarPolicy`) станут обычными data class'ами — совместимо со старыми данными.
- `Profile.query { ... }` + удобные селекторы (`byTag`, `byAllTags`, `byAnyTag`, `byNotTag`, `homeScreenTiles`, `toolbar`). `toolbar()` — query-based, без `is Toolbar`. `homeScreenTiles()` **не показывает** компоненты со статусом `Failed`/`Skipped` (устройство не смогло применить / пользователь пропустил в мастере) — пожилой пользователь не увидит мёртвую кнопку.
- `ProfileBackedFlowRepository` — новый адаптер, реализует **все четыре** метода существующего порта. Главное: `loadFlows()` (именно там рождалась ошибка «Не удалось загрузить настройки» — разовая загрузка с таймаутом 3 сек) теперь ждёт первый непустой Profile и возвращает плитки.
- **Никакой миграции сейчас** (суть решения Q6 сохранена): MVP не релизнут. `schemaVersion` **остаётся 2** — как в уже написанном коде TASK-120; добавление `tags` аддитивно, номер не трогаем (раннее «сбросить на 1» отменено — противоречило коду). Отсутствие `tags` в JSON = kotlinx.serialization подставляет constructor-default из Component subtype (единственный источник истины).
- `ConfigBackedFlowRepository` **остаётся** в коде для будущего сценария «админ пушит настройки», но `HomeScreen` его больше не использует.

**Что новых зависимостей**: ноль. Всё чистый Kotlin. Никаких новых gradle-модулей.

**Как проверяем**:
- JVM unit-тесты (запрос по тегам, roundtrip формата v2, дефолты для каждого компонента, фиксация fail-loud поведения на незнакомом теге/типе).
- JVM-тест `HomeComponentLoadingStateTest` (commonTest) с новым сценарием «после мастера → плитки видны».
- Ручной smoke на эмуляторе + физический Xiaomi (обязателен для закрытия SC-001 / SC-002).

**Что «one-way door»**: пока pre-release — none (можем менять formats свободно, сбрасывая dev `ProfileStore`). Post-release каждое breaking change полей = one-way door + migration writer + schemaVersion bump. `schemaVersion: 2` уже в формате (rule 5 требование выполнено с TASK-120) — это обеспечивает возможность будущей миграции. Честное ограничение: незнакомый тег/тип компонента роняет старого читателя — «снисходительный» читатель обязателен до того, как профили начнут ходить между устройствами (Risk R-8).

**Что откладываем**:
- Удаление `ConfigDocument` полностью — отдельная задача SRV-CONFIG-DEPRECATION в server-roadmap.
- Индексацию запросов (сейчас линейный проход по ~20 компонентам — достаточно). Exit ramp есть.
- Разделение `Toolbar` на отдельные кнопки — отдельная задача.
