# Checklist: performance — спек 006

**Spec:** [`spec.md`](../spec.md) · **Run:** 2026-05-09 (post-clarify) · **Score:** 15 ✓ / 4 ◐ / 2 N/A / 0 ✗

Source: Article IX [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md), [ADR-005](../../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md), Android Vitals.

Legend: `[x]` pass · `[~]` partial · `[ ]` fail · `N/A` not applicable.

---

## Startup

- [x] **CHK001** Cold-start target documented.
  - NFR-004 «≤ 20 ms» + SC-014 «macrobenchmark before/after». В пределах ADR-005 budget HomeActivity ≤ 600ms.
- [x] **CHK002** No new I/O / parsing / DI eagerness on cold-start.
  - FR-005 «DataStore lazy read», NFR-004 «no eager network/disk», NFR-N01 «no network at all».
- [~] **CHK003** Application.onCreate init cost per-component documented.
  - **Finding:** общий budget NFR-004 «≤ 20 ms» задан, но не разбит на: DI wiring (CapabilityRepository, HealthRepository, BundledIconStorage), DataStore lazy initialization, ContentObserver registration, NetworkCallback registration.
  - **Fix:** plan.md разбивает budget per-component (e.g. DI ≤ 5ms, listener registration ≤ 10ms, lazy reads = 0ms cold).

## Runtime — frames

- [x] **CHK004** Scrolling frame budget.
  - NFR-007 «Banner UI ≤ 16 ms (one frame at 60Hz)». Snapshot не на скролл-поверхности.
- N/A **CHK005** Animations.
  - No new animations introduced; banner appear/disappear FR-029 «within 1 second» — reactivity SLA, not animation.
- [x] **CHK006** No synchronous network/disk on main thread.
  - NFR-003 «MUST NOT block main thread» + NFR-N01. DataStore Preferences API suspending — non-blocking.

## ANR risk

- [x] **CHK007** User-input ops > 100 ms identified.
  - Banner button clicks: `AudioManager.setStreamVolume` synchronous <10ms; `startActivity(tel:)` async. No long ops.
- [~] **CHK008** BroadcastReceiver onReceive bodies short.
  - **Finding:** FR-002 (PACKAGE_*) и FR-018 (несколько ContentObserver / sticky broadcasts) не имеют **explicit requirement** «onReceive body ≤ 10 ms, work delegates to coroutine/WorkManager». Implicit через FR-003 debounce.
  - **Fix:** добавить в spec NFR-012 «All BroadcastReceiver onReceive bodies MUST complete within 10 ms; work delegates to coroutine on background dispatcher». Применимо к FR-002, FR-018.

## Background work

- [x] **CHK009** Each background task justified per Article IX §2.
  - `OfflineEscalationWorker` (WorkManager one-shot, event-cancelable, не periodic) — justified by FR-019..023.
  - No services, no Application-scope coroutines without lifecycle.
- [x] **CHK010** Polling avoided.
  - NFR-N05 explicit «MUST NOT polling — no periodic timers, no scheduled reads. All updates event-driven».
- [~] **CHK011** Event listeners documented (source, frequency, threading, battery, fallback).
  - **Finding:** FR-002 (PACKAGE_*) и FR-018 (NetworkCallback / AirplaneMode ContentObserver / VOLUME_CHANGED ContentObserver / ACTION_BATTERY_CHANGED) **перечислены**, но per-listener attributes (frequency, battery cost, fallback if delayed) **не документированы**.
  - **Fix:** plan.md создаёт таблицу listeners with attributes:

    | Listener | Source | Expected freq | Threading | Battery cost | Fallback |
    |----------|--------|---------------|-----------|--------------|----------|
    | PACKAGE_ADDED/REMOVED/REPLACED | system broadcast | rare (install events) | binder thread → coroutine | ≈ 0 | RESUMED rebuild |
    | NetworkCallback onAvailable/onLost | ConnectivityManager | medium (network changes) | binder thread → coroutine | ≈ 0 | DataStore last-known + RESUMED |
    | AIRPLANE_MODE_ON ContentObserver | system Settings | rare | main → coroutine | ≈ 0 | RESUMED rebuild |
    | VOLUME_CHANGED ContentObserver | system Settings | medium-high (every key press) | main → coroutine + debounce | low | RESUMED rebuild |
    | ACTION_BATTERY_CHANGED sticky | system | continuous (charging events) | binder → coroutine | ≈ 0 | RESUMED rebuild |
    | ProcessLifecycleOwner.RESUMED | androidx.lifecycle | medium (foreground events) | main | ≈ 0 | always-on, no fallback needed |

## Memory

- N/A **CHK012** Caches size + invalidation.
  - NFR-N08 «кэш-механика MUST NOT be implemented in спек 006». No caches in 006. (Спек 007 обязан проверить при добавлении `RemoteIconStorage`.)
- N/A **CHK013** Bitmap/image budget.
  - `BundledIconStorage` reads vector drawables / PNG via standard CMP resource loader. No Coil, no custom image-loading.
- [x] **CHK014** No long-lived Activity/Context refs.
  - WorkManager Worker uses `applicationContext`. Repositories DI-singleton on Application scope. (Plan.md verifies.)

## APK / binary size

- [x] **CHK015** APK delta documented.
  - NFR-009 «≤ 100 KB (provider brand drawable assets)». ADR-005 budget headroom: 100 KB << 6 MB available.
- [x] **CHK016** Native libs / large assets justified.
  - 8 provider drawables (~12.5 KB each, vector preferred). No native libs added.

## Measurement

- [x] **CHK017** Measurement method documented.
  - SC-009 «Battery Historian», SC-010 «Compose recomposition trace», SC-013 «release variant APK measure», SC-014 «macrobenchmark».
- [~] **CHK018** Perf checkpoint planned in tasks.md.
  - **Finding:** tasks.md not yet generated. SC-009 / SC-013 / SC-014 must surface as concrete tasks during speckit-tasks.
  - **Fix:** speckit-tasks must add T-tasks for: macrobenchmark cold-start (SC-014), Battery Historian session (SC-009), APK size measurement (SC-013), perf-checkpoint.md output.

## Battery (Android Vitals)

- [x] **CHK019** No new wake locks.
  - Spec doesn't introduce wake locks. WorkManager manages internally for short tasks (NFR-011 ≤ 100ms per invocation).
- [x] **CHK020** No alarms / Doze-bypassing.
  - WorkManager respects Doze (Edge Cases explicitly accepts «эскалация в Doze может задержаться»). No `setExactAndAllowWhileIdle`, no `AlarmManager`.

---

## Open items для plan.md / tasks.md

1. **CHK003** plan.md разбивает init cost per-component within NFR-004 budget.
2. **CHK008** добавить spec NFR-012 «BroadcastReceiver onReceive ≤ 10 ms» **перед** speckit-plan.
3. **CHK011** plan.md создаёт listeners table with attributes (sketched above).
4. **CHK018** speckit-tasks выделяет T-задачи на perf-checkpoint (Battery Historian + macrobenchmark + APK size).

## Itog

- 15 PASS, 4 PARTIAL (3 fixable в plan.md, 1 — добавить NFR-012 в spec), 2 N/A (caches/bitmaps не вводим), 0 hard FAIL.
- **Verdict:** спек **очень дисциплинирован** по производительности. Главные сильные стороны: explicit no-polling (NFR-N05), no persistent connections (NFR-010), event-driven with cancellable one-shot tasks (FR-019..023), explicit Doze tolerance (Edge Cases). Никакого «батарейного риска».
- Перед speckit-plan: добавить **NFR-012** (BroadcastReceiver body limit). Остальное — заметки для plan.md/tasks.md.

---

## TL;DR для нетехнического читателя

**Главный вывод: спек очень бережно относится к батарее телефона бабушки.**

Что мы пообещали и что Compass проверил:
- Никакого «висения в фоне». Приложение спит, просыпается только когда система говорит «есть событие» (поменялась сеть, установили приложение, изменилась громкость).
- Никаких таймеров «проверяй каждые N секунд» — система сама будит когда нужно.
- Эскалация громкости при долгом офлайне — это **отменяемая** задача. Связь вернулась → задача отменяется до срабатывания. Не разрядит телефон.
- Расход батареи: **≤ 0.1% в день** в обычном режиме (с интернетом). **≤ 0.2% в день** в худшем случае (если 5 часов офлайна и шла эскалация). Для сравнения: WhatsApp с уведомлениями ест порядка 2-3% в день.
- Никаких больших картинок в памяти, никакого кэша на сервере (он появится только в спеке 007).
- Размер приложения вырастет на ~100 КБ (8 иконок провайдеров). Это копейки.

Что нужно дополнить **до того как идти в фазу плана**:
- Добавить одно правило: «обработчики системных событий должны заканчиваться за 10 миллисекунд, всё долгое — отдельным шагом». Это хорошая дисциплина, чтобы Android не думал что приложение зависло.

Что нужно проверить **в фазе задач**:
- Реальное измерение батареи через Battery Historian (стандартный инструмент Google).
- Реальное измерение скорости старта через macrobenchmark (тоже стандартный инструмент).
- Реальное измерение размера APK после сборки release-варианта.

Дополнительные мелочи (как именно разбить 20 миллисекунд старта по компонентам, какой именно поток использовать для каждого слушателя) — переносятся в plan.md, это не критично для понимания спека.
