---
id: TASK-125
title: 'F-CRYPTO SQLCipher storage provider (openmls persistence)'
status: Draft
assignee: []
created_date: '2026-07-10 16:40'
updated_date: '2026-07-10 16:40'
labels:
  - phase-2
  - F-feature
  - crypto
  - persistence
  - sqlcipher
  - openmls
milestone: m-1
dependencies:
  - TASK-124
priority: high
ordinal: 125000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

> **Контекст создания (2026-07-10).** См. TASK-122 для полного контекста decomposition. Эта таска добавляет **persistence** к openmls integration из TASK-124. Отделена, чтобы бонус «MLS работает» (TASK-124) можно было мержить и валидировать до того как persistence-сложность (SQLCipher setup, migration, Android Keystore integration) войдёт в scope.

## Что это простыми словами

Добавляем **бензобак** к Tesla из TASK-124. Сейчас MLS-состояние живёт в памяти и умирает при перезапуске app'а — это **дизайн-заглушка**, не bug. Эта таска заменяет in-memory storage на SQLCipher — encrypted SQLite базу — так что MLS-состояние переживает reboot.

**Что происходит по шагам:**
1. Добавляется зависимость `net.zetetic:android-database-sqlcipher` (или KMP-совместимый аналог).
2. Пишется Kotlin `SQLCipherStorageProvider` — реализует openmls `StorageProvider` trait через SQLCipher-backed таблицы.
3. **Encryption key** для SQLCipher хранится в Android Keystore (AES-256-GCM wrap), unlock через TEE.
4. При старте app'а: unwrap SQLCipher key → open DB → openmls adapter получает real StorageProvider вместо `InMemoryStorageProvider`.
5. DI (Koin) swap'ает InMemoryStorageProvider → SQLCipherStorageProvider (adapter tier).
6. Migration flow (первый запуск с этой версией): нет — старой persistence не было, начинаем с чистого листа.
7. Tests: (a) все contract tests из TASK-123 зелёные на SQLCipher-backed adapter, (b) explicit persistence test — encrypt message, kill process, restart, decrypt тот же message.

**Что НЕ входит в scope:**
- MLS crypto logic — TASK-124.
- iOS persistence — deferred в TASK-26 (V-1, iOS uses другой SQLite backend).
- Multi-device sync group state — separate concern (архитектура single-device MLS state в MVP).
- Backup/export group state — TASK-38 (backup / disaster recovery, m-4).

## Зачем

**MLS без persistence бесполезен в проде.** Юзер закрывает app → группа теряется → пересобирать pairing / re-adding members. Это не launch-ready.

**Отдельно от TASK-124**: persistence имеет свою complexity — SQLCipher initialization, TEE key management, migration story, DB corruption recovery. Смешивать это с «MLS-crypto работает?» — раздувает PR до непроверяемого. В TASK-124 после merge можно продемонстрировать «MLS работает» через emulator smoke, независимо от того landings TASK-125 или нет.

**Signal / Wire pattern**: обе продукции используют encrypted SQL (SQLCipher для Signal, custom encrypted SQL для Wire). Precedented industrial choice.

## Что входит технически (для AI-агента)

**Kotlin implementation** в `core/crypto/src/androidMain/kotlin/cryptokit/adapters/openmls/storage/`:

- `SQLCipherStorageProvider.kt` — implements openmls `StorageProvider` trait через UniFFI-callback pattern (Rust вызывает Kotlin callbacks для persistence operations).
  - Alternative pattern: полностью Rust-side implementation через `rusqlite` + SQLCipher — decision в spec.md phase.
- `SqlCipherKeyManager.kt` — управляет encryption key: generate (первый запуск), wrap с Android Keystore AES-256-GCM (StrongBox if available, TEE fallback), unwrap на старте.
- `MlsStateSchema.kt` — SQL schema:
  - Table `mls_groups(group_id TEXT PK, epoch INTEGER, state_blob BLOB)`.
  - Table `mls_key_packages(package_id TEXT PK, expires_at INTEGER, package_blob BLOB, used INTEGER)`.
  - Table `mls_pending_commits(commit_id TEXT PK, group_id TEXT, commit_blob BLOB, applied_at INTEGER)`.

**Rust changes** в `crypto-ffi/src/storage.rs`:
- Или (option A): keep `InMemoryStorageProvider`, add `KotlinCallbackStorageProvider` который через UniFFI callback interface вызывает Kotlin (реализует SQLCipher).
- Или (option B): add `SqliteStorageProvider` в Rust через `rusqlite` — self-contained. Decision в plan.md phase.

**Android Keystore integration**:
- `AndroidKeystoreSecureStore.kt` (может уже быть — см. `SecureKeyStore.android.kt` per crypto.md).
- SQLCipher DB key = 32 random bytes → wrap AES-256-GCM с TEE-resident key → save wrapped bytes в EncryptedSharedPreferences.
- Unwrap на старте app'а через TEE call.

**DI wiring** в `app/di/CryptoModule.kt`:
- Old: `single<StorageProvider> { InMemoryStorageProvider() }`.
- New: `single<StorageProvider> { SQLCipherStorageProvider(get<SqliteDatabase>(), get<SqlCipherKeyManager>()) }`.

**Tests**:
- Все contract tests из TASK-123 запускаются на SQLCipher-backed OpenMlsGroupPort / OpenMlsCryptoPort — все зелёные.
- **Persistence integration test** (androidUnitTest с Robolectric OR emulator androidTest):
  - Create group, add member, encrypt message.
  - Explicitly close DB / recreate provider instance.
  - Reopen DB → decrypt message → equals original.
- **Key rotation test** (unit): Android Keystore key updated → old DB unusable → migration path (или fresh DB per spec decision).
- **DB corruption recovery** (unit): SQL query returns wrong data → adapter returns Failure — не крашит app.
- Emulator smoke: full user journey (create → encrypt → kill process (`adb kill`) → restart → decrypt) на pixel_5_api_34.

**Non-scope decisions to defer to spec.md**:
- Rust-side vs Kotlin-callback StorageProvider — evaluated в plan phase.
- KeyPackage persistence — если TASK-104 (server pool) уже landed to this point, adapter может подключать server; иначе — local persistence adequate.

**Documentation**:
- Update `docs/architecture/crypto.md` frontmatter: `encrypted-keystore.decision-status: implemented (YYYY-MM-DD)`.
- Add persistence section в `core/crypto/README.md`.

**Constitution notes**:
- **Rule 5 (wire format)**: SQL schema включает `schema_version INT` field на каждую table для future migrations.
- **Rule 8 (server migration)**: N/A pure client-side.
- **Rule 12 (server hardening)**: N/A no server touch.
- **Rule 13 (zero-knowledge server)**: N/A — SQLCipher-backed state полностью client-side.

## Состояние

**Draft.** Ждём TASK-124 (openmls integration работает) → готова к `/speckit.specify`.

---

## Готовый промт для `/speckit.specify`

```
Реализуй F-CRYPTO SQLCipher storage provider.

ЧТО СТРОИМ:
SQLCipher-backed StorageProvider для openmls (замена InMemoryStorageProvider из TASK-124). Android Keystore-wrapped encryption key (AES-256-GCM, StrongBox если доступен). DI swap in-memory → SQLCipher на adapter tier. Persistence integration test: create group / encrypt / kill process / restart / decrypt.

ЗАЧЕМ:
MLS без persistence не launch-ready. Изолирует persistence complexity от TASK-124 «MLS-crypto работает?» — можно смержить TASK-124 и продемонстрировать independent from persistence.

SCOPE ВКЛЮЧАЕТ:
- SQLCipher dependency + init.
- SQLCipherStorageProvider реализующий openmls StorageProvider trait.
- SqlCipherKeyManager: generate DB key, wrap Android Keystore, unwrap на старте.
- SQL schema (mls_groups + mls_key_packages + mls_pending_commits) с schemaVersion field (rule 5).
- DI swap в app/di/CryptoModule.kt.
- Все contract tests из TASK-123 зелёные на SQLCipher adapter.
- Persistence integration test (kill process → restart → decrypt).
- Key rotation test.
- DB corruption recovery test.
- Emulator smoke: full user journey.
- Documentation update: crypto.md frontmatter + README.

SCOPE НЕ ВКЛЮЧАЕТ:
- iOS persistence — TASK-26 (V-1).
- Multi-device sync group state — separate concern.
- Backup/export — TASK-38.

DEPENDENCIES:
- TASK-124 (openmls integration работает с InMemoryStorageProvider).

ACCEPTANCE CRITERIA:
- Все contract tests из TASK-123 зелёные на SQLCipher-backed adapters.
- Persistence integration test: create group, encrypt, kill process (via adb), restart app, decrypt → plaintext equals original.
- Key rotation: rotate Android Keystore key, старая DB inaccessible → migration path clear (или fresh DB per spec decision).
- DB corruption recovery: adapter возвращает Failure, не крашит app.
- Emulator smoke (skill android-emulator): full user journey pass на pixel_5_api_34.
- crypto.md frontmatter обновлён: encrypted-keystore.decision-status = "implemented (YYYY-MM-DD)".

LOCAL TEST PATH:
- `./gradlew :core:crypto:androidUnitTest` — contract + persistence integration + key rotation + corruption tests.
- Emulator: skill android-emulator → smoke test.

CONSTITUTION GATES:
- Rule 1 (domain isolation): SQLCipher types инкапсулированы в androidMain adapter.
- Rule 2 (ACL): SQLCipher SDK не вытекает в domain.
- Rule 5 (wire format): SQL schema versioned с schema_version field.
- Rule 8 (server migration): N/A — pure client-side.
- Rule 12/13 (server): N/A — no server touch.

EFFORT: ~3-5 дней (12-20 часов).
```

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Все contract tests из TASK-123 зелёные на SQLCipher-backed OpenMlsGroupPort + OpenMlsCryptoPort
- [ ] #2 [hand] Persistence integration test: create group → encrypt → `adb kill` → restart → decrypt → plaintext equals original
- [ ] #3 [hand] Key rotation test: rotate Android Keystore → старая DB inaccessible, migration path работает
- [ ] #4 [hand] DB corruption recovery test: adapter возвращает Failure, не крашит app
- [ ] #5 [hand] Emulator smoke test (skill `android-emulator`, pixel_5_api_34): full user journey зелёный
- [ ] #6 [hand] `docs/architecture/crypto.md` frontmatter обновлён: `encrypted-keystore.decision-status: implemented (YYYY-MM-DD)`
<!-- AC:END -->
