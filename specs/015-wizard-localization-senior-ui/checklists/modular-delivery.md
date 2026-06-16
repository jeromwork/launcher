# Checklist: modular-delivery

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16 (post meta-minimization fixes — three premature abstractions removed)
**Verdict**: 13 ✓ / 0 ⚠ / 0 ✗ + 5 N/A — clean (foundation handheld scope, all form-factor expansion explicitly deferred)

---

## Scope of the feature

- [✓] **CHK001** Form-factor classification explicit.
  - **Бизнес-логика (`core/wizard/`, `core/localization/`)**: form-factor-agnostic, KMP commonMain. ✓
  - **UI (`core/ui-senior/`)**: form-factor-specific — Android handheld/tablet Compose. **НЕ** TV, **НЕ** auto, **НЕ** wear. ✓ explicit per C-7 + FR-033.
  - **Bundled pool (`android-pool.json`)**: handheld Android. iOS / TV / wear pools — explicit deferred (OUT-019, FR-053b inline TODO). ✓

- [✓] **CHK002** Form-factor-specific code в dedicated module.
  - Android UI — `core/ui-senior/` (Android library).
  - Android adapters — `:app/androidMain` (`PersistentCheckpointStore`, `AndroidSystemSettingAdapter`, etc.).
  - TV variants (когда material'изуются) → `core/ui-senior-tv/` (separate module, не в core/ui-senior/). ✓ planned architecture.

- [✓] **CHK003** Form-factor-agnostic shared code не leak's vendor SDK / platform-specific APIs.
  - `commonMain` — pure Kotlin + multiplatform libs (moko-resources, kotlinx-datetime, Kotlin Serialization).
  - Verified by FR-038 Konsist lint rule (`core/* → app/*` import ban + FR-038a directional graph).
  - Domain types use BCP-47 String for Locale (per DI-1 fix), `Instant` from kotlinx-datetime (multiplatform). ✓

## Module placement

- [✓] **CHK004** No new vendor SDK in Core.
  - moko-resources — multiplatform, **not** vendor SDK; serves as ACL for resource loading.
  - DataStore (AndroidX) — Android-only, lives в `:app/androidMain` через port pattern.
  - Compose — Android-only, lives в `core/ui-senior/` (which IS form-factor-specific module per CHK002).
  - Claude API — **dev-time** skill, **not runtime** app code.

- [✓] **CHK005** Article V §7 answered for each new module.
  - **`core/wizard/`**: Why not package? KMP common target — package within `:app` impossible. API boundary: `WizardEngine`, `ConfigSource`, `SystemSettingPort`, `UserPreferencesStore` ports. Complexity removed: testability на JVM без эмулятора (saves 2-4 min per cycle per A-19).
  - **`core/localization/`**: Why not package? KMP target + isolated locale logic + CI fitness function dependency. API: `StringResolver` port. Complexity removed: i18n logic isolated от app logic.
  - **`core/ui-senior/`**: Why not package? Android library boundary for senior-friendly UI primitives. API: `SeniorButton`, `SeniorWarmTheme`, etc. Complexity removed: senior-safe overrides centralized (≥56dp, warm-contrast, fontScale-aware).
  - **Recommendation**: добавить эту аргументацию в Assumptions как A-5b. Optional, не блокер.

- [✓] **CHK006** Form-factor-specific code в shared module с explicit regret conditions.
  - `core/ui-senior/` Android-only — **trigger для split**:
    - iOS UI: «когда первый iOS launcher материализуется — отдельный модуль `core/ui-senior-ios/` или CMP refactor» (A-12, OUT-019). ✓
    - TV UI: «когда первый TV launcher материализуется — отдельный модуль `core/ui-senior-tv/`» (OUT-019). ✓
  - Не «как-нибудь потом», а explicit triggers + design ready (port-based architecture позволяет swap).

## Profile / preset declaration

- [N/A] **CHK007** Profile `requiredModules` / `optionalModules`.
  - F-3 не introduces profile / app-family declaration. Это S-1 / S-2 territory (per OUT-001).

- [N/A] **CHK008** Profile schema bump.
  - F-3 определяет `wizard.manifest` schema (first version: `schemaVersion: 1`). Не bumps existing.

- [✓] **CHK009** Base application loads без new module.
  - F-3 — foundation: без него Phase 1 app fundamentally не запустится (wizard, localization, ui-senior — required).
  - Это **intentional** для foundation. ✓ documented в roadmap.md «F-3 первый шаг».

## Form-factor expansion (non-handheld)

- [N/A] **CHK010-012** Non-handheld form factor delivery channel.
  - F-3 explicitly **не ships** TV / voice / wear / auto code (OUT-019).
  - Когда первый non-handheld consumer материализуется — отдельная спека добавит iosMain / tvMain source sets + delivery channel ADR.
  - **Acceptable defer** — это per C-3 + C-7 + OUT-019 explicit design constraint.

## One-way doors raised

- [✓] **CHK013** One-way doors documented с alternatives + regret conditions.
  - **KMP commonMain choice** (C-7 + A-11): alternatives (pure Android, full CMP, hybrid) considered; regret condition если CMP iOS support stalls.
  - **moko-resources choice** (C-8 + A-14): alternatives (Compose Resources, Lyricist) documented.
  - **Konsist choice** (C-15 + A-15): alternatives (ArchUnit, custom Gradle) documented.
  - **Android-only ui-senior** (C-7 + A-12): regret condition «когда iOS launcher материализуется, refactor cost ~2-4 weeks».
  - **English как base language** (C-6 + A-15b): override от ADR-004 explicit, migration path noted в Cross-spec impact.

- [✓] **CHK014** «Vendor disappears tomorrow» test.
  - moko-resources gone → 1 file (StringResolver impl) — bounded.
  - DataStore gone → 3 files (store impls) в `:app/androidMain` — bounded.
  - AccessibilityService Android API change → 1 file (`AndroidSystemSettingAdapter`) — bounded.
  - **All within single-adapter-module bounds** per CLAUDE.md rule 2. ✓

- [✓] **CHK015** Free workaround / server-roadmap entry.
  - F-3 wizard preferences = local-only (UserPreferencesStore) instead of «proper» cloud sync.
  - FR-051 inline-TODO explicit.
  - `docs/dev/server-roadmap.md` entry planned в Cross-spec impact: «UserPreferences cloud sync — additive в спеке 008 после F-4».

## Anti-bloat sanity

- [✓] **CHK016** No Gradle module для single class / single-impl interface.
  - `core/wizard/`: 8+ ports (WizardEngine, ConfigSource, WizardStep + 9 step impls, SystemSettingPort, UserPreferencesStore, DiagnosticEmitter, DismissedHintsStore, WizardCheckpointStore) + 4 JSON schemas + Pool + TutorialHintManager. **Substantial content**.
  - `core/localization/`: StringResolver port + impl + RTL helper + CI fitness function + CONTEXT.json + GLOSSARY.md + translation skill integration. **Substantial content**.
  - `core/ui-senior/`: 5+ primitives + 2 theme variants + 2 utilities. **Substantial content**.

- [✓] **CHK017** No pre-emptive split «in case multi-form-factor».
  - Three modules — design'ются launcher-agnostic per **real** ecosystem plan (C-2 «messenger / album планируется в Phase 4»), не speculative.
  - A-5 weakened к «discipline tool, не extraction prerequisite» (per meta-minimization checklist update).
  - **Post-meta-minimization fixes**: WizardStepRegistry, MigrationRegistry skeleton, ResourceReader port removed — eliminated three speculative abstractions. ✓

- [✓] **CHK018** Future splits recorded as regret conditions.
  - System-settings pool extraction → inline-TODO в `android-pool.json` (FR-053b): «when second real consumer materializes».
  - Three core modules extraction (отдельная library) → README inline-TODO (FR-042): «extract on second consumer».

---

## Резюме

**13 ✓ / 0 ⚠ / 0 ✗ + 5 N/A** — F-3 modular-delivery discipline чистая.

Critical observations:
- Все form-factor expansion (iOS, TV, voice, auto, wear) **explicitly deferred** с named triggers, не «потом разберёмся».
- Meta-minimization fix'ы (3 removed abstractions) усилили cleanliness.
- Architecture choices правильно decoupled через ports + KMP source sets.

**No spec edits required** — modular-delivery gates pass cleanly.
