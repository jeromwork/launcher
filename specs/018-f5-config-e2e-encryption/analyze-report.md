# Analyze Report: F-5 spec 018

**Generated**: 2026-06-19
**Mode**: Pre-implementation audit (read-only)
**Artifacts analyzed**: spec.md (462 строки), plan.md (164), research.md (214), data-model.md (334), quickstart.md (186), contracts/ (5 файлов, 660 строк), tasks.md (438 строк, **101 task**), checklists/requirements.md (75)

---

## Executive Summary

**Verdict**: **NEEDS-FIXES**

Артефакты технически богаты и concept-wise согласованы — clarify был тщательный, threat model явная, recovery flow продуман end-to-end. Все 8 конституционных gates продолжают проходить, CLAUDE.md правила 1, 2, 5, 6 явно соблюдаются (port-based architecture, schemaVersion везде, fake adapters first). Однако обнаружены **5 cross-artifact inconsistencies (Java-package names, сигнатуры port'ов, nullable types)** и **3 несоответствия в task counting**, которые легко исправить за 30-60 минут и которые иначе превратятся в реальный merge-conflict noise на implementation phase. Risk hotspots по безопасности (Argon2id params, AAD binding, passphrase hygiene) покрыты тестами; remaining gaps — attempt counter persistence, schema-version downgrade protection, recovery vault enumeration leak.

---

## Detection Pass Results

### A. Duplication

- **A-1 (LOW)**: `KeyRegistryError` декларируется одновременно в contracts/key-registry-v1.md (включая `RootKeyUnavailable`) и data-model.md §5 (без `RootKeyUnavailable`). Дублирующая декларация с расхождением.
- **A-2 (LOW)**: `VaultError` дублирована в data-model.md §5 (4 кейса) и contracts/recovery-vault-v1.md (5 кейсов, добавлен `Malformed`). Single source of truth не назначен.
- **A-3 (LOW)**: `RecoveryError` дублирована: tasks T024 объявляет `WrongPassphrase | MalformedVault | NoVaultPresent | TooManyAttempts` (4 кейса), data-model.md §5 — 3 кейса (без `TooManyAttempts`).

### B. Ambiguity

- **B-1 (LOW)**: SC-008 формулирован как «100+ DEK records без degradation» — слово «degradation» не measurable. T104 трактует как «all readable» — это не perf-критерий, что более reasonable. SC-008 надо переформулировать в «100 DEKs registered + sequentially retrieved, total time < N ms».
- **B-2 (LOW)**: SC-002 указывает «эмуляторе Pixel 5 API 34» — это конкретно, ОК; но «mid-tier Android» в research R-1 без конкретных моделей в SC. Acceptable.
- **B-3 (LOW)**: «accepted risk» в spec assumption «не защита от forensic acquisition» — без явного criterion, что именно accepted. Достаточно для MVP, но в Polish phase надо ссылку на threat-model doc.

Никаких `[NEEDS CLARIFICATION]`, `TODO без trace`, `???` или `<placeholder>` не найдено.

### C. Underspecification

- **C-1 (MEDIUM)**: FR-013 «passphrase обнуляется» не указывает **где** zeroize — в `Argon2idPassphraseKdf` или в caller? T088 тестирует «CharArray заполнен пробелами», но без указания ответственности. Возможна dropped responsibility.
- **C-2 (MEDIUM)**: FR-027 говорит «при 3 неправильных попытках offer fallback», но **где** хранится attempt counter? В RAM (теряется при kill app) или persistent (Firestore)? Tasks T065 «attempt counter» без указания storage. Под-coverage.
- **C-3 (LOW)**: spec.md FR-024a Owner decision ссылается на `docs/product/future/multi-app-cohabitation.md` — этот файл существует? Trace не проверена, но за рамками spec анализа.
- **C-4 (LOW)**: FR-031a «strict isolation» — не указано, **что происходит**, если код пытается cross-UID операцию. Сейчас implicit — нет таких операций. Acceptable.

### D. Constitution Alignment

| Article | Status | Notes |
|---|---|---|
| I (Spec-First) | PASS | spec.md создан до plan.md, документирован путь от user description. |
| II (Domain Vocabulary) | PASS | Все термины (RootKey, KeyRegistry, DEK, vault) в data-model.md. |
| III (Architecture / KMP layering) | PASS | core/keys/commonMain — только domain ports. App-слой owns Firebase. |
| IV (Core/System Integration) | PASS | Re-use F-CRYPTO + F-4, без новых системных взаимодействий. |
| V (Modularization With Restraint) | PASS | Один новый module `core/keys/`, ~10 файлов, обоснован Gate 8. |
| VI (Action Architecture) | N/A | F-5 не вводит действий launcher'а. |
| VII (Profile-Driven / Configurable / Wire-format) | PASS | schemaVersion в обоих wire-formats с дня 1. |
| VIII (Accessibility / Senior-safe) | PASS | autofillHints, tap target ≥ 56dp в plan §Gate 5. |
| IX (Battery / Performance) | PASS | Argon2id только при setup/recovery, lazy-init libsodium. |
| X (Privacy & Telemetry) | PASS | Plaintext config — никогда в Firestore; AAD биндинг к UID. |
| XI (Simplicity / MVA) | PASS | `KeyRegistry` map оправдан known future consumers; нет premature abstractions. |
| XII (Testing) | PASS | Каждый port: fake + contract test; каждый wire-format: roundtrip + back-compat. |
| XIII (Observability) | PARTIAL | Нет требований к metrics/structured logs для recovery failures. Add observability в Polish? |
| XIV (Security & Privacy) | PASS | Central tenet, threat model explicit, MASVS audit запланирован в Polish (checklist-security). |
| XV (Documentation) | PASS | quickstart.md, OEM matrix, AI Affordance, Local Test Path присутствуют. |
| XVI (Constitution Check Gates) | PASS | Все 8 gates pass в plan.md, T131 re-runs гейты после impl. |
| XVII (Backend Substitution Readiness) | PASS | `RecoveryKeyVault` port; `OwnServerRecoveryKeyVault` future-spec упомянут в OEM matrix footer. |
| XVIII (AI Affordance) | PASS | Inline TODO + section в spec — AI client-side only. |
| XIX (Organic Question Budget) | PASS | 14 clarify Q зафиксированы — organic count, не padded к 5. |

### E. CLAUDE.md Engineering Rules

| Rule | Status | Notes |
|---|---|---|
| 1 (Domain isolated) | PASS | core/keys/commonMain зависит только от F-CRYPTO ports и AuthIdentity. Никаких Firebase / libsodium в domain. T122 — module-graph fitness function. |
| 2 (ACL) | PASS | GoogleSignInIdentityProof и FirestoreRecoveryKeyVault — единственные точки контакта с SDK. T121 — Detekt rule. |
| 3 (One-way doors) | PASS | Argon2id+XChaCha20 — wire-format `algorithm` поле = exit ramp; recovery vault format app-agnostic = exit ramp на broker pattern; FR-024a фиксирует single signing key как target. |
| 4 (MVA, not MVP) | PASS | KeyRegistry map оправдан 3 known future consumers; PassphrasePrompter port = single impl, но scenario 6 (SmsIdentityProof) явно показывает second impl; `wipe()` declared optional MVP. |
| 5 (Wire-format versioning) | PASS | `schemaVersion` в SealedConfig, RecoveryVaultBlob, WrappedDek с дня 1; back-compat tests T053, T081, T105; fixtures v1 в T054, T082, T106. |
| 6 (Mock-first) | PASS | FakeIdentityProof, FakeRecoveryKeyVault, FakeKeyRegistry в T025-T027. |
| 7 (Fitness functions) | PASS | T120, T121, T122 — Detekt + module-graph checks. |
| 8 (Server migration tracking) | PASS | T134 явно обновляет server-roadmap.md с SRV-RECOVERY-001 и SRV-CRYPTO-007. |
| 9 (Shareability-readiness) | N/A | F-5 — identity-bound infrastructure, не user-facing config. |
| 10 (Notification minimization) | N/A | F-5 не вводит push notifications. |
| 11 (Refuse-list 1-13) | PASS | Никаких vendor types в domain values, никаких DTO в domain returns, никаких UI→SDK direct calls. |
| 12 (Shareability — config) | N/A | См. rule 9. |
| 13 (Push w/o severity) | N/A | См. rule 10. |

### F. Cross-artifact Trace

**Корневые числа**:
- **FRs в spec**: 33 (FR-001..FR-033a, считая суффиксы `a`)
- **SCs**: 8 (SC-001..SC-008)
- **User Stories**: 4 (US1..US4)
- **Contracts**: 5 (key-registry, recovery-vault, sealed-config, identity-proof, firestore-security-rules)
- **Tasks**: фактически **101** (T001-T006, T010-T033, T040-T057, T060-T089, T100-T106, T110-T113, T120-T138). Spec.md/plan.md header утверждает «82 tasks»; tasks.md краткое резюме говорит «138 задач». **Все три числа разные** — см. F-9.

| Аспект | Покрытие |
|---|---|
| FRs с tasks | 33 / 33 (100%) per Validation Checklist (tasks.md §Coverage). Verified. |
| SCs с verification tasks | 8 / 8 (SC-001→T056+T125, SC-002→T128+T129, SC-003→T130, SC-004→T083+T086, SC-005→T087, SC-006→T102, SC-007→T126, SC-008→T104). |
| US с independent test task | 4 / 4 (US1→T056, US2→T086, US3→T101+T102, US4→T110+T112). |
| Contracts с contract test | 5 / 5 (T028-T032 + T125 для firestore-rules). |
| Data-model entities с tasks | 7 / 7 (AuthIdentity внешняя; RootKey→T041; WrappedDek→T018+T042; KeyRegistry→T010+T042; Passphrase→T060; RecoveryVaultBlob→T016+T060; SealedConfig→T015+T040). |
| Research decisions в tasks | 7 / 7 (R-1→T060+T129, R-2→T041, R-3→T123+T125, R-4→T070+T071, R-5→встроен в plan, R-6→T089+T070, R-7→T041+T102). |

**Cross-artifact mismatches** (находки):

- **F-1 (HIGH) — package name inconsistency**: spec.md FR-001 объявляет пакет `family.keys.api`. plan.md Project Structure показывает `src/commonMain/kotlin/com/launcher/keys/api/`. tasks.md T001/T010-T014 используют `family/keys/api/`. contracts/*.md заявляют `package com.launcher.keys.api`. **Четыре варианта (`family.keys.api`, `com.launcher.keys.api`, `com.launcher.keys`, `launcher.keys`)** в разных артефактах. Implementation начнёт с одного из вариантов и придётся переименовать остальные при первом code review.

- **F-2 (HIGH) — IdentityProof signature mismatch**: contract identity-proof-v1.md объявляет 4 операции (`currentIdentity`, `identityFlow: Flow<...>`, `requestSignIn`, `signOut`). tasks.md T012 объявляет только 2 (`currentIdentity` + `requestSignIn`). Spec FR-006 совпадает с tasks (2 операции), но игнорирует `identityFlow` и `signOut`, без которых `RootKeyManager` не сможет среагировать на sign-out для wipe ключей в будущем.

- **F-3 (HIGH) — RecoveryKeyVault.fetchVault return type mismatch**: contract recovery-vault-v1.md — `fetchVault(uid: String): Outcome<RecoveryVaultBlob, VaultError>` (`VaultError.NotFound` для отсутствия). tasks.md T013 — `fetchVault(uid): Outcome<RecoveryVaultBlob?, VaultError>` (nullable). Это разные API; nullable vs sealed-error — выбор для domain.

- **F-4 (HIGH) — ConfigCipher.seal signature mismatch**: contract sealed-config-v1.md — `seal(configBytes: ByteArray, uid: String)`. tasks.md T014 — `seal(config: ConfigDocument): Outcome<SealedConfig, CryptoError>` (без uid, с `ConfigDocument` type, который не определён в F-5). Реальная impl задача T040 говорит «AAD = uid || schemaVersion», что не сходится с T014 сигнатурой (uid не передаётся).

- **F-5 (MEDIUM) — VaultError shape mismatch**: contract объявляет 5 cases (`Unauthorized | NotFound | Network | Conflict | Malformed`). tasks.md T023 — 4 case (без `Malformed`). data-model.md §5 — 4 case (без `Malformed`). Если parse fails при чтении Firestore, какой error?

- **F-6 (MEDIUM) — RecoveryError shape mismatch**: tasks.md T024 = `WrongPassphrase | MalformedVault | NoVaultPresent | TooManyAttempts`. data-model.md §5 = `WrongPassphrase | MalformedVault | NoVaultPresent` (без TooManyAttempts). FR-027 явно требует UX отличать «3 неверных раза → fallback». Если `TooManyAttempts` не in sealed type, UI ViewModel будет считать вручную, разъезжаясь с domain.

- **F-7 (LOW) — KeyRegistryError mismatch**: contract = `NotFound | StorageFailure | UnknownDek | RootKeyUnavailable`. tasks.md T020 + data-model.md §5 — без `RootKeyUnavailable`. Без него impl T042 не сможет вернуть нужный error, если RootKey ещё не загружен.

- **F-8 (LOW) — RootKeyError mismatch**: tasks.md T021 = `StorageFailure | KeystoreUnavailable | RecoveryRequired`. data-model.md §5 = `KeystoreInvalidated | StorageFailure` (без `RecoveryRequired`!). T075 explicitly checks `getOrCreate` returns `RecoveryRequired`. Если sealed type без него — compile error.

- **F-9 (MEDIUM) — task count inconsistency**: spec.md header «tasks.md (82 tasks)», tasks.md краткое резюме «138 задач разбиты на 7 фаз», фактически — 101 задача в файле. Это cosmetic, но указывает на потерю синхронизации при доработках.

### G. Coverage Gaps

- **G-1 (MEDIUM)**: FR-013 (passphrase zeroize) → task T060 + T088. Не указано, что **derived key** (32 bytes из Argon2id) тоже должен zeroize'аться после wrap/unwrap. Только passphrase упомянут.
- **G-2 (MEDIUM)**: T065 «attempt counter» — нет соответствующего test task. SC-004 (FR-027) тестируется только «правильный/неправильный → ошибка», не «3 попытки → fallback».
- **G-3 (LOW)**: FR-031a «никаких cross-UID операций» — explicit verification отсутствует (T102 проверяет isolation как раз cross-UID, но не «отказ от попытки»).
- **G-4 (LOW)**: SC-002 perf target «<500ms на эмуляторе» — T129 benchmark «JVM с interactive params», JVM ≠ Android emulator (JNI overhead отсутствует). Реальная проверка на эмуляторе требует androidTest, которого нет.
- **G-5 (LOW)**: FR-018 «локальный app cache plaintext» — T111 проверяет `allowBackup=false`, но **где** в plan/tasks сказано, что cache layer хранит plaintext? Это implicit — T051 «передать plaintext дальше в app cache». Адекватно, но не testable.

Tasks без trace к FR/US: **0** (все tasks помечены либо setup/foundation/polish, либо [US#]).

### H. Risk Hotspots (security-critical)

- **H-1 (HIGH)** — **Attempt counter persistence**: spec FR-027 требует 3-attempt limit, но не указывает, persists ли counter через app restart. Атакующий с unlocked admin device может kill app и неограниченно brute-force passphrase через recovery flow. **Suggested fix**: persistent counter в DataStore + auto-reset через 1 час, или server-side rate-limit (отнести в SRV-RECOVERY-001).

- **H-2 (HIGH)** — **Schema-version downgrade attack**: Firestore Rules валидируют `schemaVersion >= 1`, но не запрещают write с `schemaVersion = 1`, когда клиент уже мигрировал на `schemaVersion = 2`. Атакующий с украденными credentials может откатить vault до устаревшего алгоритма. **Suggested fix**: client-side check «refuse to write schemaVersion < currentClientVersion».

- **H-3 (MEDIUM)** — **AAD authentication для open**: contract sealed-config-v1.md «open валидирует aad parse → check uid match», но если ciphertext был sealed с `aad={uid: X, schemaVersion: 1}` и подменён на `aad={uid: X, schemaVersion: 99}`, AEAD tag не совпадёт (это и есть auth) — тут ок. Но **client должен проверять `schemaVersion` ДО передачи в AEAD**, иначе бесконечная попытка decrypt с unsupported algorithm.

- **H-4 (MEDIUM)** — **RootKey wipe на Sign-In другим UID**: spec edge case «UID1 ключи изолированы» — но что если UID2 — атакующий, который через подмену Google account на устройстве хочет читать UID1 config? Изоляция через alias prefix R-7 — это **не** crypto-isolation, а namespace-isolation. Если код в `KeyRegistryImpl` ошибётся при формировании alias (пустой UID, race condition в `currentIdentity()`), может произойти cross-UID leak. **Suggested fix**: добавить тест «попытка getDek с пустым UID → fail».

- **H-5 (LOW)** — **Argon2id params 2026**: 64MB/3/1 — OWASP 2024 рекомендация. К 2030 году рекомендация скорее всего поднимется. Wire-format поддерживает (kdfParams в blob), но Polish phase должен включить «review Argon2id params» в roadmap каждые 2 года.

- **H-6 (LOW)** — **Identity enumeration через Firestore**: атакующий с Google account UID-A может fetchVault(UID-B) — Firestore Rules вернут permission-denied (не not-found). Это leak'ает «UID-B существует в системе» через timing. **Acceptable threat** для семейного app, но note worth.

- **H-7 (LOW)** — **PassphrasePrompter is suspending UI port**: T062 объявляет в `commonMain` `requestSetupPassphrase(): Outcome<CharArray, RecoveryError>`. CharArray в Kotlin/Native (iOS) имеет другие memory semantics — zeroize в `commonMain` может не сработать одинаково. iOS deferred, accepted now.

- **H-8 (LOW)** — **Argon2id implementation availability**: spec assumes «Argon2id available through libsodium (verified F-CRYPTO research)». F-CRYPTO еще InProgress (per tasks dependency). F-5 Phase 2 заблокирована F-CRYPTO progress — это известно.

---

## Checklist Results

### checklist-security (MASVS)

**Pass: 11 / 14**. Failures:
- ❌ Attempt counter persistence не определён (H-1, FR-027 underspec).
- ❌ Schema-version downgrade protection (H-2).
- ⚠️ Derived passphrase key zeroize не explicit (G-1).
- ✅ AEAD authentication для всех decrypts: T032, T040, T064.
- ✅ Identity-bound AAD: T057.
- ✅ Hardware-backed root key wrap: R-2, T041.
- ✅ Firestore Rules per-UID + shape validation: T123-T125.
- ✅ Passphrase plaintext-free pipeline: FR-013, T088.
- ✅ Clipboard sensitivity flag + auto-clear: T089, R-6.
- ✅ Brute-force defence via memory-hard KDF: R-1, FR-030.

### checklist-wire-format

**Pass: 9 / 9**. Все wire-formats (SealedConfig, RecoveryVaultBlob, WrappedDek):
- ✅ Имеют `schemaVersion: Int = 1` с дня 1.
- ✅ Имеют `algorithm: String` для миграции.
- ✅ Roundtrip tests: T052 (sealed), T080 (vault), T100 (registry).
- ✅ Backward-compat tests: T053, T081, T105.
- ✅ Fixture files: T054, T082, T106.
- ✅ Additive change rules задокументированы в contracts.
- ✅ Server-side schema validation в Firestore Rules для recovery-vault.

### checklist-backend-substitution

**Pass: 7 / 7**.
- ✅ `RecoveryKeyVault` — port в domain, Firestore — adapter в app-layer.
- ✅ `OwnServerRecoveryKeyVault` явно упомянут как future-adapter (SRV-RECOVERY-001).
- ✅ Никаких Firebase types в commonMain.
- ✅ Non-GMS fallback (`NoOpRecoveryKeyVault`) демонстрирует port-swap pattern.
- ✅ Server-roadmap.md update в T134.
- ✅ inline TODO в T013 и T134.
- ✅ ACL enforcement через T121 Detekt rule.

### checklist-failure-recovery

**Pass: 9 / 11**. Failures:
- ❌ Attempt counter behavior on app kill (H-1, G-2).
- ❌ Что происходит, если Argon2id derivation fails partway (out of memory на low-end device)? Не explicit.
- ✅ Wrong passphrase UX: T071, T083.
- ✅ Malformed vault: T084.
- ✅ Network failure (Firestore unreachable): VaultError.Network.
- ✅ Keystore invalidated после OS update: RootKeyError sealed.
- ✅ Non-GMS / no-internet path: T126.
- ✅ 3 wrong attempts fallback: T072 (UI).
- ✅ Recovery vault не найден: NoVaultPresent.
- ✅ User cancels Sign-In: IdentityError.Cancelled.

### checklist-tamper-resistance

**Pass: 5 / 7**. Failures:
- ❌ Schema-version downgrade (H-2).
- ❌ Attempt counter local-only — bypass через app data clear (H-1).
- ✅ Firestore Rules `request.auth.uid == uid` — server-validated.
- ✅ AEAD prevents config tampering.
- ✅ AAD prevents cross-identity replay.
- ✅ kdfSalt validated server-side (16 bytes).
- ✅ wrappedRootKey size capped.

### checklist-meta-minimization

**Pass: 8 / 8**.
- ✅ Один новый module, не три.
- ✅ Нет premature abstractions: каждый port имеет MVP impl + один или more declared future impls.
- ✅ `wipe()` опциональный MVP — explicit.
- ✅ Cross-app broker — НЕ реализуется, только TODO.
- ✅ Optional Firestore mirror для DEKs — НЕ MVP.
- ✅ Биометрия — отложено.
- ✅ Passphrase change — отложено.
- ✅ Algorithm migration — отложено.

### checklist-requirements-quality

**Pass: 12 / 14** (re-check after clarify). Failures:
- ❌ FR-027 attempt counter без storage spec (C-2).
- ❌ FR-013 zeroize ответственность размыта (C-1).
- ✅ Все остальные FRs measurable.
- ✅ Tech-agnostic (Argon2id/XChaCha20 — стандарты, не вендор-bound).
- ✅ 33 FR покрыты tasks 100%.
- ✅ Все US имеют independent test.
- ✅ Edge cases в spec явные.

---

## Findings Summary

| ID | Category | Severity | Description | Suggested Fix |
|---|---|---|---|---|
| F-1 | Cross-artifact | HIGH | Package name inconsistency (`family.keys.api` vs `com.launcher.keys.api` vs `com.launcher.keys`) в spec / plan / tasks / contracts | Зафиксировать один canonical (рекомендую `com.launcher.keys.api` из contracts), patch spec.md FR-001 + tasks.md T001 + plan.md project structure |
| F-2 | Cross-artifact | HIGH | IdentityProof signature: contract 4 ops vs tasks/spec 2 ops (отсутствуют `identityFlow`, `signOut`) | Добавить `identityFlow` + `signOut` в FR-006 и в task T012; либо удалить из contract |
| F-3 | Cross-artifact | HIGH | RecoveryKeyVault.fetchVault: contract non-nullable + `VaultError.NotFound`, tasks `Outcome<RecoveryVaultBlob?, ...>` (nullable) | Принять contract-вариант (sealed-error, идиоматичнее), update T013 |
| F-4 | Cross-artifact | HIGH | ConfigCipher.seal: contract `(configBytes, uid)`, tasks `(config: ConfigDocument)` без uid | Принять contract; T014 update; T040 уже согласуется с contract по AAD |
| F-5 | Cross-artifact | MEDIUM | VaultError sealed type: 5 cases (contract) vs 4 (tasks + data-model) | Добавить `Malformed` в T023 + data-model §5 |
| F-6 | Cross-artifact | MEDIUM | RecoveryError: tasks включает `TooManyAttempts`, data-model — нет | Добавить `TooManyAttempts` в data-model §5 |
| F-7 | Cross-artifact | LOW | KeyRegistryError mismatch (`RootKeyUnavailable` только в contract) | Добавить в T020 + data-model §5 |
| F-8 | Cross-artifact | LOW | RootKeyError mismatch: tasks `RecoveryRequired`, data-model — нет (есть `KeystoreInvalidated`) | Объединить: `KeystoreInvalidated | StorageFailure | RecoveryRequired` |
| F-9 | Meta | MEDIUM | Task count: spec header «82», tasks summary «138», фактически 101 | Update spec.md header + tasks.md резюме до «101 task» |
| A-1 | Duplication | LOW | KeyRegistryError дублирован contracts/data-model | Single source — contracts; data-model ссылается |
| A-2 | Duplication | LOW | VaultError дублирован | Same |
| A-3 | Duplication | LOW | RecoveryError дублирован | Same |
| B-1 | Ambiguity | LOW | SC-008 «без degradation» — non-measurable | Уточнить «100 sequential getDek < 200 ms» |
| C-1 | Underspec | MEDIUM | FR-013 zeroize responsibility не named | Специфицировать «`Argon2idPassphraseKdf` zeroize input passphrase» |
| C-2 | Underspec | MEDIUM | FR-027 attempt counter storage не указан | Persistent counter в DataStore с UID-namespace + auto-reset 1h |
| G-1 | Coverage | MEDIUM | Derived key zeroize не покрыт тестом | Add test «после wrap/unwrap derivedKey ByteArray zeroized» |
| G-2 | Coverage | MEDIUM | FR-027 «3 попытки → fallback» нет teста | Add T-аналог тест «3 wrong attempts → TooManyAttempts → UI navigated to fallback» |
| G-3 | Coverage | LOW | FR-031a no-cross-UID-ops не verified | Implicit OK, можно declarative-test (compile-time, что в core/keys нет cross-UID API) |
| G-4 | Coverage | LOW | SC-002 на эмуляторе — T129 на JVM (нет JNI overhead) | Add androidTest perf benchmark |
| G-5 | Coverage | LOW | FR-018 plaintext-in-cache не testable | Add invariant test in T110 |
| H-1 | Risk | HIGH | Attempt counter persistence не определена → brute-force через app kill | Persistent counter; будущий server-side rate-limit (SRV-RECOVERY-001) |
| H-2 | Risk | HIGH | Schema-version downgrade не защищён | Client-side check + Firestore Rules `schemaVersion >= existingDoc.schemaVersion` |
| H-3 | Risk | MEDIUM | AAD schemaVersion check ДО AEAD | Add early validation в `open()` |
| H-4 | Risk | MEDIUM | Cross-UID alias formation — race условие на currentIdentity | Test «getDek с пустым UID → fail» |
| H-5 | Risk | LOW | Argon2id params review cadence | Roadmap-level note: review every 2 years |
| H-6 | Risk | LOW | Identity enumeration через Firestore permission-denied vs not-found timing | Accepted threat, document в threat-model |
| H-7 | Risk | LOW | iOS CharArray zeroize semantics — KMP gotcha | Accepted, iOS deferred |
| B-3 | Ambiguity | LOW | «accepted risk» в assumptions без doc | Polish: ссылка на threat-model.md |

**Total findings**: **27** (3 HIGH risk + 4 HIGH cross-artifact = 7 HIGH; 6 MEDIUM; 14 LOW).

---

## Constitution Re-check (Article XVI gates)

| Gate | Status | Notes after tasks generation |
|---|---|---|
| 1 — Architecture | **PASS** | Module structure unchanged from plan; commonMain stays clean. |
| 2 — Core / System Integration | **PASS** | F-CRYPTO + F-4 dependency unchanged. |
| 3 — Configuration | **PASS (N/A)** | Не user-facing config. |
| 4 — Required Context Review | **PASS** | Все memories + spec 016/017 + multi-app-cohabitation.md прочитаны. |
| 5 — Accessibility | **PASS** | autofillHints + 56dp tap target в T070/T071. Полная валидация — checklist-elderly-friendly в impl phase. |
| 6 — Battery / Performance | **PASS** | T128/T129/T130 benchmarks покрывают все perf SCs. |
| 7 — Testing | **PASS** | Каждый port: fake + contract test. Каждый wire-format: roundtrip + back-compat. Integration через Firestore Emulator. |
| 8 — Simplicity | **PASS** | Один новый module, оправдан known consumers. |

**Verdict**: все 8 gates PASS. Findings выше — implementation-detail inconsistencies, не gate violations.

---

## Recommendations

### Перед `/speckit.implement` поправить (HIGH severity)

1. **F-1**: Зафиксировать canonical package name (рекомендуется `com.launcher.keys.api`). Patch spec.md FR-001, plan.md project structure, tasks.md T001 + все port-declaring tasks.
2. **F-2, F-3, F-4**: Привести signature port'ов к единому виду. Source of truth — contracts/ (это formal contracts). Update tasks.md T012, T013, T014.
3. **F-5, F-6, F-7, F-8**: Sync sealed error types между contracts ↔ data-model.md §5 ↔ tasks T020-T024.
4. **H-1**: Specify attempt counter persistence в FR-027; add task для DataStore-backed counter.
5. **H-2**: Add client-side downgrade protection + Firestore Rules monotonic schemaVersion check.

### Можно начать impl параллельно с (LOW risk, не блокирует)

- T001-T006 (Setup phase) — module skeleton independently от inconsistencies.
- T015-T024 (wire-formats + error types) — после resolving F-5..F-8.
- T054, T082, T106 (fixtures) — independent работа.

### Open follow-ups (after MVP merge, не блокируют ship)

- B-1: SC-008 measurable refinement.
- G-4: Android emulator perf benchmark (vs JVM).
- H-5: Argon2id params periodic review entry в roadmap.
- Documentation: explicit threat-model.md (закрывает B-3, H-6).

### Recommended impl ordering

1. **Fix HIGH findings F-1..F-4** (30 min — single PR «spec consistency»).
2. **Fix MEDIUM F-5..F-9, C-1, C-2** (30 min — same PR).
3. **Address H-1, H-2** через 1-2 новых FR-ов или sub-tasks.
4. После — `/speckit.implement` от T001.

---

## Next Step

**Recommendation**: **Fix findings first**, затем `/speckit.implement`.

Конкретно: **семь HIGH findings (F-1..F-4 + H-1, H-2 + F-9)** — обязательны до старта impl, иначе первый же merge приведёт к 4 PR'ам "rename package" / "fix port signature". Это 30-60 минут работы в spec'е vs дни refactoring потом.

После HIGH-fixes — implement готов: Constitution Check PASS, coverage 100% по FR/US/SC/contracts, fitness functions встроены.
