# Checklist: requirements-quality — spec 016 F-CRYPTO

Run date: 2026-06-17 (after Clarifications pass).

## Content Quality

- [ ] **CHK001** No implementation details in spec.md.
  **FAIL** — спека намеренно содержит implementation hints: `kotlinx.serialization.json`, `kotest properties`, `Koin DI`, `ionspin/kotlin-multiplatform-libsodium`, `kotlinx.serialization`, конкретные алгоритмы (XChaCha20-Poly1305, X25519, Ed25519, HKDF-SHA256), Gradle source sets, Detekt-правило. **Обоснование принять с открытыми глазами**: F-CRYPTO — это infrastructure-уровневая спека о криптографических примитивах; «технологически-агнистично» здесь невозможно — алгоритмы и есть user-value (industrial baseline Signal/WhatsApp). Аналогично для KMP targets — это и есть scope. **Принимается как expected exception**, документировано в spec.md «Контекст и цель спека».

- [x] **CHK002** Focus on user value.
  Раздел «Контекст и цель спека» формулирует value через **потребителей** (F-5, спека 011, future multi-device-recovery spec (TBD), мессенджер владельца, фото-приложение, AndroidTV/EOS) — для каждого свой use case. User Stories 1, 2, 3, 6 фокусируются на наблюдаемом behaviour, не на коде.

- [x] **CHK003** Non-technical stakeholder readable.
  Секция «Решения mentor-сессии 2026-06-17» даёт plain-Russian summary 8 пунктов. Clarifications таблица — резолюции каждого решения с обоснованием. Edge Cases переформулированы пользовательскими сценариями («App factory reset / переустановка»).
  ⚠️ **Caveat**: FR-секция всё-таки требует Android/Kotlin background для понимания (это infrastructure spec, не feature spec). Mitigation: User Stories + Edge Cases + ADR-008 cross-ref дают non-tech картинку.

- [x] **CHK004** All mandatory sections present.
  ✅ User Stories (6 штук, P1-P3), ✅ Scope (через «что строим» / «что НЕ строим» + Edge Cases), ✅ Functional Requirements (30 штук с FR-001..FR-030), ✅ Success Criteria (12 штук с SC-001..SC-012), ✅ Local Test Path (mandatory), ✅ AI Affordance (mandatory, явно `no AI`), ✅ OEM Matrix (mandatory, 5 OEM строк).

## Requirement Completeness

- [x] **CHK005** No `[NEEDS CLARIFICATION]` markers remain.
  Grep по spec.md — нет таких маркеров. Все 8 вопросов из Clarifications таблицы resolved.

- [x] **CHK006** Every requirement is testable.
  Проверка FR-001..FR-030:
  - FR-001..005 (build structure) — проверяется через `./gradlew :core:crypto:dependencies` и Gradle fitness function (SC-006).
  - FR-006..012 (ports) — проверяется через compile-time (interface существует) + property tests (SC-001).
  - FR-013..018 (adapters + storage) — проверяется через RFC KAT (SC-002), property tests (SC-004), wrap/unwrap roundtrip (SC-008/SC-009).
  - FR-019..024 (validation set) — каждое — отдельный CI test (SC-002, SC-003, SC-004, SC-010).
  - FR-025..027 (wire format) — backward-compat read test (SC-007).
  - FR-028..030 (constitution gates) — Detekt rules + Gradle dependency check.
  Все 30 FR имеют observable assertion в SC или Local Test Path.

- [x] **CHK007** Every requirement is unambiguous.
  Каждое FR содержит конкретный verb (`MUST содержать`, `MUST реализовать`, `MUST NOT импортировать`) + specific subject. Subjective qualifiers («fast», «simple», «intuitive») отсутствуют. ⚠️ Один потенциально серый момент: FR-014 «research-фаза MUST подтвердить актуальность library» — «актуальность» расшифрована через Q4 ответ, но в FR это не вплетено. **Минорный gap**.

- [x] **CHK008** Success criteria measurable.
  SC-001 (grep по конкретному файлу), SC-002 (CI зелёный), SC-004 (1000 итераций), SC-006 (Gradle dependencies = пустой список launcher-модулей), SC-007 (identical bytes), SC-008/SC-009 (instrumentation test pass), SC-012 (effort 2-3 weeks). Все SC имеют конкретную метрику или command.

- [x] **CHK009** Success criteria technology-agnostic.
  ⚠️ **Частично FAIL**: SC упоминают конкретные tools (Gradle commands, GitHub Actions, ionspin binding). Это **обоснованно** для infrastructure spec'и (см. CHK001 rationale) и owner explicitly accepts.

- [x] **CHK010** Acceptance scenarios explicit.
  Все 6 US имеют **Acceptance Scenarios** в формате Given/When/Then (US-1: 3 сценария, US-2: 3, US-3: 3, US-4: 2, US-5: 2, US-6: 2 = 15 acceptance scenarios). Никакого «и так далее».

- [x] **CHK011** Edge cases identified.
  9 явных Edge Cases:
  - ionspin dead (fallback decision)
  - Android Keystore unavailable (rooted)
  - FakeAdapter в release build (Detekt rule + runtime assertion)
  - Cross-platform vector mismatch
  - Wycheproof low-order point
  - Nonce reuse
  - SecureKeyStore + user lock-screen change
  - Key wire-format migration
  - App reinstall (TEE loss → cross-ref на future spec (TBD))
  - Unknown KeyId prefix (added in Clarifications)
  - Large file OOM (added in Clarifications)
  Покрытие: empty state (нет применимо для крипты), error state (✓), retry (n/a — крипта stateless), double-action (nonce reuse = ✓).

- [x] **CHK012** Scope clearly bounded.
  «Что строим» — 8 пунктов; «Что НЕ строим» — 7 пунктов. Каждый Out-of-scope item имеет cross-reference куда переехало (F-5, spec 011, future spec (TBD), post-MVP).

- [x] **CHK013** Dependencies and assumptions explicit.
  Раздел `## Assumptions` — 9 пунктов. Раздел dependencies — секция `Dependencies` в роадмапе (F-4 dependency снят, F-CRYPTO работает standalone). Cross-references: ADR-008, project_deferred_cloud_architecture memory, SRV-CRYPTO-004/006/007, project_f_crypto_decisions, CLAUDE.md rules 1/2/4/5/6/8.

## Feature Readiness

- [x] **CHK014** All FRs have clear acceptance criteria.
  Cross-traceability:
  - FR-001..005 → SC-006 (Gradle check) + US-5 (extract-readiness).
  - FR-006..012 → SC-001 (potрebители не импортируют libsodium) + US-1.
  - FR-013..018 → SC-008, SC-009 (wrap pattern works) + US-3.
  - FR-019..024 → SC-002, SC-003, SC-004, SC-010 + US-6.
  - FR-025..027 → SC-007 (cross-platform parity) + US-2.
  - FR-028..030 → SC-011 (Detekt rule) + US-4 (libsodium replaceable).

- [x] **CHK015** Primary flows covered + at least one error path per US.
  US-1: error path — FakeAdapter в release build (Detekt+runtime).
  US-2: error path — cross-platform vector mismatch (CI fail).
  US-3: error path — Android Keystore unavailable / user lock-screen change / app reinstall.
  US-4: error path — ionspin dead → fallback to BouncyCastle.
  US-5: error path — Gradle dependency leak (launcher-module imported).
  US-6: error path — отсутствует (документ-уровень spec). **Acceptable** — US-6 about documentation existence.

- [x] **CHK016** Measurable outcomes covered by FRs.
  Walk SC → FR:
  - SC-001 → FR-006, FR-028 ✓
  - SC-002 → FR-019, FR-022 ✓
  - SC-003 → FR-020 ✓
  - SC-004 → FR-021 ✓
  - SC-005 → FR-013, FR-017 (testable через port substitution) ✓
  - SC-006 → FR-005 ✓
  - SC-007 → FR-022, FR-027 ✓
  - SC-008/SC-009 → FR-015 ✓
  - SC-010 → FR-023, FR-024 ✓
  - SC-011 → FR-018 ✓
  - SC-012 → effort estimate, not strictly FR; acceptable as a release SC.

---

## Open issues (для followup)

| # | Issue | Severity | Action |
|---|---|---|---|
| O-1 | CHK001 — implementation details в spec'е | Accepted exception | Документировано, infrastructure spec'и unavoidably tech-detail-heavy. |
| O-2 | FR-014 «актуальность ionspin library» — критерий dead не вплетён в FR (только в Clarification Q4) | Minor | Можно дополнить FR-014 текстом: «dead = last commit > 12 мес ИЛИ open critical iOS issue без response > 90 дней». Не блокер для plan-фазы. |
| O-3 | US-6 error path | Minor | US-6 — про documentation; error path = doc отсутствует или устарел. Не критично. |
| O-4 | SC-012 effort estimate ≠ FR-mapped | Minor | Effort — release metric, не testable FR. Принимается. |

## Result

**13/16 PASS**, 2 accepted exceptions, 1 minor open.

**Verdict**: spec.md **готова к `/speckit.plan`**. Open issues — non-blocking, могут быть закрыты в plan/research фазе.

---

<!-- novice summary -->

## TL;DR простым языком

Прошёл по 16 пунктам качества спеки. **Спека готова к следующему шагу** (planning). Мелкие замечания:
- В спеке упоминаются конкретные технологии (XChaCha20, libsodium, Gradle и т.п.) — обычно так делать не стоит, но это **криптографическая infrastructure**: технологии и есть predmет спеки, иначе разговор бессодержательный. **Принято как исключение.**
- Один FR (FR-014 про «когда менять libsodium-библиотеку») не до конца формализован — критерий «библиотека мёртвая» описан в Clarifications, но в саму формулировку FR не вплетён. Минорно.
- Одна User Story (US-6 про документацию для аудитора) без error path — нормально, потому что error = «документация отсутствует», тривиально.

Это нормальный результат для infrastructure-спеки. Главное — **30 функциональных требований** все **measurable** (можно проверить тестом), **12 success criteria** все имеют конкретные команды или метрики, **9 edge cases** покрыты.
