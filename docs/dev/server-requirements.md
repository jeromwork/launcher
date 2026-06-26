# Server Requirements — что launcher ожидает от backend'а

**Назначение**: единый список **функциональных и нефункциональных требований** launcher-клиента к серверной части. Уровень абстракции — «что должно делать», не «как реализовано» и не «когда мигрируем». Файл standalone — можно подгружать AI-агенту для дизайн-обсуждений серверной архитектуры без чтения остального проекта.

**Связь с другими документами**:
- [server-roadmap.md](server-roadmap.md) — operational план миграции (когда что мигрировать, что workaround сейчас, exit ramps). Источник правды по каждой `SRV-*` задаче.
- [server-context-for-ai-agent.md](server-context-for-ai-agent.md) — расширенный брифинг с текущей инфраструктурой и open architectural questions.
- Этот файл — **спецификация интерфейса** между launcher'ом и сервером.

**Snapshot date**: 2026-06-26.

---

## 1. Auth + identity

**Требование**: server-issued session JWT (не Firebase ID-token напрямую), глобально-уникальный stable UUID на пользователя независимо от провайдера identity.

```
POST /auth/google-signin
  body: { idToken: <Google ID Token> }
  response: { sessionJwt, stableId }
```

- Server верифицирует Google ID Token через JWKS, сам выпускает наш JWT с claim `stableId`.
- **Identity-links table**: atomic lookup-or-create `(providerKind, providerAccountId) → stableId UUID`. Race-safe. При повторном входе — **тот же UUID** (иначе ломается delegation и pair-key).
- Custom claim `googleSub` для downstream authorization.

**Связано**: SRV-AUTH-001, SRV-AUTH-IDENTITY-001, SRV-AUTH-IDENTITY-002.

---

## 2. Envelope storage (encrypted config data)

**Требование**: arbitrary key-value store зашифрованных envelope'ов per-user namespace. Server не видит plaintext.

```
PUT    /users/{namespace}/data/{key}        body: envelope JSON
GET    /users/{namespace}/data/{key}
LIST   /users/{namespace}/data?prefix={p}
DELETE /users/{namespace}/data/{key}
```

- Server-side validation: `schemaVersion` monotonic increase (downgrade defence).
- Envelope shape валидируется (Maps/Blobs typed), содержимое — нет.

**Связано**: SRV-STORAGE-001.

---

## 3. Public Key Directory + access grants

**Требование**: каждое устройство публикует свой X25519 pub-key; access-grant'ы между UID'ами; atomic create/revoke.

```
PUT    /users/{uid}/devices/{deviceId}/pub-key       body: X25519 pub bytes
GET    /users/{uid}/devices                          → list (deviceId, pubKey) с grant check
GET    /users/{uid}/access-grants
PUT    /users/{ownerUid}/access-grants/{helperUid}   atomic
DELETE /users/{ownerUid}/access-grants/{helperUid}   atomic revoke
```

Server enforces:
- Owner-only write своей pub-key entry.
- Helper read of owner devices iff non-revoked grant.
- Atomic transactional grant create/revoke (race-free).

**Связано**: SRV-PKD-001.

---

## 4. DeviceId allocation

**Требование**: collision-resistant deviceId allocation внутри UID-namespace.

```
POST /users/{uid}/devices/allocate          → returns unique deviceId
```
ИЛИ (cheaper):
```
POST /users/{uid}/devices/{deviceId}       409 Conflict если занят
```

**Связано**: SRV-DEVICEID-001.

---

## 5. Push trigger (generic)

**Требование**: один generic endpoint для триггера push-уведомлений из app в app внутри family group. Event type whitelist'ится на сервере.

```
POST /push
  Header: Authorization: Bearer <session JWT>
  Header: Idempotency-Key: <UUID v4>
  body: {
    schemaVersion: 1,
    eventType: "config-updated" | "sos-triggered" | "health-critical" | "entitlement-expired" | "pairing" | "messenger-msg" | "album-update" | "caregiver-invite" | "config-rewrite",
    targetScope: "own-devices" | "own-and-grants",
    ownerUid: <uid>,
    payload: { ... }
  }
```

Server:
1. JWT validate.
2. EventType whitelist + per-event authz (config-updated = owner∨grant-holder; sos-triggered = owner; entitlement-expired = server-internal only).
3. Per-event rate-limit (config-updated 60/min, sos-triggered 10/min, etc.).
4. Idempotency dedupe (10-min TTL).
5. Recipient resolution per targetScope (devices + grants).
6. FCM dispatch с `collapse_key: "{eventType}:{ownerUid}:{contextKey}"`.
7. Bounded retry FCM 3× (500ms / 2s / 8s).

**Связано**: SRV-PUSH-FOUNDATION.

---

## 6. Blob storage (encrypted media)

**Требование**: opaque ciphertext storage с reference counting; server не видит plaintext, не знает recipient'ов.

```
POST   /links/{linkId}/blobs/{uuid}              upload envelope
GET    /links/{linkId}/blobs/{uuid}              download
DELETE /links/{linkId}/blobs/{uuid}              cleanup
PUT    /links/{linkId}/devices/{deviceId}/pubkey
GET    /links/{linkId}/devices/{deviceId}/pubkey
```

- Server-side reference counting через ACID transaction (refCount = 0 → реально удалить).
- Auth: link-membership check на каждый запрос.
- Storage backend: S3-compatible (для drop-in замены провайдера).

**Связано**: SRV-CRYPTO-001.

---

## 7. Config history + atomic writes

**Требование**: atomic «новый current + старый current → history» одним write'ом; retention; lazy schema migration; app version compatibility.

```
POST /users/{uid}/config                         atomic write
GET  /users/{uid}/config/current
GET  /users/{uid}/config/history?limit={n}
POST /users/{uid}/config/rollback?version={v}
```

- ACID transaction (заменяет client-side two-write race).
- Cron retention: cleanup snapshots > 10 per linkId, hourly.
- Schema transformers при чтении: `vN → vCurrent` chain.
- Reject writes если `managedAppVersion < required` (compatibility).

**Связано**: SRV-CONFIG-001, SRV-CONFIG-002, SRV-CONFIG-003, SRV-CONFIG-004.

---

## 8. Recovery key vault (single-owner E2E recovery)

**Требование**: encrypted root key blob storage + **server-side atomic brute-force counter** (закрывает H-1: нельзя обойти через Clear App Data / factory reset / root).

```
PUT  /users/{uid}/recovery-vault                 upload AEAD blob
GET  /users/{uid}/recovery-vault
POST /users/{uid}/recovery-vault/attempt         atomic counter inc + verify
```

- Atomic counter persistent (per-UID, не per-device).
- Schema-version downgrade defence.
- Audit log access.

**Связано**: SRV-RECOVERY-001.

---

## 9. Subscription entitlement

**Требование**: server-validated entitlement, **не** client-computed flag (anti-tamper для paid tier).

```
GET  /users/{uid}/entitlement                    → {tier, expiresAt}
POST /users/{uid}/entitlement/check              → JWT с entitlement claims
```

- `entitlement-expired` push event — server-internal only (не от клиента).
- Server-side timer для подписок.

**Связано**: TASK-15 (Subscription Server Timer).

---

## 10. GDPR / 152-ФЗ compliance

**Требование**: subject-driven export и deletion, ≤30 дней.

```
GET    /users/{uid}/export                       → all user data JSON
DELETE /users/{uid}                              → полное удаление
```

- Может быть инициировано самим пользователем (без admin'а).
- Audit log на каждый запрос.

**Связано**: SRV-SEC-003.

---

## 11. Audit log

**Требование**: лог всех write-операций для compliance + forensics.

```
GET /users/{uid}/audit-log?from={ts}&to={ts}    (admin/owner only)
```

- Записи: UID + timestamp + operation + payload hash. Retention 90 дней.
- Server пишет автоматически на каждый mutating endpoint.
- Append-only, tamper-evident через hash chain (см. §26 §audit_key).

**Связано**: SRV-SEC-002.

---

## 12. Admin-to-Managed runtime commands

**Требование**: command queue с TTL + retry + ack.

```
POST /links/{linkId}/commands                    admin queues
GET  /links/{linkId}/commands                    Managed polls (или push)
POST /links/{linkId}/commands/{id}/ack
```

- Queue с TTL.
- Push trigger на Managed при enqueue.
- Server удаляет на ack или TTL expiry.

**Связано**: SRV-CMD-001.

---

## 13. NetworkConfigSource (signed manifests)

**Требование**: подписанные wizard manifests / tile sets / themes без app update.

```
GET /v1/configs/{kind}/{id}                      → signed manifest envelope
```

- Ed25519 signed, client verifies против pinned public key.
- `BundledConfigSource` остаётся offline fallback.

**Связано**: SRV-CONFIG-001 (F-3 variant).

---

## 14. UserPreferences cloud sync

**Требование**: theme, fontScale, languageOverride, wizardCompletedAppFamilies синхронизируются между устройствами одного владельца.

Реализуется как slot внутри `/users/{uid}/data/userPreferences` (использует §2 envelope storage), отдельных endpoint'ов не требует.

**Связано**: SRV-PREFS-001.

---

## 15. Algorithm migration (crypto rotation)

**Требование**: server-triggered batch job для re-wrap старых vault'ов и re-encrypt envelope'ов под новый AEAD/KDF.

```
POST /users/{uid}/recovery-vault/rewrap-request  (server-initiated)
GET  /users/{uid}/migration-status               → {phase, deadline}
POST /admin/migration/force-deadline             (admin only)
```

- 3 фазы: coexistence → auto-migration → deprecation/forced update (WhatsApp E2E backup pattern).
- Requires client participation (passphrase для decrypt старого).

**Связано**: SRV-CRYPTO-008.

---

## 16. Forward unsharing

**Требование**: при unpair admin'а — re-encryption всех existing config snapshots под новым CEK без removed recipient.

```
POST /links/{linkId}/unshare                     body: { removedDeviceId }
```

- Server coordinates re-encryption (requires client cooperation для CEK rewrap).

**Связано**: SRV-CRYPTO-006.

---

## 17. Safety Number (out-of-band fingerprint)

**Требование**: verified pubkey publication (signed by owner private key, server validates signature) + UI flow для голосовой сверки fingerprint.

```
PUT /users/{uid}/devices/{deviceId}/pub-key/signed   body: {pubKey, sig}
GET /users/{uid}/devices/{deviceId}/fingerprint      → SHA-256 первые 24 бит
```

**Связано**: SRV-CRYPTO-005.

---

## 18. Social recovery (multi-device recovery)

**Требование**: 3-факторное recovery (PIN + email/password + 2FA от trusted peer), encrypted backup storage.

```
PUT  /users/{uid}/recovery-backup                upload encrypted blob
GET  /users/{uid}/recovery-backup
POST /users/{uid}/recovery-attempt               initiates 2FA push to peer
POST /users/{uid}/recovery-attempt/{id}/approve  peer approves
POST /users/{uid}/recovery-attempt/{id}/verify   new device submits PIN-derived proof
```

- Atomic counter на attempts (rate-limit, см. §8).
- Push trigger к trusted peer.
- Atomic activation новых ключей + invalidation старых.
- Опционально multi-peer Shamir N-of-M (future).

**Связано**: SRV-CRYPTO-004, ADR-008.

---

## 19. App Check validation

**Требование**: validate `X-Firebase-AppCheck` (или эквивалент) header на каждом write endpoint — anti-abuse.

Не отдельный endpoint, middleware на всех mutating endpoints.

**Связано**: SRV-SEC-001.

---

## 20. Critical health → push admin

**Требование**: server listens на изменения health-state Managed device → детект Critical transition → дедуплицирует → push admin'у.

```
PUT /links/{linkId}/health                       Managed publishes
                                                  (server-side trigger → push admin)
```

- Деduplicate per-incident.

**Связано**: SRV-MONITOR-001.

---

## 21. Wearable / security sensor ingest

**Требование**: time-series ingest для часов (HRV, BP, steps) и охранных датчиков (motion, door, smoke); alerts по threshold.

```
POST /links/{linkId}/sensors/{kind}              body: time-series data
GET  /links/{linkId}/sensors/{kind}/timeline?from={ts}&to={ts}
POST /links/{linkId}/sensors/{kind}/thresholds   admin sets
```

- Triggers push в зависимости от severity.

**Связано**: SRV-MONITOR-002.

---

## 22. Shared admin contact book

**Требование**: контакты per-admin, ссылки из config по UUID.

```
PUT    /admins/{adminUid}/contacts/{contactUuid}
GET    /admins/{adminUid}/contacts
DELETE /admins/{adminUid}/contacts/{contactUuid}
```

- Atomic refcount при добавлении/удалении ссылки из config.

**Связано**: SRV-CONTACTS-002.

---

## 23. Named configs persistence (admin self-configs backup)

**Требование**: backup admin'ских named configurations (для cross-device + auto-delete orphans).

```
PUT    /admin-self-configs/{adminUid}/configs/{configName}/current
GET    /admin-self-configs/{adminUid}/configs/{configName}/current
LIST   /admin-self-configs/{adminUid}/configs
DELETE /admin-self-configs/{adminUid}/configs/{configName}
```

- Atomic single-default invariant через transaction.
- Cron auto-delete orphan configs (нет ссылок из активного config).

**Связано**: SRV-CFG-006.

---

## 24. Manual key rotation

**Требование**: on-demand rotation identity-ключа admin'а с re-wrap всех envelope wrappers под new pub.

```
POST /users/{uid}/keys/rotate                    initiates flow
GET  /users/{uid}/keys/rotation-status
POST /users/{uid}/keys/rotation/complete         atomic switch
```

- Re-encrypt на client после download (server не видит plaintext).
- Atomic switch identity reference + invalidation old priv references.

**Связано**: SRV-CRYPTO-002.

---

## 25. Translation cache (dev tool, low priority)

**Требование**: shared translation cache для разработчиков (один shared API key + human review queue).

```
POST /api/translate                              body: {source, target_locale, key, context_hash}
GET  /api/translate/queue                        human reviewer pulls
POST /api/translate/queue/{id}/approve
```

**Связано**: SRV-TRANSLATE-001.

---

## 26. Server-at-rest encryption + threshold unsealing + remote attestation

**Требование**: защита всего server-side metadata (identity-links, public keys, access-grants, audit log, recovery vault ciphertext, envelope storage, blob references) от sценариев:
- **TM-1 stolen DB dump** — украденная копия Postgres.
- **TM-2 stolen running server** — атакующий получает live VM с decrypted master key in-memory.
- **TM-3 compromised hosting provider / insider** — admin провайдера может dump memory или modify binary.
- **TM-4 long-term forward compromise** — master key compromised → все данные читаемы.

E2E inviolable (client-side encryption) защищает только пользовательский plaintext; metadata должна защищаться отдельно server-side.

### 26.1 At-rest encryption всего metadata

- **Field-level encryption** для sensitive columns (recovery vault ciphertext, audit log payload hash, identity-links — всё, что не нужно для index lookup).
- **Disk-level encryption** (LUKS / cloud-provider TDE) как defence-in-depth, **не как primary**.
- Encryption key = `master_data_key`, derived from `master_unseal_key`.

### 26.2 Threshold unsealing (Shamir N-of-M)

- `master_unseal_key` разделён на **N shards** через Shamir's Secret Sharing.
- Server при boot ожидает **M-of-N shards** (recommendation: 3-of-5 production, 2-of-3 MVP).
- Shards вводятся через secure channel:

```
POST /ops/unseal                       (mTLS + IP allowlist)
  body: { shardIndex: int, shardValue: <base64> }
```

- До unseal'а: server в `sealed` state — отвечает только на `/health` и `/ops/unseal`, отбрасывает все остальные requests с 503.
- После M shards: derives `master_unseal_key` in-memory → derives `master_data_key` → открывает БД.
- Reference: HashiCorp Vault `vault operator unseal`.

### 26.3 Shard custody policy

- Каждый shard хранится **отдельным operator'ом** (физически разделённые люди / accounts / clouds).
- Recommended distribution (3-of-5):
  1. Offline cold storage (paper / metal backup в сейфе).
  2. Hardware token у владельца.
  3. Trusted operator (юрист / партнёр).
  4. Cloud KMS другого провайдера (отличного от hosting).
  5. Backup в другом geographic region.
- **Никакие 2 shard'а не лежат вместе**.

### 26.4 Hardware-backed unsealing (preferred path для Phase 3)

- `master_unseal_key` wrapped через **Cloud HSM / KMS** (AWS KMS + Nitro Enclaves, GCP Cloud KMS + Confidential VMs, Azure Key Vault + Confidential Computing).
- KMS releases key **только** если attestation document от instance проходит verification (PCR measurements / Nitro attestation).
- Закрывает TM-3 (insider): hosting provider не может dump memory без trip attestation.

### 26.5 Periodic remote attestation

```
POST /ops/attestation                  (server → external attestation service)
  body: { binaryHash, configHash, tpmPcr, uptime, bootTimestamp }
```

- Каждые **6 часов** (recommended) server отправляет attestation document на **external attestation service** (отдельный endpoint, отдельный provider).
- Attestation service сравнивает с known-good baseline. На mismatch:
  - Alert (push владельцу + Slack/email).
  - Auto-shutdown via revoke unseal token.
- Owner получает push на каждую failed attestation.

### 26.6 Auto re-seal on TTL

- `master_unseal_key` в memory имеет **bounded TTL** (recommendation: 24h).
- После TTL: server переходит в `sealed` state, требует свежий threshold unsealing.
- Защищает от long-running compromise.

### 26.7 Master key rotation

- `master_data_key` ротируется раз в **90 дней**.
- Background job re-encrypts старые ciphertext'ы под новый key.
- Старый key хранится для read для `re-encryption period + 30 дней`, затем удаляется.
- Audit log записывает rotation events.

### 26.8 Forensic-safe audit log

- Audit log сам по себе зашифрован под отдельный `audit_key` (не master_data_key).
- `audit_key` shards распределены по другим operator'ам (отличным от master_unseal_key custody).
- Append-only structure, tamper-evident через hash chain (каждая запись содержит hash предыдущей).

### 26.9 Phase rollout

- **Phase 1 (single-server, < 100 users)**: `master_unseal_key` в env var через secret manager (Vault / AWS Secrets Manager). LUKS disk encryption. Documented limitation — закрывает только stolen-disk vector.
- **Phase 2 (production, > 100 users)**: добавить Shamir N-of-M threshold unsealing + `POST /ops/unseal`.
- **Phase 3 (regulated / GDPR-heavy)**: добавить hardware-backed (Cloud KMS + attestation) + periodic remote attestation + auto re-seal.
- Migration между phases — additive (port `MasterKeyProvider` остаётся, swap implementation).

### 26.10 Что нужно от launcher-клиента

**Ничего** — это полностью server-side concern. Клиент продолжает работать с REST endpoint'ами как обычно. При server `sealed` state клиент получает 503 → retry с exponential backoff (та же логика, что и для любого server outage).

**Связано**: SRV-SEC-006.

**Industry references**:
- [HashiCorp Vault — Seal/Unseal](https://developer.hashicorp.com/vault/docs/concepts/seal)
- [Shamir's Secret Sharing](https://en.wikipedia.org/wiki/Shamir%27s_secret_sharing)
- [AWS Nitro Enclaves Attestation](https://docs.aws.amazon.com/enclaves/latest/user/nitro-enclave-concepts.html)
- [GCP Confidential VMs](https://cloud.google.com/confidential-computing/confidential-vm/docs/about-cvm)
- [Signal — Sealed Sender](https://signal.org/blog/sealed-sender/)

---

## Кросс-функциональные требования (на всех endpoint'ах)

- **JWT auth** (server-issued, не Firebase ID-token напрямую) кроме §1.
- **Idempotency-Key** на всех state-mutating endpoint'ах (UUID v4, server dedupe).
- **Rate-limit** persistent по dimensions: per-UID, per-linkId, per-IP-hash, per-eventType.
- **schemaVersion** в payload, monotonic increase enforced server-side.
- **E2E inviolable**: server никогда не видит plaintext config / media / keys / passphrase.
- **Audit log** auto-write на каждый mutating endpoint.
- **App Check** middleware на каждый write.
- **CORS / CSP** правила (если будет web admin в будущем).
- **При sealed state** (см. §26): отвечать 503 на всё кроме `/health` + `/ops/unseal`.

---

## Что НЕ требуется от сервера (остаётся на клиенте)

- Crypto operations (libsodium на клиенте, server только хранит ciphertext).
- Private keys (Android Keystore).
- Envelope serialization (client encodes/decodes).
- Contact drift detection (privacy: контакты не уходят на сервер).
- Local UI state, theme application, wizard navigation.

---

## Принципы интерфейса launcher ↔ server

1. **Domain port stability**: каждый server endpoint = реализация существующего port'а в `core/*/api/`. Adapter swap, не domain rewrite.
2. **Wire-format versioning**: каждый payload содержит `schemaVersion`, monotonic increase enforced server-side (downgrade defence).
3. **E2E inviolable**: server никогда не видит plaintext config / media / keys.
4. **Substitution-readiness**: каждый Firebase-зависимый компонент — через port, чтобы swap = новый adapter.
5. **Atomic counters server-side** (§8, §18) — закрывает client-side bypass через Clear App Data / factory reset / root.
6. **Rate-limit per dimension**: per-UID, per-linkId, per-IP-hash, per-eventType.
7. **Idempotency keys** на всех state-mutating endpoints (UUID v4 client-generated, server dedupe).
8. **JWT auth с server-issued session token**, не Firebase ID-token напрямую.
9. **Server-at-rest защищён threshold unsealing** (§26) — клиент не видит этого, но это требование к серверу.

---

**End of requirements.** Источник правды по operational план — `server-roadmap.md`. Этот файл — спецификация интерфейса, regenerate при добавлении новых SRV-задач.
