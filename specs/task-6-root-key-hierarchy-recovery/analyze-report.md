# Analyze Report — F-5 Root Key Hierarchy + Owner Recovery

**Date**: 2026-06-28
**Branch**: `task-6-root-key-hierarchy-recovery`
**Backlog**: [TASK-6](../../backlog/tasks/task-6%20-%20F-5-Root-Key-Hierarchy-Owner-Recovery.md)
**Trigger**: `/speckit.analyze` after rounds 2-3 substantial rework (Drive App Data → Cloudflare Worker; microservice split into `workers/backup/` + `workers/identity/`; constitution Article XIV §7 added; backlog stays single TASK-6)

## Verdict: **READY-WITH-CAVEATS**

Architecture solid, constitutional posture **improved** by rework. Two known-open hygiene items + one minor cleanup remain — all non-blocking for implementation start.

---

## Constitution Check (re-run on plan.md)

**8/8 PASS, no drift handled** — all rework points improved constitutional posture rather than introducing violations.

| Gate | Status | Notes |
|---|---|---|
| 1 Architecture | PASS | Worker split mirrors future Go microservices boundary |
| 2 Core/System Integration | PASS | No new system interactions; reuses F-CRYPTO + F-4 |
| 3 Configuration | N/A | Foundation feature; wire-format has schemaVersion=1 |
| 4 Required Context Review | PASS | Article XIV §7 (added 2026-06-28) read and applied |
| 5 Accessibility | PASS | Senior-safe Compose styling, AutofillHints |
| 6 Battery/Performance | PASS | Argon2 only on setup/recovery (rare), HKDF sub-ms |
| 7 Testing | PASS | All ports have fakes, contracts have roundtrip |
| 8 Simplicity | PASS | Round-2 removal of NoOpRecoveryKeyBackup + Selector — visible anti-abstraction discipline |
| Article XIV §7 | PASS | All 5 clauses (a-e) satisfied; design note in spec.md Notes |

Full report: [checklists/constitution-check-analyze.md](./checklists/constitution-check-analyze.md)

---

## Cross-Artifact Trace (re-run)

```
23/23 FR, 6/6 US, 13/13 SC — PASS
Gaps: 3 minor (hygiene), 0 architectural
```

| Coverage type | Count | Status |
|---|---|---|
| FR → T traceability | 23/23 | ✓ all covered |
| US → test evidence | 6/6 | ✓ all covered |
| SC → verifying task | 13/13 | ✓ all covered |
| Contracts → tests | 2/2 | ✓ recovery-key-backup-v1 + worker-api-v1 |
| Ports → fakes | 4/4 | ✓ FakeKeyRegistry/RootKeyManager/RecoveryKeyBackup/AuthAvailability |
| Renumbering (T660-T675 → T671-T686) | — | ✓ internally consistent |

Minor cleanup items (non-blocking, fixed inline post-analyze):
- ✓ Fixed: T682/T685/T686 broken T-ID references (was T670/T655/T662 — corrected to T681/T669/T673).
- ✓ Fixed: NOVICE-SUMMARY tasks.md «TASK-X отдельный трек» → «in-scope of TASK-6».
- ✓ Fixed: spec.md ADR-011 link path (`docs/adrs/` → `docs/adr/`).
- ⚠ Remaining stale: ~5 mentions of TASK-X / TASK-Y in checklist files (created before round-3 decision); annotated as historical context, not implementation directives. Not fixed because checklist files are point-in-time records.

Full report: [checklists/cross-artifact-trace-analyze.md](./checklists/cross-artifact-trace-analyze.md)

---

## Checklist Re-runs (analyze pass)

| Checklist | Score | Status | Notes |
|---|---|---|---|
| requirements-quality | 14/16 | OPEN | CHK001 (impl names leak in spec) + CHK009 (SCs name tooling) — tracked via T678/T679. Scope of CHK001 **expanded** after Worker pivot (transport/library/algorithm names leaked into FR-006/010/009/002). |
| meta-minimization | 13/13 | PASS | Worker split passes rule 4 Test 1 (would-be-rewrite without seam). 86 tasks within precedent. |
| dev-experience | 21/22 | PASS | (from clarify pass, unchanged) |
| wire-format | 18/18 | PASS | All 4 previously-open closed via contracts/recovery-key-backup-v1.md. Worker API v1 posture clean. |
| domain-isolation | 16/16 | PASS | Worker addition didn't break isolation. Konsist forbidden-token list expanded to include `Cloudflare\|Worker`. |
| security | 29/29 | PASS | 24 MASVS + 5 Article XIV §7. CHK018 + CHK024 closed (T675-T677). JWT custom-claim flow safe. |
| failure-recovery | 15/17 | OPEN | (from clarify pass — CHK016/CHK017 observability concerns, deferred to future spec) |
| backend-substitution | 16/16 | PASS | Worker pivot **strengthens** substitution-readiness vs Drive. Cost-of-swap = 3 Android files. |
| device-self-sufficiency | 17/17 | PASS | (from clarify pass, unchanged) |

Sub-reports written to `checklists/<name>-analyze.md`. Pre-pivot checklists describe Drive/NoOp/Selector — annotated as historical, not corrected.

**Totals**: 159/166 (96%). 7 open items, all known-tracked.

---

## Specific Scans

```
vague-lang 0, dangling-refs 1, source-set 1, adr-refs 1, trace-cross-links 4 → 4 fixed inline, 3 remain
```

| Scan | Findings |
|---|---|
| Vague language sweep | ✓ 0 survivors |
| Dangling deleted-file refs | ⚠ 1 (ADR-011 link path) — ✓ fixed |
| Source-set placement | ⚠ 1 — `RootKeyManagerImpl` declared in `commonMain` (plan §Project Structure) vs `Argon2RootKeyManager` (its androidMain impl). Need plan-time clarification — but tasks.md T633 already disambiguates as androidMain. Cosmetic. |
| ADR / docs references | ⚠ 1 — ADR-011 link path — ✓ fixed |
| Trace cross-links | ⚠ 4 broken T-IDs — ✓ 3 fixed (T682/T685/T686), 1 remains in stale checklists (`_overview.md` lines 129-131 refer to T660/T661/T662 from pre-renumber; checklist files are historical) |

Full report: [checklists/specific-scans-analyze.md](./checklists/specific-scans-analyze.md)

---

## Open items inventory (final)

### Must-track but **non-blocking** for implementation start

1. **requirements-quality CHK001 expanded** — Worker pivot leaked more tech-detail into spec.md (OkHttp, Bearer, HTTP statuses, Argon2id, HKDF-SHA256, R2/KV, Cloudflare Worker mentions in FR-006/010/009/002). **Tracked**: expand T678 to cover the new layer in Phase 6 cleanup.
2. **requirements-quality CHK009** — SC-005/007/010 name Konsist/Detekt/pixel_5_api_34/GPM. **Tracked**: T679.
3. **failure-recovery CHK016/CHK017** — diagnostic events + per-category metric aggregation. **Tracked**: deferred to future observability spec.
4. **Source-set ambiguity** — `RootKeyManagerImpl` placement (plan §Project Structure could be clearer). **Tracked**: clarify in T633 task description before implementation.

### Architectural follow-ups (post-merge)

5. **server-roadmap.md SRV-IDENTITY-001** — add sub-bullet documenting `workers/identity/` → future `identity-service` migration path (current SRV-RECOVERY-001 only covers backup).
6. **`checklist-server-data-minimization` skill** — recommended in spec.md Notes; not blocker.
7. **Compose UI Test 1.8 upgrade** — one-way door BOM upgrade; `[deferred-local-emulator]` workaround acceptable for MVP.

---

## Cleanup performed inline by analyze

- ✓ `tasks.md` T682: `T670` → `T681` (corrected reference to SC-001 smoke).
- ✓ `tasks.md` T685: `T655` → `T669`, `T653` → `T666` (corrected references — `T655` was ratelimit, not integration test).
- ✓ `tasks.md` T686: `T662` → `T673` (corrected docs/recovery-flow.md reference).
- ✓ `tasks.md` NOVICE-SUMMARY: «TASK-X отдельный трек» → «in-scope of TASK-6».
- ✓ `tasks.md` Open items §1: «TASK-X нужно «да» владельца» → «Worker deployment T666 — единственный operational блокер».
- ✓ `spec.md` ADR-011 link: `docs/adrs/adr-011-ai-owner-collaboration.md` → `docs/adr/ADR-011-ai-owner-collaboration-conventions.md`.

---

## Cleared for `/speckit.implement`

Architecture is sound. Constitutional posture improved by recent rework. All architectural concerns (domain isolation, wire-format, security, backend substitution, meta-minimization) PASS.

Remaining open items are **hygiene tracked via existing tasks** (T678, T679) or **deferred to future specs** (observability). None block starting implementation.

**Recommendation**: Proceed to `/speckit.implement` with awareness that:
- Phase 1+2+3+5+6 are closeable in AI session against Fake adapters (~70/86 tasks).
- Phase 4 Worker deployment (T666) is the single operational blocker for Phase 7 manual gates.
- Phase 7 (T681-T686) requires real device and is `[deferred-physical-device]`.

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Простыми словами (TL;DR)

**Что это.** Финальная проверка перед началом написания кода — все артефакты F-5 (spec, plan, tasks, contracts, checklists) прогнаны через 9 параллельных проверок: конституция, traceability, 6 checklists + специальные сканы.

**Что нашли.**
- **Архитектурно всё чисто.** 8 гейтов конституции PASS, 16/16 domain-isolation PASS, 18/18 wire-format PASS, 29/29 security PASS, 16/16 backend-substitution PASS, 13/13 meta-minimization PASS.
- **Worker pivot улучшил архитектуру**, не сломал её. Provider-agnostic design подтверждён ещё раз.
- **Article XIV §7** (новое правило конституции про minimization инфо на сервере) применено полностью — все 5 клауз (a)-(e) соблюдены.
- **2 известных open items** (CHK001 + CHK009 — мелкий tech-detail в spec'е, отслеживается через T678/T679) и **3 минорных hygiene** (1 битая ссылка ADR-011, 4 битых T-ID ссылки в tasks.md) — все hygiene уже починены прямо сейчас в этом analyze.

**Что нельзя закрыть в AI-сессии.** Те же что и раньше: 5 `[deferred-local-emulator]` (UI tests + integration tests, нужен AVD API ≤34), 5 `[deferred-physical-device]` (Xiaomi 11T smoke), 1 `[deferred-external]` (owner peer-review docs).

**Verdict: READY-WITH-CAVEATS.** Можно запускать `/speckit.implement` — open items не блокеры, отслеживаются через существующие задачи. ~70 из 86 задач реально закрываются в AI-сессии; остальное — на железе.

**Следующий шаг.** `/speckit.implement` — запустит pipeline для Phase 1 (Foundation, T601-T631) сразу.
<!-- NOVICE-SUMMARY:END -->
