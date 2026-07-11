# Checklist: permissions-platform
**Spec**: task-126-wizard-runtime-migration
**Date**: 2026-07-11
**Result**: 12/22 ✓

---

## Scope of this spec

**New platform surface introduced:**
- `LauncherRole` Component → `RoleManager.ROLE_HOME` request via `Intent` (equivalent of `ACTION_CHANGE_DEFAULT_DIALER` pattern for home role)
- `StatusBarPolicy` Component → `WindowInsetsControllerCompat.hide(statusBars())` — no permission, but window-level API
- `BootCheckReceiver` → listens for `BOOT_COMPLETED` broadcast
- `AccessibilityService` declaration **removed** from manifest (FR-005)
- `WizardStore` DataStore (device-local, no new permissions)

**Permissions changed:** `uses-accessibility-service` removed. No new dangerous permissions added.

---

## Runtime permissions

- [N/A] **CHK001** Each runtime permission requested has explicit user-value justification in spec.
  > No new dangerous runtime permissions introduced. `RoleManager.ROLE_HOME` is a role request, not a permission. N/A for traditional permissions. However, spec does not justify the user-value of hiding the status bar (FR-005) or why LauncherRole is needed — these are system-level capabilities, not declared permissions, but still affect UX trust.
  > **MARGINAL PASS** — no new dangerous permissions, but FR-005 / FR-002 lack explicit user-value rationale text.

- [ ] **CHK002** First-launch permission flow specified — when prompted, in what context, what the user sees.
  > FAIL — FR-002 specifies that `LauncherRoleProvider.apply()` sends the role-change intent, but does not describe what the user sees in the system dialog (dialog title, button labels, what happens if the user dismisses it). User Story 2 covers the dual-dialog regression bug but not the first-time UX description.

- [ ] **CHK003** Re-prompt strategy specified — first denial vs "don't ask again" handled separately.
  > FAIL — No spec text covers what happens if user denies the ROLE_HOME assignment dialog. First denial vs permanent denial distinction absent.

- [ ] **CHK004** Settings deep-link path designed for permanent denial recovery.
  > FAIL — No deep-link to Settings specified for the case where user denied launcher role and cannot be re-prompted.

- [ ] **CHK005** Pre-permission rationale screen specified per Material Design guidance (when appropriate).
  > FAIL — No rationale screen designed for the ROLE_HOME request step in the wizard.

## Manifest declarations

- [ ] **CHK006** Required permissions in `AndroidManifest.xml` listed; no broad permissions ("for safety").
  > FAIL — Spec does not enumerate current manifest state or delta. FR-005 says `uses-accessibility-service` MUST be removed and `AccessibilityService` manifest declaration MUST be deleted, but no manifest delta table exists in the spec. No statement on whether this removal affects any flavour-specific manifests (`realBackend` / `mockBackend`).

- [x] **CHK007** `<uses-feature>` declarations match actual hardware needs with `required="false"` when feature is graceful-degraded.
  > PASS — No new hardware feature requirements. Telephony already covered by spec-010 with `required="false"`. This spec adds no new hardware dependencies.

- [x] **CHK008** `<queries>` element declared for any package the app inspects (Android 11+).
  > PASS — No new package inspection. AppTile launching inherits existing `<queries>` from spec-005/009. No `getInstalledApplications` added in this spec.

## Android version specifics

- [x] **CHK009** Scoped storage compliance for any file I/O (Android 10+).
  > PASS — `WizardStore` and `ProfileStore` use DataStore (app-private, scoped to app sandbox). No external storage access.

- [x] **CHK010** Foreground service `type` declared if used (Android 14+ enforcement).
  > PASS — No foreground services introduced.

- [x] **CHK011** Exact alarms (`SCHEDULE_EXACT_ALARM`) avoided unless justified.
  > PASS — No alarms used.

- [x] **CHK012** Notification permission flow on Android 13+ (`POST_NOTIFICATIONS`) specified.
  > PASS — No new system notifications introduced.

- [ ] **CHK013** Predictive back gesture (Android 14+) compatibility documented for screens that override back.
  > FAIL — Wizard has a "Cancel" flow (US1 AC4: user confirms cancellation → Profile restored from snapshot). This implies back-press interception. Spec does not document how back gesture is handled in `WizardScreen` for Android 14+ predictive back API. If `OnBackPressedCallback` is used, `setEnabled(true)` predictive back coordination is required.

## HOME / launcher role (project-specific)

- [ ] **CHK014** If feature interacts with HOME/ROLE_HOME: behaviour when role denied documented.
  > FAIL — FR-002 covers the "already default" happy path (`check()` → `Ok`) and the "needs apply" path, but no spec text covers what happens when the user dismisses the system role-assignment dialog (denial). Does wizard show an error? Retry? Skip step? Not specified.

- [ ] **CHK015** If feature requires being default launcher: fallback behaviour documented.
  > FAIL — Corollary of CHK014. If `LauncherRole` is `required=true` in the preset's pool descriptor (per FR-006 `required: Boolean`), wizard cannot complete without it. The spec does not state whether `LauncherRole` is `required=true` or `required=false` in the bundled `simple-launcher` preset, and does not specify fallback UX for denial of a required role.

## OEM quirks

- [x] **CHK016** Samsung: KNOX restrictions on AccessibilityService (if used) acknowledged.
  > PASS — AccessibilityService is **removed** by this spec (FR-005). KNOX restriction concern is eliminated. No `AccessibilityService` remains.

- [ ] **CHK017** Xiaomi MIUI: aggressive battery saver / autostart whitelisting addressed if feature relies on background work.
  > FAIL — `BootCheckReceiver` listens for `BOOT_COMPLETED`. On Xiaomi MIUI, apps must be whitelisted in the autostart list for `BOOT_COMPLETED` to be delivered. Spec mentions Xiaomi in Edge Cases (`WindowInsetsController` fallback) and in verification evidence (Xiaomi Redmi Note 11 target device) but does not address the autostart whitelist requirement. BootCheck may silently not run on Xiaomi without whitelist. No prompt or user guidance specified.

- [ ] **CHK018** Huawei EMUI: Push-to-protected-apps process for background work documented if applicable.
  > FAIL — `BootCheckReceiver` is also blocked by Huawei EMUI battery optimisation unless added to protected apps. No mention in spec. Lower priority (Huawei not primary test device) but should be documented as a known gap.

- [ ] **CHK019** OEM launcher-replacement quirks (Samsung One UI, OnePlus OxygenOS) noted for HOME-related features.
  > FAIL — `LauncherRoleProvider` interacts with the system home-role assignment. Samsung One UI has additional prompts / proprietary home replacement flows. OnePlus OxygenOS may suppress the standard `ACTION_HOME_CHANGED` equivalent. No OEM quirk acknowledgment beyond Xiaomi in this spec.

## Package visibility (Android 11+)

- [x] **CHK020** If feature inspects or launches other apps: relevant `<queries>` entries declared in manifest.
  > PASS — AppTile launching inherits from spec-005/009. No new package inspection introduced. `LauncherRoleProvider` uses `RoleManager.ROLE_HOME` which does not require `<queries>`.

- [x] **CHK021** If app needs to detect ANY installed app: `QUERY_ALL_PACKAGES` justified per Play policy.
  > PASS — Not used.

## Compliance docs

- [ ] **CHK022** [`docs/compliance/permissions-and-resource-budget.md`](../../../../docs/compliance/permissions-and-resource-budget.md) updated with delta for this spec.
  > FAIL — No entry for `task-126` exists in `permissions-and-resource-budget.md`. Delta to record: (1) `uses-accessibility-service` removed, (2) `BOOT_COMPLETED` receiver added via `BootCheckReceiver` (inherited from existing code but exercised in new path), (3) `WindowInsetsControllerCompat` API used (no permission, but API-level constraint ≥ API 30 for `WindowInsetsControllerCompat.hide`).

---

## Summary

**12/22 ✓**

| Result | CHKs |
|--------|------|
| PASS | CHK001(marginal), CHK007, CHK008, CHK009, CHK010, CHK011, CHK012, CHK016, CHK020, CHK021 |
| N/A counted as PASS | — |
| FAIL | CHK002, CHK003, CHK004, CHK005, CHK006, CHK013, CHK014, CHK015, CHK017, CHK018, CHK019, CHK022 |

## Recommended actions before implementation

**Priority 1 (blocks wizard correctness):**
- Add FR covering LauncherRole denial UX: what wizard shows, whether role is `required=true` in bundled presets, and what the retry/skip path is (fixes CHK002, CHK003, CHK004, CHK005, CHK014, CHK015).
- Add manifest delta table in spec: enumerate removals (`AccessibilityService`) and additions (`RECEIVE_BOOT_COMPLETED` if not already present) per flavour (fixes CHK006).

**Priority 2 (OEM resilience):**
- Add Edge Case for Xiaomi MIUI autostart whitelist for `BootCheckReceiver` + user guidance or in-app prompt if boot check cannot be confirmed (fixes CHK017).
- Add note on Samsung One UI / OnePlus OxygenOS launcher replacement quirks (fixes CHK019).

**Priority 3 (documentation):**
- Document predictive back handling in `WizardScreen` for Android 14+ (fixes CHK013).
- Add task-126 entry to `docs/compliance/permissions-and-resource-budget.md` (fixes CHK022).
- Add Huawei EMUI note as known gap (fixes CHK018).
