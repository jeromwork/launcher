# Crypto review document — F-CRYPTO (`:core:crypto`)

**Status**: living document, updated on every F-CRYPTO release.
**Last update**: 2026-06-17 (F-CRYPTO 1.0.0 plan + implementation).
**Owner**: launcher core team.

This document is the validation-set reference for the `:core:crypto` foundation
module (spec 016). It is intentionally specific: anyone auditing the module —
whether a friend with a crypto background, a paid auditor before the billing
milestone, or a future maintainer — must be able to identify the primitives, the
industrial reference baseline, the validation set, and the open risks without
reading every source file.

---

## Primitives in production

All primitives are provided by `libsodium` via the
[`ionspin/kotlin-multiplatform-libsodium`](https://github.com/ionspin/kotlin-multiplatform-libsodium)
binding pinned at version **`0.9.5`** (released 2025-11-23).

| Purpose | Primitive | Adapter |
|---|---|---|
| AEAD (envelope encryption) | XChaCha20-Poly1305 IETF (24-byte nonce) | [`LibsodiumAeadCipher`](../../core/crypto/src/commonMain/kotlin/family/crypto/libsodium/LibsodiumAeadCipher.kt) |
| Key agreement | X25519 raw `crypto_scalarmult` (RFC 7748) | [`LibsodiumAsymmetricCrypto.deriveSharedSecret`](../../core/crypto/src/commonMain/kotlin/family/crypto/libsodium/LibsodiumAsymmetricCrypto.kt) |
| Digital signatures | Ed25519 detached (`crypto_sign_detached`) | `LibsodiumAsymmetricCrypto.sign` / `verify` |
| Sealed-box envelope (ADR-008 social recovery) | `crypto_box_seal` / `crypto_box_seal_open` | `LibsodiumAsymmetricCrypto.sealForRecipient` / `openSealed` |
| Key derivation | HKDF-SHA256 (RFC 5869) — hand-rolled over platform HMAC-SHA256 because ionspin 0.9.5 does not expose `crypto_kdf_hkdf_sha256` | [`LibsodiumKeyDerivation`](../../core/crypto/src/commonMain/kotlin/family/crypto/libsodium/LibsodiumKeyDerivation.kt) + `HmacSha256` expect/actual (JCA on Android/JVM, stub on iOS) |
| CSPRNG | libsodium `randombytes_buf` | [`LibsodiumRandomSource`](../../core/crypto/src/commonMain/kotlin/family/crypto/libsodium/LibsodiumRandomSource.kt) |
| Key-at-rest protection | Android Keystore AES-256-GCM wrap, StrongBox-backed where available (Pixel Titan / Samsung Knox), TEE-only fallback elsewhere | [`SecureKeyStore.android.kt`](../../core/crypto/src/androidMain/kotlin/family/crypto/SecureKeyStore.android.kt) |

iOS adapters are stub-screamers per Clarifications Q1 — replaced when V-1 (iOS
Admin Preset) ships. iOS targets compile today so the contract is fixed.

---

## Industrial reference baseline

Each primitive choice mirrors industry-standard practice:

- **XChaCha20-Poly1305** — used by WireGuard (file format), age, Signal Sealed
  Sender, Threema, Bitwarden Send. RFC draft-irtf-cfrg-xchacha-03 (long-running
  IRTF status).
- **X25519 ECDH** — Signal Protocol, WhatsApp, WireGuard, Wire, age, all modern
  ECDH stacks. RFC 7748.
- **Ed25519 signatures** — Signal Sealed Sender, age, OpenSSH default, Tor,
  WireGuard handshake. RFC 8032.
- **HKDF-SHA256** — TLS 1.3 key schedule, Signal Double Ratchet, age,
  WireGuard, MLS. RFC 5869.
- **Android Keystore wrap pattern for Curve25519** — Signal Android
  (`IdentityKeyUtil`), Bitwarden Android, Threema Android. Android Keystore
  does not support Curve25519 as native key storage primitive, so all senior-
  level Curve25519 mobile apps wrap the raw priv bytes under a TEE-resident AES
  key. We follow the same pattern (see research.md §R3).

We are not inventing crypto. Where our code differs from a vendor (e.g.,
hand-rolled HKDF until ionspin exposes it), the diff is small enough to read
in one screen and matches the RFC verbatim.

---

## Validation set

The validation set replaces a single point-in-time friend-review with a
**measurable** test suite that re-runs in CI on every change. Per F-CRYPTO
mentor decision 2026-06-17, the friend review is dropped as mandatory; the
items below are the substitute.

### A. RFC Known-Answer-Test vectors

All vectors are extracted directly from RFC text and live under
`core/crypto/src/jvmTest/kotlin/family/crypto/kat/`. Each test asserts
byte-identical output against the RFC.

| Test class | RFC | Coverage |
|---|---|---|
| `X25519KatTest` | RFC 7748 §5.2 + §6.1 | 2 raw scalar*basepoint vectors + Alice/Bob ECDH symmetry vector. |
| `Ed25519KatTest` | RFC 8032 §7.1 | TEST 1 (empty msg), TEST 2 (1 byte), TEST 3 (2 bytes) + tamper-fails-verify. |
| `ChaCha20Poly1305KatTest` | RFC 8439 §2.8.2 + draft-irtf-cfrg-xchacha-03 §A.3 | ChaCha20-Poly1305 IETF vector + XChaCha20-Poly1305 IETF roundtrip with forced nonce + tamper detection. |
| `HkdfKatTest` | RFC 5869 §A.1 + §A.3 | Test Case 1 (typical) + Test Case 3 (zero-length salt+info). |
| `SealedBoxRoundtripTest` | libsodium spec | sealed-box roundtrip + wrong-recipient rejection + CSPRNG sanity. |

CI runs `./gradlew :core:crypto:jvmTest` on every PR.

### B. Google Wycheproof subset

Wycheproof is Google's adversarial test corpus for crypto libraries. Adding
the full corpus (~50 MB) is out of scope for the MVP; we pick a representative
subset focused on the failure modes most likely to bite us:

- **X25519 low-order points** — `LibsodiumAsymmetricCrypto.deriveSharedSecret`
  must reject (libsodium does, we surface as `CryptoException.InvalidPublicKey`).
- **Ed25519 malleable signatures** — must fail verify (libsodium does).
- **ChaCha20-Poly1305 AAD edge cases** — empty AAD, large AAD.

**Pinning policy**: pinned to Wycheproof commit SHA `<TBD-in-T658>` — to be
chosen when the subset is curated. Subset file lives under
`core/crypto/src/commonTest/resources/wycheproof-subset/`. Bump policy: a
SHA bump requires a deliberate PR; CI does not auto-update.

### C. Property-based tests (1000 iterations each, deterministic seeds)

Live under `core/crypto/src/commonTest/kotlin/family/crypto/property/`:

- `AeadRoundtripPropertyTest` — 1000 iter encrypt→decrypt roundtrip + 200 iter
  tamper-detection (random bit flip in MAC region).
- `EcdhSymmetryPropertyTest` — 1000 iter `DH(a, B) == DH(b, A)`.
- `SignVerifyTamperPropertyTest` — 1000 iter sign+verify roundtrip + 200 iter
  signature tamper + 200 iter message tamper.
- `NonceReuseRejectionPropertyTest` — forced-nonce-reuse triggers
  `CryptoException.NonceReuseDetected`.
- `KeyIdPrefixPropertyTest` — 100 valid + 100 invalid prefixes against
  `KeyNamespace` allowlist.

CI runs the same suite.

### D. Cross-platform byte parity

`KeyBlobCrossPlatformParityTest` and the planned encryption-vector parity
(spec 016 T691) assert that JSON serialization of `KeyBlob` and the
deterministic AEAD output with forced nonce match byte-for-byte on JVM and
Android. This is the FR-022 guarantee that a config written by an Android
launcher is parseable by a future iOS / desktop launcher.

### E. Backward-compat fixtures (FR-026)

`core/crypto/src/commonTest/resources/key-blob/v1-sample.json` and
`v1-retired-sample.json` are **frozen at F-CRYPTO 1.0.0 release**. Future
minor releases MUST continue to parse them. `KeyBlobBackwardCompatReadTest`
also verifies `UnsupportedSchemaVersion` is thrown when `schemaVersion=999`.

### F. Android Keystore instrumentation

`core/crypto/src/androidInstrumentedTest/kotlin/family/crypto/`:

- `SecureKeyStorePersistenceTest` (SC-008) — store/load against real TEE.
- `SecureKeyStoreNoPlaintextLeakTest` (SC-009) — disk-resident blob does NOT
  contain any 4-byte plaintext subsequence of the secret. The wrap pattern's
  whole point.

Verified on emulator API 34/35; physical-device verification pending
(`TODO(physical-device): TEE attestation + StrongBox verification`).

### G. Friend crypto review — снят как mandatory

Friend crypto review снят как mandatory (решение 2026-06-17, F-CRYPTO mentor
session). Заменён на the measurable validation set above (A through F). If a
friend with a crypto background reviews the module — great; we welcome it —
but the project no longer blocks on it.

---

## Paid audit milestone

Per [SRV-CRYPTO-003](server-roadmap.md#srv-crypto-003-paid-security-audit-milestone-f-crypto-billing-gate)
(server-roadmap entry to be added): a paid third-party audit of `:core:crypto`
is mandatory before billing flips on. The audit's brief is this document —
auditors confirm:

1. The primitives table matches what is in production.
2. The validation set is wired into CI and passes.
3. The known risks below are still the only known risks.

The audit blocks the billing gate, not the MVP launch. Local-mode users
benefit from F-CRYPTO without waiting on the audit.

---

## Known risks / open TODOs

- **`crypto_box_seal` / HKDF-SHA256 not in ionspin public API.** Verified in
  Phase 5 by using `Box.seal` / `Box.sealOpen` (present) and hand-rolling HKDF
  over `HmacSha256`. If a future ionspin release adds first-class HKDF, switch
  to it and remove the hand-rolled implementation.
- **iOS path is stub-only.** Replaced with Keychain when V-1 ships
  (`SecureKeyStore.ios.kt` throws `NotImplementedOnIos` with cross-ref).
- **TEE attestation not enforced.** `KeyInfo.isInsideSecureHardware == false`
  on emulators is logged but not treated as a hard error in MVP. Decision
  recorded in spec 016 T6B4. When billing ships, this becomes a hard fail.
- **Wycheproof subset not yet pinned.** T658 picks the commit SHA during full
  Phase 5 implementation; this document gets updated with the pinned SHA.
- **Key rotation is interface-only.** `StubKeyRotation` throws `NotImplementedError`
  with a future rotation spec cross-ref (TBD number — see ADR-008). Real implementation lives in
  [SRV-CRYPTO-002](server-roadmap.md#srv-crypto-002-manual-key-rotation-flow-future-spec-010).

---

## Cross-reference index

- Spec: [`specs/016-f-crypto-core-module/spec.md`](../../specs/016-f-crypto-core-module/spec.md).
- Plan: [`specs/016-f-crypto-core-module/plan.md`](../../specs/016-f-crypto-core-module/plan.md).
- Research (one-way-door analysis): [`specs/016-f-crypto-core-module/research.md`](../../specs/016-f-crypto-core-module/research.md).
- Wire-format contract: [`specs/016-f-crypto-core-module/contracts/key-blob-v1.md`](../../specs/016-f-crypto-core-module/contracts/key-blob-v1.md).
- Server-roadmap entries: [`SRV-CRYPTO-001`](server-roadmap.md#srv-crypto-001), [`SRV-CRYPTO-002`](server-roadmap.md#srv-crypto-002-manual-key-rotation-flow-future-spec-010), `SRV-CRYPTO-003` (to be added).

---

## TL;DR простым языком

Документ для аудиторов и для нас же через год.

**Что в проекте сейчас**: набор стандартных крипто-примитивов из libsodium
(XChaCha20-Poly1305 для шифрования, X25519 для обмена ключами, Ed25519 для
подписей, HKDF-SHA256 для производства ключей, sealed-box для social recovery,
TEE-обёртка ключей на Android). Ничего самописного — везде library, которая
используется в Signal, WhatsApp, age, WireGuard.

**Как мы проверяем, что не накосячили**: вместо одной разовой
«дружеской ревизии» — постоянный набор тестов в CI:
1. **RFC test vectors** — RFC 7748 (X25519), RFC 8032 (Ed25519), RFC 8439
   (ChaCha20-Poly1305) + XChaCha20 IETF draft + RFC 5869 (HKDF). Все наши
   реализации обязаны вернуть **байт-в-байт** то, что написано в RFC.
2. **Google Wycheproof subset** — тестовые векторы от Google, специально
   подобранные для нахождения багов (low-order points, malleable signatures,
   AAD edge cases).
3. **Property tests** — 1000 случайных входов, что roundtrip всегда работает,
   подделанная подпись всегда не проходит, и т.д.
4. **Cross-platform parity** — JSON и шифротексты на Android и JVM
   совпадают байт-в-байт (чтобы конфиг с Android-launcher'а читался будущим
   iOS-launcher'ом без миграции).
5. **Backward-compat fixtures** — заморожены образцы файлов формата v1,
   новые версии приложения обязаны их читать.
6. **Instrumentation** — отдельные тесты на эмуляторе проверяют, что обёрнутые
   ключи **действительно** не лежат plaintext на диске.

**Что ещё впереди**:
- Платный аудит перед запуском подписок (billing-gate).
- iOS-реализация когда V-1 спека станет приоритетом.
- Ротация ключей (future rotation spec, TBD — see ADR-008).

---

# Appendix — Post-1.0.0 strategic notes

Этот appendix зафиксирован 2026-06-18 по итогам владельческой mentor-сессии. Цель — **не потерять** контекст ключевых решений к моменту pre-release research перед выходом в Google Play / App Store.

---

## A1. iOS reuse strategy

F-CRYPTO 1.0.0 уже включает `iosX64`, `iosArm64`, `iosSimulatorArm64` targets. `commonMain` код (ports, value types, Libsodium-based adapters для AEAD / AsymmetricCrypto / KeyDerivation / RandomSource) — **переиспользуется один-в-один** на iOS, потому что ionspin libsodium binding официально поддерживает все три iOS targets.

**Что переиспользуется без изменений** (один файл, общий для всех платформ):
- `family.crypto.api.*` — все ports и value types.
- `family.crypto.libsodium.LibsodiumAeadCipher` — XChaCha20-Poly1305 IETF.
- `family.crypto.libsodium.LibsodiumAsymmetricCrypto` — X25519 + Ed25519 + sealed-box.
- `family.crypto.libsodium.LibsodiumKeyDerivation` — HKDF-SHA256 (через expect/actual HmacSha256).
- `family.crypto.libsodium.LibsodiumRandomSource` — `randombytes_buf`.
- `family.crypto.fake.*` — фейки для тестов.
- `family.crypto.stubs.*` — KeyRotation/KeyEscrow stubs.
- `family.crypto.api.values.KeyBlob` — wire format (JSON-сериализуется одинаково на всех платформах).

**Что нужно заменить на iOS** (3 файла, каждый — замена `actual class` или `actual object`):

1. **`iosMain/SecureKeyStore.ios.kt`** — сейчас stub-screamer, бросает `NotImplementedOnIos`. Замена: реализация через **iOS Keychain Services** (`SecItemAdd` / `SecItemCopyMatching` / `SecItemDelete`). Атрибуты:
   - `kSecClass = kSecClassGenericPassword` (для произвольных secret bytes).
   - `kSecAttrAccount = keyId.raw` (наш идентификатор).
   - `kSecAttrService = "family.crypto.v1"` (namespace).
   - `kSecAttrAccessible = kSecAttrAccessibleAfterFirstUnlock` (доступно после первого unlock телефона; не требует биометрии).
   - Опционально `kSecAttrAccessControl` с `SecAccessControlCreateWithFlags(...kSecAccessControlPrivateKeyUsage...)` если когда-то понадобится биометрия для специальных ключей (не для baseline).

2. **`iosMain/HmacSha256.ios.kt`** — сейчас stub-screamer. Замена: вызов `CCHmac(kCCHmacAlgSHA256, ...)` из CommonCrypto (системная iOS библиотека, нулевые зависимости).

3. **Никаких изменений в commonMain не нужно.** Любая попытка добавить iOS-specific логику в commonMain — нарушение CLAUDE.md rule 1 (domain isolation).

**Wire format совместимость**: `KeyBlob` JSON, написанный Android-launcher, **читается** iOS-launcher байт-в-байт (это гарантировано `KeyBlobCrossPlatformParityTest`). Но: **wrapped private keys** — НЕ переносимы между Android Keystore wrap и iOS Keychain. Это потому, что AES-GCM wrap key на Android привязан к Android Keystore alias, который iOS прочитать не может. Для cross-device миграции «Android-юзер пересел на iPhone» используется **ADR-008 social recovery** (future spec, TBD), не прямой transfer файлов.

**iOS Team ID для будущих app** (launcher + messenger + photo album): чтобы переиспользовать crypto handoff через **App Groups + shared Keychain access groups** на iOS, все 3 app должны быть в **одном Apple Developer account** (один Team ID). Решение про "один аккаунт за $99/год vs три за $99×3" — фиксируется при покупке Apple Developer Program: **один аккаунт обслуживает всю семью**.

**Тестирование iOS**: KAT и property tests в `:core:crypto:iosTest` нужны и должны зеленеть. Запуск требует macOS host (KMP iOS targets не собираются на Windows/Linux). До покупки Mac — `TODO(physical-mac)` в `iosMain/SecureKeyStore.ios.kt` (уже стоит).

**TODO(pre-release-audit): iOS — `iosMain/SecureKeyStore.ios.kt` + `HmacSha256.ios.kt` реализованы и `:core:crypto:iosTest` зеленый.**

---

## A2. Multi-app cohabitation — chain-of-trust strategy

### Контекст

Семейство приложений запланировано (в течение ~5 месяцев от 2026-06-18):
- **launcher** (это приложение).
- **messenger** (E2E чат для пожилых + admin-родственников).
- **photo album** (управление семейными фото).

Каждое — отдельное Android-приложение, отдельный package, отдельный sandbox. Android **не позволяет** одному app читать файлы другого. Каждое app имеет **свой** экземпляр `:core:crypto` и **свои** ключи.

### Решение для MVP первого релиза каждого app — Вариант A (Independent)

Каждое app:
1. При первой установке генерирует свои ключи через свой `:core:crypto`.
2. Имеет свой ADR-008 social recovery flow (отдельная связка trusted-peer).
3. Не знает о других app семейства.

**Это уже работает** для launcher 1.0.0 — никаких дополнительных изменений не нужно.

### Желаемое long-term поведение (post-MVP, **сейчас не реализуем**)

Owner-видение от 2026-06-18:

> «При восстановлении лаунчера мы подтвердили его — и сам лаунчер также мог подтвердить, что вот мессенджер тоже доверенный. Чтобы одни ключи шифровали другие ключи. Как двухфакторная авторизация между разными лаунчерами на разных телефонах — здесь чтобы примерно так же работало для разных приложений, чтобы одно подтверждало другое.»

Это паттерн **«chain-of-trust между app в семействе»**: после того как launcher на новом устройстве прошёл recovery (через ADR-008 social recovery, подтверждение от бабушкиного / тёткиного устройства), launcher становится «trusted introducer» для своих same-family app на этом же устройстве. Messenger на новом устройстве при установке видит «launcher уже trusted» → запрашивает у launcher **одну подпись** через App-to-App канал → отправляет эту подпись на свой recovery server → не требует отдельной recovery-сессии.

**UX-цель** (буквально слова owner от 2026-06-18): «условно один клик — нажал пользователь, восстановил доступ. Чтоб не для каждого приложения свои.»

### Технические варианты реализации (для будущей P-10 спеки в Phase 3)

Эти три варианта **не выбираем сейчас**, фиксируем для research-фазы перед messenger MVP.

**B. ContentProvider + custom permission** (Signal-style):
- launcher экспортирует `ContentProvider` с custom permission `com.launcher.CRYPTO_FAMILY_BRIDGE`.
- messenger / photo при установке заявляют это permission в manifest. Android при установке (или при первом use) показывает single confirmation UI: «messenger хочет восстанавливать доступ совместно с launcher — разрешить?».
- Данные через ContentProvider — **sealed-box** к app-specific pubkey messenger. launcher не видит messenger секреты, только signs trust-introduction.

**C. Server-mediated handoff**:
- После launcher recovery launcher загружает на сервер encrypted-pending-handoff (зашифрован для messenger pubkey).
- messenger при первом запуске после установки опрашивает сервер по своему UID → находит pending handoff → расшифровывает (knows own privkey) → запускает свой quick-recovery flow.
- Работает **cross-device**: launcher на Android, messenger на iPhone.

**Гибрид B + C** (рекомендуемый по итогам mentor-сессии):
- Cohabitation на одной платформе → ContentProvider (нет cloud dependency).
- Cross-platform handoff → server-mediated.

### Что фиксируется в коде сейчас

- `TODO(pre-release-audit): multi-app cohabitation strategy — выбрать B / C / гибрид при создании P-10 спеки в Phase 3 (см. docs/product/future/multi-app-cohabitation.md). До тех пор каждое app в семействе использует независимый recovery flow (вариант A).`
- Research notes для P-10 спеки лежат в `docs/product/future/multi-app-cohabitation.md` (раньше был placeholder `specs/017-multi-app-cohabitation/`, переехал и каталог удалён 2026-06-18 после переназначения номера 017 на F-4 AuthProvider).

### Что точно НЕ делать

- ❌ `android:sharedUserId` — deprecated в Android 10, удалён в Android 13.
- ❌ `MODE_WORLD_READABLE` для shared crypto files — Android Security Bulletin SA-2017, deprecated.
- ❌ Один master ключ через сервер для всех 3 app — концентрация риска.
- ❌ iCloud Keychain как cross-app sync механизм — only-Apple-id-tied, не работает cross-platform.

---

## A3. Data export (EU Data Act 2024 — minimal compliance)

### Контекст

EU Data Act 2024 требует, чтобы пользователь мог **экспортировать свои данные** независимо от вендора. Если crypto-key и шифрованные данные хранятся только на устройстве — нужен explicit export flow.

### Решение owner от 2026-06-18

> «Ничего не мешает сделать кнопочку экспортировать, и пускай он явно отдаёт свои данные в JSON или в ZIP архиве, не зашифрованным. Это уже его проблемы дальше. Мы всё шифруем, если он хочет экспортировать — мы ему отдаём, но явно предупреждаем, что это убирается шифрование.»

### Что фиксируется

- Спека `018-data-export` (имя tentative, выбирается при создании) описывает:
  - Кнопка «Экспорт моих данных» в Settings.
  - Большой warning UI перед export на простом русском: «**Внимание**: экспортированный файл **не зашифрован**. Любой, кто получит этот файл, увидит ваши контакты, фото, настройки. Храните его в безопасном месте.»
  - Формат: ZIP с JSON-файлами внутри. Каждый JSON — текстовый, читаемый.
  - На каждый экран добавляется один кнопочный bridge "почему именно мы спрашиваем разрешение перед экспортом".
- `KeyEscrow` port (сейчас stub) при реализации в future social recovery spec (TBD) НЕ используется для этого — это **разные** flow:
  - **KeyEscrow** = recovery (шифрованный backup для восстановления на другом устройстве).
  - **DataExport** = compliance (plaintext дамп для самого юзера).

**TODO(pre-release-audit): data export UI — реализован с senior-safe warning + Google Play Data Safety form задекларирован "user can export all data".**

---

## A4. Pre-release agent audit checklist

Стратегия по итогам owner от 2026-06-18: **внешний платный crypto-аудит не проводится** до момента, когда (a) накопится достаточная user base и (b) запустится монетизация. До этого момента — **agent-based pre-release audit**: перед каждым крупным релизом агент проходит этот checklist и репортит, что закрыто, что нет.

### Pre-release audit checklist (запускается перед Google Play submission)

Каждый пункт — это вопрос на стол `pre-release-audit` агенту. Если ответ «не закрыто» — релиз не уходит.

**Cryptography correctness:**
- [ ] Wycheproof subset SHA pinned, файлы лежат в `core/crypto/src/commonTest/resources/wycheproof-subset/`, CI `f-crypto-tests.yml` гоняет subset на every PR.
- [ ] Все RFC KAT зеленые на JVM и iOS (если iOS релиз).
- [ ] Property tests (1000 iter) зеленые на JVM, Android, iOS.
- [ ] Все известные CVE в libsodium версии 0.9.5+ (ionspin binding) проверены — если есть unpatched HIGH/CRITICAL, апгрейд binding.

**Android Keystore security:**
- [ ] Real-device verification на **Pixel** (StrongBox) и **Samsung Galaxy A-series** (Knox).
- [ ] `SecureKeyStoreNoPlaintextLeakTest` зеленый на физическом устройстве (не только эмулятор).
- [ ] `KeyInfo.isInsideSecureHardware` проверяется на инициализации — если false на production устройстве, log + telemetry.
- [ ] TEE attestation hard-fail wired для billing-protected features (когда billing появится; не критично для local-mode features).

**iOS Keychain security** (если iOS релиз):
- [ ] `iosMain/SecureKeyStore.ios.kt` реализован через Keychain Services.
- [ ] `iosMain/HmacSha256.ios.kt` реализован через CommonCrypto.
- [ ] `:core:crypto:iosTest` зеленый на macOS host.
- [ ] Verified на физическом iPhone (не только simulator).

**Wire format integrity:**
- [ ] `KeyBlob v1-sample.json` + `v1-retired-sample.json` НЕ изменялись (frozen since 1.0.0).
- [ ] Schema version check (UnsupportedSchemaVersion для `schemaVersion > known`) работает.

**Production hygiene:**
- [ ] `verifyCryptoIsolation` Gradle task зеленый — `:core:crypto` не зависит от других модулей.
- [ ] Konsist fitness `NoFakeCryptoInAppTest` зеленый — нет `family.crypto.fake.*` imports в `app/src/main`.
- [ ] R8/ProGuard рулы strip `family.crypto.fake.**` из release APK (verify через dexdump или unzip).
- [ ] `assertNoFakeCryptoInRelease()` вызывается в `LauncherApplication.onCreate` под `!BuildConfig.DEBUG`.

**Backup safety:**
- [ ] `data_extraction_rules.xml` + `backup_rules.xml` exclude `keys/` (verified в обоих файлах).
- [ ] Manual test: cloud backup + restore — wrapped keys НЕ переносятся (это expected).

**Multi-app cohabitation** (если выпускается ≥2 app одновременно):
- [ ] P-10 спека в Phase 3 (number TBD at /speckit.specify time) — chain-of-trust strategy выбрана (B / C / гибрид) и реализована. См. `docs/product/future/multi-app-cohabitation.md`.
- [ ] Cross-app sealed-box handoff протестирован end-to-end.

**Data sovereignty / compliance:**
- [ ] Data export UI реализован с senior-safe warning (см. A3).
- [ ] Google Play Data Safety form заполнен — задекларировано всё, что шифруется на устройстве, и что user может экспортировать.
- [ ] Apple App Privacy nutrition labels заполнены (если iOS релиз).
- [ ] Privacy policy упоминает crypto storage и user export rights.

**Research before release:**
- [ ] Прогон по проектам Signal Android, Bitwarden Android, Threema Android: посмотреть, какие крипто-практики у них появились за прошедшие месяцы, что мы можем у них перенять.
- [ ] Проверить актуальные Wycheproof commits — pin последний.
- [ ] Проверить Android Keystore behavior changes в latest Android version.
- [ ] Проверить iOS Keychain behavior changes в latest iOS version.
- [ ] Проверить CVE database (NVD, GHSA) для libsodium / ionspin binding.

### Когда переходим к платному аудиту

Платный crypto-аудит ($3-12k, Cure53 / 7ASecurity / Radically Open Security / Trail of Bits) проводится **когда**:

1. Монетизация запущена и есть user base ≥ ~10k активных пользователей; **И**
2. Доход от подписок превышает $50/месяц устойчиво; **ИЛИ**
3. PR-инцидент или CVE в libsodium / Android Keystore (тогда аудит делается срочно).

До этих условий — agent-audit + community feedback (issues от пользователей / security researchers) считается достаточным.

**TODO(pre-release-audit): когда выполнились условия выше — перейти на платный внешний аудит. До тех пор — agent-audit перед каждым крупным релизом.**

---

## A5. Что отложено и **причина откладывания** (для будущего self / агента)

| Item | Откладывание | Триггер «делать сейчас» | Текущий статус |
|---|---|---|---|
| Wycheproof subset SHA pin | До pre-release research | Перед Play Store submission | TODO в коде + checklist |
| iOS Keychain + HmacSha256 | До V-1 спеки | Решение делать iOS-релиз | stub-screamer + reuse strategy в A1 |
| TEE attestation hard-fail | До монетизации | Перед платным релизом | Документировано в A4 |
| Library extract `family-crypto-kmp` | До 2-го потребителя | До написания messenger spec.md | TODO inline + watching memory |
| Real-device StrongBox verification | До покупки Pixel | Pixel б/у в течение 3-4 месяцев | Запланировано через owner |
| Multi-app cohabitation chain-of-trust | До messenger MVP | Создание messenger спеки | P-10 в Phase 3 (research notes: `docs/product/future/multi-app-cohabitation.md`) + variant A в MVP |
| Data export UI | До EU релиза или Data Act enforcement | EU юзеры в base | TODO в коде |
| Платный crypto-аудит | До устойчивой монетизации | User base ≥ 10k + revenue ≥ $50/мо | Agent-audit вместо в MVP |

Каждый TODO в коде помечен `TODO(pre-release-audit): ...` для grep-discoverability. Будущий агент или владелец проекта запускает `grep -r "TODO(pre-release-audit):" core/ app/ docs/` и получает живой список открытых пунктов.
