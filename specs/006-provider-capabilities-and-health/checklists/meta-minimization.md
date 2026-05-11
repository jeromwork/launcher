# Checklist: meta-minimization — spec 006

**Spec:** [`spec.md`](../spec.md) · **Run:** 2026-05-09 (speckit-clarify pre-plan pass) · **Score:** 7 ✓ / 2 ◐ / 0 ✗ / 4 N/A

Source: Article XI of [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md), rule 4 of [`CLAUDE.md`](../../../CLAUDE.md).

Legend: `[x]` pass · `[~]` partial · `[ ]` fail · `N/A` not applicable.

---

## Inventory of new abstractions in this spec

| # | Entity | Kind | Current consumers (this spec) |
|---|--------|------|-------------------------------|
| A1 | `CapabilityRepository` | port (commonMain) | AddSlotWizard (US2), CapabilitySnapshotDebugActivity (US1), Setup Assistant (US1 future-but-cited) |
| A2 | `HealthRepository` | port (commonMain) | HealthSnapshotDebugActivity (US3), OfflineGraceWatcher (FR-013) |
| A3 | `Capability` | domain entity | A1 |
| A4 | `Health` | domain entity | A2 |
| A5 | `IconRef = Bundled \| Remote` | sealed class | A1 emits **only `Bundled`** in this spec; `Remote` has no consumer until spec 007/009 |
| A6 | `Connectivity` enum | domain enum | A4 field |
| A7 | `OfflineGraceWatcher` | Android adapter | WorkManager periodic worker per FR-014 |
| A8 | `CapabilityProjection`, `HealthProjection` | DataStore Serializer | cold-start fast path per FR-005 |
| A9 | Toggle `raiseRingerOnLongOffline` | Settings field | FR-014 watcher reads, FR-016 Settings UI exposes |

No new gradle modules.

---

## New abstractions

- [x] **CHK001** Every new port has a consumer in this spec.
  - All ports A1, A2 have 2+ in-spec consumers. ✓
- [x] **CHK002** Single-impl port justified by port-shape need.
  - A1, A2: real Android adapter + fake (CLAUDE.md rule 6 mock-first), plus KMP commonMain port for iOS deferred. ✓
- [x] **CHK003** Mediator/orchestrator justified by data transformation.
  - A1 transforms `ProviderState → Capability` (icon resolution, displayName, version). A7 is decision-maker (compares timestamps, checks toggle, checks airplane mode). Not pass-through. ✓
- [x] **CHK004** No custom DSL/registry/plugin.
  - None. ✓

## New modules / packages

- N/A **CHK005..007** — no new gradle modules.

## New configuration

- [~] **CHK008** New config has a current FR consumer.
  - **Violation: `IconRef.Remote`** has no FR-006-emitting code path in spec 006. All bundled icons are `Bundled(name)`. The `Remote` variant exists only as forward-compat for spec 007/009 admin-custom tiles.
  - Other fields (toggle, version, schemaVersion, displayName) all have current FR consumers. ✓
- [x] **CHK009** Defaults + backward-compat documented.
  - `schemaVersion: 1` from first commit; backward-compat reader mandated by FR-006/FR-009. Toggle default per preset documented in FR-016. ✓

## CLAUDE.md rule 4 self-test

- [~] **CHK010** Test 1 — what's lost if inlined?
  - A1 `CapabilityRepository`: lost shared debounce, cold-start projection, single ConnectivityManager listener. Not optionality. ✓
  - A2 `HealthRepository`: same — shared state across consumers. ✓
  - **A5 `IconRef` sealed class**: if inlined to `iconRef: String` (bundled name only), nothing of value is lost in spec 006. The sealed shape is **only** for future Remote variant.
  - A7 `OfflineGraceWatcher`: it's the Worker class itself, not an extra wrapping. ✓
  - A8 Projections: lost cold-start fast path. Not optionality. ✓
- [x] **CHK011** Test 2 — swap cost if dependency deprecated.
  - DataStore → Preferences/SQLDelight: swap touches one Serializer file (≤ 1 day). Repository seam justified by runtime concerns (debounce, StateFlow), not storage. ✓
  - WorkManager → JobScheduler/AlarmManager: only A7 affected. ≤ 1 day. ✓

## Removal validation

- [~] **CHK012** Dangling references after `migrateLegacyAction` removal audited.
  - Grep audit completed earlier (15 files mention symbol):
    - **Code (delete):** `core/src/commonMain/.../ActionWireFormat.kt` (function + grep anchor `LEGACY-BRIDGE-EXPIRES-IN-SPEC-006`).
    - **Code (delete call site):** `core/src/androidMain/.../MockFlowRepository.kt` `parseAction()`.
    - **Tests (delete):** `core/src/androidUnitTest/.../ActionWireFormatFixtureTest.kt` (5 `fixture_legacy*` методов + adjust `fixture_directoryListedInReadme` expectation).
    - **Test (auto-flips to noop):** `core/src/androidUnitTest/.../LegacyMigrationExpiryTest.kt` — keep, оба `@Test` метода становятся green-and-noop after cleanup.
    - **Build script (delete constant):** `core/build.gradle.kts` `migrateLegacyActionDeadlineSpec`.
    - **Test fixtures (delete files):** 5 `core/src/commonTest/resources/fixtures/action-wire-format/legacy-spec003-*.json` + README update.
    - **Docs (update):** `docs/product/roadmap.md` (replace «Pre-requisite cleanup that lands with this spec» note → «Cleanup landed»).
    - **Specs (do not touch):** all `specs/005-*` files — historical documentation.
    - **Test resources README:** `core/src/commonTest/resources/fixtures/action-wire-format/README.md` — adjust list of files.
  - **Disposition:** explicit list above must seed tasks.md T001..T005 in speckit-tasks step. spec.md FR-017 currently mentions cleanup без explicit file list — pointer added in this checklist instead of expanding spec.
- [x] **CHK013** Concrete removal task in tasks.md.
  - FR-017 commits to «MUST be the first task in tasks.md (T001..T005)». tasks.md generated by speckit-tasks. ✓ Concrete commitment exists.

---

## Open items для speckit-clarify Step 4

1. **A5 IconRef premature abstraction (CHK008 + CHK010 violation):** ask the project owner to choose:
   - **(a)** `iconRef: String` (bundled name only); `IconRef` sealed class deferred to 007 → minimum violation today, but breaking wire-format change in 007 (must bump schemaVersion when Remote added).
   - **(b)** Sealed `IconRef = Bundled` only; wire-format already encodes discriminator `kind: "bundled"` → forward-compat wire-format, sealed class with one variant. Code-cost minimum, wire-format ready.
   - **(c)** Keep as drafted: `Bundled | Remote` sealed; wire-format ready; Remote код-путь без consumer'а в 006 → most extensible, biggest CHK008 violation.
   - **Recommendation:** **(b)** — wire-format готов к 007 без migration, code surface не несёт unused variant. Spec 006 §FR-007 reformulated: «`IconRef = Bundled(name); Remote variant added in spec 007». Wire-format spec stays with discriminator field.

## Itog

- 7 PASS, 2 PARTIAL (1 design choice for clarify, 1 documentation pointer to seed tasks.md), 4 N/A, 0 hard FAIL.
- **Verdict:** spec годится для speckit-plan после resolving A5 design choice in clarify Q-pass.

---

## TL;DR для нетехнического читателя *(добавит procedure-add-novice-summary)*

(Будет дописано на Step 5b.)
