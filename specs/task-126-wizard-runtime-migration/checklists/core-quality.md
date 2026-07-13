# Checklist: core-quality — task-126 Wizard Runtime Migration

Google Core App Quality + Android Vitals alignment.

Spec: `specs/task-126-wizard-runtime-migration/spec.md` (Draft, 414 lines).

**Context**: refactor collapsing 3 engines into 1. No new user-facing feature; UX must be visually identical to pre-migration (NFR-004). Removes `AccessibilityService` (positive for Play Store compliance, FR-005). Adds fitness-function lint rule FF-011.

## Visual experience

- [x] CHK001 Material Design + senior-safe overrides. Inherited from TASK-120; NFR-004 pins visual identity. No new deviation.
- [ ] CHK002 Light + dark theme. **WARN**: FR-003 defines `Theme.darkMode: Boolean` + `wizardPresentation.darkMode`. Spec says wizard theme applies once at start; but does not confirm both light and dark are tested/verified against the Xiaomi screenshot. Home-screen post-wizard theme untouched.
- [ ] CHK003 Edge-to-edge / insets. **FAIL**: FR-005 introduces `StatusBarPolicyProvider` that hides status bar via `WindowInsetsControllerCompat.hide(statusBars())`. No mention of edge-to-edge / gesture-inset handling for Android 15+ where opting out of edge-to-edge is deprecated. MIUI fallback uses `FLAG_FULLSCREEN` — deprecated on newer Android. Behaviour on API 35+ not specified.
- [ ] CHK004 Foldable / large-screen. **WARN**: not addressed. Since this is a refactor with UX identity target, inherits TASK-120 foldable posture (likely unhandled but pre-existing).

## Functional

- [x] CHK005 Works without internet. Fully offline: `PresetBootstrap` reads bundled assets; `WizardStore` device-local; no network dependency introduced (Assumptions).
- [ ] CHK006 Configuration changes / locale / dark mode. **FAIL**: FR-004 `AppCompatDelegate.setApplicationLocales()` triggers Activity recreation mid-wizard. Behaviour undefined — see localization.md CHK017 / localization-ui.md CHK-UI-017. Dark-mode toggle mid-wizard not addressed either (FR-003: `wizardPresentation` applied once, does not react to runtime toggle).
- [x] CHK007 Doze / App Standby. `BootCheckReceiver` triggered by `BOOT_COMPLETED` (system broadcast, exempt from Doze at boot). No background alarms/jobs introduced; feature is one-shot on boot + user-driven for wizard.
- [ ] CHK008 Multi-window / split-screen. **WARN**: not addressed. Given `LauncherRole` + hidden status bar, launcher likely runs full-screen — split-screen probably N/A but spec silent.

## Performance (cross-check with checklist-performance)

- [x] CHK009 ANR < 0.47%. Bootstrap emits `Loading` state; async off-main (CL-1). NFR-001 pins engine cold-start ≤ 30ms. Low ANR risk. See performance.md CHK008 open item on BootCheckReceiver.
- [x] CHK010 Crash rate < 1.09%. `PresetValidator` fail-fast on invalid preset (FR-006) — deliberate throw at load time (admin/dev-facing case). Force-close/resume documented (US3). Edge Cases enumerate Koin, MIUI, missing hint-pool — all handled.
- [x] CHK011 Battery / wakeups. No new wake locks, no new alarms, no polling (performance.md CHK019/CHK020 PASS).

## Privacy / Play policy

- [x] CHK012 Data Safety section. Refactor collects no new data; `WizardStore` and `ProfileStore` are device-local (Assumptions). Zero-knowledge posture inherited.
- [x] CHK013 No prohibited content / SDK. Removes SDK surface: `AccessibilityService` deleted (FR-005) — **positive** for Play policy (accessibility-service abuse policy).
- [x] CHK014 Restricted permissions. Removes `uses-accessibility-service` manifest declaration (FR-005) — one restricted-permission-class removed. `LauncherRole` uses default-home role assignment intent, not a restricted permission per Play policy.

## Compatibility

- [x] CHK015 minSdk / targetSdk. Not modified by this spec; inherits project baseline. **WARN**: FR-005 `WindowInsetsControllerCompat` was added in API 30 with compat lib; MIUI `FLAG_FULLSCREEN` fallback covers older. Verify targetSdk 35+ status bar semantics at plan time.
- [x] CHK016 Medium-tier + OEM tested. NFR-001 baseline Pixel 5 (medium-tier). SC-1/SC-2 explicit on Xiaomi Redmi Note 11 (OEM). SC-8 = `connectedMockBackendDebugAndroidTest --tests "*E2E*"` on Xiaomi.

## Distribution

- [x] CHK017 Feature flag / staged rollout. Refactor with **no production users** (D1, Assumptions); no rollout risk. FR-017 deletes legacy assets outright — clean cut, no toggle needed.
- [ ] CHK018 Crash reporting / Vitals dashboards. **WARN**: spec is silent on whether new code paths (`ReconcileEngine` in wizard mode, `PresetValidationException` throwers, MIUI fallback branch) are tagged for Vitals observability. Consistent with failure-recovery.md CHK016 open item on diagnostics policy.

---

## Summary

- **Pass**: 11/18
- **Warn**: 4/18 (CHK002 dark theme verification, CHK004 foldable, CHK008 multi-window, CHK015 targetSdk 35+ semantics, CHK018 Vitals wiring)
- **Fail**: 3/18 (CHK003 edge-to-edge on API 35+, CHK006 config-change / locale / dark-mode mid-flow)

**Verdict**: net-positive for Play Store compliance (`AccessibilityService` removed). Two real gaps to close before implementation:
1. Edge-to-edge / gesture-inset behaviour on Android 15+ for `StatusBarPolicyProvider` — deprecated `FLAG_FULLSCREEN` needs replacement path documented.
2. Mid-wizard configuration change (locale, dark mode, orientation) UI behaviour — currently undefined.

Cross-checks with performance.md (CHK008 receiver dispatch), failure-recovery.md (CHK012/CHK013 permission-denied path, CHK016 diagnostics), and localization-ui.md (CHK-UI-017 recreation) — all downstream of the same undefined-lifecycle question.
