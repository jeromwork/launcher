# Checklist: domain-isolation

Applied to: `specs/task-120-preset-composition-foundation/spec.md`
Date: 2026-07-10
Rules enforced: [CLAUDE.md](../../../CLAUDE.md) rules 1 (Domain isolated from infrastructure) and 2 (Anti-Corruption Layer for every external dependency), ADR-001 (Platform Parity Gate).

---

## Vendor SDKs

- [x] CHK001 No vendor SDK type (Firebase, Coil, WhatsApp SDK, Google Play Services, Crashlytics) appears in any signature visible to the domain layer. Domain ports (`PoolSource`, `PresetSource`, `ProfileStore`, `Provider`, `ProviderRegistry`, `InteractionSink`, `ConditionEvaluator`, `PackageManagerFacade`) are all declared in `core/preset/` commonMain per Assumption "Foundation этого spec'а живёт в `core/preset/` KMP commonMain — pure Kotlin, zero Android". Assumption "JSON runtime — kotlinx.serialization" — kotlinx.serialization is a Kotlin-first multiplatform lib, not a vendor SDK; polymorphic sealed via `classDiscriminator = "type"` is domain-owned schema, not a generated DTO framework.
- [x] CHK002 Each external SDK has exactly one wrapper module — Android PackageManager wrapped by `PackageManagerFacade` port (FR-014), WorkManager/AlarmManager/geofencing API implicitly wrapped inside individual `Provider` adapters in `androidMain/provider/` (FR-006 explicitly says "включение (через WorkManager / AlarmManager / geofencing API) и выход"). DataStore wrapped by `ProfileStore` port (FR-013). Hilt DI wiring is confined to `HandlerModule.kt` (FR-017).
- [x] CHK003 Vendor-disappears test: if Android PackageManager API changed — only `PackageManagerFacade` Android adapter changes (one file). If DataStore replaced with Room — only `ProfileStore` androidMain adapter. If WorkManager replaced — only respective `Provider` adapter. Documented in Assumptions ("Android SDK usage — только в `app/androidMain/provider/*` adapter модуле") and FR-014, FR-015. FAIL-risk noted: spec doesn't explicitly enumerate a count, but the boundary is stated.

## Transport types

- [x] CHK004 No transport types (HTTP clients, retrofit annotations, raw JSON containers) in domain signatures. Preset wire format is declared as domain-owned data classes with `@Serializable` (FR-001, FR-003). No HTTP client mentioned — admin push transport explicitly deferred (US5: "runtime реализации transport'а (FCM push, encrypted delivery) deferred в отдельные tasks").
- [x] CHK005 Wire format types (`Preset`, `Pool`, `Profile`, `ComponentDeclaration`, `WizardFlowEntry`, `SettingsMapEntry`, `ActiveComponentEntry`, `ChangeItem`) are domain-owned data classes. Serializers via kotlinx.serialization live with the class (annotation-based) which is idiomatic Kotlin multiplatform — not a generated DTO posing as domain model. `schemaVersion: Int` explicitly required per FR-016.

## Platform types

- [x] CHK006 No `android.*`, `androidx.*`, `Intent`, `Uri`, `Context`, `Bundle`, `LifecycleOwner` in any FR signature or Key Entity description. FR-014 mentions "system intent" for install fallback, but the `apply()` returns `Outcome`, not `Intent` — the intent construction happens inside the Android adapter. Edge case "AppTile ссылается на не установленный пакет: `check()` возвращает `NeedsApply`, `apply()` эмитит `Intent.ACTION_VIEW`" — `Intent.ACTION_VIEW` is described as adapter-internal behavior, not a domain return type. **Minor concern**: SEQ-1 Plan-level references `WizardActivity/Composable` (Android type) but that's the UI layer, not domain — acceptable per architectural layering.
- [x] CHK007 Platform-derived data uses domain-typed projections: `Vendor` enum (FR-018) is domain-typed with explicit list `Xiaomi | Samsung | Huawei | GoogleTV | GenericAndroid | iOS`. FR-018 says "Vendor определяется через `Build.MANUFACTURER` mapping" — the `Build.MANUFACTURER` read happens in the Android adapter (`VendorDetector` implied), the domain sees only the resolved `Vendor` enum value. `iOS` is a domain concept, not a platform type. `packageName` in `AppTile` is `String`, not `android.content.pm.PackageInfo`. **Verified**: platform detection does NOT leak `Build` type into domain — only the enum result crosses the boundary.

## Ports

- [x] CHK008 Every external surface is exposed through a port. Enumerated: `PoolSource` (FR-002), `PresetSource` (Key Entities), `ProfileStore` (FR-013), `Provider<T>` (FR-006), `ProviderRegistry` (FR-007), `InteractionSink` (Key Entities), `PackageManagerFacade` (FR-014), `ConditionEvaluator` (FR-020), and future `AuthHandoffService` (deferred to task-121).
- [x] CHK009 Port shape is domain-driven, not adapter-driven. `Provider<T>.check(step, profile): Outcome` and `apply(step, profile): Outcome` express the domain need "know current state / bring to desired state" — not adapter convenience like `writeSharedPreference(key, value)`. `PoolSource.load(): Pool` expresses domain need "give me the catalog" — not "readAssetFile(path)". `InteractionSink.askUser(step): ModifiedStep` expresses "ask the user about this step" — not "showComposeDialog(spec)".
- [x] CHK010 Fake adapters enumerated in Local Test Path: `FakePoolSource`, `FakePresetSource`, `FakeProfileStore` (in-memory `MutableStateFlow<Profile>`), `FakeInteractionSink`, `FakeProvider<T>`, `FakeAuthHandoffService`. Property-based test SC-011 uses fakes to run 100 combinations.
- [x] CHK011 Real adapters implied in `androidMain/provider/*` (FR-015, Assumptions) and placeholder `iosMain/provider/*` for future iOS work (Assumptions: "iOS Provider реализации — placeholder"). `PackageManagerFacade` real adapter in androidMain implied by FR-014 wrapping "Android PackageManager". `ProfileStore` real adapter uses DataStore (FR-013).
- [x] CHK012 DI wiring picks fake/real per build: Hilt `@IntoMap` with custom `@ComponentKey` annotation (FR-017); test builds substitute fakes via Hilt test modules (standard pattern, Assumptions "DI framework — Hilt").

## Source-set placement

- [x] CHK013 Source-set placement stated for each file class in Assumptions: `core/preset/` KMP commonMain (domain, wire format types, ports), `app/androidMain/provider/*` (real adapters), `iosMain/provider/*` (placeholder). SEQ-3 explicitly names files: `core/preset/Component.kt` (commonMain), `androidMain/provider/TimeLockdownProvider.kt`, `HandlerModule.kt` (androidMain DI), `assets/pool.json` (androidMain resources). Justification: commonMain for zero-Android pure Kotlin; androidMain for SDK calls.
- [x] CHK014 Default is commonMain; deviation reason is stated. All Provider adapters live in androidMain because they use WorkManager / AlarmManager / geofencing API (platform-specific). `ProfileStore` real adapter is androidMain because DataStore is Android-only (KMP DataStore in Alpha, not adopted per Assumption). This matches ADR-005 Gate 1.

## Existing-code regressions

- [x] CHK015 Spec doesn't reintroduce vendor types into commonMain. Preset composition is a greenfield foundation replacing hardcoded `FirstLaunchActivity` (draft-1) — no prior commonMain cleansed files that could be regressed. `Vendor` enum introduction is additive, doesn't leak `Build` into commonMain.
- [x] CHK016 Spec doesn't add `expect`/`actual` where pure Kotlin suffices. The spec explicitly avoids `expect`/`actual` by using port/adapter pattern instead — `Provider<T>` is a pure interface in commonMain, implementations are per-platform classes in androidMain/iosMain wired via DI. No mention of `expect fun` or `actual fun` anywhere.

---

## Summary

- Total: 16/16 CHK [x]
- FAIL: none
- Concerns / soft notes:
  - CHK003: exit-ramp "how many files change if vendor disappears" is stated qualitatively via port boundaries, not quantitatively per adapter. Downstream spec (e.g. LockdownProvider spec) should count files explicitly.
  - CHK006: `Intent.ACTION_VIEW` in Edge Cases prose is adapter-internal — acceptable, but plan.md should verify that no `Intent` type reaches `Outcome` returns.
  - The spec is exemplary regarding domain isolation: pure Kotlin commonMain, ports for every external surface (`PackageManagerFacade` wraps PackageManager, `ProfileStore` wraps DataStore, `ConditionEvaluator` wraps future JsonLogic runtime, `InteractionSink` wraps UI), explicit Fitness function #1 (import guard) enforces boundary at CI level. `Vendor` enum decouples domain from `Build.MANUFACTURER`.

## Fitness functions supporting this checklist

- Fitness #1 (import guard engine): mechanically enforces CHK006, CHK015.
- Fitness #6 (cross-provider isolation guard): supports CHK002 by preventing Provider→Provider direct calls (would violate ACL).
- Fitness #3 (coverage Component↔Provider): supports CHK008, CHK010, CHK011.

The spec's architectural discipline exceeds the minimum required by rules 1 and 2.
