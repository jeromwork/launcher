# Analyze Report: Settings as Profile View (TASK-69)

Pre-implementation audit (2026-07-18) over the complete artifact set: spec.md, plan.md, data-model.md, tasks.md. Model authority = [`ecs.md`](../../docs/architecture/ecs.md) (cited, not restated).

## Constitution Check
**8/8 PASS** (plan.md §10; Gate 5 accessibility fixed via SC-011). No changes since — re-affirmed.

## Cross-artifact trace
- **FR coverage**: FR-000..017, 020, 021 all covered by tasks; FR-018 / FR-019 are **negative** scope-boundary requirements (no task needed, honoured by re-host-only design).
- **US evidence**: US1 → T069-008; US2 → T069-011/013/014/016; US3 → T069-017/031; US4 → T069-004/006.
- **SC**: SC-001..005 [backlog] → test tasks; SC-006 → T069-015/028; SC-007 → T069-026/027; SC-008 → T069-013; SC-009 → T069-025; SC-010 → T069-008; SC-011 → T069-018/033.
- **Gates**: port has fake (T069-009); legacy delete has deletion (T069-023) + grep-verify (T069-024); no new wire format → no roundtrip task (correct).

## Checklists (chat-only per ADR-011 §5)
- requirements-quality ✓ (numbered FRs, measurable SCs, 0 NEEDS CLARIFICATION)
- meta-minimization ✓ (gateway + builder justified; editability field rejected)
- domain-isolation ✓ (port/builder in core, no Android; valueText pre-formatted; SC-006 fitness)
- failure-recovery ✓ (FR-010 keep-value, FR-012 cancel, FR-013 Unverifiable, edit-during-apply risk, no-profile/empty-map edges)
- ux-quality ✓ · accessibility ✓ (SC-011) · localization ✓ (SC-007) · state-management ✓ (language recreation T069-019) · permissions-platform ✓ (ROLE_HOME existing, OEM deferred) · wire-format N/A (no new format)

## Scans
- **Deleted-file dangling refs**: legacy `SettingsScreen` is wired into **Decompose nav** (`RootContent`, `RootChild`, `RootComponent`, `SettingsComponent`, `SettingsScreenTest`), while the ECS-era screen is an **Activity** (`SettingsActivity`). Two nav stacks to reconcile → captured in **T069-020** + plan §8 risk (SettingsActivity = single host; Decompose entry torn down).
- **Wire-format schemaVersion**: new types (`SettingsView` etc.) are **runtime non-persisted** — no schemaVersion needed (data-model.md explicit). ✓
- **Source-set placement**: gateway/builder/view in `core/preset` (commonMain), adapter + UI in `app`. Consistent with plan. ✓
- **Required-context**: ecs.md, ADR-013, constitution articles, permissions-budget all linked. ✓
- **Vague language**: none found. ✓

## Verdict: READY-WITH-CAVEATS

Cleared for implementation (fresh session). Open items — all **documented, none blocking**:
1. **Nav-stack reconciliation** (T069-020): confirm the Decompose `SettingsComponent`/`RootChild` teardown + `SettingsActivity` single-host approach during the audit task. Now captured in tasks + plan risk.
2. **Deferred verification** (expected, not blockers): T069-031–033 `[deferred-local-emulator]` (US3 dialog, language recreation, TalkBack), T069-034 `[deferred-physical-device]` (Xiaomi OEM). These keep the backlog task in **Verification** until run on hardware.

No Constitution FAIL, no cross-artifact gap, no undefined behaviour. Implementation may start.

<!-- NOVICE-SUMMARY:BEGIN -->
## Коротко для владельца

**Финальная проверка перед кодом — пройдена.** Вердикт: **готово с оговорками** (ничего блокирующего).

- Конституция 8/8, все требования покрыты задачами, лишних абстракций нет, формат не меняется.
- Единственная реальная деталь, которую вскрыл аудит: старый экран настроек вшит в **одну** систему навигации (Decompose), а новый — в **другую** (Activity. Поэтому «поглощение» = свести к одному хосту (новый экран), старую навигацию разобрать. Это уже прописано в задаче T069-020 — имплементатор не удивится.
- Отложенное (нормально, не блокеры): визуальные прогоны на эмуляторе (диалог, смена языка, TalkBack) и проверка на Xiaomi — их делаем на железе; задача останется в статусе Verification, пока их не закроют.

**Можно начинать код** — в отдельной сессии (по твоему правилу для крупных фич).
<!-- NOVICE-SUMMARY:END -->
