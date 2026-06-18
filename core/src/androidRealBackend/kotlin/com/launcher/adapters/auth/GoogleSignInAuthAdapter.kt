package com.launcher.adapters.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.launcher.api.auth.AuthError
import com.launcher.api.auth.AuthIdentity
import com.launcher.api.auth.AuthProvider
import com.launcher.api.auth.internal.SessionRecord
import com.launcher.api.auth.internal.SessionStore
import com.launcher.api.result.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Production [AuthProvider] на базе Credential Manager + Google Identity
 * + Firebase Auth + Firestore identity-links.
 *
 * Flow [signIn]:
 *  1. Открыть Credential Manager bottom-sheet (Google Sign-In option).
 *  2. Извлечь Google ID Token; распарсить `sub` claim (T750).
 *  3. lookupOrCreateIdentityLink(sub) → stableId (T751, Firestore transaction).
 *  4. signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
 *     → Firebase JWT + refresh token (T752).
 *  5. Сохранить [SessionRecord] в [SessionStore].
 *  6. Эмитить [AuthIdentity] в [currentUser].
 *
 * Token refresh (T753): автоматически перед expiry-5min, через
 * `firebaseAuth.currentUser.getIdToken(true)`. Refresh failed → signOut.
 *
 * NB (spec 017 FR-035): adapter-scoped coroutine [scope] переживает
 * Activity rotation — sign-in continues across recreation.
 *
 * NB (CLAUDE.md §1): этот файл — единственное место в проекте, где видно
 * Firebase Auth / Credential Manager / Google Identity. Detekt rule
 * `Spec017AuthIsolationTest.T791` запретит появление этих типов в domain.
 *
 * TODO(auth-provider-extensions): когда AuthProvider port обзаведётся
 * `linkWithCredential(...)` для cross-provider linking (P-OAuth, P-Phone),
 * этот adapter добавит реализацию рядом с [signIn] (per spec 017 FR-005).
 *
 * TODO(server-roadmap SRV-AUTH-001): credential exchange должен переехать
 * на наш сервер (instead of direct Firebase). См. docs/dev/server-roadmap.md
 * запись SRV-AUTH-001.
 *
 * TODO(country-ban-exit-ramp): если Google заблокирован в регионе,
 * fallback к Phone/Email Magic Link — extends port, не переписывает.
 * См. specs/017-f4-auth-provider/research.md §R3.
 *
 * TODO(authorized-request-signer): future port для подписи RPC будет читать
 * `SessionRecord.extra["firebase_jwt"]` через SessionStore. Consumer'ы
 * НЕ должны видеть JWT напрямую.
 */
internal class GoogleSignInAuthAdapter(
    private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sessionStore: SessionStore,
    private val credentialManager: CredentialManager = CredentialManager.create(context),
    private val serverClientId: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
) : AuthProvider {

    private val _currentUser = MutableStateFlow<AuthIdentity?>(null)
    override val currentUser: Flow<AuthIdentity?> = _currentUser.asStateFlow()

    private val signInMutex = Mutex()
    private var inFlightSignIn: Deferred<Outcome<AuthIdentity, AuthError>>? = null

    init {
        // Async session restore — не блокирует cold start (FR-035).
        scope.launch {
            val restored = sessionStore.current() ?: return@launch
            // Если token истёк или истекает в ближайшие 5 минут — refresh.
            val refreshed = refreshTokenIfNeeded(restored) ?: restored
            // Эмитим identity без email/displayName из stored record
            // (полные detail заполняются при следующем signIn). Главное —
            // передать stableId consumer'ам, чтобы они могли работать.
            _currentUser.value = AuthIdentity(
                stableId = refreshed.stableId,
                displayName = null,
                email = null,
            )
        }
    }

    override suspend fun signIn(): Outcome<AuthIdentity, AuthError> {
        // De-dup: если signIn уже в полёте, ждём его результата.
        // Это обеспечивает rotation-safety (FR-035, US 2 acceptance #1).
        val deferred = signInMutex.withLock {
            inFlightSignIn ?: scope.async { doSignIn() }.also { inFlightSignIn = it }
        }
        return try {
            deferred.await()
        } finally {
            signInMutex.withLock {
                if (inFlightSignIn === deferred) inFlightSignIn = null
            }
        }
    }

    private suspend fun doSignIn(): Outcome<AuthIdentity, AuthError> {
        return try {
            // 1. Credential Manager → Google ID Token credential.
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setServerClientId(serverClientId)
                        .setFilterByAuthorizedAccounts(false)
                        .setAutoSelectEnabled(false)
                        .build(),
                )
                .build()
            val credResponse = credentialManager.getCredential(context, request)
            val googleCred = GoogleIdTokenCredential.createFrom(credResponse.credential.data)
            val idToken = googleCred.idToken
            val email = googleCred.id  // Google ID Credential — `id` = email.
            if (email.isBlank()) {
                return Outcome.Failure(AuthError.NoEmail)
            }

            // 2. Extract Google `sub` claim (T750, см. GoogleIdTokenParser).
            val sub = GoogleIdTokenParser.extractSubClaim(idToken, json)

            // 3. Lookup or create identity-link (T751).
            val stableId = lookupOrCreateIdentityLink(sub)

            // 4. Firebase exchange (T752).
            val firebaseResult = firebaseAuth
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .await()
            val firebaseUser = firebaseResult.user
                ?: return Outcome.Failure(AuthError.Unknown("Firebase signIn succeeded but user is null"))

            val tokenResult = firebaseUser.getIdToken(false).await()
            val jwt = tokenResult.token ?: ""
            val expiresAtEpochMillis = tokenResult.expirationTimestamp

            // 5. Persist session.
            //
            // refreshToken = null: Firebase Android SDK 23+ держит refresh token
            // во внутреннем state FirebaseAuth и автоматически использует его в
            // `getIdToken(true)`. Прямой доступ к refresh token из API нет
            // (намеренное скрытие). Наше поле остаётся в wire-format на случай
            // miграции к другому provider'у, где refresh token экспонируется.
            val record = SessionRecord(
                schemaVersion = 1,
                stableId = stableId,
                expiresAtEpochMillis = expiresAtEpochMillis,
                refreshToken = null,
                extra = mapOf("firebase_jwt" to jwt),
            )
            sessionStore.save(record)

            // 6. Emit identity.
            val identity = AuthIdentity(
                stableId = stableId,
                displayName = googleCred.displayName,
                email = email,
            )
            _currentUser.value = identity
            Outcome.Success(identity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Outcome.Failure(AuthError.Cancelled)
        } catch (e: NoCredentialException) {
            Outcome.Failure(AuthError.ProviderUnavailable)
        } catch (e: GetCredentialException) {
            // Generic Credential Manager failure (configuration, network, etc.).
            Outcome.Failure(AuthError.Unknown(e::class.simpleName ?: "credential"))
        } catch (e: FirebaseNetworkException) {
            Outcome.Failure(AuthError.NetworkError)
        } catch (e: FirebaseTooManyRequestsException) {
            Outcome.Failure(AuthError.NetworkError)  // rate-limited → user retries позже.
        } catch (e: Exception) {
            Outcome.Failure(AuthError.Unknown(e::class.simpleName ?: "auth"))
        }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        sessionStore.clear()
        _currentUser.value = null
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    /**
     * T751. Атомарный lookup-or-create в Firestore `identity-links/google/{sub}`.
     *
     * При первом sign-in нового Google-аккаунта создаёт пару documents:
     *  - `identity-links/google/{sub}` — `{ schemaVersion: 1, stableId, createdAt }`.
     *  - `users/{stableId}` — `{ schemaVersion: 1, stableId, createdAt }`.
     *
     * При повторных sign-in возвращает существующий stableId.
     *
     * Race-safe: Firestore transaction атомарна — если два устройства
     * одновременно first-time-sign-in на тот же аккаунт, один выиграет
     * create, второй прочитает результат.
     *
     * Per spec 017 FR-016a, contract `identity-link-v1.md`.
     */
    internal suspend fun lookupOrCreateIdentityLink(sub: String): String {
        val identityLinkRef = firestore.document("identity-links/google/$sub")
        return firestore.runTransaction<String> { txn ->
            val snapshot = txn.get(identityLinkRef)
            if (snapshot.exists()) {
                snapshot.getString("stableId")
                    ?: error("identity-links/google/$sub exists but missing stableId field")
            } else {
                val newUuid = UUID.randomUUID().toString()
                txn.set(
                    identityLinkRef,
                    mapOf(
                        "schemaVersion" to 1L,
                        "stableId" to newUuid,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                txn.set(
                    firestore.document("users/$newUuid"),
                    mapOf(
                        "schemaVersion" to 1L,
                        "stableId" to newUuid,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                newUuid
            }
        }.await()
    }

    /**
     * T753. Refresh Firebase JWT если истёк или истекает в ближайшие 5 минут.
     * Возвращает обновлённый [SessionRecord] на success, null на refresh failure
     * (в этом случае trigger'ует [signOut] — consumer увидит null currentUser).
     */
    private suspend fun refreshTokenIfNeeded(record: SessionRecord): SessionRecord? {
        val expiresAt = record.expiresAtEpochMillis ?: return null
        val nowPlusBuffer = System.currentTimeMillis() + REFRESH_BUFFER_MILLIS
        if (expiresAt > nowPlusBuffer) return null  // ещё свежий.

        return try {
            val firebaseUser = firebaseAuth.currentUser ?: run {
                // SessionStore говорит «есть session», но FirebaseAuth — нет.
                // Это рассинхрон (например, после device-restore). Чистим.
                signOut()
                return null
            }
            val tokenResult = firebaseUser.getIdToken(true).await()
            val newJwt = tokenResult.token ?: ""
            val refreshed = record.copy(
                expiresAtEpochMillis = tokenResult.expirationTimestamp,
                extra = record.extra + ("firebase_jwt" to newJwt),
            )
            sessionStore.save(refreshed)
            refreshed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Refresh failed → invalidate session (US 4 #3).
            signOut()
            null
        }
    }

    companion object {
        private const val REFRESH_BUFFER_MILLIS = 5 * 60 * 1000L
    }
}
