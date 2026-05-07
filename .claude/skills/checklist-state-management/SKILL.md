---
name: checklist-state-management
description: Verifies UI state survives Activity recreation, configuration changes, process death, and low-memory kill scenarios. Enforces Article IV §5 and §III.3 of constitution.md. Triggered when spec mentions Activity, Fragment, lifecycle, configuration change, recreation, savedInstanceState, process death, low memory.
---

# Checklist: state-management

Verifies **state survival** across Android lifecycle events. Aligned with Article IV §5 and §III.3 of [`/.specify/memory/constitution.md`](/.specify/memory/constitution.md).

Reference: [`specs/002-whatsapp-tile-return/checklists/state-management.md`](specs/002-whatsapp-tile-return/checklists/state-management.md).

---

## Lifecycle events

- [ ] CHK001 Behaviour after Activity recreation (rotation, language change, dark/light theme switch) explicitly specified.
- [ ] CHK002 Behaviour after process death (system kill while in background) specified — what is restored, what is lost, what is shown to the user.
- [ ] CHK003 Behaviour after low-memory kill (foreground process trimmed) specified.
- [ ] CHK004 Behaviour after device reboot specified (if feature has any persistent state).

## State scope

- [ ] CHK005 For each piece of state: scope explicitly chosen — UI-local (`remember`), screen (`rememberSaveable` / SavedStateHandle), feature (singleton in DI), or persistent (DataStore/SQLDelight).
- [ ] CHK006 No use of process-singleton state for things that should be screen-scoped (a frequent recreation bug).
- [ ] CHK007 No use of `rememberSaveable` for non-trivial / large objects (Bundle limits).

## Recreation correctness

- [ ] CHK008 No "first-only" navigation logic that skips on recreation (e.g. `if (savedInstanceState == null) navigate(...)` checked).
- [ ] CHK009 Form input survives rotation without re-querying network/disk.
- [ ] CHK010 In-flight async operations survive recreation OR are cancelled+restarted predictably (choice documented).

## Configuration changes

- [ ] CHK011 Locale change handled: strings re-resolved, no stale rendered text.
- [ ] CHK012 Density / font-scale change handled: layout doesn't break.
- [ ] CHK013 Window size change (split-screen, foldable) handled OR explicitly out-of-scope per spec.

## Tests

- [ ] CHK014 Each US that touches state has at minimum one recreation test (Compose `StateRestorationTester` or equivalent).
- [ ] CHK015 At least one process-death simulation test for any feature with persistent state.

## Edge cases

- [ ] CHK016 Multiple instances of the same Activity (multi-window) — behaviour documented or exclusion stated.
- [ ] CHK017 Feature accessed from notification while killed — entry path tested.

---

## How to apply

1. List every screen + every piece of state introduced by spec.
2. For each, walk the gates.
3. Failures → add explicit FR or acceptance criterion; or shrink scope.

## Output

Inline into `specs/<id>/checklists/state-management.md`.
