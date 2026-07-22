# Feature Specification: KeyVault port — narrow derived-key export hatch (граница 2)

**Feature Branch**: `task-112-keyvault-boundary-reconcile`
**Created**: 2026-07-22
**Status**: Draft
**Input**: TASK-112 — порт `family.keys.api.KeyVault`, граница 2 (симметричные операции над data-ключами) из трёхграничной vault-модели. Контракт: [`docs/architecture/crypto-key-hierarchy.md`](../../docs/architecture/crypto-key-hierarchy.md) §Key vault (research-reconciled 2026-07-22).

## Clarifications

### 2026-07-22 — Pre-plan clarification pass (verified against code, not the arch-pack)

| # | Вопрос | Резолюция |
|---|--------|-----------|
| 1 | Кто реально потребляет `aeadSeal(Purpose.CONFIG)`? | **Никто.** `ConfigCipher2` (`EnvelopeConfigCipherImpl`) шифрует config **hybrid-envelope'ом**: случайный CEK + AEAD под ним + `crypto_box_seal` CEK под X25519-pubkey получателей (граница 1). Он не деривит purpose-ключ из RootKey и не касается `KeyRegistry`/`RootKey`. `DerivedKey(CONFIG)` из пака — **фантом без потребителя**. → US про миграцию `ConfigCipher2` **удалён**. |
| 2 | Кто потребляет `aeadSeal(RECOVERY_BLOB)` / `mac`? | **Никто.** Recovery (`RecoveryFlow`) деривит wrap-ключ через **Argon2 от passphrase**, не HKDF-from-RootKey-purpose; `mac(purpose)` не имеет ни одного call site. → `aeadSeal`/`aeadOpen`/`mac` **выведены из MVP-scope**, добавляются аддитивно при появлении реального boundary-2 потребителя. |
| 3 | Что реально нужно из порта сейчас? | Только **`exportDerivedKey`** — узкая форточка сырья для внешних Rust-либ: `NOISE_STATIC` (pairing `snow`, TASK-67), `MLS_SIGNATURE` (openmls, TASK-124). Оба downstream в статусе Draft → **реализация KeyVault привязана к первому из них**, не пишется наперёд (rule 4 MVA). |
| 4 | Коллизия имени `Ciphertext`? | Снята автоматически: `aeadSeal` убран → типы `Ciphertext`/`Mac` не вводятся. Существующий `family.crypto.api.values.Ciphertext` не трогается. |

## Обзор для владельца *(mentor-detail)*

<!-- MENTOR-DETAIL:BEGIN -->
Простыми словами. Изначально задумывалось, что `KeyVault` будет «окошком в сейф» для многих операций (шифрование настроек, MAC и т.д.). Но проверка кода показала: реальное шифрование настроек уже работает по-другому (envelope под ключи получателей), а восстановление — через пароль (Argon2). То есть для «шифруй/расшифруй под ключом-для-задачи» **сегодня нет ни одного места в коде**.

Единственное, что реально понадобится в ближайших фичах, — **узкая форточка выдачи сырых байт ключа** внешним крипто-библиотекам, которые обязаны держать ключ сами: рукопожатие при связывании устройств (`snow`) и будущий мессенджер (`openmls`). Только для них нужен детерминированный ключ, выведенный из мастер-ключа.

Поэтому вместо «большого сейфа с кучей операций» строим **ровно одну дверцу** — `exportDerivedKey`. Всё остальное (шифрование под purpose-ключом, MAC) добавим **потом**, когда появится тот, кому это нужно, — не раньше. Так мы не пишем код «на всякий случай» (это правило проекта rule 4).

Плюс наводим гигиену: сырьё мастер-ключа `RootKey` прячем внутрь крипто-слоя, чтобы его нельзя было случайно вытащить наружу в лог.
<!-- MENTOR-DETAIL:END -->

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Узкая форточка экспорта derived-ключа для внешних крипто-либ (Priority: P1)

Внешние библиотеки, обязанные сами держать сырьё ключа (`snow` — Noise static key при pairing; `openmls` — signature key), получают детерминированные байты **только** через `exportDerivedKey(purpose, context, length)`. Возвращаемый `DerivedKeyBytes` — `AutoCloseable`, обнуляется по выходу из `use { }`. Purpose ограничен whitelist'ом.

**Why this priority**: это единственная часть порта с реальным (хоть и downstream) потребителем. Определяет узкую, аудируемую границу выдачи сырья с первого дня, чтобы pairing (TASK-67) и MLS (TASK-124) не изобретали свой доступ к RootKey.

**Independent Test**: fake-adapter — `exportDerivedKey(Purpose.NOISE_STATIC, context, 32)` для фиксированного RootKey+context возвращает детерминированные 32 байта; повтор с тем же входом даёт те же байты (идемпотентность HKDF); после `use { }` буфер обнулён; неизвестный purpose → `VaultException.UnknownPurpose`.

**Acceptance Scenarios**:

1. **Given** RootKey и `Purpose.NOISE_STATIC`, **When** `exportDerivedKey(purpose, context, 32)`, **Then** возвращается `DerivedKeyBytes` длины 32, детерминированно выведенный из RootKey+purpose+context (HKDF-SHA256).
2. **Given** тот же вход дважды, **When** два вызова `exportDerivedKey`, **Then** байты идентичны (idempotent derivation, K2).
3. **Given** полученный `DerivedKeyBytes`, **When** блок `use { }` завершается, **Then** внутренний буфер заполнен нулями.
4. **Given** purpose вне whitelist'а, **When** `exportDerivedKey`, **Then** `VaultException.UnknownPurpose`.

---

### User Story 2 — Сырьё RootKey недоступно снаружи (Priority: P2)

Публичный `RootKey(val bytes: ByteArray)` понижается до `internal` (`family.keys.impl.*`). Внешний код не может достать сырьё root-ключа — только через `exportDerivedKey` для whitelisted purpose. `KeyRegistry.derive` остаётся internal-хелпером внутри impl `KeyVault`.

**Why this priority**: закрывает rule-1 трещину (публичные сырые байты root-ключа). Может делаться вместе с US1 (impl `exportDerivedKey` и так нужен доступ к internal `derive`).

**Independent Test**: компиляционный/fitness — обращение к `RootKey.bytes` из модуля вне `family.keys.impl` не компилируется; grep-fitness: `RootKey(` отсутствует в публичных сигнатурах.

**Acceptance Scenarios**:

1. **Given** кодовая база после миграции, **When** внешний модуль ссылается на `RootKey`, **Then** недоступно (internal visibility).
2. **Given** impl `KeyVault.exportDerivedKey`, **When** нужен derived-ключ, **Then** используется internal `KeyRegistry.derive`, сырьё RootKey наружу не выходит.

### Edge Cases

- **Неизвестный / не-export purpose**: purpose вне whitelist'а (или future `Purpose.External` с невалидной меткой) → `VaultException.UnknownPurpose`.
- **length вне разумных границ** (0 или чрезмерная) → `VaultException` категории programming-error (валидируется до derivation).
- **Hardware keystore недоступен** (если adapter опирается на TEE-unwrap RootKey) → `VaultException.HardwareBackedKeystoreUnavailable`.
- **Vault заблокирован** (будущий passphrase/biometric gate — не сегодня) → зарезервировано `VaultException.VaultLocked`; в MVP не бросается.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Система MUST предоставлять порт `KeyVault` в domain-слое (`family.keys.api`, commonMain) с **единственным** методом MVP: `exportDerivedKey(purpose, context, length) → DerivedKeyBytes` (`@Throws(VaultException::class)`, синхронный).
- **FR-002**: Порт MUST охватывать только границу 2 (data-key operations) и MUST NOT содержать `sign`/`agree` (граница 1 — `AsymmetricCrypto`) и capability/security-level (граница 3 — `SecureKeyStore`).
- **FR-003**: `Purpose` MUST быть закрытым enum ровно из реальных export-потребителей: `{ MLS_SIGNATURE, NOISE_STATIC }`. `CONFIG`/`RECOVERY_BLOB` NOT добавляются (у них нет boundary-2 потребителя: config = envelope, recovery = Argon2). Расширение — аддитивным вариантом `Purpose.External(labelBytes)` при появлении потребителя.
- **FR-004**: `exportDerivedKey` MUST быть единственным путём получения сырья наружу, ограниченным purpose-whitelist'ом; невалидный purpose → `VaultException.UnknownPurpose`.
- **FR-005**: `DerivedKeyBytes` MUST быть `AutoCloseable` с обнулением буфера в `close()` (best-effort zeroize).
- **FR-006**: Derivation MUST быть детерминированной и идемпотентной для фиксированного RootKey+purpose+context (HKDF-SHA256, переиспользуя `KeyDerivation` из `:core:crypto`).
- **FR-007**: Ошибки MUST моделироваться sealed `VaultException` (категории hardware / user-action / data-integrity / programming-error), бросаться через `@Throws`. Методы синхронны (FFI-friendly).
- **FR-008**: `RootKey` MUST быть понижен до `internal` (`family.keys.impl.*`); публичный `RootKey(val bytes: ByteArray)` удалён. `KeyRegistry.derive` остаётся internal-хелпером внутри impl.
- **FR-009**: Порт MUST иметь fake-adapter (детерминированный derivation) и реальный adapter, оборачивающий существующий `KeyRegistry.derive`; DI выбирает по сборке (rule 6 mock-first).
- **FR-010**: `aeadSeal` / `aeadOpen` / `mac` и типы `Ciphertext` / `Mac` NOT входят в этот scope — добавляются аддитивно, отдельным изменением, когда появится реальный boundary-2 потребитель (не наперёд, rule 4).
- **FR-011**: Реализация (код adapter'а + миграция) MUST быть привязана к первому реальному потребителю `exportDerivedKey` — TASK-67 (`NOISE_STATIC`) или TASK-124 (`MLS_SIGNATURE`); порт-контракт (interface + fake) может быть зафиксирован раньше, но production-adapter не пишется без потребителя.

### Key Entities

- **KeyVault** — порт границы 2; MVP = единственный метод `exportDerivedKey`. Не хранит состояние сессии, не знает про identity-ключи и хранилище.
- **Purpose** — закрытый enum export-назначений (`MLS_SIGNATURE`, `NOISE_STATIC`); стабильная HKDF `info`-метка под капотом (K2).
- **DerivedKeyBytes** — `AutoCloseable` носитель сырых байтов; обнуляется по close.
- **VaultException** — sealed-иерархия ошибок по категориям.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: Внешние крипто-либы (pairing `snow`, openmls) получают детерминированный derived-ключ через одну узкую аудируемую форточку `exportDerivedKey`, не касаясь RootKey напрямую.
- **SC-002 [backlog]**: Сырьё root-ключа недоступно из кода вне крипто-impl-слоя — публичный `RootKey.bytes` удалён.
- **SC-003 [backlog]**: Порт покрывает ровно текущую потребность (export-hatch); операции без потребителя (aeadSeal/mac) не построены — добавляются аддитивно при появлении потребителя.
- **SC-004**: Contract-тест: `exportDerivedKey` детерминирован и идемпотентен для фиксированного RootKey+purpose+context; `DerivedKeyBytes` обнуляется после `use { }`; неизвестный purpose → `VaultException.UnknownPurpose`.
- **SC-005**: Fitness: нет обращений к `RootKey`/сырым `DerivedKey.bytes` из потребительских модулей; `sign`/`agree`/capability-level/`aeadSeal`/`mac` отсутствуют в сигнатурах `KeyVault`.

## Assumptions

- RootKey существует и восстанавливается механизмами TASK-6 (Done); эта фича его генерацию/recovery не трогает.
- HKDF-SHA256 (`KeyDerivation`) уже реализован в `:core:crypto` и переиспользуется adapter'ом.
- Реальные export-purposes — ровно 2 (`MLS_SIGNATURE`, `NOISE_STATIC`); оба потребителя (TASK-67, TASK-124) в Draft → production-код KeyVault пишется вместе с ними.
- Config-шифрование (`ConfigCipher2`/envelope) и recovery (`RecoveryFlow`/Argon2) остаются **как есть** — они не boundary-2 и вне scope.
- Boundary-2 AEAD/MAC — не в этой спеке; появится аддитивно с первым потребителем.
- Пак `crypto-key-hierarchy.md` derivation-chain правится в этом же цикле: `DerivedKey(CONFIG)` помечается как не имеющий текущего потребителя (config идёт через envelope).

## Local Test Path *(mandatory)*

- **Emulator / device**: логика — JVM unit-тесты (`:core:keys`), без эмулятора. Инструментальная проверка Android-адаптера (unwrap RootKey из TEE перед derive) — эмулятор `pixel_5_api_34` через skill `android-emulator`, в конце фазы, если adapter трогает Keystore.
- **Fake adapters used**: `FakeKeyVault` (детерминированный HKDF-derivation для тестов).
- **Fixtures / seed data**: фиксированный тестовый RootKey + известный HKDF KAT-вектор для проверки детерминизма экспорта.
- **Verification command**: `./gradlew :core:keys:jvmTest` (contract: детерминизм, идемпотентность, zeroize, unknown-purpose).
- **Cannot-test-locally gaps**: реальный TEE-unwrap RootKey на конкретном железе (граница 3) — вне scope; `none` для логики экспорта.

## AI Affordance *(mandatory)*

`no AI affordance — internal capability only`. `KeyVault` выдаёт сырьё ключа только доверенным внутренним крипто-либам; экспонирование AI-агенту нарушило бы rule 1 и privacy. Никаких domain-verbs для AI, никакого MCP-surface.

## OEM Matrix

`not applicable` — pure-Kotlin domain + adapter над существующим `KeyDerivation`; поведение не зависит от OEM. Аппаратное хранение RootKey — граница 3 (`SecureKeyStore`), вне scope.
