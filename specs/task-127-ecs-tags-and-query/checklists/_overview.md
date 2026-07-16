# Checklists Overview — task-127 ECS Tags + Query

Applied: 2026-07-15 (updated after re-run of 3 checklists post-spec-fixes)
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

## Summary

| Checklist | Status | Passed | Total | Open items / blockers |
|-----------|--------|--------|-------|----------------------|
| requirements-quality | ✓ | 15 | 16 | CHK009 — SC uses Kotlin identifiers (acceptable for foundational refactor) |
| meta-minimization | ✓ | 13 | 13 | none |
| dev-experience | ✓ | 20 | 22 | CHK013 recommend `TODO(physical-device)` inline (plan phase); CHK018 add log tag on ProfileBackedFlowRepository (plan phase) |
| domain-isolation | ✓ | 16 | 16 | none |
| wire-format | ✓ | 18 | 18 | none (recommend documenting unknown-Tag-value deserializer behavior in plan) |
| state-management | ✓ | 16 | 17 | CHK015 — cross-ref TASK-120 Profile rehydration tests |
| failure-recovery | ✓ | 17 | 17 | none (corrupt Profile recovery explicitly out-of-scope with rationale; diagnostic log tag deferred to plan via dev-experience CHK018) |
| ux-quality | ✓ | 22 | 22 | none |
| localization | ✓ | 20 | 20 | none (`wizard_step_of` = `<plurals>` with Russian `one/few/many/other`) |
| localization-ui | ✓ | 17 | 19 | CHK-UI-011/012 — shared with checklist-localization plural issue (now resolved upstream) |
| elderly-friendly | ✓ | 21 | 22 | CHK014 — cross-ref failure-recovery corrupt Profile path (now out-of-scope) |
| preset-readiness | ✓ | 16 | 20 | CHK006/CHK009 inherited from TASK-120 (packageName + fallback); CHK012 recommend `TODO(shareability)` for ComponentDeclaration.tags override; CHK015 v3→v2 reject path |
| modular-delivery | ✓ | 17 | 18 | CHK007 N/A (additive field, not new profile) |

## Totals

- **Total checks**: 240 across 13 checklists
- **Passed**: 228 (was 220 — +8 after re-run)
- **Open items**: 12 (all recommendations for plan phase, not blockers)
- **Hard FAIL**: 0 checklists

## Blockers for speckit-plan

None. All three prior blockers closed:
1. ~~dev-experience CHK001/CHK002~~ → `## Local Test Path` section added (lines 197-203).
2. ~~failure-recovery CHK014~~ → corrupt Profile recovery explicitly out-of-scope with rationale.
3. ~~localization CHK005/CHK006~~ → FR-008 declares `wizard_step_of` as `<plurals>` with Russian forms.

## Recommendations for plan phase

- Add inline `TODO(physical-device)` in code touched by ProfileBackedFlowRepository DI wiring.
- Add log tag (e.g. `ProfileBackedFlowRepo`) for state transitions (null Profile → non-null, empty → non-empty).
- Add inline `TODO(shareability)` at ComponentDeclaration.tags override site.
- Document unknown-Tag-value deserializer behavior (skip vs fail-closed) — recommend fail-closed given closed additive-only set.
- Cross-ref existing TASK-120 Profile rehydration tests in test plan.

## Not applicable / trivially passed

- No new UI screens → localization-ui / elderly-friendly / ux-quality mostly N/A.
- No new external SDK → domain-isolation trivially passes.
- No new module → modular-delivery trivially passes.
- No new server endpoint → server-hardening + zero-knowledge-server not triggered.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Прогнали 13 чеклистов на spec.md (240 проверок, **228 pass**). После fix'а 3 blocker'ов в spec.md и re-run trex чеклистов — **все зелёные, hard FAIL нет, blocker'ов для speckit-plan нет**. Осталось 12 open items — все recommendation'ы для plan phase (inline TODOs, log tags, cross-refs), не блокеры.

**Конкретика, которую стоит запомнить:**
- **dev-experience 18/22 → 20/22** (после Local Test Path section). CHK013/CHK018 остались как plan-phase рекомендации.
- **localization 17/20 → 20/20** (после `<plurals>` для `wizard_step_of`). Полный зелёный.
- **failure-recovery 14/17 → 17/17** (после Out-of-Scope corrupt Profile recovery с rationale). Полный зелёный.
- **preset-readiness 16/20** — 4 open items унаследованы от TASK-120 (packageName + fallback + shareability TODO + v3→v2 reject) — не блокеры для TASK-127.
- **requirements-quality 15/16** — CHK009 (SC uses Kotlin identifiers) accepted для foundational refactor.
- Не применимы: server-hardening / zero-knowledge-server (нет endpoint'ов), permissions-platform (нет новых permissions).

**На что смотреть с осторожностью:**
- preset-readiness open items (packageName + fallback) — если TASK-120 их не закроет до Verification TASK-127, они всплывут снова.
- Рекомендации для plan phase (inline TODOs `TODO(physical-device)` + `TODO(shareability)`, log tag `ProfileBackedFlowRepo`, unknown-Tag deserializer decision) — не блокеры, но должны попасть в tasks.md, иначе потеряются при переходе plan → tasks → implement.
