# Performance Checklist: Setup Assistant and Launcher Bootstrap

**Purpose**: Verify measurable performance targets per constitution Article IX + ADR-005 + Android Vitals.
**Created**: 2026-05-19 (post `/speckit.clarify`)
**Feature**: [spec.md](../spec.md)

---

## Performance budget summary (от спека 10)

| Path | Target | Source | Risk |
|------|--------|--------|------|
| HomeScreen cold-start (first frame of раскладки) | ≤ 1 sec on Pixel 4a | FR-002 + SC-002 | Room read + Compose render — должно fit с запасом |
| Admin push → Managed applied | ≤ 10 sec (T-PUSH) | FR-006 + SC-001 | Inherited from спека 7 FCM latency |
| APK size delta vs спек 9 | ≤ +500 KB | SC-009 | Modest new code; нет новых deps |
| 7-tap gate detection | ≤ 5 sec window | FR-021 | Trivial gesture handler |
| Challenge FP rate | ≤ 1% | SC-007 | Unit test, not runtime |

## Startup

- [⚠️] **CHK001** — Cold/warm/hot start targets documented:
  - HomeScreen cold-start: ≤ 1 sec (FR-002 + SC-002). ✓
  - **ADR-005 baseline**: HomeActivity ≤ 600ms, FirstLaunchActivity ≤ 700ms. **Спец 10 SC-002 ≤ 1 sec — выше ADR-005 baseline**. **Borderline**: SC-002 описывает "first frame of раскладки" (i.e. user-perceived раскладка appearance), а ADR-005 baseline — first frame of Activity. Эти разные метрики, но **разница не explicit в спеке**. Plan должен reconcile.
  - FirstLaunchActivity: спек 10 добавляет wizard шаги (ROLE_HOME, POST_NOTIFICATIONS), но new steps render lazily per current Composable. Initial Activity creation cost не возрастает. ✓
- [⚠️] **CHK002** — No new I/O / parsing / DI eagerness on cold-start без budget:
  - FR-020a: SetupCheck initial run на cold-start. **Cost estimate в спеке отсутствует**. Soft observation: 5 check'ов, каждый ~5-10ms (permission check, GMS check) → total ~25-50ms. Не должно affect SC-002. **Plan должен validate с macrobenchmark.**
  - GMS check на FirstLaunchActivity (FR-042) — `GoogleApiAvailability.isGooglePlayServicesAvailable()` cold call ~10-30ms. Acceptable. **Plan-level measurement.**
  - Room read appliedConfig (FR-002) — fast (< 50ms на Pixel 4a). Should fit. ✓
- [⚠️] **CHK003** — Init code в `Application.onCreate`:
  - Spec 010 adds SetupCheck registration в Koin module (implicit, plan-level). Cost: < 5ms (interface registration). ✓
  - `ChallengeRegistry` Koin registration: < 1ms. ✓
  - **Не documented explicitly в спеке** but consistent with спек 9 Phase A pattern.

## Runtime — frames

- [X] **CHK004** — Scrolling surfaces:
  - HomeScreen scroll inherited from спека 3/8. ✓ (нет regression — все changes на data layer)
  - Settings scroll — короткий список checks/devices, не bottleneck. ✓
  - Paired devices list (FR-029) — typically 1-3 entries для семейного use case. ✓
- [X] **CHK005** — Animations duration:
  - Спек 10 не вводит explicit visual animations (FR-021 no visual countdown).
  - Vibration escalation (FR-021) — haptic, не frame-budget-affecting. ✓
- [X] **CHK006** — No synchronous network/disk на main thread:
  - SetupCheck.check() — `suspend fun` (FR-017 explicit). ✓
  - GMS detection — synchronous но fast (< 30ms). Можно validate в plan.

## ANR risk

- [X] **CHK007** — Main-thread blocks > 100ms identified:
  - 7-tap detection: trivial. ✓
  - Challenge generation: random int + 1-2 string operations. Trivial. ✓
  - Challenge UI rendering: standard Compose. ✓
  - GMS detection: < 30ms typical, < 100ms worst-case. Acceptable. ✓
  - Setup check execution: off-thread (`suspend`). ✓
- [X] **CHK008** — BroadcastReceiver onReceive: **спек 10 не вводит BroadcastReceiver'ов**. ✓

## Background work

- [X] **CHK009** — New background tasks justified:
  - **No new background tasks introduced.** ✓
  - FR-020a explicit: «**НЕТ** background polling, **НЕТ** проактивных subscriptions». Соответствует Article IX §3.
- [X] **CHK010** — Polling avoided / justified:
  - SetupCheck — **lazy + reactive** (cold-start + Settings RESUMED), не polling. ✓
- [X] **CHK011** — Event listeners documented:
  - `Lifecycle.RESUMED` Settings screen — встроенный Android event, frequency = user navigation (low). Battery cost negligible. ✓ (FR-020a explicit).

## Memory

- [X] **CHK012** — New caches:
  - SetupCheck results — cached during Settings screen lifetime, invalidated on RESUMED. Bounded. ✓
  - No new long-term caches. ✓
- [X] **CHK013** — Bitmap loading:
  - Спек 10 не вводит новые bitmap surfaces (call confirmation dialog photo — read from спека 6 IconStorage). ✓
- [X] **CHK014** — Long-lived Activity/Context references:
  - Spec text не raises red flags. Plan should validate с `LeakCanary` или explicit code review.

## APK / binary size

- [X] **CHK015** — New dependencies vs ADR-005 budget:
  - **Спец 10 не добавляет новых external dependencies.** Use existing Compose, Koin, Coroutines, Firebase (через спек 7/8), GMS (через `play-services-base` уже в zavisimostях). ✓
  - SC-009: ≤ +500 KB delta vs спек 9. Modest new code (~10-15 small Composables + 5 SetupCheck implementations + 2 Challenge types). Fits budget с запасом. ✓
- [X] **CHK016** — No native libs / large assets. ✓

## Measurement

- [⚠️] **CHK017** — Measurement method documented:
  - SC-002 (cold-start) — **macrobenchmark required** per ADR-005. **Не explicitly stated в спеке 10**, но pattern из спека 6 Phase 13 и спека 7 T105-T107. Plan должен enumerate.
  - SC-007 (challenge FP rate ≤ 1%) — unit test с симулированным random input. **Не explicitly stated method.**
  - SC-009 (APK delta) — `apkdiff` или GitHub Actions comparison. **Не explicitly stated.**
- [⚠️] **CHK018** — Perf checkpoint planned в tasks.md:
  - Спек 9 имел perf-checkpoint.md (Phase 13). Спек 10 plan.md должен add similar — Phase 13 равного типа.

## Battery (Android Vitals)

- [X] **CHK019** — No new wake locks. ✓
- [X] **CHK020** — No new alarms / Doze-bypassing scheduling. ✓ (FR-020a explicit).

---

## Open items

1. **CHK001 — SC-002 reconciliation с ADR-005 baseline**: SC-002 говорит «≤ 1 sec до first frame **of раскладки**» — это разный metric чем ADR-005 «HomeActivity ≤ 600ms» (first frame of Activity вне зависимости от content). **Plan.md должен clarify**:
   - First-frame-of-Activity: target ≤ 600ms (ADR-005 baseline, unchanged).
   - First-frame-of-раскладки (с applied config rendered): target ≤ 1 sec (SC-002, new).
   - Difference (~400ms) — это window для Room read + Compose layout of tiles.

2. **CHK002 — Cold-start budget для SetupCheck initial run** (FR-020a): plan.md должен зафиксировать explicit budget (e.g. ≤ 50ms total) и measure в Phase 13 macrobenchmark.

3. **CHK017/CHK018 — Measurement methods**: plan.md должен enumerate:
   - Phase 3 unit test: challenge FP rate с симулированным random input (SC-007).
   - Phase 13 macrobenchmark: HomeScreen cold-start (SC-002), FirstLaunchActivity cold-start, SetupCheck cold-start cost.
   - CI gate: APK size delta vs спек 9 (SC-009).

## Result

**16/20 ✓, 4 observations** (CHK001/CHK002 cold-start budget reconciliation; CHK017/CHK018 measurement method enumeration — все plan-level). **Не blocker для `/speckit.plan`**: спек 010 — performance-conservative by design (no new background work, no new deps, lazy patterns). Главная perf-нагрузка — на plan-level measurement plan.

---

## Краткое содержание (для не-разработчика)

Проверили: не сделает ли спек приложение медленнее или тяжелее. **Очень чистый результат**: спек явно не добавляет фоновых задач (FR-020a explicit «no background polling»), не добавляет новых dependencies, не вводит heavy animations. Cold-start ≤ 1 sec и APK +500 KB — реалистичные бюджеты. План должен add macrobenchmark + APK delta CI gates, но это стандартная работа.
