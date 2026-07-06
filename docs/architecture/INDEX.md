---
kind: architecture-map
audience: [owner, ai-agent]
purpose: Single-page overview of the current tech stack — what we chose, where it lives, what problem it solves.
domains:
  - id: crypto
    file: crypto.md
    scope: MLS library, KeyPackage management, group encryption, keystore.
  - id: identity
    file: identity.md
    scope: Identity model (LOCAL/CLOUD), signup gate, invitation, JWT.
    status: skeleton — full content pending TASK-106 Decision.
  - id: server
    file: server.md
    scope: Cloudflare Worker runtime, rate limiting tiers, storage, migration to Go microservices.
    status: skeleton — full content pending.
  - id: client-android
    file: client-android.md
    scope: core/ domain layer, adapter modules, Compose UI structure.
    status: skeleton — full content pending.
  - id: external-services
    file: external-services.md
    scope: Firebase Auth, FCM, future third-party integrations.
    status: skeleton — full content pending.
last-synced-decision: TASK-105
pending-decisions: [TASK-106]
---

# Архитектурная карта — обзор

**Что это**: одностраничный snapshot текущего стека — какую библиотеку, платформу, механизм мы выбрали и **где** это живёт в кодовой базе. Читается за 3-5 минут, даёт полную картину.

**Что это НЕ**: не документация "почему выбрали". Причины — в Decision-блоках соответствующих backlog task'ов. Ссылки — в таблице ниже.

**Как поддерживается актуальным**: когда backlog task переходит из статуса `Discussion` в `Draft` (Decision block frozen) — соответствующий раздел этой карты обновляется в том же commit'е. Skill автоматизации — см. TODO ниже.

---

## Master diagram — все "коробки" сразу

### Диаграмма 1 — устройство пользователя (Android app)

```mermaid
flowchart TB
    subgraph ui["app/ui — Compose"]
        u_launcher["Лаунчер"]
        u_wizard["Setup Wizard"]
        u_messenger["Messenger UI"]
    end
    subgraph core["core — domain (без SDK)"]
        core_ports["Ports: Crypto, KeyPackage, Identity,<br/>Pairing, Storage, Group"]
        core_types["Domain types: Identity, Group,<br/>Invitation, Message"]
    end
    subgraph adapters["app/adapters — ACL"]
        a_mls["openmls adapter"]
        a_firestore["Firestore adapter"]
        a_fcm["FCM adapter"]
        a_pairing["QR pairing adapter"]
        a_keystore["Keystore adapter (SQLCipher)"]
    end
    rust["openmls native lib<br/>Rust · MIT · SRLabs 2024"]

    ui --> core_ports
    ui -.->|через ports| adapters
    adapters --> core_ports
    a_mls --> rust
    rust --> a_keystore
```

### Диаграмма 2 — сервер (MVP → миграция)

```mermaid
flowchart LR
    subgraph worker["Cloudflare Worker — MVP (TS)"]
        w_mw["Middleware: JWT + rate limit +<br/>validate + observability"]
        w_ident["/v1/identity/*"]
        w_kp["/v1/keypackage/*"]
        w_grp["/v1/group/*"]
        w_lock["/v1/lock/*"]
    end
    subgraph gomicro["Future: Go microservices"]
        g_ident["workers/identity/"]
        g_kp["workers/keypackage-store/"]
        g_fanout["workers/message-fanout/"]
        g_lock["workers/device-lock/"]
    end
    w_mw --> w_ident
    w_mw --> w_kp
    w_mw --> w_grp
    w_mw --> w_lock
    worker -.->|migration path · правило 8| gomicro
```

### Диаграмма 3 — как устройство говорит с сервером и внешними

```mermaid
flowchart LR
    subgraph device["Android app"]
        a_mls2["openmls adapter"]
        a_firestore2["Firestore adapter"]
        a_fcm2["FCM adapter"]
        a_pairing2["QR pairing adapter"]
    end
    subgraph srv["Cloudflare Worker"]
        w_kp2["keypackage endpoints"]
        w_grp2["group endpoints"]
        w_ident2["identity endpoints"]
    end
    subgraph ext["External services"]
        e_fb["Firebase Auth<br/>lazy · при cloud action"]
        e_fcm["FCM push"]
        e_firestore["Firestore config sync"]
    end
    a_mls2 -->|publish KeyPackage batch| w_kp2
    a_mls2 -->|MLS Welcome/Commit/App| w_grp2
    a_pairing2 -->|register identity| w_ident2
    a_pairing2 -.->|при first cloud action| e_fb
    a_firestore2 --> e_firestore
    a_fcm2 --> e_fcm
```

**Как читать**:
- **Стрелки сплошные** = реальный runtime-вызов.
- **Стрелки пунктирные** = "через порты" (правило 1 domain isolation) или "будущий migration path".
- **Цвет subgraph** отражает уровень: android (устройство) → worker (текущий сервер) → gomicro (будущий сервер) → external (внешние сервисы).

---

## Registry — что выбрано, где живёт, кто решил

### Crypto

| Компонент | Выбор | Task-решение | Статус | Exit ramp |
|---|---|---|---|---|
| Групповой e2e-протокол | MLS TreeKEM (RFC 9420) | [TASK-104](../../backlog/tasks/task-104%20-%20Decision-KeyPackage-rate-limit.md) | Draft | Sender Keys (major refactor) |
| MLS client-библиотека | **openmls** (Rust · MIT · аудирован SRLabs 2024) | TASK-107 (draft, из research) | **Proposed** | `mls-rs` swap в адаптере |
| Kotlin binding для openmls | UniFFI-сгенерированные bindings | TASK-107 | **Proposed** | Manual JNI |
| Encrypted keystore | SQLCipher provider для openmls | TASK-107 | **Proposed** | Room + separate keystore |
| KeyPackage pool cap | 100 per identity | [TASK-104](../../backlog/tasks/task-104%20-%20Decision-KeyPackage-rate-limit.md) | Draft | Preset field |
| KeyPackage dedup TTL | 10 min | [TASK-104](../../backlog/tasks/task-104%20-%20Decision-KeyPackage-rate-limit.md) | Draft | Preset field |
| Last-resort rotation | 7 дней (family default) | [TASK-104](../../backlog/tasks/task-104%20-%20Decision-KeyPackage-rate-limit.md) | Draft | Preset field |
| Group revoke policy | Immediate hard kick via MLS Remove, 3-tier role (owner/admin/other) | [TASK-102](../../backlog/tasks/task-102%20-%20Decision-Revoke-policy.md) | Draft | Per-revoke reason enum for beta |
| History backup | Signal-style (нет восстановления истории на MVP) | [TASK-100](../../backlog/tasks/task-100%20-%20Decision-History-backup-strategy-for-MVP.md) | Draft | HIST-BACKUP-001 (Phase-3+) |

Детально — см. [crypto.md](crypto.md).

### Identity

| Компонент | Выбор | Task-решение | Статус | Exit ramp |
|---|---|---|---|---|
| Identity model | Hybrid: LOCAL per-device (для MLS) + CLOUD per-user (для sync) | [TASK-106](../../backlog/tasks/task-106%20-%20Decision-Sybil-resistance-and-signup-gate.md) | **Discussion** | Change requires MLS group re-key |
| Signup gate (family MVP) | Invitation-code от admin'а (мама-дочка выдаёт бабушке) | [TASK-106](../../backlog/tasks/task-106%20-%20Decision-Sybil-resistance-and-signup-gate.md) | **Discussion** | Pool entry — preset-parameterizable |
| Signup gate (clinic) | TBD | TASK-106 (Phase-3+) | Deferred | — |
| Recovery flow | Auto MLS Add + post-facto notification (Chrome/Google Account model) | [TASK-101](../../backlog/tasks/task-101%20-%20Decision-Peer-confirmation-on-recovery.md) | Draft | RECOVERY-2FA-001 (opt-in 2FA) |
| Remote app lock | Cryptographic defense: Keystore wipe + full recovery on unlock | [TASK-103](../../backlog/tasks/task-103%20-%20Decision-Remote-app-lock-for-stolen-device.md) | Draft | Wipe-verification token + Google Find My integration |
| Cloud identity provider | Firebase Auth (lazy, only при first cloud action) | (existing) | Prod | Own OIDC provider (Go migration) |

Детально — см. [identity.md](identity.md) (пока skeleton, ждёт закрытия TASK-106).

### Server

| Компонент | Выбор | Task-решение | Статус | Exit ramp |
|---|---|---|---|---|
| Runtime (MVP) | Cloudflare Worker (TypeScript) | [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) | Draft | Go microservices per `workers/` |
| Runtime (Phase-3+) | Go microservices (workers/identity, workers/keypackage-store, workers/message-fanout, workers/device-lock) | (roadmap) | Deferred | — |
| API URL scheme | `/v1/<domain>/<action>` versioned | [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) | Draft | Major version bump on breaking change |
| Request/response schema | `{ schemaVersion: N, data | error }` | [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) | Draft | правило 5 |
| JWT verify | `jose` npm + JWKS memory cache 10 min | [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) | Draft | `go-jose` |
| Rate limit (normal) | Cloudflare RATE_LIMITER binding (edge, 60s окно) | [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) | Draft | `go-redis/redis_rate` |
| Rate limit (critical) | Cloudflare Durable Object counter | [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) | Draft | Go actor + Redis Cluster |
| Rate limit dimension | per-identity (JWT claim `identity_id`) | [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) | Draft | `TODO(server-roadmap)`: per-device |
| Input validation | `zod` schema | [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) | Draft | `go-playground/validator` |
| Observability | Structured JSON logs + Cloudflare Analytics Engine counters | [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) | Draft | Prometheus + Grafana Loki |
| Idempotency | Natural dedup + `Idempotency-Key` header для state-modifying без natural bound | [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) | Draft | — |

Детально — см. [server.md](server.md) (пока skeleton).

### Client-Android

| Компонент | Выбор | Статус |
|---|---|---|
| UI framework | Jetpack Compose | Prod |
| Domain layer | `core/` KMP module (чистый Kotlin) | Prod |
| Adapter modules | `app/adapters/<vendor>/` — один subfolder на внешнюю зависимость | Prod |
| DI | Manual constructor injection (без DI framework в MVP) | Prod |

Детально — см. [client-android.md](client-android.md) (пока skeleton).

### External services

| Компонент | Выбор | Стоимость | Exit ramp |
|---|---|---|---|
| Push notifications | FCM (Firebase Cloud Messaging) | Free tier достаточно | Own APNS-like server |
| Cloud identity | Firebase Auth | Spark plan (free) | Own OIDC provider |
| Config sync | Firestore | Spark plan (free) | PostgreSQL row-level security |

Детально — см. [external-services.md](external-services.md) (пока skeleton).

---

## Что открыто прямо сейчас (pending decisions)

- **[TASK-106](../../backlog/tasks/task-106%20-%20Decision-Sybil-resistance-and-signup-gate.md)** (Discussion) — identity signup gate. Влияет на `identity.md` registry. Разделы `signupGate`, `identityModel`, `bootstrapSource` изменятся после закрытия.
- **TASK-107** (draft, не создан) — MLS library choice (openmls vs mls-rs). Из research 2026-07-06. Разделы crypto: `MLS-библиотека`, `Kotlin binding`, `Encrypted keystore`.

Пока эти task'и в `Discussion` — соответствующие ячейки в registry отмечены **Discussion** / **Proposed**.

---

## Как это связано с backlog-task'ами

- **Backlog Decision-блок** = **"почему"**. Immutable исторический контракт. Rationale + alternatives + trade-offs + exit ramp.
- **Architecture registry (эта страница)** = **"что и где"**. Текущий snapshot, обновляется при закрытии Decision.

Ссылки в обе стороны:
- Registry → Decision task (колонка `Task-решение`).
- Decision task Applies-to section → упоминание разделов Architecture doc (`Applies to: docs/architecture/crypto.md § MLS library`).

Один источник правды для каждого вопроса, никакого дублирования.

---

## TODO — автоматизация

- [ ] Skill `procedure-sync-architecture-map` — вызывается автоматически при переходе Decision task `Discussion → Draft`. Читает Decision block + обновляет соответствующий раздел registry + domain-файл. Спроектировать после того как убедимся что паттерн работает на `crypto.md`.
- [ ] Fitness function: linter проверяет что каждая `decision-task: TASK-N` в YAML frontmatter существует и имеет статус ≥ `Draft`. При `Discussion` — предупреждение "поле помечено `Proposed`, обновить после закрытия".
- [ ] Добавить в `procedure-decision-drift-check` шаг: проверять synchronization Architecture ↔ Decision. Flag drift.
- [ ] Rule в CLAUDE.md: **при commit'е закрывающем Decision task — обязательно update соответствующей секции Architecture в том же commit'е**. Refuse pattern для сессии.

---

## История версий этой карты

| Дата | Изменение | Commit |
|---|---|---|
| 2026-07-06 | Initial version. Registry заполнен по Decision blocks TASK-100…105. TASK-106 pending. | (pending) |
