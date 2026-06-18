# Checklist: backend-substitution

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 15/16 ✓ — **passes cleanly**. F-4 — образцовая spec для backend substitution readiness благодаря clarification Q1 (UUID stableId + identity-links exit ramp).

---

## Adapter boundary

- [x] **CHK001** No provider types в domain/UI — ✓.
  - `core/domain/auth/` package contains только domain types (AuthProvider, AuthIdentity, AuthError, User, SubscriptionState, Outcome).
  - **No** `FirebaseUser`, `FirebaseAuth`, `DocumentReference`, `DocumentSnapshot`, `QuerySnapshot`, `StorageReference` в любой port signature.
  - FR-002 + FR-027 + Detekt rules enforced.
  - **SignInTrigger composable** (`app/androidMain/auth/ui/`) — Android-specific Compose composable. Uses `AuthProvider` port internally, не Firebase types. UI layer **also clean**.
- [x] **CHK002** One wrapper adapter — ✓.
  - Firebase Auth + Credential Manager → `GoogleSignInAuthAdapter` (single adapter file).
  - Firestore identity-links lookup → inline в `GoogleSignInAuthAdapter` (per domain-isolation discussion).
  - Firestore `/users/{stableId}` writes → также inside same adapter (or via own internal helper).
  - **Single adapter module**. No leak.
- [x] **CHK003** "Provider disappears tomorrow" test, in writing — ✓.
  - **TL;DR section explicit**: «если Google или Firebase "исчезнут" — мы переписываем **один файл** (adapter), а не весь identity-слой».
  - **Domain-isolation CHK003** quantifies: 2-3 days для Firebase replacement, 1-2 days для Credential Manager replacement.
  - **Identity migration**: **zero**, because stableId = UUID (clarification Q1).

## Wire format

- [x] **CHK004** Persisted remote data is domain-owned data class — ✓.
  - **`/users/{stableId}`** — wire format owned by `User` domain class (FR-010).
  - **`/identity-links/{providerKind}/{providerAccountId}`** — FR-016a defines structure: `{ stableId: String, createdAt: Timestamp }`.
  - **`schemaVersion` field**: present для SessionRecord (FR-021), **missing для identity-links** — это open item из wire-format checklist (FR-016b proposed).
  - No `Firestore.Timestamp` / `FieldValue.serverTimestamp()` types appear в domain layer — these are adapter concerns.
- [x] **CHK005** schemaVersion с first commit — ⚠️ partial.
  - `SessionRecord` schemaVersion=1 (FR-021) ✓.
  - Identity-links document schemaVersion — **missing** (wire-format checklist open item FR-016b).
- [x] **CHK006** Roundtrip test exists и runs в CI — ✓ для SessionRecord. SC-009. Identity-links roundtrip test — open item (wire-format checklist).

## Identity

- [x] **CHK007** Domain primary key = project-owned value — ✓ **strongly enforced**.
  - **Clarification Q1**: `AuthIdentity.stableId: String` — наш собственный UUID. **Не** Firebase UID, **не** Google `sub` claim, **не** OAuth subject.
  - FR-007: explicit «наш собственный UUID, не Firebase UID и не Google sub claim».
  - This is **the central architectural property** F-4.
- [x] **CHK008** Provider IDs stored as credentials inside adapter — ✓.
  - Firebase UID — used internally by Firebase Auth SDK, не exposed в domain.
  - Google `sub` claim — used **только** в `GoogleSignInAuthAdapter` для identity-links lookup (`/identity-links/google/{sub}`).
  - **Mapping**: `Google sub → our UUID` lives в Firestore identity-links collection (server-side), не in client.
  - Domain receives только `AuthIdentity` с our UUID.
- [x] **CHK009** No provider UID as domain ID — ✓.
  - Per Q1, this is **explicitly NOT done**. The recommended path (a) Google sub claim was **rejected** на основании country-ban + own-server cutover + future Phone adapter compatibility concerns.
  - **One-way door avoided** properly per CLAUDE.md rule 3.
  - Exit ramp documented inline (FR-016a country-ban exit ramp TODO) + server-roadmap (`SRV-AUTH-IDENTITY-001`).

## Query/command surface

- [x] **CHK010** Domain talks в domain verbs — ✓.
  - `AuthProvider.signIn()`, `signOut()`, `currentUser` — все domain verbs.
  - **No** `firestore.collection("users").document(uid).get()` or `firebaseAuth.signInWithCredential(...)` сигнатуры в domain.
  - Consumer-сервисы (F-5 ConfigCipher, S-2 PairingFlow) обращаются через `AuthIdentity.stableId` — opaque UUID, не Firestore document reference.
- [x] **CHK011** No security-rules-shaped logic в calling code — ✓.
  - Consumer'ы не знают про `request.auth.uid` semantics, server timestamps, или Firestore transactions.
  - Adapter handles atomicity (identity-links creation race — open item для plan.md).

## Server-roadmap surfacing

- [x] **CHK012** Server-roadmap entries added — ✓.
  - Spec «Связанные документы» explicit:
    - `SRV-AUTH-001` (existing) — own JWT issuer post-cutover.
    - **`SRV-AUTH-IDENTITY-001`** (новый) — identity-links collection мигрирует первой на own-server, она authoritative source of truth маппинга providerAccountId → stableId; UUID stableId не меняется.
  - **Open task**: добавить эти entries в `docs/dev/server-roadmap.md`. Это plan.md task.
- [x] **CHK013** Inline `TODO(server-roadmap)` markers — ✓.
  - **FR-005** explicit (mandatory inline comment в `GoogleSignInAuthAdapter`):
    ```
    // TODO(auth-provider-extensions): Phone / Email-Password / Apple / SSO
    // adapters add through this port without changing the port shape.
    // TODO(server-roadmap): After own-server cutover — replace Firebase Auth
    // exchange step (Step 2) with POST ID Token → /auth/google on our server,
    // server verifies Google signature, issues own JWT. Port unchanged.
    // Per decision 2026-05-30-f4-identity/05-own-server-migration-strategy.md.
    ```
  - **FR-016a** explicit country-ban exit ramp:
    ```
    // TODO(country-ban-exit-ramp): when adding NonGoogleAuthAdapter for
    // jurisdictions where Google is restricted ... stableId UUID stays.
    // No data migration. Per discussion 2026-06-18.
    ```
  - **FR-020** explicit (encryption tier escalation):
    ```
    // TODO migration на SecureKeyStore из F-CRYPTO когда F-5 готов
    ```
  - **FR-006** explicit (AuthorizedRequestSigner port):
    ```
    // TODO(authorized-request-signer): future port для подписи RPC...
    ```

## Exemptions (intentionally provider-specific)

- [x] **CHK014** Platform integrations не classified как substitutable — ✓.
  - F-4 не treats FCM / APNs / SMS / biometrics как substitutable backend.
  - **FCM** упомянут в decision 2026-05-30/05 как «remains forever as push transport».
  - F-4 не вводит push functionality — это S-4 / consumer-spec territory.
- [x] **CHK015** No needless cross-provider abstraction — ✓.
  - F-4 не создаёт «universal push channel», «cross-platform credential manager», etc.
  - `AuthProvider` port — это **legitimate** abstraction (per CLAUDE.md rule 2 ACL), не bloat. **Current consumer (GoogleSignInAuthAdapter)** существует.

## Cost-of-swap summary

- [x] **CHK016** Cost-of-swap paragraph — ✓ explicit в **TL;DR section**:

> Если завтра Google или Firebase «исчезнут» — мы переписываем **один файл** (adapter), а не весь identity-слой. Это применение правила CLAUDE.md №2 («ACL для каждой внешней зависимости») к авторизации.

**Detailed cost-of-swap**, per `domain-isolation` checklist quantification:

| Provider | Replacement scope | Estimated time | Data migration |
|----------|---------------------|----------------|----------------|
| Firebase Auth (Step 2 of `GoogleSignInAuthAdapter`) | `GoogleSignInAuthAdapter` file + DI binding | 2-3 days | None (stableId = UUID, persists) |
| Credential Manager (Step 1) | `GoogleSignInAuthAdapter` file | 1-2 days | None |
| EncryptedSharedPreferences → SecureKeyStore | `EncryptedLocalSessionStore` file + DI binding | 1 day (additive change) | None (session re-establishable) |
| Firestore identity-links → own-server table | `GoogleSignInAuthAdapter` lookup logic + Firestore Security Rules → own-server endpoint | 1 week (includes one-time data migration script per `SRV-AUTH-IDENTITY-001`) | One-time bulk copy of identity-links to own-server |
| Adding country-ban PhoneAuthAdapter (new jurisdictions) | Net-new `PhoneAuthAdapter` file + DI binding | 2-3 weeks (new adapter, не rewrite) | None (UUID identity-links reusable) |

**Total swap to own-server identity stack**: ~2 weeks работы, no user-visible data loss, no UID migration. This is **the value** of clarification Q1 (UUID stableId).

---

## Strengths (beyond baseline)

### Strength 1: Q1 decision устранил the single biggest one-way door

Pre-clarify version of spec assumed Firebase UID or Google sub claim as stableId. **Owner explicitly rejected** мой recommendation (a) Google sub claim because of country-ban + own-server + PhoneAuthAdapter concerns. The resulting design (UUID + identity-links) — **textbook one-way door avoidance** per CLAUDE.md rule 3.

### Strength 2: Identity-links collection migrates first

`SRV-AUTH-IDENTITY-001` server-roadmap entry explicitly designates identity-links collection as **first to migrate** в own-server cutover. This makes cutover **predictable**: own-server stands up with identity-links table, then everything else moves piece by piece.

### Strength 3: Multiple inline TODOs со exit destinations

Spec has 4 distinct `// TODO(...)` markers:
- `TODO(server-roadmap)` — Firebase exchange step replacement.
- `TODO(auth-provider-extensions)` — future Phone/Email/Apple adapters.
- `TODO(country-ban-exit-ramp)` — non-Google adapter for restricted jurisdictions.
- `TODO(authorized-request-signer)` — future RPC signing port.

Каждый — **specific exit destination**, не «как-нибудь потом». Это canonical application of CLAUDE.md rule 3 + 8.

---

## Open items (для plan stage)

1. **Add to `docs/dev/server-roadmap.md`**: `SRV-AUTH-IDENTITY-001` (identity-links collection migrates first, UUID stableId persists).
2. **Identity-links schemaVersion**: FR-016b (already raised by wire-format checklist).
3. **Identity-links roundtrip test**: SC-009 расширить или новый SC.
4. **Identity-links Firestore Security Rules** explicit в plan.md (already raised by security + tamper-resistance).

---

## Verdict

**15/16 ✓, 1 partial.** F-4 — **canonical** backend substitution-ready spec. Все rules 1, 2, 5, 8 applied; one-way doors avoided (Q1); cost-of-swap explicit (TL;DR + table above); multiple inline TODOs со specific exit destinations.

**The one partial** (CHK005 — schemaVersion для identity-links) — это plan-stage open item, не fundamental design issue.

---

## Что это значит простыми словами

Спека правильно готовится к **переезду на собственный сервер**:
- Все vendor-specific детали (Firebase, Google) сидят **в одном файле** (`GoogleSignInAuthAdapter`). Если завтра переезжаем на свой сервер — переписываем один файл.
- **Идентификатор пользователя** — наш собственный UUID, не Firebase UID. Это значит, что переезд **не требует** менять идентификаторы в базе данных. Все ссылки `pair → user`, `config → owner`, `device → user` продолжают работать.
- **Маппинг Google → наш UUID** живёт в отдельной таблице `/identity-links/...` на Firestore. При переезде эта таблица — **первая**, что мигрирует на собственный сервер (`SRV-AUTH-IDENTITY-001`).
- **Country-ban готовность**: если завтра в стране запретят Google, добавляем `PhoneAuthAdapter` (вход по телефону), который **переиспользует** ту же таблицу identity-links (просто с другим providerKind = phone). UUID идентификаторы остаются. Все пользовательские данные продолжают работать.
- **Стоимость переезда** прозрачно посчитана в спеке: ~2 недели работы на собственный сервер, никаких потерь пользовательских данных.

**1 уточнение для plan'а**: добавить версию схемы в Firestore документ identity-links (это уже отмечено в чек-листе wire-format).
