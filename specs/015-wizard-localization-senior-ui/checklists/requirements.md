# Checklist: requirements-quality

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16 (post mentor-session, post Part K additions)
**Verdict**: 12 ✓ / 4 ⚠ / 0 ✗

---

## Content Quality

- [⚠] **CHK001** No implementation details (programming languages, frameworks, vendor APIs) appear in `spec.md`.
  - **Finding**: спека содержит конкретные технологии — Kotlin Multiplatform, Compose, DataStore, moko-resources, Konsist, Claude API, `Settings.ACTION_*`, AccessibilityService, RoleManager, `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
  - **Rationale acceptance**: F-3 — foundation spec для технической инфраструктуры. Решения library/framework (moko-resources, Konsist) — это **архитектурные** решения, явно зафиксированные в Clarifications C-8, C-15. По строгому канону spec-kit они должны быть в plan.md, но F-3 — особый случай: его scope **есть** module structure + tool choices, не feature behavior.
  - **Recommendation**: оставить как есть с пометкой «foundation spec — implementation choices are part of scope». При желании можно вынести конкретные library names в plan.md, оставив в spec.md только «string-table library» / «lint tool».

- [⚠] **CHK002** Focus on user value and business need, not on technical "how".
  - **Finding**: 7 User Stories есть и user-facing, но 67 FRs + Key Entities — глубоко технические.
  - **Rationale**: foundation spec по своей природе балансирует. User-value framed как «enables future apps to ship faster / consistently» (С-2 ecosystem reuse), а не «end user sees X». TL;DR + User Stories обеспечивают user-value reading; FRs обеспечивают actionable contracts.
  - **Recommendation**: acceptable trade-off для F-3.

- [⚠] **CHK003** Written so a non-technical stakeholder can read and validate.
  - **Finding**: TL;DR и User Stories — да, non-technical reader может validate. Часть FR (особенно Part K с `SystemSettingPort` API) — требует технического чтения.
  - **Recommendation**: TL;DR покрывает stakeholder use case. Если нужен ещё более простой документ — это отдельный artifact (executive summary), не задача F-3 spec.

- [✓] **CHK004** All mandatory sections present.
  - User Scenarios & Testing ✓, Scope (In = FRs, Out = OUT-list) ✓, Functional Requirements ✓, Success Criteria ✓, Assumptions ✓, Local Test Path ✓, AI Affordance ✓, OEM Matrix ✓, TL;DR ✓.

---

## Requirement Completeness

- [✓] **CHK005** No `[NEEDS CLARIFICATION]` markers remain.
  - Найдено 0 instances в spec.md. Clarifications секция содержит зафиксированные resolutions (C-1..C-23), не open questions.

- [✓] **CHK006** Every requirement is testable.
  - Все 67 FRs формулированы как assertion'ы (presence / behavior / output). Структурные FR (типа FR-001 «MUST be KMP module») testable через build inspection. Behavioural FR (типа FR-029 fallback chain) testable через unit-test.

- [✓] **CHK007** Every requirement is unambiguous.
  - Точные термины (BCP-47 tag, schemaVersion, fontScale). Нет «fast / simple / intuitive» без operationalisation. Минорные вольности в TL;DR («простая и правильная архитектура») — acceptable для TL;DR.

- [✓] **CHK008** Success criteria measurable.
  - SC-001 (< 500ms), SC-002 (100% pass), SC-003 (fails build), SC-005 (100% эмулятор-тестов), SC-010 (≤ +1.5 MB), SC-011 (≤ 1.5 сек), SC-012/013/014 (verified). Все 15 SC имеют explicit metric или explicit assertion.

- [⚠] **CHK009** Success criteria technology-agnostic.
  - **Finding**: SC упоминают «Pixel 5 API 34», «Konsist», «JVM unit-test», «instrumented test», «Compose preview screenshot test». Это technology-specific.
  - **Rationale acceptance**: device baseline (Pixel 5 API 34) — это **test matrix**, не feature constraint; lint tool (Konsist) — выбран в C-15 как architecture mandate. Допустимо для foundation spec.
  - **Recommendation**: при желании заменить «Konsist» на «automated import lint rule», «Pixel 5 API 34» на «medium-tier emulator baseline». Не блокирует.

- [✓] **CHK010** All acceptance scenarios explicit (Given/When/Then).
  - Все 7 US имеют от 3 до 4 Given/When/Then scenarios. US-1 (4), US-2 (3), US-3 (4), US-4 (4), US-5 (3), US-6 (3), US-7 (3).

- [✓] **CHK011** Edge cases identified.
  - Edge Cases секция — 8 explicit edge cases: пустой manifest, unknown stepType, missing iconKey, locale change mid-wizard, JSON parsing exception, extreme fontScale, process death на первом шаге, unsupported locale.

- [✓] **CHK012** Scope clearly bounded.
  - 67 FRs (In) + 23 OUT items (Out). Out-items покрывают все ожидаемые «а это входит?» вопросы (iOS source sets, cloud sync, alternative translation providers, конкретные manifests, и т.д.).

- [✓] **CHK013** Dependencies and assumptions explicit.
  - 17 A-items (A-1..A-15b + A-16, A-17). 4 spec dependencies (008, 005, 007, 010) explicit в Cross-spec impact + Assumptions. External tooling (moko-resources, Konsist, Claude API) declared.

---

## Feature Readiness

- [⚠] **CHK014** All FRs have clear acceptance criteria mapped to US or independent.
  - **Finding**: implicit mapping есть (FR-001..006 → US-1/US-2, FR-007..010 → US-1, FR-052..060 (Part K) → US-1 + US-2 implicit), но **explicit traceability matrix** не в spec.md.
  - **Recommendation**: matrix FR→US — задача `procedure-cross-artifact-trace`, выполняется в `speckit-analyze` (последний gate). Не блокирует сейчас.

- [✓] **CHK015** User scenarios cover primary flows + at least one error path per US.
  - US-1 (happy path) + US-2 (process death recovery) — error path для wizard execution.
  - US-3 (happy locale) + Edge Case «missing key» — error path для localization.
  - US-4 (happy senior UI) + SC-006 (max fontScale без обрезки) — error path для UI scaling.
  - US-5 (lint guard fails build) — error path для discipline.
  - US-6 (forward-compat happy + hard-fail на breaking) — explicit error path.
  - US-7 (hint shown + dismissed) + Acceptance #2 (re-show suppressed) — error path для repeat dismissal.

- [✓] **CHK016** Feature meets measurable outcomes (no SC without FR producing measurement).
  - SC-001 ↔ FR-002..006 (WizardEngine). SC-002 ↔ FR-017 (roundtrip tests). SC-003 ↔ FR-031 (CI fitness function). SC-004 ↔ FR-038 (lint rule). SC-005 ↔ FR-003..004 (checkpoint resume). SC-006 ↔ FR-034..036 (senior UI primitives). SC-007 ↔ FR-032 (RTL helper). SC-008 ↔ FR-015 (forward-compat). SC-009 ↔ FR-016 (hard-fail). SC-010 ↔ implicit budget. SC-011 ↔ implicit cold-start budget. SC-012 ↔ FR-052..057 (system settings registry). SC-013 ↔ FR-058 (self-attest). SC-014 ↔ FR-060 (cross-app independence).

---

## Open items / actions

1. **CHK001 + CHK009 (technology specificity)** — два пути: (a) принять как design constraint foundation-спеки (мой default); (b) вынести library/tool choices (moko-resources, Konsist) в plan.md, оставить в spec только generic «string-table library» / «lint tool». Решение — твой call.
2. **CHK014 traceability matrix** — выполнится в `speckit-analyze`. Не блокирует переход в plan.md.

## Резюме

**12 ✓ / 4 ⚠ / 0 ✗** — спека готова к плану. Warning'и не блокируют: они отражают inherent trade-off foundation-спеки. Хочешь — можем вынести technology choices в plan.md перед началом `speckit-plan`. Не хочешь — переходим как есть.
