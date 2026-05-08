# Spec 005 — Smoke Checkpoint

Date: 2026-05-08

## Status: ⚠ NOT RUN IN THIS SESSION

T642 (two-emulator smoke) and T643 (TalkBack walkthrough) require running emulators and visual / aural verification by a human operator. They are explicitly out of scope for the autonomous code-only execution that produced everything else in this spec.

## What automated coverage already gives us

- 142 unit tests in `:core:testDebugUnitTest` are green, including:
  - 7 wire-format roundtrip tests covering every payload variant.
  - On-disk fixture tests (`ActionWireFormatFixtureTest`) — 18 fixture files parsed and asserted.
  - 6 handler tests with `Intent.filterEquals` against captured `startActivity` calls.
  - 6 dispatcher integration tests covering spec §7.1 algorithm.
  - 5 wizard UI tests (`WizardScreensTest`) covering US-507's five availability states.
  - 4 `FlowComponent` unit tests for tap/retry/acknowledge plumbing (US-508).
  - 1 `TileCard` UI test for the 500 ms debounce (US-508).
  - 4 fitness functions: domain isolation, residue grep, legacy-bridge expiry, event taxonomy.

This gets us to "dispatch is correct, intents are well-formed, snackbar/retry plumbing is wired"; it does **not** verify that real WhatsApp / Telegram / YouTube apps respond to the intents we build, that the snackbar is actually visible on a real screen, or that TalkBack reads the wizard rows in a sensible order.

## To execute (carry-over for the next session)

### T642 — Two-emulator smoke

Per `android-emulator` skill: run on both `workspace` and `simple-launcher` presets.

1. Boot two Pixel 4a emulators.
2. `:app:installDebug` on each, then launch.
3. Per emulator:
   - Pick preset on FirstLaunch.
   - On Home: tap WhatsApp tile → confirm (no longer present in spec 005 — direct dispatch) → handoff observed (intent fires; absent WhatsApp → fallback to phone dialer per the mock JSON).
   - Tap phone tile → `ACTION_DIAL` screen visible.
   - Tap browser tile → URL opens in default browser.
4. On `simple-launcher`: load a flow with a `Custom("smart_assistant", …)` payload (test fixture) → snackbar "feature unavailable in this version" (forward-compat).

Document outcomes inline above each step, with screenshots if the emulator rendering differs from expectations.

### T643 — TalkBack walkthrough on AddSlotWizardScreen

1. Enable TalkBack on the Pixel 4a.
2. Open `AddSlotWizardScreen` via `Add Slot` flow.
3. Swipe forward through every provider row; verify:
   - Available providers announce their label only (no "missing" / "not applicable" suffix).
   - Missing providers announce the label + "установить" + recommended package.
   - NotApplicable providers announce the label + "недоступно" + reason.
4. Confirm the primary action ("Готово") is reachable in ≤ 3 swipes from the wizard top.
5. Document any contentDescription gaps; ADR-005 senior-safe contract requires that no element be reachable only by sight.
