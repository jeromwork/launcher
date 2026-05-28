# 09. Backend & Reliability — серверная сторона, performance, exit ramps

> **Status**: 🟡 partially decided (performance gates 2026-05-28) · **Created**: 2026-05-27

## Performance Gates — MVP commitments (2026-05-28, D-33)

**Pass bar для MVP release**:

| Metric | Limit | Measured on | CI |
|---|---|---|---|
| Cold start (app open from killed state) | ≤ 1 sec p95 | Pixel 5 / equivalent class device | Macrobenchmark module mandatory |
| Frame budget | ≥ 95% frames < 16ms on main flows | Pixel 5 class | Macrobenchmark |
| APK size | ≤ 30 MB | release build | Per-PR check |
| RAM peak | ≤ 200 MB | 2 GB RAM device | Manual measurement pre-release |
| Battery passive (no foreground use) | ≤ 1% per day | Pixel 5 | 24-hour wakeup trial (DEVICE-001) |

### Что делать если gate fails

- **Cold start regression**: PR block. Investigate startup work, defer to background.
- **Frame budget regression**: PR warning, release blocker if major flow affected.
- **APK size growth >10%**: PR warning, review needed.
- **RAM peak exceeded**: feature blocker для low-end devices.
- **Battery regression**: critical. Investigate wakeups, jobs, polling.

### Tools

- **Macrobenchmark** (Jetpack) — Android Gradle Plugin module. **TODO-PERF-001**.
- **Battery Historian** для wakeup analysis. **TODO-DEVICE-001**.
- **App size monitoring** через Play Console + per-PR в CI.
- **GC pressure / allocation tracking** — Android Studio Profiler manual pre-release.


> **Зачем читать**: продукт **не существует без backend'a**. Сейчас стек — Firebase Spark + Cloudflare Worker (free tier). Это работает для MVP, но имеет жёсткие лимиты и риски. Все exit ramps зафиксированы в `server-roadmap.md` — здесь use case'ы вокруг них.
> **Источник**: `user-journeys-draft.md` §7.6 + §7.13 + `docs/dev/server-roadmap.md` + `docs/dev/project-backlog.md`.

---

## Что это за документ (просто)

Все, что admin делает у себя, должно **доехать** до бабушки. Все, что бабушка делает (если делает), должно вернуться admin'у. Между ними — **сеть**, и она ненадёжна.

Backend — это **посредник**, который держит state (config, pairing, blobs), доставляет сообщения (FCM, push), валидирует операции (security rules, rate-limit). Если backend не работает — admin делает, ничего не происходит, frustration.

Сейчас мы используем **free tier стек** (Cloudflare Worker workers.dev + Firebase Spark). Это означает: жёсткие лимиты, отсутствие cron-jobs, in-memory rate-limit. Это **намеренный** выбор для MVP с **зафиксированными exit ramps** (см. `docs/dev/server-roadmap.md`).

Этот документ — про **сценарии отказов** (что когда сломается) и **performance budget** (cold start, frame, battery).

## Главные понятия (просто)

- **Spark plan** — бесплатный Firebase tier. Лимиты: 50k reads/day, нет Cloud Functions, нет server cron. Сейчас наш default.
- **Blaze plan** — платный Firebase tier. Cloud Functions, server cron, без жёстких лимитов. Exit ramp — ARCH-003 в backlog.
- **Workers.dev** — Cloudflare's бесплатный subdomain. Лимит 100k req/day. Exit ramp — собственный домен (ARCH-001).
- **In-memory rate-limit** — наш Worker считает rate-limit в RAM. При рестарте — счётчик обнуляется. Exit ramp — Cloudflare KV (ARCH-002).
- **App Check** — Firebase feature, который удостоверяет, что запрос идёт от **нашего** легитимного app'а, не от scraper'а. Сейчас not enabled (SEC-001).
- **Polling fallback** — когда FCM не работает (non-GMS), мы опрашиваем Firestore каждые 15 мин. Работает, но battery cost.
- **Backend substitution readiness** — наш принцип (CLAUDE.md rule 1): если завтра нужно сменить Firebase на свой сервер — это должно стоить одной адаптерной модулей, не переписывания.

## Use case инвентарь

### Backend operations / reliability

| ID | Кейс | Status |
|---|---|---|
| O-001 | Cloudflare Worker down (FCM relay недоступен) | 🟡 (007 polling fallback) |
| O-002 | Spark free-tier limit hit (reads/writes/bandwidth) | 🔮 ARCH-003 |
| O-003 | Firestore offline — UI behavior | 🟡 |
| O-004 | FCM down (Google outage) | 🟡 (polling) |
| O-005 | Rate-limit per uid (не только per linkId) | ❌ SEC-002 |
| O-006 | Audit logging для critical ops | ❌ SEC-003 |
| O-007 | Firebase App Check (anti-abuse) | 🟡 SEC-001 |
| O-008 | Production vs dev environment separation | 🟡 OPS-004, OPS-005 |
| O-009 | Backend substitution readiness (rule 1) | 🟡 (RemoteSyncBackend port) |
| O-010 | Disaster recovery (Firestore data lost) | ❌ |
| O-011 | Custom domain для Worker | 🔮 ARCH-001 |
| O-012 | KV rate-limit (persistent) | 🔮 ARCH-002 |
| O-013 | Cloud Functions for server cron | 🔮 ARCH-003 |
| O-014 | Multi-region failover | ❌ |
| O-015 | Cloudflare attack (DDoS на наш Worker) | ❌ |

### Performance / resource budget

| ID | Кейс | Status |
|---|---|---|
| PF-001 | Cold start ≤ 650ms p95 | 🟡 PERF-001 (macrobenchmark module needed) |
| PF-002 | Frame budget (Compose render) | 🟡 |
| PF-003 | Background battery (15-min polling vs FCM) | ✅ (007) |
| PF-004 | Memory limit на low-end devices | ❌ |
| PF-005 | Storage limit (photos especially) | 🟡 (011 storage adapter) |
| PF-006 | Network usage (sync size, large config) | 🟡 ARCH-009 |
| PF-007 | Doze + background restrictions | ✅ (007) |
| PF-008 | Macrobenchmark в CI | ❌ TODO-PERF-001 |
| PF-009 | 24-hour wakeups trial via Battery Historian | 🔮 DEVICE-001 |

## Главные открытые вопросы

### D-Be-1. Когда мигрируем на Blaze (платный Firebase)

**Контекст**: Blaze стоит pay-per-use. Дёшево для MVP, дорого для viral growth.

**Триггеры миграции** (зафиксированы в `server-roadmap.md` «Триггеры»):
- Spark лимит подходит (50k reads/day).
- Нужны Cloud Functions (server cron, server-side validation).
- Нужны Cloud Storage at scale (>1GB).

**Регрет**: затянули миграцию → внезапные outages при scale. Скаканули рано → платим за неиспользуемые ресурсы.

**Рекомендация**: фиксируем checkpoint в roadmap — «Blaze upgrade когда: первый месяц с ≥50% spark лимита **или** нужна server-side cron».

### D-Be-2. Custom domain — когда

**Контекст**: workers.dev — техническое имя, не выглядит профессионально. Custom domain — несколько $/year. Связан с **branding** (10-monetization).

**Рекомендация**: до публичного релиза. ARCH-001 в backlog.

### D-Be-3. Multi-region / failover

**Контекст**: если Firestore region down → весь продукт лежит. Реализация multi-region — overhead.

**Варианты**:
- **Single region (us-central1 default)**: simple, cheap, **outage = downtime**.
- **Multi-region replication**: defensible, expensive, requires Blaze + setup.

**Рекомендация**: single region для MVP. Multi-region — после ≥10k MAU + business critical.

### D-Be-4. Disaster recovery — backup strategy

**Контекст**: Firestore «у Google» — можно ли его потерять? Теоретически нет, но...

**Варианты**:
- **No backup**: rely on Firebase SLA.
- **Periodic Firestore export → GCS**: official Google tool, runs as Cloud Function (Blaze).
- **Daily backup to our own infra**: when we have own server.

**Рекомендация**: вариант 2 после Blaze upgrade. До этого — accept риск.

## Что в спеках уже зафиксировано

| Спек / документ | Что фиксирует |
|---|---|
| 007 Pairing | Cloudflare Worker as push-relay; FCM topic subscribe; 15-min polling fallback |
| 008 Config Sync | Firestore document model; optimistic concurrency |
| 011 Crypto Foundation | Storage adapter port (Firestore/blob storage) |
| `docs/dev/server-roadmap.md` | comprehensive exit ramps list |
| backlog OPS-001/002 | 2FA on Cloudflare + Firebase accounts |
| backlog OPS-004/005 | Production environment separation |
| backlog SEC-001/002/003 | App Check, per-uid rate-limit, audit logging |
| backlog ARCH-001 to ARCH-006 | infrastructure exit ramps |

## Связь с другими документами

- **05 Pairing** — backend reliability определяет, насколько pairing восстанавливаемо.
- **07 Data & privacy** — где physical-ly хранятся данные = backend choice.
- **10 Monetization** — Blaze upgrade ↔ revenue (нужны деньги, чтобы тратить на серверы).
- **11 Support+Dev** — production support, on-call.

## Источники

- `docs/dev/server-roadmap.md` — главный источник по exit ramps.
- `docs/dev/project-backlog.md` секции «Security & Operations», «Architecture Exit Ramps».
- [Firebase Spark vs Blaze](https://firebase.google.com/pricing) — pricing reference.
- [Cloudflare Workers limits](https://developers.cloudflare.com/workers/platform/limits/).
- [Article IX of constitution.md](../.specify/memory/constitution.md) — battery / performance discipline.

## Заметки решений

| Дата | Решение | Regret | Exit ramp |
|---|---|---|---|
| _(пусто)_ | | | |
