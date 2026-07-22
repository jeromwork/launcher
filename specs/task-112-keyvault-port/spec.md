# Feature Specification: KeyVault port — операции над data-ключами (граница 2)

**Feature Branch**: `task-112-keyvault-boundary-reconcile`
**Created**: 2026-07-22
**Status**: Draft
**Input**: TASK-112 — порт `family.keys.api.KeyVault`, граница 2 (симметричные операции над data-ключами) из трёхграничной vault-модели. Контракт зафиксирован в [`docs/architecture/crypto-key-hierarchy.md`](../../docs/architecture/crypto-key-hierarchy.md) §Key vault (research-reconciled 2026-07-22).

## Обзор для владельца *(mentor-detail)*

<!-- MENTOR-DETAIL:BEGIN -->
Простыми словами. В приложении есть «мастер-ключ» пользователя (RootKey), из которого детерминированно выводятся отдельные ключи под каждую задачу («purpose»): один для шифрования настроек, один для recovery-блоба, один для будущего мессенджера и т.д. Сегодня код, которому нужно что-то зашифровать, **сам достаёт сырые байты ключа** и сам зовёт алгоритм шифрования. Это опасно (байты ключа гуляют по коду и могут утечь в лог) и дорого при переезде на новую платформу (iOS, Rust) — придётся переписывать каждое место.

`KeyVault` — это «окошко в сейф»: код просит **операцию** («зашифруй вот эти данные ключом для настроек»), а не сам ключ. Ключ никогда не выходит наружу. Единственное исключение — узкая, явно помеченная «форточка» `exportDerivedKey` для внешних крипто-библиотек (будущий мессенджер openmls, рукопожатие pairing), которые обязаны управлять сырьём сами.

Важно: `KeyVault` отвечает **только** за симметричные операции над данными (граница 2). Подпись твоей личности и обмен ключами (граница 1) — это уже готовый `AsymmetricCrypto`. Хранение ключа в железе телефона и «насколько крепко» (граница 3) — это готовый `SecureKeyStore`. Мы их не трогаем и не дублируем.

Конечная польза для пользователя: (1) данные шифруются единообразно и без риска утечки ключа; (2) когда мы добавим iOS или перенесём крипту в Rust — ни одна фича не сломается, потому что все они говорят с портом, а не с конкретной платформой.
<!-- MENTOR-DETAIL:END -->

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Шифрование настроек идёт через порт (Priority: P1)

Существующее шифрование конфигурации (`ConfigCipher2`) перестаёт доставать сырые байты derived-ключа и вместо этого зовёт `KeyVault.aeadSeal(Purpose.CONFIG, …)` / `aeadOpen(…)`. Поведение снаружи не меняется — тот же зашифрованный конфиг читается и пишется, но ключ больше не покидает порт.

**Why this priority**: это MVP-срез. Без перевода реального потребителя (`ConfigCipher2`) порт — мёртвая абстракция (нарушение rule 4). Один этот срез уже доказывает, что граница проведена верно и раскодирование работает.

**Independent Test**: roundtrip-тест — зашифровать конфиг через `KeyVault.aeadSeal`, расшифровать через `aeadOpen`, сравнить с исходником; плюс backward-compat: конфиг, записанный старым `ConfigCipher2`, читается новым путём (тот же derived-ключ, тот же алгоритм).

**Acceptance Scenarios**:

1. **Given** валидный RootKey и plaintext конфига, **When** вызывается `aeadSeal(Purpose.CONFIG, plaintext, aad)`, **Then** возвращается `Ciphertext`, а `aeadOpen` того же `Ciphertext` с тем же `aad` возвращает исходный plaintext.
2. **Given** `Ciphertext`, записанный до миграции (старый `ConfigCipher2`), **When** он читается через `aeadOpen`, **Then** расшифровка успешна (формат и derived-ключ идентичны).
3. **Given** подделанный `Ciphertext` (перевёрнут байт), **When** `aeadOpen`, **Then** бросается `VaultException.CorruptedCiphertext`.

---

### User Story 2 — Узкая форточка экспорта для внешних крипто-либ (Priority: P2)

Внешние библиотеки, которые обязаны сами держать сырьё ключа (будущий openmls — signature key; pairing-рукопожатие `snow` — Noise static key), получают байты **только** через явный `exportDerivedKey(purpose, context, length)`. Возвращаемый `DerivedKeyBytes` — `AutoCloseable`, обнуляется по выходу из `use { }`.

**Why this priority**: разблокирует downstream (TASK-67 pairing, TASK-124 MLS), но сам по себе не нужен для MVP шифрования конфига. Проектируется сейчас, чтобы форточка была узкой и аудируемой с первого дня, а не добавлялась задним числом как широкая дыра.

**Independent Test**: fake-adapter тест — `exportDerivedKey(Purpose.NOISE_STATIC, context, 32)` возвращает детерминированные 32 байта для фиксированного RootKey+context; повторный вызов с тем же входом даёт те же байты (идемпотентность derivation); после `use { }` буфер обнулён.

**Acceptance Scenarios**:

1. **Given** RootKey и `Purpose.NOISE_STATIC`, **When** `exportDerivedKey(purpose, context, 32)`, **Then** возвращается `DerivedKeyBytes` длины 32, детерминированно выведенный из RootKey+purpose+context.
2. **Given** полученный `DerivedKeyBytes`, **When** блок `use { }` завершается, **Then** внутренний буфер заполнен нулями.

---

### User Story 3 — Сырьё RootKey больше не доступно снаружи (Priority: P3)

Публичный класс `RootKey(val bytes: ByteArray)` понижается до `internal` (живёт только в impl-слое). Внешний код физически не может достать сырьё root-ключа — только через операции `KeyVault`. `KeyRegistry.derive` остаётся internal-хелпером внутри impl `KeyVault`.

**Why this priority**: closes rule-1 трещину (публичные сырые байты). Не блокирует MVP, но без этого граница «дырявая» — legitimate call site может случайно вытащить сырьё в лог/IPC.

**Independent Test**: fitness/компиляционный тест — попытка обратиться к `RootKey.bytes` из модуля вне `family.keys.impl` не компилируется; grep-fitness: `RootKey(` не встречается в публичных сигнатурах.

**Acceptance Scenarios**:

1. **Given** кодовая база после миграции, **When** внешний модуль пытается сослаться на `RootKey`, **Then** это недоступно (internal visibility).
2. **Given** impl-слой `KeyVault`, **When** ему нужен derived-ключ, **Then** он использует internal `KeyRegistry.derive`, не экспонируя сырьё наружу.

### Edge Cases

- **Неизвестный purpose**: если вызывающий передаёт purpose вне enum (теоретически невозможно при enum, но при будущем `Purpose.External` — да) → `VaultException.UnknownPurpose`.
- **Неподдерживаемая версия ciphertext**: cleartext-префикс версии указывает на неизвестную схему → `VaultException.UnsupportedSchemaVersion(version)` (версия читается ДО расшифровки — консенсус age/JWE/MLS).
- **Hardware keystore недоступен** на устройстве → `VaultException.HardwareBackedKeystoreUnavailable` (адаптер решает: fallback на software-wrapped или отказ).
- **Vault заблокирован** (будущий passphrase/biometric gate — не сегодня) → зарезервировано `VaultException.VaultLocked`; в MVP не бросается.
- **Пустой plaintext / пустой aad**: валидная операция (AEAD допускает пустой plaintext); тест на это.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Система MUST предоставлять порт `KeyVault` в domain-слое (`family.keys.api`, commonMain) с методами `aeadSeal(purpose, plaintext, aad) → Ciphertext`, `aeadOpen(purpose, ciphertext, aad) → ByteArray`, `mac(purpose, message) → Mac`, `exportDerivedKey(purpose, context, length) → DerivedKeyBytes`.
- **FR-002**: Порт MUST охватывать **только** симметричные операции над data-ключами (граница 2). Он MUST NOT содержать операций `sign`/`agree` (граница 1 — `AsymmetricCrypto`) и MUST NOT содержать поля/операций capability/security-level (граница 3 — `SecureKeyStore`).
- **FR-003**: `Purpose` MUST быть закрытым enum `{ CONFIG, MLS_SIGNATURE, NOISE_STATIC, RECOVERY_BLOB }`. Расширение сверх ~10 purposes — аддитивным вариантом `Purpose.External(labelBytes)`, не переходом на свободную строку.
- **FR-004**: Ошибки MUST моделироваться sealed-иерархией `VaultException` по категориям (hardware / user-action / data-integrity / programming-error), бросаться через `@Throws`. Методы MUST быть синхронными (не `suspend`) — FFI-friendly, все референс-либы синхронны.
- **FR-005**: `Ciphertext` MUST нести `schemaVersion` как cleartext-префикс, читаемый adapter DTO **до** крипто-операции. Крипто-примитив MUST NOT читать версию из своего входа (крипто-исключение rule 1, K5/TASK-141).
- **FR-006**: `DerivedKeyBytes` MUST быть `AutoCloseable` с обнулением буфера в `close()` (best-effort zeroize, паттерн age/OpenMLS).
- **FR-007**: Существующий `ConfigCipher2` MUST быть переведён на `KeyVault.aeadSeal`/`aeadOpen`, перестав обращаться к сырым байтам `DerivedKey`. Совместимость чтения ранее записанных конфигов MUST сохраняться (тот же derived-ключ, тот же алгоритм).
- **FR-008**: `RootKey` MUST быть понижен до `internal` visibility (`family.keys.impl.*`); публичный `RootKey(val bytes: ByteArray)` MUST быть удалён. `KeyRegistry.derive` MUST остаться internal-хелпером внутри impl `KeyVault`.
- **FR-009**: Каждый порт MUST иметь fake-adapter (для тестов/dev) и реальный Android-адаптер, оборачивающий существующие `KeyRegistry.derive` + `AeadCipher`; DI выбирает нужный по сборке (rule 6 mock-first).
- **FR-010**: `exportDerivedKey` MUST быть единственным путём получения сырья наружу и MUST быть ограничен purpose-whitelist'ом (валидными enum-значениями).

### Key Entities *(include if feature involves data)*

- **KeyVault** — порт (interface) границы 2; принимает purpose + байты, возвращает байты/`Ciphertext`/`Mac`. Не хранит состояние сессии, не знает про identity-ключи и про хранилище.
- **Purpose** — закрытый enum назначений derived-ключа (HKDF `info`-метка под капотом). Стабилен across устройств/установок (иначе ключи расходятся, K2).
- **Ciphertext** — обёртка `(bytes, purpose)`; `schemaVersion` = cleartext-префикс, парсится adapter DTO.
- **Mac** — обёртка `(bytes, purpose)` результата MAC.
- **DerivedKeyBytes** — `AutoCloseable` носитель сырых байтов для узкой форточки экспорта; обнуляется по close.
- **VaultException** — sealed-иерархия ошибок по категориям.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: Настройки пользователя шифруются и расшифровываются через `KeyVault`, при этом ранее сохранённые настройки продолжают читаться (нулевой регресс для пользователя).
- **SC-002 [backlog]**: Сырьё root-ключа недоступно из кода вне крипто-impl-слоя — публичный `RootKey.bytes` удалён.
- **SC-003 [backlog]**: Будущие платформы (iOS, Rust) и внешние крипто-либы подключаются добавлением адаптера/вызовом форточки, без изменения domain и потребителей.
- **SC-004**: Roundtrip-тест (`aeadSeal`→`aeadOpen`) и backward-compat тест (чтение до-миграционного `Ciphertext`) проходят в `:core:keys` JVM-тестах.
- **SC-005**: `exportDerivedKey` детерминирован и идемпотентен для фиксированного RootKey+purpose+context (contract-тест); `DerivedKeyBytes` обнуляется после `use { }`.
- **SC-006**: Fitness-проверка: нет обращений к `RootKey`/сырым `DerivedKey.bytes` из потребительских модулей; `sign`/`agree`/capability-level отсутствуют в сигнатурах `KeyVault`.

## Assumptions

- RootKey уже существует и восстанавливается механизмами TASK-6 (Done) — эта фича не трогает генерацию/recovery root-ключа.
- Алгоритмы (XChaCha20-Poly1305 AEAD, HKDF-SHA256, HMAC для MAC) уже реализованы в `:core:crypto` (`AeadCipher`, `KeyDerivation`) и переиспользуются адаптером без изменений.
- Реальные purposes Phase 2-3 — ровно 4 (CONFIG, MLS_SIGNATURE, NOISE_STATIC, RECOVERY_BLOB); стаые `contacts`/`media` — это Profile-buckets, не vault-purposes (убраны из пака 2026-07-22).
- Biometric/passphrase-gate для vault — не в этой спеке (зарезервировано `VaultLocked`, exit ramp — отдельный suspend-метод при появлении).
- Миграция `Outcome<T,E>` → exceptions на прочих портах — TASK-113 (отдельно, не блокирует).

## Local Test Path *(mandatory)*

- **Emulator / device**: логика — JVM unit-тесты (`:core:keys`), без эмулятора. Инструментальная проверка Android Keystore-адаптера (реальный TEE, no-plaintext-on-disk) — эмулятор `pixel_5_api_34` через skill `android-emulator`, в конце фазы.
- **Fake adapters used**: `FakeKeyVault` (детерминированный derivation + AEAD для тестов), потребители под тестом получают его через DI.
- **Fixtures / seed data**: `core/keys/src/test/resources/fixtures/config-ciphertext-v1.json` (замороженный до-миграционный `Ciphertext` для backward-compat теста); фиксированный тестовый RootKey.
- **Verification command**: `./gradlew :core:keys:jvmTest` (roundtrip, backward-compat, exportDerivedKey contract, zeroize); `./gradlew :app:connectedDebugAndroidTest` для Keystore-инструментала.
- **Cannot-test-locally gaps**: реальное поведение StrongBox/TEE на конкретном железе (граница 3, `SecureKeyStore`) — вне scope этой спеки; здесь не тестируется. `none` для границы 2.

## AI Affordance *(mandatory)*

`no AI affordance — internal capability only`. `KeyVault` — крипто-примитивный порт; операции над ключами не экспонируются AI-агенту (экспонирование сырья/операций ключа для LLM нарушило бы rule 1 и privacy). Никаких domain-verbs для AI, никакого MCP-surface. Если гипотетически потребуется — только на уровне фич-потребителей (config sync), не самого vault.

## OEM Matrix

`not applicable` — фича pure-Kotlin domain + адаптер над уже существующими крипто-портами; поведение не зависит от OEM (аппаратное хранилище — граница 3, `SecureKeyStore`, вне scope). Инструментальный тест на эмуляторе не про OEM-дивергенцию, а про факт persistence.
