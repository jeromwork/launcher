# Checklist: domain-isolation — **plan-level** run

**Spec**: `spec.md` (rev. 2026-05-15, post-clarify)
**Plan**: `plan.md` (rev. 2026-05-15, 8 phases, Constitution Check 8/8 PASS)
**Run**: 2026-05-15 — post-`/speckit.plan`, pre-`/speckit.tasks`.
**Companion**: [`domain-isolation.md`](domain-isolation.md) (spec-level run, 16/16 PASS, 4 watch items).

Enforces [`CLAUDE.md`](../../../CLAUDE.md) rules 1 (Domain isolated from infrastructure) and 2 (Anti-Corruption Layer for every external dependency) + ADR-001 Platform Parity Gate.

**Focus per caller request**: проверить, что plan.md / data-model.md / contracts/ материализовали все action items из spec-level run в виде конкретного file layout, port shapes, gates и adapter placement.

---

## How plan-level differs from spec-level

| Aspect | Spec-level (previous run) | Plan-level (this run) |
|---|---|---|
| What's checked | «есть ли в спеке намёки на vendor leak»; FR-completeness | Конкретный module map; port signatures; file paths; Konsist rules; test strategy |
| Source artifact | `spec.md` only | `plan.md` + `data-model.md` + `contracts/*.md` |
| Output | 5 mandatory action items + 4 watch | Verify все action items закрыты + новые watch items на уровне deployment |

---

## Plan-level action-items audit (from spec-level run)

| # | Spec-level action | Plan-level location | Status |
|---|---|---|---|
| 1 | 5 NEW ports в `:core/commonMain/` | plan §2 Module map (lines 31-36); data-model §6-10 | ✅ all 5 present: `ConfigHistoryRepository`, `InstalledAppsCatalog`, `SystemContactPicker`, `VCardImporter`, `OpenAppDispatcher` |
| 2 | `Contact.fromRaw()` + `ValidationError` | data-model §1-2 (lines 18-95) | ✅ companion object factory; sealed `ValidationError`; pure Kotlin regex |
| 3 | Domain projections `RawPickerContact`, `RawVCard`, `ValidationError` | data-model §7-9 (lines 296-363); §2 | ✅ все 3 в `api/contacts/` / `api/apps/` |
| 4 | `ConfigSnapshot` с двумя schemaVersion | data-model §3 (lines 98-128); contracts/config-history.md §«Field schema» | ✅ envelope + nested independent versioning; reader fail-closed on `> SUPPORTED_VERSION` (FR-043) |
| 5 | 5 NEW androidMain adapters | plan §2 Module map (lines 49-55); §6 test strategy | ✅ все 5 в `:core/androidMain/adapters/` или `:core/androidMain/firestore/` |
| 6 | 5 NEW fakes | plan §6 Test strategy table (lines 207-211) | ✅ `FakeConfigHistoryRepository`, `FakeSystemContactPicker`, `FakeVCardImporter`, `FakeOpenAppDispatcher`, `FakeInstalledAppsCatalog` |
| 7 | Koin bindings — расширение `realBackendModule` / `mockBackendModule` | plan §10 Phase 0/1 verification | ⚠️ **plan не показывает** explicit DI module file paths и binding code. Sufficient для tasks.md generation (single mention в §6), но deserves a task entry. → see new watch W-PL-3. |
| 8 | Konsist gates — 5 NEW rules | plan §6 «Fitness functions (Konsist gates)» (lines 250-258) | ✅ все 5 явно перечислены: `api/contacts` no `android.*`, `api/history` no `com.google.firebase.*`, `api/apps` no `android.content.pm.*`, `Contact.fromRaw` return type, `TileCard` icon variation |
| 9 | Contract + roundtrip tests | plan §6 «Contract tests» (lines 203-211) + «Wire format roundtrip tests» (lines 214-218) | ✅ Roundtrip × 4 + per-port contract × 5 |
| 10 | ADR-001 Platform Parity confirm Android-only | plan §2 («Spec 009 — Android-only»), constitution check gate 1 | ✅ explicit |

**Verdict**: 9/10 action items fully materialized, 1 (DI wiring file paths) implicit but acceptable for plan-level.

---

## Inventory: external surfaces — re-validation against plan

Same 16 surfaces from spec-level run. **No new external surfaces** introduced by plan.md (plan stays within spec scope). Port mapping confirmed:

| Surface | Spec-level proposed port | Plan-level final port | Match |
|---|---|---|---|
| S1-S2 Firestore /config/current | re-use `RemoteSyncBackend` | re-use спека 8 | ✅ |
| S3-S5 Firestore /config/history | NEW `ConfigHistoryRepository` | `api/history/ConfigHistoryRepository.kt` (port) + `FirestoreConfigHistoryAdapter.kt` (androidMain) | ✅ note: spec-level называл «composite over RemoteSyncBackend», plan делает direct Firestore subcollection access — это OK (один adapter = один SDK boundary, CLAUDE.md rule 2 не нарушен) |
| S6 Firestore /health | re-use `HealthRepository` | re-use 006 | ✅ |
| S7 Room LocalConfigStore | re-use 008 | re-use 008 PendingLocalChangesRepository | ✅ |
| S8 Contacts SDK picker | NEW `SystemContactPicker` | `api/contacts/SystemContactPicker.kt` (port) + `SystemContactPickerAdapter.kt` (androidMain) | ✅ |
| S9 VCard intent | NEW `VCardImport` | renamed `VCardImporter` (data-model.md §9, plan §2) — naming change is OK | ✅ |
| S10 Installed apps list | NEW `InstalledAppsCatalog` | `api/apps/InstalledAppsCatalog.kt` + `InstalledAppsCatalogAdapter.kt` (androidMain) | ✅ |
| S11 OpenApp dispatch + Play Store fallback | NEW `OpenAppDispatcher` | `api/apps/OpenAppDispatcher.kt` + `OpenAppDispatcher.kt` adapter (androidMain) | ✅ (treats как «existing extended» — see data-model §10) |
| S12 READ_CONTACTS permission | re-use или NEW `PermissionRequester` | **Not explicitly addressed in plan.md** | ⚠️ W-PL-4 |
| S13 PackageManager getPackageInfo | inline в OpenAppDispatcher adapter | inline в `OpenAppDispatcher.kt` androidMain adapter | ✅ |
| S14 UUID | inline `kotlin.uuid.Uuid` | inline (existing ElementId.random()) | ✅ |
| S15 FCM push critical | out of scope, event-only | out of scope — `TODO-ARCH-012` в backlog (plan §9 Gate 6) | ✅ |
| S16 JsonObject в Slot.args | existing | unchanged | ✅ |

---

## Vendor SDKs

- [x] **CHK001 — No vendor SDK type in any signature visible to domain**

  Plan.md §2 Module map clearly separates `:core/commonMain` (ports, domain types) from `:core/androidMain/adapters/` (vendor wrapping). Data-model.md §6-10 port signatures use **only** Kotlin / domain types in return signatures:
  - `ConfigHistoryRepository.recordSnapshot(LinkId, ConfigSnapshot): Result<Unit, RepositoryError>`
  - `SystemContactPicker.pickContact(): Result<RawPickerContact, PickError>`
  - `VCardImporter.parse(ByteArray): Result<RawVCard, ImportError>`
  - `InstalledAppsCatalog.listApps(): List<InstalledApp>`
  - `OpenAppDispatcher.openApp(String): OpenAppResult`

  Никаких `DocumentSnapshot`, `Cursor`, `Uri`, `Intent`, `Drawable`, `ResolveInfo`, `ApplicationInfo` в return signatures. Plan §2 ACL shape diagram (lines 78-94) явно фиксирует «adapter-result type: `Result<DomainType, DomainError>`, никогда не `Uri`/`Cursor`/`Intent`».

- [x] **CHK002 — Each external SDK has exactly one wrapper module**

  Plan §2 — clear one-to-one mapping. New adapters:
  - Firebase Firestore /config/history → `FirestoreConfigHistoryAdapter` only.
  - ContactsContract → `SystemContactPickerAdapter` only.
  - VCard parser → `VCardImportAdapter` only.
  - PackageManager (admin) → `InstalledAppsCatalogAdapter` only.
  - PackageManager (Managed) + Intent system → `OpenAppDispatcher` (androidMain) only.

- [x] **CHK003 — Vendor-disappears test ≤ adapter module scope**

  Plan §5 Dependency impact + §7 R6 risk row confirm. Hand-written VCard parser (§5 line 188) — **explicit win** для disappearance test: парсер ~100 LOC в одном файле, заменим за день.

  Spec-level R4 (spoofing) + R5 (race) рассматривают **integrity attack vectors**, не vendor swap — orthogonal. Verdict ✅ unchanged.

## Transport types

- [x] **CHK004 — No transport types in domain signatures**

  Data-model §6 `ConfigHistoryRepository` использует `LinkId`, `ConfigSnapshot`, `ConfigSnapshotWithId`, `RepositoryError` — все pure domain. **Никаких** `Task<>`, `QuerySnapshot`, `WriteBatch`. ✅

  Watch from spec-level «`Intent`-приём VCard не должен пересекать границу core» — plan §2 явно размещает `VCardReceiveActivity.kt` в `:app/contacts/` (lines 65-66), который читает `EXTRA_STREAM` и передаёт `ByteArray` в `VCardImporter.parse()` — exactly as required. ✅

- [x] **CHK005 — Wire-format type — domain data class with serializers in adapter**

  - `ConfigSnapshot` (data-model §3) — `@Serializable data class` в `api/config/`, не Firestore `DocumentSnapshot`. ✅
  - `PresetSettings` + `PhoneHealthSettings` (data-model §4-5) — `@Serializable data class` в `api/config/`, forward-compat, always null wire в spec 9. ✅
  - `SeverityWire` (data-model §5) — `@Serializable enum` отдельный от UI `PhoneHealthSeverity` (data-model §12) — **explicit wire/UI separation** per CLAUDE.md rule 1. ✅ **Strength**: ловит ordering coupling между wire bytes и UI ordinal.

## Platform types

- [x] **CHK006 — No `android.*`, `androidx.*`, `Intent`, `Uri`, `Context`, `Bundle` в commonMain**

  Plan §6 «Fitness functions» — 3 из 5 Konsist gates на этот invariant:
  - Gate 1: `api/contacts/*.kt` MUST NOT import `android.*` / `androidx.*`.
  - Gate 2: `api/history/*.kt` MUST NOT import `com.google.firebase.*`.
  - Gate 3: `api/apps/*.kt` MUST NOT import `android.content.pm.*`.

  Konsist auto-enforces, не зависит от ревьюера. ✅

  **Watch (data-model §7 `IconRef`)**: `IconRef(packageName: String, resourceId: Int)` — `resourceId: Int` is **semantically** Android `R.id` reference, но **typed** as Kotlin Int. Это grey area: type — Kotlin, но meaning — Android. **Verdict**: acceptable since Int не leak'ает Android type system, и adapter сам резолвит resource. Документировано в data-model §7 docstring. → see new watch W-PL-1.

- [x] **CHK007 — Domain values carry domain-typed projection, not raw platform type**

  - `packageName: String` (data-model §7) — String, not `ComponentName`. ✅
  - `phoneNumber: String` (data-model §1) — normalized via `Contact.fromRaw()` pure-Kotlin pipeline. ✅
  - `displayName: String` — String. ✅
  - `recordedFromDeviceId: String` (data-model §3) — String UID, not Firebase Auth `FirebaseUser`. ✅
  - `recordedAt: Long` (data-model §3) — epoch millis, not `Timestamp` Firebase type. Docstring: «epoch millis (server-side `serverTimestamp()` mapped on read)» — explicit mapping в adapter. ✅
  - `iconResource: IconRef?` (data-model §7) — see W-PL-1 above.

## Ports

- [x] **CHK008 — Every external surface exposed through port**

  All 13 external surfaces from spec-level inventory either re-use existing port (S1-S2, S6, S7, S14) или новый port (S3-S5, S8, S9, S10, S11, S13) или out of scope (S15). ✅

  **Plan-level addition**: `OpenAppDispatcher` зарегистрирован как «existing, extended» — data-model §10 (line 367) уточняет «Already exists from spec 003. Spec 009 only documents the contract for new callers; no new methods.» Это разумно (avoids unnecessary churn), но **stress-test**: если spec 003's existing `OpenAppDispatcher` имеет vendor types в signature — это unfixed regression. → see new watch W-PL-2.

- [x] **CHK009 — Port shape driven by domain need**

  All 5 port methods read как domain ops, not SDK conveniences:
  - `recordSnapshot(LinkId, ConfigSnapshot)` — not `writeFirestoreDoc(collectionPath, json)`.
  - `pickContact()` — not `launchActivityForResult(intent, code)`.
  - `parse(payload: ByteArray)` — not `parseFromExtraStream(intent)`.
  - `listApps()` — not `queryIntentActivities(filter)`.
  - `openApp(packageName)` — not `startActivityWithFallback(intent, fallbackIntents)`.

- [x] **CHK010 — Each port has fake adapter**

  Plan §6 table (lines 207-211) explicit: 5 fakes в `commonTest`. Konsist gate 4 (return type of `Contact.fromRaw`) гарантирует, что fake'и не throw'ают exceptions (forces Result discipline). ✅

- [x] **CHK011 — Each port has real adapter (androidMain)**

  Plan §2 Module map (lines 49-55) lists 5 real adapters in `:core/androidMain/adapters/` + `:core/androidMain/firestore/`. ✅

  **Note**: Spec-level CHK011 рассматривал iOS — plan §2 не mentions iOS sources, что consistent с ADR-001 Platform Parity decision (Android-only spec). ✅

- [x] **CHK012 — DI wiring picks fake/real per build**

  Plan §10 Phase 0 verification mentions «all contract + roundtrip tests green» — implies DI wiring готова. Plan не показывает explicit Koin module file paths, но это **plan-level detail acceptable** (tasks.md детализирует). → W-PL-3.

## Source-set placement

- [x] **CHK013 — Every new file assigned to source set with one-sentence justification**

  Plan §2 Module map — comprehensive table format. Each file in `:core/commonMain` is pure-domain; each in `:core/androidMain` has explicit Android API reason (line 53: «ContactsContract → Contact.fromRaw», line 54: «VCard text → Contact.fromRaw», etc.). ✅

  **Critical placement verification per caller request**:
  - `Contact.fromRaw()` factory placement: data-model §1 explicit «File: api/config/Contact.kt (extend existing data class)» — это **commonMain** (existing Contact живёт там). ✅ Pure Kotlin regex (`^\+?\d{5,20}$`), `String` operations, `Result<Contact, ValidationError>`. Никаких Android imports. Konsist gate 4 enforces.

  **Watch: PhoneHealthIndicator placement disagreement**:
  - Spec.md Key Entities + spec-level checklist W-1: «PhoneHealthIndicator — UI-layer projection, **не часть domain layer**; должен жить в `app/health-ui/`».
  - Plan.md §2 Module map (lines 42-46) размещает его в `:core/ui/health/` (commonMain).
  - Data-model.md §11 (line 397-398) тоже размещает в `core/src/commonMain/kotlin/com/launcher/ui/health/`.

  **Анализ**: это **conscious deviation** от spec-level recommendation. `:core/ui/` is **UI** layer внутри `:core` KMP module (per ADR-005 Compose Multiplatform), а не domain layer (`api/`). Plan §2 явно discriminates `api/` (domain ports) от `ui/` (UI components). Это всё ещё respects «not part of domain layer» invariant, потому что `ui/health/` ≠ `api/health/`. Verdict: ✅ acceptable interpretation, но **deviation должна быть зафиксирована**. → see W-PL-5.

- [x] **CHK014 — Default placement is commonMain; deviation has explicit reason**

  Plan §2 — all `:core/androidMain` files имеют explicit Android API justification в parentheses. `:app/` files имеют UI surface justification (admin screens, contacts UI). `:core/commonMain` is default. ✅

## Existing-code regressions

- [x] **CHK015 — Spec doesn't reintroduce vendor type into cleansed commonMain file**

  - `Contact.kt` extension (data-model §1) — pure Kotlin, Konsist gate 4 enforces. ✅
  - `ConfigCurrent.kt` extension с `presetOverrides: PresetSettings?` (contracts/config-current-additions.md) — `PresetSettings` is `@Serializable` Kotlin data class, no Android. ✅
  - `Slot.kt` (existing) — unchanged; `args: JsonObject` остаётся (CLAUDE.md project convention для kind-specific shape). ✅
  - `firestore.rules` extension (contracts/config-history.md §«Security Rules requirements») — Security Rule, not Kotlin code; OK. ✅
  - `AndroidManifest.xml` extension (plan §5) — manifest, not code; OK. ✅

- [x] **CHK016 — Spec doesn't add new expect/actual where pure-Kotlin would suffice**

  Plan §2 не упоминает expect/actual. All 5 new ports — plain `interface` в commonMain + `class` adapter в androidMain via DI. ✅ Per CLAUDE.md rule 2 preferred pattern.

  Plan-level confirmation: `VCardImportAdapter` хочет писать parser **в androidMain only** (plan §5 line 188 + contracts/vcard-incoming.md §«Parser implementation hint»). НЕ соблазнились на expect/actual ради «общего» парсера на iOS — explicitly документировано «Spec 009 — Android-only». ✅

---

## Summary

| Status | Count | Notes |
|---|---|---|
| ✅ Pass (plan-level) | **16** | All 16 checks pass at plan-level. |
| 📋 Action items closed from spec-level | **9 of 10** | 1 implicit (DI module file paths — acceptable, will surface в tasks.md). |
| ⚠️ Watch items (new at plan-level) | **5** | См. ниже. |
| ❌ Fail | **0** | — |

**Verdict: PASS at plan-level. Plan ready для `/speckit.tasks` без блокеров.**

---

## Plan-level watch items (NEW relative to spec-level run)

### W-PL-1 — `IconRef.resourceId: Int` semantic Android coupling

**Location**: `data-model.md` §7, `api/apps/InstalledAppsCatalog.kt`.

`IconRef(packageName: String, resourceId: Int)` — type `Int` is Kotlin primitive, но **semantically** это Android `R.id` integer. Adapter резолвит в `Drawable` в UI module.

**Why это grey, не fail**:
- `Int` doesn't import `android.*` → Konsist gate проходит.
- `IconRef` Serializable, может в теории идти на wire (хотя в спеке 9 не идёт).
- Тест «vendor disappears»: если Android resource system изменится, нужно поменять resolver в `:app/` UI layer + понять что `resourceId` semantics поменялись. **1-2 файла в `:app/`** — within adapter module scope, не утечка в domain.

**Watch для tasks.md**: если в будущем (spec 010+) `IconRef` начнёт сериализоваться в wire format — нужен дополнительный wire-format gate. Сейчас — acceptable as documented in data-model.md docstring.

**Recommendation**: оставить как есть; добавить inline TODO comment в `IconRef.kt`: «`resourceId` — Android R.id reference; valid только для package=packageName. Если будущий spec будет сериализовать `IconRef` на wire, добавить wire-format checklist row».

### W-PL-2 — `OpenAppDispatcher` treated as «existing extended» — re-verify spec 003 signature

**Location**: `data-model.md` §10 + `plan.md` §2.

Plan says `OpenAppDispatcher` уже существует из спека 003. Data-model §10: «Spec 009 only documents the contract for new callers; no new methods.»

**Risk**: if existing `OpenAppDispatcher` от спека 003 has vendor types in signature (e.g., `Intent` return type), plan inherits that regression. Spec-level checklist не проверил existing spec 003 code.

**Recommendation**: tasks.md должен явно включить **verification task**: «прочитать `core/src/commonMain/kotlin/com/launcher/api/apps/OpenAppDispatcher.kt` (existing from спека 003) и confirm signature uses только domain types. Если найдена vendor leak — refactor task before extending callers». 30 минут работы.

### W-PL-3 — DI module bindings paths not explicit в plan

**Location**: `plan.md` §6 + §10 Phase 0/1 не показывают конкретные Koin module file paths (`realBackendModule.kt`, `mockBackendModule.kt`).

**Risk**: low. Spec-level action item 7 «extend Koin modules with 5 NEW port bindings» — это plan-level task, который проверяется при `tasks.md` generation. Plan implicitly assumes existing wiring pattern from спеков 007/008.

**Recommendation**: tasks.md должен иметь explicit task «add 5 new Koin bindings to `realBackendModule.kt` + 5 to `mockBackendModule.kt`».

### W-PL-4 — `PermissionRequester` port for READ_CONTACTS not addressed

**Location**: `spec.md` FR-023 + spec-level inventory S12. Plan §2 показывает `ContactPermissionRationale.kt` в `:app/contacts/` (line 67), но не упоминает domain port для permissions.

**Анализ**:
- Если в проекте уже есть `PermissionRequester` port из спека 006 — fine, plan implicitly re-uses.
- Если нет — plan должен либо (a) создать NEW port `PermissionRequester` в `:core/commonMain/api/permissions/`, либо (b) inline permission check в `:app/` UI layer (acceptable если no commonMain code touches `Manifest.permission.READ_CONTACTS`).

**Recommendation**: tasks.md должен explicitly resolve: проверить existence of `PermissionRequester` port; если нет — добавить NEW port в plan-level scope или явно zafiksирovat'ь, что permission check inline в `:app/contacts/ContactPermissionRationale.kt` (без commonMain coupling). 1 час work.

### W-PL-5 — `PhoneHealthIndicator` placement deviation from spec-level guidance

**Location**: `plan.md` §2 (lines 42-46) + `data-model.md` §11 размещают в `:core/ui/health/` (commonMain). Spec-level checklist W-1 рекомендовал `app/health-ui/`.

**Анализ**: plan размещает в `:core/ui/health/`, не в `:app/`. **Это всё ещё satisfies invariant** «not part of domain (api/) layer», потому что `:core/ui/` — это UI layer внутри core KMP module (per ADR-005), и `:core/ui/health/` ≠ `:core/api/health/`.

**Why это deviation defensible**:
- Spec 009 — Android-only, но `:core/ui/` уже содержит Compose UI primitives (TileCard, FlowScreen) per ADR-005.
- Размещение `PhoneHealthIndicator` рядом с другими UI types в `:core/ui/` дешевле, чем создавать новый `:app/health-ui/` package.
- Konsist gate 1 (api/contacts no android.*) НЕ применяется к `:core/ui/health/` — что correct, потому что Compose UI MAY use `androidx.compose.*` imports (Material BOM).

**Why этого нужно watch**: spec.md Key Entities явно пишет «не часть domain layer (не претендует на universality для часов / сенсоров)». Plan размещает в `:core/ui/` — что технически common, но semantically Android-only Compose. Если в будущем spec 011/012 будет переиспользовать `PhoneHealthIndicator` для смарт-часов / сенсоров, придётся либо (a) обобщать тип, либо (b) дублировать. Это **forward-compat concern**, не текущая нарушение.

**Recommendation**: оставить как plan говорит; добавить inline TODO в `PhoneHealthIndicator.kt`: «Local UI type for phone health screen. Not designed for re-use by watch/sensor specs (spec 011/012); если такая потребность возникнет — refactor: extract domain `HealthIndicator` в `:core/api/health/` + UI mapper».

---

## Что НОВОГО найдено на plan-level (per caller request)

1. **Wire/UI severity enum separation** (data-model §5 vs §12) — **strength** plan'a, не fail. Spec-level не explicit об этом разделении; plan материализовал в виде двух разных enum (`SeverityWire` wire-side, `PhoneHealthSeverity` UI-side) с one-way mapping. Это образцовое применение CLAUDE.md rule 1 (no shared enum ordinal between wire bytes and UI).

2. **`IconRef.resourceId: Int` semantic coupling** (W-PL-1) — новый watch, не было в spec-level. Type — Kotlin Int, но meaning — Android R.id.

3. **`OpenAppDispatcher` «existing» status** (W-PL-2) — plan лучилось avoid'ит unnecessary churn, но требует одной verification задачи в tasks.md (прочитать existing спек-003 signature и confirm clean).

4. **`PermissionRequester` not addressed** (W-PL-4) — spec-level выписал в action items, но plan implicitly решил, что permission check будет в `:app/` UI layer (через `ContactPermissionRationale.kt`). Это acceptable resolution, но нужна явная task entry.

5. **`PhoneHealthIndicator` placement** (W-PL-5) — plan deviates от spec-level recommendation (placed в `:core/ui/` вместо `:app/health-ui/`), но deviation defensible per ADR-005.

---

## Нужно ли менять plan.md?

**No blocking changes required.**

Желательно добавить:
- В plan §10 Phase 0: явная task entry «verify existing `OpenAppDispatcher` (spec 003) signature uses domain-only types; refactor if vendor leak found» (W-PL-2).
- В plan §10 Phase 5: явная task entry «resolve PermissionRequester strategy — port или inline?» (W-PL-4).

Both — это **tasks.md concerns**, не plan-level rewrites. Plan-level verdict: ✅ ready для `/speckit.tasks`.

---

<!-- novice summary -->

## TL;DR (русский)

Plan-level прогон domain-isolation чек-листа на спек 9 — **все 16 пунктов PASS, 0 FAIL**. Plan материализовал 9 из 10 action items'ов spec-level run в конкретные файлы / port signatures / Konsist gates / тесты. Один пункт (DI Koin bindings paths) implicit, что приемлемо на plan-level.

**Главные плюсы plan'a относительно spec-level**:
1. **5 NEW ports** все легли в `:core/commonMain/api/{contacts,apps,history,config}` с корректными domain-shaped return types (никаких Firebase / Android типов в signatures).
2. **5 NEW androidMain adapters** в правильных пакетах `:core/androidMain/adapters/` + `:core/androidMain/firestore/`.
3. **5 Konsist gates** явно перечислены в test strategy — auto-enforced, не зависит от ревьюера.
4. **Contact.fromRaw()** размещён в `api/config/Contact.kt` (commonMain) с pure-Kotlin regex — exactly как требовал spec-level run.
5. **Wire/UI severity enum separation** (`SeverityWire` vs `PhoneHealthSeverity`) — **строгое** разделение, не было даже на spec-level — отличное решение plan'a.

**5 новых watch items на plan-level** (не блокеры):
- W-PL-1: `IconRef.resourceId: Int` semantically Android, but type-wise Kotlin — OK with inline TODO.
- W-PL-2: `OpenAppDispatcher` помечен «existing from спека 003» — нужна одна verification task в tasks.md.
- W-PL-3: Koin DI module paths implicit — fix в tasks.md.
- W-PL-4: `PermissionRequester` port не упомянут — resolve в tasks.md (port or inline в `:app/`).
- W-PL-5: `PhoneHealthIndicator` placement deviates (`:core/ui/health/` вместо `:app/health-ui/` per spec-level recommendation) — defensible per ADR-005, оставить как есть с inline TODO.

**Менять plan.md не нужно**. Добавления — это tasks.md scope (две новые задачи: verify `OpenAppDispatcher` existing signature; resolve `PermissionRequester` strategy). Plan готов идти в `/speckit.tasks`.

**Summary counts**: ✅ 16 PASS / ⚠️ 5 plan-level watch / ❌ 0 FAIL.
