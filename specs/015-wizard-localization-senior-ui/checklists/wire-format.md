# Checklist: wire-format

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16 (post Group A + domain-isolation fixes)
**Verdict**: 13 ✓ / 1 ⚠ / 4 ✗ — четыре violations (FR-017 outdated count, three persistent formats missing `schemaVersion`)

---

## Wire formats в F-3

F-3 имеет **семь** wire formats:

| # | Format | Тип | Где хранится |
|---|---|---|---|
| 1 | `wizard.manifest` | bundled JSON | `core/wizard/src/commonMain/resources/MR/files/wizard-manifests/*.json` |
| 2 | `screen.layout` | bundled JSON | `core/wizard/src/commonMain/resources/MR/files/screen-layouts/*.json` |
| 3 | `tile.set` | bundled JSON | `core/wizard/src/commonMain/resources/MR/files/tile-sets/*.json` |
| 4 | `system-settings.pool` | bundled JSON | `core/wizard/src/commonMain/resources/MR/files/system-settings/*.json` |
| 5 | `WizardCheckpoint` | persistent (DataStore) | устройство, переживает app update |
| 6 | `UserPreferences` | persistent (DataStore) | устройство, переживает app update |
| 7 | `CONTEXT.json` | dev-time JSON in repo | `core/localization/strings-context/CONTEXT.json` |

8-я возможная (`DismissedHintsStore`) — простой `Set<String>`, без structured body — нет необходимости в schemaVersion.

---

## Schema version

- [⚠ → ✗] **CHK001** Every wire format carries explicit `schemaVersion: Int`.
  - **JSON schemas (1-4)**: ✓ schemaVersion в 6-полевом header (FR-011, FR-052).
  - **WizardCheckpoint (5)**: ✗ **VIOLATION**. FR-003 определяет shape `{manifestId, currentStepIndex, answers}` — нет `schemaVersion`. При future bump'е (например, добавление поля `completedSteps: Set<StepId>`) старая версия app не сможет читать новую persistent state без version detection.
  - **UserPreferences (6)**: ✗ **VIOLATION**. FR-047 определяет `data class UserPreferences(theme, fontScale, languageOverride, attestedSettings)` — нет `schemaVersion`. Future bump (добавление поля) ломает старые app reads.
  - **CONTEXT.json (7)**: ✗ **VIOLATION**. FR-031b показывает schema по ключу, но top-level нет `schemaVersion`. Если позже добавим поле `screenshot[]` (массив) или `tone: "formal" | "casual"` — нужен version detection.

- [✓] **CHK002** schemaVersion read first.
  - JSON schemas: `schemaVersion` — первое поле в 6-полевом header'е. ✓
  - Persistent stores: implicit (impl detail в plan.md).

- [✓] **CHK003** Currently supported `schemaVersion` documented.
  - FR-012/013/014/052: each schema `schemaVersion: 1`. ✓ single source of truth.

## Backward compatibility

- [✓] **CHK004** Reads of previous schema versions remain possible.
  - FR-018: backward-compat reader test mechanism prepared (placeholder для первой версии). ✓ для JSON schemas.
  - Persistent stores: implicit, but без schemaVersion (CHK001) backward-compat невозможен.

- [✓] **CHK005** Adding field allowed; missing fields handled with documented defaults.
  - FR-015: forward-compat additive read (`ignoreUnknownKeys = true`). ✓
  - Defaults explicit: `ThemeChoice = Auto`, `fontScale = null`, `languageOverride = null`, `attestedSettings = empty map`, `canSkip = false`. ✓

- [✓] **CHK006** Rename/remove requires migration written before shipping.
  - FR-045: explicit policy «migration code до ship'а bump'а». ✓

- [✓] **CHK007** Migration code scoped.
  - FR-045 фиксирует policy без impl pattern (защищено rule 4 — определяется при первом реальном bump'е). ✓ acceptable foundation deferral.

## Forward compatibility

- [✓] **CHK008** Reading newer schema versions handled gracefully.
  - FR-015 (additive): unknown fields silently ignored.
  - FR-016 (breaking): hard-fail с понятным сообщением + «Понятно» button (паттерн спека 010 FR-042). ✓ hybrid documented.

- [✓] **CHK009** Unknown discriminator yields Failure not crash.
  - F-3 НЕ использует `kind`-discriminator (отвергнуто glossary §5.2). N/A на envelope level.
  - **Per-schema discriminators**: 
    - Unknown `stepType` в wizard.manifest → FR-010: skip с diagnostic warning. ✓
    - Unknown `mechanism` в system-settings.pool: **не explicit**. Recommend добавить: «Unknown SettingMechanism → `SettingStatus.NotSupportedOnPlatform` для этого setting, остальные settings обрабатываются нормально».
    - Unknown `actionType` в tile.set: FR-014 — soft validation (warning + плитка отрисовывается). ✓

## Tests

- [✗] **CHK010** Roundtrip test exists for every wire-format type.
  - **VIOLATION**: FR-017 говорит «для каждой из **трёх** схем». **Часть K добавила четвёртую (`system-settings.pool`)** — FR-017 устарел. Также persistent stores (WizardCheckpoint, UserPreferences) не упомянуты в roundtrip требованиях.
  - **Fix**: переписать FR-017 → «для каждой из **четырёх** JSON-схем» + добавить FR про roundtrip persistent formats.

- [✓] **CHK011** Backward-compat test exists.
  - FR-018 mechanism для JSON ✓.
  - **Для persistent stores** — без schemaVersion невозможен. После CHK001 fix добавится.

- [✓] **CHK012** Test fixtures stored as files.
  - Все fixtures в Local Test Path — `.json` файлы в `commonTest/resources/`. ✓

## Persistence specifics

- [⚠] **CHK013** SharedPreferences/DataStore keys namespaced.
  - Спека не определяет explicit key naming. Implementation detail для plan.md. ⚠ acceptable foundation deferral.

- [N/A] **CHK014** SQLDelight migrations. F-3 не использует SQLDelight.

- [N/A] **CHK015** Removed types cleanup. F-3 не removes existing types.

## Deep-link / QR / exported config

- [N/A] **CHK016** URL/QR payload `schemaVersion`. F-3 не определяет deep-link / QR formats (это спека 007 pairing).
- [✓] **CHK017** Truncated/corrupted payload yields user-facing error.
  - FR-019: `ConfigSourceResult.ParseError(reason)`. ✓
  - FR-016: `ConfigSourceResult.IncompatibleVersion`. ✓

## Contract folder

- [⚠] **CHK018** `contracts/` folder optional.
  - F-3 не имеет explicit `specs/015-*/contracts/` folder. JSON schemas determined в spec.md FRs.
  - Спека 008, 014 имеют `contracts/`. F-3 может добавить как best practice — `contracts/wire-format.md` со schema definitions + migration policy.
  - **Recommendation**: создать `specs/015-*/contracts/wire-formats.md` который extracts FR-011..018 + FR-052..053 schemas в один документ. Не блокирует, но повышает clarity для plan.md.

---

## Issues & fixes

### Issue WF-1 — FR-017 outdated (CHK010, severity Low — stale count)

**Fix**: переписать FR-017:
```
- **FR-017**: System MUST содержать roundtrip-тест **для каждой** из ЧЕТЫРЁХ JSON-схем
  (wizard.manifest, screen.layout, tile.set, system-settings.pool): serialize fixture
  → deserialize → assert structural equality + assert localization keys not literal strings.
```

### Issue WF-2 — `WizardCheckpoint` missing `schemaVersion` (CHK001, severity Medium)

**Fix**: дополнить FR-003:
```
- **FR-003**: ... Checkpoint содержит: `schemaVersion: Int = 1`, `manifestId: String`,
  `currentStepIndex: Int`, `answers: Map<StepId, JsonElement>`. При load checkpoint'а
  с `schemaVersion > 1` — engine considers checkpoint invalid → starts wizard from step 0
  (graceful, не crash; редко-возможный случай app downgrade).
```

### Issue WF-3 — `UserPreferences` missing `schemaVersion` (CHK001, severity Medium)

**Fix**: дополнить FR-047:
```kotlin
data class UserPreferences(
    val schemaVersion: Int = 1,        // wire format version per CLAUDE.md rule 5
    val theme: ThemeChoice,
    val fontScale: Float?,
    val languageOverride: String?,
    val attestedSettings: Map<String, AttestationRecord>
)
```
Плюс: при load с unknown future `schemaVersion` — store возвращает defaults (graceful migration: бабушка проходит wizard заново, prefs reset; **не** crash).

### Issue WF-4 — `CONTEXT.json` missing `schemaVersion` (CHK001, severity Low)

**Fix**: дополнить FR-031b:
```json
{
  "schemaVersion": 1,
  "entries": {
    "wizard.next_button": { "value": "Next", "context": "...", "screenshot": null }
  }
}
```
Translation skill validates `schemaVersion` matches expected; mismatched → skill fails с сообщением «CONTEXT.json schemaVersion mismatch — update translation skill or revert».

### Issue WF-5 — Unknown `SettingMechanism` not explicit (CHK009, severity Low)

**Fix**: дополнить FR-053:
```
... Unknown `mechanism` value в pool entry → AndroidSystemSettingAdapter возвращает
SettingStatus.NotSupportedOnPlatform для этого settingId; остальные entries
обрабатываются нормально (forward-compat для будущих mechanisms добавленных в новых
версиях `system-settings.pool` schema).
```

### Issue WF-6 (optional) — `contracts/` folder для clarity

**Recommendation**: создать `specs/015-wizard-localization-senior-ui/contracts/wire-formats.md` со схемами + migration policy. Не блокирует переход в plan.md. Добавим во время `speckit-plan` или skip.

---

## Резюме

**13 ✓ / 1 ⚠ / 4 ✗** — четыре fix'а:

- **WF-1**: FR-017 «трёх» → «четырёх» schemas (cosmetic)
- **WF-2**: WizardCheckpoint += `schemaVersion`
- **WF-3**: UserPreferences += `schemaVersion`
- **WF-4**: CONTEXT.json += `schemaVersion`
- **WF-5**: explicit unknown `mechanism` handling

Applying все пять inline.
