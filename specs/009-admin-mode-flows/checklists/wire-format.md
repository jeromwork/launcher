## Checklist: wire-format

**Spec**: `spec.md` (rev. 1, post-clarify C1-C5, 2026-05-15)
**Run**: 2026-05-15 — после `/speckit.clarify`, перед `/speckit.plan`.

Enforces [`CLAUDE.md`](../../../CLAUDE.md) rule 5 + Article VII §3 of `.specify/memory/constitution.md`.

---

## Inventory of wire formats touched by spec 009

| # | Wire format | Type | Direction | Persistence | Schema-version owner | Status in 009 |
|---|---|---|---|---|---|---|
| W1 | `/links/{linkId}/config/current` — поле `presetOverrides: PresetSettings?` | Firestore document (расширение) | leaves device + crosses versions | server (Firestore) | inherited from спек 008 (`schemaVersion = 1`) | **additive** (FR-013) — всегда `null` в 009, без bump |
| W2 | `/links/{linkId}/config/history/{autoId}` — **новая subcollection** | Firestore document (новый) | leaves device + crosses versions | server (Firestore) | spec 009 — **два независимых** `snapshotSchemaVersion` (envelope) + вложенный `config.schemaVersion` (FR-036) | **new wire format** |
| W3 | `/links/{linkId}/health` (поле `audioStreamMuted` и пр.) | Firestore document | leaves device | server | inherited from спек 006/007 | **read-only consumer**, без изменений wire-format |
| W4 | `/links/{linkId}/state.appliedConfigUpdatedAt` | Firestore document | leaves device | server | inherited from спек 008 | **read-only consumer**, без изменений |
| EX1 | VCard payload (`text/x-vcard`) from WhatsApp / Telegram / Viber / system Contacts | external wire format мы **читаем** через `ACTION_SEND` | enters device | n/a (transient intent payload) | **владелец — третья сторона** (vCard 2.1 / 3.0 RFC 2426) | **ingress-only adapter** (FR-028) |
| EX2 | `ContactsContract.CommonDataKinds.Phone` URI / cursor | platform wire format мы **читаем** через picker | enters device | n/a (transient platform call) | Android-owned | **ingress-only adapter** (FR-026) |
| P1 | `PendingLocalChanges` Room table — переиспользование для admin draft (FR-014a) | SQLite table | persists across app upgrades (local-only) | client (Android) | inherited from спек 008 plan/data-model | **possibly extended** (per-Managed `linkId` discriminator). TBD в plan.md (Room migration version 1→2?) |
| P2 | `PhoneHealthPreset` — `DEFAULT_PHONE_HEALTH_PRESET` constant | in-memory data class | not persisted, not transmitted | n/a | constant in code | **NOT a wire format** (sentinel). Becomes wire format only когда поедет в `/config.presetOverrides.phoneHealthSettings` (TODO-ARCH-010). |
| P3 | `PhoneHealthCriticalEvent` — local event | in-process bus | not persisted, not transmitted | n/a | event class в коде | **NOT a wire format** (in-process). Станет wire format когда подписчик будет writing /commands/ к Worker (SRV-MONITOR-001). |

**Не wire formats** (не подпадают под CLAUDE.md §5):

- `PhoneHealthIndicator` (FR-017) — local UI type в `app/health-ui/`, не leaves device.
- `PhoneHealthSeverity` enum — local UI type.
- Драфт-state ViewModel — derivative от P1, не wire format сам по себе.

**Не вводится новых deep-links / QR / exported config файлов в 009.** Это важно зафиксировать: спек 9 — pure CRUD над `/config` + новая subcollection `/config/history`.

---

## Schema version

### CHK001 — Every wire format carries an explicit `schemaVersion: Int` field from its first commit

- **W1 (`presetOverrides` additive field)**: ✅ Pass. Поле — additive (FR-013, всегда `null` в 009), envelope `/config.schemaVersion` уже стоит на `1` из спека 008. Bump НЕ требуется.
- **W2 (`/config/history/{autoId}`)**: ✅ Pass — **двойная schema version** зафиксирована в FR-036:
  - `snapshotSchemaVersion: Int` — envelope wrapper.
  - `config: ConfigCurrent` — вложенный, со своим `schemaVersion: Int` (наследуется из спека 8).
  - **Это критичное архитектурное решение (Clarification C2)**: envelope и config эволюционируют независимо; bump одного не требует transformer для другого.
  - Spec явно описывает обоснование («## Почему два независимых schemaVersion'a» — последний параграф FR-036).
- **EX1 (VCard)**: ⚠️ N/A — внешний wire format, владелец — RFC 2426. **Spec правильно** обрабатывает: парсер reads только `FN` + `TEL[n]` (FR-028), игнорирует все прочие поля. Версия vCard в самом payload (`VERSION:3.0`) не используется в логике — adapter работает по lowest-common subset.
- **EX2 (Contacts picker)**: ⚠️ N/A — платформенный URI, владелец Android. ACL через `SystemContactPickerAdapter` (FR-026).
- **P1 (Room `PendingLocalChanges`)**: ⚠️ TBD в plan.md — Room schema version поднимается через `@Database(version = N, autoMigrations = ...)` если таблица расширяется. Spec не специфицирует (правильный уровень — plan.md).

**Verdict**: ✅ Pass. Все spec-level wire formats имеют schemaVersion.

### CHK002 — `schemaVersion` field is **read first** during deserialization

- **W2**: spec не специфицирует deserialization order. **N/A на spec-level**.
  - **Action для plan.md**: в `contracts/config-history.md` явно зафиксировать «read `snapshotSchemaVersion` first → route envelope reader; read nested `config.schemaVersion` second → route config reader».
- **W1**: уже зафиксировано в спеке 008 `contracts/config.md` (CHK002 закодифицирован в `ConfigDocumentWireFormat.kt`). Спек 9 не меняет.
- **Finding**: NOT IN SPEC, правильный уровень — plan.md.

### CHK003 — Currently-supported `schemaVersion` constant is documented in code

- **N/A на spec-level**. Plan.md / code-level concern.
- **Action для plan.md**: ввести константы `const val CONFIG_SNAPSHOT_SCHEMA_VERSION = 1` (envelope) и переиспользовать существующую `CONFIG_SCHEMA_VERSION` для вложенного config.

---

## Backward compatibility

### CHK004 — Reads of **previous** schema versions remain possible for at least one major release

- **W1 (`presetOverrides`)**: ✅ Pass — additive поле (FR-013, A-10). Старые reader'ы 008 не знающие про `presetOverrides` — просто игнорируют поле (Firestore SDK default behaviour). Forward+backward compat обе работают.
- **W2 (`/config/history`)**: ✅ Pass — FR-043 явно: «если `snapshot.schemaVersion > current code support` — отвергаем; если `<` — lazy transformer (TODO-ARCH-015), пока кнопка отката заблокирована с пояснением». **Conscious deferred**: на момент `schemaVersion = 1` транзформеров 0 (OUT-018). Это правильно — пока нет реальной N+1 версии, transformer-код вреден (overengineering).
- **EX1 (VCard)**: ✅ Pass — adapter работает по minimal subset (`FN` + `TEL`), новые vCard версии (4.0+) будут читаемы пока поля не переименованы.
- **P1 (Room)**: TBD в plan.md (Room autoMigration v1→v2 при расширении).

### CHK005 — Adding a field is allowed; deserializer handles missing fields with documented defaults

- **W1**: ✅ Pass — FR-013 формализует additive policy. `presetOverrides` отсутствует у старых записей → reader использует `null` как default.
- **W2**: ✅ Pass — envelope additive (например, будущее поле `revertedFromId: String?` — FR-036 явно упоминает как пример).
- **Documentation gap**: spec.md **не содержит таблицы «field → default if missing»** для W2. Это **обязательное** для plan.md.
- **Action для plan.md**: `contracts/config-history.md` — fields table с дефолтами для отсутствующих полей.

### CHK006 — Renaming or removing a field requires a versioned migration **written before** breaking change ships

- **W1**: ✅ Pass — наследуется из спека 008 FR-006 («Rename/remove полей requires schemaVersion bump + reader-migration в Phase 0 следующего спека»).
- **W2**: ✅ Pass — FR-043 + TODO-ARCH-015 описывают transformer-chain pattern для bump'ов.
- В спеке 009 **никаких renames/removes** не делается (только additive `presetOverrides` + новая subcollection `/config/history`). Ничего ломающего не вводится. ✅

### CHK007 — Migration code is **scoped**

- **N/A на spec-level**. Code-level discipline.
- **Action для plan.md**: pattern `ConfigSnapshotTransformers.envelope_v1_to_v2(rawJson): ConfigSnapshot` и `ConfigTransformers.config_v1_to_v2(...)` — раздельные scope'ы, не branching.

---

## Forward compatibility

### CHK008 — Reading **newer** schema versions is handled gracefully

- **W2**: ✅ Pass — FR-043 явно: «если `snapshot.schemaVersion > current code support` — отвергаем как "слишком новая версия"». Это **fail-closed** policy с user-facing message, не crash. ✅
- **W1**: ⚠️ **Conscious deferred** — наследуется из спека 008 (Q4 clarify вынес forward-compat в отдельный спек `app-version-compatibility`, OUT-006 / TODO-ARCH-007). Spec 009 НЕ меняет policy.
- **EX1 (VCard)**: ✅ Pass — adapter игнорирует unknown fields (PHOTO, EMAIL, X-*), reads only FN + TEL (FR-028). Defense in depth — **zero attack surface** на новые поля.
- **Watch**: до первого production-update с `config.schemaVersion = 2` нужен `app-version-compatibility` спек (TODO-ARCH-007).

### CHK009 — If discriminator open: unknown value yields Failure, not crash

- Spec 9 вводит расширение `SlotKind` через **новый kind `OpenApp`** (FR-035, US-7). Это значит `SlotKind` становится open-set discriminator (`Call | Sms | OpenApp | ?`).
- **Finding**: **spec.md не специфицирует behavior** Managed при чтении `Slot` с unknown `kind` (например, Managed на старой версии получает `kind = "OpenApp"` пока сам не обновлён).
- **Severity**: ⚠️ Watch — реальная forward-compat дыра.
- **Action для plan.md**:
  - В `contracts/config.md` (наследуется из 008) добавить раздел «Forward-compat: unknown `slot.kind` → log warning, render placeholder tile "Эта плитка требует обновления приложения", не crash».
  - **Connected to** TODO-ARCH-007 (`app-version-compatibility`).
  - Реалистично: на момент 009 admin и Managed предполагается **обновляются вместе** (A-10). Но это допущение не закодировано в коде → fail-closed reader должен быть в плане.
- **VCard FN parsing**: ✅ Pass — FR-028 + FR-031 явно покрывают: VCard без TEL → user-facing reject, не crash.

---

## Tests

### CHK010 — Roundtrip test exists for every wire-format type: write → read → assertEquals

- **W1 (`presetOverrides`)**: ⚠️ **NOT explicitly required в spec.md**. Spec 008 SC-005 покрывал roundtrip `/config` — но 009 расширяет схему новым полем, отдельный roundtrip-test для случая `presetOverrides = null` (current) и `presetOverrides = <non-null>` (future-ready) — НЕ упомянут.
- **W2 (`/config/history/{autoId}`)**: ❌ **MISSING в spec.md**. Spec не содержит явного SC или FR требования «MUST have roundtrip test for ConfigSnapshot». SC-005 говорит только про backward-compat snapshot чтение через transformers.
- **Severity**: ⚠️ — рекомендуется добавить explicit FR / SC в spec.md.
- **Recommended spec edit**:
  - Добавить **FR-047** (или SC-009): «App MUST включать roundtrip-тесты `write → read → deep-equal` для (а) `/config` с `presetOverrides = null`, (б) `/config` с `presetOverrides = <sample>`, (в) `/config/history/{autoId}` envelope + nested config».
  - Альтернатива: positional уточнение в SC-005 («wire-format roundtrip и backward-compat для **`/config/history`** и **`presetOverrides` additive field** — 100% green»).
- **Action для tasks.md** (mandatory): `T-Wire-ConfigSnapshot-Roundtrip`, `T-Wire-PresetOverridesAdditive-Roundtrip`.

### CHK011 — Backward-compat test exists: fixture from previous schema version reads successfully

- **W1 (`presetOverrides`)**: ✅ Pass implicitly — additive policy наследуется. SC-005 спека 008 уже требует backward-compat test для config. Spec 009 не ломает это.
- **W2 (`/config/history`)**: ✅ Pass — SC-005 спека 009: «при schema bump в будущем существующие snapshot'ы в history остаются preview-доступны (через lazy transformer когда написан); 100% snapshot'ов с `schemaVersion = 1` корректно читаются». Это **частично** покрывает backward-compat.
- **Gap**: spec формулирует SC-005 на будущее («когда написан transformer»). **На момент 009 нет N-1 версии** — synthetic backward-compat test невозможен (как было в 008 через v0.5 fixture). **Принимаем**: явный backward-compat test для `/config/history` появится только в первом спеке-bumper'е.
- **Action для plan.md**: документировать «backward-compat synthetic test for `/config/history` — not applicable in spec 009 (no prior version exists). First migration spec MUST add it as Phase 0 task».

### CHK012 — Test fixtures stored as files in `commonTest/resources/`

- **N/A на spec-level**. Code-level pattern.
- **Action для plan.md / tasks.md**: явные fixture-файлы:
  - `commonTest/resources/wire-format/config-v1-with-preset-overrides-null.json`
  - `commonTest/resources/wire-format/config-v1-with-preset-overrides-sample.json`
  - `commonTest/resources/wire-format/config-history-snapshot-v1.json`
  - `commonTest/resources/wire-format/vcard-whatsapp-1tel.vcf`
  - `commonTest/resources/wire-format/vcard-telegram-2tel.vcf`
  - `commonTest/resources/wire-format/vcard-no-tel.vcf` (FR-031 reject path)
  - `commonTest/resources/wire-format/vcard-oversized-15kb.vcf` (FR-028 reject path)
  - `commonTest/resources/wire-format/vcard-non-utf8.vcf` (FR-028 reject path)
  - Pattern наследуется из спека 007/008.

---

## Persistence specifics

### CHK013 — SharedPreferences/DataStore: keys namespaced

- 009 НЕ вводит новые SharedPreferences / DataStore (использует Room через переиспользование `PendingLocalChanges` спека 008). **N/A**.
- **Watch**: убедиться в plan.md что admin draft в Room **не уезжает** случайно в SharedPreferences ради скорости.

### CHK014 — SQLDelight migration script + test

- 009 использует Room (наследует из 008), не SQLDelight. **N/A в SQLDelight-форме**.
- **Room эквивалент**: если в plan.md `PendingLocalChanges` расширяется (например, добавление `linkId: String` для per-Managed draft, FR-014a), нужен Room autoMigration v1→v2 + Room migration-test-helper.
- **Action для plan.md**: явно проверить — расширяется ли таблица или достаточно текущей схемы 008. Если расширяется — Room migration **обязательна**.

### CHK015 — If a stored type is removed entirely: one-shot cleanup written; grep-anchor

- Spec 9 НЕ удаляет ни одного stored type. **N/A**.
- **Note**: `OUT-017` (technical `/commands/`) — это **never-shipped** type, не нужен cleanup. ✅
- **Note**: edge case «локальные кэши удалённых Managed — удаляются при следующем launch admin-приложения» (FR-004) — **это** есть cleanup-like behavior. Action: в plan.md явно — какая таблица/директория чистится, и через какой trigger (например, `AdminAppStartupCleanup.run()`).

---

## Deep-link / QR / exported config

### CHK016 — URL/QR payload embeds schemaVersion

- Spec 009 НЕ вводит deep-link / QR / exported config (наследуется из 007).
- **Но**: VCard intent (`ACTION_SEND` + `text/x-vcard`) — это **incoming wire format**, не deep-link. Versioning принадлежит RFC 2426. Adapter reads только subset (FR-028).
- **Verdict**: ✅ N/A для outgoing payloads; ✅ Pass для incoming VCard через robust subset-only reader.

### CHK017 — Truncated/corrupted payload yields user-facing error, not crash

- **VCard adapter (FR-028)**: ✅ Pass — explicit:
  - reject payload > 10 KB → «слишком большой»;
  - reject не-UTF8 → «Не удалось прочитать контакт»;
  - reject no `TEL` → «Контакт без номера телефона не может быть добавлен в текущей версии» (FR-031);
  - ValidationError из `Contact.fromRaw()` → user-facing message (FR-028 last bullet).
- **System picker URI (FR-026)**: ✅ Pass — `ValidationError` → «Не удалось добавить контакт: <причина>», не пытаться чинить.
- **Firestore corrupted documents**: ⚠️ Watch — spec не явно специфицирует. Наследуется поведение спека 008 (через `/state.partialApplyReasons`?). **Action для plan.md**: zero-value snapshot в `/config/history` (missing required field) → reader gracefully skip + log, не crash.

---

## Contract folder

### CHK018 — If `contracts/` exists: each contract lists semantic version, breaking-change policy, link to roundtrip fixture

- `specs/009-admin-mode-flows/contracts/` пока **не существует** (создастся в plan.md).
- **Mandatory contracts для plan.md**:
  - `contracts/config-history.md` — schema W2 (envelope `snapshotSchemaVersion` + nested `config.schemaVersion`), fields table, lifecycle (write current → history → update current), Security Rules (FR-044/045), client-side housekeeping FR-038, lazy transformer plan FR-043, roundtrip fixture path. **Pattern**: спек 008 `contracts/config.md`.
  - `contracts/config-preset-overrides.md` (OR раздел в существующем 008 `contracts/config.md` extension): описание additive field `presetOverrides: PresetSettings?`, future PhoneHealthSettings структура (зарезервировано для TODO-ARCH-010).
  - `contracts/vcard-ingress.md` — incoming wire format VCard через `ACTION_SEND`. Описание subset (FN + TEL), reject rules (size / encoding / no-TEL), domain-validator route (FR-028 → `Contact.fromRaw()`). **Не часть нашего versioning** (RFC 2426), но **должен** быть документирован как ACL boundary per CLAUDE.md rule 2.
  - `contracts/contacts-picker-ingress.md` — incoming platform wire format (ContactsContract URI). Аналогично — subset (DISPLAY_NAME + NUMBER), ACL через `SystemContactPickerAdapter`.

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass (spec-level) | 11 | CHK001 (W1/W2), CHK004, CHK005 (с пометкой), CHK006, CHK008 (W2 fail-closed), CHK011 (с conscious-deferred для backward-compat synthetic), CHK013 (N/A), CHK015 (N/A), CHK016 (N/A + VCard subset), CHK017, отдельный pass для VCard adapter |
| 🟢 N/A для spec.md, обязательно для plan.md | 5 | CHK002, CHK003, CHK007, CHK012, CHK014, CHK018 |
| ⚠️ Watch / Conscious deferred | 2 | CHK008 W1 (наследует deferred из 008 TODO-ARCH-007); CHK009 (SlotKind становится open-set с добавлением `OpenApp` → нужна fail-closed reader policy в plan.md) |
| ❌ Fail (spec edit рекомендуется) | 1 | CHK010 — roundtrip test для `/config/history` envelope + nested config + `presetOverrides` additive **НЕ зафиксирован** явно в spec.md (нет FR/SC). Рекомендация: добавить FR-047 или расширить SC-005. |

**Verdict: PASS at spec-level с одной recommended-edit и двумя watch-items.**

Spec.md корректно закладывает wire-format discipline (CLAUDE.md §5):

- **Двойной schemaVersion для W2** (`/config/history`) — критичное решение C2, правильно обосновано (envelope vs config независимы).
- **`presetOverrides` additive** (FR-013) без bump — соответствует спеку 008 FR-006.
- **VCard ingress** — robust subset-only reader (FR-028), zero attack surface на unknown vCard fields.
- **Domain validation contract** (`Contact.fromRaw`) — universal ACL per CLAUDE.md rule 2, на 100% правильная архитектура для ingress wire formats.
- **Lazy transformers deferred** (TODO-ARCH-015) — правильное решение пока `schemaVersion = 1` единственный.
- **Schema mismatch fail-closed** на чтении snapshot (FR-043).

---

## Mandatory action items для plan.md

1. **`contracts/config-history.md`** — полная schema W2 (envelope + nested config), fields table с дефолтами для missing fields, lifecycle diagram, Security Rules (FR-044/045), client-side housekeeping (FR-038), backward-compat policy, ссылка на roundtrip test fixture. **Pattern**: спек 008 `contracts/config.md`.
2. **`contracts/vcard-ingress.md`** + **`contracts/contacts-picker-ingress.md`** — документация incoming wire formats и ACL boundaries (CLAUDE.md rule 2).
3. **Поправка спека 008 `contracts/config.md`** — добавление `presetOverrides: PresetSettings?` в fields table как additive field (либо новый `contracts/config-preset-overrides.md` для spec 009 со ссылкой назад). Решение оставить за planning agent'ом.
4. **Schema-version constants в коде**: `CONFIG_SNAPSHOT_SCHEMA_VERSION = 1` (envelope), переиспользование `CONFIG_SCHEMA_VERSION` (nested config).
5. **Reader-first schemaVersion parsing** — pattern «read `snapshotSchemaVersion` first → route envelope reader; read nested `config.schemaVersion` second → route config reader».
6. **Test fixtures stored as files** в `commonTest/resources/wire-format/` — список из CHK012.
7. **Room migration plan** — если `PendingLocalChanges` расширяется (per-Managed `linkId`), Room `@Database(version = 2)` + autoMigration + DAO-level migration test.
8. **Forward-compat для open-set `SlotKind`** (CHK009) — fail-closed reader: unknown `slot.kind` → log warning + placeholder tile «Эта плитка требует обновления приложения», не crash. Связь с TODO-ARCH-007.
9. **Cleanup unpaired Managed caches** (FR-004) — `AdminAppStartupCleanup` с grep-anchor comment.

---

## Recommended spec edit (single)

**Add FR-047** (or extend SC-005) explicitly requiring roundtrip tests:

> **FR-047**: App MUST include roundtrip tests (`write → read → deep-equal`) for the following wire formats:
> - `/config` с `presetOverrides = null` (текущее состояние спека 9);
> - `/config` с `presetOverrides = <sample valid PresetSettings>` (forward-compat readiness);
> - `/config/history/{autoId}` envelope с `snapshotSchemaVersion = 1` и вложенным `config.schemaVersion = 1`;
> - VCard ingress adapter contract test: sample WhatsApp/Telegram/Viber VCard → adapter → ValidContact или typed ValidationError (без crashes).

Альтернатива: расширить SC-005 — «100% snapshot'ов с `schemaVersion = 1` корректно читаются **и** roundtrip-проходят».

---

## Watch items

- **CHK008 forward-compat для W1**: наследуется conscious-deferred из спека 008 (TODO-ARCH-007 `app-version-compatibility`). До первого production-update с `config.schemaVersion = 2` — реализовать.
- **CHK009 open-set `SlotKind` с `OpenApp`**: spec 009 расширяет дискриминатор. Plan.md **обязан** зафиксировать fail-closed reader policy для unknown kind. Реальный риск: admin v2 пушит `kind = "WebPage"`, Managed v1 (старая версия) — не должен crash'ить.
- **CHK011 backward-compat synthetic test для `/config/history`**: на момент 009 нет N-1 версии. Первый migration спек **обязан** добавить как Phase 0 task.
- **Privacy compliance для VCard / Contacts ingress** (TODO-LEGAL-001) — связан с wire-format discipline в части «какие поля мы храним». Сейчас читаем только subset (FN + TEL) — правильное minimization. Watch для production-prep спека.

---

## Что внутри (TL;DR на русском)

**Чеклист wire-format для спека 009 — admin-mode flows.**

**Главные wire formats спека 9:**

1. **`/config/history/{autoId}`** — новая subcollection с историей конфигов (FR-036). Самое важное: **два независимых `schemaVersion`** — один для envelope-обёртки (snapshot wrapper), второй внутри вложенного `config`. Это критичное решение C2 — envelope и config могут эволюционировать раздельно, не дёргая друг друга.
2. **`presetOverrides: PresetSettings?`** — новое опциональное поле в `/config` (FR-013). Всегда `null` в спеке 9 — это **резерв на будущее** под редактируемые пресеты (TODO-FUTURE-SPEC-005). Additive, без bump'a schemaVersion. Forward-compat ready.
3. **VCard ingress** (FR-028) — мы **читаем** контакты из WhatsApp/Telegram/Viber через `ACTION_SEND` + `text/x-vcard`. Внешний формат, мы владельцы НЕ являемся. Adapter robust: размер ≤ 10 KB, только UTF-8, читает только `FN` + `TEL[n]`, игнорирует всё прочее (PHOTO, EMAIL, X-*) — zero attack surface.
4. **Contacts picker ingress** (FR-026) — платформенный URI через системный Android picker. Тот же ACL pattern — раw `displayName` + `phoneNumber` идут в **общий доменный валидатор** `Contact.fromRaw()`.

**Что важно: спек 9 НЕ меняет `/config/current` wire format спека 8** — только additive расширение (`presetOverrides`). Это правильно: контракт стабилизирован в 8 с `schemaVersion = 1`, ломать его нельзя.

**Что главное в проверке:**

- ✅ 11 проверок прошли на уровне спека (schemaVersion present, additive policy, backward-compat для будущих bump'ов через lazy transformers, VCard adapter robust).
- 🟢 5 проверок — N/A для спека, обязательны для plan.md (контракты, fixtures, Room migrations, schemaVersion-first reader pattern).
- ⚠️ 2 watch-item:
  - **CHK008** — forward-compat `/config` (унаследованный deferred из спека 8, TODO-ARCH-007).
  - **CHK009** — `SlotKind` становится open-set (добавляется `OpenApp`), нужен fail-closed reader в plan.md (Managed на старой версии не должен crash'ить от unknown kind).
- ❌ 1 рекомендация на правку спека — **CHK010**: roundtrip-тесты для `/config/history` envelope + nested config + `presetOverrides` additive **не зафиксированы явно** в FR/SC. Рекомендуем добавить **FR-047** (или расширить SC-005) с явным перечислением 4 roundtrip-тестов.

**Top-3 wire-format риска:**

1. **Open-set `SlotKind` с `OpenApp` без fail-closed reader** (CHK009). Если admin v2 пушит новый kind, а Managed v1 читает — должен gracefully показать placeholder, не crash. Plan.md обязан это закодифицировать.
2. **Race condition на write current→history без транзакции** (FR-037 явно принимает это). Migration на сервер через SRV-CONFIG-001. Watch до production-роллаута.
3. **Roundtrip-тесты не зафиксированы как explicit FR/SC** (CHK010). Без явного требования есть риск, что в tasks.md забудут T-Wire-ConfigSnapshot-Roundtrip.

**Нужно ли описать roundtrip тесты явно в спеке?** — **Да, рекомендуется**, через FR-047 или расширение SC-005. Это единственное предложенное spec-level правка. Альтернатива: оставить как есть и зафиксировать обязательность roundtrip-тестов в plan.md `contracts/config-history.md` и явных тасках в tasks.md.

**Размер**: 9 inventory items, 18 CHK gates, 9 mandatory plan.md actions, 1 recommended spec edit, 4 watch-items.
