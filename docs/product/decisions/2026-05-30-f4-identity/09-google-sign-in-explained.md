# 09. Google Sign-In — концептуальное и техническое объяснение

**Назначение**: повторное обращение к теме при возвращении к спеке 015. Объяснение для не-Android-разработчика.

---

## Что это концептуально

**Google Sign-In** — способ дать пользователю войти в наше приложение через его Google-аккаунт (тот же, что для Gmail / Play Store), вместо отдельного логина и пароля.

Для нас это **identity provider** — внешний сервис, которому мы делегируем «кто этот человек, и тот ли он, за кого себя выдаёт».

**Что важно**: мы вообще **не видим и не храним** пароль пользователя. Google проверяет его сам и говорит нам «да, это user X, его email такой-то». Нам отдают подписанный токен (как нотариально заверенную справку), мы проверяем подпись и доверяем содержимому.

---

## Поток шаг-за-шагом (наш use-case)

```
1. Пользователь (помощник или сама бабушка) на телефоне
   │
   ▼
2. Наш app показывает кнопку «Войти через Google»
   │
   ▼
3. Наш app зовёт Credential Manager (Android system API)
   │
   ▼
4. Credential Manager показывает bottom-sheet с Google-аккаунтами на устройстве
   │
   ▼
5. User выбирает аккаунт → Google проверяет его (биометрия / PIN)
   │
   ▼
6. Google выдаёт ID Token — подписанный JWT с claims:
   - email (что мы используем)
   - sub (Google User ID)
   - name (display name)
   - exp (когда истекает)
   - подпись Google
   │
   ▼
7а. (Сейчас, MVP) Наш app передаёт ID Token в Firebase Auth.
    Firebase обменивает его на Firebase UID + session token.
    Frontend хранит Firebase session token.
   │
7б. (После own-server) Наш app POST'ит ID Token на наш /auth/google.
    Наш сервер верифицирует подпись Google,
    создаёт user record (или находит существующий),
    выпускает наш JWT с нашими claims.
    Frontend хранит наш JWT.
   │
   ▼
8. Domain получает User(uid, email, displayName) через AuthProvider port.
   Никакого Firebase / Google SDK в domain — только наш port.
```

---

## Технические термины (простым языком)

- **OAuth 2.0** — протокол «как одно приложение получает разрешение действовать от имени пользователя в другом сервисе, не зная пароля». Стандарт интернета.
- **OpenID Connect (OIDC)** — надстройка над OAuth, добавляющая identity (кто пользователь, его email, имя), не только «может ли он что-то делать».
- **ID Token** — JWT (подписанный JSON Web Token) с claims (email, sub, name, exp). Это и есть «справка от Google».
- **Access Token** — токен для вызова Google API (Gmail, Drive). **Нам не нужен** — мы не вызываем Google API, только идентифицируем пользователя.
- **Refresh Token** — даёт получать новый ID Token без повторного login. Firebase Auth / наш сервер обрабатывает это автоматически.
- **OAuth Client ID** — идентификатор нашего app в Google Cloud. Формат `123456-abc...apps.googleusercontent.com`. Создаётся автоматически при включении Google Sign-In в Firebase Console.
- **SHA-1 fingerprint** — «отпечаток» нашего подписывающего ключа (40 hex символов). Google узнаёт наш app по подписи, не по имени пакета. У тебя минимум **два разных ключа**: debug (автоматический) и release (твой собственный для Play Store) — обоих SHA-1 надо добавить в Firebase Console.
- **Credential Manager** (`androidx.credentials`) — современная Android-библиотека для всех методов login (Google, passkeys, password manager). Это **рекомендованный путь сейчас**, не deprecated Google Sign-In SDK.
- **Google Play Services (GMS)** — фоновый системный сервис на Android-устройствах, через который работают все Google API. Без него Sign-In невозможен.

---

## Что нужно настроить (вне кода)

| Что | Где | Кто | Сложность |
|---|---|---|---|
| Включить Google Sign-In provider | Firebase Console → Authentication → Sign-in methods | admin Firebase project'а | 1 клик |
| OAuth Client ID | Создаётся автоматически Firebase'ом | — | автоматически |
| OAuth Consent Screen | Google Cloud Console → APIs & Services → OAuth consent screen | admin Google Cloud project'а | 10-15 мин (scopes = только `openid email profile`) |
| SHA-1 для debug.keystore | Firebase Console → Project Settings → SHA certificate fingerprints | каждый dev на своей машине | 5 мин (`./gradlew signingReport`) |
| SHA-1 для release keystore | Firebase Console → Project Settings → SHA certificate fingerprints | владелец release-keystore | 5 мин |
| google-services.json | Скачать из Firebase Console, положить в `app/` | каждый dev | 5 мин |
| Privacy Policy update | Свой документ | владелец продукта | требует юридического review |
| Play Console Data Safety form | Play Console | владелец Play Console | 30 мин |

---

## Подводные камни (типичные pitfalls)

1. **Эмулятор без Google Play Services не логинится.** Если ты создаёшь AVD (Android Virtual Device) через Android Studio — есть две колонки: «Google Play» (с Play Services, можно логиниться) и «Google APIs» (без Play Store, нельзя). **Для разработки и тестирования F-4 обязательно нужен AVD с Google Play.**

2. **SHA-1 разный для debug и release.** У каждого dev'а свой `debug.keystore` → свой debug SHA-1. У release — общий ключ (или Play App Signing, где Google управляет ключом, но тебе нужен upload SHA-1). **Все ключи нужно добавить в Firebase Console.** Иначе debug работает, release падает с `DEVELOPER_ERROR`.

3. **OAuth Consent Screen scopes — минимум.** Если вдруг кто-то добавит scope типа `gmail.readonly` — Google потребует verification process (3-6 недель работы юристов Google). Держать только `openid email profile`. Записано в спеке как inline TODO.

4. **Multi-account на устройстве**. Credential Manager покажет все Google-аккаунты, привязанные к Android-устройству. User сам выбирает. Это OK для нашего use-case (не строим per-account switching UX в MVP).

5. **Google Sign-In UI кастомизировать нельзя**. Google рисует bottom-sheet. Это **не проблема** в нашем продукте — настройка проходит в Standard mode (см. [01-unified-app-model.md](01-unified-app-model.md)) до переключения в Senior mode.

6. **NO_PROXY environment variable** — на этом проекте уже зафиксировано в memory как обязательное для эмулятора (per spec 007 operational state). Без него Firebase Auth не работает в эмуляторе за корпоративным прокси.

7. **JDK 21** — также зафиксировано в memory как обязательное для проекта.

---

## Email REQUIRED — почему

В 99.9% реальных Google-аккаунтов email есть (это базовое требование Google account). Случай «Google-аккаунт без email» — редкие corporate Google Workspace, где админ организации заблокировал email scope.

Для нашей audience (взрослые родственники пожилых) — **practically zero**.

**Решение**: email = REQUIRED в `User` data class. Если Google вернул user без email → refuse login с UI ошибкой «Этот Google-аккаунт не подходит, используйте личный Google-аккаунт». Это убирает null-checks везде и даёт sealed invariant в domain.

См. [02-identity-anonymous-removal.md](02-identity-anonymous-removal.md) для контекста и [03-auth-provider-port.md](03-auth-provider-port.md) для модели `User`.
