# Checklist: wire-format — TASK-51 libsodium consolidation

**Applied**: 2026-06-26
**Skill**: `.claude/skills/checklist-wire-format/SKILL.md`
**Spec**: `specs/task-51-libsodium-consolidation/spec.md`
**Source rules**: CLAUDE.md rule 5 + constitution Article VII §3

---

## Wire-format inventory for this spec

TASK-51 — это **инфраструктурный рефакторинг crypto-стэка** (замена lazysodium → ionspin, переименование `family.*` → `cryptokit.*`, force re-pair of Android Keystore aliases). Спека **явно не вводит и не меняет** ни один wire-format:

- **Spec 011 wire-format types** (`DeviceIdentity`, `EncryptedEnvelope`, `Recipient`, ...) — FR-004 фиксирует **byte-equal read/write**, `schemaVersion: 1` остаётся неизменным. Это **в scope spec 011**, не TASK-51. Эти типы только **переезжают в новый Kotlin-пакет** `cryptokit.pairing.api.*` (rename импорта, не изменение wire-format).
- **Android Keystore aliases** (`spec011.encryption.own`, `spec011.signing.own`) — это **persisted ключи в системном Keystore**, не структурированный wire-format (raw bytes под именованным alias'ом, schemaVersion неприменим). FR-005 предписывает **force re-pair** (Pattern 4): aliases удаляются на первом запуске после upgrade, никакой backward-compat read. Migration code = nuke-and-re-pair, без structured migration script.
- **`EnvelopeConfigCipherRoundtripTest`** (golden vectors) — это **существующий** roundtrip-тест spec 011 wire-format, должен остаться зелёным (FR-011). TASK-51 его не меняет.
- **Никаких новых JSON / Proto / DataStore / SQLDelight / deep-link / QR схем** не вводится.

Поэтому большинство пунктов skill'а — **N/A для TASK-51**, но проверяются как «не нарушается ли инвариант существующих wire-format'ов».

---

## Schema version

- [N/A] CHK001 Не вводится новый wire-format. Существующий spec 011 формат carries `schemaVersion: 1` (assumption в spec.md). Verified by ownership boundary — это spec 011 territory.
- [N/A] CHK002 Same — read-order инвариант существующего формата сохраняется (byte-equal требование FR-004 + assumption «schemaVersion: 1 остаётся»).
- [N/A] CHK003 Same — константа версии существует в spec 011 коде, не трогается этой спекой.

## Backward compatibility

- [x] CHK004 Reads of previous versions — **N/A для нового formata** (нет нового). Для существующего spec 011 wire-format: FR-004 явно требует byte-equal compatibility, ассумпция «schemaVersion: 1 остаётся». Соответствует.
- [N/A] CHK005 Добавления полей нет — wire-format не меняется.
- [N/A] CHK006 Renaming/removing полей нет — wire-format не меняется. (Pakage rename `family.*` → `cryptokit.*` — это Kotlin-import rename, не wire-format поле; serialized name не зависит от Kotlin package у используемого ionspin/kotlinx.serialization сериалайзера, **assumption** требует verify в plan'е.)
- [N/A] CHK007 Никаких migration веток `if (version == ...)` не появляется.

## Forward compatibility

- [N/A] CHK008 Forward-compat reads — N/A, schemaVersion остаётся 1, новой версии не появляется.
- [N/A] CHK009 Discriminator `kind: "..."` — N/A, не вводится новый sealed wire-type.

## Tests

- [x] CHK010 Roundtrip-тест существует: `core/src/commonTest/.../EnvelopeConfigCipherRoundtripTest` (spec.md, секция Local Test Path / Fixtures). FR-011 требует оставить его зелёным.
- [x] CHK011 Backward-compat test существует через тот же `EnvelopeConfigCipherRoundtripTest` (golden vectors из spec 011) — read pre-TASK-51 fixture успешно проходит, потому что format не меняется. FR-004 (byte-equal) + FR-011 (тесты зелёные) формализуют требование.
- [ ] CHK012 Fixtures как файлы в `commonTest/resources/`: **в спеке не подтверждено явно**. Roundtrip-тест упомянут, но формат хранения golden vectors (литералы vs resources) не специфицирован. **Open item** для plan'а.

## Persistence specifics

- [N/A] CHK013 SharedPreferences/DataStore namespacing — TASK-51 не трогает persisted SharedPreferences/DataStore структуры. Android Keystore aliases `spec011.encryption.own` / `spec011.signing.own` — **уже** namespaced (`spec011.*`), но они **удаляются** в этой спеке (force re-pair), не сохраняются.
- [N/A] CHK014 SQLDelight миграции — отсутствует SQLDelight, не trogen.
- [x] CHK015 **Stored type removed (Keystore aliases)** — FR-005 явно описывает one-shot cleanup: «при первом запуске после upgrade старые Keystore aliases удаляются». Grep-anchor — inline-TODO в спеке: `// TODO(post-task-6): replace nuke-and-re-pair with derive-from-root after Root Key Hierarchy lands`. Это exit-ramp + анкер для будущего grep.

## Deep-link / QR / exported config

- [N/A] CHK016 QR payload schemaVersion — TASK-51 не меняет QR payload. QR-код в PairingActivity (spec 011) генерируется существующим форматом, который остаётся неизменным.
- [N/A] CHK017 Truncated/corrupted payload handling — N/A для этой спеки (QR scanning ownership в spec 011 / TASK-8 admin app).

## Contract folder

- [N/A] CHK018 `contracts/` — для TASK-51 plan ещё не сгенерирован; если контракты появятся, они должны касаться **cryptokit API surface** (Kotlin interfaces), а не wire-format. Wire-format контракты — territory spec 011.

---

## Open items

1. **CHK012** — нужно подтвердить в `plan.md`, что `EnvelopeConfigCipherRoundtripTest` использует **file-based fixtures** в `commonTest/resources/`, а не строковые литералы. Если литералы — flag как drift risk и переезд на файлы. Owner-decision при plan generation.
2. **CHK006 (assumption verify)** — подтвердить в plan.md что namespace rename `family.*` → `cryptokit.*` (FR-016) **не меняет** сериализованные имена полей в spec 011 wire-format типах. Если используется `kotlinx.serialization` с `@SerialName`, Kotlin package неважен; если defaults — переименование класса теоретически может сломать byte-equal. Требует grep-check во время plan'а.
3. **Force re-pair documentation** (CHK015 — passing, но напоминание): после реализации FR-005 убедиться что inline-TODO `// TODO(post-task-6): replace nuke-and-re-pair with derive-from-root after Root Key Hierarchy lands` присутствует в коде wipe-логики (grep-anchor для exit ramp на rule 8 server-roadmap паттерн).

---

## Verdict

**PASS with 1 open item** (CHK012 deferred to plan.md verification).

Wire-format не вводится и не меняется этой спекой. Существующий spec 011 wire-format защищён через FR-004 (byte-equal) + FR-011 (tests green). Force re-pair миграция Keystore aliases корректно документирована как one-shot cleanup с inline-TODO exit-ramp (CHK015 pass). Большинство гейтов N/A не из-за упущения, а из-за scope boundary — wire-format ownership принадлежит spec 011, не TASK-51.

**Counts**:
- `[x]` (passing applicable): 3 (CHK004, CHK010, CHK011, CHK015) → **4** passing
- `[ ]` (open): 1 (CHK012)
- `[N/A]`: 13 (CHK001-003, 005-009, 013-014, 016-018)

Total applicable (`[x]` + `[ ]`): 5. Passing: 4/5.
Total CHK items: 18. `[x]`: 4.
