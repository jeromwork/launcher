package com.launcher.adapters.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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
            //
            // Используем GetSignInWithGoogleOption (не GetGoogleIdOption) —
            // первая всегда показывает Google Sign-In bottom-sheet, даже если
            // в системе нет authorized accounts; GetGoogleIdOption с
            // setFilterByAuthorizedAccounts(false) бросает NoCredentialException
            // на новых устройствах. Per Google docs:
            // https://developer.android.com/identity/sign-in/credential-manager-siwg
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(serverClientId).build(),
                )
                .build()
            Log.i(TAG, "sign_in.attempt serverClientId.last8=${serverClientId.takeLast(8)}")
            // Credential Manager требует Activity-based context (не application).
            // ActivityHolder заполняется ActivityHolderLifecycleObserver на onResume.
            // Если Activity нет (background) — это user error, не падаем.
            val activityContext = ActivityHolder.current() ?: run {
                Log.w(TAG, "sign_in.no_activity — adapter called without foreground Activity")
                return Outcome.Failure(AuthError.Unknown("no_activity"))
            }
            val credResponse = credentialManager.getCredential(activityContext, request)
            val googleCred = GoogleIdTokenCredential.createFrom(credResponse.credential.data)
            val idToken = googleCred.idToken
            val email = googleCred.id  // Google ID Credential — `id` = email.
            if (email.isBlank()) {
                return Outcome.Failure(AuthError.NoEmail)
            }

            // 2. Firebase exchange (T752) — выполняется ПЕРВЫМ, чтобы
            // получить Firebase Auth UID для identity-link rules. Firebase UID
            // стабилен per (google account, firebase project) пара, поэтому
            // надёжнее использовать его как identifier чем sub claim
            // (sub claim — Google-specific, а Firebase UID работает для любого
            // provider'а — упрощает future migration к Apple/Phone auth).
            val firebaseResult = firebaseAuth
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .await()
            val firebaseUser = firebaseResult.user
                ?: return Outcome.Failure(AuthError.Unknown("Firebase signIn succeeded but user is null"))
            val firebaseUid = firebaseUser.uid

            // 3. Lookup or create identity-link (T751). Document ID = Firebase UID
            // (не Google sub claim) — это согласуется с request.auth.uid в Firestore
            // Rules без custom claims (см. SRV-AUTH-IDENTITY-002 — больше не нужен).
            val stableId = lookupOrCreateIdentityLink(firebaseUid)

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
            Log.w(TAG, "sign_in.cancelled by user")
            Outcome.Failure(AuthError.Cancelled)
        } catch (e: NoCredentialException) {
            Log.w(TAG, "sign_in.no_credential: ${e.message}")
            Outcome.Failure(AuthError.ProviderUnavailable)
        } catch (e: GetCredentialException) {
            // Generic Credential Manager failure (configuration, network, etc.).
            Log.w(TAG, "sign_in.credential_error type=${e::class.simpleName} msg=${e.message} cause=${e.cause?.message}")
            Outcome.Failure(AuthError.Unknown(e::class.simpleName ?: "credential"))
        } catch (e: FirebaseNetworkException) {
            Log.w(TAG, "sign_in.firebase_network: ${e.message}")
            Outcome.Failure(AuthError.NetworkError)
        } catch (e: FirebaseTooManyRequestsException) {
            Log.w(TAG, "sign_in.firebase_rate_limited: ${e.message}")
            Outcome.Failure(AuthError.NetworkError)
        } catch (e: Exception) {
            Log.w(TAG, "sign_in.unknown type=${e::class.simpleName} msg=${e.message}", e)
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
     * T751. Lookup-or-create в Firestore identity-link + users.
     *
     * **Two-step idempotent**, не одна транзакция. Причина:
     *  • Single transaction нельзя — Firestore Security Rules для `users/{stableId}`
     *    create требуют `exists(ownerIdentityLink(...))`, а в той же транзакции
     *    identity-link на момент валидации ещё не существует (Rules смотрят
     *    pre-transaction state). Single tx падает PERMISSION_DENIED.
     *  • Two-step с client-side rollback тоже нельзя — Rules заведомо запрещают
     *    клиентский delete identity-link (`allow delete: if false`, L341); orphan
     *    не починить.
     *
     * Решение — **идемпотентный self-healing**: оба шага гоняются на КАЖДОМ
     * sign-in, не только при первом. Если предыдущий sign-in упал между
     * identity-link и users — следующий sign-in доделает users автоматически
     * (find identity-link → попробовать создать users → если уже есть, Rules
     * вернут PERMISSION_DENIED на update [L357 allow update: if false] →
     * ловим этот частный случай как «уже создан» и идём дальше).
     *
     * Шаги:
     *  1. `runTransaction` для identity-link — race-safe lookup-or-create.
     *     Если два устройства одновременно first-time-sign-in — выиграет один.
     *  2. `setDoc` для `users/{stableId}` с попыткой create. При успехе — создали
     *     впервые. При PERMISSION_DENIED — документ уже есть (норма для повторных
     *     входов или для recovery после прерванного предыдущего sign-in), идём дальше.
     *
     * Per spec 017 FR-016a, contract `identity-link-v1.md`.
     */
    internal suspend fun lookupOrCreateIdentityLink(sub: String): String {
        val identityLinkRef = firestore.document("identity-links/google_$sub")
        val stableId = firestore.runTransaction<String> { txn ->
            val snapshot = txn.get(identityLinkRef)
            if (snapshot.exists()) {
                snapshot.getString("stableId")
                    ?: error("identity-links/google_$sub exists but missing stableId field")
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
                newUuid
            }
        }.await()

        try {
            firestore.document("users/$stableId").set(
                mapOf(
                    "schemaVersion" to 1L,
                    "stableId" to stableId,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            // PERMISSION_DENIED на втором шаге = users/{stableId} уже существует
            // (Rules L357 запрещают update). Норма для повторных sign-in.
            if (e.code != com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw e
            }
        }
        return stableId
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
        private const val TAG = "Auth"
        private const val REFRESH_BUFFER_MILLIS = 5 * 60 * 1000L
    }
}
