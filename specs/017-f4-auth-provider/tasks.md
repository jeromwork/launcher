# Tasks: F-4 — AuthProvider port + Google Sign-In adapter

**Branch**: `017-f4-auth-provider` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Generated**: 2026-06-18

Tasks decomposed from plan.md §"Project Structure" + "Test Strategy" + "Rollout / verification". Each task traces to FR / US / SC / Plan section / Research section.

**Format**: `T7NN [P?] [Story] Description`
- **T7NN** — task ID (spec 017 → T7NN range, consistent с project pattern).
- **[P]** — parallel-safe (different files, no dependencies с other [P] tasks в same phase).
- **[Story]** — User Story tag (US1-US7) для traceability.

**Total**: 64 tasks across 10 phases + 8 pre-release tasks (separate section).

---

## Phase 0 — Project setup & dependencies

### T701 — Verify `androidx.credentials` library version

**Trace**: research.md §R6; plan.md §"Dependency impact"; FR-014.
**What**: Open https://developer.android.com/jetpack/androidx/releases/credentials. Confirm:
- Last release date < 6 months.
- API level floor (minSdk = 24 для F-4 — confirm library supports).
- `credentials-play-services-auth` available at same version.
**Acceptance**: Concrete version + commit message-ready note added к research.md §R6 "Status" line. Decision: keep v1.3.x or bump к latest stable.
**Dependencies**: none.
**Files**: `research.md` (update §R6).

### T702 — Add F-4 dependencies к `gradle/libs.versions.toml`

**Trace**: plan.md §"Dependency impact"; FR-014, FR-015.
**What**: Add version constants and library aliases:
```toml
androidx-credentials = "1.3.0"
google-id = "1.1.1"
firebase-bom = "33.x.x"  # confirm latest at task time
androidx-security-crypto = "1.1.0-alpha06"
```
With library aliases для каждого: `androidx-credentials`, `androidx-credentials-play-services-auth`, `google-id`, `firebase-auth-ktx`, `firebase-firestore-ktx`, `androidx-security-crypto`.
**Acceptance**: `./gradlew :app:dependencies` lists new entries; no version conflicts с existing Firebase BOM (если уже есть в проекте через spec 007).
**Dependencies**: requires T701.
**Files**: `gradle/libs.versions.toml`.

### T703 — Add F-4 dependencies к `app/build.gradle.kts`

**Trace**: plan.md §"Dependency impact"; FR-014, FR-015, FR-020.
**What**: Add к `androidMain` source set:
- `androidx.credentials:credentials`
- `androidx.credentials:credentials-play-services-auth`
- `com.google.android.libraries.identity.googleid:googleid`
- `com.google.firebase:firebase-auth-ktx`
- `com.google.firebase:firebase-firestore-ktx`
- `androidx.security:security-crypto`
**Acceptance**: `./gradlew :app:assembleDebug` succeeds. No new dependencies introduced в `core/domain/` (verify by `./gradlew :core:domain:dependencies | grep -i 'firebase\|google\|credentials'` returns empty).
**Dependencies**: requires T702.
**Files**: `app/build.gradle.kts`.

---

## Phase 1 — Domain types (no behaviour, pure Kotlin)

**Goal**: Public domain API в `core/domain/auth/` без vendor SDK. Foundation для всех User Stories.

### T710 [P] [US3] Create `AuthError` sealed type

**Trace**: FR-009; data-model.md §"AuthError"; US 2 acceptance #5, #6.
**What**: Create file `core/domain/src/commonMain/kotlin/family/launcher/domain/auth/AuthError.kt`:
```kotlin
package family.launcher.domain.auth

sealed class AuthError {
    object NetworkError : AuthError()
    object Cancelled : AuthError()
    object NoEmail : AuthError()
    object ProviderUnavailable : AuthError()
    data class Unknown(val message: String) : AuthError()
}
```
**Acceptance**: Compiles. Exhaustive `when` test случай покрывает все 5 cases.
**Dependencies**: requires T703.
**Files**: `core/domain/src/commonMain/kotlin/family/launcher/domain/auth/AuthError.kt`.

### T711 [P] [US3] Create `Outcome<T, E>` sealed type (if not exists)

**Trace**: FR-006; data-model.md §"Outcome".
**What**: Check if `family.launcher.domain.common.Outcome` already exists. If yes — skip. If no — create:
```kotlin
package family.launcher.domain.common

sealed class Outcome<out T, out E> {
    data class Success<T>(val value: T) : Outcome<T, Nothing>()
    data class Failure<E>(val error: E) : Outcome<Nothing, E>()
}
```
**Acceptance**: Compiles. `Outcome.Success("x").value == "x"`, `Outcome.Failure(AuthError.Cancelled).error == AuthError.Cancelled`.
**Dependencies**: requires T703. Independent of T710.
**Files**: `core/domain/src/commonMain/kotlin/family/launcher/domain/common/Outcome.kt` (new или skip).

### T712 [P] [US3] Create `AuthIdentity` data class

**Trace**: FR-007; clarification Q1, Q4; data-model.md §"AuthIdentity".
**What**: Create file `AuthIdentity.kt`:
```kotlin
package family.launcher.domain.auth

data class AuthIdentity(
    val stableId: String,
    val displayName: String?,
    val email: String?,
)
```
**NB**: NO `providerKind` field (Q4 removal). `stableId` — наш UUID, not Firebase UID, not Google sub claim (Q1).
**Acceptance**: Compiles. Property test: `AuthIdentity(stableId = "uuid", null, null)` valid. Detekt rule `NoProviderKindInAuthIdentity` (created в Phase 8) green.
**Dependencies**: requires T703. Independent of T710, T711.
**Files**: `core/domain/src/commonMain/kotlin/family/launcher/domain/auth/AuthIdentity.kt`.

### T713 [P] [US3] Create `SubscriptionState` sealed type

**Trace**: FR-011; clarification Q6; data-model.md §"SubscriptionState".
**What**: Create file `SubscriptionState.kt`:
```kotlin
package family.launcher.domain.auth

sealed class SubscriptionState {
    object Unknown : SubscriptionState()
    object LocalOnly : SubscriptionState()
    object Trial : SubscriptionState()
    object Active : SubscriptionState()
    object Expired : SubscriptionState()
}
```
**Acceptance**: Compiles. MVP usage: всегда `Unknown` (per FR-031); Detekt rule `NoClientComputedSubscriptionActive` enforces.
**Dependencies**: requires T703. Independent of T710-T712.
**Files**: `core/domain/src/commonMain/kotlin/family/launcher/domain/auth/SubscriptionState.kt`.

### T714 [P] [US3] Create `IdentityKeys` forward-declaration interface

**Trace**: FR-010; data-model.md §"User"; F-5 forward declaration.
**What**: Create empty marker interface:
```kotlin
package family.launcher.domain.auth

/** Forward declaration для F-5. В F-4 всегда null. Real type — F-5 ConfigCipher spec. */
interface IdentityKeys
```
**Acceptance**: Compiles. No implementations in F-4.
**Dependencies**: requires T703. Independent.
**Files**: `core/domain/src/commonMain/kotlin/family/launcher/domain/auth/IdentityKeys.kt`.

### T715 [US3] Create `User` data class

**Trace**: FR-010; clarification Q4; data-model.md §"User".
**What**: Create file `User.kt`:
```kotlin
package family.launcher.domain.auth

data class User(
    val id: String,
    val identityKeys: IdentityKeys?,
    val email: String?,
    val displayName: String?,
    val subscriptionState: SubscriptionState,
)
```
**Acceptance**: Compiles. No `providerKind` field. `identityKeys` всегда null в F-4 path.
**Dependencies**: requires T712, T713, T714.
**Files**: `core/domain/src/commonMain/kotlin/family/launcher/domain/auth/User.kt`.

### T716 [US3] Create `AuthProvider` port interface

**Trace**: FR-006; contract `auth-provider-port.md`; clarification Q7; US 1, US 2, US 3.
**What**: Create file `AuthProvider.kt`:
```kotlin
package family.launcher.domain.auth

import family.launcher.domain.common.Outcome
import kotlinx.coroutines.flow.Flow

interface AuthProvider {
    val currentUser: Flow<AuthIdentity?>
    suspend fun signIn(): Outcome<AuthIdentity, AuthError>
    suspend fun signOut()
}
```
**Acceptance**: Compiles. Detekt rule `NoVendorImportsInDomain` green (no Firebase, Google, OAuth, Apple, Phone, Email words в file per FR-027).
**Dependencies**: requires T710, T711, T712.
**Files**: `core/domain/src/commonMain/kotlin/family/launcher/domain/auth/AuthProvider.kt`.

### T717 [P] [US3] Create internal `SessionRecord` data class

**Trace**: FR-013; clarification Q2 (internal); data-model.md §"SessionRecord"; contract `session-record-v1.md`.
**What**: Create file `core/domain/src/commonMain/kotlin/family/launcher/domain/auth/internal/SessionRecord.kt`:
```kotlin
package family.launcher.domain.auth.internal

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class SessionRecord(
    val schemaVersion: Int = 1,
    val stableId: String,
    val expiresAt: Instant?,
    val refreshToken: String?,
    val extra: Map<String, String>,
)
```
**Acceptance**: Compiles. `internal` modifier verified. No `providerKind` (Q4 cascade). Detekt rule `NoSessionRecordInConsumers` (Phase 8) green.
**Dependencies**: requires T703.
**Files**: `core/domain/src/commonMain/kotlin/family/launcher/domain/auth/internal/SessionRecord.kt`.

### T718 [US3] Create internal `SessionStore` port interface

**Trace**: FR-012; clarification Q2; data-model.md §"SessionStore"; US 5.
**What**: Create file `SessionStore.kt`:
```kotlin
package family.launcher.domain.auth.internal

import kotlinx.coroutines.flow.Flow

internal interface SessionStore {
    suspend fun save(session: SessionRecord)
    suspend fun current(): SessionRecord?
    suspend fun clear()
    val sessionChanges: Flow<SessionRecord?>
}
```
**Acceptance**: Compiles. `internal` modifier verified.
**Dependencies**: requires T717.
**Files**: `core/domain/src/commonMain/kotlin/family/launcher/domain/auth/internal/SessionStore.kt`.

**Checkpoint**: All public + internal domain types созданы. `core/domain/` compiles cleanly. Detekt baseline still green (Detekt rules будут добавлены в Phase 8). Consumer code (F-5, S-2) уже может писать against AuthProvider port using Fake adapter (after Phase 2).

---

## Phase 2 — Fake adapters (mock-first per CLAUDE.md rule §6)

**Goal**: Enable consumer tests (F-5, S-2 future) writing tests against AuthProvider без real Firebase.

### T720 [P] [US3] Implement `FakeAuthAdapter`

**Trace**: FR-024, FR-025; data-model.md §"AuthProvider"; US 4 (refresh simulators), US 6 (provider-swap).
**What**: Create file `core/domain/src/commonTest/kotlin/family/launcher/domain/auth/FakeAuthAdapter.kt`:
- Constructor accepts `fakeUsers: List<AuthIdentity>` (pre-seeded).
- `signIn()` returns first user в list as Success, OR error (configurable simulator).
- `signOut()` clears state, emits null.
- `currentUser: MutableStateFlow<AuthIdentity?>`.
- Test API: `simulateRefreshFailure()`, `simulateNoEmail()`, `simulateCancellation()`, `simulateNetworkError()`, `simulateProviderUnavailable()`.
- Annotated `@VisibleForTesting`.
**Acceptance**: Implements `AuthProvider` interface. Unit test: `FakeAuthAdapter(listOf(testUser)).signIn() == Outcome.Success(testUser)`. Each `simulateX()` triggers corresponding `AuthError` variant.
**Dependencies**: requires T716.
**Files**: `core/domain/src/commonTest/kotlin/family/launcher/domain/auth/FakeAuthAdapter.kt`.

### T721 [P] [US3] Implement `FakeSessionStore`

**Trace**: FR-026; data-model.md §"FakeSessionStore"; US 5.
**What**: Create file `FakeSessionStore.kt`:
- In-memory `HashMap`-based.
- Implements `SessionStore` interface.
- `MutableStateFlow<SessionRecord?>` для `sessionChanges`.
- Deterministic — no Random / Clock.now() — accepts `Clock` parameter для tests.
- Annotated `@VisibleForTesting`.
- **No Android Context dependency** (это commonTest).
**Acceptance**: Compiles в `commonTest`. Unit test: `save(record); current() == record`. `clear(); current() == null`. `sessionChanges` emits same sequence as save/clear calls.
**Dependencies**: requires T718.
**Files**: `core/domain/src/commonTest/kotlin/family/launcher/domain/auth/FakeSessionStore.kt`.

### T722 [P] [US3] Write `AuthProviderContractTest` (runs against Fake)

**Trace**: FR-006; contract `auth-provider-port.md`; plan.md §"Test Strategy" #1; US 1, US 2, US 4, US 5.
**What**: Contract test suite verifying semantic invariants для any `AuthProvider` implementation. Runs против `FakeAuthAdapter`. Covers:
- `signIn()` success → currentUser emits Identity within 100ms.
- `signOut()` clears, emits null.
- `signOut()` idempotent (повторный вызов = no-op).
- `currentUser` distinct-until-changed.
- Initial null emission на adapter creation.
- `Outcome.Failure(Cancelled)` doesn't change currentUser state.
**Acceptance**: All assertions pass. Same suite will run против mocked Google adapter в T755.
**Dependencies**: requires T720, T721.
**Files**: `core/domain/src/commonTest/kotlin/family/launcher/domain/auth/AuthProviderContractTest.kt`.

### T723 [US3] Write `AuthErrorExhaustivenessPropertyTest`

**Trace**: FR-009; plan.md §"Test Strategy" #2.
**What**: Property test (Kotest properties): each `AuthError` variant returned by at least one `FakeAuthAdapter.simulateX()` path. Iterations: 1000. Verifies AuthError sealed `when` exhaustive в consumer code patterns.
**Acceptance**: 1000 iterations pass. If new AuthError added без simulator — test fails.
**Dependencies**: requires T720.
**Files**: `core/domain/src/commonTest/kotlin/family/launcher/domain/auth/AuthErrorExhaustivenessPropertyTest.kt`.

**Checkpoint**: Consumer tests могут запускаться против Fake adapter. F-5 ConfigCipher tests, S-2 PairingFlow tests могут начать разработку independent от Firebase setup.

---

## Phase 3 — Wire format contracts + tests

**Goal**: SessionRecord JSON wire format + test fixtures + roundtrip + backward-compat test (per CLAUDE.md rule §5).

### T730 [P] [US5] Create SessionRecord v1 test fixture

**Trace**: contract `session-record-v1.md` §"Test fixtures"; FR-022.
**What**: Create file `core/domain/src/commonTest/resources/auth-fixtures/session-record-v1.json`:
```json
{
  "schemaVersion": 1,
  "stableId": "550e8400-e29b-41d4-a716-446655440000",
  "expiresAt": 1739456789000,
  "refreshToken": "1//04test-refresh-token-stable-fixture",
  "extra": {
    "firebase_jwt": "eyJhbGciOiJSUzI1NiIs.test.payload"
  }
}
```
**Acceptance**: File exists. Hardcoded UUID (no `UUID.randomUUID()`) для stable fixture.
**Dependencies**: requires T717.
**Files**: `core/domain/src/commonTest/resources/auth-fixtures/session-record-v1.json`.

### T731 [US5] Write `SessionRecordRoundtripTest`

**Trace**: FR-022; SC-009; contract `session-record-v1.md`; plan.md §"Test Strategy" #2 (property tests).
**What**: Roundtrip test:
```kotlin
val original = SessionRecord(schemaVersion = 1, stableId = "...", expiresAt = Instant.fromEpochMilliseconds(1739456789000), refreshToken = "...", extra = mapOf("firebase_jwt" to "..."))
val json = Json.encodeToString(original)
val decoded = Json.decodeFromString<SessionRecord>(json)
assertEquals(original, decoded)
```
**Acceptance**: Test passes. `Json.encodeToString` and `decodeFromString` roundtrip identity.
**Dependencies**: requires T717, T730.
**Files**: `core/domain/src/commonTest/kotlin/family/launcher/domain/auth/internal/SessionRecordRoundtripTest.kt`.

### T732 [US5] Write `SessionRecordBackwardCompatTest`

**Trace**: FR-022; contract `session-record-v1.md`; CLAUDE.md rule §5.
**What**: Read fixture from T730 → parse → assert expected fields:
```kotlin
val v1Fixture = readResource("auth-fixtures/session-record-v1.json")
val decoded = Json.decodeFromString<SessionRecord>(v1Fixture)
assertEquals(1, decoded.schemaVersion)
assertEquals("550e8400-e29b-41d4-a716-446655440000", decoded.stableId)
// ... assert all 5 fields
```
Also: schemaVersion=2 forward-compat test stub — fixture с `"schemaVersion": 2` → parse → expect graceful failure (returns null если invoked through EncryptedLocalSessionStore.current(); throws SerializationException если invoked directly).
**Acceptance**: Test passes. Migration handler placeholder работает для hypothetical v2 fixture.
**Dependencies**: requires T717, T730.
**Files**: `core/domain/src/commonTest/kotlin/family/launcher/domain/auth/internal/SessionRecordBackwardCompatTest.kt`.

**Checkpoint**: SessionRecord wire format fully tested. Future schema bumps (v2) уже имеют test scaffolding.

---

## Phase 4 — EncryptedLocalSessionStore (Android adapter)

**Goal**: Real persistent SessionStore через EncryptedSharedPreferences + TEE-backed master key.

### T740 [US5] Implement `EncryptedLocalSessionStore`

**Trace**: FR-020, FR-021, FR-023; data-model.md §"EncryptedLocalSessionStore"; US 5; research.md §R7 (no caching SessionRecord в memory beyond MutableStateFlow).
**What**: Create file `app/src/androidMain/kotlin/family/launcher/app/auth/EncryptedLocalSessionStore.kt`:
- Constructor: `Context`, `Json` (для serialization).
- `EncryptedSharedPreferences.create(...)` с `MasterKey.Builder(context).setKeyScheme(AES256_GCM).build()`.
- File name: `auth_session_v1.preferences`.
- Key: `"session"`.
- `save(record)`: `withContext(Dispatchers.IO) { prefs.edit { putString("session", json.encodeToString(record)) } }`. Also emit к `MutableStateFlow`.
- `current()`: `withContext(IO) { prefs.getString("session", null)?.let { runCatching { json.decodeFromString<SessionRecord>(it) }.getOrNull() } }`. If `runCatching` fails (corrupted blob) — log warning + return null (FR-023).
- `clear()`: `prefs.edit { remove("session") }` + emit null.
- `sessionChanges`: `MutableStateFlow<SessionRecord?>` initialized from `current()` (async).
- Inline TODO comments:
  - `// TODO(F-CRYPTO migration): currently EncryptedSharedPreferences (L0 tamper-resistance level). When F-5 ships, migrate к SecureKeyStore из F-CRYPTO. Additive change. Per FR-020.`
  - `// TODO(authorized-request-signer): future port для подписи RPC — adapter will read SessionRecord.extra["firebase_jwt"], consumer'ы не видят token.`
**Acceptance**: Compiles. Internal modifier verified. Implements `SessionStore` interface.
**Dependencies**: requires T718, T703.
**Files**: `app/src/androidMain/kotlin/family/launcher/app/auth/EncryptedLocalSessionStore.kt`.

### T741 [P] [US5] Write `EncryptedLocalSessionStorePropertyTest` (corrupted blob handling)

**Trace**: FR-023; US 5 acceptance #3; plan.md §"Test Strategy" #2.
**What**: Property test:
```kotlin
propertyTest(1000.iterations()) { seed: Int ->
    val store = EncryptedLocalSessionStore(testContext, Json)
    runBlocking { store.save(validSessionRecord()) }
    // Flip random byte в underlying file
    corruptFileRandomly(seed, testContext)
    assertEquals(null, runBlocking { store.current() })  // null, не throw
}
```
**Acceptance**: 1000 iterations, no crashes. Always null on corruption.
**Dependencies**: requires T740.
**Files**: `app/src/test/kotlin/family/launcher/app/auth/EncryptedLocalSessionStorePropertyTest.kt`.

### T742 [P] [US5] Write `EncryptedLocalSessionStoreInstrumentationTest` (real TEE)

**Trace**: FR-020; data-model.md §"EncryptedLocalSessionStore"; SC-007 (session persistence); US 5 acceptance #1.
**What**: Instrumentation test на `pixel_5_api_34`:
- Real Android Keystore TEE-backed AES master key.
- save → process restart simulation (new EncryptedSharedPreferences instance same Context) → current() returns saved record.
- Scan underlying `auth_session_v1.preferences.xml` file bytes: assert plaintext refreshToken **not present** (encryption verified).
**Acceptance**: Test passes on `pixel_5_api_34`. Sensitive bytes encrypted.
**Dependencies**: requires T740.
**Files**: `app/src/androidTest/kotlin/family/launcher/app/auth/EncryptedLocalSessionStoreInstrumentationTest.kt`.

### T743 [US5] Update `app/src/main/res/xml/data_extraction_rules.xml` (backup exclusion)

**Trace**: research.md §R10; security checklist CHK024; tamper-resistance HIGH-4.
**What**: Add backup exclusion:
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="auth_session_v1.preferences.xml"/>
        <exclude domain="sharedpref" path="auth_session_v1.preferences.xml.bak"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="auth_session_v1.preferences.xml"/>
        <exclude domain="sharedpref" path="auth_session_v1.preferences.xml.bak"/>
    </device-transfer>
</data-extraction-rules>
```
Update `AndroidManifest.xml`: `<application android:dataExtractionRules="@xml/data_extraction_rules" ...>`. If file already exists для других целей — merge.
**Acceptance**: Manifest references file. Manual smoke (T801 в pre-release): adb-restore от device backup НЕ восстанавливает session blob.
**Dependencies**: requires T740.
**Files**: `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/AndroidManifest.xml`.

**Checkpoint**: SessionStore production-ready. Persistence verified, encryption verified, corrupted blob graceful, backup excluded.

---

## Phase 5 — GoogleSignInAuthAdapter (real implementation)

**Goal**: Working Google Sign-In flow с identity-links lookup + Firebase exchange. Largest phase.

### T750 [US3] Implement `extractSubClaim(idToken: String): String` private helper

**Trace**: research.md §R1; FR-016a.
**What**: Add к `GoogleSignInAuthAdapter` (создаётся в T752) private function:
- Split ID token by `.` — middle segment = payload.
- Base64url decode (no padding) middle segment.
- Parse JSON, extract `sub` field as String.
- Return.
**Acceptance**: Unit test с hardcoded Google ID Token fixture → expected `sub` value. Edge cases: malformed token → throws `IllegalArgumentException`.
**Dependencies**: requires T703.
**Files**: `app/src/androidMain/kotlin/family/launcher/app/auth/GoogleSignInAuthAdapter.kt` (private function within).

### T751 [US3] Implement `lookupOrCreateIdentityLink(sub): String` private helper

**Trace**: FR-016a; research.md §R4, §R9; contract `identity-link-v1.md`.
**What**: Add к `GoogleSignInAuthAdapter` private function using Firestore transaction:
```kotlin
private suspend fun lookupOrCreateIdentityLink(sub: String): String {
    return firestore.runTransaction { transaction ->
        val docRef = firestore.document("identity-links/google/$sub")
        val snapshot = transaction.get(docRef)
        if (snapshot.exists()) {
            snapshot.getString("stableId")!!
        } else {
            val newUuid = UUID.randomUUID().toString()
            transaction.set(docRef, mapOf("schemaVersion" to 1, "stableId" to newUuid, "createdAt" to FieldValue.serverTimestamp()))
            transaction.set(firestore.document("users/$newUuid"), mapOf("schemaVersion" to 1, "stableId" to newUuid, "createdAt" to FieldValue.serverTimestamp()))
            newUuid
        }
    }.await()
}
```
Add inline TODOs: `country-ban-exit-ramp`, `server-roadmap SRV-AUTH-IDENTITY-001`.
**Acceptance**: Compiles. Unit test (с mocked Firestore): existing doc → returns stableId; non-existing → creates new UUID + commits.
**Dependencies**: requires T703.
**Files**: same file as T750.

### T752 [US2] [US3] Implement `GoogleSignInAuthAdapter` (main class)

**Trace**: FR-005, FR-014, FR-015, FR-016, FR-017, FR-029; US 2, US 3; data-model.md §"GoogleSignInAuthAdapter"; research.md §R2 (adapter-scoped coroutine), §R8 (logging).
**What**: Create file `GoogleSignInAuthAdapter.kt` implementing `AuthProvider`:
- Constructor: `CredentialManager`, `FirebaseAuth`, `FirebaseFirestore`, `SessionStore`, `Json`, `Clock`.
- `adapterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())`.
- `_currentUser: MutableStateFlow<AuthIdentity?>(null)`.
- `init { adapterScope.launch { restoreSessionAsync() } }` — async session restore, не блокирует cold start (FR-035).
- `signIn()`: dedup via `inFlightSignIn: Deferred<...>?`. Steps:
  1. Open Credential Manager → get `GoogleIdTokenCredential.idToken`.
  2. `extractSubClaim(idToken)` (T750).
  3. `lookupOrCreateIdentityLink(sub)` (T751) → stableId.
  4. Check email: if Google credential has email null → return `Outcome.Failure(AuthError.NoEmail)` (FR-016).
  5. Firebase `signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))` → Firebase JWT, refreshToken.
  6. `SessionRecord(schemaVersion=1, stableId, expiresAt, refreshToken, extra = mapOf("firebase_jwt" to jwt))`.
  7. `sessionStore.save(record)`.
  8. `_currentUser.value = AuthIdentity(stableId, displayName, email)`.
  9. Return `Outcome.Success(identity)`.
  - Error mapping: Credential Manager Cancelled → `AuthError.Cancelled`; network → `NetworkError`; Firebase API error → `Unknown(message)`.
- `signOut()`: `firebaseAuth.signOut(); sessionStore.clear(); _currentUser.value = null`.
- Inline TODOs (per FR-005): `auth-provider-extensions`, `server-roadmap SRV-AUTH-001`, `country-ban-exit-ramp`, `authorized-request-signer`.
**Acceptance**: Compiles. NO `signInAnonymously` calls anywhere (FR-029 Detekt rule). Calls structured `AuthLog` (T780) instead of raw `Log.*`.
**Dependencies**: requires T716, T717, T740, T750, T751.
**Files**: `app/src/androidMain/kotlin/family/launcher/app/auth/GoogleSignInAuthAdapter.kt`.

### T753 [US4] Implement token refresh logic в GoogleSignInAuthAdapter

**Trace**: FR-017; US 4; research.md §R2.
**What**: Add к `GoogleSignInAuthAdapter`:
- `private suspend fun refreshTokenIfNeeded(record: SessionRecord): SessionRecord?` — checks `expiresAt < Clock.System.now() + 5.minutes`. If expired:
  - `firebaseAuth.currentUser?.getIdToken(true)` — forces refresh.
  - Updates `SessionRecord` with new `expiresAt`, new `extra["firebase_jwt"]`.
  - Saves через `sessionStore.save(...)`.
  - Returns updated record.
  - On failure: `signOut()` triggered automatically (Refresh failed → null currentUser, per US 4 #3).
- Called from `restoreSessionAsync()` after reading SessionStore.
- Adapter-side trigger (no public API — consumer-сервисы don't call this directly).
**Acceptance**: Test (`TokenRefreshTest`) с FakeAuthAdapter + FakeSessionStore (можно — pure logic): expired session → refresh attempt → new record saved. Refresh failed → `currentUser` emits null.
**Dependencies**: requires T752.
**Files**: same file as T752.

### T754 [P] [US2] Write `GoogleSignInAdapterUnitTest` (mocked Firebase)

**Trace**: US 2 acceptance scenarios #2, #5, #6, #7, #8.
**What**: JVM unit tests с `mockk` mocking Firebase Auth + Firestore + Credential Manager:
- Sign-in success new account: Credential Manager returns ID Token → identity-links lookup returns null → adapter creates UUID → returns AuthIdentity с new UUID.
- Sign-in success existing account: identity-links returns existing UUID → adapter returns AuthIdentity с same UUID.
- Cancelled: Credential Manager throws cancellation exception → returns `Outcome.Failure(Cancelled)`.
- NoEmail: ID Token's email field null → returns `Outcome.Failure(NoEmail)`.
- NetworkError: Firestore throws network exception → returns `Outcome.Failure(NetworkError)`.
- Sign-out: clears Firebase Auth, clears SessionStore, emits null currentUser.
**Acceptance**: All 6 scenarios pass.
**Dependencies**: requires T752.
**Files**: `app/src/test/kotlin/family/launcher/app/auth/GoogleSignInAdapterUnitTest.kt`.

### T755 [P] [US3] Write `AuthProviderContractTest` runs against mocked Google adapter

**Trace**: contract `auth-provider-port.md`; plan.md §"Test Strategy" #1 (contract tests run **дважды**).
**What**: Same contract suite from T722 (semantic invariants — signIn, signOut, currentUser invariants) runs against mocked GoogleSignInAuthAdapter (mockk Firebase + Firestore + Credential Manager). Identical assertions pass.
**Acceptance**: Both Fake and Google pass same contract suite (provider-agnostic verified).
**Dependencies**: requires T722, T752.
**Files**: `app/src/test/kotlin/family/launcher/app/auth/AuthProviderContractTestGoogle.kt`.

### T756 [US2] [US3] Write `GoogleSignInAdapterInstrumentationTest` (Firebase Emulator)

**Trace**: FR-014; SC-005; quickstart.md §"Firebase Emulator Setup"; research.md §R3.
**What**: Instrumentation test на `pixel_5_api_34`:
- Setup: Firebase Auth Emulator + Firestore Emulator running (firebase.json pre-configured).
- `FirebaseEmulatorRule` connects SDK к localhost.
- Pre-seed test user `test-admin@example.com` в Auth Emulator.
- Test: trigger `signIn()` programmatically (skip Credential Manager UI for emulator test) → assert `currentUser` emits AuthIdentity → assert identity-links document created в Firestore Emulator с stableId UUID.
**Acceptance**: Test passes against Firebase Emulator. Identity-links collection populated correctly.
**Dependencies**: requires T752, T756-prereq (Firebase Emulator setup in quickstart, manual).
**Files**: `app/src/androidTest/kotlin/family/launcher/app/auth/GoogleSignInAdapterInstrumentationTest.kt`.

### T757 [US3] Write `InFlightSignInRotationTest`

**Trace**: research.md §R2; state-management HIGH-5; US 2 #1 (sign-in survives rotation).
**What**: Instrumentation test:
- Trigger `signIn()` via test harness (mock Credential Manager that delays 2s before returning credential).
- After 1s — simulate Activity recreation (`InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.recreate() }`).
- After Credential Manager returns — assert `currentUser` emits AuthIdentity (signIn survived rotation).
**Acceptance**: Test passes. Adapter-scoped coroutine completion guaranteed.
**Dependencies**: requires T752, T781 (SignInTrigger composable for context).
**Files**: `app/src/androidTest/kotlin/family/launcher/app/auth/InFlightSignInRotationTest.kt`.

**Checkpoint**: GoogleSignInAuthAdapter functional end-to-end в emulator. Token refresh works. Rotation-safe.

---

## Phase 6 — AuthAdapterSelector + DI wiring

**Goal**: Runtime selection between Fake (debug/test) and Google (release). GMS detection. Application initialization без cold-start блокировки.

### T760 [US3] Implement `NoSupportedAuthProvider` placeholder adapter

**Trace**: FR-018; edge case «Google Play Services недоступен»; OEM matrix Huawei row.
**What**: Create file:
```kotlin
package family.launcher.app.auth

internal object NoSupportedAuthProvider : AuthProvider {
    override val currentUser: Flow<AuthIdentity?> = flowOf(null)
    override suspend fun signIn(): Outcome<AuthIdentity, AuthError> = Outcome.Failure(AuthError.ProviderUnavailable)
    override suspend fun signOut() { /* no-op */ }
}
```
**Acceptance**: Compiles. Sign-in always returns ProviderUnavailable. Used by AuthAdapterSelector on Huawei / non-GMS devices.
**Dependencies**: requires T716.
**Files**: `app/src/androidMain/kotlin/family/launcher/app/auth/NoSupportedAuthProvider.kt`.

### T761 [US3] Implement `AuthAdapterSelector`

**Trace**: FR-018; data-model.md §"AuthAdapterSelector".
**What**: Create file:
```kotlin
internal class AuthAdapterSelector(
    private val googleApiAvailability: GoogleApiAvailability,
    private val googleAdapter: GoogleSignInAuthAdapter,
    private val context: Context,
) {
    fun pickAdapter(): AuthProvider = when {
        isGmsAvailable() -> googleAdapter
        else -> NoSupportedAuthProvider
    }
    private fun isGmsAvailable(): Boolean =
        googleApiAvailability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
```
**Acceptance**: Compiles. Unit test: GMS available → returns Google adapter; GMS unavailable → returns NoSupportedAuthProvider.
**Dependencies**: requires T752, T760.
**Files**: `app/src/androidMain/kotlin/family/launcher/app/auth/AuthAdapterSelector.kt`.

### T762 [US3] Create `AuthModule` DI wiring

**Trace**: FR-019; plan.md §"Project Structure" `di/AuthModule.kt`.
**What**: Create Koin (или другой DI framework, depends on project) module:
```kotlin
val authModule = module {
    single<Json> { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
    single { GoogleApiAvailability.getInstance() }
    single { FirebaseAuth.getInstance() }
    single { FirebaseFirestore.getInstance() }
    single { CredentialManager.create(get()) }
    single<SessionStore> { EncryptedLocalSessionStore(get(), get<Json>()) }
    single { GoogleSignInAuthAdapter(get(), get(), get(), get(), get<Json>(), Clock.System) }
    single { AuthAdapterSelector(get(), get(), get()) }
    single<AuthProvider> { get<AuthAdapterSelector>().pickAdapter() }
}
```
For `debug` / `test` source set: override `AuthProvider` binding to `FakeAuthAdapter`.
**Acceptance**: Koin verification test passes. `AuthProvider` resolvable from any consumer. Debug build uses Fake; release build uses real.
**Dependencies**: requires T720, T761.
**Files**: `app/src/androidMain/kotlin/family/launcher/app/auth/di/AuthModule.kt`, `app/src/debug/kotlin/family/launcher/app/auth/di/AuthDebugModule.kt`.

### T763 [P] [US6] Write `ProviderSwapFitnessTest`

**Trace**: SC-008; US 6; plan.md §"Test Strategy" #5; contract `auth-provider-port.md`.
**What**: Special integration test:
- Build DI graph A: real `GoogleSignInAuthAdapter` (with mockk-mocked Firebase + Credential Manager + Firestore Emulator).
- Build DI graph B: `FakeAuthAdapter` (pre-seeded users).
- Define consumer-test suite (placeholder F-5 ConfigCipher тест: derive key from `AuthIdentity.stableId`; placeholder S-2 PairingFlow: create delegation document с UUID).
- Run consumer suite на обоих DI graphs.
- Assert: identical pass rate (только identity values отличаются).
- Add `// TODO(consumer-tests): when F-5 / S-2 specs ship, replace placeholder consumer tests with real ones.`
**Acceptance**: Both graphs pass. If consumer test would fail на одном graph и не на другом — fitness violation (rule 2 ACL leak).
**Dependencies**: requires T720, T752, T762.
**Files**: `app/src/test/kotlin/family/launcher/app/auth/ProviderSwapFitnessTest.kt`.

### T764 [US1] Write `LocalModeNoSignInTest`

**Trace**: FR-030; SC-004; US 1 acceptance #1, #2, #3, #4.
**What**: Integration test на `pixel_5_api_34`:
- Force-stop app + clear data.
- Launch app.
- Walk through wizard выбрав «Настроить с нуля» (skip sign-in).
- Wait 10 seconds idle on main screen.
- Assertions:
  - `authProvider.currentUser.first() == null`.
  - `sessionStore.current() == null`.
  - Network traffic к Firebase Auth domains (`*.googleapis.com`, `*.firebaseapp.com`) — **zero packets** (через test-side network interceptor).
  - `signIn()` never invoked (через test hook).
**Acceptance**: Test passes. F-4 не активируется в local mode.
**Dependencies**: requires T762, F-3 wizard merged (dependency).
**Files**: `app/src/androidTest/kotlin/family/launcher/app/auth/LocalModeNoSignInTest.kt`.

### T765 [US5] Write `ColdStartLatencyTest`

**Trace**: FR-035; plan.md §"Test Strategy" #4; performance goals.
**What**: Instrumentation test на `pixel_5_api_34`:
- Force-stop app.
- Pre-seed valid `SessionRecord` в EncryptedSharedPreferences.
- Launch app via intent.
- Measure time-to-first-paint via Compose test hooks (`waitForIdle`).
- Assert: time-to-first-paint ≤ 500ms perceived latency.
- Assert: `SessionStore.current()` Job не завершён к моменту первого paint (verified через test-injected hook on `EncryptedLocalSessionStore.current()`).
**Acceptance**: Test passes. F-4 не блокирует cold-start path.
**Dependencies**: requires T740, T762.
**Files**: `app/src/androidTest/kotlin/family/launcher/app/auth/ColdStartLatencyTest.kt`.

**Checkpoint**: DI complete, GMS detection works, provider-swap fitness verifies ACL, cold-start unblocked. F-4 ready для wiring в Application class.

---

## Phase 7 — SignInTrigger composable + UI tests

**Goal**: Reusable Compose UI для входа/выхода.

### T780 [P] [US2] Create localized strings в `strings_auth.xml`

**Trace**: FR-033; checklist-localization-ui; clarification Q5; senior-friendly «настройки» replacement.
**What**: Create file `core/src/commonMain/composeResources/values/strings_auth.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="auth_sign_in_button">Войти в аккаунт</string>
    <string name="auth_sign_in_explanation">Чтобы настройки сохранялись и были доступны на других устройствах</string>
    <string name="auth_signed_in_status">Вошли как %1$s</string>
    <string name="auth_sign_out_button">Выйти</string>
    <string name="auth_loading_signing_in">Выполняется вход…</string>
    <string name="auth_loading_signing_out">Выполняется выход…</string>
    <string name="auth_error_no_email">Используйте личный Google-аккаунт</string>
    <string name="auth_error_network">Нет соединения. Попробуйте позже</string>
    <string name="auth_error_provider_unavailable">Вход через Google недоступен на этом устройстве</string>
    <string name="auth_error_unknown">Не удалось войти. Попробуйте позже</string>
    <!-- TalkBack content descriptions (FR-036) -->
    <string name="auth_a11y_sign_in_button">Войти в Google аккаунт. Чтобы настройки сохранялись и были доступны на других устройствах</string>
    <string name="auth_a11y_signed_in_status">Вошли как %1$s</string>
    <string name="auth_a11y_sign_out_button">Выйти из учётной записи %1$s</string>
</resources>
```
**Acceptance**: File compiles. RU baseline ready. Will be auto-translated к 9 locales (ES/ZH/AR/HI/PT/DE/FR/JA/KK-Latn) через `procedure-translate-spec-strings` после spec.tasks completion.
**Dependencies**: requires T703.
**Files**: `core/src/commonMain/composeResources/values/strings_auth.xml`.

### T781 [US2] Implement `SignInTriggerState` sealed type + state derivation

**Trace**: FR-033; research.md §R7; data-model.md §"SignInTriggerState".
**What**: Create file `SignInTriggerState.kt`:
```kotlin
internal sealed class SignInTriggerState {
    object NotSignedIn : SignInTriggerState()
    object SigningIn : SignInTriggerState()
    data class SignedIn(val identity: AuthIdentity) : SignInTriggerState()
    object SigningOut : SignInTriggerState()
    data class Error(val reason: AuthError) : SignInTriggerState()
}
```
Plus composable helper `rememberSignInTriggerState(currentUser: AuthIdentity?, isSigningIn: Boolean, isSigningOut: Boolean, lastError: AuthError?): SignInTriggerState`.
**Acceptance**: Compiles. Unit test: state combinations correctly derived (currentUser != null → SignedIn; isSigningIn → SigningIn; lastError != null && currentUser == null && !isSigningIn → Error).
**Dependencies**: requires T712, T710.
**Files**: `app/src/androidMain/kotlin/family/launcher/app/auth/ui/SignInTriggerState.kt`.

### T782 [US2] Implement `SignInTrigger` composable

**Trace**: FR-033; FR-036; clarification Q5, Q6, Q9; research.md §R7; elderly-friendly + localization-ui + ux-quality + state-management checklists.
**What**: Create file `SignInTrigger.kt`:
- Signature: `@Composable fun SignInTrigger(modifier: Modifier = Modifier, onSignedIn: (AuthIdentity) -> Unit = {}, onSignedOut: () -> Unit = {})`.
- Subscribes к `AuthProvider.currentUser` via `collectAsState(initial = null)`.
- `var isSigningIn by rememberSaveable { mutableStateOf(false) }`.
- `var lastError by remember { mutableStateOf<AuthError?>(null) }`.
- Derived state via T781 helper.
- UI structure per state:
  - **NotSignedIn**: `Button(onClick = startSignIn)` with text `R.string.auth_sign_in_button` (≥16sp, height ≥56dp); `Text(R.string.auth_sign_in_explanation, fontSize = 14.sp)` below.
  - **SigningIn**: same button + disabled + CircularProgressIndicator overlay.
  - **SignedIn**: `Text(stringResource(R.string.auth_signed_in_status, identity.email))` + `Button(onClick = startSignOut)` with text `R.string.auth_sign_out_button`.
  - **SigningOut**: SignedIn UI + disabled + progress.
  - **Error**: NotSignedIn UI + inline error text (red color + warning icon) ниже кнопки; `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`.
- `Modifier.semantics { contentDescription = stringResource(R.string.auth_a11y_sign_in_button) }` для кнопок.
- onClick callbacks launch via `coroutineScope.launch { authProvider.signIn() }` (caller scope; adapter has own internal scope per research §R2).
- Senior-safe baseline: tap targets ≥56dp, spacing 16dp, contrast ≥4.5:1 (handled via Material Theme).
**Acceptance**: Compiles. Manual rendering preview shows correct UI per state. Material Theme inheritance verified.
**Dependencies**: requires T780, T781, T716.
**Files**: `app/src/androidMain/kotlin/family/launcher/app/auth/ui/SignInTrigger.kt`.

### T783 [P] [US2] Write `SignInTriggerStateTest` (Compose StateRestorationTester)

**Trace**: FR-033; state-management CHK014; plan.md §"Test Strategy".
**What**: Compose UI tests:
- Rotation test: `StateRestorationTester` recreate → state preserved (`isSigningIn` через `rememberSaveable`).
- Locale change test: re-render с different `Configuration` → strings re-resolve.
- `fontScale=2.0` test: all elements remain readable, не truncated, tap targets ≥56dp.
- TalkBack content description verified via `onNodeWithContentDescription(...)`.
- Each of 5 states (NotSignedIn / SigningIn / SignedIn / SigningOut / Error) — assert correct UI elements visible.
**Acceptance**: All UI tests pass on `pixel_5_api_34`.
**Dependencies**: requires T782.
**Files**: `app/src/androidTest/kotlin/family/launcher/app/auth/ui/SignInTriggerStateTest.kt`.

### T784 [P] [US2] Take screenshots для 3 locales (EN / RU / DE) — localization-ui smoke

**Trace**: localization-ui CHK-UI-005; elderly-friendly CHK022.
**What**: Use skill `android-emulator`:
- Run app в RU locale → screenshot SignInTrigger NotSignedIn + SignedIn + Error states.
- Switch к EN locale → re-screenshot.
- Switch к DE locale (longest expansion) → re-screenshot.
- Verify: button label wraps to 2 lines if needed (longest «Войти в аккаунт» / «Mit Konto anmelden» / «Sign in to account»), no clipping, tap targets remain ≥56dp.
- Save screenshots в `specs/017-f4-auth-provider/screenshots/sign-in-trigger-{ru|en|de}-{state}.png`.
**Acceptance**: Screenshots committed. Manual review confirms layout robustness.
**Dependencies**: requires T782.
**Files**: `specs/017-f4-auth-provider/screenshots/`.

**Checkpoint**: SignInTrigger production-ready. UI tested across rotation, locale, fontScale, TalkBack.

---

## Phase 8 — Structured logging + Detekt rules (fitness functions)

**Goal**: Automated enforcement CLAUDE.md rule 2 ACL + no PII в логах + no Fake в release.

### T790 [P] [US3] Implement `AuthLog` structured logger

**Trace**: research.md §R8; HIGH-3 logging policy; security CHK004.
**What**: Create file `AuthLog.kt`:
```kotlin
internal object AuthLog {
    private const val TAG = "Auth"
    fun signInAttempt() { Log.i(TAG, "sign_in.attempt") }
    fun signInSuccess(emailPresent: Boolean, displayNamePresent: Boolean) { Log.i(TAG, "sign_in.success emailPresent=$emailPresent displayNamePresent=$displayNamePresent") }
    fun signInFailure(reason: AuthError) { Log.w(TAG, "sign_in.failure reason=${reason.categoryName()}") }
    fun signOut() { Log.i(TAG, "sign_out") }
    fun tokenRefreshAttempt() { Log.i(TAG, "token_refresh.attempt") }
    fun tokenRefreshSuccess() { Log.i(TAG, "token_refresh.success") }
    fun tokenRefreshFailure(reason: AuthError) { Log.w(TAG, "token_refresh.failure reason=${reason.categoryName()}") }
    fun sessionRestored(identityPresent: Boolean) { Log.i(TAG, "session.restored identityPresent=$identityPresent") }
    fun sessionCorrupted() { Log.w(TAG, "session.corrupted") }
    fun identityLinksLookup(outcome: String) { Log.i(TAG, "identity_links.lookup outcome=$outcome") }
    fun identityLinksFailure(reason: AuthError) { Log.w(TAG, "identity_links.failure reason=${reason.categoryName()}") }
    private fun AuthError.categoryName(): String = when (this) {
        AuthError.NetworkError -> "NetworkError"
        AuthError.Cancelled -> "Cancelled"
        AuthError.NoEmail -> "NoEmail"
        AuthError.ProviderUnavailable -> "ProviderUnavailable"
        is AuthError.Unknown -> "Unknown"
    }
}
```
**Acceptance**: Compiles. Methods exposed только category-based. No method accepts `AuthIdentity` / `SessionRecord` / `String token` directly.
**Dependencies**: requires T710.
**Files**: `app/src/androidMain/kotlin/family/launcher/app/auth/logging/AuthLog.kt`.

### T791 [US3] Write Detekt custom rule `NoVendorImportsInDomain`

**Trace**: SC-003; SC-010-a; FR-002, FR-027; plan.md §"Test Strategy" #5.
**What**: Implement Detekt rule в `config/detekt/auth-rules/`:
- Scans every Kotlin file в `core/domain/auth/` (recursively).
- For each `import` statement: FAIL if matches `com.google.firebase.*`, `com.google.android.libraries.identity.*`, `androidx.credentials.*`, `com.apple.*`.
- Also FAIL if file content (case-insensitive) contains words: `Firebase`, `OAuth`, `Apple`, `Phone`, `Email` (allowed in `AuthError.NoEmail` enum sealed name, override per name pattern).
**Acceptance**: Rule registered в `detekt.yml`. CI runs `./gradlew detekt` → passes. If someone adds Firebase import к domain — rule fails.
**Dependencies**: requires T716.
**Files**: `config/detekt/auth-rules/NoVendorImportsInDomain.kt`, `config/detekt/detekt.yml`.

### T792 [P] [US3] Write Detekt rule `NoVendorImportsInConsumers`

**Trace**: SC-010-b; FR-028; US 3 acceptance #1, #2.
**What**: Detekt rule:
- Scans Kotlin files в `core/config/`, `core/pairing/`, `app/sos/`, `app/photos/` (consumer-spec territories).
- FAIL on imports: `com.google.firebase.auth.*`, `com.google.android.libraries.identity.googleid.*`, `androidx.credentials.*`.
**Acceptance**: Rule registered. CI green. If F-5 / S-2 dev adds vendor import — rule fails.
**Dependencies**: requires T791.
**Files**: `config/detekt/auth-rules/NoVendorImportsInConsumers.kt`.

### T793 [P] [US3] Write Detekt rule `NoFakeInRelease`

**Trace**: SC-010-c; FR-019; FR-004.
**What**: Detekt rule:
- Scans Kotlin files в `app/src/main/` and `app/src/release/`.
- FAIL on imports: `family.launcher.domain.auth.FakeAuthAdapter`, `family.launcher.domain.auth.FakeSessionStore`.
**Acceptance**: Rule registered. If accidentally Fake imported в release — rule fails.
**Dependencies**: requires T720, T721.
**Files**: `config/detekt/auth-rules/NoFakeInRelease.kt`.

### T794 [P] [US3] Write Detekt rule `NoAnonymousAuth`

**Trace**: SC-010-d; FR-029.
**What**: Detekt rule:
- Scans every Kotlin file в project.
- FAIL on text match `signInAnonymously` or `FirebaseAuth.getInstance().signInAnonymously`.
**Acceptance**: Rule registered. Anonymous Firebase Auth removal verified.
**Dependencies**: none (independent rule).
**Files**: `config/detekt/auth-rules/NoAnonymousAuth.kt`.

### T795 [P] [US3] Write Detekt rule `OAuthScopeWhitelist`

**Trace**: HIGH-4 security; FR-032 OAuth Consent Screen integration; security CHK consideration 5.
**What**: Detekt rule:
- Scans Kotlin files в `app/src/androidMain/auth/`.
- Finds Credential Manager request строки (e.g., `.setServerClientId(...)`, `.setScopes(...)`).
- FAIL if any scope outside whitelist `["openid", "email", "profile"]`.
**Acceptance**: Rule registered. Future maintainer cannot accidentally add `calendar`, `contacts` scopes без обновления Privacy Policy + Data Safety.
**Dependencies**: requires T752.
**Files**: `config/detekt/auth-rules/OAuthScopeWhitelist.kt`.

### T796 [P] [US3] Write Detekt rule `NoPIIInAuthLog`

**Trace**: HIGH-3 logging; security CHK004; research.md §R8.
**What**: Detekt rule:
- Scans Kotlin files в `app/src/androidMain/auth/` and `app/src/androidMain/auth/ui/`.
- FAIL on `Log.*` calls (`Log.d`, `Log.i`, `Log.w`, `Log.e`) с parameter types: `AuthIdentity`, `SessionRecord`, `User`.
- Allowed: `Log.*` calls в `AuthLog.kt` file specifically.
- FAIL on `String` parameter если variable name contains: `email`, `displayName`, `token`, `refresh`, `sub`, `jwt`.
**Acceptance**: Rule registered. All auth-related logging goes через `AuthLog.X(...)` typed methods.
**Dependencies**: requires T790.
**Files**: `config/detekt/auth-rules/NoPIIInAuthLog.kt`.

### T797 [US3] Write Detekt rule `NoSessionRecordInConsumers`

**Trace**: clarification Q2; SC-001, SC-002 (indirect — SessionRecord internal).
**What**: Detekt rule:
- Scans Kotlin files outside `core/domain/auth/internal/` and `app/src/androidMain/auth/`.
- FAIL on imports: `family.launcher.domain.auth.internal.SessionRecord`, `family.launcher.domain.auth.internal.SessionStore`.
**Acceptance**: Rule registered. Consumer code cannot accidentally use SessionRecord (would leak Firebase JWT exposure).
**Dependencies**: requires T717, T718.
**Files**: `config/detekt/auth-rules/NoSessionRecordInConsumers.kt`.

### T798 [US3] Write Detekt rule `NoClientComputedSubscriptionActive`

**Trace**: FR-031; SC-014; tamper-resistance CHK-TAM-003, CHK-TAM-014.
**What**: Detekt rule:
- Scans every Kotlin file в project (except F-4 Fake adapter test which может simulate active for tests).
- FAIL on assignment `SubscriptionState.Active` или `SubscriptionState.Expired` — these MUST come from server-validated JWT, not client code.
- Allowed: `SubscriptionState.Unknown`, `LocalOnly`, `Trial` assignments (Trial — server-driven).
**Acceptance**: Rule registered. Client-side subscription bypass prevented.
**Dependencies**: requires T713.
**Files**: `config/detekt/auth-rules/NoClientComputedSubscriptionActive.kt`.

**Checkpoint**: 8 Detekt fitness functions enforce CLAUDE.md rules 1, 2, 4, 6, 8 automatedly. CI gate green required для merge.

---

## Phase 9 — Firestore Security Rules + tests

**Goal**: Server-side defense-in-depth для identity-links + users collections.

### T800 [US3] Update `firestore/firestore.rules` с identity-links + users rules

**Trace**: FR-016a; contract `firestore-security-rules.md`; HIGH-2; tamper-resistance CHK-TAM-016.
**What**: Append к existing `firestore/firestore.rules` (или create if not exists):
```javascript
// F-4 additions
match /identity-links/google/{providerAccountId} {
    allow read: if request.auth != null && request.auth.uid == providerAccountId;
    allow create: if request.auth != null
                  && request.auth.uid == providerAccountId
                  && !exists(/databases/$(database)/documents/identity-links/google/$(providerAccountId))
                  && request.resource.data.keys().hasOnly(['schemaVersion', 'stableId', 'createdAt'])
                  && request.resource.data.schemaVersion == 1
                  && request.resource.data.stableId is string
                  && request.resource.data.stableId.size() == 36
                  && request.resource.data.stableId.matches('^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')
                  && request.resource.data.createdAt == request.time;
    allow update, delete: if false;
}

match /users/{stableId} {
    allow read: if request.auth != null
                && exists(/databases/$(database)/documents/identity-links/google/$(request.auth.uid))
                && get(/databases/$(database)/documents/identity-links/google/$(request.auth.uid)).data.stableId == stableId;
    allow create: if request.auth != null
                  && existsAfter(/databases/$(database)/documents/identity-links/google/$(request.auth.uid))
                  && getAfter(/databases/$(database)/documents/identity-links/google/$(request.auth.uid)).data.stableId == stableId
                  && request.resource.data.keys().hasOnly(['schemaVersion', 'stableId', 'createdAt'])
                  && request.resource.data.schemaVersion == 1
                  && request.resource.data.stableId == stableId
                  && request.resource.data.createdAt == request.time;
    allow update, delete: if false;  // overridden by F-5 / S-8 spec'ями
}
```
**Acceptance**: `firebase deploy --only firestore:rules --dry-run` succeeds. Syntax verified.
**Dependencies**: requires T751.
**Files**: `firestore/firestore.rules`.

### T801 [US3] Write Firestore Rules unit tests

**Trace**: contract `firestore-security-rules.md` §"Tests"; HIGH-2; SC-010 (security testing).
**What**: Create JS test suite (using `@firebase/rules-unit-testing`) в `firestore-rules-tests/`:
- All ~10 test cases from contract: owner read OK, other read fail, create OK if not exists, no overwrites, no wrong sub, no extra fields, no update, no delete, users requires identity-link.
- `package.json` + `npm test` script.
- `firebase.json` lists rules file path.
**Acceptance**: `cd firestore-rules-tests && npm test` все 10 tests pass.
**Dependencies**: requires T800.
**Files**: `firestore-rules-tests/test/identity-links.test.js`, `firestore-rules-tests/test/users.test.js`, `firestore-rules-tests/package.json`, `firestore-rules-tests/firebase.json`.

### T802 [US3] Write `IdentityLinksRaceTest` (concurrent create race)

**Trace**: contract `identity-link-v1.md` §"Atomic create-or-lookup"; research.md §R9; failure-recovery CHK009.
**What**: Instrumentation test (uses Firestore Emulator):
- Setup 2 parallel coroutines с same Google `sub` claim.
- Both call `GoogleSignInAuthAdapter.lookupOrCreateIdentityLink(sub)` одновременно.
- Assert: both return **same UUID**.
- Assert: only one document created в Firestore (verify via Emulator inspection).
**Acceptance**: Test passes 100 iterations. Race resolved correctly (first wins, second retries и finds existing).
**Dependencies**: requires T751, T800.
**Files**: `app/src/androidTest/kotlin/family/launcher/app/auth/IdentityLinksRaceTest.kt`.

**Checkpoint**: Server-side defense complete. Spoofing impossible. Race conditions handled.

---

## Phase 10 — Documentation + cleanup

**Goal**: Update affected docs, server-roadmap entries, project backlog.

### T810 [P] Update `docs/dev/server-roadmap.md` с `SRV-AUTH-IDENTITY-001`

**Trace**: FR-016a; plan.md §"Required Context Review"; backend-substitution CHK012.
**What**: Add new entry к server-roadmap.md:
```markdown
### SRV-AUTH-IDENTITY-001 — Identity-links collection migration

**Trigger**: own-server cutover (Phase 2+, post-MVP).
**Why**: identity-links collection — authoritative source маппинга providerAccountId → stableId. Must migrate first to preserve UUID stability.
**Plan**: see specs/017-f4-auth-provider/research.md §R4.
**Effort estimate**: 1 week dev + 1 day operational migration window.
**Risk если skipped**: stableId UUIDs могут разойтись между client expectations и server state → broken delegations, broken config-sync.
```
**Acceptance**: Entry committed.
**Dependencies**: requires T751.
**Files**: `docs/dev/server-roadmap.md`.

### T811 [P] Update `docs/compliance/permissions-and-resource-budget.md` с F-4 delta

**Trace**: permissions-platform CHK022; plan.md §"Required Context Review".
**What**: Add F-4 row:
```markdown
| F-4 AuthProvider | None new runtime perms | INTERNET (existing) | EncryptedSharedPreferences excluded from backup | Zero scheduled background work | spec 017 |
```
**Acceptance**: Doc updated.
**Dependencies**: requires T743.
**Files**: `docs/compliance/permissions-and-resource-budget.md`.

### T812 [P] Update `docs/dev/project-backlog.md` AUTH-001 status

**Trace**: plan.md §"Required Context Review".
**What**: Move AUTH-001 от "Planned" → "In progress" (or status в проекте). Add cross-ref к `specs/017-f4-auth-provider/`.
**Acceptance**: Doc updated.
**Dependencies**: requires T762 (spec mostly implemented).
**Files**: `docs/dev/project-backlog.md`.

### T813 [P] Create `docs/dev/auth-setup.md` (one-time setup guide)

**Trace**: dev-experience CHK016; HIGH-1 (open item).
**What**: Create new doc consolidating Firebase setup steps from `quickstart.md`:
- Firebase project creation steps.
- SHA-1 fingerprints generation + добавление.
- OAuth Consent Screen configuration.
- Firebase Auth Emulator setup для local development.
- Common issues troubleshooting.
- Cross-reference к `quickstart.md` для test commands.
**Acceptance**: Doc readable by new developer in <5 minutes.
**Dependencies**: none (independent doc).
**Files**: `docs/dev/auth-setup.md`.

### T814 Update `app/google-services.json.template` (committed, sanitized)

**Trace**: dev-experience CHK017.
**What**: Create template файл с placeholder values for Firebase project IDs (committed в repo). Real `google-services.json` остаётся в `.gitignore`. Developer copies template и заполняет своими values.
**Acceptance**: Template exists. Real json gitignored (verify with `git check-ignore app/google-services.json`).
**Dependencies**: none.
**Files**: `app/google-services.json.template`, `.gitignore` (if not already ignores google-services.json).

### T815 Verify all spec FRs covered by tasks (cross-artifact trace)

**Trace**: spec.md §FR-001..FR-036.
**What**: Manual / scripted verification: each FR in spec.md mapped к at least one task in this file. Run procedure-cross-artifact-trace if available.
**Acceptance**: Output coverage table. 36/36 FRs covered.
**Dependencies**: requires all preceding tasks defined (this file complete).
**Files**: tasks.md (this) — verification, no new file.

**Checkpoint**: Docs synchronized. server-roadmap, compliance, backlog updated. Setup guide exists.

---

## Pre-release tasks (NOT blocking code merge — block production release к Phase 2 cloud features)

### T901 [admin] Configure Firebase Auth project (production)

**Trace**: FR-032; plan.md §"Rollout / Verification" Phase 2; quickstart.md "Pre-release checklist".
**What**: In Firebase Console:
- Create production Firebase project (if not exists).
- Enable Google Sign-In provider.
- Add Android app с production package name + SHA-1 fingerprint.
- Download `google-services.json` → secure storage (e.g., release CI secret).
**Acceptance**: Production Firebase project ready. SHA-1 verified.
**Dependencies**: none (admin task).
**Files**: external (Firebase Console + secure secret storage).

### T902 [admin] Setup OAuth Consent Screen + verification

**Trace**: FR-032; decision 2026-05-30/08; plan.md "Pre-release Phase 2".
**What**: In Google Cloud Console:
- Configure OAuth Consent Screen.
- User type: External.
- App name, support email.
- **Scopes**: только `openid`, `email`, `profile` (per Detekt OAuthScopeWhitelist rule).
- Submit for Google verification (1-2 weeks process).
**Acceptance**: Consent Screen verified by Google. Production sign-in works.
**Dependencies**: requires T901.
**Files**: external (Google Cloud Console).

### T903 [admin] Update Privacy Policy

**Trace**: FR-032; security CHK020.
**What**: Update project Privacy Policy:
- Section про data collected: email, displayName, profile photo URL (через Google Sign-In).
- Section про third-party sharing: Google (sign-in), Firebase (auth backend + Firestore).
- Section про data deletion right (cross-ref к S-6 spec когда ship'нется).
- Section про data retention.
**Acceptance**: Updated Privacy Policy published на product website. URL referenced в Play Store listing.
**Dependencies**: none (admin task).
**Files**: external (project website).

### T904 [admin] Prepare Play Console Data Safety form

**Trace**: FR-032; core-quality CHK012; security CHK021.
**What**: In Play Console Data Safety form:
- **Collected**: Email address, Name. Linked to user: yes. Purpose: app functionality (account, sync).
- **Shared with third parties**: Yes, with Google (authentication), Firebase (auth backend).
- **Encrypted in transit**: Yes (HTTPS via Firebase SDK).
- **User can request deletion**: Yes (will be implemented через S-6 spec).
**Acceptance**: Form filled, ready для submission with первой Phase 2 cloud feature release.
**Dependencies**: requires T901.
**Files**: external (Play Console).

### T905 [admin] Deploy Firestore Security Rules к production

**Trace**: T800; contract `firestore-security-rules.md` §"Deployment".
**What**: After T801 tests green в CI:
- `firebase use production`.
- `firebase deploy --only firestore:rules`.
- Verify deployed rules match committed: `firebase firestore:rules:get`.
**Acceptance**: Production Firestore rules updated. Verify via random spot-check (try to read other user's identity-link → should fail).
**Dependencies**: requires T800, T801.
**Files**: external (Firebase production).

### T906 [admin] Configure Crashlytics + Vitals dashboards

**Trace**: plan.md §"Rollout / Verification" Phase 3; dev-experience CHK018, CHK019.
**What**: Setup tracking categories:
- Crashlytics custom keys: `auth.last_error_reason`, `auth.session_present`.
- Vitals dashboard: track `auth.error.*` categories.
- Anomaly detection: alert if sign-in failure rate > 5%.
- ANR monitoring: trigger investigation если any ANR tagged with `Auth`.
**Acceptance**: Dashboards live. Test alert fires for synthetic failure.
**Dependencies**: requires T790.
**Files**: external (Firebase Console).

### T907 [admin] Manual smoke test на real device + real Google account

**Trace**: spec.md Local Test Path §"Cannot-test-locally gaps"; quickstart.md "Manual smoke".
**What**: На real Android device (not emulator):
- Install release APK.
- Wizard flow с real Google account: «Войти в аккаунт» → real Google bottom-sheet → real sign-in.
- Verify: Wizard recovery flow works (config restored if exists).
- Verify: signOut returns к local mode.
- Repeat для 3 scenarios: new account, existing account, Cancelled.
**Acceptance**: All 3 scenarios pass на real device. Findings documented.
**Dependencies**: requires T901, T902, T905, T762.
**Files**: smoke test report.

### T908 [admin] Verify backup exclusion via adb-restore

**Trace**: research.md §R10; T743; security CHK024.
**What**: On real device:
- Sign-in.
- Trigger backup: `adb shell bmgr backupnow @pm@ && adb shell bmgr backupnow family.launcher.app`.
- Factory reset device.
- Restore from backup: `adb shell bmgr restore`.
- Open app. **Expected**: user NOT signed in (session blob excluded from backup).
**Acceptance**: Manual test passes. Refresh token не leaked в backup.
**Dependencies**: requires T743, T901.
**Files**: smoke test report.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 0** (T701-T703): Setup. No dependencies.
- **Phase 1** (T710-T718): Domain types. Depends on Phase 0.
- **Phase 2** (T720-T723): Fake adapters. Depends on Phase 1.
- **Phase 3** (T730-T732): Wire format tests. Depends on Phase 1 (T717).
- **Phase 4** (T740-T743): EncryptedLocalSessionStore. Depends on Phase 1 (T717, T718).
- **Phase 5** (T750-T757): Google adapter. Depends on Phase 1, Phase 4.
- **Phase 6** (T760-T765): AuthAdapterSelector + DI. Depends on Phase 2, Phase 5.
- **Phase 7** (T780-T784): SignInTrigger UI. Depends on Phase 1, Phase 6 (DI).
- **Phase 8** (T790-T798): Detekt rules. Depends on Phase 1-7 (rules need targets to scan).
- **Phase 9** (T800-T802): Firestore Security Rules. Depends on Phase 5 (T751).
- **Phase 10** (T810-T815): Docs cleanup. Depends on completion of Phase 1-9.

### Parallel opportunities

- **Within Phase 1**: T710, T711, T712, T713, T714 parallel-safe (different files, no deps).
- **Within Phase 2**: T720, T721, T722 (after T720+T721), T723 parallel after T720.
- **Within Phase 3**: T730 [P], T731 + T732 after T730.
- **Within Phase 4**: T741, T742 parallel after T740. T743 parallel with T740.
- **Within Phase 5**: T750, T751 parallel (different functions). T753, T754, T755, T756, T757 parallel after T752.
- **Within Phase 7**: T780 [P] first; T781, T782 sequential; T783, T784 parallel after T782.
- **Within Phase 8**: T790 first. T791-T798 mostly parallel (different rule files).
- **Within Phase 10**: T810, T811, T812, T813, T814 all parallel (different docs).

### Pre-release tasks

- T901-T908 are **admin tasks**, not blocking code merge.
- T901 → T902 → T903, T904, T907 (sequential for Firebase setup chain).
- T905 needs T800, T801 (security rules tested before deploy).
- T906, T908 independent of each other but require core merge.

---

## Coverage table (FR → tasks trace)

| FR | Coverage tasks | Notes |
|----|----------------|-------|
| FR-001 | T710-T718 | Domain types в `core/domain/auth/` |
| FR-002 | T703, T791 | No vendor SDK в domain + Detekt enforcement |
| FR-003 | T740, T752, T761, T782 | `app/androidMain/auth/` content |
| FR-004 | T720, T721, T793 | FakeAdapter + Detekt enforcement |
| FR-005 | T752 | Inline TODOs в `GoogleSignInAuthAdapter` |
| FR-006 | T716 | AuthProvider port + invariants per contract |
| FR-007 | T712 | AuthIdentity (UUID + nullable email/displayName) |
| FR-008 | — | Снят per clarification Q4 (providerKind removed) |
| FR-009 | T710, T723 | AuthError sealed + exhaustiveness test |
| FR-010 | T714, T715 | User data class + IdentityKeys forward decl |
| FR-011 | T713, T798 | SubscriptionState + Detekt enforcement |
| FR-012 | T718 | SessionStore internal port |
| FR-013 | T717, T797 | SessionRecord internal + Detekt enforcement |
| FR-014 | T701, T703, T752 | Credential Manager API |
| FR-015 | T703, T752 | Firebase Auth exchange |
| FR-016 | T752 (NoEmail policy в adapter) | |
| FR-016a | T750, T751, T800 | Identity-links collection |
| FR-017 | T753 | Token refresh |
| FR-018 | T761 | AuthAdapterSelector |
| FR-019 | T762 | DI seam |
| FR-020 | T740, T741, T742, T743 | EncryptedLocalSessionStore |
| FR-021 | T717, T731 | Wire-format JSON schemaVersion=1 |
| FR-022 | T731, T732 | Roundtrip + backward-compat |
| FR-023 | T740, T741 | Corrupted blob → null |
| FR-024 | T720 | FakeAuthAdapter |
| FR-025 | T720 | Fake simulator test API |
| FR-026 | T721 | FakeSessionStore |
| FR-027 | T791 | Detekt no vendor names в domain |
| FR-028 | T792 | Detekt no vendor in consumers |
| FR-029 | T794 | Detekt no anonymous |
| FR-030 | T764 | LocalModeNoSignInTest |
| FR-031 | T713, T798 | SubscriptionState never client-Active |
| FR-032 | T901-T907 | Pre-release tasks (Privacy, OAuth, Data Safety) |
| FR-033 | T780, T781, T782, T783, T784 | SignInTrigger composable |
| FR-034 | — | Cross-references (declarative — verified via FR-029 anonymous removal + consumer-specs eventual existence) |
| FR-035 | T740 init, T752 init, T765 | Cold-start latency (async init + LatencyTest) |
| FR-036 | T780, T782, T783 | TalkBack content descriptions |

**Coverage**: 35 FRs in spec (FR-001..FR-036 minus removed FR-008), 35 covered. ✅

| SC | Coverage tasks |
|----|----------------|
| SC-001 | T791 (Detekt vendor isolation) |
| SC-002 | T792 (Detekt consumer isolation) |
| SC-003 | T791 (Detekt domain clean) |
| SC-004 | T764 (LocalModeNoSignInTest) |
| SC-005 | T756 (sign-in flow Firebase Emulator) |
| SC-006 | T753 (token refresh) |
| SC-007 | T742 (session persistence instrumentation) |
| SC-008 | T763 (ProviderSwapFitnessTest) |
| SC-009 | T731 (SessionRecord roundtrip) |
| SC-010 | T791-T798 (all 8 Detekt rules) |
| SC-011 | Deferred к S-2 spec (two-emulator pairing) |
| SC-012 | T901-T908 (pre-release timing) |
| SC-013 | T903 (Privacy Policy) |
| SC-014 | T798 (SubscriptionState Detekt) |

**Coverage**: 14 SCs, 14 covered (SC-011 deferred к S-2 — documented). ✅

| US | Coverage tasks |
|----|----------------|
| US 1 (Local mode forever) | T764 |
| US 2 (Wizard recovery sign-in) | T754, T756, T782, T783 |
| US 3 (Consumer без vendor knowledge) | T791, T792, T763 |
| US 4 (Token refresh) | T753 |
| US 5 (Session persistence) | T741, T742, T740 init, T732 |
| US 6 (Provider-swap fitness) | T763 |
| US 7 (Two-emulator pairing) | Deferred к S-2 |

**Coverage**: 6/7 US covered in F-4 directly; US 7 explicitly deferred к S-2 spec (P3 marker в spec). ✅

---

## TL;DR для не-разработчика

**В файле — 64 задачи для разработчика**, разбитых на **10 фаз**, плюс **8 admin-задач** для релиза (не блокируют слияние кода).

**Что произойдёт по фазам**:

1. **Фаза 0** (3 задачи): добавить новые библиотеки (Credential Manager, Firebase) в проект.
2. **Фаза 1** (9 задач): создать «чистые» типы данных в коде (AuthProvider, AuthIdentity, и т.д.) без слов Firebase/Google.
3. **Фаза 2** (4 задачи): сделать заглушки (FakeAuthAdapter) для тестов — теперь другие части проекта могут начинать писаться, не дожидаясь реальной интеграции.
4. **Фаза 3** (3 задачи): зафиксировать формат хранения сессии (версия 1) + тесты на «записал → прочитал → получил то же».
5. **Фаза 4** (4 задачи): реальное хранилище сессии в зашифрованном файле + исключение из автобэкапа.
6. **Фаза 5** (8 задач): реальный Google Sign-In: извлечение `sub` claim, поиск/создание UUID в Firestore, обмен на Firebase токен, обновление токена, защита от поворота экрана.
7. **Фаза 6** (6 задач): автоматический выбор адаптера (на Huawei = «недоступно», иначе Google), wiring всего в DI, негативные тесты «без интернета», тесты скорости запуска.
8. **Фаза 7** (5 задач): UI-компонент `SignInTrigger` (кнопка «Войти в аккаунт» + поясняющий текст) с 5 состояниями, локализация, поддержка TalkBack для слабовидящих, скриншоты в 3 языках.
9. **Фаза 8** (9 задач): 8 автоматических проверок Detekt — гарантия, что Firebase/Google не «утекут» в неправильные места кода + структурированное логирование без PII.
10. **Фаза 9** (3 задачи): правила безопасности Firestore (нельзя подменить чужой identity-link) + тесты + проверка гонки одновременного входа с двух устройств.
11. **Фаза 10** (6 задач): обновить документы — server-roadmap, compliance, инструкции для разработчиков, шаблон конфига Firebase.

**8 admin-задач**: настроить Firebase, пройти OAuth review (2 недели у Google), обновить Privacy Policy, заполнить Data Safety форму в Play Console, развернуть Firestore Rules в production, настроить Crashlytics, протестировать на реальном устройстве, проверить что бэкап не утаскивает токен.

**Параллельная работа**: задачи помечены `[P]` если их можно делать одновременно (разные файлы, нет зависимостей). Если бы было 3 разработчика — фаза 8 могла бы пройти за 1 день вместо 3.

**Покрытие**: 35 из 35 требований (FR) и 14 из 14 критериев успеха (SC) покрыты задачами. Один P3 пункт (связка двух эмуляторов через QR) отложен к спеке S-2 — там будет подробно.

**После tasks.md** — рекомендуется `/speckit.analyze` (финальный аудит): прогон всех чек-листов ещё раз, поиск «потерянных» задач, проверка целостности между spec/plan/tasks. Это **последний шаг** перед началом реального кодирования.
