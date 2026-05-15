# Checklist: performance

**Spec**: `spec.md` (rev. 1 — 2026-05-15, pre-`/speckit.plan`)
**Run**: 2026-05-15 — `/speckit.clarify` post-pass before `/speckit.plan`.

Verifies Article IX of constitution + ADR-005 performance targets + Android Vitals.

---

## Performance surface inventory

| # | Surface | Type | Frequency | Target / Risk |
|---|---|---|---|---|
| P1 | Cold start admin app (Managed list + health badges) | Startup | Once per launch | Inherits 007 SC-007 (≤ 650 ms p95) + new query batch |
| P2 | Firestore `/links/{linkId}/health` listener (Warning/Critical) | Realtime listener | While editor screen open | FR-020: realtime когда экран открыт; closed → unsubscribe |
| P3 | Firestore `/links/{linkId}/health` poll (Info severity) | Polling | 30 sec while screen open | FR-020: 30 sec cadence Info; SC-002 ≤ 35 sec for Critical detection |
| P4 | Drag-and-drop tile reorder (FR-008) | UI gesture | Per user drag op | **16ms frame budget** (Compose 1.6 `Modifier.dragAndDropSource/Target`) |
| P5 | LazyVerticalGrid recompose during drag (FR-005a) | UI rendering | Per drop / draft mutation | 0 dropped frames Pixel 4a class (ADR-005) |
| P6 | VCard parser (FR-028) | CPU one-shot | Per share-intent | **10 KB max payload, < 100 ms parse** (DoS guard) |
| P7 | Local draft write to Room `PendingLocalChanges` (FR-014a) | I/O background | Per edit (likely debounced) | Inherits 008 autosave pattern (off-main) |
| P8 | History list read `/config/history` (10 items) (FR-039) | I/O network | When user opens "История" | Sub-second; cache acceptable |
| P9 | Snapshot preview render (FR-040) | UI rendering | Per snapshot tap | Same pipeline as editor; no jank |
| P10 | Rollback push (FR-041) → goes through 008 push flow | I/O network | Per rollback | Inherits 008 push budget |
| P11 | Contacts picker → `Contact.fromRaw()` validation | CPU one-shot | Per pick | < 10 ms (regex + trim) |
| P12 | OpenApp dispatcher resolve (`packageManager.queryIntentActivities`) | I/O on-device | Per Managed tile tap | < 50 ms; cached package availability |
| P13 | `PhoneHealthCriticalEvent` emit (FR-021) | Event-driven | Per severity transition | Local-only emit, no subscriber in 009 (deferred SRV-MONITOR-001) |

---

## Startup

- [x] **CHK001 — If feature affects cold/warm/hot start path: target time documented**

  Spec.md не вводит explicit cold-start target для 009 — наследуется от спека 007 (SC-007 ≤ 650 ms p95) и спека 008 (Room read ≤ 50 ms).

  **Risk**: список Managed-устройств (FR-001) с 4 health-индикаторами на каждом устройстве может потребовать дополнительного Firestore read (`/links/{linkId}/health` для каждого Managed). Если у admin'a 3 paired Managed → 3 paralleled health reads на старте.

  **Action для plan.md**: explicit budget «Managed list query + health snapshot read ≤ 100 ms p95 для 3 Managed; параллельно через Firestore SDK». Если synced cache есть — должно быть instant.

- [ ] **CHK002 — No new I/O / parsing / DI eagerness on the cold-start path without explicit budget**

  ⚠️ **Finding: 009 inherits Room cold-start от 008 (read of `LocalAppliedConfig` + `PendingLocalChanges`), плюс может добавить query `LinkRepository.listPairedManaged()`.**

  - Spec.md не специфицирует budget для admin-list query.
  - **Action для plan.md**: explicit бюджет «list paired Managed (Room or Firestore offline cache) ≤ 30 ms p95»; Firestore listener subscriptions создаются **lazily** при open редактора, не на cold start.

- [x] **CHK003 — Init code added to Application.onCreate has documented cost in milliseconds**

  009 не вводит нового init кода в `Application.onCreate` (наследуется от 007/008). Drag-and-drop infra (`Modifier.dragAndDropSource/Target`) — Compose runtime, не Application init.

  **Action для plan.md**: подтвердить, что VCard `<intent-filter>` (FR-027) — pure manifest decl, без runtime init cost.

## Runtime — frames

- [ ] **CHK004 — Scrolling surfaces have target frame budget**

  ⚠️ **Critical surface**: `FlowScreen` + `LazyVerticalGrid` плиток в edit-режиме с **drag-and-drop overlays** (FR-005a + FR-008).

  Spec.md не специфицирует frame budget явно — наследует ADR-005 «0 dropped frames Pixel 4a class» implicitly.

  **Risk areas**:
  - **Drag-and-drop hover state** — `Modifier.dragAndDropTarget` пересчитывает `dropTargets` при каждом hover событии. На LazyVerticalGrid с 12 tiles это может вызвать ре-композицию каждой `TileCard` на каждый drag-frame → 16ms frame budget под угрозой.
  - **Cross-flow drag** (FR-008): visualизация показывает плитку «выпавшую» из текущей flow → в `BottomFlowBar` показывается tab активной flow → потенциально дорогой layout pass.
  - **SC-006 «95% сценариев без падений»** — это functional success criterion, **не frame budget**.

  **Action для plan.md** (mandatory):
  1. Explicit «drag-and-drop 16 ms frame budget, measured via systrace на Pixel 4a class».
  2. **Two-way door fallback** (FR-008 inline-TODO): если built-in API ломает frame budget → ручная реализация через `Modifier.pointerInput` с throttled position update.
  3. Microbenchmark на recompose cost `TileCard(editMode = true)` при изменении draft state.

- [x] **CHK005 — Animations duration documented; >300ms animations justified**

  Spec.md mentions:
  - Long-press → drag (FR-008): standard Android pattern, ~500 ms long-press timeout (default Compose `detectDragGesturesAfterLongPress`).
  - Drop animation (snap to position): не специфицирован, defaults Material ~200-300 ms.
  - Health severity color transition (FR-018 Info→Warning→Critical при изменении значения): не специфицирован.

  **Action для plan.md**: defaults — drop animation ≤ 200ms; severity color transition ≤ 150ms (avoid distracting "blinking"). >300ms — только long-press detection (standard).

- [x] **CHK006 — No synchronous network/disk on the main thread**

  Spec.md не явно forbid'ит, но architecture pattern (inherits от 007/008) обеспечивает:
  - Firestore SDK: `suspend` functions + `Flow` (kotlinx).
  - Room: `suspend` DAOs.
  - VCard parser (FR-028) — должен быть `Dispatchers.Default` (CPU-bound), не main.

  **Action для plan.md**: VCard parsing on `Dispatchers.Default`; show progress UI ≤ 16ms если parsing > 1 frame.

## ANR risk

- [x] **CHK007 — Operations triggered by user input that block main thread > 100ms identified**

  Risk points:
  - **Drag-and-drop** (FR-008): inherent latency in `Modifier.dragAndDropSource` is OS-managed; risk = our `onDrop` callback. **Action для plan.md**: `onDrop` callback должен быть < 16ms (только mutate `StateFlow<DraftConfig>`), Room write — async через `launch { dao.upsertDraft(...) }`.
  - **VCard parse** (FR-028): up to 10 KB, target < 100ms. Must be off-main (CPU-bound on `Dispatchers.Default`).
  - **Rollback preview** (FR-040): rendering snapshot — same Compose pipeline as editor, fast enough.
  - **History list query** (FR-039): Firestore subcollection read of 10 docs — typically < 200ms, must be off-main; UI shows loading state.

- [x] **CHK008 — BroadcastReceiver onReceive bodies stay short**

  009 не вводит новых BroadcastReceivers (intent-filter в FR-027 для `ACTION_SEND` — это **Activity intent-filter**, не Broadcast). VCard share opens Activity, parsing — в Activity scope, dispatched to `Dispatchers.Default`.

## Background work

- [ ] **CHK009 — Each new background task is justified per Article IX §2**

  ⚠️ **Watch: 009 introduces новые background surfaces:**

  - **Firestore listener на `/links/{linkId}/health`** (FR-020): realtime listener для Warning/Critical когда **экран открыт**; для Info — 30-sec poll. **Lifecycle-bound** — закрывается при `onPause`. ✅ Justified by US-2 (admin узнаёт о Critical в ≤ 35 сек).
  - **Battery cost realtime listener**: Firestore listener держит persistent WebSocket-ish connection через Firestore SDK. Per Google SDK docs, idle cost minimal, активный update ~1 KB. Закрывается при экран ушёл → no background cost. ✅
  - **30-sec poll** (FR-020 Info severity): polling, **не event-driven**. **Justified by**: Info severity update не критичен, exit ramp на Firestore listener если 30-sec poll окажется heavy. **Watch**: Article IX §3 «event-driven preferred» — полезнее перейти на Firestore listener даже для Info (одинаковый API, severity вычисляется client-side). **Recommendation для plan.md**: рассмотреть унификацию — listener всегда когда экран открыт, severity discrimination — local computation.
  - **Push admin при Critical (FR-021 `PhoneHealthCriticalEvent`)**: emitted локально, **подписчика нет** (deferred SRV-MONITOR-001). Нет background cost в 009. Future push subscriber должен учитывать Android Doze/battery saver — **TODO-ARCH-012**.

  **Action для plan.md**: явная таблица «Background task → Justification per Article IX §2».

- [ ] **CHK010 — Polling explicitly avoided or justified; event-driven preferred per Article IX §3**

  ⚠️ **Finding: 30-sec poll для Info severity (FR-020) — это polling.**

  Justification в спеке: «Info — pull раз в 30 сек когда экран открыт».
  - **Argument против polling**: Firestore listener даёт events для всех severity-уровней — нет смысла polling'ить Info отдельно. Severity вычисляется **client-side** через `DEFAULT_PHONE_HEALTH_PRESET` thresholds (FR-018).
  - **Argument за polling**: меньше event-traffic для Info (большинство времени).
  - **Real cost**: Firestore listener идёт по persistent connection, marginal cost per event ~zero. 30-sec poll создаёт 2 RPC/min vs listener — 1 RPC при изменении.

  **Recommendation для plan.md**: унифицировать — Firestore listener всегда когда экран открыт; severity discrimination — local. Это убирает polling, упрощает код. Exit ramp — если listener окажется costly (нереально), вернуть 30-sec poll.

- [ ] **CHK011 — Each event listener documents source, frequency, threading, expected battery cost, fallback if delayed/absent (Article VI §6)**

  ⚠️ Spec.md описывает FR-020 поведение, но не делает **structured table** per Article VI §6.

  **Action для plan.md** (mandatory): создать таблицу:
  ```
  | Listener | Source | Frequency | Thread | Battery cost | Fallback if absent |
  |---|---|---|---|---|---|
  | /links/{linkId}/health listener | Firestore SDK | Per update, while screen open | dispatch to IO | ~0 (system) | Manual pull-to-refresh |
  | Info 30-sec poll (или унифицировать) | Coroutine `delay(30s)` | 2/min while screen open | IO | <1% over 10min | Lifecycle close on `onPause` |
  | VCard ACTION_SEND intent | Android share sheet | Per user share | Main → IO | ~0 (user-bound) | N/A (user explicit) |
  | drag-and-drop callback | Compose UI | Per gesture | Main | ~0 (user-bound) | Ручная кнопка "···" (FR-009) |
  ```

## Memory

- [x] **CHK012 — New caches have explicit size limit and invalidation rule (Article IX §6)**

  New caches/state в 009:
  - **`DraftConfig` Room (FR-014a)**: переиспользует `PendingLocalChanges` table из 008. Per-Managed (один draft на linkId). Size bound — config ≤ 1 MiB. Invalidation: cleared on successful push.
  - **History snapshots cache** (FR-036, FR-038): retention 10 на сервере. Локально — Firestore SDK кэш (managed by SDK, no explicit limit needed). ✅
  - **Local `PhoneHealthIndicator` state**: derived from `Health` snapshot, single instance per Managed. Bounded.
  - **Contacts list (FR-033a)**: list view of `/config.contacts[]` across all Managed — bounded by config size.

  ✅ All bounded.

- [x] **CHK013 — Bitmap/image loading uses configured memory budget**

  009 не вводит bitmap loading. `photoRef = null` для contacts (FR-026 — фото отложено на spec 011). OpenApp tile иконки (FR-046) — `packageManager.getApplicationIcon()` returns `Drawable`, low memory cost; cache via Coil 3 default policy. ✅

- [x] **CHK014 — No long-lived references holding Activity/Context (leak risk)**

  Spec.md не описывает Activity refs. **Action для plan.md**: VCard share intent Activity должен dispatch payload to ViewModel и не удерживать reference на raw Intent после parse. Standard pattern.

## APK / binary size

- [ ] **CHK015 — New dependencies vs ADR-005 budget**

  ⚠️ **Action for plan.md**: 009 adds:
  - **No new vendor SDKs** (drag-and-drop через Compose 1.6+ built-in, который уже в проекте).
  - **VCard parser** — handwritten lightweight regex-based parser в `VCardImportAdapter` (per FR-028 strict subset: only FN + TEL, ignore everything else). ~5 KB код, no library dependency.
  - **Compose drag-and-drop** — built-in Compose 1.6, no extra dep.
  - **ContactsContract** — Android SDK, no extra dep.

  Per memory: 007 SC-006 fail by 0.99 MB; 008 adds Room (~150-300 KB). **R8 minification (TODO-ARCH-006)** должен быть включён до 009 если ещё не сделано.

  **Action для plan.md**: recalculate APK delta after 009 features; minimal new deps but verify VCard parser footprint < 5 KB.

- [x] **CHK016 — Native libraries / large assets justified**

  009 не вводит native libs / large assets. ✅

## Measurement

- [ ] **CHK017 — If perf target is set: measurement method documented**

  Spec.md SCs mention:
  - SC-001 «≤ 90 секунд» (user task time, manual measurement).
  - SC-002 «≤ 35 сек» (Critical detection — combination listener latency + UI update).
  - SC-003 «≤ 60 сек» (VCard share flow, manual).
  - SC-004 «≤ 60 сек» (rollback, manual).
  - SC-006 «95% сценариев drag-and-drop без падений» — functional, не perf.

  None of these are frame-budget или battery measurements.

  **Action для plan.md** (mandatory):
  - SC-002: integration test измеряет от Firestore mock write до UI severity update; target ≤ 35 сек (включает 30-sec poll worst case или listener latency).
  - Drag-and-drop frame budget: macrobenchmark с `BaselineProfile` + `FrameTimingMetric` на Pixel 4a class; target 0 dropped frames during 10-tile reorder.
  - VCard parse benchmark: microbenchmark на 10 KB payload; target < 100 ms.
  - Battery: WorkManager / Firestore listener cost — measured через Android Studio Energy Profiler на 10-min session.

- [ ] **CHK018 — Perf checkpoint planned in tasks.md (perf-checkpoint.md output)**

  ⚠️ Спек 009 **must** include perf checkpoint phase, mirror спека 007/008 Phase 11.

  **Action для tasks.md**:
  - Final phase — `perf-checkpoint.md` с macrobenchmark results.
  - Measurements: drag-and-drop frame budget (Pixel 4a); VCard parse latency (10 KB); cold start delta vs 008 baseline; APK size delta; Firestore listener battery cost (10-min open editor session).
  - Особенно: **drag-and-drop frame budget gate** — если 0 dropped frames не достигается через Compose built-in API, активировать FR-008 two-way door fallback (`Modifier.pointerInput`).

## Battery (Android Vitals)

- [x] **CHK019 — No new wake locks without explicit justification and timeout**

  009 не вводит explicit wake locks. Firestore listener / WorkManager — system-managed. ✅

- [x] **CHK020 — No new alarms / Doze-bypassing scheduling**

  009 не вводит alarms. Future push-admin-on-Critical (FR-021 subscriber, deferred SRV-MONITOR-001) **должен учитывать Android Doze + battery saver** — это **inline-TODO в backlog**, не часть 009.

  **Note для будущего SRV-MONITOR-001**: FCM high-priority push для critical alerts может обойти Doze (legitimate use case "user-perceptible alert about a contact's emergency"); но frequency должна быть rate-limited (max 1 push/час на Managed) для соответствия Android Vitals.

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 12 | CHK001, CHK003, CHK005, CHK006, CHK007, CHK008, CHK012, CHK013, CHK014, CHK016, CHK019, CHK020 |
| ⚠️ Watch / Plan.md action | 8 | CHK002 (cold-start Managed list budget), CHK004 (**drag-and-drop frame budget — critical**), CHK009 (background task table), CHK010 (30-sec poll vs unified listener), CHK011 (event listener table), CHK015 (APK delta), CHK017 (measurement methods), CHK018 (perf checkpoint task) |
| ❌ Fail | 0 | — |

**Verdict: PASS at spec-level with critical plan.md follow-ups, especially drag-and-drop frame budget.**

Спек 9 имеет три perf-чувствительные surface'а:

1. **Drag-and-drop frame budget (CHK004)** — критический: `Modifier.dragAndDropSource/Target` на LazyVerticalGrid с recompose-able TileCards в edit-режиме. Два-way door fallback (`pointerInput`) уже в спеке (FR-008), но frame budget должен быть measured early в имплементации.
2. **VCard parser DoS guard (FR-028)** — adequate в спеке (10 KB limit, regex on `Dispatchers.Default`), но measurement method (CHK017) не специфицирован.
3. **Firestore listener battery cost** — adequate в спеке (lifecycle-bound, закрывается при `onPause`), но **polling vs listener унификация** (CHK010) может упростить и улучшить.

Push admin при Critical (FR-021) — **deferred** в спек server-side (SRV-MONITOR-001); battery/Doze concerns explicitly **out of scope** для 009.

---

## Top 3 Performance Risks

1. **Drag-and-drop frame budget breach (FR-008)** — самый высокий риск. Compose 1.6 `Modifier.dragAndDropSource/Target` — относительно новый API; cross-flow drag + recompose of TileCards (после FR-005a добавил `editMode` param) может превысить 16ms frame. **Mitigation**: early macrobenchmark; two-way door fallback на `pointerInput` уже зашит в FR-008.

2. **30-sec polling для Info severity (FR-020)** — мелкая, но архитектурная: Article IX §3 предпочитает event-driven. **Mitigation**: унифицировать на Firestore listener всегда (severity discrimination — client-side); упрощает код и улучшает соответствие конституции.

3. **VCard parse DoS via large payload** — частично mitigated в FR-028 (10 KB limit, UTF-8 only). **Risk**: regex backtracking на malicious crafted VCard под 10 KB может занять > 100 ms. **Mitigation**: use linear-time parser (no nested quantifiers in regex), measured benchmark.

---

## Mandatory action items для plan.md

1. **Cold-start budget** (CHK001/002): Managed list query + parallel health reads ≤ 100 ms p95; lazy listener subscription (only on editor open).

2. **Drag-and-drop frame budget gate** (CHK004): explicit 16 ms target на Pixel 4a class; macrobenchmark с `FrameTimingMetric`; gate в perf-checkpoint phase; ready to flip to `pointerInput` fallback if failed.

3. **Background task justification table** (CHK009): per Article IX §2 — Firestore listener, Info poll, VCard intent, drag callback.

4. **Polling vs listener unification** (CHK010): обсудить отказ от 30-sec Info poll в пользу единого Firestore listener; severity discrimination — client-side через `DEFAULT_PHONE_HEALTH_PRESET`.

5. **Event listener table** (CHK011): per Article VI §6, structured table с source / frequency / thread / battery / fallback.

6. **APK delta recalculation** (CHK015): минимальные новые deps (VCard handwritten parser, Compose built-in DnD); verify R8 включён.

7. **Measurement methods** (CHK017): macrobenchmark для drag-and-drop; microbenchmark для VCard parse (10 KB worst case); Energy Profiler для listener.

8. **Perf checkpoint phase в tasks.md** (CHK018): mirror спека 007/008; specific gates — drag-and-drop frame budget + VCard parse latency.

## Recommended spec.md edits (optional)

Spec.md **could** добавить два явных NFR:

> **NFR-001 (Drag-and-drop frame budget)**: Drag-and-drop reorder плиток в редакторе MUST поддерживать 0 dropped frames на Pixel 4a class hardware (per ADR-005), measured через macrobenchmark в perf-checkpoint phase. При failure — fallback на `Modifier.pointerInput` per FR-008 two-way door.

> **NFR-002 (VCard parse latency)**: Parse VCard payload до 10 KB MUST complete < 100 ms p95 на Pixel 4a class, measured via microbenchmark; runs on `Dispatchers.Default` чтобы не блокировать UI.

**Recommendation**: добавить NFR-001 и NFR-002 в spec.md перед `/speckit.plan` — обе measurable, обе связаны с user-perceptible UX. SC-006 (drag-and-drop reliability) — functional, **дополняется** NFR-001 (performance dimension).

---

## Что внутри (TL;DR на русском)

**Performance чеклист для спека 9 (admin mode flows) — статус PASS с 8 plan.md follow-ups, 0 fail'ов.**

**Самые большие риски — три:**

1. **Drag-and-drop frame budget (FR-008)** — самый критический. Перетаскивание плиток в редакторе использует Compose 1.6 built-in API; на LazyVerticalGrid с recompose-able карточками в edit-mode может ломаться 16ms frame budget. В спеке уже зашит two-way door fallback на `pointerInput`, но **измерение нужно сделать рано** в имплементации. Add NFR-001 в spec.md.

2. **30-sec polling для Info severity (FR-020)** — мелкая архитектурная неаккуратность: Article IX §3 предпочитает event-driven. Firestore listener покрывает все severity-уровни; severity вычисляется client-side. Рекомендация: унифицировать на listener всегда (когда экран открыт), убрать polling.

3. **VCard parse latency (FR-028)** — частично mitigated в спеке (10 KB limit, UTF-8 only). Risk: regex backtracking на malicious payload < 10 KB. Add NFR-002 в spec.md (< 100 ms p95, linear-time parser).

**Нужно ли measurable targets добавить в спек:** **да, два NFR**:
- NFR-001 (drag-and-drop frame budget, 0 dropped frames Pixel 4a).
- NFR-002 (VCard parse, < 100 ms p95 для 10 KB).

Существующие SC (SC-001/002/003/004 ≤ N сек user-task) — это functional success criteria, **не perf** measurements. Frame budget и parse latency — отдельная performance dimension, должна быть явно в спеке.

**Что вне scope спека 9 (perf-wise):**
- Push admin при Critical (FR-021 subscriber) — deferred SRV-MONITOR-001; Android Doze/battery saver concerns для будущего.
- Cold start admin — inherits от 007/008.
- APK size — inherits R8 enablement decision (TODO-ARCH-006).

**Архитектурно: спек 9 — event-driven с одним justified poll fallback (FR-020 Info severity). Battery profile хороший: listeners lifecycle-bound, закрываются при `onPause`. Memory bounded (draft через Room, history 10 retention). Главный watch — drag-and-drop frame budget на edit-mode LazyVerticalGrid.**
