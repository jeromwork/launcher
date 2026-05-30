# 03. AuthProvider port + AuthMethod sealed type

**Дата фиксации**: 2026-05-30

---

## Суть решения

В `core/commonMain/auth/` объявляется **один port** `AuthProvider`, через который domain работает с identity. **Конкретный метод авторизации** (Google / Email / Phone / Apple / own-server) выбирается через **расширяемый `AuthMethod` sealed type**.

**MVP F-4 реализует только Google Sign-In.** Остальные методы — sealed cases объявлены, adapter'ы — future specs.

---

## Архитектура

### Domain (`core/commonMain/auth/`)

```kotlin
// Один port для всех методов
interface AuthProvider {
    val currentUser: Flow<User?>
    suspend fun signIn(method: AuthMethod): Outcome<User, AuthError>
    suspend fun signOut()
    suspend fun deleteAccount(): Outcome<Unit, AuthError>
}

// Расширяемый набор методов
sealed class AuthMethod {
    object Google : AuthMethod()         // ← F-4 реализуется
    object Email : AuthMethod()          // ← future spec
    object Phone : AuthMethod()          // ← future spec
    object Apple : AuthMethod()          // ← future spec (V-1 iOS)
    // future: object OwnServer : AuthMethod() — когда сервер заменит Firebase JWT
}

// User — только идентифицированный, AnonymousUser НЕТ
data class User(
    val uid: String,
    val email: String,           // REQUIRED
    val displayName: String,
    val authMethod: AuthMethod   // через какой провайдер залогинен
)

sealed class AuthError {
    object NoEmail : AuthError()
    object NetworkError : AuthError()
    object Cancelled : AuthError()
    object ProviderUnavailable : AuthError()
    data class Unknown(val message: String) : AuthError()
}
```

### Adapters (`app/androidMain/auth/`)

```kotlin
// F-4: оборачивает Google Sign-In via Credential Manager + Firebase Auth
class GoogleSignInFirebaseAuthAdapter : AuthProvider {
    // Step 1: Credential Manager → Google ID Token.
    // Step 2: Firebase Auth — обменивает ID Token на Firebase JWT.
    // Step 3: domain получает User(uid, email, displayName).
    //
    // TODO(server-roadmap): После own-server cutover — Step 2 заменить.
    //   Вместо Firebase Auth: POST ID Token на наш /auth/google,
    //   сервер верифицирует подпись Google, выпускает own JWT.
    //   Port остаётся, adapter меняется внутри.
}

// Для тестов (CLAUDE.md rule 6 mock-first)
class FakeAuthAdapter : AuthProvider {
    // pre-seeded users без Google
}

// Future: EmailAuthAdapter, PhoneAuthAdapter, AppleAuthAdapter
// Future: GoogleSignInOwnServerAuthAdapter (после server cutover)
```

### Runtime selector

```kotlin
// app/androidMain/auth/AuthAdapterSelector.kt
class AuthAdapterSelector(
    private val deviceCapabilities: DeviceCapabilities
) {
    fun pickAdapter(): AuthProvider = when {
        deviceCapabilities.hasGooglePlayServices -> googleSignInFirebaseAdapter
        // Future:
        // deviceCapabilities.isHuawei -> hmsAuthAdapter
        // deviceCapabilities.isTv -> crossDeviceAuthAdapter
        else -> error("No supported auth provider")
    }
}
```

---

## Ключевые свойства port'а

1. **Vendor-agnostic signatures.** `signIn(method: AuthMethod)`, не `signInWithGoogle()`. Имя провайдера — параметр, не метод.

2. **`User` без null-полей.** Email REQUIRED. Если Google не вернул email — refuse login с UI ошибкой «используйте личный Google-аккаунт».

3. **Sealed type, не enum, для AuthMethod.** Sealed позволяет добавлять new cases без breaking changes (Kotlin exhaustive matching покажет где нужно дописать).

4. **`currentUser: Flow<User?>`** — стандартный паттерн для observing identity state. Null = не залогинен.

5. **Outcome type для возвратов.** `Outcome<User, AuthError>` — типизированные ошибки, не exception.

6. **Один port для всех методов**, не «GoogleAuthProvider + EmailAuthProvider». Иначе UI должен знать про N портов = leakage.

---

## Что в F-4 реализуется

| Компонент | Файл | Реализация |
|---|---|---|
| `AuthProvider` port | `core/commonMain/auth/AuthProvider.kt` | Только интерфейс |
| `User` data class | `core/commonMain/auth/User.kt` | Domain type, без vendor зависимостей |
| `AuthMethod` sealed | `core/commonMain/auth/AuthMethod.kt` | Все cases объявлены: Google/Email/Phone/Apple |
| `AuthError` sealed | `core/commonMain/auth/AuthError.kt` | Все cases объявлены |
| `GoogleSignInFirebaseAuthAdapter` | `app/androidMain/auth/` | Реализация для Google |
| `FakeAuthAdapter` | `core/commonTest/auth/` | Для тестов |
| `AuthAdapterSelector` | `app/androidMain/auth/` | Runtime device-capability dispatch |

## Что в F-4 НЕ реализуется

- `EmailAuthAdapter`, `PhoneAuthAdapter`, `AppleAuthAdapter` — future specs.
- `GoogleSignInOwnServerAuthAdapter` — после own-server cutover.
- Account linking (owner добавляет второй метод входа) — post-MVP.
- HMS / Huawei adapter — out of scope MVP.
- Cross-device auth (TV) — post-MVP, V-4.

---

## Принципы (per CLAUDE.md rules)

- **Rule 1** (domain isolation): `AuthProvider` в `core/commonMain/`, никакого Firebase / Google SDK там.
- **Rule 2** (ACL): Firebase / Google SDK живут **только** в `GoogleSignInFirebaseAuthAdapter`. Domain `User` не содержит Firebase / Google типов.
- **Rule 4** (MVA): `AuthMethod` sealed type с 4 case'ами с первого дня **легитимен** — это honest extensibility, не abstraction for future. Реализация только Google в MVP.
- **Rule 5** (wire-format): `/users/{uid}` document в Firestore имеет `schemaVersion: 1` от первого commit.
- **Rule 6** (mock-first): `FakeAuthAdapter` обязателен, тесты domain logic — через него.
- **Rule 8** (server migration tracking): inline TODO в `GoogleSignInFirebaseAuthAdapter` про переход на own JWT (см. файл 05).

---

## Связанные документы

- [02-identity-anonymous-removal.md](02-identity-anonymous-removal.md) — почему AnonymousUser удалён.
- [04-google-as-one-of-many.md](04-google-as-one-of-many.md) — Google = частный случай.
- [05-own-server-migration-strategy.md](05-own-server-migration-strategy.md) — port остаётся при cutover.
- [08-f4-spec-scope.md](08-f4-spec-scope.md) — F-4 spec scope.
