# Checklist: meta-minimization (plan-level)

**Spec**: [`spec.md`](../spec.md) (rev. 1 — post-`/speckit.clarify` 2026-05-15)
**Plan**: [`plan.md`](../plan.md) (rev. 1 — post-`/speckit.plan` 2026-05-15)
**Data-model**: [`data-model.md`](../data-model.md)
**Research**: [`research.md`](../research.md)
**Prior run**: [`meta-minimization.md`](meta-minimization.md) (spec-level, 10/13 PASS + 3 watch).
**Run**: 2026-05-15 — before `/speckit.tasks`.

Plan-level anti-bloat audit per Article XI of [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md) and rule 4 of [`CLAUDE.md`](../../../CLAUDE.md). Focus: 5 new ports, 5 new packages, 8-week phase roadmap, and 3 spec-level watch items projected onto concrete plan constraints.

---

## Inventory of new abstractions introduced by plan.md

### Ports (5 NEW, all in `:core/commonMain`)

| # | Port | File | Consumers in спека 9 | Implementations in спека 9 |
|---|---|---|---|---|
| P1 | `ConfigHistoryRepository` | `api/history/ConfigHistoryRepository.kt` | `EditorScreen.publish` (FR-036), `HistoryScreen` (FR-037..040), `RollbackUseCase` (FR-041) | 1 real (`FirestoreConfigHistoryAdapter`) + 1 fake (`InMemoryConfigHistoryRepository`) |
| P2 | `InstalledAppsCatalog` | `api/apps/InstalledAppsCatalog.kt` | `TileEditForm` app-picker (FR-034) | 1 real (`InstalledAppsCatalogAdapter`) + 1 fake |
| P3 | `SystemContactPicker` | `api/contacts/SystemContactPicker.kt` | `ContactPickerLauncher` (FR-024) | 1 real (`SystemContactPickerAdapter`) + 1 fake |
| P4 | `VCardImporter` | `api/contacts/VCardImporter.kt` | `VCardReceiveActivity` (FR-027/027a/028) | 1 real (`VCardImportAdapter`) + 1 fake |
| P5 | `OpenAppDispatcher` (extended) | `api/apps/OpenAppDispatcher.kt` | Open-app tile при tap (FR-035/035a) | 1 real (`OpenAppDispatcherAndroid`) + 1 fake |

### Packages (5 NEW под `api/`)

| Package | Types | Justified by ≥2 types? |
|---|---|---|
| `api/contacts/` | `SystemContactPicker`, `RawPickerContact`, `PickError`, `VCardImporter`, `RawVCard`, `ImportError` | ✅ 6 types |
| `api/history/` | `ConfigHistoryRepository`, `ConfigSnapshotWithId`, `RepositoryError` | ✅ 3 types |
| `api/apps/` | `InstalledAppsCatalog`, `InstalledApp`, `IconRef`, `OpenAppDispatcher` (extended), `OpenAppResult` | ✅ 5 types |
| `api/admin/` | `AdminEditorMode` (enum), `EditorState`, `MergeConflictState` | ✅ 3 types |
| `ui/health/` | `PhoneHealthIndicator`, `PhoneHealthSeverity`, `PhoneHealthPreset`, `HealthToPhoneIndicatorAdapter`, `DEFAULT_PHONE_HEALTH_PRESET` | ✅ 5 types |

### Phase roadmap (§10)

8 phases × 0.5-1.5 weeks = ~8 weeks total. Each phase has a named deliverable + verification gate.

---

## CHK001 — Every new port has at least one concrete consumer **in this spec**

- ✅ **P1 `ConfigHistoryRepository`** — 3 consumers (EditorScreen FR-036, HistoryScreen FR-037..040, RollbackUseCase FR-041).
- ✅ **P2 `InstalledAppsCatalog`** — 1 consumer (TileEditForm FR-034). Single but real.
- ✅ **P3 `SystemContactPicker`** — 1 consumer (ContactPickerLauncher FR-024). Single but real.
- ✅ **P4 `VCardImporter`** — 1 consumer (VCardReceiveActivity FR-027/028).
- ✅ **P5 `OpenAppDispatcher`** — existing port (spec 003); plan only documents extended contract. Consumer = OpenApp tile in HomeScreen/FlowScreen.

**Verdict**: PASS. Все ports потребляются в этом спеке.

## CHK002 — Single-implementation ports justified by port-shape need

Все 5 ports имеют **2 implementation'а в спеке 9** (real + fake), что прямо удовлетворяет CLAUDE.md rule 6 (mock-first) и Article XI exception "fakes for tests".

- ✅ **P1**: real Firestore + in-memory fake; необходим для contract roundtrip-теста (FR-036 → readAll → housekeep) без живого Firestore. Wire-format ownership = clear boundary.
- ✅ **P2**: real PackageManager + fake; необходим для UI-теста TileEditForm без устройства.
- ✅ **P3**: real ContactsContract + fake; необходим для тестирования permission denial / cancel flow без интеграционного теста.
- ✅ **P4**: real VCard parser + fake; **критически** необходим для контракт-теста с 4-5 real VCard samples (WhatsApp / Telegram / system Contacts) — без port'a это превращается в Android-instrumented integration.
- ✅ **P5**: real Intent dispatch + fake; необходим для теста fallback chain (launcher → market → web) без эмулятора Play Store.

**Verdict**: PASS. Port-shape oправдан в каждом случае через DI / mock-first / contract test.

## CHK003 — No mediator/orchestrator/manager classes

Plan §2 модули и data flows:
- `EditorScreen`, `EditorViewModel` — Compose VM, не manager.
- `ConfigPublishUseCase` — **inherited из спека 8** (не новый в спеке 9).
- `HealthToPhoneIndicatorAdapter` — **adapter** (data transformation), не оркестратор.
- `OpenAppDispatcher` — **dispatcher** в смысле «выбор fallback target» (3 шага: launcher → market → web), это **transformation logic**, не pass-through. Justified.

**Verdict**: PASS. Никаких `*Manager` / `*Coordinator` / `*Mediator` классов в plan.md.

## CHK004 — No custom DSL / registry / plugin system

Plan §2 архитектура — port/adapter pattern (CLAUDE.md rule 2), Compose `Modifier.dragAndDropSource/Target` (built-in API), Firestore SDK (vendor). Никаких самописных DSL / ServiceLoader / plugin registry. Per-provider adapters для контактов (FR-024, FR-028) — это **обычные классы реализующие интерфейс**, не plugin system (нет dynamic discovery, нет ServiceLoader).

**Verdict**: PASS.

## CHK005 — New gradle module: Article V §3 criteria

Plan §2 явно: **no new gradle modules**. Все 5 новых packages — внутри existing `:core` (`commonMain` и `androidMain`) и `:app`. Plan §5 «Dependency impact» подтверждает: **no new gradle deps**.

**Verdict**: PASS — критерий не активируется.

## CHK006 — "Why is a package not enough?"

Plan §2 implicitly отвечает: packages enough. Никаких gradle модулей не вводится.

**Verdict**: PASS.

## CHK007 — No "utils"/"common"/"helpers" dumping ground

Plan §2 имена packages — `api/contacts/`, `api/history/`, `api/apps/`, `api/admin/`, `ui/health/`. Каждое имя — **domain concept**, не «utils/common/misc». Adapter package `core/androidMain/adapters/` — единственный package с generic именем, но он по convention'у CLAUDE.md rule 2 (все Android adapters в одном месте, по принципу ownership boundary, не "helpers").

**Verdict**: PASS.

## CHK008 — New config field has current FR consumer

Plan §3 / `data-model.md` §4-5 / `contracts/config-current-additions.md`:

- ✅ `ConfigSnapshot.*` — все 4 поля consumed FR-036..043.
- ⚠️ **`PresetSettings` / `PhoneHealthSettings`** — **NO consumer в спеке 9**. Plan §3 явно: «forward-compat... всегда null в спеке 9». **Спек-level watch item #1.**

**Plan-level constraint** (что plan конкретно говорит про speculative field):
- `data-model.md` §4: `phoneHealthSettings: PhoneHealthSettings? = null` — single nullable field.
- `data-model.md` §5: 8-полевая `PhoneHealthSettings` struct + `SeverityWire` enum — **полный struct**, не placeholder.

**Это эскалация vs спек-level**: спек-level говорил «1 nullable field, бесплатное». Plan говорит «full 8-field struct + enum». **Cost удержания вырос**.

**Mitigating factors**:
- `SeverityWire` enum (3 значения) — закрытый, минимальный, не abstraction layer.
- Полный struct в коде нужен **только** для roundtrip-теста forward-compat (`ConfigCurrentNonNullPresetRoundtripTest` — Plan §6). Без struct тест нечем заполнить.
- Wire format отделён от UI (`SeverityWire` ≠ `PhoneHealthSeverity`) — это правильное CLAUDE.md rule 1 separation.

**Verdict для plan-level**: ⚠️ **Watch — confirmed**. Plan **усилил** speculative footprint (full 8-field struct vs 1 nullable field в спеке), но cost удержания всё ещё пренебрежимо мал (data class + serialization annotations).

**Plan-level action item**: добавить в `PresetSettings.kt` inline-TODO exit-ramp comment per memory `feedback_exit_ramps_as_todos`:
```kotlin
// TODO-FUTURE-SPEC-005 (preset-editor): если spec 010/011 не введёт consumer
// для phoneHealthSettings в течение 6 месяцев — удалить весь struct.
// Re-introduction cost через additive policy (spec 008 FR-006) = 1 час.
@Serializable
data class PresetSettings(...)
```

## CHK009 — Backward-compat / migration policy

Plan §4 (Wire formats) + `contracts/`:

- ✅ `ConfigSnapshot.snapshotSchemaVersion = 1` from first commit (data-model §3).
- ✅ `ConfigSnapshot.config.schemaVersion` — наследуется из ConfigCurrent (spec 008).
- ✅ `presetOverrides` — additive по policy спека 8 FR-006, NO schemaVersion bump.
- ✅ Backward-compat tests: `ConfigCurrentSchemaV1Test` + `ConfigSnapshotForwardSchemaTest` (Plan §6).
- ✅ Migration policy: TODO-ARCH-015 lazy transformers (Research R-002 + R-012).
- ✅ Retention: FR-038 (10 snapshots, housekeep on push), exit ramp → SRV-CONFIG-002 (Research R-006).

**Verdict**: PASS.

---

## CLAUDE.md rule 4 — Test 1 & Test 2 per port

### P1 `ConfigHistoryRepository`

**Test 1 (inline → что потеряем?)**: Inline alternative — `EditorViewModel` напрямую вызывает `FirebaseFirestore.collection("links/$linkId/configHistory")...`. Loss:
- Roundtrip contract test (Plan §6) невозможен без живого Firestore — превращается в Android-instrumented test, требующий emulator suite + service account.
- Housekeeping logic (FR-038, retention=10) разбрасывается по UI code; concurrent housekeeping race (Research R-006 regret condition) не unit-тестируется.
- Future server-side migration (SRV-CONFIG-001, Research R-005 exit ramp): без port'a — переписать все call sites; с port'om — заменить один adapter. Saving: ~2 недели.
- **Verdict**: Port justified ✅.

**Test 2 (Firestore deprecated / удвоилась в цене / privacy violation — сколько займёт swap?)**:
- Firestore deprecated → Supabase / own-backend: real adapter swap ≈ 1-2 недели (тот же estimate что у `/config/current` спека 8, потому что share same infrastructure pattern). С port'om — atomic swap. Без port'a — refactor через все UI слои.
- Privacy: если Firestore privacy compliance ужесточится (GDPR transfer-to-processor) — adapter swap на self-hosted ≈ 2-3 недели. С port'om — clean. **Seam justified.**
- **Verdict**: Port justified ✅.

### P2 `InstalledAppsCatalog`

**Test 1**: Inline — `TileEditForm` напрямую вызывает `context.packageManager.queryIntentActivities(...)`. Loss:
- UI-test TileEditForm требует Android instrumented context; fake adapter позволяет Compose UI test в `:core/androidUnitTest` (Plan §6).
- `ResolveInfo` → `InstalledApp` mapping (drop own app, sort by label) — без adapter становится duplicated в каждом call site (Plan §3 mentions 2 потенциальных users в спеке 9: TileEditForm + future per-Managed app filter в спеке 011).
- Domain isolation: `ResolveInfo` / `Drawable` в UI = CLAUDE.md rule 1 violation. Konsist gate #3 (Plan §6) lint'ит именно это.
- **Verdict**: Port justified ✅ (Konsist gate enforced).

**Test 2 (PackageManager deprecated / privacy)**:
- PackageManager — Android stable. Privacy: Android 11+ `<queries>` уже добавлен (Plan §5). Если требования ужесточатся (Android 16+ scoped package visibility) — adapter swap ≈ 2-3 дня.
- **Verdict**: Port justified ✅ (privacy seam).

### P3 `SystemContactPicker`

**Test 1**: Inline — `ContactPickerLauncher` напрямую запускает `ActivityResultContracts.PickContact` + парсит `Uri` через `ContactsContract.CommonDataKinds.Phone`. Loss:
- Контракт-тест (Plan §6: «Validates URI → Contact.fromRaw() flow») невозможен без real device + contacts DB.
- `Uri` / `Cursor` / `ContactsContract` constants утекают в UI code → CLAUDE.md rule 1 violation. Konsist gate #1 ловит это.
- Permission denial path (FR-023b deep-link to Settings) — без adapter нет clean place для error mapping; fragmented по UI.
- **Verdict**: Port justified ✅.

**Test 2 (ContactsContract deprecated / privacy ужесточение)**:
- READ_CONTACTS — Android stable, маловероятно deprecation. Privacy: GDPR (Маша — 3rd party PII) уже addressed через `data_extraction_rules.xml` exclusion (FR-046a). Если требования ужесточатся (Android Privacy Sandbox для contacts) — adapter swap ≈ 1 неделя.
- **Verdict**: Port justified ✅.

### P4 `VCardImporter`

**Test 1**: Inline — `VCardReceiveActivity` парсит payload inline (~100 LOC parser). Loss:
- Contract test с 4-5 real VCard samples (Plan §6) — невозможен без extracted parser interface.
- Sample suite reuse в будущем спеке (если 011 добавит iCal / org-mode import) — без port'a parser logic размазана.
- Domain isolation: `ByteArray` payload + `RawVCard` mapping — на границе. Без adapter mapping остаётся в UI = `RawVCard` всплывает в Composable. Acceptable, но adapter cleaner.
- **Verdict**: Port justified ✅, но **slimmer** чем P1-P3. Если бы был только 1 sample test — port был бы avoidable.

**Test 2 (VCard format deprecated)**:
- VCard RFC stable. Если бы Android 16 ввёл native JSON-vcard (гипотетически) — adapter dual-format ≈ 2-3 дня.
- **Если ezvcard library понадобится** (Research R-008 exit ramp): swap adapter implementation ≈ 2 дня. С port'om — atomic; без — нужно искать все references к hand-written parser. **Seam justified through exit ramp.**
- **Verdict**: Port justified ✅.

### P5 `OpenAppDispatcher` (extended)

**Test 1**: Уже существующий port из спека 003. Не новый в спеке 9. Plan только **расширяет contract** (add `OpenedPlayStore` / `OpenedWebPlayStore` results). Loss inline'а уже принят в спеке 003.
- **Verdict**: Port pre-existing ✅. Plan-level inline analysis не применим.

**Test 2 (Play Store deprecated / `market://` schema removed)**:
- Уже addressed FR-035 fallback chain: launcher → `market://` → `https://play.google.com/...`. Если `market://` deprecated — fallback на web. С port'om — adapter добавляет 4-й fallback. ≈ 1 день.
- Huawei AppGallery (Research R-? + Plan §7 R6) — без GMS, port позволяет inject Huawei-specific adapter. **Seam justified.**
- **Verdict**: Port justified ✅.

---

## Spec-level watch items — projected onto plan-level constraints

### Watch item #1: `presetOverrides: PresetSettings? = null` — speculative field

**Spec-level state**: 1 nullable field, всегда null. Rule 4 Test 1 → mild speculative, retain with exit-ramp comment.

**Plan-level state** (data-model.md §4-5): **full 8-field `PhoneHealthSettings` struct + `SeverityWire` enum**. Polly не 1 nullable field — это **полный wire schema** под будущий consumer.

**Эскалация**: plan усилил footprint vs спек.

**Plan-level constraint** (что plan конкретно ограничивает):
- ✅ Wire format отделён (`SeverityWire` ≠ UI `PhoneHealthSeverity`) — правильное rule 1 separation.
- ✅ Konsist gate #2 (Plan §6) lint'ит, чтобы `api/history/*.kt` не импортировали Firebase — косвенно усиливает isolation.
- ⚠️ **No explicit "no protobuf registry" constraint** в plan.md. План не запрещает добавить generic schema registry / proto-style code generation. **Risk**: при появлении preset-editor спека (010/011) кто-то введёт `SchemaRegistry<T>` для генерации `SeverityWire` <-> `PhoneHealthSeverity` mapping'ов.

**Action item для plan.md**:
- Добавить explicit constraint в §5 (Dependency impact) или §2 (Architecture):
  > **No schema registry / protobuf code-gen**: `SeverityWire` ↔ `PhoneHealthSeverity` mapping — это **ручной `fun toUi() / fun toWire()`** на enum, не generated. Если в будущем (спека 012+) wire enums размножатся (≥ 5 разных wire→UI mappings), пересмотреть.
- **Verdict**: ⚠️ watch carried forward с plan-level escalation note.

### Watch item #2: `PhoneHealthCriticalEvent` — event без подписчика

**Spec-level state**: эмитится из severity computation, нет subscriber. Constrain: 1-line `SharedFlow.emit`, no EventBus framework.

**Plan-level state**: ⚠️ **plan.md / data-model.md / research.md не упоминают `PhoneHealthCriticalEvent` явно**. Поиск по plan §10 phases — Phase 2 (Health monitoring UI) описывает severity computation, но event'a в data-model.md §11-13 нет.

**Это либо**:
- (a) Plan **молчаливо удалил** event'a → нарушение spec contract (Spec-level CHK002 retained event как conscious decision).
- (b) Event implicit где-то в `PhoneHealthIndicator` viewmodel layer и просто не задокументирован.

**Plan-level constraint требуется**:
- Если event сохраняется — добавить в data-model.md §11 (PhoneHealthIndicator) или новый §15 entity с явным:
  > `_phoneHealthCriticalEvents: MutableSharedFlow<PhoneHealthCriticalEvent>` — emission в `HealthToPhoneIndicatorAdapter.computeSeverity()` при Info→Critical transition. **No subscriber в спеке 9**. **Constraint**: ровно один emit site, ровно один SharedFlow, no EventBus / Dispatcher / Registry pattern. Unit test `PhoneHealthSeverityTransitionEventTest` проверяет emission.

**Verdict для plan-level**: ⚠️ **Watch escalated** — plan should explicitly document event constraint или signal removal.

### Watch item #3: `ConfigSnapshot` dual schemaVersion

**Spec-level state**: conscious decision (C2 clarification); flag retrospective при первом config schema bump.

**Plan-level state**: Research R-002 явно фиксирует решение + exit ramp (collapse to single field, 2 дня). Data-model §3 явно: `snapshotSchemaVersion: Int = SUPPORTED_SNAPSHOT_SCHEMA_VERSION` (envelope) + `config: ConfigCurrent` (nested with own schemaVersion).

**Plan-level constraint** (что plan конкретно ограничивает):
- ✅ Research R-002 documented exit ramp (~2 дня collapse).
- ✅ Plan §9 Constitution Check Gate 3 явно ссылается: «`ConfigSnapshot.snapshotSchemaVersion = 1` explicit».
- ✅ TODO-ARCH-015 lazy transformers (Research R-012) — concrete tracker.
- ⚠️ **No explicit "no protobuf registry" constraint** для transformer chain. Если transformer chain вырастет до 4+ hops (R-012 regret condition), кто-то может ввести `TransformerRegistry<TVersion, TConfig>` generic infra.

**Action item для plan.md**:
- Добавить в §10 Phase 0 (Foundation) или §6 (Test strategy):
  > **No transformer registry / code-gen**: `SnapshotMigrator.migrate(raw, fromVersion, toVersion)` — это **explicit `when (fromVersion)` chain** в одном файле, не generated. Если cascade chain превысит 4 hops (TODO-ARCH-017 drop policy активируется), пересмотреть.

**Verdict**: ⚠️ watch carried forward, exit ramp documented.

---

## 8-Week Phase Roadmap audit (Plan §10)

**Hypothesis**: ищем (a) «framework» phase (Phase 0 «build the infrastructure» без deliverable), (b) phases без verification gate.

| Phase | Weeks | Deliverable | Verification | Verdict |
|---|---|---|---|---|
| Phase 0 — Foundation | 1.0 | 4 domain types + 5 ports + 5 fakes + Konsist gates × 5 + roundtrip contracts | All contract + roundtrip tests green; Konsist gates pass | ✅ Concrete |
| Phase 1 — Adapters | 1.0 | 5 real adapters + Security Rules + Manifest changes + backup XML | Real-adapter contract tests + firestore-tests green | ✅ Concrete |
| Phase 2 — Health monitoring UI | 0.5 | PhoneHealthIndicator + AdminDevicesScreen + listener wiring + vector icons | Manual TalkBack walkthrough US-2; accessibility scanner clean | ✅ Concrete |
| Phase 3 — Editor scaffold | 1.5 | TileCard / FlowScreen / BottomFlowBar / HomeScreen `editMode` + EditorScreen + TileEditForm + autosave | Manual edit + save + recreation test; SC-001 90-sec scenario | ✅ Concrete |
| Phase 4 — Drag-and-drop | 1.5 | `Modifier.dragAndDropSource/Target` + drop targets + alt button | NFR-001 macrobenchmark; manual DnD; manual TalkBack | ✅ Concrete + gate |
| Phase 5 — Contacts | 1.0 | SystemContactPicker flow + permission rationale + manual entry + VCardReceiveActivity + parser + ContactsManageScreen | NFR-002 microbenchmark; OEM matrix; privacy walkthrough | ✅ Concrete + gate |
| Phase 6 — History + rollback | 1.0 | FirestoreConfigHistoryAdapter + housekeeping + HistoryScreen + rollback flow + schema validation | End-to-end manual: edit → push → rollback → conflict | ✅ Concrete |
| Phase 7 — OpenApp tiles | 0.5 | OpenAppDispatcher fallback chain + InstalledAppsCatalog UI selector | OEM matrix Pixel + Samsung + Huawei | ✅ Concrete |
| Phase 8 — Verification & ship | 0.5 | Cross-artifact trace + `/speckit.analyze` + checklist re-run + perf-checkpoint + smoke-checkpoint | All gates green pre-release | ✅ Concrete |

**Verdict**: ✅ **No framework phases.** Phase 0 не «build the platform» — это «4 типа + 5 ports + 5 fakes + 5 Konsist gates + contracts». Каждое имеет verification gate.

**Watch**: Phase 4 (Drag-and-drop, 1.5w) — самая большая по weeks (вместе с Phase 3). NFR-001 macrobenchmark gate ловит regret condition R1 (Research R-004) — `Modifier.dragAndDropSource` глюки. Если macrobench fails — есть two-way door на `pointerInput` (Research R-004 exit ramp, 3-5 дней).

**Phase 0 + Phase 1** combined = 2 weeks of pure infra (ports + adapters + Konsist + contracts) **before** first UI deliverable (Phase 2 Health UI at week 2.5). Это **типичный** spec-kit pattern (mock-first per CLAUDE.md rule 6), не «framework phase». Phase 0 deliverables — concrete (4 типа + 5 файлов), а не «abstract foundation». **Pass.**

---

## Summary table

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 10 | CHK001, CHK002, CHK003, CHK004, CHK005, CHK006, CHK007, CHK009, CHK010 (all 5 ports), CHK011 (all 5 ports) |
| ⚠️ Watch (carried + escalated) | 3 | CHK008 (presetOverrides — plan усилил footprint до full 8-field struct), W2 (PhoneHealthCriticalEvent — plan молчит, needs explicit constraint), W3 (dual schemaVersion — no transformer-registry constraint) |
| ❌ Fail | 0 | — |

**Verdict для plan.md**: **PASS** с 3 watch-item'ами. Все 5 NEW ports проходят CLAUDE.md rule 4 Test 1 + Test 2. Все 5 new packages justified ≥ 2 types. 8-week phase roadmap — no framework phase. **Spec-level дисциплина сохранена на plan-level**, с одним escalated finding (W1: plan усилил `presetOverrides` footprint vs спек).

---

## Top 3 potential over-abstractions (plan-level)

### 1. ⚠️ `PhoneHealthSettings` 8-field struct + `SeverityWire` enum (data-model §5)

**Plan-level issue**: спек обещал «1 nullable field, всегда null». Plan материализовал **полный wire schema** под будущий consumer (8 полей + closed enum + per-field invariants). Это **escalation**, не projection.

**Mitigation**:
- Полный struct нужен для `ConfigCurrentNonNullPresetRoundtripTest` (Plan §6) — forward-compat smoke. Без struct тест нечем заполнить.
- `SeverityWire` отделён от UI `PhoneHealthSeverity` — правильное rule 1 separation, не violation.

**Action item для plan.md (PRE-tasks)**: добавить в `PresetSettings.kt` (или новый `PhoneHealthSettings.kt` если выделится) inline-TODO exit-ramp:
```kotlin
// TODO-FUTURE-SPEC-005 (preset-editor): full struct exists ONLY for forward-compat roundtrip test.
// If preset-editor spec (010/011) does NOT materialise within 6 months — delete entire struct + SeverityWire.
// Re-introduction via additive policy (spec 008 FR-006) = 1 hour for nullable field, 1 day for full struct.
```

Дополнительно: добавить в Plan §2 или §5 constraint:
> **No schema registry / code-gen for wire↔UI mappings**: `SeverityWire.toUi()` / `PhoneHealthSeverity.toWire()` — manual enum mapping (3 cases), not generated. If wire enums multiply (≥ 5 mappings in спека 012+) — re-evaluate.

### 2. ⚠️ `PhoneHealthCriticalEvent` silently dropped (or not documented) in plan

**Plan-level issue**: спек §3 fixed эту watch item с constraint «1-line SharedFlow.emit, no EventBus». Plan.md / data-model.md / research.md не упоминают event явно. **Либо**:
- (a) Plan молчаливо удалил event (нарушение spec contract);
- (b) Event implicit где-то и не задокументирован (нарушение plan completeness).

**Action item для plan.md (PRE-tasks)**:
Добавить в data-model.md §11 (PhoneHealthIndicator) или новую секцию:
```kotlin
// In HealthToPhoneIndicatorAdapter:
private val _criticalEvents = MutableSharedFlow<PhoneHealthCriticalEvent>(extraBufferCapacity = 1)
val criticalEvents: SharedFlow<PhoneHealthCriticalEvent> = _criticalEvents.asSharedFlow()

// Emitted exactly once when severity transitions Info→Critical or Warning→Critical.
// No subscriber in spec 009. Future subscriber: SRV-MONITOR-001 (FCM push).
// CONSTRAINT: single emit site, single SharedFlow. NO EventBus / Dispatcher / Registry.
```

Plus unit test `PhoneHealthSeverityTransitionEventTest` в Plan §6.

### 3. ⚠️ `ConfigSnapshot` dual schemaVersion — transformer-registry risk

**Plan-level issue**: dual versioning supports independent evolution, but plan doesn't constrain HOW transformer chain (TODO-ARCH-015) implements migrations. Risk: при первом config schema bump кто-то введёт generic `TransformerRegistry<Version, Config>` infra.

**Action item для plan.md (PRE-tasks)**: добавить в Plan §6 (Test strategy) или §10 Phase 0 footnote:
> **No transformer registry**: `SnapshotMigrator.migrate(raw, fromV, toV)` implements as explicit `when (fromV)` chain in single file. NO `TransformerRegistry`, NO code-gen, NO `Map<Version, Transformer>` lookup. If chain exceeds 4 hops (TODO-ARCH-017 drop-policy activation point) — re-evaluate, possibly switch to drop-policy instead of registry.

---

## Нужно ли удалять что-то из plan.md

**Hard remove**: **нет**. Plan disciplined, no premature gradle modules, no DSL, no registries, no managers.

**Hard constrain (PRE-tasks action items)**:
1. **`PresetSettings` exit-ramp comment** (W1) — inline-TODO with 6-month deletion condition.
2. **`PhoneHealthCriticalEvent` explicit documentation** (W2) — add to data-model.md with SharedFlow constraint.
3. **Transformer chain non-registry constraint** (W3) — add to Plan §6 / §10 Phase 0.

**Optional**:
- Plan §2 mentions `ui/health/` package как `:app/health-ui/` в нескольких местах + `core/.../ui/health/` в других. **Inconsistency** между текстами; нужно зафиксировать одно местоположение в tasks.md. Не блокирующее.
- Plan §10 Phase 2 (0.5w) — самая короткая phase. Если в реальности окажется > 1w (TalkBack passes + accessibility scanner edge cases), это **акустический сигнал** что Health UI больше чем 0.5w; не блокирующее сейчас.

---

## Action items для tasks.md / implementation

1. **W1 (`PresetSettings`)**: добавить task «add inline-TODO exit-ramp comment to `PresetSettings.kt` and `PhoneHealthSettings.kt` per memory `feedback_exit_ramps_as_todos`».
2. **W2 (`PhoneHealthCriticalEvent`)**: добавить task «document event emission in `HealthToPhoneIndicatorAdapter` with explicit constraint (SharedFlow, no EventBus) + add `PhoneHealthSeverityTransitionEventTest`».
3. **W3 (transformer chain)**: добавить task в Phase 0 или Phase 6 «document `SnapshotMigrator` no-registry constraint in code comment + Konsist gate (optional)».
4. **Plan inconsistency**: resolve `app/health-ui/` vs `core/.../ui/health/` location — pick one в tasks.md Phase 2.

---

## TL;DR на русском

**Вердикт plan-level**: **PASS** с 3 watch-item'ами (carried forward со спек-level + 1 escalated).

**Распределение**:
- ✅ **10 PASS**: все 5 NEW ports (ConfigHistoryRepository, InstalledAppsCatalog, SystemContactPicker, VCardImporter, OpenAppDispatcher-extended) проходят Test 1 (что потеряем при inline) + Test 2 (стоимость swap при deprecation). Все 5 новых packages (`api/contacts/`, `api/history/`, `api/apps/`, `api/admin/`, `ui/health/`) justified ≥ 2 types каждый. No new gradle modules. No DSL/registry/plugin/manager classes. 8-week roadmap — **no framework phase**: Phase 0 (1w) деливерит конкретные 4 типа + 5 ports + 5 fakes + Konsist gates, не «abstract foundation».
- ⚠️ **3 Watch** (с эскалацией для W1):
  - **W1 escalated**: спек обещал `presetOverrides` как 1 nullable field, plan материализовал **full 8-field `PhoneHealthSettings` struct + `SeverityWire` enum**. Это нужно для forward-compat roundtrip-теста (без struct тест нечем заполнить), и wire↔UI separation правильное (`SeverityWire` ≠ `PhoneHealthSeverity` — rule 1 compliant). Но cost удержания вырос vs спек. **Action**: inline-TODO exit-ramp comment + plan-level constraint «no schema registry для wire↔UI mappings».
  - **W2**: `PhoneHealthCriticalEvent` (фиксированный watch на спек-level) **не упомянут** в plan.md / data-model.md / research.md. Либо молчаливо удалён, либо implicit. **Action**: явно задокументировать в data-model.md (одна `MutableSharedFlow`, одна точка emit, no EventBus).
  - **W3**: `ConfigSnapshot` dual schemaVersion (envelope + nested config) — research R-002 documented exit ramp. **Action**: добавить plan-level constraint «no transformer registry — explicit `when (fromV)` chain в одном файле, не generic infra».

**Top 3 potential over-abstractions**:
1. `PhoneHealthSettings` 8-field wire struct без consumer'a в спеке 9 (W1).
2. `PhoneHealthCriticalEvent` без подписчика + не задокументирован в plan (W2).
3. `ConfigSnapshot` dual schemaVersion без constraint'a на implementation transformer chain (W3).

**Нужно ли удалять что-то из plan.md**: **нет**. Plan дисциплинированный. Действия — **constrain**, не **remove**: 3 inline-TODO / constraint'а в коде + data-model.md, плюс resolve inconsistency `app/health-ui/` vs `core/ui/health/` в tasks.md. Все 3 watch-item'а — soft constraints на implementation phase, не блокеры `/speckit.tasks`.

**Особое достоинство plan-level vs spec-level**: каждый из 5 ports имеет **real + fake adapter** в Plan §6 — это прямое удовлетворение CLAUDE.md rule 6 (mock-first) и Article XI exception "fakes for tests". Все 5 Konsist gates (Plan §6) lint'ят rule 1 domain isolation на уровне build pipeline — это правильная application Article VII §7 fitness functions.
