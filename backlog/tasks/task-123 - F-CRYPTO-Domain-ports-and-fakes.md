---
id: TASK-123
title: 'F-CRYPTO Domain ports + fakes (CryptoPort / GroupPort / KeyPackagePort, mock-first)'
status: Draft
assignee: []
created_date: '2026-07-10 16:40'
updated_date: '2026-07-10 16:40'
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
1. Определяются 3 доменных порта в `core/crypto/src/commonMain/kotlin/cryptokit/ports/`:
   - `CryptoPort` — «зашифруй сообщение / расшифруй сообщение».
   - `GroupPort` — «создай группу / добавь участника / убери участника / прими коммит».
   - `KeyPackagePort` — «опубликуй пачку моих ключей / забери один чужой ключ».
2. Для каждого порта пишется **fake-адаптер** на in-memory Map/HashSet — работает без Rust, без openmls, без сервера. Живёт в `core/crypto/src/commonTest/kotlin/cryptokit/fakes/`.
3. Пишутся **contract tests** — набор тестов которые обязан пройти любой adapter port'а (fake сейчас, real в TASK-124). Тесты формулируют инварианты: «после `addMember(X)` метод `hasMember(X)` возвращает true», «двойной `encrypt(msg)` даёт разные ciphertext'ы (nonce ratchet)».
4. Домен-код (будущий messenger, pairing) может писаться против портов — тестируется на fake, интегрируется на real когда real готов.

**Что НЕ входит в scope:**
- Real (openmls) adapter — это TASK-124.
- Persistence (SQLCipher) — это TASK-125.
- Rust workspace — это TASK-122.
- `IdentityVaultPort` (a.k.a. KeyVault) — уже в scope TASK-112 (Decision + implementation).

## Зачем

**Rule 6 (mock-first development) prerequisite.** Без портов + fakes консументы (TASK-67 pairing, TASK-42 messenger) заблокированы до окончания openmls integration. С портами + fakes они могут проектироваться и тестироваться немедленно, а openmls integration подключается как альтернативный adapter.

**Rule 1 (domain isolation) enforcement.** Домен НЕ должен знать про openmls / Rust / UniFFI. Порт — это seam через который эта изоляция обеспечивается. Fitness function (rule 7) может грепом проверять что `core/crypto/src/commonMain` не импортирует ничего Rust-специфичного.

## Что входит технически (для AI-агента)

**Domain ports** (все в `core/crypto/src/commonMain/kotlin/cryptokit/ports/`):

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

**Value types** в `cryptokit/ports/model/`:
- `IdentityKey(bytes: ByteArray)` — Ed25519 pubkey.
- `KeyPackage(bytes: ByteArray, expiresAt: Instant)` — opaque MLS KeyPackage payload.
- `Commit(bytes: ByteArray)` — opaque MLS Commit message.
- `GroupState(bytes: ByteArray)` — opaque group state snapshot (в TASK-124 это будет ratcheted MLS state, сейчас — plaintext member list).

**Fakes** (все в `core/crypto/src/commonTest/kotlin/cryptokit/fakes/`):
- `FakeCryptoPort` — encrypt = xor с deterministic key derived from groupId; decrypt reverses. Simulates ratchet через counter++ per group. НЕ безопасный крипто, но satisfies port contract.
- `FakeGroupPort` — in-memory `Map<GroupId, Set<IdentityKey>>` + `Map<GroupId, Long>` (epoch counter).
- `FakeKeyPackagePort` — in-memory `Map<IdentityKey, ArrayDeque<KeyPackage>>`.

**Contract tests** (в `core/crypto/src/commonTest/kotlin/cryptokit/contracts/`):
- `CryptoPortContract` — 5-7 инвариантов: encrypt→decrypt roundtrip, разные plaintext'ы дают разные ciphertext'ы, unknown groupId → error, decrypt чужого ciphertext → error, ratchet forward-secrecy (после encrypt старый ключ нельзя восстановить — на fake проверяется через counter increment).
- `GroupPortContract` — create → members = {creator}; addMember → member appears; removeMember → member gone; processCommit — epoch increments; двойной createGroup с тем же id → error.
- `KeyPackagePortContract` — publish batch → claim returns one → next claim returns next → pool exhaustion → null; refillIfLow пополняет когда pool < threshold.

**Fitness function** (rule 7): grep-based test на `core/crypto/src/commonMain/` — не должен импортировать: `okhttp`, `firebase.*`, `libsodium.*` (?), `openmls`, `uniffi`, `android.*`. Только Kotlin stdlib + coroutines + serialization.

**Dependencies rationale**:
- **TASK-112** — определяет shape `IdentityVaultPort` (a.k.a. KeyVault). Влияет на shape `CryptoPort` (нужен ли `signingKeyRef` параметр) и `GroupPort` (member identity resolution). Если TASK-112 ещё Draft — этот task ждёт Decision block заполнения.

**Non-dependencies** (intentionally):
- TASK-122 — не блокирует; это pure Kotlin.
- TASK-124 — этот task блокирует TASK-124, не наоборот.

## Состояние

**Draft.** Ждём TASK-112 Decision block. После него готова к `/speckit.specify`. Параллельна TASK-122.

---

## Готовый промт для `/speckit.specify`

```
Реализуй F-CRYPTO Domain ports + fakes (mock-first).

ЧТО СТРОИМ:
Три доменных порта (CryptoPort / GroupPort / KeyPackagePort) в core/crypto/commonMain + fake in-memory adapters в commonTest + contract tests. Pure Kotlin, zero Rust, zero openmls.

ЗАЧЕМ:
Разблокирует domain-side работу consumer'ов (TASK-67 pairing, TASK-42 messenger) до готовности openmls integration (TASK-124). Enforces rule 1 (domain isolation) через explicit seam. Rule 6 (mock-first) prerequisite.

SCOPE ВКЛЮЧАЕТ:
- CryptoPort interface (encryptMessage / decryptMessage) + value types (GroupId, Ciphertext).
- GroupPort interface (createGroup / addMember / removeMember / processCommit / getMembers) + value types (IdentityKey, KeyPackage, Commit, GroupState).
- KeyPackagePort interface (publish / claim / refillIfLow).
- Fake in-memory adapters для каждого порта.
- Contract tests для каждого порта — 5-7 инвариантов на порт, тесты проходят на fakes.
- Fitness function: grep-based check что commonMain НЕ импортирует okhttp / firebase / libsodium / openmls / uniffi / android.
- KeyVault integration: используем IdentityVaultPort из TASK-112 в signatures.

SCOPE НЕ ВКЛЮЧАЕТ:
- Real openmls adapter — TASK-124.
- Rust FFI toolchain — TASK-122.
- Persistence (SQLCipher) — TASK-125.
- IdentityVaultPort — уже в TASK-112.

DEPENDENCIES:
- TASK-112 (KeyVault port Decision) — влияет на shape доменных портов.

ACCEPTANCE CRITERIA:
- Все 3 порта определены в core/crypto/commonMain/kotlin/cryptokit/ports/.
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
- [ ] #1 [hand] CryptoPort / GroupPort / KeyPackagePort interfaces определены в `core/crypto/src/commonMain/kotlin/cryptokit/ports/`
- [ ] #2 [hand] Fakes для каждого порта компилируются в commonTest и satisfy contract tests
- [ ] #3 [hand] Contract tests: 5-7 инвариантов на порт, все зелёные — `./gradlew :core:crypto:commonTest`
- [ ] #4 [hand] Fitness function падает при добавлении import okhttp / firebase / openmls / uniffi / android в commonMain
- [ ] #5 [hand] Пример consumer usage snippet в `core/crypto/README.md` — как feature-код зовёт порты
- [ ] #6 [hand] TASK-112 Decision block интегрирован — signatures используют `IdentityVaultPort`
<!-- AC:END -->
