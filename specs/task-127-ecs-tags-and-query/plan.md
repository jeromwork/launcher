# Implementation Plan: ECS Tags Foundation + HomeScreen Query Rewire

**Branch**: `task-127-home-config-load-fix` | **Date**: 2026-07-16 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/task-127-ecs-tags-and-query/spec.md`
**Backlog task**: [task-127](../../backlog/tasks/task-127%20-%20HomeActivity-config-load-failure-post-wizard-TASK-126-regression.md)

---

## Summary

Extend TASK-120 domain model with ECS-standard tag+query patterns (add `Component.tags: Set<Tag>` + `Profile.query`), replace HomeScreen's `ConfigBackedFlowRepository` binding with a new `ProfileBackedFlowRepository` that reads `Profile` directly through the query API. Fixes the TASK-52 regression introduced by TASK-126 (wizard writes `Profile`, HomeScreen reads `ConfigDocument` — architectural gap → Error UI). Migration writer v2 → v3 fills tags defaults from Component subtype; `ConfigDocument` code stays for future admin push scenarios but is no longer bound to `FlowRepository`.

---

## Technical Context

**Language/Version**: Kotlin 2.0 (Multiplatform: `commonMain` + `androidMain`).
**Primary Dependencies**: kotlinx.serialization (Profile JSON), kotlinx.coroutines (Flow API on `ProfileStore.observe()`). No new deps.
**Storage**: `ProfileStore` = local file-based JSON on device (existing from TASK-120). Pool = bundled asset (`pool.json` in APK).
**Testing**: JVM unit tests (`./gradlew :core:test`), Robolectric instrumented (`:core:connectedAndroidTest`), manual on emulator + Xiaomi Redmi Note 11 physical device.
**Target Platform**: Android (minSdk 24, target 34). Zero Android imports in Query API / Tag / `ProfileBackedFlowRepository` per rule 1 (domain isolation).
**Project Type**: Mobile app (Android launcher). This spec touches `core` KMP module + `app` Android DI wiring.
**Performance Goals**: `profile.query(...)` under 1 ms at MVP scale (~20 Components in Profile). Linear scan; indexing deferred (see research.md).
**Constraints**: Wire-format bump v2→v3 is one-way door per rule 5 — migration writer + backward-compat roundtrip test mandatory. Zero Android imports in domain layer.
**Scale/Scope**: MVP Profile ~10-20 Components. HomeScreen renders 3-8 tiles + 1 Toolbar. Query invoked on every Profile emission (rare — user edits) + on cold start.

---

## Architecture

### Module map

```
core/                                    (KMP module, commonMain)
├── preset/
│   ├── model/
│   │   ├── Enums.kt                     [MODIFIED] add Tag enum
│   │   ├── Component.kt                 [MODIFIED] add tags: Set<Tag>
│   │   ├── Preset.kt                    [MODIFIED] doc-comment ref preset-model.md
│   │   └── Profile.kt                   [MODIFIED] add query API extensions
│   ├── serialization/
│   │   ├── ProfileSerializer.kt         [MODIFIED] add v2→v3 migration hook
│   │   └── ProfileMigrationV2toV3.kt    [NEW] hardcoded subtype→tags mapping
│   └── query/
│       └── ProfileQuery.kt              [NEW] extension functions on Profile
└── adapters/
    └── flow/
        ├── FlowRepository.kt            [UNCHANGED] existing port
        ├── ConfigBackedFlowRepository.kt [MODIFIED] add TODO(config-deprecation) comment
        └── ProfileBackedFlowRepository.kt [NEW] reads ProfileStore, projects via query

app/                                     (Android app module)
└── src/main/java/com/launcher/app/di/
    ├── MockBackendModule.kt             [MODIFIED] rebind FlowRepository
    └── RealBackendModule.kt             [MODIFIED] rebind FlowRepository

docs/
├── architecture/
│   └── preset-model.md                  [NEW] two-dimensions doc (already merged)
└── dev/
    └── server-roadmap.md                [MODIFIED] SRV-CONFIG-DEPRECATION entry (already merged)
```

### Port-adapter shape

```
                     ┌──────────────────────────┐
HomeComponent ──────►│  FlowRepository (port)   │
                     │  observeFlows(): Flow<…> │
                     └──────────────────────────┘
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
  ↓ observeFlows()
ProfileBackedFlowRepository
  ↓ profileStore.observe().filterNotNull()
  ↓ profile.homeScreenTiles()          ← query API
Flow<List<FlowDescriptor>>
  ↓
HomeLoadingState.Ready(flows)
```

### Rule 1 verification (domain isolation)

All new files in `core/preset/model/`, `core/preset/query/`, `core/preset/serialization/` are pure Kotlin — zero `import android.*`. `ProfileBackedFlowRepository` in `core/adapters/flow/` also zero Android imports (`FlowRepository` port is domain-level; `ProfileStore` is a port too, not a vendor SDK). Verified by `checklist-domain-isolation` on plan-level review + build-time fitness function (source-set placement).

---

## Data model

See [`data-model.md`](data-model.md).

Key types:
- `Tag` enum (9 values, additive-only per rule 5).
- `Component.tags: Set<Tag>` with per-subtype defaults.
- `Profile` query API surface (extension functions or member methods — decision in research.md).
- `ProfileMigrationV2toV3` mapping table.

---

## Wire formats

- [`contracts/profile-v3.md`](contracts/profile-v3.md) — Profile serialized JSON schemaVersion=3. Documents new `tags` field per Component, migration path from v2, backward-compat guarantees.

Not touched:
- `pool.json` — bundled build-time artefact, additive optional `tags` field on `ComponentDeclaration`; no schemaVersion bump (per Clarification Q2).
- `ConfigDocument` — unchanged (stable for admin push scenarios).

---

## Dependency impact

**No new gradle dependencies.** Feature is pure Kotlin extensions on existing types + one new adapter class + one migration writer. Reuses existing kotlinx.serialization + kotlinx.coroutines Flow already in `core` module.

Per Article XIII (Dependency Restraint): the "add nothing" outcome is preferred; documented explicitly.

---

## Test strategy

Per CLAUDE.md rules §6 (mock-first) + §7 (fitness functions).

### Contract tests (`core/src/commonTest/`)

- **`ProfileWireFormatV3ContractTest`**: roundtrip Profile v3 → JSON → Profile v3 (byte-equal serialization stable). Uses fixture in `core/src/commonTest/resources/fixtures/profile-v3-sample.json`.
- **`ProfileMigrationV2toV3RoundtripTest`**: read v2 fixture → migrate → assert tags populated per subtype defaults. Re-apply migration on result → assert unchanged (idempotency, NFR-004).
- **`ProfileMigrationV2toV3BackwardCompatTest`**: v2 fixture read succeeds without exception; migration writer produces valid v3.

### Unit tests

- **`ProfileQueryTest`**: `byTag` (single), `byAllTags` (AND), `byAnyTag` (OR), `homeScreenTiles`, `toolbar`, empty result, tag-not-present, empty Profile.
- **`ProfileBackedFlowRepositoryTest`** (JVM, uses `FakeProfileStore`):
  - Non-null Profile → emits `List<FlowDescriptor>` matching `homeScreenTiles()`.
  - `filterNotNull` — null emission upstream → no downstream emission.
  - Profile update sequence (v1 → v2 emissions) → repository emits two lists.
- **`ComponentTagsFitnessTest`**: reflection walk over `Component` sealed subclasses; assert each has non-empty `tags` in default constructor. Prevents accidental `emptySet()` regressions.

### Integration tests (`:core:androidTest`)

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
| R-1 | v2 → v3 migration writer has a subtype we forgot → migrated Profile has `tags = emptySet()` → Component invisible to queries | Medium | High | `ComponentTagsFitnessTest` (reflection walk) catches this at build time; migration writer + fitness function share subtype list (co-located). |
| R-2 | Existing v2 Profile on disk from earlier dev builds → user upgrades, migration runs, disk write racing with wizard save | Low | Medium | Migration is read-side only (lazy write on next `ProfileStore.save()`); no concurrent writes at cold start (single reader). |
| R-3 | `ProfileStore.observe()` semantic differs from assumption (`Flow<Profile?>` — nulls on cold start) → `filterNotNull` starves HomeComponent | Low | Medium | Verify in TASK-126 code before implementation (Assumption from spec.md `## Assumptions`). If contract differs, adapt `ProfileBackedFlowRepository` (may become `Flow<Profile>` directly). |
| R-4 | Query linear scan performance degrades if Profile grows past MVP scale (100+ Components) | Low (MVP) | Low (MVP) | Documented exit ramp in research.md § "Query indexing" — indexed lookup as additive change; no wire-format break required. |
| R-5 | Xiaomi MIUI kills HomeActivity between wizard finish and HomeComponent init | Low | High | `HomeComponent` reads `Profile` from disk on init (not from in-memory hand-off) — surviving process death by design (state-management pattern from TASK-52). |
| R-6 | `Toolbar` Component in Profile but `Profile.toolbar()` returns null (e.g., someone sets `tags = emptySet()`) | Low | Low | Covered by `ComponentTagsFitnessTest`; runtime null tolerated (Toolbar is optional UI element). |

---

## Required Context Review

- [CLAUDE.md](../../CLAUDE.md) — rules §1 (domain isolation), §4 (MVA), §5 (wire-format versioning), §9 (shareability-readiness).
- [ADR-011](../../docs/adr/ADR-011-ai-owner-collaboration-conventions.md) — sequences + checklist-in-chat convention (used in this spec).
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
| G-3 Configuration | **PASS** | Wire-format bump v2 → v3 explicit; `schemaVersion` field present in Profile v3 contract; migration writer + backward-compat roundtrip test scoped (`ProfileMigrationV2toV3RoundtripTest`, `ProfileMigrationV2toV3BackwardCompatTest`, `ProfileWireFormatV3ContractTest`). Pool.json addition is additive optional per Clarification Q2. |
| G-4 Required Context Review | **PASS** | Links present: CLAUDE.md (rules 1, 4, 5, 9), ADR-011, constitution.md (XI, XVI, XVII), preset-model.md, server-roadmap.md, TASK-120 Decision, task-49 as precedent. No permissions change (no compliance doc needed). |
| G-5 Accessibility | **PASS** | US-3 (wizard localization) → readable strings for senior users; SC-002 verifies. No new UI surfaces below 56dp — only existing HomeScreen (senior-safe per TASK-52) + wizard (senior-safe per TASK-126). `FontSize` Component carries `Tag.Accessibility` — fitness function verifies. |
| G-6 Battery / Performance | **PASS** | Event-driven (`ProfileStore.observe()` emission on user edit); no polling. One-time migration cost on v2→v3 (lazy write, SEQ-2). Perf target NFR-003 (< 1 ms) + SC-008 benchmark. Zero new deps. |
| G-7 Testing | **PASS** | Contract (roundtrip v3, migration idempotency, backward-compat v2 read); unit (Query API, Profile-backed repo, fitness); integration (`HomeComponentLoadingStateTest` extended); fitness function (`ComponentTagsFitnessTest` reflection walk); micro-benchmark. `FakeProfileStore` for adapter testing. |
| G-8 Simplicity | **PASS** | `ProfileQueryService` explicitly rejected (research.md R-1). Migration writer justified over `@Serializable` default (R-2). Linear scan over index (R-4). Rule 4 Test 1: inlining Query API loses closed-set Tag type-safety + convenience selectors — kept. Test 2: swap to member methods = ~1 hour. |

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
│   └── profile-v3.md    # Phase 1 output (Profile wire-format schemaVersion=3)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code

```
core/src/commonMain/kotlin/com/launcher/
├── preset/model/            [Component.kt, Preset.kt, Profile.kt, Enums.kt modified]
├── preset/serialization/    [ProfileSerializer.kt modified; ProfileMigrationV2toV3.kt new]
├── preset/query/            [ProfileQuery.kt new]
└── adapters/flow/           [ConfigBackedFlowRepository.kt annotated; ProfileBackedFlowRepository.kt new]

core/src/commonTest/kotlin/com/launcher/
├── preset/query/            [ProfileQueryTest.kt, ComponentTagsFitnessTest.kt]
├── preset/serialization/    [ProfileMigrationV2toV3RoundtripTest.kt, ProfileWireFormatV3ContractTest.kt, ProfileMigrationV2toV3BackwardCompatTest.kt]
└── adapters/flow/           [ProfileBackedFlowRepositoryTest.kt]

core/src/commonTest/resources/fixtures/
├── profile-v2-sample.json   [new — v2 baseline for migration test]
└── profile-v3-sample.json   [new — v3 roundtrip fixture]

core/src/androidTest/kotlin/com/launcher/
└── home/                    [HomeComponentLoadingStateTest.kt extended with postManifestWizardReconcile scenario]

app/src/main/java/com/launcher/app/
├── di/                      [MockBackendModule.kt, RealBackendModule.kt modified]
└── (WizardViewModel.kt debug logging removed as fix stabilises)

core/src/commonMain/composeResources/values/
└── strings_wizard.xml       [modified — add wizard_step_of plurals + wizard_component_font_size / wizard_component_sos / wizard_confirm etc.]
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
3. **Contract gate**: `ProfileWireFormatV3ContractTest` byte-equal roundtrip + `ProfileMigrationV2toV3RoundtripTest` idempotent.
4. **Integration gate**: `HomeComponentLoadingStateTest.postManifestWizardReconcile_profileSeeded_homeReady` green.
5. **Emulator smoke**: pixel_5_api_34 AVD — fresh install → wizard → HomeScreen shows tiles + localized wizard strings.
6. **Physical smoke**: Xiaomi Redmi Note 11 (adb id `17f33878`) — same flow. Closes SC-001 + SC-002.
7. **Backlog sync**: `pre-pr-backlog-sync` — verify `[auto:deferred-local-emulator]` and `[auto:deferred-physical-device]` gates, status → Verification (PR merged) → Done (physical smoke passes).

---

## TL;DR для владельца

**Что делаем**: расширяем модель TASK-120 (Component / Profile) двумя ECS-паттернами — тегами и запросом по тегам, — и переключаем HomeScreen на чтение `Profile` напрямую (сейчас он читает старую модель `ConfigDocument`, отсюда и «Не удалось загрузить настройки» после мастера).

**Что нового в коде**:
- `Tag` enum (9 значений: `Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency, Tile`).
- `Component.tags: Set<Tag>` — новое поле на компонентах, дефолты для каждого субтипа (плитка приложения → `{Presentation, Tile}`, кнопка SOS → `{Presentation, Tile, Safety, Emergency}`, тулбар → `{Presentation}` без `Tile`).
- `Profile.query { ... }` + удобные селекторы (`byTag`, `homeScreenTiles`, `toolbar`).
- `ProfileBackedFlowRepository` — новый адаптер, читает `Profile` и отдаёт плитки на `HomeScreen`.
- Миграция сохранённых профилей v2 → v3 (заполняет теги дефолтами, идемпотентная).
- `ConfigBackedFlowRepository` **остаётся** в коде для будущего сценария «админ пушит настройки», но `HomeScreen` его больше не использует.

**Что новых зависимостей**: ноль. Всё чистый Kotlin. Никаких новых gradle-модулей.

**Как проверяем**:
- JVM unit-тесты (запрос по тегам, roundtrip миграции, дефолты для каждого компонента).
- Robolectric-тест `HomeComponentLoadingStateTest` с новым сценарием «после мастера → плитки видны».
- Ручной smoke на эмуляторе + физический Xiaomi (обязателен для закрытия SC-001 / SC-002).

**Что «one-way door»**: bump `schemaVersion` профиля с 2 на 3 — необратимый (после релиза старая версия приложения не сможет прочитать v3). Стандартная практика; exit ramp — миграция идемпотентная, downgrade не поддерживается.

**Что откладываем**:
- Удаление `ConfigDocument` полностью — отдельная задача SRV-CONFIG-DEPRECATION в server-roadmap.
- Индексацию запросов (сейчас линейный проход по ~20 компонентам — достаточно). Exit ramp есть.
- Разделение `Toolbar` на отдельные кнопки — отдельная задача.
