# Checklist: performance

**Spec**: `spec.md` (rev. 2026-05-14, post-clarify Q1-Q10)
**Run**: 2026-05-14 — `/speckit.clarify` post-pass before `/speckit.plan`.

Verifies Article IX of constitution + ADR-005 performance targets + Android Vitals.

---

## Performance surface inventory

| # | Surface | Type | Frequency | Target |
|---|---|---|---|---|
| P1 | Cold start with Room read | Startup | Once per launch | SC-004a: ≤ 650 ms p95 |
| P2 | Post-startup `/config` fetch | Background (5s after first frame) | Once per launch | SC-004b: «через 5 секунд», no jank |
| P3 | FCM `config.updated` handler (T1) | Event-driven | Per admin push | Lightweight, sub-second apply |
| P4 | NetworkCallback `onAvailable` handler (T2) | Event-driven | Per network state change | Lightweight check |
| P5 | WorkManager periodic 15min (T3) | Background | Every 15 min | Brief check, ~few KB network |
| P6 | Activity#onResume throttled 2min (T4) | UI lifecycle | Per RESUMED if >2min since last | Brief check |
| P7 | Push UX feedback (FR-015 spinner) | UI animation | Per push | <100ms response |
| P8 | Merge UI render с diff | UI rendering | When conflict | <100ms even with large diffs |
| P9 | Room writes on autosave (FR-056) | I/O background | Every edit (debounce 300ms) | Off-main-thread |
| P10 | Diff algorithm computation (FR-051) | CPU domain | When conflict detected | <50ms for typical config (30 contacts) |

---

## Startup

- [x] **CHK001 — If feature affects cold/warm/hot start path: target time documented**

  ✅ SC-004a: `≤ 650 ms (p95)` for first frame с last-applied-config из Room. **Re-uses SC-007 спека 007** (consistent с ADR-005 HomeActivity ≤ 600ms — slight overhead допустим для Room read).

  **Watch**: 650ms — это **inherited бюджет** от 007. Если 007 SC-007 уже близок к лимиту (per memory `project_007_operational_state`, T108 perf check'point был сделан и delta 3.99 MB), нужно проверить **остаётся ли запас** на Room read. **Action для plan.md**: empirical measurement (microbenchmark) Room read of typical applied-config (30 contacts + flows), target <50ms (10% бюджета).

- [ ] **CHK002 — No new I/O / parsing / DI eagerness on the cold-start path without explicit budget**

  ⚠️ **Finding: NEW I/O on cold-start path — Room read.**

  - **New I/O**: Room database open + read of `LocalAppliedConfig` table.
  - **Budget**: spec.md не специфицирует. **Action для plan.md**: explicit budget «Room cold-start read ≤ 50 ms p95».
  - Mitigation candidates: (а) Room with lazy DAO init, (b) pre-warm Room на background thread в Application.onCreate (но это тоже cost), (c) Use `LiveData`/`Flow` `distinctUntilChanged` для UI binding.
  - **Best practice**: open Room *and* run read on background dispatcher; first-frame UI reads from in-memory StateFlow that's populated by Room read; show loading state ≤16ms (1 frame) если Room read not yet done. **Action для plan.md**.

- [x] **CHK003 — Init code added to Application.onCreate has documented cost in milliseconds**

  Spec.md не специфицирует Application.onCreate work. **Action для plan.md**:
  - Room DB init: **lazy** (creating DAO doesn't open DB; first query opens).
  - Firebase init: re-uses 007 (no new work).
  - Koin module bindings: add 008 module to existing setup; should be <5ms total.
  - **Watch**: не добавлять `runBlocking { dao.readAppliedConfig() }` в Application.onCreate.

## Runtime — frames

- [x] **CHK004 — Scrolling surfaces have target frame budget**

  008 introduces:
  - Settings UI (extends spec 003 / 009 patterns).
  - Merge UI (new screen).
  - Main screen pending-badge in device list (FR-046 — small UI element).

  Spec.md не специфицирует frame budget. **Inherits** ADR-005 «0 dropped frames on Pixel 4a class» implicitly. **Action для plan.md**: confirm merge UI render performance with realistic config (30 contacts, 5 flows) — no dropped frames during scroll.

- [x] **CHK005 — Animations duration documented; >300ms animations justified**

  Spec.md mentions:
  - FR-015 «крутящийся спиннер» (push UX feedback).
  - SC-001b «значок применено бабушкой ✓» (appearance animation).

  No explicit durations. **Action для plan.md**: defaults — spinner 1.5s rotation period (standard Material), state transitions ≤200ms. **Inherits** project animation conventions.

- [x] **CHK006 — No synchronous network/disk on the main thread**

  Spec.md не явно forbid'ит main-thread I/O, но через `RemoteSyncBackend` (suspend functions) + Room (suspend DAOs) — это **structurally enforced**. ✅

  **Action для plan.md**: явное правило — все Firestore / Room operations через `Dispatchers.IO` или suspend functions. No `runBlocking` в UI code.

## ANR risk

- [x] **CHK007 — Operations triggered by user input that block main thread > 100ms identified**

  Risk points:
  - **Autosave (FR-056)**: each edit triggers Room write. With debounce 300ms — Room writes happen every 300ms during typing, off-main per Room contract. ✅
  - **Save локально + push button** (FR-040): launches coroutine, не blocks. UI shows spinner immediately (FR-015). ✅
  - **Diff computation (FR-051)** at merge UI open: domain function, fast enough for <50ms target on typical config. **Watch**: if config grows to 200+ elements — diff может приближаться к 16ms frame budget. Должно быть pure-CPU (no I/O), runs on `Dispatchers.Default`.

- [x] **CHK008 — BroadcastReceiver onReceive bodies stay short**

  - FCM receiver (inherited from 007): onReceive should just dispatch to coroutine, <10ms. **No new BroadcastReceivers in 008**.
  - NetworkCallback `onAvailable` (T2, FR-022): not a BroadcastReceiver, it's a callback. Similar discipline: dispatch to coroutine, no work in callback itself. **Action для plan.md**.

## Background work

- [ ] **CHK009 — Each new background task is justified per Article IX §2**

  ⚠️ **Watch: 4 triggers (T1-T4) need explicit justification per Article IX §2.**

  Spec 008 introduces:
  - **T1 FCM listener** (FR-022 T1): inherited from 007. ✅ Justified by US-1, US-2.
  - **T2 NetworkCallback** (FR-022 T2): NEW. Justified by F1 mitigation (offline → online recovery). Battery cost: ~zero (Android system delivers events).
  - **T3 WorkManager periodic 15min** (FR-022 T3): partially NEW (007 had stub). Justified by no-GMS fallback + general resilience. Battery cost: 1 wakeup per 15min = ~96 wakeups/day. Per Article IX §3 «excessive wakeups <10/hr» — 4/hr is well under cap.
  - **T4 Activity#onResume throttled 2min** (FR-022 T4): NEW. No background cost (only fires when launcher visible). Justified by «catch newly-pushed config without waiting 15min».
  - **Push to `/config`** (FR-010, FR-040 «push на сервер»): user-initiated, transient. Lives в `applicationScope` (per state-management checklist action item). Per-push cost: ~one Firestore write.
  - **State write back** (FR-030): triggered after apply; transient.
  - **Room autosave (FR-056)**: triggered by user edit; debounced 300ms; runs on Dispatchers.IO.

  All justified, но spec.md не делает this **explicit**. **Action для plan.md**: явная таблица «Background task → Justification per Article IX §2».

- [x] **CHK010 — Polling explicitly avoided or justified; event-driven preferred per Article IX §3**

  ✅ **Excellent**: spec 008 fundamentally **event-driven**:
  - FCM push (T1) — event-driven by admin write.
  - NetworkCallback (T2) — event-driven by OS.
  - Activity#onResume (T4) — event-driven by user.
  - Only **T3 WorkManager 15min** is polling — explicitly justified as **fallback for no-GMS / network event gaps**.

  Per Article IX §3 «event-driven preferred» — fully consistent.

  **Action для plan.md**: подтвердить, что T3 frequency 15min == 007 FR-018 (consistency).

- [ ] **CHK011 — Each event listener documents source, frequency, threading, expected battery cost, fallback if delayed/absent (Article VI §6)**

  ⚠️ Spec.md describes triggers (FR-022) but не делает **structured table** per Article VI §6.

  **Action для plan.md** (mandatory): создать таблицу в plan.md / research.md:
  ```
  | Listener | Source | Frequency | Thread | Battery cost | Fallback if absent |
  |---|---|---|---|---|---|
  | FCM /config.updated | FCM service | Per admin push | dispatch to IO | ~0 (system) | T2/T3/T4 |
  | NetworkCallback.onAvailable | ConnectivityManager | Per network change | dispatch to IO | ~0 (system) | T3 fallback |
  | WorkManager periodic | WorkManager | 15 min | WorkManager thread | <0.1% / day | T1/T2/T4 |
  | Activity#onResume (throttled) | Lifecycle | RESUMED + >2min | Main → dispatch IO | ~0 (user-bound) | T1/T2/T3 |
  ```

## Memory

- [x] **CHK012 — New caches have explicit size limit and invalidation rule (Article IX §6)**

  New caches in 008:
  - **Room `LocalAppliedConfig`**: one row per linkId; size bounded by Firestore /config size (≤ 1 MiB). Invalidation: overwrite on apply.
  - **Room `PendingLocalChanges`**: one row per linkId; same size bound. Invalidation: cleared on successful push (FR-013 acknowledgment).
  - **In-memory `StateFlow<ConfigDocument>`**: same content as Room. Single instance. Invalidation: re-derives from Room.

  ✅ All bounded. No unbounded growth.

- [x] **CHK013 — Bitmap/image loading uses configured memory budget**

  008 not introducing новые bitmap loading. Contact photos → spec 011 (e2e media). **N/A для 008**.

- [x] **CHK014 — No long-lived references holding Activity/Context (leak risk)**

  Spec.md не описывает Activity references. **Action для plan.md**: convention — `applicationContext` only in adapters; ViewModels не keep Activity. Standard pattern, inherited.

## APK / binary size

- [ ] **CHK015 — New dependencies vs ADR-005 budget**

  ⚠️ **Action for plan.md**: 008 adds:
  - **Room library** (`androidx.room:room-runtime` + `room-ktx` + compiler) — ~150-300 KB compiled.
  - **kotlinx.uuid** (если решим использовать вместо `kotlin.uuid.Uuid` из stdlib 2.0.20+) — ~50 KB or 0 if stdlib.
  - No new vendor SDKs.

  Per memory `project_007_operational_state`: 007 SC-006 fail by 0.99 MB (delta 3.99 MB SI, target 3.00 MB). **R8 minification (TODO-ARCH-006)** would compensate.

  **Action для plan.md**: recalculate APK delta after Room addition; trigger TODO-ARCH-006 (R8 enable) если не сделано.

- [x] **CHK016 — Native libraries / large assets justified**

  008 не вводит native libs или large assets. **N/A**.

## Measurement

- [ ] **CHK017 — If perf target is set: measurement method documented**

  Spec.md SC's mention 650ms (SC-004a) but не specify measurement method.

  **Action для plan.md**:
  - SC-004a: macrobenchmark с `MeasureCriterion.StartupCriterion` (как в 007 Phase 11 perf-checkpoint pattern).
  - SC-001 spinner timing: manual UX validation + unit test on state machine.
  - SC-002 4-trigger coverage: integration tests per trigger.

- [ ] **CHK018 — Perf checkpoint planned in tasks.md (perf-checkpoint.md output)**

  ⚠️ Spec 008 **must** include perf checkpoint phase, mirror of спек 007 Phase 11.

  **Action для tasks.md**:
  - Phase 11 (или соответствующая) — `perf-checkpoint.md` с macrobenchmark results.
  - Measurements: cold start delta vs 007 baseline; APK size delta; battery cost для WorkManager 15min.

## Battery (Android Vitals)

- [x] **CHK019 — No new wake locks without explicit justification and timeout**

  008 не вводит explicit wake locks. WorkManager автоматически берёт wake lock для своей работы (короткий, OS-managed). FCM service — short wake lock managed by Firebase. ✅

- [x] **CHK020 — No new alarms / Doze-bypassing scheduling**

  WorkManager periodic 15min — Doze-aware (Android system delays during Doze if needed). No `setExactAndAllowWhileIdle` или similar. ✅ Per Article IX §4.

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 14 | CHK001, CHK003, CHK004, CHK005, CHK006, CHK007, CHK008, CHK010, CHK012, CHK013, CHK014, CHK016, CHK019, CHK020 |
| ⚠️ Watch / Plan.md action | 6 | CHK002 (Room cold-start budget), CHK009 (background task justification table), CHK011 (event listener table), CHK015 (APK delta recalc), CHK017 (measurement methods), CHK018 (perf checkpoint task) |
| ❌ Fail | 0 | — |

**Verdict: PASS at spec-level with critical plan.md follow-ups.**

Spec 008 is **fundamentally event-driven** (4 triggers per FR-022) с одним justified polling fallback (T3 WorkManager 15min). Battery profile excellent: <10 wakeups/hour (well within Article IX cap). Critical perf risks: cold start path с Room read (SC-004a budget 650ms), и APK size delta (Room ~150-300 KB on top of 007's already-tight 3.99 MB delta).

---

## Mandatory action items для plan.md

1. **Cold-start budget table** (CHK002): explicit Room read budget ≤ 50 ms p95; Application.onCreate cost <5 ms.

2. **Background task justification table** (CHK009): per Article IX §2, для каждого из 4 triggers + push + state write + autosave.

3. **Event listener table** (CHK011): per Article VI §6, structured table с source / frequency / thread / battery / fallback.

4. **APK delta recalculation** (CHK015): post-Room addition; trigger TODO-ARCH-006 (R8 minification) если ещё не сделано.

5. **Measurement methods** (CHK017): explicit macrobenchmark / instrumentation tests для каждого SC с числовым target (SC-004a primarily).

6. **Perf checkpoint phase** (CHK018): mirror спек 007 Phase 11 — `perf-checkpoint.md` с measured results перед merge.

## Recommended add to spec.md (optional)

Spec.md **could** добавить one performance NFR explicitly mentioning Room budget:

> **NFR-001 (Room cold-start budget)**: Read of `LocalAppliedConfig` from Room MUST complete < 50 ms p95 на reference hardware (Pixel 4a class), чтобы не нарушить SC-004a общий бюджет 650 ms.

Это explicit-ifies CHK002 watch item. Но: this is **plan.md territory** (specific budget for one component). **Recommendation**: оставить в spec.md только SC-004a (общий 650ms target), детализацию в plan.md. **No spec.md edit needed.**

**No spec.md edits required.**
