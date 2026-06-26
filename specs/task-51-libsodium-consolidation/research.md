# Research / Decisions: TASK-51 libsodium consolidation

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Архитектурные решения с alternatives + exit ramps per CLAUDE.md rule §3 (one-way doors).

---

## R-001 — Migration strategy: deep refactor vs adapter pattern

**Решено**: deep refactor — старая стопка `com.launcher.api.crypto.*` **удаляется**, 15 wire-format типов переезжают в `cryptokit.pairing.api.*`.

**Alternatives considered**:
- **Adapter pattern (narrow)** — оставить старые ports, создать обёртки `Spec011XxxImpl(newPort) : OldPort`. ~3-4 часа vs ~12-15 часов.

**Why deep**:
- Owner-mandate «делаем от и до, не возвращаться к этой теме»
- Adapter pattern оставляет 2 пакета с одинаковыми именами → mental overhead для future-dev
- Wire-format типы spec 011 архитектурно ≠ crypto primitives — заслуживают своего пакета

**Regret conditions**:
- Если migration regression detected на устройстве с persisted lazysodium-state — нужен backward-compat read.
- Если call-sites показались гораздо более сложными чем оценено (60+ vs 25).

**Exit ramp**:
- Adapter pattern всегда можно retro-fit: добавить `cryptokit.pairing.api.compat.LegacyOutcomeAdapter` если callers требуют.
- Old types можно временно вернуть через `git revert` нужных коммитов (sequence reversible через Phase 4-7).

---

## R-002 — Persisted Keystore keys migration

**Решено**: silent auto-migration через AndroidKeystore TEE access — read legacy alias → re-encrypt under new keyId → delete old. **Никаких user-facing шагов**.

**Alternatives considered**:
- **Force re-pair** (изначальное предложение, отклонено owner'ом 2026-06-26): user видит pairing flow заново.
- **Versioned aliases + lazy migration** (Signal style): два code path сосуществуют N релизов.
- **Atomic upgrade at startup** (Room Migration style): миграция в одной транзакции.
- **Dual-write / dual-read transition** (3-релизный backend-style).
- **Force re-pair (Pattern 4)** ← initial preference перед owner pushback.

**Why silent**:
- Owner-mandate 2026-06-26: «Раз у нас есть [ключ], значит мы доверяем тому что уже произошло, и не требуем никаких действий от пользователя».
- Memory `feedback_no_user_action_for_internal_migrations`.
- Pre-release context (на текущий момент on-device persisted ключей реально нет — PairingActivity всегда крашился), но pattern закладывается future-proof.

**Industry research**:
- Signal Messenger делает версионирование `IdentityKeyPair` через protobuf field versioning, **не** force re-pair.
- Bitwarden mobile делает PBKDF2 → Argon2 migration silent (master password неизменна, derive parameters обновляются).
- AndroidX Security `MasterKeys → MasterKey` API migration — guide official "read with old, write with new on access".

**Regret conditions**:
- AndroidKeystore TEE недоступен (clear-data, factory reset) — миграция невозможна → fall-through на recovery flow F-5b. Это **acceptable**, потому что recovery = другой scenario.

**Exit ramp**:
- TASK-6 (Root Key Hierarchy) — после её реализации silent migration code заменяется на derive-from-root (re-derive ключи deterministically). Inline-TODO в коде: `// TODO(post-task-6): replace read-old-then-re-encrypt with derive-from-root`.

---

## R-003 — Error handling: throws vs Outcome

**Решено**: uniform `throws CryptoException` для всех crypto + pairing API. Universal `try/catch` на верхнем уровне с auto re-throw `CancellationException`.

**Alternatives considered**:
- **`Outcome<T, CryptoError>` (sealed result)** — старый pattern из `com.launcher.api.crypto.*`. Sealed type заставляет UI явно обработать каждый случай.
- **Hybrid** (initial proposal): crypto primitives throws + pairing domain sealed Outcome. Industry-mainstream pattern (Now in Android sample).
- **`kotlin.Result<T>`** — official Kotlin Result type. Officially **not recommended** as public return type (KEEP-127, Elizarov).
- **Arrow Either / Raise DSL** — не mainstream для Android KMP (1.x → 2.x context receivers breaking API; tax на не-Arrow consumers).

**Why uniform throws**:
- Owner-mandate 2026-06-26: «Давай везде писать кидать, и можем тогда универсально отлавливать ошибки. Что значит пострадает качество если мы завернем, отловим ошибку?»
- Один логгер, один UI error handler, меньше кода в адаптерах.
- Совместимо с coroutine structured concurrency (exceptions propagate естественно).

**Critical detail**: универсальный `try/catch` обязан re-throw `CancellationException` — иначе coroutine cancellation сломается (silent bug, проявляется как UI hang при close экрана).

**Industry research**:
- Tink (Google's crypto): throws `GeneralSecurityException`. Fail-closed design.
- JCA / Android Keystore / Cipher: throws (`GeneralSecurityException`, `UserNotAuthenticatedException`).
- ionspin libsodium-kmp: throws (родной паттерн).
- Now in Android sample: sealed Result для domain layer (но это **больше про state management UI**, не error propagation).

**Regret conditions**:
- Если в pairing-side flow окажется ≥ 5 различных user-actionable failure cases с разным UI flow → exhaustive sealed Outcome был бы лучше для compiler-enforced handling.

**Exit ramp**:
- Любой `throws CryptoException` API можно обернуть в `Result<T, CryptoException>` extension function в layer выше — additive change.

---

## R-004 — Fingerprint hash для debug-activity

**Решено**: inline `java.security.MessageDigest.getInstance("SHA-256")` в `Spec011SmokeDebugActivity`. **Не вводить** `HashFunction` port в `cryptokit.crypto.api`.

**Alternatives considered**:
- **Add `HashFunction` port** — формальный API для hash; future-proof.
- **Use BLAKE2b through ionspin** (если есть в API) — consistent with libsodium primitives.
- **Remove fingerprint feature** — debug-only, не критично.

**Why inline MessageDigest**:
- CLAUDE.md rule §4 (MVA) — port ради одной debug-фичи = premature abstraction.
- `MessageDigest` — JDK stdlib, не vendor SDK (rule §1 не нарушается).
- SHA-256 — industry-default (SSH, Bitcoin, PGP V5, Signal Safety Numbers через iterated SHA-512, Telegram через SHA-256).
- NIST SP 800-107: для fingerprint (collision resistance) SHA-256 → 128-bit collision strength, более чем достаточно.

**Industry research**:
- Никто не использует BLAKE2/BLAKE3 для fingerprint'ов. BLAKE3 преимущества только при hash gigabytes (SIMD parallelism).
- Truncation safety: 32-bit → broken (Evil32 attack), 64-bit → broken для well-funded adversary, 128-bit → comfortable. Debug visual sanity check: 64-bit (16 hex chars) OK.

**Regret conditions**:
- Если в будущем понадобится hash в domain code (TASK-9 contact tile fingerprints? TASK-42 MLS safety numbers?) — вводи port с конкретным use-case.

**Exit ramp**:
- Add `cryptokit.crypto.api.HashFunction` interface в любой момент — additive, replace inline call с port resolve через Koin.

---

## R-005 — DI module structure

**Решено**: один объединённый Koin module (`cryptokitModule`) — слияние `F016CryptoModule` + `CryptoModule.kt` (legacy).

**Alternatives considered**:
- **Два module** (CryptoModule.kt + F016CryptoModule.kt) — текущее состояние, dual maintenance.
- **Раздельные сabineты по слоям** (cryptoModule + pairingModule + keystoreModule).

**Why one module**:
- Owner-mandate 2026-06-26: «Конечно один! не надо плодить».
- После Q1 deep migration — старая стопка `com.launcher.api.crypto.*` удалена → нет смысла в двух modules.
- `PairingModule.kt` остаётся **отдельным** — он DI для pairing-flow (PairingService, PairingViewModel), а не crypto-API.

**Regret conditions**:
- Если cryptokit вырастет до ≥ 30 bindings — стоит разделить на `cryptokitCoreModule` + `cryptokitPairingModule`. Pre-emptive split сейчас — premature abstraction.

**Exit ramp**:
- Koin позволяет split module на несколько и `loadModules(...)` в `startKoin` — additive change. Tax = ноль.

---

## R-006 — Namespace choice: `cryptokit.*`

**Решено**: переименовать `family.* → cryptokit.*` в первом implementation commit'е.

**Alternatives considered**:
- **`family.*`** — текущее, historical artifact из spec 016. Owner смущает (метафора «семейная криптография» — но это просто метафора проекта для пожилых).
- **`launcher.crypto.*`** — по имени проекта. Не подходит — будущая выноска для переиспользования (messenger, photo, medical).
- **`com.launcher.crypto.*`** — full reverse-domain. Слишком длинно.
- **`securecomms.*` / `aegis.*` / `kin.*`** — alternatives озвучены в обсуждении.
- **`cryptokit.*`** ← owner-decision 2026-06-26.

**Why cryptokit**:
- Owner-decision: понятно, отражает «обёртка над crypto-примитивами, переиспользуемая для экосистемы».
- Apple's Swift `CryptoKit` существует (`import CryptoKit` в Swift) — но это **Swift-side**, наш Kotlin namespace `cryptokit` (lowercase) — никакой коллизии.
- В Kotlin/Java ecosystem `cryptokit` свободен (нет конкурирующих библиотек с таким top-level package).

**Regret conditions**:
- Если в будущем появится Maven artifact с тем же top-level — namespace conflict.

**Exit ramp**:
- Переименовать через find-replace + git mv — operation reversible (один коммит, можно revert).
- При выносе в отдельный репо (parking-lot task) — namespace остаётся, только git remote меняется.

---

## R-007 — `AndroidKeystoreSecureKeystore` — rewrite vs delete

**Решено**: удалить целиком. Использовать `cryptokit.crypto.api.SecureKeyStore` (expect/actual из spec 016).

**Alternatives considered**:
- **Rewrite** `AndroidKeystoreSecureKeystore` под новую crypto-стопку (заменить lazysodium calls на ionspin).
- **Keep as facade** (adapter pattern): `AndroidKeystoreSecureKeystore` остаётся как `SecureKeystore` impl, под капотом делегирует к `cryptokit.crypto.api.SecureKeyStore`.

**Why delete**:
- Owner-mandate 2026-06-26: «Если уже есть аналог, то зачем этот класс нужен? Просто удаляем и всё». Также: «большой класс из которого методы дёргаются — сигнал разобрать».
- Memory `feedback_large_classes_signal_split.md`.
- `cryptokit.crypto.api.SecureKeyStore` (expect/actual) уже реализует generic wrap-pattern для произвольных ByteArray (AES master в Android Keystore TEE, EncryptedSharedPreferences для wrapped bytes).
- iOS readiness (TASK-26) — actual implementation в `iosMain` подключается без рефакторинга consumer'ов.

**Industry research**:
- Signal Android: не держит identity X25519 в Android Keystore напрямую. Используют SQLCipher DB + master key через Android Keystore (тот же wrap-pattern, но обёрнут не один ключ а DB).
- Google Tink: `AndroidKeystoreAead` / `AndroidKeystoreKmsClient` — точно тот же wrap-pattern.
- ionspin libsodium-kmp **НЕ предоставляет** `expect class SecureKeyStore` — это наш собственный adapter из spec 016.
- AES-GCM vs AES-KW (RFC 3394): AES-GCM с random 96-bit nonce достаточен. AES-KW в AndroidKeystore не экспонируется как mode → unwrap пришлось бы делать в software → теряется TEE преимущество.

**Hardening adjacent concerns** (НЕ в TASK-51 scope, но verify в plan-time):
- AAD в AES-GCM wrap (binding к keyId / schemaVersion).
- Random 96-bit nonce per encrypt.
- `android:allowBackup="false"` или backup_rules exclusion (иначе wrapped blob в Google Drive backup → post-restore breakage).
- Zeroize ByteArray после use (`key.fill(0)`).

**Regret conditions**:
- Если `cryptokit.crypto.api.SecureKeyStore` actual в `androidMain` не работает с произвольными ByteArray (ограничен например только AES) — пришлось бы расширять API. Plan-time verify.

**Exit ramp**:
- Если нужны pairing-specific operations над keystore (не general `store/load` API) — добавить `cryptokit.pairing.api.PairingKeyStore` обёртку. Additive change.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** 7 архитектурных решений (R-001..R-007) для TASK-51, фиксирующих owner-decisions из Q1..Q7 clarify pass + namespace decision. Все решения имеют alternatives considered, why-rationale, regret conditions и exit ramps per CLAUDE.md rule §3 (one-way doors).

**Конкретика, которую стоит запомнить:**
- **R-001 deep migration**: 15 типов spec 011 переезжают в `cryptokit.pairing.api.*`, старая стопка удалена. Exit ramp — git revert reversible.
- **R-002 silent migration**: НИКАКИХ user-facing pairing-шагов при upgrade. read-old → re-encrypt → delete. TODO к TASK-6 (Root Key Hierarchy) для derive-from-root.
- **R-003 throws CryptoException**: uniform для всех API. Universal try/catch с auto re-throw CancellationException (без этого coroutine cancellation сломается).
- **R-004 inline MessageDigest.SHA-256**: для debug fingerprint'а, без HashFunction port. SHA-256 = industry default (SSH/Bitcoin/Signal/Tink/PGP).
- **R-005 один Koin module**: `cryptokitModule`. PairingModule (DI для pairing flow) остаётся отдельным.
- **R-006 namespace `cryptokit.*`**: owner-decision, не конфликтует с Apple Swift CryptoKit (разные платформы, lowercase vs PascalCase).
- **R-007 удалить AndroidKeystoreSecureKeystore**: 300+ строк класса, заменяется `cryptokit.crypto.api.SecureKeyStore` (expect/actual). iOS-ready out-of-box.

**На что смотреть с осторожностью:**
- **R-002 silent migration verification gap**: на Xiaomi 11T нет persisted lazysodium-state (всегда крашился), поэтому migration logic **не tested end-to-end** в TASK-51. Future deployment risk если в production окажется устройство с persisted state.
- **R-007 hardening adjacent concerns**: AAD, nonce strategy, allowBackup=false, zeroize — verify в plan-time в `cryptokit.crypto.api.SecureKeyStore` androidMain actual. Если не реализовано — отдельный bug, не в TASK-51 scope.
- **R-003 CancellationException re-throw**: classic Result-based codebases часто пропускают; Konsist rule обязателен для enforcement.
