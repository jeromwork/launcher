# Checklist: security — spec 016 F-CRYPTO

Run date: 2026-06-17.

F-CRYPTO **является** security infrastructure. Применяем гейты к **тому, как F-CRYPTO защищает данные**, не «как F-CRYPTO выполняет networking» (его нет).

## Data at rest (MASVS-STORAGE)

- [x] CHK001 — PII в F-CRYPTO scope = private keys. **Никогда** не хранятся plain text — всегда обёрнуты wrap pattern (FR-015). SC-009 (Scan blob → no plaintext key found) — explicit test.
- [x] CHK002 — Sensitive data — Curve25519 private keys — в `SecureKeyStore` через Android Keystore wrap, не bare SharedPreferences. Это **главное архитектурное решение** F-CRYPTO.
- [N/A] CHK003 — Cache files — F-CRYPTO не имеет cache (stateless).
- [x] CHK004 — Logging: exception messages содержат **alias** (категория), не key material. Edge Cases явно: `KeystoreUnavailableException("alias=__internal-hkdf-device-salt-v1 not found")`. ⚠️ Plan-phase: добавить explicit Kotlin docs «MUST NOT log key bytes; use alias references».

## Data in transit (MASVS-NETWORK)

- [N/A] CHK005 — F-CRYPTO offline (Assumption).
- [N/A] CHK006 — Same.

## Authentication / Authorization (MASVS-AUTH)

- [N/A] CHK007 — F-CRYPTO не имеет user-facing privileged actions.
- [x] CHK008 — No security by obscurity — все алгоритмы стандартные (XChaCha20-Poly1305, X25519, Ed25519, HKDF-SHA256), industrial baseline (Signal/WhatsApp/age). Open primitives, secret keys — Kerckhoffs's principle (Clarifications).

## Platform interaction (MASVS-PLATFORM)

- [N/A] CHK009..014 — F-CRYPTO не имеет Activities/Services/Receivers/ContentProviders/WebViews.

## Permissions (Article XIV)

- [N/A] CHK015..018 — Android Keystore не требует USES_PERMISSION declaration. Никаких permissions F-CRYPTO не добавляет.

## Privacy (Article XIV §3, §4)

- [x] CHK019 — No hidden collection. F-CRYPTO offline.
- [x] CHK020 — Local-first by design. Cloud usage (cloud key escrow) — отдельная спека 017.
- [N/A] CHK021 — Data не покидает device через F-CRYPTO (зашифрованные blobs может потом отправлять F-5/спека 011 — их responsibility).
- [x] CHK022 — Data minimisation: hierarchy keys derived через HKDF info field — каждый назначение свой key, не shared.

## Build hardening

- [x] CHK023 — `BuildConfig.DEBUG` gate для FakeAdapter (FR-018). Detekt rule + runtime assertion (Edge Cases).
- [N/A] CHK024 — `allowBackup` — application-level, не F-CRYPTO. **Но**: backup rules **должны** exclude `/data/data/<pkg>/files/keys/` — это application-config issue, **записать в plan-phase** «update `data_extraction_rules.xml` to exclude `files/keys/`».

## Дополнительно для крипто-foundation

- [x] **Nonce policy** — adapter генерирует random nonce, caller не передаёт (Clarification Q3, FR-006). Защита от nonce reuse on API level.
- [x] **Wycheproof edge cases** — low-order points, malleable signatures rejected (FR-020).
- [x] **Property tests** — sign→tamper→fail, nonce reuse, replay (FR-021).
- [x] **Cross-platform parity** — гарантия отсутствия platform-specific implementation drift (FR-022).
- [x] **Sealed-box для recovery** — `sealCEK`/`unsealCEK` через `crypto_box_seal` (FR-007, добавлено в Clarifications).

## Open issues

| # | Issue | Severity | Action |
|---|---|---|---|
| O-1 | KDoc «MUST NOT log key bytes» не explicit | Minor | Plan-phase: добавить аннотацию `@SensitiveByteArray` или KDoc convention. |
| O-2 | `data_extraction_rules.xml` exclude `files/keys/` не запланирован | **Medium** | Plan-phase: explicit task для application module — backup rules update. Без этого — Android Auto Backup отправит зашифрованные blobs в Google Drive (что **OK** криптографически — они зашифрованы — но **wrap key** в TEE remains, поэтому в восстановленном backup'е они нечитаемы → плохой UX). |
| O-3 | Friend crypto review снят; нужно явно записать в `docs/dev/crypto-review.md` industrial baseline + reasoning | Already planned | FR-023, FR-024. |

## Result

**11/11 actionable PASS, 11 N/A (offline + infrastructure), 1 medium + 2 minor opens**.

**Verdict**: PASS with one medium open для plan-фазы (backup rules).

---

## TL;DR простым языком

Главные security-проверки крипто-модуля:
- **Приватные ключи никогда не лежат в открытом виде** — всегда обёрнуты через защищённый чип телефона (TEE). Тест проверяет, что в файле blob'а не находится байты исходного ключа.
- **Логи не содержат ключи** — только «названия» (alias'ы) ключей, как категории. Доработать KDoc-конвенцию в plan-фазе.
- **Все алгоритмы стандартные** — те же, что Signal/WhatsApp используют (XChaCha20, X25519, Ed25519). Не изобретаем своё.
- **Уникальный одноразовый код (nonce) генерируется автоматически** — потребитель физически не может зафакапить.
- **Одно среднее замечание**: Android по умолчанию делает резервную копию данных приложения в Google Drive. Нужно настроить **исключение** для папки с ключами — это работа в plan-фазе.
