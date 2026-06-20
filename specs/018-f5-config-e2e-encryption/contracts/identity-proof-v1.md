# Contract: `IdentityProof` port v1

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **FRs**: FR-006, FR-007, FR-028

Port для подтверждения identity владельца устройства. F-5 использует identity для:
- partitioning KeyRegistry per UID (FR-031),
- определения, какой Firestore document читать (`users/{uid}/recovery-key`).

---

## Kotlin declaration (commonMain)

```kotlin
package com.launcher.api.keys.api

import com.launcher.api.auth.AuthIdentity
import kotlinx.coroutines.flow.Flow

public interface IdentityProof {

    /**
     * Текущая authenticated identity. Null если пользователь не подписан.
     * Suspends — может потребовать чтения persistent storage.
     */
    public suspend fun currentIdentity(): AuthIdentity?

    /**
     * Stream identity-изменений. Эмиссия при sign-in / sign-out.
     */
    public val identityFlow: Flow<AuthIdentity?>

    /**
     * UI-triggered sign-in flow. Возвращает identity при успехе.
     * На non-GMS устройствах (Huawei) → IdentityError.NoSupportedProvider (FR-028).
     */
    public suspend fun requestSignIn(): Outcome<AuthIdentity, IdentityError>

    /**
     * Sign-out. НЕ wipe'ит RootKey / KeyRegistry — Keystore сохраняется (clarify Q «Sign-out поведение»).
     */
    public suspend fun signOut(): Outcome<Unit, IdentityError>
}

public sealed class IdentityError {
    public data object NoSupportedProvider : IdentityError()      // Huawei / non-GMS
    public data object Cancelled : IdentityError()                // user dismissed sign-in UI
    public data class Failure(val cause: Throwable) : IdentityError()
}
```

---

## Relationship с F-4 (spec 017)

F-5 **не дублирует** Google Sign-In инфраструктуру. MVP adapter `GoogleSignInIdentityProof` — **тонкий wrapper** над существующим `AuthProvider` из F-4:

```kotlin
// app/src/main/kotlin/com/launcher/data/identity/GoogleSignInIdentityProof.kt

internal class GoogleSignInIdentityProof(
    private val authProvider: AuthProvider, // F-4 port
) : IdentityProof {

    override suspend fun currentIdentity(): AuthIdentity? =
        authProvider.currentIdentity()

    override val identityFlow: Flow<AuthIdentity?> =
        authProvider.identityFlow

    override suspend fun requestSignIn(): Outcome<AuthIdentity, IdentityError> =
        authProvider.signIn().mapError { it.toIdentityError() }

    override suspend fun signOut(): Outcome<Unit, IdentityError> =
        authProvider.signOut().mapError { it.toIdentityError() }
}

// AuthError → IdentityError mapping:
private fun AuthError.toIdentityError(): IdentityError = when (this) {
    is AuthError.NoSupportedProvider -> IdentityError.NoSupportedProvider
    is AuthError.Cancelled -> IdentityError.Cancelled
    is AuthError.Failure -> IdentityError.Failure(cause)
}
```

**Почему отдельный port в F-5, а не прямое использование `AuthProvider`?**

Per FR-006, F-5 имеет concept «identity proof» отдельно от «auth provider»: future spec'и (scenario 6 в spec.md) могут добавить `SmsIdentityProof`, `MultiFactorIdentityProof`, которые **не являются** Google Sign-In и не подходят под `AuthProvider` интерфейс F-4. F-5 же зависит от identity-понятия, не от конкретного provider'а.

Это **port-based architecture** (CLAUDE.md rule 1) — F-5 domain не знает о Google Sign-In, знает только о `IdentityProof`. MVP мостит к F-4 через тонкий adapter.

---

## Non-GMS adapter — `NoOpIdentityProof`

```kotlin
internal class NoOpIdentityProof : IdentityProof {
    override suspend fun currentIdentity(): AuthIdentity? = null
    override val identityFlow: Flow<AuthIdentity?> = flowOf(null)
    override suspend fun requestSignIn(): Outcome<AuthIdentity, IdentityError> =
        Outcome.Failure(IdentityError.NoSupportedProvider)
    override suspend fun signOut(): Outcome<Unit, IdentityError> =
        Outcome.Success(Unit) // no-op
}
```

DI wiring (per F-4 `AuthAdapterSelector` pattern):
```kotlin
// KeysModule.kt
single<IdentityProof> {
    if (authAdapterSelector.hasGmsSupport()) {
        GoogleSignInIdentityProof(authProvider = get())
    } else {
        NoOpIdentityProof()
    }
}
```

---

## Tests required

- Fake: `FakeIdentityProof(initialIdentity: AuthIdentity?)` — для consumer tests `KeyRegistry` / `RootKeyManager`.
- Contract test: `GoogleSignInIdentityProofTest` мокает `AuthProvider`, проверяет mapping errors.
- Smoke на эмуляторе: real `GoogleSignInIdentityProof` с тестовым Google account → `currentIdentity()` non-null after sign-in (через skill `android-emulator`).

---

## Краткое резюме

Контракт port'а для подтверждения identity. Тонкий wrapper над F-4 `AuthProvider` — отдельный port нужен, чтобы будущие способы аутентификации (SMS, push-confirm, multi-factor — сценарий 6 spec'и) подключались без переделок F-5. На Huawei/non-GMS — заглушка, app работает локально. F-5 не дублирует Google Sign-In инфраструктуру.
