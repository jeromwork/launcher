# 08 — Copy-paste prompt для `/speckit.specify` (спека 015)

## Когда использовать

Когда готов стартовать спеку 015 (F-4 Unified Identity Layer) — скопировать prompt ниже и подать в `/speckit.specify`. Spec-kit оркестратор сгенерирует spec.md.

## Перед запуском

1. Прочитать файлы 01-07 в этом наборе — убедиться что финальная модель тебе ясна.
2. Проверить что 014 завершён или явно decoupled — F-4 mega-block переписывает спеки 007-012, и спека 014 (F-014.0) живёт параллельно на namedConfigs локально.
3. Создать ветку `015-unified-identity-layer` (или иное имя по согласованию).
4. Убедиться что F-4 dependencies готовы:
   - Firebase project настроен (Google Sign-In включён в Authentication).
   - OAuth Client ID создан.
   - SHA-1 fingerprints (debug + release) добавлены в Firebase Console.
   - `google-services.json` на месте.

## Prompt

```
Напиши спецификацию для F-4: Unified Identity Layer (Google Sign-In, all apps).

КОНТЕКСТ:
F-4 — это mega-block (~12-16 недель) переписывания identity слоя всего продукта.
Удаляет anonymous Firebase Auth полностью. Каждый app = свой Google-аккаунт = свой
Firebase UID. Подробное обсуждение и обоснование решений — в наборе документов
docs/product/decisions/2026-05-30-f4-identity/ (файлы 01-07).

Ключевые решения (из обсуждения 2026-05-30):

1. ANONYMOUS FIREBASE AUTH УДАЛЯЕТСЯ ПОЛНОСТЬЮ.
   Каждое устройство = свой Google-аккаунт = свой Firebase UID.
   Спеки 007-012 переписываются в части identity.

2. UNIFIED APP MODEL: один app, runtime preset.
   Не существует отдельных Admin app / Simple Launcher APK.
   После Google Sign-In + wizard, app переключается в Senior preset
   (фасад) или остаётся в Standard preset. 7-tap = back to Standard.

3. AUTHPROVIDER PORT + AUTHMETHOD SEALED.
   Один port для всех методов авторизации.
   AuthMethod sealed: Google (реализуется F-4) / Email / Phone / Apple
   (объявлены, реализация — future specs).
   Google — первый провайдер, но один из многих. Архитектура vendor-agnostic.

4. EMAIL = REQUIRED для admin User.
   Refuse login если Google вернул user без email.
   Sealed User type гарантирует invariant.

5. CREDENTIAL MANAGER (новый Android API) — primary.
   Legacy Google Sign-In SDK — fallback если minSdk требует.

6. PAIR-BINDING = DELEGATION между двумя identified users.
   /delegations/{ownerUid}/helpers/{helperUid} в Firestore.
   Owner + helper — оба Google-bound UIDs.
   Может быть создана, может быть отозвана. Отзыв не убивает app.

7. PRE-F-4 ANONYMOUS PAIR'Ы WIPE.
   Pre-release, реальных пользователей нет.

8. PAIR MIGRATION при смене телефона admin'а — OUT OF SCOPE F-4.
   В MVP F-4 — pair-binding не мигрирует автоматически, требует rescan QR.
   Полноценная migration через 2FA escrow — отдельная спека post-own-server cutover.
   Inline TODO в коде + backlog entry TODO-FUTURE-SPEC-012.

9. EXIT RAMP TODO в адаптере:
   После own-server cutover Firebase Auth уходит, Google ID Token идёт
   на свой сервер, который выпускает own JWT. Port остаётся.
   Это стандартный pattern Sign in with Google + own JWT issuer.

10. FCM/APNs ОСТАЮТСЯ как push transport forever.
    Триггеры переезжают на свой сервер при cutover'е.

ЦЕЛЬ F-4:
Создать AuthProvider port + AuthMethod sealed + GoogleSignInFirebaseAuthAdapter
+ FakeAuthAdapter + AuthAdapterSelector. Удалить anonymous flow из спек 007-012.
Wire-format /users/{uid} со schemaVersion. Privacy Policy + Data Safety + OAuth
Consent — pre-release tasks.

SCOPE ВКЛЮЧАЕТ:
- AuthProvider port в core/commonMain/auth/
- AuthMethod sealed: Google | Email | Phone | Apple (последние три — sealed
  declarations only, реализация — future specs)
- User data class (uid, email REQUIRED, displayName, authMethod)
- AuthError sealed (NoEmail, NetworkError, Cancelled, ProviderUnavailable, Unknown)
- GoogleSignInFirebaseAuthAdapter (Credential Manager + Firebase Auth)
- FakeAuthAdapter (pre-seeded test users)
- AuthAdapterSelector (runtime device-capability check)
- Wire-format /users/{uid} schemaVersion: 1
- /delegations/{ownerUid}/helpers/{helperUid} schemaVersion: 1
- Tests AuthProvider contract
- Inline TODO в адаптере для own-server exit ramp
- Server-roadmap entry §auth-jwt-issuer
- Перепись identity слоя в спеках 007-012 (UID источник: AuthProvider.currentUser)
- Wipe pre-F-4 anonymous pair'ов (server-side migration tool)
- Privacy Policy update + Data Safety form + OAuth Consent Screen tasks

SCOPE НЕ ВКЛЮЧАЕТ:
- PhoneAuthAdapter / EmailAuthAdapter / AppleAuthAdapter (future specs)
- OwnServerJwtAuthAdapter (server cutover spec)
- CrossDeviceAuthAdapter для TV (V-4 post-MVP)
- Account linking (post-MVP)
- 2FA admin device migration (отдельная спека post-own-server cutover)
- Non-GMS phone support (out of scope MVP)
- TV UI (V-4)
- Real own-server implementation (только TODO)
- Generic identity provider plugin system (overengineering, CLAUDE.md rule 4)

DEPENDENCIES:
- Firebase Auth project с Google Sign-In provider включён (admin task).
- OAuth Client ID создан в Google Cloud Console (admin task).
- SHA-1 fingerprints (debug + release) в Firebase Console (admin task).
- google-services.json в app/ (admin task).
- 014 F-014.0 завершён или явно decoupled (он на local-only DataStore — OK).

LOCAL TEST PATH (mandatory per D-2):
- JVM unit tests: AuthProvider port contract на FakeAuthAdapter.
- JVM unit tests: User invariants (email required, refuse login без email).
- JVM unit tests: AuthMethod sealed exhaustive matching.
- Android instrumented tests: GoogleSignInFirebaseAuthAdapter с реальным
  test Google account на эмуляторе с Google Play.
- Cross-device test: два эмулятора с разными Google accounts, pair-delegation flow.
- Wire-format roundtrip tests: /users/{uid} + /delegations/...

ВЕТКА: 015-unified-identity-layer

EFFORT: ~12-16 weeks (mega-block).

REFERENCE DOCS:
- docs/product/decisions/2026-05-30-f4-identity/ (полный набор обсуждения)
- docs/product/roadmap.md F-4 (обновится в рамках спеки)
- docs/dev/server-roadmap.md §auth-jwt-issuer (обновится)
- specs/014-tile-editing-admin-senior-profiles/ (interpretation α' совместимость)
- CLAUDE.md rules 1, 2, 4, 5, 6, 7, 8
- .specify/memory/constitution.md (Articles I-XVI)

CONSTITUTION GATES:
- Architecture (rule 1+2 domain isolation, ACL для Google/Firebase SDKs)
- Configuration (preset = runtime named config, не build-time)
- Required Context Review
- Accessibility (Google Sign-In UI проходит в Standard mode — OK)
- Battery/Performance (Credential Manager nominally lightweight)
- Testing (FakeAuthAdapter contract tests)
- Simplicity (rule 4 MVA — нет generic plugin system)

NOTES:
- Это переписка identity, не "новая фича". Спеки 007-012 продолжают работать
  функционально, меняется только источник UID.
- В UX-плане: помощник проходит Google Sign-In wizard на бабушкином телефоне
  при первой установке (физически или удалённо). Бабушка не путается, потому
  что Senior mode активируется после wizard'а.
- Migration concern: pre-F-4 anonymous pair'ы wipe, реальных пользователей нет.
```

## После генерации spec.md

1. Прогнать `procedure-assess-spec-complexity` → список релевантных checklist'ов.
2. Прогнать `procedure-constitution-check` на сгенерированный plan.md.
3. Прогнать `speckit-clarify` если есть grey areas.
4. Прогнать `procedure-cross-artifact-trace` после `speckit-tasks`.
