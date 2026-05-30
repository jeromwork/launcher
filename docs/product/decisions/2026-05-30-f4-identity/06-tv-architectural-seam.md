# 06 — TV: out of scope MVP, но архитектурный шов оставляется сейчас

## Решение

TV launcher (Android TV) **не реализуется** в MVP. Но **сейчас** в F-4 / F-014 проектируются три минимальных архитектурных шва, чтобы потом не переписывать ядро ради TV:

1. **AuthProvider** — sealed AuthMethod с расширяемыми case'ами (добавится CrossDeviceAuth для TV без переписывания domain).
2. **EditUiProfile** — sealed type с расширяемыми case'ами (добавится TVProfile без переписывания selector'а).
3. **ConfigDocument** — platform-agnostic, без hardcoded phone-specific assumptions (touch gestures, screen dimensions).

**Дискуссия о деталях TV** (что показывает, как настраивается, какие use-cases) — **откладывается до фазы разработки TV** post-MVP.

## Что зафиксировано из обсуждения 2026-05-30

### TV-launcher как продукт

- **«TV — это другой телефон, только формат UI/UX другой.»**
- **Настройки в формате обычного app — невозможны** на TV (нет touch, только D-pad). Альтернатива — remote configuration через phone admin app.
- Для TV **обязательна возможность настройки без привязки к телефону**, иначе app on выброс. **Но обсуждать это в MVP не будем.**

### Что показывает TV launcher (отложено, открытые варианты)

- **Curated app list**: предзаготовленный нами список популярных TV apps (Netflix, YouTube, Kinopoisk, etc), потому что устанавливаемых TV apps мало и они известны. Список — на нашем сервере, обновляется через API, не зашит в app.
- **Контакты для звонков** на TV — через наш будущий мессенджер (не стандартное dial).
- **Family photos** на TV — через тот же мессенджер.
- **Multi-profile** (бабушка / папа / дети на одном TV) — это **отдельный** вопрос (как разные именованные preset'ы на устройстве admin'а), отложен.

### Архитектурное расширение для TV (когда дойдёт)

```
app/
  ├─ phoneMain/          ← Compose UI для touch (текущий — Standard / Senior modes)
  ├─ tvMain/             ← compose-tv UI для D-pad (новый, post-MVP)
  └─ shared-ui/          ← общие компоненты и темы

core/commonMain/         ← общий домен, без изменений
```

**Дистрибуция:** один Play Store listing (если можно регистрировать тот же APK в TV section с разными описаниями) **или** отдельный TV listing — это product decision когда дойдёт до V-4. **НЕ два разных app**, в любом случае.

## Auth на TV (отложено, но шов готов)

Когда TV дойдёт — `CrossDeviceAuthAdapter` имплементирует Google Device Code Flow:

```
1. TV запускается, нужен Google Sign-In.
2. TV показывает QR-код (или 6-значный код).
3. User открывает google.com/device на телефоне, вводит код, подтверждает.
4. TV получает токен от Google.
5. Дальше та же логика что на phone (Firebase Auth или own-server).
```

Это **стандартный TV pattern**, поддерживается из коробки в Credential Manager API. В F-4 (спека 015) этот adapter **не реализуется**, но `AuthProvider` port способен его принять как ещё один case в `AuthMethod`.

## Non-GMS TVs (открытый вопрос)

- Huawei TVs, Xiaomi TVs китайского региона, небрендовые TV boxes — **без Google Play Services**.
- На таких TV Google Sign-In **не работает** в принципе.
- В V-4 spec'е придётся решать: либо «non-GMS TV out of scope», либо альтернативный identity (Phone Auth / Email Auth / собственный QR-pairing с phone).
- В F-4 **не решается** — но `AuthMethod` sealed type **способен** принять non-Google providers.

## Что НЕ обсуждаем сейчас

- Тип монетизации TV (отдельная подписка / включена в основную).
- Multi-user TV (Android system users vs наши preset'ы).
- Voice control (Google Assistant integration).
- Live TV channels integration.
- Streaming services deep-link.
- Parental controls.
- Remote-side caching.

## Когда вернёмся к TV

После того, как MVP-спеки пройдены и own-server foundation готов. Тогда отдельная спека V-4 с полноценным обсуждением. До тех пор — только швы.

## Принцип

> «**Сейчас** — только не закрываем дверь к TV (расширяемый sealed type, platform-agnostic domain). **Реализацию** TV UI — потом.»

Это минимизирует scope F-4 и оставляет нам время продумать TV нормально, когда дойдём.
