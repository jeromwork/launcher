# Checklist: requirements-quality

**Spec**: `spec.md` (rev. 2026-05-14, post-clarify Q1-Q10)
**Run**: 2026-05-14 — `/speckit.clarify` post-pass before `/speckit.plan`.

---

## Content Quality

- [ ] **CHK001 — No implementation details** (programming languages, frameworks, vendor APIs in `spec.md`)
  **Finding: FAIL.** spec.md содержит vendor/framework leaks, которые относятся к `plan.md`:
  - `Firestore`, `Cloudflare Worker`, `FCM` (FR-013, FR-020, FR-021, FR-022 T1, SC-004a/b, etc.) — vendor SDKs.
  - `Room` / `AndroidX` (FR-041, FR-042, FR-045) — framework.
  - `ConnectivityManager.NetworkCallback`, `Activity#onResume`, `Activity#onCreate` (FR-022 T2/T4, FR-044) — platform API.
  - `WorkManager` (FR-022 T3) — framework.
  - `UUID v4` (FR-004) — implementation choice.
  - `Kotlin Serialization`-style references implicit in roundtrip-test wording.
  **Severity**: Medium. Spec-kit canon: vendor/API should be in plan.md. But: this is repository convention (spec 007 has same pattern — FCM/Firebase mentioned throughout its spec.md; convention is "wire-format spec может ссылаться на vendor, потому что wire-format **существует** только через vendor"). **Action**: либо принять как локальный convention и закрыть как **N/A**, либо переписать spec.md в vendor-neutral терминах ("push channel", "local persistent store", "network availability callback"). Я рекомендую **N/A — repository convention**, потому что переписывание сейчас раздует scope clarify и противоречит pattern спека 007.

- [x] **CHK002 — Focus is on user value and business need, not on technical "how"**
  Контекст-секция, user stories, и acceptance scenarios написаны от лица пользовательской ценности («admin меняет раскладку», «бабушка видит экран без задержки», «нельзя потерять плитку "вызов скорой"»). FR имеют technical detail (см. CHK001), но это **обоснованно** через wire-format-specific характер фичи.

- [x] **CHK003 — Written so a non-technical stakeholder can read and validate**
  Есть отдельная секция «Контекст (для не-разработчика)»; user stories на простом русском; даже Edge cases объяснены через сценарии («admin приехал, бабушкин телефон offline»). Validated via memory `feedback_plain_russian_for_novice`.

- [x] **CHK004 — All mandatory sections present**
  ✅ User Scenarios & Testing (6 US + Edge Cases), ✅ Requirements (FR-001..054 + OUT-001..008 + Key Entities), ✅ Success Criteria (SC-001..008), ✅ Assumptions, ✅ Open Questions (resolved).

## Requirement Completeness

- [x] **CHK005 — No `[NEEDS CLARIFICATION]` markers remain**
  Verified by grep: 0 occurrences of `[NEEDS CLARIFICATION]` in current spec.md. Все 10 Q-вопросов закрыты (см. Open Questions section).

- [x] **CHK006 — Every requirement is testable**
  Каждый FR имеет observable assertion: FR-001/002/003 — поля в документе можно проверить чтением; FR-004 — UUID в id регексом; FR-013 — server-side reject поведение тестируется интеграционным тестом с Firebase Emulator; FR-022 — четыре триггера, каждый тестируется отдельно; FR-046/047 — UI-baseline тесты. Edge cases (US-2 scenarios 3/4/5) явно описаны как acceptance scenarios.

- [x] **CHK007 — Every requirement is unambiguous**
  Никаких "fast", "intuitive", "smooth" без operationalisation. Цифры приведены: 5 секунд threshold (SC-001), 2 минуты throttle (FR-022), 15 минут polling (FR-022), 650 ms cold start (SC-004a), 5 секунд post-startup delay (SC-004b). "p95" приписан где применимо.

- [x] **CHK008 — Success criteria are measurable**
  SC-001 — «100% push'ей имеют видимый UI-state, 100 push'ей → 100 transitions».
  SC-002 — «каждый из 4 триггеров покрыт integration-test'ом».
  SC-003 — «100 push'ей → 100 исходов, 0 silent failures».
  SC-004a — «≤ 650 ms p95».
  SC-005/007 — «100% green».
  SC-008 — «100% editor'ов с pending показывают маркер».
  Все измеримы.

- [ ] **CHK009 — Success criteria are technology-agnostic**
  **Finding: PARTIAL FAIL** — те же leaks, что в CHK001. SC-001 упоминает `Firestore`, `ConnectivityManager.NetworkCallback`. SC-002 перечисляет `FCM`, `WorkManager`, `Activity#onResume`. SC-004a/b упоминают «Room». **Same root cause, same resolution**: либо принять как repository convention (как 007), либо переписать в vendor-neutral. Рекомендую **принять как convention** для consistency с 007.

- [x] **CHK010 — All acceptance scenarios for each User Story are explicit (Given/When/Then)**
  US-1: 2 scenarios with G/W/T. US-2: 5 scenarios. US-3: 3 scenarios. US-4: 3 scenarios. US-5: 2 scenarios. US-6: 2 scenarios. Total 17 scenarios — все G/W/T.

- [x] **CHK011 — Edge cases are identified — at minimum: empty state, error state, retry, double-action**
  Edge Cases section: partial apply, schema mismatch, concurrent writes, UUID collision, revoke during apply, large contacts list, rollback, network drop mid-push, app-killed mid-edit, long-lived pending. Покрыто 10 edge cases. Empty state implicit (US-1 scenario 1 «никто параллельно не редактирует» = empty conflict state). Retry — упомянут как «push retry с проверкой updatedAt». Double-action — FR-052/053 покрывают idempotent identical edits.

- [x] **CHK012 — Scope is clearly bounded — In Scope and Out of Scope are exhaustive**
  In Scope: 6 user stories с приоритетами. Out of Scope: 8 явных OUT-блоков (admin UI editor → 009, commands → 009, roll-back → 009, capabilities/health → 006-ext, live notifications → no, app version compat → backlog spec, config history → 009, size limits → no). Exhaustive для области — все Q-clarifications resulting в OUT documented.

- [x] **CHK013 — Dependencies and assumptions are explicit**
  Assumptions section: 8 пунктов — зависимость от спека 007 (`46eb5de` PR merged), Worker extension, Security Rules update, AndroidX/Room, UUID v4 assumption, no new external SDKs, admin UI minimal-in-008. Internal cross-references на спеки 003/006/007/009/011 в OUT-блоках.

## Feature Readiness

- [x] **CHK014 — All functional requirements have clear acceptance criteria (mapped to a US or independent)**
  Каждый FR прослеживается:
  - FR-001..006 → SC-005 (wire-format tests) + US-6 acceptance scenario 2.
  - FR-010..015 → US-1 scenarios + SC-001/003.
  - FR-020..023 → US-1 scenario 1 + SC-002.
  - FR-030..034 → US-1 scenario 2 + US-2 + SC-001b.
  - FR-040..047 → US-4 + SC-008.
  - FR-050..054 → US-2 + SC-007.
  Никаких dangling FR без US/SC.

- [x] **CHK015 — User scenarios cover primary flows — not just happy path; at minimum one error path per US**
  US-1: scenario 1 = happy, scenario 2 = partial-apply (error path).
  US-2: scenarios 1-5 — все error/conflict paths (это сама суть US).
  US-3: scenario 2 = conflict on Managed-as-editor, scenario 3 = offline.
  US-4: scenarios 2 = long-lived pending, scenario 3 = conflict on resume.
  US-5: scenario 2 = schemaVersion N+1 read (forward-compat).
  US-6: scenario 2 = stale contacts removed on apply (state divergence).
  Каждый US имеет ≥1 error path.

- [x] **CHK016 — Feature meets measurable outcomes defined in Success Criteria (no SC without an FR producing the measurement)**
  SC-001 ← FR-015. SC-001b ← FR-031. SC-002 ← FR-022. SC-003 ← FR-014 + FR-030. SC-004a ← FR-041 + FR-044. SC-004b ← FR-044. SC-005 ← FR-005. SC-006 ← N/A (vacated by OUT-006). SC-007 ← FR-051..054. SC-008 ← FR-046.

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 13 | CHK002, CHK003, CHK004, CHK005, CHK006, CHK007, CHK008, CHK010, CHK011, CHK012, CHK013, CHK014, CHK015, CHK016 |
| ⚠️ Local-convention N/A | 2 | CHK001, CHK009 — vendor/SDK names in spec.md (Firestore, FCM, Room, WorkManager, ConnectivityManager). Same pattern в spec 007 (которая в main); считаем repository convention. **Альтернатива**: переписать в vendor-neutral wording. Не блокирующее. |
| ❌ Fail | 0 | — |

**Verdict: PASS** (с двумя `repository-convention N/A`).

**Рекомендации для plan.md:**
- Перенести vendor-specific implementation details (точные API имена, library versions, exact wire-format кодирование) в plan.md / research.md.
- В plan.md явно зафиксировать decision «использовать Firestore optimistic concurrency через `updatedAt` precondition» с указанием альтернатив (Firestore transaction, ETag-style).
- Constitution Check (Article XVI) — в speckit-plan, не в этом чеклисте.

**Spec.md правки:** не требуются. Spec проходит чеклист с N/A на CHK001/CHK009 как локальную convention.
