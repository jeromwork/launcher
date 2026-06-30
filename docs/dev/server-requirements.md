# Server Requirements — Zero-Knowledge модель

**Назначение**: единый список **функциональных и нефункциональных требований** launcher-клиента к серверной части, спроектированный по **zero-knowledge / sealed-server** принципу. Уровень абстракции — «что должно делать», не «как реализовано». Файл standalone — можно подгружать AI-агенту для дизайн-обсуждений серверной архитектуры без чтения остального проекта.

**Snapshot date**: 2026-06-26.
**Версия**: v2 (zero-knowledge rewrite, заменяет v1 «smart server»).

**Связь с другими документами**:
- [server-roadmap.md](server-roadmap.md) — operational план миграции (SRV-* задачи).
- [client-requirements-for-zero-knowledge-server.md](client-requirements-for-zero-knowledge-server.md) — что добавить в launcher-клиент, чтобы сервер мог быть тупым.
- [server-context-for-ai-agent.md](server-context-for-ai-agent.md) — расширенный брифинг.

---

## Threat model — что мы защищаем

| Угроза | Защита |
|---|---|
| **Server stolen / DB dump** | Сервер не знает контент blob'ов (E2E); metadata минимизирован до opaque IDs; sealed-at-rest (см. Tier 2 §T4). |
| **Hosting provider insider** | Hardware attestation + threshold unsealing; metadata минимален. |
| **Network sniffing** | TLS + E2E внутри payload (двойное шифрование push payload, body требований). |
| **Compromised account at provider** | Sealed-at-rest требует M-of-N shards с distributed custody — provider один не может unseal. |
| **Member kicked from group** | Forward unsharing полностью client-coordinated (re-key + re-encrypt + новый keyring); сервер просто принимает signed write. |
| **Ghost device attack** | Out-of-band fingerprint verification (Safety Number) — client-side, сервер не валидирует кто кому. |
| **Replay attacks** | Nonce внутри encrypted payload + client-side dedup; сервер не отслеживает «доставлено или нет». |
| **Brute-force passphrase** | **Единственное место Tier 2**: atomic server-side counter на recovery vault (закрывает Clear App Data / factory reset / root bypass). |

---

## Принцип tiering — что сервер знает

Каждый endpoint классифицируется в один из 3 tier'ов. **Tier 0 — default**, повышение tier'а требует доказательства «почему нельзя ниже».

### Tier 0 — Sealed storage

- Сервер видит: opaque namespace ID + opaque key + ciphertext blob + signing pubkey владельца namespace.
- Сервер НЕ видит: контент, owner UID, тип данных, граф связей, кто кому отправляет.
- Authorization: signed write (Ed25519 signature от namespace-owner key), сервер проверяет подпись против записанного при namespace setup.
- **80% endpoint'ов сюда.**

### Tier 1 — Minimal directory

- Сервер видит: opaque ID → public key bytes (или FCM token bytes).
- Сервер НЕ видит: связи между ID, content, ownership graph.
- Industry pattern: Signal Identity Server, X3DH prekey directory.
- **Применяется только когда async setup невозможен без directory** (Alice оффлайн, Bob хочет послать).

### Tier 2 — Server-required logic

- Сервер выполняет логику, не имея plaintext данных, но имея side-effect knowledge (counter, timer).
- **Допускается только** когда client-side bypass возможен через Clear App Data / factory reset / root.
- Единственные кейсы: atomic anti-brute-force counter, subscription entitlement timer, JWT issuance.

---

## Industry baseline reference

Эта модель опирается на проверенные production patterns:

- **Signal Sender Keys / Sealed Sender** — group messaging без membership graph на сервере. [Whitepaper](https://signal.org/docs/specifications/sesame/).
- **MLS (RFC 9420)** — Messaging Layer Security, новый IETF стандарт group E2E, формальное доказательство security. Used by Cisco Webex, Wire, Discord.
- **Tresorit envelope wrapping** — file key wrapped under each user pub-key, server видит ciphertext + opaque sharing relationships. [Whitepaper](https://tresorit.com/files/tresoritwhitepaper.pdf).
- **WhatsApp E2E Encrypted Backup** — backup ciphertext + HSM-protected key derivation. [Engineering paper](https://engineering.fb.com/2021/09/10/security/messenger-encrypted-backup/).
- **Bitwarden vault sharing** — open-source реализация envelope encryption pattern.

---

## Core data model на сервере

```
Namespace (opaque UUID)              — единица ownership (личный namespace, group namespace)
├── ownerSigningPubKey: bytes        — Ed25519, прибит при namespace создании, immutable
├── blobs/{opaque-key}/              — Tier 0 storage
│   ├── ciphertext: bytes
│   ├── signature: bytes             — Ed25519 sig от ownerSigningPubKey
│   ├── version: int                 — optimistic locking
│   └── createdAt: timestamp         — для cron retention (НЕ для бизнес-логики)
└── (всё; ничего другого)

PubKeyEntry (Tier 1 directory)       — отдельная сущность, НЕ привязана к namespace
├── lookupId: opaque                 — opaque ID (UUID или hash от identity)
├── encryptionPubKey: bytes          — X25519 для async encrypt-for-recipient
├── signingPubKey: bytes             — Ed25519
└── prekeys: [bytes]                 — ephemeral X25519 для X3DH (опционально)

PushChannel (Tier 1)                 — opaque FCM-token directory
├── tokenId: opaque                  — UUID
└── fcmToken: bytes                  — owner sам подписывает обновление

RecoveryCounter (Tier 2)             — anti-brute-force
├── vaultId: opaque
├── attemptsSinceWindow: int
└── windowStart: timestamp
```

**Сервер НЕ хранит**:
- `userUid → namespaces` mapping (клиент сам помнит свои namespaces).
- `namespace → members` (membership — клиентская кухня в keyring blob'е внутри namespace).
- `namespace → namespace` связи (pairing graph).
- Тип данных blob'а (config / photo / message / token-list — всё opaque).

---

## Tier 0 endpoints — opaque storage

### S0. Blob storage (универсальный)

Заменяет: §2 envelope storage, §6 blob storage, §7 config history, §14 user preferences, §15 algorithm migration, §22 shared contacts, §23 named configs из v1.

```
PUT    /namespaces/{nsId}/blobs/{key}
  Header: X-Sig: <base64 Ed25519 sig от ownerSigningPubKey над (nsId|key|version|ciphertext)>
  Header: X-Version: <int, optimistic locking>
  body: <opaque ciphertext>
  → 200 OK | 409 Conflict (version mismatch) | 403 Bad signature

GET    /namespaces/{nsId}/blobs/{key}
  → 200 {ciphertext, version, createdAt} | 404

LIST   /namespaces/{nsId}/blobs?prefix={opaque}
  → 200 [{key, version, createdAt}, ...]

DELETE /namespaces/{nsId}/blobs/{key}
  Header: X-Sig: <signature над (nsId|key|delete-marker)>
  → 200 OK
```

**Сервер делает**:
1. Verifies signature against ownerSigningPubKey записанный при `POST /namespaces` (см. S1).
2. Stores opaque ciphertext.
3. Optimistic locking через version field.
4. Cron retention — клиент сам указывает TTL при PUT (`X-TTL: <seconds>`), сервер удаляет по timeout. Дефолт — без TTL.

**Сервер НЕ делает**:
- Не понимает что внутри ciphertext.
- Не знает чей это namespace (только ownerSigningPubKey, не привязан к userUid).
- Не делает schema validation (только byte size limit).
- Не делает «history rotation», «keep last 10» — клиент сам LIST + DELETE старых.
- Не resolve'ит recipients, не понимает grants.

**Что переехало в клиент**: config history rotation, schema transformers, contact refcount, named configs invariants, algorithm migration logic, recovery vault payload structure.

### S1. Namespace lifecycle

```
POST /namespaces
  body: {ownerSigningPubKey: bytes}
  → 200 {nsId: opaque-UUID}

DELETE /namespaces/{nsId}
  Header: X-Sig: <signature над (nsId|delete-marker|timestamp)>
  → 200 (cascade delete всех blob'ов в namespace)
```

**Сервер делает**: создаёт opaque namespace, привязывает к Ed25519 signing pubkey. Cascade delete по подписи owner'а.

**Сервер НЕ делает**: не привязывает namespace к userUid, не знает сколько у одного user'а namespaces.

### S2. Push delivery (sealed)

Заменяет: §5 push trigger из v1 (убрали eventType registry, target scope, access grants).

```
POST /push
  Header: Authorization: Bearer <session JWT>          — anti-abuse, не для routing
  Header: Idempotency-Key: <UUID>
  body: {
    targetTokens: [opaque-token-id-1, opaque-token-id-2, ...],
    encryptedPayload: <ciphertext под group key, ≤4KB FCM limit>,
    collapseKey: <opaque hash, для FCM dedup>
  }
  → 200 {delivered: N, failed: [{tokenId, reason}]}
```

**Сервер делает**:
1. JWT validate (anti-abuse: rate-limit, abuse detection — не для маршрутизации).
2. Lookup `tokenId → fcmToken` в Tier 1 directory.
3. Forward в FCM HTTP v1 API с `{data: {p: encryptedPayload}, collapse_key}`.
4. Bounded retry FCM 3× (500ms / 2s / 8s) на 429/5xx.
5. Idempotency dedupe 10-min TTL.
6. Rate-limit per-JWT-uid (anti-abuse only, не per-event).

**Сервер НЕ делает**:
- Не знает eventType (config-updated / sos-triggered / etc) — это **внутри encrypted payload**.
- Не знает кто получатель (только opaque tokenIds, не привязаны к userUid).
- Не resolve'ит группы — клиент-отправитель сам tickает blob с keyring группы (где список tokenIds членов).
- Не делает per-event rate-limit — клиент сам respect'ит quota.

**Что переехало в клиент**: eventType dispatching, target scope resolution, group membership lookup, FCM token freshness check.

---

## Tier 1 endpoints — minimal directory

### D1. Public key directory

```
PUT  /pubkeys/{lookupId}
  body: {encryptionPubKey, signingPubKey, prekeys: [...]}
  Header: X-Sig: <Ed25519 self-sig над body>
  → 200 OK

GET  /pubkeys/{lookupId}
  → 200 {encryptionPubKey, signingPubKey, prekey: <single ephemeral, consumed>} | 404
```

**Сервер делает**: хранит mapping `lookupId → pubkeys`. При GET выдаёт один prekey (X3DH async setup), удаляет его (one-time use).

**Сервер НЕ делает**:
- Не связывает lookupId с userUid.
- Не знает, кто запрашивает pubkey (anonymous lookup допустим).
- Не знает граф «кто кому послал prekey».

**lookupId** — opaque UUID, **не** равен userUid. Клиент сам помнит свои lookupIds. Sharing — клиент out-of-band передаёт recipient'у `lookupId` (через QR pairing).

**Industry reference**: Signal Identity Server + Prekey Server (X3DH).

### D2. Push token directory

```
PUT  /push-tokens/{tokenId}
  body: {fcmToken: bytes}
  Header: X-Sig: <Ed25519 sig от signing key, записанного при первом PUT>
  → 200 OK

DELETE /push-tokens/{tokenId}
  Header: X-Sig: <sig>
  → 200 OK
```

**Сервер делает**: хранит `tokenId → fcmToken`. Owner sам обновляет (token refresh при app reinstall).

**Сервер НЕ делает**:
- Не связывает tokenId с userUid.
- Не знает, в какие группы tokenId входит.
- Не знает FCM-результаты доставки (push code на отправителе).

**Distribution tokenId**: клиент при join в группу шифрует свой `tokenId` под group key и записывает в keyring blob группы (Tier 0). Отправитель скачивает keyring → видит tokenIds → шлёт `POST /push`.

---

## Tier 2 endpoints — server-required logic

Каждый Tier 2 endpoint сопровождается **доказательством «почему нельзя Tier 0/1»**.

### T1. Auth — JWT issuance

```
POST /auth/google-signin
  body: {idToken: <Google ID Token>}
  → 200 {sessionJwt, anonymousId}

POST /auth/refresh
  Header: Authorization: Bearer <expiring JWT>
  → 200 {sessionJwt}
```

**Почему Tier 2**: JWT issuance требует verification Google ID Token против Google JWKS — клиент сделать это **может**, но тогда JWT issuer = клиент, и сервер не сможет rate-limit / abuse-detect по JWT. JWT нужен для anti-abuse Tier 0/1 endpoints.

**Что сервер знает**: `googleSub → anonymousId` mapping. `anonymousId` — opaque UUID, **не** привязан к namespaces / pubkeys / push-tokens (клиент сам помнит свои opaque IDs локально). При reinstall клиент re-fetch'ит свои IDs из локального backup или recovery flow.

**Что сервер НЕ знает**:
- Какие namespaces принадлежат этому anonymousId.
- Какие pubkeys / push-tokens принадлежат.
- Email пользователя (только googleSub hash).

**Industry reference**: Signal phone number → registration ID pattern (Signal не помнит email/phone после registration в Sealed Sender mode).

### T2. Recovery vault — atomic counter

```
PUT  /vaults/{vaultId}
  body: <opaque ciphertext blob>
  Header: X-Sig: <Ed25519 sig owner signing key>
  → 200 OK

POST /vaults/{vaultId}/attempt
  body: {proofBytes: bytes}                  — client proof (HMAC of derived key)
  → 200 {blob: ciphertext, remaining: N} | 429 (rate-limited) | 403 (counter exceeded)
```

**Почему Tier 2**: anti-brute-force counter **должен быть** server-side, иначе обходится через Clear App Data / factory reset / root. Client-side counter в DataStore — bypass'ится физически. Это единственный mandatory metadata leak.

**Что сервер делает**:
1. Atomic counter increment per vaultId (window: 3 attempts / hour sliding).
2. Client отправляет `proofBytes` = HMAC(derived_key, vaultId) → сервер проверяет против заранее записанного `expectedProof` → выдаёт blob или отказывает.
3. Counter сбрасывается только при successful unlock (proof matches).

**Что сервер НЕ знает**:
- Plaintext root key (vault ciphertext opaque).
- Passphrase (only HMAC proof).
- Кто owner (vaultId opaque).

**Industry reference**: Signal SVR (Secure Value Recovery) — server hosts vault + counter в Intel SGX enclave; WhatsApp E2E Backup — counter в HSM.

### T3. Subscription entitlement

```
GET  /entitlement
  Header: Authorization: Bearer <session JWT>
  → 200 {tier: "free"|"premium", expiresAt: timestamp, signature: bytes}
```

**Почему Tier 2**: subscription validity **должна** проверяться сервером, иначе клиент-side flag обходится. Server-side timer для expiration.

**Что сервер знает**: `anonymousId → entitlement tier + expiresAt`. Сигнатура entitlement — клиент проверяет offline.

**Что сервер НЕ знает**: за что подписался (just tier label).

### T4. Sealed unsealing (server bootstrap)

Это **внутренняя operation**, не клиентский endpoint. См. SRV-SEC-006 в server-roadmap.md — Shamir N-of-M threshold unsealing master key через `POST /ops/unseal` (mTLS, IP allowlist, operator-only).

Клиент не видит этого endpoint'а. При `sealed` state получает 503 → exponential backoff retry.

---

## Cross-cutting requirements (на всех endpoint'ах)

- **JWT auth** (Tier 0/1 — anti-abuse; Tier 2 — primary auth).
- **Idempotency-Key** на всех state-mutating endpoints.
- **Rate-limit** per-JWT (anti-abuse only, не для business logic).
- **Signature verification** на write endpoints (Ed25519 against stored signing pubkey).
- **TLS 1.3** обязательно.
- **CORS** запрещён (никаких web origins кроме явного admin panel).
- **При sealed state**: 503 + Retry-After header на всё кроме `/health` + `/ops/unseal`.
- **Audit log** server-side для administrative операций (unseal, key rotation) — не для пользовательских.

---

## Что **НЕ делает** сервер (явный список не-функционала)

Каждый пункт — потенциальный искушённый «давайте на сервере сделаем», который **отвергнут** в пользу клиентского решения.

| Не-функционал | Альтернатива на клиенте | Почему сервер не должен |
|---|---|---|
| Resolve recipients для push | Клиент скачивает keyring группы (Tier 0 blob), видит tokenIds, шлёт explicit list | Иначе сервер знает membership graph |
| Schema transformers `vN → vCurrent` | Клиент при чтении видит schemaVersion → транслирует | Иначе сервер видит plaintext полей |
| Config history rotation (keep last 10) | Клиент LIST + DELETE старых | Иначе сервер понимает «это config history» |
| Forward unsharing при kick member | Existing member ротирует groupKey, перешифровывает keyring + blobs, signed write новой версии | Иначе сервер знает membership |
| Atomic membership transactions | Single-writer группы (один из членов делает edit, остальные принимают signed update) | Иначе ACID cross-document на сервере |
| Drift detection contacts | Полностью client-side (контакты не уходят на сервер) | Privacy |
| Validation `managedAppVersion >= required` | Клиент при чтении blob'а проверяет version в plaintext payload, отказывается читать если несовместимо | Иначе сервер видит app version |
| Eventtype dispatching push | encryptedPayload содержит eventType в plaintext (после расшифровки на receiver'е) | Иначе сервер видит тип события |
| Health critical → auto-push admin | Health критическое триггерит client-side → клиент сам шлёт `POST /push` группе | Иначе сервер listens на health state |
| Wearable sensor ingest | Sensor data → encrypted → клиент сам пушит в Tier 0 blob | Иначе server видит time-series данные |
| Refcount blob references | Client-side garbage collector (раз в N месяцев scan + delete unreferenced) | Иначе сервер понимает «эти blob'ы связаны» |
| `entitlement-expired` server-internal push | Receiver сам poll'ит `GET /entitlement` при app open | Tier 2 ограничен issuance, не push |
| Translation cache | Build-time только; не нужен runtime endpoint | Не пользовательский endpoint |
| Audit log пользовательских операций | Client-side append-only encrypted log в Tier 0 blob | Иначе сервер видит «admin изменил config» |
| GDPR export | Client-side — пользователь decrypt'ит свои blob'ы локально → export. Server только удаляет blob'ы по запросу владельца | Сервер у нас и так ничего не знает что экспортировать |
| Algorithm migration coordinator | Каждый client при open vault'а сам видит старый algorithm → re-wrap → uploads new | Иначе сервер видит algorithm |
| NetworkConfigSource (signed manifests) | **Это публикация, отдельный сервис** — выносим в CDN с подписанными manifest'ами. Не часть user-data сервера. | Разделение concerns |

---

## Соответствие старым SRV-задачам

| v1 SRV ID | v2 решение | Что изменилось |
|---|---|---|
| SRV-AUTH-001 | T1 | Anti-abuse focus, не identity provider |
| SRV-AUTH-IDENTITY-001 | T1 | `googleSub → anonymousId`, без namespace связи |
| SRV-STORAGE-001 | S0 | Generic blob storage, без `users/{namespace}/data/{key}` смысла |
| SRV-PKD-001 | D1 | Pubkey directory anonymous lookup, без grants |
| SRV-DEVICEID-001 | client-side | Клиент сам генерит, server collision-resistance не нужен (deviceId внутри namespace, namespace opaque) |
| SRV-PUSH-FOUNDATION | S2 | Sealed push, без eventType registry на сервере |
| SRV-CRYPTO-001 (blobs) | S0 | Generic blob storage, без linkId |
| SRV-CONFIG-001..004 | S0 + client | History — клиент LIST + сам обрезает; transformers — клиент; version compat — клиент |
| SRV-RECOVERY-001 | T2 | Counter остаётся server-side |
| TASK-15 entitlement | T3 | Server-validated, JWT-signed |
| SRV-SEC-003 GDPR | client-side delete + S1 cascade | Клиент удаляет namespace = всё стёрто |
| SRV-SEC-002 audit | client-side blob | Audit log в собственный namespace владельца |
| SRV-CMD-001 commands | client-side push | Команды = encrypted push payload |
| SRV-CONFIG-001 (F-3 manifests) | **отдельный CDN** | Не часть user-server |
| SRV-PREFS-001 | S0 | Внутри блоба user preferences |
| SRV-CRYPTO-008 algorithm migration | client-side | Каждый client мигрирует при open |
| SRV-CRYPTO-006 forward unsharing | client-side | Member-coordinated re-key |
| SRV-CRYPTO-005 Safety Number | client-side | Pubkey fingerprint сравнение |
| SRV-CRYPTO-004 social recovery | S0 + T2 | Backup blob в S0, attempt counter в T2 |
| SRV-SEC-001 App Check | cross-cutting | Middleware на всех Tier 0/1 endpoints |
| SRV-MONITOR-001 health push | client-side push | Health critical → client triggers `POST /push` |
| SRV-MONITOR-002 sensor ingest | S0 | Sensor data → encrypted blob |
| SRV-CONTACTS-002 shared contacts | S0 | Shared contacts blob внутри group namespace |
| SRV-CFG-006 named configs | S0 | Named config blobs внутри namespace |
| SRV-CRYPTO-002 key rotation | client-side | Client coordinates re-wrap |
| SRV-TRANSLATE-001 | build-time | Не серверный endpoint |
| SRV-SEC-006 sealed-at-rest | T4 | Threshold unsealing (отдельный operator concern) |

**Result**: с 26 функциональных требований v1 → **2 storage + 2 directory + 3 logic = 7 endpoint категорий v2.** Остальное переехало на клиент.

---

## Открытые вопросы (для дизайн-обсуждения)

1. **anonymousId — нужен ли вообще?** Если JWT issuance делает Google напрямую (Firebase Auth), а наш сервер только verifie'ет — anonymousId исчезает. Trade-off: тогда rate-limit per Google sub, что чуть менее private.

2. **Prekey directory (D1) — opt-in или mandatory?** X3DH async setup нужен для pairing flow когда recipient оффлайн. Если pairing всегда online (QR code in person) — prekeys не нужны, D1 упрощается до одного pubkey без prekey list.

3. **Subscription Tier 2 — отдельный endpoint или часть JWT?** Можно entitlement встроить в session JWT claims (refresh JWT раз в час с свежим entitlement). Тогда T3 исчезает как отдельный endpoint.

4. **Sealed unsealing для small deployment** — Shamir overhead неоправдан для < 100 users. Phase 1 (env var через secret manager) — допустим без threshold.

5. **NetworkConfigSource** — выносим в отдельный CDN сервис или встраиваем в user-server как отдельный Tier 0 namespace «published» (с server-known signing pubkey)? Лично я за отдельный CDN — меньше путаницы.

---

## Industry pattern catalogue (для AI-агента — куда смотреть)

| Наш Tier | Industry pattern | Production system |
|---|---|---|
| S0 blob storage | Envelope encryption + signed writes | Bitwarden vault, Tresorit |
| S1 namespace lifecycle | Anonymous registration | Signal registration |
| S2 sealed push | Sealed Sender + opaque targeting | Signal Sealed Sender |
| D1 pubkey directory | X3DH prekey server | Signal Identity Server |
| D2 push token directory | FCM-token anonymous registry | WhatsApp push routing |
| T1 JWT issuance | OAuth proxy без identity disclosure | Apple Hide My Email pattern |
| T2 vault counter | HSM-protected attempt counter | Signal SVR, WhatsApp E2E Backup |
| T3 entitlement | Server-signed offline-verifiable token | Apple App Store receipts, Google Play Billing |
| T4 sealed unseal | Threshold cryptography | HashiCorp Vault Shamir unseal |

---

**End of server requirements.** Источник правды по operational плану — `server-roadmap.md`. Этот файл — спецификация интерфейса для zero-knowledge модели.
