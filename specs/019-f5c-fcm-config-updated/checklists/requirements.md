# Checklist: requirements-quality — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass

## Content Quality

- [⚠️] **CHK001** No implementation details in spec.md.
  - **Status**: Partial violation, accepted with note. Spec содержит код-snippets (TypeScript для EventTypeRegistry entry, Kotlin для SosService example), упоминания `jose`, `Workers KV`, `firebase-admin` (для обоснования отказа), FCM SDK calls. Это инфраструктурная foundation-спека — детали транспорта **являются** user concern'ом (rule 5 wire-format, rule 8 server migration tracking). Convention в проекте: spec 018 F-5b аналогично содержит crypto algorithm names (XChaCha20-Poly1305, libsodium). Не блокирует.
  - **Action**: при `plan.md` написании — выделить чисто-tech detail (algorithm picks, library versions) туда, оставив в spec.md только wire-format contracts и port shapes.

- [x] **CHK002** Focus on user value. US-1 (multi-device sync), US-2 (remote support через cross-UID), US-3 (graceful degradation) — все user-facing value. US-0 (foundation) — infrastructural, но он явно отмечен и оправдан 9 known consumers (см. §«Reuse pattern»).

- [⚠️] **CHK003** Non-technical stakeholder readable. Сценарии 1-3 — да, читаемы. Сценарий 0 (foundation extensibility) — требует знания Kotlin/TS. FRs — dense с technical detail. Mixed.
  - **Action**: при review с non-tech stakeholder — пройти Scenarios 1-3 + Reuse pattern table, пропустить Foundation FRs.

- [⚠️] **CHK004** Mandatory sections present.
  - User Stories ✅
  - Requirements (FRs) ✅
  - Success Criteria ✅
  - Scope (In/Out): **нет explicit `## Scope` section**. In scope implied через FRs (Foundation, Config-updated specific, Wire format, Future-extensibility). Out of scope scattered в Notes («F-5c не делает merge», «F-5c не строит group management UX»).
  - **Action**: добавить explicit `## Scope` section с In/Out bullets — поднять чёткость.

## Requirement Completeness

- [x] **CHK005** No `[NEEDS CLARIFICATION]` markers. Все 6 Q's resolved в Clarifications session 2026-06-20 evening.

- [x] **CHK006** Every requirement testable. Audit sample:
  - FR-002 (jose validation, claims list) → testable через fake test tokens.
  - FR-009 (3 retries with backoff 500ms/2s/8s) → testable через fake FCM что возвращает 429.
  - FR-026 (client NO retries) → testable через mock Worker 5xx + assert client doesn't retry.
  - FR-060 (extensibility — ≤15 LOC per new event type) → testable через demonstration с dummy event type.

- [x] **CHK007** Unambiguous. Нет «fast»/«simple»/«intuitive» floaters. Все measurable terms — с числом или explicit boolean.

- [x] **CHK008** Measurable SCs. SC-001 (5s/30s p95), SC-002 (200ms p95), SC-003 (10s), SC-004 (5s), SC-005 (next foreground), SC-006 (exactly 1), SC-007 (<10ms, 100 concurrent), SC-008 (≤15 LOC), SC-009 (fitness test pass).

- [⚠️] **CHK009** Technology-agnostic SCs.
  - SC-007 mentions «CF free-tier CPU limits» — technology-specific.
  - SC-008 mentions «EventTypeRegistry», «EventType sealed» — technology-specific names.
  - SC-009 mentions «`core/push/`», «`core/launcher/*`» package paths.
  - **Это accepted trade-off** для foundation spec — измеримые критерии extraction-readiness требуют называния модулей.
  - **Action**: rule 9 violation principle — НЕ переписывать, foundation specs допускают technology-coupling в SC.

- [x] **CHK010** Acceptance scenarios explicit. Each US (0, 1, 2, 3) имеет Given/When/Then.

- [x] **CHK011** Edge cases identified. 8 edge cases в § Edge Cases (duplicate FCM, token rotation, JWKS rotation, revoked grant mid-flight, stale FCM token, unknown event type, schemaVersion mismatch, cold start).

- [⚠️] **CHK012** Scope bounded.
  - In Scope implicit через FRs (явно сгруппированы: Foundation, EventType-specific, Wire format, Future-extensibility).
  - Out of Scope scattered (Notes: «не делает merge», «не group UX», «не AI surface»).
  - **Action**: одна правка — добавить `## Scope` section с явными bullets In/Out (см. CHK004 action). Дубль item с CHK004.

- [x] **CHK013** Dependencies/assumptions explicit.
  - Dependencies в header («Зависит от: F-5b, F-4, Spec 007»).
  - Assumptions section с 6 пунктами (CF free tier, FCM quota, recipient stability, spec 008 model, FCM best-effort).

## Feature Readiness

- [x] **CHK014** FRs have clear acceptance criteria mapped to US. Группировка FRs (Foundation / Client / EventType-specific / Wire format / Future-extensibility) соответствует US-0 (foundation), US-1/2 (config-updated specific), US-3 (degradation). Mapping implicit но recoverable.

- [x] **CHK015** Primary + error flows. US-1 error: offline B; US-2 error: revoked grant mid-flight; US-3 — full error path.

- [x] **CHK016** SCs traceable to FRs:
  - SC-001 ↔ FR-008 (FCM dispatch) + FR-042 (ConfigSaver invokes) + FR-043 (handler invokes loadOwn).
  - SC-002 ↔ FR-026 (no retry) + FR-031 (fire-and-forget).
  - SC-003 ↔ FR-007 (recipient resolution).
  - SC-004 ↔ FR-027 (FcmTokenPublisher).
  - SC-005 ↔ FR-038 (graceful degradation).
  - SC-006 ↔ FR-044 (debounce 2s по triggerId).
  - SC-007 ↔ FR-009 (Worker retry) + FR-002 (jose).
  - SC-008 ↔ FR-060 (extensibility).
  - SC-009 ↔ pre-extraction hygiene в Notes (нет dedicated FR — есть в Notes).

## Summary

- **Pass**: 11/16
- **Partial/Warning**: 5/16 (CHK001, CHK003, CHK004, CHK009, CHK012)
- **Fail**: 0/16

## Action items

1. **High priority** (one PR-able change): добавить `## Scope` section в spec.md с явными In/Out bullets. Закрывает CHK004 + CHK012.
2. **Low priority**: при `plan.md` написании — вынести чисто-tech detail (library versions, algorithm picks) из spec.md в plan.md. Закрывает CHK001.
3. **Accept без правки**: CHK003 (foundation specs inherently dense), CHK009 (extraction-readiness SCs требуют module names).

---

## Заметка для новичка (TL;DR)

Качество требований проверено по 16 критериям. 11 чистых, 5 «частично» (детали техники в spec'е, scope-список разбросан), 0 fail'ов. Одна реальная правка нужна — добавить **раздел «Что входит / Что не входит»** в [spec.md](../spec.md), чтобы границы фичи были собраны в одном месте, а не разбросаны по примечаниям. Остальные warning'и — известный trade-off для foundation-специфик (инфраструктура требует технических деталей даже в спецификации). Это и есть «всё хорошо, нужна одна правка scope-section».
