# `com.launcher.api.wizard` — Wizard domain

**Spec**: [015 — Wizard + Localization + Senior UI](../../../../../../specs/015-wizard-localization-senior-ui/spec.md)

**Status**: EXTRACT CANDIDATE per FR-042 — when a second ecosystem app
materialises (messenger, family-photos, etc.), this package becomes its own
KMP library. Today it's a package inside `:core` to keep the build
graph simple (per CLAUDE.md rule 4).

## What's inside

Ports and wire-format types that drive the first-run wizard:

- `WizardEngine` — the state machine. Pure domain. No Android imports.
- `WizardStep` / `StepType` / `StepParams` / `StepResult` — extensible step
  contract. Three concrete impls live in `com.launcher.ui.wizard.steps`.
- `ConfigSource` + `ConfigKind` — loads the 5 bundled wire formats.
- `WizardCheckpointStore` / `DismissedHintsStore` / `UserPreferencesStore`
  — persistence ports (DataStore-backed on Android, in-memory in tests).
- `SystemSettingPort` — abstracts Android permission / settings deep-link
  plumbing behind a domain interface.
- `Clock` / `AnimationPreferenceProvider` / `DiagnosticEmitter` /
  `PermissionRequestPort` — cross-cutting ports.
- `data/` — `@Serializable` wire-format types (5 schemas + 3 persistent
  formats) and `ConfigParser` (forward-compat + hard-fail policy per
  FR-015 / FR-016).

## Architectural rules

Enforced by [Spec015IsolationTest](../../../../../androidUnitTest/kotlin/com/launcher/test/fitness/Spec015IsolationTest.kt)
(per FR-038):

- `api.wizard.*` MUST NOT depend on `com.launcher.ui.*` or `com.launcher.app.*`.
- `api.wizard.*` MAY depend on `api.localization.*`.
- All `WizardStep` implementations live in `com.launcher.ui.wizard.steps`.

## Exit ramps

- TODO(server-roadmap): future `NetworkConfigSource` adapter fetches
  signed configs from our server. See FR-046 + docs/dev/server-roadmap.md.
- TODO(shareability): future `FileImportConfigSource`, `ShareIntentConfigSource`,
  `MarketplaceConfigSource` adapters. The wire-format contract above is
  identical for all of them (per CLAUDE.md rule 9).
- TODO(server-roadmap): future cross-app `UserPreferences` sync via
  `ConfigDocument.userPreferences` in spec 008. Requires F-4 + cloud sync.
- TODO(extract): when a second ecosystem app starts importing this package,
  extract `:core` into `:core-wizard` per FR-042.
