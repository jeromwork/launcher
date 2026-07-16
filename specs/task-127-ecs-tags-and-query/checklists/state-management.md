# Checklist: state-management

Applied: 2026-07-15
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

Note: This spec is a data-layer refactor (Profile → FlowRepository adapter swap + Tag ECS). No new Activity/screen. State touched: `HomeComponent.loadingState` (existing per TASK-52), `ProfileStore` observable (existing).

## Lifecycle events

- [x] CHK001 Rotation: `ProfileBackedFlowRepository.observeFlows()` is a Flow — survives Activity recreation via ViewModel scope (existing pattern). FR-006 explicit: after first non-null Profile, emits on every change.
- [x] CHK002 Process death: `ProfileStore` reloads from persisted Profile v3 file. Cold start → null → filterNotNull → Loading until loaded. Explicit in FR-006.
- [x] CHK003 Low-memory kill: same as process death (ProfileStore rehydrates).
- [x] CHK004 Reboot: Profile persisted, rehydrates from disk on next launch — inherited from TASK-120.

## State scope

- [x] CHK005 State scopes are explicit — Profile persistent (ProfileStore), HomeLoadingState screen-scoped (HomeComponent per TASK-52), Flows via repository observable.
- [x] CHK006 No new process-singleton state introduced.
- [x] CHK007 N/A — no rememberSaveable-sized objects introduced.

## Recreation correctness

- [x] CHK008 US1 acceptance #3 verifies emit-on-change without Activity restart — no first-only logic.
- [x] CHK009 Not applicable — no form input in scope.
- [x] CHK010 Flow-based observation cancels+restarts predictably on Activity rebind (existing ViewModel pattern).

## Configuration changes

- [x] CHK011 Locale change: FR-008 wizard strings via `strings_wizard.xml` — standard Android string resolution, survives locale change.
- [x] CHK012 Density / font-scale: no new layout introduced in this spec.
- [x] CHK013 Window size: no new UI.

## Tests

- [x] CHK014 SC-005 mandates HomeComponentLoadingStateTest extension — covers recreation-equivalent Flow re-subscribe scenario.
- [ ] CHK015 PARTIAL — no explicit process-death simulation test mandated for Profile rehydration. Existing TASK-120 tests likely cover, but not called out here. Recommend cross-reference.

## Edge cases

- [x] CHK016 Multi-window: N/A — HomeActivity is launcher, not multi-window candidate.
- [x] CHK017 Notification entry while killed: N/A — spec doesn't touch notifications.

**Result**: 16/17 passed, 1 recommend (CHK015 — cross-reference to existing TASK-120 Profile rehydration tests would strengthen coverage story).

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Проверили что state переживёт rotation / process death / low-memory kill / reboot. 16/17 pass. Ключевой pass — `ProfileBackedFlowRepository.observeFlows()` это Flow, переживает Activity recreation через ViewModel scope; `ProfileStore` rehydrates с диска на cold start; `filterNotNull` держит HomeComponent в Loading пока Profile не появится.

**Конкретика, которую стоит запомнить:**
- FR-006 explicit: после первого non-null Profile — эмитит на каждом изменении (rotation-safe).
- Process death: ProfileStore reloads из persisted Profile v3 файла (наследуется от TASK-120).
- CHK015 recommend — cross-reference TASK-120 rehydration тесты, а не переписывать их для TASK-127.
- Multi-window / notification-entry — N/A (HomeActivity это launcher).

**На что смотреть с осторожностью:**
- Rehydration coverage прячется в TASK-120 test suite — если те тесты сломаются, TASK-127 не заметит без явного cross-ref.
