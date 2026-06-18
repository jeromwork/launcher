# Checklist: backend-substitution — spec 016 F-CRYPTO

Run date: 2026-06-17.

F-CRYPTO **сама** offline и без backend. Чек-лист применяется **по запросу владельца** про подготовку к собственному серверу для future multi-device-recovery spec (TBD) (storage encrypted_backup).

## Adapter boundary

- [x] CHK001 — No Firebase/Cloudflare types в F-CRYPTO signatures (offline by design).
- [x] CHK002 — F-CRYPTO не имеет provider — N/A.
- [x] CHK003 — «Provider disappears» test: F-CRYPTO нет provider'а. Future future spec (TBD) будет иметь `RecoveryBackupStorage` port (SRV-CRYPTO-007).

## Wire format

- [x] CHK004 — `KeyBlob` — domain-owned data class (FR-016). Никаких Firestore `Timestamp` / `DocumentReference` в нём.
- [x] CHK005 — `schemaVersion` от первого коммита (FR-025).
- [x] CHK006 — Roundtrip test exists (FR-027).

## Identity

- [N/A] CHK007 — F-CRYPTO не имеет user identity типа. **F-4 dependency снят** — F-CRYPTO работает БЕЗ Google UID.
- [x] CHK008 — Когда F-4 activated (S-5 onwards), UID не leak'нет в F-CRYPTO — будет mixed в HKDF info field как opaque string.
- [x] CHK009 — F-CRYPTO не делает one-way door на provider UID.

## Query/command surface

- [x] CHK010 — F-CRYPTO domain verbs (`encrypt`, `sign`, `deriveSharedSecret`), не provider verbs.
- [x] CHK011 — No security-rules-shaped logic в F-CRYPTO.

## Server-roadmap surfacing

- [x] CHK012 — Records в `docs/dev/server-roadmap.md`:
  - SRV-CRYPTO-001 (универсальный маршрут переезда крипто на свой backend) — pre-existing.
  - SRV-CRYPTO-003 (paid audit milestone перед billing) — added 2026-06-17.
  - SRV-CRYPTO-004 (multi-device recovery per ADR-008) — updated 2026-06-17, social recovery.
  - SRV-CRYPTO-005 (server-side re-encryption для rotation) — added 2026-06-17.
  - SRV-CRYPTO-006 (rate-limit на recovery attempts) — added 2026-06-17.
  - **SRV-CRYPTO-007** (substitution-ready `RecoveryBackupStorage` port для encrypted_backup) — added 2026-06-17 per owner request.
- [x] CHK013 — Inline TODOs не требуются в F-CRYPTO коде (F-CRYPTO offline). TODO для future multi-device-recovery spec (TBD) будет inline в её коде.

## Exemptions

- [x] CHK014 — F-CRYPTO не классифицирует platform integrations.
- [x] CHK015 — F-CRYPTO не делает needless cross-provider abstraction.

## Cost-of-swap summary

- [x] CHK016 — **Cost-of-swap для F-CRYPTO**: 0. F-CRYPTO offline, нет backend dependency. **Для будущей future multi-device-recovery spec (TBD)** (с использованием F-CRYPTO primitives): «If Firestore/Cloudflare disappeared, мы перепишем `FirestoreRecoveryBackupStorage` adapter → `HttpRecoveryBackupStorage` adapter (1 file), запустим migration of existing `encrypted_backup` blobs from Firestore Document → own-server DB (1 background reconciler script), переключим DI binding (1 line). Estimated cost: 3 файла, схема blob'а **не меняется**».

## Open issues

| # | Issue | Severity |
|---|---|---|
| O-1 | F-CRYPTO compliance с substitution rules — **direct beneficiary** ADR-008 architecture (HKDF + AEAD на client; server только хранит ciphertext). Это **уже** дизайн с substitution-readiness. | Verified — no action. |

## Result

**14/14 PASS, 1 N/A**.

**Verdict**: PASS perfectly. F-CRYPTO **является** примером substitution-ready design — крипто on client, server только store ciphertext blobs которые он не может прочитать. Переезд на собственный сервер бесплатный по дизайну.

---

## TL;DR простым языком

F-CRYPTO не использует никакого сервера — всё работает offline. Поэтому вопрос «как переедем на свой сервер» неприменим. **Но** будущая future multi-device-recovery spec (TBD) (восстановление при потере телефона) будет использовать сервер для хранения зашифрованного backup'а. Для неё мы запланировали `RecoveryBackupStorage` port — простой интерфейс «положи blob по ID / возьми blob по ID». Сейчас будем использовать Firestore Document, но **переезд на собственный сервер = переписать 1 файл + перенести существующие blob'ы**. Это и есть «substitution-ready». Записано как SRV-CRYPTO-007.
