# Checklist: state-management

Applied to: `specs/task-126-wizard-runtime-migration/spec.md`
Reference: [CLAUDE.md](../../../CLAUDE.md) rule 4; constitution Article IV §5, §III.3.

**State inventory (TASK-126 introduces or rewrites):**

| # | State piece | Scope | Storage |
|---|-------------|-------|---------|
| S1 | `ReconcileState` (Loading/Interactive/Done) | Screen — `WizardViewModel` | In-memory `StateFlow` |
| S2 | `WizardStore.lastCompletedStepIndex: Int` | Persistent device-local | DataStore (new) |
| S3 | `Profile` (component statuses) | Persistent device-local | `ProfileStore` / DataStore |
| S4 | `Profile.preWizardSnapshot` | Persistent device-local | Inside ProfileStore blob |
| S5 | `InteractionSink` in-flight user answer | Transient / in-coroutine | In-memory (coroutine suspension) |
| S6 | `PendingChecklistViewModel` UI state | Screen | `StateFlow` in ViewModel |
| S7 | Applied system state (font, locale, status-bar, launcher role) | Android OS level | OS (not in app storage) |

**Screens introduced / rewritten:**
- `WizardScreen` (Composable) — new, consumes `StateFlow<ReconcileState>` + `InteractionSink`
- `FirstLaunchActivity` — rewritten to call `PresetBootstrap` + `ReconcileEngine`
- Settings screens — migrated from `ConfigKind` → `Preset.settingsMap[]`

---

## Lifecycle events

- [x] **CHK001** Behaviour after Activity recreation (rotation, language change, dark/light theme switch) explicitly specified.
  - FR-001 states "WizardViewModel emits `ReconcileState.Loading` until `PresetBootstrap` completes initialization; wizard UI renders only after first non-Loading state emission." However, the spec does NOT explicitly state what happens when `FirstLaunchActivity` is recreated mid-wizard (rotation or config change).
  - The rescue is FR-008: "On resume, `ReconcileEngine` starts from `lastCompletedStepIndex + 1` but re-runs `check()` for each step." This applies on rotation as well — ViewModel survives rotation via `ViewModelStore` (standard Compose ViewModel lifecycle), `StateFlow` already carries current `ReconcileState`, and re-read of `lastCompletedStepIndex` from `WizardStore` gives the correct resume point.
  - **Partial** — the spec describes resume-after-process-death semantics but does not explicitly call out that rotation reuses the same ViewModel instance (no re-run of `bootstrap()`). Relying on `ViewModel` survival without stating it is an implicit assumption. Should be one explicit sentence. Carry as plan-level note.
  - `Language` Component applies via `AppCompatDelegate.setApplicationLocales()` — a locale change triggered by ReconcileEngine could trigger Activity recreation. The spec does not address the re-entry path: Activity recreated by the locale change it just applied, `ReconcileState.Done` → does `WizardScreen` try to navigate again? Risk of double-navigate if `Done` callback is triggered on recreated Activity. **Gap — needs explicit handling in plan.md.**

- [x] **CHK002** Behaviour after process death (system kill while in background) specified.
  - PASS. US-3 (Persistence через force-close) is a dedicated user story with explicit acceptance scenarios: "force-close on step 1 → reopen → wizard shows step 2 (step 1 Applied in ProfileStore)". FR-008: `WizardStore` stores `lastCompletedStepIndex`; on resume ReconcileEngine starts from that index. SC-3 success criterion is precisely this.
  - What is restored: ProfileStore (component statuses) + WizardStore (step index).
  - What is lost: S5 (transient InteractionSink answer for the current in-flight Interactive step) → step shown again (US-3 scenario 2). ✓
  - User-visible behaviour: wizard reopens at same step (Interactive) or next step (AutoApply already applied). ✓

- [x] **CHK003** Behaviour after low-memory kill (foreground process trimmed) specified.
  - PASS by equivalence. The spec does not explicitly distinguish low-memory kill from force-close, but the mechanism is identical: `ProfileStore.save(profile)` is called after every step (ReconcileEngine.runWizard saves after each iteration, confirmed in source). Low-memory kill = process death between saves — same resume behaviour as US-3. Edge Case "BootCheck" + US-3 cover this. Explicit equivalence statement not present but implementation makes it equivalent.

- [x] **CHK004** Behaviour after device reboot specified.
  - PASS. US-4 (BootCheck после reboot) is a dedicated P2 user story. `BootCheckReceiver` runs `ReconcileEngine.run(RunMode.BootCheck)`, only `critical: true` components re-applied. FR-012 mandates this explicitly. SC-4 is the corresponding success criterion.

## State scope

- [x] **CHK005** For each piece of state: scope explicitly chosen.
  - S1 (`ReconcileState`): screen-scoped via ViewModel `StateFlow`. ✓ (FR-008)
  - S2 (`WizardStore`): persistent device-local DataStore. FR-008 explicitly mandates "WizardStore MUST be distinct from ProfileStore". ✓
  - S3 (`Profile`): persistent device-local via `ProfileStore` port. ✓ (FR-013 in TASK-120, continued)
  - S4 (`preWizardSnapshot`): persistent inside Profile blob. ✓ (FR-011 + TASK-120 architecture)
  - S5 (transient InteractionSink answer): in-coroutine suspension, intentionally lost on process death (US-3 scenario 2 specifies this is acceptable). ✓
  - S6 (PendingChecklistViewModel): standard ViewModel `StateFlow`, no explicit scope statement but conventional. Partial — FR-009 says migrate from ConfigKind but does not specify state survival on Settings recreation.
  - S7 (Android OS state): Lives in OS, Provider.check() queries on every resume (FR-008). ✓

- [ ] **CHK006** No use of process-singleton state for things that should be screen-scoped.
  - **Gap**: `WizardStore.lastCompletedStepIndex` is persistent DataStore — broader scope than screen. This is intentional (it needs to survive process death), but the spec does not address the **cleanup** case: after wizard completes, `WizardStore` must be cleared/reset. Otherwise a subsequent walkthrough or re-run of wizard starts at the wrong step. FR-008 and FR-016 do not specify when `lastCompletedStepIndex` is reset (on wizard completion? on preset change? on factory reset assumption). **Gap — needs explicit reset semantics in FR-008.**
  - No other singleton-vs-screen scope violations detected.

- [x] **CHK007** No use of `rememberSaveable` for non-trivial / large objects (Bundle limits).
  - Spec mandates `StateFlow<ReconcileState>` (not `rememberSaveable` for engine state). `WizardViewModel` holds the `ReconcileEngine` reference and exposes `StateFlow` — ViewModel itself is NOT parceled. No `rememberSaveable` for Profile or ReconcileState prescribed. N/A risk for Bundle overflow. ✓

## Recreation correctness

- [ ] **CHK008** No "first-only" navigation logic that skips on recreation.
  - **Gap**: FR-001 "wire `FirstLaunchActivity.onCreate()` → `PresetBootstrap.bootstrap()`". `bootstrap()` is called in `onCreate`. If Activity is recreated (rotation), `onCreate` fires again. If `WizardViewModel` is NOT used to scope this call, `bootstrap()` runs twice. The existing `FirstLaunchActivity` implementation uses `lifecycleScope.launch` in `onCreate` directly (not ViewModel-scoped), which means rotation = duplicate bootstrap call.
  - TASK-126 introduces `WizardViewModel` (task 2.4) to wrap ReconcileEngine — if `bootstrap()` is called inside `WizardViewModel.init { }` this is safe (ViewModel survives rotation). But the spec says "wire `FirstLaunchActivity.onCreate()` → `PresetBootstrap.bootstrap()`" without specifying ViewModel init vs Activity onCreate. The current `FirstLaunchActivity` code (which TASK-126 rewrites) calls everything in `lifecycleScope.launch` from `onCreate` — this is the recreate-bug pattern.
  - **Needs explicit statement**: "bootstrap() MUST be called inside WizardViewModel's init block, not in Activity.onCreate()" — or equivalent guarantee. Plan.md item.

- [ ] **CHK009** Form input survives rotation without re-querying network/disk.
  - **Gap**: S5 (InteractionSink in-flight answer for an Interactive step) is held in a coroutine suspension, not `rememberSaveable` or ViewModel state. On rotation, `WizardScreen` Composable is recomposed. If the Interactive step renders a user-selection widget (font size slider, contact picker), the user's mid-step selection must survive rotation via either:
    - (a) `rememberSaveable` inside the step Composable, OR
    - (b) ViewModel holding the partial answer.
  - Neither is specified. FR-008 does not address transient Interactive step answer survival across recreation. The spec only says "wizard resumes from `lastCompletedStepIndex + 1`" on PROCESS DEATH — this clears the in-flight answer deliberately. But on ROTATION (no process death), losing a half-entered font-scale value is an unnecessary regression.
  - **Needs a sentence in FR-008**: "WizardViewModel MUST hold the current Interactive step's user selection in a `MutableStateFlow` that survives rotation (ViewModel instance survives config change)."

- [x] **CHK010** In-flight async operations survive recreation OR are cancelled+restarted predictably.
  - PASS. `ReconcileEngine.runWizard()` is a `suspend fun` that saves after each step. On rotation: if `WizardViewModel` scopes the coroutine (`viewModelScope`), the coroutine survives recreation (ViewModel is retained). If `bootstrap()` is mistakenly called from `lifecycleScope` (Activity scope), rotation cancels the coroutine and `PresetBootstrap` re-runs from scratch — but `ProfileStore` has the saved progress, so the worst case is a re-run starting at step 1 but immediately skipping Applied steps. US-3 behaviour. Acceptable but non-ideal.
  - Decision documented: "On resume, ReconcileEngine starts from `lastCompletedStepIndex + 1`" (FR-008). ✓

## Configuration changes

- [x] **CHK011** Locale change handled: strings re-resolved, no stale rendered text.
  - PASS. FR-004 (`Language` Component) applies locale change via `AppCompatDelegate.setApplicationLocales()`. This triggers an Activity recreation for locale-aware string resolution — standard Android pattern.
  - Post-wizard: locale applied post-wizard (CL-2, Assumption "Wizard works as standard Android app" — kiosk settings apply on home screen transition). ✓
  - During wizard: `wizardPresentation` field in Preset controls wizard appearance (FR-003), not the `Language` Component — wizard itself does not change locale mid-flow. ✓
  - Edge case: locale applied by `Language` Component triggers recreation → see CHK001 gap about double-navigate risk.

- [x] **CHK012** Density / font-scale change handled: layout doesn't break.
  - PASS (explicit deferral). Assumption "Wizard works as standard Android app" — wizard UI uses SplashScreen + standard Compose layout, no custom density overrides. `FontSize` Component changes app-level font scale post-wizard, not mid-wizard. During wizard, OS font scale remains unchanged, so layout resizing follows standard Compose behavior.
  - `StatusBarPolicy` hides the status bar post-wizard (CL-2) — font-scale change during wizard does not interact with kiosk settings.
  - Split-screen and foldable: same as TASK-120 — not a real concern for a launcher app targeting elderly users on MIUI devices. No explicit exclusion statement in spec. Plan.md should add one sentence.

- [x] **CHK013** Window size change (split-screen, foldable) handled OR explicitly out-of-scope per spec.
  - PASS (implicit exclusion). Launcher apps occupy the full screen and are typically `singleTask`; split-screen during first-launch wizard is not a realistic scenario for the target devices (Xiaomi Redmi Note 11, elderly use case). Spec does not address it but the target hardware + app role makes this N/A in practice. The spec should state this once explicitly — carry as a one-liner for plan.md: "Split-screen and foldable: out of scope. App targets full-screen launcher mode."

## Tests

- [ ] **CHK014** Each US that touches state has at minimum one recreation test.
  - US-1 (first launch): `PresetBootstrapTest` covers bootstrap → profile creation, but NOT Activity recreation mid-wizard. No `StateRestorationTester` test declared for `WizardScreen`. **GAP.**
  - US-3 (force-close persistence): Robolectric test declared in spec: "ProfileStore → destroy process → load → assert Applied status". This is the closest to a process-death simulation but is unit-level, not a Compose recreation test. **Partial.**
  - US-5 (Settings edit): `PendingChecklistViewModelTest` declared but no recreation test mentioned. **GAP.**
  - Missing: a `StateRestorationTester` or Compose recreation test for `WizardScreen` mid-flow. Should be declared in plan.md under Phase 2.

- [x] **CHK015** At least one process-death simulation test for any feature with persistent state.
  - PASS. US-3 Independent Test: "Robolectric — создать Profile с 3 шагами, сохранить через ProfileStore, уничтожить process, загрузить снова, проверить что `status=Applied` у шага 1 сохранился." This is an explicit process-death simulation for S3. SC-3 is the corresponding success criterion.
  - `WizardStore.lastCompletedStepIndex` persistence: not covered by a separate process-death test. Carry as plan item.

## Edge cases

- [x] **CHK016** Multiple instances of the same Activity (multi-window) — behaviour documented or exclusion stated.
  - PASS (implicit). `FirstLaunchActivity` routes to `HomeActivity` with `FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK` — stack is cleared after wizard completes. This prevents multi-instance wizard. Launcher apps are typically `singleTask`. Assumption "One active Preset per device" eliminates the concurrent-wizard scenario.

- [x] **CHK017** Feature accessed from notification while killed — entry path tested.
  - PASS (explicit deferral). No push notification entry points defined for TASK-126. The only entry point is `BOOT_COMPLETED` broadcast → `BootCheckReceiver` (US-4), which is a `BroadcastReceiver` not an Activity, and has no state-restoration issue. Admin push (RemotePush RunMode) is wired through `ReconcileEngine` but not via notification in TASK-126 scope. Out-of-scope entry paths deferred to TASK-27 (messenger) and future admin push integration.

---

## Summary

Total gates: 17. **Passed: 11. Open issues: 5 (CHK006, CHK008, CHK009, CHK014) + 1 partial (CHK001).**

| Gate | Status | Issue |
|------|--------|-------|
| CHK001 | PARTIAL | Rotation reuses ViewModel (implicit); locale-change-triggered recreation double-navigate risk not addressed |
| CHK006 | FAIL | `WizardStore.lastCompletedStepIndex` reset semantics not specified — when is it cleared after wizard completes? |
| CHK008 | FAIL | `bootstrap()` must be in `WizardViewModel.init`, not `Activity.onCreate` — spec wording is ambiguous; recreation = duplicate bootstrap if wired wrong |
| CHK009 | FAIL | In-flight Interactive step user selection survival on rotation not specified; need ViewModel-held `MutableStateFlow` for transient step answer |
| CHK014 | FAIL | No `StateRestorationTester` / Compose recreation test declared for `WizardScreen` mid-flow; `WizardStore` process-death test not declared |

**Blockers (must fix before Phase 2 implementation starts):**
- CHK008: Clarify `bootstrap()` call site → `WizardViewModel.init` (not `Activity.onCreate`). Add to FR-001 or plan.md.
- CHK006: Add WizardStore reset semantics to FR-008: "reset `lastCompletedStepIndex` to -1 on `ReconcileState.Done`".

**Plan-level items (carry to plan.md):**
- CHK001: Add explicit statement "WizardViewModel is retained across Activity recreation; bootstrap() is called at most once per ViewModel lifetime."
- CHK009: Add to Phase 2 plan — WizardViewModel holds current step's partial user-selection in a `MutableStateFlow<UserResponse?>`.
- CHK014: Add Compose recreation test to Phase 2 plan: rotate device mid-wizard-step, verify step index is preserved and Interactive UI is re-rendered.
- CHK013: One-liner in Assumptions or FR: "Split-screen / foldable: out of scope; app targets full-screen launcher mode."
