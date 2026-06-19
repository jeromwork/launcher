# Manual setup — переезд на свой сервер

Runbook **ручных действий** для миграции с Firebase / Google Cloud / Cloudflare
Worker на собственный backend. Сейчас это **скелет** — будет заполняться по мере
того как сервер появится и спеки будут переезжать одна за одной.

**Аудитория**: release engineer + server team в момент когда принято решение
о cutover'е на own server.

**Когда читать**:
- Сейчас — обзорно, чтобы понимать масштаб будущей работы.
- Перед запуском собственного backend — пошагово.

**Связанный документ**: [`server-roadmap.md`](server-roadmap.md) — архитектурный
план **что** должно переехать (со статусами `SRV-*`). Этот файл — **как** это
сделать руками.

---

## 0. Принципы миграции

1. **Adapter pattern уже готов** (CLAUDE.md rule 2). Domain код не знает про
   Firebase / Cloudflare — он работает с port'ами. Переезд = новый адаптер,
   не переписывание domain.

2. **Wire formats версионируются** (CLAUDE.md rule 5). При переезде данных
   формат остаётся прежним — сервер читает existing schemaVersion и пишет
   тот же. Никакой принудительной re-encryption.

3. **Background reconciler** — стандартный паттерн миграции данных:
   - Новые writes идут сразу на свой backend.
   - Старые writes — background job читает из Firebase, проверяет integrity,
     перекладывает на свой backend.
   - Firebase остаётся read-only ещё 30 дней как fallback.

4. **Тесты сначала, миграция потом**. Каждый новый адаптер сначала проходит
   contract tests (тот же набор что Firebase-адаптер) → потом включается.

---

## 1. Authentication (SRV-AUTH-001 + SRV-AUTH-IDENTITY-001)

**Что сейчас**: F-4 spec 017 — Google Sign-In через Firebase Auth + identity-links
в Firestore.

**Что меняется при переезде**:
- Google ID Token exchange делает **наш backend**, не Firebase.
- Identity-links хранятся в **нашей БД** (Postgres / SQLite), не Firestore.
- Session token выпускается **нашим backend** (JWT с claim `stableId`).

**Что НЕ меняется**:
- AuthProvider port → один adapter swap.
- AuthIdentity domain type → не трогается.
- SessionRecord wire format → не трогается.
- Google Sign-In UI (Credential Manager + bottom-sheet) → остаётся тот же.

### 1.1. Backend endpoint

```
POST /auth/google-signin
  Body: { id_token: string }
  Response: { jwt: string, stable_id: string, expires_at: number }
```

Backend должен:
1. Verify Google ID Token через [Google Token Info](https://oauth2.googleapis.com/tokeninfo)
   или JWKS public key.
2. Lookup существующего `identity_links` row по `(provider=google, provider_account_id=sub)`.
3. Если нет — create row + новый `users` row, generate stable_id (UUID v4).
4. Issue наш JWT с claim `stableId`.
5. Return JWT.

### 1.2. Миграция identity-links из Firestore

⚠️ **Критично сделать до cutover'а**. Если потерять mapping `sub → stableId` —
существующие user'ы при следующем sign-in получат **новые** UUID, что сломает
все их pair-keys, delegation, config-sync.

```bash
# 1. Export Firestore collection
firebase firestore:export gs://launcher-backup/identity-links-$(date +%Y%m%d) \
        --collection-ids identity-links --project launcher-prod

# 2. Download dump
gsutil -m cp -r gs://launcher-backup/identity-links-* ./migration-data/

# 3. Конвертировать в SQL INSERT statements (написать кастомный скрипт под формат БД)
# Пример Python:
# for doc in firestore_dump:
#     INSERT INTO identity_links (firebase_uid, stable_id, created_at) VALUES (...)
```

### 1.3. Client switch

В `BackendInit.kt` (или через BuildConfig flavor):
```kotlin
// Было:
GoogleSignInAuthAdapter(firebaseAuth = ..., firestore = ..., ...)

// Стало:
OwnBackendAuthAdapter(httpClient = ..., backendUrl = ..., ...)
```

AuthProvider port не меняется — consumer'ы (S-5, S-8, etc.) не замечают переезда.

### 1.4. Чек-лист F-4 миграции

- [ ] Backend endpoint `POST /auth/google-signin` развёрнут + протестирован.
- [ ] Firestore `identity-links` export'нут в SQL и загружен в нашу БД.
- [ ] Контроль целостности: число записей в SQL = число в Firestore + 0 расхождений.
- [ ] `OwnBackendAuthAdapter` написан + проходит тот же `AuthProviderContractTest`.
- [ ] Client switch через BuildConfig сделан.
- [ ] Read-only mode на Firestore identity-links collection (writes отключены).
- [ ] 30-дневный fallback period: если backend упадёт — клиент возвращается
      к Firebase auth (через feature flag).
- [ ] После 30 дней без incident'ов — Firestore collection удалена.

---

## 2. Config Sync (SRV-CONFIG-001)

**Будет заполнено когда F-5 + S-8 спеки достанутся до миграции.**

Сейчас: client пишет в Firestore через client-side transaction. После
переезда: client POST'ит к нашему backend, backend атомарно (одна DB
транзакция) пишет new + history.

См. [`server-roadmap.md` §SRV-CONFIG-001](server-roadmap.md#srv-config-001).

---

## 3. Encrypted Media Storage (SRV-CRYPTO-001)

**Будет заполнено когда S-5 + F-5 ship'нутся.**

Сейчас: Cloudflare Worker proxy → Backblaze B2 storage.
После переезда: own server REST endpoints → own S3-compatible storage
(может остаться B2 — drop-in S3 API).

См. [`server-roadmap.md` §SRV-CRYPTO-001](server-roadmap.md#srv-crypto-001-универсальный-маршрут-переезда-крипто-инфраструктуры-на-собственный-backend).

---

## 4. FCM Push (SRV-CMD-001)

**Будет заполнено когда S-4 SOS ship'нется.**

FCM **остаётся** Google'овским (нет смысла своё писать). Меняется только
**кто** отправляет push: сейчас Cloudflare Worker → потом наш backend.

---

## 5. Общие архитектурные вопросы при cutover'е

Прежде чем начинать миграцию, ответить на эти вопросы и зафиксировать ответы
в этом файле:

1. **Backend stack**:
   - Язык: Kotlin/Ktor (KMP harmony с client'ом) / Node.js/Fastify / Go / другое.
   - Database: PostgreSQL + Redis для cache & queues.
   - Hosting: VPS (Hetzner / DigitalOcean) / Kubernetes / serverless.

2. **Domain имена**:
   - Production API: `api.launcher-app.com` (или твоё).
   - Staging API: `api-staging.launcher-app.com`.
   - CDN / static: `cdn.launcher-app.com`.

3. **Secrets management**:
   - Vault для production credentials (HashiCorp Vault / AWS Secrets Manager).
   - НЕ environment variables в plain text.
   - НЕ git repo (даже private).

4. **Monitoring**:
   - APM: Sentry / DataDog / Grafana + Prometheus.
   - Uptime checks: UptimeRobot / Better Uptime.
   - On-call rotation: PagerDuty / Opsgenie.

5. **Backup strategy**:
   - Postgres: daily full + WAL streaming в отдельный region.
   - Encrypted blobs: cross-region replication.
   - Тестовое restore раз в квартал.

---

## История изменений

| Дата | Что добавлено | Контекст |
|------|---------------|----------|
| 2026-06-19 | Initial skeleton: §0 принципы, §1 F-4 миграция (детально), §2-4 placeholders, §5 общие вопросы | После закрытия spec 017 F-4 |
