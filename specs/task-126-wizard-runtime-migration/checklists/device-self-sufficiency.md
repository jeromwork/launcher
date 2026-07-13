# Checklist: device-self-sufficiency
# spec: specs/task-126-wizard-runtime-migration/spec.md
# generated: 2026-07-11

Per decision 2026-06-15-deferred-cloud: each device is self-sufficient. App after install + wizard fully works without Google Sign-In, without internet, indefinitely.

## Feature classification

TASK-126 is a pure **LOCAL-only** technical refactoring. It consolidates three parallel wizard/preset engines (legacy wizard, TASK-65 model, TASK-120 foundation) into a single `ReconcileEngine`. No cloud services, no Google Sign-In, no server calls introduced by this spec.

---

## Local viability

- [ ] **CHK-DSS-001** — FAIL: The spec does not explicitly declare the feature mode (LOCAL-only / CLOUD-only / HYBRID). This is a documentation gap only — the design is clearly LOCAL-only throughout. Add one sentence to Assumptions: "This refactoring is LOCAL-only — all wizard, BootCheck, and Settings flows operate on-device using bundled assets and DataStore persistence. No network call, no Google Sign-In required."
- [x] **CHK-DSS-002** — PASS: Feature requires zero network calls. `PresetBootstrap` loads from bundled assets (`app/androidMain/assets/`). `ReconcileEngine` runs on-device only. All Providers interact exclusively with Android OS APIs (`WindowInsetsControllerCompat`, `AppCompatDelegate`, `Intent` for home role). `ProfileStore` and `WizardStore` are DataStore (local). Works fully on fresh install without internet. Confirmed by Assumptions: "WizardStore is device-local only."
- [N/A] **CHK-DSS-003** — N/A: Not CLOUD-only.
- [N/A] **CHK-DSS-004** — N/A: Not HYBRID. ProfileStore Assumptions note "синхронизируется" for future (TASK-127+), but that is out of scope for this spec. Within TASK-126 scope: LOCAL-only.

## Sign-In trigger point

- [N/A] **CHK-DSS-005** — N/A: No Sign-In in this spec.
- [N/A] **CHK-DSS-006** — N/A: No Sign-In prompt.
- [N/A] **CHK-DSS-007** — N/A: No Sign-In decline path needed.

## Local→cloud promotion

- [N/A] **CHK-DSS-008** — N/A: No local state merging into cloud namespace in this spec.
- [N/A] **CHK-DSS-009** — N/A: No Google account switching scenario.

## Cloud→local downgrade

- [N/A] **CHK-DSS-010** — N/A: Feature is LOCAL-only; no cloud dependency to degrade from.
- [N/A] **CHK-DSS-011** — N/A: No cloud-only mode to pause.

## Anti-patterns

- [x] **CHK-DSS-012** — PASS: No mandatory Sign-In at first launch. First launch flow: preset picker → wizard steps → home screen (US1, FR-001). Zero auth required.
- [x] **CHK-DSS-013** — PASS: No mandatory pairing at first launch. BootCheck, wizard, and Settings flows are all local.
- [x] **CHK-DSS-014** — PASS: No local feature bottlenecked behind cloud. All Providers (LauncherRole, Theme, Language, StatusBarPolicy, FontSize, Sos, AppTile, Toolbar) call Android OS APIs directly without cloud intermediary.
- [x] **CHK-DSS-015** — PASS: No Firebase Auth (anonymous or otherwise) referenced. Anonymous Auth was removed in F-4 (2026-05-30). No regression.

## Cross-spec consistency

- [N/A] **CHK-DSS-016** — N/A: No cross-spec cloud/local interaction in this task.
- [x] **CHK-DSS-017** — PASS: No cloud data assumed present. All flows (wizard, BootCheck, settings) read from `ProfileStore` (local DataStore) and bundled JSON assets. No `Outcome.CloudUnavailable` handling needed.

---

## Summary

**Result: 6 PASS, 1 FAIL, 9 N/A** (out of 17 gates)

### Only failure

**CHK-DSS-001** — Documentation gap: missing explicit LOCAL/CLOUD/HYBRID classification in spec. Recommended fix: add to Assumptions section:

> "This refactoring is **LOCAL-only** — wizard, BootCheck, and Settings flows operate on-device using bundled assets and DataStore persistence. No network call, no Google Sign-In required. `ProfileStore` cloud sync is out of scope for TASK-126 (deferred to TASK-127+)."

**This is a trivial documentation fix, not an architectural problem.** The feature design is fully compliant with the device-self-sufficiency principle:
- Bundled assets load on first install without internet.
- ReconcileEngine runs entirely on-device.
- WizardStore and ProfileStore persist locally via DataStore.
- BootCheck uses only Android OS APIs and local ProfileStore.
- No Sign-In, no pairing, no cloud state required at any point.
