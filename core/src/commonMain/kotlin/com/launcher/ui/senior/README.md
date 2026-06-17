# `com.launcher.ui.senior` — Senior-safe Compose primitives

**Spec**: [015 — Wizard + Localization + Senior UI](../../../../../../specs/015-wizard-localization-senior-ui/spec.md)

**Status**: EXTRACT CANDIDATE per FR-042. Today it's a package in `:core`.

## What's inside

Compose Multiplatform primitives that enforce the senior-safe baseline:

- `SeniorButton` / `SeniorSecondaryButton` / `SeniorIconButton` — ≥56dp tap
  target, ≥18sp text, wrap-content sizing tolerant of translated strings.
- `SeniorBodyText` / `SeniorTitleText` — ≥18sp / ≥24sp with 1.5× line height.
- `SeniorTextField` — ≥56dp.
- `SeniorWarmTheme.Light` / `Dark` — Material 3 theme with WCAG AAA ≥7:1
  contrast on warm terracotta primary.
- `progress/WizardProgressIndicator` — "Шаг N из M" + dots, ≥18sp, FR-008c.
- `progress/LiveRegionAnnouncement` — TalkBack live region for state-change
  announcements, FR-008b.
- `overlay/TutorialHintOverlay` — anchored dismissible hint bubble,
  FR-023.
- `util/AnimationDuration.scaledDurationMillis()` — wraps
  `AnimationPreferenceProvider.durationScale()` for reduce-motion support,
  FR-036a.
- `util/FontScaleAware.rememberFontScaleAware()` — read effective font scale.

## Architectural rules

Enforced by `Spec015IsolationTest.ui_senior_does_not_depend_on_api_wizard()`:

- `ui.senior.*` MUST NOT depend on `com.launcher.api.wizard.data.*` (wire
  formats), `ConfigSource`, `WizardEngine`, `WizardCheckpoint`,
  `SystemSettingPort`, `UserPreferencesStore`, or `DismissedHintsStore`.
  Primitives stay reusable in S-1+ home screens too.
- `ui.senior.*` MAY depend on `AnimationPreferenceProvider` — that's a
  generic cross-cutting port.

## Snapshot tests (Roborazzi)

Per spec 015 T094-T096, the senior primitives have Roborazzi screenshot
tests in
[`core/src/androidUnitTest/kotlin/com/launcher/ui/senior/SeniorPrimitivesScreenshotTest.kt`](../../../../../androidUnitTest/kotlin/com/launcher/ui/senior/SeniorPrimitivesScreenshotTest.kt).

Run:

```bash
# First run / after intentional UI changes — records new baselines.
./gradlew :core:recordRoborazziMockBackendDebug

# CI gate — fails on any pixel diff against the baselines.
./gradlew :core:verifyRoborazziMockBackendDebug
```

Snapshots live under `core/src/androidUnitTest/snapshots/` and are committed
to git. Roborazzi runs in JVM via Robolectric — no emulator required.

## iOS support

Compose Multiplatform renders these primitives on iOS automatically (per
ADR-005). No separate iOS UI code is required for the primitives themselves.
Real-device iOS rendering is verified later when the iOS launcher consumer
ships.

## Exit ramps

- TODO(extract): when a sister-app starts importing these primitives,
  extract `:core` into `:core-ui-senior` per FR-042.
