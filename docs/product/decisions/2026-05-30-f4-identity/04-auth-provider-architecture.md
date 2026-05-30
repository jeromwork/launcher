# 04 — AuthProvider port + AuthMethod sealed архитектура

## Решение

Domain (`core/commonMain/auth/`) объявляет один `AuthProvider` port для всех методов авторизации. `AuthMethod` — sealed type с расширяемыми case'ами: Google (реализуется в F-4) / Email / Phone / Apple (future specs) / OwnServer (после cutover).

Google Sign-In — **первый** провайдер, но **один из многих**. Архитектура построена так, что Google **не определяющий** — другие провайдеры добавляются additive change, без переписывания domain или UI.

## Domain (`core/commonMain/auth/`)

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
    object Google : AuthMethod()     // ← F-4 реализуется
    object Email : AuthMethod()      // ← future spec
    object Phone : AuthMethod()      // ← future spec
    object Apple : AuthMethod()      // ← future spec (V-1 iOS)
    // future: object OwnServer — когда сервер заменит Firebase JWT
}

// User — только идентифицированный, anonymous нет
data class User(
    val uid: String,
    val email: String,             // REQUIRED, refuse без email
    val displayName: String,
    val authMethod: AuthMethod
)

sealed class AuthError {
    object NoEmail : AuthError()
    object NetworkError : AuthError()
    object Cancelled : AuthError()
    object ProviderUnavailable : AuthError()
    data class Unknown(val message: String) : AuthError()
}
```

## Adapters (`app/androidMain/auth/`)

```kotlin
// Сейчас: оборачивает Google Sign-In + Firebase Auth
class GoogleSignInFirebaseAuthAdapter : AuthProvider {
    // Flow:
    // 1. Credential Manager → Google ID Token
    // 2. Firebase Auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken))
    // 3. Возвращает User с Firebase UID + email + displayName
    //
    // TODO(server-roadmap): после own-server cutover —
    //   ID Token отдаётся СВОЕМУ серверу (POST /auth/google),
    //   сервер верифицирует подпись Google,
    //   выпускает own JWT с моими claims.
    //   Firebase Auth выпиливается, port остаётся.
}

// Для тестов (CLAUDE.md rule 6 mock-first)
class FakeAuthAdapter : AuthProvider {
    // Pre-seeded users:
    // - test-admin-1@fake.local — обычный admin
    // - test-admin-no-email@fake.local — для refuse-flow теста
    // - test-admin-second-device@fake.local — для multi-device
}

// Future adapters (отдельные spec'и):
// - EmailPasswordAuthAdapter
// - PhoneAuthAdapter
// - AppleSignInAuthAdapter
// - CrossDeviceAuthAdapter (для TV Device Code Flow)
// - OwnServerJwtAuthAdapter (после server cutover)
```

## Runtime selection

```kotlin
// app/androidMain/auth/AuthAdapterSelector.kt
class AuthAdapterSelector(
    private val context: Context
) {
    fun selectForDevice(): AuthProvider {
        // Сейчас: всегда Google + Firebase
        // Будущее: проверка device capabilities
        //   - есть GMS? → Google adapter
        //   - есть Credential Manager? → новый или legacy
        //   - TV? → CrossDeviceAuthAdapter
        //   - non-GMS? → Email или Phone provider
        return GoogleSignInFirebaseAuthAdapter(context)
    }
}
```

## Wire-format

`/users/{uid}` Firestore document — schemaVersion от первого commit (CLAUDE.md rule 5):

```
{
  "schemaVersion": 1,
  "uid": "...",
  "email": "...",
  "displayName": "...",
  "authMethod": "google",
  "createdAt": <ts>,
  "lastSeenAt": <ts>
}
```

## Что обязательно в спеке 015 (F-4)

| Артефакт | Описание |
|---|---|
| AuthProvider port в `core/commonMain/auth/` | Минимальная поверхность, vendor-agnostic |
| AuthMethod sealed type | Cases: Google / Email / Phone / Apple |
| User data class | uid, email (required), displayName, authMethod |
| AuthError sealed type | NoEmail / NetworkError / Cancelled / ProviderUnavailable / Unknown |
| GoogleSignInFirebaseAuthAdapter | Реализация для Google → Firebase, с exit ramp TODO |
| FakeAuthAdapter | Тесты + pre-seeded users |
| AuthAdapterSelector | Runtime выбор adapter'а |
| Wire-format `/users/{uid}` со schemaVersion | Firestore document |
| Tests AuthProvider contract | Гарантируют что swap adapter'а работает |
| Inline TODO в adapter'е | Exit ramp для own-server |
| server-roadmap.md entry | Phased migration plan |

## Что НЕ реализуется в F-4

- ❌ PhoneAuthAdapter, EmailAuthAdapter, AppleAuthAdapter — sealed cases объявлены, adapter'ы — future specs.
- ❌ OwnServerJwtAuthAdapter — будет в server cutover spec'е.
- ❌ Account linking — post-MVP.
- ❌ Generic «identity provider plugin system» (overengineering, CLAUDE.md rule 4 MVA).

## Принципы из CLAUDE.md, которые это закрывает

- **rule 1** (domain isolation): никаких Firebase / Google SDK типов в `core/commonMain/`.
- **rule 2** (ACL): все vendor zависимости в `app/androidMain/auth/`.
- **rule 4** (MVA): port имеет minimum surface, нет «generic plugin system» сейчас.
- **rule 5** (wire-format versioning): `/users/{uid}` schemaVersion от первого commit.
- **rule 6** (mock-first): FakeAuthAdapter обязателен.
- **rule 7** (fitness functions): тесты на port contract.
- **rule 8** (server migration tracking): inline TODO + server-roadmap entry.
