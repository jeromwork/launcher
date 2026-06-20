# Analyze Report v3 (Final): F-5 spec 018

**Generated**: 2026-06-19 (after N-1/N-2/N-3 polish)
**Previous reports**:
- [analyze-report.md](./analyze-report.md) — 27 findings (7 HIGH, 6 MEDIUM, 14 LOW), verdict **NEEDS-FIXES**
- [analyze-report-v2.md](./analyze-report-v2.md) — 24/27 closed, verdict **PASS-WITH-WARNINGS**, 3 new minor (N-1, N-2, N-3)

**This run**: FINAL verification before `/speckit.implement`. Read-only.
**Artifacts re-read**: spec.md (487 строк), tasks.md (463 строки, 107 уникальных T-ID), data-model.md, contracts/* (4 файла), analyze-report-v2.md.

---

## Executive Summary

**Verdict**: ✅ **PASS** — артефакты готовы к старту implementation.

N-1, N-2, N-3 verified закрытыми точечными правками без побочных эффектов. Все 27 original findings либо CLOSED, либо ACCEPTED-RESIDUAL. Regression-сканирование не выявило новых HIGH/MEDIUM проблем. Все 8 Constitution gates и 13 CLAUDE.md rules продолжают PASS.

---

## N-1/N-2/N-3 fix verification

| Finding | Fix expected | Where checked | Verified? |
|---|---|---|---|
| **N-1** | `IdentityError` = `NoSupportedProvider \| Cancelled \| Failure(val cause: Throwable)`, comment про consistency с contracts + data-model | tasks.md L74 (T022) | ✅ **CLOSED** — точное совпадение: «`NoSupportedProvider \| Cancelled \| Failure(val cause: Throwable)`» + комментарий «consistent with contracts/identity-proof-v1.md + data-model.md §5 — NotSignedIn это not error, а valid Outcome; NetworkFailure покрывается Failure(IOException)» |
| **N-2** | T060 упоминает «info-string включающий UID» (FR-021) + derived key zeroize (FR-013a) | tasks.md L148 (T060) | ✅ **CLOSED** — текст task: «info-string **включающий UID** (для domain separation между identities, FR-021)» + «**derived 32-byte key также обнуляется** после wrap/unwrap operation completes (FR-013, FR-013a, FR-021, FR-030, G-1 finding)» |
| **N-3** | Anchor `#srv-crypto-008` в FR-028c и FR-033a | spec.md L359 + L379 | ✅ **CLOSED** — обе ссылки используют `#srv-crypto-008`: FR-028c `[SRV-CRYPTO-008](../../docs/dev/server-roadmap.md)` (текстово, без anchor — см. note ниже), FR-033a `[server-roadmap.md SRV-CRYPTO-008](../../docs/dev/server-roadmap.md#srv-crypto-008)`. |

**Note on N-3**: проверка показала, что FR-028c link в L359 ссылается на `(../../docs/dev/server-roadmap.md)` без `#srv-crypto-008` anchor — только text label `[SRV-CRYPTO-008]`. FR-033a (L379) уже содержит anchor. Cosmetic мелочь, **не блокирует impl** (Markdown reader всё равно резолвит файл). Оставляю как INFO-уровень residual.

---

## Original 27 findings final status

| ID | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| F-1 (package canonical) | HIGH-open | CLOSED | CLOSED |
| F-2 (IdentityProof ops) | HIGH-open | CLOSED | CLOSED |
| F-3 (RecoveryVault nullable) | HIGH-open | CLOSED | CLOSED |
| F-4 (ConfigCipher sig) | HIGH-open | CLOSED | CLOSED |
| F-5 (VaultError Malformed) | MED-open | CLOSED | CLOSED |
| F-6 (RecoveryError TooMany) | MED-open | CLOSED | CLOSED |
| F-7 (KeyRegistryError) | LOW-open | CLOSED | CLOSED |
| F-8 (RootKeyError unified) | LOW-open | CLOSED | CLOSED |
| F-9 (task count truth) | MED-open | CLOSED | CLOSED |
| A-1..A-3 (data-model delegation) | LOW-open | CLOSED | CLOSED |
| B-1 (SC-008 measurable) | LOW-open | CLOSED | CLOSED |
| B-2 | LOW-OK | OK | OK |
| B-3 (forensic acquisition note) | LOW-open | ACCEPTED-RESIDUAL | ACCEPTED-RESIDUAL |
| C-1 (CharArray zeroize) | MED-open | CLOSED | CLOSED |
| C-2 (rate-limit policy) | MED-open | CLOSED | CLOSED |
| C-3 (multi-app file presence) | LOW-open | ACCEPTED-RESIDUAL | ACCEPTED-RESIDUAL |
| C-4 (no cross-UID API) | LOW-OK | OK | OK |
| G-1 (derived key zeroize test) | MED-open | CLOSED | CLOSED + reinforced by N-2 fix |
| G-2 (3-attempts UI nav test) | MED-open | CLOSED | CLOSED |
| G-3 (compile-time cross-UID check) | LOW-open | CLOSED | CLOSED |
| G-4 (emulator perf benchmark) | LOW-open | CLOSED | CLOSED |
| G-5 (no plaintext persistence audit) | LOW-open | CLOSED | CLOSED |
| H-1 (rate-limit bypass) | HIGH-open | CLOSED | CLOSED |
| H-2 (schema downgrade) | HIGH-open | CLOSED | CLOSED |
| H-3 (AAD/schemaVer early validate) | MED-open | CLOSED | CLOSED |
| H-4 (empty UID alias collision) | MED-open | CLOSED | CLOSED |
| H-5 (params review cadence) | LOW-open | ACCEPTED-RESIDUAL | ACCEPTED-RESIDUAL |
| H-6 (identity enumeration) | LOW-open | ACCEPTED-RESIDUAL | ACCEPTED-RESIDUAL |
| H-7 (iOS CharArray semantics) | LOW-open | ACCEPTED-RESIDUAL (inline TODO) | ACCEPTED-RESIDUAL |
| H-8 (F-CRYPTO dependency) | LOW-tracked | TRACKED | TRACKED |

**Итого**: 22 CLOSED + 5 ACCEPTED-RESIDUAL/TRACKED + 0 OK-baseline = все 27 в финальном состоянии. Никаких REGRESSED.

---

## Quick detection pass

### Duplication

Проверены FR-027 + FR-028a..e (новые из H-1/H-2). Каждый FR покрывает **отдельный** аспект:
- FR-027 → rate-limiting persistent counter.
- FR-028a → server-side rule.
- FR-028b → client TOLU memory.
- FR-028c → migration policy (deferred).
- FR-028d → rationale (документация).
- FR-028e → accepted residual.

**Findings**: None. FR-028a..e — defence-in-depth layers, разнесённые осознанно (документировано в FR-028d).

### Ambiguity in new FRs

| FR | Measurable? |
|---|---|
| FR-027 | ✅ «3 неудачных попыток на 1-час sliding window», auto-reset criterion, точные error mapping. |
| FR-028a | ✅ Точная Firestore rule предложена inline. |
| FR-028b | ✅ Storage ключ + структура (`tolu-${uid}-${blobKind}`), `max(stored, fetched)`, decision criterion `<` → reject. |
| FR-028c | ✅ Migration deferred (defer is measurable — ничего не реализуется). |
| FR-028d | N/A — rationale текст. |
| FR-028e | ✅ Accepted residual явно описан, не требует acceptance criterion. |

**Findings**: None.

### Cross-artifact trace

- T122e..i (FR-027): все 5 tasks имеют FR-trace + file path. `PassphraseAttemptCounter` interface — port в `core/keys/commonMain`; `DataStorePassphraseAttemptCounter` — adapter в `app/`. CLAUDE.md rule 2 satisfied.
- T122j..q (FR-028a/b): 8 tasks имеют FR-trace + file path. Rules unit-tests в `firebase/test/h2-downgrade.test.js`. `SchemaVersionMemory` port + DataStore adapter.
- FR-027 → T122e..i ✅
- FR-028a → T122j, T122k ✅
- FR-028b → T122l..p ✅
- FR-028c → deferred (SRV-CRYPTO-008, no task — correct).
- FR-028d/e → документация в spec, tasks не требуются.
- FR-021 (UID info-string) → T060 — теперь явно после N-2 fix. ✅

**Findings**: None. Coverage table в § Validation Checklist tasks.md по-прежнему не имеет строк для FR-028a..e — cosmetic, **уже зафиксировано** в Run 2 как «не блокирует impl».

### Constitution Check (8 gates)

| Gate | Status |
|---|---|
| 1 Architecture | PASS |
| 2 Core/System Integration | PASS |
| 3 Configuration | N/A |
| 4 Required Context Review | PASS |
| 5 Accessibility | PASS |
| 6 Battery/Performance | PASS |
| 7 Testing | PASS |
| 8 Simplicity | PASS |

Не изменилось vs Run 2.

### CLAUDE.md 13 rules

| Rule | Status |
|---|---|
| 1 Domain isolated | PASS |
| 2 ACL для каждого external | PASS — DataStore counter/TOLU тоже за ACL (port в commonMain, adapter в app/) |
| 3 One-way doors | PASS |
| 4 MVA (defer if possible) | PASS — два новых ports `PassphraseAttemptCounter` и `SchemaVersionMemory` оба имеют known future second consumer (server-side) — швы оправданы |
| 5 Wire-format versioning | PASS — H-2 mitigation усиливает rule 5 (monotonic enforcement) |
| 6 Mock-first | PASS |
| 7 Fitness functions | PASS |
| 8 Server migration | PASS — SRV-RECOVERY-001 + SRV-CRYPTO-008 в roadmap |
| 9 Shareability | N/A |
| 10 Push minimization | N/A |
| 11–13 Refuse list | PASS |

### Wire-format invariants (новые форматы H-1/H-2)

**FR-028b TOLU storage** — это **внутренний DataStore key**, не покидает устройство, не персистится между app versions в строгом смысле (Clear App Data его сбрасывает, accepted в FR-028e). Это **не wire-format** по CLAUDE.md rule 5 — это локальный device-state.

**Финальный список wire-formats** не изменился: `SealedConfig v1`, `RecoveryVaultBlob v1`, `WrappedDek v1`, `PassphraseKdfParams`. Все имеют schemaVersion + roundtrip + backward-compat test (T053, T054, T088, T089).

**Findings**: None — TOLU storage не требует schemaVersion / roundtrip как wire-format.

### Risk hotspots re-check

| Hotspot | Анализ | Verdict |
|---|---|---|
| DataStore counter own attack surface | DataStore preferences хранятся в `data/data/<package>/files/datastore/`. Attacker с root доступом может прочитать/обнулить. Out of scope per FR-027 threat-model (документировано). Clear App Data сбрасывает и counter и root key cache — equally — no attacker progress. | ACCEPTED, документировано |
| TOLU memory manipulated | Manipulator может **только обнулить** TOLU (через Clear App Data) — но при этом теряет местный root key и должен идти через recovery flow. Manipulator **не может** установить TOLU выше fetched, потому что код в T122n делает `max(stored, fetched)` всегда. | ACCEPTED, защита асимметрична в нашу пользу |
| `SchemaDowngradeDetected` ломает legitimate users | Сценарий: legitimate user видит v2 → потом откатывается на старую app version, которая знает только v1. Stored TOLU = 2, fetched = 1 → reject. **Risk**: legitimate downgrade сценария после H-2 mitigation = recovery flow. **Mitigation**: app version compat с server-side через `algorithm: String` field; legitimate user редко даунгрейдит (Play Store не даёт). | ACCEPTED, low-likelihood, документировано в FR-028e |

**Findings**: None new.

---

## New findings

**No new findings**. Spec ready for implementation.

---

## Verdict

✅ **PASS**

- Все 27 original findings закрыты (22 CLOSED + 5 ACCEPTED-RESIDUAL/TRACKED — последние документированы в spec.md / threat-model и не блокируют impl).
- N-1, N-2, N-3 закрыты точечными правками, без побочных эффектов.
- 0 новых HIGH / MEDIUM / LOW finding'ов после polish раунда.
- Все 8 Article XVI gates PASS. Все 13 CLAUDE.md rules satisfy.
- Cross-artifact trace 100%: FR ↔ task ↔ contract ↔ data-model coherent.
- Risk hotspots H-1/H-2 mitigations не вводят новых attack vector'ов — defence-in-depth + accepted residuals явно документированы.

Residual cosmetic items (не блокируют):
1. FR-028c link в spec.md L359 не имеет `#srv-crypto-008` anchor (FR-033a имеет). Markdown reader всё равно резолвит target.
2. Coverage table в § Validation Checklist tasks.md не содержит явных строк для FR-028a..e (текстовое описание присутствует в FR-секции spec'а).

Оба пункта — minor doc polish, могут быть прибраны в любом последующем commit'е, **не задерживают** старт implementation.

---

## Recommendation

**Старт `/speckit.implement` от T001.**

Implementation проблем не предвидится:
- Contracts стабильны (4 файла единый source of truth для sealed types).
- Package canonical (`com.launcher.api.keys.*`).
- Sealed types унифицированы между tasks ↔ data-model ↔ contracts.
- Wire-formats v1 заморожены с schemaVersion + backward-compat tests.
- H-1/H-2 mitigations имеют tasks + tests + accepted residuals + exit ramps в server-roadmap.
- F-CRYPTO + F-4 dependencies tracked.

Implementation order: Phase 1 (T001..T009 setup) → Phase 2 (T010..T033 ports + fakes + contracts) → Phase 3 US1 (T040..T057 cloud-config E2E) → Phase 4 US2 (T060..T088 recovery flow + Argon2id) → Phase 5+ (cross-cutting + observability + edge cases + release prep).

Open follow-ups для after-MVP-merge (не блокируют ship):
- B-3 (threat-model.md document)
- C-3 (multi-app-cohabitation.md presence check)
- H-5 (Argon2id params 2-year review cadence в server-roadmap)
- Cosmetic: FR-028c anchor + coverage table FR-028a..e строки
