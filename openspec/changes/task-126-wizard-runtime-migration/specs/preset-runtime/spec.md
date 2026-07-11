## ADDED Requirements

### Requirement: LauncherRole component
The system SHALL include a `LauncherRole` Component subtype with no parameters. When present in a Preset's `wizardFlow`, the Provider SHALL check whether the launcher is already set as the default home app and skip the system dialog if so. When absent from a Preset, the system SHALL NOT request home app assignment.

#### Scenario: Launcher not yet default home
- **WHEN** `LauncherRole` is in `wizardFlow` AND launcher is not currently the default home app
- **THEN** ReconcileEngine presents the Android system role-request dialog to the user

#### Scenario: Launcher already default home
- **WHEN** `LauncherRole` is in `wizardFlow` AND launcher is already the default home app
- **THEN** ReconcileEngine skips the step without showing any dialog

#### Scenario: Preset without LauncherRole
- **WHEN** a Preset does not include `LauncherRole` in any flow
- **THEN** the system SHALL NOT request home app assignment at any point during wizard or BootCheck

### Requirement: Theme component
The system SHALL include a `Theme` Component subtype with fields `paletteSeedHex: String`, `typographyScale: TypographyScale`, `shapeStyle: ShapeStyle`, `darkMode: Boolean`. A `ThemeRef(name: String)` convenience type SHALL expand to these four fields at write time against a bundled `theme-catalog.json`. The wire format SHALL never contain `ThemeRef` — only expanded flat fields.

#### Scenario: Named theme applied
- **WHEN** user selects a named theme (e.g., "warm") during wizard
- **THEN** system resolves it from `theme-catalog.json` and writes expanded fields to Preset
- **AND** Compose UI reflects the new Material 3 palette on next composition

#### Scenario: Custom field override
- **WHEN** Preset contains individual flat fields without a ThemeRef
- **THEN** Provider applies them directly without catalog lookup

#### Scenario: ThemeRef not in wire format
- **WHEN** a Preset is serialized to JSON
- **THEN** the JSON SHALL contain `paletteSeedHex`, `typographyScale`, `shapeStyle`, `darkMode` fields, never a `themeRef` field

### Requirement: Language component
The system SHALL include a `Language` Component subtype with field `locale: String`. The sentinel value `"system"` SHALL mean "follow OS locale". Default for new Presets SHALL be `"system"`.

#### Scenario: Explicit locale selected
- **WHEN** `Language(locale: "ru")` is in Preset
- **THEN** app locale is set to Russian via `AppCompatDelegate.setApplicationLocales()`

#### Scenario: System sentinel
- **WHEN** `Language(locale: "system")` is in Preset
- **THEN** app follows OS locale and does not override it

#### Scenario: Null locale forbidden
- **WHEN** a Preset JSON contains `language: null`
- **THEN** PresetValidator SHALL throw a validation error at deserialization time

### Requirement: StatusBarPolicy component
The system SHALL include a `StatusBarPolicy` Component subtype with no parameters. Provider SHALL hide the status bar via `WindowInsetsController.hide(statusBars())`. The `AccessibilityService` previously used for this purpose SHALL be deleted. The `uses-accessibility-service` manifest declaration SHALL be removed.

#### Scenario: Status bar hidden via Preset
- **WHEN** `StatusBarPolicy` is present in Preset's `activeComponents`
- **THEN** status bar is hidden on app start via `WindowInsetsControllerCompat`

#### Scenario: Status bar visible when absent
- **WHEN** `StatusBarPolicy` is absent from Preset
- **THEN** status bar remains visible (OS default)

#### Scenario: AccessibilityService absent
- **WHEN** app is installed
- **THEN** manifest SHALL NOT declare `android.accessibilityservice.AccessibilityService`

### Requirement: Component dependency validation
Pool JSON component descriptors SHALL support an optional `requires: List<ComponentId>` field. A `PresetValidator` SHALL run at Preset deserialization and verify that for each Component in `wizardFlow`, all its declared dependencies appear earlier in the flow. Violation SHALL throw a `PresetValidationException` with the offending component ID and missing dependency ID.

#### Scenario: Valid ordering
- **WHEN** Preset `wizardFlow` lists `LauncherRole` before `AppTile`
- **AND** `AppTile` declares `requires: ["launcher-role"]` in pool.json
- **THEN** PresetValidator passes without error

#### Scenario: Invalid ordering detected at load time
- **WHEN** Preset `wizardFlow` lists `AppTile` before `LauncherRole`
- **AND** `AppTile` declares `requires: ["launcher-role"]`
- **THEN** PresetValidator throws `PresetValidationException("AppTile requires launcher-role which appears later in wizardFlow")`

#### Scenario: No requires field — no validation
- **WHEN** a component descriptor in pool.json has no `requires` field
- **THEN** PresetValidator imposes no ordering constraint for that component

### Requirement: TutorialHint as separate pool
The system SHALL support a `hint-pool.json` file separate from the main component pool. Preset SHALL include an optional `hintFlow: List<HintFlowEntry>` field. `HintFlowEntry` SHALL contain `hintId: String`, `targetComponentId: String`, `textKey: String`. TutorialHint SHALL NOT be a `Component` subtype and SHALL NOT be processed by `ReconcileEngine`.

#### Scenario: Hint shown for component
- **WHEN** a Preset contains `hintFlow` entries and a Component completes its wizard step
- **THEN** the UI displays the matching hint overlay for `targetComponentId` if present in `hintFlow`

#### Scenario: No hintFlow — no hints
- **WHEN** Preset has no `hintFlow` field
- **THEN** no hint overlays are shown during wizard or post-wizard

#### Scenario: ReconcileEngine ignores hints
- **WHEN** `ReconcileEngine.run()` processes a Preset
- **THEN** it SHALL NOT read or process `hintFlow` — hint rendering is UI-layer responsibility

### Requirement: Single engine — no legacy wizard imports
The system SHALL NOT contain any import of `com.launcher.api.wizard.*` in production code after migration. A lint fitness function (FF-011) SHALL enforce this as a build-time check.

#### Scenario: Fitness function blocks legacy import
- **WHEN** a Kotlin source file in `app/` or `core/` contains `import com.launcher.api.wizard`
- **THEN** the lint check FF-011 SHALL fail the build with a descriptive error

#### Scenario: Clean codebase after Phase 6
- **WHEN** `./gradlew lint` runs after Phase 6 deletion
- **THEN** FF-011 reports zero violations

### Requirement: Wire format backward compatibility
All Preset and Pool JSON schema changes in this migration SHALL be additive. `schemaVersion` SHALL be bumped for Preset (new `hintFlow` field) and Pool (new `requires` field on component descriptors). Older readers SHALL ignore unknown fields without error.

#### Scenario: Old reader ignores new fields
- **WHEN** a Preset JSON with `schemaVersion: 2` and `hintFlow` field is read by a schemaVersion 1 reader
- **THEN** the reader ignores `hintFlow` and loads remaining fields correctly

#### Scenario: New reader handles missing hintFlow
- **WHEN** a Preset JSON with `schemaVersion: 1` (no `hintFlow`) is read by current reader
- **THEN** reader defaults `hintFlow` to empty list without error
