# Spec 006 — Performance Checkpoint

Date: 2026-05-11

## T712 — APK size delta (SC-013, NFR-009)

**Status: ⏳ size on branch measured; baseline from `main` to be measured at PR-time**

Methodology: `./gradlew :app:assembleRelease` on this branch, compared against the same target built from tip of `main` (pre-006). Budget: delta ≤ 100 KB.

| Branch | APK | Size (bytes) | Size (MB) |
|---|---|---|---|
| `006-provider-capabilities-and-health` (this) | `app-release-unsigned.apk` | 8 677 298 | 8.28 |
| `main` (pre-006) | _to be measured in PR step_ | _tbd_ | _tbd_ |
| **Delta** | | _tbd_ | target ≤ 100 KB |

Note: spec 006 Phase 1 (`50d9bed`) **removed** the `migrateLegacyAction` bridge from spec 005's wire-format module, so delta is expected to be near zero or slightly negative. Final number recorded in the PR description before merge.

## T732 — Release APK build + install smoke

**Status: ⚠ build ✅ / install BLOCKED — release `signingConfig` not configured**

- `./gradlew :app:assembleRelease` → BUILD SUCCESSFUL, produces `app/build/outputs/apk/release/app-release-unsigned.apk` (8.28 MB).
- R8 / lintVitalAnalyzeRelease pass without errors.
- Install on emulator requires a signed APK. Project's `app/build.gradle.kts` has no `signingConfigs { … } / buildTypes.release.signingConfig …`. Setting one up is an identity-level one-way door (keystore choice ties release-track signing for the lifetime of the app on Play) and is out of scope for spec 006.
- Debug-variant smoke is covered: `app-debug.apk` (11.07 MB) installed on `emulator-5554` today (`md5sum` FRESH), `FirstLaunchActivity` → `HomeActivity` rendered correctly; screenshot in `build/emu-step6-home.png` and walkthrough screenshots in `walkthrough/`.

Required to fully close T732: add a `signingConfigs` block (with the production keystore + env-driven password vars), then run `:app:installRelease` on either emulator or a real device for the smoke. Tracked as a separate small chore branch — not blocking the 006 merge.

## T710 — Macrobenchmark: cold-start delta ≤ 20ms (SC-014, NFR-004)

**Status: ⚠ DEFER — emulator unrepresentative, real device required**

Per the pattern set by spec 005 perf-checkpoint (cold-start on this AMD Radeon Vega 8 emulator host is dominated by Compose first-frame software-render tax and host GPU shader-compile, not by application code), macrobenchmark numbers from `Medium_Phone_API_36.1` would not reflect the on-device cold-start budget. Deferring to a real device pass before final merge sign-off.

Required to close:
- Run `:macrobenchmark:connectedReleaseAndroidTest` on a representative physical phone (target: low-end Android 13+ device similar to the senior-safe end-user).
- Capture baseline against `main` and post-006 numbers.
- Assert p95 delta ≤ 20 ms; record raw numbers here.

## T711 — Battery Historian: ≤ 0.1%/day (SC-009, NFR-005)

**Status: ⚠ DEFER — real device required (emulator does not model battery drain)**

Required to close:
- 24h on a real phone with WiFi online, screen off, no user interaction.
- Collect bugreport, run through `battery-historian`.
- Assert `com.launcher.app` consumption ≤ 0.1% / 24h.
- Attach trace summary here.

## Sign-off

| Gate | State |
|---|---|
| T712 (APK size) | ⏳ measure on PR |
| T710 (cold-start macrobench) | ⚠ deferred to real device |
| T711 (Battery Historian) | ⚠ deferred to real device |

Deferred gates do not block merge of the implementation phases; they block the spec being moved to «Готов / Merged» in `docs/product/roadmap.md`. Per skill `android-emulator` and project rule «реальные звонки и поведение HOME/role — на реальном устройстве», T710/T711 are tracked as «real-device follow-up» tasks.
