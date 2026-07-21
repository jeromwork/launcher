---
id: TASK-141
title: >-
  Crypto wire formats: move version handling out of the crypto modules + retire
  legacy paths
status: In Progress
assignee: []
created_date: '2026-07-20 09:37'
updated_date: '2026-07-20 14:46'
labels:
  - wire-format
  - crypto
  - phase-2
milestone: m-2
dependencies:
  - TASK-138
priority: medium
ordinal: 141000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

TASK-138 переводит форматы данных на новую систему версий. Шесть форматов из неё **намеренно исключены** — те, что принадлежат крипто-модулям: хранилище ключа восстановления, зашифрованный конверт конфига, обёртка ключа на диске, личность устройства, конверт спаривания и заготовка под социальное восстановление.

Причина исключения — решение владельца от 2026-07-20: **крипта не должна ничего знать о системе версий.** Её дело — шифровать и подписывать, а не решать, читаемый ли документ.

**Правка модели (2026-07-20, при работе):** `DeviceIdentity` названа в исходной карточке «образцом уже правильным» — **это неверно**, проверено по коду. Гейт действительно в адаптере, но:
- поле `schemaVersion: Int` и `@SerialName` сидят **в самом крипто-типе**;
- константа `SUPPORTED_SCHEMA_VERSION` живёт **в крипто-модуле** (`CryptoEnvelopeWireFormat.kt`), её же импортит гейт в адаптере;
- `@Serializable` на типе используется **только тестом** — прод-путь в Firestore идёт через ручной `toMap`/`fromMap` в адаптере.

То есть образца «крипта ничего не знает о версиях» в коде пока **нет ни у одного из шести форматов** — все несут `schemaVersion` внутри крипто-типа.

Остальные пять форматов надо привести к тому же виду.

**Отдельный вопрос — `KeyBlob`.** Это локальный файл, который крипта пишет сама себе и сама читает; он никуда не уезжает и второго читателя у него нет. Предложение: оставить ему собственную приватную версию, не подключая к общей дисциплине. Вытаскивать из `SecureKeyStore` всю запись на диск ради единообразия — перекройка ответственности модуля, а не работа с версиями.

**Заодно — уборка мёртвого кода.** При инвентаризации нашлись пути, которые, судя по всему, не используются. Их надо проверить и, если подтвердится, удалить, а не конвертировать.


## Находки при работе (2026-07-20) — две развилки для владельца

**Развилка A — версия криптографически связана в `Envelope`.** У `Envelope` (`:core:keys`) `schemaVersion` **входит в AAD** — authenticated data вычисляется читателем из `(namespace, key, schemaVersion)`. Это значит: убрать версию из крипто-типа буквально = сломать AEAD-контракт = **перешифровать все существующие зашифрованные документы**. Это one-way door (CLAUDE.md rule 3), не механический перенос.

Разведение, которое НЕ ломает контракт: крипта перестаёт **решать** про версию (убрать константу «текущей версии», убрать любые сравнения, гейт в адаптер), но **продолжает включать переданное ей число в AAD** — как непрозрачный вход AEAD, не зная про дисциплину версионирования. Это укладывается в AC #1 («не содержит разбора, сравнения или проверки версии») — включение в AAD не есть проверка. Но это тонкий дизайн, а не «переставить поле».

**Развилка B — вынос `@Serializable` из крипто-типов = TASK-144.** Чтобы крипто-тип совсем «не знал» про wire (убрать `schemaVersion`-поле и `@Serializable`/`@SerialName`), нужен DTO-слой в адаптере. Нужен ли DTO вообще — это **ровно вопрос TASK-144** (domain vs DTO annotation, rule 1), который ещё не решён. Пока он открыт, минимальный безопасный ход: убрать из крипты **константу и сравнения** (крипта не знает «текущую» версию), но `@Serializable` и поле-переносчик оставить до решения TASK-144.

**Мёртвый код требует расследования, не grep.** `EncryptedEnvelope` как формат нигде в проде не конструируется, но порт `EncryptedMediaStorage` живой и wired в DI (иконки). Прежде чем удалять — понять, создаётся ли `EncryptedEnvelope` внутри `WorkerEncryptedMediaStorage`.

**Что уже сделано:** переименование `cryptokit.*` → `family.*` (первый коммит), правило `NoLegacyFamilyNamespaceTest` инвертировано, всё зелёное.

## Зачем

Без этого либо крипта тащит зависимость на модуль версий (и ломается барьер, который ставили ради возможности вынести её в отдельный репозиторий), либо шесть форматов навсегда остаются на старой целочисленной версии и расходятся с остальной кодовой базой.

## Что входит технически (для AI-агента)

**Форматы к переводу** (версия — инертное поле, разбор и гейт снаружи, `verifyCryptoIsolation` не трогать):

- `Envelope` (`:core:keys`) — раскладка по полям Firestore уже в `:app`; переехать должна только проверка версии из `EnvelopeConfigCipherImpl.open()`. **Важно**: там сейчас в одном `if` смешаны две проверки — «формат новее» и «алгоритм незнакомый». Вторая (H-3) — законное дело крипты и остаётся, у `Envelope` для этого есть поле `algorithm`. Уезжает только первая.
- `RecoveryKeyBackupBlob` (`:core:keys`) — `RecoveryBlobCodec` держит кодек и гейт; гейт переезжает в `WorkerRecoveryKeyBackup` (`:app`). Дублирующая проверка на сервере (`workers/backup/src/index.ts:166`) уже есть, риск низкий.
- `DeviceIdentity` (`:core:crypto`) — гейт уже снаружи; довести до конца: вынести и константу `SUPPORTED_SCHEMA_VERSION` туда же, где гейт.
- `EncryptedEnvelope` (`:core:crypto`) — сериализация уже в `:core`, гейта нет нигде. Сперва проверить, живой ли формат (см. ниже).
- `EscrowBundle` (`:core:crypto`) — заглушка, не `@Serializable`, нигде не сериализуется. Версия не нужна, пока не появится реальная реализация.
- `KeyBlob` (`:core:crypto`) — запись, чтение и гейт целиком внутри `SecureKeyStore.android.kt`. Решить: приватная версия крипты (предложение) либо вынос персистентности.

**Проверить и, если мёртвое, удалить:**

- `EncryptedEnvelope` / `EncryptedMediaStorage` — единственное место создания в продакшене найдено в отладочном экране `Spec011SmokeDebugActivity`. Настоящего вызова нет.
- `FirestoreRecoveryKeyBackup` — DI (`F018KeysBackendModule`) подключает `WorkerRecoveryKeyBackup`; Firestore-путь оставлен «для преемственности спеки 018».
- Легаси-ветка в `FirestoreEnvelopeStorage` по тому же признаку.

**Переименование пакетов — первым коммитом, до всего остального.** `cryptokit.crypto` → `family.crypto`, `cryptokit.keys` → `family.keys`.

**Это отмена TASK-56** (завершена 2026-07-13), которая делала ровно обратное по мандату владельца 2026-06-26 «слово family меня смущает». 2026-07-20 владелец уточнил: тогда `family` читалось как аудитория, имелось в виду семейство продуктов; в этом значении корень принят. `cryptokit` отвергнут как общий корень — это один из модулей внутри выносимой части, а не её имя.

**Блокирующая деталь:** [NoLegacyFamilyNamespaceTest.kt](../../core/src/androidUnitTest/kotlin/com/launcher/test/fitness/NoLegacyFamilyNamespaceTest.kt) запрещает импорты `family.crypto` / `family.pairing` / `family.keys` — то есть ровно то, к чему мы возвращаемся. Список инвертируется **в том же коммите**, иначе сборка не пройдёт. Новый смысл правила: запрещён `cryptokit.*`, чтобы старый корень не пополз обратно. Корень `family` = семейство продуктов (лаунчер, мессенджер, галерея), **не** целевая аудитория — записать это комментарием в заголовке обоих модулей, слово двусмысленно именно в этом продукте. `:core:push` уже `family.push`, `:core:wire` переведён в TASK-138 (коммит `39e36c1`), эти два — последние.

Объём: 178 файлов плюс четыре строки в [app/proguard-rules.pro:5](app/proguard-rules.pro#L5). Механически безопасно: полиморфной сериализации в модулях нет, имена пакетов на провод не попадают, ломаются только импорты — но правило proguard `-keepnames class cryptokit.crypto.api.values.KeyBlob` молча перестанет совпадать, если его не подвинуть, и обфускация съест имя класса. Отдельный коммит нужен, чтобы переименовательный шум не утопил содержательные правки в ревью.

**Firestore rules.** Правила для `/users/{uid}/recovery-key/{docId}` и `/links/{linkId}/devices/{deviceId}` сравнивают версию численно. При переводе на точечную строку — использовать хелпер `versionOrder()` (см. TASK-138). Строковое сравнение здесь ломает защиту H-2 от отката (FR-028a).

## Состояние

**Draft.** Выделена из TASK-138 2026-07-20 по решению владельца: «крипта ничего не знает о версиях, занимаемся сейчас не криптой». Работа в TASK-138 по этим форматам была начата и откачена (не коммитилась).


## Implementation plan (разведка 2026-07-21, готов к исполнению)

Разведка показала: это три связанные под-задачи, все в крипто-зоне. Порядок — от безопасного к тонкому.

### A. Удалить мёртвый media-storage feature (spec 011) — AC #5

**Подтверждено мёртвым** (grep по всему дереву):
- `EncryptedEnvelope` конструируется только в `Spec011SmokeDebugActivity` (debug) и тестах.
- `EncryptedMediaStorage.upload/download` в проде **не вызываются** нигде.
- `BackgroundReconciler.reconcile()` **не вызывается** нигде (конструируется в DI `CryptokitModule:85`, но метод мёртв). `list/delete` работают, но чистят то, что никто не загружает.

**Footprint:** (уточнено 2026-07-21 до ~19)

**Изоляция подтверждена:** `PairingCryptoCoordinator` media НЕ трогает — feature изолирован от живого pairing. `CryptoEnvelopeWireFormat.kt` — только `const SUPPORTED_SCHEMA_VERSION`, удаление `EncryptedEnvelope` его не задевает.

**Удалить целиком:** `EncryptedEnvelope.kt`, `EncryptedMediaStorage.kt`, `WorkerEncryptedMediaStorage.kt`, `InMemoryEncryptedMediaStorage.kt`, `FakeEncryptedMediaStorage.kt`, `BackgroundReconciler.kt`, `EncryptedEnvelopeSerializationTest.kt`, `Spec011RoundtripSmokeTest.kt`, `Spec011SmokeDebugActivity.kt` (12 media-упоминаний — весь экран). **Проверить на сиротство:** `ClearDataDetector` (используется только reconciler'ом + CryptokitModule — если reconciler уходит, проверить, жив ли он ещё где-то как clear-data sentinel).

**Править (убрать ссылки/DI):** `CryptokitModule` (reconciler+storage+clearData wiring), `BackendInit` mock+real (storage single), `FirestoreLinkRegistry` (nullable `encryptedMediaStorage` param), `IconStorage.kt`/`Link.kt` (комментарии), `NoFakeCryptoInAppTest` (список fakes), `CryptoEnvelopeWireFormatTest` (если тестит EncryptedEnvelope). Старый footprint:

**Старый список (12 файлов):** `EncryptedEnvelope.kt`, `EncryptedMediaStorage.kt`, `WorkerEncryptedMediaStorage.kt`, `InMemoryEncryptedMediaStorage.kt`, `BackgroundReconciler.kt`, DI-wiring в `CryptokitModule` + `BackendInit` (mock+real), `Spec011SmokeDebugActivity`, ссылки в комментариях `IconStorage.kt`/`Link.kt`, тесты. Удаление обратимо (git), два-way door.

**Выгода:** после удаления `EncryptedEnvelope` **не нужно выносить** — минус один крипто-формат из B.

### B. Вынести версию из живых крипто-форматов — AC #1, #2, #3

**Общая константа — ключевая деталь.** `SUPPORTED_SCHEMA_VERSION` в `CryptoEnvelopeWireFormat.kt` (крипто) делится `DeviceIdentity`, `EncryptedEnvelope` и потребителями в `:core:keys` (`BackupError`, `RecoveryBlobCodec`). Вынос не изолирован по формату — надо разнести per-format в адаптеры.

Живые форматы к выносу (после A остаётся 3):
- **`DeviceIdentity`** — версия НЕ крипто-связана (не в подписи `signedPayloadBytes`, не шифруется). Чистый вынос: убрать поле `schemaVersion`, `@Serializable`/`@SerialName` (по TASK-144 крипто-тип без сериализации; сейчас `@Serializable` юзается только тестом, прод — ручной `toMap` в `FirestoreDeviceIdentityRepository`). Конструкторы: `PairingCryptoCoordinator` (95,105), `FirestoreDeviceIdentityRepository` (153).
- **`Envelope`** — версия В AAD (крипто-связана). По решению владельца 2026-07-21: крипта принимает AAD как **готовые непрозрачные байты**, версию подмешивает слой над криптой (`EnvelopeRemoteStorage.aadFor`). Убрать из `EnvelopeConfigCipherImpl.open` строку `if (envelope.schemaVersion > Envelope.SCHEMA_VERSION)` (гейт наверх). **H-3** (проверка `algorithm != ALGORITHM_V1`) — остаётся в крипте, это не версия (AC #3).
- **`RecoveryKeyBackupBlob`** — `RecoveryBlobCodec` держит кодек+гейт; гейт наверх в `WorkerRecoveryKeyBackup`.

### C. KeyBlob — вынести персистентность из крипты (AC #4) — РЕШЕНО владельцем 2026-07-21

`KeyBlob` — локальный файл-сейф с обёрнутым ключом (`<filesDir>/keys/<id>.blob`), крипта (`SecureKeyStore.android`) пишет и читает его сама, наверх не уезжает. Владелец выбрал **вариант 2** (строго по букве правила): крипта не знает о версиях даже для своего локального файла.

**План:** `SecureKeyStore` перестаёт сериализовать/парсить `KeyBlob` и проверять версию (убрать строку `if (blob.schemaVersion > KeyBlob.CURRENT_SCHEMA_VERSION)`). Крипта отдаёт/принимает **обёрнутые байты** (`wrappedKey`, `iv`, `wrapKeyAlias`, `algorithm`, `createdAt`) через порт; новый адаптер персистентности (androidMain :core или :app) добавляет версию, сериализует `KeyBlob` и пишет/читает `.blob`-файл. Тип `KeyBlob` (с `@Serializable` + версией) уезжает из `:core:crypto` в адаптерный слой. `CURRENT_SCHEMA_VERSION` и гейт — туда же. `ByteArrayBase64Serializer` — проверить, используется ли ещё в крипте после выноса.

**Осторожно:** `SecureKeyStore.android` — это `SecureKeyStore` expect/actual. Вынос персистентности меняет его контракт (порт). Проверить всех потребителей `SecureKeyStore` и `proguard -keepnames KeyBlob` (переедет вместе с типом).

### D. Firestore rules + server-log — AC #6

Правила `/users/{uid}/recovery-key/{docId}`, `/links/{linkId}/devices/{deviceId}` сравнивают версию численно (int). После B (формат пишет строку) — перевести на `versionOrder()`, тест границы 9→10. Записать в `server-log.md`: сервер трактует версию как **opaque** (rule 13, per TASK-144 server note).

**Почему план, а не наспех-код:** крипто-зона, ошибка дорога (rule 3). Вынос через общую константу + 12-файловое удаление feature — крупный связный рефакторинг, надёжнее свежим заходом по этой карте, чем в конце длинной сессии.

## Decision — Part B DTO strategy (2026-07-21, подтверждено владельцем)

Крипто-тип каждого из трёх живых форматов становится чистым: без поля `schemaVersion`, без `@Serializable`/`@SerialName` (rule 1 crypto-exception). Форма «слоя над криптой» выбирается по тому, как формат сериализуется в **проде** (рабочее приложение, не тест):

- **`DeviceIdentity`** (`:core:crypto`) и **`Envelope`** (`:core:keys`) — в проде раскладываются вручную в `Map` (`toMap`/`fromMap` в `FirestoreDeviceIdentityRepository`, `encode`/`decode` в `FirestoreEnvelopeStorage`); `@Serializable` дёргает только юнит-тест. → ДТО-класс НЕ заводим: ручная map-функция и есть объявленный wire-контракт (список полей + проверки + гейт версии). Снимаем `@Serializable`/`@SerialName` и поле `schemaVersion` с крипто-типа; версия-константа и гейт живут в адаптере. Комментарий-пометка у map-функции: «если формат переедет на JSON-строку — стандартизованная форма = `@Serializable`-DTO класс». Для `Envelope` дополнительно: гейт версии переезжает из `EnvelopeConfigCipherImpl.open:99` в адаптер `decode`; H-3 (проверка `algorithm`) **остаётся** в крипте (AC #3).
- **`RecoveryKeyBackupBlob`** (`:core:keys`) — в проде реально сериализуется kotlinx-JSON (`RecoveryBlobCodec`). → заводим `@Serializable`-DTO класс в адаптерном слое + маппер + гейт версии там же.

Версия в AAD `Envelope`: остаётся литерал `"family-storage::v1"` в `aadFor` (это версия схемы привязки), поле `schemaVersion` документа НЕ подмешивается в AAD — **перешифровки нет** (развилка A закрыта: aadFor не читает поле версии). Существующих пользовательских данных нет — ломать нечего. Реализация — по одному формату отдельными коммитами (`DeviceIdentity` → `Envelope` → `RecoveryKeyBackupBlob`). `SUPPORTED_SCHEMA_VERSION` уезжает из `:core:crypto` (`CryptoEnvelopeWireFormat.kt`) в адаптерный слой per-format.

## Прогресс (2026-07-21)

- **A** — мёртвый media-storage удалён (988570d). AC #5.
- **B1** `DeviceIdentity` (ab90a26), **B2** `Envelope` (b27a903), **B3** `RecoveryKeyBackupBlob` (74f684e) — крипто-типы без версии и `@Serializable`, версия в адаптере (int). AC #1/#2/#3.
- **C** `KeyBlob` — персистентность вынесена в `FileKeyBlobStore` (:core), `SecureKeyStore` только wrap/unwrap через порт `KeyBlobStore` (195bf3c). AC #4.
- **D** — **ещё не сделано** (AC #6). Разведка выполнена, карта ниже. НЕ начинать в конце длинной сессии — это security-rules + Worker, нужен Firebase-эмулятор для проверки.

### Part D — карта для исполнения (разведка 2026-07-21)

Перевести 6 форматов с int-версии на точечную строку + 3-полевой заголовок `family.wire.WireVersion`/`WireVersionHeader`. Эталон: `core/src/androidRealBackend/.../adapters/auth/IdentityDocumentWireFormat.kt` — `header()` строит `mapOf("schemaVersion" to sv.toString(), "minReaderVersion" to ..., "minWriterVersion" to ...)` из `WireVersion(1,0)`. Для `@Serializable`-DTO: реализовать `WireVersionHeader` + `@EncodeDefault(EncodeDefault.Mode.ALWAYS)` на трёх полях (иначе `VersionFieldsAlwaysEncoded` fitness падает).

Форматы + где версия:
1. `FirestoreDeviceIdentityRepository` (`WIRE_SCHEMA_VERSION Int=1`, toMap/fromMap). **Правило `/links/{linkId}/devices` УЖЕ строковое** (`firestore.rules:274-314`, `hasValidVersionHeader`) — адаптер сейчас пишет int и его записи **уже отвергались бы**; перевод адаптера чинит. Правило НЕ трогать.
2. `FirestoreEnvelopeStorage` (`WIRE_SCHEMA_VERSION Int=1`, encode/decode). Правило `/users/{ns}/data/{key}` (`firestore.rules:520-525`) — сырой `>=`, нет type-check → добавить `hasValidVersionHeader` + `versionOrder`.
3. `FirestorePublicKeyDirectory` (`PUBKEY_SCHEMA_VERSION Int=1`, 2 write-сайта 78/85, **read-гейта нет**). Правила `firestore.rules:536-543` — owner-only, версию не валидируют.
4. `FirestoreRecoveryKeyBackup` (использует `RecoveryBlobJsonCodec.WIRE_SCHEMA_VERSION`, encode/decode). Правило `/users/{uid}/recovery-key` (`firestore.rules:431-469`) — **`is int` + сырой `>=`** (строка 465) → перевести на `hasValidVersionHeader` + `versionOrder`. **AC #6 явно про это + devices.** Осторожно: имена полей в правиле (`kdfSalt`, `wrappedRootKey`) не совпадают с адаптером (`salt`, `ciphertext`) — pre-existing, вне scope версии.
5. `RecoveryBlobJsonCodec` + `RecoveryKeyBackupBlobDto` (`WIRE_SCHEMA_VERSION Int=1`, DTO `schemaVersion: Int` строка 101). Перевести DTO на `WireVersionHeader`, decode-гейт на `versionOrder`. **Серверный близнец** — `workers/backup/src/index.ts:166-173` проверяет `typeof number` → перевести на строку + `versionOrder`; `MAX_SUPPORTED_SCHEMA_VERSION` env (`workers/backup/src/env.ts:13`, `wrangler.toml [vars]`) `"1"` → `"1.0"`.
6. `KeyBlob` (`core/src/commonMain/.../adapters/crypto/KeyBlob.kt`, `CURRENT_SCHEMA_VERSION Int=1`, локальный файл — НЕ Firestore). Перевести на `WireVersionHeader`, гейт в `FileKeyBlobStore.read`.

Хелперы правил: `versionOrder(v)` (`firestore.rules:48-50`), `maxAcceptedReaderVersion()`→`"1.0"` (55-57, поднять перед деплоем строгих правил), `hasValidVersionHeader(d)` (67-74). Опорные versionOrder-правила для копирования: `/users/{uid}/config` (474-484), `/links/{linkId}/state`, `identity-links`.

Тесты/проверка: `firestore-tests/` — `npm test` (Firebase-эмулятор + vitest). `rules.f5.recovery.test.ts` фикстура `validVaultV1()` пишет `schemaVersion: 1` int (строка 56) → перевести на строку. Golden-фикстуры: `core/keys/src/jvmTest/resources/fixtures/recovery-blob-v1/v2*.json` (int → строковые близнецы). `KeyBlobWireFormatTest` + `RecoveryBlobJsonCodecTest` — inline-фикстуры int → строка.

server-log (rule 13): `docs/dev/server-log.md` — anchor `A-1 · Sealed blob storage`; добавить короткую заметку (Worker `MAX_SUPPORTED_SCHEMA_VERSION`/`typeof number` теперь строка) + строку в `## Journal` (2026-07-21).

Финал: fitnessCheck (allowlist `CRYPTO_PENDING_TASK_141` в `ArchitectureFitnessTest.kt` **опустеет** — сигнал что TASK-141 приземлился) + unit + `firestore-tests npm test` + workers test.

**Lockout-риск**: каждый путь, где `is int`→строка, отвергнет старые int-записи в момент деплоя. Деплой правил координировать с выкаткой app. `maxAcceptedReaderVersion()` — потолок против lockout.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Ни один модуль крипты (`:core:crypto`, `:core:keys`) не содержит разбора, сравнения или проверки версии формата; `verifyCryptoIsolation` и `verifyKeysIsolation` остались в исходном виде, без послаблений
- [ ] #2 [hand] Проверка «можно ли читать документ» для каждого крипто-формата живёт в адаптере, по образцу `FirestoreDeviceIdentityRepository`
- [ ] #3 [hand] Проверка незнакомого алгоритма шифрования (H-3) осталась внутри крипты и покрыта тестом — она не является проверкой версии
- [ ] #4 [hand] По `KeyBlob` принято и записано решение: приватная версия крипты либо вынос персистентности
- [ ] #5 [hand] Мёртвые пути проверены и удалены либо подтверждены как живые с объяснением, почему остаются
- [ ] #6 [hand] Firestore rules для vault восстановления и devices переведены на точечную строку через `versionOrder()`; защита от отката сохранена и проверена тестом на границе 9→10
<!-- AC:END -->

