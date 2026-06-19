# Checklist: domain-isolation

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 16/16 ✓ — **passes baseline cleanly**. F-4 — образцовая реализация CLAUDE.md rule 2 ACL для auth-провайдеров.

---

## Vendor SDKs

- [x] **CHK001** No vendor SDK type в domain — ✓.
  - FR-002 explicitly: `core/domain/auth/` MUST НЕ импортировать `com.google.*`, `com.google.firebase.*`, `androidx.credentials.*`, `com.apple.*` — проверяется Detekt rule + Gradle dependency check.
  - FR-027: ни одно из слов `Google`, `Firebase`, `OAuth`, `Apple`, `Phone`, `Email` (case-insensitive) MUST НЕ быть в коде `core/domain/auth/` — Detekt rule.
  - **`AuthIdentity` после clarify Q1 содержит наш UUID `stableId`** — не Firebase UID, не Google `sub` claim. Это **значительное** усиление rule 2 — даже сам identity identifier vendor-agnostic.
- [x] **CHK002** One wrapper module per SDK — ✓.
  - Firebase Auth SDK + Credential Manager → **только** в `GoogleSignInAuthAdapter` (`app/androidMain/auth/`).
  - `EncryptedSharedPreferences` (`androidx.security:security-crypto`) → **только** в `EncryptedLocalSessionStore` (`app/androidMain/auth/`).
  - Никаких leakage points обнаружено.
- [x] **CHK003** "Vendor disappears tomorrow" test documented — ✓.
  - Firebase Auth disappears → replace 1 adapter file `GoogleSignInAuthAdapter`. Estimated 2-3 days (per meta-minimization CHK011).
  - Google Sign-In SDK deprecated → replace Step 1 of adapter. 1-2 days.
  - **Identity migration**: zero, because `stableId` = наш UUID, не Google sub claim (per clarification Q1). All `/users/{stableId}` документы продолжают работать.
  - Документировано в **TL;DR section** и **clarification Q1** explicit.

## Transport types

- [x] **CHK004** No transport types в domain — ✓.
  - FR-006 explicit: «Никаких `String token`, `Map<String, Any>`, JSON containers, Firebase / Google типов» в port signatures.
  - `AuthIdentity` содержит только Kotlin stdlib types (`String`, `String?`).
  - `SessionRecord` остаётся **internal** (clarification Q2) — никогда не пересекает domain boundary.
- [x] **CHK005** Wire format types — domain-owned — ✓.
  - `SessionRecord` (FR-013) — domain-defined data class. Сериализация — в adapter через `kotlinx.serialization.json` (FR-021). Class не generated DTO, а explicitly designed domain type.
  - Identity-links Firestore document structure (FR-016a) — описан как domain contract; serialization — adapter responsibility.
  - **No leaked DTOs**.

## Platform types

- [x] **CHK006** No `android.*` / `Intent` / `Context` / `Bundle` в `commonMain` — ✓.
  - FR-001 ограничивает `core/domain/auth/` package: только domain types.
  - Single platform-touching point: `EncryptedLocalSessionStore` (`app/androidMain/`) использует Android Context для EncryptedSharedPreferences — это **adapter**, не domain.
  - Local Test Path подтверждает: pure JVM unit tests возможны (no Android Context dependency for domain logic).
- [x] **CHK007** Domain values use domain-typed projections — ✓.
  - `AuthIdentity.stableId: String` (наш UUID — не Firebase UID, не Android account ID).
  - `AuthIdentity.email: String?`, `displayName: String?` — plain types.
  - `SessionRecord.expiresAt: Instant?` — `kotlinx.datetime.Instant`, не `java.time.Instant` или Firebase `Timestamp`.

## Ports

- [x] **CHK008** Every external surface через port — ✓.
  - Google Sign-In credential flow → `AuthProvider` port.
  - Local encrypted storage → `SessionStore` port (internal to F-4).
  - Identity-links Firestore lookup → currently implemented **inside** `GoogleSignInAuthAdapter` (FR-016a). **Open consideration**: возможно нужен отдельный `IdentityLinksRepository` port для testability — но это **partial bloat risk** (one current consumer). Recommendation: оставить inline в adapter; если в будущем PhoneAuthAdapter будет переиспользовать ту же lookup logic — extract в shared adapter-internal helper, не domain port.
- [x] **CHK009** Port shape driven by domain need — ✓.
  - `AuthProvider.signIn(): Outcome<AuthIdentity, AuthError>` — domain verb «sign in», not adapter convenience «invokeFirebaseCredentialManager».
  - `AuthProvider.signOut()` — domain verb «sign out», not «clearFirebaseSession».
  - `AuthProvider.currentUser: Flow<AuthIdentity?>` — domain observable, not «firebaseAuthStateListener».
  - **No adapter-convenience methods leak** в port.
- [x] **CHK010** Fake adapter существует — ✓.
  - `FakeAuthAdapter` (FR-024) с pre-seeded users + test triggers (FR-025): `simulateRefreshFailure()`, `simulateNoEmail()`, `simulateCancellation()`.
  - `FakeSessionStore` (FR-026) — in-memory HashMap.
  - **Available для всех higher-level код**: consumer'ы F-5, S-2 пишут тесты против Fake (provider-swap fitness test, US 6 / SC-008, проверяет это).
- [x] **CHK011** Real adapter существует — ✓.
  - `GoogleSignInAuthAdapter` (`app/androidMain/auth/`).
  - `EncryptedLocalSessionStore` (`app/androidMain/auth/`).
  - **No iOS adapter** в MVP — F-4 Android-only. Будущая iOS адаптация = добавление `iosMain/auth/AppleSignInAuthAdapter.kt` без изменений в domain. Это **planned cross-platform readiness**, не current need.
- [x] **CHK012** DI picks fake/real per build — ✓.
  - FR-019: `debug` / `test` → `FakeAuthAdapter`; `release` → `AuthAdapterSelector.pickAdapter()`.
  - Build-config gate + Detekt rule (FR-004) — двойная защита от accidental Fake в release.

## Source-set placement

- [x] **CHK013** Source-set assignments justified — ✓.
  - `core/domain/auth/` → **commonMain** (pure Kotlin, no platform). Justified: shared across platforms.
  - `app/androidMain/auth/` → **androidMain** (uses Credential Manager API, Firebase Auth SDK, EncryptedSharedPreferences). Justified: Android-specific APIs.
  - `core/commonTest/auth/` → **commonTest** (FakeAuthAdapter, FakeSessionStore). Justified: тестовая инфраструктура available для всех source sets.
  - `app/androidMain/auth/ui/SignInTrigger.kt` → **androidMain** (Compose UI). Justified: Android-specific Compose; будущая iOS версия — отдельный `iosMain/auth/ui/SignInTrigger.kt` с iOS Composable.
- [x] **CHK014** Default commonMain, deviations justified — ✓. Все android-side файлы документируют platform-specific reason (Credential Manager API, EncryptedSharedPreferences, Compose).

## Existing-code regressions

- [x] **CHK015** No vendor type reintroduction — ✓. F-4 — net-new spec, ничего не reintroduces. Naoborot, FR-029 fixates removal of anonymous Firebase Auth (Detekt ловит `signInAnonymously` imports).
- [x] **CHK016** No spurious expect/actual — ✓. F-4 не использует `expect`/`actual` declarations (pure Kotlin domain + Android-only adapter в MVP). Если iOS adapter добавится позже — `actual` будет в `iosMain/`, не domain.

---

## Notable strengths (beyond baseline)

### Strength 1: `stableId` = наш UUID — самый строгий ACL

Clarification Q1 устранила **скрытый leakage point**: даже если бы FR-002 запрещал vendor SDK imports в domain, использование Firebase UID или Google `sub` claim как `stableId` оставило бы vendor-shaped data в domain values. Owner explicitly выбрал UUID — это **значительное усиление** rule 2 ACL.

### Strength 2: Provider-swap fitness function (US 6, SC-008)

Большинство спек объявляют rule 2 ACL и надеются что это так. F-4 имеет **автоматизированный тест** (`ProviderSwapFitnessTest`): подмена `GoogleSignInAuthAdapter` на `FakeAuthAdapter` в DI → прогон consumer-тестов → identical pass rate. Это **proves** провайдер-agnostic дизайн, а не объявляет.

### Strength 3: SessionRecord стал internal post-Q2

Clarification Q2 убрала `SessionRecord` и `SessionStore` из публичного domain API. Consumer'ы видят **только** `AuthIdentity` через `currentUser` flow. Это устраняет risk что consumer случайно прочитает `extra["firebase_jwt"]` и завяжется на Firebase. **Сильнее**, чем просто Detekt rule.

### Strength 4: providerKind удалён (Q4)

Каждое поле `providerKind: ProviderKind` в `AuthIdentity` / `User` / `SessionRecord` создавало **потенциальный leakage**: consumer мог писать `when (providerKind) { GOOGLE -> ... else -> ... }` и завязаться на enum состав. Удаление поля устраняет этот risk полностью. Adapter знает свой kind для логирования у себя.

---

## Open consideration (не failure)

**`IdentityLinksRepository` port?**

FR-016a описывает identity-links Firestore lookup inside `GoogleSignInAuthAdapter`. Это **inline** в adapter (не отдельный port). Аргументы:

- **За extract port**: testability (Fake identity-links для tests), future PhoneAuthAdapter reuse, separation of concerns (Firestore-specific code).
- **Против extract port**: один current consumer (Google adapter), premature decomposition (rule 4 MVA), Fake адаптер всё равно нужно писать целиком для AuthProvider — отдельный Fake для identity-links добавит boilerplate.

**Recommendation**: оставить **inline в adapter** для MVP. Когда добавляется PhoneAuthAdapter (future spec) — extract shared adapter-internal helper или explicit port. Это **additive change**, не rewrite. `// TODO(identity-links-port-extraction): if PhoneAuthAdapter reuses lookup logic, extract to IdentityLinksRepository port or adapter-internal helper.`

---

## Verdict

**16/16 ✓.** F-4 — **образцовая** реализация CLAUDE.md rule 1 + 2. Все vendor SDK isolated в adapter'ах; domain не знает о Firebase / Google / Credential Manager; даже `stableId` vendor-agnostic после clarify Q1; provider-swap fitness test делает ACL **provable**, не declared.

Никаких violations. Один open consideration (IdentityLinksRepository extraction) — это **future MVA decision**, не current requirement.

---

## Что это значит простыми словами

Спека правильно отделяет **«что приложение умеет»** от **«как именно это сделано»**:
- В чистой части (`core/domain/auth/`) — только описание поведения, без слов Google или Firebase. Это проверяется автоматически (Detekt-правила + проверка зависимостей).
- В прикладной части (`app/androidMain/auth/`) — реальная работа с Google и Firebase SDK. Это место **изолировано**: если Google завтра «исчезнет» или поменяет API, переписывается **один файл**, всё остальное (F-5 шифрование, S-2 связка, S-8 синхронизация) продолжает работать.
- Главное «открытие» в clarify-проходе: даже идентификатор пользователя (`stableId`) — **наш собственный UUID**, не Firebase UID. Это значит, что при переезде на собственный сервер ничего не меняется — идентификаторы остаются прежними.
- Есть автоматический тест (`ProviderSwapFitnessTest`), который **доказывает** что provider-agnostic дизайн работает, а не только утверждает это.

**Ни одного нарушения** — спека готова к следующему шагу `/speckit.plan`.
