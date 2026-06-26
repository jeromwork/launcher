# Checklist: failure-recovery — TASK-51 libsodium consolidation

Применён skill `.claude/skills/checklist-failure-recovery/SKILL.md` к `specs/task-51-libsodium-consolidation/spec.md`.

Контекст: спека — внутренний рефакторинг crypto-стэка (libsodium / KMP foundation). Нет сетевых вызовов, нет runtime-permissions, нет fallback chain, нет user-initiated network actions. Большинство пунктов checklist'а либо N/A, либо покрываются throws-pattern (FR-009) + edge cases.

---

## Error categories

- [x] CHK001 Each FR involving an external action lists at least one failure mode.
  Verdict: PASS. FR-001 явно фиксирует устранение `UnsatisfiedLinkError` (native link failure). FR-008/FR-010 затрагивают Keystore — edge case «load(keyId) returns null» покрыт. FR-009 кодифицирует `CryptoException` как единый failure mode для всех crypto APIs. Edge Cases также покрывают: native link failure on ionspin path, persisted-keys-after-upgrade, missing key alias, leftover fake adapters.

- [x] CHK002 For each failure mode: user-visible behaviour specified — not "show error".
  Verdict: PASS with caveat. FR-005 описывает конкретный user-visible flow при missing/wiped old keys — «user видит pairing flow заново с QR-кодом» (force re-pair). Edge case missing key → idempotent ensure-keys (silent recovery, корректно для internal capability). Failure ionspin lazy-bind → fail-fast в `Application.onCreate` через `assertNoFakeCryptoInRelease` / health check (Edge Cases §3). NB: исходный crash (FR-001) — это just то что устраняется, не visible behaviour к проектированию.

- [x] CHK003 No silent failures of user-initiated actions.
  Verdict: PASS. FR-009 enforce uniform throws (`CryptoException`) — нет silent swallow. Pairing-flow ошибки propagate через coroutine scope (CancellationException re-throw гарантирован). Idempotent ensure-keys в edge case — это recovery, не silent failure (нет user-initiated action в этой точке).

## Fallbacks

- [N/A] CHK004 Fallback chain max depth.
  Verdict: N/A. У спеки нет fallback chain. Force re-pair (FR-005) — это recovery strategy, не fallback. Q2 явно зафиксировал «Pattern 4 — zero migration code».

- [N/A] CHK005 Fallback specified by data not hardcoded.
  Verdict: N/A. Нет fallback.

- [N/A] CHK006 Terminal behaviour if fallback fails.
  Verdict: N/A. Нет fallback.

## Retries

- [N/A] CHK007 Retry behaviour explicit.
  Verdict: N/A. Crypto-примитивы детерминированы; нет network / transient failures, где retry имел бы смысл. Pairing-handshake retry — territory TASK-8, не TASK-51.

- [N/A] CHK008 No infinite retry loops.
  Verdict: N/A.

- [x] CHK009 Idempotency: actions safe to repeat.
  Verdict: PASS. Edge case §1 явно требует от `PairingCryptoCoordinator` идемпотентного `ensure-keys` поведения. FR-005 «force re-pair» — idempotent (повторный wipe-and-recreate безопасен).

## Offline / degraded modes

- [N/A] CHK010 Offline behaviour for network reads.
  Verdict: N/A. Спека не делает network reads. (Любая Firestore-публикация DeviceIdentity — domain TASK-8/spec 011, не покрывается TASK-51.)

- [N/A] CHK011 Stale data TTL / freshness.
  Verdict: N/A. Crypto-ключи в Keystore — local persisted state, не имеют TTL в этой спеке. Rotation — explicitly parked в TASK-6 inline-TODO (FR-005).

## Permissions denied

- [N/A] CHK012 Each permission required: behaviour when denied first time.
  Verdict: N/A. Спека не вводит и не использует runtime-permissions. Android Keystore доступ — system capability без runtime prompt.

- [N/A] CHK013 Permanent denial recovery path.
  Verdict: N/A. Нет permissions.

## Recovery from invalid state

- [x] CHK014 Persistent state corruption / schema drift recovery.
  Verdict: PASS. FR-005 покрывает upgrade-from-old-version case: старые aliases (`spec011.encryption.own`, `spec011.signing.own`) детектятся и wipe'ятся, user проходит pairing заново. Edge Cases §1 покрывает missing/corrupted key alias — idempotent regeneration. FR-004 фиксирует что wire-format `schemaVersion: 1` остаётся стабильным (нет schema drift в этой задаче).

- [x] CHK015 No "crash and restart" as recovery for handled errors.
  Verdict: PASS. Throws-pattern (FR-009) + try/catch наверху + UI error handler означает что ошибки обрабатываются, не пропускаются в crash. Edge case ionspin-link-failure — fail-fast в Application.onCreate (это un-handled environmental failure, не handled error type — корректно).

## Diagnostics

- [ ] CHK016 Failures observable: diagnostic event emitted with category, not PII.
  Verdict: GAP. Спека упоминает «один логгер» (Q3 rationale) и health check (Edge Cases §3), но **не определяет** конкретный diagnostic-channel / event category / structured log shape для `CryptoException`. Нет FR покрывающего «при CryptoException emit event X с category Y».
  Open item: добавить FR (например FR-017) — «CryptoException catch site emit-ит structured log с category (`AEAD_FAILURE`, `KEYSTORE_LOAD_NULL`, `NATIVE_LINK_FAILURE`, ...) без PII / key material». Либо явно зафиксировать что diagnostics — out-of-scope TASK-51 и переезжает в отдельный task (тогда inline-TODO).

- [ ] CHK017 Failures aggregated by category, not unique per error string.
  Verdict: GAP. Из той же причины — `CryptoException` hierarchy упомянута (FR-009), но **подклассы / category enum не специфицированы** в спеке. Без этого aggregation by category невозможна — каждое exception message было бы unique.
  Open item: уточнить hierarchy `CryptoException` (например `AeadException`, `KeyStoreException`, `KeyDerivationException`, `NativeLinkException`) либо явный category-enum внутри `CryptoException`. Это maps на FR-009 — расширить.

---

## Open items / follow-ups

1. **CHK016 / CHK017 diagnostic gap**: спека не специфицирует structured logging / category enum для `CryptoException`. Два варианта:
   - (a) добавить FR-017 «CryptoException category enum + structured log emit без PII», обновить FR-009.
   - (b) явно scoped-out: добавить в Assumptions / Edge Cases что diagnostics observability — отдельный backlog-task (TODO ссылку), и поставить inline-TODO в catch-site коде.
2. **CHK002 caveat**: user-visible failure paths покрыты только для re-pair flow (FR-005) и native-link (Edge Cases). Если в будущем `Spec011SmokeDebugActivity` или PairingActivity начнёт catching `CryptoException` — нужно явно прописать user-visible UI behaviour (toast / snackbar / blocking dialog) в spec, а не только «один UI error handler».

---

## Summary

- Total CHK items: 17
- `[x]` (PASS): 7 (CHK001, CHK002, CHK003, CHK009, CHK014, CHK015)
- `[N/A]`: 8 (CHK004, CHK005, CHK006, CHK007, CHK008, CHK010, CHK011, CHK012, CHK013) — wait, 9
- `[ ]` (GAP): 2 (CHK016, CHK017)

Recount: PASS=6 (CHK001/002/003/009/014/015), N/A=9 (CHK004/005/006/007/008/010/011/012/013), GAP=2 (CHK016/017). Total 6+9+2 = 17. OK.

Verdict (skill output line):

failure-recovery: 6/17 CHK [x]

(N/A counted separately per skill convention — [x] only counts true PASS items. With N/A: 15/17 closed; 2 open gaps on diagnostics observability.)
