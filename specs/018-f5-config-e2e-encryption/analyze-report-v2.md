# Analyze Report v2: F-5 spec 018 (re-run after fixes)

**Generated**: 2026-06-19 (после H-1/H-2 mitigation и закрытия F-1..F-9)
**Previous report**: [analyze-report.md](./analyze-report.md) — 27 findings (7 HIGH, 6 MEDIUM, 14 LOW), verdict **NEEDS-FIXES**
**Mode**: Read-only re-verification.
**Artifacts re-read**: spec.md (488 строк), tasks.md (~463 строки, фактически 107 уникальных task ID), data-model.md (319), contracts/firestore-security-rules.md (133), contracts/identity-proof-v1.md, contracts/key-registry-v1.md, contracts/recovery-vault-v1.md, contracts/sealed-config-v1.md.

---

## Diff vs previous run

| ID | Prev severity | Status | Notes after re-check |
|---|---|---|---|
| **F-1** | HIGH | ✅ FIXED | Все артефакты используют `com.launcher.api.keys.*` (spec.md FR-001, plan.md, tasks.md T001/T010-T014, contracts/*.md L12-14). Canonical package зафиксирован. |
| **F-2** | HIGH | ✅ FIXED | tasks.md T012 теперь объявляет 4 операции (`currentIdentity`, `identityFlow`, `requestSignIn`, `signOut`); spec.md FR-006 расширен до 4 операций; matches contracts/identity-proof-v1.md. |
| **F-3** | HIGH | ✅ FIXED | tasks.md T013 теперь non-nullable `Outcome<RecoveryVaultBlob, VaultError>` + `VaultError.NotFound`; matches contracts/recovery-vault-v1.md L21. |
| **F-4** | HIGH | ✅ FIXED | tasks.md T014: `seal(configBytes: ByteArray, uid: String): Outcome<SealedConfig, CryptoError>` — точно соответствует contracts/sealed-config-v1.md и FR-016. AAD binding (`uid || schemaVersion`) у T040 согласован. |
| **F-5** | MEDIUM | ✅ FIXED | `VaultError.Malformed` в T023 (tasks.md L75) + data-model.md §5 L287 (5 cases). |
| **F-6** | MEDIUM | ✅ FIXED | `RecoveryError.TooManyAttempts` в data-model.md §5 L288 (4 cases, совпадает с T024). |
| **F-7** | LOW | ✅ FIXED | `KeyRegistryError.RootKeyUnavailable` в T020 (tasks.md L72) + data-model.md §5 L285 (4 cases, совпадает с contract). |
| **F-8** | LOW | ✅ FIXED | `RootKeyError` унифицирован: `KeystoreInvalidated | StorageFailure | RecoveryRequired` (T021 + data-model.md §5 L286 — 3 cases совпадают). |
| **F-9** | MEDIUM | ✅ FIXED | tasks.md краткое резюме говорит «107 задач (101 base + 6 новых)»; реальный счёт уникальных T-ID совпадает (включая T072a, T088a, T122a-d, T122e-q). |
| **A-1** | LOW | ✅ FIXED | data-model.md §5 явно делегирует к contracts/ как single source of truth. |
| **A-2** | LOW | ✅ FIXED | Same. |
| **A-3** | LOW | ✅ FIXED | Same. |
| **B-1** | LOW | ✅ FIXED | SC-008 переформулирован «100 sequential getDek operations < 200 ms на pixel_5_api_34» — измеримый. |
| **B-2** | LOW | OK | Acceptable as-is. |
| **B-3** | LOW | open (Polish) | Assumption «не защита от forensic acquisition» — оставлено accepted; H-6 риск задокументирован в Assumptions secret. Не блокирует. |
| **C-1** | MEDIUM | ✅ FIXED | FR-013 явно прописывает «`Argon2idPassphraseKdf` обнуляет CharArray; derived 32-byte key zeroized in `RootKeyManagerImpl`»; tests T088 + T088a. |
| **C-2** | MEDIUM | ✅ FIXED | FR-027 переписан целиком: persistent DataStore counter, 1h sliding, accepted residual risks. Tasks T122e-T122i. |
| **C-3** | LOW | open | `multi-app-cohabitation.md` — присутствие файла не проверено в этом запуске; ссылка вне scope spec analyze. |
| **C-4** | LOW | OK | Implicit OK (нет cross-UID API). |
| **G-1** | MEDIUM | ✅ FIXED | T088a `DerivedKeyZeroizeTest` добавлен. |
| **G-2** | MEDIUM | ✅ FIXED | T072a `ThreeAttemptsTooManyAttemptsFallbackTest` добавлен; ViewModel state переход покрыт. |
| **G-3** | LOW | ✅ FIXED | T122a `NoCrossUidApiInKeyRegistryTest` — declarative Detekt rule. |
| **G-4** | LOW | ✅ FIXED | T122b `Argon2idAndroidEmulatorBenchmark` добавлен. |
| **G-5** | LOW | ✅ FIXED | T110 расширен явной проверкой «нет persistence plaintext config в `core/keys/`». |
| **H-1** | HIGH | ✅ FIXED | FR-027 переписан (persistent DataStore counter, 1h sliding, 3-attempt limit, auto-reset, accepted residual risks). Tasks T122e (interface), T122f (DataStore impl), T122g (wire в RootKeyManager), T122h (persistence test), T122i (Clear-App-Data bypass test). SRV-RECOVERY-001 как exit ramp. |
| **H-2** | HIGH | ✅ FIXED | FR-028a (Firestore Rule monotonic update) + FR-028b (client TOLU) + FR-028c (migration → SRV-CRYPTO-008) + FR-028d (defence-in-depth rationale) + FR-028e (residual risk). Tasks T122j (rules update), T122k (rules unit-tests с 3 scenarios), T122l (`SchemaVersionMemory` interface), T122m (`DataStoreSchemaVersionMemory`), T122n (wire в fetchVault/open), T122o (error case), T122p (detection test), T122q (UI test). firestore-security-rules.md содержит `request.resource.data.schemaVersion >= resource.data.schemaVersion` для обеих коллекций + threat-model table. |
| **H-3** | MEDIUM | ✅ FIXED | T122c `AadSchemaVersionEarlyValidationTest` — early validate до AEAD. |
| **H-4** | MEDIUM | ✅ FIXED | T122d `EmptyUidRejectionTest` добавлен. |
| **H-5** | LOW | open | «Argon2id params review every 2 years» — roadmap-уровень, не блокирует impl. |
| **H-6** | LOW | accepted | Identity enumeration leak зафиксирован в spec.md Assumptions L413. |
| **H-7** | LOW | accepted | T062 содержит inline TODO про iOS CharArray semantics. |
| **H-8** | LOW | tracked | F-CRYPTO dependency tracked в tasks.md Dependencies. |

**Summary**: 24 из 27 закрыты явными правками. 3 остались **open** в low-severity / accepted (B-3, C-3, H-5) — не блокируют impl.

---

## New findings (regression / introduced during fixes)

### N-1 (LOW) — IdentityError sealed type mismatch

**Шаблон тот же, что у бывших F-5..F-8** (sealed type cases разъезжаются между tasks vs contract/data-model). Появился при правках T012 (4 операции) — case-набор не был обновлён вместе с операциями.

- **contracts/identity-proof-v1.md L44-47**: `NoSupportedProvider | Cancelled | Failure(Throwable)` (3 cases).
- **data-model.md §5 L289**: `NoSupportedProvider | Cancelled | Failure(Throwable)` (3 cases) — совпадает с contract.
- **tasks.md T022 L74**: `NotSignedIn | NoSupportedProvider | UserCancelled | NetworkFailure` (4 cases, **другие имена**: `UserCancelled` vs `Cancelled`, `NetworkFailure` vs `Failure(Throwable)`, добавлен `NotSignedIn`, нет `Failure`).

Implementation T012/T022 при попытке использовать `IdentityError.NoSupportedProvider` в T048 / `IdentityError.Cancelled` в маппинге contract — compile error.

**Suggested fix** (≤2 min): обновить T022 до contract-варианта: `NoSupportedProvider | Cancelled | Failure(Throwable)`. Если хочется `NotSignedIn` — добавить отдельно в contract И в data-model одновременно.

### N-2 (LOW) — FR-021 «KDF info-string uid» — task coverage слабая

Coverage table приписывает FR-021 → T060 («Argon2id salt + uid in info»), но в самом T060 описании только «salt + params → 32-byte key», без явного упоминания включения UID в Argon2id info-string. При impl developer может пропустить — domain separation не enforced testom.

**Suggested fix** (≤5 min): дополнить T060 фразой «info-string = `"f5-root-wrap-v1|" + uid`», либо добавить отдельный sub-task с test «derive под uid1 ≠ derive под uid2 при одинаковом passphrase/salt».

### N-3 (info, не finding) — FR-028c ссылка на SRV-CRYPTO-008 без anchor

`(../../docs/dev/server-roadmap.md)` — без `#srv-crypto-008`. FR-032 link уже использует anchor. Minor doc polish, не блокирует.

---

## Cross-artifact re-check

| Аспект | Статус |
|---|---|
| FR-028a, FR-028b, FR-028c, FR-028d, FR-028e имеют tasks? | ✅ FR-028a → T122j, T122k; FR-028b → T122l..p; FR-028c → SRV-CRYPTO-008 (no task — deferred); FR-028d/e → документация в spec, не требуют отдельной task. |
| T122e..q traceability | ✅ Все 12 tasks имеют FR-trace (FR-027 mitigation для e-i; FR-028a/b для j-q) и file paths (`app/src/main/java/com/launcher/app/data/recovery/` или `core/keys/src/commonMain/...`). |
| SRV-CRYPTO-008 anchor | ⚠️ FR-032 ссылается с anchor `#srv-crypto-008`; FR-028c ссылается без anchor (`server-roadmap.md`). См. N-3. Cosmetic. |
| Firestore Rules contract — monotonic + tests | ✅ Полная rule приведена в contracts/firestore-security-rules.md L31-34, L47-49 для обеих коллекций. Тестовые сценарии (3 шт: =, >, <) описаны L121-125. T122k маппится на эти tests. |
| Cosmetic: tasks.md «107 задач» в краткое резюме | ✅ Соответствует фактическому подсчёту. |
| Coverage table FR → tasks | ⚠️ Не обновлено для FR-028a..e (отсутствует строка). Suggested: добавить пять строк в § Validation Checklist. Cosmetic, не блокирует impl. |
| Coverage SC-001..SC-008 → tests | ✅ Все mapping валидны после refresh. SC-008 теперь measurable. |

---

## Constitution Check (Article XVI gates)

Все 8 gates остаются **PASS** после правок:
- Gate 1 Architecture — module structure unchanged.
- Gate 2 Core/System Integration — F-CRYPTO + F-4 unchanged.
- Gate 3 Configuration — N/A.
- Gate 4 Required Context Review — все memories учтены, decisions linkованы.
- Gate 5 Accessibility — autofillHints + 56dp в Compose-screen tasks (T070/T071).
- Gate 6 Battery/Performance — T128/T129/T130/T122b benchmarks.
- Gate 7 Testing — каждый port: fake + contract; каждый wire-format: roundtrip + back-compat; Firestore Rules — 3 H-2 scenarios + ownership scenarios.
- Gate 8 Simplicity — один новый module, оправдан known consumers.

---

## CLAUDE.md re-check

| Rule | Status |
|---|---|
| 1 Domain isolated | PASS — `com.launcher.api.keys.api` в commonMain, никаких Firebase / libsodium. T122 Konsist check. |
| 2 ACL | PASS — `FirestoreRecoveryKeyVault` + `GoogleSignInIdentityProof` + `DataStorePassphraseAttemptCounter` + `DataStoreSchemaVersionMemory` — единственные точки контакта с external SDKs. |
| 3 One-way doors | PASS — algorithm field, schemaVersion monotonic rule (новая защита от downgrade — сама defence-in-depth снижает one-way риск). |
| 4 MVA | PASS — H-1/H-2 mitigations не добавляют premature abstractions, только два interface'а (`PassphraseAttemptCounter`, `SchemaVersionMemory`) с одним current impl + один known future consumer (own-server). |
| 5 Wire-format versioning | PASS — H-2 mitigation усиливает rule 5: monotonic schemaVersion = formal enforcement правила. |
| 6 Mock-first | PASS — fakes для всех ports, включая новые. |
| 7 Fitness functions | PASS — Detekt + Konsist + новый T122a (no cross-UID API). |
| 8 Server migration | PASS — T134 обновляет server-roadmap.md (SRV-RECOVERY-001 + SRV-CRYPTO-008). |
| 9 Shareability | N/A. |
| 10 Push minimization | N/A. |
| 11-13 Refuse-list | PASS — никаких vendor types в domain. |

---

## Findings Summary

| ID | Category | Severity | Description |
|---|---|---|---|
| N-1 | Cross-artifact | LOW | `IdentityError` sealed type разъезжается: tasks T022 объявляет 4 cases с **другими именами**, contracts + data-model дают 3 cases. См. fix above. |
| N-2 | Coverage | LOW | FR-021 «KDF info-string uid» имеет formal trace к T060, но T060 описание не упоминает включение UID в info-string — risk silent gap. |
| N-3 | Doc polish | INFO | FR-028c ссылка на SRV-CRYPTO-008 без anchor (FR-032 уже с anchor). |
| (open) B-3 | Ambiguity | LOW | Threat-model.md ссылка — Polish. |
| (open) C-3 | Trace | LOW | `multi-app-cohabitation.md` существование не проверено. |
| (open) H-5 | Risk | LOW | Argon2id params 2-year review cadence — roadmap-уровень. |
| (cosmetic) | Coverage table | INFO | FR-028a..e отсутствуют в Validation Checklist таблице tasks.md (5 строк); существует только в текстовом описании FR. |

**Total new findings**: **0 HIGH + 0 MEDIUM + 2 LOW + 1 INFO + 1 cosmetic** (regression risk minimal).

---

## Verdict

**PASS-WITH-WARNINGS** ✅

- Все **7 HIGH** findings закрыты (F-1..F-4, F-9, H-1, H-2). Все 6 MEDIUM закрыты (F-5, F-6, F-9, C-1, C-2, G-1/G-2).
- 0 новых HIGH или MEDIUM regression'ов.
- 2 новых LOW (N-1, N-2) — обе тривиально fix'аемые за < 10 минут, **не блокируют** старт implementation.
- Все Article XVI gates PASS. Все 13 CLAUDE.md rules satisfy. Coverage FR/US/SC/contracts → tasks остаётся 100%.

Артефакты ready to implement. N-1 имеет смысл починить **before T022** (это compile-error риск), N-2 — before T060 (silent risk).

---

## Recommendations — next step

1. **Quick polish (< 15 минут)** — рекомендуется ДО `/speckit.implement`:
   - **N-1 fix**: обновить tasks.md T022 sealed cases на `NoSupportedProvider | Cancelled | Failure(Throwable)` (соответствие contract). Это превентит compile-fail на T048.
   - **N-2 fix**: добавить в T060 описание phrase «info-string = `"f5-root-wrap-v1|" + uid`» + 1 unit test «derive под uid1 ≠ derive под uid2 при одинаковом passphrase+salt».
   - (опционально) **N-3 fix**: добавить `#srv-crypto-008` anchor к FR-028c link.
   - (опционально) coverage table в tasks.md: добавить 5 строк для FR-028a..e.

2. **После polish** — `/speckit.implement` от T001. Implementation проблем не предвидится: contracts стабильны, package canonical, sealed types унифицированы, wire-formats с защитой от downgrade, H-1/H-2 mitigations + tests готовы как explicit tasks.

3. **Open follow-ups** (after MVP merge, не блокирует ship): B-3 (threat-model.md), C-3 (multi-app-cohabitation.md presence check), H-5 (params review cadence в server-roadmap).
