# Contract: Identity-link Firestore document v1

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Date**: 2026-06-18
**Type**: Server-side wire format (Firestore document).
**Source**: FR-016a (added в clarify pass 2026-06-18 per clarification Q1).
**Server location**: Firestore collection path `/identity-links/{providerKind}/{providerAccountId}`.

---

## Purpose

`identity-link` document — **server-side mapping** «провайдер-аккаунт → наш UUID». Это ключевая структура, которая делает identity provider-agnostic и country-ban-готовой.

Без этой мапы `stableId` пришлось бы хардкодить как Google `sub` claim или Firebase UID — что привязало бы нас к Google навсегда. С мапой — можем добавить PhoneAuthAdapter / EmailAuthAdapter / OwnServer adapter без миграции UUID.

## Path structure

```
/identity-links/
    google/
        {google_sub_claim}    # e.g., "108572394857283745728"
    phone/                      # future, post-MVP
        {phone_e164}            # e.g., "+71234567890"
    email/                      # future, post-MVP
        {email_address}         # e.g., "anna@example.com"
    apple/                      # future, iOS launcher V-1
        {apple_subject}
    sso/                        # future, enterprise
        {sso_subject}
```

В MVP F-4 — **только** `google/` subpath заполняется.

## Document schema v1

```json
{
  "schemaVersion": 1,
  "stableId": "550e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2026-06-18T14:23:00.000Z"
}
```

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `schemaVersion` | Number | Yes | Always 1 в v1. |
| `stableId` | String | Yes | UUID v4 (36 chars). Наш собственный identifier. |
| `createdAt` | Timestamp | Yes | Firestore Timestamp. Когда identity-link был создан (= когда пользователь впервые signed in с этим аккаунтом). |

**Note**: providerAccountId (Google sub claim) — **в пути document'а**, не внутри document'а. Это позволяет direct lookup `firestore.document("/identity-links/google/$sub").get()` без queries.

## Lifecycle

| Phase | Trigger | Action |
|-------|---------|--------|
| **Create** | First sign-in нового Google account | Generate UUID v4, atomic transaction: `create /identity-links/google/{sub}` + `create /users/{uuid}` |
| **Read** | Every subsequent sign-in | Lookup by `{sub}` → return `stableId` |
| **Update** | Never | Forbidden by Security Rules (immutable after creation) |
| **Delete** | Account deletion (S-6 spec, post-MVP) | Only через Cloud Function (server-side admin), не client-side |

## Atomic create-or-lookup

Per [research.md §R9](../research.md#r9-firestore-security-rules--atomic-create-only-для-identity-links):

```kotlin
private suspend fun lookupOrCreateIdentityLink(googleSubClaim: String): String {
    firestore.runTransaction { transaction ->
        val docRef = firestore.document("/identity-links/google/$googleSubClaim")
        val snapshot = transaction.get(docRef)

        if (snapshot.exists()) {
            // Existing: return stableId
            snapshot.getString("stableId")!!
        } else {
            // New: generate UUID, create both /identity-links and /users
            val newUuid = UUID.randomUUID().toString()
            transaction.set(docRef, mapOf(
                "schemaVersion" to 1,
                "stableId" to newUuid,
                "createdAt" to FieldValue.serverTimestamp(),
            ))
            transaction.set(firestore.document("/users/$newUuid"), mapOf(
                "schemaVersion" to 1,
                "stableId" to newUuid,
                "createdAt" to FieldValue.serverTimestamp(),
            ))
            newUuid
        }
    }
}
```

**Race protection**: if two devices sign in одновременно with same Google account, only one transaction succeeds. Other gets `FirebaseFirestoreException.code = ABORTED` → caller retries (transaction docs recommend ≤ 5 retries). Second retry sees existing document → returns same UUID.

## Security Rules

См. [contracts/firestore-security-rules.md](firestore-security-rules.md) для полного текста rules.

Ключевые ограничения:
- **Read**: only if `request.auth.uid == providerAccountId` (only the owner of Google account can read their identity-link).
- **Create**: only if `request.auth.uid == providerAccountId` AND document does NOT exist AND fields exactly `[schemaVersion, stableId, createdAt]` AND `schemaVersion == 1` AND `stableId is UUID format`.
- **Update / Delete**: forbidden.

Это defense-in-depth: даже если transaction в коде глючит, Security Rules не позволят перезаписать existing link на другой UUID.

## Tests

### Required tests

- **Roundtrip test** (instrumentation): write document к Firestore Emulator → read back → assert fields match.
- **Race condition test**: parallel coroutines call `lookupOrCreateIdentityLink(sameSub)` → assert only one UUID returned (all callers).
- **Security Rules test** (`firestore-rules-test`):
  - Authenticated as Sub A → can read `/identity-links/google/A`. ✅
  - Authenticated as Sub A → cannot read `/identity-links/google/B`. ❌
  - Authenticated as Sub A → can create `/identity-links/google/A` if not exists. ✅
  - Authenticated as Sub A → cannot create `/identity-links/google/B` (uid mismatch). ❌
  - Cannot create with extra fields (`extra_field`). ❌
  - Cannot update existing. ❌
  - Cannot delete (except via Cloud Function). ❌

### Test fixtures

Location: `core/commonTest/resources/auth-fixtures/`:
- `identity-link-google-v1.json` — golden document для backward-compat read.

## Future schema bumps

### v2 hypothetical (additive — adding field)

Если, например, нужно добавить `lastSignInAt: Timestamp`:

1. Bump `schemaVersion = 2`.
2. Update Security Rules to allow optional new field.
3. App reads v1 documents с missing field → null default.
4. Updates write v2 documents (через explicit migration step или naturally over time).

### v2 hypothetical (breaking — restructuring)

Если, например, переместить `providerAccountId` внутрь document (вместо path):

1. Bump `schemaVersion = 2`.
2. **Migration script** (server-side admin task): batch read all v1 documents, transform, write v2. NO client involvement.
3. App version N+1 reads only v2 format.
4. Document breaking change в `docs/dev/server-roadmap.md`.

## Migration к own-server (cutover)

Per [research.md §R4](../research.md#r4-identity-links-firestore-migration-to-own-server):

| Phase | What happens к identity-links |
|-------|------------------------------|
| **Phase 1** | Own-server stands up identity-links endpoint (read-only mirror, sync via Firestore Trigger или batch copy). |
| **Phase 2** | Dual-write: client writes к both Firestore AND own-server. Reads from Firestore (source of truth). |
| **Phase 3** | Switch read source: app version N+1 reads from own-server. Firestore = backup. |
| **Phase 4** | App version N+2 writes only к own-server. |
| **Phase 5** | Firestore identity-links collection archived (after grace period). |

**Crucial**: `stableId` UUID **does not change** через migration. All `/users/{stableId}` documents, all `/delegations/{ownerStableId}/...` records, all encrypted blobs — continue working without modification.

Server-roadmap entry: `SRV-AUTH-IDENTITY-001`.

## Related

- [contracts/auth-provider-port.md](auth-provider-port.md) — domain port consuming this mapping.
- [contracts/firestore-security-rules.md](firestore-security-rules.md) — access control rules.
- [contracts/session-record-v1.md](session-record-v1.md) — local persistence (separate concern).
- [research.md §R1](../research.md#r1-id-token-sub-claim-extraction-для-identity-links-lookup) — Google sub claim extraction.
- [research.md §R4](../research.md#r4-identity-links-firestore-migration-to-own-server) — migration plan.
- [research.md §R9](../research.md#r9-firestore-security-rules--atomic-create-only-для-identity-links) — race + spoofing protection.
- Server-roadmap entry: `SRV-AUTH-IDENTITY-001`.

## TL;DR для не-разработчика

`identity-link` — это **запись на сервере Firestore**, которая связывает Google аккаунт пользователя с нашим UUID.

**Структура**:
- Путь к записи = `google/{Google_аккаунт}` (например, `google/108572394857283745728`).
- Содержимое = `{ schemaVersion: 1, stableId: "наш UUID", createdAt: "когда создан" }`.

**Lifecycle**:
- **Создаётся один раз** при первом входе пользователя.
- **Только читается** при последующих входах.
- **Никогда не меняется и не удаляется** (только через специальную функцию удаления аккаунта).

**Защита**:
- Прочитать свою запись может только владелец Google аккаунта.
- Записать может только владелец и только если записи ещё нет (нельзя подменить чужую).
- Эта защита проверяется правилами Firestore (server-side), а не доверяется клиенту.

**Защита от гонки** (два устройства одного аккаунта входят одновременно): используется атомарная транзакция Firestore — выигрывает первая, вторая видит существующую запись и берёт её UUID.

**Главное архитектурное свойство**: этот UUID **не меняется** при переезде на собственный сервер. Все остальные данные пользователя (конфиг, делегирования, ключи) продолжают работать без миграции.
