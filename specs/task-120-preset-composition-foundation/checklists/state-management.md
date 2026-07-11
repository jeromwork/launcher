# Checklist: state-management

Spec: [spec.md](../spec.md)
Reference skill: [.claude/skills/checklist-state-management/SKILL.md](../../../.claude/skills/checklist-state-management/SKILL.md)

Note on scope: this spec is a **domain foundation** (`core/preset/` KMP commonMain, zero Android). It defines ports, wire formats, and engine semantics. UI (Wizard / Settings screens) is downstream (draft-1, TASK-69). Several Android-lifecycle gates therefore reduce to "explicitly out of scope" or "delegated to consuming spec", which is a legitimate outcome — but the spec must still SAY that, not stay silent.

---

## Lifecycle events

- [ ] CHK001 Behaviour after Activity recreation (rotation, language change, dark/light theme switch) explicitly specified.
  - FAIL. Spec references `WizardActivity/Composable`, `SettingsActivity/Composable`, `WizardViewModel`, `SettingsViewModel` in SEQ-1/SEQ-2 plan-level diagrams, so UI surface IS visible from this spec. Rotation / locale / theme switch behaviour of Wizard mid-flow is neither specified nor explicitly deferred. `WizardViewModel.onStart` reloads preset + pool + rebuilds Profile — no statement whether this happens once per Activity instance or once per Profile lifetime; on rotation it would re-run and clobber user's Interactive answers unless a screen-scoped cache exists.

- [x] CHK002 Behaviour after process death (system kill while in background) specified — what is restored, what is lost, what is shown to the user.
  - PASS. US1 acceptance scenario #3 ("Wizard прерван, resume from first non-applied step") + FR-013 (persist after every step) + edge case "Wizard прерван на Interactive-шаге: partial state сохраняется в Profile, статус шага `Pending`, при resume — снова показывается". Restore semantics = ProfileStore replay; loss semantics = current Interactive step's transient answer. User-visible behaviour = Wizard restarts from first non-`Applied` step. Sufficient.

- [ ] CHK003 Behaviour after low-memory kill (foreground process trimmed) specified.
  - FAIL. Not distinguished from process death. In practice the domain-level "save after every step" (FR-013) makes low-memory kill behave like process death, which is fine — but the spec does not state this equivalence. A mid-Interactive-step low-memory trim between `check()` returning `NeedsApply` and the user answering leaves ambiguity: does the engine treat the step as `Pending` (edge case says yes) or re-issue `check()` on resume (unclear)? One sentence acknowledging equivalence with process death would close this.

- [x] CHK004 Behaviour after device reboot specified (if feature has any persistent state).
  - PASS. US4 (BootCheck) is a dedicated user story. `RunMode.BootCheck` reads `activeComponents`, runs `check()` on `critical: true` steps only, `apply()` on `NeedsApply`. Non-critical steps skipped. Failed → status stored for later admin visibility. Explicit.

## State scope

- [x] CHK005 For each piece of state: scope explicitly chosen — UI-local, screen, feature, or persistent.
  - PASS at domain level. `Profile` is persistent via `ProfileStore` port (DataStore) — FR-013. `preWizardSnapshot` is persistent inside Profile — FR-024. Pool is feature-scoped (loaded via `PoolSource`, effectively singleton per app process). Preset is feature-scoped. UI-local (`remember`) and screen-scoped (`rememberSaveable` / SavedStateHandle) not addressed but justifiable — no UI state defined in this foundation spec. If Wizard partial answers before step commit belong to screen scope, that is a downstream (draft-1) call, not this spec's.

- [x] CHK006 No use of process-singleton state for things that should be screen-scoped.
  - PASS. `activeComponents` is correctly persistent (source of truth per Assumptions). `preWizardSnapshot` correctly persistent (survives process death, else undo is meaningless). No violation surfaced.

- [x] CHK007 No use of `rememberSaveable` for non-trivial / large objects (Bundle limits).
  - N/A. No `rememberSaveable` usage prescribed. Foundation spec — no Compose state APIs invoked.

## Recreation correctness

- [ ] CHK008 No "first-only" navigation logic that skips on recreation.
  - FAIL (gap, not violation). SEQ-1 plan-level starts with `UI->>VM: onStart` → `VM->>PS: loadPreset(...)` → rebuild Profile. There is no statement whether `onStart` fires once per creation or per recreation. If the `WizardViewModel` naively reloads preset + rebuilds Profile on every `onStart`, an in-flight Interactive step's user-entered draft (before commit through InteractionSink) is lost on rotation. Domain-level fix: none needed. Spec-level fix: add one line "on Activity recreation, WizardViewModel restores from ProfileStore and does not restart Wizard from step 1" or explicitly punt to draft-1 spec.

- [ ] CHK009 Form input survives rotation without re-querying network/disk.
  - FAIL (gap). Interactive steps ask user for values (font scale, SOS target). Whether the partial answer (before `InteractionSink` commit) survives rotation is unspecified. Given all persistence is via DataStore (disk), naive rotation would re-read Profile — acceptable — but the transient InteractionSink question state is not scoped. Delegate to draft-1 or state here.

- [x] CHK010 In-flight async operations survive recreation OR are cancelled+restarted predictably.
  - PASS. `Provider.check` and `Provider.apply` are `suspend fun` on the engine, not the ViewModel — FR-006. Engine execution is per `ReconcileEngine.run(RunMode)` call and persists after each step (FR-013). Recreation cancels the coroutine; resume restarts from the persisted step. Predictable, documented via US1 #3.

## Configuration changes

- [ ] CHK011 Locale change handled: strings re-resolved, no stale rendered text.
  - FAIL (gap). No mention. Any user-facing string in Wizard (step labels, category names in Settings) sourced from preset — `paramsOverride` and preset entries can contain literal strings. Whether these are localizable (string-resource-refs vs literal user-visible strings) is not decided. Wire format field for `label: LocalizedString | String` not present. This is a foundation-level gap: if the wire format doesn't support localized strings from day one, adding it later is a rule 5 wire-format-versioning break.

- [ ] CHK012 Density / font-scale change handled: layout doesn't break.
  - FAIL (gap). `FontSize` is itself a Component in the MVP wave, so font-scale IS a first-class managed value. What happens when OS-level font scale changes while a `FontSize` Component is active is not stated. Likely defer to consuming spec, but foundation should note whether `FontSizeProvider.check` runs at all lifecycle events or only at BootCheck.

- [ ] CHK013 Window size change (split-screen, foldable) handled OR explicitly out-of-scope.
  - FAIL. Neither addressed nor excluded. For a launcher / kiosk app targeting elderly users, split-screen is not a real concern, but the spec should say so once.

## Tests

- [ ] CHK014 Each US that touches state has at minimum one recreation test.
  - FAIL. SC-011 (property-based test, N=100 preset combinations through `ProfileFactory` + `ReconcileEngine.run(RunMode.Wizard)`) exercises engine correctness but not Android recreation. SC-013 (undo Wizard) covers state restoration semantically but not lifecycle. No test targets "rotate during Wizard step 2, verify step 2 still shown with same options" — reasonable because that lives in draft-1 UI spec, but foundation should call this out as a downstream test obligation.

- [x] CHK015 At least one process-death simulation test for any feature with persistent state.
  - PASS. SC-013 test description "fake ProfileStore captures pre-wizard snapshot, wizard проходит частично, undo вызван, ProfileStore.load() возвращает pre-wizard Profile" is effectively a process-death simulation (state comes only from store). SC-011 property test also verifies roundtrip preset → profile → serialize → deserialize → equal, covering restore-after-kill semantics.

## Edge cases

- [x] CHK016 Multiple instances of the same Activity (multi-window) — behaviour documented or exclusion stated.
  - PASS (by exclusion). Assumption "Один active preset per device" implicitly precludes concurrent Wizard sessions. Additionally launcher-role apps generally are singleTask. Acceptable for foundation.

- [ ] CHK017 Feature accessed from notification while killed — entry path tested.
  - FAIL. No push / notification entry points defined for this feature (Admin push US5 is schema-only, no transport). If admin push later delivers a preset while the app is killed, entry path (cold start → resolve preset → apply diff → dismiss notification) is not sketched even at seam level. Given US5 is schema-only, this is arguably premature — but the spec silently assumes something without stating "notification entry path is out of scope until TASK-27 messenger integrates".

---

## Investigation summary (context-directed questions)

- **(a) process-death handling explicit** — YES (CHK002 PASS). US1 #3 + FR-013 + edge case cover it.
- **(b) Activity/Fragment lifecycle mentioned or ruled out** — NO. Spec references `WizardActivity` / `SettingsActivity` in SEQ diagrams but never states the lifecycle contract. Neither "foundation is domain, UI concerns delegated to draft-1" nor "recreation handled by X" appears. CHK001, CHK008, CHK009 all FAIL for this reason.
- **(c) low-memory scenario for Wizard mid-flow** — PARTIAL. FR-013 (save after every step) implicitly covers it, but not stated (CHK003 FAIL).
- **(d) preWizardSnapshot retention policy — is 7 days concrete enough?** — Concrete but weakly justified. FR-024 says "через 7 суток после последнего Wizard-run (soft-limit)". Edge case says "пользователь получил достаточно времени осознать изменения". The choice is defensible but arbitrary — no data or user-research anchor. Bigger concern: retention CLOCK reset semantics unclear. "После последнего Wizard-run" — does re-opening Wizard and cancelling reset the 7-day clock (extends indefinitely if user keeps opening) or does it reset only on completed run? Also no user-facing warning "your undo expires in N days" specified. Adequate for MVP with an inline-TODO for tuning based on real usage, but should be tightened.
- **(e) undo mid-Wizard — partial applies rollback via `Provider.rollback` fallback** — WEAK / hand-wavy. FR-022 explicitly says `Provider.rollback` is NOT in MVP. Edge case says undo "откатываются в обратном порядке через `Provider.rollback` fallback (`Unsupported` → best-effort revert через повторный apply старого значения, если Component не поддерживает clean rollback)". This is internally inconsistent: FR-022 says the port doesn't exist, edge case says it exists as a fallback returning `Unsupported`. The "best-effort revert через повторный apply старого значения" mechanism is not defined anywhere — where does "старое значение" come from? From `preWizardSnapshot`? Then the mechanism is: engine re-runs `apply()` for each already-Applied step using the pre-snapshot Profile's params. That would be a real mechanism but is not stated. As written, this is a hand-wave. SC-013 test "fake ProfileStore captures pre-wizard snapshot, wizard проходит частично, undo вызван, ProfileStore.load() возвращает pre-wizard Profile" only tests that the STORE returns the pre-snapshot — it does NOT test that the world (system font, launcher tiles, permission grants) is actually rolled back. So the acceptance criterion is weaker than the FR promises. Concrete gap: either weaken FR-024 / edge case to say "Profile is restored; world-state rollback is best-effort and Components document their own rollback behaviour", or strengthen with a defined `Provider.revertTo(oldStep, newStep)` operation.

---

## Result

- Total: 17
- PASS: 7 (CHK002, CHK004, CHK005, CHK006, CHK010, CHK015, CHK016)
- FAIL: 9 (CHK001, CHK003, CHK008, CHK009, CHK011, CHK012, CHK013, CHK014, CHK017)
- N/A: 1 (CHK007)

Score: 7/16 effective (excluding N/A).

**Verdict**: BLOCK on lifecycle-contract silence. Foundation spec has correctly delegated Android UI implementation to downstream tasks BUT references `WizardActivity` / `SettingsActivity` in its own plan-level sequences, creating an implicit UI contract without lifecycle semantics. Either (a) remove Activity references from SEQ-1/SEQ-2 plan lifelines and explicitly state "UI lifecycle is defined by consuming specs (draft-1, TASK-69)", or (b) add a "UI Lifecycle Contract" mini-section that states what `WizardViewModel.onStart` guarantees on recreation and how mid-Interactive-step transient answers are scoped.

Secondary blocker: the `preWizardSnapshot` + rollback story (FR-024 vs FR-022 vs edge case) is internally contradictory — pick a lane.

Non-blockers (should-fix): localization (CHK011) is a wire-format concern that will bite later (rule 5); explicit "notification entry path deferred to TASK-27" one-liner (CHK017); explicit "split-screen out of scope for launcher" one-liner (CHK013).
