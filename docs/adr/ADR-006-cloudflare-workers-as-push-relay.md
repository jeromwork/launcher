# ADR-006: Cloudflare Workers as push-relay platform

**Status**: Accepted (stub — full version после Phase 5 спека 007)
**Date**: 2026-05-11
**Spec**: [007 — pairing-and-firebase-channel](../../specs/007-pairing-and-firebase-channel/spec.md)
**Supersedes**: N/A
**Related**: ADR-001 (cross-platform strategy), [`research.md §FCM-via-Cloudflare-Worker`](../../specs/007-pairing-and-firebase-channel/research.md)

---

## Context

Спек 007 требует FCM-push delivery от admin к managed device — чтобы telефon с приложением в фоне просыпался через Doze когда admin меняет конфиг или шлёт команду.

Firebase Cloud Messaging HTTP v1 API требует OAuth access-token со scope `https://www.googleapis.com/auth/firebase.messaging`. Этот token выдаётся **только сервисным аккаунтам** (server-side), не пользовательским OAuth flow'ом.

Возможные пути доставки push (после трёх ревизий C1, см. `research.md §History`):

1. **Variant A — Firebase Cloud Functions** (Firestore-trigger) — требует Blaze plan (карта).
2. **Variant B — client-side FCM publish** — технически невозможен (FCM topic-publish не доступен с пользовательского OAuth scope).
3. **Variant C — Cloudflare Workers как push-relay** — Worker hosted с Firebase service-account JSON в Cloudflare Secrets, принимает HTTPS от клиентов, отправляет FCM от имени Firebase project.

## Decision

**Принят Variant C — Cloudflare Workers** как push-relay платформа.

Архитектура:
- `push-worker/` subproject в корне репо (TypeScript, ~50 LOC).
- Hosted на free tier `*.workers.dev` (100k req/day лимит).
- Service-account JSON в Cloudflare Secrets (encrypted, never в git).
- Endpoint: `POST /notify` с Firebase ID-token authentication.
- Worker валидирует подпись JWT (jose library), читает `/links/{linkId}.adminId` для авторизации, отправляет FCM HTTP v1 data-message на topic `link-{linkId}`.

## Consequences

### Positive

- ✅ Spark plan совместимо — никаких Cloud Functions, никакой карты.
- ✅ Service-account secret защищён (Cloudflare Secrets, не в APK).
- ✅ Free tier 100k req/day — на годы хватит при MVP-нагрузке (~50 req/day).
- ✅ Worker — `~50 LOC` чистого кода; легко портируется на любой serverless.

### Negative

- ⚠ +1 сервис в стеке (Cloudflare Worker + Firebase).
- ⚠ Зависимость от Cloudflare uptime (SLA 99.9%).
- ⚠ Owner Cloudflare account имеет доступ к service-account JSON → требуется 2FA (см. project-backlog TODO-OPS-001).

## Alternatives considered

См. `specs/007-pairing-and-firebase-channel/research.md §OWD-6`. Таблица сравнения с Vercel Functions, Fly.io Apps, AWS Lambda, Firebase Cloud Functions, self-hosted DigitalOcean/Hetzner.

## Exit Ramp

Контракт `POST /notify` фиксирован (см. `specs/007-pairing-and-firebase-channel/contracts/worker-notify.md`). Замена Cloudflare на любой другой backend = переписать deploy (~3-6 часов), код переносится почти без изменений (Cloudflare Worker API ≈ Service Worker API).

Шаги миграции на альтернативную платформу:
1. Реализовать `POST /notify` с тем же поведением (auth + authorize + rate-limit + FCM send).
2. Положить service-account JSON в secret store новой платформы.
3. Обновить `WORKER_BASE_URL` env var в admin-приложении (build config).
4. Без code changes на стороне Android-клиентов.

**Подробные runbook'и для каждой альтернативной платформы** будут в `push-worker/README.md §Migration to alternative platform` (создаётся в T070 спека 007).

## Implementation status

- 2026-05-11 (этот ADR): scaffold, accepted в качестве approach.
- Phase 5 спека 007 (T061-T070): полная реализация Worker + tests + deploy.
- Phase 12 спека 007 (T110): end-to-end smoke с реальным push-доставкой.

## TODO before this ADR is "final" (vs "stub")

- [ ] Дополнить раздел Consequences после фактического deployment (latency observed, costs observed, OEM compatibility).
- [ ] Добавить раздел "Operational runbook" с monitoring + alert setup.
- [ ] Cross-reference с TODO-OPS-001, TODO-OPS-003, TODO-SEC-001 из project-backlog.
- [ ] Финализировать после спека 008 (когда `/config` действительно применяется через push consumer).
