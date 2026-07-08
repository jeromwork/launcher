# Client Requirements — что добавить в launcher для zero-knowledge сервера

> **⚠️ ASSUMPTION-LEVEL SKETCH (reviewed 2026-07-08 in TASK-57)**. Этот документ — draft уровня «direction», не «finalised specification». C1-C20 client components — sketch shape, not verified port contracts. Effort estimates (Low/Medium/High) — best-guess, real numbers появятся в момент конкретной feature-task'и. Deep validation каждого component'а произойдёт когда соответствующая feature-task (TASK-6 recovery → C18, TASK-27 messenger → C2/C3/C8, TASK-102 → C20, etc.) берётся в работу через skill [`checklist-zero-knowledge-server`](../../.claude/skills/checklist-zero-knowledge-server/SKILL.md). Открытые вопросы (5 штук в секции «Открытые вопросы» ниже + пересечения с server-requirements) вынесены в [`docs/dev/server-log.md` Part B](server-log.md) как Q-13..Q-16 (multi-device / group size / atomic keyring / self-state backup) + перекрёстные Q-2 (push 4KB). TASK-57 review не переписал этот документ — только промаркировал assumption-level и вынес questions в накопитель.
>
> **Не путать с**:
> - [`docs/dev/server-requirements.md`](server-requirements.md) — парный server-side sketch (тоже assumption-level).
> - [`docs/dev/server-log.md`](server-log.md) — растущий накопитель confirmed patterns + open questions + contradictions.
> - [`docs/architecture/client-android.md`](../architecture/client-android.md) — current-state architecture snapshot (skeleton).

**Назначение**: парный документ к [server-requirements.md](server-requirements.md). Перенос логики со «smart server» на «sealed server» означает, что **клиент берёт на себя** то, что раньше делал сервер. Этот файл — список **delta'ов** для launcher-клиента.

**Принцип**: zero-knowledge сервер работает только если клиент достаточно умный. Сервер — тупой storage; клиент — координатор всех бизнес-операций.

**Snapshot date**: 2026-06-26 (initial) / 2026-07-08 (assumption-level review TASK-57).

**Связь**:
- [server-requirements.md](server-requirements.md) — то, что сервер делает (минимум).
- Этот файл — то, что **клиент** должен уметь сверху минимума.

---

## Что у клиента **уже есть** (из текущей кодовой базы)

Не повторять, не строить заново — использовать существующее:

- **cryptokit.crypto.api**: AeadCipher, AsymmetricCrypto, KeyDerivation, KeyEscrow, KeyRotation, PasswordHash, RandomSource, SecureKeyStore. ✅
- **cryptokit.pairing.api**: DeviceIdentity, DeviceKeyPair, EncryptedEnvelope, Recipient, PublicKey, SigningPublicKey, EncryptedMediaStorage, DeviceIdentityRepository. ✅
- **family.keys.api**: Envelope, EnvelopeBootstrap, ConfigSaver, RootKey, RootKeyManager, RecoveryKeyVault, RecoveryVaultBlob, PassphraseAttemptCounter, RemoteStorage, SchemaVersionMemory. ✅
- libsodium через cryptokit-libsodium стопку (XChaCha20-Poly1305, X25519, Ed25519, Argon2id, HKDF). ✅
- Wire-format с `schemaVersion`, `@SerialName` audit (TASK-51). ✅

Это foundation. **Новый delta** ниже.

---

## C1. Namespace abstraction

Заменяет: linkId, userUid-keyed storage paths.

**Что нужно добавить**:

```kotlin
// core/storage/api/Namespace.kt
package launcher.storage.api

data class NamespaceId(val value: ByteArray)        // opaque UUID
data class NamespaceOwnerKey(
    val signingKeyPair: DeviceSigningKeyPair         // Ed25519, immutable после создания
)

interface NamespaceRegistry {
    /** Создаёт новый namespace на сервере, возвращает opaque ID + signing key. */
    suspend fun create(): Namespace

    /** Список namespaces, которыми этот клиент владеет — хранится локально, не на сервере. */
    suspend fun listOwned(): List<NamespaceId>

    /** Запоминает чужой namespace, к которому нас «пригласили» (через QR pairing). */
    suspend fun rememberShared(nsId: NamespaceId, theirSigningPubKey: SigningPublicKey)

    suspend fun delete(ns: Namespace)
}

data class Namespace(
    val id: NamespaceId,
    val ownerKey: NamespaceOwnerKey
)
```

**Что клиент берёт на себя**:
- Помнит свои namespace IDs локально (DataStore / SQLDelight).
- Помнит chuжие namespaces, в которые приглашён (после pairing).
- Backup'ит свои namespace IDs в recovery vault (иначе при reinstall потеряны).
- Восстанавливает namespaces при recovery flow.

**Эквивалент в server-requirements**: S1 namespace lifecycle.

---

## C2. Group abstraction поверх namespace

Раньше «группа» = `linkId` на сервере. Теперь группа = **namespace + keyring blob внутри namespace**.

**Что нужно добавить**:

```kotlin
// core/group/api/Group.kt
package launcher.group.api

data class GroupId(val namespaceId: NamespaceId)    // group = opaque namespace
data class MemberId(val lookupId: ByteArray)         // opaque, не привязан к userUid

interface GroupCoordinator {
    /** Создаёт новую группу: namespace на сервере + keyring blob с одним участником (self). */
    suspend fun createGroup(): Group

    /** Добавляет нового участника: обновляет keyring (wrap groupKey под их pub-key), signed write. */
    suspend fun addMember(group: Group, theirPubKey: PublicKey, theirSigningPubKey: SigningPublicKey)

    /** Удаляет участника: ротирует groupKey, перешифровывает все blob'ы группы под новый key, signed write нового keyring'а. */
    suspend fun removeMember(group: Group, memberId: MemberId)

    /** Возвращает member'ов группы (читает keyring, расшифровывает своим priv key). */
    suspend fun listMembers(group: Group): List<MemberId>

    /** Текущий groupKey (расшифрован при open группы). */
    suspend fun currentGroupKey(group: Group): ContentEncryptionKey
}

/**
 * Keyring blob — TopLevel в группе, key = "_keyring".
 *
 * Структура (после расшифровки):
 * {
 *   version: int,                    // optimistic locking
 *   groupKeyEpoch: int,              // bumped при removal (forward unsharing)
 *   members: [
 *     {
 *       memberId: opaque,
 *       theirEncryptionPubKey: bytes,
 *       theirSigningPubKey: bytes,
 *       wrappedGroupKey: bytes,      // groupKey encrypted под theirEncryptionPubKey (X25519 sealed box)
 *       wrappedTokenId: bytes,       // their push tokenId encrypted под groupKey (для discovery)
 *     }
 *   ]
 * }
 */
```

**Что клиент берёт на себя**:
- Membership management (add / remove / list).
- Forward unsharing (rotate groupKey + re-encrypt всех blob'ов при removal).
- Conflict resolution (если два member'а одновременно edit'ят keyring — optimistic locking + retry).

**Эквивалент в server-requirements**: ничего на сервере про группы. Сервер видит только namespace + signed writes.

**Industry reference**: Signal Sender Keys distribution pattern.

---

## C3. Encrypted push payload + dispatching

Раньше сервер видел `eventType` и dispatch'ил per-event handler'ом. Теперь — клиент сам.

**Что нужно добавить**:

```kotlin
// core/push/api/PushPayload.kt
package launcher.push.api

@Serializable
@SerialName("PushPayload")
data class PushPayload(
    val schemaVersion: Int,
    val eventType: String,                          // "config-updated", "sos-triggered", etc.
    val nonce: ByteArray,                            // для replay protection
    val sentAt: Long,                                // unix millis
    val groupId: GroupId,                            // в какой группе
    val data: Map<String, JsonElement>               // event-specific fields
)

interface PushSender {
    /**
     * Отправить push группе:
     * 1. Открыть keyring группы.
     * 2. Достать tokenIds всех member'ов (кроме self).
     * 3. Зашифровать PushPayload под groupKey.
     * 4. POST /push с targetTokens + ciphertext.
     */
    suspend fun sendToGroup(group: Group, payload: PushPayload)
}

interface PushReceiver {
    /**
     * При onMessageReceived (FCM):
     * 1. Получить opaque ciphertext.
     * 2. Найти groupId (из notification metadata).
     * 3. Расшифровать под groupKey.
     * 4. Проверить nonce (replay protection — dedupe last 100 nonces).
     * 5. Dispatch к локальному handler'у по eventType.
     */
    suspend fun onReceive(opaqueCiphertext: ByteArray): PushPayload?
}

interface PushHandlerRegistry {
    fun register(eventType: String, handler: PushHandler)
    fun handlerFor(eventType: String): PushHandler?
}
```

**Что клиент берёт на себя**:
- Encryption / decryption payload'ов.
- Event type registry (на клиенте, был на сервере).
- Replay protection (nonce + dedup last 100).
- Routing к event-specific handler'у.

**Эквивалент в server-requirements**: S2 push delivery — server forward'ит opaque ciphertext, не понимая eventType.

**Critical**: groupId в FCM notification metadata должен быть **opaque** (UUID), чтобы FCM не leak'ил «config-updated for group X».

---

## C4. PushTokenDirectory client + token freshness

**Что нужно добавить**:

```kotlin
// core/push/api/PushTokenManager.kt
package launcher.push.api

data class TokenId(val value: ByteArray)            // opaque UUID

interface PushTokenManager {
    /** Регистрирует свой FCM token на сервере с opaque tokenId. */
    suspend fun registerSelfToken(): TokenId

    /** Обновляет свой FCM token (при reinstall / token refresh). */
    suspend fun refreshSelfToken()

    /** При join в группу — публикует свой tokenId в keyring группы. */
    suspend fun publishTokenToGroup(group: Group, tokenId: TokenId)

    /** При app open — проверяет, что свой tokenId в keyring каждой группы соответствует свежему FCM token. */
    suspend fun reconcileGroupKeyrings(groups: List<Group>)
}
```

**Что клиент берёт на себя**:
- FCM token lifecycle (subscribe FCM token refresh callback).
- Publish своего tokenId в каждую группу при join.
- Reconcile при app open (если token обновился — записать в keyring всех групп).

**Эквивалент в server-requirements**: D2 push token directory — server хранит `tokenId → fcmToken` без знания связей с группами.

---

## C5. ConfigHistory rotation (client-side)

Раньше сервер делал `cron retention: cleanup snapshots > 10`. Теперь — клиент.

**Что нужно добавить**:

```kotlin
// core/config/api/ConfigHistoryManager.kt
package launcher.config.api

interface ConfigHistoryManager {
    /**
     * При write нового config'а:
     * 1. PUT нового config blob'а с opaque key (например, timestamp-based).
     * 2. LIST все config blobs в namespace.
     * 3. Если > N (например, 10) — DELETE старейшие.
     */
    suspend fun appendConfig(group: Group, encrypted: ByteArray)

    suspend fun listHistory(group: Group): List<ConfigSnapshot>
    suspend fun rollback(group: Group, snapshotId: SnapshotId)
}
```

**Что клиент берёт на себя**: history rotation (раньше cron на сервере).

**Эквивалент в server-requirements**: S0 generic blob storage, server retention только по TTL header'у.

---

## C6. Schema transformers (client-side)

Раньше сервер делал `vN → vCurrent` chain при чтении. Теперь — клиент.

**Что нужно добавить**:

```kotlin
// core/config/api/SchemaTransformer.kt
package launcher.config.api

interface SchemaTransformer<T> {
    fun fromVersion(): Int
    fun toVersion(): Int
    fun transform(blob: JsonElement): JsonElement
}

interface SchemaTransformerChain<T> {
    fun register(transformer: SchemaTransformer<T>)

    /**
     * При чтении blob'а:
     * 1. Decrypt → JsonElement.
     * 2. Прочитать schemaVersion.
     * 3. Apply chain transformers до currentVersion.
     */
    fun transformToLatest(blob: JsonElement): T
}
```

**Что клиент берёт на себя**: lazy schema migration (раньше серверная).

**Эквивалент в server-requirements**: server-side schema transformers удалены.

---

## C7. ContactRefcount client-side (или вообще выкинуть)

Раньше сервер делал refcount при добавлении/удалении ссылки из config. Теперь:

**Вариант A — client GC**:

```kotlin
// core/contacts/api/SharedContactsGc.kt

interface SharedContactsGc {
    /**
     * Раз в N месяцев (или при manual triggering) сканирует config + shared contacts blob,
     * удаляет contacts без активных ссылок.
     */
    suspend fun collectGarbage(group: Group)
}
```

**Вариант B — не делать GC вообще**. Места дёшево, blob'ы маленькие. Удалить при `removeMember` группы.

**Я бы взял Вариант B** — меньше кода. GC — overkill для пожилых семей.

---

## C8. Forward unsharing logic

Раньше сервер coordinate'ил re-encryption. Теперь клиент:

```kotlin
// core/group/api/ForwardUnsharing.kt

interface ForwardUnsharingCoordinator {
    /**
     * При removeMember:
     * 1. Сгенерить новый groupKey (epoch + 1).
     * 2. Обновить keyring: убрать removed member'а, перешифровать groupKey под pub-key оставшихся.
     * 3. Re-encrypt каждый blob группы под новый groupKey:
     *    - LIST blob'ов.
     *    - Для каждого: GET → decrypt старым groupKey → encrypt новым → PUT новый blob → DELETE старый.
     * 4. Signed write нового keyring'а.
     *
     * Atomic guarantee: если упал между шагами 1 и 4 — old keyring всё ещё валиден, removed member продолжает иметь доступ
     * (acceptable — он уже видел plaintext до этого). Retry continue безопасно.
     */
    suspend fun unshareWithMember(group: Group, removedMemberId: MemberId)
}
```

**Что клиент берёт на себя**: всё. Сервер только хранит signed writes новой версии keyring + новых blob'ов.

---

## C9. AppVersionCompatibility check (client-side)

Раньше сервер reject'ил writes если `managedAppVersion < required`. Теперь:

**Подход**: каждый blob внутри plaintext payload содержит `minReadAppVersion`. Reader при decrypt → проверяет → если своя версия меньше → отказывается читать, показывает «обнови приложение».

**Не нужен отдельный port** — это просто проверка в payload schema.

---

## C10. AuditLog encrypted blob

Раньше сервер делал audit log. Теперь — клиент пишет в свой namespace.

```kotlin
// core/audit/api/AuditLogger.kt

interface AuditLogger {
    /**
     * Append-only encrypted log в opaque blob key (например, "audit/{timestamp}-{random}").
     * Каждая запись содержит:
     * - timestamp
     * - operation (config-edit, member-add, member-remove, etc.)
     * - prevHash (hash chain для tamper-evident)
     */
    suspend fun log(group: Group, operation: AuditOperation)

    suspend fun read(group: Group, from: Long, to: Long): List<AuditEntry>
}
```

**Что клиент берёт на себя**: всё. Server хранит как обычные opaque blob'ы.

---

## C11. AlgorithmMigration coordinator (client-side)

Раньше — server-triggered batch job. Теперь:

```kotlin
// core/keys/api/AlgorithmMigrator.kt

interface AlgorithmMigrator {
    /**
     * При open recovery vault:
     * 1. Decrypt vault → видит algorithm version.
     * 2. Если < current — derive new vault под current algorithm/params.
     * 3. PUT новый vault.
     *
     * При open envelope blob'а:
     * 1. Decrypt → видит algorithm.
     * 2. Если < current — re-encrypt под current.
     * 3. PUT новый blob, DELETE старый.
     */
    suspend fun migrateOnOpen(blob: EncryptedBlob, currentAlgorithm: AlgorithmVersion): EncryptedBlob
}
```

**Что клиент берёт на себя**: migration coordination. Сервер не знает algorithm.

---

## C12. GDPR export — client side

Раньше — `GET /users/{uid}/export` на сервере. Теперь:

```kotlin
// core/gdpr/api/UserDataExporter.kt

interface UserDataExporter {
    /**
     * Скачать все blob'ы из всех своих namespaces.
     * Decrypt каждый под соответствующий groupKey / personal key.
     * Сохранить в JSON файл локально.
     */
    suspend fun exportAll(): File
}
```

**Что клиент берёт на себя**: всё. Server только cascade delete namespace по запросу (`DELETE /namespaces/{id}`).

---

## C13. HealthCritical → client-driven push

Раньше — server listen на `/health` → push admin. Теперь:

```kotlin
// core/health/api/HealthMonitor.kt

interface HealthMonitor {
    /**
     * Local health check job (WorkManager).
     * При detect Critical transition:
     * 1. Append HealthEvent в health blob группы (encrypted).
     * 2. Push notification через PushSender.sendToGroup(group, PushPayload("health-critical", data)).
     */
    suspend fun checkAndNotify(group: Group)
}
```

**Что клиент берёт на себя**: detection + push triggering.

---

## C14. SOS trigger — client-driven push

Аналогично HealthCritical — client triggers push при SOS button press.

---

## C15. SensorIngest — client-driven blob writes

Раньше — `POST /links/{linkId}/sensors/{kind}` на сервере. Теперь:

```kotlin
interface SensorDataPublisher {
    /**
     * Local sensor data (wearable / smart-home):
     * 1. Encrypt time-series chunk под groupKey.
     * 2. PUT в opaque blob (key = "sensors/{kind}/{timestamp}").
     * 3. Reader (admin) скачает LIST + GET периодически.
     */
    suspend fun publish(group: Group, kind: SensorKind, data: TimeSeriesChunk)
}
```

**Что клиент берёт на себя**: всё. Server хранит opaque blob'ы.

---

## C16. Replay protection

Каждый push payload содержит `nonce` (random bytes). Receiver:
- Хранит local set из last 100 nonces per sender.
- При receive: nonce in set → drop как replay.

```kotlin
// core/push/api/ReplayProtection.kt

interface ReplayProtection {
    fun isReplay(senderPubKey: SigningPublicKey, nonce: ByteArray): Boolean
    fun remember(senderPubKey: SigningPublicKey, nonce: ByteArray)
}
```

---

## C17. SignatureSigner для signed writes

Каждый write в Tier 0 endpoint требует Ed25519 подписи. Клиент:

```kotlin
// core/storage/api/SignedWriter.kt

interface SignedWriter {
    suspend fun put(
        ns: Namespace,
        key: ByteArray,
        ciphertext: ByteArray,
        version: Int
    ): Result<Unit>
    // Внутри: sign (nsId|key|version|ciphertext) с ns.ownerKey.signingKeyPair → PUT с X-Sig header.
}
```

**Что клиент берёт на себя**: signature generation на каждый write.

---

## C18. RecoveryVaultBackup своих opaque IDs

При reinstall клиент теряет local state (NamespaceIds, MemberIds, GroupKeys). Нужен **recovery flow**:

```kotlin
// core/keys/api/SelfStateBackup.kt

interface SelfStateBackup {
    /**
     * Содержит:
     * - List<NamespaceId> своих namespaces.
     * - List<(GroupId, theirSigningPubKey)> чужих групп.
     * - Recovery vault для root key.
     *
     * Хранится в зашифрованном виде в recovery vault (T2 на сервере).
     * Восстанавливается при recovery flow.
     */
    suspend fun backup(): EncryptedBackup
    suspend fun restore(passphrase: String): SelfState
}
```

**Critical**: без этого после reinstall клиент **не знает** какие namespaces ему принадлежат.

---

## C19. RetryWithExponentialBackoff на 503 (sealed state)

При sealed state сервер отвечает 503 + Retry-After. Клиент:

```kotlin
// core/network/api/SealedAwareClient.kt

interface SealedAwareClient {
    /**
     * При 503: parse Retry-After, exponential backoff.
     * При persistent sealed state > 5 min: показать user'у «сервер недоступен».
     */
}
```

---

## C20. KeyringEditConflictResolution

Когда два member'а одновременно edit'ят keyring — optimistic locking conflict (409 на сервере). Клиент:

```kotlin
// core/group/api/KeyringConflictResolver.kt

interface KeyringConflictResolver {
    /**
     * При 409:
     * 1. GET свежий keyring.
     * 2. Re-apply свой edit поверх свежего state'а.
     * 3. PUT с новым version.
     * 4. Если опять 409 — retry с jitter.
     */
}
```

---

## Что **исчезает** из клиента (deprecated logic)

| Старая компонента | Причина удаления |
|---|---|
| `LinkBootstrap` / `linkId` resolver | Заменён opaque NamespaceId |
| `BackgroundReconciler` для grants | Grants ушли в keyring blob, нет server-side понятия |
| `WorkerEncryptedMediaStorage` (текущая impl) | Заменён `Tier0BlobStorage` adapter (тот же port `EncryptedMediaStorage`, новый adapter) |
| `FirestoreDeviceIdentityRepository` | Заменён `Tier1PubKeyDirectory` adapter |
| `PairRecipientResolver` через Firestore | Заменён `KeyringRecipientResolver` (читает keyring blob) |
| Server-side push event type registry | Заменён клиентским `PushHandlerRegistry` |
| `FirestoreEnvelopeStorage` paths `/users/{uid}/data/{key}` | Заменён generic `Tier0BlobStorage` с opaque keys |

---

## Effort estimate (rough)

| Компонента | Сложность | Эффект |
|---|---|---|
| C1 Namespace abstraction | Medium | Base для всего |
| C2 Group + keyring | High | Самое сложное; Signal Sender Keys pattern |
| C3 Push payload encryption | Medium | Foundation для всех push |
| C4 PushTokenManager | Low | Refresh + reconcile |
| C5 ConfigHistory rotation | Low | Уже частично есть |
| C6 SchemaTransformer | Medium | Chain pattern |
| C7 ContactRefcount | Skip (recommend) | Не делать |
| C8 ForwardUnsharing | High | Critical для security |
| C9 AppVersionCompat | Low | Schema field |
| C10 AuditLog | Medium | Append-only blob |
| C11 AlgorithmMigrator | Medium | Per-blob check |
| C12 GDPR export | Low | Iterate blobs |
| C13/C14/C15 client push triggers | Low each | Использует C3 |
| C16 ReplayProtection | Low | Set + check |
| C17 SignedWriter | Low | Cross-cutting |
| C18 SelfStateBackup | Critical | Без этого reinstall = broken |
| C19 SealedAware client | Low | Retry logic |
| C20 KeyringConflictResolver | Medium | Optimistic locking pattern |

**Главные риски**:
- **C2 + C8 (group + forward unsharing)** — сложнее всего. Industry pattern есть (Signal Sender Keys), но реализация требует careful crypto review.
- **C18 (self-state backup)** — без этого пользователь теряет access при reinstall. Это **must-have**, не nice-to-have.
- **C20 (keyring conflict resolution)** — race conditions трудно тестировать; нужен property-based test.

---

## Industry patterns для каждого client component'а

| Component | Pattern | Reference |
|---|---|---|
| C1 Namespace | Anonymous workspace registration | Tresorit workspaces |
| C2 Group + keyring | Sender Keys distribution | Signal Sender Keys |
| C3 Encrypted push | Sealed Sender | Signal Sealed Sender |
| C4 PushTokenManager | Token rotation | WhatsApp push token refresh |
| C6 SchemaTransformer | Versioned data evolution | CRDT-style migration |
| C8 ForwardUnsharing | Group rekey on member change | MLS (RFC 9420) tree update |
| C10 AuditLog | Hash chain append-only | Certificate Transparency log |
| C11 AlgorithmMigrator | Lazy crypto migration | WhatsApp E2E Backup phase rollout |
| C16 ReplayProtection | Nonce window | Signal Double Ratchet replay defense |
| C18 SelfStateBackup | Encrypted state recovery | iCloud Keychain backup |
| C20 ConflictResolver | Optimistic concurrency | Firestore txn pattern (без Firestore) |

---

## Открытые вопросы

1. **Multi-device per UID** — один Google account на нескольких устройствах. Нужен ли cross-device state sync через namespace? Или каждое устройство имеет свои opaque IDs?
2. **Group size limit** — keyring blob растёт линейно с числом member'ов. До 100 members keyring blob ~ 10 KB. Acceptable.
3. **Keyring blob size limit FCM push** — 4 KB FCM limit. Если payload > 4 KB — split на multiple push'ей или fetch trigger (FCM only as notification, payload через GET).
4. **Atomic single-writer для keyring**? Или multi-writer с CRDT-style merge? Я бы взял optimistic locking single-writer (проще; merge только в случае конфликта).
5. **Backup self-state — кладём в свой recovery vault или отдельный backup blob?** Recovery vault проще (один passphrase для всего).

---

## Mapping client-component → server-tier

| Client component | Server tier | Endpoint(s) used |
|---|---|---|
| C1 Namespace | S1 | POST /namespaces, DELETE /namespaces/{id} |
| C2 Group keyring | S0 | PUT/GET /namespaces/{ns}/blobs/_keyring |
| C3 Push send | S2 | POST /push |
| C3 Push receive | (FCM only) | — |
| C4 Token registry | D2 | PUT/DELETE /push-tokens/{tokenId} |
| C5 Config history | S0 | LIST + DELETE на opaque keys |
| C8 Forward unsharing | S0 | Batch PUT/DELETE |
| C10 Audit log | S0 | PUT в свой namespace |
| C11 Migration | S0 | PUT/DELETE при re-encrypt |
| C12 GDPR export | S0 | LIST + GET всех blob'ов |
| C13/14/15 sensor/health/SOS | S0 + S2 | PUT blob + POST /push |
| C17 Signed writer | S0 cross-cutting | Header X-Sig на каждый PUT |
| C18 Self-state backup | T2 | Recovery vault put + get |

---

**End of client requirements.** Этот документ — спецификация delta'ов для launcher. После реализации client может работать против zero-knowledge сервера. Источник правды по архитектурным принципам — [server-requirements.md](server-requirements.md).
