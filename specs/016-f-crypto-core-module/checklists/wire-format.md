# Checklist: wire-format — spec 016 F-CRYPTO

Run date: 2026-06-17.

Wire formats в F-CRYPTO: **только `KeyBlob`** (формат хранения wrapped private keys в app sandbox).
AEAD ciphertext blob — внутренний формат libsodium, не считается wire format потребителей (это intermediate byte array, версионируется через libsodium API).

## Schema version

- [x] CHK001 — `KeyBlob.schemaVersion: Int` обязателен (FR-016, FR-025) с первого коммита.
- [x] CHK002 — `schemaVersion` first-read во время deserialization — это plan-фаза implementation detail; пометить как FR в plan'е.
- [x] CHK003 — Currently-supported version constant в одном месте: `KeyBlobSchema.CURRENT = 1` (plan-phase implementation).

## Backward compatibility

- [x] CHK004 — Backward-compat read в пределах major version обязателен (Clarifications Q8 — semver).
- [x] CHK005 — Adding field allowed; default value handling — implementation in plan.
- [x] CHK006 — Rename/remove требует migrator до релиза (FR-026 backward-compat read test).
- [x] CHK007 — `migrateLegacy(blob, fromVersion): KeyBlob` style — plan-phase.

## Forward compatibility

- [x] CHK008 — Reading newer version: FR-026 test для будущей `v2` миграции. Fail-closed на unknown version с `UnsupportedSchemaVersionException`.
- [N/A] CHK009 — Discriminator не используется в KeyBlob.

## Tests

- [x] CHK010 — Roundtrip test exists: FR-027 «все wire-format roundtrip тесты MUST include cross-platform assertion».
- [x] CHK011 — Backward-compat test: FR-026 — `v1` blob читается current code; future v2 blob чтение v1 — pass.
- [x] CHK012 — Fixtures в `commonTest/resources/key-blob/v1-sample.json` — Local Test Path упоминает `cross-platform-vectors/encryption-roundtrip-v1.json`; добавить `key-blob/v1-sample.json` в plan-фазу.

## Persistence specifics

- [x] CHK013 — SharedPreferences/DataStore — F-CRYPTO не использует. Blob хранится в файле `/data/data/<pkg>/files/keys/<keyId>.blob` (FR-015). Namespacing через `KeyId` prefix (FR-010).
- [N/A] CHK014 — SQLDelight не используется.
- [N/A] CHK015 — Removal cleanup — F-CRYPTO ничего не удаляет.

## Deep-link / QR / exported config

- [N/A] CHK016 — F-CRYPTO не делает deep-links/QR. Future экспорт ключей через `KeyEscrow` — stub-only, реальный flow в спеке 017.
- [N/A] CHK017 — Same as above.

## Contract folder

- [N/A] CHK018 — `contracts/` будет добавлена в plan-фазе если потребуется (для KeyBlob format).

## Open issues

| # | Issue | Severity |
|---|---|---|
| O-1 | `contracts/key-blob-v1.md` не создан — рекомендация для plan-фазы. | Minor |
| O-2 | AEAD ciphertext blob format (nonce + ciphertext + tag) — libsodium-defined; documented as "internal", но потребители (F-5) **передают эти байты** в Firestore — это **тоже** wire format. Нужен schemaVersion wrapper? | **Medium — пересмотреть в plan** |

## Result

**12/13 actionable PASS, 5 N/A, 2 opens (1 minor, 1 medium)**.

**Verdict**: PASS with caveat — Issue O-2 требует решения в plan-фазе. Если AEAD ciphertext будет передаваться через Firestore (F-5), то нужен envelope формат `{schemaVersion, cipher, version, ciphertext_bytes}` — не часть F-CRYPTO, но F-CRYPTO должен явно decline ответственности за этот wrapper.

---

## TL;DR простым языком

В F-CRYPTO один формат, который надо версионировать — это файл с обёрнутым ключом (`KeyBlob`). Версия (`schemaVersion`) добавлена с первого коммита, тесты для миграции форматов запланированы. **Замечание**: сами зашифрованные данные (ciphertext), которые F-5 будет передавать на сервер — это **тоже** в каком-то смысле wire format, но F-CRYPTO даёт их как «непрозрачные байты». Решить, кто отвечает за обёртку этих байтов (F-CRYPTO или F-5), — задача plan-фазы.
