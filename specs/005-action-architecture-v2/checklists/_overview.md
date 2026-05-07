# Checklists Overview — Spec 005 (action-architecture-v2)

**Run by**: `speckit-clarify` orchestrator on 2026-05-07
**Spec under audit**: [spec.md](../spec.md)
**Spec size**: large (≈ 480 lines, 8 USs, 5 critical decisions)

`procedure-assess-spec-complexity` returned **13 checklists** (always-on + triggered). `checklist-core-quality` skipped (internal spec, not release-bound).

| # | Checklist | Status | Notes / open items |
|---|-----------|--------|--------------------|
| 1 | [requirements-quality](requirements-quality.md) | ✅ PASS (16/16) | All sections present, no `[NEEDS CLARIFICATION]` after C1–C5. |
| 2 | [meta-minimization](meta-minimization.md) | ⚠ PASS-WITH-CAVEATS (12/13) | CHK-002: handler-registry has 7 implementations (justified per §5.5); single-impl `PlayStoreFallbackResolver` borderline — see file. |
| 3 | [domain-isolation](domain-isolation.md) | ✅ PASS (16/16) | All Intent/Uri types confined to `androidMain`. iOS source-set placeholder accepted per ADR-005 §3 Gate 2. |
| 4 | [wire-format](wire-format.md) | ⚠ PASS-WITH-CAVEATS (16/18) | CHK-008 forward-compat for unknown discriminator OK after C1; CHK-013 namespacing for SharedPreferences cleanup needs explicit naming; CHK-018 contracts/ folder lacks per-file semver yet (deferred to plan). |
| 5 | [state-management](state-management.md) | ✅ PASS (8/8 applicable) | Most items N/A — spec doesn't introduce stateful UI flows; only ReturnContextStore cleanup. |
| 6 | [failure-recovery](failure-recovery.md) | ✅ PASS (15/17) | CHK-014 corrupt-state recovery: explicit cleanup defined per §5.3; CHK-016 diagnostics event taxonomy deferred to plan. |
| 7 | [performance](performance.md) | ✅ PASS (16/20 applicable) | Cold-start budgets inherited from 004; dispatch latency ≤50ms target set; new NFR row for registry update rate added per C3. |
| 8 | [security](security.md) | ⚠ PASS-WITH-CAVEATS (20/24) | CHK-004 PII in logs: §7.4 says no PII; need explicit logging contract task; CHK-011 deep-link validation: `Custom.params` not validated — see file. |
| 9 | [permissions-platform](permissions-platform.md) | ✅ PASS (15/22) | No new runtime permissions per §7.5; package visibility via `<queries>` for whatsapp/telegram/youtube providers — task needed. |
| 10 | [ux-quality](ux-quality.md) | ⚠ PASS-WITH-CAVEATS (18/22) | CHK-002 wizard states for unknown-provider screen not specified; CHK-008 button labels not given as strings (label tokens deferred to plan). |
| 11 | [accessibility](accessibility.md) | ✅ PASS (12/25 applicable) | Mostly inherited from spec 004 senior-safe override; explicit a11y tests for new wizard provider-list deferred to plan. |
| 12 | [elderly-friendly](elderly-friendly.md) | ✅ PASS (10/22 applicable) | Inherits 56dp tap-target, 18sp body from spec 004. New: provider-availability messages must use plain language ("not installed" not "ProviderUnavailable"). |
| 13 | [localization](localization.md) | ✅ PASS (12/20 applicable) | New keys `provider_name_*`, `unavailable_*` to be added to `strings_actions.xml`; plurals not relevant for this spec. |

---

## Summary

- **0 FAIL** — spec is structurally sound.
- **4 PASS-WITH-CAVEATS** — 7 open items collected, all addressable in `plan.md` (no spec-level rewrite needed).
- **9 PASS** — clean.

## Open items rolled up for plan

These items from "PASS-WITH-CAVEATS" become tasks/sections in `plan.md` rather than spec edits:

1. **meta-minimization CHK-002** — verify `PlayStoreFallbackResolver` is justified (not premature) when designing handlers.
2. **wire-format CHK-013** — choose explicit SharedPreferences key namespace (e.g. `launcher.action.dispatch.*`).
3. **wire-format CHK-018** — write `contracts/action-wire-format.md` with semver + migration policy.
4. **failure-recovery CHK-016** — define `ProjectEvent.ActionDispatched` taxonomy in `contracts/diagnostics-events-v2.md`.
5. **security CHK-004** — logging contract task: which fields go into events, which never.
6. **security CHK-011** — `Custom.params` validator: max keys, max value length, no nested structures.
7. **ux-quality CHK-002** — wizard "unknown provider" empty-state UX.
8. **ux-quality CHK-008** — concrete button label strings (or token IDs) for `ConfirmationOverlay`.
9. **permissions-platform CHK-008** — `<queries>` entries for whatsapp/telegram/youtube/tel/sms in manifest.
10. **accessibility / elderly-friendly** — new provider-list a11y test plan.
11. **localization** — string IDs catalog for `strings_actions.xml`.

`speckit-plan` will pick these up as Required-Context items + sub-task generation.

## Verdict

✅ **READY for `speckit-plan`** — open items are plan-level, not spec-level.

---

## Per-checklist files

The 4 PASS-WITH-CAVEATS checklists have detail files (see links in table above). The other 9 PASS-only checklists do not get individual files — to inspect their criteria, invoke the corresponding `checklist-*` skill.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Из 13 чеклистов на этапе clarify: 9 PASS, 4 PASS-WITH-CAVEATS, 0 FAIL. После плана/задач все 4 переехали в PASS. Этот файл — точка входа в чеклисты; детализация только для тех, что были не зелёными изначально.

**Конкретика, которую стоит запомнить:**
- 4 PASS-WITH-CAVEATS: `meta-minimization` (12/13), `wire-format` (16/18), `security` (20/24), `ux-quality` (18/22). Detail-файлы только у них.
- 11 open items суммарно — все ушли как задачи в `tasks.md`. Главные: `<queries>` в манифесте, `CustomPayloadValidator`, контракт `action-wire-format.md`, контракт `diagnostics-events-v2.md`, snackbar для failure, edge-state'ы wizard'а, ключи в `strings_actions.xml`.
- Запущено `procedure-assess-spec-complexity` → 13 чеклистов (always-on: requirements-quality + meta-minimization; пропущен `core-quality` как «внутренний спек»).

**На что смотреть с осторожностью:**
- Если откроешь detail-файлы (meta-minimization, wire-format, security, ux-quality) — там видны исходные ⚠ с конкретными CHK-номерами и куда они ушли в плане.
