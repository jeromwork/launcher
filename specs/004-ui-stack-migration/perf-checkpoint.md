# Performance Checkpoint — Spec 004 (T412)

**Date**: 2026-05-07
**Branch**: `004-ui-stack-migration`
**APK**: `app/build/outputs/apk/debug/app-debug.apk` (debug, unsigned)

## Targets (from plan.md / ADR-005)

| Metric | Budget |
|---|---|
| `HomeActivity` cold start | ≤ 600 ms (medium-tier real device) |
| `FirstLaunchActivity` cold start | ≤ 700 ms (medium-tier real device) |
| Frame budget on Workspace grid scroll | 0 dropped frames |
| APK debug size | ≤ 18 MB |
| APK release size | ≤ 12 MB |

## Environment

- Emulator: `Medium_Phone_API_36.1` (1080×2400, density 420, x86_64, Android 16)
- Host: Windows 10, software-rendered Vega 8 GPU (per emulator skill)
- Build: `./gradlew :app:assembleDebug`
- Install verified fresh via §5a md5 check.

## Measurements

### APK size — PASS

```
app-debug.apk = 15.17 MB
```

Within ADR-005 debug budget (≤ 18 MB). Release size deferred to spec 010
(release-readiness). Predicted release: 8–10 MB after R8/ProGuard.

### Cold start `FirstLaunchActivity` — DEFERRED-TO-DEVICE

`am start -W -n com.launcher.app/.firstlaunch.FirstLaunchActivity` after
`pm clear` (3 runs):

| run | TotalTime | WaitTime |
|---|---|---|
| 1 | 16708 ms | 16761 ms |
| 2 | 6400 ms  | 6409 ms  |
| 3 | 6865 ms  | 6877 ms  |

Run 1 is dominated by ART verification + first-time class loading on a cold
emulator process; runs 2–3 are representative of the steady-state cold path
on this software-rendered emulator. **Emulator cold start consistently runs
5–10× slower than medium-tier real-device cold start on this host** (known
property of the AMD-software-render setup, see emulator skill §5d). Real
device measurement is required to validate the 700 ms budget; deferred to
T412-followup once a physical device is provisioned (tracked separately —
no real device is in scope for this spec).

### Cold start `HomeActivity` — DEFERRED-TO-DEVICE

After preset selection seeds the Home path, `am start -W -n
com.launcher.app/.HomeActivity` (3 cold runs after `force-stop`):

| run | TotalTime | WaitTime |
|---|---|---|
| 1 | 7226 ms | 7275 ms |
| 2 | 6948 ms | 6961 ms |
| 3 | 6664 ms | 6749 ms |

Same caveat as above — emulator overhead. Validation against the 600 ms
budget deferred to real-device measurement.

### Warm start `HomeActivity` — INDICATIVE-PASS

Process kept alive, navigation back to home via `KEYCODE_HOME` and
re-`am start` (3 warm starts):

| run | TotalTime | WaitTime |
|---|---|---|
| 1 (1st warm)  | 1038 ms | 1081 ms |
| 2 | 475 ms | 527 ms |
| 3 | 514 ms | 547 ms |

Runs 2–3 fit the 600 ms budget even on this emulator. This is the most
useful relative signal we can get without a real device — the steady-state
recomposition path is fast; the cold path is dominated by the emulator's
class-loading overhead which evaporates on real hardware.

### Frame budget on Workspace grid scroll — INDICATIVE-FAIL-EMULATOR / DEFERRED

`dumpsys gfxinfo com.launcher.app` after 5 scroll-up/down cycles on the
Workspace grid (mock data: 2 slots in the active flow):

```
Total frames rendered: 21
Janky frames: 21 (100.00%)
50th percentile: 57 ms
90th percentile: 1100 ms
95th percentile: 3350 ms
99th percentile: 3750 ms
Number Missed Vsync: 10
Number Slow UI thread: 15
```

100% jank on the emulator's software renderer is expected for this host
(see skill §5d: AMD Vega 8 + Android 16 system image consistently misses
vsync on Compose-heavy frames). Frame budget verification deferred to
real-device measurement; the small frame count (21) also reflects that
the test seed has only 2 slots — full Workspace-grid scroll perf will be
re-measured in spec 005 once richer flow data is wired.

## Conclusion

- **APK size** within budget (validated).
- **Cold/warm start and frame budget** are deferred to real-device
  measurement. The emulator-side measurements taken here serve as a
  baseline to track regressions inside CI, not as gate validation.
- Per ADR-005 §6 the cold-start gate is **medium-tier real device**.
  Provisioning that is out of scope for spec 004.
- **No optimization actions taken** (no Phase 6.5). The migration to
  Compose Multiplatform did not introduce any obvious red flags in the
  steady-state warm-path measurements; cold path verification remains a
  known-open follow-up tracked here.

## Follow-ups

1. Provision medium-tier real device, repeat cold-start measurements,
   update this checkpoint.
2. After spec 005 (action-architecture-v2) wires real flow data, re-run
   frame-budget measurement on a populated Workspace grid.
3. Consider adding a Macrobenchmark module (separate Gradle module) once
   the iOS spec lands — that's the appropriate place for repeatable
   cold-start measurement in CI.
