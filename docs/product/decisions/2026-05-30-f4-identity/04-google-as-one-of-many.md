# 04. Google Sign-In = один из провайдеров, не основа

**Дата фиксации**: 2026-05-30

---

## Суть решения

Google Sign-In — **частный случай** identity провайдера. Архитектура **не завязана** на Google. То что в MVP реализуется только Google adapter — это **временное** упрощение scope'а, не философская приверженность Google.

Будущие провайдеры (Email/Password, Phone, Apple Sign-In, наш собственный) — **first-class**, не fallback.

---

## Стандартный pattern: Sign in with Google + own JWT issuer

**Сейчас (Firebase phase):**
```
Registration & Session:
  Credential Manager → Google ID Token
    ↓
  Firebase Auth (обменивает Google ID Token на Firebase JWT)
    ↓
  Frontend хранит Firebase JWT, использует в запросах к Firestore
```

**После own-server cutover:**
```
Registration:
  Credential Manager → Google ID Token
    ↓
  POST ID Token на наш /auth/google
    ↓
  Наш сервер верифицирует подпись Google (Google public keys, JWKS)
    ↓
  Сервер создаёт user record (если первый раз) или находит существующий
    ↓
  Сервер выпускает СВОЙ JWT с нашими claims
    ↓
Session:
  Frontend хранит наш JWT
  Использует в запросах к нашему backend'у
```

**Google остаётся как identity verifier forever** — это стандартная индустриальная практика. Firebase Auth уходит, потому что в нём нет смысла, когда у нас есть свой JWT issuer.

То же будет для других провайдеров:
- Apple Sign-In → Apple ID Token → наш `/auth/apple` → наш JWT.
- Email/Password → POST на наш `/auth/email` → проверка пароля → наш JWT.
- Phone (SMS OTP) → POST на наш `/auth/phone-verify` → проверка кода → наш JWT.

**Любой identity verifier** конвертируется в **наш JWT**, который используется единообразно.

---

## Что это означает для F-4

**Архитектурно**: `AuthProvider` port + `AuthMethod` sealed type — построены **с учётом** этой будущей картины. Никакие сигнатуры port'а не специфичны для Google.

**Реализация**: `GoogleSignInFirebaseAuthAdapter` в MVP — это **прокси к Firebase Auth**. После cutover он заменяется на `GoogleSignInOwnServerAdapter` (другая Step 2/3 внутри adapter'а), port не меняется.

**Inline TODO** в adapter'е:
```kotlin
// TODO(server-roadmap): После own-server cutover —
//   заменить Firebase Auth на POST к /auth/google.
//   Подробнее: docs/product/decisions/2026-05-30-f4-identity/04-google-as-one-of-many.md
//   Подробнее: docs/dev/server-roadmap.md §auth-cutover
```

---

## Почему НЕ строим Email/Phone/Apple adapter'ы сейчас

Per CLAUDE.md rule 4 (MVA — Minimum Viable Architecture):

- `AuthMethod` sealed type **с 4 case'ами** существует с первого дня — это **легитимно**, потому что это **honest extensibility** (мы **знаем**, что хотим эти провайдеры).
- **Adapter'ы** для Email/Phone/Apple **не реализуются** до тех пор, пока нет конкретной user-facing спеки, требующей их. Это premature implementation.

Каждый из этих провайдеров добавляется как:
- Отдельная спека (например, «F-X: Phone Auth для users без Google»).
- Новый adapter в `app/androidMain/auth/`.
- UI для выбора метода в Sign-In экране.
- Без переписывания domain или существующих adapter'ов.

---

## Не-Google устройства (Huawei, FireOS, etc.)

**MVP**: out of scope. Поддерживаем только устройства с GMS (Google Play Services).

**Long-term post own-server**: возможный target. На своём сервере мы **не зависим** от Firebase Auth, поэтому Huawei users могут логиниться через **Email/Password** или **Phone Auth** (которые не требуют Google). Это работает на одной той же серверной инфраструктуре.

Но это требует:
- Реализовать Email или Phone adapter (отдельная спека).
- Решить, доступен ли app в AppGallery (Huawei store) — отдельный distribution вопрос.
- Push transport: для Huawei устройств нужен HMS Push Kit (FCM не работает без GMS).

Это **post-V-1**, не сейчас.

---

## Ключевая мысль для понимания

**Google Sign-In ≠ Firebase Auth.** Это **два разных сервиса**:

- **Google Sign-In** — identity verification (Google говорит «да, это user X, его email такой»). Это OAuth/OIDC протокол. **Стандарт.** Мы будем использовать его forever.
- **Firebase Auth** — session management (хранит юзеров, выдаёт JWT, делает refresh). Это **сервис Firebase**. Мы используем его сейчас как **временный shortcut**. После own-server — выпиливаем.

Эти два понятия часто смешиваются ("залогинился через Google = залогинился в Firebase"), но они **разные**. F-4 эту разницу делает явной в архитектуре.

---

## Связанные документы

- [03-auth-provider-port.md](03-auth-provider-port.md) — port + sealed type, расширяемый.
- [05-own-server-migration-strategy.md](05-own-server-migration-strategy.md) — phased migration, JWT issuer первым.
- [02-identity-anonymous-removal.md](02-identity-anonymous-removal.md) — anonymous уходит независимо от провайдера.
