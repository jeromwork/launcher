# Contract: Module registration

**contractId**: `launcher.modules`  
**majorVersion**: `1` (MVP)

## Owner

- **ModuleRegistry** (`core`)

## Registration flow

1. **`app` Application** class (or explicit bootstrap) passes **ModuleDescriptor** list to Core.
2. Registry validates **requiredContracts** against current Core versions.
3. On failure: module enters **degraded** (not loaded) and reason recorded per **Safe fallback** model.
4. On success: module receives **service locator** or explicit handles for `launcher.events`, `launcher.appindex`, `launcher.actions` only (no god object).

## Feature module constraints

- MUST NOT register OS listeners.
- MUST expose only **declared publishedSurfaces** to shell/other modules.

## Dynamic graph observation

- Registry exposes read-only `registeredModules: StateFlow` or equivalent for shell composition.

## Extension (spec FR-020)

- Adding a module = add descriptor + wire in `app` + profile flag; Core changes limited to contract version bumps or new **published** types, not one-off feature branches inside unrelated packages.
