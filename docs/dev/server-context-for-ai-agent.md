# Server-side context briefing for another AI agent

**Purpose**: единый дамп для AI-агента, с которым владелец будет обсуждать дизайн собственного backend'а. НЕ для разработчиков-людей (для них — [server-roadmap.md](server-roadmap.md) + [project-backlog.md](project-backlog.md)). Документ — производный, не источник правды; при расхождении правда в server-roadmap.md.

**Snapshot date**: 2026-06-26.

---

## 0. Что такое launcher

Android-приложение типа **launcher** (replacement for Pixel/Samsung home screen) для **пожилых пользователей**. Архитектура: **two-sided** — Managed device (на руках у пожилого) + Admin device (родственник/врач/IT-support удалённо настраивает Managed). Связаны через **pairing** (QR) + future Google Sign-In identity.

Code base: Kotlin Multiplatform (KMP) — `commonMain` domain + ports; `androidMain` adapters. Стек: Compose UI, Koin DI, Firebase (Firestore + FCM + Auth), Cloudflare Worker для server-touchpoints, Backblaze B2 для blob storage.

**Engineering rules** (`CLAUDE.md`):
- §1 domain isolated from infrastructure (vendor SDK не в domain),
- §2 ACL для каждой external dependency,
- §3 one-way doors с exit ramp,
- §4 MVA (минимум абстракций),
- §5 wire-format с `schemaVersion`,
- §6 mock-first (fake adapters в `core/*/fake`),
- §8 каждый client-side workaround = запись в server-roadmap.

---

## 1. Текущая серверная инфраструктура (что есть прямо сейчас)

| Слой | Что используется | Назначение |
|---|---|---|
| **API surface** | Cloudflare Worker `launcher-push.<account>.workers.dev` | Один endpoint `POST /push` (generic push trigger). Free tier 100k req/day. |
| **Push delivery** | FCM (Firebase Cloud Messaging) HTTP v1 API | Worker → FCM → Android device. |
| **Auth (identity)** | Firebase Anonymous Auth, переезжает на Google Sign-In (TASK-3 / spec 017 F-4 уже реализован) | UID stableId. Identity-links `google sub → stableId UUID` в Firestore. |
| **Database** | Firestore (NoSQL, document-oriented) Spark plan (free) | `/users/{uid}/data/{key}` (envelopes), `/users/{uid}/devices/{deviceId}` (public keys), `/identity-links/google/{sub}` (auth mapping), `/users/{uid}/grants/{helperUid}` (access grants). |
| **Blob storage** | Backblaze B2 `launcher-private-media-eastclinic` (S3-compatible, 10GB free, EU Central) | Зашифрованные медиа-blob'ы. Доступ через Worker proxy `/blobs/{linkId}/{uuid}` с Firebase ID-token auth + link-membership check. |
| **Authorization** | Firestore Security Rules + Worker JWT verification (jose lib) | Owner-only writes, grant-holders read, JWKS dynamic TTL. |
| **Rate limiting** | Cloudflare Workers KV (per-eventType: config-updated 60/min, sos-triggered 10/min) | Persistent rate-limit + idempotency dedupe (10-min TTL). |
| **Cron / background jobs** | **НЕТ** (Spark plan не имеет Cloud Functions) | Все housekeeping client-side. |
| **Audit log** | **НЕТ** | Запланирован SRV-SEC-002. |
| **Custom domain** | **НЕТ** (`*.workers.dev`) | TODO-ARCH-001. |
| **Staging environment** | **НЕТ** (только dev) | SRV-DEV-001. |

### Существующий Worker code

`workers/push/src/` (TypeScript, Cloudflare Worker):
- `auth/` — JWT verification (Firebase ID-token, jose library, JWKS cache).
- `contract/` — wire-format DTOs.
- `dedupe/` — idempotency через Workers KV.
- `dispatch/` — FCM dispatcher (Firebase Admin REST API через service account JWT).
- `ratelimit/` — KV-based rate limiter.
- `recipient/` — recipient resolution (Firestore lookup `users/{uid}/devices` + `users/{uid}/grants`).
- `registry/` — EventTypeRegistry (whitelist eventType, per-event authz rule + rate limit).

`workers/_shared/auth-jwt/` — standalone JWT verification module, готов к extraction (см. SRV-PUSH-EXTRACTION).

---

## 2. Что launcher ждёт от сервера — функциональные требования

### 2.1 Auth + identity

- `POST /auth/google-signin` — принимает Google ID Token, верифицирует через Google Token Info API / JWKS, выпускает наш session JWT с claim `stableId`.
- **Identity-links table**: глобально-уникальный mapping `(providerKind, providerAccountId) → stableId UUID`. Atomic lookup-or-create (race-safe). При вернувшемся пользователе — тот же UUID, иначе ломается delegation/pair-key.
- **Custom claim `googleSub`** для downstream rules (защита от Firebase SDK UID generation strategy changes).
- **Источник**: SRV-AUTH-001, SRV-AUTH-IDENTITY-001, SRV-AUTH-IDENTITY-002. Спека: 017 (F-4 AuthProvider, реализована).

### 2.2 Envelope storage (encrypted config data)

REST API:
```
PUT  /users/{namespace}/data/{key}      ← envelope JSON body
GET  /users/{namespace}/data/{key}      ← returns envelope JSON
LIST /users/{namespace}/data?prefix={p} ← returns list of keys
DELETE /users/{namespace}/data/{key}
```

- JWT auth (Bearer token).
- Server-side validation: `schemaVersion` monotonic increase (downgrade defence), envelope shape (Maps/Blobs typed).
- Domain port `EnvelopeStorage` не меняется при миграции — заменяется только adapter (FirestoreEnvelopeStorage → OwnServerEnvelopeStorage).
- **Источник**: SRV-STORAGE-001. Спека: 018 F-5b.

### 2.3 Public Key Directory (per-device X25519 pubkeys + access grants)

REST API:
```
PUT  /users/{uid}/devices/{deviceId}/pub-key      ← X25519 pub bytes
GET  /users/{uid}/devices                          ← list of (deviceId, pubKey) with grant check
GET  /users/{uid}/access-grants                    ← list of grant holders
PUT  /users/{ownerUid}/access-grants/{helperUid}   ← create grant (owner only)
DELETE /users/{ownerUid}/access-grants/{helperUid} ← revoke (atomic)
```

Server enforces:
1. Owner-only write to own pub-key entries.
2. Helper read of owner devices iff non-revoked grant.
3. Atomic grant create/revoke (transactional).

- **Источник**: SRV-PKD-001. Спека: 018 F-5b.

### 2.4 DeviceId allocation (collision-resistant)

Currently client-side (16 random bytes hex). Migration:
```
POST /users/{uid}/devices/allocate   ← server returns unique deviceId
```
Server transactionally reserves deviceId; eliminates collision residual.

Альтернатива (cheaper): server validates client-proposed deviceId на collision при `publishMyDevice`, rejects with 409 Conflict.

- **Источник**: SRV-DEVICEID-001. Спека: 018 F-5b.

### 2.5 Push trigger (generic)

Currently реализовано в Cloudflare Worker `workers/push/`. Migration копирует логику 1:1:
```
POST /push
  Header: Authorization: Bearer <session JWT>
  Header: Idempotency-Key: <UUID v4>
  Body: {
    schemaVersion: 1,
    eventType: "config-updated" | "sos-triggered" | "entitlement-expired" | ...,
    targetScope: "own-devices" | "own-and-grants",
    ownerUid: "<uid>",
    payload: { ...event-specific fields... }
  }
```

Server:
1. Validate JWT.
2. Lookup eventType в registry (whitelist).
3. Per-event authz rule + rate limit.
4. Idempotency dedupe.
5. Recipient resolution (devices + grants).
6. FCM dispatch with `collapse_key: "{eventType}:{ownerUid}:{contextKey}"`.
7. Bounded retry на FCM 429/5xx (3 attempts: 500ms, 2s, 8s).

**9 known consumers**: config-updated (spec 019 F-5c, реализовано), sos-triggered (S-4), health-critical (S-9), pairing (S-2), subscription (S-10), messenger (V-2), album (V-3), caregiver (V-6), config-rewrite (spec 008).

- **Источник**: SRV-PUSH-FOUNDATION. Спека: 019 F-5c, реализована.

### 2.6 Blob storage (encrypted media)

```
POST   /links/{linkId}/blobs/{uuid}       ← upload зашифрованный envelope
GET    /links/{linkId}/blobs/{uuid}       ← download
DELETE /links/{linkId}/blobs/{uuid}       ← cleanup with refcount
PUT    /links/{linkId}/devices/{deviceId}/pubkey
GET    /links/{linkId}/devices/{deviceId}/pubkey
```

- Server-side reference counting через ACID transaction (заменяет client-side `BlobReferenceLedger` SQLite).
- Storage: S3-compatible (B2 → drop-in replacement own S3 / MinIO).
- Encryption stays client-side (e2e не нарушать).
- **Источник**: SRV-CRYPTO-001. Спеки: 011, 012.

### 2.7 Config history + atomic writes

```
POST /users/{uid}/config         ← atomic: new current + old current → history
GET  /users/{uid}/config/current
GET  /users/{uid}/config/history?limit=10
POST /users/{uid}/config/rollback?version={v}
```

- Server-side ACID transaction вместо client-side two-write race.
- Cron retention: cleanup snapshots > 10 per linkId, раз в час.
- Schema transformers: `vN → vCurrent` при чтении старых snapshots.
- App version compatibility check (reject incompatible writes).
- **Источник**: SRV-CONFIG-001, SRV-CONFIG-002, SRV-CONFIG-003, SRV-CONFIG-004. Спеки: 008, 009.

### 2.8 Recovery key vault (single-owner E2E recovery)

```
PUT    /users/{uid}/recovery-vault       ← upload AEAD-encrypted root key blob
GET    /users/{uid}/recovery-vault
POST   /users/{uid}/recovery-vault/attempt  ← atomic counter increment + verify
```

Crit feature: **atomic server-side counter** — server-side rate-limit, который **полностью** закрывает H-1 brute-force: нельзя обойти через Clear App Data, factory reset, root (counter не на устройстве, привязан к UID).

- Schema-version downgrade defence (FR-028a equiv).
- **Источник**: SRV-RECOVERY-001. Спека: 018 F-5.

### 2.9 Push entitlement (subscription)

- `entitlement-expired` event server-internal (нельзя триггернуть от клиента).
- Server-validated JWT, не client-computed flag (anti-tamper).
- **Источник**: spec 015 S-10 (Subscription Server Timer, TASK-15).

### 2.10 GDPR / 152-ФЗ compliance

```
GET    /users/{uid}/export   ← все данные в JSON
DELETE /users/{uid}          ← полное удаление, ≤30 дней
```

- Subject-driven deletion (пожилой может сам удалить, без admin).
- **Источник**: SRV-SEC-003. Backlog: TODO-LEGAL-001 (Play Store blocker).

### 2.11 Audit log

- Все write-операции: UID + timestamp + payload hash. Retention 90 дней.
- **Источник**: SRV-SEC-002.

### 2.12 Admin-to-Managed runtime commands

```
POST /links/{linkId}/commands     ← admin queues command
GET  /links/{linkId}/commands     ← Managed polls (or FCM push)
POST /links/{linkId}/commands/{id}/ack
```

- Redis queue, TTL, retry policy.
- **Источник**: SRV-CMD-001.

### 2.13 NetworkConfigSource (wizard manifests, tile sets, themes)

```
GET /v1/configs/{kind}/{id}    ← signed manifest envelope
```

- Ed25519 signed manifests, client verifies против pinned public key.
- `BundledConfigSource` остаётся как offline fallback.
- **Источник**: SRV-CONFIG-001 (другой, F-3). Спека: 015 F-3.

### 2.14 UserPreferences cloud sync

- Move из локального DataStore в `ConfigDocument.userPreferences` slot.
- **Источник**: SRV-PREFS-001. Спека: 015.

### 2.15 Algorithm migration job (crypto rotation)

WhatsApp E2E backup pattern (3 фазы):
1. **Coexistence**: новые клиенты пишут v2, старые читают v1.
2. **Auto-migration (months)**: при каждом recovery (когда passphrase введён) — re-encrypt в v2.
3. **Deprecation (~12 месяцев)**: v2+ отказывается читать v1, forced app update.

- **Источник**: SRV-CRYPTO-008 (renamed from 007). Спека: 018 F-5.

### 2.16 Forward unsharing (при unpair)

- При unpair триггерить re-encryption всех existing config snapshots под новым CEK без removed recipient.
- **Источник**: SRV-CRYPTO-006.

### 2.17 Out-of-band fingerprint verification (Safety Number)

- Endpoint для verified pubkey publication (signed by admin private key, server validates).
- UI: 4-6-цифровой fingerprint (SHA-256 от обоих pubkey, первые 24 бит), голосовая сверка.
- Закрывает ghost device attack vector.
- **Источник**: SRV-CRYPTO-005.

### 2.18 Multi-device recovery (social recovery, future)

- Setup: passphrase → `recovery_key = HKDF(passphrase, peer_nonce, "launcher-recovery-aead-v1")` → `encrypted_backup = AEAD(recovery_key, priv_keys_bundle)` → server.
- Recovery: новое устройство → email auth → server initiates 2FA push к trusted peer → peer пере-sealCEK'ает `peer_nonce` для new device pub → новое устройство просит PIN → derives `recovery_key` → decrypts backup.
- **3-фактор**: PIN + email/password + физическое подтверждение от peer-device.
- **Источник**: SRV-CRYPTO-004, ADR-008. Backlog: TODO-FUTURE-SPEC-009.

### 2.19 Rate-limit на recovery attempts

- Atomic counter в Redis: block after N failed attempts/hour per externalId.
- Push notification бабушке: «Кто-то пытался восстановить».
- Audit log.
- **Источник**: SRV-CRYPTO-006 (recovery context, не unsharing).

### 2.20 Encrypted backup storage (substitution-ready)

```kotlin
interface RecoveryBackupStorage {
  suspend fun upload(externalId: ExternalId, schemaVersion: Int, blob: ByteArray): Result<Unit>
  suspend fun download(externalId: ExternalId): Result<EncryptedBackup>
  suspend fun delete(externalId: ExternalId): Result<Unit>
}
```

- MVP: `FirestoreRecoveryBackupStorage`. Own server: `HttpRecoveryBackupStorage`.
- **Источник**: SRV-CRYPTO-007.

### 2.21 Atomic membership operations (Family Group)

- ACID transactions cross-document (add/remove/promote/kick).
- Сейчас Firestore Transactions + optimistic locking, race conditions возможны.
- Own server: настоящие ACID (Postgres).
- **Источник**: SRV-CMD-002.

### 2.22 Named configs persistence (admin's own configs backup)

- `/admin-self-configs/{adminUid}/configs/{configName}/current` — backup admin'ских named configs.
- Atomic single-default invariant через transaction.
- Cron auto-delete orphan configs.
- **Источник**: SRV-CFG-006, TODO-FUTURE-SPEC-008. Спека: 014.

### 2.23 Manual key rotation flow

- Generate new keypair locally → re-encrypt на client после download через ACID-coordinated transaction → atomic switch identity reference.
- Signal Double Ratchet — overkill (real-time messaging). Matrix Megolm closer. **Recommendation: on-demand manual, not automatic**.
- **Источник**: SRV-CRYPTO-002. Backlog: TODO-FUTURE-SPEC-010.

### 2.24 Translation cache (dev tool)

- `/api/translate` proxy: server-cached translations keyed by `(source, target_locale, key, context_hash)`; human review queue для AR/HI/ZH/JA/KK; shared API key.
- **Источник**: SRV-TRANSLATE-001.

### 2.25 App Check validation

- Validate `X-Firebase-AppCheck` header на каждом write endpoint.
- **Источник**: SRV-SEC-001. Backlog: TODO-SEC-001.

### 2.26 Critical health → push admin

- Listener на изменения `/links/{linkId}/health` → детект Critical transition → дедуплицирует → FCM push админу.
- **Источник**: SRV-MONITOR-001. Backlog: TODO-ARCH-012.

### 2.27 Wearable / security sensor ingest

- Принимать данные с часов (HRV, BP), охранных датчиков (motion, smoke); time-series БД; alerts; push.
- **Источник**: SRV-MONITOR-002. Backlog: TODO-FUTURE-SPEC-001/002.

### 2.28 Shared admin contact book

- Контакты per-admin, ссылки из `/config.contacts[]` по UUID на shared.
- **Источник**: SRV-CONTACTS-002. Backlog: TODO-FUTURE-SPEC-004.

---

## 3. Что НЕ переезжает на сервер (остаётся на клиенте навсегда)

- **Криптография** (encryption / hashing / signing через libsodium) — клиент-side по дизайну e2e.
- **Приватные ключи** (X25519 priv, Ed25519 priv) — в Android Keystore, никогда не покидают устройство.
- **Envelope format и cipherSuite** — wire format, версионируется через `schemaVersion`.
- **Contact drift detection** (SRV-CONTACTS-001) — client-side check (data residency).

---

## 4. Триггеры для перехода на собственный сервер (one-way door)

Нужно ≥2 из 4:
1. **Privacy / compliance**: реальные EU пользователи → GDPR → SRV-SEC-003 endpoints.
2. **Integrity incident**: один случай spoofing или потери данных от race condition.
3. **Scale**: Cloudflare free 100k req/day исчерпан или Firestore 1GB близко.
4. **Vendor lock-in / pricing**: Cloudflare ужесточает условия или Google поднимает цены.

До этого момента — каждое новое client-side workaround = запись в server-roadmap.md.

---

## 5. Архитектурное видение (preferred stack)

Из server-roadmap §"Архитектурное видение":
- **Language/framework**: **Kotlin/Ktor** (preferred — KMP harmony с клиентом) или Node.js/Fastify.
- **БД**: PostgreSQL (relational, ACID) + Redis (cache, rate-limit, queues).
- **Storage**: S3-compatible (drop-in replacement Backblaze B2 → own MinIO / Cloudflare R2 / etc.).
- **Push**: остаётся FCM (нет смысла своё писать) — но service account JSON переезжает в свой secret manager (HashiCorp Vault / AWS Secrets Manager).
- **Auth**: Firebase Auth можно оставить как identity provider, опционально мигрировать на own JWT issuer.
- **Deployment**: собственный VPS или K8s.
- **Observability**: Self-hosted Sentry (TASK-37).

---

## 6. Порядок миграции (что должно ехать первым)

Из inline-TODO в спеках и server-roadmap:

1. **Identity-links migration (SRV-AUTH-IDENTITY-001)** — MUST migrate first. Read-only Firestore identity-links ещё 30 дней как fallback. Если skip → broken UUID stability → broken delegations/config-sync.
2. **Push (SRV-PUSH-FOUNDATION)** — wire-format уже generic, портируется 1:1 (5-7 дней работы).
3. **Envelope storage (SRV-STORAGE-001)** + **PublicKeyDirectory (SRV-PKD-001)** — domain port не меняется, swap adapter + ETL Firestore → REST.
4. **Blob storage (SRV-CRYPTO-001)** — drop-in S3 (если B2 → own S3-compatible). Background reconciler перевозит старые blobs.
5. **Config history (SRV-CONFIG-001..003)** — atomic transaction + cron retention + schema transformers.
6. **Recovery vault (SRV-RECOVERY-001)** — atomic counter критичен для H-1 closure.
7. **Audit log + GDPR endpoints (SRV-SEC-002, SRV-SEC-003)** — до production launch (Play Store blocker).
8. **Remaining**: monitoring, commands queue, shared contact book, wearable ingest — по мере надобности.

---

## 7. Ключевые архитектурные принципы для server design

1. **Domain port stability**: каждый server endpoint = реализация существующего port'а в `core/*/api/`. Adapter swap, не domain rewrite.
2. **Wire-format versioning**: каждый payload содержит `schemaVersion`, monotonic increase enforced server-side (downgrade defence).
3. **E2E inviolable**: server никогда не видит plaintext config / media / keys. Только ciphertext + metadata.
4. **Substitution-readiness**: каждый Firebase-зависимый компонент — через port, чтобы swap = новый adapter.
5. **Atomic counters server-side** (SRV-RECOVERY-001, SRV-CRYPTO-006) — закрывает client-side bypass через Clear App Data / factory reset / root.
6. **Rate-limit per dimension**: per-UID, per-linkId, per-IP-hash, per-eventType.
7. **Idempotency keys** на всех state-mutating endpoints (UUID v4 client-generated, server dedupe).
8. **JWT auth с server-issued session token**, не Firebase ID-token напрямую (см. SRV-AUTH-001).

---

## 8. Связанные документы (для глубокого dive)

- [docs/dev/server-roadmap.md](server-roadmap.md) — источник правды по каждой SRV-задаче.
- [docs/dev/project-backlog.md](project-backlog.md) — TODO-ARCH-*, TODO-SEC-*, TODO-OPS-*, TODO-LEGAL-* (operational).
- [CLAUDE.md](../../CLAUDE.md) — engineering rules (особенно §1, §2, §5, §8).
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md) — Articles I-XVI.
- [docs/adr/](../adr/) — architectural decisions (ADR-008 social recovery, etc.).
- [docs/product/vision.md](../product/vision.md) — strategic vision, exit ramps, soft launch gate.
- `specs/task-3-*/` (F-4 AuthProvider, реализовано) — pattern для auth endpoint.
- `specs/task-7-*/` (F-3 wizard) — pattern для NetworkConfigSource.
- `specs/task-4-*/` (F-5b own config E2E), `specs/task-6-*/` (Root Key Hierarchy) — patterns для envelope/PKD/recovery.
- `workers/push/src/` — existing push trigger, готов к extraction.
- `workers/_shared/auth-jwt/` — JWT verification, готов к extraction.

---

## 9. Open architectural questions (для AI-агента — обсудить с владельцем)

1. **Backend stack — Kotlin/Ktor vs Node/Fastify vs Go**: владелец склонен к Kotlin (KMP harmony), но Ktor server ecosystem меньше; обсудить trade-off.
2. **Identity provider strategy**: оставить Firebase Auth как identity provider навсегда (только session JWT issued by us) vs полный собственный auth (email/password + OAuth). Влияет на SRV-AUTH-001.
3. **Storage strategy**: own S3 (MinIO self-hosted) vs Cloudflare R2 (другой vendor lock-in, но cheaper) vs AWS S3 (dearer, но stable).
4. **Push: FCM forever vs WebPush/own protocol**: FCM требует Google Cloud Project even on own backend. WebPush — не работает на Android без unified push.
5. **БД choice**: Postgres vs MySQL vs SQLite (для MVP own server). Postgres preferred per roadmap.
6. **Deployment topology**: single VPS (DigitalOcean droplet) vs K8s (overkill для MVP) vs serverless (Cloudflare Workers + D1 / DurableObjects — остаётся внутри CF ecosystem).
7. **Migration window strategy**: dual-write phase (write to Firestore AND own server) vs hard cutover. Identity-links MUST be migrated first per SRV-AUTH-IDENTITY-001.
8. **Cost target**: подъёмная цифра для solo-dev — $10-30/month managed Ktor hosting. Free tier (CF Workers + D1) — почти бесплатно, но vendor lock-in остаётся.

---

## 10. Numbering note (для AI-агента)

Некоторые SRV ID были renamed/replaced — следить за подзаголовками в server-roadmap.md:
- SRV-CRYPTO-007 → SRV-CRYPTO-008 (renumbered due to ID conflict).
- SRV-FCM-CONFIG-UPDATE → SRV-PUSH-FOUNDATION (rescoped 2026-06-20).
- SRV-CMD-001 (config history) vs SRV-CONFIG-001 (NetworkConfigSource) — разные подсистемы, одинаковый префикс.
- "future multi-device-recovery spec (TBD)" — формулировка, исторически указывала на разные number'а; сейчас = TODO-FUTURE-SPEC-009.

---

**End of briefing.** Полная и актуальная картина — в `server-roadmap.md`. Этот файл — производный, regenerate при major shift.
