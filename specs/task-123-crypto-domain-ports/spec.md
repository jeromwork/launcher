# Feature Specification: F-CRYPTO — доменные порты + fakes (CryptoPort / GroupPort / KeyPackagePort)

**Feature Branch**: `task-123-crypto-domain-ports`
**Created**: 2026-07-22
**Status**: Draft
**Backlog**: TASK-123
**Input**: Готовый промт TASK-123 (reconciled 2026-07-22 против крипто-арх-паков). См. [task-123](../../backlog/tasks/task-123%20-%20F-CRYPTO-Domain-ports-and-fakes.md), [crypto.md](../../docs/architecture/crypto.md), [crypto-mls.md](../../docs/architecture/crypto-mls.md), [crypto-key-hierarchy.md](../../docs/architecture/crypto-key-hierarchy.md).

> **Что это простыми словами.** Мы пишем «контракт водителя» для крипто: три интерфейса (`CryptoPort` — шифруй/расшифруй сообщение группы; `GroupPort` — создай группу / добавь / убери участника / прими изменение; `KeyPackagePort` — опубликуй пачку своих ключей-приглашений / забери один чужой) плюс их **поддельные (fake) реализации** на обычных структурах в памяти. Никакого Rust, никакого openmls, никакого сервера. Это разблокирует фичи-потребители (мессенджер, pairing) — они смогут писаться и тестироваться уже сейчас, а «настоящий двигатель» (openmls) подключится позже как альтернативная реализация того же контракта.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Потребитель кодит и тестирует против портов без реального крипто (Priority: P1)

Разработчик фичи-потребителя (мессенджер TASK-42, pairing TASK-67) пишет доменную логику против `CryptoPort` / `GroupPort` / `KeyPackagePort` и прогоняет её на fake-реализациях — без Rust-тулчейна, без openmls, без развёрнутого сервера.

**Why this priority**: это весь смысл rule 6 (mock-first). Без портов+fakes потребители заблокированы до конца openmls-интеграции (TASK-124). С ними — работа потребителей стартует немедленно.

**Independent Test**: написать минимальный consumer-snippet (в `core/crypto/README.md`), который зовёт все три порта, подставить fakes, прогнать как unit-тест на JVM — зелёный без единой внешней зависимости.

**Acceptance Scenarios**:

1. **Given** fake-реализации всех трёх портов, **When** consumer-код вызывает `createGroup → addMember → encryptMessage → decryptMessage`, **Then** сценарий проходит целиком на in-memory структурах, без Rust/openmls/сети.
2. **Given** только интерфейсы портов (без адаптеров), **When** потребитель компилирует свою доменную логику, **Then** компиляция проходит — потребитель не зависит от выбора реализации.

---

### User Story 2 - Изоляция домена гарантируется автоматически (Priority: P1)

Архитектор полагается на то, что доменный слой не может протащить вендор-типы: порты в `commonMain` физически не собираются, если кто-то импортировал openmls / Rust / Android / вендор-SDK.

**Why this priority**: rule 1 (domain isolation) + rule 7 (fitness functions). Порт — это шов, через который изоляция обеспечивается; без автоматической проверки шов деградирует незаметно.

**Independent Test**: добавить в `commonMain` заведомо запрещённый импорт (`openmls`, `cryptokit`, `okhttp`, `android.*`) → fitness-проверка падает; убрать → зелёная.

**Acceptance Scenarios**:

1. **Given** чистый `commonMain` пакета `family.crypto.ports`, **When** запускается fitness-проверка, **Then** она зелёная.
2. **Given** искусственно добавленный `import openmls.*` (или `cryptokit.*`) в порт, **When** запускается fitness-проверка, **Then** она падает с понятным сообщением.

---

### User Story 3 - Контракт-тесты фиксируют инварианты для будущего реального адаптера (Priority: P2)

Автор реального openmls-адаптера (TASK-124) получает готовый набор контракт-тестов: любой адаптер порта обязан их пройти. Fake проходит сейчас, real — когда появится.

**Why this priority**: контракт-тесты — это исполняемая спецификация поведения порта. Они превращают «порт» из голого интерфейса в проверяемый договор и защищают от расхождения fake↔real.

**Independent Test**: прогнать `CryptoPortContract` / `GroupPortContract` / `KeyPackagePortContract` на fakes — зелёные; тот же набор позже переиспользуется для real-адаптера в TASK-124.

**Acceptance Scenarios**:

1. **Given** контракт-тесты порта, **When** они запускаются против fake-реализации, **Then** все зелёные.
2. **Given** тот же контракт-набор, **When** он будет запущен против real-адаптера (TASK-124), **Then** структура тестов не требует переписывания — только подмена фабрики реализации.

---

### Edge Cases

- **Пустой пул KeyPackage**: `claim(targetIdentity)` при исчерпанном пуле возвращает «пусто» (null / sealed «empty»), не падает.
- **Неизвестный `groupId`**: `encryptMessage` / `decryptMessage` / `addMember` на несуществующей группе — детерминированная ошибка, не молчаливый успех.
- **Двойное создание группы** с тем же id — ошибка, не тихая перезапись.
- **Расшифровка чужого ciphertext** (из другой группы / другого ratchet-шага) — ошибка.
- **Forward-secrecy инвариант на fake**: после `encryptMessage` предыдущий ключ нельзя воспроизвести (на fake моделируется монотонным counter'ом — старый шаг недоступен).
- **Авторизация НЕ здесь**: «кто имеет право add/remove» — политика приложения (инвариант ML6), не метод `GroupPort`; primary-user device — единственный подписант Commit (TASK-102). Порт не решает разрешения.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Три доменных порта `CryptoPort`, `GroupPort`, `KeyPackagePort` определены в модуле `:core:crypto`, `commonMain`, namespace `family.crypto.ports` (НЕ `cryptokit.*` — забанен fitness-тестом `NoLegacyFamilyNamespaceTest`, TASK-141).
- **FR-002**: Порты — чистый Kotlin: только stdlib + coroutines + `kotlinx.serialization`-аннотации (при необходимости) + другие доменные типы. Никаких вендор/платформенных/транспортных типов в сигнатурах (rule 1).
- **FR-003**: Для каждого порта существует fake in-memory адаптер в `commonTest` (`family.crypto.fakes`), работающий без Rust/openmls/сервера.
- **FR-004**: Для каждого порта существует контракт-тест (`family.crypto.contracts`) на 5–7 инвариантов, зелёный на fake; структура пригодна для переиспользования real-адаптером (TASK-124).
- **FR-005**: Fitness-проверка падает, если `commonMain` пакета портов импортирует любое из: `okhttp`, `firebase.*`, `openmls`, `uniffi`, `android.*`, `cryptokit.*`.
- **FR-006**: `Ciphertext` **переиспользуется** из существующего `family.crypto.api.values.Ciphertext` — второй тип не заводится (арх-пак запрещает дубль, [crypto-key-hierarchy.md](../../docs/architecture/crypto-key-hierarchy.md) §Key vault).
- **FR-007**: `GroupState` **не** является возвращаемым доменным value-типом. Состояние группы opaque и в реальном адаптере персистится целиком openmls `StorageProvider` (TASK-124/125). На fake состояние скрыто за реализацией и наружу как «снимок» не отдаётся.
- **FR-008**: Доменные порты **не импортируют** `KeyVault` (`family.keys.api`, `:core:keys`). Signing-материал поступает в openmls через `KeyVault.exportDerivedKey(MLS_SIGNATURE, …)` внутри real-адаптера (TASK-124), а не параметром доменного порта.
- **FR-009**: Сигнатуры методов портов спроектированы **от формы Wire `CoreCrypto`/`Conversation` API** ([crypto-mls.md](../../docs/architecture/crypto-mls.md): `add_members` / `remove_members` / `self_update` / `commit_to_pending_proposals` / `merge_pending_commit` / `process_message → ProcessedMessage/StagedCommit`; `KeyPackageBuilder…last_resort()`), а не из произвольной формы. [NEEDS CLARIFICATION: финальный перечень методов и их точные сигнатуры — фиксируется в /speckit.clarify.]
- **FR-010**: В `core/crypto/README.md` присутствует consumer-usage snippet — минимальный Kotlin-пример, как фича-код зовёт порты и подставляет fakes.
- **FR-011**: `KeyPackagePort` поддерживает жизненный цикл пула: публикация пачки, атомарный one-time `claim`, клиентский refill при снижении пула ниже порога, семантика last-resort. [NEEDS CLARIFICATION: нужен ли на этом этапе last-resort в доменном порту или он остаётся деталью TASK-124 адаптера.]

### Key Entities *(include if feature involves data)*

- **CryptoPort**: encrypt/decrypt сообщения в контексте группы. Форма — от Wire CoreCrypto `encrypt`/`decrypt` над `Conversation`.
- **GroupPort**: жизненный цикл MLS-группы (создание, add/remove участника, приём коммита). Обёртка над openmls `add_members`/`remove_members`/`process_message`; **не** решает авторизацию.
- **KeyPackagePort**: пул одноразовых ключей-приглашений (publish / claim / refill). Ниже уровнем в реальном адаптере опираются на opaque `KeyPackageId` и last-resort ключ.
- **IdentityKey**: Ed25519 pubkey (opaque bytes). [NEEDS CLARIFICATION: заводим как отдельный value-тип или переиспользуем существующий из `:core:crypto` primitives.]
- **KeyPackage**: opaque MLS KeyPackage payload + `expiresAt`.
- **Commit**: opaque MLS Commit message (bytes).
- **Ciphertext**: **переиспользуемый** `family.crypto.api.values.Ciphertext` — не новый тип.
- **GroupState**: **не** доменный тип — opaque state, живёт в openmls `StorageProvider` (TASK-124/125).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: Потребитель может написать и прогнать сценарий `createGroup → addMember → encrypt → decrypt` целиком на fakes, без Rust/openmls/сети — зелёный JVM-тест.
- **SC-002 [backlog]**: Все три контракт-теста (`CryptoPortContract` / `GroupPortContract` / `KeyPackagePortContract`) зелёные на fakes — `./gradlew :core:crypto:commonTest`.
- **SC-003 [backlog]**: Изоляция домена гарантирована машиной: fitness-проверка падает при добавлении запрещённого импорта (`openmls` / `cryptokit` / `android` / `okhttp` / `firebase`) в порты и зелёная без него.
- **SC-004 [backlog]**: Consumer-usage snippet в `core/crypto/README.md` компилируется и показывает подстановку fakes.
- **SC-005**: Контракт-набор структурно переиспользуем real-адаптером TASK-124 без переписывания (только подмена фабрики реализации).
- **SC-006**: `Ciphertext` в сигнатурах — тот же символ `family.crypto.api.values.Ciphertext` (нет второго типа); `GroupState` не встречается как возвращаемый тип портов; `KeyVault` не импортируется в `commonMain` портов.

## Assumptions

- TASK-112 `KeyVault`-контракт заморожен (export-hatch `exportDerivedKey` + `Purpose{MLS_SIGNATURE, NOISE_STATIC}`) и в сигнатуры этих портов не входит.
- TASK-122 (Rust FFI foundation) — Done; но эта таска pure-Kotlin и от него не зависит по коду.
- Реальный openmls-адаптер (TASK-124) и persistence (SQLCipher, TASK-125) — вне scope; здесь только контракт + fakes.
- Форма портов проектируется от Wire CoreCrypto/Conversation API как эталона (openmls-consensus), чтобы будущий real-адаптер лёг тонкой обёрткой.
- Fake-крипто (`CryptoPort`) **намеренно небезопасно** (например, детерминированный xor + counter-ratchet) — оно удовлетворяет контракту, но не является настоящим шифрованием.

## Local Test Path *(mandatory)*

- **Emulator / device**: не требуется — pure-Kotlin domain logic, только JVM `commonTest`.
- **Fake adapters used**: `FakeCryptoPort`, `FakeGroupPort`, `FakeKeyPackagePort` (все в `commonTest`, `family.crypto.fakes`).
- **Fixtures / seed data**: детерминированные in-memory значения внутри тестов; отдельных fixture-файлов не требуется.
- **Verification command**: `./gradlew :core:crypto:commonTest`; fitness-проверка — через konsist/detekt-правило (`./gradlew :core:crypto:test` или выделенный fitness-таск, уточняется в plan).
- **Cannot-test-locally gaps**: none — реального крипто/устройства/сети на этом этапе нет.

## AI Affordance *(mandatory)*

`no AI affordance — internal crypto capability only`. Порты — низкоуровневый крипто-контракт, не user-facing verb. Возможная будущая AI-экспозиция (например, «создай группу») живёт на уровне фич-потребителей (мессенджер/pairing), не в этом контракте; здесь провайдеров/LLM-типов быть не должно (rule 1). Никакой telemetry/PII здесь нет.

## OEM Matrix *(mandatory if feature touches device behavior)*

`not applicable` — pure-Kotlin domain logic в `commonMain`/`commonTest`, никакого device-поведения (нет фоновой работы, permissions, launcher-роли, уведомлений). Таблица удалена намеренно.

---

## TL;DR (что внутри — для быстрой перезагрузки)

- **Строим**: три доменных крипто-порта (`CryptoPort`/`GroupPort`/`KeyPackagePort`) в `:core:crypto` `commonMain` (namespace `family.crypto.ports`) + fake in-memory адаптеры + контракт-тесты + fitness-проверка запрещённых импортов. Pure Kotlin, ноль Rust/openmls/сети.
- **Зачем**: разблокировать потребителей (мессенджер TASK-42, pairing TASK-67) до готовности openmls (TASK-124) — rule 6 mock-first; закрепить изоляцию домена машиной — rule 1 + 7.
- **Ключевые границы (reconcile 2026-07-22)**: `KeyVault` НЕ в сигнатурах (его дергает openmls-адаптер TASK-124 через `exportDerivedKey`); `Ciphertext` — reuse существующего; `GroupState` не возвращается наружу; сигнатуры проектируются от Wire `CoreCrypto`/`Conversation` shape.
- **Открытые вопросы (для /speckit.clarify)**: точный перечень методов/сигнатур портов (FR-009); `IdentityKey` как новый тип или reuse (Key Entities); нужен ли last-resort в доменном `KeyPackagePort` или это деталь TASK-124 (FR-011).
- **Вне scope**: real openmls-адаптер (TASK-124), persistence SQLCipher (TASK-125), Rust FFI (TASK-122, Done).
