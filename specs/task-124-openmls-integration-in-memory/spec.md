# Feature Specification: openmls integration — real MLS adapter (in-memory)

**Feature Branch**: `task-124-openmls-integration-in-memory`
**Created**: 2026-07-23
**Status**: Draft
**Backlog**: TASK-124 (depends TASK-122, TASK-123)
**Arch-pack (read-truth)**: [`docs/architecture/crypto-mls.md`](../../docs/architecture/crypto-mls.md) — MLS-слой; эта спека НЕ переопределяет архитектуру, только описывает реализацию адаптера за уже определёнными портами.
**Input**: Реальная MLS-крипта (`openmls`) за портами `CryptoPort` / `GroupPort` / `KeyPackagePort` из TASK-123, поверх Rust FFI из TASK-122. Storage — только in-memory (persistence = TASK-125).

---

## Контекст (для читателя)

Это **headless core/crypto-спека** — без UI. «Потребитель» фичи — доменный слой и будущие фичи (TASK-67 Pairing, TASK-42 messenger), которые уже пишут против портов TASK-123 на fake-адаптерах. Эта спека заменяет fake на **реальный `openmls`**, не меняя ни одной сигнатуры порта. Внутренности MLS-протокола делегированы арх-паку [`crypto-mls.md`](../../docs/architecture/crypto-mls.md) (по CLAUDE.md rule 14 он — источник правды; здесь — только контракт реализации и её проверяемое поведение).

`## Sequences` намеренно опущены — headless-спека без пользовательских флоу (per convention: сценарии только для user-facing / многоучастниковых флоу).

### Проверенные факты первоисточника (openmls-v0.8.1, локальный клон + два web-прогона)

Зафиксировано до написания кода, чтобы scope был точным:

1. **`MlsGroup` не сериализуется байтами напрямую** — serde/tls_codec сняты с `MlsGroup` в 0.8.x. Состояние живёт в `StorageProvider`; группа восстанавливается через `MlsGroup::load(storage, group_id) -> Result<Option<MlsGroup>, _>`. → форма FFI = **снапшот `StorageProvider`**, не group-state.
2. **`remove_members(members: &[LeafNodeIndex])`** — openmls удаляет по индексу листа, не по ключу. → адаптер обязан резолвить `IdentityKey → LeafNodeIndex`.
3. **`add_members` → `(commit, welcome, Option<GroupInfo>)`** (welcome всегда есть); **`remove_members` → `(commit, Option<welcome=None>, …)`**. Ложится на `CommitBundle(commit, welcome: ByteArray?)`.
4. **`create_message(provider, signer, &[u8])`** падает при наличии pending proposals — адаптер обязан гарантировать чистое состояние перед encrypt.
5. **UniFFI — proc-macro режим, `.udl` НЕТ** (TASK-122: `uniffi::setup_scaffolding!()` + `#[uniffi::export]`). MLS-типы добавляются Rust-аннотациями, не UDL.
6. Пины: `openmls = "=0.8.1"`, `openmls_traits 0.5.0`, `openmls_rust_crypto 0.5.1`, `uniffi 0.28.3` (lockstep-проверка уже есть — `verifyUniffiVersions`). Ciphersuite MTI `MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519`.

---

## Clarifications

### 2026-07-24 — Pre-plan clarification pass

| # | Вопрос | Резолюция | Источник |
|---|--------|-----------|----------|
| 1 | Откуда TASK-124 берёт подписной MLS-ключ (Ed25519)? | **Эфемерный ключ сейчас**: адаптер генерит `SignatureKeyPair` внутри openmls (стандартный паттерн). TASK-124 остаётся unblocked (нет зависимости от Paused TASK-112). Ключ всё равно теряется при reboot (in-memory scope). Привязка к key-hierarchy через `KeyVault.exportDerivedKey` — когда приедет persistence (TASK-125) + TASK-112. Inline `// TODO(task-112)` в коде. | Owner-решение 2026-07-24 |
| 2 | В каком пакете/модуле живёт Kotlin-адаптер? | Пакет **`family.crypto.mls`** (арх-пак), в `:core:crypto` androidMain, зависящем от `:crypto-ffi`. НЕ новый модуль (rule 4 MVA), НЕ пакет `cryptokit`. | [crypto-mls.md](../../docs/architecture/crypto-mls.md) title + module home |
| 3 | Версионируется ли in-memory снапшот `StorageProvider`? | **Нет.** Снапшот — внутренний in-memory формат, не покидает устройство и не переживает reboot. Версионированный at-rest формат вводит TASK-125 (SQLCipher) за тем же trait; in-memory формат не переживает версии → версионировать нечего (rule 5 не триггерится здесь). | [crypto-mls.md](../../docs/architecture/crypto-mls.md) verified FFI shape |
| 4 | Оборачиваем ли ciphertext/commit/welcome в свой envelope со `schemaVersion`? | **Нет.** Гоняем сырые RFC 9420 байты (`MlsMessageOut` сериализованный). Свой MLS wire format **отвергнут** арх-паком (ломает RFC 9420 interop / MIMI). Envelope появится только когда реальный транспорт потребует (messenger TASK-42), не здесь. | [crypto-mls.md](../../docs/architecture/crypto-mls.md) §Rejected |

---

## User Scenarios & Testing *(mandatory)*

Роли-«потребители» headless-фичи — доменный код и его тесты. Истории приоритизированы как срезы, каждый независимо тестируем.

### User Story 1 — Реальная групповая крипта за портами (Priority: P1)

Доменный код вызывает `GroupPort.createGroup` / `addMembers` / `CryptoPort.encryptMessage` / `decryptMessage` и получает **настоящее MLS-шифрование** вместо fake-XOR — не меняя ни строки в своих вызовах.

**Why this priority**: без этого никакая крипто-зависимая фича не может выйти за рамки fake. Это ядро задачи.

**Independent Test**: contract-тесты TASK-123 (`GroupPortContract`, `CryptoPortContract`) прогоняются на `OpenMlsGroupPort` / `OpenMlsCryptoPort` — все зелёные, тестовый код не переписан (наследование abstract-класса из `androidUnitTest`).

**Acceptance Scenarios**:

1. **Given** пустой адаптер, **When** `createGroup(g)` затем `addMembers(g, [kp])` → `mergePendingCommit(g)`, **Then** группа существует, эпоха продвинулась, вернулся `CommitBundle` с non-null `welcome`.
2. **Given** группа из 2+ членов, **When** `encryptMessage(g, m)` на одном участнике и `decryptMessage` на другом (через доставленный ciphertext), **Then** расшифрованный plaintext побайтово равен `m`.

---

### User Story 2 — Forward secrecy и изоляция групп (Priority: P1)

Крипта обязана давать реальные гарантии: два одинаковых сообщения → разные ciphertext'ы; сообщение старой эпохи не читается после ре-кея; ciphertext чужой группы не расшифровывается.

**Why this priority**: fake это лишь имитировал; на реальном openmls это должно держаться по-настоящему, иначе крипта бессмысленна.

**Independent Test**: property-based тест (100 случайных последовательностей create/add/remove/encrypt/processCommit) + прицельные assertions forward-secrecy и cross-group.

**Acceptance Scenarios**:

1. **Given** активная группа, **When** `encryptMessage(g, m)` вызван дважды подряд одним `m`, **Then** два ciphertext'а различны (ratchet продвинулся).
2. **Given** ciphertext, снятый в эпохе N, **When** после `selfUpdate` + `mergePendingCommit` (эпоха N+1) пытаемся его расшифровать, **Then** ошибка (forward secrecy).
3. **Given** две независимые группы, **When** ciphertext группы A подан в `decryptMessage(B, …)`, **Then** ошибка (изоляция).

---

### User Story 3 — KeyPackage pool (local-only) и last-resort (Priority: P2)

Клиент публикует свои KeyPackage в локальный пул и выдаёт по одному при claim; при исчерпании — переиспользуемый last-resort (RFC 9750).

**Why this priority**: нужно для pairing/add-member, но серверная публикация (`KeyPackagePort` real против сервера) ждёт TASK-104 — здесь local-only.

**Independent Test**: `KeyPackagePortContract` на реальной openmls-генерации KeyPackage.

**Acceptance Scenarios**:

1. **Given** опубликованы 2 one-time KeyPackage, **When** `claim` дважды, **Then** `localCount` 2→1→0, каждый выдан ровно один раз.
2. **Given** пул пуст, но есть last-resort, **When** `claim`, **Then** возвращён last-resort с `isLastResort=true`, повторный claim снова его возвращает (переиспользуем).

### Edge Cases

- Двойной `createGroup(g)` → детерминированная ошибка (не паника процесса — UniFFI конвертирует в Kotlin-исключение).
- `encryptMessage` при наличии pending proposals → адаптер сперва приводит состояние в чистое (или детерминированная ошибка), т.к. `create_message` openmls падает на pending.
- `processMessage` с мусорными байтами → типизированная ошибка, не abort.
- `removeMembers` по `IdentityKey`, которого нет в группе → детерминированная ошибка (резолв `IdentityKey → LeafNodeIndex` не нашёл лист).
- Rust-паника в openmls → UniFFI panic-catcher (TASK-122 контракт) конвертирует в Kotlin-исключение, процесс не падает.

---

## Requirements *(mandatory)*

### Functional Requirements

**Rust FFI-слой (`crypto-ffi/`)**

- **FR-001**: Крейт `crypto_ffi` MUST добавить зависимости `openmls = "=0.8.1"`, `openmls_traits = "0.5.0"`, `openmls_rust_crypto = "0.5.1"` (exact/compatible пины), и экспортировать MLS-операции через `#[uniffi::export]` (**proc-macro режим, без `.udl`**).
- **FR-002**: MUST реализовать `InMemoryStorageProvider` — openmls-trait `StorageProvider<VERSION>` поверх `HashMap`. Хранит все под-сущности (group context, tree, secrets, keys, proposals), НЕ переживает reboot (persistence = TASK-125 через тот же trait).
- **FR-003**: FFI MUST быть **stateless по границе**: каждая операция принимает сериализованный снапшот `StorageProvider` (bytes) + `group_id` + параметры, внутри — deserialize storage → `MlsGroup::load(provider, group_id)` → операция (openmls сам пишет в storage) → merge при необходимости → serialize storage обратно; возвращает `(результат, updated_state_bytes)`. **Не** сериализует `MlsGroup` напрямую (в 0.8.1 невозможно).
- **FR-004**: Экспортируемые операции MUST покрыть: создание группы (`new_with_group_id` c `CredentialWithKey`), `add_members`, `remove_members`, `self_update`, `commit_to_pending_proposals`, `merge_pending_commit`, `merge_staged_commit`, `process_message`, `create_message`, генерацию KeyPackage (`KeyPackage::builder()…build`, флаг `mark_as_last_resort()`).

**Kotlin adapter (androidMain)**

- **FR-005**: MUST предоставить `OpenMlsGroupPort` (impl `GroupPort`) и `OpenMlsCryptoPort` (impl `CryptoPort`) в пакете **`family.crypto.mls`** (`:core:crypto` androidMain, зависит от `:crypto-ffi`), реализованные через UniFFI-generated Kotlin API (`uniffi.crypto_ffi`). Адаптер держит `MutableMap<GroupId, ByteArray>` (снапшот storage per group) в памяти; каждый метод: read snapshot → вызов Rust → write updated snapshot.
- **FR-006**: `OpenMlsGroupPort` и `OpenMlsCryptoPort` MUST делить одно состояние группы/эпохи (contract требует общий epoch-state между двумя портами).
- **FR-007**: `removeMembers` MUST резолвить `IdentityKey → LeafNodeIndex` внутри адаптера (openmls удаляет по индексу листа). Отсутствующий член → детерминированная ошибка.
- **FR-008**: Маппинг результатов MUST соответствовать openmls-сигнатурам: `addMembers` → `CommitBundle(commit, welcome=non-null)`; `removeMembers`/`selfUpdate` → `CommitBundle(commit, welcome=null)`; `processMessage` → `ProcessedMessage.{ApplicationMessage|StagedCommit|Proposal}`.
- **FR-009**: `encryptMessage` MUST гарантировать отсутствие pending proposals перед `create_message` (иначе openmls падает) — либо чистое состояние, либо детерминированная ошибка.
- **FR-010**: `OpenMlsKeyPackagePort` (impl `KeyPackagePort`) — local-only: `publish` кладёт KeyPackage в локальный пул, `claim` выдаёт one-time (иначе last-resort, иначе `ClaimResult.Empty`, **никогда не бросает**), `localCount` = число one-time. Подписной ключ (Ed25519) в этой таске — **эфемерный `SignatureKeyPair`, сгенерированный внутри адаптера/openmls** (Clarification #1), НЕ параметром порта. Привязка к key-hierarchy через `KeyVault.exportDerivedKey(MLS_SIGNATURE, …)` — **отложена** до persistence (TASK-125 + TASK-112).
  - `// TODO(task-112): подписной MLS-ключ должен выводиться из key-hierarchy через KeyVault, когда TASK-112/TASK-125 landing (сейчас эфемерный, теряется при reboot — ok для in-memory scope).`
  - `// TODO(server-roadmap): серверная публикация/claim KeyPackage должна перейти на сервер (TASK-104) ради anti-abuse + async discovery.`
- **FR-011**: Ни один openmls/uniffi/cryptokit-тип MUST NOT протечь в сигнатуры, видимые домену; адаптер живёт в пакете `family.crypto.mls` (androidMain), вне `family.crypto.ports` (Clarification #2).

**Wiring & fitness**

- **FR-012**: DI MUST выбирать real vs fake крипто-адаптер по флейвору, зеркаля паттерн `androidRealBackend` / `androidMockBackend`; release-guard (`assertNoFakeCryptoInRelease`) MUST срабатывать, если в release-графе оказался `family.crypto.fake.*`.
- **FR-013**: Fitness-функция MUST падать, если `commonMain` (пакет `ports`) импортирует `openmls*` / `uniffi*` / `cryptokit*` (существующий `PortsNoVendorImportTest` — проверить покрытие).
- **FR-014**: UniFFI version lockstep (`verifyUniffiVersions`, 0.28.3) MUST оставаться зелёным после добавления MLS-биндингов.

**Wire format**

- **FR-015**: Форматы, покидающие адаптер (ciphertext, commit, welcome, KeyPackage) — **сырые RFC 9420 байты** (`MlsMessageOut` сериализованный), **без своей envelope-обёртки** (Clarification #4; свой MLS wire format отвергнут арх-паком). MUST иметь roundtrip-тест (write→read→equal). Собственный envelope-`schemaVersion` появится только когда реальный транспорт потребует (messenger TASK-42), не здесь. Снапшот `StorageProvider` — внутренний in-memory формат, **не версионируется** (Clarification #3; не покидает устройство, не переживает reboot); версионированным at-rest wire-форматом станет в TASK-125 (SQLCipher) за тем же trait.

**Docs**

- **FR-016**: После merge — обновить frontmatter [`crypto-mls.md`](../../docs/architecture/crypto-mls.md): статус реализации MLS-адаптера = `implemented (YYYY-MM-DD)`; секцию интеграции с примерами — в `core/crypto/README.md`. Арх-пак — источник правды; любое расхождение реализации с ним правится в арх-паке тем же коммитом (rule 14).

### Key Entities

- **StorageProvider snapshot**: сериализованный `HashMap` под-сущностей openmls (context/tree/secrets/keys/proposals) для одной группы — единица состояния, гоняемая по FFI-границе. In-memory, per-`GroupId`.
- **CommitBundle**: `commit: Commit` + `welcome: ByteArray?` (non-null только для add) — уже определён в TASK-123.
- **KeyPackage / LastResortKey**: opaque MLS KeyPackage (RFC 9420 / 9750) — генерируется openmls, хранится в локальном пуле.
- **IdentityKey ↔ LeafNodeIndex**: доменный ключ члена ↔ openmls-индекс листа; маппинг живёт внутри адаптера.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: Contract-тесты TASK-123 (`GroupPortContract`, `CryptoPortContract`) зелёные на `OpenMlsGroupPort` + `OpenMlsCryptoPort` без изменения тестового кода.
- **SC-002 [backlog]**: Roundtrip: `encryptMessage → (доставка) → decryptMessage` даёт plaintext, побайтово равный исходному, в группе из 3 членов.
- **SC-003 [backlog]**: Forward secrecy: два `encryptMessage(m)` подряд → разные ciphertext'ы; ciphertext прошлой эпохи не читается после `selfUpdate + mergePendingCommit`.
- **SC-004 [backlog]**: Property-based: 100 случайных последовательностей (create + N add + M encrypt + K remove + processCommit) — без исключений, множество членов консистентно.
- **SC-005 [backlog]**: Emulator smoke на `pixel_5_api_34`: 3-member группа, 10 сообщений encrypt+decrypt — все зелёные.
- **SC-006 [backlog]**: KeyPackage pool: one-time выдаётся ровно раз, last-resort переиспользуется, пустой пул → `Empty` без исключения.
- **SC-007**: Fitness `PortsNoVendorImportTest` падает при импорте `openmls`/`uniffi`/`cryptokit` в `ports`; `verifyUniffiVersions` (0.28.3) зелёный.
- **SC-008**: Release-guard `assertNoFakeCryptoInRelease` крашит сборку при fake-адаптере в release-графе.
- **SC-009 [backlog]**: После merge frontmatter `crypto-mls.md` помечен `implemented (дата)`; расхождений реализации с арх-паком нет.

---

## Assumptions

- TASK-122 (Rust FFI toolchain, proc-macro UniFFI 0.28.3, panic-catcher) и TASK-123 (порты + contract-тесты + fakes) — Done и стабильны.
- Persistence вне scope: состояние теряется при рестарте app'а (deliberate; TASK-125 подставит SQLCipher через тот же `StorageProvider` trait).
- iOS вне scope: `iosMain`-адаптер — stub'ы, бросающие `NotImplementedError` (реализация в TASK-26 / V-1).
- Серверная сторона KeyPackage (публикация/claim через сеть) вне scope — `KeyPackagePort` local-only до TASK-104.
- `KeyVault.exportDerivedKey(MLS_SIGNATURE, …)` доступен адаптеру (граница TASK-112) для получения signing-материала.
- Ciphersuite фиксирован MTI (`MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519`) — не preset-параметр в MVP.
- **TASK-124 адаптер сам по себе не production-ready** (эфемерный подписной ключ + отсутствие persistence): «настоящая» крипта требует минимум TASK-125 (persistence + key-hierarchy binding). Пока крипто-фичи не шипятся, DI по умолчанию остаётся на fake/выключено; release-guard (FR-012) не конфликтует, т.к. крипта не в release-графе до готовности цепочки. Это НЕ security-регрессия — in-memory scope deliberate.

---

## Local Test Path *(mandatory)*

- **Emulator / device**: JVM/`androidUnitTest` для contract + roundtrip + property-based (логика через UniFFI-биндинги на host-нативной библиотеке); `pixel_5_api_34` via skill `android-emulator` для smoke (SC-005).
- **Fake adapters used**: никаких для проверки самого адаптера — тестируется **реальный** `OpenMls*Port`. Fakes из TASK-123 остаются для потребителей выше.
- **Fixtures / seed data**: детерминированные KeyPackage/credential, сгенерированные в тесте; golden-байты для roundtrip — в `core/crypto/src/*Test/resources/` (создать).
- **Verification command**: `./gradlew :core:crypto:testDebugUnitTest` (contract + roundtrip + property) → затем `./gradlew :crypto-ffi:check` (fitness/version) → emulator smoke.
- **Cannot-test-locally gaps**: реальное межустройственное MLS (два физических девайса, доставка commit/welcome по сети) — вне in-memory scope; `// TODO(physical-device)` появляется при интеграции с pairing/messenger (TASK-67/42), не здесь. Cross-device — TASK-118.

---

## AI Affordance *(mandatory)*

`no AI affordance — internal crypto capability only`. MLS-адаптер — инфраструктурный примитив за доменными портами; никакой AI-агент не оперирует им напрямую. Крипто-верб-серфейс уже выражен портами TASK-123 (provider-agnostic по построению); эта спека их лишь реализует и не добавляет новых внешне-вызываемых поверхностей.

---

## OEM Matrix *(mandatory if feature touches device behavior)*

`not applicable` — чистая доменно-крипто-логика + Rust FFI. Не касается background work, permissions, launcher role, notifications, battery, telephony. Единственная device-специфика — загрузка `.so` (arm64-v8a), покрыта emulator smoke (SC-005) и уже отлаженным механизмом TASK-122.

---

## TL;DR для новичка

**Что делаем простыми словами.** У нас уже есть «розетки» для шифрования (порты `CryptoPort`/`GroupPort`/`KeyPackagePort` из TASK-123) и построенный «завод» Rust-мостика (TASK-122). Раньше в розетки был воткнут *игрушечный* шифровальщик (fake, для тестов). Эта задача — привозим **настоящую** крипто-машину `openmls` (та же, что у Wire, авторы — соавторы стандарта MLS) и втыкаем её в те же розетки, ничего не меняя в остальном коде.

**Ключевые «ага» из исследования** (сверено с настоящим кодом openmls-v0.8.1, не по памяти):
1. Группу нельзя «сохранить в байтики» напрямую — состояние живёт в отдельном хранилище (`StorageProvider`), а группа собирается из него на лету. Поэтому по мостику гоняем **снимок хранилища**, а не саму группу.
2. Удаление участника в openmls — по «номеру места в дереве» (`LeafNodeIndex`), а не по ключу. Адаптер сам переводит наш ключ участника в этот номер.
3. Свою обёртку над зашифрованными сообщениями **не делаем** — гоняем стандартные RFC 9420 байты (иначе сломаем совместимость).

**Что НЕ входит.** Крипта пока живёт только в памяти — при перезапуске приложения теряется (это нарочно; «долговременное хранение» — отдельная TASK-125). Подписной ключ временный (настоящий, из нашей иерархии ключей, подключим вместе с TASK-125/TASK-112). iOS и серверная публикация ключей — тоже позже.

**Как проверяем.** Старые тесты-контракты из TASK-123 прогоняются на новом *настоящем* адаптере — все должны быть зелёные, плюс тесты на «два одинаковых сообщения → разный шифротекст» (forward secrecy) и smoke на эмуляторе (3 участника, 10 сообщений).

**Побочный вывод сессии** (записан в арх-пак, не в этой таске): QR-pairing и «вступление в группу мессенджера» — НЕ одно и то же на уровне протокола (разные машины: живое рукопожатие vs асинхронный KeyPackage). Общим может быть только QR-сканер и хранилище доверия. См. [crypto-pairing.md](../../docs/architecture/crypto-pairing.md) §Trust-bootstrap reuse boundary.
