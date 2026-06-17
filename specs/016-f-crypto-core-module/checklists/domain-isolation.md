# Checklist: domain-isolation — spec 016 F-CRYPTO

Run date: 2026-06-17.

## Vendor SDKs

- [x] CHK001 — Никакой libsodium/ionspin/Android Keystore тип не в domain signatures. FR-028 явно запрещает `com.ionspin.kotlin.crypto.*` и `android.security.keystore.*` в `core/crypto/api/`.
- [x] CHK002 — libsodium имеет один wrapper (`core/crypto/libsodium/`). Android Keystore имеет один wrapper (`SecureKeyStore` Android adapter).
- [x] CHK003 — «libsodium disappears tomorrow» test: 1 adapter module (`core/crypto/libsodium/`). US-4 acceptance scenario 1 явно это тестирует.

## Transport types

- [N/A] CHK004 — F-CRYPTO не имеет network calls. Никаких HTTP/Retrofit/JSON DTO.
- [x] CHK005 — `KeyBlob` — domain-owned data class (FR-016); сериализация через `kotlinx.serialization.json` — это сериализатор, не transport type.

## Platform types

- [x] CHK006 — `commonMain` (т.е. `core/crypto/api/`) не содержит `android.*` / `androidx.*` / `Intent` / `Uri`. FR-028 enforces.
- [x] CHK007 — `KeyId` — value class над `String`, не platform type. `KeyBlob.createdAt` — domain-owned timestamp (clarify в plan: `kotlinx.datetime.Instant` или Long ms), не `java.time.Instant`.

## Ports

- [x] CHK008 — Каждая внешняя поверхность через port: AeadCipher (libsodium), AsymmetricCrypto (libsodium), KeyDerivation (libsodium), RandomSource (libsodium), SecureKeyStore (Android Keystore / iOS Keychain).
- [x] CHK009 — Port shapes domain-driven: `encrypt(plaintext, key, aad) → ciphertext` — domain verb, не `callLibsodium(...)`.
- [x] CHK010 — Fake adapter для каждого port'а (FR-017, Local Test Path).
- [x] CHK011 — Real adapter в `androidMain` (`LibsodiumAeadCipher`, `KeystoreSecureKeyStore`); iOS adapter — заглушка-крикун (Q1 resolution).
- [x] CHK012 — DI wiring per build variant (FR-030).

## Source-set placement

- [x] CHK013 — `commonMain` для ports + KeyBlob domain type + value classes (KeyId, KeyNamespace); `androidMain` для libsodium adapter (через ionspin) + Keystore adapter; `iosMain` для заглушки.
- [x] CHK014 — Default `commonMain`; deviation для `SecureKeyStore` actual = use Android Keystore API (`androidMain`).

## Existing-code regressions

- [N/A] CHK015 — F-CRYPTO — новый модуль, не touch'ает существующий `commonMain` код.
- [x] CHK016 — `expect/actual` оправдано: `SecureKeyStore` физически разный на Android (Keystore) и iOS (Keychain).

## Open issues

| # | Issue | Severity |
|---|---|---|
| O-1 | `kotlinx.datetime.Instant` vs `Long` для `createdAt`/`retiredAt` — выбрать в plan-фазе. Оба domain-safe. | Minor |

## Result

**14/14 PASS, 2 N/A, 1 minor open**.

**Verdict**: PASS. F-CRYPTO архитектурно строится **именно как** реализация rule 1+2 — это его цель.

---

## TL;DR простым языком

Главное правило проекта — «бизнес-логика не должна знать про конкретные внешние библиотеки». В F-CRYPTO это правило **является самой целью спеки**: мы выделяем libsodium в отдельный модуль с интерфейсами, чтобы остальные части проекта могли использовать криптографию, **не зная** про libsodium. Если libsodium завтра умрёт — мы заменим **один** файл, остальной код не заметит. Проверка прошла полностью.
