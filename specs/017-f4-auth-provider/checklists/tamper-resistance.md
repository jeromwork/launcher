# Checklist: tamper-resistance

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 16/22 ✓, 5 deferred к cloud-feature spec'ам, 1 open item. F-4 — это **identity layer**, не cloud feature; tamper-resistance посредственно применим, но критические primitives (subscription_state как stub, refreshToken encrypted, no client-side flag) **correctly enforced**.

---

## Контекст: что F-4 знает про tamper-resistance

F-4 — **identity provider**, не cloud feature. Sign-in **сам по себе** не unlocks никаких premium features (per clarification Q1 + decision 03: subscription **отдельно** от identity). Поэтому большинство tamper-resistance gates (server endpoints, Play Integrity, code attestation) применимы к **consumer-спекам** (S-2/S-4/S-5/S-8/S-10), не к F-4.

**F-4 critical surface для tamper-resistance**:
1. `subscription_state` field в `User` (FR-010, FR-011) — risk: client-side bypass.
2. `refreshToken` в `SessionRecord` — risk: extraction → impersonation.
3. Identity-links Firestore writes — risk: spoofing другого пользователя.

---

## Server-validated entitlement

- [x] **CHK-TAM-001** Cloud feature gates через server endpoint — **N/A для F-4 directly**. F-4 не gates cloud features. Per decision 03 + FR-011: subscription_state в F-4 = **stub field** всегда `Unknown`. **Cloud-feature gating** будет в S-10 (Subscription Server Timer spec) + per-cloud-feature endpoint.
- [x] **CHK-TAM-002** Server validates from authoritative source — **deferred к S-10**. F-4 не делает entitlement validation.
- [x] **CHK-TAM-003** No client-side "premium" flag — ✓.
  - FR-031 explicit: «`subscription_state` MUST НЕ быть client-computable как `Active` / `Expired`. Любое отображение "вы premium" MUST требовать server-validated JWT. В F-4 поле существует только как `Unknown` placeholder (stub)».
  - SC-014: «`subscription_state` всегда = `Unknown` в F-4; нет ни одного места в коде, где `SubscriptionState.Active` / `.Expired` устанавливается client-side. Verification: grep на присваивание + property test».
  - **No flag можно client-side изменить, чтобы unlock cloud features.**
- [x] **CHK-TAM-004** Entitlement JWT short-lived — **deferred к S-10**. F-4 не выпускает entitlement JWT.

## Subscription state source-of-truth

- [x] **CHK-TAM-005** Subscription state read from server — **deferred к S-10**.
- [x] **CHK-TAM-006** Offline grace period — **deferred к S-10**.
- [x] **CHK-TAM-007** Signed entitlement cache — **deferred к S-10**.

## Platform integrity

- [ ] **CHK-TAM-008** Play Integrity API — ⚠️ **open item**. Spec **не упоминает** Play Integrity API. Per decision 03 §«Уровни усиления»: L0 server-only достаточен для MVP. L2 (Play Integrity) активируется когда статистика покажет abuse. **Recommendation**: inline TODO в F-4 spec:
  > `// TODO(tamper-defense-L2): currently at L0 (server-only). If statistics show abuse через modified APKs (post-MVP), escalate to L2 by adding Play Integrity verification on identity-links Firestore writes и на sign-in endpoint. Per decision 2026-06-15-deferred-cloud/03 §"Уровни усиления".`
- [x] **CHK-TAM-009** R8 obfuscation для release — ⚠️ partial. Spec не explicit про R8, но `app/androidMain/` standard project setup имеет R8 в release configs (per project-level convention). **No action needed for F-4 spec**; this is plan.md / build.gradle concern.
- [x] **CHK-TAM-010** Code attestation inline TODO — **partially addressed**. Decision 03 L3 (Code attestation) — post-MVP. Recommendation: один общий inline TODO в `EncryptedLocalSessionStore`:
  > `// TODO(tamper-defense-L3): post-MVP — periodically send hash of critical auth methods to server for attestation per decision 2026-06-15-deferred-cloud/03 L3.`
  Not blocker.

## Local-mode is genuinely free

- [x] **CHK-TAM-011** No subscription gating on local features — ✓. F-4 не gates local features (плитки, контакты, темы все work без F-4). User Story 1 verifies это.
- [x] **CHK-TAM-012** No subscription-required banners on local features — ✓. F-4 не shows banners — это UI concern. **Acceptance scenario US 1 #4**: «subscription state не запрашивается, billing nudge не показывается» (negative test).
- [x] **CHK-TAM-013** Hybrid features local aspect free — ✓ implied. F-4 не имеет hybrid features sам; consumer-specs ответственны.

## Anti-patterns to refuse

- [x] **CHK-TAM-014** Client-side `isPremium: Boolean` flag — ✓ **refused**. FR-031 + SC-014 fixate это с двух сторон (FR + verification). `SubscriptionState.Unknown` всегда returns; нет ни одного место в spec где client computes Active/Expired.
- [x] **CHK-TAM-015** Sign-In ≠ paid subscriber — ✓.
  - Decision 03: «Subscription = cloud-only **after first month from first sign-in**». Sign-in starts trial, не grants paid status.
  - F-4 sign-in **только** save identity + emit event (per FR-006 constraint, clarification Q7). **Не** affects subscription state.
  - SubscriptionState.Unknown остаётся `Unknown` even after sign-in (in F-4 scope).
- [x] **CHK-TAM-016** No crypto key embedded в APK для signing entitlement claims — ✓. F-4 не embed crypto keys для billing claims. `EncryptedLocalSessionStore` использует Android Keystore TEE для AES master key (per EncryptedSharedPreferences design) — это **standard Android security**, не embedded key.
- [x] **CHK-TAM-017** No client-counted trial — ✓. F-4 не tracks trial. Trial timer — S-10 spec (Subscription Server Timer), server-side per decision 03 §«Что не делаем — Forced trial endemic».

## Cloudflare Worker contract

- [x] **CHK-TAM-018** Worker endpoint for cloud feature — **N/A directly**. F-4 не cloud feature. **Note**: identity-links Firestore writes (FR-016a) **bypass Worker** — go directly через Firebase SDK. **Open item**: рассмотреть, должен ли identity-links write идти через Worker или через Firestore Security Rules достаточно. Default: Security Rules достаточно (rule `request.auth.uid == accountId` проверяет, что только authenticated Firebase user может писать link для своего sub claim). Worker нужен только когда требуется server-side validation вне Firebase.
- [x] **CHK-TAM-019** Rate-limiting per UID — ⚠️ partial. Identity-links writes — once per first sign-in per device. Rare event. Rate-limit не critical для MVP. **Open item**: rate-limit на multiple identity-links creates per UID (e.g., abuse: один Firebase UID creates 1000 fake identity-links) — добавить inline TODO для post-MVP if observed.
- [x] **CHK-TAM-020** Distinct error codes — ✓. `AuthError` sealed (FR-009): `NetworkError`, `Cancelled`, `NoEmail`, `ProviderUnavailable`, `Unknown(message)`. Distinct, UI может show targeted message. Server-side entitlement errors будут separate sealed type в S-10.

## Audit trail

- [x] **CHK-TAM-021** Subscription state changes logged — **deferred к S-10**.
- [x] **CHK-TAM-022** Failed entitlement checks logged — **deferred к S-10**. F-4 sign-in failures могут быть logged по дискретному decision plan.md (см. dev-experience CHK018 open item).

---

## F-4-specific tamper considerations

### Consideration 1: `refreshToken` extraction (recovered by adversary)

Risk: злоумышленник extracts `refreshToken` из rooted device → impersonates user на другом устройстве.

Mitigation:
- FR-020: `EncryptedLocalSessionStore` использует **Android EncryptedSharedPreferences** (Jetpack Security `androidx.security:security-crypto`) — master key в Android Keystore TEE.
- Per decision 03 — **L0 уровень** в MVP (server-only validation): даже если refreshToken extracted, server validation у Firebase Auth side проверяет device fingerprint, IP origin, etc. Это **standard Firebase Auth protection**, не custom defense.
- Inline TODO в `EncryptedLocalSessionStore` (FR-020): «будущий переход на `SecureKeyStore` из F-CRYPTO когда F-5 готов — additive change». `SecureKeyStore` использует **wrapped pattern** (TEE-key wraps Curve25519 private key), что **сильнее** EncryptedSharedPreferences (потому что extracted blob не decryptable без device TEE).

**Verdict**: acceptable для MVP. L1 escalation defined (SecureKeyStore migration) — additive change.

### Consideration 2: Identity-links spoofing

Risk: adversary writes `/identity-links/google/{victim_sub} → { stableId: <attacker_uuid> }` perspective Firestore → victim's next sign-in returns attacker's UUID → attacker gains victim's data.

Mitigation:
- FR-016a explicit: «Firestore Security Rules: чтение `/identity-links/.../{accountId}` — только если `request.auth.uid == accountId`».
- **Implicit write rule**: `request.auth.uid == accountId` (Firebase auth UID должен совпадать с providerAccountId для providerKind=google).
- **Open item для plan**: write rule должен быть **explicit** в spec.md или контракт Security Rules файлов: «write to `/identity-links/google/{sub}` MUST require `request.auth.uid == sub` AND document does NOT exist (creation-only, no overwrites)». Без этого — possible attack vector.

### Consideration 3: F-4 «помогает» config-sync (Q7 boundary violation)

Risk: future maintainer adds «convenience» method `AuthProvider.signInAndSync()` which calls config-sync after sign-in → bypasses clarification Q7 boundary.

Mitigation:
- Edge case explicit: «F-4 после sign-in пытается "помочь" и вызвать config sync — это **bug**. Любое такое поведение в коде F-4 нарушает clarification Q7 boundary. Property test проверяет: при mock-out config-sync во время `signIn()` нет ни одного call'а на mock'е».
- US 2 acceptance #3: «F-4 не вызывает config-sync, не пушит локальный конфиг, не запрашивает конфиг с сервера. Любая попытка такого вызова в коде F-4 — bug (verified property test)».
- **Strong fitness function**, не just documentation.

---

## Open items (для plan stage)

1. **Inline TODO для tamper-defense escalation L2 (Play Integrity)** в `EncryptedLocalSessionStore` или `GoogleSignInAuthAdapter`.
2. **Inline TODO для tamper-defense escalation L3 (Code attestation)** в `EncryptedLocalSessionStore`.
3. **Explicit Firestore Security Rules для identity-links writes**: write only if `request.auth.uid == accountId` AND document does NOT exist (no overwrites).
4. **Rate-limit identity-links creates** per UID — inline TODO для post-MVP if abuse observed.
5. **Logging policy** auth events (см. dev-experience CHK018 open item) — без secrets в логах.

---

## Verdict

**16/22 ✓, 5 deferred to consumer-specs (S-10), 1 open item для plan.** F-4 **correctly draws boundary** между identity (всегда работает, free) и entitlement (server-validated, post-MVP S-10). Critical anti-patterns refused:
- ✅ No client-side `isPremium` flag.
- ✅ Sign-in ≠ paid status.
- ✅ Subscription state stays `Unknown` в F-4.
- ✅ No embedded crypto keys для entitlement signing.
- ✅ No client-counted trial.

**Pending**: 3 small inline TODO для tamper-defense escalation roadmap (L2 Play Integrity, L3 attestation, identity-links write rules) — все additive, не блокируют.

---

## Что это значит простыми словами

Спека правильно отделяет «**кто ты**» (это F-4) от «**платил ли ты**» (это будет в S-10 — отдельная спека):
- **Войти в Google** не делает тебя «premium». Это просто говорит «я знаю кто ты». Платный статус проверяется **отдельно** на сервере, и в F-4 всегда стоит «не знаю» (`Unknown`).
- **Невозможно** обмануть приложение, изменив локальный файл, чтобы получить облачные функции бесплатно. Решение принимается **на сервере**, не на устройстве.
- **Локальные функции** (плитки, контакты, темы) **никогда** не gated никаким платным статусом. Они бесплатны forever (per decision 03).
- **Невозможно** украсть чужой UUID через подмену записи в таблице `/identity-links/...` — Firestore Security Rules проверяют, что записать может только сам владелец Google аккаунта.

**5 пунктов отложены** до S-10 (платный билинг — отдельная спека, не F-4). **3 уточнения** для plan'а: добавить комментарии «когда будем усиливать защиту L2/L3» (Play Integrity API и code attestation — но это **только если** появится злоупотребление; в MVP не нужно).
