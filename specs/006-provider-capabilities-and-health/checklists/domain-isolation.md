# Checklist: domain-isolation — спек 006

**Spec:** [`spec.md`](../spec.md) · **Run:** 2026-05-09 · **Score:** 11 ✓ / 5 ◐ / 0 ✗

Source: [CLAUDE.md rules 1, 2, 6](../../../CLAUDE.md), [ADR-001 Platform Parity Gate](../../../docs/adr/ADR-001-cross-platform-strategy.md).

Legend: `[x]` pass · `[~]` partial · `[ ]` fail.

---

## Inventory of new external surfaces / ports

| # | Surface | Wrapped by | Source-set |
|---|---------|------------|------------|
| S1 | `PackageManager` (Android system) | `AndroidCapabilityCollector` (implicit, named in plan.md) | androidMain |
| S2 | `ConnectivityManager` + `NetworkCallback` | `AndroidHealthCollector` | androidMain |
| S3 | `AudioManager` + Volume ContentObserver | `AndroidHealthCollector` | androidMain |
| S4 | `Settings.Global.AIRPLANE_MODE_ON` ContentObserver | `AndroidHealthCollector` | androidMain |
| S5 | `Intent.ACTION_BATTERY_CHANGED` (sticky) | `AndroidHealthCollector` | androidMain |
| S6 | DataStore Preferences | DataStore Serializer adapters | androidMain (or commonMain via datastore-multiplatform) |
| S7 | APK drawable resources | `BundledIconStorage` | androidMain |
| S8 | `ProcessLifecycleOwner.RESUMED` (androidx) | Both collectors subscribe | androidMain |

**Domain ports (commonMain):** `CapabilityRepository`, `HealthRepository`, `IconStorage`.

**Domain entities (commonMain):** `Capability`, `Health`, `Connectivity`, `LauncherSettings`, `IconResolution` sealed.

---

## Vendor SDKs

- [x] **CHK001** No vendor SDK type in domain signatures.
  - `Capability`/`Health`/`LauncherSettings` — pure Kotlin (`Int`, `String`, `Long`, enum, `Boolean`).
  - `IconStorage.resolve(iconId: String): IconResolution` — domain types only.
- [~] **CHK002** Single wrapper per SDK.
  - **Finding:** спек не именует adapter-классы explicit. Implicit single wrappers per surface (S1..S8).
  - **Fix:** plan.md создаёт явные имена (`AndroidCapabilityCollector`, `AndroidHealthCollector`, `BundledIconStorage`, `CapabilitySnapshotProjection`, `HealthSnapshotProjection`, `LauncherSettingsProjection`).
- [x] **CHK003** «Vendor disappears tomorrow» test.
  - Each system API wrapped in single Android adapter file. If `ConnectivityManager` API changed — only `AndroidHealthCollector` touched.

## Transport types

- [x] **CHK004** No transport types in domain.
  - kotlinx.serialization annotations on data classes — это **library-on-domain-types**, не «transport-type-as-domain». Acceptable per spec 005 pattern.
- [x] **CHK005** Wire format type domain-owned.
  - `Capability`, `Health`, `LauncherSettings` declared in commonMain. JSON serializers run via `Json` instance (also commonMain, переиспользуется из спека 005 `ActionWireFormat.json`).

## Platform types

- [~] **CHK006** No `android.*` in commonMain.
  - **Finding:** spec FR упоминают Android типы (`ProcessLifecycleOwner`, `ConnectivityManager`, etc.) — это спецификация, не код. **Implicit:** эти типы живут в androidMain. Не зафиксировано explicit.
  - **Fix:** добавить **FR-047** в spec: «All Android system types (`android.*`, `androidx.*`, `Intent`, `Uri`, `Context`, `Bundle`, `LifecycleOwner`) MUST stay in `androidMain`. `commonMain` exposes pure Kotlin ports + domain types only».
- [x] **CHK007** Domain projection of platform-derived data.
  - `Capability.iconId: String`, `versionCode: Long?`, `Health.batteryPercent: Int`, `connectivity: Connectivity` enum, `ringerVolumePercent: Int` — all projections, no raw platform types.

## Ports

- [x] **CHK008** Every external surface via port.
  - S1 (PackageManager) → CapabilityRepository.
  - S2-S5, S8 → HealthRepository.
  - S7 → IconStorage.
  - S6 (DataStore) → infrastructure inside adapter, no domain port needed (storage is implementation detail of repository).
- [x] **CHK009** Port shape driven by domain need.
  - `observe()`, `snapshot()`, `resolve(iconId)` — domain semantics, не «getFromSharedPreferences».
- [~] **CHK010** Fake adapter per port (CLAUDE.md rule 6 mock-first).
  - **Finding:** спек НЕ указывает явно требование на fake adapters.
  - **Fix:** добавить **FR-048**: «Each port (`CapabilityRepository`, `HealthRepository`, `IconStorage`) MUST have a fake adapter in `commonTest` (or shared test artifact) — `FakeCapabilityRepository`, `FakeHealthRepository`, `FakeIconStorage`. Used by domain-level tests and dev/debug builds».
- [x] **CHK011** Real adapter in androidMain.
  - Implicit through FR-002, FR-018, FR-010. iOS explicitly deferred (Assumptions §6).
- [~] **CHK012** DI wiring picks fake/real per build.
  - **Finding:** спек не указывает DI strategy. Spec 004 ввёл Koin per Amendment 2026-05-07a.
  - **Fix:** plan.md документирует Koin modules для спека 006:
    - `androidMain`: `capabilityModule`, `healthModule`, `iconStorageModule` — real adapters.
    - `commonTest`: shared `fakesModule` — fake adapters.
    - Test fixtures override real bindings with fakes.

## Source-set placement

- [~] **CHK013** Each new file assigned source-set with justification (ADR-005 §3 Gate 1).
  - **Finding:** спек не содержит таблицу source-set placement.
  - **Fix:** plan.md создаёт таблицу:

    | Type / file | Source-set | Reason |
    |---|---|---|
    | `Capability`, `Health`, `LauncherSettings`, `Connectivity`, `IconResolution` | commonMain | Pure Kotlin domain entities |
    | `CapabilityRepository`, `HealthRepository`, `IconStorage` interfaces | commonMain | Pure Kotlin ports |
    | `AlertBannerStateProvider`, `AlertBannerType` | commonMain | Domain logic over Health + Settings |
    | `FakeCapabilityRepository`, `FakeHealthRepository`, `FakeIconStorage` | commonTest | Domain-test infrastructure |
    | `AndroidCapabilityCollector` | androidMain | Uses `PackageManager` |
    | `AndroidHealthCollector` | androidMain | Uses `ConnectivityManager`/`AudioManager`/ContentObservers |
    | `BundledIconStorage` | androidMain | Reads APK drawable resources |
    | `CapabilitySnapshotProjection`/`HealthSnapshotProjection`/`SettingsProjection` | androidMain | DataStore Preferences (or commonMain if datastore-multiplatform adopted) |
    | `AndroidCapabilityRepository`, `AndroidHealthRepository` | androidMain | Real adapters wiring system + projections |

- [x] **CHK014** Default commonMain, deviation justified.
  - All Android adapters justified by Android API usage.

## Existing-code regressions

- [x] **CHK015** No vendor types reintroduced in commonMain.
  - Спек не добавляет vendor SDK; cleanup `migrateLegacyAction` уменьшает surface.
- [x] **CHK016** No new `expect`/`actual` where pure Kotlin would suffice.
  - Domain pure Kotlin, adapters Android-only (no iOS yet — Assumptions §6). No `expect`/`actual` needed in спеке 006.

---

## Open items для spec.md / plan.md

**Add to spec.md before speckit-plan:**

- **FR-047** Android types stay in androidMain (CHK006).
- **FR-048** Fake adapters per port (CHK010).

**For plan.md to document:**

- Adapter class names (CHK002).
- Koin modules + DI strategy (CHK012).
- Source-set placement table (CHK013).

## Itog

- 11 PASS, 5 PARTIAL (2 spec FRs + 3 plan.md docs), 0 hard FAIL.
- **Verdict:** спек **архитектурно чист**. Domain pure Kotlin данные и порты, adapters в androidMain wrap system APIs, wire formats domain-owned, vendor types не протекают. Перед speckit-plan — добавить FR-047 и FR-048 для explicit'ной фиксации (это implicit, но fitness-test без explicit FR не сможет проверить).

---

## TL;DR для нетехнического читателя

Этот checklist проверяет **чистоту архитектуры** — что «бизнес-логика лаунчера» не перемешана с «как именно Android устроен внутри». Зачем это важно:

- Если завтра придётся делать iOS-версию лаунчера — бизнес-логику не переписываем, заменяем только Android-специфику.
- Если Google поменяет какой-нибудь API в Android — правим один маленький адаптер, не весь проект.
- Если хотим протестировать логику на компьютере (без эмулятора) — это работает, потому что бизнес-логика не привязана к Android.

**Что хорошо в спеке 006:**
- Все «карточки данных» (Capability, Health, настройки) — на чистом языке программирования, без привязки к Android.
- Все обращения к Android-системе спрятаны за «портами» — единый интерфейс, разные реализации (для Android, для тестов, в будущем для iOS).
- Иконки берутся через единый интерфейс `IconStorage` — сейчас одна реализация (встроенные иконки), потом добавим вторую (облачные) без переделки.

**Что нужно дополнить:**
- Явно записать в спек, что Android-специфика **обязана** жить в Android-папке проекта (а не в общей).
- Явно записать, что для каждого «порта» нужен **поддельный двойник** — для тестов и режима разработки, чтобы запускать без реального Android.
- В плане (следующая фаза) — описать конкретные имена адаптеров и стратегию выбора «настоящий или поддельный» через систему конфигурации (Koin).

Это всё уточнения, не пересмотр. Архитектура хорошая.
