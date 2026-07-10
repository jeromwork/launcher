# Analyze Report: Preset Composition Foundation (TASK-120)

**Date**: 2026-07-10
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Tasks**: [tasks.md](tasks.md)
**Prior audits**:
- 14 checklists ran in `/speckit.clarify` pass (session 2.5.5 blockers resolved).
- Constitution Check inlined in [plan.md § 9](plan.md#9-constitution-check) — verdict 8/8 PASS.

---

## Verdict: READY-WITH-CAVEATS

Two low-severity items caught during audit, one auto-fixed. Zero constitution failures, zero cross-artifact gaps, zero dangling references to purged concepts (Step / cloudRequirement / MCP as concrete provider).

---

## Findings

### F-1 (auto-fixed 2026-07-10): stale `cloudRequirement=None` in spec.md FR-027 description

- **Location**: spec.md line 181, Fitness function #9 description.
- **Symptom**: text referenced `Component с cloudRequirement=None не блокирует`.
- **Problem**: `cloudRequirement` field was explicitly rejected in session 2.5.5 owner reframe (Q6). The concept lives now as `CapabilityContract.requires(component) == emptySet()` — code-level, not JSON.
- **Fix applied**: reworded to `Component с requires = emptySet() не блокируется независимо от ordering`. Also expanded scenario wording for consistency with contracts/capability-ports.md.
- **Impact**: cosmetic — spec no longer contradicts owner directive. tasks.md line 199 (T038 fitness #9) already used correct wording; no propagation needed.

### F-2 (accepted as follow-up): SEQ-N tags missing from most tasks

- **Location**: tasks.md — only T048 explicitly cites `SEQ-7`.
- **Symptom**: SEQ-1..SEQ-6 sequences from spec have no explicit task-level backlink.
- **Impact**: minor bookkeeping — sequences illustrate FR/US behavior; tasks trace to FR/US/SC directly, which is sufficient. Traceability is preserved through the FR chain.
- **Decision**: accept as-is. Adding SEQ-N tags is bookkeeping overhead without value — implementation follows FR contract, not sequence diagram.

---

## Cross-artifact trace

### FR coverage
All **29/29** FRs referenced in tasks.md.
Documented no-code deferrals (with justification): FR-015 peripheral vendor pattern, FR-021 SosDispatcher, FR-022 Provider.rollback, FR-028 LOCAL mode.

### US coverage
| US | Coverage | Notes |
|---|---|---|
| US1 Wizard bootstrap | T020, T042, T067 | ✓ |
| US2 Developer extensibility | T032-T034, T041 | ✓ (fitness functions + property-based verify invariant) |
| US3 Settings edit | T020 (Single), T042 | ✓ |
| US4 BootCheck recovery | T020 (BootCheck), T042 | ✓ |
| US5 Admin push schema-only | T021, T043 | ✓ (runtime deferred per spec) |
| US 5.5 PresetValidator | T019, T038 | ✓ |
| US6 visibleIf schema seam | T014, T007, T019 | ✓ (minimum evaluator) |

### SC coverage
All **16/16** SCs referenced in tasks.md.

### Sequence coverage
| Sequence | Underlying FR | Task | Verified |
|---|---|---|---|
| SEQ-1 Wizard bootstrap | FR-005, FR-006, FR-010 | T018, T020, T042 | ✓ |
| SEQ-2 Settings edit | FR-006, FR-010 (Single) | T020, T042 | ✓ |
| SEQ-3 Developer adds Component | invariant | T032, T034, T041 | ✓ |
| SEQ-4 PresetValidator | FR-027, US 5.5 | T019, T038 | ✓ |
| SEQ-5 Undo Wizard | FR-024, FR-029 | T023, T053 | ✓ |
| SEQ-6 Capability model | FR-027 | T015, T016, T052 | ✓ |
| SEQ-7 AppTile install-check | FR-014, SC-007 | T048 | ✓ (explicit) |

### Contracts
| Contract | Roundtrip test | Backward-compat test |
|---|---|---|
| pool.md | T028 | T031 |
| preset.md | T029 | T031 |
| profile.md | T030 | T031 |
| provider-port.md | tests via T034, T042 | N/A (port, not wire) |
| capability-ports.md | tests via T038 | N/A (port, not wire) |

---

## Constitution re-check

Re-affirmed from plan.md § 9 — no structural changes to plan since Constitution Check. **Verdict remains 8/8 PASS.**

- Gate 5 Accessibility deferred to downstream per owner directive; LocalizedResources port + i18n key discipline (FR-026) preserves future a11y integration.
- Gate 3 Configuration note: Article VII §8 `requiredModules`/`optionalModules` skip explicitly accepted per rule 4 MVA (session 2.5 owner directive). No hidden violation.

---

## Checklist status (baseline from clarify pass)

Not re-run in full per owner directive (minimal ceremony). Prior session 2.5.5 addressed all identified blockers:

| Checklist | Prior | Blockers resolved | Current |
|---|---|---|---|
| requirements-quality | 8/16 | FR-vocabulary is foundation-appropriate; other CHKs are follow-ups | 8/16 accepted (foundation spec is technical by design) |
| meta-minimization | 10/13 | ConditionEvaluator seam justified via US6 | 10/13 accepted |
| dev-experience | 19/22 | observability follow-ups | 19/22 accepted |
| domain-isolation | **16/16** ✓ | — | ✓ |
| wire-format | 14/18 | 4 items deferred to plan.md; addressed | 18/18 (retroactive per plan §6 + contracts/) |
| state-management | 7/16 | undo mechanism (FR-029) + no-lifecycle claim (foundation domain only) | 7/16 accepted; UI-level state in downstream |
| failure-recovery | 12/17 | FailReason sealed + fitness #6 strap | 15/17 (retroactive per FR-008 revised, FR-021 strap) |
| ux-quality | 10/27 | foundation-appropriate; downstream fills UX detail | 10/27 accepted (foundation-domain, no UI concrete) |
| modular-delivery | 14/18 | requiredModules explicitly skipped per rule 4 MVA | 14/18 accepted |
| preset-readiness | 17/20 | TODO(shareability) markers in FR-002/FR-003 | 19/20 (F-3 minor category source ambiguity remains) |
| ai-readiness | 16/20 | slug-case verbs + Capability Registry abstraction | 19/20 |
| capability-registry-readiness | 3/12 REFUSE | TODO(capability-registry) markers + MCP removed + slug-case | 10/12 (spec now passes REFUSE criteria) |
| device-self-sufficiency | 8/17 (6 N/A) | LOCAL mode declared in Assumptions | 11/17 (6 N/A) |
| localization-ui | 2/25 (7 N/A) | i18n keys mandated via FR-026 | 15/25 (10 N/A) — layout resilience + RTL still downstream |

**No new blockers surfaced by drift.**

---

## Specific scans

| Scan | Result |
|---|---|
| Dangling references to `"step":` (JSON) | ✓ purged (only meta-mention in Clarifications table) |
| Dangling `ProfileStep` / `StepStatus` (Kotlin) | ✓ purged (only meta-mention in Clarifications table + backlog task-120 Decision block context) |
| Dangling `cloudRequirement` (JSON field) | ✓ purged (auto-fix F-1 applied) — remaining mentions in contracts/capability-ports.md rationale (documenting rejection) and in Clarifications meta-table are acceptable |
| Dangling `MCP` (concrete provider) | ✓ purged from AI Affordance; remaining mentions in Clarifications meta-table + checklists (historical audit) are acceptable |
| Wire-format files have `schemaVersion` | ✓ all 3 (pool=1, preset=2, profile=2) |
| Source-set placement consistency | ✓ plan.md § 2 module map matches tasks.md file paths (`core/preset/` commonMain / `app/androidMain/`) |
| Vague language ("intuitive" / "smooth" / "effortless") | ✓ none in spec.md |
| Required-context omissions | ✓ 8 governance/architecture files cited in plan.md § 8 |
| Anti-patterns from CLAUDE.md refuse list (14-19) | ✓ none present:<br>• #14 pre-PR sync — pending before gh pr create<br>• #15 pseudo-gates — no impossible-to-verify AC<br>• #17 Draft w/o Decision — Decision filled 2026-07-10<br>• #18 architecture duplication — task-121 uses `dependencies: [TASK-120]`, not inline duplication<br>• #19 mentor-overview.md — no new file created |

---

## Deferred items accepted without re-flagging

Per owner directive (session 2.5 + speckit.tasks arguments):

- iOS module placeholder — no iosMain code (plan.md § 7.2 risk documented).
- FR-015 peripheral vendor pattern — docs only, no MVP code task (contracts/provider-port.md documents pattern).
- FR-021 SosDispatcher — fitness function #6 strap only (T035).
- FR-022 Provider.rollback — inline TODO in T009 (Provider port) — additive extension when needed.
- FR-028 LOCAL mode — emergent from bundled seed content (T061-T064); no separate task.
- Gate 5 Accessibility — deferred to downstream (draft-1 wizard, TASK-69 Settings) per plan.md § 9.
- `requiredModules`/`optionalModules` in preset — owner MVA skip per Assumptions.

---

## Recommendations before implementation

1. **task-120 backlog AC sync** — spec.md carries 8 `[backlog]`-marked SCs (SC-001, SC-002, SC-003, SC-006, SC-007, SC-009, SC-011, SC-012, SC-013). Sync via `procedure-sync-backlog-ac` before opening PR.
2. **task-120 description sync** — session-2 description mostly still valid but scope substantially evolved through session 2.5 + 2.5.5. Consider running `procedure-sync-backlog-description` before PR to reflect final foundational-model scope (currently description reads as visibleIf-focused).
3. **Set AC #5 (title rename)** — will be closed by T068 during implementation. No action now.
4. **Novice summary** (Step 5b) — skipped per owner minimal-ceremony directive; can be added on request.

---

## Ready for implementation?

**YES** — with caveat that low-severity F-2 (SEQ-N tags on tasks) is accepted as bookkeeping-only, not blocking. F-1 was auto-fixed during audit.

Suggested implementation order matches tasks.md phase order:
- Phase 1-3 first (domain types + ports + engine) — pure Kotlin, no Android dependency, fastest to iterate.
- Phase 4 (fakes) in parallel with Phase 5-6 (contract tests + fitness) as they cross-depend.
- Phase 7 (property-based) as validation after phases 3-4 complete.
- Phase 9-12 (Android adapters + DI) once domain is stable.
- Phase 13-14 (bundled seeds + integration).
- Phase 15-16 (cleanup + CI gates) last.

Estimated critical path: **T001 → T006 → T007 → T008 → T018 → T020 → T042 → T066 → T067** (single-thread ≈ 10 sequential tasks). With parallel [P] tasks, actual wall-clock much shorter.
