# Specification Quality Checklist: Setup Assistant and Launcher Bootstrap

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-05-17
**Updated**: 2026-05-19 (post `/speckit.clarify` — 7 resolutions woven into spec.md §Clarifications)
**Feature**: [spec.md](../spec.md)

## Content Quality

- [X] **CHK001** — No implementation details (languages, frameworks, vendor APIs) appear in `spec.md`. **Acknowledged exceptions**: Android-specific terms `RoleManager`, `AccessibilityManager`, `ACTION_CALL`/`ACTION_DIAL`, `HapticFeedbackConstants`, `GoogleApiAvailability`, `Lifecycle.RESUMED`, `finishAffinity()`, `core/commonMain/api/` paths appear in FRs. Спек 10 — **explicitly platform-bound (Android-only)** (см. A-12 + OUT-006 «no iOS»), и эти термины сохраняются ради traceability к permissions-and-resource-budget.md, ADR-005 platform asymmetry rules, и к будущему plan.md. Это **намеренное исключение**, не bleed.
- [X] **CHK002** — Focused on user value and business need. Каждая US начинается с user-story (что делает бабушка/admin) + явный «Why this priority» с обоснованием через core use-case продукта.
- [X] **CHK003** — Written for non-technical stakeholders. Раздел «Краткое содержание простым русским языком» (внизу spec.md, ~20 строк) описывает 8 главных и не-главных пунктов на повседневном русском без жаргона; обновлён в clarify 2026-05-19 (добавлен §Hard-block на GMS-less устройствах, §обучение → спек 014).
- [X] **CHK004** — All mandatory sections completed:
  - User Scenarios & Testing — US-1..US-7 (US-8 удалена в clarify, см. OUT-013)
  - Requirements (Functional + Cross-cutting + Key Entities) — FR-001..FR-044 (с gap'ами для удалённых FR-034..FR-038)
  - Success Criteria — SC-001..SC-009
  - Assumptions — A-1..A-12
  - Out of Scope — OUT-001..OUT-013
  - Cross-cutting concerns from mentor session — 8 items
  - Clarifications — table с 7 resolutions от 2026-05-19

## Requirement Completeness

- [X] **CHK005** — No `[NEEDS CLARIFICATION]` markers remain. Грей-зоны mentor session 2026-05-17 (6 пунктов) и clarify session 2026-05-19 (7 пунктов) — все resolved.
- [X] **CHK006** — Requirements are testable. Каждый FR имеет конкретный observable trigger (UI событие, system state, permission status, lifecycle event). Примеры с явными измеримыми параметрами: FR-021 (±48dp дельта, ≤5sec, 7 тапов), FR-023 (≥2 challenge типа), FR-026 (≤14sp шрифт, ≥56dp кнопка), FR-042 (`GoogleApiAvailability.isGooglePlayServicesAvailable() != SUCCESS`).
- [X] **CHK007** — Requirements are unambiguous. Раньше vague формулировки («скрытая область», «опционально PIN», «через 7 дней») заменены конкретными number-bound triggers в clarify. Example: «скрытая область» → «любая non-interactive область, ±48dp дельта»; «опционально PIN» → «rotating challenge с 2 типами».
- [X] **CHK008** — Success criteria measurable. SC-001..SC-009 содержат: percentages (95%, 100%, ≤1%), time thresholds (≤10sec, ≤1sec, ≤3sec), counts (минимум 2 типа challenges), size deltas (≤500KB APK).
- [X] **CHK009** — Success criteria technology-agnostic — **с принятыми исключениями**: SC-008 упоминает «Robolectric тесты» (Android testing framework — platform-bound), SC-009 «APK size delta» (Android distribution metric). Эти exceptions documented as constitutional baseline (Article IX §3). Метрики типа «pixel 4a baseline» — описаны как **observable benchmark**, не как technical mandate.
- [X] **CHK010** — All acceptance scenarios are defined. Каждая US имеет 3-7 Given/When/Then сценариев (US-1: 4, US-2: 4, US-3: 6, US-4: 3, US-5: 4, US-6: 4, US-7: 7 — incl. TalkBack edge + cancel path).
- [X] **CHK011** — Edge cases identified. Раздел Edge Cases — 7 пунктов после clarify: challenge state corruption (handled by in-memory only), POST_NOTIFICATIONS revoked, `flows_mock` deletion impact, challenge bypass FP rate, TalkBack-challenge edge, vibration отключена системно, GMS-less first-launch. Покрывает empty state (FR-033 empty paired list), error state (FR-013 permission denied), retry (FR-024 challenge regenerate), no double-action (FR-021 single-point gesture).
- [X] **CHK012** — Scope is clearly bounded. OUT-001..OUT-013 — 13 явных out-of-scope пунктов после clarify (добавлены OUT-011 real-security, OUT-012 slide-puzzle, OUT-013 onboarding-tutorials как отдельный спек 014).
- [X] **CHK013** — Dependencies and assumptions identified. A-1..A-12 — 12 assumptions: зависимости от спеков 3/5/7/8/9 (A-1..A-4), архитектурные принципы (A-5..A-7), технические (A-8..A-10), preset visibility (A-11), GMS precondition (A-12).

## Feature Readiness

- [X] **CHK014** — All functional requirements have clear acceptance criteria. Каждый FR-NNN traceable к US-N + acceptance scenarios. Cross-cutting FRs (FR-039 localization, FR-040 wire-format, FR-041 domain isolation) ссылаются на constitution articles (ADR-004, rule 5, rule 1).
- [X] **CHK015** — User scenarios cover primary flows AND error paths. Каждая US имеет минимум один error path:
  - US-1: offline cold-start (4-й acceptance)
  - US-2: ROLE_HOME отказ + reset path (2-й/3-й/4-й acceptance)
  - US-3: permission denied → ACTION_DIAL fallback (4-й acceptance), invalid number (FR-015)
  - US-4: POST_NOTIFICATIONS denied (2-й acceptance)
  - US-5: empty paired list (3-й acceptance)
  - US-6: smooth disambiguation между `!N` и `?M` (FR-019)
  - US-7: TalkBack edge (7-й acceptance), wrong answer без lockout (5-й/6-й acceptance)
- [⚠️] **CHK016** — Feature meets measurable outcomes defined in Success Criteria. Mostly traced, **1 weak link**:
  - SC-001 ↔ US-1 + FR-006 ✓
  - SC-002 ↔ FR-002 ✓
  - SC-003 ↔ US-3 + FR-012 ✓
  - SC-004 ↔ US-6 + FR-019 ✓
  - SC-005 ↔ FR-018 + FR-019 ✓
  - SC-006 ↔ **implicit от US-7 + FR-021..FR-023, но нет FR явно про "admin reach Settings ≤ 3 sec"**. Это soft metric, может быть исключена или подкреплена. **Observation, не blocker.**
  - SC-007 ↔ FR-021 + FR-023 + FR-024 ✓
  - SC-008 ↔ FR-004 ✓
  - SC-009 ↔ Article IX (constitutional, не FR) ✓

## Notes

- Items marked incomplete require spec updates before `/speckit.plan`
- **Pre-specify mentor session 2026-05-17** разрешила 6 ключевых вопросов scope.
- **Clarify session 2026-05-19** разрешила 7 дополнительных вопросов (5 plus 2 bonus) — SetupCheck execution model, PIN → challenge replacement, badge layout, 7-tap zone, tutorial removal, Settings preset visibility, GMS hard-block. Clarifications зафиксированы в `spec.md` §Clarifications.
- **Затрагиваемые внешние артефакты** — modifications вне `specs/010-*/` явно перечислены. `/speckit.plan` должен подтвердить план их обновления.
- **Adjacent concerns** (8 пунктов) — surface'нуты из mentor + clarify sessions, должны быть проверены `procedure-assess-spec-complexity` → checklists.

## Open items (warnings, non-blockers)

1. **CHK016 SC-006 weak traceability**: «admin reach Settings ≤ 3 sec» нет в FR — implicit, можно добавить FR-021a «target SLA для admin-side 7-tap completion» или удалить SC-006 (US-8 tutorial был его primary driver, после удаления US-8 SC-006 потерял часть основания).

## Result

**15/16 ✓, 1 observation** (CHK016 — non-blocker). Spec готов к `/speckit.plan`.

---

## Краткое содержание (для не-разработчика)

Проверили базовое качество спека: все ли разделы заполнены, понятны ли требования, измеримы ли критерии успеха, есть ли граничные случаи. **15 из 16 пунктов пройдены**. Единственное наблюдение: SC-006 «admin находит Settings за ≤ 3 sec» не имеет прямого FR — слабая связь между success criterion и функциональным требованием. Не блокирует переход к plan.
