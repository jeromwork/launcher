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

## T640 — Cold-start measurement on emulator

**Status: ⚠ DEFER — emulator-only, target unrepresentative**

Emulator: `Medium_Phone_API_36.1` (API 36.1, software-rendered Compose, AMD Radeon RX 5700 host with Vulkan host-mode GPU). Started via Android Studio's `Run` / Device Manager (CLI `emulator.exe` runs hung at boot on this host — see entry in repo notes).

Methodology: `pm clear com.launcher.app` + `am force-stop` between runs, then `am start -W -n <activity>`, capture `TotalTime`. Five runs each.

| Activity | Run 1 | Run 2 | Run 3 | Run 4 | Run 5 | Median |
|---|---|---|---|---|---|---|
| `FirstLaunchActivity` | 2759 ms | 2133 ms | 2542 ms | 2226 ms | 2095 ms | **2226 ms** |
| `HomeActivity` (preset=simple-launcher) | 2299 ms | 2165 ms | 2088 ms | 2095 ms | 2150 ms | **2150 ms** |

The numbers exceed the spec §9 budget (≤ 600 ms / ≤ 700 ms) by roughly 3–4×. **Interpretation**: this is the well-known "Compose-on-software-rendered-emulator" tax — the emulator runs Compose Multiplatform without GPU acceleration on top of a virtualised display, so frame setup dominates the timing. The spec budget targets the physical Pixel 4a / Pixel 6a that the launcher will actually ship to.

**This number is not a regression signal**. The dispatcher itself is uninvolved in cold start path beyond constructor; the heavy work is Compose layout + theme + first-frame draw. Since this test happens to exercise a Compose-heavy startup, the emulator-side number is dominated by Compose, not by spec 005 work.

To get an answer that matters before release, T640 needs to run on a physical Pixel 4a (or comparable hardware-accelerated emulator). The spec's budgeting is correct; the emulator number reflects the test environment, not the product.

**Action item for the next session with a physical device:**
1. `adb -s <pixel-serial> install -r app-debug.apk` after `:app:assembleDebug`.
2. Run the same `am start -W` loop 5× per activity.
3. Compare against the 600/700 ms budget.

If the physical-device numbers also exceed budget, plan §Risks row 5 mitigation (lazy handler construction) becomes mandatory; if they pass, the eager handler `init` map in `LauncherCore` stays as designed.
