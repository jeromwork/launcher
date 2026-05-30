# 09 — Что обновить в roadmap и server-roadmap

## Что менять в `docs/product/roadmap.md`

### Раздел F-4 (текущий line ~589)

**Было:** F-4 AuthProvider + Google Sign-In, ~2 weeks Medium.

**Стало:** F-4 Unified Identity Layer, ~12-16 weeks (mega-block).

Расширить раздел F-4 следующими пунктами:
- Удаление anonymous Firebase Auth полностью.
- Unified app model: один app, runtime preset (Standard / Senior через wizard).
- Email REQUIRED, refuse без email.
- Pair-binding = delegation между two identified users.
- Spec'и 007-012 переписываются в части identity.
- Pre-F-4 anonymous pair'ы wipe (server migration tool).
- Privacy Policy + Data Safety + OAuth Consent — pre-release tasks.
- Inline TODO для own-server cutover.

### Добавить раздел F-7 (новый)

**F-7: 2FA Admin Device Migration** — post-own-server cutover spec.
- Описание: pair-binding восстанавливается через 2FA escrow на своём сервере при смене телефона admin'а.
- Dependencies: own-server foundation готов, F-5 (E2E encryption) готов.
- Effort: TBD когда дойдёт.
- См. файл 05 в decisions/2026-05-30-f4-identity/.

### Phase 1 (Foundation) пересчитать

Старая структура:
```
F-2 Capability Registry  ─┐
F-3 Wizard Module        ─┼── могут параллелизоваться
F-4 AuthProvider Google  ─┘
F-5 ConfigDocument E2E   🔴 PRODUCTION BLOCKER
```

Новая структура:
```
F-2 Capability Registry          (параллельно)
F-3 Wizard Module + Localization (параллельно)
F-4 Unified Identity Layer       🔴 mega-block, переписывает 007-012 (~12-16 weeks)
F-5 ConfigDocument E2E           🔴 PRODUCTION BLOCKER (после F-4)
F-7 2FA Device Migration         (после own-server cutover, не часть MVP)
```

### Phase 2 (MVP Vertical Slices) — добавить блокер на F-4

Все S-* спеки теперь явно ожидают F-4 (а не «depends on AuthProvider»). Уточнить графически:

```
F-4 ──► все S-* slices
F-4 + F-5 ──► production release
```

### Pre-MVP timeline сдвиг

Текущий roadmap: MVP ~6-8 months parallelized.
Новый: MVP **+ 3-4 месяца** сдвиг из-за F-4 mega-block (~10-12 months).

Записать как явное decision с обоснованием: «решение 2026-05-30 — sacrifice timeline ради clean identity модели для долгосрочной поддерживаемости и own-server cutover».

### Unified app model — новый раздел

В Part II «Vision Recap» или отдельным блоком: «Unified app model: один app, runtime preset».

Содержание: см. файл 02 в decisions/2026-05-30-f4-identity/.

### Server-roadmap horizon

Update: «~6 месяцев после MVP launch — Phase 1 cutover (свой JWT issuer + Google Sign-In integration)».

Реалистичный slip horizon: до 9-12 месяцев. Acceptable.

## Что менять в `docs/dev/server-roadmap.md`

### Добавить раздел §auth-jwt-issuer

Описание:
- Сейчас: Firebase Auth выдаёт JWT.
- После cutover: свой сервер принимает Google ID Token (POST /auth/google), верифицирует подпись Google, выпускает own JWT.
- Frontend: AuthProvider port не меняется; адаптер swap'ается (GoogleSignInFirebaseAuthAdapter → GoogleSignInOwnServerAuthAdapter).
- Timeline: Phase 1 cutover, ~6 месяцев после MVP launch.

### Добавить раздел §config-storage

Описание:
- Сейчас: Firestore /admin-self-configs/, /delegations/, /users/.
- После cutover: свой REST/WebSocket API.
- Security rules → server-side authorization.
- Data export tool from Firebase → import в свою DB.
- Timeline: Phase 2 cutover, ~9-12 месяцев после MVP launch.

### Добавить раздел §push-triggers

Описание:
- Сейчас: Cloudflare Worker триггерит FCM HTTP API.
- После cutover: свой сервер триггерит FCM HTTP API.
- **FCM остаётся** как transport (Android) и APNs (iOS).
- Worker либо поглощается своим сервером, либо остаётся edge-кэшем.
- Timeline: Phase 3, после Phase 2.

### Добавить раздел §2fa-key-escrow

Описание:
- Сейчас: pair-keys локально на устройстве admin'а; при смене телефона требуется rescan QR (MVP).
- После own-server: pair-keys зашифрованы и хранятся на сервере под adminUid; расшифровываются на новом устройстве через 2FA factor (Google passkey / device PIN).
- См. файл 05.
- Timeline: post-own-server cutover, после F-5.

## Что менять в `docs/dev/project-backlog.md`

Добавить entries:

- `TODO-FUTURE-SPEC-012` — Admin device migration via 2FA escrow (post-own-server).
- `TODO-FUTURE-SPEC-013` — PhoneAuthAdapter (post-MVP).
- `TODO-FUTURE-SPEC-014` — EmailAuthAdapter (post-MVP).
- `TODO-FUTURE-SPEC-015` — AppleSignInAuthAdapter (V-1 iOS).
- `TODO-FUTURE-SPEC-016` — CrossDeviceAuthAdapter для TV (V-4 post-MVP).
- `TODO-FUTURE-SPEC-017` — OwnServerJwtAuthAdapter (Phase 1 cutover).
- `TODO-FUTURE-SPEC-018` — Firebase data export tool (pre-cutover).

## Когда применять эти изменения

После того как ты соберёшь воедино новые decisions с пересмотрами спеки 014 и явно подтвердишь финальную концепцию. **До этого** — не трогать roadmap, чтобы не зафиксировать половинчатое решение.
