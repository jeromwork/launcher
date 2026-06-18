# Checklist: failure-recovery — spec 016 F-CRYPTO

Run date: 2026-06-17.

## Error categories

- [x] CHK001 — Каждый FR с external action имеет failure mode (Edge Cases section):
  - libsodium binding dead → fallback BouncyCastle
  - Android Keystore unavailable → `KeystoreUnavailableException`
  - Keystore alias invalidated (Xiaomi MIUI) → `KeystoreInvalidatedException`
  - Cross-platform vector mismatch → CI failure
  - Wycheproof low-order point → adapter rejects
  - Nonce reuse → adapter raises
  - User lock-screen change → key not bound to biometry, не падает
  - Wire format migration → `UnsupportedSchemaVersionException`
  - App reinstall → cross-ref на future spec (TBD)
  - Unknown KeyId prefix → `IllegalArgumentException`
  - Large file OOM → consumer responsibility
- [x] CHK002 — User-visible behaviour: F-CRYPTO — infrastructure module, **UI не показывает**. Failure modes — exceptions, которые **потребитель** (UI of F-5/spec 011) маппит в snackbar/dialog. Спека делает это явно (Edge Cases «Production app должен fail-fast с ясным сообщением...»).
- [x] CHK003 — No silent failures: все failure modes throw exception, не возвращают `null`/`empty`.

## Fallbacks

- [N/A] CHK004 — F-CRYPTO не имеет fallback chains. libsodium → BouncyCastle — это **build-time** decision, не runtime fallback.
- [N/A] CHK005 — Same.
- [x] CHK006 — Terminal behaviour для libsodium fallback: spec явно declines runtime fallback, всё решается в research-фазе плана.

## Retries

- [N/A] CHK007 — Крипто-операции stateless и deterministic, retry не applicable (encrypt сам по себе не fails из-за transient errors). `SecureKeyStore` — local file I/O, может fails из-за file system; retry — consumer responsibility.
- [N/A] CHK008 — Same.
- [x] CHK009 — Idempotency: encrypt с новым nonce каждый раз — non-idempotent (by design — иначе nonce reuse). decrypt — idempotent (same ciphertext → same plaintext). SecureKeyStore.store — idempotent (overwrite). Документировать в plan-phase Kotlin docs.

## Offline / degraded modes

- [x] CHK010 — F-CRYPTO **offline by design** (Assumption «никакой telemetry/network»). Нет сетевых вызовов.
- [N/A] CHK011 — Нет cached data (F-CRYPTO stateless).

## Permissions denied

- [N/A] CHK012 — F-CRYPTO не требует runtime permissions (Android Keystore работает без USES_PERMISSION).
- [N/A] CHK013 — Same.

## Recovery from invalid state

- [x] CHK014 — Corrupt blob (binary garbage, malformed JSON): `KeyBlobDeserializationException`. Recovery — consumer прекращает использовать broken keyId, генерирует новый (или fail-stop). Документировать в plan-phase.
- [x] CHK015 — No "crash and restart": все exception типы recoverable выше по стеку.

## Diagnostics

- [x] CHK016 — Failure observability: exceptions содержат **категорию**, не PII. `KeystoreUnavailableException("alias=__internal-hkdf-device-salt-v1 not found")` — alias = категория, не key material.
- [x] CHK017 — Failures aggregated by exception class. Sentry/Crashlytics (если добавим) будет group'ить.

## Open issues

| # | Issue | Severity | Action |
|---|---|---|---|
| O-1 | Список конкретных exception классов не enumerated в FR | Minor | Plan-phase: добавить sealed class `CryptoException` иерархию: `KeystoreUnavailableException`, `KeystoreInvalidatedException`, `UnsupportedSchemaVersionException`, `NonceReuseException`, `KeyBlobDeserializationException`, `WycheproofRejectionException`. |
| O-2 | Idempotency annotations не определены | Minor | Plan-phase: KDoc на каждой method. |

## Result

**11/11 actionable PASS, 6 N/A, 2 minor opens**.

**Verdict**: PASS. Failure modes покрыты Edge Cases section; exceptions throw'аются, не silent. UI-уровень — consumer responsibility, что архитектурно правильно.

---

## TL;DR простым языком

Что произойдёт, когда что-то сломается в крипто-модуле? **11 сценариев перечислено**: библиотека сдохла, Keystore недоступен, файлы повредились, формат старый и т.п. Для каждого — конкретное поведение (выбросить exception определённого типа). **F-CRYPTO не показывает UI** — это infrastructure-модуль, поэтому показать пользователю snackbar или диалог — это работа того кода, который F-CRYPTO использует (например, F-5 или фото-приложение). Спека явно говорит это. Мелкие замечания — детализировать иерархию exception типов в plan-фазе.
