# Feature Specification: TASK-66 — Generic Encrypted Bucket Registry

**Feature Branch**: `task-66-generic-encrypted-bucket-registry`
**Created**: 2026-07-01
**Closed**: 2026-07-01 — **Resolved by existing code**
**Status**: Closure record (no implementation performed)

---

## Verdict

**TASK-66 закрывается без реализации.** В ходе двух проходов `/speckit.clarify` в mentor-режиме обнаружено, что вся инфраструктура, которую предполагала эта задача, **уже реализована в рамках TASK-6 (Root Key Hierarchy + Owner Recovery)** и готова к использованию как есть.

Оригинальная формулировка «Generic Encrypted Bucket Registry» вводила в код избыточный слой (`EncryptedBucketRegistry`, `BucketTypeSpec`, `BucketCatalog`) поверх уже существующего `RemoteStorage` порта. Это **дублирование**, запрещённое CLAUDE.md rule 4 (Minimum Viable Architecture).

---

## Что уже сделано (что делало бы TASK-66)

### 1. Тонкий universal remote-storage port

**Реализовано в TASK-6:**

- [core/keys/src/commonMain/kotlin/family/keys/api/RemoteStorage.kt](../../core/keys/src/commonMain/kotlin/family/keys/api/RemoteStorage.kt) — порт `put/get/list/delete(namespace, key, bytes)` над opaque bytes.
- [core/keys/src/commonMain/kotlin/family/keys/impl/EnvelopeRemoteStorage.kt](../../core/keys/src/commonMain/kotlin/family/keys/impl/EnvelopeRemoteStorage.kt) — единственная реализация; склеивает `RecipientResolver + ConfigCipher2 + EnvelopeStorage + DeviceIdentity`.
- Docstring порта явно называет будущих потребителей: *«future ecosystem apps — messenger, album»* — та же extraction-readiness, что TASK-66 хотела ввести.

Caller видит только `(namespace, key, bytes)`. Шифрование, backend, wire format — всё инкапсулировано. **Это ровно та «тонкая прослойка», которую хотели.**

### 2. E2E envelope с multi-recipient поддержкой

- [core/keys/src/commonMain/kotlin/family/keys/api/Envelope.kt](../../core/keys/src/commonMain/kotlin/family/keys/api/Envelope.kt) — hybrid encryption: CEK шифрует ciphertext (XChaCha20-Poly1305), CEK шифруется per-recipient X25519 sealed-box.
- `recipientKeys: Map<DeviceId, sealedCEK>` — N recipients в одном envelope без изменения wire format.
- `schemaVersion=1`, `algorithm="envelope-xchacha20poly1305-x25519-v1"`, AAD binding = `"family-storage::v1::$namespace::$key"`.

### 3. Recipient resolution (то, о чём мы говорили как о «policy»)

- [core/keys/src/commonMain/kotlin/family/keys/api/internal/RecipientResolver.kt](../../core/keys/src/commonMain/kotlin/family/keys/api/internal/RecipientResolver.kt) — порт.
- [core/keys/src/commonMain/kotlin/family/keys/impl/PublicKeyDirectoryRecipientResolver.kt](../../core/keys/src/commonMain/kotlin/family/keys/impl/PublicKeyDirectoryRecipientResolver.kt) — **production implementation, не placeholder**:
  - Тянет свои устройства (`fetchDevicesFor(ownerUid)`).
  - Тянет всех grant-holders (`fetchGrantHolders(ownerUid)`).
  - Разворачивает каждый grant в устройства holder'а.
  - Дедуплицирует, возвращает список public keys.

Тест [`crossUserAdminEditsBabushkaConfigViaGrantBothCanRead`](../../core/keys/src/commonTest/kotlin/family/keys/EnvelopeRemoteStorageTest.kt) уже проходит: admin пишет в namespace бабушки, envelope содержит ключи обоих, оба читают. **Multi-recipient сценарий уже E2E работает.**

### 4. Public key directory (публикация ключей + список grant-holders)

- [core/keys/src/commonMain/kotlin/family/keys/api/internal/PublicKeyDirectory.kt](../../core/keys/src/commonMain/kotlin/family/keys/api/internal/PublicKeyDirectory.kt) — порт.
- [app/src/realBackend/java/com/launcher/app/data/envelope/FirestorePublicKeyDirectory.kt](../../app/src/realBackend/java/com/launcher/app/data/envelope/FirestorePublicKeyDirectory.kt) — Firestore adapter.
- Firestore paths:
  - `/users/{uid}/devices/{deviceId}/pub-key/current` — публичные ключи устройств.
  - `/users/{uid}/access-grants/{helperUid}` — «я разрешаю helperUid'у читать».

### 5. Per-device X25519 keypair + Android Keystore

- [core/keys/src/androidMain/kotlin/family/keys/android/AndroidDeviceIdentity.kt](../../core/keys/src/androidMain/kotlin/family/keys/android/AndroidDeviceIdentity.kt) — генерация X25519 keypair при первом запуске, private key в TEE-wrapped Android Keystore под alias `config-device-priv-x25519-{deviceId}`.

### 6. Recovery infrastructure

- `RootKeyManager.recover(identity, passphrase)` — восстанавливает root key из Cloudflare Worker backup blob'а.
- `RemoteStorage.list(namespace, prefix)` — существует. Recovery-side «restore all my configs» получит список ключей через один вызов.
- Оставшийся код (перечислить + `get` каждый) — это ~20 строк helper'а в recovery flow, **не отдельный слой**.

### 7. Push infrastructure

- [core/push/](../../core/push/) — `PushPayload` (schemaVersion, eventType, ownerUid, triggerId, fields Map), `PushHandlerRegistry` с event-type-based dispatch.
- Уже event-driven, расширяемо. То, что TASK-66 предлагала как «wakeup routing», — уже сделано лучше через типизированные events.

---

## Почему TASK-66 в её оригинальной формулировке не нужна

Оригинальная идея вводила `BucketTypeSpec`, `BucketTypeId`, `EncryptedBucketRegistry`, `BucketCatalog`. Каждый пункт:

| Что предлагала TASK-66 | Уже есть в TASK-6 | Комментарий |
|---|---|---|
| `EncryptedBucketRegistry` port | `RemoteStorage` port | Идентичная roль: opaque `put/get`. |
| `BucketTypeSpec` (паспорт) | `key: String` + `RecipientResolver` (implicit policy) | «Паспорт» = ключ + resolver. Никакой отдельной структуры не надо. |
| `BucketTypeId` (namespaced) | `key` — произвольная строка | Именование — забота caller'а. |
| `BucketCatalog` (encrypted map of what's mine) | `RemoteStorage.list(namespace, prefix)` | Уже перечисляет через backend adapter. Никакого отдельного encrypted meta-bucket'а. |
| `recoverAll(rootKey)` | `list()` + `get()` в recovery flow | ~20 строк helper'а, не порт. |
| `RecipientPolicy` sealed hierarchy | `RecipientResolver.resolveFor(namespace, key)` | Resolver уже реализует «self + grant-holders» — это та же policy, инкапсулированная. |
| Wakeup FCM routing | `PushHandlerRegistry` (event-driven) | Уже лучше — типизированные events, а не тупой будильник. |

**Итог:** каждый порт / структура из TASK-66 либо дублирует существующий, либо заменяется на 1–2 существующих вызова. Rule 4 (MVA) прямо запрещает такое дублирование.

---

## Что реально осталось и куда попадает

В процессе clarify всплыли несколько узких задач, которые **не относятся к TASK-66**:

### 1. Opaque keys на сервере (server-blind key naming)

**Что.** Сейчас Firestore видит plain-text keys: `/users/{uid}/data/config__default`. Слово `config` в plaintext даёт серверу подсказку о содержимом. Правильно — детерминированный хеш от logical name через root key: `key_on_wire = HKDF(rootKey, "config/default")`.

**Куда.** **Не создаём отдельную задачу.** Просто inline `TODO` в `RemoteStorage` порту — крючок на будущее. Это не one-way door: `RemoteStorage` API уже принимает произвольный `key: String`, поэтому переход от plain-text к хешу — **изменение только внутри caller'ов**, wire format сервера не меняется (сервер и так видит escaped string, ему всё равно plain или hash).

**Приоритет.** Не MVP-блокер. Владелец: «сейчас можно plain-text, потом переделать на шифрованные ключи».

**TODO добавляется в этом же PR.**

### 2. Pairing → access-grant bridge

**Что.** Pairing UI существует (spec 007 merged), но никто не пишет `/users/{uid}/access-grants/{helperUid}` документ. Из-за этого `PublicKeyDirectoryRecipientResolver` в проде возвращает N=1 (только собственные устройства владельца), несмотря на то что multi-recipient wire format готов.

**Куда.** **TASK-67** (Pairing Feature + pairing-edges bucket), уже в backlog. Область: после успешного pair добавить write в `access-grants`.

### 3. Recover-all в recovery flow

**Что.** Recovery flow (TASK-6) сейчас восстанавливает root key. Следующий шаг — «перечисли всё моё, скачай каждое, расшифруй» — не реализован (заявлен в docstring resolver'а, но код не написан).

**Куда.** **TASK-57 или расширение TASK-6.** ~20 строк helper'а: `remoteStorage.list(namespace) → forEach(get)`. Не отдельная фича. Manual gate уже отложен в TASK-6 deferred-list как cross-device recovery на двух физических устройствах.

### 4. Grant scope (гранулярность доступа)

**Что.** Сейчас grant = «этот helper читает всё моё». Возможна необходимость «bob читает contacts, но не документы». Расширение — поле `scope: List<Category>` в grant document, `RecipientResolver` учитывает scope при `resolveFor`.

**Куда.** **Не MVP.** Открытая эволюция при появлении сценария. Комментарий добавлен в TODO в `PublicKeyDirectory` docstring.

---

## Что делается в этом PR

1. **`spec.md` переписан как closure record** (этот файл).
2. **Inline `TODO` в [core/keys/src/commonMain/kotlin/family/keys/api/RemoteStorage.kt](../../core/keys/src/commonMain/kotlin/family/keys/api/RemoteStorage.kt)** — крючок про opaque keys на будущее. Не one-way door: caller сегодня передаёт plain string, завтра — hash, порт не меняется.
3. **Constitution Article XX** (Pre-MVP no-migration override) — принят ранее в этой же ветке. Сохраняется, так как relevant для проекта в целом, не только TASK-66.
4. **Backlog TASK-66 → Done** через `pre-pr-backlog-sync` с обоснованием «resolved-by-existing-code».

---

## Уроки из этой итерации (для будущих спек)

- **Article XVI (Required Constitution Check) и §Required Context Review** требовали проверить существующий код **до** написания спеки. Не было сделано в первом драфте TASK-66. Результат — два прохода clarify с постепенным обнаружением, что «фича» уже реализована.
- **CLAUDE.md rule 4 (MVA)** прямо запрещает надстройку с одной реализацией, дублирующую существующую. TASK-66 попадала под §Refuse pattern #9 (premature abstraction: single-implementation interface with no port-shaped seam).
- **Правильный первый шаг для любой backlog-задачи, касающейся infrastructure/foundation:** сначала `grep`, потом `spec.md`. Специфицировать в отрыве от реального состояния кода = писать спеку про фичу, которая может уже быть.
- **Личность владельца как фильтр:** владелец несколько раз указал «ты растекаешься», «это утечка ответственности», «сначала исследуй». Эти сигналы должны были триггерить discovery-режим **до** clarify, не после.

---

## Related artifacts

- **Discovery reports** — Explore-агент дважды прошёл по коду (текущее состояние TASK-6 + pairing). Результаты — в conversation history.
- **Constitution v1.7.0** — Article XX добавлен в этой ветке, остаётся.
- **`docs/dev/server-roadmap.md`** — не требует новых entries (opaque keys покрывается существующим SRV-PKD-001 hook'ом).
