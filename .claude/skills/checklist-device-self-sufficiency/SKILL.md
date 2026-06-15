---
name: checklist-device-self-sufficiency
description: Verifies a spec respects the device-self-sufficiency principle from decision 2026-06-15-deferred-cloud — every device works locally without Google Sign-In; cloud features are an opt-in upgrade triggered at the first cloud action, not at first launch. Triggered on any spec touching launcher, wizard, config, contacts, themes, or any feature that could plausibly work locally. Also triggers on any spec that introduces a cloud dependency, to check that the LOCAL→CLOUD upgrade path is designed.
---

# Checklist: device-self-sufficiency

Per [decision 2026-06-15-deferred-cloud/](../../../docs/product/decisions/2026-06-15-deferred-cloud/) — each device is self-sufficient. App after install + wizard fully works **without** Google Sign-In, without internet, indefinitely. Cloud features (pair, sync, push, remote) appear when the user takes a cloud action, with a clear "what you'll get for Sign-In" prompt.

This skill enforces that the spec respects this principle.

---

## Local viability

- [ ] CHK-DSS-001 The spec explicitly declares the feature's mode: **LOCAL-only** / **CLOUD-only** / **HYBRID** (local-baseline + cloud-enhanced).
- [ ] CHK-DSS-002 If LOCAL-only: feature requires no network call, no Google Sign-In, no server-state read. Works fully on a fresh-installed app right after wizard.
- [ ] CHK-DSS-003 If CLOUD-only: spec explicitly justifies why local fallback is impossible (e.g. cross-device sync — by definition needs server) and confirms the cloud-action trigger initiates Sign-In with a clear reason message.
- [ ] CHK-DSS-004 If HYBRID: spec describes the local-baseline (what works without cloud) AND the cloud-enhancement (what improves with cloud). The local-baseline must be useful on its own.

## Sign-In trigger point

- [ ] CHK-DSS-005 If the feature requires Sign-In, the spec specifies **exactly when** the Sign-In prompt appears. NOT at app launch. NOT at wizard step 1. It appears at the user-initiated cloud action.
- [ ] CHK-DSS-006 The Sign-In prompt copy is included or sketched in the spec, and explains **what cloud capability the user unlocks**, not just "please sign in to continue".
- [ ] CHK-DSS-007 If user declines Sign-In, the spec describes the graceful degradation path (return to wherever they came from, no broken state).

## Local→cloud promotion

- [ ] CHK-DSS-008 If the feature has local state that will need to merge into cloud namespace at Sign-In time, the spec references the `VersionedConfigViewer` (from S-8) as the merge UI. NOT a custom one-off merge dialog.
- [ ] CHK-DSS-009 The spec acknowledges the "different Google account" case: if user signs in with a DIFFERENT account than ever before, the local state stays on this device, the new account starts fresh (no auto-merge).

## Cloud→local downgrade

- [ ] CHK-DSS-010 If the feature is CLOUD-only or HYBRID, the spec describes what happens on **subscription expiry / no internet**: cloud features pause cleanly, local data is preserved, app does NOT become unusable.
- [ ] CHK-DSS-011 The user is shown WHAT specifically stopped working in local-only mode, with a "renew / sign-in" button — not a generic "premium required".

## Anti-patterns to refuse

- [ ] CHK-DSS-012 The spec does NOT introduce mandatory Sign-In at first launch.
- [ ] CHK-DSS-013 The spec does NOT introduce mandatory pairing at first launch.
- [ ] CHK-DSS-014 The spec does NOT bottleneck a local feature behind a cloud dependency without explicit justification.
- [ ] CHK-DSS-015 The spec does NOT treat anonymous Firebase Auth as a fallback for "user hasn't signed in" — that auth mode is removed (per [`2026-05-30-f4-identity/03-anonymous-removed.md`](../../../docs/product/decisions/2026-05-30-f4-identity/03-anonymous-removed.md)).

## Cross-spec consistency

- [ ] CHK-DSS-016 If two specs interact (e.g. S-3 contact tiles is LOCAL, S-5 contact photos is CLOUD), the spec documents what the user sees on a LOCAL device when CLOUD-feature data is unavailable (e.g. tile renders with placeholder instead of photo).
- [ ] CHK-DSS-017 Spec doesn't assume cloud data is always present — uses Outcome/Result types with explicit cloud-unavailable handling.

---

## When to refuse

Refuse the spec if any of:

1. Adds mandatory Sign-In at first launch.
2. Treats local-only mode as "broken" or "incomplete".
3. Introduces a cloud dependency without justifying why local fallback is impossible.
4. Introduces a merge dialog that's NOT `VersionedConfigViewer` (custom one-off → refuse, point to S-8).
5. Re-introduces anonymous Firebase Auth as a "sign-in skip" hack.

Propose corrected shape, then continue.

## Output

Inline into `specs/<id>/checklists/device-self-sufficiency.md`.

## Relationship to other skills

- **`checklist-backend-substitution`** — covers whether we could swap our backend provider. This skill covers whether we **need** a backend at all for this feature.
- **`checklist-modular-delivery`** — covers form-factor variants. This skill covers cloud-vs-local boundary.
- **`checklist-tamper-resistance`** — covers protecting cloud features from local-flag bypass. This skill covers the boundary between cloud and local.
