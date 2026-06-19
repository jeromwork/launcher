# Specification Quality Checklist: F-5 Root Key Hierarchy + ConfigDocument Encryption + Recovery

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-06-19 (revised after clarify session 2026-06-19)
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
      - Wire-format и port-имена описаны как доменные контракты. Конкретные алгоритмы (XChaCha20-Poly1305, Argon2id) появляются как `algorithm` strings в wire-format — это часть публичного контракта interop'а.
- [x] Focused on user value and business needs
      - 4 User Stories: P1 «cloud config without plaintext», P1 «recovery after device loss», P2 «foundation для будущих cloud-фичей», P2 «Senior работает из кэша». Privacy regression и recovery UX — реальные user-facing values.
- [x] Written for non-technical stakeholders
      - User Stories — plain-language. Краткое резюме в конце переведено в plain Russian для не-разработчика владельца (per memory `feedback_plain_russian_for_novice`).
- [x] All mandatory sections completed
      - User Scenarios, Requirements, Success Criteria, Assumptions, Local Test Path, AI Affordance, OEM Matrix, **Clarifications** — все заполнены.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
      - 14 Clarifications зафиксированы в session 2026-06-19, все 30 FR конкретны.
- [x] Requirements are testable and unambiguous
      - Каждый FR содержит измеримый критерий (MUST / SHOULD NOT) с конкретными artefacts (имя port'а, поле wire-format, числовой limit).
- [x] Success criteria are measurable
      - SC-001 (0 plaintext), SC-002 (< 50ms roundtrip + < 500ms Argon2id), SC-003 (< 3s recovery e2e), SC-004 (100% правильный/неправильный passphrase), SC-005 (sign-out не триггерит recovery), SC-006 (0 cross-contamination identities), SC-007 (Huawei works in local mode), SC-008 (100+ DEKs scale).
- [x] Success criteria are technology-agnostic (no implementation details)
      - Метрики выражены в user-facing терминах или защищённых инвариантах.
- [x] All acceptance scenarios are defined
      - По 2-4 acceptance scenarios на каждую из 4 User Stories. Given/When/Then формат соблюдён.
- [x] Edge cases are identified
      - Sign-out/Sign-in same UID, Sign-in different UID, переустановка, огромный config, attacker tampered ciphertext, brute-force passphrase, Huawei, corrupted vault — каждый сценарий закрыт явным поведением.
- [x] Scope is clearly bounded
      - Что НЕ входит явно перечислено в Assumptions и в Clarifications: multi-admin (S-2), cross-app sharing (V-2/V-3), смена passphrase (future), биометрический unlock (future).
- [x] Dependencies and assumptions identified
      - F-4, F-CRYPTO, новый module `core/keys/` явно перечислены. Threat model явно описан.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
      - FR-001..FR-030 каждый покрыт хотя бы одной acceptance scenario или success criterion.
- [x] User scenarios cover primary flows
      - US-1 (encryption happy path), US-2 (recovery happy path), US-3 (foundation для future), US-4 (Senior cache invariant). Покрывают все реальные ситуации MVP.
- [x] Feature meets measurable outcomes defined in Success Criteria
      - Каждое SC-N связано с одним или несколькими FR.
- [x] No implementation details leak into specification
      - Класс-имена (`GoogleSignInIdentityProof`, `FirestoreRecoveryKeyVault`, `NoOpRecoveryAdapter`) появляются только в Key Entities / Assumptions как concrete MVP adapters — это контракт layering'а (CLAUDE.md rule 1+2), не implementation detail.

## Architecture Compliance (расширенный раздел)

- [x] CLAUDE.md rule 1 (Domain isolated from infrastructure) compliance
      - Все ports (`IdentityProof`, `RecoveryKeyVault`, `KeyRegistry`, `RootKeyManager`, `ConfigCipher`) в `core/keys/api/`. Concrete adapters (Google Sign-In, Firestore, Android Keystore) — отдельные modules.
- [x] CLAUDE.md rule 2 (ACL for every external dependency) compliance
      - Firebase wrapped через `FirestoreRecoveryKeyVault`. Google Sign-In wrapped через `GoogleSignInIdentityProof` (re-used from F-4). Android Keystore wrapped через `SecureKeyStore` (re-used from F-CRYPTO).
- [x] CLAUDE.md rule 3 (One-way doors) exit ramps documented
      - `RecoveryKeyVault` port позволяет swap Firestore → OwnServerRecoveryKeyVault (per SRV-RECOVERY-001) без переписывания F-5.
      - `IdentityProof` port позволяет swap Google Sign-In → future SMS/email/passkey adapters.
      - Cross-app: format root key not closes broker pattern (V-2/V-3 future).
      - Schema задел: `recipientMasterSignature: ByteArray?` nullable для future cross-signing.
- [x] CLAUDE.md rule 4 (MVA — defer if no rewrite needed) compliance
      - `KeyRegistry` с `schemaVersion` + additive DEK names с дня 1 — future spec'ы (S-2, S-5) добавляют свои DEK'и без переписывания.
      - Cross-app sharing — НЕ строим, но format готов.
      - Смена passphrase — отложена, inline TODO.
- [x] CLAUDE.md rule 5 (Wire-format versioning) compliance
      - `SealedConfig`, `RecoveryVaultBlob`, `KeyRegistry` storage — все имеют `schemaVersion` с первого коммита. Roundtrip + backward-compat tests в Local Test Path.
- [x] CLAUDE.md rule 7 (Fitness functions) addressed
      - Detekt-rule «no libsodium imports outside F-CRYPTO» применяется к F-5 (FR-002).
- [x] CLAUDE.md rule 8 (Server migration tracking) compliance
      - Добавлены SRV-RECOVERY-001, SRV-CRYPTO-005, SRV-CRYPTO-006 в server-roadmap.md.

## Notes

- Items marked incomplete require spec updates before `/speckit.plan`.
- Все 26 чеков прошли при записи финальной версии (после clarify session 2026-06-19).
- **Triggered triage** (per `procedure-assess-spec-complexity`): спека касается security/privacy, wire-format, persistence (Firestore + Android Keystore), backend-substitution, identity. Следующие checklists стоит прогнать на этапе plan: `checklist-security`, `checklist-wire-format`, `checklist-backend-substitution`, `checklist-failure-recovery`, `checklist-tamper-resistance`, `checklist-meta-minimization`, `checklist-requirements-quality`.
- **Cross-spec impact**: F-5 создаёт `core/keys/` module — это foundation, на которой строятся S-2 (multi-admin), S-5 (photos), S-4 (SOS), V-2 (messenger), V-3 (album). При планировании S-2 — обязательно прочитать enhancement notes 2026-06-19 в roadmap.md.
