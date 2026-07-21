# Server Log — накопитель для будущего own-server repo

**Что это**: единый файл, куда каждая feature-task в launcher-репо записывает свои server-side observations (принятые решения + открытые вопросы + обнаруженные противоречия). Когда придёт время строить own-server (отдельный repo per правило 8 CLAUDE.md) — этот файл = входной context. Никаких дополнительных research-tasks здесь не создаём; server-thinking живёт **внутри** feature-task'а, а результат — здесь.

**Snapshot date**: 2026-07-08 (initial, TASK-57).

**Заполняется автоматически**: skill [`checklist-zero-knowledge-server`](../../.claude/skills/checklist-zero-knowledge-server/SKILL.md) на каждой feature spec'е, касающейся сервера. Ручные правки — только на pre-MVP этапе (пока skill не работает).

---

## Не путать с

| Файл | Что делает | Отличие |
|---|---|---|
| [`docs/architecture/server.md`](../architecture/server.md) | **Snapshot** текущей модели сервера (Cloudflare Worker, endpoints, storage tiers). | Snapshot ↔ log: server.md = "что сейчас", server-log.md = "что накопили для будущего own-server". |
| [`docs/dev/server-requirements.md`](server-requirements.md) | V2 sketch — что сервер должен делать (Tier 0/1/2, S0/S1/S2 endpoints). | Sketch редкое обновление; server-log — растёт с каждой feature-task. |
| [`docs/dev/server-roadmap.md`](server-roadmap.md) | Migration plan — SRV-* задачи "когда переезжаем". | Roadmap = ops-план; server-log = design context для того ops-плана. |
| [`docs/dev/client-requirements-for-zero-knowledge-server.md`](client-requirements-for-zero-knowledge-server.md) | Что клиент должен уметь, чтобы сервер мог быть тупым. | Client-side delta; server-log — server-side мысли. |
| [`docs/dev/server-context-for-ai-agent.md`](server-context-for-ai-agent.md) | Standalone briefing для внешнего AI-чата. | Briefing = самодостаточный документ на момент подгрузки; server-log = растущий log. |

---

## Правила консистентности

1. **Перед записью нового Entry в Part A** skill (или автор) сверяется с существующими Part A entries. Если та же тема уже освещена → ссылка на existing entry, дублирование не создаём.
2. **Если новое решение противоречит existing Part A entry** → Entry идёт в Part C (contradiction), не в Part A. Part C блокирует переход source feature-task в Done, пока противоречие не разрешено.
3. **Открытые вопросы Part B закрываются** через обновление Part A (не удаление из B, а перенос). Q-N сохраняет свой ID навсегда для traceability.
4. **Каждый Entry имеет метаданные**: дата, source (task-N / spec / commit), tier (0/1/2 если применимо), refs.

---

## Part A — Confirmed patterns

Решения, принятые в feature-tasks и подтверждённые как соответствующие CLAUDE.md rule 13 (zero-knowledge server posture). Каждая новая feature-task сверяется с этим списком перед тем как принять свои server-side decisions.

### A-1 · Sealed blob storage (Tier 0 default)
- **Source**: TASK-57 (initial, из V2 sketch)
- **Date**: 2026-06-26 (V2 write-up) / 2026-07-08 (perekopiruem sюда)
- **Tier**: 0
- **Pattern**: сервер хранит `(nsId, key) → (ciphertext, signature, version)`. Ничего не расшифровывает, не понимает семантику blob'а. Authorization — Ed25519 signature от ownerSigningPubKey.
- **Applies to**: config sync, message envelope storage, photo blob storage, generic bucket storage (TASK-4, TASK-66, TASK-27, TASK-28, TASK-11).
- **Refs**: [`server-requirements.md § Tier 0 endpoints S0`](server-requirements.md).
- **Industry reference**: Tresorit envelope wrapping, WhatsApp E2E Encrypted Backup (assumption-level, needs deep validation before first prod implementation — см. Q-3).
- **Update (2026-07-21, TASK-141 Part D)**: the `version` the server stores/compares is now an **opaque dotted string** (`"1.0"`), not an integer. The backup Worker (`workers/backup`) treats it as opaque per rule 13 — it never parses business meaning, only orders `minReaderVersion` against `MAX_SUPPORTED_SCHEMA_VERSION` via a `versionOrder()` twin of the firestore.rules helper (anti-lockout ceiling; it gates the reader field, not the diagnostics-only `schemaVersion`). Firestore rules gate the same field through the same ordinal. No new server touch point, no new metadata — the blob stays sealed; only the version-field encoding changed.

### A-2 · Signature-based authorization (не ACL graph)
- **Source**: TASK-57 (initial)
- **Date**: 2026-06-26
- **Tier**: 0 / 1
- **Pattern**: сервер не хранит "кто кому помощник / кто в какой группе". На каждый write требуется Ed25519 signature от namespace-owner key. Membership — client-coordinated keyring blob **внутри** namespace, сервер не знает список.
- **Applies to**: любой endpoint с authorization requirements.
- **Refs**: TASK-102 (revoke policy — client-side reconciliation), TASK-108 (metadata privacy).
- **Industry reference**: Signal Sender Keys, MLS (RFC 9420 group membership state client-side).

### A-3 · Opaque identifiers на server-side
- **Source**: TASK-57 (initial)
- **Date**: 2026-06-26
- **Tier**: 0 / 1 / 2
- **Pattern**: все ID видимые серверу — opaque UUID, не производные от identity-provider primary keys (Google `sub`, email, phone). Mapping `userUid → namespace` — только на клиенте. Никаких URL типа `/users/{uid}/data/{key}` — только `/namespaces/{nsId}/blobs/{key}`.
- **Applies to**: любой endpoint contract.
- **Refs**: constitution.md строка 425 (opaque identifiers reaching the server — уже частично зафиксировано).

### A-4 · Client-side history rotation (не server-side retention)
- **Source**: TASK-57 (initial)
- **Date**: 2026-06-26
- **Tier**: 0
- **Pattern**: сервер поддерживает cron-time-based retention (TTL header, client указывает при PUT). Business-rule-based retention («keep last 10 configs», «delete when refcount=0») — на клиенте через LIST + DELETE.
- **Applies to**: config history (TASK-13 VersionedConfigViewer), photo/contact media (TASK-11), message queue (TASK-27).
- **Refs**: [`server-requirements.md § S0 blob storage`](server-requirements.md).

### A-5 · Push forwarding без event type
- **Source**: TASK-57 (initial)
- **Date**: 2026-06-26
- **Tier**: 0
- **Pattern**: push payload шифруется под shared key группы. Сервер получает `(targetTokens[], encryptedPayload, collapseKey)` — forwards opaque ciphertext в FCM. Не понимает eventType, не routing'ует по content.
- **Applies to**: TASK-27 messenger push, TASK-31 caregiver invite push, будущие push events.
- **Refs**: [`server-requirements.md § S2 push delivery`](server-requirements.md). См. также Q-4.

### A-7 · Backlog audit table (2026-07-08 TASK-57 review)

Классификация backlog-task'ов по риску конфликта с rule 13 zero-knowledge posture. Skill `checklist-zero-knowledge-server` использует эту таблицу как reference — когда классифицированный task берётся в работу, skill автоматически перепроверит его спеку против принципов и обновит эту секцию + Part B если появятся новые questions. Таблица снята "снимком" на 2026-07-08 против актуального backlog (не устаревший 2026-06-26 sketch).

**Категории**:
- 🟢 **safe** — task концептуально соответствует rule 13, при пере-review skill'ом ожидаем pass.
- 🟡 **at-risk** — task в текущей форме может нарушить rule 13; при переходе в work skill обязан pre-spec review.
- 🔴 **needs-reset** — task построен на "smart-server" assumption'ах; концепция требует переосмысления перед `/speckit.specify`.

| TASK | Название | Категория | Причина |
|---|---|---|---|
| TASK-4 | Config sync (Verification) | 🟢 safe | Wire format opaque blob, storage per opaque key. Уже соответствует. |
| TASK-6 | Root Key Hierarchy + Owner Recovery | 🟡 at-risk | Recovery vault Tier 2 (Q-1). Ensure vault opaque + counter не reveals ownership. |
| TASK-8 | Admin App + QR Pairing | 🟡 at-risk | Pairing может привнести helper graph. Rule 13 требует opaque handoff. |
| TASK-9 | Contact Tiles + Handoff Calling | 🟢 safe | Локально; сервер не касается. |
| TASK-10 | SOS Capability | 🟡 at-risk | Push routing — encrypted payload, opaque tokens. |
| TASK-11 | Contact Photos | 🟢 safe | Blob storage per rule 13 A-1. |
| TASK-12 | Account Deletion Flow | 🟢 safe | Client cascade DELETE. |
| TASK-13 | VersionedConfigViewer + Layout Editor | 🟢 safe | Client LIST+DELETE per A-4. |
| TASK-14 | Phone Health Monitoring | 🔴 needs-reset | Sketch V2 явно outlaws server-side «health critical → auto-push admin». Client-driven push only. |
| TASK-15 | Subscription Server Timer | 🟡 at-risk | Tier 2 T3 (Q-11). Ensure entitlement opaque. |
| TASK-17 | Android Deep Integration Steps | 🟢 safe | Клиентское. |
| TASK-19 | Config Sync (S-3 stream) | 🟡 at-risk | Проверить что server не understands eventType. |
| TASK-20 | Config Copy Between Own Devices | 🟡 at-risk | Multi-device (Q-13). |
| TASK-21 | Account Recovery + 2FA escrow | 🟡 at-risk | Recovery vault (Q-1) + 2FA escrow — ensure opaque. |
| TASK-23 | Provider Recipe Catalogue | 🟡 at-risk | NetworkConfigSource (Q-12) — CDN vs встроить. |
| TASK-26 | iOS Admin Preset | 🟢 safe | Клиентское. |
| TASK-27 | Elderly-Friendly Messenger | 🟡 at-risk | Group + push — Q-2 (4KB), Q-8 (keyring format). |
| TASK-28 | Full Shared Family Album | 🟡 at-risk | Group storage. Ensure opaque per A-1. |
| TASK-29 | Android TV Preset | 🟢 safe | Клиентское. |
| TASK-30 | Wearable Health Monitoring | 🔴 needs-reset | Sensor ingest sketch V2 outlaws server-side timeseries. Client-driven blob writes. |
| TASK-31 | Caregiver Remote Invite + LinkInvitePairingChannel | 🟡 at-risk | LinkId concept ⚠ — ensure not exposing pairing graph. |
| TASK-32 | Audit Log Infrastructure | 🔴 needs-reset | Sketch V2: audit log MUST be client-side encrypted blob, not server-side log. |
| TASK-34 | Clinic / partner B2B integration | 🟡 at-risk | Preset-parameterized. Phase-3+. |
| TASK-39 | Social recovery (re-open D-25 OWD-4) | 🔴 needs-reset | Пересекается с ADR-008 SUPERSEDED. Helper graph переработать на client-coordinated. |
| TASK-40 | Multi-device per user beyond F-4 | 🟡 at-risk | Q-13 namespace ownership + Q-7 transfer. |
| TASK-41 | Key rotation / forward secrecy | 🟡 at-risk | Q-7 namespace ownership transfer. |
| TASK-42 | Family group encryption Signal-style | 🟡 at-risk | Q-8 keyring format. Priority bumped High per TASK-57 AC #7. |
| TASK-43 | Wearable monitoring full | 🔴 needs-reset | Same as TASK-30. |
| TASK-44 | Security sensors integration (smart-home) | 🔴 needs-reset | Same pattern — sensor ingest client-side. |
| TASK-46 | Shared admin contact book | 🟡 at-risk | Group storage + Q-15 atomic keyring writes. |
| TASK-47 | Family Activity Challenges | 🔴 needs-reset | Gamification event log — sketch V2 outlaws server-side «X выполнил активность». |
| TASK-48 | Tamper-resistance L1+L2+L3 | 🟢 safe | Client-side attestation, rule 13 не пересекается (см. TASK-48 revision note). |
| TASK-67 | Pairing Feature + pairing-edges bucket | 🟡 at-risk | Pairing graph ⚠. Q-10 prekey directory. |
| TASK-101 | Peer confirmation on recovery | 🟡 at-risk | Depends TASK-100 Done. Ensure opaque helper decisions. |
| TASK-102 | Device management MLS group | 🟢 safe | MLS = client-coordinated per A-2. |
| TASK-103 | Remote app lock | 🟡 at-risk | Ensure lock trigger opaque; no server-side "who owns which device". |
| TASK-104 | KeyPackage rate limit | 🟢 safe | Uses TASK-105 baseline (A-6). Q-6 pool cap validation. |
| TASK-105 | Server-side abuse defense baseline | 🟢 safe | Rule 12 companion; already merged principles. |
| TASK-106 | Identity signup gate | 🟡 at-risk | Q-9 anonymousId. |
| TASK-108 | Metadata privacy T0/T1/T2 | 🟡 at-risk | Q-4 metadata visibility. |
| TASK-109 | Server-side anti-brute-force | 🟢 safe | Own-server phase, per rule 12 + 13. |
| TASK-111 | Signed upload tokens + server-side quotas | 🟡 at-risk | Quota logic — ensure по opaque namespace, не по content. |
| TASK-112 | KeyVault port boundary | 🟢 safe | Clientside port design. |
| TASK-114 | Encrypted co-admin display directory | 🟡 at-risk | Directory ⚠ Tier 1 pattern applicable. |
| TASK-115 | Family app onboarding via Install Referrer | 🟢 safe | Клиентское + external Play Store. |
| TASK-116 | Iconic pairing challenge | 🟡 at-risk | Pairing UX; ensure не exposes graph. |
| TASK-117 | Universal attestation mechanism | 🟡 at-risk | Attestation touches server. |

Не в таблице (не сервер-related или Done): TASK-1..3, 5, 7, 16, 18, 22, 24, 25 (superseded), 33, 36, 37, 38, 45, 49, 50-56, 58-66 (client-focused, phase-5 parking, meta или superseded), 68-73 (profile composition v2), 100 (Done), 107, 110, 113, 118.

**Skill enforcement**: этот snapshot **не обновляется вручную**. При взятии at-risk / needs-reset task'а в `In Progress` skill `checklist-zero-knowledge-server` перепрогонит его spec.md против rule 13, обновит категорию (safe / at-risk / needs-reset → resolved) прямо здесь и добавит Part A entry или Part B question по итогам.

### A-6 · Zero-trust baseline (rule 12) — orthogonal к zero-knowledge
- **Source**: TASK-105 Decision (2026-07-02, Draft)
- **Date**: 2026-07-02
- **Tier**: applies to всем endpoints
- **Pattern**: каждый endpoint требует JWT verify + rate limit + input validation + observability + explicit failure modes. Это **дополняет** zero-knowledge posture, не заменяет. Zero-trust = "не доверяй запросам", zero-knowledge = "не знай контент запросов".
- **Applies to**: все server endpoints.
- **Refs**: CLAUDE.md rule 12, [`docs/architecture/server.md § Zero-trust baseline`](../architecture/server.md).

---

## Part B — Open questions

Нерешённые вопросы, накопленные с указанием источника. Каждый Q закрывается через **перенос** в Part A (Q сохраняет свой ID). Каждая feature-task, планирующая работать над Q → сначала обновляет здесь статус, потом решает в своей spec'е.

### Q-1 · Recovery vault anti-brute-force — SVR vs OPAQUE vs simple HMAC
- **Source**: перенесено из TASK-59 (закрыт как superseded 2026-07-08)
- **Date**: 2026-06-26 (surfaced) / 2026-07-08 (moved here)
- **Tier**: 2 (единственный оправданный Tier 2 case)
- **Context**: recovery vault требует server-side counter чтобы закрыть Clear App Data / factory reset / root bypass. Три industry pattern'а: Signal SVR (Software Vault Rooms + Intel SGX), OPAQUE protocol (RFC 9807), simpler MAC-based counter.
- **Blocked by**: none, ждёт feature-task'а recovery (TASK-6 или TASK-21).
- **Trigger for deep research**: когда TASK-6 берётся в работу и начинается server-side implementation.
- **Refs**: TASK-59 archive, [`server-requirements.md § Tier 2 T2 recovery counter`](server-requirements.md).

### Q-2 · Push payload encryption + FCM 4KB constraint
- **Source**: перенесено из TASK-60 (закрыт как superseded 2026-07-08)
- **Date**: 2026-06-26 (surfaced) / 2026-07-08 (moved here)
- **Tier**: 0
- **Context**: FCM ограничивает payload 4KB. Если шифровать под group key MLS-стиля (Signal подход — server видит ciphertext + opaque group ID) — как поместить sender identity + preview + metadata в 4KB? Или использовать placeholder push + fetch по opaque ID?
- **Blocked by**: none, ждёт feature-task'а messenger (TASK-27) или caregiver invite (TASK-31).
- **Trigger for deep research**: когда первый push-emitting endpoint берётся в работу.
- **Refs**: TASK-60 archive, [`server-requirements.md § S2 push delivery`](server-requirements.md), правило 10 CLAUDE.md (notification minimization).

### Q-3 · Sealed-at-rest — hardware attestation vs threshold unsealing vs plain AES
- **Source**: TASK-57 (initial, из V2 sketch Tier 2 T4)
- **Date**: 2026-06-26
- **Tier**: 2
- **Context**: sketch V2 упоминает "sealed-at-rest требует M-of-N shards с distributed custody — provider один не может unseal". Это **допущение** уровня "wave hand". Три варианта: (a) hardware attestation (Intel SGX / AMD SEV) — сложно на Cloudflare, требует own-server; (b) threshold unsealing через M-of-N distributed custody; (c) plain AES с shared key которого не знает provider (но тогда где хранить key?).
- **Blocked by**: перенос на own-server (правило 8, Phase-3+).
- **Trigger for deep research**: миграция на own-server или incident.
- **Refs**: [`server-requirements.md threat model row "hosting provider insider"`](server-requirements.md).

### Q-4 · Metadata visibility trade-off (TASK-108 T0/T1/T2)
- **Source**: TASK-108 (Discussion, HIGH priority)
- **Date**: 2026-06-26
- **Tier**: crosscuts 0/1/2
- **Context**: даже с opaque IDs сервер видит **паттерны** — timing, размер blob'ов, частота запросов от одного JWT. Насколько это ok? T0 (только opaque) — WhatsApp base, Signal Sealed Sender закрывает sender identity через blind auth. Что мы выбираем на MVP?
- **Blocked by**: TASK-108 Decision закрытие.
- **Trigger**: TASK-108 Session 1.
- **Refs**: [TASK-108](../../backlog/tasks/task-108%20-%20Decision-Metadata-privacy.md).

### Q-5 · Rate limit dimension (per-identity → per-device migration)
- **Source**: TASK-105 Decision (dependency от TASK-101 multi-device)
- **Date**: 2026-07-02
- **Tier**: crosscuts
- **Context**: TASK-105 baseline = per-identity (JWT claim `identity_id`). SRV-SEC-004 говорит "TODO per-device". Rogue device в legitimate identity может spam'ить под valid JWT — это purpose zero-trust. Когда переходим на per-device rate limit? Нужен ли device attestation в JWT?
- **Blocked by**: TASK-101 multi-device Decision, TASK-40.
- **Trigger**: первый rogue device incident или proactive перед soft launch.
- **Refs**: [`docs/dev/server-roadmap.md § SRV-SEC-004`](server-roadmap.md), TASK-105.

### Q-6 · Keypackage pool cap validation
- **Source**: TASK-104 Decision (Draft)
- **Date**: 2026-07-07
- **Tier**: 0 / 1
- **Context**: pool cap = 100 per identity (Signal-inspired). Dedup TTL = 10 min. Last-resort rotation = 7d. Эти числа — assumption-level, не validated production references. При scaling > 10k users — достаточно? Или нужен sliding window / per-requester quota?
- **Blocked by**: none.
- **Trigger**: TASK-104 implementation.
- **Refs**: TASK-104, [`docs/architecture/crypto.md § keypackage-pool`](../architecture/crypto.md).

### Q-7 · Namespace ownership transfer (после key rotation / device migration)
- **Source**: TASK-57 (initial, из V2 sketch)
- **Date**: 2026-06-26
- **Tier**: 0
- **Context**: namespace прибит к ownerSigningPubKey immutable. При key rotation (TASK-41) или device migration (TASK-101) — как передать ownership? Варианты: (a) namespace immutable, migration = new namespace + client re-associates; (b) explicit transfer endpoint с dual-signature.
- **Blocked by**: TASK-41 (key rotation), TASK-101 (multi-device).
- **Trigger**: первый из них.
- **Refs**: [`server-requirements.md § S1 namespace lifecycle`](server-requirements.md).

### Q-9 · anonymousId — нужен ли отдельный UUID vs использовать Google `sub` напрямую
- **Source**: TASK-57 review, `server-requirements.md` Open Question 1 (2026-06-26)
- **Date**: 2026-06-26 (surfaced) / 2026-07-08 (moved here)
- **Tier**: 2 (JWT issuance)
- **Context**: sketch V2 T1 говорит `googleSub → anonymousId` mapping. Если Firebase Auth сам делает issuance, а наш сервер только verify — anonymousId не нужен, rate-limit будет per Google `sub`. Trade-off: чуть менее private, но проще. Влияет на весь identity dance TASK-106.
- **Blocked by**: TASK-106 (identity signup gate — Discussion).
- **Trigger**: TASK-106 Session 1.
- **Refs**: [`server-requirements.md § T1 JWT issuance`](server-requirements.md), TASK-106.

### Q-10 · Prekey directory (D1) — mandatory или opt-in
- **Source**: TASK-57 review, `server-requirements.md` Open Question 2 (2026-06-26)
- **Date**: 2026-06-26 / 2026-07-08
- **Tier**: 1
- **Context**: X3DH async setup нужен если recipient offline при pairing. Если pairing всегда online (QR code face-to-face) — prekey list не нужен, D1 сжимается до single-pubkey entry без prekey consumption. Влияет на pairing flow TASK-67 и messenger TASK-27.
- **Blocked by**: TASK-67 (pairing) или TASK-27 (messenger).
- **Trigger**: первый из них.
- **Refs**: [`server-requirements.md § D1 pubkey directory`](server-requirements.md), TASK-67.

### Q-11 · Subscription entitlement — Tier 2 endpoint vs claim в JWT
- **Source**: TASK-57 review, `server-requirements.md` Open Question 3 (2026-06-26)
- **Date**: 2026-06-26 / 2026-07-08
- **Tier**: 2
- **Context**: T3 GET /entitlement или встроить `tier` + `expiresAt` в session JWT claims (refresh JWT каждый час). Второй вариант убирает отдельный endpoint, но требует более частого JWT refresh. Влияет на TASK-15 subscription server timer.
- **Blocked by**: TASK-15.
- **Trigger**: TASK-15 берётся в работу.
- **Refs**: [`server-requirements.md § T3 subscription entitlement`](server-requirements.md), TASK-15.

### Q-12 · NetworkConfigSource — отдельный CDN vs встроить в user-server
- **Source**: TASK-57 review, `server-requirements.md` Open Question 5 (2026-06-26)
- **Date**: 2026-06-26 / 2026-07-08
- **Tier**: 0 (если встроить) / N/A (если CDN)
- **Context**: F-3 profile manifests (bundled recipes catalogue, provider recipes) — публикация от нас пользователям, signed manifest. Отдельный CDN (Cloudflare Pages / R2 + signing key) vs встроить как "published namespace" с server-known signing pubkey. Sketch V2 favours separate CDN. Влияет на TASK-23 (Provider Recipe Catalogue) и любые future published-config фичи.
- **Blocked by**: TASK-23 или P-8 Provider Recipe Catalogue.
- **Trigger**: TASK-23 берётся в работу.
- **Refs**: [`server-requirements.md § Что НЕ делает сервер`](server-requirements.md), TASK-23.

### Q-13 · Multi-device per UID — cross-device namespace sync
- **Source**: TASK-57 review, `client-requirements-for-zero-knowledge-server.md` Open Question 1 (2026-06-26). Пересекается с [Q-7 namespace ownership transfer](#q-7--namespace-ownership-transfer-после-key-rotation--device-migration).
- **Date**: 2026-06-26 / 2026-07-08
- **Tier**: 0 / 1
- **Context**: один Google account на нескольких устройствах — device 1 создаёт namespace, device 2 (same UID) хочет к нему access. Варианты: (a) каждое устройство имеет свои opaque IDs + отдельный namespace, sync через shared blob; (b) shared namespace + multi-device keyring; (c) MLS group для owner's own devices. Влияет на TASK-40 (Multi-device per user) и pairing.
- **Blocked by**: TASK-40, TASK-101 (multi-device first-class).
- **Trigger**: TASK-40 берётся в работу.
- **Refs**: [`client-requirements-for-zero-knowledge-server.md § C1 Namespace`](client-requirements-for-zero-knowledge-server.md), TASK-40, TASK-101, Q-7 (related).

### Q-14 · Group keyring blob size limit — линейный рост vs cap
- **Source**: TASK-57 review, `client-requirements-for-zero-knowledge-server.md` Open Question 2 (2026-06-26)
- **Date**: 2026-06-26 / 2026-07-08
- **Tier**: 0
- **Context**: keyring blob растёт линейно с числом member'ов (~100 bytes per member). До 100 members ~10 KB — acceptable. Свыше — split на shards? Family group MVP <10 members, но clinic preset (Phase-3+) может достигать 100+. Влияет на TASK-42.
- **Blocked by**: TASK-42 или clinic preset spec (Phase-3+).
- **Trigger**: family group scale test или clinic preset design.
- **Refs**: [`client-requirements-for-zero-knowledge-server.md § C2 Group`](client-requirements-for-zero-knowledge-server.md), TASK-42.

### Q-15 · Keyring atomic writes — optimistic locking single-writer vs CRDT multi-writer
- **Source**: TASK-57 review, `client-requirements-for-zero-knowledge-server.md` Open Question 4 (2026-06-26)
- **Date**: 2026-06-26 / 2026-07-08
- **Tier**: 0
- **Context**: одновременное обновление keyring двумя admin'ами (add member + remove member одновременно). Sketch favours single-writer optimistic locking (проще, конфликты редки, merge только on conflict). Альтернатива — CRDT-style operation log с автоматическим merge. Влияет на TASK-102 (revoke policy) и multi-admin flows.
- **Blocked by**: TASK-102, TASK-46 (Shared admin contact book).
- **Trigger**: TASK-102 implementation.
- **Refs**: [`client-requirements-for-zero-knowledge-server.md § C20 KeyringEditConflictResolution`](client-requirements-for-zero-knowledge-server.md), TASK-102, TASK-46.

### Q-16 · Self-state backup — часть recovery vault vs отдельный backup blob
- **Source**: TASK-57 review, `client-requirements-for-zero-knowledge-server.md` Open Question 5 (2026-06-26)
- **Date**: 2026-06-26 / 2026-07-08
- **Tier**: 0 или 2 (recovery vault)
- **Context**: клиент теряет устройство → recovery через passphrase → нужны его opaque IDs (namespaces список, tokenIds, etc.) чтобы re-associate. Кладём в recovery vault (один passphrase на всё) vs отдельный backup blob. Sketch favours recovery vault для простоты. Влияет на TASK-6 (Root Key Hierarchy + Owner Recovery).
- **Blocked by**: TASK-6.
- **Trigger**: TASK-6 implementation.
- **Refs**: [`client-requirements-for-zero-knowledge-server.md § C18 RecoveryVaultBackup`](client-requirements-for-zero-knowledge-server.md), TASK-6.

### Q-8 · Group keyring blob format — MLS TreeKEM vs Sender Keys vs custom
- **Source**: TASK-42 (Family group encryption migration)
- **Date**: 2026-06-26
- **Tier**: 0 (server видит blob как opaque)
- **Context**: sketch V2 говорит "membership — client-coordinated keyring blob внутри namespace". Формат этого blob — MLS TreeKEM (RFC 9420, наш выбор per TASK-104) или Sender Keys (Signal group protocol). Формат влияет на C2 (group keyring) client component.
- **Blocked by**: TASK-42 Decision (сейчас parking-lot LOW).
- **Trigger**: TASK-42 priority bump (запланирован в TASK-57 AC #7).
- **Refs**: TASK-42, TASK-104 openmls selection, [`docs/architecture/crypto.md § group-protocol`](../architecture/crypto.md).

---

## Part C — Contradictions detected

Противоречия между новыми feature-decisions и existing Part A entries. Каждое противоречие **блокирует** переход source feature-task в Done, пока не разрешено (либо изменением Part A entry, либо изменением feature-decision).

_Пусто на 2026-07-08._

---

## Journal — chronological log правок

| Дата | Изменение | Source |
|---|---|---|
| 2026-07-08 | Initial version. Part A = 6 patterns из V2 sketch + TASK-105 baseline. Part B = 8 open questions (Q-1/Q-2 из superseded TASK-59/60, Q-3..Q-8 из sketch V2 + пересечений с TASK-104/105/108). Part C пустой. | TASK-57 |
| 2026-07-08 | Part B extended: Q-9..Q-16 добавлены после assumption-level review двух source-документов (server-requirements.md v2 open questions 1-5 → Q-9/Q-10/Q-11/Q-12; client-requirements-for-zero-knowledge-server.md open questions 1-5 → Q-13/Q-14/Q-15/Q-16). Q-3 (sealed-at-rest) already covered sketch Q-4. Q-2 (push 4KB) already covered client Q-3. Итого Part B = 16 open questions. | TASK-57 AC #3, #4 |
| 2026-07-08 | Part A extended: A-7 Backlog audit table (2026-07-08 snapshot). 43 task'а классифицированы safe / at-risk / needs-reset. Skill автоматически перепрогонит каждый при взятии в работу. | TASK-57 AC #5 |
| 2026-07-21 | A-1 note: recovery-blob version на сервере (backup Worker + firestore.rules) переведена с integer на opaque dotted string; Worker гейтит `minReaderVersion` ordinally (versionOrder twin), версия остаётся opaque per rule 13. Новых server touch point'ов нет. | TASK-141 Part D |
