# Specific Scans — /speckit.analyze Step 5 (F-5)

**Date**: 2026-06-28
**Scope**: spec.md, plan.md, tasks.md, checklists/
**Method**: 5 targeted scans per analyze checklist.

---

## Scan 1 — Vague language sweep (spec.md)

Searched for: `intuitive | smooth | fast | simple | easy | should be | may | could` (case-insensitive).

Findings:
- **Line 494** (inside a code/TODO comment block): `// For MVP — plain string keeps domain simple (rule 4 minimum viable architecture).` — "simple" appears as design-rule citation, not as user-visible vague qualifier. Acceptable.
- **Line 706** (Clarifications Q-G resolution): "plain string keeps domain **simple**" — same justification, contextualised by rule 4 reference. Acceptable.
- No instances of `intuitive`, `smooth`, `fast` (standalone), `easy`, `may`, `could` (case-insensitive) found anywhere in spec.md.
- "Should be" not flagged — none found.

**Verdict**: ✓ PASS — 0 unoperationalised vague qualifiers. All occurrences of "simple" are anchored to rule 4 (MVA) with explicit justification.

---

## Scan 2 — Dangling deleted-file references

Searched: `FirestoreRecoveryKeyVault`, `GoogleDriveAppDataRecoveryKeyBackup`, `drive.appdata`, `DriveAppData`.

Findings:
- **spec.md line 729, 734, 754, 757**: All under `## Clarifications` (Q-D resolution) or `## Notes (для AI-agent)` (legacy `origin/020` ветка inventory). Explicitly marked as obsolete / legacy / "**отклонён**". ✓ Intentional historical context.
- **plan.md line 41, 334**: `drive.appdata` references — both phrased as "никакой `drive.appdata` scope" / "no `drive.appdata` scope (moot after Worker pivot)". Negative assertions documenting closure of security CHK018. ✓ Intentional.
- **tasks.md line 174**: T675 mandates docs update with explicit "NO `drive.appdata` Google scope is used". ✓ Intentional negative reference.
- **checklists/backend-substitution.md (lines 18, 19, 27, 29, 36) + domain-isolation.md (16, 17, 18, 25) + meta-minimization.md (19) + _overview.md (16, 33) + requirements-quality.md (23)**: These checklist files still describe the **pre-pivot** architecture (Drive App Data + `NoOpRecoveryKeyBackup` + `RecoveryKeyBackupSelector`) as if it were current. Owner pushback round 2 (2026-06-28) inverted the design — but these checklist files were not regenerated. ⚠ Stale checklist content, not dangling implementation directive. Acceptable for analyze (constitution-check-analyze.md §«Was/Now» explicitly captures the pivot); should be flagged for next checklist refresh.
- **GoogleDriveAppDataRecoveryKeyBackup**: only appears in spec.md `## Clarifications` (Q-D obsolete) and `## Notes` (legacy mention as «отклонён»). ✓ Intentional.
- **FirestoreRecoveryKeyVault**: only in spec.md `## Notes` (legacy origin/020 branch inventory). ✓ Intentional.

**Verdict**: ⚠ ISSUES (minor) — 1 finding:
- Pre-pivot checklists (backend-substitution.md, domain-isolation.md, meta-minimization.md, _overview.md, requirements-quality.md) reference `GoogleDriveAppDataRecoveryKeyBackup` / `NoOpRecoveryKeyBackup` / `RecoveryKeyBackupSelector` as if current. These are historical artifacts from clarify-round-1 and were not regenerated after owner pushback round 2. Not blocking — analyze-time constitution-check captures the pivot. Recommend: regenerate these checklists during pre-PR sync OR add a header note "pre-pivot version, see constitution-check-analyze.md for current design".

---

## Scan 3 — Source-set placement audit (plan.md §Project Structure)

Verified each declared file against domain-isolation (rule 1) + responsibility separation:

| File | Declared location | Verdict |
|---|---|---|
| `KeyRegistry.kt`, `RootKeyManager.kt`, `RecoveryKeyBackup.kt`, `AuthAvailability.kt`, value types, sealed errors | `core/keys/src/commonMain/kotlin/family/keys/api/` | ✓ Ports + values in commonMain. |
| `RootKeyManagerImpl.kt`, `KeyRegistryImpl.kt`, `RecoveryBlobCodec.kt` | `core/keys/src/commonMain/kotlin/family/keys/impl/` | ⚠ `RootKeyManagerImpl` and `KeyRegistryImpl` are placed in commonMain/impl, but per tasks.md T633 the Android-specific Argon2 implementation is `Argon2RootKeyManager.kt` in androidMain. The commonMain `RootKeyManagerImpl.kt` shown in plan.md may be redundant or needs clarification (is it a base class? abstract?). Minor — implementation detail to resolve in code. |
| `AndroidKeystoreRegistry.kt`, `Argon2RootKeyManager.kt`, `DeviceKeyNamespaceProvider.kt` | `core/keys/src/androidMain/kotlin/family/keys/impl/` | ✓ Android-specific Keystore/libsodium adapters in androidMain. |
| `WorkerRecoveryKeyBackup.kt`, `DataStorePassphraseAttemptCounter.kt`, `DataStoreSchemaVersionMemory.kt`, `AuthAvailabilityAndroidImpl.kt` | `app/src/main/kotlin/com/launcher/data/recovery/` | ✓ App-layer adapters (OkHttp + DataStore). |
| `InitClaimClient.kt` | `app/src/main/kotlin/com/launcher/data/identity/` (tasks T668) | ✓ App-layer adapter for Worker call. |
| 3 Compose screens + ViewModel | `app/src/main/kotlin/com/launcher/ui/recovery/` | ✓ UI layer. |
| `KeysModule.kt` | `app/src/main/kotlin/com/launcher/di/` | ✓ DI binding layer. |
| Worker code | `workers/backup/src/`, `workers/identity/src/` | ✓ TS Workers in their own folders, separate `wrangler.toml`. |

**Verdict**: ✓ PASS (with 1 minor clarification needed):
- `RootKeyManagerImpl.kt` in commonMain/impl: ambiguous relationship with androidMain `Argon2RootKeyManager.kt`. Recommend either drop the commonMain stub, or document that commonMain holds passphrase-derivation orchestration logic delegating to `KeyDerivation` port (in which case Argon2RootKeyManager becomes a thin wrapper or is unnecessary). Not blocking — clarify during T613/T633 implementation.

---

## Scan 4 — ADR / docs reference audit

References found in spec.md / plan.md:

| Reference | Location | Path declared | Actual path | Verdict |
|---|---|---|---|---|
| ADR-011 | spec.md:45 | `../../docs/adrs/adr-011-ai-owner-collaboration.md` | `docs/adr/ADR-011-ai-owner-collaboration-conventions.md` | **❌ BROKEN** — wrong folder (`adrs/` vs `adr/`) and wrong filename (`adr-011-ai-owner-collaboration.md` vs `ADR-011-ai-owner-collaboration-conventions.md`). |
| ADR-011 | plan.md:64, 296 | bare mention "ADR-011 (AI-owner conventions)" | exists at `docs/adr/ADR-011-...` | ✓ Bare mention, no broken link. |
| constitution.md Article XIV | spec.md:30, 763, 786 | `../../.specify/memory/constitution.md#article-xiv` | exists, line 411 `## Article XIV` | ✓ File exists; §7 added at constitution.md:419 (confirmed). |
| constitution.md Article XIV §7 | plan.md:42, 56, 283 | same | confirmed at line 419 | ✓ Section exists. |
| `docs/dev/server-roadmap.md` | plan.md:66, 285 | `../../docs/dev/server-roadmap.md` | exists | ✓ |
| `docs/dev/project-backlog.md` | plan.md:287 | `../../docs/dev/project-backlog.md` | exists | ✓ |
| `docs/product/vision.md` | plan.md:286 | `../../docs/product/vision.md` | exists | ✓ |
| `docs/product/decisions/2026-06-15-deferred-cloud/` | plan.md:294 | folder ref | exists | ✓ |
| `docs/product/decisions/2026-05-30-f4-identity/` | plan.md:295 | folder ref | exists | ✓ |
| `docs/compliance/permissions-and-resource-budget.md` | plan.md:41, 306 | UPDATE target | not verified to exist now, but **marked UPDATE** in T675 | ✓ Marked as existing-to-update; tasks.md T675 owns the update. |
| `docs/recovery-flow.md` | plan.md:305 (NEW) | NEW per FR-020 | ✓ Marked NEW, owned by T673. |
| `docs/dev/key-hierarchy.md` | plan.md:304 (NEW) | NEW per FR-021 | ✓ Marked NEW, owned by T674. |

**Verdict**: ⚠ ISSUES — 1 broken link:
- **spec.md:45** — ADR-011 link points to `docs/adrs/adr-011-ai-owner-collaboration.md` but file is at `docs/adr/ADR-011-ai-owner-collaboration-conventions.md`. Fix: update path + filename casing.

---

## Scan 5 — Trace table cross-link audit (tasks.md)

Verified every T6NN reference in `## Trace summary`, `## Required-task gates`, `## Dependencies` against actual task definitions (T601–T686 confirmed defined in tasks.md).

Cross-link findings:

1. **T681 (line 190)** — "install on Xiaomi 11T (device A) → setup with passphrase ... → encrypt config → verify Worker blob exists. Then on second device (or factory-reset same device) ...": no T-reference, but logically depends on T666 (deploy) + T635 (Android Worker client). ✓ Self-contained.
2. **T682 (line 191)** — "on Xiaomi 11T **after T670**". T670 is `InitClaimClientIntegrationTest` (localhost). Real-device manual smoke for SC-002 should depend on T666 (Worker deploy) + a successful T681 setup, not on T670 (localhost integration test). ⚠ **Wrong dependency** — should be "after T681" or "after T666".
3. **T685 (line 194)** — "**Real Worker E2E**: T655 against deployed `<account>.workers.dev/backup`". T655 is `ratelimit.ts` (rate-limit implementation). E2E POST/GET/DELETE smoke would exercise T654 (`index.ts` with the 3 endpoints), not T655. ⚠ **Wrong T-reference** — should be T654 (or T635 Android adapter against deployed Worker).
4. **T686 (line 195)** — "**SC-006 docs/recovery-flow.md peer review**: owner reads `docs/recovery-flow.md` (T662 output)". T662 is `workers/identity/` scaffolding, NOT docs/recovery-flow.md. The docs file is owned by T673. ⚠ **Wrong T-reference** — should be "T673 output".
5. **Trace SC table line 252** — "`worker-api-v1.md` → ... real-Worker E2E (T685)". Consistent with T685 line 194's intent, but T685 itself references T655 (see point 3). Cascades from same error.
6. **checklists/cross-artifact-trace.md:129-131** — references T660 (permissions docs), T661 (allowBackup), T662 (data_extraction_rules.xml). After renumbering these tasks moved to T675, T676, T677. ⚠ **Stale checklist** — predates the T660→T671+ renumbering. Same stale-checklist class as Scan 2 finding.
7. **All other T-IDs** in trace tables, dependencies, and required-task gates (T601–T686) map cleanly to task definitions. FR matrix complete (FR-001…FR-023 + Worker entries). SC matrix complete (SC-001…SC-013).

**Verdict**: ⚠ ISSUES — 4 broken cross-references after renumbering:
- T682 line 191: "after T670" → should be "after T681" or "after T666".
- T685 line 194: "T655 against deployed" → should be "T654 against deployed".
- T686 line 195: "(T662 output)" → should be "(T673 output)".
- cross-artifact-trace.md §7 (lines 129-131): T660/T661/T662 → should be T675/T676/T677.

---

## Summary

`specific-scans: vague-lang 0, dangling-refs 1, source-set 1, adr-refs 1, trace-cross-links 4. Total issues: 7`

Severity:
- **Blocking before merge** (broken link/reference): ADR-011 path in spec.md:45; 3 broken cross-refs in tasks.md (T682/T685/T686).
- **Should fix during pre-PR sync** (stale content, not blocking design): pre-pivot checklists (Scan 2), stale T-refs in cross-artifact-trace.md.
- **Code-time clarification** (not analyze blocker): RootKeyManagerImpl commonMain/impl ambiguity (Scan 3).
