# 01 — Google Sign-In: что это и как работает

## Решение

В спеке 015 (F-4) реализуется **Google Sign-In** как первый из расширяемого набора провайдеров авторизации. Это **один из** способов входа, не единственный — архитектура (AuthProvider port + AuthMethod sealed) проектируется под расширение Email / Phone / Apple / own-server.

## Что это концептуально

Google Sign-In = способ дать пользователю войти в наше приложение через его Google-аккаунт (тот же, что для Gmail / Play Store), вместо отдельного логина и пароля. Мы **не видим и не храним пароль** — Google проверяет его сам и отдаёт нам подписанную «справку» (токен) что «да, это user X, email такой-то».

Для нас это **identity provider** — внешний сервис, которому мы делегируем «кто этот человек, и тот ли он, за кого себя выдаёт».

## Как это работает технически (упрощённо)

```
1. User в нашем app нажимает «Войти через Google»
2. Наш app зовёт Credential Manager (Android system API)
3. Credential Manager показывает bottom-sheet с Google-аккаунтами на устройстве
4. User выбирает аккаунт → Google проверяет (биометрия / PIN)
5. Google возвращает ID Token (подписанный JSON Web Token с email, name, sub)
6. Сейчас: наш app отдаёт ID Token в Firebase Auth → получает Firebase UID
   Будущее (own-server): наш app отдаёт ID Token СВОЕМУ серверу →
                          сервер верифицирует подпись Google →
                          выдаёт own JWT с своими claims
7. Domain получает User(uid, email, displayName) через AuthProvider port
```

## Ключевые термины

- **OAuth 2.0** — протокол «как одно приложение получает разрешение действовать от имени пользователя в другом сервисе, не зная пароля».
- **OpenID Connect (OIDC)** — надстройка над OAuth, добавляющая identity (email, name).
- **ID Token** — подписанный JWT с claims (email, sub, name, exp). Это и есть «справка от Google».
- **OAuth Client ID** — идентификатор нашего app в Google Cloud. Создаётся автоматически при включении Google Sign-In в Firebase Console.
- **SHA-1 fingerprint** — «отпечаток» подписывающего ключа нашего app. Каждый ключ (debug, release, upload) надо зарегистрировать в Firebase Console.
- **Credential Manager** — современная Android-библиотека для всех видов login (Google, passkeys, password). Замена deprecated Google Sign-In SDK.

## Что надо настроить (admin tasks вне кода)

| Что | Где | Кто делает |
|---|---|---|
| Включить Google Sign-In provider | Firebase Console → Authentication | Владелец Firebase project |
| OAuth Consent Screen | Google Cloud Console → APIs & Services | Владелец Google Cloud project |
| Добавить SHA-1 debug keystore | Firebase Console → Project Settings | Каждый dev |
| Добавить SHA-1 release keystore | Firebase Console → Project Settings | Владелец project |
| OAuth Client ID | Создаётся автоматически | Firebase |
| `google-services.json` | Скачать из Firebase Console, положить в `app/` | Каждый dev |
| Privacy Policy update | Свой документ | Владелец продукта |
| Play Console Data Safety form | Play Console | Владелец продукта |

## Что выбрано

- **Credential Manager** (новый, рекомендованный Google) — по умолчанию.
- Legacy Google Sign-In SDK как fallback — только если minimum SDK level требует. Свериться при старте спеки.
- **Один общий Firebase project** для всех устройств в MVP.
- Email = **REQUIRED** для admin (refuse login если Google вернул user без email).

## Подводные камни

1. **Эмулятор без Google Play Services не логинится.** AVD должен быть «Google Play», не «Google APIs».
2. **SHA-1 fingerprint** — у каждого dev свой debug.keystore, у release — свой. Все надо добавить.
3. **DEVELOPER_ERROR** при release сборке из Play Store — забыли upload-key SHA-1 в Firebase Console (если используется Play App Signing).
4. **Multi-account на устройстве** — Credential Manager покажет все аккаунты, user сам выбирает.
5. **Google Sign-In UI кастомизировать нельзя** — Google рисует bottom-sheet, не senior-safe. **Не проблема** в нашем продукте — Google Sign-In проходится **в Standard mode** перед переключением в Senior mode (см. файл 02).

## Что выходит за рамки MVP F-4

- ❌ Phone Auth, Email/Password Auth — sealed cases объявлены, реализация — отдельные future specs.
- ❌ Apple Sign-In — V-1 iOS territory.
- ❌ Account linking (admin добавляет second method) — post-MVP.
- ❌ Android TV via Device Code Flow / Cross-Device Sign-In — post-MVP, но AuthProvider port способен принять адаптер.
- ❌ Non-GMS phones (Huawei) — out of scope MVP.
