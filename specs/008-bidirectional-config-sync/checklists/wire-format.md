# Checklist: wire-format

**Spec**: `spec.md` (rev. 2026-05-14, post-clarify Q1-Q10)
**Run**: 2026-05-14 — `/speckit.clarify` post-pass before `/speckit.plan`.

Enforces [`CLAUDE.md`](../../../CLAUDE.md) rule 5 + Article VII §3.

---

## Inventory of wire formats in spec 008

| # | Wire format | Type | Туда/обратно | Persistence | Schema version owner |
|---|---|---|---|---|---|
| W1 | `/links/{linkId}/config/current` | Firestore document | leaves device (Firestore) + crosses app versions | server (Firestore) | spec 008 — `schemaVersion: Int` (FR-001) |
| W2 | `/links/{linkId}/state/current` | Firestore document | leaves device + crosses versions | server | **inherited** from spec 007 `state-bootstrap.md` (schemaVersion=1); 008 extends additive (FR-032) |
| P1 | `LocalAppliedConfig` (Room) | SQLite table | persists across app upgrades (local-only) | client (Android) | TBD in plan.md (Room schema version) |
| P2 | `PendingLocalChanges` (Room) | SQLite table | persists across app upgrades (local-only) | client | TBD in plan.md |

**Note about FCM payload `config.updated`** (FR-020): это **trigger-only** payload (без data полей; Managed просто читает `/config` после FCM прихода) — формально not a wire format в смысле data-carrying, поэтому за этим чеклистом не идёт. Подтвердить в plan.md.

---

## Schema version

- [x] **CHK001 — Every wire format carries an explicit `schemaVersion: Int` field from its first commit**
  - W1 (`/config`): FR-001 — `schemaVersion: Int` MUST с первого коммита.
  - W2 (`/state`): inherited from спека 007 (already present), FR-032 extends additive.
  - P1, P2 (Room): TBD в plan.md — Room имеет свой собственный механизм schema versioning через `@Database(version = N)` + migrations. Это **не** должно быть spec-уровневым решением, но spec должен явно сказать «Room schema versioned starting at 1». **Action для plan.md**: документировать starting Room schema version и стратегию migrations.

- [ ] **CHK002 — `schemaVersion` field is read first during deserialization**
  - W1, W2: **spec не специфицирует deserialization order**. Это обычно plan.md / реализация. Но spec.md **может** упомянуть это явно как требование, не уточняя как. В спеке 007 `state-bootstrap.md` тоже не специфицирует — это principles-level, не spec-level.
  - **Action для plan.md**: явно зафиксировать «read schemaVersion first, route to appropriate reader». В contracts/config.md (когда создастся в plan.md) — это требование.
  - **Finding**: NOT IN SPEC, но это правильно для уровня spec.md. **N/A на spec-level**, обязательно покрыть в plan.md.

- [ ] **CHK003 — Currently-supported `schemaVersion` constant is documented in code (single source of truth)**
  - Уровень spec.md этого не покрывает (это code-level concern). Обязательно для plan.md (e.g., `const val CONFIG_SCHEMA_VERSION = 1` в `ConfigDocument.kt`).
  - **N/A на spec-level**.

## Backward compatibility

- [x] **CHK004 — Reads of previous schema versions remain possible for at least one major release**
  - W1: FR-005 — backward-compat read test (`reader v2 reads v1`). Закреплено как обязательный test.
  - W2: FR-032 — extends 007 schema additive (без bump version), что **автоматически** означает forward-compat (старые reader'ы спека 007 могут продолжать читать).
  - P1, P2: Room migrations — TBD в plan.md.

- [x] **CHK005 — Adding a field is allowed; deserializer handles missing fields with documented defaults**
  - FR-006: «Field additions MUST быть additive (новые опциональные поля без bump schemaVersion)».
  - Документация дефолтов **отсутствует** в spec.md (нет таблицы «field → default if missing»). Это уровень plan.md / contracts/.
  - **Action для plan.md**: создать `contracts/config.md` с таблицей field-schema (как у спека 007 `link.md`, `state-bootstrap.md`).

- [x] **CHK006 — Renaming or removing a field requires a versioned migration written before breaking change ships**
  - FR-006: «Rename/remove полей requires schemaVersion bump + reader-migration в Phase 0 следующего спека».
  - Зафиксировано как требование. ✅

- [ ] **CHK007 — Migration code is scoped — `migrateLegacy(json): Action` style — not branching everywhere**
  - Spec.md этого не специфицирует. Это code-level discipline.
  - **N/A на spec-level**. Plan.md должен документировать pattern (e.g., `ConfigMigrations.migrateV1ToV2(rawJson): ConfigDocument`).

## Forward compatibility

- [ ] **CHK008 — Reading newer schema versions is handled gracefully**
  - **Finding: FORWARD-COMPAT POLICY EXPLICITLY DEFERRED.** Q4 clarify решил вынести schema mismatch behavior в отдельный спек `app-version-compatibility` (OUT-006). Это **осознанное решение**: в 008 все editor'ы тестируются монорелизом, schema mismatch by construction не возникает. Forward-compat policy придёт в будущем спеке.
  - **Watch**: до того как первый user обновится из эпохи 008 в эпоху «008+v2 schema», должен быть реализован `app-version-compatibility` спек (см. project-backlog `TODO-ARCH-007`).
  - **Verdict**: **conscious deferred**, документировано, не блокирующее.

- [x] **CHK009 — If discriminator open: unknown value yields Failure, not crash**
  - В 008 нет open discriminators (никаких `kind: "..."` полей с открытым набором values). Все поля — закрытые типы (presetId: String — конкретная ссылка на known preset; flows[] и contacts[] — массивы fixed-shape objects).
  - **Note**: если в plan.md появится discriminator (например, `slot.kind: "call" | "sms" | "web"`) — должен быть failure-on-unknown handler.
  - **N/A для текущей spec-формы**, watch для plan.md.

## Tests

- [x] **CHK010 — Roundtrip test exists for every wire-format type: write → read → assertEquals**
  - FR-005 (W1, W2): «спек MUST включать roundtrip-test (`write v1 → read v1 → deep-equal`) для `/config` и `/state`».
  - SC-005: «Wire-format roundtrip и backward-compat read tests — 100% green».
  - P1, P2: TBD в plan.md (Room — это другой уровень, обычно проверяется через DAO-level tests).
  - **Action для tasks.md**: явные tasks `T-Wire-Config-Roundtrip`, `T-Wire-State-Roundtrip`, `T-Room-LocalAppliedConfig-Roundtrip`, `T-Room-PendingLocalChanges-Roundtrip`.

- [x] **CHK011 — Backward-compat test exists: fixture from previous schema version reads successfully**
  - FR-005: «backward-compat read-test (`reader v2 reads v1`)». Закреплено.
  - SC-005: «100% green».
  - **Watch**: SC-005 требует «reader v2 reads v1» — но в 008 у нас только v1; реальный backward-compat тест понадобится только в момент, когда поставится v2 (что произойдёт в `app-version-compatibility` спеке). **В 008** этот test реализуется как **synthetic** — fixture, имитирующий «v0.5» (без некоторых полей которые есть в v1), и проверка, что v1 reader справляется через defaults. Это нужно явно описать в plan.md.

- [x] **CHK012 — Test fixtures are stored as files in `commonTest/resources/` (not literal strings)**
  - Уровень spec.md не покрывает. **N/A на spec-level**, обязательно в plan.md / tasks.md.
  - Action: явные fixture-файлы `commonTest/resources/wire-format/config-v1-minimal.json`, `config-v1-full.json`, `state-v1-bootstrap.json`, `state-v1-applied.json`, и т.п. Опираться на pattern спека 007 (если такой pattern там есть — проверить).

## Persistence specifics

- [ ] **CHK013 — SharedPreferences/DataStore: keys namespaced**
  - 008 использует **Room**, не SharedPreferences / DataStore. **N/A**.
  - **Note**: убедиться в plan.md что 008 НЕ вводит дополнительные SharedPreferences / DataStore. Если нужно (например, «schemaVersion флаг приложения для cleanup FR-045») — namespaced keys.

- [ ] **CHK014 — SQLDelight: каждая migration script + test**
  - 008 использует Room, не SQLDelight. **N/A**.
  - Room эквивалент: `@Database(version = N, autoMigrations = [...], migrations = ...)` + Room-built migration-test-helper. Action для plan.md.

- [x] **CHK015 — If a stored type is removed entirely: one-shot cleanup written; documented with grep-anchor**
  - FR-045: «MUST при первом запуске после обновления до 008-кода **удалить** любые legacy mock-storage artifacts (JSON-файлы из спека 003)».
  - Это **one-shot cleanup**. ✅
  - **Action для plan.md**: явный grep-anchor comment в cleanup-коде, e.g., `// CLEANUP-008: removed legacy 003 mock-JSON storage; remove this code after 2026-12-31` (TBD).

## Deep-link / QR / exported config

- [x] **CHK016 — URL/QR payload embeds schemaVersion**
  - 008 НЕ вводит deep-links / QR / exported config. Все эти существуют в спеке 007 (pairing-token QR, pairing deep-link) — там покрыто. **N/A для 008**.
  - **Watch**: backlog `config-portability` спек (когда дойдёт) — будет import/export config файлов; **обязательно** будет нуждаться в этом чеклисте.

- [x] **CHK017 — Truncated/corrupted payload yields user-facing error, not crash**
  - Аналогично: 008 не вводит scan-payloads. **N/A**.
  - Но: Firestore document с corrupted JSON (например, missing required field) — должен gracefully отказать. FR-005 (roundtrip) + FR-006 (additive policy) частично покрывают; явный «corrupted document → user-facing error in /state.partialApplyReasons» — должен быть явным в plan.md.

## Contract folder

- [ ] **CHK018 — If `contracts/` exists: each contract file lists its semantic version, breaking-change policy, and a link to roundtrip test fixture**
  - **Finding**: `specs/008-bidirectional-config-sync/contracts/` пока **не существует** (создастся в plan.md, как для спека 007 — там `contracts/link.md`, `contracts/state-bootstrap.md`, и т.д.).
  - **Action для plan.md** (mandatory):
    - `contracts/config.md` — schema W1, fields table, lifecycle, Security Rules, tests, backward-compat policy.
    - `contracts/state-applied.md` — schema W2 extension to спека 007 `state-bootstrap.md`, fields table, links, tests.
    - `contracts/fcm-payload-config-updated.md` — payload type definition (если решим формализовать; possibly inline in worker-notify.md спека 007).
    - Каждый contract — pattern спека 007 (`link.md` хороший образец).

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass (spec-level) | 8 | CHK001, CHK004, CHK005, CHK006, CHK010, CHK011, CHK015, CHK016 |
| 🟢 N/A для spec.md, обязательно для plan.md | 6 | CHK002, CHK003, CHK007, CHK012, CHK013, CHK014, CHK017, CHK018 |
| ⚠️ Conscious deferred | 1 | CHK008 — forward-compat вынесено в `app-version-compatibility` (OUT-006, TODO-ARCH-007) |
| ❌ Fail | 0 | — |

**Verdict: PASS at spec-level.**

Spec.md правильно закладывает wire-format discipline (CLAUDE.md §5):
- schemaVersion present (FR-001, inherited 007).
- Roundtrip + backward-compat tests required (FR-005, SC-005).
- Additive-only policy (FR-006).
- Cleanup для legacy (FR-045).
- Schema mismatch forward-compat — явно вынесено в отдельный спек (Q4 clarify).

**Mandatory action items для plan.md** (без них spec не реализуем):

1. **`contracts/config.md`** — полная schema W1 с fields table, lifecycle diagram, Security Rules requirements (FR-011 расширение), tests, backward-compat policy. Образец: спек 007 `link.md`.
2. **`contracts/state-applied.md`** — schema W2 как extension к `state-bootstrap.md` спека 007, fields table, additive fields list.
3. **Room schema versioning strategy** — `@Database(version = 1)` initial + migration plan for future bumps.
4. **Test fixtures stored as files** in `commonTest/resources/wire-format/*.json` — pattern из 007 если есть.
5. **`migrateLegacyMockStorage()`** function для FR-045 cleanup — scoped, with grep-anchor comment.
6. **Reader-first schemaVersion parsing** — documented decoder pattern (read version, route to appropriate parser).

**Watch items**:

- **CHK008 forward-compat**: до первого production-update должен быть реализован `app-version-compatibility` спек (TODO-ARCH-007 в project-backlog).
- **CHK011 backward-compat test**: в 008 будет synthetic v0.5 fixture (без некоторых полей) для проверки v1-reader-через-defaults.
- **CHK018 `config-portability` будущий**: спек export/import будет нуждаться в этом чеклисте для QR/file payloads.

**No spec.md edits required.**
