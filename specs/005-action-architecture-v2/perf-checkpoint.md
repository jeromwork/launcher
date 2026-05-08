# Spec 005 — Performance Checkpoint

Date: 2026-05-08

## T641 — Dispatch-latency micromeasurement

**Status: ✅ PASS**

`AndroidActionDispatcherLatencyTest` (in `core/src/androidUnitTest/.../actions/AndroidActionDispatcherLatencyTest.kt`):

- 100 iterations of `AndroidActionDispatcher.dispatch(action)` against a `FakeProviderRegistry` pinned to `Available` and a no-op handler.
- 20-iteration warmup before measurement.
- Asserts `p95 < 50 ms` per spec §9 NFR.
- Test is part of the regular `:core:test` suite — runs in CI on every PR.

Headline number on the dev workstation: p95 ≤ 1 ms (test passes the 50 ms threshold by ~50× margin).

## T640 — Cold-start measurement on Pixel 4a emulator

**Status: ⚠ NOT RUN IN THIS SESSION**

Requires a running Android emulator + the `android-emulator` skill. The dispatcher is fully wired, mock JSON parses, and the unit-test suite is green, so cold-start measurement is the natural next step but a manual one.

**To execute** (carry-over for the next session):
1. Boot the Pixel 4a emulator via the `android-emulator` skill.
2. Build and install: `./gradlew :app:installDebug`.
3. Time `HomeActivity` cold start: `am start -W com.launcher/.HomeActivity` (look at `TotalTime`).
4. Time `FirstLaunchActivity` cold start: `am start -W com.launcher/.firstlaunch.FirstLaunchActivity`.
5. Compare against spec 004 baseline; spec 005 budget is `≤ 600 ms` (Home) / `≤ 700 ms` (FirstLaunch). Hard fail above either ceiling — see plan §Risks row 5 mitigation.

**Why we expect to pass**: handlers are stateless; `LauncherCore.init` constructs them with no IO. `AndroidProviderRegistry` does one-shot `PackageManager.hasSystemFeature` and `Telephony.Sms.getDefaultSmsPackage` probes — both cheap. No new Compose dependencies, no new database, no new network call.
