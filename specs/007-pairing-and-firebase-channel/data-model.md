# Data Model: Pairing and Firebase Channel (spec 007)

**Generated**: 2026-05-11 by `speckit-plan` orchestrator.
**Scope**: domain entities + ports + wire-format типы.

---

## Domain entities (`:core/api/`)

### `OldIdentity` (`api/identity/`)

```kotlin
sealed interface Identity {
  val firebaseAuthUid: String
}

@JvmInline
value class OldIdentity(override val firebaseAuthUid: String) : Identity {
  companion object {
    fun anonymous(uid: String): OldIdentity = OldIdentity(uid)
  }
}
```

- Не путать с `oldDeviceId` (UUIDv4 из DataStore).
- Firebase Auth UID — короткоживущий; `oldDeviceId` — стабильный.

### `AdminIdentity` (`api/identity/`)

```kotlin
@JvmInline
value class AdminIdentity(override val firebaseAuthUid: String) : Identity
```

- Same shape as `OldIdentity`; разный role discriminator на DI-уровне (admin-mode/OLD-mode Koin scopes).

### `IdentityError` (`api/identity/`)

```kotlin
sealed interface IdentityError {
  data object NetworkUnavailable : IdentityError
  data object QuotaExceeded : IdentityError
  data class Unknown(val message: String) : IdentityError
}
```

### `PairingToken` (`api/pairing/`)

```kotlin
@JvmInline
value class PairingToken(val raw: String) {
  init {
    require(REGEX.matches(raw)) { "invalid token format: $raw" }
  }
  companion object {
    val REGEX = Regex("^[A-HJ-NP-Z2-9]{6}$")
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  // exclude 0, O, I, 1
    fun generate(random: Random = Random.Default): PairingToken =
      PairingToken(buildString { repeat(6) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } })
  }
}
```

- Alphabet (32 символа) исключает визуально схожие `0/O/I/1`.
- 6 chars → 32^6 = 1.07B вариантов.
- Single-use, TTL 5 минут.

### `TrustEdgeBootstrap` (`api/pairing/`) *[per «Reusable trust primitive» research.md §]*

Sealed result type для `PairingService.claim()`. В 007 — единственный subtype `LinkBootstrap`; будущие спеки добавят свои.

```kotlin
sealed interface TrustEdgeBootstrap {
  val edgeId: String
  val createdAt: Instant
}
```

### `Link` (`api/link/`) — implements TrustEdgeBootstrap

```kotlin
data class Link(
  val linkId: String,                    // opaque Firestore doc ID (OWD-4) — это edgeId
  val adminId: AdminIdentity,
  val oldDeviceId: String,               // UUIDv4 from DataStore
  val oldDeviceFirebaseUid: String,      // current Firebase Auth UID; меняется при reinstall (см. research.md)
  override val createdAt: Instant
) : TrustEdgeBootstrap {
  override val edgeId: String get() = linkId
}
```

### `LinkBootstrap` (`api/link/`)

Минимальный initial snapshot для `/links/{linkId}/state` при «Allow» на consent.

```kotlin
data class LinkBootstrap(
  val schemaVersion: Int = 1,
  val appliedAt: Instant,
  val presetId: String,                 // из DataStore текущий preset
  val fcmToken: String?                  // null если GMS отсутствует (C13)
)
```

**Полная схема state** — спек 008.

### `PairingType` (`api/pairing/`) *[per reusable primitive]*

Discriminator для `/pairings/{token}.pairingType` field — позволяет в одной коллекции хранить токены разных видов trust-pairing.

```kotlin
enum class PairingType {
  AdminOldLink,           // spec 007 — единственный текущий subtype
  // TrustedContact,      // spec 011 — future
  // CallTrustEdge,       // future jitsi spec
  // SubAdminLink,        // future multi-admin spec
  // DeviceReplacement,   // backlog config-portability
}
```

В wire-format `/pairings/{token}` `pairingType: String?` (default `"admin-old-link"` если отсутствует — backward-compat для существующих токенов).

### `PairingState` (`api/pairing/`)

```kotlin
sealed interface PairingState {
  data object Idle : PairingState
  data class WaitingForClaim(
    val token: PairingToken,
    val expiresAt: Instant
  ) : PairingState
  data class Claimed(val link: Link) : PairingState
  data object Expired : PairingState
  data object Revoked : PairingState
  data class Error(val cause: PairingError) : PairingState
}

sealed interface PairingError {
  data object TokenAlreadyClaimed : PairingError
  data object TokenExpired : PairingError
  data object TokenNotFound : PairingError
  data object NetworkUnavailable : PairingError
  data object PermissionDenied : PairingError
  data class Unknown(val message: String) : PairingError
}
```

### `DocPath` (`api/sync/`)

```kotlin
sealed interface DocPath {
  val rawPath: String

  data class Pairings(val token: PairingToken) : DocPath {
    override val rawPath = "pairings/${token.raw}"
  }
  data class Links(val linkId: String) : DocPath {
    override val rawPath = "links/$linkId"
    fun child(sub: String) = SubPath(this, sub)
  }
  data class LinkState(val linkId: String) : DocPath {
    override val rawPath = "links/$linkId/state/current"
  }
  data class LinkConfig(val linkId: String) : DocPath {
    override val rawPath = "links/$linkId/config/current"
  }
  data class LinkCapabilities(val linkId: String) : DocPath {
    override val rawPath = "links/$linkId/capabilities/current"
  }
  data class LinkHealth(val linkId: String) : DocPath {
    override val rawPath = "links/$linkId/health/current"
  }
  data class LinkCommand(val linkId: String, val cmdId: String) : DocPath {
    override val rawPath = "links/$linkId/commands/$cmdId"
  }
  data class Devices(val oldDeviceId: String) : DocPath {
    override val rawPath = "devices/$oldDeviceId"
  }
  data class SubPath(val parent: Links, val sub: String) : DocPath {
    override val rawPath = "${parent.rawPath}/$sub"
  }
}
```

- Заменяет raw strings; domain никогда не складывает path вручную.
- Все известные subcollection paths нужны для recursive-revoke (research.md).

### `DocSnapshot` (`api/sync/`)

```kotlin
data class DocSnapshot(
  val path: DocPath,
  val data: JsonElement,                 // kotlinx.serialization — не Map<String, Any?>
  val schemaVersion: Int,
  val updatedAt: Instant?,               // server-set, может быть null до первого set
  val isStale: Boolean = false           // true если adapter offline (C5)
)
```

### `BackendError` (`api/sync/`)

```kotlin
sealed interface BackendError {
  data object Offline : BackendError
  data object PermissionDenied : BackendError
  data object NotFound : BackendError
  data class TransactionConflict(val message: String) : BackendError
  data object Expired : BackendError                          // для pairing token
  data class Unknown(val message: String) : BackendError
}
```

- Все `FirebaseFirestoreException` преобразуются в эти; ни одна Firebase exception не пересекает границу `androidMain/adapters → commonMain` (FR-013).

### `PushPayload` (`api/push/`) *[per C14]*

```kotlin
data class PushPayload(
  val schemaVersion: Int = 1,
  val type: PushType,
  val linkId: String,
  val extra: JsonObject? = null         // type-specific extra data (см. fcm-payload.md)
)

sealed interface PushType {
  data object ConfigChanged : PushType { override fun toString() = "config-changed" }
  data object CommandIssued : PushType { override fun toString() = "command-issued" }
  data object Revoke : PushType { override fun toString() = "revoke" }
}
```

---

## Ports (`:core/api/`)

### `RemoteSyncBackend` (`api/sync/`)

```kotlin
interface RemoteSyncBackend {
  suspend fun writeDoc(path: DocPath, data: JsonElement, schemaVersion: Int): Result<Unit, BackendError>
  suspend fun readDoc(path: DocPath): Result<DocSnapshot?, BackendError>           // null = not found
  suspend fun deleteDoc(path: DocPath): Result<Unit, BackendError>
  fun observe(path: DocPath): Flow<Result<DocSnapshot?, BackendError>>
  suspend fun <T> runTransaction(block: suspend TransactionScope.() -> T): Result<T, BackendError>
  suspend fun dispose()
}

interface TransactionScope {
  suspend fun get(path: DocPath): DocSnapshot?
  suspend fun set(path: DocPath, data: JsonElement, schemaVersion: Int)
  suspend fun delete(path: DocPath)
}
```

### `IdentityProvider` (`api/identity/`)

```kotlin
interface IdentityProvider {
  suspend fun signInAnonymous(): Result<Identity, IdentityError>
  fun currentIdentity(): Identity?
  suspend fun signOut()
}
```

### `PushSender` (`api/push/`) *[per C14]*

```kotlin
interface PushSender {
  /**
   * Triggers a push notification for the given link.
   * Caller (admin app) invokes after a successful Firestore write.
   *
   * Implementations:
   *   - WorkerPushSender (androidMain, realBackend): HTTPS call to Cloudflare Worker
   *   - FakePushSender (commonTest): counter increment, no network
   */
  suspend fun notify(linkId: String, type: PushType, extra: JsonObject? = null): Result<Unit, PushError>
}

sealed interface PushError {
  data object NetworkUnavailable : PushError
  data object Unauthorized : PushError                  // ID-token invalid / uid != adminId
  data object RateLimited : PushError                   // Worker 429
  data class WorkerError(val code: Int, val message: String) : PushError  // 5xx
  data class Unknown(val message: String) : PushError
}
```

### `PushReceiver` (`api/push/`) *[per C14]*

```kotlin
interface PushReceiver {
  /**
   * Invoked by FirebaseMessagingService on incoming data-message.
   * Implementations route by type:
   *   - ConfigChanged → read /links/{linkId}/config, log received
   *   - CommandIssued → read /links/{linkId}/commands/{cmdId}, log (handler in spec 009)
   *   - Revoke → admin-side: refresh paired-devices list
   */
  suspend fun onPush(payload: PushPayload)
}
```

### `LinkRegistry` (`api/link/`)

```kotlin
interface LinkRegistry {
  fun currentLink(): Flow<Link?>                       // null если не paired
  suspend fun activate(linkId: String): Result<Link, BackendError>
  suspend fun revoke(): Result<Unit, BackendError>     // subscribes/unsubscribes from FCM topic internally
}
```

---

## Domain orchestrator (`api/pairing/`)

### `PairingService` (pure domain — no port, no adapter)

```kotlin
class PairingService(
  private val backend: RemoteSyncBackend,
  private val identity: IdentityProvider,
  private val deviceId: DeviceIdProvider,                 // wraps DataStore (oldDeviceId)
  private val linkRegistry: LinkRegistry,
  private val pushSender: PushSender,                     // for admin-side post-action
  private val clock: Clock,
  private val random: Random
) {
  fun state(): Flow<PairingState>

  // OLD-side
  suspend fun startPairingAsOld(): Result<PairingToken, PairingError>
  suspend fun cancelPairingAsOld(): Result<Unit, PairingError>
  suspend fun confirmConsentAsOld(): Result<Link, PairingError>     // → linkRegistry.activate + FCM subscribe
  suspend fun declineConsentAsOld(): Result<Unit, PairingError>

  // Admin-side
  suspend fun claimAsAdmin(token: PairingToken): Result<TrustEdgeBootstrap, PairingError>
  // ↑ возвращает sealed TrustEdgeBootstrap (не Link напрямую) per reusable trust primitive — research.md §
  // В 007 единственный result subtype — Link (admin↔OLD).
  // Будущие спеки (011 контакты, future звонки, multi-admin) добавят свои TrustEdgeBootstrap subtypes без правок PairingService.
}
```

---

## Relationships

```text
OldIdentity, AdminIdentity (value classes, opaque)
       │
       ├──► Link.adminId / Link.oldDeviceFirebaseUid (ids only — no nested in wire)
       │
PairingToken (single-use)
       │
       └──► /pairings/{token} doc
                │
                └─(transaction claim)──► /links/{linkId} doc
                                              │
                                              ├──► /state (created on consent.allow)
                                              ├──► /config (created in spec 008, push consumer in 007)
                                              ├──► /capabilities (созданы в спеке 008/006-export)
                                              ├──► /health (спек 008/006-export)
                                              └──► /commands/{cmdId} (спек 009)

PairingService (pure domain)
       ├──uses──► RemoteSyncBackend (port)
       │                ├──impl──► FirebaseRemoteSyncBackend (androidMain)
       │                └──impl──► FakeRemoteSyncBackend (commonTest)
       ├──uses──► IdentityProvider (port)
       │                ├──impl──► FirebaseIdentityProvider
       │                └──impl──► FakeIdentityProvider
       ├──uses──► LinkRegistry (port)
       │                ├──impl──► FirestoreLinkRegistry (androidMain)
       │                └──impl──► FakeLinkRegistry (commonTest)
       └──uses──► PushSender (port)
                        ├──impl──► WorkerPushSender (androidMain)
                        └──impl──► FakePushSender (commonTest)

PushReceiver (port)
       ├──impl──► LauncherFirebaseMessagingService (androidMain, realBackend)
       └──impl──► FakePushReceiver (commonTest)
```

---

## Persistence (DataStore Preferences)

### `com.launcher.pairing.identity_v1`

JSON:
```json
{
  "schemaVersion": 1,
  "oldDeviceId": "0c8e3a5e-1e7c-4f7b-9a1d-2b3c4d5e6f7a",
  "lastLinkId": "abc123XYZ",
  "fcmTokenLastSeen": "fAk3FcmT0k3n..."
}
```

- Generated on first launch, **never deleted** (даже при revoke — нужен для следующего pairing).
- `lastLinkId` обновляется при activate; null при revoke.
- `fcmTokenLastSeen` — для detect rotation (FR-017).

### Firebase Auth UID (Firestore SDK)

- Управляется Firebase SDK; persistent across launches пока user не отвязал.
- При reinstall — новый UID; `oldDeviceId` в DataStore выживает только если backup/restore включён (Android Auto Backup); по умолчанию reset → новый pairing.

**TODO в `IdentityCache.kt`**: «при добавлении named auth (OWD-2) — linkWithCredential сохранит UID; oldDeviceId как stable id остаётся».

---

## Что НЕ моделируется в этом спеке

- **`LinkConfig`** (полная схема раскладки) — спек 008.
- **`Capability`** / **`HealthSnapshot`** — уже есть в `:core/api/capability/`, `:core/api/health/` из спека 006; экспорт в Firestore — спек 008.
- **`Command`** business types (open Play Store etc.) — спек 009; в 007 только sealed-stub `PushType.CommandIssued`.
- **`PrivateMedia`** (e2e photos) — спек 011.
- **`Contact`** — спек 011.
- **iOS Identity types** — отдельный спек.

---

<!-- novice summary -->

## TL;DR для новичка

Этот документ — **«какие классы и интерфейсы появятся в коде»** для спека 007.

**Главные группы**:

1. **Идентичности** (`OldIdentity`, `AdminIdentity`) — обёртки над Firebase Auth UID. Domain-типы, без зависимости от SDK.

2. **Pairing-токен** (`PairingToken`) — 6 символов, alphabet без `0/O/I/1`, single-use, TTL 5 мин.

3. **Link** — «связь admin ↔ OLD». Поля: `linkId` (random), `adminId`, `oldDeviceId`, `createdAt`. Хранится в `/links/{linkId}` Firestore.

4. **DocPath, DocSnapshot, BackendError** — доменные обёртки чтобы код в `:core` **никогда не видел Firebase-типы напрямую**. Если завтра меняем backend на Supabase — этот код не правится.

5. **5 портов** (interfaces):
   - `RemoteSyncBackend` — read/write/observe Firestore-документов.
   - `IdentityProvider` — anonymous sign-in.
   - `PushSender` — отправить push (вызывает Cloudflare Worker).
   - `PushReceiver` — обработать входящий push.
   - `LinkRegistry` — текущая связь + revoke.
   У каждого порта — **2 реализации**: Firebase/Worker для production, Fake для тестов.

6. **`PairingService`** — главная domain-логика. Чистая Kotlin функция, без Android-зависимостей. Принимает все 5 портов через DI.

**Что хранится локально (DataStore)**:
- `oldDeviceId` — наш стабильный UUID, не теряется между запусками.
- `lastLinkId` — текущая связь, очищается при revoke.
- `fcmTokenLastSeen` — для отслеживания ротации Google'ом.

**Что НЕ моделируется в 007** — раскладка (config), команды (commands), приватные медиа, контакты. Это будущие спеки 008/009/011.
