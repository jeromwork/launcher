# Implementation Plan: Tile Editing — Admin and Senior Profiles (F-014.0)

**Branch**: `014-tile-editing-admin-senior-profiles`
**Date**: 2026-05-29
**Spec**: [spec.md](spec.md)
**Phase scope**: **F-014.0 only** (local-only DataStore, no server backup, no Google Sign-In dependency). F-014.1 (server backup) и F-014.2 (encryption) — separate plan rounds, blocked by F-4 / F-5.

---

## 1. Overview

F-014.0 закрывает базовый gap "no self-editing for admin, no local edit-mode for senior, no profile-driven UX" через **unified editing layer** поверх existing ConfigDocument (спека 008). Реализует domain verbs (`addSlot/removeSlot/moveSlot/replaceSlot`) + `EditUiProfileSelector` pure function + Compose presentation для двух UX profiles (admin / senior) — все локально, без server backup.

См. полный rationale в [spec.md](spec.md) §"Контекст и цель спека".

---

## 2. Technical Context

| Поле | Значение |
|---|---|
| Language/Version | Kotlin 2.0.x (KMM commonMain + androidMain) |
| Primary Dependencies | Compose Multiplatform, Decompose, AndroidX DataStore (existing), kotlinx-serialization (existing) |
| Storage | DataStore Preferences (local NamedConfigs metadata) + ConfigEditor pendingDraft (existing per спека 008) |
| Testing | kotlin.test (JVM unit), Compose UI test, `FakeConfigEditor`/`FakeProviderRegistry`/`FakeContactsRepository` |
| Target Platform | Android API 26+ (existing project floor) |
| Project Type | Mobile launcher (Android), KMM common domain |
| Performance Goals | APK delta ≤300KB (SC-008), edit-mode entry <100ms perceived, no dropped frames on Pixel 4a class (ADR-005) |
| Constraints | No new vendor SDKs, no new runtime permissions, plaintext local storage (F-5 encryption deferred) |
| Scale/Scope | F-014.0: single-device, single named config, no server sync. ~15 new files в core, ~10 в app |

---

## 3. Architecture

### 3.1 Module map

```
core/commonMain/kotlin/com/launcher/api/edit/
├── EditUiProfileSelector.kt        # pure function (FR-008, Q2)
├── EditUiProfile.kt                # sealed class AdminProfile/SeniorProfile
├── EditError.kt                    # sealed class (Key Entities)
├── TileEditOperation.kt            # Add/Move/Remove/Replace data
├── EditMode.kt                     # presentation-state value class
├── TargetIdentity.kt               # {linkId, presetId, isSelf}
├── PickerType.kt                   # enum Application/Contact/Document/Widget/Action
├── NamedConfig.kt                  # F-014.0 local domain type
└── TileEditOperations.kt           # addSlot/removeSlot/moveSlot/replaceSlot (FR-001)

core/commonTest/kotlin/com/launcher/api/edit/
├── EditUiProfileSelectorTest.kt    # SC-005 unit test
├── TileEditOperationsTest.kt       # FR-001 contract tests
└── EditErrorTest.kt                # sealed exhaustiveness

data/src/main/kotlin/com/launcher/adapter/edit/
└── NamedConfigsLocalStore.kt       # DataStore adapter (F-014.0 only)

data/src/test/kotlin/com/launcher/adapter/edit/
└── NamedConfigsLocalStoreTest.kt   # roundtrip + persistence

app/src/main/kotlin/com/launcher/ui/edit/
├── EditModeComposable.kt           # edit-mode entry + jiggle + banner host
├── EditTopBanner.kt                # «Готово» banner + remote indicators
├── RemoteEditFrame.kt              # 4dp frame для FR-014
├── EmptyStateTile.kt               # FR-020/FR-020a — empty-state «+»
├── UnifiedPickerSheet.kt           # 5/3 tabs picker (FR-018/FR-019)
├── PlaceholderInDevelopmentScreen.kt  # Widget/Action/Custom preset placeholders (FR-018, FR-008b)
└── ConflictSnackbar.kt             # FR-016 admin-side snackbar host

app/src/main/kotlin/com/launcher/ui/edit/integration/
└── ExistingEditorScreenExtensions.kt   # расширяет EditorScreen из спеки 009
```

### 3.2 Port-adapter shape

```
Domain (core/commonMain)                  Adapter (data/, app/)
─────────────────────                     ──────────────────────
TileEditOperations         ──┐
EditUiProfileSelector      ──┼──► (pure, no adapter needed)
EditError, EditMode...     ──┘

ConfigEditor (existing port,             ─► FirestoreConfigEditor (existing)
 спека 008)                              ─► FakeConfigEditor (tests)

NamedConfigsLocalStore     ─────────────► DataStoreNamedConfigsLocalStore (F-014.0)
 (NEW port,                              ─► FakeNamedConfigsLocalStore (tests)
  read+write NamedConfig list locally)
```

### 3.3 Data flow

```
User action (long-press / 7-tap / tap target tile)
    │
    ▼
EditMode.enter(targetIdentity)
    │
    ▼  derived
EditUiProfileSelector.selectProfile(targetIdentity.presetId)
    │   ─► AdminProfile | SeniorProfile | EditError.ProfileSelectionRequiresCapabilityRegistry
    │
    ▼
Compose UI renders EditModeComposable with profile-specific decorations
    │
User: add/move/remove tile
    │
    ▼
TileEditOperations.{add|move|remove|replace}Slot(...)
    │   ─► Outcome<ConfigDocument, EditError>
    │
    ▼
ConfigEditor.updateDraft { newConfig }
    │
    ▼
ConfigEditor.pushPending() (async, governed спекой 008)
    │   ─► success | ConfigSyncError.Conflict (handled per FR-016)
```

### 3.4 Profile asymmetry (per Q7)

```
Conflict detected by ConfigEditor.pushPending()
    │
    ├─ if EditMode.profile == AdminProfile
    │     ▼
    │     ConflictSnackbar shows "[Обновить] [Перезаписать]"
    │     User picks → re-fetch & merge OR pushPending(force=true)
    │
    └─ if EditMode.profile == SeniorProfile
          ▼
          Senior's local write applied silently (last-local-write-wins).
          If admin had a pending push from other device — that push is rejected;
          admin gets snackbar "Бабушка только что изменила. [Обновить]" (no Overwrite).
```

---

## 4. Data model

См. [data-model.md](data-model.md). Сводка:
- `NamedConfig` (F-014.0 local) — domain type.
- `EditMode` — presentation state.
- `TargetIdentity`, `TileEditOperation`, `EditUiProfile`, `EditError`, `PickerType` — supporting types.
- `ConfigDocument` (existing спека 008) — **не меняется** в F-014.0. Schema bump 1→2 откладывается на F-014.1.

---

## 5. Wire formats

| Wire format | Phase | Schema | Contract |
|---|---|---|---|
| `NamedConfig` (DataStore JSON) | F-014.0 | v1 | [contracts/named-config-local.md](contracts/named-config-local.md) |
| `ConfigDocument` (existing) | unchanged | v1 (existing) | governed спека 008, no change in F-014.0 |
| `ConfigDocument` v2 (named-config fields) | **F-014.1, not F-014.0** | v2 | deferred to F-014.1 plan |

---

## 6. Dependency impact

**Новых gradle dependencies — НЕТ.** Все требуемые libraries уже в проекте:
- `androidx.datastore:datastore-preferences` (existing).
- `org.jetbrains.kotlinx:kotlinx-serialization-json` (existing).
- `androidx.compose.material3` (existing).
- `com.arkivanov.decompose:decompose` (existing).

Per Article XIII §1 (no new external dependencies без justification) — gate PASS trivially.

APK delta измерение: gradle task `:app:assembleRelease` до и после F-014.0, diff через APK Analyzer. Target ≤300KB per SC-008.

---

## 7. Test strategy

Per CLAUDE.md rule 6 (mock-first) + 7 (fitness functions):

### 7.1 Contract tests

- `TileEditOperationsTest` — FR-001: add/move/remove/replace return `Outcome<ConfigDocument, EditError>` for valid + invalid inputs (slot not found, flow not found, invalid position).
- `EditUiProfileSelectorTest` — SC-005: Workspace→Admin, SimpleLauncher→Senior, unknown built-in→Admin fallback, custom preset→`ProfileSelectionRequiresCapabilityRegistry`.
- `NamedConfigsLocalStoreContractTest` — FR-003: read/write/list, default-flag invariant (FR-003a), 5-config limit refuse (FR-003c).

### 7.2 Integration tests

- `EditModeIntegrationTest` (`:app:test`) — entry via long-press, jiggle visible, banner shown, add/move/remove flows, exit via "Готово".
- `RemoteEditIntegrationTest` — 2-эмулятор smoke (admin Pixel 7 + Managed Pixel 4a): frame + banner visible, picker filtered to 3 tabs (FR-019), push to Firestore mocked through FakeConfigEditor.
- `ConcurrentEditConflictTest` (FR-016, FR-017) — admin push + senior local write: admin sees snackbar, senior writes succeed silently per Q7.
- `EmptyStateTileTest` (FR-020/FR-020a) — empty config renders «+» tile, tap opens picker directly, after first add → use mode (no edit mode).

### 7.3 Fake adapters

- `FakeNamedConfigsLocalStore` — in-memory list, supports `applyTo/markDefault/listOrphans`. Required by tests of `TileEditOperations` and presentation.
- Existing fakes reused: `FakeConfigEditor`, `FakeProviderRegistry`, `FakeInstalledAppsCatalog`, `FakeContactsRepository`.

### 7.4 Fitness functions (automated invariants)

- **Konsist** lint rule: no class in `core/commonMain/api/edit/` imports `android.*`, `androidx.*`, `com.google.firebase.*`. Per CLAUDE.md rule 1.
- **Konsist** lint rule: no class in `core/commonMain/api/edit/` имеет `expect`/`actual` (forces pure Kotlin).
- **APK size check** (CI): release APK size delta ≤300KB per SC-008. Fails build if exceeded.

### 7.5 Manual smoke

- 2-эмулятор smoke: admin Workspace self-edit + remote target edit + senior 7-tap → all 3 flows работают per Local Test Path в spec.

---

## 8. Risks

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | TalkBack accessibility regression — drag-and-drop в edit mode (FR-012) недоступен для screen reader | **High** | **Plan**: add alt mechanism — long-press tile triggers context menu "Переместить вверх/вниз/влево/вправо/Удалить". Add new FR-012a in spec post-plan если architecturally significant. **Track**: accessibility.md CHK010. |
| R2 | OEM long-press dispatch conflict (MIUI custom handler) | Medium | HOME role claim makes our launcher first-priority. Test on Xiaomi Mi 11 Lite 5G. Listed Cannot-test-locally → physical-device. |
| R3 | Jiggle animation jank на low-end OEM (OPPO ColorOS, Vivo FuntouchOS) | Medium | FR-011 — honor `prefers-reduced-motion`, fallback to static frame. Cannot-test-locally gap explicit. |
| R4 | State loss on Activity recreation в edit mode (rotation, theme change) | Medium | Decompose component lifecycle для `EditMode` state — already-supported pattern. Add `EditModeStateRestorationTest`. |
| R5 | Process-death во время unsaved edit | Low | ConfigEditor.pendingDraft already persists (спека 008). Edit-mode flag — UI-local, reset on restart (acceptable mainstream behavior). |
| R6 | configName user input — Unicode normalization / collision detection | Medium | Plan.md specify: NFC normalize, case-insensitive uniqueness, length 1-32, allowed Unicode letters+digits+space+hyphen. Add ValidationError variant in NamedConfigsLocalStore. |
| R7 | Picker cold-load latency (installed apps fetch) | Low | Skeleton loading state, async fetch on tab activation. Existing спека 005 pattern. |
| R8 | F-014.1 phase delivers с named-configs domain shape, но F-014.0 строит с 1-config single-state UI hidden — schema mismatch risk при F-014.1 upgrade | Low | Domain shape **already supports** multi-config in F-014.0 (per spec meta-minimization analysis). Only UI hidden via FR-003d State 0/1. F-014.1 unlock'ает UI без domain refactor. |

---

## 9. Required Context Review

Per Article XII §7 — все relevant docs прочитаны / linked:

### Constitution + governance
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md):
  - Article III §4 (deterministic fallback) — applied к conflict resolution.
  - Article IV §5 + §III.3 (state survival) — risks R4, R5.
  - Article V §3 (modularization restraint) — no new modules in F-014.0.
  - Article VII §3 (wire format versioning) — NamedConfig v1, ConfigDocument unchanged.
  - Article VIII §7 (senior-safe) — FR-013, FR-021, Q4 cancellation, Q7 silent conflict.
  - Article IX (performance) — SC-008 APK budget.
  - Article XI (MVA) — meta-minimization passes.
  - Article XIII (dependencies) — no new deps.
  - Article XIV (security) — security checklist passes.
  - Article XV (AI dignity) — AI Affordance read-only, F-2 deferred.
  - Article XVI (Constitution Check) — invoked в §10 below.

### ADRs
- [docs/adr/ADR-001-platform-parity.md](../../docs/adr/ADR-001-platform-parity.md) — Kotlin domain в commonMain.
- [docs/adr/ADR-004-localization.md](../../docs/adr/ADR-004-localization.md) — strings.xml externalization, Russian plurals (localization checklist).
- [docs/adr/ADR-005-performance.md](../../docs/adr/ADR-005-performance.md) — APK budget 12MB release / 18MB debug, frame budget Pixel 4a class.

### Project docs
- [CLAUDE.md](../../CLAUDE.md) — все 10 правил применены через checklists.
- [docs/dev/project-constants.md](../../docs/dev/project-constants.md) — §Senior-safe vs mainstream UX rules (key for Q4 cancellation).
- [docs/dev/server-roadmap.md](../../docs/dev/server-roadmap.md) — F-014.1 phase entry to add (backend-substitution.md CHK004).
- [docs/product/roadmap.md](../../docs/product/roadmap.md) — F-4 dependency для F-014.1.
- [docs/product/future/ecosystem-vision.md](../../docs/product/future/ecosystem-vision.md) — §Compositable Presets (F-008a exit ramp).
- [docs/research/2026-05-28-shared-editor-deep-dive.md](../../docs/research/2026-05-28-shared-editor-deep-dive.md) — Figma multiplayer pattern rationale.

### Existing specs (extends)
- Спека 003 (UI Skeleton), 005 (AddSlotWizard), 008 (Bidirectional Config Sync), 009 (Admin Mode Flows), 010 (Setup Assistant), 011 (Contacts), 012 (Documents).

---

## 10. Constitution Check

> Generated by `procedure-constitution-check` 2026-05-29 after initial plan draft + FR-012a remediation.

**Status**: ✅ **PASS** (8/8 после Gate 5 remediation).

| Gate | Article | Verdict | Notes |
|---|---|---|---|
| 1. Architecture | V §3 | PASS | No new gradle modules; ports/adapters from existing :core/:data/:app. EditUiProfileSelector + NamedConfigsLocalStore — port-shape justified (≥2 adapters: real + fake). |
| 2. Core/System Integration | VI | PASS | No new BroadcastReceivers / system events. ConfigEditor event flow inherited from спека 008. |
| 3. Configuration | VII §3 + CLAUDE rule 5 | PASS | NamedConfig wire-format has explicit `schemaVersion: Int = 1`. ConfigDocument unchanged in F-014.0. Forward-compat policy fail-closed. Validation rules (NFC, length 1-32, allowed chars, uniqueness) documented в contracts/named-config-local.md. |
| 4. Required Context Review | XII §7 | PASS | Plan §9 explicitly links 11 constitution articles, 3 ADRs (001/004/005), 5 project docs, 1 research doc, 7 existing specs. Permissions docs N/A — F-014 не trogает permissions. |
| 5. Accessibility | VIII §7 | PASS (after remediation) | Initially PARTIAL FAIL — TalkBack drag-and-drop gap. **Remediation applied**: FR-012a добавлен в spec.md (context menu alternative для drag-and-drop при `isTouchExplorationEnabled()==true`). Tap target ≥56dp senior use mode (FR-013/FR-021). Contrast tokens + contentDescription mapping → tasks.md responsibility. |
| 6. Battery/Performance | IX | PASS | No background work / polling / alarms. Cold-start не затронут. APK budget ≤300KB explicit (SC-008). Frame budget Pixel 4a per ADR-005. Risk R7 (picker cold-load) mitigation: skeleton + async fetch. |
| 7. Testing | X §3 + CLAUDE rule 6 | PASS | Contract + integration + fake adapters + 2 fitness functions (Konsist domain isolation + APK size CI). Roundtrip + invariant tests для NamedConfig wire-format. |
| 8. Simplicity | XI §2 + CLAUDE rule 4 | PASS | meta-minimization checklist 15/15 PASS. NamedConfig 9 sub-FRs justified via Test 1 (inline would force F-014.1 rewrite). ProfileSelectionRequiresCapabilityRegistry — explicit refuse > silent fallback per rule 3. |

### Remediation applied

**Gate 5** initially flagged FR-012a missing для TalkBack accessibility. Resolved 2026-05-29: FR-012a added to spec.md §Functional Requirements — Senior profile UX, specifying context menu alternative for drag-and-drop при `AccessibilityManager.isTouchExplorationEnabled() == true`.

After remediation: 8/8 PASS. Plan is **complete** and ready for `speckit-tasks`.

---

## 11. Rollout / verification

### 11.1 Phase gating

F-014.0 — local-only, может ship'иться **независимо** от F-4 / F-5. Production-ready без backend dependencies.

### 11.2 Verification milestones

1. **Domain milestone**: `:core:test --tests *TileEdit*` + `*EditUiProfileSelector*` + `*NamedConfigsLocalStore*` все зелёные. Konsist fitness functions pass.
2. **UI milestone**: `:app:test --tests *EditMode*` + `*EmptyStateTile*` + `*UnifiedPicker*` + `*RemoteEditIntegration*` все зелёные.
3. **Smoke milestone**: 2-эмулятор smoke прошёл — admin Workspace edit, remote target edit, senior 7-tap edit.
4. **APK budget milestone**: release APK delta ≤300KB measured.
5. **Accessibility milestone**: TalkBack manual test — drag alt context menu work'ает (если R1 mitigation реализуется как new FR-012a).

### 11.3 Rollout strategy

F-014.0 ships в одном release branch — no feature flag (per CLAUDE.md "don't use feature flags when you can change code"). Single-config domain shape compatible с existing users (defaults to "config_default" config_name with isDefault=true).

### 11.4 Phase F-014.1 readiness

Когда F-4 (Google Sign-In + AuthProvider) ship'нется, F-014.1 plan round:
- Add `RemoteNamedConfigsStore` adapter для Firestore.
- Bump ConfigDocument schema 1→2 (add named-config fields).
- Build My Configs screen + push edit dialog.
- Add backward-compat v1→v2 migration + roundtrip fixtures.

См. wire-format.md / state-management.md open items для full F-014.1 list.

---

## 12. Project Structure

### 12.1 Documentation

```
specs/014-tile-editing-admin-senior-profiles/
├── spec.md                          # clarified (Q1-Q9 resolved)
├── plan.md                          # THIS FILE
├── research.md                      # one-way doors analysis
├── data-model.md                    # NamedConfig + EditMode + supporting types
├── quickstart.md                    # dev workflow (run, test)
├── contracts/
│   └── named-config-local.md        # F-014.0 wire format
├── checklists/                      # generated by speckit-clarify
│   ├── _overview.md
│   ├── requirements-quality.md
│   ├── meta-minimization.md
│   ├── ... (15 more)
└── tasks.md                         # generated by speckit-tasks (not by plan)
```

### 12.2 Source code (selected layout from §3.1)

KMM project. `commonMain` для domain, `androidMain` (effectively in `app/`) для Compose UI. Tree shown in §3.1 Module map.

**Structure Decision**: KMM-with-app layout matches existing repo. F-014 не вводит новых gradle modules; все changes в existing `core` (domain), `data` (adapter), `app` (presentation) modules.

---

## 13. Complexity Tracking

Constitution Check violations? See §10. None expected — F-014.0 is exemplary MVA per meta-minimization checklist.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| (none expected) | | |

---

## TL;DR на русском

**Что в этом плане**: разбивка F-014.0 phase спеки 014 на конкретные файлы, тесты, риски. **F-014.0 = local-only**: domain verbs + profile selector + Compose UI + DataStore named configs metadata. **Без server backup, без encryption, без Google Sign-In** — это F-014.1 и F-014.2 phases.

**Ключевые архитектурные точки**:
1. **Все новые domain types** живут в `core/commonMain/api/edit/` — pure Kotlin, no platform deps. `EditUiProfileSelector` — pure function, unit-testable без UI runtime.
2. **NamedConfigsLocalStore** — новый port, один adapter (DataStore) для F-014.0. F-014.1 добавит второй adapter (Firestore) without domain change.
3. **ConfigDocument schema не меняется** в F-014.0 (bump 1→2 откладывается на F-014.1).
4. **Никаких новых gradle modules, никаких новых vendor SDKs, никаких новых runtime permissions**.

**Главный риск (R1)**: TalkBack accessibility — drag-and-drop edit mode (FR-012 universal mainstream) недоступен screen reader пользователю. **Mitigation**: alt context menu "Переместить вверх/вниз/влево/вправо". Может потребовать добавить FR-012a в спеку — обсудить перед tasks.md.

**8 рисков total**, 1 high (R1 TalkBack), 4 medium, 3 low.

**Test strategy**: contract tests домен + integration UI tests + 2 fitness functions (Konsist для domain isolation, APK size CI check) + manual 2-эмулятор smoke.

**Constitution Check** запускается ниже (§10) — пока PENDING до Step 4 orchestrator'а.
