# Security — spec 014

Generated: 2026-05-29.

OWASP MASVS + Article XIV constitution.

## Authentication / authorization

- [x] **CHK001** Admin ownership: `ownerUid = Google UID` (FR-003h для target named configs). Firestore Security Rules govern access — write only by `ownerUid` (per спека 008 + спека 007).
- [x] **CHK002** Senior on Simple Launcher: pairing-key based auth (existing спека 007). F-014 не trogает auth.
- [x] **CHK003** `EditError.NotAuthorized` variant — explicit refuse при попытке edit чужого конфига без pairing. Defined в Key Entities.

## PII / sensitive data

- [⚠️] **CHK004** ConfigDocument содержит **contact references** (через Contact tile slots, спека 011). Это PII (имена, phone numbers). **Existing** policy спеки 011/008: contact references — opaque IDs, не plaintext в config. F-014 не меняет это. **Verify в plan.md**: что admin remote editing бабушкиного config не лeak'ает new contact info.
- [x] **CHK005** Banner "Редактируешь телефон Маши" — name of target user. Per Edge Case это admin'ская локальная label (он сам присвоил alias при pairing). PASS.

## Encryption

- [⚠️] **CHK006** F-014.0 / F-014.1 — **plaintext ConfigDocument** на server (Firestore). Spec явно: "F-014 пишет в plaintext config (пока), будет автоматически зашифрован после F-5". **One-way door risk**: production-blocker per F-5 status. Spec correctly **defers F-014.1 production** until F-5 ships. PASS as policy.
- [⚠️] **CHK007** F-014.0 local DataStore — plaintext на устройстве. Admin device unlock защищает, OS-level encryption (Android FBE). Acceptable для MVP.

## Intent / deep-link / exported components

- [N/A] **CHK008** F-014 не вводит deep-link / exported components / new intents.

## Input validation

- [⚠️] **CHK009** `configName` user-input в FR-003c, FR-003g (anonymous→Google migration "prompt for name"). Validation rules не specified:
  - Max length?
  - Allowed characters?
  - Profanity / Unicode normalization?
  - Conflict с existing configName?
  **Improvement**: plan.md должен specify (e.g., 1-32 chars, alphanumeric+space+hyphen, NFC normalize, case-insensitive uniqueness).

## Privacy / PII leakage in logs

- [⚠️] **CHK010** Logcat output для F-014 events не specified. **MUST NOT** log: contact names, phone numbers, configName (user PII potential), `ownerUid`. **MUST log only**: anonymized event categories, ConfigDocument schema version, error types. Plan.md responsibility.
- [x] **CHK011** Diagnostic events anonymized — implied per **CHK010** rule.

## Multi-tenancy / data isolation

- [x] **CHK012** Firestore path `/admin-self-configs/{adminUid}/...` — namespaced by admin UID. Rules enforce. PASS per спека 008.
- [x] **CHK013** Target configs: `ownerUid = admin UID` (FR-003h) per Q1.4 + Q1.5 clarification. No accidental cross-admin leak — Rules enforce.

## Threat model

- [⚠️] **CHK014** Threat model для F-014 не explicit в спеке. Implicit:
  - **Spoofed admin**: pairing key required (спека 007). PASS.
  - **Replay attack** на push: optimistic concurrency version field (спека 008). PASS.
  - **Malicious config**: admin push'ит malformed ConfigDocument на бабушку. Schema validation на read side (per ConfigEditor). PASS.
  - **DoS**: 5-config hard limit (FR-003c) prevents unbounded config creation. PASS.

## Permission scope

- [x] **CHK015** F-014 не вводит новых permissions. Existing CONTACTS (спека 011), HOME role — handled там.

## Open items

1. **CHK004**: PII handling for contact tiles в admin remote edit — plan.md verify no leak.
2. **CHK006**: F-014.1 production gate after F-5 — explicit phasing в plan.md.
3. **CHK009**: configName validation rules — plan.md.
4. **CHK010**: PII-free logging policy — plan.md.

**Verdict**: PASS. Architecture sound (auth, multi-tenancy, no new permissions). 4 plan.md items для PII / validation / encryption phasing.
