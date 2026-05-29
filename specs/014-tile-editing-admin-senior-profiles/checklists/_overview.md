# Checklists overview — spec 014

Generated: 2026-05-29 by `speckit-clarify`.

## Run summary

Spec size: **Large** (≈430 lines, 4 US, 23 FR sub-items, 8 SC, 3 phases F-014.0/0.1/0.2).

Clarifications asked this pass: **4 questions** (Q6-Q9). All resolved in `spec.md` §Clarifications + FR updates.

Checklists run: **18** (3 always-on + 15 triggered, 2 skipped: core-quality, notification-minimization).

## Per-checklist verdict

| Checklist | Verdict | Open items |
|---|---|---|
| requirements-quality | PASS | 2 minor (forward-refs OK) |
| meta-minimization | PASS | 1 note (named configs sub-FR — acceptable, exit ramp documented) |
| dev-experience | PASS | 5 items для plan.md (emulator preset name, DI wiring, v1 fixture, log tags, background failure surfacing) |
| domain-isolation | PASS | 0 |
| wire-format | PASS (F-014.0); 5 items для F-014.1 phase |
| state-management | PASS | 4 items для plan.md (rotation/process-death edit-mode survival, recreation tests, foldable scope) |
| failure-recovery | PASS | 2 items для plan.md (v1→v2 mid-flight recovery, diagnostic taxonomy) |
| performance | PASS | 3 items (frame budget picker, picker cold-load lazy, perf-checkpoint) |
| security | PASS | 4 items (PII contact tile verify, F-5 production gate, configName validation, PII-free logging) |
| permissions-platform | PASS | 4 items (package visibility, OEM touch testing protocol, long-press dispatch) |
| ux-quality | PASS | 2 items (loading states, gender-neutral copy fallback) |
| accessibility | PASS basic | **Significant work** для plan.md (contentDescription coverage + TalkBack drag alternative) |
| elderly-friendly | PASS | 0 (exemplary design per Q4/Q7/FR-019/FR-021) |
| modular-delivery | PASS | 0 |
| backend-substitution | PASS | 4 items (server-roadmap.md entry, atomic invariant alternative, Firestore Rules mapping) |
| preset-readiness | PASS | 2 items (TODO-FUTURE-SPEC-007 hooks для activeDeviceIds strip) |
| ai-readiness | PASS | 3 items (PII anonymization policy, AI auth/audit — F-2 responsibility) |
| localization | PASS | 6 items (significant strings.xml inventory + plurals + RTL verify) |

## Aggregate open issues — top priorities для plan.md

1. **Accessibility — TalkBack alternative for drag-and-drop** (accessibility CHK010). FR-012 universal mainstream UX is incompatible с screen reader без explicit alt mechanism (e.g. context-menu "Переместить вверх / вниз").
2. **Localization — strings.xml inventory + Russian plurals** (localization CHK001-CHK004). ~13 strings + 3-4 plural rules.
3. **State management — edit mode survival across rotation / process death** (state CHK001-CHK002). Plan.md должен specify `rememberSaveable` / Decompose state.
4. **Wire format — v1→v2 migration code + roundtrip + backward-compat fixtures** (wire CHK007, CHK010-CHK011). For F-014.1 phase.
5. **Security — configName validation rules + PII-free logging policy** (security CHK009-CHK010).

## Cross-cutting recommendations

- **Phasing discipline**: F-014.0 has minimal open items; F-014.1 introduces ~10 items related to schema migration, server backup, encryption phasing. Plan.md should explicit'но split phase scopes.
- **TalkBack accessibility** is the **only architecturally significant gap** — может потребовать FR addition (alt drag mechanism в FR-012).
- **No blockers** для proceed to `speckit-plan`. All open items are plan-level details, not spec-level decisions.

## Next steps

1. (Optional) Address TalkBack drag alt — может потребовать ещё одну clarification round или FR add.
2. Run `speckit-plan` to generate `plan.md` + `research.md` + `data-model.md` + `contracts/` for F-014.0 phase.
3. F-014.1 / F-014.2 phases — separate plan rounds when dependencies (F-4, F-5) closer to ready.
