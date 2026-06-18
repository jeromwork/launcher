# Contract: Firestore Security Rules (F-4 portion)

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Date**: 2026-06-18
**Type**: Server-side access control configuration.
**File location**: `firestore/firestore.rules` (existing repo file — F-4 adds rules section).

---

## Purpose

Firestore Security Rules enforce defense-in-depth для identity-links + users collections. Even if client code (`GoogleSignInAuthAdapter`) глючит, Security Rules prevent:
- Spoofing другого пользователя (write `/identity-links/google/{other_sub}`).
- Overwrite existing identity-link (changing `stableId` to other UUID).
- Reading other users' identity-links.
- Direct writes к `/users/{stableId}` без identity-link establishment.

## Rules text (F-4 additions)

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // =========================================================================
    // identity-links collection — providerKind = "google" subpath (MVP F-4)
    // =========================================================================
    match /identity-links/google/{providerAccountId} {

      // READ: only the owner of the Google account may read their own link
      allow read: if request.auth != null
                  && request.auth.uid == providerAccountId;

      // CREATE: only owner may create, only if document does NOT exist, with exact schema
      allow create: if request.auth != null
                    && request.auth.uid == providerAccountId
                    && !exists(/databases/$(database)/documents/identity-links/google/$(providerAccountId))
                    && request.resource.data.keys().hasOnly(['schemaVersion', 'stableId', 'createdAt'])
                    && request.resource.data.schemaVersion == 1
                    && request.resource.data.stableId is string
                    && request.resource.data.stableId.size() == 36   // UUID v4 length
                    && request.resource.data.stableId.matches('^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')   // UUID v4 regex
                    && request.resource.data.createdAt == request.time;   // server timestamp

      // UPDATE: never allowed (immutable after creation)
      allow update: if false;

      // DELETE: only via Cloud Function for S-6 Account Deletion (post-MVP)
      // For F-4 phase: forbidden client-side
      allow delete: if false;
    }

    // =========================================================================
    // identity-links collection — future provider subpaths (not active в F-4)
    // =========================================================================
    // /identity-links/phone/{phoneE164}   — added by future PhoneAuthAdapter spec
    // /identity-links/email/{emailAddress} — added by future EmailAuthAdapter spec
    // /identity-links/apple/{appleSubject} — added by V-1 iOS launcher spec
    // /identity-links/sso/{ssoSubject}     — added by future SSO spec
    //
    // Same pattern: read if uid == accountId, create-only-if-not-exists, no update/delete.
    // These match blocks added BY their respective spec'и (additive change).


    // =========================================================================
    // users collection — keyed by наш stableId UUID
    // =========================================================================
    match /users/{stableId} {

      // F-4 only creates пустой document с базовыми fields во время identity-link establishment.
      // F-5 (ConfigCipher) и S-8 (sync) добавят свои fields через own match blocks (additive в их specs).

      // CREATE: only as part of identity-link establishment transaction
      // (transaction guarantees: simultaneous create к both /identity-links and /users)
      // Verification: caller must have just-created identity-link с matching stableId
      allow create: if request.auth != null
                    && existsAfter(/databases/$(database)/documents/identity-links/google/$(request.auth.uid))
                    && getAfter(/databases/$(database)/documents/identity-links/google/$(request.auth.uid)).data.stableId == stableId
                    && request.resource.data.keys().hasOnly(['schemaVersion', 'stableId', 'createdAt'])
                    && request.resource.data.schemaVersion == 1
                    && request.resource.data.stableId == stableId
                    && request.resource.data.createdAt == request.time;

      // READ: only the owner of the matching identity-link
      // Translation: the user whose Google sub claim's identity-link points к этот stableId
      allow read: if request.auth != null
                  && exists(/databases/$(database)/documents/identity-links/google/$(request.auth.uid))
                  && get(/databases/$(database)/documents/identity-links/google/$(request.auth.uid)).data.stableId == stableId;

      // UPDATE: F-4 doesn't allow update. F-5 (identityKeys) and S-8 (config) will add update rules.
      allow update: if false;   // overridden by future spec'и

      // DELETE: only via Cloud Function для S-6 Account Deletion
      allow delete: if false;
    }
  }
}
```

## Defense-in-depth analysis

### Spoofing attack

**Attacker scenario**: Eve вошла со своим Google account (sub = "E"). Tries to create `/identity-links/google/V` (Victim's sub) с `stableId = Eve_own_uuid`.

**Defense layers**:
1. **Client adapter** (`GoogleSignInAuthAdapter`) extracts Eve's sub claim → tries to lookup `/identity-links/google/E`, не `/V`. Eve would need to bypass client code.
2. **Firestore Security Rules CREATE**: `request.auth.uid == providerAccountId` — Eve's `auth.uid == "E"`, `providerAccountId == "V"` → mismatch → REJECT.
3. **Even with bypassed client code**, Firestore rejects the request.

### Overwrite attack

**Attacker scenario**: Victim's identity-link уже создан (`stableId = X`). Eve has access к Victim's Firebase auth token (через various leak scenarios) and tries to update `/identity-links/google/V` to `stableId = Eve_own_uuid` — что позволило бы Eve impersonate Victim в delegations и в config-sync.

**Defense layers**:
1. **Firestore Security Rules UPDATE: `false`** → REJECT regardless of auth.
2. **CREATE rule** also rejects (document exists check).

### Read leak

**Attacker scenario**: Eve queries `/identity-links/google/V` для discovery Victim's UUID, then potentially use UUID для запросов к Victim's `/users/{V_uuid}`.

**Defense layers**:
1. **Read rule**: `request.auth.uid == providerAccountId` → Eve's uid != V → REJECT.

### Direct users collection access

**Attacker scenario**: Eve узнала Victim's UUID (через some other leak), tries to read `/users/{V_uuid}` directly.

**Defense layers**:
1. **users READ rule**: cross-references identity-link. Eve's auth.uid leads к `/identity-links/google/E` → `stableId = Eve_uuid`, not `V_uuid` → REJECT.

### Race создания между two devices same account

**Scenario**: Victim signs in одновременно с two devices с same Google account.

**Defense layers**:
1. **Client transaction** (research.md §R9): atomic check-and-create — only one transaction commits, other gets ABORTED → retries → finds existing document.
2. **Firestore Security Rules CREATE: `!exists(...)`** — second transaction's create rejected even если client retry logic broken.

## Tests

Required Firestore Rules tests (using `@firebase/rules-unit-testing`):

```javascript
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');

describe('identity-links/google', () => {
  // ... setup ...

  test('owner can read own identity-link', async () => {
    const aliceAuth = testEnv.authenticatedContext('alice_sub_claim');
    await assertSucceeds(
      aliceAuth.firestore().doc('identity-links/google/alice_sub_claim').get()
    );
  });

  test('user cannot read other identity-link', async () => {
    const aliceAuth = testEnv.authenticatedContext('alice_sub_claim');
    await assertFails(
      aliceAuth.firestore().doc('identity-links/google/bob_sub_claim').get()
    );
  });

  test('owner can create identity-link if not exists', async () => {
    const aliceAuth = testEnv.authenticatedContext('alice_sub_claim');
    await assertSucceeds(
      aliceAuth.firestore().doc('identity-links/google/alice_sub_claim').set({
        schemaVersion: 1,
        stableId: '550e8400-e29b-41d4-a716-446655440000',
        createdAt: firebase.firestore.FieldValue.serverTimestamp(),
      })
    );
  });

  test('cannot create identity-link если document уже exists (no overwrites)', async () => {
    // ... pre-seed existing document ...
    const aliceAuth = testEnv.authenticatedContext('alice_sub_claim');
    await assertFails(
      aliceAuth.firestore().doc('identity-links/google/alice_sub_claim').set({
        schemaVersion: 1,
        stableId: 'malicious-different-uuid',
        createdAt: firebase.firestore.FieldValue.serverTimestamp(),
      })
    );
  });

  test('cannot create identity-link для other sub claim', async () => {
    const aliceAuth = testEnv.authenticatedContext('alice_sub_claim');
    await assertFails(
      aliceAuth.firestore().doc('identity-links/google/bob_sub_claim').set({
        schemaVersion: 1,
        stableId: '550e8400-e29b-41d4-a716-446655440000',
        createdAt: firebase.firestore.FieldValue.serverTimestamp(),
      })
    );
  });

  test('cannot create with extra fields', async () => {
    const aliceAuth = testEnv.authenticatedContext('alice_sub_claim');
    await assertFails(
      aliceAuth.firestore().doc('identity-links/google/alice_sub_claim').set({
        schemaVersion: 1,
        stableId: '550e8400-e29b-41d4-a716-446655440000',
        createdAt: firebase.firestore.FieldValue.serverTimestamp(),
        extra_field: 'malicious data',
      })
    );
  });

  test('cannot update identity-link (immutable)', async () => {
    // ... pre-seed ...
    const aliceAuth = testEnv.authenticatedContext('alice_sub_claim');
    await assertFails(
      aliceAuth.firestore().doc('identity-links/google/alice_sub_claim').update({
        stableId: 'malicious-new-uuid',
      })
    );
  });

  test('cannot delete identity-link', async () => {
    const aliceAuth = testEnv.authenticatedContext('alice_sub_claim');
    await assertFails(
      aliceAuth.firestore().doc('identity-links/google/alice_sub_claim').delete()
    );
  });
});

describe('users collection', () => {
  test('cannot create users doc без matching identity-link', async () => {
    const aliceAuth = testEnv.authenticatedContext('alice_sub_claim');
    // No identity-link for Alice yet
    await assertFails(
      aliceAuth.firestore().doc('users/550e8400-e29b-41d4-a716-446655440000').set({
        schemaVersion: 1,
        stableId: '550e8400-e29b-41d4-a716-446655440000',
        createdAt: firebase.firestore.FieldValue.serverTimestamp(),
      })
    );
  });

  test('owner can read own users doc (через identity-link cross-ref)', async () => {
    // ... pre-seed identity-link ...
    const aliceAuth = testEnv.authenticatedContext('alice_sub_claim');
    await assertSucceeds(
      aliceAuth.firestore().doc('users/aliceUuid').get()
    );
  });

  test('cannot read other users doc', async () => {
    const aliceAuth = testEnv.authenticatedContext('alice_sub_claim');
    await assertFails(
      aliceAuth.firestore().doc('users/bobUuid').get()
    );
  });
});
```

Run rules tests:
```powershell
cd firestore-rules-tests/
npm test
```

## Deployment

```powershell
# Deploy rules к Firebase project
firebase deploy --only firestore:rules

# Verify deployed rules
firebase firestore:rules:get
```

CI should run rules tests **before** allowing deploy to production.

## Related

- [contracts/identity-link-v1.md](identity-link-v1.md) — document schema.
- [contracts/session-record-v1.md](session-record-v1.md) — local persistence (different concern).
- [research.md §R9](../research.md#r9-firestore-security-rules--atomic-create-only-для-identity-links) — design rationale.
- [research.md §R4](../research.md#r4-identity-links-firestore-migration-to-own-server) — own-server cutover impact.

## TL;DR для не-разработчика

Firestore Security Rules — это **правила доступа к серверной базе данных**, которые работают на стороне сервера. Даже если клиентский код «взломан» или содержит баги, сервер не позволит сделать запрещённую операцию.

**Что защищаем**:
1. Никто не может **прочитать чужую запись** identity-link.
2. Никто не может **создать запись для чужого Google аккаунта** (подмена пользователя).
3. Никто не может **изменить существующую запись** — стабильный UUID гарантированно не подменяется.
4. Никто не может **удалить запись** напрямую (только через специальную функцию удаления аккаунта).
5. Никто не может **создать запись с лишними полями** (например, с маркером «я админ»).

**Защита от гонки** (два устройства одного пользователя входят одновременно): транзакция Firestore гарантирует, что только одна операция успешна — вторая видит существующую запись и берёт её UUID.

**Тесты**: ~10 автоматических проверок на CI, которые проверяют все вышеперечисленные сценарии. Деплой правил без прохождения тестов запрещён.

**Развёртывание**: команда `firebase deploy --only firestore:rules` (admin задача).
