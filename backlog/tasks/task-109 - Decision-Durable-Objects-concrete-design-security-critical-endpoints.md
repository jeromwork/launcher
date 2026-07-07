---
id: TASK-109
title: 'Server-side anti-brute-force protection (own-server phase, frontend-agnostic)'
status: Paused
assignee: []
created_date: '2026-07-07'
updated_date: '2026-07-07'
labels:
  - decision
  - server
  - infrastructure
  - own-server-phase
  - post-mvp
milestone: m-2
dependencies: []
priority: medium
ordinal: 109000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

**Серверная задача**, полностью изолированная от frontend'а. Проектирование anti-brute-force защиты для security-critical endpoints собственного сервера (когда будем его делать).

**Проблема**: некоторые endpoints требуют строгого anti-brute-force — если attacker может делать тысячи попыток, он взламывает наш криптографический контракт:
- **`/recovery/attempt`** — попытка восстановить root_key через passphrase.
- **`/unlock/attempt`** — попытка разблокировать stolen device (TASK-103).

Обычная защита («5 попыток за 15 минут») требует **глобального счётчика** — одного на весь сервис.

## Frontend contract (уже определён, не в scope этой задачи)

**Frontend полностью независим от того как реализована server-side защита.** Контракт — стандартная HTTP semantic:

```
POST /recovery/attempt         → 200 OK { recovery_envelope }
                                 429 Too Many Requests + Retry-After: 900
                                     { error: "rate_limited", locked_until: "..." }
                                 503 Service Unavailable + Retry-After: 300
                                     (server down)
```

Frontend UX:
- 200 → продолжаем.
- 429 → показать «попробуйте через N минут».
- 503 → показать «сервер недоступен, попробуйте позже».

**Frontend делать сейчас можно** — стандартная обработка HTTP status. Никакой зависимости от того будет ли на сервере Redis / PostgreSQL / Go actor / что угодно.

## Что в scope этой задачи (когда возьмём)

Только серверные архитектурные вопросы:
1. Какие endpoints security-critical.
2. По чему считаем subject (identity / device / IP).
3. Storage schema счётчика.
4. Failure modes (fail-closed / fail-open).
5. Тестирование.
6. Выбор implementation pattern (Redis INCR / PostgreSQL row lock / Go actor).

## Состояние

**PAUSED 2026-07-07** — берём когда будем делать собственный сервер.

Причины paused:
1. Frontend от этой задачи не зависит (стандартный HTTP contract выше).
2. Текущая инфра (Cloudflare Worker) — bootstrap, не production. Проектировать anti-brute-force под неё = двойная работа.
3. Нет других dependencies — задача полностью standalone.

**Return trigger**: старт work'а над собственным сервером (Go microservice, PostgreSQL / Redis infra). До этого — frontend работает с temporary Cloudflare Worker'ом, который делает best-effort защиту как получится, without formal architecture.

**Что при этом сделать в frontend**:
- Обычная обработка 200 / 429 / 503 responses.
- UX text для rate-limited / server-down cases.
- Retry logic с exponential backoff при 503 (не при 429 — там Retry-After честный).

**Уточняющие вопросы Q1-Q5** (mentor Part A ниже) — отложены до старта серверной работы.

## Зачем

**Blocks / narrows**:
- **TASK-6** (Root Key Hierarchy) — recovery attempts counter не спроектирован; TASK-105 сказал "нужен DO", но не какой.
- **TASK-103** (Remote app lock) — unlock attempts counter mentioned как DO candidate, но не specified.
- **TASK-67** (Pairing) — pairing claim endpoint может быть security-critical или нет — надо решить.
- **TASK-104** (KeyPackage) — cross-region drain detection parked как "requires TASK-109 (was Q-14) DO baseline".

**Consumers Decision block'а**:
- Все server-facing tasks получают machine-readable answer: "мой endpoint попадает в DO bucket или RATE_LIMITER?".

## Что входит технически (для AI-агента)

**Требования к серверному компоненту anti-brute-force** (семь свойств, vendor-agnostic):
1. Считать попытки конкретного subject'а (identity_id / device_id / IP).
2. **Один счётчик на весь сервис** (глобальный, не per-instance).
3. **Persistence** — переживает restart.
4. **Sliding window** — попытки старше N минут автоматически не считаются.
5. **Lockout state** — превышение → блок на M минут даже при обнулении счётчика.
6. **Latency < 100ms** allow/deny decision.
7. **Concurrent-safe** — race conditions невозможны.

Все семь = **компонент с эксклюзивным владением состоянием на весь сервис + persistent storage**.

**Endpoint classification criteria**:
- Может attacker обойти rate limit через distributed request pattern? Если да → security-critical (нужен глобальный persistent counter).
- Blast radius single successful abuse — если crypto-compromise возможен → security-critical.
- Frequency сама по себе — если endpoint hits 1000/s normally, brute-force protection overhead неоправдан.

**Candidate endpoints (черновик, ждём Q1)**:
1. Recovery attempts (TASK-6, TASK-101) — точно security-critical.
2. Unlock attempts (TASK-103) — точно security-critical.
3. Admin actions signing (TASK-102) — вероятно НЕТ (signature verification issue, не rate-limit).
4. Pairing claim (TASK-67) — вероятно НЕТ (TTL 90s + high-entropy token → brute-force impractical).

**Abstract port** (в domain):

```kotlin
interface AttemptCounter {
    suspend fun increment(subject: SubjectId): CounterState
    suspend fun reset(subject: SubjectId): Unit
}

data class CounterState(
    val allowed: Boolean,
    val retryAfterSeconds: Int?,
    val lockoutUntil: Instant?,
)

sealed class SubjectId {
    data class Identity(val id: IdentityId) : SubjectId()
    data class Device(val id: DeviceId) : SubjectId()
    data class IpAddress(val addr: String) : SubjectId()
}
```

**Storage schema (draft)**:
```
{
  "schemaVersion": 1,          // rule 5
  "attempts": [timestamp1, timestamp2, ...],  // sliding window через prune
  "lockedUntil": timestamp | null,
  "lastSuccess": timestamp | null              // success сбрасывает счётчик
}
```

**Adapter options** (implementation detail, не architectural):
- **Pattern A: Redis atomic INCR** — Redis (in-memory + AOF/RDB persistence), atomic operations. Latency ~1ms. Требует Redis infra.
- **Pattern B: PostgreSQL row lock** — `UPDATE ... WHERE identity = ?` (row lock built-in). Latency ~10ms. Требует PostgreSQL.
- **Pattern C: In-process actor** — Go goroutine per identity через `sync.Map`, serialized через channel. Persist через SQLite/file. Latency ~1ms. Требует sticky routing.
- **Current infra (temporary)**: implement через primitive текущей infra + `// TODO(server-roadmap): SRV-COUNTER-001 migrate to A/B/C on own-server ship`.

Все три A/B/C — well-known industry patterns, никакой vendor lock-in в domain.

**Failure modes** (не решено, ждём Q4):
- Fail-open (attacker выжидает outage) / fail-closed (503 + Retry-After) / fallback tier (weak consistency).

**Migration path** (`docs/dev/server-roadmap.md § SRV-COUNTER-001`):
- Current adapter (Cloudflare / whatever bootstrap infra) → Redis INCR или PostgreSQL row lock при own-server microservice ship. Adapter swap, domain код не меняется.

## Состояние

**Discussion, Session 1 opened 2026-07-07**. Mentor Part A написан ниже (5 вопросов). Ждём ответов владельца.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] Session 1 mentor Part A написан (5 вопросов surfaced, vendor-agnostic)
- [x] #2 [hand] Frontend contract зафиксирован: standard HTTP 200 / 429 / 503 semantic (см. § Frontend contract). Frontend работать может независимо.
- [x] #3 [hand] Задача явно standalone: `dependencies: []`, никого не блокирует до старта серверной работы
- [ ] #4 [hand] При unfreeze: провести mentor Part B, ответить на Q1-Q5
- [ ] #5 [hand] При unfreeze: Decision block (English, immutable) — endpoint classification, storage schema, failure modes, chosen pattern (A/B/C)
- [ ] #6 [hand] При unfreeze: server-roadmap запись `SRV-COUNTER-001` — chosen adapter + migration story
<!-- AC:END -->

## Discussion
<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 (2026-07-07, mentor skill invoked)

**Revision 2026-07-07**: изначальная сессия зациклилась вокруг Cloudflare Durable Objects. Владелец указал (правильно): «не надо, это завязка на чужой сервер и его требования, распиши что нужно от нашего сервера». Часть A ниже — переработана в vendor-agnostic формулировку. DO / RATE_LIMITER — только deployment detail для current infra, записываются в inline TODO + server-roadmap, не в architectural decision.

#### A.1 Что за область

Некоторые серверные endpoints требуют строгого anti-brute-force защиты — если attacker может делать тысячи попыток, он взламывает наш криптографический контракт. Примеры:

- **`/recovery/attempt`** — попытка восстановить root_key через passphrase на новом устройстве. Успех = attacker получает все зашифрованные данные пользователя.
- **`/unlock/attempt`** — попытка разблокировать stolen device (TASK-103). Успех = full access.

Обычная защита («не более 5 попыток за 15 минут») требует **глобального счётчика** — одного на весь сервис. Если счётчик локальный (per-instance или per-region), attacker обходит через параллельные запросы к разным инстансам или регионам.

TASK-105 baseline установил ladder posture (обычные endpoints — простой rate limit; security-critical — глобальный persistent counter). TASK-109 заполняет: **какие endpoints security-critical + абстрактный `AttemptCounter` port + storage schema + failure modes**.

#### A.2 Что нужно от сервера (семь свойств, vendor-agnostic)

Для anti-brute-force защиты critical endpoint'а сервер должен уметь одновременно:

1. **Считать попытки конкретного subject'а** (identity_id / device_id / IP).
2. **Один счётчик на весь сервис** (глобальный, не per-instance / per-region).
3. **Persistence** — счётчик переживает restart сервера.
4. **Sliding window** — попытки старше N минут автоматически не считаются.
5. **Lockout state** — превышение → блок на M минут даже при обнулении счётчика.
6. **Latency < 100ms** allow/deny decision.
7. **Concurrent-safe** — race conditions невозможны (2 попытки в 10ms не должны обе пройти).

Все семь = **компонент с эксклюзивным владением состоянием на весь сервис + persistent storage**.

#### A.3 Три известных pattern'а на собственном сервере

| Pattern | Что даёт | Latency | Требует |
|---|---|---|---|
| **A: Redis atomic INCR** | Все 7 свойств. Redis in-memory + AOF/RDB persistence. Atomic operations concurrent-safe. | ~1ms | Redis infra |
| **B: PostgreSQL row lock** | Все 7. `UPDATE ... WHERE identity = ?` — БД serialize'ит через row lock. Persist встроен. | ~10ms | PostgreSQL |
| **C: In-process actor (Go goroutine)** | Все 7. Goroutine per identity через `sync.Map`, serialized через channel. Persist через SQLite/файл. | ~1ms | Sticky routing к одному инстансу |

Все три — well-known industry patterns, никакой vendor lock-in.

#### A.4 Абстрактный port в domain

Domain код видит **только это**:

```kotlin
interface AttemptCounter {
    suspend fun increment(subject: SubjectId): CounterState
    suspend fun reset(subject: SubjectId): Unit
}

data class CounterState(
    val allowed: Boolean,
    val retryAfterSeconds: Int?,
    val lockoutUntil: Instant?,
)

sealed class SubjectId {
    data class Identity(val id: IdentityId) : SubjectId()
    data class Device(val id: DeviceId) : SubjectId()
    data class IpAddress(val addr: String) : SubjectId()
}
```

**Adapter сегодня** (temporary Cloudflare bootstrap infra) — implement через что удобно на текущей инфре + inline TODO:

```typescript
// TODO(server-roadmap): SRV-COUNTER-001
// Current adapter uses [current infra primitive]. Must migrate to
// Redis INCR (Pattern A) / PostgreSQL row lock (Pattern B) / Go actor (Pattern C)
// when own-server microservice ships. Domain code unchanged.
```

**Adapter завтра** (own Go microservice — уже в roadmap) — Redis / PostgreSQL / Go actor. Domain не заметит подмену.

#### A.5 Storage schema (draft)

```
{
  "schemaVersion": 1,           // rule 5 — persistent state = wire format
  "attempts": [ts1, ts2, ...],   // timestamps (sliding window через prune старше 15 мин)
  "lockedUntil": ts | null,
  "lastSuccess": ts | null        // success сбрасывает счётчик
}
```

Sliding window (точнее, DO/Redis это могут дешёво) vs fixed window (проще, но attacker burst'ит на boundary hh:14→hh:15).

#### A.6 Уточняющие вопросы

Тема узкая (endpoint classification + abstract port), **5 вопросов** достаточно.

**Q1. Какие endpoints security-critical** (нужен глобальный persistent counter, не local rate limit):

- **(a) Recovery attempts** (TASK-6, TASK-101) — brute-force passphrase → root_key. **Точно да**.
- **(b) Unlock attempts** (TASK-103 remote app-lock) — brute-force unlock passphrase. **Точно да**.
- **(c) Admin actions signing** (TASK-102 MLS Commits) — attacker forge через spoofed JWT? Bab's device holds signing key; без Keystore access forge невозможен. **Скорее нет** — это signature verification, не rate-limit.
- **(d) Pairing claim** (TASK-67 claim token endpoint) — attacker brute-force claim token до TTL 90s. **Скорее нет** — TTL + high-entropy random token делают brute-force impractical.
- **(e) KeyPackage claim** (TASK-104) — attacker drain'ит pool. Currently в TASK-104 через local rate limit + claim dedup. Promote в security-critical сейчас или deferred trigger?
- **(f) Что-то ещё** что важно?

Мой bet: **(a) + (b) обязательно в MVP**. (c), (d) — обычный rate limit. (e) — оставить per TASK-104 текущий подход, promote только если dedup недостаточен.

**Почему спрашиваю**: определяет scope. Каждый security-critical endpoint = свой counter setup + tests + failure mode.

---

**Q2. Instance keying — по чему считаем subject.**

- **Recovery**: per-identity (`identity_id`)? Или + per-IP secondary counter (defense in depth vs attacker с валидным identity_id который откуда-то украл)?
- **Unlock**: per-device (`device_id`) — каждое устройство считает unlock attempts отдельно. Правильно?

Мой bet:
- **Recovery: per-identity primary + per-IP secondary**. Attacker с sybil identities (если TASK-106 sybil defense пробита) всё равно ограничен per-IP counter'ом (5 attempts total per IP). Defense in depth.
- **Unlock: per-device**, как и было.

**Почему спрашиваю**: subject определяет unit of consistency и cost. Слишком широкий (per-identity only) — attacker с 100 identities обходит. Слишком узкий (per-request) — счётчик не нужен вообще.

---

**Q3. Storage schema — что храним в persistent state.**

Per subject:
- `schemaVersion: 1` (rule 5, обязательно).
- `attempts: number[]` — timestamps recent attempts (sliding window через prune старше N min).
- `lockedUntil: number | null`.
- `lastSuccess: number | null` — success сбрасывает counter.
- **Что-то ещё** что нужно?

Sliding window (5 в trailing 900 seconds) vs fixed window (5 в fixed 15-min bucket).

Мой bet: **sliding window** + минимум полей выше. Fixed window даёт reliability attack vector.

**Почему спрашиваю**: persistent state = wire format (rule 5). Позже добавить поле = migration path. Лучше решить сейчас.

---

**Q4. Failure modes — что делаем когда счётчик недоступен.**

Redis/PostgreSQL/whatever упал:
- **(a) Fail-open**: pass request, если counter не отвечает. **Плохо** — attacker выжидает outage → brute-force.
- **(b) Fail-closed**: 503 Service Unavailable + Retry-After. **Может лишить** легит пользователя доступа во время incident'а.
- **(c) Fallback tier**: read из кэша (weak consistency). Middle ground, complex, weak security.

Мой bet: **fail-closed** для recovery и unlock. Они не time-critical (5-10 мин подождать recovery/unlock приемлемо для legit). Attacker vs legit asymmetry: attacker хочет brute-force during outage, legit user приемлет краткосрочный delay.

**Почему спрашиваю**: TASK-105 baseline требует explicit failure modes.

---

**Q5. Local dev / testing story.**

- **(a) Fake in-memory `AttemptCounter` adapter** — для unit tests domain code. Deterministic, controllable time (mock `Instant.now()`).
- **(b) Integration tests** — реальный adapter (Redis / PostgreSQL / current infra emulator) через docker-compose или local emulator.
- **(c) Staging deploy tests** — реальные production-like semantics.

Мой bet: **(a) для unit tests + (b) для integration**. Fake adapter покрывает 95% cases, integration test проверяет adapter semantics (concurrent-safe? persistent через restart?).

**Почему спрашиваю**: TASK-105 checklist требует observability + failure modes tests. Если тесты болезненные — developers их скипают.

---

Останавливаюсь. Жду ответы владельца.

<!-- SECTION:DISCUSSION:END -->

## Implementation Plan
<!-- SECTION:PLAN:BEGIN -->
_(pending — заполняется в /speckit.plan после Decision block frozen)_
<!-- SECTION:PLAN:END -->
