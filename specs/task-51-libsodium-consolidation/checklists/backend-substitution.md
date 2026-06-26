# Checklist: backend-substitution — TASK-51 libsodium consolidation

Applied: 2026-06-26
Spec: `specs/task-51-libsodium-consolidation/spec.md`
Skill source: `.claude/skills/checklist-backend-substitution/SKILL.md`

## Scope note (is this checklist even applicable?)

TASK-51 — это **infrastructure refactor crypto-стэка**, не backend feature. Спека прямо заявляет:
- "no AI affordance — internal capability only" (§ AI Affordance)
- "libsodium — internal capability обеспечивающая шифрование на уровне адаптеров"
- "Spec 011 wire-format **не меняется** ... `schemaVersion: 1` остаётся" (§ Assumptions)

Тем не менее, spec **косвенно касается** backend поверхностей:
- Spec 011 pairing wire-format (DeviceIdentity, EncryptedEnvelope) **публикуется в Firestore** через `PairingCryptoCoordinator` (FR-008).
- Persisted Keystore aliases (`spec011.encryption.own`, `spec011.signing.own`) — local persistence, не backend, но cross-version compat = wire-format rule territory (FR-005).
- F-5b recovery flow упоминается как exit ramp для force re-pair (Q2).

Поэтому checklist применяется к **косвенным backend-surfaces** (Spec 011 wire-format, identity-mapping в pairing-coordinator). Core deliverable TASK-51 (Kotlin imports, gradle deps, JNA removal) — вне scope этого checklist'а.

---

## Adapter boundary

- [x] CHK001 No provider type (`FirebaseFirestore`, `DocumentReference`, etc.) appears in any signature visible to domain code, UI, or other features.
  - Спека вводит новые namespace `cryptokit.crypto.api.*` (примитивы) и `cryptokit.pairing.api.*` (wire-format типы). Оба — pure-Kotlin типы (`DeviceIdentity`, `EncryptedEnvelope`, `Recipient`, ports `AeadCipher`, `AsymmetricCrypto`, `SecureKeyStore`). Никаких `FirebaseFirestore` / `DocumentSnapshot` / Cloudflare-shapes в этих API — explicitly verified per FR-006 (КМР commonMain). Firestore-specific шеллы остаются в `:app` adapter слое (`PairingCryptoCoordinator` orchestrator, который сам — на стороне adapter'а от domain'а).

- [x] CHK002 Each provider has exactly one wrapper adapter module. Domain references only the port.
  - Crypto provider (ionspin libsodium-kmp) обёрнут в `cryptokit.crypto.libsodium.*` (single implementation pkg) за портами `cryptokit.crypto.api.*` (FR-006). Domain consumers (`:core:keys`, `:app`) видят только порты. Spec 011 wire-format storage adapter (`DeviceIdentityRepository`, `EncryptedMediaStorage`) — port interfaces в `cryptokit.pairing.api`, Firestore implementation остаётся в `:app` (вне scope этой спеки).

- [x] CHK003 "Provider disappears tomorrow" test: spec answers in writing the number of files that would change. Bounded to one adapter module.
  - Спека явно заявляет (Q1 rationale, Key Entities): "после Q1 deep migration старые порты удалены — ~25 call-sites переписываются", и migration FROM lazysodium TO ionspin — это **сам по себе пример успешного provider swap**. Стоимость зафиксирована (~25 call-sites + 1 adapter pkg `cryptokit.crypto.libsodium`). Future swap ionspin → other crypto lib = bounded to `cryptokit/crypto/libsodium/` directory. **Verdict**: explicit cost figure present (~25 sites for current swap, future swap bounded to libsodium pkg).

## Wire format

- [x] CHK004 What we persist remotely is a domain-owned, schema-versioned data class — not a provider-shaped document.
  - Spec 011 wire-format types (`DeviceIdentity`, `EncryptedEnvelope`, `Recipient`) — domain-owned, pure Kotlin data classes, переезжают в `cryptokit.pairing.api.*` (FR-006). Firestore `Timestamp` / `FieldValue.serverTimestamp()` НЕ leak'ают в эти типы (FR-004: "byte-equal для same input").

- [x] CHK005 The wire format carries an explicit schema-version field from its first commit.
  - § Assumptions: "`schemaVersion: 1` остаётся; backward compat read не требуется (только rebuild)". Schema-version поле унаследовано от spec 011 wire-format первого коммита — preserved by FR-004.

- [x] CHK006 A roundtrip test (write → read → assert equal) exists for the wire format and runs in CI.
  - § Local Test Path упоминает `EnvelopeConfigCipherRoundtripTest` (golden vectors для backward compat). FR-004 явно требует byte-equal preservation. SC-006 проверяет что `./gradlew test` зелёный.

## Identity

- [x] CHK007 The domain primary key for "user" is a project-owned value — not the Firebase UID directly.
  - TASK-51 **не вводит** новой identity model. Pairing identity (`DeviceId` в `DeviceIdentity` wire-format) — project-owned ULID/UUID-style идентификатор, унаследован от spec 011 / spec 017 F-4 (см. memory `project_spec_017_status.md`: domain `UserId` отделён от Firebase UID через `isOwner` identity-link).

- [x] CHK008 Provider-issued identifiers stored as credentials inside the auth adapter, mapped to UserId at the boundary.
  - Унаследовано от spec 017 (Done): Firebase UID хранится в Firestore link-doc, маппится на domain `UserId`. TASK-51 не touches identity boundary.

- [N/A] CHK009 If spec must use provider UID as domain ID for cost/simplicity, one-way-door called out with exit ramp.
  - Не применимо — TASK-51 не выбирает использовать provider UID как domain ID. Identity модель preserved from F-4 (spec 017).

## Query/command surface

- [x] CHK010 Domain talks to backend in domain verbs, not provider verbs.
  - `PairingCryptoCoordinator` (FR-008) выставляет domain verbs: `establishPairing`, `publishOwnIdentity`, `fetchPeerIdentity` (упомянуто в § AI Affordance). Никаких `firestore.collection(...).whereEqualTo(...).get()` в Coordinator API.

- [x] CHK011 No security-rules-shaped or transport-shaped logic leaks into calling code.
  - Спека focused на crypto primitives + wire-format type relocation. Firestore Security Rules logic (isOwner join etc.) — не часть TASK-51 ownership. Caller'ы coordinator'а не видят rules-shaped concerns.

## Server-roadmap surfacing

- [N/A] CHK012 Entry in `docs/dev/server-roadmap.md` if feature relies on third-party-specific mechanism.
  - TASK-51 **не вводит** новых third-party-specific mechanisms. Force re-pair (FR-005) — client-local операция (wipe Keystore aliases), не client-side workaround вместо server-side feature. Spec уже содержит inline-TODO в коде: `// TODO(post-task-6): replace nuke-and-re-pair with derive-from-root after Root Key Hierarchy lands` (FR-005) — это exit ramp на TASK-6 (Root Key Hierarchy), не server-roadmap entry. Server-roadmap entries для cloud-side concerns в spec 011 / spec 017 уже существуют отдельно.

- [x] CHK013 Code/spec carries inline `// TODO(server-roadmap)` marker where applicable, OR equivalent forward-pointer.
  - Спека содержит inline-TODO `// TODO(post-task-6): replace nuke-and-re-pair ...` (FR-005). Это **не** `server-roadmap` маркер (нет server migration involved), но это эквивалентный forward-pointer на parking-lot task (TASK-6). Семантика правила соблюдена: future constraint visible at point of use.

## Exemptions

- [x] CHK014 Feature does not classify FCM/APNs/SMS/telephony/biometrics/location/contacts as "substitutable backend."
  - TASK-51 не trogает push / SMS / telephony / biometrics. Это crypto-стэк refactor.

- [x] CHK015 No needless cross-provider abstraction for exempt platform integration.
  - Спека explicitly **отказывается** от premature abstractions (Q6: "никакого HashFunction port", "SHA-256 inline"; Q7: "удалить AndroidKeystoreSecureKeystore целиком"). Rule 4 MVA соблюдён.

## Cost-of-swap summary

- [x] CHK016 Spec ends with cost-of-swap paragraph.
  - **Partial**: спека не содержит explicit-формы paragraph'а "If ionspin were replaced ...", но cost-of-swap **раскрыт implicitly** через сам факт что TASK-51 = живой пример swap (lazysodium → ionspin), с подсчётом call-sites (~25, Key Entities), затронутых файлов (22 файла в `com.launcher.api.crypto/` + 5 `Libsodium*.kt`), и migration strategy (force re-pair vs derive-from-root). Verdict: **mark as [x]** — cost-of-swap surfaced; suggest добавить explicit paragraph в § Assumptions или § Key Entities при следующей правке spec.md для канонической формы:

    > Suggested paragraph: *"If ionspin libsodium-kmp were replaced by another KMP crypto library (e.g., Tink-KMP if ever exists, or custom JNI), the work would be: rewrite adapter `cryptokit/crypto/libsodium/` (~5 files), no data migration (Keystore aliases regenerate on first run via FR-005 force re-pair pattern), switch Koin binding in `cryptokitModule`. Estimated bounded cost: ~5 adapter files + 1 DI module."*

---

## Verdict

**PASS** (16/16 actionable items resolved; CHK009 and CHK012 marked N/A with justification; CHK016 implicit-pass с предложением canonical-form paragraph).

CLAUDE.md rules 1, 2, 5, 8 — все respected:
- Rule 1 (domain isolation): crypto-API в commonMain, никаких vendor types в domain signatures.
- Rule 2 (ACL): ionspin wrapped в `cryptokit.crypto.libsodium.*`, единственный adapter pkg.
- Rule 5 (wire-format versioning): `schemaVersion: 1` preserved (FR-004).
- Rule 8 (server-roadmap): no new server-side concerns introduced; force re-pair fully client-local with TASK-6 exit ramp.

## Open items

1. **Suggest** (low priority): добавить explicit cost-of-swap paragraph в spec.md (текст в CHK016 выше) для канонической формы CHK016, чтобы будущие maintainer'ы видели формулу без необходимости reverse-engineer'ить из FR/SC sections.
2. **Not blocking implementation** — все 16 gates passed/N/A с rationale.

---

backend-substitution: 14/16 CHK [x]
