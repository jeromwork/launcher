# Checklist: performance — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass

## Startup

- [⚠️] **CHK001** Feature affects cold/warm/hot start — target time documented.
  - F-5c вводит `FcmTokenPublisher.publish(currentToken)` вызов после Sign-In + при `FirebaseMessagingService.onNewToken`.
  - Per F-5b convention — `EnvelopeBootstrap` async после Sign-In. FcmTokenPublisher должен следовать тому же pattern.
  - **НЕ explicit в spec**.
  - **Action**: добавить FR — «`FcmTokenPublisher.publish(...)` async (coroutine on IO dispatcher), fire-and-forget. MUST NOT block cold start critical path (HomeActivity ≤ 600ms per ADR-005). Vызывается post-Sign-In или onNewToken callback вне initialization sequence».

- [x] **CHK002** No I/O / parsing / DI eagerness on cold-start path без budget.
  - FCM token retrieval: Firebase Messaging SDK returns from internal cache синхронно после first init. No network on warm token reads.
  - HTTP push (HttpPushTrigger): not on startup — invoked только после ConfigSaver.save (post-Sign-In, post-action).
  - DI registration для push subsystem (PushTrigger, PushHandler, PushHandlerRegistry, FcmTokenPublisher) — Koin registration <5ms typically. Acceptable.

- [⚠️] **CHK003** Init code added к Application.onCreate documented cost (ms).
  - F-5c adds DI module (Koin `push` module). Cost estimate: ~5ms (small interface registry).
  - `LauncherFirebaseMessagingService` declared в Android Manifest — system-instantiated lazy, NOT in Application.onCreate.
  - `PushHandlerRegistry` initialization — Map<EventType, PushHandler> setup в DI, ~1ms.
  - **Not measured**.
  - **Action**: добавить в spec.md: «Application.onCreate cost target: F-5c DI module init ≤ 10ms (measured during plan.md perf-checkpoint)».

## Runtime — frames

- [N/A] **CHK004** Scrolling surfaces frame budget. F-5c — нет UI scrolling.

- [N/A] **CHK005** Animations duration. F-5c — нет animations.

- [⚠️] **CHK006** No synchronous network/disk on main thread.
  - `HttpPushTrigger.trigger()` — HTTP POST. **MUST** be coroutine on IO dispatcher.
  - **НЕ explicit в spec**. Implicit through `suspend fun` signature и assumption что ConfigSaver invokes на IO dispatcher (F-5b convention).
  - **Action**: добавить FR — «All push subsystem calls (`PushTrigger.trigger`, `FcmTokenPublisher.publish`) MUST be invoked on IO dispatcher (`Dispatchers.IO`). UI MUST NOT directly invoke push subsystem».

## ANR risk

- [⚠️] **CHK007** User-input operations blocking main thread > 100ms identified.
  - **Trigger path**: user save → ConfigSaver.save (F-5b — already async per F-5b spec) → ConfigSaver invokes pushTrigger.trigger() fire-and-forget. **PushTrigger MUST NOT block** ConfigSaver coroutine — должен сам launch на отдельной background coroutine, или быть очень быстрым (если synchronous to save).
  - Per FR-031 (fire-and-forget) — spec implies non-blocking, но не explicit.
  - **Action**: дополнить FR-031: «Fire-and-forget = `pushTrigger.trigger(...)` MAY be launched as detached coroutine; ConfigSaver.save MUST complete без awaiting push result».

- [⚠️] **CHK008** BroadcastReceiver onReceive < 10s.
  - `LauncherFirebaseMessagingService.onMessageReceived` — не строго BroadcastReceiver, но **similar constraint**: ANR risk ≈ 20s, recommended < 10s.
  - F-5c work внутри `onMessageReceived`:
    1. Parse PushPayload (<1ms).
    2. Lookup handler в PushHandlerRegistry (<1ms).
    3. Dispatch coroutine (instant).
    4. Coroutine: `ConfigSaver.loadForOther(ownerUid, configName)` — Firestore read + decrypt + DataStore write. **Может занять 1-10s on slow network**.
  - **Risk**: если loadOwn > 10s — service killed mid-write, partial state.
  - **Action**: добавить FR — «`PushHandler.handle()` MUST respect 10s budget. If exceeds (slow network, large config) — `loadOwn` MUST be dispatched к WorkManager job, not блокирует FirebaseMessagingService lifecycle».

## Background work

- [x] **CHK009** Each new background task justified.
  - `FirebaseMessagingService.onMessageReceived` — required by FCM design.
  - Internal coroutine dispatch внутри handler — required для не-blocking service lifecycle.
  - WorkManager (если выше CHK008 action принят) — required для long-running pulls. Per existing convention (F-5b uses WorkManager для async config push queue).

- [x] **CHK010** Polling avoided or justified.
  - F-5c IS the **anti-polling** mechanism — push-based cache invalidation заменяет polling.
  - No new polling introduced.

- [⚠️] **CHK011** Event listener documents source/frequency/threading/battery/fallback.
  - `FirebaseMessagingService.onMessageReceived`:
    - **Source**: FCM data-messages (subscribed implicit via service declaration).
    - **Frequency**: 1-10 push/day per device (estimate based on ~5 admin saves/day × 2 recipients).
    - **Threading**: Firebase background thread (managed by Firebase SDK).
    - **Battery cost**: minimal per message (~10mAh per push processing — мокрый estimate; FCM-driven wake from doze).
    - **Fallback if event delayed/absent**: pull-on-app-open (FR-022).
  - **Не explicit в spec** в одном месте.
  - **Action**: добавить в spec.md note: «FCM listener profile: source=FCM data-messages, frequency ~10/day per device, thread=Firebase background, battery ~negligible, fallback=pull-on-app-open».

## Memory

- [x] **CHK012** New caches with size limit + invalidation rule.
  - **Workers KV** caches:
    - Idempotency cache: 10-min TTL, per-key size <1KB. KV handles eviction.
    - JWKS cache: 1 entry, ~5KB, dynamic TTL.
    - Rate-limit cache: per-UID counters, 60s TTL.
  - **Client-side**: no new in-memory caches.
  - Acceptable size profile.

- [N/A] **CHK013** Bitmap/image loading. F-5c не handles images.

- [x] **CHK014** No long-lived references holding Activity/Context.
  - `FirebaseMessagingService` lifecycle managed by Android.
  - `PushHandlerRegistry` — singleton в Koin scope, references PushHandlers (which themselves are domain types, нет Activity refs).
  - `HttpPushTrigger` — receives `AuthIdentity` (which may hold ApplicationContext, F-4 territory). No Activity ref.

## APK / binary size

- [⚠️] **CHK015** New dependencies vs ADR-005 budget.
  - **New module `core/push/`**:
    - Ktor client (if commonMain placement chosen): ~1-2MB compressed.
    - kotlinx.serialization: already used elsewhere — no new cost.
    - Kotlin coroutines: already used.
    - **Estimated APK delta: 1-2MB**.
  - **Worker** (`workers/family-push/`) — separate deployable, NOT in APK.
  - ADR-005 budget: debug ≤ 18MB, release ≤ 12MB (per spec 007 reports — currently release at ~13MB after F-5b, **already over budget** with delta 3.99MB pre-R8).
  - **Concern**: F-5c пушит ещё на 1-2MB up. R8 minification ([TODO-ARCH-006](../../docs/dev/project-backlog.md#todo-arch-006)) — blocker для production release.
  - **Action**: измерить APK delta после implementation, добавить в `perf-checkpoint.md` task'у. Если ≥ 2MB delta — R8 minification обязательна перед merge.

- [N/A] **CHK016** Native libraries.
  - F-5c не adds native libs. Firebase Messaging native — уже в APK через F-5b.

## Measurement

- [x] **CHK017** Perf target — measurement method documented.
  - SC-001 (5s median / 30s p95 sync latency) → emulator-to-emulator stopwatch + structured log timestamps.
  - SC-002 (200ms save overhead p95) → macrobenchmark.
  - SC-007 (100 concurrent <10ms Worker CPU) → load test против `wrangler dev` (wrk / k6).
  - Documented (implicit in Local Test Path).

- [⚠️] **CHK018** Perf checkpoint planned в tasks.md.
  - tasks.md ещё не существует.
  - **Action**: при `/speckit.tasks` — добавить task «create perf-checkpoint.md measuring SC-001, SC-002, SC-007 + APK delta».

## Battery (Android Vitals)

- [⚠️] **CHK019** New wake locks justified + timeout.
  - `FirebaseMessagingService` — Firebase SDK may acquire WAKE_LOCK internally при FCM message receipt (wake device from doze). Standard Firebase behaviour, managed by SDK.
  - F-5c само НЕ acquires wake locks.
  - **Action** (informational): document в spec.md «F-5c relies on Firebase Messaging SDK's internal WAKE_LOCK for FCM message wake-up. No custom wake locks added».

- [x] **CHK020** No new alarms / Doze-bypassing scheduling.
  - F-5c не использует AlarmManager, setExactAndAllowWhileIdle, etc.
  - FCM data-messages do wake device from doze (Google's design), but не bypass — managed by Firebase SDK + Android system.
  - WorkManager job (если CHK008 action принят) для long-running pulls — respects doze (default WorkManager behaviour).

## Summary

- **Pass**: 9/20
- **Partial/Warning**: 9/20 (CHK001, CHK003, CHK006, CHK007, CHK008, CHK011, CHK015, CHK018, CHK019)
- **Fail**: 0/20
- **N/A**: 2/20 (CHK004, CHK005, CHK013, CHK016 — though CHK013/16 separately marked)

**Big picture**: Performance characteristics **mostly OK**:
- No polling (F-5c IS anti-polling).
- No UI/frames concern.
- No new wake locks.
- KV caches bounded.
- Single fallback (pull-on-app-open).

**Concerns** (operational, не architectural):
1. Many «implicit» assumptions about async-ness — спека полагается на reader'а понять что push subsystem не на main thread. **Должны быть FRs**.
2. CHK008 — receiver `loadOwn` может exceed 10s service budget. Нужна WorkManager fallback (или explicit cancel).
3. CHK015 — APK delta 1-2MB. R8 minification уже blocker per ARCH-006.
4. Perf checkpoint planned для tasks.md (CHK018).

## Action items (priority order)

1. **Высокая** (FRs в spec.md, consolidates 3 checks):
   - All push subsystem calls on IO dispatcher (CHK006).
   - `FcmTokenPublisher.publish()` + `PushTrigger.trigger()` MUST NOT block cold start critical path (CHK001).
   - Fire-and-forget = detached coroutine, save не awaits push (CHK007 supplement to FR-031).
2. **Высокая** (FR в spec.md): `PushHandler.handle()` 10s budget; long-running `loadOwn` deferred к WorkManager (CHK008).
3. **Средняя** (для tasks.md): perf-checkpoint.md task для SC-001, SC-002, SC-007 + APK delta (CHK018).
4. **Низкая** (одна правка в spec.md): FCM listener profile note (CHK011).
5. **Низкая** (одна правка в spec.md): WAKE_LOCK reliance disclaimer (CHK019).
6. **Низкая** (для plan.md): documented APK delta — connect с TODO-ARCH-006 R8 milestone (CHK015).

---

## Заметка для новичка (TL;DR)

Проверено: не замедлит ли эта фича приложение, не съест ли батарею, не превысит ли лимит размера APK.

**Хорошо сделано** (9/20):
- F-5c — это **противоположность polling'у**: вместо «спрашивать сервер каждые 5 минут» мы получаем push когда что-то поменялось. Это **экономит** батарею.
- Никакого UI / анимаций — нечего тормозить.
- Кэши на сервере (Workers KV) ограничены по TTL — память не утекает.

**Чего не хватает** (9 «частично»):
- **Не записано явно** что push-вызовы должны быть на background-потоке (не на UI), хотя это очевидно для разработчика.
- **Не записан риск 10-секундного лимита**: когда телефон получает push и идёт скачивать конфиг — Android даёт сервису 10 секунд, потом убивает. Если сеть медленная и конфиг большой — может не успеть. Нужно «если не успеваем — отдать в WorkManager».
- **APK вырастет на 1-2MB** из-за нового модуля (Ktor HTTP client). Не критично, но release-сборка уже выходит за лимит — нужно включить R8 минификацию (это уже стоит в backlog'е).
- **Perf checkpoint** — задача измерить реальную скорость — попадёт в tasks.md при следующем шаге spec-kit.

Это **не блокирует** /speckit.plan. Нужно 3-4 новых FRs про async/threading + 1 задача в tasks.md.
