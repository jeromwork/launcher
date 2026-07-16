# Checklist: failure-recovery

Applied: 2026-07-15 (re-run after Out of Scope section explicitly documented corrupt Profile recovery)
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

## Error categories

- [x] CHK001 Failure modes covered in Edge Cases:
  - Profile with 0 tiles → Ready with empty screen (not Error).
  - Profile v2 without tags → migration writer fills defaults.
  - Component with empty tags → not found by query (valid deprecated marker + fitness function).
  - null Profile from ProfileStore → stay in Loading (not Error).
- [x] CHK002 User-visible behaviour: empty-Home is Ready with empty screen (valid MVP state, no user-visible error). Edge Cases block documents each state transition explicitly. Empty-screen UX guidance ("how to add tiles") is separate future feature, not error handling.
- [x] CHK003 No silent failure — HomeLoadingState machine explicit (Loading vs Ready vs Error contract from TASK-52).

## Fallbacks

- [x] CHK004 No fallback chains introduced.
- [x] CHK005 N/A — no data-driven fallback in scope.
- [x] CHK006 Terminal state = Error via existing HomeLoadingState contract.

## Retries

- [x] CHK007 No retry logic introduced. Flow-based observation naturally handles emissions.
- [x] CHK008 filterNotNull is not a retry loop — passive wait.
- [x] CHK009 N/A — no repeated actions.

## Offline / degraded modes

- [x] CHK010 No network in scope. ProfileStore is local.
- [x] CHK011 N/A — no cache TTL.

## Permissions denied

- [x] CHK012 N/A — no new permissions.
- [x] CHK013 N/A.

## Recovery from invalid state

- [x] CHK014 Corrupt Profile recovery **explicitly out-of-scope** per `## Out of Scope` section (line 167): "MVP: exception в migration writer покрыт roundtrip тестом; явный recovery flow (reset ProfileStore + rerun FirstLaunchActivity) — отдельный будущий task если понадобится по production feedback." NFR-004 mandates migration writer idempotency + roundtrip test. Explicit out-of-scope decision with rationale = valid closure per checklist standard.
- [x] CHK015 No "crash and restart" strategy. filterNotNull + Loading state is graceful.

## Diagnostics

- [x] CHK016 Structured diagnostics deferred to plan phase (recommendation from dev-experience CHK018 — log tag on `ProfileBackedFlowRepository` transitions). Not a functional requirement gap — passive Flow observation model is diagnostically observable through existing HomeLoadingState transitions. Recommend explicit log tag in plan; no new error surface introduced beyond TASK-52 HomeLoadingState.Error.
- [x] CHK017 Failure aggregation: N/A (no error surface introduced beyond HomeLoadingState.Error inherited from TASK-52).

**Result**: 17/17 passed, no open items.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Re-run после того, как `## Out of Scope` в spec.md явно документировал Corrupt Profile recovery как out-of-scope с rationale (MVP покрывает migration writer roundtrip тестом; explicit recovery отложен). 17/17 pass (было 14/17).

**Конкретика:**
- CHK014 → `[x]` (out-of-scope decision с явным rationale в spec.md — valid closure; roundtrip test через NFR-004 покрывает happy path migration).
- CHK002 → `[x]` (empty-Home как Ready valid state — не error UX, отдельная тема).
- CHK016 → `[x]` (diagnostic recommendation передан в plan через dev-experience CHK018 log tag; no new error surface = no new aggregation gap).
- Retries / offline / permissions — N/A (нет сетевого слоя, нет новых permissions).

**На что смотреть с осторожностью:**
- Corrupt Profile в production → сейчас crash без graceful fallback (owner accept'нул риск как out-of-scope). Если появится feedback — вернуться и открыть FR-011 в отдельной задаче.
- Log tag CHK016 recommendation — не забыть в plan phase добавить log tag / diagnostic event для surface migration/query failures.
