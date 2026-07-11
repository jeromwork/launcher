# Checklist: domain-isolation — task-126 Wizard Runtime Migration

Applies CLAUDE.md rules 1 (Domain isolated from infrastructure), 2 (ACL). Aligned with ADR-001 (Platform Parity Gate).

Spec: `specs/task-126-wizard-runtime-migration/spec.md` (Draft, 414 lines).

## Vendor SDKs

- [x] CHK001 No vendor SDK type in domain signatures. Spec never puts Firebase / Coil / Play Services in `commonMain`. `InteractionSink`, `Provider<T>`, `ReconcileEngine`, `Preset`, `Pool`, `Profile` — all domain types. Android SplashScreen is used inside `FirstLaunchActivity` (androidMain), not in domain (FR-001).
- [x] CHK002 Each external SDK has one adapter. `WindowInsetsControllerCompat` used only inside `StatusBarPolicyProvider` (androidMain adapter for `StatusBarPolicy` port) — see FR-005. `AppCompatDelegate.setApplicationLocales()` used only inside the Language provider (FR-004).
- [x] CHK003 "Vendor disappears tomorrow" test. Each Android SDK touchpoint is one Provider file. Documented via FR-002/003/004/005 — 1-to-1 mapping Component → Provider adapter.

## Transport types

- [x] CHK004 No transport types in domain signatures. No HTTP / retrofit / raw JSON in `Preset`/`Pool`/`Profile` shapes. JSON is loaded via `PresetBootstrap` / `HintPoolLoader` (adapters).
- [x] CHK005 Wire format types are domain-owned. `Preset` (schemaVersion 2) and `Pool` (schemaVersion 2) are declared as domain data classes; serializers live in adapters (implicit, follows TASK-120 pattern). `ThemeRef` explicitly excluded from wire format (FR-003, D3).

## Platform types

- [x] CHK006 No `android.*` in commonMain surfaces. Spec is careful: `AppCompatDelegate`, `WindowInsetsControllerCompat`, `Intent.ACTION_CHANGE_DEFAULT_DIALER`, `Android SplashScreen`, `MANUFACTURER`-detection — all in androidMain Provider adapters or in `FirstLaunchActivity`. **WARN**: FR-018 mentions `SystemPermissionProvider` "facade wrapper" — verify at plan time the port stays domain-typed and no `Intent`/`Context` leaks upward through `check()`/`apply()` signatures.
- [x] CHK007 Domain values use domain-typed projections. `ComponentId`, `paletteSeedHex: String`, `locale: String`, `hintId: String`, `textKey: String` — all pure Kotlin. No raw `Uri`/`Bundle`.

## Ports

- [x] CHK008 Every external surface exposed through a port. Spec inventory: `Provider<T>` (already exists TASK-120), `InteractionSink` (FR-008, new domain port), `ProfileStore` / `WizardStore` / `PoolSource` / `PresetSource` (loaders), `SystemPermissionProvider` (FR-018 replaces `SystemSettingPort` + `PermissionRequestPort`).
- [x] CHK009 Port shape driven by domain need. `InteractionSink.answer(componentId, response)` speaks in domain vocabulary — no `getFromSharedPreferences`-style leakage. `Provider.check()/apply()` is component-typed via `<T>`.
- [ ] CHK010 Fake adapter per port. **WARN**: spec references `fakeInteractionSink` in User Story 1 Independent Test and `FontSizeProvider` unit test in US5, but does not enumerate a fake for every new port (e.g. `WizardStore`, `HintPoolLoader`, `SystemPermissionProvider`). Plan.md must list one fake per port per CLAUDE.md §6.
- [x] CHK011 Real adapter per port. Spec explicitly places Providers in `com.launcher.preset.provider.*` (FR-010) with Android adapters. iOS deferred (Assumptions) — additive, KMP-ready port shape preserved.
- [x] CHK012 DI picks fake/real per build. `PresetModule` (rename/merge of `task120Module`) is the single Koin module after Phase 6 (FR-016). Fake wiring pattern inherited from TASK-120.

## Source-set placement

- [ ] CHK013 Every new file assigned to sourceset. **WARN**: spec identifies domain types (ports) and Android providers but does not enumerate `commonMain` vs `androidMain` per file. Plan.md must produce per-file placement (Gate 1 of ADR-005 §3) — critical for `InteractionSink`, `WizardStore` (domain port), and the SplashScreen wiring (androidMain-only).
- [x] CHK014 Default placement commonMain. Spec explicitly says "iOS providers deferred — KMP-ready ports, Android-only adapters" (Assumptions) — signals default commonMain for ports.

## Existing-code regressions

- [x] CHK015 No vendor type reintroduced into previously-cleansed commonMain. Migration collapses 3 engines into one and deletes `WizardCheckpointStore` (FR-011), `AccessibilityService` (FR-005), legacy `wizard/` assets (FR-017). Nothing is added back to domain that was pulled out.
- [x] CHK016 No unnecessary `expect`/`actual`. Providers are plain interface + Android impl (no `expect class Provider`). `SystemPermissionProvider` is facade, not `expect`.

---

## Summary

- **Pass**: 14/16
- **Warn**: 2/16 (CHK010 fake enumeration, CHK013 per-file sourceset placement)
- **Fail**: 0/16

Both warnings are plan-time work items, not spec defects. Spec correctly declares port shapes and Android/domain split; plan.md must (a) list a fake adapter per new port, (b) produce a per-file sourceset placement table before implementation.
