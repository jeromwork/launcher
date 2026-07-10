---
id: TASK-124
title: 'F-CRYPTO openmls integration (Rust adapter + UniFFI + in-memory storage)'
status: Draft
assignee: []
created_date: '2026-07-10 16:40'
updated_date: '2026-07-10 16:40'
labels:
  - phase-2
  - F-feature
  - crypto
  - mls
  - openmls
  - rust-ffi
milestone: m-1
dependencies:
  - TASK-122
  - TASK-123
priority: high
ordinal: 124000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

> **Контекст создания (2026-07-10).** См. TASK-122 для полного контекста decomposition. Эта таска — **реальная MLS integration**, связывающая Rust FFI toolchain (TASK-122) с доменными портами (TASK-123). Storage провайдер здесь — **in-memory only**; SQLCipher persistence — отдельная TASK-125, чтобы не смешивать «крипта работает» и «крипта переживает reboot» в одном PR.

## Что это простыми словами

Добавляем **реальную MLS-крипту** в Rust workspace через `openmls` (Rust crate от IETF-authors MLS protocol). Обёртываем через UniFFI. Пишем Kotlin adapter который implement'ит доменные порты (`CryptoPort`, `GroupPort`) из TASK-123 — уже НЕ на fake in-memory-Map, а на реальном openmls state. **Persistence — только in-memory** пока, MLS-состояние теряется при рестарте app'а. Это deliberate — persistence в TASK-125.

**Аналогия**: у нас есть чёрный водитель-контракт (порты из TASK-123, тесты на fake-Toyota) и построенный завод (Rust FFI из TASK-122). Эта таска — **привозим настоящую Tesla** и подключаем её к контракту водителя. Но заправляем её на глаз — топливо всё равно закончится при перезапуске (persistence в TASK-125 разбирается с бензобаком).

**Что происходит по шагам:**
1. В Rust workspace добавляется openmls 0.8.1 (pinned в crypto.md frontmatter) как dependency.
2. Пишется Rust wrapper `crypto-ffi/src/mls.rs` — экспортирует UniFFI-friendly API (openmls нативно stateful, wrapper делает его stateless через explicit `MlsGroup` передаваемый параметром).
3. UDL файл дополняется MLS types (Group, KeyPackage, Commit, Ciphertext).
4. In-memory `StorageProvider` — реализация openmls `StorageProvider` trait, backed by `HashMap` в Rust. НЕ переживает reboot — это ok, persistence в TASK-125.
5. Kotlin adapter `core/crypto/src/androidMain/kotlin/cryptokit/adapters/openmls/OpenMlsGroupPort.kt` (+ `OpenMlsCryptoPort.kt`) — implement'ит порты из TASK-123 через UniFFI-generated Kotlin API.
6. Wire format roundtrip тесты — MLS сообщение сериализуется в bytes, десериализуется, эквивалентно исходному.
7. Property-based тесты — рандомные последовательности `addMember` / `encrypt` / `removeMember` / `processCommit` не должны падать / не должны терять inv'ы.
8. Существующие contract tests из TASK-123 запускаются на real adapter — все зелёные.

**Что НЕ входит в scope:**
- SQLCipher persistence — TASK-125.
- iOS build — deferred в TASK-26.
- KeyPackage server publishing — TASK-104 (server-side logic) + `KeyPackagePort` real adapter требует serверный endpoint, поэтому в этой таске `KeyPackagePort` может остаться на fake in-memory, или local-only implementation (client shares own keypackage через QR/pairing без сервера).

## Зачем

**Первая реальная крипто-фича доступна для домена.** С этой таской TASK-67 (Pairing) может использовать real openmls group operations вместо fake. TASK-42 (messenger encryption) unblocked технически.

**Isolation of persistence bug surface.** Persistence — сложная тема с миграциями, encryption-at-rest keys, DB corruption recovery. Разбирать её ортогонально к «работает ли MLS-крипта в принципе» — правильный split.

**~1-2 недели самая большая таска в этой цепочке.** Это ожидаемо: openmls integration = 20-30 часов (per TASK-58 research), плюс наш UniFFI wrapper + Kotlin adapter + тесты = 8-15 часов сверху.

## Что входит технически (для AI-агента)

**Rust changes** в `crypto-ffi/`:
- `Cargo.toml` добавляет `openmls = "=0.8.1"` (exact version pin per crypto.md).
- `crypto-ffi/src/mls.rs` — UniFFI-exportable wrappers:
  - `fn create_group(group_id: String, creator_credential: Vec<u8>) -> Vec<u8>` — returns serialized MlsGroup state.
  - `fn add_member(group_state: Vec<u8>, member_key_package: Vec<u8>) -> AddMemberResult` — returns new state + commit.
  - `fn remove_member(group_state: Vec<u8>, member_identity: Vec<u8>) -> RemoveMemberResult`.
  - `fn process_commit(group_state: Vec<u8>, commit: Vec<u8>) -> Vec<u8>` — returns updated state.
  - `fn encrypt(group_state: Vec<u8>, plaintext: Vec<u8>) -> EncryptResult` — returns ciphertext + updated state (ratchet forward).
  - `fn decrypt(group_state: Vec<u8>, ciphertext: Vec<u8>) -> DecryptResult` — returns plaintext + updated state.
- `crypto-ffi/src/storage.rs` — `InMemoryStorageProvider` implementing openmls `StorageProvider` trait via `HashMap`.
- UDL файл дополняется MLS types.

**Kotlin adapter** в `core/crypto/src/androidMain/kotlin/cryptokit/adapters/openmls/`:
- `OpenMlsGroupPort.kt` — implements `GroupPort` (TASK-123) через UniFFI-generated Kotlin functions.
- `OpenMlsCryptoPort.kt` — implements `CryptoPort` similarly.
- **State management pattern**: adapter держит `MutableMap<GroupId, ByteArray>` в памяти (group state serialized). Каждый method: read state → call Rust → get new state + result → write state back.
- **KeyPackagePort** пока остаётся на fake (client-local); real adapter добавляется когда TASK-104 (KeyPackage server) landing.

**Tests**:
- Contract tests из TASK-123 запускаются на `OpenMlsGroupPort` / `OpenMlsCryptoPort` — все зелёные.
- Wire format roundtrip тесты:
  - `MlsMessageRoundtripTest` — encrypt → serialize → deserialize → decrypt → plaintext equals original.
  - `GroupStateRoundtripTest` — group state serialize → deserialize → operations produce same result.
- Property-based тест (kotest-property Arb) — 100 random sequences (create + N adds + M encrypts + K removes + processCommits), assert no exception + members set consistent + ratchet advances.
- **Forward secrecy assertion**: `encrypt` дважды одним plaintext'ом → разные ciphertext'ы (ratchet moved).
- Emulator smoke test — creates 3-member group, encrypts 10 messages, all decrypt correctly.

**Fitness functions** (rule 7):
- Grep-based: `core/crypto/src/commonMain/` — не импортирует `openmls*`, `uniffi*`. Только androidMain / iosMain могут.
- Kotlin binding version check — `cryptokit_ffi_kotlin.*` version соответствует Rust `Cargo.lock` uniffi version.

**Documentation**:
- Update `docs/architecture/crypto.md` frontmatter: `mls-library.decision-status: implemented (2026-XX-XX)` after merge.
- Add MLS integration section в `core/crypto/README.md` с примерами.

**Non-implementation notes**:
- **iOS deferred**: iosMain adapter — stubs throwing NotImplementedError. Implementation в V-1 (TASK-26).
- **StorageProvider trait**: openmls требует implementation. In-memory здесь — ok. TASK-125 подставит SQLCipher via same trait, adapter swap на app-level DI.
- **UniFFI async**: openmls sync API. Kotlin adapter обёртывает в `withContext(Dispatchers.IO)` — стандартный pattern.
- **Panic handling**: UniFFI panic catcher должен быть active (crypto.md frontmatter follow-up-flag). Verify в этой таске.

## Состояние

**Draft.** Ждём TASK-122 (Rust FFI foundation) + TASK-123 (порты определены) → готова к `/speckit.specify`.

---

## Готовый промт для `/speckit.specify`

```
Реализуй F-CRYPTO openmls integration.

ЧТО СТРОИМ:
Rust wrapper над openmls 0.8.1 (pinned), UniFFI Kotlin bindings, Kotlin adapters implement'ящие CryptoPort + GroupPort из TASK-123. In-memory StorageProvider (persistence — отдельная TASK-125). Wire format roundtrip тесты. Property-based тесты. Emulator smoke test — 3-member group + 10 encrypted messages.

ЗАЧЕМ:
Первая реальная MLS-крипта доступна домену. Разблокирует TASK-67 (Pairing) и TASK-42 (messenger encryption) на domain-стороне. Изолирует complexity persistence в отдельный PR (TASK-125).

SCOPE ВКЛЮЧАЕТ:
- Rust: openmls 0.8.1 dependency, wrappers в crypto-ffi/src/mls.rs, InMemoryStorageProvider.
- UDL: MLS types (Group, KeyPackage, Commit, Ciphertext).
- Kotlin androidMain: OpenMlsGroupPort + OpenMlsCryptoPort implementing ports из TASK-123.
- KeyPackagePort — на fake (local-only) пока TASK-104 server-side не готов.
- Contract tests из TASK-123 запускаются на real adapters, все зелёные.
- Wire format roundtrip тесты (MLS message + group state).
- Property-based тест (100 random sequences).
- Forward secrecy assertion (двойной encrypt → разные ciphertext'ы).
- Emulator smoke (skill android-emulator).
- Fitness function: commonMain не импортирует openmls / uniffi.
- Update crypto.md frontmatter decision-status после merge.

SCOPE НЕ ВКЛЮЧАЕТ:
- SQLCipher persistence — TASK-125.
- iOS adapter — TASK-26 (V-1).
- KeyPackage server publishing — TASK-104.
- Group history persistence across app restart — TASK-125.

DEPENDENCIES:
- TASK-122 (Rust FFI toolchain работает).
- TASK-123 (порты определены, contract tests существуют).

ACCEPTANCE CRITERIA:
- Contract tests из TASK-123 зелёные на OpenMlsGroupPort + OpenMlsCryptoPort.
- Wire format roundtrip: encrypt → serialize → deserialize → decrypt → plaintext equals original.
- Property-based: 100 random sequences (create + N add + M encrypt + K remove + processCommit) — no exception, members set consistent.
- Forward secrecy: encrypt(msg) дважды подряд → разные ciphertext'ы (ratchet).
- Emulator smoke: 3-member group, 10 messages encrypt+decrypt, все зелёные.
- Fitness function: commonMain НЕ импортирует openmls / uniffi (androidMain / iosMain — можно).
- After merge: crypto.md frontmatter mls-library.decision-status = "implemented (YYYY-MM-DD)".

LOCAL TEST PATH:
- `./gradlew :core:crypto:commonTest` — contract + roundtrip + property-based.
- Emulator: skill android-emulator → pixel_5_api_34 → smoke test.

CONSTITUTION GATES:
- Rule 1 (domain isolation): commonMain импорты чистые, adapter в androidMain.
- Rule 2 (ACL): openmls types полностью инкапсулированы в adapter (не вытекают в domain).
- Rule 3 (one-way door): openmls choice = one-way door (Rust workspace + wire format persistence в TASK-125). Exit ramp — swap на mls-rs (~1-2 недели adapter rewrite, RFC 9420 compatible), documented в crypto.md frontmatter.
- Rule 5 (wire format versioning): MLS wire format уже versioned by MLS RFC 9420. Наш schemaVersion — на envelope-level поверх (если понадобится).
- Rule 6 (mock-first): реальный adapter приходит после fakes — правильный порядок.
- Rule 7 (fitness functions): grep-based import check + UniFFI version lockstep.
- Rule 8 (server migration): N/A — MVP client-only. KeyPackagePort server-side в TASK-104.

EFFORT: ~1-2 недели (30-50 часов).
```

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Contract tests из TASK-123 зелёные на OpenMlsGroupPort + OpenMlsCryptoPort
- [ ] #2 [hand] Wire format roundtrip тесты зелёные (MLS message + group state)
- [ ] #3 [hand] Property-based тест: 100 random sequences (create + add/remove/encrypt/processCommit) — no exception, members consistent
- [ ] #4 [hand] Forward secrecy assertion: два `encrypt(msg)` подряд → разные ciphertext'ы (ratchet)
- [ ] #5 [hand] Emulator smoke test: 3-member group, 10 messages encrypt+decrypt на pixel_5_api_34 — все зелёные
- [ ] #6 [hand] Fitness function падает если commonMain импортирует openmls / uniffi
- [ ] #7 [hand] `docs/architecture/crypto.md` frontmatter обновлён: `mls-library.decision-status: implemented (YYYY-MM-DD)`
<!-- AC:END -->
