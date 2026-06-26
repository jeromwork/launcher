# Checklist: performance — TASK-51 libsodium consolidation

Applied skill: `.claude/skills/checklist-performance/SKILL.md`
Spec: `specs/task-51-libsodium-consolidation/spec.md`
Date: 2026-06-26

Reference: Article IX of `.specify/memory/constitution.md`, ADR-005 performance targets, Android Vitals.

---

## Startup

- [x] CHK001 If feature affects cold/warm/hot start path: target time documented (per ADR-005: HomeActivity ≤ 600ms, FirstLaunchActivity ≤ 700ms; iOS ≤ 800ms).
  - Evidence: SC-009 [backlog] — `adb shell am start -W -n com.launcher.app/.HomeActivity` TotalTime ≤ 1330 ms on Xiaomi 11T (baseline from TASK-7 T061). FR-013 captures cold-start non-regression.
  - Note: spec uses TASK-7 T061 device-measured baseline (1260-1330 ms на Xiaomi 11T) rather than ADR-005 600 ms target — this is the project's already-accepted physical-device baseline for non-Pixel-class arm64. Not a fail, but observation.

- [x] CHK002 No new I/O / parsing / DI eagerness on the cold-start path without explicit budget.
  - Evidence: refactor REMOVES eager JNA bind (root cause of crash and prior cold-start overhead). FR-001 + FR-002 eliminate lazysodium/JNA AAR. ionspin libsodium-kmp uses lazy-bind (Assumption: "ionspin lazy-bind by design"). No new eager init introduced.

- [x] CHK003 Init code added to Application.onCreate has documented cost in milliseconds.
  - Evidence: spec adds NO new Application.onCreate init code. Edge case "ошибка JNI link на cryptokit.crypto.libsodium" mentions optional `assertNoFakeCryptoInRelease` health check — but this is an existing pattern, not new init. FR-015 consolidates two DI modules into one Koin module — net reduction, not addition. Net cost ≤ 0 vs baseline.

## Runtime — frames

- [N/A] CHK004 Scrolling surfaces have target frame budget: 0 dropped frames on medium-tier (Pixel 4a class) per ADR-005.
  - Justification: infrastructure refactor of crypto layer. No new scrolling surfaces / lists / RecyclerViews / LazyColumns introduced. PairingActivity is touched but as a non-regression target (open without crash), not new scroll UX.

- [N/A] CHK005 Animations duration documented; >300ms animations justified.
  - Justification: no animations introduced or modified.

- [x] CHK006 No synchronous network/disk on the main thread.
  - Evidence: crypto primitives (`AsymmetricCrypto.generateX25519KeyPair`, `SecureKeyStore.store/load`) and pairing operations remain on background dispatchers as before. FR-009 throws-pattern + auto re-throw `CancellationException` preserves coroutine structured concurrency. No change to threading model from baseline.

## ANR risk

- [x] CHK007 Operations triggered by user input that block the main thread > 100ms identified; either moved off-thread or justified.
  - Evidence: keypair generation (X25519/Ed25519) and `SecureKeyStore` Android Keystore access historically run off main thread in `PairingCryptoCoordinator`. FR-008 rewrites coordinator preserving suspend semantics. No new main-thread blocking introduced.

- [N/A] CHK008 BroadcastReceiver onReceive bodies stay short (no work > 10s, ideally < 1s); long work delegated to WorkManager.
  - Justification: spec introduces no new BroadcastReceivers.

## Background work

- [N/A] CHK009 Each new background task (Service, WorkManager job, Coroutine on Application scope, BroadcastReceiver) is justified per Article IX §2.
  - Justification: no new background tasks. Refactor is pure replacement of crypto adapter implementation.

- [N/A] CHK010 Polling explicitly avoided or justified; event-driven preferred per Article IX §3.
  - Justification: no polling introduced; FR-005 force-re-pair migration is one-shot at first launch after upgrade (idempotent ensure-keys per edge case), not polled.

- [N/A] CHK011 Each event listener documents source, frequency, threading, expected battery cost, fallback if event delayed/absent (Article VI §6).
  - Justification: no new event listeners.

## Memory

- [x] CHK012 New caches have explicit size limit and invalidation rule (Article IX §6).
  - Evidence: no new caches introduced. `SecureKeyStore` uses Android Keystore (OS-managed), not an in-process cache. `FakeSecureKeyStore` (in-memory HashMap) is test-only.

- [N/A] CHK013 Bitmap/image loading uses configured memory budget (Coil 3 default policy, no custom 100MB caches).
  - Justification: no bitmap/image loading involved.

- [x] CHK014 No long-lived references holding Activity/Context (leak risk).
  - Evidence: crypto APIs in `cryptokit.crypto.api.*` live in commonMain (no Android Context dependency by design). Android Keystore adapter uses `expect/actual` `SecureKeyStore` and accesses `AndroidKeyStore` provider statically (no Activity reference held). FR-006 keeps API surface Context-free.

## APK / binary size

- [x] CHK015 New dependencies vs ADR-005 budget (debug ≤ 18MB, release ≤ 12MB; hard-fail at 22 / 16MB) — delta documented.
  - Evidence: SC-008 [backlog] — APK MUST shrink by ≥ 3 MB (removal of JNA AAR ~5 MB). FR-012 caps APK at baseline `d5763d6`. Net delta is negative (smaller).

- [x] CHK016 Native libraries / large assets justified.
  - Evidence: FR-003 — exactly one `libsodium.so` per ABI in shipped APK (down from two due to lazysodium + ionspin duplication). `packaging.jniLibs.pickFirsts` workaround removed (SC-010). Native lib retention justified: libsodium is the core crypto primitive used by spec 011 pairing.

## Measurement

- [x] CHK017 If perf target is set: measurement method documented (macrobenchmark, manual stopwatch, frametime logs).
  - Evidence: Local Test Path documents `adb shell am start -W -n com.launcher.app/.HomeActivity` for cold start (SC-009), `stat -c%s` for APK size (SC-008), `./gradlew :app:dependencies | grep` for dependency verification (SC-005).

- [ ] CHK018 Perf checkpoint planned in tasks.md (perf-checkpoint.md output).
  - Status: tasks.md not yet generated for TASK-51 (spec post-factum, plan/tasks pending). Perf-checkpoint should be added during speckit-tasks. Open item — see below.

## Battery (Android Vitals)

- [x] CHK019 No new wake locks without explicit justification and timeout.
  - Evidence: no wake locks introduced.

- [x] CHK020 No new alarms / Doze-bypassing scheduling.
  - Evidence: no AlarmManager / JobScheduler / setExactAndAllowWhileIdle usage introduced.

---

## Verdict

**PASS with one open item.**

Summary: TASK-51 is an infrastructure refactor that REMOVES eager JNA binding, REDUCES APK size (≥3 MB), and INTRODUCES no new background work, animations, caches, wake locks, or scrolling surfaces. Performance impact is net-positive or neutral on every axis the checklist examines. The single gap (CHK018, perf-checkpoint in tasks.md) is procedural — to be resolved when `speckit-tasks` runs.

- Items checked `[x]`: 13
- Items `[N/A]` (not applicable to infrastructure refactor): 6
- Items `[ ]` (open): 1
- Total: 20

Pass rate (excluding N/A): 13/14 = 93%.

## Open items

1. **CHK018 — Perf checkpoint in tasks.md.** TASK-51 spec was authored post-factum after Phase 1 gradle cleanup; plan.md and tasks.md not yet generated. When `speckit-tasks` runs, add an explicit perf-checkpoint task that captures:
   - APK size measurement: `stat -c%s app/build/outputs/apk/mockBackend/debug/app-mockBackend-debug.apk` before/after, asserting ≥3 MB reduction (SC-008).
   - Cold start measurement: `adb shell am start -W -n com.launcher.app/.HomeActivity` ×5 on Xiaomi 11T, asserting median TotalTime ≤ 1330 ms (SC-009).
   - Dependency-tree check: `./gradlew :app:dependencies | grep -E "lazysodium|net.java.dev.jna"` empty (SC-005).
   - Output: `specs/task-51-libsodium-consolidation/perf-checkpoint.md` with raw numbers.

## Notes (not blocking)

- SC-009 uses the device-measured baseline (1330 ms on Xiaomi 11T) rather than ADR-005's 600 ms HomeActivity target. This is consistent with the project's accepted physical-device baseline for non-Pixel arm64 hardware but should be flagged if/when ADR-005 is revisited.
- Edge case in spec ("если ionspin тоже зачем-то eager-bind'нет") posits an `assertNoFakeCryptoInRelease` health check at Application.onCreate. If implemented, document its cost (target ≤ 5 ms) to honour CHK003 going forward.
