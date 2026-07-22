---
id: TASK-60
title: 'Research: Push payload encryption + FCM 4KB constraint'
status: Draft
superseded-by: TASK-152
assignee: []
created_date: '2026-06-26 13:57'
labels:
  - phase-2
  - push
  - research
  - server
milestone: m-1
dependencies:
  - TASK-57
priority: high
ordinal: 60000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

> **SUPERSEDED (2026-07-08) by TASK-57 + `docs/dev/server-log.md`**. Владелец принял решение: отдельные server-research-task'и в launcher-репо не создаём. Server-thinking живёт **внутри** feature-task'а, которая до сервера коснулась (здесь — TASK-27 Messenger / TASK-31 Caregiver invite / любой future push-emitting endpoint). Research-scope этой task'и перенесён в [`docs/dev/server-log.md` Part B → Q-2 (Push payload encryption + FCM 4KB)](../../docs/dev/server-log.md). Deep research по overflow patterns (split vs fetch-trigger) и FCM metadata visibility произойдёт когда первый push-emitting endpoint берётся в работу. Эту карточку не удаляем — историческая справка о том, что вопрос был surfaced 2026-06-26.

## Что это простыми словами

Под zero-knowledge моделью push payload **должен быть зашифрован** (сервер не должен видеть eventType типа «config-updated» или «sos-triggered»). FCM имеет жёсткий лимит **4KB** на payload. Простой replay attack защиты тоже надо проектировать.

Три открытых вопроса:

1. **Помещаются ли наши event types в 4KB encrypted?** Нужны точные замеры. Для большинства — да, но photo-album-update с миниатюрой может не влезть.

2. **Что делать при overflow?** Два паттерна: split на N push с reassembly (сложно из-за FCM at-least-once delivery), или fetch-trigger (push говорит «есть новое», receiver делает GET) — проще, но requires receiver быть online.

3. **Что FCM сам логирует про opaque payload?** Если FCM пишет в свой Cloud Logging «delivered to token X with payload size 234 bytes» — это metadata, которую Google видит. Допустимо или нет.

## Зачем

Это **foundation для всех push-based event types**: TASK-5 (config-updated, **уже DONE** — может потребовать patch), TASK-10 (SOS), TASK-14 (Health), TASK-22 (Reminder), TASK-47 (Activity), TASK-27 (Messenger). Если выбрать неправильный pattern — каждое event type придётся переделывать.

## Состояние

Draft. Зависит от TASK-57. Блокирует все push-triggering task'и под zero-knowledge.

---

## Что входит технически

### FCM 4KB ограничение — точная природа

Не путать два FCM message types:

1. **Notification message**: Google показывает уведомление автоматически (title + body). Limit: **4KB total payload включая metadata**. Доставка optimistic — Google может drop'ить при offline (TTL default 4 weeks).
2. **Data message**: вообще нет UI auto-show, доставляется в `onMessageReceived` callback на receiver app. Limit: **4KB total payload**. App **обязан** обрабатывать сам.

Для zero-knowledge — **только data message**, потому что notification message требует Google знать title/body. Auto-показ невозможен если payload encrypted.

**Receiver side**: при data message receiver app обязан декриптовать → сформировать local notification → показать пользователю. Это требует **wake up app** даже если backgrounded. Battery cost — измерять.

**Реальные ограничения** (sourcing required):
- FCM HTTP v1 API: maximum message size 4096 bytes total, includes JSON envelope + data dict.
- Effective data payload after Google's JSON wrapping: ~3.5-3.8 KB usable.
- Base64 encoding ciphertext добавляет ~33% overhead. То есть real binary capacity ~ 2.5-2.8 KB binary.

### Event types — размерные оценки (AC#1)

Для каждого event type точно оценить:

| Event type | Plaintext fields | Estimated encrypted size | Fits in 4KB? |
|---|---|---|---|
| config-updated | `{nonce, sentAt, groupId, configBlobId, schemaVersion}` | ~150 bytes | ✅ |
| sos-triggered | `{nonce, sentAt, groupId, location?, deviceId}` | ~200 bytes | ✅ |
| health-critical | `{nonce, sentAt, groupId, alertKind, severity, deviceId}` | ~250 bytes | ✅ |
| pairing | `{nonce, sentAt, pubKey, channel info}` | ~400 bytes | ✅ |
| messenger-msg (text) | `{nonce, sentAt, groupId, ciphertextMsg, senderId}` | text-length-dependent | usually ✅ |
| messenger-msg (voice 5s clip) | with audio blob | **NO** — voice ≥10KB | needs fetch-trigger |
| album-update (thumbnail 50×50) | with thumbnail bytes | ~3 KB binary = 4 KB base64 | borderline |
| album-update (large) | full photo | **NO** | needs fetch-trigger |
| caregiver-invite | `{nonce, sentAt, inviteToken, expiresAt}` | ~300 bytes | ✅ |
| config-rewrite | `{nonce, sentAt, groupId, oldVersion, newVersion}` | ~150 bytes | ✅ |
| entitlement-expired (server-internal) | `{nonce, sentAt, tier, expiresAt}` | ~200 bytes | ✅ |

Замеры должны быть **реальные**, не estimated. Прогнать через actual XChaCha20-Poly1305 encryption + base64.

### Decision matrix: data-message vs split vs fetch-trigger (AC#2-3)

Три стратегии для каждого event type:

1. **Self-contained data message**: всё нужное упаковано в одно encrypted payload. Plug-and-play.
2. **Multi-part split**: payload разбит на N FCM messages, receiver reassembles по `multipartId` + `partIndex`. **Проблемы**: FCM at-least-once delivery + possible reordering + parts can be lost. Sender должен иметь retry semantics. Receiver hold буфер N seconds, drop incomplete.
3. **Fetch-trigger pattern**: push говорит только «есть новое в namespace X, blob Y». Receiver делает `GET /namespaces/{X}/blobs/{Y}` через encrypted channel. **Проблема**: receiver обязан быть online сразу (или сохранять trigger для later fetch). Latency: 2-3 seconds vs immediate.

**Industry pattern reference**:
- **Signal**: использует fetch-trigger для media. Push notifies, app downloads message body отдельно. Pattern works для пожилых телефонов потому что media опционально.
- **WhatsApp**: hybrid — small text inline, media через fetch-trigger.
- **iMessage Apple Push Service (APNS)**: 4KB limit ditto. Apple использует fetch-trigger для большого контента.

Decision matrix:
- Small events (config-updated, SOS, health) → self-contained (Option 1).
- Medium events (text message, small thumbnails) → self-contained если влезает, иначе fetch-trigger.
- Large events (voice, video, full photo) → **always fetch-trigger** (Option 3).
- Split (Option 2) — **избегать**, сложность не окупается.

### Replay protection (AC#4 detail)

Каждый encrypted payload содержит:
- `nonce: bytes` (random 12-24 bytes).
- `sentAt: unix-millis`.

Receiver side:
- `ReplayCache: Map<SenderPubKey, RingBuffer<Nonce>>` локально (DataStore).
- Buffer size: 100-1000 last nonces per sender.
- On receive: `nonce in cache → drop as replay`.
- `sentAt < now - 24h → drop as too-old` (защита от long-delayed replay через FCM TTL).

**Interaction с FCM at-least-once**: FCM может deliver duplicate (rare, но happens). Replay cache handles it — duplicate same nonce → drop silently, **не** показывать notification.

**Storage option для cache**:
- Sliding 100 last nonces в memory + DataStore persist — simple, ~3KB per sender. Для 5 senders = 15KB total.
- Bloom filter (1024 bits, 7 hashes) — smaller but false positives possible (~0.1%). False positive = silent drop legitimate message. **Не подходит** — нельзя терять real messages.
- **Sliding window**: simple ring buffer 100 entries. Recommended.

### Groupid в FCM metadata leak (AC#5)

Вопрос: что Google FCM логирует помимо payload?

Известно (из FCM documentation):
- FCM sees: `token` (destination), `payload size`, `collapse_key`, `priority`, `time_to_live`, `delivery_status`.
- FCM does NOT see: payload contents (если data message и we encrypt).

**collapse_key**: Google использует для дедупликации (старый message с тем же collapse_key replaced). Если collapse_key plaintext = `"config-updated:ownerUid42"` — leak.

**Mitigation**: collapse_key = opaque hash (`SHA-256(eventType || ownerUid || epoch)` first 16 bytes hex). Google видит opaque string, не знает meaning.

**Token-level leak**: Google всегда знает `token → device → Google account`. Это **unavoidable** при использовании FCM. Owner accepted risk — иначе нужен own push protocol (huge cost).

**FCM analytics**: если в Firebase Console включена FCM analytics — Google пишет per-message metadata (timestamp, token, delivery status). Recommendation: disable FCM analytics. Document это в server-roadmap.

### Compatibility review TASK-5 (AC#7)

TASK-5 (FCM config-updated push trigger) уже **DONE**. Реализована spec 019 F-5c (push foundation в `core/push/` + `workers/push/`).

Текущая implementation **partially zero-knowledge friendly**:
- ✅ Payload encrypted? — да, через `PushPayload` data class с ciphertext.
- ✅ Server sees opaque tokenIds? — частично, server resolve recipients через Firestore lookup (это **smart-server**, не zero-knowledge).
- ✅ Event type whitelist на сервере? — да, **это leak** под zero-knowledge.
- ✅ Per-event rate-limit на сервере? — да, **это leak** под zero-knowledge.

**Patch требуется** после TASK-60 decision:
- Server-side recipient resolution → client-side (client скачивает keyring группы).
- EventTypeRegistry → client-only (server forwards opaque ciphertext).
- Per-event rate-limit → per-JWT rate-limit only (anti-abuse).

**Estimate**: medium rewrite (~80-120 часов) на existing TASK-5 code base. Должен идти под отдельным task'ом TASK-5b или as follow-up.

### Decision document structure (AC#6 detail)

`docs/dev/decisions/2026-XX-XX-push-payload-encryption.md`:

- Context: zero-knowledge model, push events required, FCM 4KB constraint.
- Options analysis per event type (table из AC#1).
- Decision: data-message + self-contained для small, fetch-trigger для large.
- Replay protection: sliding window 100 nonces per sender.
- Metadata hardening: opaque collapse_key, disable FCM analytics.
- Consequences для existing TASK-5 implementation.
- Exit ramp: own push protocol (WebPush + APNS) — when и почему.
- Regret conditions: FCM pricing change, Google deprecating data messages, replay attack patterns observed in beta.

---

## Контекст из обсуждения 2026-06-26

Mentor-сессия упомянула push payload encoding как одну из 10 слабых сторон V2 sketch'а:

> «Push payload в FCM constraints. FCM payload limit 4 KB. Что если group keyring > 4 KB? Я написал «split or fetch trigger», но это не решение, это handwave.»

TASK-60 — закрытие этого handwave'а через actual measurements + production pattern research.

Также важна цитата владельца про zero-knowledge:

> «Сервер не должен знать eventType типа «config-updated» или «sos-triggered».»

Это направляет research: всё encoding event types **обязано** быть encrypted внутри payload. Server forwards opaque ciphertext. Это меняет TASK-5 existing implementation (там сейчас сервер видит eventType).

### Связь с другими task'ами

- **TASK-5** (DONE) — нужен compatibility patch после decision. См. AC#7.
- **TASK-10 (SOS)**, **TASK-14 (Health)**, **TASK-22 (Reminder)**, **TASK-47 (Activity)** — все используют push event types, **обязаны** соответствовать decision.
- **TASK-27 (Messenger)** — voice/text messages в группе. Largest payload sizes. Probably fetch-trigger для voice.
- **TASK-28 (Album)** — photo shares. Always fetch-trigger.
- **TASK-32 (Audit log)** — sync events. Small payload, self-contained.

## Что НЕ делать в TASK-60

- НЕ реализовывать patches на TASK-5 — это будет отдельный TASK-5b.
- НЕ выбирать group E2E protocol — это TASK-58.
- НЕ проектировать audit log structure — это TASK-32.
- НЕ менять FCM service account / hosting — это outside scope.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Замер: какие event types физически помещаются в 4KB encrypted payload (config-updated, sos-triggered, health-critical, pairing, messenger-msg, album-update, caregiver-invite). Конкретные bytes для каждого
- [ ] #2 Decision matrix: data-message vs notification-message FCM — какие event types куда
- [ ] #3 Solution для overflow: split на N push с reassembly, или fetch-trigger pattern (push = «есть новое в namespace X», receiver GET-ит blob)
- [ ] #4 Replay protection: nonce window size, dedup storage на receiver (sliding 100 last? bloom filter?), interaction с FCM at-least-once delivery
- [ ] #5 Groupid в FCM notification metadata — opaque требование, замер что FCM сам логирует
- [ ] #6 Документ docs/dev/decisions/2026-XX-XX-push-payload-encryption.md
- [ ] #7 TASK-5 (FCM config-updated, Done) ревью на compatibility — нужен ли patch existing implementation
<!-- AC:END -->
