# Implementation Plan: Bidirectional Config Sync

**Branch**: `008-bidirectional-config-sync`
**Date**: 2026-05-14
**Spec**: [spec.md](spec.md)
**Input**: post-clarify (Q1-Q10 resolved), all 10 checklists passed.

---

## Summary

Collaborative-editing wire format for `/links/{linkId}/config/current` (admin-side+Managed-as-editor write) with optimistic-concurrency check (`serverUpdatedAt`), unified diff/merge UI, and `/links/{linkId}/state/current` extension (Managed-side write) that tracks what was actually applied. Local persistence in Room (last-applied + pending-local-changes) survives process death. Reuses spec 007's `RemoteSyncBackend` / `PushReceiver` / Cloudflare Worker / Security Rules infrastructure — adds new domain types, new ports for local storage and lifecycle events, and 3 new wire-format contracts.

---

## Technical Context

**Language/Version**: Kotlin 2.0.20+ (KMP common; UUID via `kotlin.uuid.Uuid` stdlib).
**Primary Dependencies (new in 008)**:
- `androidx.room:room-runtime` + `room-compiler` (KSP) + `room-ktx` — local persistence (Android adapter).
- No new vendor SDKs. Reuses Firebase BoM (Firestore / Auth / Messaging) from spec 007.

**Storage**:
- Firestore documents `/links/{linkId}/config/current`, `/links/{linkId}/state/current` (wire formats, leave device).
- Room SQLite tables `LocalAppliedConfig`, `PendingLocalChanges` (local-only, per-link).

**Testing**: kotlin-test (common), JUnit + Robolectric (androidUnitTest), Compose StateRestorationTester (UI), Firebase Emulator (integration), Konsist (fitness functions). Same stack as 007.

**Target Platform**: Android API 30+ (project minSdk per 007). iOS out of scope (ADR-001 Platform Parity Gate — no iOS adapters today; commonMain code parity-ready for future iOS).

**Project Type**: KMP library + Android app (existing structure; no new gradle module — see Module Layout below).

**Performance Goals**:
- SC-004a: First frame from Room ≤ 650 ms p95 (Pixel 4a class) — inherits spec 007 SC-007 budget; Room read sub-budget ≤ 50 ms p95.
- SC-002: 4 independent refresh triggers (FCM / NetworkCallback / WorkManager 15min / RESUMED 2min throttle) — event-driven, no SLO.
- WorkManager 15-min cadence → ≤ 96 wakeups/day, well within Article IX §3 cap.

**Constraints**:
- APK delta from Room library: ~150-300 KiB; triggers TODO-ARCH-006 (R8 minification) check on top of spec 007's tight 3.99 MiB delta.
- No new runtime permissions (reuses INTERNET + ACCESS_NETWORK_STATE).
- One-way door: optimistic-concurrency model on `serverUpdatedAt` (see research.md §1).

**Scale/Scope**: Single link → max 50 contacts, max 10 flows × 10 slots = 100 elements, max 30 KiB document. Firestore hard-limit 1 MiB (OUT-008).

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Run**: 2026-05-14 via `procedure-constitution-check`. Full report: [constitution-check.md](constitution-check.md).

```
Gate 1 Architecture           : PASS  — no new modules, port-adapter pattern consistent with 007 baseline
Gate 2 Core/System Integration: PASS  — system events wrapped in ports; Article VI §6 documented in research.md §5
Gate 3 Configuration          : PASS  — schemaVersion present, backward-compat policy explicit, tests planned
Gate 4 Required Context Review: PASS  — constitution + ADRs + roadmap + compliance + 007 deps all linked
Gate 5 Accessibility          : PASS WITH EXCEPTION — FR-050 documented per Article VIII §7 (7-tap+password barrier)
Gate 6 Battery/Performance    : PASS  — explicit budgets, event-driven preferred, perf checkpoint planned
Gate 7 Testing                : PASS  — mock-first + roundtrip + integration + fitness; 8 levels mirror 007
Gate 8 Simplicity             : PASS  — no speculative abstractions; Tests 1 + 2 applied in research.md

OVERALL: 8 PASS (1 with documented exception), 0 FAIL, 0 N/A — plan is COMPLETE.
```

**FR-050 exception** (Gate 5) is **explicitly authorized by Article VIII §7** (single merge UI behind 7-tap+password barrier = cognitive filter; не «justified deviation», а documented constraint). No remediation required.

---

## Architecture

### Module map (existing structure, no new gradle modules)

```text
core/
├── src/commonMain/kotlin/com/launcher/
│   ├── api/
│   │   ├── config/                          ← NEW (this spec)
│   │   │   ├── ConfigDocument.kt            # domain data class (FR-003)
│   │   │   ├── ConfigDocumentWireFormat.kt  # kotlinx.serialization
│   │   │   ├── ConfigElement.kt             # sealed: Flow / Slot / Contact (FR-004 UUID id)
│   │   │   ├── ConfigDiff.kt                # value type + pure diff() function (FR-051)
│   │   │   ├── ConfigApplier.kt             # PORT (Managed apply, FR-021)
│   │   │   ├── ConfigEditor.kt              # PORT (save локально + push, FR-040/056)
│   │   │   ├── LocalConfigStore.kt          # PORT (Room-backed local persistence)
│   │   │   └── ConfigSyncError.kt           # sealed (extends BackendError categories)
│   │   ├── lifecycle/                       ← NEW
│   │   │   ├── NetworkAvailability.kt       # PORT (ConnectivityManager wrap)
│   │   │   └── AppForegroundEvents.kt       # PORT (Activity#onResume + throttle)
│   │   ├── sync/                            ← EXISTING (spec 007); 008 reuses, no changes
│   │   ├── push/                            ← EXISTING; 008 adds PushType.ConfigUpdated
│   │   └── ...
│   ├── core/preset/                         ← EXISTING; mock-storage cleanup (FR-045)
│   └── fake/
│       ├── config/                          ← NEW
│       │   ├── FakeLocalConfigStore.kt
│       │   ├── FakeConfigApplier.kt
│       │   └── FakeConfigEditor.kt
│       └── lifecycle/                       ← NEW
│           ├── FakeNetworkAvailability.kt
│           └── FakeAppForegroundEvents.kt
├── src/androidMain/kotlin/com/launcher/
│   ├── adapters/config/                     ← NEW (real adapters)
│   │   ├── RoomLocalConfigStore.kt
│   │   ├── RoomEntities.kt                  # @Entity (private, never leaked to commonMain)
│   │   ├── ConfigDocumentDao.kt             # @Dao
│   │   ├── ConfigSyncDatabase.kt            # @Database(version = 1)
│   │   ├── FirebaseConfigApplier.kt
│   │   └── DefaultConfigEditor.kt           # uses RemoteSyncBackend.runTransaction
│   ├── adapters/lifecycle/                  ← NEW
│   │   ├── ConnectivityManagerNetworkAvailability.kt
│   │   ├── ProcessLifecycleForegroundEvents.kt
│   │   └── ConfigRefreshWorker.kt           # WorkManager periodic (T3); single use site, no port
│   └── di/                                  ← EXISTING; extends Koin modules for 008 ports
└── src/commonTest/                          ← NEW tests
    ├── api/config/
    │   ├── ConfigDocumentWireFormatTest.kt  # roundtrip + backward-compat (FR-005)
    │   ├── ConfigDiffTest.kt                # FR-051..054 (auto-mergeable / no-op / conflict)
    │   └── ConfigApplierContractTest.kt     # idempotent apply, FR-023 self-as-writer skip
    └── ...

app/                                          ← EXISTING
├── src/main/kotlin/com/launcher/ui/
│   ├── settings/                            ← EXISTING; extended with FR-040 autosave UI
│   │   └── ...
│   ├── devicelist/                          ← EXISTING (spec 003); extended FR-046 pending badge
│   └── merge/                                ← NEW
│       ├── MergeScreen.kt                   # FR-050 unified UI
│       ├── MergeComponent.kt                # Decompose component
│       └── MergeViewModel.kt                # diff state + user resolution
└── src/main/AndroidManifest.xml             ← may extend (WorkManager init); no new permissions

push-worker/                                  ← EXISTING (spec 007 Cloudflare Worker)
└── src/                                     # extends: handle PushType.ConfigUpdated payload
```

**Konsist gates** (extend spec 007 Phase 10 pattern):
- `commonMain/api/config/` MUST NOT import `com.google.firebase.*`, `androidx.room.*`, `android.*`.
- `commonMain/api/lifecycle/` MUST NOT import `android.net.*`, `androidx.lifecycle.*`.
- `androidMain/adapters/config/` MUST NOT export Room entity types beyond adapter boundary.

### Port-adapter shape

| Port (commonMain) | Real adapter (androidMain) | Fake adapter (commonTest) | Consumer |
|---|---|---|---|
| `ConfigApplier` | `FirebaseConfigApplier` | `FakeConfigApplier` | Managed apply flow (FR-021/023), tests of higher-level code |
| `ConfigEditor` | `DefaultConfigEditor` | `FakeConfigEditor` | All editor flows (FR-040/056); tests |
| `LocalConfigStore` | `RoomLocalConfigStore` | `FakeLocalConfigStore` (in-memory map) | FR-041/042/044 |
| `NetworkAvailability` | `ConnectivityManagerNetworkAvailability` | `FakeNetworkAvailability` (programmable Flow) | FR-022 T2 |
| `AppForegroundEvents` | `ProcessLifecycleForegroundEvents` | `FakeAppForegroundEvents` (programmable Flow) | FR-022 T4 |

**No port for `WorkManager`** (single use site, no fake value per meta-minimization CHK010): direct Android-only `ConfigRefreshWorker` in `androidMain/adapters/lifecycle/`.

**No port for `kotlin.uuid.Uuid`** (stdlib pure-Kotlin since 2.0.20, no expect/actual needed per CHK016).

### Data flow — Push happy path (US-1)

```text
admin editor                                      Managed
   |                                                |
   | (autosave per change, debounce 300ms FR-056)   |
   v                                                |
[LocalConfigStore.writePendingChanges]              |
   |                                                |
   | (user taps "Отправить" — FR-040)               |
   v                                                |
[ConfigEditor.pushPending] -> RemoteSyncBackend.runTransaction {
   read /config/current → check serverUpdatedAt == snapshot.serverUpdatedAt
   if match → write new ConfigDocument (server-set serverUpdatedAt = serverTimestamp)
   else     → throw TransactionConflict → BackendError.TransactionConflict
}
   |
   | (Firestore ack — FR-015 spinner → "Отправлено ✓" indicator)
   |
   | Cloudflare Worker (FR-020):
   |   onWriteTrigger(/config/current) → FCM(topic=link-{linkId}, type=config.updated)
   v
                                                  FCM data-message received
                                                  → ConfigApplier.applyFromRemote()
                                                    read /config/current
                                                    persist to Room (atomic)
                                                    re-render launcher UI
                                                    write /state/current.appliedConfigUpdatedAt
                                                                |
                              admin sees "Применено на телефоне ✓" indicator (SC-001b)
                              when /state/current snapshot updated via observe()
```

### Data flow — Conflict (US-2)

```text
device A: snapshot serverUpdatedAt = T0 → write succeeds → server T1
device B: snapshot serverUpdatedAt = T0 → write → runTransaction fails (server now T1)
                                                ↓
device B: ConfigEditor catches TransactionConflict
                                                ↓
                                  read /config/current at T1
                                                ↓
                            ConfigDiff.compute(localPending, serverT1)
                                                ↓
                          if diff empty           → silently retry write at T1 (FR-052)
                          if non-overlapping      → merge UI "Применить оба?" default (FR-053)
                          if overlapping conflict → merge UI with per-element choices (FR-051)
                                                ↓
                          user resolves → re-read /config (now Tx) → retry write at Tx (FR-054)
                          (if another conflict → second round of merge UI)
```

### Data flow — Cold start (US-5, SC-004a/b)

```text
Application.onCreate (Koin init only; no Room access — lazy DAO)
   ↓
Activity#onCreate → ViewModel observes LocalConfigStore.appliedConfigFlow
   ↓
First frame (≤650ms p95) with last-applied-config from Room
   ↓
5 seconds after first frame (background coroutine in applicationScope, SC-004b)
   ↓
RemoteSyncBackend.readDoc(/config/current)
   ↓
if serverUpdatedAt > local appliedConfigUpdatedAt → ConfigApplier.applyFromRemote()
                                                  → re-render UI
```

---

## Data Model

See [data-model.md](data-model.md). New domain types:

- `ConfigDocument(schemaVersion, serverUpdatedAt, lastWriterDeviceId, presetId, flows: List<Flow>, contacts: List<Contact>)`
- `Flow(id: ElementId, slots: List<Slot>, ...)`
- `Slot(id: ElementId, ...)`
- `Contact(id: ElementId, phoneNumber, displayName, ...)`
- `ElementId(value: String)` — value class wrapping UUID v4 string (kotlin.uuid.Uuid)
- `ConfigDiff(added: List<ElementId>, removed: List<ElementId>, modified: List<ModifiedElement>)`
- `ModifiedElement(id, localValue, serverValue, fieldDiff: Map<String, Pair<Any?, Any?>>)`
- `PendingLocalChanges(linkId, snapshotServerUpdatedAt: Timestamp, draftConfig: ConfigDocument)`
- `LocalAppliedConfig(linkId, appliedConfig: ConfigDocument, appliedAt: Timestamp)`
- `ConfigSyncError` — sealed:
  - `Conflict(localDiff, serverDiff)` (extends FR-013 reject scenario)
  - `BackendFailure(cause: BackendError)`
  - `ApplyPartial(reasons: List<PartialReason>)`
  - `LocalStorageCorrupt(cause: Throwable)`

---

## Wire Formats

3 new/extended documents — each in [contracts/](contracts/):

| Wire format | File | Origin | Notes |
|---|---|---|---|
| `/links/{linkId}/config/current` | [contracts/config.md](contracts/config.md) | NEW in 008 | FR-001..006, FR-010..014, FR-020 |
| `/links/{linkId}/state/current` (extended) | [contracts/state-applied.md](contracts/state-applied.md) | NEW in 008; extends 007 `state-bootstrap.md` additive | FR-031..033 |
| FCM data-message `type=config.updated` | (inline in contracts/state-applied.md §FCM payload) | extends 007 `fcm-payload.md` | FR-020 |

Wire-format discipline (CLAUDE.md §5, FR-005/006):
- `schemaVersion: Int` first in every document.
- Roundtrip + backward-compat read tests required (`commonTest/resources/wire-format/*.json` fixtures).
- Additive-only changes per spec; rename/remove requires schemaVersion bump + reader-migration (deferred to future spec via OUT-006).

---

## Dependency Impact

### New dependencies (Android only — androidMain)

| Dependency | Version | Justification | Article XIII |
|---|---|---|---|
| `androidx.room:room-runtime` | 2.7+ | FR-041/042 local persistence | OK — AndroidX, stable, mandatory for local-first per Article XIV §4 |
| `androidx.room:room-ktx` | 2.7+ | Coroutines support for DAOs | OK |
| `androidx.room:room-compiler` (KSP plugin) | 2.7+ | @Database / @Entity / @Dao codegen | OK — build-time only |

### No new commonMain dependencies

UUID via `kotlin.uuid.Uuid` (stdlib 2.0.20+). Serialization via existing `kotlinx-serialization-json` from spec 007.

### APK delta budget

- Spec 007 baseline: realBackend delta = 3.99 MiB (SC-006 of 007 failed by 0.99 MiB; TODO-ARCH-006 R8 minification planned).
- Spec 008 addition: Room ~150-300 KiB → realBackend delta ≈ 4.15-4.30 MiB without R8.
- **Action**: spec 008 Phase 12 (perf-checkpoint) re-measures; TODO-ARCH-006 (R8 enable) should land before or during spec 008 to keep delta < 4 MiB.

---

## Test Strategy

Per CLAUDE.md §6 (mock-first) and §7 (fitness functions). 8 levels mirror spec 007:

| Level | What | Where |
|---|---|---|
| 1. Domain unit | ConfigDiff algorithm; element-by-element matching by ElementId; auto-resolve identical diffs | `commonTest/api/config/ConfigDiffTest.kt` |
| 2. Contract | Roundtrip + backward-compat reads for ConfigDocument, StateApplied | `commonTest/api/config/ConfigDocumentWireFormatTest.kt`, `StateAppliedWireFormatTest.kt` |
| 3. Fake-adapter | FakeLocalConfigStore behaviour parity with RoomLocalConfigStore (contract test) | `commonTest/fake/config/FakeLocalConfigStoreContractTest.kt` |
| 4. Firebase Emulator integration | Optimistic concurrency: T0/T0 conflict; Security Rules write authorization (admin+Managed); revoke deletes /config recursively | `androidUnitTest` or `androidInstrumentedTest` with Firebase Emulator |
| 5. Worker unit | `config.updated` payload generation in Cloudflare Worker | `push-worker/test/` |
| 6. UI Compose | MergeScreen state restoration (StateRestorationTester); pending badge visibility; spinner state transitions | `androidUnitTest` with Robolectric |
| 7. Fitness (Konsist) | commonMain config/* clean of Firebase/Room/android.*; lifecycle/* clean of android.net.*; Room entities don't leak | `core/src/test/kotlin/.../KonsistConfigSyncTest.kt` |
| 8. Smoke / manual | 2-device end-to-end: edit on device A → push → applied on device B; cold-start with Room; elderly walkthrough exempted per FR-050 | manual; documented in `smoke/008/` |

**Test fixtures as files** (wire-format checklist CHK012): `commonTest/resources/wire-format/`:
- `config-v1-minimal.json`, `config-v1-full.json`
- `state-applied-v1-bootstrap-only.json`, `state-applied-v1-full.json`
- `config-v0-synthetic.json` (for backward-compat read test)

---

## Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | **One-way door — optimistic concurrency on `serverUpdatedAt`** | — | High (changes wire format if revised) | Documented in research.md §1 with exit ramp; alternative (vector-clock / CRDT) cost-analyzed and rejected |
| R2 | Cold start regression — Room read on hot path | Medium | Medium (SC-004a fail) | Lazy DAO init; read on Dispatchers.IO; first-frame UI from in-memory StateFlow populated async; **Phase 12 macrobenchmark gates merge** |
| R3 | APK size delta exceeds budget | Medium | Medium | R8 minification (TODO-ARCH-006) MUST land in or before this spec; measure at Phase 12 |
| R4 | Merge UI complexity → bugs in conflict resolution | Medium | High (data loss for editor) | Extensive unit tests on ConfigDiff; integration tests for 5 acceptance scenarios (US-2 1-5); manual smoke with 2 devices |
| R5 | Pending changes orphaned when link revoked | Medium | Low | FR-034 recursive subtree delete; new action item: clear local Room pending for revoked linkId (security checklist) |
| R6 | Room corruption (rare) → app stuck | Low | High | catch SQLiteException at startup → wipe DB → fresh-state fallback; documented in plan §error-recovery |
| R7 | Future schema bump breaks existing Managed (forward-compat) | Medium | High | OUT-006 carves out `app-version-compatibility` spec; in 008 monorelease testing only |
| R8 | NetworkCallback delivers stale `onAvailable` events on some OEMs | Medium | Low | T3 (WorkManager 15min) and T4 (RESUMED) provide redundancy; documented per Article VI §6 |
| R9 | Cloudflare Worker rate limit / quota | Low | Medium | inherits 007 risk; TODO-ARCH-002 (Cloudflare KV) deferred for accurate rate-limiting |
| R10 | Merge UI not senior-safe-aware | Medium | Medium | Article VIII §7 documented exception (FR-050); 7-tap+password barrier as cognitive filter; quality bar still applies (CHK001-005) |
| R11 | Continuous autosave wears flash storage on Managed | Low | Low | Debounce 300ms; Room batches small writes; typical edit session = 10-50 writes |
| R12 | Diff algorithm complexity grows quadratic with element count | Low | Low | Element matching by id is O(n); diff is O(elements); typical n=50, no perf concern |

---

## Required Context Review

Per Article XII §7, the following docs were consulted (or are required to be consulted by implementers):

**Constitution & rules**:
- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — Articles I-XVI, especially VIII §7 (senior-safe exception), IX (battery), XI (anti-bloat), XII §7 (context review), XIV (privacy).
- [`CLAUDE.md`](../../CLAUDE.md) — rules 1, 2, 4, 5, 6.

**ADRs** (relevant subset):
- ADR-001 Platform Parity Gate — iOS out of scope today, commonMain stays parity-ready.
- ADR-004 Localization — string-resources extraction (CHK008/009 of ux-quality).
- ADR-005 UI Stack — Compose Multiplatform for MergeScreen.
- (No new ADR needed for 008 — optimistic concurrency on `serverUpdatedAt` is below ADR-threshold per Article XV §4.)

**Product**:
- [`docs/product/roadmap.md`](../../docs/product/roadmap.md) §008 (block lines 168-188) — feature scope.
- [`docs/product/senior-safe-launcher-plan.md`](../../docs/product/senior-safe-launcher-plan.md) — accessibility baseline for inherited surfaces.

**Compliance**:
- [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — update planned (008 adds no new permissions, reuses INTERNET + ACCESS_NETWORK_STATE).
- [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) — `TODO-ARCH-007` (app-version-compatibility), `TODO-ARCH-008` (config history+rollback in 009), `TODO-ARCH-009` (config size soft-limits) — all originated in 008 clarify.

**Spec 007 dependencies** (already in main):
- [`specs/007-pairing-and-firebase-channel/plan.md`](../007-pairing-and-firebase-channel/plan.md) — structural model.
- [`specs/007-pairing-and-firebase-channel/contracts/`](../007-pairing-and-firebase-channel/contracts/) — wire-format template.
- [`specs/007-pairing-and-firebase-channel/contracts/link.md`](../007-pairing-and-firebase-channel/contracts/link.md) §Subcollections — 008 fills `/config/current`.
- [`specs/007-pairing-and-firebase-channel/contracts/state-bootstrap.md`](../007-pairing-and-firebase-channel/contracts/state-bootstrap.md) — 008 extends additive.

---

## Implementation Phasing

Mirrors spec 007's 12-phase layout. Total estimate: 5-7 weeks (per Q2 clarify discussion).

| Phase | Goal | Gate to next phase |
|---|---|---|
| **0** | Env prep — Room dependencies added, Security Rules diff written + deployed to Firestore Emulator, fixtures committed | Emulator tests of new Security Rules pass |
| **1** | Domain types — ConfigDocument, Element, Diff, errors, ports defined (commonMain) | All commonTest unit tests for domain types green |
| **2** | Fake adapters + contract tests — Fake{LocalConfigStore, ConfigApplier, ConfigEditor, NetworkAvailability, AppForegroundEvents}; wire-format roundtrip + backward-compat tests | All contract-level tests green |
| **3** | Real adapters: Room — RoomLocalConfigStore + entities + DAO + ConfigSyncDatabase; cleanup of legacy mock-storage (FR-045) | RoomLocalConfigStore contract-test parity with fake; manual cleanup test |
| **4** | Real adapters: Firebase — FirebaseConfigApplier (uses RemoteSyncBackend.runTransaction for FR-013); DefaultConfigEditor with optimistic-concurrency | Firebase Emulator integration test: write A → read serverUpdatedAt, B writes with stale snap → conflict |
| **5** | Security Rules — extend /config write to admin+Managed; ensure /state write stays Managed-only; revoke recursively deletes /config (extends 007 FR-033) | Emulator security-rules test suite (admin write OK, Managed write OK, foreign uid denied) |
| **6** | Worker extension — Cloudflare Worker recognises `PushType.ConfigUpdated`, sends FCM to `link-{linkId}` topic | Worker unit test for config.updated dispatch |
| **7** | Lifecycle adapters: ConnectivityManager + ProcessLifecycle + WorkManager (T2/T3/T4 triggers) | Each trigger integration-tested in isolation; SC-002 4-trigger coverage |
| **8** | UI: editor flows — Settings autosave wiring (FR-040/056); pending banner (FR-047); discard confirmation (FR-057); device list pending badge (FR-046) | StateRestorationTester tests green; manual UI smoke |
| **9** | UI: MergeScreen — single unified UI (FR-050); per-element diff (FR-051); auto-merge default (FR-053); cancel preserves pending (FR-055) | All 5 US-2 acceptance scenarios pass; StateRestorationTester green |
| **10** | Konsist fitness gates — extend 007 Phase 10 patterns for config/* and lifecycle/* | Konsist tests green; vendor-import violations = 0 |
| **11** | In-process end-to-end test — multi-editor scenario in single test JVM (two FakeConfigEditor instances + FakeRemoteSyncBackend) | E2E test green for US-1, US-2, US-4 |
| **12** | Perf checkpoint + smoke + docs — macrobenchmark SC-004a; APK size measurement; manual 2-device smoke; novice summaries; novice TODO updates | All SC pass; perf-checkpoint.md committed |

**Push policy**: per CLAUDE.md §Branching — push after each Phase or significant step. Open PR as soon as Phase 0 has reviewable commit.

---

## Rollout / Verification

**Pre-merge gates** (must all pass):
1. All 8 SC measurable outcomes met (especially SC-004a macrobenchmark, SC-003 100% push-to-state, SC-005 wire-format tests).
2. Konsist fitness functions green (Phase 10).
3. Firebase Emulator integration tests green (Phase 4, 5).
4. Manual 2-device smoke documented in `smoke/008/README.md` (Phase 12).
5. APK delta < 4 MiB (with R8) — Phase 12 measurement; if fails — block until TODO-ARCH-006 lands.
6. `procedure-cross-artifact-trace` clean — all FRs ↔ tasks ↔ contracts ↔ acceptance scenarios mapped.
7. `/speckit.analyze` PASS — final consistency audit before implementation.

**Post-merge**:
- Update [`docs/product/roadmap.md`](../../docs/product/roadmap.md) §008 status to «Готов».
- Close `TODO-ARCH-009` review window (was 008 followup).
- Trigger planning for spec 009 `admin-mode-flows` (depends on 008).

---

## Complexity Tracking

Initial self-assessment: no Constitution gate failed. No complexity items to justify.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

(If `procedure-constitution-check` finds violations at Step 4, table will be filled.)
