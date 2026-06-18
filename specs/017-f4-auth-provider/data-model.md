# Data Model: F-4 — AuthProvider port + Google Sign-In adapter

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-06-18

Domain types F-4. Все типы — pure Kotlin (no Android, no vendor SDK). Подчинены CLAUDE.md rule 1 (domain isolation).

---

## Public domain types (`core/domain/auth/`)

Эти типы видят consumer'ы (F-5, S-2, S-8, S-9, P-6).

### `AuthProvider` (port)

```kotlin
package family.launcher.domain.auth

import kotlinx.coroutines.flow.Flow

interface AuthProvider {

    /**
     * Reactive identity state.
     * - `null` означает «не залогинен» (включая initial cold-start state до session restore).
     * - Эмитит обновления при sign-in success, sign-out, refresh failure.
     * - Single source of truth для consumer'ов (F-5, S-2, S-8, S-9, P-6).
     * - НЕ блокирует cold-start (FR-035): эмитит null первым, потом восстановленное identity асинхронно.
     */
    val currentUser: Flow<AuthIdentity?>

    /**
     * Initiate sign-in flow.
     * - Adapter-specific: Google adapter открывает Credential Manager bottom-sheet.
     * - Adapter-scoped coroutine (research.md §R2): survives UI lifecycle changes.
     * - Result delivered AND через return value AND через `currentUser` flow emission.
     */
    suspend fun signIn(): Outcome<AuthIdentity, AuthError>

    /**
     * Sign out: stop syncing, clear SessionStore, emit null в currentUser.
     * - НЕ destructive: локальный кеш конфига, плитки, контакты, темы остаются.
     * - Idempotent: повторный вызов = no-op.
     * - Per clarification Q3.
     */
    suspend fun signOut()
}
```

**Invariants**:
- `currentUser` MUST эмитить хотя бы один value в течение 100ms после Application start (initial `null` — fine).
- `signIn()` параллельные вызовы — adapter MUST deduplicate (research.md §R2 / spec Edge case).
- `signOut()` — fire-and-forget semantics; await completion гарантирует только что SessionStore cleared.

### `AuthIdentity`

```kotlin
package family.launcher.domain.auth

data class AuthIdentity(
    /**
     * Stable identifier — наш собственный UUID v4.
     *
     * НЕ Firebase UID, НЕ Google `sub` claim, НЕ OAuth subject.
     * Lookup `(providerKind, providerAccountId) → stableId` живёт в Firestore коллекции
     * `/identity-links/...` (FR-016a). Cross-device stability через эту мапу.
     *
     * Per clarification Q1 + R4 server-roadmap exit ramp.
     */
    val stableId: String,

    /**
     * User-visible name (Google account name, обычно «Имя Фамилия»).
     * Nullable: future providers (phone) могут не возвращать.
     * В MVP де-facto заполнен — Google всегда возвращает displayName при `profile` scope.
     */
    val displayName: String?,

    /**
     * Email address (Google account email).
     * Nullable: future providers (phone) могут не возвращать.
     * MVP policy (adapter-level, FR-016): Google adapter refuse sign-in если email == null → AuthError.NoEmail.
     */
    val email: String?,
)
```

**NB**: `providerKind` field **удалён** per clarification Q4. Consumer'ы не должны знать через какой provider authenticated. Adapter знает у себя.

### `AuthError`

```kotlin
package family.launcher.domain.auth

sealed class AuthError {
    /** Network unavailable / timeout. */
    object NetworkError : AuthError()

    /** User cancelled Credential Manager bottom-sheet. Legitimate choice, no error toast. */
    object Cancelled : AuthError()

    /** Google returned credential без email (corporate-restricted accounts). */
    object NoEmail : AuthError()

    /** No supported auth provider available (e.g., Huawei без GMS). */
    object ProviderUnavailable : AuthError()

    /** Other (Firebase API error, identity-links Firestore error, parsing error). */
    data class Unknown(val message: String) : AuthError()
}
```

**Extension policy**: добавление новых sealed cases — additive change (no breaking). Removal — major version bump.

### `User`

```kotlin
package family.launcher.domain.auth

data class User(
    /** = AuthIdentity.stableId (наш UUID). */
    val id: String,

    /**
     * Forward declaration для F-5 (E2E encryption keys).
     * В F-4 = `null` всегда. Populated F-5 spec.
     */
    val identityKeys: IdentityKeys?,

    val email: String?,
    val displayName: String?,

    /**
     * Subscription status — stub field в F-4 (MVP всегда Unknown).
     * Server-validated в post-MVP S-10 (Subscription Server Timer).
     * Никогда client-computed как Active / Expired (per FR-031, tamper-resistance).
     */
    val subscriptionState: SubscriptionState,
)

/**
 * Forward declaration. Real type — F-5 ConfigCipher spec.
 * F-4 уrn только nullable placeholder.
 */
interface IdentityKeys
```

**NB**: `providerKind` field удалён per Q4.

### `SubscriptionState`

```kotlin
package family.launcher.domain.auth

sealed class SubscriptionState {
    /** MVP F-4 default. Server-validation deferred к S-10. */
    object Unknown : SubscriptionState()

    /** Залогинен, но subscription expired → app в local-only режиме. Used by S-10 / decision 03 §«Cloud expired downgrade». */
    object LocalOnly : SubscriptionState()

    /** First month after first sign-in (free trial, server-tracked). */
    object Trial : SubscriptionState()

    /** Active paid subscription (server-validated JWT). */
    object Active : SubscriptionState()

    /** Subscription cancelled или истекла. */
    object Expired : SubscriptionState()
}
```

**Constraint** (FR-031, SC-014): в F-4 codebase **никогда** не присваивается `Active` / `Expired` client-side. Property test проверяет это.

### `Outcome<T, E>` (если ещё нет в `core/domain/common/`)

```kotlin
package family.launcher.domain.common  // или auth, если общий type уже не существует в проекте

sealed class Outcome<out T, out E> {
    data class Success<T>(val value: T) : Outcome<T, Nothing>()
    data class Failure<E>(val error: E) : Outcome<Nothing, E>()
}
```

**Note**: если Outcome уже существует в проекте — reuse. F-4 не вводит дубликат.

---

## Internal types (`core/domain/auth/internal/`)

Эти типы **не видят** consumer'ы. Imports forbidden Detekt rule.

### `SessionStore` (internal port)

```kotlin
package family.launcher.domain.auth.internal

import family.launcher.domain.auth.AuthIdentity
import kotlinx.coroutines.flow.Flow

internal interface SessionStore {
    suspend fun save(session: SessionRecord)
    suspend fun current(): SessionRecord?
    suspend fun clear()
    val sessionChanges: Flow<SessionRecord?>
}
```

**Why internal**: per clarification Q2 — consumer'ы работают **только** через `AuthProvider.currentUser`. `SessionStore` — contract между `AuthProvider` adapter и persistent storage (`EncryptedLocalSessionStore`).

### `SessionRecord` (internal value)

```kotlin
package family.launcher.domain.auth.internal

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class SessionRecord(
    /** Wire-format version. Always 1 в MVP. Bump при breaking change. */
    val schemaVersion: Int = 1,

    /** = AuthIdentity.stableId (наш UUID). */
    val stableId: String,

    /**
     * Token expiry timestamp. Null означает «unknown / never» (для adapter'ов без token concept).
     * For Google: Firebase JWT expiry (~1 hour after issue).
     */
    val expiresAt: Instant?,

    /**
     * Opaque refresh token. Adapter-internal use only.
     * Encrypted at rest через EncryptedSharedPreferences (FR-020).
     * Never exposed to consumer'ам.
     */
    val refreshToken: String?,

    /**
     * Adapter-specific blob.
     * For GoogleSignInAuthAdapter: `extra["firebase_jwt"]` = current Firebase JWT.
     * Domain не интерпретирует — adapter ownership.
     */
    val extra: Map<String, String>,
)
```

**Wire format**: serialized as JSON via `kotlinx.serialization.json`. Stored в `EncryptedSharedPreferences` файле `auth_session_v1.preferences`. Full contract — [contracts/session-record-v1.md](contracts/session-record-v1.md).

**Backward compat**: when introducing schemaVersion 2 — write migrator before breaking change ships (CLAUDE.md rule 5). Read test для v1 fixture обязателен в каждой future spec touching SessionRecord.

---

## Android adapter types (`app/androidMain/auth/`)

Эти types — **implementation details** Google adapter'а. Consumer'ы НЕ должны их знать.

### `EncryptedLocalSessionStore` (implementation)

```kotlin
package family.launcher.app.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import family.launcher.domain.auth.internal.SessionStore
import family.launcher.domain.auth.internal.SessionRecord
// ...

internal class EncryptedLocalSessionStore(
    context: Context,
    private val json: Json,
) : SessionStore {
    private val prefs by lazy {
        // EncryptedSharedPreferences с TEE-backed AES-256-GCM master key
        // FILE: "auth_session_v1.preferences" (per wire format identifier; namespaced)
        // KEY: "session" (single entry — current session blob serialized as JSON string)
    }

    override suspend fun save(session: SessionRecord) { /* withContext(IO) { ... } */ }
    override suspend fun current(): SessionRecord? { /* withContext(IO) { read + parse + handle corruption } */ }
    override suspend fun clear() { /* withContext(IO) { ... } */ }
    override val sessionChanges: Flow<SessionRecord?> = /* MutableStateFlow updated on save/clear */
}
```

**Key namespace** (wire-format CHK013 + EncryptedSharedPreferences best practice): file name `auth_session_v1.preferences`. Single key `session` для current SessionRecord blob.

**TODO (inline)**:
```kotlin
// TODO(F-CRYPTO migration): currently EncryptedSharedPreferences (L0 tamper-resistance level).
// When F-5 ships, migrate к SecureKeyStore из F-CRYPTO (wrap pattern сильнее).
// Additive change: new EncryptedLocalSessionStore implementation reads from BOTH locations during transition.
// Per FR-020 + decision 03 §L1 escalation.
```

### `GoogleSignInAuthAdapter` (implementation)

```kotlin
package family.launcher.app.auth

import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import family.launcher.domain.auth.AuthProvider
import family.launcher.domain.auth.AuthIdentity
import family.launcher.domain.auth.AuthError
import family.launcher.domain.auth.Outcome
import family.launcher.domain.auth.internal.SessionStore
import family.launcher.domain.auth.internal.SessionRecord
// ...

internal class GoogleSignInAuthAdapter(
    private val credentialManager: CredentialManager,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sessionStore: SessionStore,
    private val json: Json,
) : AuthProvider {

    // Adapter-scoped (research.md §R2)
    private val adapterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var inFlightSignIn: Deferred<Outcome<AuthIdentity, AuthError>>? = null

    private val _currentUser = MutableStateFlow<AuthIdentity?>(null)
    override val currentUser: Flow<AuthIdentity?> = _currentUser.asStateFlow()

    init {
        // Cold-start session restore (asynchronous, не блокирует Application.onCreate per FR-035)
        adapterScope.launch {
            val restored = sessionStore.current() ?: return@launch
            if (restored.expiresAt != null && restored.expiresAt < Instant.now()) {
                refreshToken(restored)  // attempt refresh; emit identity или null
            } else {
                _currentUser.value = AuthIdentity(restored.stableId, /* email, name из extra? нет — повторно из Firebase или cache */ ...)
            }
        }
    }

    override suspend fun signIn(): Outcome<AuthIdentity, AuthError> {
        // Dedup (spec Edge case + state-management CHK010)
        inFlightSignIn?.let { return awaitOrFail(it) }

        val deferred = adapterScope.async { performSignIn() }
        inFlightSignIn = deferred
        return try { deferred.await() } finally { inFlightSignIn = null }
    }

    private suspend fun performSignIn(): Outcome<AuthIdentity, AuthError> {
        // Step 1: Credential Manager → Google ID Token
        // Step 1.5: extract `sub` claim from ID Token (research.md §R1, private fn `extractSubClaim`)
        // Step 1.6: identity-links lookup-or-create через Firestore transaction (research.md §R4 + §R9)
        // Step 2: Firebase exchange (signInWithCredential) → Firebase JWT, refreshToken
        // Step 3: Save SessionRecord в SessionStore
        // Step 4: Emit AuthIdentity через _currentUser
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        sessionStore.clear()
        _currentUser.value = null
        AuthLog.signOut()
    }

    // ... private helpers (refreshToken, extractSubClaim, lookupOrCreateIdentityLink, validateEmail)
}
```

**TODO (inline)** в Step 2:
```kotlin
// TODO(server-roadmap SRV-AUTH-001): After own-server cutover — replace Firebase Auth
// exchange step with POST ID Token → /auth/google on our server, server verifies Google
// signature, issues own JWT. Port (AuthProvider) unchanged.
// Per decision 2026-05-30-f4-identity/05-own-server-migration-strategy.md.

// TODO(auth-provider-extensions): Phone / Email-Password / Apple / SSO adapters
// add through this port without changing the port shape.

// TODO(country-ban-exit-ramp): when adding NonGoogleAuthAdapter for jurisdictions
// where Google is restricted (China, Iran) — new adapter performs the same
// identity-links lookup by its own providerKind (e.g. /identity-links/phone/+86...).
// stableId UUID stays. No data migration. Per discussion 2026-06-18.

// TODO(authorized-request-signer): future port for RPC signing (S-8 sync) —
// adapter will read SessionRecord.extra["firebase_jwt"], consumer'ы не видят token.
```

### `AuthAdapterSelector`

```kotlin
package family.launcher.app.auth

internal class AuthAdapterSelector(
    private val googleApiAvailability: GoogleApiAvailability,
    private val googleAdapter: GoogleSignInAuthAdapter,
    /* future: phoneAdapter, emailAdapter ... */
) {
    fun pickAdapter(): AuthProvider = when {
        googleApiAvailability.isAvailable() -> googleAdapter
        // future:
        // deviceCapabilities.isPhoneRegisteredCountry() -> phoneAdapter
        // deviceCapabilities.isHuawei -> hmsAdapter
        else -> NoSupportedAuthProvider  // тонкая proxy которая всегда возвращает ProviderUnavailable
    }
}
```

### `SignInTrigger` (Compose composable)

```kotlin
package family.launcher.app.auth.ui

@Composable
fun SignInTrigger(
    modifier: Modifier = Modifier,
    onSignedIn: (AuthIdentity) -> Unit = {},
    onSignedOut: () -> Unit = {},
) {
    val authProvider: AuthProvider = LocalAuthProvider.current
    val identity by authProvider.currentUser.collectAsState(initial = null)

    // SigningIn flag survives через rememberSaveable (research.md §R7)
    var isSigningIn by rememberSaveable { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<AuthError?>(null) }
    val coroutineScope = rememberCoroutineScope()  // caller scope; adapter has own internal scope

    val state: SignInTriggerState = when {
        isSigningIn -> SignInTriggerState.SigningIn
        identity != null -> SignInTriggerState.SignedIn(identity!!)
        lastError != null -> SignInTriggerState.Error(lastError!!)
        else -> SignInTriggerState.NotSignedIn
    }

    when (state) {
        SignInTriggerState.NotSignedIn -> NotSignedInUi(onLogin = { /* launch signIn */ })
        SignInTriggerState.SigningIn -> NotSignedInUi(loading = true)
        is SignInTriggerState.SignedIn -> SignedInUi(state.identity, onLogout = { /* launch signOut */ })
        SignInTriggerState.SigningOut -> SignedInUi(loading = true)
        is SignInTriggerState.Error -> NotSignedInUi(error = state.reason, onLogin = { /* retry */ })
    }
}

internal sealed class SignInTriggerState {
    object NotSignedIn : SignInTriggerState()
    object SigningIn : SignInTriggerState()
    data class SignedIn(val identity: AuthIdentity) : SignInTriggerState()
    object SigningOut : SignInTriggerState()
    data class Error(val reason: AuthError) : SignInTriggerState()
}
```

UI structure (research.md §R7):
- `NotSignedInUi`: кнопка «Войти в аккаунт» + поясняющий текст «Чтобы настройки сохранялись и были доступны на других устройствах» + (если error) — inline error message + warning icon.
- `SignedInUi`: «Вошли как `<email>`» + кнопка «Выйти».

Senior-safe baseline (elderly-friendly checklist + plan.md):
- Text body ≥ 18sp.
- Button labels ≥ 16sp.
- Tap targets ≥ 56dp.
- Spacing ≥ 16dp.
- Contrast ≥ 4.5:1.
- Test: `fontScale=2.0` все элементы remain readable, не truncated.

---

## Firestore data types

### Document: `/identity-links/google/{sub}`

```
{
  "schemaVersion": 1,
  "stableId": "550e8400-e29b-41d4-a716-446655440000",
  "createdAt": Timestamp(2026-06-18T14:23:00Z)
}
```

Where `{sub}` = Google account `sub` claim (typically 21-digit numeric string).

Full contract — [contracts/identity-link-v1.md](contracts/identity-link-v1.md).

### Document: `/users/{stableId}`

```
{
  "schemaVersion": 1,
  "stableId": "550e8400-e29b-41d4-a716-446655440000",
  "createdAt": Timestamp(2026-06-18T14:23:00Z)
  // Additional fields added by F-5 (identityKeys), S-8 (configs), etc.
  // F-4 creates only above three fields. Empty user doc.
}
```

Where `{stableId}` = наш UUID v4 generated при первом sign-in.

---

## Type relationship diagram (textual)

```
Consumer (F-5 / S-2 / S-8 / S-9 / P-6)
        |
        | subscribe to
        v
AuthProvider (port) -----------> AuthIdentity { stableId, email?, displayName? }
        |                              |
        | implements                   | consumed
        v                              |
GoogleSignInAuthAdapter                | derived
        |                              |
        | persists                     |
        v                              |
SessionStore (internal port)           |
        |                              |
        | implements                   |
        v                              |
EncryptedLocalSessionStore             |
        |                              |
        | stores                       |
        v                              |
SessionRecord { schemaVersion, stableId, expiresAt?, refreshToken?, extra }
        |
        | wire format
        v
EncryptedSharedPreferences (Android Keystore TEE)


GoogleSignInAuthAdapter ---- lookup/create ----> Firestore /identity-links/google/{sub}
                                                     |
                                                     | maps to UUID
                                                     v
                                                  /users/{stableId}
```

---

## Migration / evolution

### Adding new providers (future spec'и)

Example: PhoneAuthAdapter added как additive change:

1. Create `app/androidMain/auth/PhoneAuthAdapter.kt` (new adapter implementing `AuthProvider`).
2. Generate `extractAccountIdFromSmsCode(...)` helper (parallel to Google's `extractSubClaim`).
3. Lookup в `/identity-links/phone/{phoneNumber}` (same Firestore pattern).
4. Update `AuthAdapterSelector.pickAdapter()` to include phone branch.
5. Update `firestore.rules` to add phone subpath.

**No changes** к `AuthProvider`, `AuthIdentity`, `SessionStore`, `SessionRecord`, consumer'ам.

### Migrating wire format (SessionRecord schemaVersion bump)

1. Bump `schemaVersion = 2`, add/rename/remove fields.
2. Write `SessionRecordMigrator.migrate(v1: JsonObject): SessionRecord` function.
3. Update `EncryptedLocalSessionStore.current()` to detect schemaVersion, dispatch к migrator if v1.
4. Backward-compat read test обязателен.

### Cutover Firestore identity-links → own-server (R4)

1. Own-server stands up identity-links endpoint.
2. Phase 2 dual-write.
3. Phase 3 switch read source: `lookupOrCreateIdentityLink()` body changes (no signature change).
4. Phase 4 stop Firestore writes.

**No changes** к domain types или Firestore Security Rules (for client side).

---

## TL;DR для не-разработчика

В F-4 живут **четыре типа данных**:

1. **AuthIdentity** — «карточка пользователя»: UUID, email, имя. Это то, что видят все остальные части приложения.
2. **AuthError** — список возможных ошибок при входе (нет сети, пользователь отменил, нет email, etc.).
3. **User** — расширенная карточка с подпиской и (в будущем) ключами шифрования. Сейчас почти пустая.
4. **SessionRecord** — **внутренние данные** (refreshToken, истечение), которые consumer'ы НЕ видят. Хранится зашифрованным в файле.

**На сервере Firestore** живут два типа документов:
- `/identity-links/google/{sub}` — маппинг Google аккаунт → наш UUID.
- `/users/{UUID}` — карточка пользователя на сервере (сейчас пустая, потом наполнится конфигом и ключами).

**Кнопка входа** (`SignInTrigger`) имеет 5 чётких состояний: не вошли / загрузка входа / вошли / загрузка выхода / ошибка.

**Если завтра добавим вход по телефону** — нужно только **новый адаптер** + новая подпапка в identity-links. Все остальные типы данных остаются прежними.

**Если завтра переедем на свой сервер** — поменяется только содержимое одной функции lookup. Все типы данных остаются прежними.
