# Checklist: requirements-quality

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 14/16 ✓ — passes baseline, 2 minor open items для plan stage.

---

## Content Quality

- [x] **CHK001** No implementation details in spec.md — ✓ с оговоркой. Имена типов (`AuthProvider`, `AuthIdentity`, `SessionStore`) присутствуют, но это **port-level domain types**, не implementation. Имена Firebase / Credential Manager — только в adapter-related FR (FR-014, FR-015) и edge cases, что приемлемо для подготовительной спеки на cusp с архитектурой. **Не нарушение** для spec-kit baseline; чистое разделение spec ↔ plan будет уточнено в `plan.md`.
- [x] **CHK002** Focus на user value — ✓. User Stories 1-7 формулируются от лица пользователя/разработчика: «бабушка устанавливает app без sign-in», «consumer пишет код не зная Google». TL;DR section даёт plain-Russian версию.
- [x] **CHK003** Non-technical readable — ✓. TL;DR + Clarifications section с человеческими формулировками. Не-разработчик может validate решения 1-9 без чтения FR.
- [x] **CHK004** All mandatory sections present — ✓. Контекст, Clarifications, Сценарии (placeholder для scenarios), User Stories, Edge Cases, Functional Requirements, Key Entities, Success Criteria, Assumptions, Local Test Path, AI Affordance, OEM Matrix, Связанные документы, TL;DR. Scope (In/Out) встроен в «Контекст и цель спека» и в clarifications «Что эти решения изменили».

## Requirement Completeness

- [x] **CHK005** No `[NEEDS CLARIFICATION]` markers — ✓. Grep по spec.md не находит ни одного маркера. 5 pre-formed open questions заменены на статус-сводку «все закрыты».
- [x] **CHK006** Every requirement testable — ✓ (с одним open item). 34 FR имеют либо acceptance scenarios в US, либо Detekt rules, либо measurable SC. **Open item**: FR-034 (cross-references) — это не testable requirement в строгом смысле, это **architectural contract assertion** для downstream спек. Это нормально для cross-ref FR, но plan.md должен зафиксировать как именно проверяется (например, "S-9 spec при создании ссылается на FR-034").
- [x] **CHK007** Every requirement unambiguous — ✓. Несколько мест уточнены post-clarify: «сразу Google sign-in screen без explainer'а» (FR / US 2 #1), «`signOut()` не трогает локальный кеш» (FR-006), «после sign-in F-4 НЕ инициирует config-sync» (FR-006 constraint).
- [x] **CHK008** Success criteria measurable — ✓. SC-001..SC-014 содержат конкретные verification commands (grep, Detekt rule, CI зелёный, instrumentation test).
- [x] **CHK009** Success criteria technology-agnostic — ⚠️ partial. SC-005 упоминает «эмулятор с Firebase emulator suite», SC-007 — «instrumentation test killing process». Это **acceptable** уровень specificity для spec, который преходит в plan. Полное abstraction (просто «device under test») было бы менее actionable.
- [x] **CHK010** Acceptance scenarios explicit для каждого US — ✓. Все US (1-7) имеют Given/When/Then формат. US 1: 4 сценария. US 2: **8 сценариев** (после clarify expansion включая identity-links new vs existing account). US 3: 4. US 4: 3. US 5: 3. US 6: 2. US 7: 1.
- [x] **CHK011** Edge cases identified — ✓. 17 edge cases включают: empty state (Google без email), error state (NetworkError, ProviderUnavailable, NoEmail), retry (refresh failure), double-action (двойной tap на signIn), legacy code (anonymous), corruption (corrupted session blob), Sign-In trap, конфиг устарел, merge conflict, Android settings drift → S-9, F-4 «пытается помочь» → bug.
- [x] **CHK012** Scope clearly bounded — ✓. «Что строим» и «Что НЕ строим» явно отделены в Контекст section. Phone/Email/Apple/SSO adapter'ы — out of scope. Subscription billing — out of scope. ChainOfTrust / P-10 — out of scope. Account deletion UI — out of scope (S-6). Config sync — out of scope (S-8). Health monitoring — out of scope (S-9).
- [x] **CHK013** Dependencies/assumptions explicit — ✓. Section **Dependencies (внешние)**: Firebase Auth project (admin task), F-3 merged, F-CRYPTO merged (но не runtime dep). Section **Assumptions**: 11 пунктов. Cross-references на S-8/S-6/S-9/F-5/S-2 в FR-034.

## Feature Readiness

- [x] **CHK014** All FR have acceptance criteria — ✓ (с notes). Большинство FR mapped к US или SC. **Notes**:
  - FR-001..FR-005 (structure) → проверяются Detekt rules + Gradle dependency check (SC-010).
  - FR-006 (AuthProvider port) → US 2, US 4, US 5.
  - FR-007 (AuthIdentity) → US 2 #2.
  - FR-016a (identity-links) → US 2 #7, #8.
  - FR-033 (SignInTrigger) → US 1 #4, US 5, плюс будущая sequence diagram.
  - FR-034 (cross-refs) → не testable, см. CHK006.
- [x] **CHK015** User scenarios cover error paths — ✓. Каждый US имеет минимум один error scenario:
  - US 1: subscription/billing nudge **не показывается** (negative test).
  - US 2: Cancelled (#5), NoEmail (#6).
  - US 3: vendor leakage detection (negative).
  - US 4: refresh failed (#3).
  - US 5: corrupted blob (#3).
  - US 6: provider-swap as fitness (positive but architectural).
  - US 7: только happy path — но это P3, integration test для S-2 territory.
- [x] **CHK016** SC produced by FR — ✓. Mapping:
  - SC-001 (vendor isolation F-5) → FR-002, FR-028.
  - SC-002 (S-2 vendor isolation) → FR-002, FR-028.
  - SC-003 (AuthProvider clean) → FR-002, FR-027.
  - SC-004 (LocalModeNoSignInTest) → FR-030.
  - SC-005 (first-cloud-action) → US 2.
  - SC-006 (token refresh) → US 4, FR-017.
  - SC-007 (session persistence) → US 5, FR-020, FR-022.
  - SC-008 (provider-swap) → US 6.
  - SC-009 (wire-format roundtrip) → FR-022.
  - SC-010 (Detekt rules) → FR-027 .. FR-029.
  - SC-011 (two-emulator) → US 7.
  - SC-012 (effort/timing) → not requirement-mapped, это process metric.
  - SC-013 (Privacy Policy) → FR-032.
  - SC-014 (subscription_state) → FR-011, FR-031.

---

## Внутренние противоречия — найденные и исправленные в clarify-aware diff

Перед записью этого чек-листа исправлены 4 места, где acceptance scenarios конфликтовали с clarify pass решениями:

1. **US 1 acceptance #4**: ссылка на «cloud-feature кнопка» — заменена на «пользователь никогда не нажимает Войти в Google в wizard или SignInTrigger». Q5 boundary (no cloud-feature buttons exist) enforced.
2. **US 2 acceptance #2**: ссылка на `SessionStore.current()` возвращает non-null — переформулирована: SessionStore остаётся internal, test через adapter-internal harness. Q2 boundary enforced.
3. **US 3 acceptance #4**: grep допускал `ProviderKind` в AuthProvider.kt — убрано. Q4 boundary enforced (providerKind удалён).
4. **US 4 description**: «cloud action», «cloud-feature» — заменены на «server interaction» / «consumer-сервис делает server interaction». Q5 boundary enforced.

---

## Open items (для plan stage)

1. **FR-034 как testable contract**: plan.md должен явно зафиксировать, что cross-references на S-8/S-6/S-9 проверяются при создании этих спек (CI procedure или manual checklist в `/speckit.specify` для каждой из них).
2. **SC-005 (first-cloud-action) переименовать**: после Q5 boundary это **wizard recovery test**, не «first cloud action». Не блокирует requirements-quality pass, но plan.md / tasks.md должны использовать корректное название.

---

## Verdict

**14/16 ✓, 2 partial.** Spec **проходит** requirements-quality baseline. Open items — улучшения precision, не блокеры для `/speckit.plan`. Внутренние противоречия (4 шт) исправлены при выполнении этого чек-листа.

---

## Что это значит простыми словами

Спека написана так, что:
- Все требования можно проверить (либо командой grep, либо тестом, либо Detekt-правилом).
- Все сценарии прописаны конкретно: что дано, что сделать, что должно получиться.
- Все границы понятны: что строим и что **не** строим.
- Все зависимости от других спек явные (F-3, F-CRYPTO, S-8, S-6, S-9).
- 4 небольших противоречия между clarify-таблицей и сценариями были найдены и исправлены прямо сейчас.
- Спека готова к следующему шагу `/speckit.plan` — там будет конкретный план реализации.
