# Cross-Artifact Trace — analyze re-run after rounds 2-3 rework

**Date**: 2026-06-28 (re-run after Phase 4 in-scope rework + T-ID renumber T660-T675 → T671-T686)
**Predecessor pass**: `checklists/cross-artifact-trace.md` (23/23 FR, 6/6 US, 13/13 SC, PASS)
**Artifact set**: spec.md (796L), plan.md (356L), tasks.md (327L, 86 tasks T601-T686),
data-model.md (370L), research.md (263L), quickstart.md (158L),
contracts/recovery-key-backup-v1.md (239L), contracts/worker-api-v1.md (313L)

---

## Section 1 — Spec → Tasks coverage

**FR coverage**: 23 / 23 (all FR-001..FR-023 have implementing task(s)).

Verified inline trace summary table in tasks.md §«Trace summary» lines 202-225 — every
FR row points to at least one valid T-ID present in the body. Spot-checks:

- FR-001 → T601, T602, T606-T609 ✓ (all exist)
- FR-007 → T612, T613, T614, T635, T675 ✓ (T675 is the permissions-doc update — correctly tagged)
- FR-010 → T635, T639, T640, T669, T685 ✓
- FR-011 / FR-012 → T648 (DI single-binding confirms removal of NoOp + Selector) ✓
- FR-018 → T630 (fixture), T637 (memory), T671 (refactor), T672 (test) ✓
- FR-019 → T627 (cascade-wipe test), T648 (event listener wiring noted) ✓
- FR-022 → T616-T619 (all 4 fakes) ✓
- FR-023 → T620-T627 (all contract tests) ✓

No FR without a covering task.

## Section 2 — US → acceptance evidence

**US coverage**: 6 / 6.

| US  | Test evidence tasks |
|---|---|
| US-1 | T602-T605, T610, T620, T622, T626, T645, T649, T681 |
| US-2 | T602, T608-T610, T621-T623, T626, T646, T650, T681 |
| US-3 | T608, T609, T612-T614, T647, T651, T682 |
| US-4 | T606, T607, T615, T634, T641 (mock AuthAvailability), T649-T651 (explainer path) |
| US-5 | T630, T637, T671, T672 (migration tasks all map to US-5) ✓ — note: tasks don't tag with `[US-5]` token; trace via FR-018 |
| US-6 | T624, T625, T626, T631 (Konsist fitness function) |

**Minor**: US-5 migration tasks (T671, T672) lack explicit `[US-5]` tag in their bracket prefix.
They trace correctly through FR-018. **Not a gap** — convention drift only.

## Section 3 — SC → verifying tasks

**SC coverage**: 13 / 13.

Verified tasks.md §«Trace summary» SC table lines 231-244 — every SC has at least one
verifying task. All T-IDs referenced (T620-T627, T631, T636, T643, T649-T651, T672, T673,
T681-T686) exist in body and survive the T660-T675 → T671-T686 renumber.

## Section 4 — Contracts → tests

| Contract | Roundtrip | Backward-compat | Provider-agnostic | Forward-compat |
|---|---|---|---|---|
| `recovery-key-backup-v1.md` | T622 ✓ | T623 ✓ | T624 ✓ | T625 ✓ |
| `worker-api-v1.md` | T659 (POST→GET→DELETE) ✓ | T657 auth, T658 idempotency, T660 rate-limit; T669 Android integration; T685 real E2E | n/a (HTTP not wire-blob) | n/a (versioning via parallel deploy) |

All 6 worker-side test files required by `worker-api-v1.md` §10 are covered by T657-T660
(4 of 6 explicit). **Minor gap**: contract §10 lists 6 test files (`auth-jwt.test.ts`,
`stable-id-check.test.ts`, `idempotency.test.ts`, `rate-limit.test.ts`, `r2-roundtrip.test.ts`,
`no-logging.test.ts`) but tasks.md only enumerates **4** test tasks (T657-T660). `stable-id-check`
and `no-logging` are arguably folded into T657 (auth) and a general implementation
convention, but the count mismatch deserves a punch-list note.

## Section 5 — Port → fake coverage

| Port | Fake |
|---|---|
| `KeyRegistry` | `FakeKeyRegistry` (T616) ✓ |
| `RootKeyManager` | `FakeRootKeyManager` (T617) ✓ |
| `RecoveryKeyBackup` | `FakeRecoveryKeyBackup` (T618) ✓ |
| `AuthAvailability` | `FakeAuthAvailability` (T619) ✓ |

All 4 ports have fakes.

## Section 6 — Trace table internal consistency

All T-IDs in tasks.md §«Trace summary» tables (FR-table and SC-table) reference T-IDs
that exist in the body of tasks.md after renumbering. Spot-verified:

- FR-018 → T630, T637, T671, T672 → all present in body ✓
- FR-020 → T673, T686 → both present ✓
- SC-001 → T626, T669, T681 → all present ✓
- SC-006 → T673, T686 → both present ✓

**No broken T-IDs.** Renumber T660-T675 → T671-T686 was applied consistently in tables.

## Section 7 — Worker server-side tasks (T653-T665) trace

T653-T665 cover **two Workers** (Track A = `workers/backup/`, Track B = `workers/identity/`):

- T653-T661: `workers/backup/` scaffold + 3 endpoints + ratelimit + idempotency + 4 vitest files + README.
- T662-T665: `workers/identity/` scaffold + `/init-claim` endpoint + 1 test + README.
- T666-T670: deployment + Android wiring (`InitClaimClient`, `BuildConfig.IDENTITY_INIT_CLAIM_WORKER_URL`) + integration tests.

**Trace summary table coverage** of T653-T665:

- Worker server-side block at line 226 ✓ (single-row catch-all «T653-T665»).
- Worker deployment row at line 227 → T666 ✓.
- Worker Android wiring row at line 228 → T667, T668 ✓.

**README tasks (T661, T665)** ARE present and tagged in Required-task gates §«Docs impacted»
lines 269-270 ✓.

## Section 8 — Special checks after recent rework

### 8.1 Old TASK-X / TASK-Y references — partial residue

After Phase 4 became in-scope, TASK-X / TASK-Y references should be fully removed.
Grep finds **9 residual mentions** across artifacts:

- **spec.md:718** (Q-N row) — «Создание backlog item TASK-X» — describes the SUPERSEDED
  plan. Q-M row (line 717) also still mentions «workers/identity/ или Cloud Function».
  → **stale** vs final decision «in-scope of TASK-6». Owner-facing — should reflect final.
- **spec.md:759** — «Worker artifact dependency: эта спека предполагает … TASK-X».
  → **stale**.
- **plan.md:11** — Summary line mentions «Новый TS Cloudflare Worker workers/backup/ —
  отдельный artifact (TASK-X, см. open items)».
  → **stale** vs §«Plan-level open items 2026-06-28» (line 341) which IS up-to-date
  («in-scope of TASK-6»). Internal contradiction within plan.md.
- **plan.md:181** — Project Structure comment «# NEW Cloudflare Worker — separate artifact (TASK-X)»
  → **stale**.
- **plan.md:271** — Risk R-3 «backlog item TASK-X implement workers/backup/»
  → **stale**.
- **plan.md:315** — Rollout «Phase 4 (workers/backup/ Worker artifact): parallel track (TASK-X)»
  → **stale**.
- **plan.md:352-353** — Architectural rule «workers/backup/ (TASK-X, this F-5)» /
  «workers/identity/ (TASK-Y, this F-5)»
  → **stale** — the «this F-5» tail acknowledges in-scope, but the TASK-X/Y labels contradict it.
- **tasks.md:315** — TL;DR «отдельный трек … новая backlog-задача TASK-X, которую владелец
  должен создать (см. T653)».
  → **stale** — contradicts §«Phase 4 (in-scope)» heading in same file.
- **tasks.md:321** — «Главные блокеры: 1. TASK-X (workers/backup/) — нужно явное "да" владельца».
  → **stale**.

**Correct mention**: tasks.md:287 «TS Worker effort lives **inside** TASK-6, not split into
TASK-X / TASK-Y» — this is the rewrite-disclaimer line; legitimate.

### 8.2 Worker README mentions in docs trace

Both Worker READMEs (T661 `workers/backup/README.md` + T665 `workers/identity/README.md`)
are enumerated in tasks.md «Required-task gates» §«Docs impacted» lines 269-270 ✓.

### 8.3 T660-T675 → T671-T686 renumber consistency

Spot-checked all SC-table and FR-table entries for the renumbered ID range (T671-T686):

- FR-018 → T671, T672 ✓ (renumbered from old T660, T661)
- FR-020 → T673, T686 ✓
- FR-021 → T674 ✓
- SC-002 → T651, T682 ✓
- SC-006 → T673, T686 ✓
- SC-010 → T643, T684 ✓

No table row points to a non-existent T-ID. Renumber **internally consistent**.

## Section 9 — Plan-vs-Spec grounding (smuggled architecture check)

Plan.md introduces:
- `workers/backup/` Cloudflare Worker → grounded in spec.md FR-010 ✓
- `workers/identity/` Worker → grounded in spec.md Q-M clarification + Notes §759 ✓
- `AuthAvailability` port → grounded in spec.md FR-005, FR-013, US-4 ✓
- `KeyRegistry`, `RootKeyManager`, `RecoveryKeyBackup` ports → grounded in FR-002, FR-003, FR-004 ✓

No smuggled architecture detected.

## Section 10 — Deleted-file references

Plan.md §Notes mentions legacy `origin/020-f5-root-key-hierarchy-recovery` branch files
(spec.md lines 749-756) — **historical reference**, not a «DELETE» directive. No dangling
file references in current artifacts.

---

## Punch list

1. **(MINOR, high cosmetic impact)** **9 residual TASK-X / TASK-Y mentions** contradict the
   Phase-4-in-scope decision documented in tasks.md heading and tasks.md:287, plan.md:341.
   Locations: spec.md:717-718, 759; plan.md:11, 181, 271, 315, 352-353; tasks.md:315, 321.
   **Recommended fix**: rewrite to «in-scope of TASK-6 Phase 4 (workers/backup/ + workers/identity/)»
   on next touch. Not blocking — internal inconsistency only, no code is gated on this.

2. **(MINOR)** Contract `worker-api-v1.md` §10 lists **6 required Worker-side test files**
   (`auth-jwt`, `stable-id-check`, `idempotency`, `rate-limit`, `r2-roundtrip`, `no-logging`).
   Tasks.md enumerates **4** test tasks (T657-T660): auth, idempotency, roundtrip, rate-limit.
   Missing explicit tasks: `stable-id-check.test.ts`, `no-logging.test.ts`.
   **Recommended fix**: either add T660a/T660b for the two missing tests, or fold them into
   T657 (stable-id) and T654 (no-logging lint as part of implementation) explicitly.

3. **(NIT)** US-5 migration tasks (T671, T672) lack explicit `[US-5]` bracket tag.
   Trace via FR-018 works, but convention drift. **Recommended fix**: add `[US-5]` token
   on next touch.

---

## Counts

- **FRs covered**: 23 / 23 ✓
- **US with test evidence**: 6 / 6 ✓
- **SC with verifying tasks**: 13 / 13 ✓
- **Contracts with required tests**: 2 / 2 ✓ (worker-api §10 has 4-of-6 explicit; 2 implicit)
- **Ports with fakes**: 4 / 4 ✓
- **Trace tables internally consistent**: ✓ (no broken T-IDs after renumber)
- **Gaps**: 0 blocking, 3 minor punch-list items (TASK-X residue, 2 worker tests not enumerated, US-5 tag drift)

## Verdict

**PASS** with 3 minor punch-list items. None are blocking implementation; all are
documentation-hygiene drift that should be cleaned up on next artifact touch
(particularly the TASK-X residue, which actively contradicts the final design).
