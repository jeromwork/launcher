# Checklist: requirements-quality

Applied: 2026-07-15
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

## Content Quality

- [x] CHK001 No implementation details in spec.md — Kotlin identifiers appear only inside FR text describing artefacts (allowed for foundational refactor spec); no framework internals leak.
- [x] CHK002 Focus on user value in US1/US3, developer value in US2 (declared as foundational).
- [x] CHK003 Readable by mentor-reviewing owner — plain-Russian Why-this-priority + Given/When/Then.
- [x] CHK004 Mandatory sections present: User Stories, In/Out of Scope, FR, SC.

## Requirement Completeness

- [x] CHK005 No `[NEEDS CLARIFICATION]` markers.
- [x] CHK006 Every FR is testable — each maps to at least one SC or independent unit test.
- [x] CHK007 Unambiguous — "under 1ms", "9 tags initial set", exact string keys enumerated.
- [x] CHK008 SC measurable — SC-008 gives 1ms budget, SC-003/004/005 name concrete tests.
- [ ] CHK009 SC technology-agnostic — FAILS: SC references Kotlin identifiers (`Component.tags`, `ProfileBackedFlowRepository`, `HomeComponentLoadingStateTest`). Justified for foundational architecture spec but strict CHK009 fails.
- [x] CHK010 Given/When/Then explicit in all three US.
- [x] CHK011 Edge cases: 0-tile Profile, migration without tags, empty tags, Toolbar exclusion, null ProfileStore.
- [x] CHK012 In/Out of Scope exhaustive — 5 explicit out-of-scope items.
- [x] CHK013 Dependencies explicit — TASK-126, TASK-120, TASK-52 named.

## Feature Readiness

- [x] CHK014 Every FR mapped to SC or US.
- [x] CHK015 US1 covers happy + Profile-with-defaults; edge cases block covers error paths.
- [x] CHK016 Every SC has FR producing measurement.

**Result**: 15/16 passed, 1 known deviation (CHK009 — SC uses code identifiers, acceptable for foundational refactor).

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Стандартная spec-kit проверка «content-complete, technology-agnostic, testable, unambiguous». 15/16 pass. Единственное отклонение — CHK009 «SC technology-agnostic», где SC ссылаются на Kotlin identifiers (`Component.tags`, `ProfileBackedFlowRepository`, `HomeComponentLoadingStateTest`) — accepted для foundational refactor (нельзя описать «ECS-native расширение» без имён классов).

**Конкретика, которую стоит запомнить:**
- Все FR testable, каждый маппится на минимум один SC или unit test.
- 3 US с явным Given/When/Then + Independent Test.
- 5 явных Out-of-Scope пунктов (после fix'а даже больше — corrupt Profile recovery добавлен).
- Dependencies TASK-126 / TASK-120 / TASK-52 явно named.
- Никаких `[NEEDS CLARIFICATION]` markers.

**На что смотреть с осторожностью:**
- CHK009 accepted deviation — если future foundational specs используют тот же паттерн, окей; для user-visible specs нужно строже.
