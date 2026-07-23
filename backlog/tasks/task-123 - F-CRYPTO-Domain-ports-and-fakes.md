---
id: TASK-123
title: 'F-CRYPTO Domain ports + fakes (CryptoPort / GroupPort / KeyPackagePort, mock-first)'
status: Done
assignee: []
created_date: '2026-07-10 16:40'
updated_date: '2026-07-23'
labels:
  - phase-2
  - F-feature
  - crypto
  - domain
  - ports
  - mock-first
milestone: m-1
dependencies:
  - TASK-112
references: specs/task-123-crypto-domain-ports/
priority: high
ordinal: 123000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

> **Контекст создания (2026-07-10).** См. TASK-122 для полного контекста decomposition. Эта таска — **pure Kotlin domain design**, без Rust, без openmls, без persistence. Разбита отдельно чтобы domain-side работа могла двигаться параллельно с Rust FFI foundation (TASK-122) и не блокировала будущих consumer'ов (TASK-67 pairing, TASK-42 messenger encryption).

## Что это простыми словами

**Определяем интерфейсы** между доменом и крипто-адаптерами — то что CLAUDE.md rule 1 называет «портами». Пишем 3 интерфейса на чистом Kotlin + fake-реализации на in-memory-структурах. **Никакого openmls, никакого Rust** — это TASK-124.

**Аналогия**: пишем контракт «водитель обязан уметь поворачивать руль, жать газ, тормозить», не выбирая между Toyota и Tesla. Другие фичи (pairing, messenger) уже могут проектировать «водитель приезжает по адресу», зная что контракт есть, даже если реальный автомобиль ещё в разработке.

**Что происходит по шагам:**
1. Определяются 3 доменных порта в `core/crypto/src/commonMain/kotlin/family/crypto/ports/`:
   - `CryptoPort` — «зашифруй сообщение / расшифруй сообщение».
   - `GroupPort` — «создай группу / добавь участника / убери участника / прими коммит».
   - `KeyPackagePort` — «опубликуй пачку моих ключей / забери один чужой ключ».
2. Для каждого порта пишется **fake-адаптер** на in-memory Map/HashSet — работает без Rust, без openmls, без сервера. Живёт в `core/crypto/src/commonTest/kotlin/family/crypto/fakes/`.
3. Пишутся **contract tests** — набор тестов которые обязан пройти любой adapter port'а (fake сейчас, real в TASK-124). Тесты формулируют инварианты: «после `addMember(X)` метод `hasMember(X)` возвращает true», «двойной `encrypt(msg)` даёт разные ciphertext'ы (nonce ratchet)».
4. Домен-код (будущий messenger, pairing) может писаться против портов — тестируется на fake, интегрируется на real когда real готов.

**Что НЕ входит в scope:**
- Real (openmls) adapter — это TASK-124.
- Persistence (SQLCipher) — это TASK-125.
- Rust workspace — это TASK-122.
- `KeyVault` (boundary-2 export-hatch, `family.keys.api`, `:core:keys`) — уже в scope TASK-112. **Доменные порты его НЕ импортируют**: signing-материал приходит в openmls через `KeyVault.exportDerivedKey(MLS_SIGNATURE, …)` внутри real-адаптера (TASK-124), не параметром доменного порта. См. [crypto-key-hierarchy.md §Key vault](../../docs/architecture/crypto-key-hierarchy.md).

## Зачем

**Rule 6 (mock-first development) prerequisite.** Без портов + fakes консументы (TASK-67 pairing, TASK-42 messenger) заблокированы до окончания openmls integration. С портами + fakes они могут проектироваться и тестироваться немедленно, а openmls integration подключается как альтернативный adapter.

**Rule 1 (domain isolation) enforcement.** Домен НЕ должен знать про openmls / Rust / UniFFI. Порт — это seam через который эта изоляция обеспечивается. Fitness function (rule 7) может грепом проверять что `core/crypto/src/commonMain` не импортирует ничего Rust-специфичного.

## Что входит технически (для AI-агента)

> **⚠️ Сигнатуры ниже — provisional (reconcile 2026-07-22).** Арх-пак ([crypto-mls.md:15](../../docs/architecture/crypto-mls.md)) требует проектировать методы `CryptoPort/GroupPort/KeyPackagePort` **от формы Wire `CoreCrypto`/`Conversation` API** (`add_members`/`remove_members`/`self_update`/`process_message → StagedCommit`, `KeyPackageBuilder…last_resort()`), а не от угаданных здесь. Финальные сигнатуры фиксируются в spec.md. Ниже — стартовая форма для обсуждения.

**Domain ports** (все в `core/crypto/src/commonMain/kotlin/family/crypto/ports/`, namespace `family.crypto.*` — `cryptokit.*` забанен fitness-тестом `NoLegacyFamilyNamespaceTest`, TASK-141):

- **`CryptoPort`** — encrypt/decrypt message в контексте группы.
  ```kotlin
  interface CryptoPort {
      suspend fun encryptMessage(groupId: GroupId, plaintext: ByteArray): Ciphertext
      suspend fun decryptMessage(groupId: GroupId, ciphertext: Ciphertext): ByteArray
  }
  ```
  Value types: `GroupId(opaqueId: String)`, `Ciphertext(bytes: ByteArray)`.

- **`GroupPort`** — group lifecycle operations.
  ```kotlin
  interface GroupPort {
      suspend fun createGroup(groupId: GroupId, creatorIdentity: IdentityKey): GroupState
      suspend fun addMember(groupId: GroupId, memberKeyPackage: KeyPackage): Commit
      suspend fun removeMember(groupId: GroupId, memberIdentity: IdentityKey): Commit
      suspend fun processCommit(groupId: GroupId, commit: Commit): GroupState
      suspend fun getMembers(groupId: GroupId): Set<IdentityKey>
  }
  ```

- **`KeyPackagePort`** — pool of one-time invitation keys.
  ```kotlin
  interface KeyPackagePort {
      suspend fun publish(batch: List<KeyPackage>): Result<Unit>
      suspend fun claim(targetIdentity: IdentityKey): KeyPackage?  // null if pool empty
      suspend fun refillIfLow(threshold: Int, batchSize: Int)
  }
  ```

**Value types** в `family/crypto/ports/model/` (какие реально заводим — открытый вопрос spec.md; ниже стартовый набор):
- `IdentityKey(bytes: ByteArray)` — Ed25519 pubkey.
- `KeyPackage(bytes: ByteArray, expiresAt: Instant)` — opaque MLS KeyPackage payload.
- `Commit(bytes: ByteArray)` — opaque MLS Commit message.
- **`Ciphertext` — переиспользуем существующий `family.crypto.api.values.Ciphertext`, НЕ заводим второй** (арх-пак прямо запрещает дубль — [crypto-key-hierarchy.md:98](../../docs/architecture/crypto-key-hierarchy.md)).
- **`GroupState` — НЕ доменный value-тип и НЕ возвращается из портов.** Group state opaque и персистится целиком openmls `StorageProvider` ([crypto-mls.md:24,42](../../docs/architecture/crypto-mls.md)). На fake — in-memory member list за скрытым counter'ом, наружу не отдаётся как «снимок».
- Авторизация (кто может add/remove) — политика приложения, не метод `GroupPort` (инвариант ML6); primary-user device — единственный подписант Commit (TASK-102).

**Fakes** (все в `core/crypto/src/commonTest/kotlin/family/crypto/fakes/`):
- `FakeCryptoPort` — encrypt = xor с deterministic key derived from groupId; decrypt reverses. Simulates ratchet через counter++ per group. НЕ безопасный крипто, но satisfies port contract.
- `FakeGroupPort` — in-memory `Map<GroupId, Set<IdentityKey>>` + `Map<GroupId, Long>` (epoch counter).
- `FakeKeyPackagePort` — in-memory `Map<IdentityKey, ArrayDeque<KeyPackage>>`.

**Contract tests** (в `core/crypto/src/commonTest/kotlin/family/crypto/contracts/`):
- `CryptoPortContract` — 5-7 инвариантов: encrypt→decrypt roundtrip, разные plaintext'ы дают разные ciphertext'ы, unknown groupId → error, decrypt чужого ciphertext → error, ratchet forward-secrecy (после encrypt старый ключ нельзя восстановить — на fake проверяется через counter increment).
- `GroupPortContract` — create → members = {creator}; addMember → member appears; removeMember → member gone; processCommit — epoch increments; двойной createGroup с тем же id → error.
- `KeyPackagePortContract` — publish batch → claim returns one → next claim returns next → pool exhaustion → null; refillIfLow пополняет когда pool < threshold.

**Fitness function** (rule 7): grep-based test на `core/crypto/src/commonMain/` — не должен импортировать: `okhttp`, `firebase.*`, `libsodium.*` (?), `openmls`, `uniffi`, `android.*`. Только Kotlin stdlib + coroutines + serialization.

**Dependencies rationale**:
- **TASK-112** — Decision block заполнен и контракт `KeyVault` заморожен (export-hatch). **Он НЕ входит в сигнатуры доменных портов** этой таски (KeyVault консумится openmls-адаптером TASK-124, не commonMain-портами). Зависимость нужна лишь чтобы стартовать от финальной формы vault-границы, а не старого god-port'а.

**Non-dependencies** (intentionally):
- TASK-122 — не блокирует; это pure Kotlin.
- TASK-124 — этот task блокирует TASK-124, не наоборот.

## Состояние

**In Progress (2026-07-22) — speckit-набор готов, verdict READY.** Пройден полный цикл specify → clarify → plan → tasks → analyze (сценарии пропущены: headless-контракт без UI). Артефакты: `spec.md` (11 FR, 3 US, 6 SC), `plan.md` (Constitution 4 PASS/4 N/A/0 FAIL), `data-model.md`, `tasks.md` (T001–T015, trace чистый), `analyze-report.md` (READY).

Финальный контракт (разрешено в clarify через research по crypto-mls.md, не бизнес-выбор):
- Форма портов = зеркало Wire CoreCrypto двухфазного commit: `GroupPort` (`createGroup`/`addMembers`→`CommitBundle`/`removeMembers`/`selfUpdate`/`commitToPendingProposals`/`mergePendingCommit`/`processMessage`→sealed `ProcessedMessage`), `CryptoPort` (`encryptMessage`/`decryptMessage`), `KeyPackagePort` (`publish(isLastResort)`/`claim`→`ClaimResult`/`localCount`). Все `suspend`.
- `IdentityKey` = новый opaque `value class` (reuse невозможен — `PublicKey` в `:core:pairing` после TASK-146).
- Last-resort — first-class в доменном порту (RFC 9750 §5.1).
- Порты БЕЗ `@Serializable` (`:core:crypto` без сериализации, TASK-146); wire-кодирование = адаптер TASK-124.
- Fitness FR-005 = konsist import-ban; контракт-тесты abstract+factory (переиспользуемы real-адаптером TASK-124, SC-005).

Scope: pure-Kotlin, verification `./gradlew :core:crypto:commonTest`, без device/сети, ноль deferred-гейтов. Вне scope: real openmls-адаптер (TASK-124), persistence (TASK-125). Следующий шаг — `/speckit.implement`.

---

## Готовый промт для `/speckit.specify`

```
Реализуй F-CRYPTO Domain ports + fakes (mock-first).

ЧТО СТРОИМ:
Три доменных порта (CryptoPort / GroupPort / KeyPackagePort) в модуле :core:crypto, namespace family.crypto.* (НЕ cryptokit — забанен fitness-тестом NoLegacyFamilyNamespaceTest, TASK-141), commonMain + fake in-memory adapters в commonTest + contract tests. Pure Kotlin, zero Rust, zero openmls. Сигнатуры методов проектируются ОТ формы Wire CoreCrypto/Conversation API (add_members/remove_members/self_update/process_message→StagedCommit, KeyPackageBuilder…last_resort), не из головы — см. docs/architecture/crypto-mls.md.

ЗАЧЕМ:
Разблокирует domain-side работу consumer'ов (TASK-67 pairing, TASK-42 messenger) до готовности openmls integration (TASK-124). Enforces rule 1 (domain isolation) через explicit seam. Rule 6 (mock-first) prerequisite.

SCOPE ВКЛЮЧАЕТ:
- CryptoPort interface (encryptMessage / decryptMessage) + value types (GroupId, Ciphertext).
- GroupPort interface (createGroup / addMember / removeMember / processCommit / getMembers) + value types (IdentityKey, KeyPackage, Commit, GroupState).
- KeyPackagePort interface (publish / claim / refillIfLow).
- Fake in-memory adapters для каждого порта.
- Contract tests для каждого порта — 5-7 инвариантов на порт, тесты проходят на fakes.
- Fitness function: grep-based check что commonMain НЕ импортирует okhttp / firebase / openmls / uniffi / android / cryptokit.
- Ciphertext: переиспользовать существующий family.crypto.api.values.Ciphertext, НЕ заводить второй.

SCOPE НЕ ВКЛЮЧАЕТ:
- Real openmls adapter — TASK-124.
- Rust FFI toolchain — TASK-122.
- Persistence (SQLCipher) — TASK-125.
- KeyVault (family.keys.api, :core:keys) — TASK-112; доменные порты его НЕ импортируют (KeyVault консумит openmls-адаптер TASK-124 через exportDerivedKey, не commonMain-порт).
- GroupState как возвращаемый доменный тип — group state opaque, персистится openmls StorageProvider (TASK-124/125), наружу не отдаётся.

DEPENDENCIES:
- TASK-112 (KeyVault contract, заморожен) — задаёт форму vault-границы; НЕ входит в сигнатуры этих портов.

ACCEPTANCE CRITERIA:
- Все 3 порта определены в core/crypto/commonMain/kotlin/family/crypto/ports/.
- FakeCryptoPort / FakeGroupPort / FakeKeyPackagePort компилируются в commonTest.
- CryptoPortContract / GroupPortContract / KeyPackagePortContract — 5-7 инвариантов на порт, все зелёные на fakes.
- Fitness function падает если добавить import okhttp / firebase / openmls в commonMain.
- Пример consumer usage: minimal Kotlin snippet показывающий как feature-код зовёт порты — в README `core/crypto/README.md`.

LOCAL TEST PATH:
- `./gradlew :core:crypto:commonTest` — все contract tests зелёные.
- Fitness function: `./gradlew :core:crypto:testFitness` (или через konsist).

CONSTITUTION GATES:
- Rule 1 (domain isolation): порты в commonMain, adapters в otherMain — hard separation.
- Rule 2 (ACL): fakes НЕ считаются adapter'ами внешних SDK — они pure Kotlin.
- Rule 6 (mock-first): весь point этой таски.
- Rule 7 (fitness functions): grep check на forbidden imports.
- Rule 8 (server migration): N/A — pure client-side ports.

EFFORT: ~3-5 дней (12-20 часов).
```

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] CryptoPort / GroupPort / KeyPackagePort interfaces определены в `core/crypto/src/commonMain/kotlin/family/crypto/ports/`
- [x] #2 [hand] Fakes для каждого порта компилируются в commonTest и satisfy contract tests
- [x] #3 [hand] Contract tests: 5-7 инвариантов на порт, все зелёные — `./gradlew :core:crypto:jvmTest` (commonTest прогоняется на JVM; чистого таска `commonTest` в KMP нет)
- [x] #4 [hand] Fitness function падает при добавлении import okhttp / firebase / openmls / uniffi / android / `cryptokit` в ports (PortsNoVendorImportTest, обе стороны)
- [x] #5 [hand] Пример consumer usage snippet в `core/crypto/README.md` — как feature-код зовёт порты
- [x] #6 [hand] Доменные порты KeyVault не импортируют; `Ciphertext` переиспользован из `family.crypto.api.values`, `GroupState` не возвращается наружу — signatures спроектированы от Wire CoreCrypto shape (crypto-mls.md)
<!-- AC:END -->

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Final Summary

Реализовано в PR (ветка `task-123-crypto-domain-ports`, impl-commit `f559f05`). 15/15 tasks (T001–T015) закрыты, tick-sync в том же diff.

- **Ports** (`family.crypto.ports`, commonMain): `GroupPort` / `CryptoPort` / `KeyPackagePort` + opaque value-типы + `sealed`-результаты. `Ciphertext` reused (FR-006), без `@Serializable` / `GroupState` / `KeyVault` (FR-002/7/8).
- **Fakes** (`family.crypto.fake`, commonTest): `FakeGroupPort` (two-phase merge, epoch-ratchet), `FakeCryptoPort` (insecure xor+nonce, forward-secrecy on merge), `FakeKeyPackagePort` (one-time pool + last-resort).
- **Contract tests** (`family.crypto.contracts`, commonTest): 3 abstract + factory, bound to fakes, переиспользуются реальным адаптером TASK-124 (SC-005). 18 инвариантов, все зелёные.
- **Fitness**: `PortsNoVendorImportTest` (jvmTest) — vendor/platform import-ban, обе стороны.
- **Gate**: `./gradlew :core:crypto:jvmTest :core:crypto:verifyCryptoIsolation` — BUILD SUCCESSFUL.

Нет deferred-гейтов (всё закрывается в чистом JVM без устройства/сети) → сразу Done. Hands off to TASK-124 (real openmls adapter расширяет те же контракты).
<!-- SECTION:FINAL_SUMMARY:END -->
