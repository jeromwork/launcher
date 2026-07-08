---
kind: architecture-domain
domain: server
audience: [owner, ai-agent]
purpose: Server layer snapshot — Cloudflare Worker runtime, zero-trust baseline, crypto-relevant endpoints, migration path to own Go microservices.
components:
  - id: runtime-mvp
    choice: Cloudflare Worker (TypeScript)
    reason: free tier, edge-local rate limiting via bindings, no cold start
    decision-task: TASK-105
    decision-status: draft
    location: push-worker/
    exit-ramp: Go microservices per workers/*, see server-roadmap.md
  - id: url-scheme
    choice: /v1/<domain>/<action> versioned
    decision-task: TASK-105
    decision-status: draft
    exit-ramp: major version bump on breaking change (rule 5)
  - id: wire-format
    choice: '{ schemaVersion: N, data | error } JSON'
    decision-task: TASK-105
    decision-status: draft
    exit-ramp: rule 5 wire format evolution discipline (TASK-16)
  - id: jwt-verify
    choice: jose npm + JWKS memory cache 10 min
    decision-task: TASK-105
    decision-status: draft
    exit-ramp: go-jose (Go migration)
  - id: rate-limit-normal
    choice: Cloudflare RATE_LIMITER binding (edge, 60s window)
    decision-task: TASK-105
    decision-status: draft
    exit-ramp: go-redis/redis_rate
  - id: rate-limit-critical
    choice: Cloudflare Durable Object counter
    decision-task: TASK-109 (Paused — concrete DO schema pending)
    decision-status: paused
    exit-ramp: Go actor + Redis Cluster
  - id: rate-limit-dimension
    choice: per-identity (JWT claim identity_id)
    decision-task: TASK-105
    decision-status: draft
    exit-ramp: 'TODO(server-roadmap): per-device'
  - id: input-validation
    choice: zod schema
    decision-task: TASK-105
    decision-status: draft
    exit-ramp: go-playground/validator
  - id: observability
    choice: Structured JSON logs + Cloudflare Analytics Engine counters
    decision-task: TASK-105
    decision-status: draft
    exit-ramp: Prometheus + Grafana Loki
  - id: idempotency
    choice: natural dedup + Idempotency-Key header for state-modifying without natural bound
    decision-task: TASK-105
    decision-status: draft
last-synced: 2026-07-08
---

# Домен: Сервер

**Сервер отвечает за то, чтобы**:

1. **Доставить зашифрованные конверты** между устройствами (MLS Welcome / Commit / AppMessage, config sync payloads, push wake-ups) — не читая содержимого.
2. **Хранить одноразовые публичные ключи** (KeyPackage pool бабушки) чтобы её можно было добавить в группу пока она офлайн.
3. **Координировать concurrent-edits** (profile edit lock, group state) — чтобы два admin'а не потеряли изменения друг друга.
4. **Отбивать abuse** — rate limit, drain-defense, Sybil-defense (частично на клиенте) — под допущением что **любой request потенциально враждебный, даже с валидным JWT** (правило 12 zero-trust).

**Сервер НЕ отвечает за**:

- Расшифровку чего-либо (никаких секретов на сервере).
- Определение "кто может кого добавить/удалить" — это MLS group membership + profile reconciliation (см. [crypto.md § Revoke policy](crypto.md#revoke-policy)).
- Долгосрочное хранение сообщений (только очередь до FCM push + короткий poll fallback).

---

## Как читать этот документ

- **Snapshot** — что выбрано сейчас, где живёт, кто решил.
- **Крипто-relevant endpoints** — что за endpoint, какая крипто-задача его использует, ссылка на сценарий в crypto.md.
- **Migration path** — куда переезжаем на Go microservices, когда триггер (правило 8).
- **Zero-trust baseline** — обязательные свойства каждого endpoint'а (правило 12).

**Полный migration roadmap** (SRV-* entries) — [server-roadmap.md](../dev/server-roadmap.md).

---

## Runtime — Cloudflare Worker (MVP)

- **Что**: single-file TypeScript worker, deployed via `wrangler`. Edge-local execution — request и rate-limit-check исполняются в ближайшем к пользователю datacenter'е Cloudflare.
- **Почему**:
  - Free tier достаточен для MVP + Phase-2 (< 100k requests/day).
  - Rate limit bindings работают из коробки, edge-local — нет round-trip.
  - Нет cold start.
  - Wrangler CLI + wrangler secrets — простой deploy pipeline.
- **Где живёт**:

| Element | Path |
|---|---|
| Worker entry point | `push-worker/src/index.ts` |
| Middleware chain | `push-worker/src/middleware/` (JWT verify + rate limit + validate + observability) |
| Route handlers | `push-worker/routes/<domain>/` |
| Wire format contracts | `push-worker/contracts/` |
| Wrangler config | `push-worker/wrangler.toml` |
| Secrets management | `wrangler secret put <NAME>` — см. [secrets-cloudflare-worker skill](../../.claude/skills/secrets-cloudflare-worker/SKILL.md) |

- **Decision task**: [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md).
- **Exit ramp**: Go microservices per `workers/<domain>/` — см. [Migration path](#migration-path).

---

## Zero-trust baseline (правило 12, TASK-105)

**Каждый endpoint** обязан иметь **пять** required properties + опционально idempotency для state-modifying:

### 1. Authentication — JWT verify

- **Library**: `jose` npm.
- **JWKS cache**: memory, TTL 10 min (не перезапрашиваем ключи на каждый request).
- **Verify**: signature, expiration, `iss`, `aud`, clock skew ≤ 60s.
- **JWT claims used**: `identity_id` (rate limit dimension + roster check), `iat`, `exp`.
- **Exception `[public]`** — только с явным reasoning (например, endpoint issuance самого JWT, `/health`).
- **Anti-pattern (rejected)**: trust JWT для authorization без MLS roster check → attacker с чужим JWT получает group access. См. [топ-7 способов взорвать § 7](crypto.md#топ-7-способов-взорвать-систему-нашим-кодом).

### 2. Rate limiting — two tiers

**Tier 1 — normal (large blast radius acceptable)**: Cloudflare **RATE_LIMITER binding**, edge-local, 60s window.

**Tier 2 — critical (small blast radius required)**: Cloudflare **Durable Object counter** — sharded per key, атомарные операции. Используется для anti-brute-force на JWT issuance / password verify / recovery attempts.

**Dimension MVP**: `per-identity` (JWT claim `identity_id`). Второе измерение (`per-device`, `per-IP`) — `TODO(server-roadmap)` [SRV-SEC-004](../dev/server-roadmap.md).

**Ladder RATE_LIMITER → DO** — Baseline (TASK-105) декларировал, concrete DO schema pending TASK-109 (**Paused** — ждёт первого implementation endpoint TASK-105).

### 3. Input validation — zod schema

- **Library**: `zod`.
- **Что валидируется**: shape, types, size limits, format (base64, UUID, etc.).
- **Request size limit**: 1 MB default; overrides per-endpoint (KeyPackage batch — до 100 KB).
- **Failure**: 400 Bad Request + `{ error: 'malformed_payload', reason: <field> }`.

### 4. Observability — structured logs + counters

- **Log format**: JSON, one line per request. Fields: `ts`, `route`, `identity_id`, `status`, `duration_ms`, `rate_limit_hit?`, `error?`.
- **Counters**: Cloudflare Analytics Engine — `rate_limit_hit`, `auth_failure`, `malformed_payload`, `endpoint_5xx`.
- **Alert threshold**: 1% error rate 5 min sustained → owner notification (mechanism TBD — email? Slack? Sentry?).

### 5. Failure modes — explicit HTTP + payload

| Case | HTTP | Payload |
|---|---|---|
| Rate limit hit | 429 | `{ error: 'rate_limited', retry_after: <seconds> }` + `Retry-After` header |
| Auth failure | 401 | `{ error: 'unauthorized' }` |
| Malformed payload | 400 | `{ error: 'malformed_payload', reason: <field> }` |
| Storage tier down | 503 | `{ error: 'unavailable', retry_after: <seconds> }` |
| Not found | 404 | `{ error: 'not_found' }` |
| Conflict (edit lock) | 409 | `{ error: 'conflict', held_by: <identity_id>, expires_at: <ts> }` |

### 6. Idempotency (state-modifying endpoints only)

- **Natural dedup** предпочтителен — если endpoint по природе идемпотентен (e.g. `PUT /profile` — overwrite), no header needed.
- **`Idempotency-Key` header** — required для state-modifying endpoints без natural dedup (например `POST /v1/keypackage/publish`). Server ignores duplicate keys within 24h.

---

## URL scheme + wire format

**Path**: `/v1/<domain>/<action>` — versioned. Major version bump = new prefix `/v2/`. `/v1` deprecation window — минимум 6 месяцев после `/v2` GA.

**Request body**: `{ schemaVersion: N, ...payload }`.

**Response body**: `{ schemaVersion: N, data: { ... } }` или `{ schemaVersion: N, error: <code>, ...details }`.

**Почему versioned**: правило 5 wire format evolution (TASK-16 discipline). Клиенты разных версий сосуществуют — сервер обязан backward-compat читать хотя бы одну major версию назад.

---

## Крипто-relevant endpoints (карта)

Все endpoint'ы ниже — **transport-layer only**: сервер видит только зашифрованные конверты. Расшифровка происходит на устройстве получателя.

### KeyPackage pool

| Endpoint | Что делает | Crypto scenario |
|---|---|---|
| `POST /v1/keypackage/publish` | Устройство upload'ит batch одноразовых KeyPackages + опционально last-resort | [crypto.md Сценарий 2](crypto.md#сценарий-2--таня-подключается-admin-к-бабушкиному-планшету-qr-pairing--mls-handshake) шаг 1 |
| `POST /v1/keypackage/claim` | Устройство запрашивает один KP целевой identity для MLS Add | [crypto.md Сценарий 3](crypto.md#сценарий-3--bob-claimит-бабушкин-keypackage-защита-от-drain-атаки) |

- **Storage**: Cloudflare KV binding `KEYPACKAGE_POOL` (per-identity list), `LAST_RESORT_KEY` (per-identity single), `CLAIM_DEDUP` (TTL 600s).
- **Pool cap**: 100 per identity. Cap hit → drop oldest.
- **Claim dedup**: `(requester_identity_id, target_identity_id)` → same KP within 10 min.
- **Last-resort rotation**: 7 days (family default, preset field per TASK-104).
- **Decision task**: [TASK-104](../../backlog/tasks/task-104%20-%20Decision-KeyPackage-rate-limit.md).

### Group messaging

| Endpoint | Что делает | Crypto scenario |
|---|---|---|
| `POST /v1/group/send` | Fanout MLS Commit / Welcome / AppMessage на recipients | [crypto.md Сценарий 4](crypto.md#сценарий-4--таня-отзывает-петю-через-редактирование-профиля-revoke-via-reconciliation) шаг 8 |
| `GET /v1/group/inbox` | Polling fallback offline devices | (recovery / catch-up flows) |

- **Storage**: Cloudflare KV `GROUP_INBOX` (per-recipient inbox, TTL 30 days).
- **Push**: FCM topic per recipient identity — server видит recipient identity_id, не group_id.
- **Metadata visible to server** (T0 MVP): identity_id + group roster + timing. Sealed sender-style patterns (T2 tier) — parked [TASK-108](../../backlog/tasks/task-108%20-%20Decision-Metadata-privacy-what-server-sees.md).

### Profile edit lock (revoke via reconciliation)

| Endpoint | Что делает | Crypto scenario |
|---|---|---|
| `POST /v1/profile/lock` | Admin берёт edit lock перед изменением `authorized_devices` | [crypto.md Сценарий 4](crypto.md#сценарий-4--таня-отзывает-петю-через-редактирование-профиля-revoke-via-reconciliation) шаги 1-4 |
| `POST /v1/profile/unlock` | Release edit lock после upload'а нового профиля | |
| `PUT /v1/profile/{identity_id}` | Upload encrypted profile payload (MLS AppMessage) | |
| `GET /v1/profile/{identity_id}` | Sync profile для bab's device reconciliation | |

- **Storage**: Cloudflare KV `PROFILE_STORE` (encrypted MLS payload), `PROFILE_LOCKS` (TTL 300s = 5 min).
- **Conflict on lock held**: 409 + `held_by: <identity_id>, expires_at: <ts>`.
- **Force-release by owner** — TBD post-MVP.
- **Decision task**: [TASK-102](../../backlog/tasks/task-102%20-%20Decision-Revoke-policy.md).

### Remote app lock

| Endpoint | Что делает | Crypto scenario |
|---|---|---|
| `POST /v1/lock/trigger` | Admin запускает remote lock для украденного устройства | (защита украденного планшета) |
| `GET /v1/lock/status/{device_id}` | Poll device на unlock action | |

- **Behavior**: full logout + Keystore wipe = **cryptographic defense** (не UX). После lock — устройство must go through full recovery flow.
- **Preset fields** (5 штук в `deviceLock` namespace, per TASK-103): lockScreenBehavior, unlockMethod, offlineAutoLockDays, tee attestation policy, wipeVerificationRequired.
- **Decision task**: [TASK-103](../../backlog/tasks/task-103%20-%20Decision-Remote-app-lock-for-stolen-device.md).

### Identity (issuance + upgrade)

| Endpoint | Что делает | Notes |
|---|---|---|
| `POST /v1/identity/register` | LOCAL → CLOUD upgrade at first cloud action | Depends on TASK-106 signup gate (**Discussion**) |
| `POST /v1/identity/pair` | QR pairing complete notify | [TASK-67](../../backlog/tasks/task-67%20-%20Pairing-Feature-And-Bucket.md) |

---

## Storage tiers

| Tier | Что | Куда мигрирует |
|---|---|---|
| **Cloudflare KV** | KeyPackage pool, group inbox, profile store, dedup caches. Eventually-consistent, edge-cached. | PostgreSQL row-level security (Go phase) |
| **Cloudflare Durable Object** | Atomic counters (anti-brute-force), edit locks (strong consistency, per-key sharding). | Go actor + Redis Cluster / PostgreSQL advisory locks |
| **Cloudflare Analytics Engine** | Metrics counters (rate_limit_hit, auth_failure, malformed_payload). | Prometheus |
| **In-memory (per-worker isolate)** | JWKS cache 10 min, dedup 60s. Не persistent — regenerable. | Redis / in-process Go LRU |

**Не используем**: R2 blob storage (пока — TASK-111 signed upload tokens deferred).

---

## Crypto-relevant server decisions — таблица

Явные точки соприкосновения крипто-архитектуры и серверной. При изменении крипто-decision — сверяться с этой таблицей.

| Crypto decision | Server-side implication | Endpoint / Storage |
|---|---|---|
| [TASK-102](../../backlog/tasks/task-102%20-%20Decision-Revoke-policy.md) Revoke via profile reconciliation | Server-side edit lock (TTL 5 min) + encrypted profile store | `POST /v1/profile/lock`, `PROFILE_STORE`, `PROFILE_LOCKS` |
| [TASK-103](../../backlog/tasks/task-103%20-%20Decision-Remote-app-lock-for-stolen-device.md) Remote lock | Lock trigger endpoint + status polling | `POST /v1/lock/trigger`, `GET /v1/lock/status/{device_id}` |
| [TASK-104](../../backlog/tasks/task-104%20-%20Decision-KeyPackage-rate-limit.md) KeyPackage pool + last-resort | Pool storage cap=100, dedup TTL 10 min, last-resort rotation 7d | `KEYPACKAGE_POOL`, `CLAIM_DEDUP`, `LAST_RESORT_KEY` |
| [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) Zero-trust baseline | Все endpoints обязаны 5 properties + idempotency | Все middleware + refuse pattern 20 |
| [TASK-106](../../backlog/tasks/task-106%20-%20Decision-Sybil-resistance-and-signup-gate.md) Signup gate | Identity register endpoint verifies invitation code / pool entry | `POST /v1/identity/register` (Discussion) |
| [TASK-108](../../backlog/tasks/task-108%20-%20Decision-Metadata-privacy-what-server-sees.md) Metadata privacy T0 | Server sees identity_id + roster + timing; opaque OwnerRef / BucketKey / PushTopic ports for future T1 swap | All endpoints (adapter swap ~2-3 weeks per TASK-108) |
| [TASK-111](../../backlog/tasks/task-111%20-%20Decision-Signed-upload-tokens-quotas-abuse-response.md) Signed upload tokens (Deferred) | R2 presigned URL + DO counter per (pseudonym, resource); 100 MB per identity quota | Не реализовано — при активации TASK-11/28 |

---

## Migration path (правило 8)

**MVP** — Cloudflare Worker TypeScript.

**Триггер миграции на Go microservice**: любой из:
- **Scale**: > 100k requests/day sustained → Free tier tight.
- **Latency**: p95 > 200ms sustained (edge-local должен быть < 50ms; если worse — что-то не так или scale).
- **Feature gap**: нужен long-running task (WebSocket, cron) — Cloudflare Worker не подходит.
- **Vendor lock-in concern**: attempted exit ramp verification (не срочный триггер, но architecturally motivated).

**Целевая архитектура** (per INDEX.md § Диаграмма 2):

```
workers/identity/          # Go microservice: /v1/identity/*
workers/keypackage-store/  # Go microservice: /v1/keypackage/*
workers/message-fanout/    # Go microservice: /v1/group/*
workers/device-lock/       # Go microservice: /v1/lock/*
```

Каждый microservice — отдельный binary + deployable, коммуникация через HTTP + shared PostgreSQL.

**Estimated migration cost per microservice**: ~1 неделя (JWT verify + rate limit ladder + storage adapter + observability). Two-way door — можно вернуться на Cloudflare Worker если проблема.

**Full roadmap с triggers + estimates**: [server-roadmap.md](../dev/server-roadmap.md).

---

## Open questions (pending decisions)

- **[TASK-109](../../backlog/tasks/task-109%20-%20Decision-Durable-Objects-concrete-design-security-critical-endpoints.md)** (**Paused**) — Concrete Durable Object schema для anti-brute-force. Baseline (TASK-105) declared ladder RATE_LIMITER → DO. Осталось: which endpoints classify security-critical + concrete DO schema (counter shape, TTL, key derivation). **Триггер unpause**: начало implementation первого TASK-105 endpoint'а.
- **[TASK-107](../../backlog/tasks/task-107%20-%20Decision-Abuse-response-mechanism-legal-minimum.md)** (**Paused**) — Abuse response umbrella. Post-MVP: arbitration + open/closed groups + auto-detection. Blocks TASK-11, TASK-28. Требует legal + product perspective. **Триггер**: MVP-close момент / первый paying customer.
- **[TASK-106](../../backlog/tasks/task-106%20-%20Decision-Sybil-resistance-and-signup-gate.md)** (**Discussion**) — Identity signup gate. Влияет на `POST /v1/identity/register`.

---

## Rejected server alternatives (do not re-litigate)

- ❌ **Firebase Cloud Functions** — vendor lock-in по execution model, cold start hurts UX, стоимость выше при масштабе. Cloudflare Worker дешевле + edge-local.
- ❌ **AWS Lambda** — cold start, VPC configuration overhead. Worker выигрывает по DX и стоимости.
- ❌ **Server-side session state** — все endpoints stateless, session-like state (edit lock, rate limit counter) живёт в storage tier (KV / DO). Причина: horizontal scale + failure isolation.
- ❌ **gRPC** — JSON over HTTP proще для клиентов и debugging. gRPC добавит generated stubs + HTTP/2 requirement без пропорциональной выгоды на MVP scale.
- ❌ **Server-side crypto** (расшифровка на сервере) — нарушает E2E model. Server видит только encrypted envelopes.

---

## Related domains

- [crypto.md](crypto.md) — MLS group protocol, KeyPackage lifecycle, encrypted keystore. Крипто-endpoint'ы отсюда указывают на конкретные scenarios там.
- [identity.md](identity.md) — Identity model (LOCAL/CLOUD), signup gate, invitation. `POST /v1/identity/register` — точка соприкосновения.
- [../dev/server-roadmap.md](../dev/server-roadmap.md) — Rule 8 migration tracking (SRV-* entries), estimated triggers.
- [../dev/server-requirements.md](../dev/server-requirements.md) — Историческое: JWT posture Tier 0/1/2. Частично superseded TASK-105 baseline.
- [../dev/server-context-for-ai-agent.md](../dev/server-context-for-ai-agent.md) — Context for AI sessions working on server code.
- [../dev/client-requirements-for-zero-knowledge-server.md](../dev/client-requirements-for-zero-knowledge-server.md) — Что клиент требует от сервера чтобы zero-knowledge invariant держался.

---

## История версий

| Дата | Изменение |
|---|---|
| 2026-07-08 | v1 — initial snapshot. Собран из TASK-105 baseline + crypto-relevant endpoints (TASK-102/103/104/108/111) + INDEX.md registry rows + migration path из server-roadmap.md. |
