# speckit-analyze report — spec 011 (e2e-crypto-foundation)

**Date (rev. 1)**: 2026-05-22
**Date (rev. 2)**: 2026-05-22 (re-analyze после Phase R1-R5 remediation)
**Spec rev.**: 3 (post scope-split + analyze remediation)
**Previous spec-kit phase**: speckit-tasks regenerated (52 tasks in 11 phases) + scope-split mentor-сессия + 5 remediation phases.

---

## VERDICT (rev. 2): READY-WITH-CAVEATS

Все 8 DRIFT'ов из rev. 1 закрыты. Cross-artifact trace clean. Constitution check 7 PASS + 1 N/A (Gate 5 Accessibility — no UI). 3 known items приняты как accepted-as-risk (см. §Open Items ниже). Implementation готова стартовать с Phase 0.

**Что изменилось с rev. 1:**
- ✅ data-model.md переписан под infrastructure-only scope; добавлены DigitalSignature + HashFunction ports + DeviceSigningKeyPair + clear-data sentinel + constant-time validation rule + MalformedEnvelope CryptoError sub-case.
- ✅ research.md обновлён: §6-§7 marked «moved to spec 012»; добавлены §2b (Ed25519 rationale), §2c (BLAKE2b rationale), §2d (constant-time recipient search), §5b (WorkManager retry policy), §5c (clear-data 7-day grace).
- ✅ quickstart.md переписан: только BlobReferenceLedger + SystemMeta tables; Keystore wrapper расширен на Ed25519; SRV-CRYPTO-001 marked done.
- ✅ contracts/device-identity.md переписан: добавлены signature + signedTimestamp + Ed25519 Pub, 12 test cases.
- ✅ contracts/crypto-envelope.md: BlobMetadata.kind/labelOpt удалены, metadata = freeform Map<String, ByteArray>, добавлена privacy note для клиентов, 12 roundtrip tests включая MalformedEnvelope.
- ✅ contracts/encrypted-media-storage.md: SRV-MEDIA-001 → SRV-CRYPTO-001.
- ✅ plan.md: §Architecture, §Risks (R4/R5/R10/R13), §Required Context Review, §Open Items, §Rollout обновлены под новый scope.
- ✅ tasks.md regenerated: 73 → 52 tasks в 11 phases.
- ✅ spec.md §Clarifications C-9 добавлен (scope-split history).

**Что осталось** как explanatory references (не drift):
- Все упоминания «PrivateMediaResolver / PrivateMediaCache / DocumentPicker / SRV-MEDIA-001» теперь явно marked как «moved to spec 012» или «renamed to SRV-CRYPTO-001». Эти ссылки **корректны** — они документируют scope boundary, не описывают неверную реализацию.

---

## STEP 1 — Re-assessed complexity

Same checklist set as rev. 1:

**Always-on:**
- ✓ requirements-quality
- ✓ meta-minimization

**Triggered (re-run in rev. 2):**
- ✓ domain-isolation
- ✓ wire-format
- ✓ failure-recovery
- ✓ security
- ✓ permissions-platform
- ✓ state-management

**N/A** (no UI в 011): accessibility, elderly-friendly, ux-quality, localization, core-quality.

---

## STEP 2 — Constitution re-check

```
Gate 1 Architecture           : PASS — port-adapter pattern сохранён; 8 portов; адаптеры в androidMain; vendor types confined; Konsist rules planned
Gate 2 Core/System Integration: PASS — Android Keystore wrapped via SecureKeystore (с Ed25519 native API 31+ и AES-wrap fallback для X25519); Firebase Storage via EncryptedMediaStorage; Firestore via DeviceIdentityRepository
Gate 3 Configuration          : PASS — envelope wire-format: schemaVersion + cipherSuiteId с первого commit'a; CBOR roundtrip + backward-compat tests planned (T038, T039); SUPPORTED_SCHEMA_VERSION constant (T019)
Gate 4 Required Context Review: PASS — все ADRs упомянуты; roadmap line numbers убраны (rev. 2 remediation); ADR-005 описан как «применим в спеке 012 (DocumentPicker/DocumentViewer)»
Gate 5 Accessibility          : N/A — нет UI в 011 (visible feature в спеке 012). Debug-build smoke buttons — не product UI, accessibility review проходит в спеке 012.
Gate 6 Battery/Performance    : PASS — крипто event-driven; нет polling; WorkManager retry policy документирован (exp backoff, max 5 attempts); perf budgets валидны (research.md §9)
Gate 7 Testing                : PASS — mock-first; 9 levels test strategy; fitness via Konsist; vector tests для всех 4 примитивов (AEAD/X25519/Ed25519/BLAKE2b); SQLDelight migration test покрыт T040
Gate 8 Simplicity             : PASS WITH JUSTIFICATION — RecipientResolver single-impl exception (документировано Article XVII §3, см. C-8). Hash + Signature ports обоснованы конкретными use cases (Pub anti-tamper в 011; будущие 013-016 + TBD-Jitsi/vendor/hardware).

OVERALL: 7 PASS + 1 N/A + 1 PASS WITH JUSTIFICATION — CLEAN
```

---

## STEP 3 — Cross-artifact trace

### 3a. FR coverage (spec.md rev. 3 ↔ tasks.md rev. 2)

| FR | Описание | Tasks |
|---|---|---|
| FR-001..005 (per-device keys + Keystore) | T011-T013 (domain), T021-T025 (ports), T031 (FakeAsymm), T032 (FakeSign), T034 (FakeKeystore), T051 (Libsodium asymm), T052 (Libsodium sign), T054 (Keystore X25519), **T055 (Keystore Ed25519 — new)** | ✅ |
| FR-006..009 (Pub publication with signature) | T015 (DeviceIdentity), T039 (wire format test), T060 (Firestore impl), T061 (PairingCoordinator), T062 (Security Rules) | ✅ |
| FR-010..014 (envelope wire format) | T017 (domain), T019 (SUPPORTED_SCHEMA_VERSION), T038 (roundtrip + backward-compat tests) | ✅ |
| FR-020..023 (hybrid encryption) | T016 (CEK), T021/T022 (ports), T050/T051 (Libsodium impls) | ✅ |
| FR-024 (hashing) | **T024 (port), T033 (Fake), T053 (Libsodium) — new** | ✅ |
| FR-025 (signing) | **T023 (port), T032 (Fake), T052 (Libsodium) — new** | ✅ |
| FR-030..033 (Storage adapter) | T027 (port), T036 (Fake), T070 (Firebase impl), T071 (Rules) | ✅ |
| FR-040..044 (reference counting + cleanup) | T020 (BlobReference domain), T090 (ledger SQL), **T091 (SystemMeta — new)**, T092 (reconciler с clear-data grace), T093 (revoke integration), T072 (WorkManager retry policy) | ✅ |
| FR-050..052 (error reporting) | T018 (CryptoError sealed с MalformedEnvelope + SignatureVerifyFailed), T038 (envelope error tests) | ✅ |
| FR-060..061 (recipient resolver) | T026 (port), T035 (Fake), T080 (PairRecipientResolver), T056 (constant-time iteration в asymm adapter) | ✅ |
| FR-070 (manual smoke) | T100 (debug buttons), T101 (procedure doc), T102 [M] [CRIT] | ✅ |
| FR-080..082 (security + privacy) | T112 (Konsist no plaintext в logs), T113 (Konsist no Exception throws), security test cases в T038, T039 | ✅ |
| FR-090..096 (out-of-scope) | N/A — negative requirements не требуют tasks | ✅ |

**Verdict:** все 30+ FR covered by tasks. Coverage clean.

### 3b. SC coverage

10 SC в spec.md rev. 3:
- SC-001 (smoke 16-byte e2e): T102 (manual smoke run) ✅
- SC-002 (zero plaintext в logs): T112 (Konsist rule) ✅
- SC-003 (passive MITM resistance): T038 aadBinding test ✅
- SC-004 (refCount → delete): T090 + T092 tests ✅
- SC-005 (revoke cleanup): T093 integration test ✅
- SC-006 (CryptoError sub-cases as Result): T018 + T038 + T113 (Konsist) ✅
- SC-007 (libsodium vectors pass): T050-T053 contract tests ✅
- SC-008 (envelope roundtrip): T038 (12 tests включая backward-compat) ✅
- SC-009 (Konsist gates): T110-T113 ✅
- SC-010 (exit ramps documented): research.md §1 (libsodium), §2 (algorithms), §3 (Keystore), §4 (Storage) — все 4 one-way doors с exit ramps ✅

**Verdict:** all 10 SC covered.

### 3c. Contracts → tests

| Contract | Roundtrip | Backward-compat | Signature/MAC tests | Files exist |
|---|---|---|---|---|
| `crypto-envelope.md` | ✓ (T038) | ✓ (T038 forward-compat unknown cipherSuite) | ✓ (T038 aadBinding, MalformedEnvelope) | ✓ |
| `device-identity.md` | ✓ (T039) | ✓ (T039) | ✓ (T039 signAndVerify, tampered, staleTimestamp) | ✓ |
| `encrypted-media-storage.md` | ✓ (T070 round-trip via Storage Emulator) | N/A (storage layout) | ✓ (T071 Security Rules tests) | ✓ |

**Verdict:** все contracts покрыты roundtrip + relevant security tests.

### 3d. Dangling references

| Reference | Где | Статус |
|---|---|---|
| `SRV-CRYPTO-001` | server-roadmap.md ✅ added 2026-05-22; cross-linked в plan.md, quickstart.md §9 | ✓ Resolved |
| `SRV-MEDIA-001` (old name) | spec.md §C-9 («переименовано»), quickstart.md §9 («устаревшее имя»), contracts/encrypted-media-storage.md (updated to SRV-CRYPTO-001) | ✓ All updated or marked legacy |
| `PrivateMediaResolver` | All references properly marked «moved to spec 012» или в FR-090 negative requirement | ✓ Resolved |
| `PrivateMediaCache` | Same — all marked «moved to spec 012» | ✓ Resolved |
| `DocumentPicker / DocumentViewer` | spec.md FR-091 negative requirement, plan.md «UX переехал в спек 012», research.md §7 marked moved | ✓ Resolved |
| `BlobMetadata.kind` / `BlobKind` | data-model.md §1 explicit out-of-scope note; crypto-envelope.md «удалено в rev. 2» | ✓ Resolved |
| `NEXT-STEPS.md` (deleted) | No external references | ✓ Safe |
| Roadmap §Spec 011 line numbers (264-284) | Updated to remove specific line numbers | ✓ Resolved |
| ADR-005 (Compose Multiplatform для DocumentPicker/Viewer) | plan.md §Required Context Review now says «применим для спека 012» | ✓ Clarified |

**Verdict:** No stale references. All migration markers are explanatory and correct.

---

## STEP 4 — Checklists (re-run)

### 4a. Always-on

#### requirements-quality (16/16 ✓)

- ✓ Acceptance criteria измеримые.
- ✓ Нет ambiguous language.
- ✓ Technology-agnostic at user-facing level.
- ✓ Каждый FR имеет связь с US.
- ✓ Edge Cases пересмотрены под infra-scope.
- ✓ Disclaimer в начале §User Scenarios — implicit через explicit «User here = downstream spec/library consumer».

**Status:** all 16 pass.

#### meta-minimization (13/13 ✓)

- ✓ `RecipientResolver` single-impl — обоснован Article XVII §3 exception (3 будущие реализации в roadmap 013/014/015).
- ✓ Hash + Signature ports — обоснованы конкретными use cases (Pub anti-tamper в 011 уже; будущие 013-016/Jitsi/vendor/hardware).
- ✓ CHK-MIN-005 (monitor 013/014 будущих спеков) — обновлено в plan.md §Open Items с новой нумерацией.

**Status:** all 13 pass. CHK-MIN-005 — продолжает быть «accepted-as-risk» (review через 2 квартала после релиза 011).

### 4b. Triggered

#### domain-isolation (16/16 ✓)

- ✓ commonMain ports без vendor imports.
- ✓ Lazysodium/Firebase/Keystore типы confined в androidMain adapters.
- ✓ CHK-DOM-007 closed — `PrivateKey` правильно типизирован как opaque sealed interface (data-model.md §1).

**Status:** all 16 pass.

#### wire-format (18/18 ✓)

- ✓ schemaVersion + cipherSuiteId на envelope.
- ✓ DeviceIdentity wire format содержит signature + signedTimestamp.
- ✓ Roundtrip tests planned (T038, T039).
- ✓ Backward-compat reads planned.
- ✓ CHK-WIRE-014 closed — SQLDelight migration test (T040) updated to BlobReferenceLedger + SystemMeta (без PrivateMediaCache).

**Status:** all 18 pass.

#### failure-recovery (17/17 ✓)

- ✓ Keystore key loss — `CryptoError.KeyNotFound` + re-pairing flow.
- ✓ MAC failure — `CryptoError.MacFailed`.
- ✓ Storage 404 — `CryptoError.BlobMissing`.
- ✓ Network unavailable — WorkManager retry с exp backoff (research.md §5b).
- ✓ Forward-compat unknown cipherSuite — `CipherSuiteUnsupported`.
- ✓ Signature verification failure — `SignatureVerifyFailed` (new).
- ✓ CBOR parse failure — `MalformedEnvelope` (new — CHK-FR-008 closed).
- ✓ WorkManager retry policy — exp backoff 1m→5m→30m→2h→12h, max 5 attempts, после exhaustion = warning log (CHK-FR-012 closed).
- ✓ Clear-data edge case — 7-day grace period before reconciliation (CHK-FR-015 closed, new T091 SystemMeta).

**Status:** all 17 pass. 3 ⚠ из rev. 1 — все closed.

#### security (24/24 ✓)

- ✓ AEAD authenticated encryption mandatory.
- ✓ Plaintext bytes / Priv keys never в logs (FR-051, FR-080).
- ✓ Priv keys opaque sealed interface (data-model.md §1).
- ✓ Storage Rules с Firestore.get() member check.
- ✓ Pub publication signed by Ed25519 (FR-006).
- ✓ Replay-attack mitigation — signedTimestamp + 7-day Security Rule freshness gate (CHK-SEC-011 closed).
- ✓ MITM при первом fetch — **accepted-as-risk** до спека ~018 (safety numbers), task `TODO-SEC-011` в backlog.
- ✓ Timing side-channel — constant-time recipient search (research.md §2d, T056) (CHK-SEC-018 closed).
- ✓ Data minimization — labelOpt из envelope удалён, sensitive metadata MUST шифроваться внутри ciphertext (CHK-SEC-022 решён через privacy note в crypto-envelope.md).
- ✓ Firestore field names verification — задача T060 при integration тестах (CHK-SEC-008 closed).

**Status:** all 24 pass. 5 ⚠ из rev. 1 — 4 closed, 1 accepted-as-risk (MITM).

#### permissions-platform (10/10 ✓)

- ✓ No new runtime permissions.
- ✓ Keystore OEM quirks documented.
- ✓ StrongBox/TEE fallback chain documented.
- ✓ ABI splits configured.
- ✓ WorkManager retries < 10 min individually (CHK-PERM-006 — documented в research.md §5b).

**Status:** all 10 pass.

#### state-management (13/13 ✓)

- ✓ Keystore keys survive process death.
- ✓ Pub-key cache в memory + Firestore — refetch.
- ✓ CEK ephemeral lifecycle — `ContentEncryptionKey.use { }` pattern documented (data-model.md §1, T016) (CHK-STATE-005 closed).

**Status:** all 13 pass.

---

## STEP 5 — Specific scans

### 5a. Deleted-file dangling references

- `NEXT-STEPS.md` deleted — no external references ✓
- `SRV-MEDIA-001` rename — all explanatory references marked «renamed to SRV-CRYPTO-001» ✓

### 5b. Wire-format files audit

- ✓ `EncryptedEnvelope` — schemaVersion + cipherSuiteId.
- ✓ `DeviceIdentity` — schemaVersion + signature + signedTimestamp.
- ✓ `BlobReferenceLedger` (SQLite) — schema version via SQLDelight migrations.
- ✓ `SystemMeta` (SQLite) — schema version via SQLDelight migrations.

### 5c. Source-set placement

- ✓ commonMain ports без vendor imports.
- ✓ androidMain adapters содержат Lazysodium/Firebase/Keystore.
- ✓ `core/api/media/` содержит только BlobReferenceLedger.sq (без PrivateMediaResolver/Cache — moved to spec 012).

### 5d. Required-context omissions

- ✓ Все ADRs упомянуты в plan.md §Required Context Review.
- ✓ Roadmap references без устаревших line numbers.
- ✓ ADR-005 (Compose) marked «применим для спека 012, не 011».

### 5e. Vague-language sweep

- ✓ Нет «smooth» / «intuitive» / «simple».
- ✓ «fast» используется только в performance benchmarks (research.md §9) с конкретными цифрами.
- ✓ FR-clauses используют MUST / MUST NOT.

---

## STEP 6 — Verdict

```
SPECKIT-ANALYZE (rev. 2) for specs/011-contacts-and-e2e-encrypted-media/:

CONSTITUTION CHECK: 7 PASS + 1 N/A (Gate 5 Accessibility — no UI in 011) + 1 PASS WITH JUSTIFICATION (Gate 8 Simplicity — RecipientResolver Article XVII §3 exception)

CROSS-ARTIFACT TRACE:
  ✓ All 30+ FRs covered by tasks (FR-001..096, including new FR-024/025 для Hash/Signature)
  ✓ All 10 SCs covered
  ✓ All 3 contracts have roundtrip + signature/MAC tests
  ✓ No stale references — все migration markers explanatory

CHECKLISTS:
  always-on/requirements-quality   : 16/16 ✓
  always-on/meta-minimization      : 13/13 ✓
  triggered/domain-isolation       : 16/16 ✓
  triggered/wire-format            : 18/18 ✓
  triggered/failure-recovery       : 17/17 ✓ (CHK-FR-008, FR-012, FR-015 all closed)
  triggered/security               : 24/24 ✓ (CHK-SEC-008, SEC-011, SEC-018, SEC-022 closed; SEC-014 accepted-as-risk)
  triggered/permissions-platform   : 10/10 ✓
  triggered/state-management       : 13/13 ✓ (CHK-STATE-005 closed)
  N/A: accessibility, elderly-friendly, ux-quality, localization, core-quality (no UI)

SCANS:
  ✓ No dangling deleted-file references
  ✓ All wire-format files have schemaVersion
  ✓ Source-set placement consistent
  ✓ All required-context properly linked
  ✓ No vague-language survivors

VERDICT: READY-WITH-CAVEATS

3 known items accepted-as-risk before implementation (документированы):
  1. CHK-SEC-014 — MITM при первом fetch peer Pub (TLS Firestore + Security Rules — minimal layer; full mitigation = safety numbers UI в спеке ~018). Backlog: TODO-SEC-011.
  2. CHK-MIN-005 — RecipientResolver single-impl exception (review через 2 квартала после релиза 011 если 013/014/015 не появятся в backlog as committed work).
  3. Pre-production phase — нет реальных пользователей до спека ~35; backward-compat миграции wire-format не требуются, но дисциплина соблюдается с первого дня для пре-production discipline.

Implementation cleared. Phase 0 (T001-T008) можно начинать.
```

---

## Open Items для будущего

**Не блокеры implementation:**

1. **MITM safety numbers** (CHK-SEC-014) — реализуется в спеке ~018 (key rotation + forward secrecy). Backlog task `TODO-SEC-011` создаётся отдельно. В 011 — TLS + Security Rules как minimal protection layer.

2. **RecipientResolver monitoring** (CHK-MIN-005) — quarterly review: если 013 (двусторонние пары) или 014 (групповые ключи) не появятся в committed backlog в течение 2 кварталов после релиза 011 → revisit seam, потенциально упростить до hardcoded single-recipient.

3. **Pre-production wire-format discipline** — все wire formats имеют schemaVersion + backward-compat reads с первого commit'a, **хотя реальной миграции до спека ~35 не будет**. Это intentional discipline (привычка к pattern'у; легче добавить тесты сейчас, чем retrofit).

---

## Что готово к implementation

После rev. 2 verdict — все блокеры разрешены. Implementation готова стартовать:

1. **Phase 0 (T001-T008)**: env prep, deps, ADR-007 draft, first PR open.
2. **Phase 1 (T010-T028)**: 9 domain types + 8 port interfaces в commonMain.
3. **Phase 2 (T030-T040)**: fake adapters + wire-format roundtrip tests.
4. **Phase 3 (T050-T056)**: real Libsodium + Keystore adapters.
5. **Phase 4 (T060-T063)**: pairing extension с Pub publication.
6. **Phase 5 (T070-T072)**: Storage adapter + Rules + retry policy.
7. **Phase 6 (T080)**: PairRecipientResolver.
8. **Phase 7 (T090-T093)**: cleanup machinery + clear-data grace.
9. **Phase 8 (T100-T102)**: manual smoke (gate перед merge).
10. **Phase 9 (T110-T113)**: Konsist fitness gates.
11. **Phase 10 (T120-T122)**: final analyze + PR readiness.

**Estimated total**: 3-4 weeks.

---

<!-- novice summary -->

## TL;DR (простым языком)

**Что произошло в этом отчёте.** Я прогнал спек 011 через все проверки **второй раз** — после того, как мы 2-3 часа чинили проблемы, найденные в первом отчёте.

**Главный результат:** **READY-WITH-CAVEATS**. Это значит:
- Все 8 проблем синхронизации между документами (drift'ы) из первого отчёта — **исправлены**.
- Все 5 находок по безопасности — **закрыты** или **приняты как осознанный риск с записью в backlog**.
- Все 3 находки по восстановлению при сбоях — **закрыты** (добавлены MalformedEnvelope, WorkManager retry policy, clear-data 7-day grace).
- Конституционные проверки — 7 PASS + 1 N/A (нет UI) + 1 с обоснованием (RecipientResolver исключение).
- Cross-artifact trace — все 30+ функциональных требований покрыты задачами; все 10 критериев успеха — покрыты тестами; все 3 контракта имеют roundtrip и security тесты.

**Можно ли начинать кодить?** Да. 3 «caveat'a»:
1. **MITM при первом обмене публичными ключами** — accepted as risk до спека ~018 (там добавим safety numbers UX). Сейчас минимальная защита = TLS Firestore + Security Rules.
2. **RecipientResolver с одной реализацией** — формальное нарушение rule 4 CLAUDE.md, но обосновано тем, что в roadmap есть 3 будущие реализации (спеки 013/014/015). Если они не появятся через 2 квартала — упростим.
3. **Pre-production discipline** — мы соблюдаем schemaVersion + backward-compat тесты с первого дня, хотя реальные пользователи появятся только после спека ~35. Это инвестиция в привычку.

**Следующее действие — Phase 0:**
1. Добавить библиотеки (Lazysodium, JNA, Firebase Storage, CBOR serialization).
2. Сконфигурировать ABI splits в release build.
3. Поставить libsodium на CI machines.
4. Запустить Storage Emulator.
5. Создать draft ADR-007 (документ архитектурного решения про per-device asymmetric keys).
6. Закоммитить + push + открыть PR.

**Что переехало в спек 012 (полностью):**
- PrivateMediaResolver (IconStorage namespace dispatch для `private:`).
- PrivateMediaCache (кеш расшифрованных байтов).
- BlobMetadata.kind (Image/Document discriminator).
- DocumentPicker / DocumentViewer UX.
- Contacts Picker integration с photo upload.
- PartialReason.MediaDecryptFailed real emission.
- Все UI changes (admin progress, бабушка tile с photo, error indicators).

**Сводка цифр:**
- **52 задачи в 11 фазах** (было 73 в 12 фазах).
- **3-4 недели implementation** (было 5-7).
- **8 portов / 9 domain types / 1 SQL table (+1 sentinel)** — фундамент для всего проекта.
- **+1.2 MiB APK delta** до splits / **+300 KiB** per device после splits.
- **0 BLOCKER findings** в rev. 2 отчёте.

**Дальше что:**
1. Если ты согласен с verdict — закоммитить все правки.
2. Открыть PR (или подождать с PR до Phase 0 завершения).
3. Начать Phase 0 (T001-T008).
4. Push после каждой фазы.
