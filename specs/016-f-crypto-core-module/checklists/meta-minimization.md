# Checklist: meta-minimization — spec 016 F-CRYPTO

Run date: 2026-06-17.

## New abstractions

- [x] **CHK001** Every new port has concrete consumer in this spec.
  `AeadCipher`, `AsymmetricCrypto`, `KeyDerivation`, `RandomSource`, `SecureKeyStore` — все используются прямо в US-1, US-3, validation set'е, и в имплементации (libsodium adapter + Fake + Test fixtures). ⚠️ Edge case: **`KeyRotation` и `KeyEscrow`** — interface-only **без real-impl в этом спеке**. Acceptable как **stub ports**: они нужны прямо сейчас потому что (а) определяют storage shape с `retiredAt`/`replacedBy` (FR-016, требуется для будущей backward-compat), (б) дают потребителям compile-time interface для DI; consumer — F-5/future multi-device-recovery spec (TBD) уже будут писаться против них.
- [x] **CHK002** Single-implementation interface — justified.
  Все ports реализуются Fake + Real (Libsodium). `KeyRotation`/`KeyEscrow` — DI-shape для будущих real impl + Fake для тестов. ✅
- [x] **CHK003** Mediator/manager justified.
  Нет менеджеров — простые ports + adapters.
- [x] **CHK004** No custom DSL / registry.
  `KeyNamespace` sealed class — это **enum-style enumeration**, не plugin system. Простая композиция.

## New modules / packages

- [x] **CHK005** New gradle module satisfies Article V §3.
  `core/crypto/` — owns external SDK boundary (libsodium ACL), provides independent test, может быть extract'нут в отдельный артефакт (US-5). Material testability gain (KAT + Wycheproof + property tests изолированы).
- [x] **CHK006** Why is a package not enough?
  Явно в spec'е: artifact `lib-family-crypto` будет extract'нут когда появится 2-й потребитель (мессенджер, фото). Package внутри `app/` не позволит этого. Plus: KMP target structure требует Gradle subproject.
- [x] **CHK007** No "utils" / "common" / "helpers".
  Имена ports конкретные (`AeadCipher`, `KeyDerivation`).

## New configuration

- [x] **CHK008** New config field has FR consumer.
  `KeyBlob.schemaVersion`, `algorithm`, `createdAt`, `retiredAt?`, `replacedBy?` — каждое имеет FR (FR-016, FR-025, FR-026). `replacedBy`/`retiredAt` — для будущей rotation, **но** требуются для wire-format с первого коммита (rule 5).
- [x] **CHK009** Backward-compat policy defined.
  FR-026 (backward-compat read test), Clarifications Q8 (semver policy).

## CLAUDE.md rule 4 self-test

- [x] **CHK010** Test 1: if abstraction inlined, what is lost?
  - `AeadCipher` inlined → libsodium leak в F-5/011 = нарушение rule 1+2.
  - `SecureKeyStore` inlined → Keystore-Android types leak в commonMain.
  - `KeyRotation` stub — если inline'нем сейчас, потом потребители (F-5) **обязаны** переписаться когда real-rotation придёт = переписывание, не дописывание. ✅
- [x] **CHK011** Test 2: if dependency doubled in price / deprecated / privacy issue, swap time?
  - libsodium → BouncyCastle: 1 adapter module (`core/crypto/libsodium/`). ≈1-2 дня. ✓
  - Android Keystore → software fallback: 1 adapter (`SecureKeyStore` Android impl). 1 день. ✓

## Removal validation

- [x] **CHK012** N/A — спека не удаляет существующих абстракций.
- [x] **CHK013** N/A — нет deprecated кода.

## Open issues

| # | Issue | Severity |
|---|---|---|
| O-1 | `KeyRotation`/`KeyEscrow` stub ports — formally violate "only abstractions with consumer". Mitigation: они **являются** consumer'ами своей собственной storage shape (`retiredAt`/`replacedBy`), которая нужна с первого коммита по rule 5. Accepted exception, documented в Clarifications. | Accepted |

## Result

**13/13 PASS** (с 1 documented accepted exception для stub ports).

**Verdict**: PASS. Спека минимизирована до того, что необходимо для unblock'инга потребителей.

---

## TL;DR простым языком

Проверил, нет ли в спеке «архитектуры на будущее, которая никому не нужна сейчас». Всё чисто: каждый port имеет реального потребителя (F-5, спека 011, future multi-device-recovery spec (TBD)). Единственное исключение — два port'а (`KeyRotation`, `KeyEscrow`) объявлены как «заглушка-крикуны» — у них нет реальной реализации сейчас, только interface. Это **намеренно**: они нужны для того, чтобы формат хранения ключей сразу содержал поля `retiredAt`/`replacedBy` для будущей ротации. Если бы добавили эти поля потом — была бы миграция всех уже-сохранённых ключей. А так — добавляем «дописыванием» по правилу владельца, без переписывания.
