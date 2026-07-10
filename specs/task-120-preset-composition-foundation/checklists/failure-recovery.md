# Checklist: failure-recovery

Verifies the spec describes what happens when things go wrong, not just the happy path. Aligned with Article III §4 (deterministic fallback) of the constitution.

Reference: [`specs/002-whatsapp-tile-return/checklists/failure-recovery.md`](../../002-whatsapp-tile-return/checklists/failure-recovery.md).

Spec under review: [`specs/task-120-preset-composition-foundation/spec.md`](../spec.md).

---

## Error categories

- [x] CHK001 Each FR involving an external action lists at least one failure mode (target app missing, permission denied, network unavailable, timeout, malformed response).
  - FR-006 (Provider contract) → `Outcome.Failed(reason)` + `Unsupported` explicit; Q7 clarification pins "Mark Failed + continue" default.
  - FR-007 registry fallback chain terminates in `NoOpProvider → Unsupported`.
  - FR-011 preset diff: same-version-different-content → reject with version-conflict log.
  - FR-012 schemaVersion too high → fail loud.
  - FR-014 `AppTile`: not installed → `NeedsApply → apply → install intent` or `Failed("app unavailable")`.
- [x] CHK002 For each failure mode: user-visible behaviour specified (toast, snackbar, blocking dialog, silent log) — not "show error".
  - AppTile not installed edge case: tile "remains visible but marked 'not installed' in UI". Same-version conflict: "showToast for owner-driven install, silent log for admin push". `Failed` step remains visible with status marker in Settings (US4 acceptance #3).
  - Minor gap: schemaVersion too high (FR-012) says "reject with message 'Update app to load this preset'" but does not specify the surface (dialog vs toast vs banner). Acceptable — message text specified, surface obvious in single entry-point context.
- [x] CHK003 No silent failures of user-initiated actions. If an action fails, user knows.
  - Provider `Failed` propagates to ProfileComponent status → visible in Settings (SEQ-2).
  - Wizard undo covered by preWizardSnapshot + confirm dialog (US1 acceptance #4).
  - Admin push silent log for admin path is by design (out-of-band feedback via admin dashboard) — owner-initiated actions are never silent.

## Fallbacks

- [x] CHK004 If feature has fallback chains: maximum depth defined (cycle protection).
  - FR-007 Provider fallback: `(type, platform, vendor) → (type, platform, null) → (type, null, null) → NoOp` — exactly 3 hops + terminal NoOp. Finite, no cycles possible (each step drops one dimension).
- [x] CHK005 Fallback is **specified by the data** (e.g., `Action.fallback`), nor hardcoded in dispatch.
  - Provider fallback is data-driven via `HandlerKey(type, platform, vendor)` lookup in `ProviderRegistry` Map.
  - `visibleIf` (FR-020) evaluator: Unsupported expression → fallback "show step" is spec-declared behaviour, not dispatch-hardcoded.
- [x] CHK006 If fallback also fails: terminal behaviour defined (final error UI, log, exit code).
  - NoOpProvider returns `Unsupported` → engine "skips step without error" (SC-006 explicit); step status marked, user sees it in Settings.
  - Wizard rollback fallback (edge case): reverse-order apply → `Unsupported` → "best-effort revert через повторный apply старого значения" — terminal state explicit.

## Retries

- [!] CHK007 Retry behaviour explicit: who triggers (user vs auto), how many attempts, backoff.
  - **GAP**: `Provider.apply` failures have no declared retry policy. Q7 pins "mark Failed + continue Wizard" (no auto-retry) — that resolves the Wizard loop, but does not say whether:
    - BootCheck re-attempts a `Failed` step on next boot (implicit yes since it re-reads state, but not spelled out).
    - User can trigger manual retry from Settings for a `Failed` step (implied by Settings edit → `RunMode.Single`, but no explicit "retry Failed step" UX).
  - **Suggested fix**: add sentence to FR-010 or FR-006: "Failed steps are re-attempted on next `RunMode.BootCheck` (for critical) or on next user-initiated Settings edit (for non-critical). No auto-retry inside a single engine run."
- [x] CHK008 No infinite retry loops without user intervention point.
  - Q7 explicit: no in-run retry. Engine proceeds. User can always cancel Wizard.
- [!] CHK009 Idempotency: actions that may be retried are safe to repeat (or made idempotent in the contract).
  - **GAP**: FR-006 says "Provider.apply — только включение и выход, no background loop" but does not require idempotency of `apply`. If BootCheck re-runs `apply` on a step that was partially applied and left inconsistent (e.g., WorkManager scheduled but preferences not written), a naive re-apply could double-schedule.
  - **Suggested fix**: add explicit sentence to FR-006 Provider contract: "`check` MUST be side-effect-free and idempotent. `apply` MUST be safe to call repeatedly (repeated calls converge to the same target state)." This is standard reconcile-loop discipline (Kubernetes-style) — no reason to leave implicit.

## Offline / degraded modes

- [x] CHK010 If feature reads from network: offline behaviour defined (cached, blocked, degraded).
  - Foundation spec is offline-first by design — `BundledPoolSource` + `BundledPresetSource` read from assets. No network path in MVP.
  - AppTile install intent → Play Store: edge case declares "fallback: `Failed("app unavailable")` если Play Store недоступен" — offline explicitly handled.
  - Admin push (US5) is schema-only in MVP; runtime transport (network drops mid-diff-apply) is explicitly out-of-scope, deferred to TASK-27/TASK-102. Correct scoping.
- [x] CHK011 Stale data: TTL / freshness requirements defined.
  - `preWizardSnapshot` TTL: 7 suток explicit (FR-024 + edge case).
  - Pool/preset are versioned wire formats (FR-016 schemaVersion), no staleness concept in this spec's scope.

## Permissions denied

- [!] CHK012 Each permission required: behaviour when denied first time documented.
  - Foundation spec correctly notes that Android SDK usage lives in adapter modules, not domain. So permissions are not directly required here.
  - **HOWEVER** the port contract that adapters implement (FR-006 `Outcome`) does not name a `PermissionDenied` category — only `Failed(reason: String)` with a free-form string. This means callers cannot programmatically distinguish "permission denied, needs settings deep-link" from "network failed, retry later" from "app not installed".
  - Prompt context calls this out explicitly: "permission-denied paths for future Providers (LockdownProvider without Device Owner, GeoFenceSosProvider without location permission — even though these Components are deferred, the port contract should imagine them)".
  - **Suggested fix**: extend `Outcome` sealed hierarchy in FR-008 with either:
    - Option A: `PermissionRequired(permission: String, rationale: String)` as a separate outcome variant, OR
    - Option B: structured `Failed(kind: FailedKind, message: String)` where `FailedKind = { PermissionDenied, NetworkUnavailable, AppUnavailable, PolicyBlocked, Unknown }`.
  - Rationale: `Failed(reason: String)` is a category-1 anti-pattern — CHK017 aggregation-by-category impossible when reason is a free string. Deciding this now is cheap (additive sealed variant); deciding it in Phase 3 when LockdownProvider ships requires wire-format migration if the outcome is persisted.
- [!] CHK013 Permanent denial ("don't ask again"): explicit recovery path (settings deep-link, feature disabled with explanation).
  - **GAP**: not addressed. Foundation-appropriate answer would be "adapter responsibility per Component" but the port contract offers nowhere to encode a recovery deep-link.
  - **Suggested fix (light)**: add sentence to FR-008 Outcome definition: "For `Failed` due to a recoverable permission gap, adapters SHOULD surface remediation via a follow-up port (e.g., `PermissionRepairIntent` in the Android adapter module) — not embedded in the domain `Outcome`." Keeps domain clean while acknowledging the concern.

## Recovery from invalid state

- [x] CHK014 If persistent state can become corrupt (schema drift, partial write): recovery path defined (reset, re-fetch, notify user).
  - Unknown poolRef → `profile.unknownRefs` marker + retry on upgrade (edge case).
  - `activeComponents` referencing removed Component → `Skipped`, no crash (edge case).
  - schemaVersion too high → reject preset, fail loud (FR-012, edge case).
  - Wizard interrupted mid-Interactive step → status `Pending`, resumed on next launch (edge case, US1 acceptance #3).
  - preWizardSnapshot > 7 days → auto-purge, undo disabled (edge case).
- [x] CHK015 No "crash and restart" as the recovery strategy for handled error types.
  - Every listed failure has a non-crash recovery path (skip, mark, retry-on-boot, fall back to NoOp).

## Diagnostics

- [!] CHK016 Failures are observable: at minimum one diagnostic event emitted with category, not PII.
  - **GAP**: spec has no `## Observability` or `## Diagnostics` section. No mention of telemetry events, log categories, or metrics. Fitness functions cover invariants but not runtime observability.
  - **Suggested fix**: add FR-026 (or add to Assumptions) — "Each `Outcome.Failed` and `Outcome.Unsupported` MUST be recorded via a `DiagnosticSink` port with fields `{stepId, componentType, outcomeCategory, timestamp}`. No parameter values, no PII. `DiagnosticSink` MVP adapter is `LogcatDiagnosticSink` — future adapters (Crashlytics-free / own-server) additive." This also enables SC-004-style metrics without breaking domain isolation (rule 1).
- [!] CHK017 Failures aggregated by category (not unique per error message string), to enable rate measurement.
  - Blocked by CHK012 gap: current `Failed(reason: String)` is a free-form string, so aggregation across devices is impossible without a category taxonomy.
  - Fix ties to CHK012: structured `FailedKind` enum enables per-category counters.

---

## Summary

- Total: 17
- Passed `[x]`: 12
- Failed `[!]`: 5 (CHK007, CHK009, CHK012, CHK013, CHK016+CHK017 as one cluster)
- N/A: 0

### Top-3 required fixes (by impact × cost)

1. **CHK012+CHK017 (highest ROI)** — replace `Failed(reason: String)` with structured `FailedKind` enum in FR-008 `Outcome`. Enables permission-denied semantics for future Providers (LockdownProvider, GeoFenceSosProvider named in prompt context), enables aggregation, costs one sealed subtype in domain. Cost of fixing later: wire-format migration if Outcome is ever persisted. One-way door candidate (per CLAUDE.md rule 3) — decide now.
2. **CHK009** — add explicit idempotency clause to FR-006 Provider contract. One sentence. Prevents reconcile-loop double-effects. Standard Kubernetes-style discipline, zero-cost now, expensive to retrofit if adapters ship non-idempotent apply().
3. **CHK007** — clarify retry policy for `Failed` steps across `RunMode.BootCheck` and Settings edits. One sentence in FR-010. Currently implicit and inconsistent between critical/non-critical steps.

### Optional additions

- CHK016: `DiagnosticSink` port — foundation-appropriate to declare now even if MVP adapter is `LogcatDiagnosticSink`. Prevents ad-hoc `Log.e()` calls appearing in Providers (which would violate rule 1 domain isolation).
- CHK013: sentence noting that permission-repair intents live in adapter modules, not in domain `Outcome` — keeps port contract disciplined.

### Cascading rollback (from prompt context)

Prompt asked to verify: "undo Wizard rollback 'best-effort revert via reverse-order apply' — what if reverse also fails? cascading failure handling?"

Spec addresses this partially in the edge case: reverse-order apply → `Unsupported` → "best-effort revert через повторный apply старого значения". This is a graceful terminal path (system converges to "some steps reverted, some stuck in Failed state, user sees final state in Settings"). **However** the spec does not say what the user sees at the end of a partially-failed undo. Suggested addition to US1 acceptance #4 or to the edge case: "If any reverse-apply step returns `Failed` or `Unsupported`, the confirm dialog result surface reports 'N of M steps reverted, K steps could not be undone — see Settings for details' rather than a bare success/failure."
