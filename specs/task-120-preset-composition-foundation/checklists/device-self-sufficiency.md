# Checklist: device-self-sufficiency

Spec: `specs/task-120-preset-composition-foundation/spec.md`
Skill: `.claude/skills/checklist-device-self-sufficiency/SKILL.md`
Date: 2026-07-10

Per [decision 2026-06-15-deferred-cloud](../../../docs/product/decisions/2026-06-15-deferred-cloud/) — each device is self-sufficient. App after install + wizard fully works without Google Sign-In, without internet, indefinitely.

---

## Results

| ID | Status | Notes |
|----|--------|-------|
| CHK-DSS-001 | [ ] | **FAIL** — spec does NOT explicitly declare mode (LOCAL / CLOUD / HYBRID). Behaviour is de-facto LOCAL-only (BundledPoolSource, DataStore ProfileStore, JVM-only tests, no network I/O), but the marker is missing. Recommendation: add a one-line "Mode: LOCAL-only (foundation)" statement near top of spec (e.g. after Input line or in Assumptions). |
| CHK-DSS-002 | [x] | PASS — foundation is fully local. `BundledPoolSource` reads `assets/pool.json`; `ProfileStore` = DataStore; `PresetSource` MVP variant is bundled; Wizard runs off bundled preset without any network, Sign-In, or server-state read. Assumptions confirm "core/preset/ KMP commonMain — pure Kotlin, zero Android." Local Test Path: "Emulator / device: не требуется". |
| CHK-DSS-003 | [N/A] | Not CLOUD-only. |
| CHK-DSS-004 | [N/A] | Not HYBRID at foundation level. Admin push (US5) is schema-seam only, runtime deferred to TASK-27 / task-121. |
| CHK-DSS-005 | [N/A] | No Sign-In introduced by this spec. Draft-1 downstream explicitly removes hardcoded Sign-In from `FirstLaunchActivity`. |
| CHK-DSS-006 | [N/A] | No Sign-In prompt in this spec. |
| CHK-DSS-007 | [N/A] | No Sign-In decline path in this spec. |
| CHK-DSS-008 | [ ] | **PARTIAL** — spec DOES cover local-state persistence (`ProfileStore`, `preWizardSnapshot` FR-024) but does NOT reference `VersionedConfigViewer` (S-8) as the eventual merge UI for the "user later signs in and wants to sync local Profile to cloud namespace" case. FR-011 explicitly punts merge to "future feature" without pointing at the S-8 seam. Recommendation: inline TODO `// TODO(local-to-cloud): future Sign-In flow merges local Profile via VersionedConfigViewer (S-8), not a bespoke dialog` in `ProfileStore` port doc + note in FR-011 or Assumptions. |
| CHK-DSS-009 | [ ] | **NOT ADDRESSED** — spec is silent on "user signs in later with a DIFFERENT Google account than ever before". Given foundation is fully local this is not immediately blocking, but the eventual Sign-In flow (draft-1) will need to know: local Profile stays on this device, new account starts fresh. Recommendation: add one-line assumption "Sign-In merge semantics (same vs different account) — deferred to draft-1 wizard refactor; local Profile is device-scoped, not identity-scoped." |
| CHK-DSS-010 | [N/A] | Foundation has no cloud subscription / entitlement gating. |
| CHK-DSS-011 | [N/A] | No cloud-locked feature to renew. |
| CHK-DSS-012 | [x] | PASS — no mandatory Sign-In at first launch. Wizard starts from bundled preset. Downstream contract explicitly notes draft-1 removes hardcoded Sign-In from `FirstLaunchActivity`. |
| CHK-DSS-013 | [x] | PASS — no mandatory pairing at first launch. Pairing is out of scope of this spec entirely. |
| CHK-DSS-014 | [x] | PASS — no local feature is behind a cloud dependency. `AppTile` install-intent uses system Play Store intent (still local UX flow, degrades gracefully to `Failed("app unavailable")` if store missing). MessengerTile cloud-touching variant deferred to task-121 explicitly to avoid bleeding cloud dependency into foundation. |
| CHK-DSS-015 | [x] | PASS — no reference to anonymous Firebase Auth anywhere in the spec. |
| CHK-DSS-016 | [x] | PASS — MessengerTile (potential cloud-touching Component) is deferred to task-121 with explicit connection in FR-014 and Downstream contract. `AppTile` covers the generic local case (WhatsApp/Госуслуги/etc.) with `NeedsApply → install-intent → Failed("app unavailable")` fallback. Cross-spec boundary is documented. |
| CHK-DSS-017 | [x] | PASS — `Outcome` sealed hierarchy `Ok \| NeedsApply \| Failed(reason) \| Unsupported` explicitly encodes unavailable/degraded paths. FR-007 `ProviderRegistry` fallback chain terminates in `NoOpProvider` → `Unsupported`, which engine handles as "skip step, don't crash" (Edge Cases, SC-006). No implicit assumption of cloud data presence anywhere. |

---

## Summary

- Total: 17 items
- Passed: 8 [x]
- Failed / gaps: 3 [ ] (CHK-DSS-001, CHK-DSS-008, CHK-DSS-009)
- N/A: 6

## Recommended remediation (inline, non-blocking for foundation)

1. **CHK-DSS-001** — add explicit "Mode: LOCAL-only (foundation; downstream tasks introduce CLOUD/HYBRID seams: task-121 messenger cloud action, US5 admin push runtime in TASK-27)" line to spec. One sentence, near top.
2. **CHK-DSS-008** — inline TODO on `ProfileStore` port: `// TODO(local-to-cloud): future Sign-In flow merges local Profile via VersionedConfigViewer (S-8), not a bespoke dialog`. Also add one line to FR-011 or Assumptions: "LOCAL→CLOUD Profile promotion path: via VersionedConfigViewer at first cloud action (draft-1 wizard, task-121, TASK-27), not this foundation."
3. **CHK-DSS-009** — add assumption: "Sign-In merge semantics (same-account merge vs different-account fresh-start) — deferred to draft-1 wizard refactor. Foundation treats Profile as device-scoped, not identity-scoped."

None of these gaps block the foundation from shipping — the spec is architecturally correct (fully local, no Sign-In coupling, no cloud dependency in domain code, task-121 splits off the first cloud-touching Component cleanly). Gaps are documentation-level: making the LOCAL declaration explicit, and leaving forward pointers to the eventual LOCAL→CLOUD upgrade path so downstream tasks (draft-1, task-121, TASK-27) inherit the seam.
