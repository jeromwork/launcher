# 07 — Стратегия переезда на свой сервер

## Решение

Firebase — **временный backend для разработки и MVP** (тестовый стенд). Через **~6 месяцев после MVP launch** начинается переезд на свой сервер. Переезд **постепенный, по фазам**, не big-bang.

Параллельно с разработкой MVP-фич **не строим** свой сервер. Вместо этого **везде**, где код трогает Firebase, оставляем **inline TODO** для миграции — что именно нужно реализовать на своём сервере.

**FCM (Android push) и APNs (iOS push) остаются как transport layer навсегда.** Заменяем только триггеры (с Cloudflare Worker / Firebase на свой сервер).

## Что заменяем, а что остаётся

| Сервис | Что делает | План |
|---|---|---|
| Google Sign-In (OAuth) | Идентификация владельца | **Остаётся** (один из providers) |
| Firebase Auth (session management) | JWT issuance, refresh | **Свой JWT issuer** на нашем сервере |
| Firestore (data storage) | Хранение конфигов | **Своя DB** (Postgres / etc) |
| Firestore Security Rules | Авторизация на read/write | **Свой authorization engine** на сервере |
| FCM (Android push transport) | Доставка push на устройство | **Остаётся** (единственный надёжный путь без foreground service) |
| APNs (iOS push transport) | Доставка push на iOS | **Остаётся** (нет альтернативы) |
| Push triggers (сейчас в Cloudflare Worker) | Кто решает «отправить push» | **Свой сервер** будет триггерить FCM/APNs |
| Cloudflare Worker (proxy / token issuer) | Edge logic | **Поглощается** своим сервером, либо остаётся как edge layer |
| Google Play Services (system) | Системная инфраструктура Google API | **Остаётся** на phone с GMS; non-GMS — отдельный вопрос |
| Google Play Store (distribution) | Дистрибуция app | **Остаётся** (sideload опционально) |

## Phased migration

```
Phase 0 (СЕЙЧАС — MVP development):
  - Firebase Auth + Firestore + FCM
  - Cloudflare Worker (proxy, push triggers)
  - Везде TODO для миграции

Phase 1 (~6 месяцев после MVP launch — начало переезда):
  - Свой backend: setup, hosting, DevOps
  - Свой JWT issuer (Google Sign-In → ID Token → свой сервер → own JWT)
  - Phased rollout: feature flag «use own JWT», постепенно включаем

Phase 2 (~9-12 месяцев после MVP launch):
  - Свой data API (replace Firestore reads/writes)
  - Свой authorization (replace Firestore Security Rules)
  - Data export tool from Firebase → import в свою DB
  - Cutover: переключаем app на свой backend

Phase 3 (~12+ месяцев):
  - Push triggers переезжают на свой сервер
  - FCM/APNs ОСТАЮТСЯ как transport — наш сервер вызывает их HTTP API
  - Firebase Auth выпиливается полностью
  - Cloudflare Worker либо поглощается, либо остаётся edge-кэшем
```

## Honest reality check (зафиксировано как осознанный риск)

- **Realistic estimate own-server**: ~5-9 месяцев full-time backend dev.
- **Пользователь принял horizon 6 месяцев после MVP** — это оптимистично, slip возможен до 12 месяцев.
- **MVP не блокируется отсутствием своего сервера** — продолжаем на Firebase до cutover.
- **2FA admin device migration** (см. файл 05) — реализуется ПОСЛЕ own-server cutover, не на Firebase escrow.

## Что обязательно в каждой текущей спеке

**Правило:** везде где код трогает сервер — inline TODO + entry в `docs/dev/server-roadmap.md`.

Шаблон inline TODO:
```kotlin
// TODO(server-roadmap): <конкретная операция> переезжает на свой сервер.
//   Сейчас: <что делает Firebase / Worker>.
//   После cutover: <что будет делать свой сервер>.
//   Server-roadmap entry: <ссылка на конкретный пункт>.
```

Примеры:

```kotlin
// app/androidMain/auth/GoogleSignInFirebaseAuthAdapter.kt
// TODO(server-roadmap): replace Firebase Auth с собственным JWT issuer.
//   Сейчас: Google ID Token → Firebase Auth → Firebase UID + session.
//   После cutover: Google ID Token → POST /auth/google на свой сервер →
//     сервер верифицирует подпись Google → выпускает own JWT.
//   Server-roadmap: §auth-jwt-issuer.

// app/androidMain/data/FirestoreConfigStore.kt
// TODO(server-roadmap): replace Firestore с своим REST/WebSocket API.
//   Сейчас: ConfigDocument через Firestore SDK.
//   После cutover: GET/POST /configs/{uid} на свой сервер.
//   Security rules → server-side authorization.
//   Server-roadmap: §config-storage.

// push-worker/trigger.js
// TODO(server-roadmap): триггеры переезжают на свой сервер.
//   FCM HTTP API остаётся (transport), но вызывается из нашего backend.
//   Server-roadmap: §push-triggers.
```

## Что прописать в spec 015 (F-4)

- Раздел «Exit ramp» с описанием cutover для AuthProvider.
- Inline TODO в `GoogleSignInFirebaseAuthAdapter`.
- Entry в `server-roadmap.md`: §auth-jwt-issuer.
- Тесты на `AuthProvider` port contract (а не на адаптер) — гарантируют что swap работает.

## Что НЕ делаем сейчас (overengineering trap)

- ❌ Не реализуем `OwnServerJwtAuthAdapter` сейчас (нет своего сервера).
- ❌ Не строим generic «identity provider plugin system» (CLAUDE.md rule 4 MVA).
- ❌ Не пытаемся поддержать non-GMS phone в MVP (out of scope).
- ❌ Не строим dual-mode (parallel Firebase + own JWT) — пользователь явно сказал «нет, я буду делать по другому, постепенно».
- ❌ Не строим свой push pipeline — FCM/APNs остаются как transport forever.

## Adjacent concerns

1. **Data export tool from Firebase** — обязательно ДО cutover. Firebase даёт export через GCS bucket; нужен parser. ~2-3 недели работы.
2. **Worker'у растёт scope** — Worker сейчас proxy/triggers; после cutover либо становится full backend, либо его заменяет другой сервер. Решение при старте Phase 1.
3. **FCM tokens привязаны к Firebase project** — при swap'е на свой сервер токены **остаются валидны**, потому что FCM сохраняется. Но если когда-нибудь сменим Firebase project — re-register всех токенов.
4. **GDPR data portability** — при cutover'е owner должен иметь право на export своих данных. Это **независимо** от cutover'а, должно быть всегда.
5. **Billing horizon** — Firebase Spark free tier имеет лимиты. Если MVP взлетит — есть риск upgrade на Blaze (pay-as-you-go) **до** cutover'а. Это аргумент **не затягивать** Phase 1.

## Принципы из CLAUDE.md, которые это закрывает

- **rule 8** (server migration tracking) — каждое решение «client-side / Firebase / Worker» имеет конкретный exit ramp.
- **rule 4** (MVA) — не строим свой сервер сейчас; абстракции минимальны.
- **rule 1, 2** (domain isolation, ACL) — все Firebase зависимости изолированы в адаптерах.
- **rule 5** (wire-format versioning) — все persisted документы с schemaVersion, backward-compat при cutover.
