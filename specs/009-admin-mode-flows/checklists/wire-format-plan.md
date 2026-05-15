# Checklist: wire-format (plan-level)

**Spec**: 009-admin-mode-flows
**Targets**: `plan.md` + `contracts/` (3 файла) + `data-model.md`
**Run**: 2026-05-15 — после `/speckit.plan`, перед `/speckit.tasks`.
**Previous spec-level run**: [`wire-format.md`](wire-format.md) — verdict PASS с 1 recommended-edit (FR-047 roundtrip tests) и 2 watch-items (CHK008 forward-compat, CHK009 SlotKind).

Enforces [`CLAUDE.md`](../../../CLAUDE.md) rule 5 + Article VII §3 of `.specify/memory/constitution.md`.

---

## Plan-level focus areas (per user brief)

| # | Question | Where to look | Verdict |
|---|---|---|---|
| F1 | Roundtrip tests explicit в plan §6? | `plan.md` §6 «Wire format roundtrip tests» | ✅ Pass — 4 tests listed |
| F2 | schemaVersion с момента 1 в каждом wire format в `contracts/`? | `contracts/*.md` | ✅ Pass — config-history v1, config-current v1 unchanged, vcard N/A external |
| F3 | Backward-compat test для config v1 reader на v2 doc? | `contracts/config-history.md` Tests table + plan §6 | ✅ Pass — `backwardCompat_additive_v2_on_v1_reader` + `spec008_reader_ignores_presetOverrides` |
| F4 | Forward-compat fail-closed test для schemaVersion > SUPPORTED? | `contracts/config-history.md` Tests table | ✅ Pass — `forwardCompat_v99_failsClosed` |
| F5 | Anti-spoofing rule FR-045a firestore-test? | `contracts/config-history.md` §Tests «Security.history.*» | ✅ Pass — 5 firestore-emulator security tests |
| F6 | Что-то новое после спек-level run? | дельта plan vs spec | ⚠️ 1 finding — VCardImporter в data-model.md заявлен как «ezvcard-backed», но plan §5 + contracts/vcard-incoming.md говорят «hand-written ~100 LOC, НЕ ezvcard». Internal inconsistency между data-model.md (рядом с типом) и plan/contracts. |

---

## Inventory of wire formats at plan-level

| # | Wire format | Owner contract | data-model.md type | schemaVersion |
|---|---|---|---|---|
| W1 | `/links/{linkId}/config/current` — `presetOverrides` additive | `contracts/config-current-additions.md` | `ConfigCurrent` (spec 008) + `PresetSettings` + `PhoneHealthSettings` | 1 (unchanged) |
| W2 | `/links/{linkId}/config/history/{autoId}` (NEW) | `contracts/config-history.md` | `ConfigSnapshot` (envelope) | envelope `snapshotSchemaVersion = 1`, nested `config.schemaVersion = 1` (independent) |
| EX1 | VCard payload incoming via `ACTION_SEND` + `text/x-vcard` | `contracts/vcard-incoming.md` | `RawVCard` (port output) | N/A — external (RFC 2426/6350) |
| EX2 | ContactsContract URI/Cursor incoming via picker | (no dedicated contract — adapter only per plan §2) | `RawPickerContact` (port output) | N/A — Android platform |
| P1 | `PendingLocalChanges` Room (reused spec 008) | (inherited spec 008) | (reused) | inherited; **plan §3 / §10 does NOT mention Room v1→v2 migration** |

**Watch items от спек-level run pre-checked:**
- CHK009 open-set `SlotKind` extension (`OpenApp` kind) — **NOT addressed** в plan-level wire-format docs. Поиск по plan + contracts: упоминание `SlotKind` отсутствует. Это унаследованный contract из спека 003 / 008; расширение через `OpenApp` упомянуто в plan §10 Phase 7, но **fail-closed reader policy для unknown kind в Managed v1** — не зафиксирована в plan / contracts.

---

## Schema version

### CHK001 — Every wire format carries an explicit `schemaVersion: Int` field from its first commit

- **W1**: ✅ Pass — `contracts/config-current-additions.md` явно: «`schemaVersion = 1` — unchanged. Spec 009 расширяет схему additive per spec 008 FR-006». Поле `presetOverrides` — opt-in additive, schemaVersion не bump'ается.
- **W2**: ✅ Pass — `contracts/config-history.md` §Field schema: `snapshotSchemaVersion: Int` обязательное поле с момента 1. `data-model.md` §3: `SUPPORTED_SNAPSHOT_SCHEMA_VERSION: Int = 1` константа.
- **EX1**: ✅ N/A — external RFC-owned. `contracts/vcard-incoming.md` явно: «Schema version: N/A — это **внешний** формат (RFC 2426 / 6350 VCard 3.0 / 4.0), мы его НЕ контролируем».
- **EX2**: ✅ N/A — platform-owned.

**Verdict**: ✅ Pass.

### CHK002 — `schemaVersion` field is **read first** during deserialization

- **W2**: ✅ Pass — `contracts/config-history.md` §Versioning policy: «**First-read invariant**: `snapshotSchemaVersion` MUST be deserialized FIRST (wire-format checklist CHK002), до любых `config.*` полей».
- **W1**: ✅ Pass implicitly (наследует from spec 008 `ConfigDocumentWireFormat.kt`).
- **EX1**: ✅ N/A — adapter reads RFC fields by name (FN, TEL), no schemaVersion.

**Verdict**: ✅ Pass.

### CHK003 — Currently-supported `schemaVersion` constant is documented in code

- **W2**: ✅ Pass — `data-model.md` §3: `const val SUPPORTED_SNAPSHOT_SCHEMA_VERSION: Int = 1` в companion object `ConfigSnapshot`.
- **W1**: ✅ Pass implicitly (inherited spec 008 `CONFIG_SCHEMA_VERSION`).
- **Gap**: для nested `config` внутри `ConfigSnapshot` константа берётся из spec 008 — нет явного reference в plan / data-model на reuse. Minor — но не блокер.

**Verdict**: ✅ Pass with minor note.

---

## Backward compatibility

### CHK004 — Reads of previous schema versions remain possible for at least one major release

- **W1**: ✅ Pass — `contracts/config-current-additions.md` §Forward-compat semantics: «Old reader (spec 008 reader без знания `presetOverrides`): читает unknown field — Kotlin Serialization default `ignoreUnknownKeys = true` (spec 008 invariant) — silently игнорирует».
- **W2**: ✅ Pass — `contracts/config-history.md` §Backward compatibility policy + test `backwardCompat_additive_v2_on_v1_reader` явно покрывает «Synthetic v2 envelope с дополнительным `recordedReason` field → v1 reader ignores unknown field».
- **EX1**: ✅ Pass — adapter работает по minimal subset (`FN` + `TEL`), новые vCard версии (4.0+) читаемы пока поля не переименованы.

**Verdict**: ✅ Pass — backward-compat test для config v1 reader на v2 doc **есть** для W2 (fixture `config-snapshot-v2-synthetic-additive.json`).

### CHK005 — Adding a field is allowed; deserializer handles missing fields with documented defaults

- **W1**: ✅ Pass — `contracts/config-current-additions.md` §Example: явно показано «equivalent (с полностью отсутствующим полем — для writer'ов которые не знают про FR-013)» + «Reader логика: `presetOverrides ?: null`».
- **W2**: ✅ Pass — `data-model.md` §3 Invariants table: `snapshotSchemaVersion ≥ 1`; envelope fields все required. Дополнительные additive fields (например, `recordedReason`) — readable as null default per backward-compat test.
- **Gap (spec-level inherited)**: «fields table с дефолтами для отсутствующих полей» рекомендован спек-level run для `contracts/config-history.md`. **Поверка**: таблица в `contracts/config-history.md` §Field schema — все поля Required ✓; missing-default не нужны (envelope полностью required). ✅ Resolved.

**Verdict**: ✅ Pass.

### CHK006 — Renaming or removing a field requires a versioned migration **written before** breaking change ships

- **W1**: ✅ Pass — наследуется spec 008 FR-006. `contracts/config-current-additions.md` §Backward compatibility policy явно: «Rename/remove `presetOverrides` или его вложенных полей → bump 1 → 2 + reader-migration. Не планируется».
- **W2**: ✅ Pass — `contracts/config-history.md`: «Rename/remove → bump 1 → 2 + transformer в Phase 0 следующего спека (TODO-ARCH-015)».
- Спек 9 НЕ rename/remove ничего.

**Verdict**: ✅ Pass.

### CHK007 — Migration code is **scoped** (no scattered `if (version == 1) ... else`)

- **W2**: ✅ Pass — pattern зафиксирован в `contracts/config-history.md` §Versioning policy через TODO-ARCH-015 «lazy transformer chain». Не реализуется в спеке 9 (schemaVersion = 1 единственная), но pattern документирован.
- **Plan-level note**: pattern должен быть `ConfigSnapshotTransformers.envelope_v1_to_v2(rawJson): ConfigSnapshot` (раздельный scope от `ConfigTransformers.config_v1_to_v2(...)`). **Не зафиксирован** explicitly в plan.md / contracts. Minor finding — TODO-ARCH-015 живёт в backlog как «при первом bump'е».

**Verdict**: ✅ Pass (conscious deferred до первого bump'а).

---

## Forward compatibility

### CHK008 — Reading **newer** schema versions is handled gracefully

- **W2**: ✅ Pass — `contracts/config-history.md` §Versioning policy: «Forward-compat (reader sees future version) — при `snapshot.snapshotSchemaVersion > SUPPORTED_VERSION` reader MUST **fail closed**: вернуть `Failure.SnapshotTooNew` (FR-043), кнопка отката заблокирована». Test `forwardCompat_v99_failsClosed` в Tests table.
- **W1**: ⚠️ **Watch (inherited)** — spec 008 conscious-deferred via TODO-ARCH-007 `app-version-compatibility`. Plan 9 НЕ адресует — корректно (out of scope).
- **EX1**: ✅ Pass — `contracts/vcard-incoming.md` §What we IGNORE: «Любой unknown property — silently ignored».
- **Plan §6 Test strategy**: ✅ Test «`ConfigSnapshotForwardSchemaTest` — snapshot со schema `> SUPPORTED_SCHEMA_VERSION` → reader fails closed (FR-043)» — explicitly listed in plan §6 Backward-compat smoke.

**Verdict**: ✅ Pass for W2 (explicit test + contract).

### CHK009 — If discriminator open: unknown value yields Failure, not crash

- **Inherited from spec-level**: `SlotKind` becomes open-set с добавлением `OpenApp` (FR-035, US-7). Spec-level рекомендация: «fail-closed reader policy для unknown `slot.kind` → log warning + placeholder tile, не crash. Должно быть в plan.md».
- **Plan.md проверка**:
  - §10 Phase 7 «OpenApp tiles» упоминает `OpenAppDispatcher` с fallback chain (launcher → market → web), но это **runtime fallback**, не **reader fallback** для unknown SlotKind.
  - §6 Test strategy НЕ содержит test «Managed v1 reads config с unknown SlotKind → placeholder».
  - `contracts/` — НЕТ контракта `slot-kind-evolution.md` или раздела в существующих о fail-closed для unknown kind.
- **Severity**: ⚠️ **Watch — unresolved** — то же finding что и в спек-level. **Действие**: связан с TODO-ARCH-007 (`app-version-compatibility`); решение conscious-deferred до того спека.

**Verdict**: ⚠️ Watch — inherited from spec-level, deferred to TODO-ARCH-007.

---

## Tests

### CHK010 — Roundtrip test exists for every wire-format type: write → read → assertEquals

- **W1 (`presetOverrides`)**: ✅ Pass — plan §6 «Wire format roundtrip tests» listing:
  1. `ConfigCurrentRoundtripTest` (`presetOverrides = null`)
  2. `ConfigCurrentNonNullPresetRoundtripTest` (`presetOverrides = PresetSettings(phoneHealthSettings = null)`)
- **W2 (`/config/history`)**: ✅ Pass — plan §6 listing:
  3. `ConfigSnapshotRoundtripTest` (envelope + nested config)
- **EX1 (VCard)**: ✅ Pass — plan §6 listing:
  4. `VCardAdapterContractTest` (4-5 real VCard samples → `Contact.fromRaw()` → assert match)
- **Detailed test fixtures**: `contracts/config-history.md` Tests table содержит `roundtrip_v1_minimal` + `roundtrip_v1_full`. `contracts/config-current-additions.md` Tests table содержит `roundtrip_v1_with_presetOverrides_null` + `roundtrip_v1_with_presetOverrides_omitted` + `roundtrip_v1_with_phoneHealthSettings_smoke`. `contracts/vcard-incoming.md` — 15 contract tests.

**Verdict**: ✅ Pass — все 4 roundtrip tests explicit в plan §6. FR-047 рекомендация спек-level **resolved** через plan-level test strategy.

### CHK011 — Backward-compat test exists: fixture from previous schema version reads successfully

- **W1**: ✅ Pass — test `spec008_reader_ignores_presetOverrides` (Synthetic spec-008-shaped reader reads spec-009 document с `presetOverrides: null` → unknown field ignored).
- **W2**: ✅ Pass — test `backwardCompat_additive_v2_on_v1_reader` (Synthetic v2 envelope с дополнительным `recordedReason` field → v1 reader ignores unknown field, читает остальное). Fixture `config-snapshot-v2-synthetic-additive.json`.
- **Conscious gap**: «backward-compat synthetic test для `/config/history` от prior `snapshotSchemaVersion = 0`» не существует — `schemaVersion = 1` единственная версия. Spec-level run уже зафиксировал «First migration spec MUST add it as Phase 0 task».

**Verdict**: ✅ Pass — backward-compat additive test есть для W1 и W2.

### CHK012 — Test fixtures stored as files in `commonTest/resources/`

- **W1**: ✅ Pass — `contracts/config-current-additions.md` Tests §Fixtures:
  - `config-current-v1-spec9-null-overrides.json`
  - `config-current-v1-spec9-omitted-overrides.json`
  - `config-current-v1-future-phoneHealth-synthetic.json`
- **W2**: ✅ Pass — `contracts/config-history.md` Tests §Fixtures:
  - `config-snapshot-v1-minimal.json`
  - `config-snapshot-v1-full.json`
  - `config-snapshot-v99-synthetic.json`
  - `config-snapshot-v2-synthetic-additive.json`
- **EX1**: ✅ Pass — `contracts/vcard-incoming.md` Fixtures: 14 файлов в `:core/src/androidUnitTest/resources/vcard-samples/`.

**Verdict**: ✅ Pass.

---

## Persistence specifics

### CHK013 — SharedPreferences/DataStore: keys namespaced

- 009 НЕ вводит новые SharedPreferences / DataStore. **N/A**.

**Verdict**: ✅ N/A.

### CHK014 — SQLDelight migration script + test (Room equivalent)

- Plan §2 явно: «`PendingLocalChanges` (Room — reused from spec 8) — continuous autosave».
- **Question (inherited from spec-level)**: «расширяется ли `PendingLocalChanges` под per-Managed `linkId` discriminator? → Room autoMigration v1→v2 нужен?»
- **Plan check**: §10 Phase 3 говорит «Continuous autosave в Room (FR-014b)». §6 Test strategy не содержит Room migration test. §10 Phase 0 «Foundation» — Room schema delta не упоминается.
- **Severity**: ⚠️ **Watch — unresolved**. Если `PendingLocalChanges` уже содержит `linkId` колонку (из спека 8), Room migration не нужен. Если нет — нужен Room v1→v2 + migration test.

**Action для tasks.md**: добавить task «Verify `PendingLocalChanges` schema covers per-Managed draft; if extension needed → Room autoMigration v1→v2 + DAO migration test». Если schema уже OK — note «no Room migration needed in spec 9».

**Verdict**: ⚠️ Watch — нужно решить в tasks.md.

### CHK015 — If a stored type is removed entirely: one-shot cleanup written; grep-anchor

- Спек 9 НЕ удаляет stored types.
- **Edge case (inherited)**: «локальные кэши удалённых Managed — удаляются при следующем launch admin-приложения» (FR-004). Plan-level: упомянуто в spec, но **в plan.md / data-model.md нет упоминания** какая таблица/директория чистится и через какой trigger.
- **Severity**: ⚠️ Watch — не блокирует, но **action для tasks.md**: добавить task «`AdminAppStartupCleanup.run()` с grep-anchor comment, чистит локальные кэши Managed по unpaired linkId list».

**Verdict**: ⚠️ Watch — action для tasks.md.

---

## Deep-link / QR / exported config

### CHK016 — URL/QR payload embeds schemaVersion

- Spec 009 НЕ вводит deep-link / QR / exported config. **N/A**.
- VCard intent — incoming, не deep-link. ✅ Pass.

**Verdict**: ✅ N/A.

### CHK017 — Truncated/corrupted payload yields user-facing error, not crash

- **VCard adapter**: ✅ Pass — `contracts/vcard-incoming.md` §Validation rules: explicit `ImportError` sealed class с 6 типами (`PayloadTooLarge`, `InvalidEncoding`, `MalformedWrapper`, `MissingFN`, `MissingTel`, `TelTooLong`).
- **System picker**: ✅ Pass — `data-model.md` §8 `PickError` sealed: `UserCancelled` / `PermissionDenied` / `Other(cause)`.
- **Firestore corrupted W2**: ✅ Pass — `data-model.md` §6 `RepositoryError`: `BackendUnavailable` / `PermissionDenied` / `Corrupt(cause)`. Reader returns typed error, не crash.
- **Plan §6 Test strategy**: contract tests verify all error paths.

**Verdict**: ✅ Pass.

---

## Contract folder

### CHK018 — Each contract file lists semantic version, breaking-change policy, link to roundtrip test fixture

- **`contracts/config-history.md`**: ✅ Pass — содержит `snapshotSchemaVersion`, §Versioning policy, §Backward compatibility policy, §Tests table с fixtures, §Security Rules requirements, §Retention policy, §Lifecycle diagram. Pattern спека 008 соблюдён.
- **`contracts/config-current-additions.md`**: ✅ Pass — содержит §Why this file exists (forward-compat reservation), §Spec 008 baseline reference, §Spec 009 additive change, §Forward-compat semantics, §Future evolution, §Tests с fixtures, §Backward compatibility policy.
- **`contracts/vcard-incoming.md`**: ✅ Pass — содержит §Source, §Format, §What we parse (whitelist FR-028), §What we IGNORE, §Validation rules, §Output type, §Security considerations, §Parser implementation hint, §Tests table с 15 tests и 14 fixtures, §Versioning / evolution policy.

**Verdict**: ✅ Pass — все 3 контракта полные.

---

## Findings new at plan-level (delta vs spec-level run)

### NEW-F1: VCardImporter implementation inconsistency between data-model.md and plan/contracts

**Location**:
- `data-model.md` §9 (line 339): «Parses RFC 6350 vCard 3.0/4.0 payloads. Adapter (`ezvcard`-backed) lives in `androidMain`».
- `plan.md` §5 Dependency impact: «VCard parser **hand-written ~100 LOC** в androidMain (FN + TEL only). Решение mentor-session 2026-05-15: `ezvcard` library в `:core/commonMain` нарушит rule 1 domain isolation; minimal parser в androidMain — единственный clean путь».
- `contracts/vcard-incoming.md` §Parser implementation hint: «Hand-written parser в `:core/src/androidMain`, ~100 LOC. **НЕ использовать** `ezvcard`».

**Severity**: ⚠️ — internal inconsistency. plan.md и contracts/vcard-incoming.md согласованы (hand-written), data-model.md устарел (всё ещё говорит `ezvcard`-backed).

**Action**: исправить `data-model.md` §9 — заменить «Adapter (`ezvcard`-backed) lives in `androidMain`» на «Adapter (hand-written ~100 LOC, FN+TEL whitelist) lives in `androidMain`. См. plan.md §5 + contracts/vcard-incoming.md».

### NEW-F2: nested `config.schemaVersion` constant reference

**Location**: `data-model.md` §3 показывает `SUPPORTED_SNAPSHOT_SCHEMA_VERSION = 1` (envelope), но не показывает явно reuse `CONFIG_SCHEMA_VERSION` (spec 008) для nested `config`.

**Severity**: minor (не блокер).

**Action**: optional — в `data-model.md` §3 добавить note «Nested `config.schemaVersion` reuses `CONFIG_SCHEMA_VERSION` constant from spec 008».

### NEW-F3: PendingLocalChanges Room schema delta unresolved

**Severity**: ⚠️ Watch — см. CHK014. Plan не специфицирует, нужна ли Room v1→v2 migration. Решение должно быть принято в tasks.md.

### NEW-F4: SlotKind open-set fail-closed reader not addressed

**Severity**: ⚠️ Watch (inherited from spec-level CHK009). Plan.md Phase 7 покрывает runtime fallback `OpenAppDispatcher`, но не reader fallback в Managed v1 для unknown `kind`. Conscious-deferred до TODO-ARCH-007.

### NEW-F5: `AdminAppStartupCleanup` for unpaired Managed caches not in plan

**Severity**: ⚠️ Watch — inherited spec-level finding. Plan-level: не упомянут explicit cleanup-job. **Action для tasks.md**.

---

## Summary

| Status | Count | CHK items |
|---|---|---|
| ✅ Pass | 12 | CHK001, CHK002, CHK003, CHK004, CHK005, CHK006, CHK007, CHK008 (W2), CHK010, CHK011, CHK012, CHK016, CHK017, CHK018 |
| ✅ N/A | 2 | CHK013 (no SharedPrefs/DataStore), CHK016 (no deep-link/QR) |
| ⚠️ Watch | 4 | CHK008 (W1 — inherited), CHK009 (SlotKind open-set — inherited), CHK014 (Room migration — needs tasks.md decision), CHK015 (cleanup job — needs tasks.md decision) |
| ❌ Fail | 0 | — |

**Verdict**: ✅ **PASS at plan-level** с одной правкой в data-model.md и четырьмя watch-items, ни один из которых не блокирует переход к /speckit.tasks.

---

## Plan-level focus answers (per user brief)

1. **Roundtrip tests explicit в plan §6?** — ✅ Yes. 4 tests (W1 null, W1 non-null, W2 envelope+nested, VCard adapter contract).
2. **Schema version policy в contracts/ — каждый wire format имеет schemaVersion с момента 1?** — ✅ Yes. W1 schemaVersion=1 (unchanged), W2 snapshotSchemaVersion=1, EX1 N/A (external RFC).
3. **Backward-compat test для config v1 reader на v2 doc?** — ✅ Yes для W1 (`spec008_reader_ignores_presetOverrides`) и W2 (`backwardCompat_additive_v2_on_v1_reader` с fixture).
4. **Forward-compat fail-closed test для schemaVersion > SUPPORTED?** — ✅ Yes для W2 (`forwardCompat_v99_failsClosed` + `ConfigSnapshotForwardSchemaTest` в plan §6).
5. **Anti-spoofing rule FR-045a — firestore-tests требуется?** — ✅ Yes, 5 security-rules emulator tests в `contracts/config-history.md` §Tests:
   - `Security.history.admin_can_write_self_uid`
   - `Security.history.spoofed_deviceId_denied` (FR-045a)
   - `Security.history.managed_can_write_self_uid`
   - `Security.history.foreign_uid_denied`
   - `Security.history.update_forbidden` (immutability)
6. **Что-то новое после спек-level run?** — ⚠️ Yes, 5 findings:
   - NEW-F1: data-model.md устарел (`ezvcard`-backed vs hand-written в plan/contracts) — **fix required**.
   - NEW-F2: nested config schemaVersion constant reference — minor.
   - NEW-F3: PendingLocalChanges Room migration delta — watch, в tasks.md.
   - NEW-F4: SlotKind open-set fail-closed — watch, унаследовано.
   - NEW-F5: AdminAppStartupCleanup — watch, в tasks.md.

---

## Mandatory action items

### Edit data-model.md (1 fix)

**File**: `c:/work/launcher/specs/009-admin-mode-flows/data-model.md` §9 (line ~339)

**Change**: заменить «Parses RFC 6350 vCard 3.0/4.0 payloads. Adapter (`ezvcard`-backed) lives in `androidMain`. Domain sees only `RawVCard`.» на:

> «Parses RFC 2426/6350 vCard 3.0/4.0 payloads. Adapter — **hand-written ~100 LOC** в `androidMain` (FN + TEL whitelist, no third-party library, см. `plan.md` §5 + `contracts/vcard-incoming.md` §Parser implementation hint). Domain sees only `RawVCard`.»

### Tasks.md actions (4 watch items)

1. **CHK014** (Room migration): Task «Verify `PendingLocalChanges` schema covers per-Managed `linkId` discriminator. If extension needed → Room autoMigration v1→v2 + DAO migration test. If existing schema OK → add note «no migration needed».»
2. **CHK009** (SlotKind open-set): conscious-deferred до TODO-ARCH-007. Опционально добавить smoke test «Managed reads config с unknown SlotKind → placeholder tile «обновите приложение», не crash» — если хотим guard'ить уже сейчас.
3. **CHK015** (AdminAppStartupCleanup): Task «Implement `AdminAppStartupCleanup.run()` с grep-anchor `// CLEANUP-FR-004 unpaired Managed caches` — runs on Admin app startup, removes local caches for linkId не в текущем list of paired Managed».
4. **CHK008 W1** (forward-compat): унаследовано из спека 008 TODO-ARCH-007, не блокирует спек 9.

---

## Watch items (conscious-deferred, not blockers)

- **CHK008 W1** — forward-compat для `/config` при будущем `schemaVersion = 2`. Spec 008 TODO-ARCH-007 → отдельный `app-version-compatibility` спек.
- **CHK009** — `SlotKind` open-set fail-closed reader policy. Plan покрывает runtime fallback (OpenAppDispatcher → Play Store → web), но не serialization fail-closed. Связан с TODO-ARCH-007.
- **CHK011 synthetic v0 для history** — нет N-1 версии, не applicable в 9. Первый migration спек добавит как Phase 0 task.
- **TODO-LEGAL-001** — privacy compliance для VCard / Contacts ingress (PII третьих лиц). Связан с wire-format в части «какие поля храним». Сейчас reading-only subset (FN + TEL) — правильное minimization. Watch для production-prep.

---

## Нужно ли менять plan.md / contracts/?

- **plan.md**: ✅ **НЕ менять** на plan-level. Все 4 roundtrip tests explicit в §6. Все contracts полные. Watch-items унаследованы или должны решаться в tasks.md.
- **contracts/**: ✅ **НЕ менять**. Все 3 файла (config-history.md, config-current-additions.md, vcard-incoming.md) проходят CHK018 — schemaVersion, versioning policy, breaking-change rules, test fixtures, links — всё на месте.
- **data-model.md**: ⚠️ **Менять — 1 правка** (NEW-F1): синхронизировать §9 VCardImporter с plan §5 + contracts/vcard-incoming.md (hand-written, не `ezvcard`).

---

## TL;DR на русском

**Wire-format чек-лист спека 9 на plan-level: PASS.**

**12 ✅ Pass, 2 ✅ N/A, 4 ⚠️ Watch, 0 ❌ Fail.**

**Plan-level фокус — все 5 вопросов отвечены положительно:**

1. **Roundtrip tests** — все 4 явно в plan §6 (`ConfigCurrentRoundtripTest`, `ConfigCurrentNonNullPresetRoundtripTest`, `ConfigSnapshotRoundtripTest`, `VCardAdapterContractTest`). Спек-level recommendation FR-047 закрыт через plan-level test strategy.
2. **Schema version policy** — каждый wire format в `contracts/` имеет schemaVersion с момента 1 (`/config` schemaVersion=1 unchanged, `/config/history` snapshotSchemaVersion=1, vCard N/A external).
3. **Backward-compat test (v1 reader на v2 doc)** — есть и для W1 (`spec008_reader_ignores_presetOverrides`), и для W2 (`backwardCompat_additive_v2_on_v1_reader` + fixture).
4. **Forward-compat fail-closed** — `forwardCompat_v99_failsClosed` для W2 + `ConfigSnapshotForwardSchemaTest` в plan §6.
5. **Anti-spoofing FR-045a** — 5 firestore-emulator security tests в `contracts/config-history.md` (admin/managed write self uid OK, spoofed/foreign uid DENIED, update DENIED).

**Что нового нашлось vs спек-level run — 5 findings:**

- **NEW-F1 — единственная правка**: `data-model.md` §9 устарел, говорит «`ezvcard`-backed adapter», но plan §5 + `contracts/vcard-incoming.md` единогласно: hand-written ~100 LOC без сторонних библиотек. Исправить data-model.md §9 — заменить упоминание `ezvcard` на «hand-written, FN+TEL whitelist».
- **NEW-F2** (minor): добавить в `data-model.md` §3 note про reuse `CONFIG_SCHEMA_VERSION` из спека 008 для nested config — опционально.
- **NEW-F3** (watch, в tasks.md): решить, нужна ли Room autoMigration v1→v2 для `PendingLocalChanges` под per-Managed draft, или текущая schema уже покрывает.
- **NEW-F4** (watch, унаследованно): `SlotKind` open-set с `OpenApp` — fail-closed reader для Managed v1, читающего kind='OpenApp' от admin v2. Deferred до TODO-ARCH-007.
- **NEW-F5** (watch, в tasks.md): `AdminAppStartupCleanup` для cleanup локальных кэшей unpaired Managed — упомянуто в spec FR-004, но не в plan-level Phase tasks.

**Нужно ли менять plan.md / contracts/?**

- **plan.md** — НЕ менять.
- **contracts/** (все 3 файла) — НЕ менять, все полные.
- **data-model.md** — менять **1 строку** §9 (VCardImporter: убрать упоминание `ezvcard`).

**4 watch-item действия — в tasks.md, не блокируют переход к /speckit.tasks:**

1. Room migration для `PendingLocalChanges` (или note что не нужна).
2. SlotKind fail-closed (или conscious-deferred с link на TODO-ARCH-007).
3. `AdminAppStartupCleanup` task.
4. Forward-compat W1 — унаследованно из спека 008, не для спека 9.

**Главные wire-format риски остаются те же** — open-set SlotKind, race condition без транзакции на write current→history, privacy compliance для VCard ingress. Все имеют conscious-deferred решения с маршрутом миграции.
