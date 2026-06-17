# Plan-level checklist re-runs

Per `speckit-plan` Step 5 — re-run domain-isolation, wire-format, meta-minimization на plan.md (not just spec.md) для verification, что architectural decisions sound.

Run date: 2026-06-17.

---

## domain-isolation (re-run on plan)

Все 14 actionable пунктов прошли на spec-уровне; plan-уровень добавляет конкретику размещения файлов:

- [x] **Vendor SDKs**: libsodium ionspin **только** в `core/crypto/libsodium/` package. `commonMain/api/` не импортирует `com.ionspin.*` (verified через plan.md project structure tree).
- [x] **Android Keystore**: используется **только** в `androidMain/SecureKeyStore.kt`. `commonMain/api/SecureKeyStore.kt` = `expect class` без platform deps.
- [x] **Transport types**: F-CRYPTO offline, no HTTP/Retrofit/transport types.
- [x] **Platform types в commonMain**: zero. KeyBlob использует `kotlinx.datetime.Instant`, not `java.time.Instant`.
- [x] **Port shape**: domain-driven (`encrypt`, `sign`, `deriveSharedSecret`), не `callLibsodium(...)`.
- [x] **Fake adapter для каждого port** — все 7 (5 + 2 stubs) перечислены.
- [x] **Real adapter в platform main**: `libsodium/` + `androidMain/`, iOS stub-screamer.
- [x] **DI wiring per build variant**: `app/src/main/CryptoModule.kt` + `app/src/debug/`.
- [x] **Source-set placement justified**: каждый файл размещён explicitly с reason (`SecureKeyStore` в `androidMain` потому что Android Keystore API; libsodium adapters в `commonMain/libsodium/` потому что ionspin даёт common API).
- [x] **No new expect/actual where pure-Kotlin would suffice**: `SecureKeyStore` — единственный `expect class`, justified (Android Keystore vs iOS Keychain vs in-memory test).

**Verdict**: PASS. Plan-уровень domain-isolation чист.

---

## wire-format (re-run on plan)

- [x] **Schema version**: `KeyBlob.schemaVersion: Int = 1` from day 1 (contracts/key-blob-v1.md).
- [x] **schemaVersion read first**: described in contract (Read step 6).
- [x] **CURRENT_SCHEMA_VERSION constant**: declared in `KeyBlob` companion object (data-model.md).
- [x] **Backward compat**: minor bumps = additive; major = migrator-before-release.
- [x] **Adding field policy**: default value, no migrator needed.
- [x] **Renaming/removing field**: requires migrator, major bump.
- [x] **Migration code scoped**: `migrateV1ToV2(blob)` function pattern documented in contract.
- [x] **Forward compat**: unknown schemaVersion → `CryptoException.UnsupportedSchemaVersion` throw, not silent.
- [x] **Roundtrip test**: explicit in contracts/key-blob-v1.md.
- [x] **Backward-compat test**: explicit (`v1-sample.json` frozen fixture).
- [x] **Fixtures stored as files**: `commonTest/resources/key-blob/v1-sample.json` (not string literals in test code).
- [x] **Cross-platform parity**: explicit test `KeyBlobCrossPlatformParityTest` checking identical bytes Android+JVM.
- [x] **Persistence**: file-based в app sandbox; namespacing через KeyId prefix.
- [x] **Contracts folder**: created — `contracts/key-blob-v1.md` exists.

**Plan-level addition** (not in spec checklist):
- [x] **AEAD ciphertext envelope** (FR-006): F-CRYPTO declines responsibility — consumer (F-5) defines envelope с schemaVersion в своей спеке. Plan §"Wire formats" + contracts/key-blob-v1.md "What is NOT in this contract" — explicit boundary.

**Verdict**: PASS. Wire-format checklist O-2 (medium open from spec phase: "envelope wrapper responsibility") **resolved** — F-CRYPTO declines, F-5 owns.

---

## meta-minimization (re-run on plan)

- [x] **Every new port has concrete consumer in this spec**: 5 active ports — Sentinel 1 (F-5 ConfigCipher), Сценарий 1+2 (cross-platform encrypt/decrypt), Сценарий 3 (identity key gen), Сценарий 4 (key protection). `KeyRotation`/`KeyEscrow` stubs — consumer = их собственная storage shape (rule 5 compliance).
- [x] **Single-impl interface justified**: каждый port имеет Fake + Real, не "single impl".
- [x] **No mediator/orchestrator/manager**: zero таких. Простые ports.
- [x] **No custom DSL/registry**: `KeyNamespace` — sealed class enum, не plugin system.
- [x] **Gradle module justified**: Article V §3 (5 criteria — см. Gate 1).
- [x] **Package not enough?**: yes, plan answers — KMP target structure требует subproject; library extract path требует subproject; ionspin dependency лучше isolated в subproject.
- [x] **No "utils" / "common" / "helpers" module**: имена конкретные.
- [x] **New config field has FR consumer**: каждое поле `KeyBlob` имеет FR (FR-016, FR-025, FR-026) + используется в data flow.
- [x] **Defaults documented**: `schemaVersion = CURRENT_SCHEMA_VERSION = 1`, `retiredAt = null`, `replacedBy = null`.
- [x] **Rule 4 Test 1**: inlining `AeadCipher` → leak libsodium types в потребителей = нарушение rule 1+2. Не inline.
- [x] **Rule 4 Test 2**: swap libsodium → BouncyCastle = 1 adapter module, 1-2 weeks (research.md R1). Seam needed.
- [x] **No dangling removals**: F-CRYPTO purely additive.

**Plan-level addition**: каждый Phase в Rollout / verification (14 phases) — testable, no architecture essays per Article XII §3.

**Verdict**: PASS. Все 13 пунктов passing, with 1 accepted exception (stubs).

---

## Summary

| Checklist | spec-level | plan-level re-run |
|---|---|---|
| domain-isolation | PASS | ✅ PASS |
| wire-format | 12/13 PASS, 1 medium open (O-2) | ✅ PASS — O-2 resolved at plan level (boundary declared) |
| meta-minimization | 13/13 PASS | ✅ PASS |

**Overall**: Plan-level architectural decisions sound. Ready for `/speckit.tasks`.

---

## TL;DR простым языком

После того как написали план реализации, **ещё раз** прогнали через 3 главные проверки качества:

1. **Изоляция бизнес-логики от внешних библиотек** — проверили, что внешние библиотеки (libsodium, Android Keystore) живут **только** в специально отведённых папках, а не «протекают» в общий код. ✅
2. **Версионирование форматов файлов** — проверили, что у формата файла-ключа (`KeyBlob`) есть `schemaVersion`, тесты на миграцию, фикстура «v1-sample.json», которая никогда не меняется. ✅
3. **Минимум абстракций** — проверили, что мы не добавили «на всякий случай» каких-то лишних слоёв. Каждый компонент имеет реального потребителя. ✅

Также **закрыли одно среднее замечание** из spec-фазы — про то, кто отвечает за «обёртку» зашифрованных данных, которые F-5 будет отправлять на сервер. Ответ: **не F-CRYPTO** (мы возвращаем «непрозрачные байты»), а **F-5** в своей собственной спеке.

Всё готово к следующему шагу — `/speckit.tasks`.
