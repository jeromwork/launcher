# Extension guide: first-party modules (001 foundation)

This guide is the onboarding path for adding a capability without rewriting Core. It aligns with [spec.md](./spec.md) **FR-026** and [contracts/module-registration.md](./contracts/module-registration.md).

## What you integrate with

| Need | Use | Do not |
|------|-----|--------|
| Installed apps / catalog | `AppIndex` contract (`launcher.appindex`) | Query `PackageManager` directly from a feature module for launcher-visible catalog |
| Launch / settings intents | `ActionDispatcher` (`launcher.actions`) | Start activities from raw strings without going through dispatch policy |
| OS-related signals | `EventRouter` project events (`launcher.events`) | `registerReceiver` or other OS listeners inside `:feature-*` |
| Enable/disable in UI | `ProfileEngine` / profile `moduleFlags` + `CompositionResolver` | Bypass profile resolution |

Core owns **SystemEventBridge**; only Core normalizes platform broadcasts into project events.

## Steps to add a module (e.g. “Favorites”)

### 1. Create a Gradle module

- Add `:feature-favorites` (name pattern `feature-<capability>`).
- Depend on `:core` for API types and service wiring (no dependency from `:core` back to the feature).

### 2. Declare a `ModuleDescriptor`

In the **app** module, extend the list in `com.launcher.app.AppModuleDescriptors` (or equivalent bootstrap):

- **`moduleId`**: stable string; must match the key you will use in profile `moduleFlags`.
- **`requiredContracts`**: `ContractRequirement(contractId, minimumMajorVersion)` for each contract the module needs. Majors supported by this Core build are defined in `CoreContractVersions`.
- **`publishedSurfaces`**: conceptual names of surfaces your module exposes to the shell or other modules (see module-registration contract).

If a required major is higher than Core provides, **ModuleRegistry** marks the module **degraded** (`contractSatisfied == false`); `CompositionResolver` will not treat it as enabled regardless of profile intent.

### 3. Wire construction and handles

- **Application** creates `LauncherCore(context, moduleDescriptors = …)` once (see `LauncherApplication`).
- Pass feature instances **handles** to `launcher.events` / `launcher.appindex` / `launcher.actions` surfaces (locator or explicit constructor injection). Avoid a single “god” object; add new published types in `core` only when the contract requires it (**FR-018**, **FR-025**).

### 4. Profile flag

- Add your `moduleId` to the active profile’s `moduleFlags` when the capability should be on.
- Default bundled profile is `core/src/main/assets/default_profile.json`; use `"yourModuleId": true` when you want it on by default.

### 5. Review checklist

- [ ] No `registerReceiver` (or other OS listeners) in `app/src` feature code — only Core’s bridge.
- [ ] No feature-specific business logic embedded in unrelated `core` packages; Core changes limited to contracts/registry/compatibility.
- [ ] Catalog and launch paths go through AppIndex / ActionDispatcher.

## Contract IDs (MVP)

See `CoreContractVersions` in source for the authoritative map. Conceptually: `launcher.events`, `launcher.profile`, `launcher.modules`, `launcher.appindex`, `launcher.actions`.

## Further reading

- [contracts/module-registration.md](./contracts/module-registration.md) — registration flow and constraints.
- [contracts/project-events.md](./contracts/project-events.md) — event vocabulary.
- [data-model.md](./data-model.md) — profile vs module resolution order.
