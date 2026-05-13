# Spec 007 — perf checkpoint

Measurements against the success criteria declared in `spec.md`. Date:
2026-05-11. Branch: `007-pairing-and-firebase-channel` at HEAD.

| SC | Target | Measured | Status | Notes |
|---|---|---|---|---|
| SC-006 | realBackend APK ≤ mockBackend + 3 MB | **+3.99 MB** (+3.80 MiB) | ✗ | R8 not enabled on release — see §SC-006 below for exit ramp |
| SC-007 | HomeActivity cold start median ≤650 ms | not measured | ⏸ deferred | requires Macrobenchmark setup + physical/Pixel-class emulator; spec'd at T105 |
| SC-001 | Managed→admin pair shown ≤10 s end-to-end | not measured | ⏸ deferred | requires 2 emulators + real or mock FCM; spec'd at T106 |
| SC-011 | Worker p95 latency ≤500 ms | not measured | ⏸ deferred | requires deployed Worker + k6/Artillery harness; spec'd at T107 |
| SC-003 | Push delivery end-to-end ≤10 s | not measured | ⏸ deferred | covered by T110 emulator smoke alongside SC-001 |

## T108 — APK delta (measured)

```sh
./gradlew :app:assembleRealBackendRelease :app:assembleMockBackendRelease
```

Outputs:

```
app/build/outputs/apk/realBackend/release/app-realBackend-release-unsigned.apk
  12_976_550 bytes  (12.38 MiB)
app/build/outputs/apk/mockBackend/release/app-mockBackend-release-unsigned.apk
   8_988_678 bytes  ( 8.57 MiB)
```

Delta: **3_987_872 bytes** = **3.99 MB (SI)** = **3.80 MiB (binary)**.

### SC-006 status — fails by ~0.99 MB

The delta is the cost of three Firebase BoM artefacts brought in only by
`realBackend`:

- `firebase-firestore-ktx` (largest — gRPC + protobuf-lite + Firestore
  client + offline cache),
- `firebase-auth-ktx`,
- `firebase-messaging-ktx`.

R8 minification + resource shrinking are **not** enabled on the `release`
buildType today; both APKs are unminified. This was a deliberate
two-way-door MVP decision (Phase 4 — keep the wire-format and bytecode
unobscured during initial integration so issues surface in stack traces).

### Exit ramp for SC-006

Enable R8 on `release` in [app/build.gradle.kts](../../app/build.gradle.kts)
once spec 007 ships:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
    }
}
```

Plus Firebase's published consumer ProGuard rules (they ship with the
SDK; no extra file needed for stock usage). Expected size drop on
unminified→R8 realBackend release: 40–60% — well under the SC-006 target
of mockBackend + 3 MB, with budget to spare for spec 008/009 additions.

**Verification gate** when R8 is flipped on: re-run `assembleRelease` for
both flavors, re-measure, and update this row to ✓ in the table above.

## T105 — cold start (deferred)

**Why deferred**: Macrobenchmark needs:
- a real (or Pixel-class API 34) Android emulator booted *outside* the
  test JVM,
- a release build of the app installed,
- a Macrobenchmark module at `:macrobenchmark/` (does not exist yet —
  spec 004/006 referenced the infrastructure but no module shipped),
- ≥5 iterations to reach a stable median.

**Acceptance gate**: median ≤650 ms; p95 ≤750 ms — both measured on
medium-tier emulator (Pixel 6 API 34, 4 GB RAM).

**Exit ramp**: add `:macrobenchmark/` module wired to `:app:realBackend`,
copy the canonical `StartupBenchmark` skeleton from
`androidx.benchmark.macro.junit4`, run on Pixel 6 emulator. ~1 day work.

## T106 — pairing latency (deferred)

**Why deferred**: requires
- 2 distinct emulators running concurrently (managed + admin),
- a real Firebase project OR Firestore Emulator reachable from both,
- manual stopwatch or instrumented timestamps at QR-scan and Consent
  appearance events.

**Acceptance gate**: median ≤10 s on 4G/cellular profile.

**Exit ramp**: this measurement piggy-backs on the T110 two-emulator
smoke test — once that runbook executes successfully on JDK 21, record
the deltas at: `tap toggle` → `QR shown` → `admin scan` → `consent
shown`. Sum is SC-001.

## T107 — Worker p95 latency (deferred)

**Why deferred**: requires
- the Worker deployed (T069 — not run on this branch; awaits `wrangler login`
  approval — see `docs/dev/project-backlog.md`),
- a k6/Artillery harness producing 100 sustained req/s.

**Acceptance gate**: p95 ≤500 ms.

**Exit ramp**: after T069 lands (deploy via `wrangler deploy`),
run a 60-second 100 RPS k6 script against the production endpoint with a
test linkId. Sample script lives in
[`push-worker/README.md`](../../push-worker/README.md) §«Load testing».

## Snapshot of what *is* checked today

These act as a partial perf safety net while T105–T107 are blocked:

| Test                                                                          | Tier              | Asserts                                            |
|-------------------------------------------------------------------------------|-------------------|----------------------------------------------------|
| `PairingServiceTest` (5 cases)                                                 | commonTest        | FSM transitions complete in ≤500 ms each in `runTest` |
| `PairingEndToEndTest`                                                          | commonTest        | full pair + config + push receive ≤500 ms in `runTest` |
| `push-worker/test/worker.test.ts` (10 cases)                                   | vitest            | Worker happy/sad paths under 50 ms per test         |
| `FakeRemoteSyncBackendBehaviorTest`                                            | commonTest        | offline queue replay correctness                    |

These are not direct perf benchmarks — they are correctness tests that
incidentally show the domain code's intrinsic latency is well under the
SC budgets. Real perf numbers still come from T105–T107 once unblocked.
