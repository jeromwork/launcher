# Checklists overview — spec 007

Этот файл — индекс checklists для спека 007 `pairing-and-firebase-channel`.

## Status

| Checklist | Trigger | Status |
|---|---|---|
| `requirements-quality` | always-on | **TODO** — run after `/speckit-plan` produces plan.md |
| `meta-minimization` | always-on | **TODO** |
| `wire-format` | spec вводит wire-formats (`/pairings`, `/links/*`, FCM payload, JSON schemaVersion) | **TODO** |
| `domain-isolation` | spec вводит port `RemoteSyncBackend` + Firebase adapter; explicit anti-corruption layer | **TODO** |
| `security` | spec явно затрагивает auth (anonymous Firebase Auth), Security Rules, PII (adminId, fcm-token), exported components (deep-link `launcher://pair?token=...`) | **TODO** |
| `permissions-platform` | `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (Android 13+ для FCM), deep-link verification | **TODO** |
| `failure-recovery` | spec явно обсуждает offline, expired token, race condition, GMS absent fallback | **TODO** |
| `localization` | consent screen, баннеры — все user-facing strings должны быть externalised | **TODO** |
| `elderly-friendly` | consent screen, Settings revoke flow видны бабушке — нужен senior-safe pass | **TODO** |
| `state-management` | pairing-флоу пересекает Activity recreation (QR-screen ↔ consent-screen) | **TODO** |
| `core-quality` | release-bound feature (Firebase real backend в production builds) | **TODO (defer until plan.md exists)** |

## Когда запускать

Checklist-skill'ы корректно работают **после плана** — им нужен `plan.md` чтобы судить о соответствии форме данных, architecture decisions и т.п. Поэтому в этом проходе `/speckit-clarify` они **не выполнены** — оставлены как явный TODO для `/speckit-analyze`.

Это **не** скрытый workaround — это соответствует процедуре: `speckit-analyze` явно реран все relevant checklists с актуальными артефактами как «второй пары глаз» перед имплементацией. Текущий проход `/speckit-clarify` ограничился разрешением grey zones (8 → 0) и весит ровно столько, сколько нужно для разблокировки `/speckit-plan`.

## Что важно проверить в `/speckit-analyze`

- **wire-format**: каждое из `/pairings/{token}`, `/links/{linkId}`, `/state`, `/config` (bootstrap-форма), FCM payload — имеют `schemaVersion: Int` начиная с `1`, есть roundtrip тест, есть backward-compat тест.
- **domain-isolation**: ни один файл в `:core/api/**` не импортирует `com.google.firebase.*`. Лучше — `lint:ImportRestrictionRule` в `:core/api/build.gradle.kts`.
- **security**: Firestore Security Rules написаны, протестированы через `@firebase/rules-unit-testing` или Firebase emulator, не разрешают cross-link reads.
- **permissions-platform**: `POST_NOTIFICATIONS` запрашивается runtime на Android 13+ с человеческим объяснением; нет `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (см. C7).
- **failure-recovery**: на каждой ошибке (token expired, network fail, GMS absent, FCM token rotated, Firestore permission denied) — явный UX-fallback с действием для пользователя.

## Открытые риски (вне грей-зон)

- **Blaze plan на Firebase project** (необходим из-за Cloud Functions, см. C1) — финансовое решение, требует подтверждения project owner'а перед началом Phase 1 имплементации.
- **`google-services.json` в репозитории**: для dev-проекта — да; для production — через CI secrets; production проект может ещё не существовать на момент старта спека.
- **iOS source-set**: спек 007 — Android-only пока; в KMP `:core/api` остаётся pure, в `androidMain` живут все Firebase-adapter'ы; в `iosMain` — `expect`/`actual` stub'ы, которые бросают `NotImplementedError`. Это согласуется с CMP+KMP подходом per ADR-005.
