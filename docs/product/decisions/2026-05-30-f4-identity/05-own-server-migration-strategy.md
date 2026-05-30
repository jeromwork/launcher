# 05. Стратегия миграции на свой сервер

**Дата фиксации**: 2026-05-30
**Заменяет**: предыдущее «когда-нибудь переедем» из CLAUDE.md rule 8

---

## Суть решения

Owner проекта планирует **полностью** переехать с Google/Firebase инфраструктуры на собственный сервер. Phased migration. **Старт разработки own-server — ПОСЛЕ MVP**, не параллельно.

**Сейчас** (во время MVP-спек) — **только TODO в коде** в каждом месте, которое потом мигрирует.

**Horizon**: 6 месяцев после старта own-server разработки до Phase 1 cutover (JWT issuer + main API). Полный переезд (Phase 2-3) — до года.

---

## Что переезжает / что остаётся

| Слой | Текущее | После переезда | Когда |
|---|---|---|---|
| **Identity verification** (Google Sign-In) | Google OAuth | Google OAuth (без изменений) | Forever |
| **Session management** (JWT issuance) | Firebase Auth | Own JWT issuer | Phase 1 cutover |
| **Data storage** (config, contacts, photos metadata) | Firestore | Own DB (PostgreSQL/MongoDB/etc) | Phase 2 cutover |
| **Security rules** (authorization) | Firestore Security Rules | Own server-side authorization | Phase 2 cutover |
| **Real-time sync** | Firestore listeners | WebSocket / SSE на own server | Phase 2 cutover |
| **Backend logic / proxy** | Cloudflare Worker | Own backend (Worker может остаться как edge layer) | Phase 2 cutover |
| **Push transport — Android** | FCM | **FCM остаётся** | Forever |
| **Push transport — iOS** | (будущее) APNs | **APNs остаётся** | Forever |
| **Push triggers** (что запускает push) | Cloudflare Worker → FCM HTTP API | Own server → FCM HTTP API | Phase 1 cutover |
| **Crypto / E2E** (libsodium) | На клиенте | На клиенте (без изменений) | Forever |

**Ключевой принцип**: **FCM + APNs как transport остаются forever.** Полный exit от Google push pipeline = месяцы работы ради скромного выигрыша. Своя push transport — out of scope даже long-term.

**Триггеры** (что запускает push) — наши. Они уже сейчас на Worker'е, после cutover переезжают на own-server.

---

## Phases

### Phase 0 (сейчас) — Development on Firebase

- Firebase Auth + Firestore + FCM + Cloudflare Worker как сейчас.
- Все новые спеки добавляют **inline TODO** в каждом месте, трогающем сервер.
- **Не строим** ничего из own-server инфраструктуры.

### Phase 1 (после MVP, ~3-4 месяца разработки own-server) — Auth cutover

**Что строится:**
- Own backend (язык, framework, hosting — отдельная decision spec).
- Own JWT issuer endpoint `/auth/google` (верифицирует Google ID Token, выпускает own JWT).
- Endpoint refresh JWT.
- User table в own DB (минимальная — uid, email, displayName, createdAt).
- Migration tool: export users из Firebase Auth → import в own DB.

**Что меняется на frontend:**
- `GoogleSignInFirebaseAuthAdapter` → `GoogleSignInOwnServerAdapter` (port остаётся, adapter swap).
- JWT storage переключается с Firebase управления на наше.

**Что остаётся:**
- Firestore (data) — пока Firebase.
- Push transport — FCM.
- Cloudflare Worker — продолжает proxy/trigger функции.

### Phase 2 (~2-3 месяца) — Data + sync cutover

**Что строится:**
- Own DB schema для config, contacts, photos metadata, delegations.
- REST endpoints для read/write.
- WebSocket / SSE endpoint для real-time sync.
- Server-side authorization (replaces Firestore Security Rules).
- Migration tool: Firestore export → own DB import.

**Что меняется на frontend:**
- `FirestoreConfigStore` → `OwnServerConfigStore`.
- Listeners на Firestore → WebSocket subscriptions.

### Phase 3 (~1-2 месяца) — Cleanup

- Удаляем Firebase Auth / Firestore SDK из app'а.
- Cloudflare Worker либо удаляется, либо превращается в edge proxy.
- Firebase project закрывается (после grace period).

### Phase 4 (post-own-server) — Расширение

- Поддержка non-GMS устройств (HMS / Email / Phone провайдеры).
- 2FA admin device migration (см. файл 06).
- Дополнительные auth providers.

---

## TODO rule — обязательно в коде

**Новое обязательное правило** (предлагается добавить в CLAUDE.md как rule 11 или расширить rule 8):

> **Любой код, который вызывает Firebase / Google / Cloudflare Worker API, ДОЛЖЕН содержать inline TODO с описанием перехода на own-server.**

Формат:
```kotlin
// TODO(server-roadmap): <операция> переезжает на own-server.
//   Сейчас: <как работает на Firebase/Worker>
//   После cutover: <как будет работать на own-server>
//   Cutover phase: <Phase 1 / 2 / 3>
```

**Пример:**
```kotlin
// TODO(server-roadmap): Authentication переезжает на own-server.
//   Сейчас: Firebase Auth обменивает Google ID Token на Firebase JWT.
//   После cutover: POST ID Token на /auth/google, сервер выпускает own JWT.
//   Cutover phase: Phase 1.
suspend fun signInWithGoogle(): User {
    val idToken = credentialManager.getGoogleIdToken()
    return FirebaseAuth.getInstance().signInWithCredential(...)
}
```

**Цель**: при подготовке own-server можно сгрепать `TODO(server-roadmap)` по всему репо и получить полный список того, что нужно мигрировать.

---

## Server-roadmap.md updates

Файл [`docs/dev/server-roadmap.md`](../../../dev/server-roadmap.md) обновляется:
- Добавляется таблица сервисов и timeline (как выше).
- Добавляется список Phase 1 endpoints (минимальный required список).
- Добавляется политика FCM/APNs (остаются forever).
- Добавляется политика TODO в коде.

---

## Что НЕ строится сейчас

- ❌ Own JWT issuer (Phase 1 — после MVP).
- ❌ Own DB или schema (Phase 2).
- ❌ Own push pipeline (не строится никогда — FCM/APNs forever).
- ❌ Migration tools (когда дойдём до cutover).
- ❌ Hosting / DevOps infra (когда дойдём).

---

## Связанные документы

- [03-auth-provider-port.md](03-auth-provider-port.md) — port остаётся при cutover, adapter меняется.
- [04-google-as-one-of-many.md](04-google-as-one-of-many.md) — Google Sign-In остаётся как identity verifier, Firebase Auth уходит.
- [`docs/dev/server-roadmap.md`](../../../dev/server-roadmap.md) — основной файл, обновляется.
- [`CLAUDE.md`](../../../../CLAUDE.md) rule 8 — server migration tracking (расширяется или дополняется rule 11).
