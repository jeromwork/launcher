# Feature Specification: End-to-End Crypto Foundation

**Feature Branch**: `011-contacts-and-e2e-encrypted-media` *(имя ветки сохранено; артефакт переименован 2026-05-22)*
**Created**: 2026-05-21
**Status**: Draft (rev. 3 — mentor scope-split 2026-05-22 — visible feature вынесена в спек 012)
**Input**: roadmap §Spec 011 ([docs/product/roadmap.md:264](../../docs/product/roadmap.md#L264)) — **универсальный криптографический фундамент проекта**: per-device asymmetric keys, encryption (AEAD), signing (Ed25519), hashing (BLAKE2b), membership-agnostic envelope для at-rest blob'ов, Pub-key publication, Storage adapter, reference counting + cleanup. **Без visible feature** — используется будущими спеками 012-016 (фото, двусторонние пары, группы, multi-device, key rotation), а также будущими интеграциями Jitsi room access, vendor (клиники, центры поддержки), hardware (медицинские/охранные датчики).

---

## Контекст и цель спека

### Что это и зачем

Спек 011 — это **универсальный криптографический фундамент проекта**. Без него ни одна будущая фича, требующая шифрования, не может быть построена. С ним — все они строятся **на одной и той же инфраструктуре**, без миграции данных при добавлении новых моделей доверия.

**Спек 011 НЕ имеет visible feature** — никаких фотографий на плитках, никаких UI-изменений, никаких Contacts Picker'ов. Видимый продукт первым выдаст **спек 012** (фото контактов и личных документов) — он будет **первым клиентом** этого фундамента.

### Контекст переноса (2026-05-22)

Изначально спек 011 объединял «крипто-машину» (фундамент) и «фото контактов» (visible feature). В mentor-сессии решено разрезать на два спека:

- **011 = только фундамент** — этот документ. Domain types, ports, real-adapters, fake-adapters, envelope wire-format, pairing extension (Pub-key publication), Storage adapter, reference counting, cleanup, automated e2e smoke-тест на синтетическом blob'е.
- **012 = первый клиент** — фото контактов + личных документов. UI, Contacts Picker integration, PrivateMediaResolver (IconStorage namespace dispatch для `private:`). Использует ВСЁ из 011, ничего своего не добавляет в крипто-слой.

Почему разрезано:
1. **Размер** — оба спека вместе = 73+ task'а в 12 фазах = ~7 недель в одной ветке. Нарушает CLAUDE.md §Branching «one feature = one branch = one PR».
2. **Универсальность** — фундамент используется **не только для фото**. Будущие клиенты: модели доверия (013-016), Jitsi room access, vendor integrations, hardware integrations, аудио/видеосообщения «как WhatsApp voice». Привязка фундамента к одному visible feature — переинженеринг наоборот.
3. **Чистота тестов** — фундамент тестируется на синтетических blob'ах через Storage Emulator + manual smoke на двух устройствах с тестовыми байтами. UX-тесты — в 012, отдельно.

### Зависимости от прошлых спеков (уже заложенные крючки)

Спек 006 (`provider-capabilities-and-health`) ввёл порт `IconStorage.resolve(iconId: String)` и namespace-конвенцию `<namespace>:<name>` ([icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48)). Namespace `private:` зарезервирован — но **в 011 он не используется**. PrivateMediaResolver появится в спеке 012. До 012 порт продолжает возвращать `Placeholder` для `private:<uuid>` (как и сейчас).

Спек 007 (`pairing-and-firebase-channel`) установил парный канал admin↔Managed через QR + Firestore. Спек 011 **расширяет** post-pairing-flow additive: после `consent.allow` каждое устройство публикует свой Pub-ключ в `/links/{linkId}/devices/{deviceId}`. Pairing-token wire-format **не меняется** — поле `pairingKey` из ([pairing-token.md:79](../007-pairing-and-firebase-channel/contracts/pairing-token.md#L79)) **не используется** (обмен ключами идёт через Firestore document, а не через QR).

Спек 008 (`bidirectional-config-sync`) ввёл `Contact.photoRef: String?` ([data-model.md:64](../008-bidirectional-config-sync/data-model.md#L64)) и `PartialReason.MediaDecryptFailed` ([state-applied.md:65](../008-bidirectional-config-sync/contracts/state-applied.md#L65)). В спеке 011 эти поля **остаются `null` / не эмитируются** — наполнение реальным контентом происходит в 012.

Спек 009 (`admin-mode-flows`) реализовал Contacts Picker. В 011 picker **не трогается** — фото подключается в 012.

### Архитектурная роль спека

Впервые в проекте:
1. Каждое устройство имеет свою публично-приватную пару X25519 ключей (encryption) и Ed25519 ключей (signing).
2. Появляется membership-agnostic envelope format — массив `recipients` произвольной длины, в 011 длина 1, в будущих спеках N.
3. Firebase Storage входит в проект (первый файловый storage, до 011 был только Firestore).
4. Порты `AeadCipher`, `AsymmetricCrypto`, `SecureKeystore`, `HashFunction`, `DigitalSignature`, `RecipientResolver`, `EncryptedMediaStorage`, `DeviceIdentityRepository` становятся **универсальной криптографической ABI** проекта — любая будущая фича, требующая криптографии, дёргает их.

После 011 любые приватные данные могут переехать через тот же envelope — от фото контактов до Jitsi room keys, от документов клиник до hardware attestation. Это **one-way door** (rule 3 CLAUDE.md), но дверь спроектирована так, что её **архитектурная часть** (envelope format, hybrid encryption, per-device keys) выдержит смену модели доверия без миграции blob'ов. Меняться может «как ключи распространяются», не «как зашифровано». Exit ramp — см. §Архитектурные one-way doors.

### Эволюция модели доверия (всё на одном фундаменте)

Главная архитектурная инвестиция 011 — **разделение слоёв «кто получатели» (membership) и «как они зашифрованы» (crypto envelope)**. Это позволяет добавлять новые модели доверия без перешифровки blob'ов:

1. **011-012**: 1↔1 односторонняя пара (admin → managed). `recipients[]` длины 1.
2. **013**: 1↔1 двусторонняя (mutual auth — двусторонние пары).
3. **014**: 1↔N (групповые ключи, семейный круг — `recipients[]` длины N).
4. **015**: M↔N (multi-device одного владельца, общий identity — `recipients[]` содержит все устройства владельца).
5. **016**: key rotation поверх любого из выше (1↔1, группа, multi-device) — envelope `cipherSuiteId` + deprecated keys.

Envelope формат **не меняется** при переходе между моделями. Меняется только содержимое `recipients[]` и логика `RecipientResolver`. Старые blob'ы продолжают читаться по правилам старого `schemaVersion`.

### Будущие клиенты фундамента 011 (защита от scope creep)

| Будущий клиент | Что использует из 011 | Что добавляет своё |
|---|---|---|
| spec 012 (фото) | EncryptedMediaStorage, AeadCipher, AsymmetricCrypto, RecipientResolver | UI, Contacts Picker, PrivateMediaResolver |
| spec 013 (двусторонние пары) | DigitalSignature (mutual auth), RecipientResolver (расширение) | Bidirectional Security Rules, consent UX |
| spec 014 (групповые ключи) | расширение RecipientResolver, EncryptedMediaStorage | Group membership management |
| spec 015 (multi-device) | DigitalSignature (device attestation), RecipientResolver | Recovery protocol |
| spec 016 (key rotation) | расширение envelope с deprecated keys | Periodic rotation + forward secrecy |
| TBD-Jitsi integration | DigitalSignature (room auth), HashFunction (room key fingerprint), EncryptedMediaStorage (recorded meetings at-rest) | Olm/Megolm для realtime — **НЕ** через наш envelope |
| TBD-Audio/Video messages (asynchronous, «как WhatsApp voice») | EncryptedMediaStorage + AeadCipher (тот же envelope, другой `metadata.kind`) | UI записи/проигрывания |
| TBD-Vendor integration (клиники, центры поддержки) | DigitalSignature (HMAC/JWT для b2b API), HashFunction (request signing) | Vendor-specific протокол |
| TBD-Hardware integration (медицинские датчики, охранные датчики) | DigitalSignature (attestation), HashFunction (integrity) | Hardware-specific протокол |

### Что НЕ делает фундамент 011 (явный out-of-scope)

- **Visible feature** — никаких фото, UI, Contacts Picker. Это спек 012.
- **Realtime stream encryption** (SRTP/SFrame для audio/video звонков) — **никогда** не через наш envelope. Realtime media шифруется внутри Jitsi/SFrame в будущем спеке Jitsi-integration. Наш envelope покрывает только at-rest blob'ы (зашифровал → положил → потом достал → расшифровал).
- **Post-quantum cryptography** — отдельный future спек (~020+); libsodium со временем добавит примитивы, мы обновимся через app update.
- **X.509 PKI** — если когда-нибудь vendor потребует X.509-сертификаты, это отдельный adapter (Bouncy Castle), не наш envelope.
- **Шифрование `/config` целиком** — там настройки, не приватные данные.
- **Шифрование иконок провайдеров (`bundled:`, `custom:`)** — они не приватные.

---

## Clarifications (mentor-сессия 2026-05-21)

Этот раздел фиксирует решения, принятые в discussion-фазе с пользователем по 7 ключевым архитектурным вопросам. Каждое решение содержит формулировку, обоснование и (где применимо) ссылку на будущий спек, который снимет временное ограничение.

### C-1: Модель доверия в 011 — односторонняя пара (как в спеках 7-9)

**Решение:** в спеке 011 сохраняем одностороннюю модель управления admin→Managed из спеков 7-9. Никаких изменений в Security Rules, pairing-token wire-format direction, или ownership полей `/config`.

**Обоснование:** двусторонняя/групповая/multi-device модели — отдельная значительная работа, затрагивающая 3 уже смерженных спека. Их перенос на 011 удваивает scope и блокирует крипто-фундамент. Криптографический протокол (см. C-3) спроектирован membership-agnostic — переход на любую другую модель доверия в будущем НЕ требует перешифровки blob'ов или смены envelope format.

**Будущие спеки, которые снимут ограничение:**
- **~015 «двусторонние пары»** — Managed может тоже стать управляющим для admin'a после согласия.
- **~016 «семейные группы»** — групповой ключ для семейного круга в стиле WhatsApp (несколько участников видят общие медиа).
- **~017 «multi-device recovery»** — у одного человека несколько устройств видят одну и ту же пару/группу; восстановление при потере телефона.
- **~018 «key rotation + forward secrecy»** — периодическое обновление ключей пары для защиты от утечек.

### C-2: Multi-recipient encryption — заложить архитектурно, использовать с одним получателем

**Решение:** envelope format с первого commit поддерживает **список** получателей произвольной длины. В спеке 011 список всегда содержит **одного** получателя (другой член пары). В будущих спеках (~016, ~017) список расширяется до N без изменения envelope format.

**Обоснование:** массив длиной 1 идентичен массиву длиной N в коде write/read. Цена закладывания — 0 строк сейчас, экономия — миграция всех blob'ов в будущем.

### C-3: Криптографический протокол — per-device key pairs + hybrid encryption

**Решение:** каждое устройство при первом запуске приложения генерирует свою публично-приватную пару `(Pub_X, Priv_X)`. `Pub_X` публикуется в Firestore как часть identity устройства; `Priv_X` хранится в Android Keystore и НЕ покидает устройство.

Шифрование blob'a происходит по схеме **hybrid encryption**:
1. Генерируется одноразовый Content Encryption Key (CEK) — 32 байта рандома **на каждый blob**.
2. Blob (само фото / документ) шифруется CEK через AEAD (XChaCha20-Poly1305).
3. CEK шифруется публичным ключом каждого получателя через `crypto_box_seal` (libsodium).
4. Envelope содержит: `schemaVersion`, `cipherSuiteId`, `nonce`, `recipients: [{deviceId, encryptedCEK}]`, `ciphertext`, `mac`.

**Обоснование:** этот протокол **не привязан** к модели доверия. Smena «одного получателя» на «N получателей» — это смена только содержимого массива `recipients`, без изменения структуры envelope. Per-device keys означают, что каждое устройство держит секрет только за себя — компрометация одного устройства не вредит остальным.

**Архитектурный принцип «crypto envelope is membership-agnostic»** — формат зашифрованного blob'a ничего не знает о том, кто его расшифрует. Это знает отдельный слой `RecipientResolver` (см. C-7), который для 011 возвращает «другой член пары», в будущем — «все члены группы» или «все мои устройства».

### C-4: Криптобиблиотека — libsodium через Lazysodium-android

**Решение:** используем libsodium через Java-обёртку Lazysodium-android. AEAD = XChaCha20-Poly1305. Asymmetric key agreement = X25519. Hybrid encryption = `crypto_box_seal`.

**Обоснование:**
- libsodium закрывает все нужды через минимальное число функций без избыточного API.
- Открытый проект сообщества, не привязан к одному вендору (важно для будущего «переезда на свой сервер» по server-roadmap, rule 8 CLAUDE.md).
- Универсальность runtime: libsodium работает на любом языке/платформе (когда появится свой сервер на любом стеке).

**Альтернативы и почему отброшены:**
- **Tink (Google)** — привязка к Google Cloud KMS в документации создаёт шум; runtime'ы Tink ограничены (нет, например, Rust); философия Tink ориентирована на server-side use cases. Закрывает наши потребности, но даёт меньше гибкости при будущем переезде на свой сервер.
- **Conscrypt/BouncyCastle напрямую** — низкоуровневое API, требует сборки крипто-протокола из кубиков, риск ошибки. Противоречит принципу «никакой самописной крипты».
- **Signal Protocol (libsignal)** — избыточная сложность для текущего scope (Double Ratchet, async session state). Хороший кандидат для будущего ~018 (forward secrecy), не для 011.

**Exit ramp:** schemaVersion + cipherSuiteId в envelope с первого commit. Если через 5 лет XChaCha20-Poly1305 устареет или libsodium станет недоступным — поднимаем schemaVersion до 2, шифруем новые blob'ы новым алгоритмом, читаем старые по правилам v1. Цена exit ramp = код миграции (один раз), НЕ перешифровка всех старых blob'ов.

### C-5: Namespace `private:` — без sub-namespacing

**Решение:** один namespace `private:<uuid>` для любого типа приватного медиа. Тип содержимого (image, video, document, и т.д.) хранится **внутри envelope** как metadata-поле, не в namespace.

**Обоснование:** `IconStorage` контракт остаётся прежним (см. спек 006 [icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48)). Менять тип содержимого blob'a в будущем = изменить metadata, не namespace. Sub-namespacing (`private-photo:`, `private-doc:`) ввёл бы лишнюю абстракцию в IconStorage и потребовал бы расширения контракта при добавлении новых типов — нарушение rule 4 CLAUDE.md (MVA).

### C-6: POST_NOTIFICATIONS — откладываем за пределы 011

**Решение:** разрешение POST_NOTIFICATIONS НЕ активируется в этом спеке. Запись в [docs/compliance/permissions-and-resource-budget.md:224](../../docs/compliance/permissions-and-resource-budget.md#L224) обновляется с пометкой «deferred to spec 012 (balance alerts) / spec 013 (critical health push)».

**Обоснование:** в 011 нет нативных уведомлений. Crypto-операции (расшифровка blob'a, fallback на placeholder) происходят silently в фоне ConfigApplier'a — без push'ей пользователю.

### C-7: Reference counting blob'ов учитывает history snapshots

**Решение:** blob в Storage удаляется только когда ни один документ (текущий `/config/current` ИЛИ один из history snapshots в `/config/history/`) на него не ссылается. История из спека 009 — полноценный источник references.

**Обоснование:** если бабушка откатывается на старый конфиг через history (спек 009), и в этом конфиге был контакт с фото — blob должен быть доступен. Удаление blob'a при простом удалении контакта из текущего `/config` нарушит rollback-семантику.

**Cost implication:** blob может жить дольше, чем «казалось бы» нужно — пока bookmark в history не очистится через retention-10 (спек 009 Q2=А). Это приемлемо: при retention 10 snapshot'ов и средних 30 контактах с фото на пару, верхняя оценка ~300 blob'ов на пару (≈ 60 MB при 200 KB/blob) — комфортно для Spark plan.

### C-9: Scope-split на 011 (фундамент) + 012 (visible feature) (mentor 2026-05-22)

**Решение:** разрезать original спек надвое.
- **011** = только криптофундамент: порты, адаптеры, envelope wire-format, pairing extension с Pub publication, Storage adapter, reference counting, manual smoke на синтетическом blob'е. БЕЗ visible feature.
- **012** = первый visible client: фото контактов + личных документов, UI (Contacts Picker integration, DocumentPicker, DocumentViewer), PrivateMediaResolver (IconStorage namespace dispatch), PartialReason.MediaDecryptFailed real emission.

**Обоснование:**
1. Размер — 73 task'а в одной ветке нарушал CLAUDE.md §Branching «one feature = one branch = one PR».
2. Универсальность — фундамент используется не только для фото (см. таблицу §Будущие клиенты): спеки 013-016 модели доверия, TBD-Jitsi room access, TBD-Vendor, TBD-Hardware. Привязка к одному visible feature = переинженеринг наоборот.
3. Roadmap перенумерация: 015→013, 016→014, 017→015, 018→016, 012(balance)→017, 013(offline)→018, 014(onboarding)→019.

**Также в этой mentor-сессии:**
- Фундамент расширен на 2 дополнительных порта: `DigitalSignature` (Ed25519) и `HashFunction` (BLAKE2b) — для anti-tamper Pub publication, future Jitsi room auth, future vendor HMAC, future hardware attestation, future blob deduplication.
- `SRV-MEDIA-001` (Firebase Storage migration when quota exceeded) переформулирован в `SRV-CRYPTO-001` (универсальный маршрут переезда крипто-инфраструктуры на собственный backend, не привязан к Firebase лимитам).
- Membership-agnostic envelope подтверждён как главная архитектурная инвестиция: эволюция модели доверия 1↔1 → 1↔1 mutual → 1↔N (группы) → M↔N (multi-device) → key rotation **без перешифровки blob'ов**.

**Будущие спеки, перенумерованы:**
- **013** (был 015) — symmetric-pairing-bidirectional-control.
- **014** (был 016) — family-group-shared-encryption.
- **015** (был 017) — multi-device-recovery.
- **016** (был 018) — key-rotation-forward-secrecy.

---

### C-8 (доп.): Архитектурный seam `RecipientResolver` — обоснованное исключение из rule 4

**Решение:** вводим интерфейс `RecipientResolver { fun resolveRecipients(linkId: LinkId): List<DeviceIdentity> }` с одной реализацией `PairRecipientResolver` в 011.

**Обоснование (для Constitution Check в plan-phase):** rule 4 CLAUDE.md «Minimum Viable Architecture» запрещает single-implementation interface «на будущее». Но в нашем случае seam обоснован тем, что **точно** появятся `GroupRecipientResolver` (~016), `MyDevicesRecipientResolver` (~017) — три минимум реализации в roadmap. Это даёт **архитектурную независимость крипто-протокола от модели доверия** (главное требование пользователя из discussion). Заносим как обоснованное исключение в spec.md и plan.md.

---

## User Scenarios & Testing *(mandatory)*

**Замечание про user stories для infra-спека:** 011 — это фундамент, не visible feature. User'ом здесь выступает **разработчик** (или будущий спек 012-016, или Jitsi-интеграция), потребляющий API. Acceptance scenarios формулируются как **операции инфраструктуры**, проверяемые автотестами + одним manual smoke-тестом на двух устройствах.

### User Story 1 — Pairing генерирует per-device asymmetric keys и публикует Pub (Priority: P1)

При первом запуске приложения устройство генерирует свою пару X25519 ключей (encryption) и Ed25519 ключей (signing). Priv-ключи хранятся в Android Keystore через AES-wrap (Keystore не умеет X25519 нативно — AES-ключ генерируется в Keystore, им оборачивается X25519 priv). Pub-ключи **не публикуются**, пока устройство не запарилось — нет адресата.

При scan QR + `consent.allow` (спек 007 US-1) оба устройства публикуют свои Pub-ключи в Firestore `/links/{linkId}/devices/{deviceId}`. Подпись Pub'а через Ed25519 (anti-tamper). Пара устройств узнаёт Pub друг друга через Firestore при первом online — кеширует локально для offline use.

**Why P1**: без Pub-key publication никакой `RecipientResolver` не сможет вернуть Pub получателя — фундамент бесполезен.

**Independent Test**: factory-fresh устройство → запускаем приложение → `DeviceKeyPair (Pub, Priv)` X25519 и Ed25519 сгенерированы; Priv-ключи в Keystore (`launcher_device_priv_x25519_v1`, `launcher_device_priv_ed25519_v1` aliases); Pub-ключи в RAM, **не** опубликованы. Делаем pairing с другим factory-fresh устройством → после `consent.allow` оба Pub'a в `/links/{linkId}/devices/{deviceId}` с подписью Ed25519. Firestore Security Rules не дают чужому uid писать в эту коллекцию.

**Acceptance Scenarios**:

1. **Given** factory-fresh устройство, **When** приложение запускается впервые, **Then** генерируются `(Pub_X25519, Priv_X25519)` и `(Pub_Ed25519, Priv_Ed25519)`; Priv'ы — в Keystore; Pub'ы — в memory; Firestore не трогается.
2. **Given** два устройства завершили `consent.allow`, **When** publishOwn() вызывается, **Then** каждое пишет свой Pub в `/links/{linkId}/devices/{deviceId}/pubkey` с подписью Ed25519 над содержимым; чужой uid пишет → `PERMISSION_DENIED`.
3. **Given** Pub другого члена пары опубликован в Firestore, **When** наше устройство фетчит peer Pub впервые, **Then** Pub скачивается, подпись Ed25519 верифицируется, Pub кешируется локально для offline use.
4. **Given** revoke link (спек 007 FR-033), **When** recursive subtree delete отрабатывает, **Then** документы `/links/{linkId}/devices/*` удалены; локальные Priv'ы в Keystore wipe'нуты под этот linkId.

---

### User Story 2 — Шифрование произвольного blob'а через membership-agnostic envelope (Priority: P1)

API фундамента: «зашифровать байты для списка получателей и вернуть envelope». Реализация — hybrid encryption: одноразовый CEK (32 random bytes) шифрует blob через AEAD; CEK seal'ится через `crypto_box_seal` для каждого Pub из `recipients[]`. Envelope — CBOR-сериализованный объект с `schemaVersion`, `cipherSuiteId`, `nonce`, `recipients[]`, `ciphertext`, `mac`.

В 011 список `recipients[]` всегда содержит **одного** получателя (другой член пары через `PairRecipientResolver`). В будущих спеках 014-015 список расширяется до N **без изменения envelope format**.

**Why P1**: это ядро фундамента. Без него фундамент пустой.

**Independent Test**: автотест в `commonTest` — зашифровать 16 байт случайных данных для своего же Pub (self-encrypt для теста), получить envelope, передать в `decrypt(envelope, ownPriv)`, получить обратно те же 16 байт; верификация MAC обязательна; повреждение envelope → `MacFailed`.

**Acceptance Scenarios**:

1. **Given** plaintext 16 байт и список из одного recipient'а, **When** `encrypt(plaintext, recipients)` вызывается, **Then** возвращается envelope с `recipients[].length == 1`; `decrypt(envelope, ownPriv)` возвращает оригинальные 16 байт.
2. **Given** envelope с `recipients[].length == 3` (test fixture для forward-compat), **When** `decrypt(envelope, ownPriv)` для одного из получателей, **Then** успешная расшифровка (форвард-совместимость с будущими 014-015).
3. **Given** envelope с подменённым `mac`, **When** `decrypt`, **Then** `MacFailed` error; plaintext не возвращается.
4. **Given** envelope с `cipherSuiteId` неизвестным (например `xchacha20poly1305_x25519_v999`), **When** `decrypt`, **Then** `CipherSuiteUnsupported` error (forward-compat не падает).

---

### User Story 3 — Storage adapter заливает и достаёт envelope blob'ы (Priority: P1)

API: `EncryptedMediaStorage.upload(uuid, envelope)`, `download(uuid) → envelope`, `delete(uuid)`, `exists(uuid) → Boolean`. Storage path — `/links/{linkId}/private-media/{uuid}`. Storage Security Rules ограничивают read/write только `adminId` и `managedDeviceFirebaseUid` пары.

**Why P1**: фундамент бесполезен без места, где blob'ы лежат.

**Independent Test**: Firebase Storage Emulator + Storage Rules. Upload синтетического envelope → download → проверка байт-в-байт идентичности. Foreign uid attempt → `PERMISSION_DENIED`. Delete → `exists() == false`.

**Acceptance Scenarios**:

1. **Given** valid envelope для своей пары, **When** `upload(uuid, envelope)`, **Then** Storage содержит blob по пути `/links/{linkId}/private-media/{uuid}`; `download(uuid)` возвращает идентичные байты.
2. **Given** foreign uid (не member пары), **When** попытка upload/download/delete, **Then** Storage Rules возвращают `PERMISSION_DENIED`; никаких байт не уходит.
3. **Given** revoke link (спек 007 FR-033), **When** recursive subtree delete отрабатывает, **Then** **все** blob'ы под `/links/{linkId}/private-media/*` удалены; `Link.KNOWN_SUBCOLLECTIONS` содержит `private-media`.
4. **Given** Storage недоступен (сеть упала), **When** `upload` или `delete`, **Then** возвращается structured error (не падает); retry-логика отложена в WorkManager.

---

### User Story 4 — Reference counting и cleanup orphan blob'ов (Priority: P2)

API: `BlobReferenceLedger` (SQLDelight таблица) считает ссылки на каждый `<uuid>` из любого источника — текущий `/config/current`, history snapshots (спек 009 retention 10), pending drafts. Blob удаляется только когда refCount = 0.

В 011 **сам факт ссылки** генерируется только тестами (никаких реальных `Contact.photoRef = "private:<uuid>"` не создаётся — это спек 012). Но **механика учёта** реализуется и тестируется на синтетических ledger entries.

**Why P2**: privacy hygiene + контроль расходов Storage. Без cleanup blob'ы накапливаются.

**Independent Test**: автотест на BlobReferenceLedger + FakeEncryptedMediaStorage — добавить ссылку, проверить refCount=1; убрать ссылку, проверить refCount=0 → blob помечается на удаление. Integration test с Storage Emulator — после revoke link все blob'ы под linkId удалены.

**Acceptance Scenarios**:

1. **Given** envelope `<uuid>` в Storage и одна reference в ledger, **When** reference удалена, **Then** refCount = 0; background reconciler инициирует delete; через ≤ 5 минут `exists(uuid) == false`.
2. **Given** envelope `<uuid>` с двумя ссылками (одна в current /config, одна в history snapshot), **When** убирается только current, **Then** refCount = 1, blob жив (rollback-safe).
3. **Given** revoke link, **When** recursive cleanup отрабатывает, **Then** все blob'ы под linkId удалены независимо от refCount; ledger очищен.
4. **Given** Storage недоступен в момент delete, **When** delete падает, **Then** delete отложен в WorkManager retry queue; счётчик попыток ограничен; после превышения — warning log, blob остаётся в Storage (known privacy risk).

---

### User Story 5 — Honest error reporting при сбое крипто-операций (Priority: P2)

API: все крипто-ошибки (`MacFailed`, `KeyNotFound`, `BlobMissing`, `CipherSuiteUnsupported`, `RecipientNotFound`) возвращаются как `CryptoError` sealed class через `Result<T, CryptoError>`. **Никогда** не возвращают plaintext / Priv / CEK в сообщениях ошибок. Structured logs содержат категорию + uuid + linkId, **никогда** не содержат байты blob'ов и ключевой материал.

В 011 это API. Реальное использование (показ placeholder на плитке, индикатор admin'у) — спек 012.

**Why P2**: фундамент должен **уметь** честно репортить, иначе клиенты (012+) не смогут показать пользователю ничего осмысленного.

**Independent Test**: автотест — каждый sealed sub-case `CryptoError` возвращается из соответствующего failure scenario; нет ни одного pathway, где Exception улетает наружу с plaintext / ключом в message.

**Acceptance Scenarios**:

1. **Given** envelope с повреждённым MAC, **When** `decrypt`, **Then** `Err(MacFailed(uuid))`; никаких байтов plaintext'а в сообщении / log'е.
2. **Given** envelope требует Priv, которого нет в Keystore (factory reset без revoke), **When** `decrypt`, **Then** `Err(KeyNotFound(alias, cause=null))`.
3. **Given** Storage возвращает 404, **When** `download`, **Then** `Err(BlobMissing(uuid))`.
4. **Given** envelope с future `cipherSuiteId`, **When** `decrypt`, **Then** `Err(CipherSuiteUnsupported(suiteId))`.

---

### User Story 6 — Manual smoke-тест на двух реальных устройствах (Priority: P1, gate перед merge)

Поскольку 011 не имеет visible feature, единственный способ убедиться, что вся цепочка работает на реальном железе (Android Keystore quirks, Firebase Storage TLS, FCM трансляция) — **запустить ручной smoke на двух реальных Android-устройствах**:

1. Два factory-fresh устройства, debug build приложения с 011.
2. Сделать pairing через QR.
3. На admin-устройстве через debug-кнопку «Encrypt test blob» сгенерировать 16 байт случайных данных, зашифровать через фундамент, залить в Storage. Endpoint admin'a показывает `private:<uuid>`.
4. На managed-устройстве через debug-кнопку «Decrypt test blob» ввести этот uuid, расшифровать, показать в Toast байты в hex.
5. Сравнить hex с admin-стороны и managed-стороны — должны совпасть.

**Why P1 + gate перед merge**: без этого теста мы не знаем, работает ли фундамент на реальных условиях. CI тестирует на Emulator'ах, реальное железо может отличаться (Keystore quirks на Xiaomi, Firebase TLS на старых Android, FCM delays).

**Independent Test**: ручной, документирован в `smoke/011/README.md`. Двухминутная процедура. Если не сработало — фиксим, прежде чем merge.

**Acceptance Scenarios**:

1. **Given** два factory-fresh устройства с debug build 011, **When** оператор тестирует по чеклисту в `smoke/011/README.md`, **Then** обе hex-строки идентичны; pairing + encrypt + upload + download + decrypt отрабатывают за ≤ 60 секунд.

---

### Edge Cases

- **Очень большой blob** (>10 MB): фундамент **не ограничивает** размер — это ответственность клиента (012 будет компрессить JPEG до ≤ 500 KB). Но фундамент должен корректно работать с blob'ами 10 MB+ для будущих документов / аудиосообщений.
- **Дедупликация blob'ов по content-hash**: если два разных reference указывают на одинаковое содержимое — можно ли переиспользовать один envelope? `HashFunction` (BLAKE2b) позволяет — но реализация дедупликации откладывается на спек 012 или позже (требует hash перед encrypt'ом, что меняет flow).
- **Orphan blob**: envelope залит, но `/config` push не прошёл (сеть/таймаут). Background reconciler ловит orphan'ы старше 24h и удаляет. Реализуется в 011, тестируется на синтетических.
- **На admin несколько paired Managed'ов**: пока в 011 нет multi-pair flow (один admin — один managed), но фундамент готов — `RecipientResolver` принимает `linkId`, разные linkId дают разные получателей. Mult-pair UX — за пределами 011.
- **Clock skew**: MAC / nonce-схема libsodium не зависит от времени. ОК.
- **Firebase Storage Security Rules**: blob path = `/links/{linkId}/private-media/<uuid>` доступен **только** `adminId` и `managedDeviceFirebaseUid`; чужой uid → `PERMISSION_DENIED`. Storage Rules пишутся в 011 (новые в проекте).
- **Schema mismatch envelope**: новый клиент шифрует envelope v2, старый клиент читает — должен получить `CipherSuiteUnsupported` или `SchemaVersionUnsupported`, не crash. Backward-compat reads обязательны (CLAUDE.md rule 5).
- **OEM Keystore quirks** (Xiaomi MIUI / Huawei EMUI / Samsung): Keystore иногда теряет ключи после OTA-обновления OEM прошивки. Это known issue индустрии. Fallback: если `KeyNotFound` при попытке decrypt — пара должна сделать re-pairing.

---

## Архитектурные one-way doors

Из roadmap §Spec 011 было зафиксировано как **one-way door**: выбор криптосистемы и схемы обмена ключами. После discussion-сессии 2026-05-21 (см. §Clarifications) принципиальные решения зафиксированы — door частично закрыта, но **с гарантией exit ramp на уровне envelope format**, а не на уровне «миграция всех blob'ов».

### Принятые решения и их exit ramp

**OWD-1: Криптобиблиотека = libsodium** (C-4). Exit ramp = `schemaVersion` + `cipherSuiteId` в envelope с первого commit. Смена библиотеки в будущем = новые blob'ы пишутся с другим `cipherSuiteId`, старые продолжают читаться по правилам v1. Перешифровка blob'ов НЕ требуется.

**OWD-2: Per-device key pairs (X25519) + hybrid encryption** (C-3). Exit ramp = `cipherSuiteId` в envelope покрывает и asymmetric scheme. Если через 10 лет X25519 окажется уязвимым к квантовым атакам — переход на post-quantum scheme идёт через тот же механизм version bump'а envelope.

**OWD-3: Membership-agnostic envelope** (C-2 + C-8). Список `recipients` произвольной длины. Переход от пары к группам / multi-device НЕ затрагивает envelope format — меняется только содержимое массива `recipients`. Это **главная архитектурная инвестиция** спека: разделение слоёв «кто получатели» (membership) и «как они зашифрованы» (crypto envelope).

### ADR-007 (второй subtype `TrustEdgeBootstrap`) — пишется в plan-фазе

Задача `TODO-DOC-001` в [project-backlog.md:378](../../docs/dev/project-backlog.md#L378) фиксирует, что в начале этого спека должен появиться ADR-007. По итогам clarify-сессии содержимое ADR-007 определено:
- **Subject:** TrustEdgeBootstrap расширение для per-device asymmetric keys + первое обмен публичными ключами при pairing.
- **Decision:** при первом запуске приложения генерируется (Pub_X, Priv_X); Pub_X публикуется в Firestore при первом online; pairing flow (спек 007) расширяется additive — после `consent.allow` оба устройства обмениваются Pub-ключами и сохраняют их в `/links/{linkId}/devices/{deviceId}`.
- **Consequences:** schemaVersion pairing-token остаётся 1; новое поле `pairingKey` в pairing-token НЕ нужно (отбрасываем — обмен Pub-ключами идёт через Firestore после `consent.allow`, не через QR).
- **Запись ADR-007 — в plan-фазе** (в директории `docs/adr/` если она есть, или inline в plan.md).

### Открытые вопросы для plan-фазы (не блокеры спека)

Эти вопросы влияют на детали реализации, но НЕ на архитектурные решения 011:

- **Где именно публиковать Pub_X в Firestore.** Кандидаты: `/links/{linkId}/devices/{deviceId}/pubkey` (привязан к линку) или глобальный `/devices/{deviceId}/pubkey` (один на всё). Решается в plan.md с учётом Security Rules.
- **Хранение CEK при retry uploads.** Если admin зашифровал blob, загрузил, а push `/config` упал — retry не должен заново шифровать (новый CEK = другой blob). CEK сохраняется в локальном draft'е до успешного push'а.
- **Compromised device detection** (как пользователь поймёт «pairing получился с тем, кем нужно»). В долгосрочной перспективе — safety numbers в стиле Signal. Для 011 фиксируется в backlog как `TODO-SEC-NNN`. В spec 011 этот flow НЕ обязателен (но если простой — допустимо).
- **MITM защита QR.** Текущий pairing-token из спека 007 хватает для protection of channel, но не для proof-of-identity при первом обмене. Закрывается тем же safety-numbers подходом — backlog.

---

## Requirements *(mandatory)*

### Functional Requirements

**Per-device asymmetric keys (US-1):**

- **FR-001**: On first launch each device MUST generate a `(Pub_X25519, Priv_X25519)` keypair for encryption and a `(Pub_Ed25519, Priv_Ed25519)` keypair for signing. Generation MUST use libsodium primitives (`crypto_box_keypair`, `crypto_sign_keypair`).
- **FR-002**: Both Priv keys MUST be stored in Android Keystore. Since Keystore does not support X25519 natively, X25519 Priv MUST be AES-wrapped using a Keystore-generated AES key; the wrapped X25519 Priv is stored in app-private encrypted storage (EncryptedSharedPreferences). Ed25519 Priv MAY use Keystore native support (API 31+) with software fallback for API 30 (same AES-wrap pattern).
- **FR-003**: Plain-text Priv keys MUST NOT appear in any logs, crash reports, analytics, structured events, or any other off-device channel.
- **FR-004**: On revoke (спек 007 FR-033), Priv keys associated with that link MUST be deleted from Keystore on both devices; re-pairing is required to restore crypto.
- **FR-005**: On factory reset without prior revoke, Priv keys are irrecoverable; subsequent decryption attempts MUST fail gracefully via `CryptoError.KeyNotFound`.

**Pub-key publication (US-1):**

- **FR-006**: After `consent.allow` in pairing (спек 007 FR-009), each device MUST publish its Pub_X25519 and Pub_Ed25519 to `/links/{linkId}/devices/{deviceId}` Firestore document. Publication payload MUST be signed by own Ed25519 Priv (anti-tamper).
- **FR-007**: Pairing-token wire-format ([pairing-token.md:79](../007-pairing-and-firebase-channel/contracts/pairing-token.md#L79)) MUST NOT be modified — Pub-key exchange goes through Firestore document **after** `consent.allow`, not through QR. `pairingKey` field reserved in pairing-token remains unused.
- **FR-008**: Firestore Security Rules MUST allow write to `/links/{linkId}/devices/{deviceId}` only by the uid owning `deviceId`; other uids → `PERMISSION_DENIED`. Read allowed for any member of the link.
- **FR-009**: On first need to encrypt for a peer, the device MUST fetch peer's Pub from `/links/{linkId}/devices/{peerDeviceId}`, verify Ed25519 signature, cache locally for offline use.

**Encrypted blob envelope wire format (US-2):**

- **FR-010**: Every envelope MUST carry an explicit `schemaVersion: Int` and `cipherSuiteId: String` from the first commit (CLAUDE.md rule 5). Initial values: `schemaVersion = 1`, `cipherSuiteId = "xchacha20poly1305_x25519_sealed_v1"`.
- **FR-011**: Envelope MUST contain: `schemaVersion`, `cipherSuiteId`, `nonce`, `recipients[]` (array of `{deviceId, sealedCEK}`), `ciphertext`, `mac`. Recovery of plaintext without all of these MUST be cryptographically infeasible.
- **FR-012**: `recipients[]` MUST be an array of arbitrary length from the first commit (membership-agnostic). In spec 011 length always equals 1; spec 014 (groups) and 015 (multi-device) extend to N without envelope format change.
- **FR-013**: Envelope MUST be CBOR-serialized via `kotlinx-serialization-cbor` (more compact and deterministic than JSON for binary content).
- **FR-014**: Backward-compatible reads MUST be possible: reading envelope with unknown future `cipherSuiteId` MUST return `CryptoError.CipherSuiteUnsupported`, not crash. Reading envelope with unknown future fields MUST ignore them (additive forward-compat).

**Hybrid encryption mechanics (US-2):**

- **FR-020**: `AeadCipher.encrypt(plaintext, key, aad)` MUST use XChaCha20-Poly1305 (24-byte nonce, 16-byte MAC). MUST be authenticated encryption — no CTR-only or CBC-without-MAC.
- **FR-021**: For each blob, a one-time CEK (Content Encryption Key) MUST be generated as 32 random bytes from libsodium CSPRNG. CEK MUST NOT be reused across blobs.
- **FR-022**: `AsymmetricCrypto.sealCEK(cek, recipientPub)` MUST use `crypto_box_seal` (anonymous sender). For each entry in `recipients[]`, CEK MUST be sealed for that recipient's Pub_X25519.
- **FR-023**: `AsymmetricCrypto.unsealCEK(sealedBlob, ownPriv)` MUST return the CEK or `CryptoError.MacFailed`. Unsealing for a recipient not in `recipients[]` → `CryptoError.RecipientNotFound`.

**Hashing and signing primitives (US-2):**

- **FR-024**: `HashFunction.hash(data)` MUST use BLAKE2b (256-bit output). Available for deduplication, integrity checks, future fingerprinting.
- **FR-025**: `DigitalSignature.sign(data, privEd25519)` and `verify(data, signature, pubEd25519)` MUST use Ed25519 via libsodium `crypto_sign`. Used for Pub-key publication anti-tamper (FR-006), future Jitsi room auth, future vendor integration HMAC.

**Storage adapter (US-3):**

- **FR-030**: `EncryptedMediaStorage.upload(uuid, envelope)` MUST store envelope at Firebase Storage path `/links/{linkId}/private-media/{uuid}`. UUID is UUIDv4. No metadata leaked in filename.
- **FR-031**: `EncryptedMediaStorage.download(uuid)` MUST return envelope bytes; deserialization happens upstream.
- **FR-032**: `EncryptedMediaStorage.delete(uuid)` MUST remove the blob from Storage. `EncryptedMediaStorage.exists(uuid)` MUST return whether blob is present.
- **FR-033**: Storage Security Rules MUST allow read/write of `/links/{linkId}/private-media/*` only for `adminId` and `managedDeviceFirebaseUid` of that link; other uids → `PERMISSION_DENIED`.

**Reference counting + cleanup (US-4):**

- **FR-040**: `BlobReferenceLedger` (SQLDelight) MUST track all references to each `<uuid>` from any source: `/config/current`, history snapshots (спек 009 retention 10), pending drafts.
- **FR-041**: Blob deletion MUST use reference counting — a blob is deleted only when refCount = 0.
- **FR-042**: Background reconciler MUST detect orphan blobs (in Storage with no ledger reference for ≥ 24h) and delete them. This is privacy hygiene; orphans MUST NOT accumulate indefinitely.
- **FR-043**: On revoke (спек 007 FR-033), recursive subtree delete MUST include `/links/{linkId}/private-media/` Storage path; no orphan blobs MAY remain after revoke. `Link.KNOWN_SUBCOLLECTIONS` ([Link.kt:37-44](../../core/src/commonMain/kotlin/com/launcher/api/link/Link.kt#L37)) MUST be extended to include `private-media`.
- **FR-044**: Storage delete failure (network unavailable) MUST be retried via WorkManager with limited attempts. After exhaustion — warning log, blob remains in Storage (known privacy risk).

**Error reporting (US-5):**

- **FR-050**: All crypto failures MUST be returned as `CryptoError` sealed sub-cases: `MacFailed(uuid)`, `KeyNotFound(alias, cause?)`, `BlobMissing(uuid)`, `CipherSuiteUnsupported(suiteId)`, `RecipientNotFound(deviceId)`, `SignatureVerifyFailed(deviceId)`.
- **FR-051**: Error messages and structured logs MUST NOT contain plaintext, Priv keys, CEK, or any byte content of blobs. Only categorical metadata: uuid, linkId, deviceId, error kind.
- **FR-052**: Errors MUST NOT crash the application. The library returns `Result<T, CryptoError>` — caller decides UX response.

**Recipient resolver (US-2):**

- **FR-060**: `RecipientResolver.resolveRecipients(linkId)` MUST return `List<DeviceIdentity>`. In spec 011 only implementation is `PairRecipientResolver` returning the other member of the pair.
- **FR-061**: Future spec implementations (`GroupRecipientResolver` in 014, `MyDevicesRecipientResolver` in 015) MUST conform to the same port; envelope format MUST NOT change when adding new implementations.

**Manual smoke test (US-6):**

- **FR-070**: A documented manual smoke procedure MUST exist at `smoke/011/README.md` describing how to test encryption + storage + decryption end-to-end on two real Android devices using synthetic 16-byte data. This is a gate before merge.

**Security and privacy:**

- **FR-080**: Plaintext bytes and Priv keys MUST NOT be persisted in any externally-accessible content provider on either device.
- **FR-081**: Encryption MUST be authenticated (AEAD) — no unauthenticated ciphers.
- **FR-082**: Network observers between devices and Firebase Storage MUST NOT be able to recover plaintext from observed traffic (verified by passive-MITM test).

**Out of scope (negative requirements):**

- **FR-090**: This spec MUST NOT implement `PrivateMediaResolver` (IconStorage namespace dispatch for `private:`) — that belongs to spec 012.
- **FR-091**: This spec MUST NOT introduce any UI changes (no Contacts Picker integration, no DocumentPicker, no tile changes) — all UI belongs to spec 012 onwards.
- **FR-092**: This spec MUST NOT encrypt `/config` itself — `/config` carries settings, not private user content.
- **FR-093**: This spec MUST NOT implement realtime stream encryption (SRTP/SFrame) — realtime media goes through Jitsi/SFrame in a future Jitsi-integration spec, never through our envelope.
- **FR-094**: This spec MUST NOT add screen mirroring, remote control, or DPC-mode features.
- **FR-095**: This spec MUST NOT introduce iOS support — out of project scope (ADR-001).
- **FR-096**: This spec MUST NOT introduce post-quantum cryptography — when libsodium adds it natively (~v1.0.20+), we update through app update with cipherSuiteId bump.

### Key Entities

- **DeviceKeyPair**: per-device asymmetric keys — `(Pub_X25519, Priv_X25519)` for encryption and `(Pub_Ed25519, Priv_Ed25519)` for signing. Generated on first launch. Priv'ы — в Keystore через AES-wrap. Pub'ы публикуются в Firestore после pairing.
- **DeviceIdentity**: `(deviceId, publicKey_X25519, publicKey_Ed25519, createdAt)` — публичная identity устройства, опубликованная в Firestore `/links/{linkId}/devices/{deviceId}`. Подписана Ed25519 владельца.
- **ContentEncryptionKey (CEK)**: 32 random bytes, генерируется на каждый blob, одноразовый, не Serializable, обнуляется в finally через `sodium_mlock`/`Arrays.fill`.
- **EncryptedEnvelope**: CBOR-сериализованная структура `(schemaVersion, cipherSuiteId, nonce, recipients[], ciphertext, mac)`. Хранится в Firebase Storage. Membership-agnostic — `recipients[]` массив произвольной длины.
- **Recipient**: `(deviceId, sealedCEK)` — entry в `recipients[]`. `sealedCEK` — CEK, зашифрованный через `crypto_box_seal` для Pub_X25519 этого получателя.
- **BlobReference**: `(uuid, refCount, lastSeenInConfig, lastSeenInHistorySlot)` — local ledger entry в SQLDelight. Считает ссылки на blob из всех источников.
- **OrphanBlob**: blob в Storage без ledger reference в течение ≥ 24h. Удаляется background reconciler'ом.
- **CryptoError**: sealed class — `MacFailed`, `KeyNotFound`, `BlobMissing`, `CipherSuiteUnsupported`, `RecipientNotFound`, `SignatureVerifyFailed`. Все ошибки крипто-операций возвращаются как `Result<T, CryptoError>`, не Exception.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Synthetic 16-byte blob is encrypted on admin device, uploaded to Storage, downloaded on Managed device, decrypted — bytes match exactly. End-to-end smoke runs in ≤ 60 seconds on two real Android devices (covers US-6, FR-070).
- **SC-002**: Zero plaintext bytes, Priv keys, or CEK material appear in logs, crash reports, analytics, or network payloads — verified by automated log-grep tests in CI (covers FR-051, FR-003).
- **SC-003**: A passive network observer between devices and Firebase Storage CANNOT recover plaintext from observed traffic (verified by integration test reading TLS-decrypted payloads — they're encrypted envelopes, not plaintext).
- **SC-004**: When refCount of `<uuid>` reaches 0, blob is removed from Storage within ≤ 5 minutes for ≥ 99% of attempts (covers US-4, FR-041).
- **SC-005**: After admin revokes a link, `/links/{linkId}/private-media/` Storage path is empty within ≤ 5 minutes for 100% of attempts (covers FR-043).
- **SC-006**: All `CryptoError` sub-cases are returned as `Result<T, CryptoError>` — no Exception escapes; covered by ≥ 6 unit tests, one per sub-case (covers US-5, FR-050).
- **SC-007**: All official libsodium test vectors (XChaCha20-Poly1305 from RFC 8439, X25519 from RFC 7748, Ed25519 from RFC 8032, BLAKE2b from RFC 7693, `crypto_box_seal` from libsodium official vectors) pass against our adapters — verified in Phase 3 contract tests (covers FR-020, FR-022, FR-024, FR-025).
- **SC-008**: Envelope roundtrip + backward-compat reads work — write envelope v1 with recipients[length=1], read; write envelope v1 with recipients[length=3] (forward-compat fixture), read for one recipient; write envelope with future cipherSuiteId, get `CipherSuiteUnsupported`. All in `commonTest` (covers FR-010, FR-012, FR-014).
- **SC-009**: Konsist fitness gates pass — `commonMain/api/crypto/` has zero imports of `com.goterl.lazysodium.*`, `android.*`, `com.google.firebase.*`. Adapter layer keeps vendor types internal (covers CLAUDE.md rule 1).
- **SC-010**: Exit ramp documented inline — every one-way door (libsodium choice, X25519+hybrid, Firebase Storage, Android Keystore) has documented switch-out cost in spec + plan (covers CLAUDE.md rule 3).

---

## Assumptions

- **Spec 007** is fully implemented and merged when 011 starts; pairing flow is the integration point for Pub-key publication.
- **Spec 008** is fully implemented and merged when 011 starts; `Contact.photoRef: String?` field is already in the wire-format and validated by 008's roundtrip tests. In 011 this field remains `null` — naturalized in spec 012.
- **Spec 009** is fully implemented and merged when 011 starts; Contacts Picker exists but is **not modified** by 011 (modified by 012).
- **Spec 006**'s `IconStorage` port signature ([IconStorage.kt:20](../../core/src/commonMain/kotlin/com/launcher/api/capability/IconStorage.kt#L20)) remains stable; 011 does **not** implement PrivateMediaResolver (deferred to 012). `private:<uuid>` URIs do not appear in any `/config` until 012 starts producing them.
- **Both devices run modern Android with Keystore** (Android 6+ for basic Keystore; Android 12+ for native Ed25519 — software fallback on older). Project minSdk is 30.
- **Firebase Spark plan is sufficient for 011 alone** — fundament не льёт реальных blob'ов (только синтетика в smoke). Реальное наполнение — спек 012. Migration to own backend documented in `SRV-CRYPTO-001` ([server-roadmap.md](../../docs/dev/server-roadmap.md)) — независимо от Firebase лимитов, триггер = запуск собственного backend проекта.
- **Cloudflare Worker** is not in the data path of crypto. Storage uploads/downloads go directly device↔Firebase Storage. The Worker remains the FCM-trigger relay only.
- **No server-side key escrow.** Intentional, aligns with e2e guarantee. Consequence — irrecoverable data on factory reset without revoke — accepted as UX cost of true privacy.
- **Reference counting includes history snapshots** from спек 009 (retention 10). A blob's refCount considers `/config/current` AND any `/config/history/{slot}` referencing it.
- **libsodium covers all foreseeable crypto needs** (encryption, hashing, signing, key exchange, HMAC, password hashing). Post-quantum will be added by libsodium upstream; we update through app version bump. X.509 PKI is the only foreseeable gap — handled via separate adapter if/when a vendor requires it.
- **Pre-production phase (until spec ~35)** — no real users yet. Wire-format discipline (`schemaVersion`, `cipherSuiteId`, roundtrip + backward-compat tests) maintained from day 1 anyway, to avoid retrofitting later.

---

## Зависимости-обещания (что 011 обязан выполнить за предыдущие спеки, что переехало в 012)

Спеки 6-9 заранее заложили крючки под 011. После разреза на 011/012 они распределены:

**Обязательства, остающиеся в 011 (инфраструктура):**

- **Спек 007 → расширить `Link.KNOWN_SUBCOLLECTIONS`** ([Link.kt:37-44](../../core/src/commonMain/kotlin/com/launcher/api/link/Link.kt#L37)) — добавить `"private-media"` для recursive revoke (FR-043).
- **Спек 007 → pairing-token wire-format не меняется** — поле `pairingKey` из ([pairing-token.md:79](../007-pairing-and-firebase-channel/contracts/pairing-token.md#L79)) **остаётся неиспользованным**. Pub-key publication идёт через Firestore `/links/{linkId}/devices/{deviceId}` после `consent.allow` (FR-006). schemaVersion остаётся 1.
- **Backlog → разрешить `TODO-DOC-001`** ([project-backlog.md:378](../../docs/dev/project-backlog.md#L378)) — создать ADR-007 (`TrustEdgeBootstrap` для per-device keys + Pub publication через Firestore) в начале спека 011.
- **Server-roadmap → создать `SRV-CRYPTO-001`** ([server-roadmap.md](../../docs/dev/server-roadmap.md)) — универсальный маршрут переезда крипто-инфраструктуры на собственный backend. Не привязан к Firebase лимитам.

**Обязательства, переехавшие в 012 (visible feature):**

- **Спек 006 → реализовать `PrivateMediaResolver`** под `IconStorage` ([icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48)) — **в 012**, не в 011. До 012 порт продолжает возвращать `Placeholder` для `private:<uuid>`.
- **Спек 008 → впервые заполнить `Contact.photoRef`** реальным значением — **в 012**. В 011 поле остаётся `null` как и сейчас.
- **Спек 008 → впервые эмитить `partialApplyReasons += media_decrypt_failed`** ([state-applied.md:65](../008-bidirectional-config-sync/contracts/state-applied.md#L65)) — **в 012**. В 011 enum value остаётся зарезервированным.
- **Спек 009 → подключить фото к Contacts Picker** ([009 spec.md:218](../009-admin-mode-flows/spec.md#L218)) — **в 012**, не в 011.
- **Compliance → обновить запись `POST_NOTIFICATIONS`** ([permissions-and-resource-budget.md:224](../../docs/compliance/permissions-and-resource-budget.md#L224)) — `deferred to spec 017 (balance alerts) / 018 (critical health push)` после перенумерации 2026-05-22. `READ_CONTACTS` не трогается в 011 (трогается в 012).

---

<!-- novice summary -->

## TL;DR (простым языком, для бабушки-владельца проекта и для будущего AI)

**О чём этот спек.** Это **криптографический фундамент проекта**. Сам по себе он ничего видимого не делает — никаких фотографий на плитках, никаких новых кнопок. Это «фундамент под домом»: его не видно, пока на нём ничего не построили. Видимый дом — спек 012 (фото контактов и личных документов), а спек 011 — это арматура и бетон.

**Почему фундамент отдельным спеком.** Раньше мы пытались в одном спеке сделать и крипто-машину, и фото контактов. Получалось 73 задачи и 7 недель в одной ветке — слишком большой PR, нарушает наш порядок разработки. Плюс фундамент **нужен не только для фото**: через несколько спеков он понадобится для двусторонних пар (013), семейных групп (014), нескольких устройств одного человека (015), Jitsi-звонков (потом), интеграции с клиниками (потом). Поэтому имеет смысл сделать фундамент один раз и универсальным.

**Что спек реально делает:**
1. **При первом запуске приложения** каждое устройство генерирует свои крипто-ключи (X25519 для шифрования + Ed25519 для подписи). Секретные ключи хранятся в Android Keystore — это специальная защищённая зона **внутри телефона** (не облако Google).
2. **При сканировании QR (спек 007)** оба устройства публикуют свои **публичные** ключи в Firestore. Подписывают публикацию, чтобы никто не подменил. Теперь пара устройств знает крипто-адреса друг друга.
3. **API для шифрования blob'а** — «дай мне байты и список получателей, я верну зашифрованный пакет». В 011 список всегда одного человека (член пары). В будущих спеках — N человек (группа, multi-device).
4. **Firebase Storage впервые в проекте** — нужно место для зашифрованных blob'ов. Зашифровали → положили → потом достали → расшифровали.
5. **Учёт и уборка** — если blob больше не нужен (ни в текущем `/config`, ни в истории), он удаляется. При revoke пары всё под `/links/{linkId}/` стирается.
6. **Тест на двух реальных устройствах** перед merge — 16 случайных байт зашифровались на одном телефоне, расшифровались на другом, hex совпал. Это компенсирует отсутствие визуального результата.

**Что спек НЕ делает (явно вынесено):**
- Фотографии на плитках — спек 012.
- Real-time звонки (как Jitsi/Zoom) — никогда не через наш envelope. Это другая криптография.
- Post-quantum — потом, когда библиотека дозреет.
- iOS — out of project scope.

**Главное архитектурное решение:** envelope (зашифрованный пакет) **не знает**, кто его расшифрует. Эту работу делает отдельный интерфейс `RecipientResolver`. Сейчас он умеет вернуть «другого члена пары»; в будущем будет уметь «всех членов группы» или «все мои устройства». **При этом сам формат envelope не меняется** — это значит, что когда мы дойдём до групп (014) или multi-device (015), нам **не придётся перешифровывать** уже залитые blob'ы. Это главная инвестиция спека.

**Зачем именно end-to-end (a не «Firebase правилами»):** правила Firebase защищают «доступ только своих». Но **сам Google технически имеет доступ** к файлам, если кто-то его попросит (суд, госорган, сотрудник). Фотографии родственников, документы, голосовые сообщения, видеозвонки — это слишком приватно. e2e снимает вопрос — Google видит только мусор, расшифровать его без наших ключей он не может.

**Crypto-стек:**
- Библиотека: **libsodium через Lazysodium-android**. Это та же библиотека, что в Signal, Discord, и многих других. Никакого «самописного шифрования».
- Шифрование: **XChaCha20-Poly1305** (для blob'а) + **X25519 + crypto_box_seal** (для упаковки ключа blob'а получателю).
- Подпись: **Ed25519**.
- Хеширование: **BLAKE2b**.
- Все это есть в одной библиотеке (1.2 MB на телефон), весит одинаково, дёргаем по мере нужды.

**Что зафиксировано в clarify-сессиях:**
- 2026-05-21: per-device keys + hybrid encryption + membership-agnostic envelope (8 решений C-1..C-8).
- 2026-05-22: **разрез на 011 (фундамент) и 012 (фото)**; расширение фундамента на Hash + Signature (универсальная крипто-ABI для будущих интеграций); перенумерация спеков 015→013, 016→014, 017→015, 018→016, 012(balance)→017, 013(offline)→018, 014(onboarding)→019.

**Будущие клиенты этого фундамента (см. таблицу в начале):**
- **спек 012** — фото контактов и личных документов (первый видимый продукт на фундаменте).
- **спек 013** — двусторонние пары (бабушка тоже может управлять).
- **спек 014** — семейные группы (WhatsApp-style).
- **спек 015** — multi-device одного владельца + recovery при потере.
- **спек 016** — ротация ключей.
- **TBD-Jitsi** — зашифрованный доступ к Jitsi-комнатам (ключ доступа, не realtime stream).
- **TBD-Audio/Video messages** — голосовые/видеосообщения как WhatsApp voice (asynchronous).
- **TBD-Vendor** — интеграция с клиниками, центрами поддержки.
- **TBD-Hardware** — медицинские/охранные датчики.

**Server-roadmap:** SRV-CRYPTO-001 — универсальный маршрут переезда крипто-инфраструктуры на собственный backend. Триггер — запуск собственного backend проекта (после спека ~35), не привязан к Firebase лимитам.
