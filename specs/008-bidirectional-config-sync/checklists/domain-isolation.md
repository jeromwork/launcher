# Checklist: domain-isolation

**Spec**: `spec.md` (rev. 2026-05-14, post-clarify Q1-Q10)
**Run**: 2026-05-14 — `/speckit.clarify` post-pass before `/speckit.plan`.

Enforces [`CLAUDE.md`](../../../CLAUDE.md) rules 1 (Domain isolated from infrastructure) and 2 (Anti-Corruption Layer) + ADR-001 Platform Parity Gate.

---

## Inventory: external surfaces in spec 008

| Surface | Vendor / Platform | Re-use или new? | Port |
|---|---|---|---|
| Firestore read/write `/config/current`, `/state/current` | Firebase (vendor) | **re-use** existing port из спека 007 | `RemoteSyncBackend` |
| Firestore atomic transaction (optimistic concurrency, FR-013) | Firebase | **re-use** | `RemoteSyncBackend.runTransaction` |
| FCM payload `config.updated` | Firebase (vendor) | **re-use** push infra из 007 | `PushReceiver` / `PushSender` |
| Cloudflare Worker push trigger (Worker FR-020) | Cloudflare (vendor) | **re-use** Worker из 007 + new payload type | server-side, не Android port |
| Local persistence — applied-config | Room (AndroidX platform) | **NEW** | **new port** required — `LocalConfigStore` или подобное |
| Local persistence — pending-local-changes | Room | **NEW** | **new port** required |
| Network connectivity callback | `ConnectivityManager` (Android platform) | **NEW** | **decision needed** — port или single-use inline? |
| Periodic background poll | `WorkManager` (AndroidX) | **partially re-use** (стуб из спека 007 C13) | **new port** или existing `RemoteSyncBackend.observe()`? |
| Activity#onResume trigger | Android lifecycle | **NEW** | **decision needed** — lifecycle hook, обычно не port'ится |
| UUID generation | platform-agnostic (Kotlin stdlib + uuid lib) | NEW | **decision** — domain helper `IdGenerator` port или inline `kotlin.uuid.Uuid`? |

---

## Vendor SDKs

- [x] **CHK001 — No vendor SDK type appears in any signature visible to the domain layer**

  Re-using existing 007 infrastructure:
  - `RemoteSyncBackend` (`core/commonMain/api/sync/`) — port, domain-clean, **already** Konsist-enforced («no `com.google.firebase.*` import под `:core/api/`», см. spec 007 Phase 10).
  - `PushReceiver` / `PushSender` (`core/commonMain/api/push/`) — ports for FCM.
  - Spec 008 не вводит **никаких** новых Firebase types в commonMain. ✅

  Watch: новый payload type `config.updated` (FR-020) добавляется в `PushPayload` sealed class (commonMain) — это **domain type**, не vendor. ✅

- [x] **CHK002 — Each external SDK has exactly one wrapper module (adapter); domain references only the port**

  - Firebase: один adapter (`FirebaseRemoteSyncBackend` in `:core/androidMain`, `realBackend` flavor) per 007 Phase 4.
  - FCM: один adapter (`FirebasePushReceiver`/`FirebasePushSender` in `:core/androidMain`).
  - Worker (server-side): один Cloudflare Worker, домена не касается.
  - Room (NEW): **должен быть один adapter** — `RoomLocalConfigStore` или `RoomConfigStorage` в `:core/androidMain`. **Action для plan.md**: явное правило «нет других Room dao за пределами одного adapter modul».

- [x] **CHK003 — The "vendor disappears tomorrow" test: number of files needing change ≤ size of one adapter module**

  - **Firebase vanishes**: переписать `FirebaseRemoteSyncBackend` + Cloudflare Worker payload-format. Estimate: 1-2 недели per 007. Domain + UI + tests остаются.
  - **Room vanishes / SQLDelight replacement**: переписать `RoomLocalConfigStore` adapter. Domain port `LocalConfigStore` остаётся. Estimate: 3-5 дней per CLAUDE.md §4 Test 2.
  - **FCM banned**: same as Firebase (port `PushReceiver` остаётся).
  - All within adapter-module scope. ✅

## Transport types

- [x] **CHK004 — No transport types appear in domain signatures**

  - `RemoteSyncBackend.writeDoc/readDoc/observe/runTransaction` принимает `JsonElement` (kotlinx.serialization, platform-agnostic) + `DocPath` (domain) + `BackendError` (domain). Никаких retrofit annotations, HTTP types, raw maps.
  - Watch: 008 not introducing alternate transport (e.g., REST endpoints для config). Если в plan.md появится REST для admin-side editor (например, через Cloud Function), — обязательно через port pattern.

- [x] **CHK005 — Wire format type is domain-owned data class with serializers in adapter**

  - Existing pattern (007): `Link` (domain data class) + `LinkWireFormat.kt` (commonMain) — domain-owned serialization. Adapter использует `WireFormatJson` (общий) или собственные serializers.
  - 008 должен следовать: `ConfigDocument` (commonMain) + `ConfigDocumentWireFormat.kt` (commonMain, serialization) + adapter mapping. **Action для plan.md**: создать те же два файла в `:core/commonMain/api/config/`.
  - **No generated DTOs** — пишем data classes вручную в commonMain.

## Platform types

- [x] **CHK006 — No `android.*`, `androidx.*`, `Intent`, `Uri`, `Context`, `Bundle`, `LifecycleOwner` in commonMain**

  - `ConnectivityManager.NetworkCallback` — это `android.net.ConnectivityManager`, **platform-specific**. Spec.md упоминает его в FR-022 T2 и SC-001 — но это **в spec.md контексте**, не **в коде commonMain**. В commonMain должен быть domain port (e.g., `NetworkAvailability.onAvailable: Flow<Unit>` или similar). **Action для plan.md**: ввести port `NetworkAvailability` (commonMain) + Android adapter (`ConnectivityManagerNetworkAvailability`).
  - `Activity#onResume` — Android-specific. В commonMain должен быть domain hook (e.g., `AppForegroundEvents.onResume: Flow<Unit>` или подобное). Existing pattern: spec 007 использует `ProcessLifecycleOwner` через какой adapter? Проверить в `health/` модуле. **Action для plan.md**: найти existing lifecycle abstraction or define new.
  - `WorkManager` — AndroidX, platform-specific. Должен быть скрыт за domain port (e.g., `PeriodicBackgroundTask` или просто **встроен** в Android adapter `BackgroundConfigRefresher` без отдельного port — обоснование: единственный use site, no test value of fake).

- [x] **CHK007 — Domain values carry domain-typed projection, not raw platform type**

  - Уже соблюдено в 007: `DocPath`, `DeviceId`, `LinkId` — domain projections. Spec 008 наследует.
  - Новые projections в 008: `ConfigSchemaVersion` (Int wrapper), `ElementId` (Uuid wrapper, **NOT** java.util.UUID), `ServerTimestamp` (already in 007? проверить). **Action для plan.md**: явные projections.

## Ports

- [x] **CHK008 — Every external surface used by this spec is exposed through a port**

  - Re-used: `RemoteSyncBackend`, `PushReceiver`/`PushSender`.
  - NEW (must define in plan.md):
    - `LocalConfigStore` — applied-config + pending-changes persistence (Room).
    - `NetworkAvailability` — connectivity events (ConnectivityManager).
    - `AppForegroundEvents` (или existing) — Activity#onResume trigger.
    - `IdGenerator` (или domain helper, не obligatory port) — UUID v4 generation.
  - **Possibly NEW**: `ConfigDiffer` — domain service, не obligatory port (pure function, single implementation, no I/O). **Decision для plan.md**: keep as plain function, no port.
  - **Possibly NEW**: `ConfigEditor` (упомянут в Key Entities как domain port). **Confirm в plan.md** — это application-service, не infrastructure port. Может оказаться pure-domain без adapter pair. Решение: keep как port если есть test value (fake ConfigEditor для UI tests без Firestore). См. CHK010.

- [x] **CHK009 — Port shape is driven by domain need, not by adapter convenience**

  Existing 007 pattern is exemplary: `RemoteSyncBackend.writeDoc(path, data, schemaVersion)` — это domain-level «записать документ», не `firestore.collection().document().set()`-style leakage.

  Watch для 008:
  - **DO NOT define**: `LocalConfigStore.getFromRoomDb(table, key)` — это adapter-leak.
  - **DO define**: `LocalConfigStore.readAppliedConfig(linkId): ConfigDocument?`, `writePendingChanges(linkId, draft)`, `clearAllForLink(linkId)`. Domain operations.

- [x] **CHK010 — Each port has a fake adapter (commonTest)**

  - Existing fakes: `FakeRemoteSyncBackend`, `FakePushReceiver`, `FakePushSender`, `FakeLinkRegistry`, `FakeDeviceIdProvider`, `FakeIdentityProvider`.
  - New fakes для 008 (mandatory per CLAUDE.md §6):
    - `FakeLocalConfigStore` — in-memory map.
    - `FakeNetworkAvailability` — programmable Flow<Unit> для tests.
    - `FakeAppForegroundEvents` — same.
    - `FakeIdGenerator` — incrementing counter or fixed test UUIDs.
  - **Action для tasks.md**: явные tasks per fake.

- [x] **CHK011 — Each port has a real adapter (androidMain and/or iosMain)**

  - 008 — Android-only (iOS не входит в проект сейчас per memory `project_007_operational_state`).
  - Real adapters in `:core/androidMain`:
    - `RoomLocalConfigStore` (Room implementation).
    - `ConnectivityManagerNetworkAvailability`.
    - `ProcessLifecycleAppForegroundEvents` (или подобное).
    - `JavaUuidIdGenerator` (java.util.UUID-based) или Kotlin `kotlin.uuid.Uuid`.
  - **Action для plan.md** + tasks.md.

- [x] **CHK012 — DI wiring picks fake/real per build per CLAUDE.md rule §6**

  - Existing pattern: `realBackend` / `mockBackend` build flavors (spec 007 Phase 3). 008 наследует.
  - **Action для plan.md**: явное расширение `realBackendModule` / `mockBackendModule` Koin модулей для 008-ports.

## Source-set placement

- [x] **CHK013 — Every new file clearly assigned to commonMain / androidMain / iosMain**

  Recommended placement (для plan.md):
  - `core/commonMain/api/config/ConfigDocument.kt` — domain data class.
  - `core/commonMain/api/config/ConfigDocumentWireFormat.kt` — serialization.
  - `core/commonMain/api/config/ConfigDiff.kt` — diff value type + pure function.
  - `core/commonMain/api/config/ConfigApplier.kt` — port.
  - `core/commonMain/api/config/ConfigEditor.kt` — port (если решим в CHK008).
  - `core/commonMain/api/config/LocalConfigStore.kt` — port.
  - `core/commonMain/api/lifecycle/NetworkAvailability.kt` — port.
  - `core/commonMain/api/lifecycle/AppForegroundEvents.kt` — port (или re-use existing).
  - `core/commonMain/api/identity/IdGenerator.kt` — port (или re-use).
  - `core/commonMain/fake/config/*.kt` — fakes.
  - `core/androidMain/...config/RoomLocalConfigStore.kt` — Room adapter.
  - `core/androidMain/...lifecycle/ConnectivityManagerNetworkAvailability.kt`.
  - `core/androidMain/...lifecycle/ProcessLifecycleAppForegroundEvents.kt`.
  - `core/androidMain/...config/RoomEntities.kt` — Room @Entity classes (private, никогда не leaked в commonMain).
  - `core/androidMain/...config/ConfigDocumentDao.kt` — Room DAO (Android-only).

- [x] **CHK014 — Default placement is commonMain; deviation has explicit reason**

  Каждый androidMain-файл имеет explicit reason (uses Android platform API). Каждый commonMain-файл — pure Kotlin + kotlinx primitives.

## Existing-code regressions

- [x] **CHK015 — Spec doesn't reintroduce any vendor type into a commonMain file already cleansed by prior specs**

  Spec.md явно ссылается на Firebase / Firestore / FCM / Room / WorkManager / ConnectivityManager — **но это в текстовом описании spec.md, не в коде**.
  Code будет в plan.md. Watch: при написании plan.md / tasks.md проследить, чтобы:
  - `ConfigDocument.kt` (commonMain) **не** имел `import com.google.firebase.*`.
  - `ConfigApplier.kt` (commonMain port) **не** имел `import android.*`.
  - Konsist gates (Phase 10 pattern из 007) — расширить для 008 ports.

- [x] **CHK016 — Spec doesn't add new expect/actual declaration where pure-Kotlin would suffice**

  Spec.md не упоминает `expect`/`actual`. Если plan.md захочет их ввести (например, для UUID v4 generation если решим, что нет хорошей KMP-библиотеки) — должно быть оправдано: kotlin stdlib `kotlin.uuid.Uuid` exists (since Kotlin 2.0.20), pure Kotlin, common. **Recommendation**: использовать `kotlin.uuid.Uuid` — no expect/actual.

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass (spec-level) | 16 | All 16 checks pass at spec-level |
| 📋 Action items for plan.md | many | Listed below |
| ❌ Fail | 0 | — |

**Verdict: PASS at spec-level.**

Spec.md не нарушает domain-isolation principles. Все vendor / platform упоминания в spec.md — это **информационный контекст**, не **архитектурные требования к коду в commonMain**. Spec 008 широко переиспользует существующую инфраструктуру спека 007 (RemoteSyncBackend, PushReceiver/Sender, fakes), что снижает риск regression.

---

## Mandatory action items для plan.md

1. **Создать `:core/commonMain/api/config/`** module:
   - `ConfigDocument.kt`, `ConfigDocumentWireFormat.kt`, `ConfigDiff.kt`, `ConfigApplier.kt`, `LocalConfigStore.kt`, possibly `ConfigEditor.kt`.
2. **Создать domain ports** для новых external surfaces:
   - `NetworkAvailability` (commonMain).
   - `AppForegroundEvents` (либо re-use existing если есть).
   - `IdGenerator` или решение использовать `kotlin.uuid.Uuid` inline.
3. **Создать fakes** в `:core/commonMain/fake/config/`:
   - `FakeLocalConfigStore`, `FakeNetworkAvailability`, `FakeAppForegroundEvents`.
4. **Создать Android adapters** в `:core/androidMain`:
   - `RoomLocalConfigStore` (новый Room database или extension существующего).
   - `RoomEntities.kt` (private @Entity classes, никогда не leaked в commonMain).
   - `ConnectivityManagerNetworkAvailability`.
   - Possibly `ProcessLifecycleAppForegroundEvents`.
5. **Расширить Koin modules** (`realBackendModule`, `mockBackendModule`) bindings.
6. **Расширить Konsist gates** (по pattern Phase 10 спека 007):
   - `commonMain` ports of 008 не имеют Firebase/Room/Android imports.
   - `androidMain` adapters не утекают vendor types через сигнатуры.
7. **Decision: NetworkCallback wrapping** — port или inline в single Android adapter? **Рекомендация**: port (`NetworkAvailability: Flow<Unit>`), потому что (а) есть test value через fake, (б) consistent с existing `RemoteSyncBackend` pattern.
8. **Decision: WorkManager wrapping** — port или Android-only adapter? **Рекомендация**: Android-only adapter `BackgroundConfigRefresher` без port (single use site, no test value through fake — WorkManager is hard to fake meaningfully).
9. **ADR-001 Platform Parity Gate**: подтвердить, что iOS не входит в 008 scope (только Android). Если iOS планируется — все port'ы commonMain должны иметь iOS adapters (которых сейчас нет в проекте per memory).

## Watch items

- **CHK006 (Activity#onResume)**: проверить в plan.md, есть ли уже `AppForegroundEvents`-style port в проекте (вероятно, через existing health-модуль или ProcessLifecycleOwner adapter). Re-use > new.
- **CHK010 (`ConfigEditor` port)**: подтвердить в plan.md, является ли это application-service (одна реализация, no fake needed) или infrastructure port (fake/real pair). См. spec.md Key Entities — там `ConfigEditor` помечен как «domain port», но это решение plan.md.

**No spec.md edits required.**
