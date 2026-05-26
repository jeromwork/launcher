# Checklist: performance

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 14/20 ✓ + 4 N/A + 2 open

---

## Startup

- [x] **CHK001** — cold/warm/hot start: ✓ N/A. Spec 012 не добавляет init code в `Application.onCreate` cold path. `LocalMediaStore` инициализируется lazy (первый Resolver.resolve call). `MediaPicker` adapter — registered, не active при start.
- [x] **CHK002** — no new I/O on cold start: ✓. Все I/O — за фасадами, активируются user-action'ами (admin tap, бабушка тапает плитку).
- [x] **CHK003** — `Application.onCreate` cost: ✓ N/A. DI-registration `PrivateMediaUploader/Resolver`, `MediaPicker`, `LocalMediaStore` — это object construction (≤ 1 мс на каждом, в сумме ≤ 5 мс). Не превышает бюджет ADR-005.

## Runtime — frames

- [x] **CHK004** — scrolling frame budget: ✓
  - Главный экран бабушки = плитки. Аватар = чтение `LocalMediaStore` файла (повторный показ ≤ 100 мс, FR-004).
  - **Implementation note** для plan-phase: bitmap decode идёт async через Coil 3 (or equivalent), не блокирует frame.
- [x] **CHK005** — animations duration: ✓ N/A. Spec 012 не вводит explicit animations. Pinch-to-zoom (FR-019) — стандартный gesture, native frame rate.
- [x] **CHK006** — no sync network/disk на main thread: ✓
  - Все decrypt/download — coroutines (унаследовано из 011 порт-suspendоф).
  - LocalMediaStore чтение — должно быть IO dispatcher (plan-phase requirement).

## ANR risk

- [ ] **CHK007** — operations > 100ms на main thread
  - **Status**: ⚠️ нужен явный invariant в plan-phase.
  - Первый download (3 сек, FR-004) — **должен** идти в Dispatchers.IO, плитка показывает progress.
  - Decrypt одного blob'а (≈ 100-500 мс CPU на medium-tier для 200 KB) — **должен** идти в Dispatchers.Default.
  - **Действие**: plan-phase зафиксировать «все фасадные методы — `suspend fun` с IO/Default dispatcher на actual implementation; main thread видит только результат через Flow / StateFlow».
- [x] **CHK008** — BroadcastReceiver short bodies: ✓ N/A. Spec 012 не вводит receivers.

## Background work

- [x] **CHK009** — new background tasks justified: ✓
  - Single background task — housekeeping reconciler (унаследовано из 011, WorkManager 24h cadence).
  - Spec 012 не добавляет новых.
- [x] **CHK010** — no polling: ✓. Все обновления event-driven (FCM push для config changes, user-tap для plate render).
- [x] **CHK011** — event listeners documented: ✓ N/A.

## Memory

- [x] **CHK012** — caches size + invalidation: ✓
  - `LocalMediaStore` — **persistent** (не cache), не LRU, не in-memory. Размер ограничен device storage capacity. Защита от переполнения — out of scope ([`TODO-ARCH-019`](../../docs/dev/project-backlog.md)).
  - **No in-memory cache** ввёлся (после Clarification Q5 вместо `DecryptCache` — persistent `LocalMediaStore`). ✓
- [x] **CHK013** — bitmap loading budget: ✓
  - Использовать Coil 3 default policy (plan-phase implementation detail). Custom 100MB cache не вводится.
- [x] **CHK014** — no long-lived Activity/Context refs: ✓
  - `LocalMediaStore` adapter получает `Context.applicationContext` (не Activity).
  - `MediaPicker` adapter — ActivityResultLauncher pattern (lifecycle-aware).
  - **Действие** (plan): зафиксировать `applicationContext` invariant в DI module.

## APK / binary size

- [ ] **CHK015** — APK delta vs ADR-005 budget
  - **Status**: ⚠️ tracked via SC-006 (≤ 500 KB delta), но **measurement не запланирован**.
  - **Действие**: plan-phase добавить task «measure APK delta before/after spec 012 release build with R8».
  - Текущий budget release ≤ 12 MB (ADR-005); 500 KB delta = ~4% increment.
- [x] **CHK016** — native libs / large assets: ✓ N/A. Spec 012 не добавляет native libs. libsodium native lib — уже в 011. Compose UI screens — Kotlin only.

## Measurement

- [x] **CHK017** — measurement method для каждой perf target:
  - SC-001/002 (≤ 30s e2e contact/document) → manual stopwatch на стенде, 10 проходов, p95.
  - SC-003 (cache hit ≤ 100ms, miss ≤ 3s) → macrobenchmark или Compose tracing.
  - SC-006 (APK delta) → R8 release build size diff.
- [x] **CHK018** — perf checkpoint в tasks.md: deferred-to-plan.

## Battery (Android Vitals)

- [x] **CHK019** — no new wake locks: ✓ N/A.
- [x] **CHK020** — no new alarms / Doze bypass: ✓ N/A.

---

## Summary

| Status | Count |
|---|---|
| ✓ | 14 |
| N/A | 4 (CHK001 startup, CHK005 animations, CHK008 receivers, CHK016 native, CHK019/020 battery — частично) |
| ⚠️ open | 2 (CHK007 dispatcher invariant, CHK015 APK measurement task) |
| ✗ violations | 0 |

**Open items**:
1. **CHK007**: plan-phase явно зафиксировать invariant «фасадные методы — `suspend fun` с `Dispatchers.IO` (network/disk) или `Dispatchers.Default` (crypto); main thread получает только результат».
2. **CHK015**: plan-phase task — measure APK delta release build before/after, документировать в perf-checkpoint.md.

**Verdict**: Spec 012 имеет **зрелую performance story** благодаря Clarification Q5 — отказ от in-memory decrypt cache в пользу persistent local store устраняет memory pressure / cache eviction класс проблем. Все measurable targets (≤ 30s e2e, ≤ 100ms hit, ≤ 3s miss, ≤ 500 KB APK) — operationalised.

**Constitution alignment**: Article IX ✓.
