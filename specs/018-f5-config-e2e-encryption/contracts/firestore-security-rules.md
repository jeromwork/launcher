# Contract: Firestore Security Rules для F-5

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **FRs**: FR-009, FR-028a (schema downgrade protection) | **Research**: [R-3](../research.md#r-3-firestore-security-rules-для-usersuidrecovery-key)

Security Rules для двух коллекций, которые F-5 touches:
- `users/{uid}/recovery-key/...` — encrypted RootKey vault (owned by F-5).
- `users/{uid}/config/...` — encrypted ConfigDocument (owned by spec 008, F-5 только пишет SealedConfig).

---

## Full ruleset

```javascript
// firebase/firestore.rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // F-5: Recovery key vault — strict owner-only access + shape validation + downgrade protection
    match /users/{uid}/recovery-key/{docId} {
      allow read: if request.auth != null
                  && request.auth.uid == uid;

      allow create: if request.auth != null
                    && request.auth.uid == uid
                    && isValidRecoveryVaultBlob(request.resource.data);

      // FR-028a (H-2 mitigation): schemaVersion can only monotonically increase
      // This blocks downgrade attacks where stolen Google credentials are used
      // to replace v2+ vault with vulnerable v1 vault.
      allow update: if request.auth != null
                    && request.auth.uid == uid
                    && isValidRecoveryVaultBlob(request.resource.data)
                    && request.resource.data.schemaVersion >= resource.data.schemaVersion;

      allow delete: if request.auth != null
                    && request.auth.uid == uid;
    }

    // F-5 (consumer-side): Encrypted ConfigDocument — strict owner-only + downgrade protection
    // Note: detailed schema validation для config — territory spec 008.
    match /users/{uid}/config/{docId} {
      allow read, create, delete: if request.auth != null
                                  && request.auth.uid == uid;

      // FR-028a (H-2 mitigation): schemaVersion can only monotonically increase
      allow update: if request.auth != null
                    && request.auth.uid == uid
                    && request.resource.data.schemaVersion >= resource.data.schemaVersion;
    }
  }
}

// --- Helpers ---

function isValidRecoveryVaultBlob(data) {
  return data.schemaVersion is int
      && data.schemaVersion >= 1
      && data.algorithm is string
      && data.algorithm.size() > 0
      && data.algorithm.size() <= 64
      && data.wrappedRootKey is bytes
      && data.wrappedRootKey.size() > 0
      && data.wrappedRootKey.size() <= 1024
      && data.kdfSalt is bytes
      && data.kdfSalt.size() == 16
      && data.nonce is bytes
      && data.nonce.size() == 24
      && data.kdfParams is map
      && data.kdfParams.memoryKiB is int
      && data.kdfParams.iterations is int
      && data.kdfParams.parallelism is int
      && data.createdAt is int;
}
```

---

## Threat model coverage

| Threat                                                     | Rule mitigation                                                                                          |
|------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| User A читает recovery vault User B                        | `request.auth.uid == uid` — отказ                                                                        |
| Анонимный клиент (без auth) читает любой vault              | `request.auth != null` — отказ                                                                           |
| Authenticated user пишет recovery vault для **чужого** UID  | `request.auth.uid == uid` в `create/update` — отказ                                                      |
| Malicious client пишет invalid shape (DoS на парсер)        | `isValidRecoveryVaultBlob(...)` — отказ                                                                  |
| Wrong `schemaVersion` (≤ 0)                                 | `data.schemaVersion >= 1` — отказ                                                                        |
| Huge `wrappedRootKey` (storage abuse)                       | `data.wrappedRootKey.size() <= 1024` — отказ                                                             |
| Salt длины не 16 (попытка нарушить R-1)                     | `data.kdfSalt.size() == 16` — отказ                                                                      |
| **Schema-version downgrade (H-2)** — атакующий с украденными credentials заменяет v2 vault на v1 с известной weakness | `request.resource.data.schemaVersion >= resource.data.schemaVersion` в `update` — отказ (FR-028a, WhatsApp E2E backup pattern) |

**НЕ покрыто rules (out of scope F-5)**:
- Server-side rate-limit на passphrase brute-force (scrape + offline crack). Отложено в [SRV-RECOVERY-001](../../docs/dev/server-roadmap.md). В MVP — только Argon2id memory-hardness.
- Server-side audit log кто читал recovery vault. Отложено в SRV-AUDIT-002 (future).

---

## Deploy

```bash
cd c:/work/launcher/firebase

# Local emulator подхватывает rules автоматически:
firebase emulators:start --only firestore,auth

# Production деплой (требует Firebase CLI auth):
firebase deploy --only firestore:rules --project <PROD_PROJECT_ID>
```

---

## Tests required

- **Rules unit-tests** через `@firebase/rules-unit-testing` (JS, в `firebase/test/`):
  - User A try-read User B's recovery-key → reject.
  - Anonymous try-read → reject.
  - Owner write valid blob → allow.
  - Owner write invalid blob (missing field, wrong size) → reject.
  - Owner read existing blob → allow.
  - Owner delete → allow.
  - **H-2 protection tests** (FR-028a):
    - Owner update with `schemaVersion = current` → allow (re-encryption with same version).
    - Owner update with `schemaVersion > current` → allow (migration to new version).
    - Owner update with `schemaVersion < current` → reject (downgrade attempt blocked).
    - Same tests для `users/{uid}/config/{docId}`.
- Integration test через эмулятор (см. quickstart.md §3).

---

## Краткое резюме

Правила Firestore для F-5: только **владелец UID** может читать/писать/удалять свой recovery-blob и config-blob. На запись валидируется shape — schemaVersion ≥ 1, алгоритм-строка не пустая, wrappedRootKey ≤ 1024 байт, salt ровно 16 байт, nonce ровно 24 байт, kdfParams — map с тремя int-полями. **На update — schemaVersion может только monotonically increase** (FR-028a, защита от downgrade attack по WhatsApp E2E backup pattern). Это блокирует случайный мусор, accidental schema-version 0, и downgrade-атаку с украденными credentials. Защита от brute-force на сервере (rate-limit) и audit-log — пока **не реализованы**, отложены в server-roadmap; в MVP единственная защита от offline-cracking — memory-hardness Argon2id + client-side TOLU (FR-028b) как defence-in-depth поверх Firestore Rule.
