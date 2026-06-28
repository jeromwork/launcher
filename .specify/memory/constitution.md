<!--
Sync Impact Report — Universal App / GitHub Spec Kit
- Version: 1.5.0 → 1.6.0
- Principles: Added Article XIX (Organic Question Budgets in Clarification Passes) — overrides any prior numeric question caps in `speckit-clarify`, `mentor`, and analogous skills. Question count is organic, typical range 3–7, no hard cap. Padding to target or trimming below organic count are both bugs.
- Skill alignment: `.claude/skills/speckit-clarify/SKILL.md` Step 3 + `.claude/skills/mentor/SKILL.md` step 5 updated to reference Article XIX and remove «5 questions» hard limit.
- Previous: 1.4.0 → 1.5.0
- Principles: Article II redefined project identity from "launcher product" to universal Android app
  with launcher capabilities as one supported use case; Articles III, V, VII, IX, and
  Project-Specific Architectural Direction aligned to app-owned / launcher-mode wording
- Previous: 1.3.0 → 1.4.0 — Added required `docs/**` context review rules
- Previous: 1.2.0 → 1.3.0
- Principles: Article XVIII strengthened — MUST commit and push after each significant step (with exceptions); Article XV §13 aligned
- Previous: 1.1.0 → 1.2.0 — Added Article XVIII; Article XV §13 cross-reference for AI agents
- Previous: Replaced generic principle placeholders with Articles I–XVII and supporting sections
- Added: Preamble, Normative Levels, Articles I–XVII, Delivery Rules for Spec Kit Artifacts,
  Project-Specific Architectural Direction, Quality Bar, Reference Patterns, Amendment History
- Removed: Template "Core Principles" / SECTION_2 / SECTION_3 / Governance placeholders
- Templates: plan-template.md ✅ reviewed | spec-template.md ✅ reviewed | tasks-template.md ✅ reviewed
- Commands: .specify/templates/commands/*.md — not present in this repo
- Runtime docs: AGENTS.md ✅ manual project-identity clarification added
- Follow-up TODOs: none
-->

# Universal Android App — Constitution for GitHub Spec Kit

**Status**: Adopted Draft 1.5  
**Project**: Universal Android application with accessibility-first launcher capabilities  
**Scope**: Governs all future `spec`, `plan`, `tasks`, code generation, code review, refactoring, test design, release preparation, and architectural decisions.  
**Audience**: Human maintainers and AI agents working through GitHub Spec Kit.

---

## Preamble

This constitution defines the non-negotiable engineering rules for this universal Android application.

The project is not a demo application and MUST NOT be treated as "only a launcher".
It is a long-lived Android product intended to remain maintainable as functionality grows:
accessibility modes, elderly-friendly UX, configurable builds, modular features,
profile-driven behavior, communication and assistance workflows, and potentially optional
downloadable modules. Launcher behavior is an important supported mode and product case,
not the whole product boundary.

All future specification, planning, implementation, review, and release work MUST optimize for:

1. **User safety and clarity** — app-owned surfaces, including launcher-mode surfaces, are core UX infrastructure, not toys.
2. **Reliability on real devices** — especially under low memory, device restarts, OEM variations, and intermittent permissions.
3. **Battery discipline** — background processing and event listening are controlled and justified.
4. **Accessibility first** — elderly users and users with low vision, reduced dexterity, or cognitive load sensitivity are first-class target users.
5. **Modular growth without architectural decay** — features may expand, but the codebase must remain understandable by a small team and by AI agents.

If a specification, plan, implementation, or proposed architecture conflicts with this constitution, the constitution wins unless an explicit exception is documented under Article XVII.

---

## Normative Levels

The keywords in this constitution are normative and have the following force:

- **MUST** — mandatory. Violation blocks approval unless an exception is explicitly documented.
- **SHOULD** — strong default. Deviation is allowed only with written rationale in `plan.md` or an ADR.
- **MAY** — optional and context-dependent.

This constitution intentionally separates:

1. **Non-negotiable rules** — binding product and engineering constraints.
2. **Architectural defaults** — preferred design direction that may be overridden with justification.
3. **Execution and review gates** — required checks that make compliance auditable.

---

## Article I. Specification-First Delivery

1. No implementation begins from vague intent alone.
2. Every feature MUST start with a Spec Kit `spec` describing:
   - user problem,
   - user value,
   - constraints,
   - acceptance criteria,
   - non-goals.
3. `spec.md` MUST describe **what** and **why**.
4. `plan.md` MUST describe **how** the approved specification will be implemented.
5. `tasks.md` MUST decompose only the approved plan. Tasks MUST trace back to spec requirements.
6. If uncertainty remains, the artifact MUST state `NEEDS CLARIFICATION` rather than silently invent behavior.

**Rationale**: Spec Kit works best when requirements, architecture, and execution remain separate.

---

## Article II. Product Identity and Product Reality

1. The product MUST be designed as a production universal Android application, not as a sample app.
2. The product MUST NOT be specified, planned, or implemented as if "launcher" is the
   complete product category. Launcher functionality is one supported use case and one
   app-owned operating mode among other approved capabilities.
3. Launcher-mode behavior MUST remain coherent and reliable where required, but architecture
   MUST leave room for non-launcher app features without forcing every feature through a
   launcher metaphor.
4. Every feature MUST justify its operational cost in one or more of these dimensions:
   - user value,
   - accessibility impact,
   - maintainability,
   - reliability,
   - measurable performance.
5. "Nice to have", "might need later", and speculative extensibility are prohibited unless explicitly justified.
6. The default engineering posture SHOULD be **simple now, extensible only when evidence exists**.
7. Every new module, abstraction, event type, or configuration layer MUST have an explicit reason.
8. **MVP definition.** The MVP delivers all *base functional blocks* working end-to-end — encryption, push, server integration, auth, recovery, remote management, screen rendering, settings — but is NOT yet polished for end-user experience. UX refinement, visual smoothness, and per-profile tuning happen *after* MVP through **configuration changes** (JSON wire-format documents per Article VII §10), not through new code branches or new modules.

   Specifically post-MVP work consists of:
   - Tuning bundled JSON pool entries and profiles based on usability observations (which sequences of steps work best for which target populations).
   - Visual / animation polish through Compose theming changes.
   - Adding new bundled documents (additional `tile.set`s, `screen.layout`s, adaptive-UX presets) without code changes — pure JSON content authoring.

   The MVP is "shippable as functional product" but not "delightful as user experience". Targeting delight at MVP stage is explicit anti-pattern: it slows base-block delivery and embeds early UX guesses into code where they're expensive to revise. Article VII §13 reinforces this — new profiles ship as JSON, not as code.

---

## Article III. Core Product Invariants

The following are non-negotiable product and engineering invariants:

1. Accessibility is a release concern, not a polish concern.
2. Battery waste, unnecessary background work, and uncontrolled listeners are product defects.
3. App-critical and launcher-mode behavior MUST remain reliable across device restarts, configuration changes, OEM variations, and partial permission states.
4. Invalid configuration MUST fail safely with deterministic fallback.
5. Optional capabilities MUST NOT compromise base application stability or launcher-mode stability.
6. Security, privacy, and permission scope MUST remain proportionate to approved product requirements.
7. **Stability over system-level changes.** The application MUST treat each applied configuration setting as *persistent user intent*. After a setting is explicitly applied through wizard or settings UI, the application MUST NOT silently follow a conflicting system-level change (locale, theme, font scale, accessibility settings). The user-applied value takes precedence until explicitly changed through the application's own settings.

   Rationale: primary user (elderly, low cognitive capability per Article VIII) relies on stable, predictable behaviour. Surprise UI shifts triggered by system-level changes the user did not initiate (sometimes by accident) are a top-tier defect — they break the foundational trust relationship. This applies to locale (Article VII §12 already reflects per-profile manifest semantics), theme, font scale, grid size, and any future user-applied configuration value.

   Concrete enforcement examples:
   - Locale: after wizard saves `languageOverride`, the application MUST call `AppCompatDelegate.setApplicationLocales()` (or platform equivalent) so the application override persists across system locale changes.
   - Theme: after wizard saves `ThemeChoice`, the application MUST honour the saved value regardless of `Configuration.uiMode` system shifts.
   - Font scale: similar persistence vs system font scale changes.

   Exception: device-level safety changes (emergency calls, accessibility services explicitly mandated by Android) MAY override application preferences when required by platform.

---

## Article IV. Canonical Architecture Defaults

The project SHOULD follow a layered Android architecture with at minimum:

- **UI layer**,
- **data/domain orchestration layer**,
- **data sources/integration layer**.

Additional defaults:

1. UI SHOULD be driven from state rather than imperative callbacks scattered across the app.
2. Unidirectional data flow SHOULD be the default.
3. Repositories SHOULD be the standard boundary between business logic and concrete data sources.
4. A dedicated domain/use-case layer SHOULD be introduced only when it materially reduces duplication or clarifies orchestration.
5. State ownership SHOULD avoid patterns that are fragile across recreation.
6. Models SHOULD be separated into transport, domain, and UI forms only where that separation reduces leakage and coupling.

**Precedence note**: This article defines preferred architecture, not a ban on exceptions. A plan may deviate if it preserves the product invariants in Articles I–III and documents why the alternative is simpler or more robust.

---

## Article V. Modularization With Restraint

1. Modularization is required as the product grows, but over-modularization is forbidden.
2. The initial structure SHOULD remain intentionally compact.
3. New Gradle modules SHOULD be introduced only when at least one of the following is true:
   - ownership boundary is clear,
   - build isolation is useful,
   - feature can be enabled or disabled independently,
   - API boundary is stable and valuable,
   - testability or release separation materially improves.
4. Modules MUST be loosely coupled and purpose-specific.
5. Cross-feature dependencies are prohibited except through approved shared contracts.
6. Shared modules MUST stay minimal. “utils” dumping grounds are prohibited.
7. Before adding any module, the plan MUST answer:
   - Why is a package not enough?
   - What API boundary does this module protect?
   - What complexity does it remove now?

**Project-specific rule**: Because the application may evolve toward configurable builds, launcher-mode variants, and optional modules, the architecture MUST support modular features; however, the team SHOULD favor a small, disciplined module graph over micro-modules.

---

## Article VI. Core Owns System Integration

1. All direct interaction with Android system broadcasts, package events, boot events, permission result orchestration, and other process-wide platform signals MUST be centralized in **Core** or another explicitly designated system-integration boundary.
2. Feature modules MUST NOT register arbitrary global listeners directly unless an exception is approved.
3. If a feature needs a system event, it MUST declare that need through a documented Core event contract or an approved equivalent boundary.
4. Core is responsible for:
   - registration strategy,
   - lifecycle safety,
   - deduplication,
   - throttling or debouncing where needed,
   - battery impact control,
   - translating raw Android signals into project-level events.
5. Raw system callbacks MUST NOT leak deep into feature code.
6. Every added event listener MUST document:
   - source,
   - frequency,
   - threading,
   - expected battery cost,
   - fallback behavior if the event is delayed or absent.

---

## Article VII. Profile-Driven and Configurable by Design

1. The application MUST treat configuration as a first-class concern.
2. User-facing and distribution-facing variability MUST be modeled explicitly through profiles and structured configuration, not hidden `if/else` branches scattered across the codebase.
3. A profile or configuration system MAY use JSON or another structured schema, but it MUST define:
   - versioning,
   - validation,
   - backward-compatibility rules,
   - defaults,
   - migration policy.
4. Configuration SHOULD be declarative where practical.
5. Profiles MAY enable or disable modules, features, layouts, **adaptive-UX presets** (see Project-Specific Direction §5), or integration policies only through documented contracts. Note: an **adaptive-UX preset** (variant for tremor, low vision, reduced dexterity, or cognitive load) is a sub-feature *inside* a profile — it is not itself a profile and is not at the same abstraction level as `simple-launcher`, `admin-app`, etc.
6. Runtime-loaded or downloadable modules MUST remain optional enhancements, never assumptions for base application or launcher-mode stability.
7. AI-generated changes MUST NOT silently widen config schema without updating:
   - schema docs,
   - validation logic,
   - migration logic,
   - example profiles,
   - tests.
8. A profile MUST explicitly declare its module dependencies through two fields in its schema:
   - `requiredModules`: modules without which the profile cannot be activated; the runtime MUST refuse activation and surface a clear reason if any are absent.
   - `optionalModules`: modules that enhance the profile but whose absence MUST degrade gracefully without breaking the base application or any active launcher-mode surface.
   The profile schema MUST treat these fields as part of the wire format (versioned, validated, backward-compatible per Article VII §3). End-user-facing prompts about module installation are not required: the project's primary persona (elderly user) does not configure devices — module acquisition is performed by an administrator/caregiver on the user's behalf, so the installation UX MAY be admin-facing only.

9. **Profile composition.** The application's behaviour for a given product variant is expressed as a **profile** — a named composition of configuration documents that together determine the wizard flow, the main-surface composition, the in-cell content, the platform-level settings the variant uses, and the in-app UI options. Examples: `simple-launcher` (the elderly-friendly handheld variant), `admin-app` (remote-administrator variant), `clinic-patient-app` (B2B clinic variant). A profile is identified in wire format by the field `appFamilyId` (this field name is retained for backward compatibility per Article VII §3 and CLAUDE.md rule 5; the conceptual term used in specs and discussion is **"profile id"**). Behaviour of one APK across profiles MUST come from different bundled / loaded configurations, not from code branches keyed on the profile id.

10. **Wire format kinds (current generation).** As of the current schema generation, a profile is composed by reference to documents of these kinds (`ConfigKind` enum, declared in `core/src/commonMain/kotlin/com/launcher/api/wizard/ConfigSource.kt`):
    - `wizard.manifest` — first-run walkthrough scenario (StepEntry sequence referencing pool entries).
    - `screen.layout` — main-surface composition: grid dimensions, toolbars, tabs, and — as the format evolves — status-bar configuration, app-shortcut intents, and multi-screen navigation. The current minimal schema (grid + bottom toolbar + top tabs) is expected to grow.
    - `tile.set` — concrete content occupying screen.layout cells (position, action type, label, icon).
    - `system-settings.pool` — catalogue of platform-level settings the profile uses; each entry declares mechanism (StandardPermission / SpecialPermission / DeepLink / AccessibilityService / InAppOnly), criticality, `canSkip`, deep-link target, detection strategy.
    - `ui-customization.pool` — catalogue of in-app UI options (language, theme, font scale, grid dimensions, picker references to bundled `screen.layout` and `tile.set`).
    
    This division reflects the **current schema generation** and is **expected to evolve**. Kinds MAY split (for example, `screen.layout` separating into `screen.shell` / `screen.grid` / `screen.intents`), merge, or be added. Any change MUST follow [`CLAUDE.md`](../../CLAUDE.md) rule 5: explicit `schemaVersion` bump on every affected wire format, backward-compatible reads possible for at least one major release, versioned migration written **before** the breaking change ships. Specs and code MUST treat the set of kinds as data (operating on the `ConfigKind` enum) rather than hardcoded N-way branches over fixed kind names. Adding or removing a kind is a one-way door per CLAUDE.md rule 3 and requires explicit exit-ramp documentation.

11. **Wizard as profile view.** A first-run wizard is the same set of settings that constitute the profile, exposed as a sequential walkthrough. Every setting visible in the wizard MUST also be reachable through the in-app Settings surface after first run, so that users (or, more commonly, their administrators or assisting family members) can change choices later without re-running the wizard. The wizard is one **view** of the profile, not the source of truth — the source of truth is the applied set of configuration documents.

12. **Mandatory / optional / skip semantics.** Within a profile's wizard each setting falls into one of three categories:
    - **Mandatory** — the user (or assisting administrator) MUST apply the setting; the wizard cannot proceed past it without an applied value or, where the profile allows, an explicit opt-out with a recorded reason. The profile remains visibly incomplete until this is resolved.
    - **Optional with skip-banner** — MAY be skipped during the wizard; the in-app Settings surface MUST display a reminder banner that the entry was skipped and offer a direct path to set it.
    - **Optional silent** — does not trigger a reminder banner; users can find the setting in the Settings surface but the profile does not actively prompt for it.
    
    The split is declared **per-entry in the relevant pool document** (`system-settings.pool` / `ui-customization.pool`) through `criticality` and `canSkip` fields, and MAY be overridden **per-profile** by the `wizard.manifest` (`StepEntry.canSkip`, `StepEntry.criticality`). Pool-level defaults represent reasonable defaults across profiles; per-profile overrides express the choices a specific product variant has made (for example: `android.role.home` is `canSkip: true` in `android-pool.json` so other profiles MAY skip it, but the `simple-launcher` profile MAY override to `canSkip: false` because without ROLE_HOME the simple-launcher experience is meaningless).

13. **No per-profile code module.** A new profile MUST NOT be introduced as a new Gradle module of code dedicated to that profile, and MUST NOT add a code branch keyed on `appFamilyId`. New profiles ship as new bundled JSON documents — a new `wizard.manifest` plus, where the existing bundled `screen.layout`s, `tile.set`s, and pool entries are insufficient, new bundled documents and / or new pool entries. Where a new profile genuinely requires a capability the existing `ConfigKind` set cannot express, a new kind MAY be added under §10, but the proposal MUST justify why the existing kinds were insufficient and MUST follow the schema evolution rules in [`CLAUDE.md`](../../CLAUDE.md) rule 5.

14. **Configuration lifecycle is independent of application lifecycle.** An application update (new APK version) and a configuration update (new bundled JSON in pool / new `wizard.manifest` / new `screen.layout` / etc.) are *independent events*. An application MUST be able to:
    - Update its bundled configurations through a new APK version (current path).
    - Update its configurations from a non-bundled source (file import, network, AI-agent-generated, marketplace) through a `ConfigSource` adapter pattern *without* requiring a new application version (future path; current implementation: `BundledConfigSource`. Future adapters: `FileConfigSource`, `NetworkConfigSource`, `McpConfigSource`, `MarketplaceConfigSource`).
    
    **Config-check master pattern.** When the application launches (or after a configuration update is applied), the engine MUST:
    - Load the current profile manifest (e.g., `simple-launcher.wizard.manifest.json`) via `ConfigSource`.
    - For each step entry, check the *actual current state* on the device — NOT a stored snapshot of which steps were previously completed:
      - For `SystemSetting` steps: query `SystemSettingPort.status(refId)`. Setting may have been applied through wizard, through Android system settings directly, through an admin remote push, or through an MCP agent — engine treats them identically.
      - For `UIChoice` steps: query `UserPreferencesStore.current()` for a valid value matching the current pool's allowed choices.
    - Skip steps whose actual state is already in the desired configuration.
    - Include in the pending list only steps whose state is `NotApplied` or `Indeterminate` or whose stored value is invalid against current pool definition.
    
    If the pending list is empty → no wizard runs. If the pending list is non-empty → the wizard runs as a **donastroika view** showing only the pending steps. Application updates MUST NOT silently re-trigger the full wizard. The user-applied state (per Article III §7) is preserved across application updates; only genuinely new settings require user input.
    
    **Why state-of-device, not snapshot-of-manifest.** A primary-user (or an assisting administrator, or an external Android system change) MAY apply settings through paths other than the wizard — Android Settings directly, admin remote push, AI agent capability invocation, future file import. The engine MUST detect that a setting *is* in the desired state regardless of *how* it got there. A snapshot of "what steps the wizard completed last time" would diverge from device reality whenever any non-wizard path is used.

15. **Multi-platform adapter seam.** The wire-format kinds in §10 are platform-agnostic; the *implementations* that detect, apply, and validate settings are platform-specific. The architecture MUST keep this seam strict:
    - All `ConfigKind` documents, all data classes, all sealed type hierarchies (including `CheckSpec`, `ApplySpec`, and similar declarative dispatch types) live in `commonMain`. They MUST be serializable cross-platform.
    - The engine and ports (`WizardEngine`, `SystemSettingPort`, `ConfigSource`, `UserPreferencesStore`, etc.) live in `commonMain`. Engine logic does not know which platform is running.
    - Platform adapter modules (`androidMain`, `iosMain`, `androidTvMain`, etc.) implement the ports and register handlers for the relevant `CheckSpec` / `ApplySpec` variants.
    - Pool documents are platform-keyed (`platform: "android" | "ios" | "android-tv" | "*"`). A profile's manifest MAY reference pool entries from any platform whose adapters are active in the build.
    - If a step references a `CheckSpec` variant whose handler is not registered in the current build (e.g., an iOS-only check in an Android-only build), `SystemSettingPort.status()` MUST return `Indeterminate` and the engine MUST treat the step as pending (graceful degradation, not crash).
    
    A new platform MUST ship as: (a) a new adapter module with its own `<Platform>SystemSettingAdapter` and handlers, (b) a new platform-keyed pool document with platform-specific `CheckSpec` variants, (c) DI wiring registering the platform's handlers. The engine, ports, and existing platform adapters MUST NOT change.

16. **No `Custom` step type. No per-refId Kotlin handlers.** All wizard steps MUST be expressible through the generic `StepType` set (`SystemSetting`, `UIChoice`, `TutorialHint`) combined with the declarative `CheckSpec` / `ApplySpec` sealed hierarchies (§15). A wizard step that needs behaviour the existing kinds cannot express MUST extend the `CheckSpec` / `ApplySpec` sealed classes (with schema-version migration per CLAUDE.md rule 5), NOT introduce a `StepType.Custom` variant nor a per-`refId` Kotlin handler.

    **Rationale.** Per-refId handlers couple step rendering and behaviour to compiled code keyed on a string from the manifest, breaking §11 (wizard = projection of config) and §13 (no per-profile code). They also create an escape hatch through which step types proliferate without going through the proper declarative `CheckSpec` / `ApplySpec` extension — leading to handlers that auto-execute on step entry (bypassing the user's ability to skip), to per-step DI registration that fights generic dispatch, and to manifest entries whose semantics are not knowable from the JSON alone.

    **Why this rule exists.** F-3 (spec 015) added `StepType.Custom(val name: String)` as a *placeholder for future extension* with the comment "Custom types от app-family добавляются через DI Map" — without any concrete use case. This violated CLAUDE.md rule 4 ("Minimum Viable Architecture, not Minimum Viable Product — add abstraction only when not adding it would force a future *rewrite*, not future *addition*"). TASK-7 Phase-5 then misused this placeholder to wire `pair-admin` as a `Custom` step with a `PairAdminCustomStepHandler` that fire-and-forget-launched `PairingActivity` on step entry — no UI, no skip path, no `CheckSpec`-based state read. This rule retires the placeholder and forbids the pattern.

    **Where step types that look "different" actually fit.** Pairing with admin device → `CheckSpec` queries link-registry state (`activeAdminLinkCount > 0`) + `ApplySpec` dispatches an intent to the pairing activity. SOS contact selection → `CheckSpec` queries user-prefs for a stored phone number + `ApplySpec` launches a contact-picker intent. Both fit `SystemSetting` semantics: there *is* a piece of device/user state, the wizard reads it, and applies through a generic mechanism.

    **Existing `StepType.Custom` and `CustomStep` / `CustomStepHandler` infrastructure MUST be removed** as part of TASK-7 cleanup. New step needs that cannot be expressed today are deferred until the proper `CheckSpec` / `ApplySpec` variants are added, not workarounded through a per-refId handler.

---

## Article VIII. Accessibility and Elderly-First UX

1. Accessibility is a release gate.
2. Every user-facing feature MUST be reviewed for:
   - legibility,
   - tap target size,
   - visual contrast,
   - predictable navigation,
   - low cognitive load,
   - error recoverability,
   - screen reader semantics where applicable.
3. For app-critical and launcher-mode flows, preference SHOULD be given to:
   - large targets,
   - clear labels,
   - stable layouts,
   - minimal hidden gestures,
   - consistent placement of core actions.
4. Text, icons, spacing, and navigation patterns MUST remain robust across common Android screen sizes and window states.
5. Animation is optional. Readability and orientation are mandatory.
6. Accessibility behavior MUST be testable through explicit acceptance criteria.
7. If a design is elegant for experts but confusing for elderly users, the elderly-friendly design wins by default unless a documented product constraint says otherwise.

---

## Article IX. Battery, Performance, and Startup Discipline

1. The application owns user-critical surfaces, including potential resident launcher-mode surfaces; therefore performance regressions are product regressions.
2. Every background task, observer, receiver, polling loop, or service MUST be justified.
3. Event-driven mechanisms SHOULD be preferred over polling.
4. Startup path MUST remain minimal. Non-critical work SHOULD be deferred.
5. Any new dependency on boot-time, package-change, app-list refresh, or widget refresh behavior MUST document:
   - expected trigger frequency,
   - cold-start cost,
   - memory impact,
   - power implications.
6. Caching is allowed only with explicit invalidation rules.
7. “Listen to everything just in case” is prohibited.
8. For performance-sensitive features, the plan SHOULD include measurable targets and a verification approach.
9. Benchmarking or profiling artifacts SHOULD be introduced for high-risk performance areas.

---

## Article X. Test Strategy

1. No feature is complete without tests aligned to the spec.
2. Tests MUST be selected by risk, not by ritual.
3. Preferred order when defining test coverage:
   - contract or schema tests,
   - integration tests,
   - focused unit tests,
   - UI or end-to-end tests for critical flows.
4. For profile or configuration logic, schema validation and migration tests are mandatory.
5. For Core event handling, tests MUST verify:
   - event translation,
   - deduplication,
   - backpressure or throttling logic where applicable,
   - failure or fallback behavior.
6. Use mocks sparingly. Prefer realistic boundaries when practical.
7. A test suite that validates isolated helpers while ignoring integration behavior is insufficient.
8. Bug fixes MUST include a regression test unless technically impossible.

---

## Article XI. Simplicity and Anti-Abstraction

1. Prefer platform and framework capabilities directly over custom wrappers unless the wrapper creates measurable value.
2. Do not create abstraction layers “for future flexibility” without a current consumer or constraint.
3. Avoid duplicate model hierarchies unless each boundary materially needs its own representation.
4. Avoid custom internal DSLs, plugin systems, or registries unless simpler composition fails.
5. Avoid mediator, orchestrator, or manager classes that only pass data through unchanged.
6. If an abstraction exists, the plan MUST state:
   - what pain it solves now,
   - why packages or functions are insufficient,
   - what would break if it were removed.
7. AI agents MUST bias toward fewer moving parts, fewer indirections, and clearer names.

---

## Article XII. Documentation Separation of Concerns

1. `spec.md` MUST remain product-facing and technology-agnostic wherever possible.
2. `plan.md` MUST capture architecture, dependencies, data flow, interfaces, rollout, and verification strategy.
3. `tasks.md` MUST contain execution items, not architecture essays.
4. Deep technical detail belongs in dedicated artifacts when needed:
   - contracts,
   - implementation details,
   - schema docs,
   - migration notes,
   - ADRs.
5. Code comments MUST explain intent or non-obvious constraints, not paraphrase the code.
6. README and module docs MUST be kept accurate when architecture changes.

7. The following repository documents are part of the required project context and MUST be consulted when relevant to the affected feature domain:
   - `docs/governance/document-map.md`
   - `docs/adr/*.md`
   - `docs/product/*.md`
   - `docs/compliance/*.md`
   - `docs/research/*.md`
   - `docs/operations/*.md`
8. If a feature touches a governed domain, the corresponding `spec.md` and `plan.md` MUST link the relevant context documents explicitly rather than assuming they will be discovered implicitly.


---

## Article XIII. Dependency and Technology Governance

1. New dependencies require justification in the plan.
2. Prefer stable, well-supported Android and Kotlin ecosystem components.
3. Dependency choice MUST consider:
   - maintenance activity,
   - transitive complexity,
   - size impact,
   - performance cost,
   - lock-in risk,
   - testability.
4. Official Android guidance and platform capabilities SHOULD be preferred when sufficient.
5. Experimental libraries MUST remain isolated behind a replaceable boundary.
6. A dependency added for one feature MUST NOT quietly become a project-wide standard without explicit decision.

---

## Article XIV. Security, Privacy, and Platform Respect

1. Request only permissions required by approved requirements.
2. Permissions MUST be justified in the spec and explained in the plan.
3. No hidden collection of behavioral or personal data.
4. Local-first behavior SHOULD be preferred unless a networked feature is explicitly required.
5. Sensitive actions involving installed apps, package visibility, accessibility services, or device state MUST be documented with user-value justification.
6. Fallback behavior for denied permissions MUST be designed, not improvised.
7. **Server-side data minimization (anti-traceability by design).** Architecture MUST be designed so that any current third-party backend (Cloudflare Worker, Firebase, etc.) AND any future own-server learns as little as possible about its users. This is an **architectural aspiration**, not a one-shot gate — surface concerns during every architecture discussion touching server-bound data; revisit at each major spec touching identifiers, persistence, or relationships.

   Specifically:

   (a) **Opaque identifiers reaching the server.** Identifiers stored or routed by the server MUST be opaque (UUID / random ID), not derivable from identity-provider primary keys (no Google `sub`, no email, no phone number as server-side primary key). Mapping from identity-provider ID to our opaque ID stays client-side or in a separate, access-controlled mapping table.

   (b) **Ciphertext + routing-metadata only.** Persistent server-side records SHOULD store ciphertext + minimum metadata required for routing (recipient ID, timestamp, schema version) only. Plaintext PII (names, contacts, photos, configurations, locations, behavioral data) MUST stay on device unless an explicit FR in an approved spec justifies otherwise.

   (c) **No cross-user correlation by access pattern.** Server MUST NOT be able to infer "user A and user B are paired / share a family group / share contacts" from access patterns alone. Design implications: separate endpoints per relationship type, no shared rooms / chats addressable by both parties, no temporal correlation between related operations, request mixing where natural.

   (d) **Access logs are a privacy surface.** Cloudflare access logs / our future server's request logs MUST be treated as a privacy surface. Design wire formats and addressing so that a third party with full log access cannot reconstruct user relationships, content, or behavioral patterns from IPs + timestamps + paths alone.

   (e) **Worst-case-provider assumption.** When choosing free-tier infrastructure (Cloudflare, Firebase, GitHub Pages, etc.), assume the provider sees everything in access logs and could be compelled to share them. Design so that compliance with such a request yields **the least useful data possible**.

   This rule complements rule 8 of [`CLAUDE.md`](../../CLAUDE.md) (server-roadmap migration tracking): the eventual own-server is the **destination**, not the **fix** — the on-server data shape must be minimal at design time, not retrofit-able later. Wire-format decisions made now constrain what own-server can know later (rule 5 of CLAUDE.md — wire-format versioning).

---

## Article XV. AI Agent Operating Rules

When an AI agent works inside this repository through Spec Kit, it MUST behave as a disciplined senior engineering team, not as a code autocomplete engine.

1. Read the constitution first.
2. Preserve architectural consistency across features.
3. Refuse to invent requirements not present in the spec.
4. Surface ambiguity explicitly.
5. Prefer the smallest correct change.
6. Keep diffs reviewable.
7. Update impacted docs, tests, schemas, and examples together.
8. Before proposing new abstractions, modules, or frameworks, prove necessity against Articles V and XI.
9. Before proposing new event listeners, services, or background work, prove necessity against Articles VI and IX.
10. Before proposing new UX flows, validate against Article VIII.
11. Every plan MUST include a **Constitution Check** section that explicitly states how the feature complies with relevant articles.
12. If a requested change conflicts with the constitution, the AI MUST either:
    - propose a compliant alternative, or
    - flag that the constitution must be amended or an exception must be approved.
13. Align **Git** practice with **Article XVIII**: after each **significant** step, **commit** and **push** to the remote (see Article XVIII for what counts as significant and for exceptions).
14. **Cite UX precedent before original proposals.** When proposing a user-facing UX pattern (interaction flow, layout, control type, terminology, naming, navigation pattern), AI agents MUST first research how established products / industry leaders solve the same problem. Surface 2–3 references with brief descriptions of their approach. Only after presenting precedent — propose the recommendation. Fall back to fully original proposals only when no relevant precedent exists or precedent is genuinely unsuited.

    Scope: setup wizards, settings UI, notifications, error states, navigation patterns, terminology choices, similar UX-level decisions.

    Out of scope: architectural / code decisions (those follow CLAUDE.md rules and Articles IV / VII), wire-format decisions, business-logic decisions specific to this product domain.

    Rationale: novice product owners often accept the first proposal they hear because they have no comparison anchor. Citing precedent gives them an anchor to evaluate against ("Notion does X for similar reason — does that fit us too?") and reduces the risk of unique-but-suboptimal patterns. This rule reinforces the mentor skill principle (`.claude/skills/mentor/SKILL.md`) of critical mentor stance — but extends from "critique my own defaults" to "ground recommendations in industry precedent before defaulting at all".

---

## Article XVI. Required Constitution Check in Every `plan.md`

Every implementation plan MUST include a compliance gate covering at least the following questions:

### Architecture Gate

- Does the feature fit the layered architecture or clearly justify deviation?
- Is any new module justified?
- Are boundaries explicit and minimal?

### Core/System Integration Gate

- Does the feature require system events?
- If yes, are they centralized in Core or another approved boundary?
- Are event contracts typed and documented?

### Configuration Gate

- Does this change affect profiles, schema, defaults, or migrations?
- Are validation and compatibility covered?

### Required Context Review Gate

- Which files from `docs/governance`, `docs/adr`, `docs/product`, `docs/compliance`, `docs/research`, and `docs/operations` are relevant?
- Are they explicitly linked in this plan?
- For each normally relevant document omitted, is the omission explained?

### Accessibility Gate

- How does this feature behave for elderly users and accessibility-sensitive users?
- What acceptance criteria verify that behavior?

### Battery/Performance Gate

- What is the background or runtime cost?
- Is polling avoided or explicitly justified?
- Is startup impact controlled?

### Testing Gate

- What contract, integration, regression, and UI tests are required?
- Which failure modes are covered?

### Simplicity Gate

- Is any abstraction speculative?
- Can the design be reduced further without losing correctness?

A plan that does not pass these gates is incomplete.

---

## Article XVII. Governance, Exceptions, and ADR Boundaries

1. This constitution is stable by default and changes rarely.
2. Amendments require:
   - explicit rationale,
   - impacted articles,
   - compatibility assessment,
   - migration or update plan for docs and templates.
3. Temporary exceptions MUST be documented in the relevant `plan.md` or ADR with:
   - article affected,
   - reason,
   - scope,
   - risk,
   - mitigation,
   - removal condition.
4. Repeated exceptions indicate the constitution should be revised.
5. Architectural choices that do not change constitutional rules SHOULD go into ADRs rather than expanding this document.
6. This constitution defines the guardrails, not every implementation detail.

---

## Article XVIII. Version Control and Integration Rhythm

1. **Commits** SHOULD represent **coherent logical units** of change (one reviewable concern per commit when practical), not unrelated mixed edits that are hard to revert or bisect.
2. **Commit messages** SHOULD state **what** changed and **why** in enough detail for a reviewer or future maintainer without reading the entire diff.
3. **After each significant step**, once that step is in a **consistent state** (expected build and tests for that step pass where they apply), contributors **MUST** record the work in Git with at least one **logical commit** and **MUST** **push** that commit (or a small batch of related commits for the same step) to the **remote tracking branch**. A **significant step** includes, non-exhaustively: completion of a **`tasks.md` phase** or Speckit checkpoint, a coherent **spec / plan / tasks** update, or a **self-contained implementation slice**. Letting multiple such steps pile up **only locally** without push **MUST** be avoided unless a **documented exception** applies (for example maintainer direction, embargo, or a short-lived air-gapped session, followed by catch-up push as soon as connectivity and policy allow).
4. Work tracked in Spec Kit SHOULD align **commits** with **natural breakpoints** (for example: completed task phase, spec/plan/tasks update, or a self-contained implementation chunk), keeping branches **reviewable** and **traceable** to artifacts.
5. Secrets, credentials, signing keys, and **machine-local-only** configuration (for example paths in `local.properties`) MUST NOT be committed. Such files MUST remain excluded via `.gitignore` or equivalent policy.

**Rationale**: Predictable Git rhythm reduces lost work, eases code review, and keeps continuous integration meaningful for a long-lived product. Requiring push after significant steps prevents silent drift between machines and keeps CI and review truthful.

---

## Article XIX. Organic Question Budgets in Clarification Passes

1. Any clarification or deliberation pass — `speckit-clarify`, `mentor` skill, ad-hoc discussion mode, future analogous skills — MUST ask **as many questions as the artifact's grey zones genuinely require**, no more and no fewer. The count is **organic**, derived from the content, not a numeric target.
2. **No hard cap.** Skills MAY suggest a **typical range** (currently 3–7) for orientation, but MUST NOT enforce a ceiling that silently trims real grey zones, nor a floor that forces invented ones.
3. **When the organic count exceeds 8**, the skill MUST surface this to the user explicitly: *"У меня N серьёзных вопросов с большим blast radius. Хочешь все сразу или разбить на два прохода?"* — and let the user decide. Splitting is the user's call, not the skill's.
4. **Padding to a target** (inventing a 5th question because the skill said «ask 5») is a **bug** equivalent to making up requirements. Such padded questions waste user attention and signal the skill doesn't trust its own filter.
5. **Trimming below organic count** (cutting question 6 because the skill said «top-5») is a **bug** equivalent to silently dropping requirements. The cut grey zone leaks past clarify and shows up later as a planning surprise or rework.
6. Skills MUST select questions by **highest blast radius if wrong** (questions that would invalidate the next artifact — `plan.md`, an architectural choice, a one-way door). If multiple grey zones tie at high blast radius, ask all of them.
7. This article overrides any pre-existing numeric limits in individual skill files. Skill maintainers MUST align skill prompts with this article when next touched.

**Rationale**: Numeric limits felt safe («maximum 5») but produced two failure modes — padded questions when the spec was simple, and silently dropped questions when the spec was complex. Both failed the user. The owner's intent for clarification passes is to **catch what was implicit before architecture is baked in**, which is an organic property of the spec content, not a constant. Naming this as an article (rather than a per-skill rule) keeps future clarification skills automatically aligned without per-skill edits.

---

## Delivery Rules for Spec Kit Artifacts

The following rules are binding for future Spec Kit usage in this project:

### For `/speckit.constitution`

- Update this file, not an unrelated summary.
- Preserve article numbering and amendment history.

### For `/speckit.specify`

- Focus on user value, problems, acceptance criteria, and constraints.
- Do not smuggle architecture into the spec unless it is a true constraint.
- Add a `Related Project Context` section that links the relevant files from `docs/**`.
- When a governed domain is relevant, cite the exact file paths rather than generic folder names.

### For `/speckit.plan`

- Add Constitution Check.
- Define module impact, event impact, config impact, accessibility impact, and test strategy.
- Identify risks for OEM behavior, permissions, lifecycle recreation, and battery.
- Clearly mark any exception or deviation from architectural defaults.
- Add a mandatory `Required Context Review` section linking all relevant `docs/**` files reviewed for the plan.
- If a normally relevant governed document is not used, state why.

### For `/speckit.tasks`

- Keep tasks concrete, ordered, and traceable to plan and spec.
- Include tasks for tests, docs, schema updates, migrations, and instrumentation where required.
- Include tasks for updating impacted `docs/**` files when the plan changes governed project context, policy assumptions, ADR decisions, or operational procedures.

---

## Project-Specific Architectural Direction

This section is intentionally opinionated and specific to this universal Android application.
It defines default direction, not an automatic ban on alternatives.

### 1. Core Platform Layer

Core owns platform-facing responsibilities such as app/package change observation, boot-related handling where approved, shared event intake, and common contracts. Core is not a junk drawer; it is a controlled boundary around Android system behavior needed by app-owned features.

### 2. Feature Modules

Features implement user-facing capabilities or isolated business logic. Features consume stable contracts from Core and shared modules. Features do not directly spread platform listeners across the codebase. A launcher feature is one feature family, not the architectural root for every capability.

### 3. Profiles and Configurations

Profiles define curated application variants, launcher-mode variants, or user-focused operating modes. A profile is a **composition** of multiple configuration documents per Article VII §9–10 (current generation: `wizard.manifest` + `screen.layout` + `tile.set` + `system-settings.pool` + `ui-customization.pool`); the set of document kinds is itself versioned and expected to evolve. Examples: `simple-launcher` (elderly-friendly handheld), `admin-app` (remote administrator), `clinic-patient-app` (B2B clinic), `self-care` (single-user self-managed). Profiles are **data, not forks**: a new profile MUST NOT ship as new code dedicated to that profile (Article VII §13), and MUST NOT add an `if (appFamilyId == "x")` branch in business logic.

### 4. Optional Modules

Optional or downloadable components must fail gracefully when absent. The base application and any enabled launcher-mode surface must remain coherent without them.

### 5. Adaptive-UX Presets (accessibility variants)

**Adaptive-UX presets** — variants for tremor, low vision, reduced dexterity, or cognitive load — are **sub-features within a profile** (Article VII §5, §9), not profiles themselves. A profile (e.g., `simple-launcher`) MAY enable one or more adaptive-UX presets; the same profile with a different adaptive-UX preset is still the same profile. Adaptive-UX presets are backed by configuration, validation, and acceptance tests — not by ad hoc UI overrides. Earlier wording in this constitution called these "accessibility presets"; the renamed term "adaptive-UX preset" is used to avoid confusion with the top-level "profile" concept.

### 6. Form-Factor Variants

The application targets multiple form factors over time (handheld Android, Android TV, voice-first devices, automotive, wearables, foldables). Each non-handheld form factor MUST be delivered as a combination of:

1. **A profile** (data, per §3) that wires the appropriate UX, navigation model, and input affordances.
2. **One or more optional/downloadable feature modules** (per §4 and Article V) holding form-factor-specific code, SDKs, and platform integrations (Leanback, TIF, Android Auto, Wear, Assistant SDK, etc.).

The base application code (Core, shared domain, common UI primitives) MUST remain form-factor-agnostic. Form-factor-specific vendor SDKs MUST NOT be added to Core or to handheld feature modules — they live exclusively in their form-factor adapter module (Anti-Corruption Layer per [`CLAUDE.md`](../../CLAUDE.md) rule 2).

Forking the codebase per form factor is prohibited. A new form factor that cannot be expressed as profile + downloadable modules indicates a missing abstraction in Core and MUST be raised as an architectural concern before implementation.

The choice of **delivery channel** for downloadable modules (Play Feature Delivery, in-app sideload, own server, split APK) is a one-way door per [`CLAUDE.md`](../../CLAUDE.md) rule 3 and MUST be decided in an ADR with an explicit exit ramp before the first non-handheld form factor ships.

### 7. Backend Substitution Readiness

The project currently relies on third-party backend services (Firebase Auth, Firestore, Cloud Storage, Cloudflare Worker, etc.). The standing intent is that any non-platform backend dependency will eventually be replaced by an own-server implementation that the team fully controls. This is an **intent**, not a planned migration spec — no timeline, no triggering feature, no committed scope. Its purpose is to keep every design conversation honest about how hard a future provider swap would be.

To support this, every new feature that touches a backend MUST be designed so that:

1. The application code (domain, UI, feature modules) talks to the backend **only through domain-owned ports**. Vendor types (`FirebaseFirestore`, `DocumentReference`, `QuerySnapshot`, `FirebaseUser`, `StorageReference`, etc.) MUST NOT appear in any signature outside the dedicated adapter module (CLAUDE.md rules 1–2).
2. The **wire format** stored remotely is a domain-owned, schema-versioned data class. Provider-specific shapes (Firestore `Timestamp`, `FieldValue.serverTimestamp()`, document-reference paths, security-rules-shaped field names) live in the adapter, not in the persisted domain model.
3. **Identity** in the domain is a project-owned value (e.g., `UserId`). Provider-specific identifiers (Firebase UID, Google account ID) are stored as credentials inside the auth adapter, not as the domain primary key.

**Exempt from this rule** — platform integrations that have no realistic substitute and remain provider-specific by design: push notifications (FCM on Android, APNs on iOS), SMS, telephony, biometrics, location, contacts, and other OS-mediated services. These are still wrapped in ports (CLAUDE.md rule 2) but are not part of the "substitutable backend" perimeter.

**Not exempt** — anything we choose to put on a third-party backend for convenience: Firebase Auth, Firestore, Realtime Database, Cloud Storage, Cloud Functions, Crashlytics, Analytics, Remote Config. All MUST be approached as substitutable, with the seams placed up front.

The intent of an eventual server swap MUST surface in design discussions whenever a feature crosses the backend boundary. The `checklist-backend-substitution` skill enforces this surfacing.

---

## Quality Bar for “World-Class Team” AI Output

An AI contribution is acceptable only if it is:

1. **Correct** — matches the approved requirement.
2. **Explainable** — design can be defended in review.
3. **Minimal** — no speculative complexity.
4. **Tested** — risk-appropriate automated validation exists.
5. **Observable** — failures are diagnosable.
6. **Maintainable** — naming, boundaries, and docs remain clear.
7. **Android-realistic** — respects lifecycle, battery, OEM quirks, and platform constraints.
8. **Accessible** — suitable for elderly and accessibility-sensitive users.

Anything below this bar is rework, not progress.

---

## Reference Patterns and Mature Sources

These sources informed the constitution and are recommended reference material when creating plans for this project:

1. **GitHub Spec Kit**
   - Official repository and workflow guidance for constitutions, specs, plans, and tasks.
   - `spec-driven.md` explains constitutional enforcement, simplicity gates, anti-abstraction gates, and integration-first testing.

2. **Android Developers — Guide to app architecture**
   - Official recommendation for layered architecture, separation of concerns, UDF, repositories, state holders, and optional domain layer.

3. **Android Developers — Recommendations for Android architecture**
   - Strong guidance on UI/data layers, repositories, coroutines/flows, and ViewModel-based state handling.

4. **Android Developers — Guide to app modularization**
   - Official rationale for cohesive, loosely coupled modules in larger codebases.

5. **Android Developers / GitHub — Now in Android**
   - Real-world, fully functional sample app modeling Android best practices, modularization, build variants, and architecture guidance.

6. **android/architecture-samples**
   - Mature reference repository comparing architectural patterns in Android.

7. **Android Architecture Guidelines and related community references**
   - Useful as secondary material, but subordinate to official Android guidance for foundational rules.

---

## Amendment History

### 1.10 — 2026-06-25

- **Article VII §16 added** — No `StepType.Custom`, no per-refId Kotlin handlers. All wizard steps MUST be expressible through generic `StepType` + declarative `CheckSpec` / `ApplySpec` (§15). New step needs MUST extend the sealed `CheckSpec` / `ApplySpec` hierarchies, NOT introduce a custom step variant nor a per-`refId` handler. Existing `StepType.Custom` placeholder + `CustomStep` / `CustomStepHandler` / `PairAdminCustomStepHandler` infrastructure MUST be removed as part of TASK-7 cleanup.
- **Rationale**: triggered by TASK-7 device verification 2026-06-25 on Xiaomi 11T. F-3 (spec 015) had added `StepType.Custom(val name: String)` as a placeholder for "future extension via DI Map" with no concrete use case — violating CLAUDE.md rule 4. TASK-7 Phase-5 then misused this escape hatch to wire `pair-admin` as a custom step with a handler that fire-and-forget-launched `PairingActivity` on step entry — no user UI, no skip path, no state read. Pair-admin actually fits `SystemSetting` semantics (state = activeAdminLinkCount; apply = launch pairing intent) but the proper `CheckSpec` / `ApplySpec` variants are deferred to TASK-8 (Admin App + QR Pairing), TASK-51 (libsodium ristretto255 fix), and beyond. Removing `Custom` step now prevents the placeholder from causing further misuse and codifies the rule.

### 1.9 — 2026-06-24 (later same day)

- **Article XV §14 added** — Cite UX precedent before original proposals. AI agents must research established products / industry leaders solving the same problem, surface 2–3 references, then recommend. Originals reserved for genuine precedent gaps. Scope: setup wizards, settings UI, notifications, error states, navigation, terminology. Out of scope: architectural / code / wire-format / domain business logic.
- **Rationale**: triggered by TASK-7 scenarios pass 2026-06-24. Owner surfaced that prior proposals (donastroika via wizard re-run) lacked precedent grounding — leading to potential unique-but-suboptimal UX choices. Reinforces mentor skill critical stance from "critique my own defaults" → "ground in precedent before defaulting".

### 1.8 — 2026-06-24 (later same day)

- **Article II §8 added** — MVP definition: base functional blocks end-to-end working, polish through JSON configuration not code, anti-pattern to target UX delight at MVP stage.
- **Article III §7 added** — Stability over system-level changes: applied settings are persistent user intent; system-level shifts (locale, theme, font scale) MUST NOT silently override user-applied values. Locale enforcement via `AppCompatDelegate.setApplicationLocales()` explicitly required.
- **Article VII §14 added** — Configuration lifecycle independent of application lifecycle. Engine reconciles current device state against current config spec at each launch through *config-check master* pattern: query `SystemSettingPort.status()` per step, skip applied, show only pending. Replaces snapshot-of-manifest approach. Applies to all paths through which a setting may have been applied (wizard, Android Settings directly, admin remote push, AI agent capability invocation, file import).
- **Article VII §15 added** — Multi-platform adapter seam. Declarative dispatch types (`CheckSpec`, `ApplySpec`) in `commonMain`; handlers in platform-specific adapter modules. Graceful degradation: unregistered `CheckSpec` variants return `Indeterminate`. New platforms ship as new adapter + new platform-keyed pool; engine and ports unchanged.
- **Rationale**: triggered by TASK-7 clarify pass 2026-06-24 (continuation of amendment 1.7 session). Owner explicitly surfaced that (a) MVP definition was implicit — risking AI scope creep through "let's polish this now" patches; (b) locale / theme silent shifts on system-level changes break the trust relationship with elderly primary users; (c) F-3 engine implementation traverses manifest linearly without checking current device state, causing wizard to re-show steps already applied by other paths; (d) future platforms (iOS, Android TV) and future sources (AI agent, file import, marketplace) require strict separation between commonMain data + ports and platform/source-specific implementations.

### 1.7 — 2026-06-24

- **Article VII** — added §9 (profile composition), §10 (current generation of wire format kinds + explicit evolution policy), §11 (wizard = view of profile, not source of truth), §12 (mandatory / optional with skip-banner / optional silent semantics, pool-level defaults + per-profile override), §13 (no per-profile code module, no `if (appFamilyId == "x")` branches).
- **Article VII §5** — clarified that "adaptive-UX presets" are sub-features within a profile, not parallel concepts to profiles.
- **Project-Specific Direction §3** — expanded definition of "profile" to reference the composition model and the current 5-kind division; added the concrete sample profile names (`simple-launcher`, `admin-app`, `clinic-patient-app`, `self-care`).
- **Project-Specific Direction §5** — renamed "Accessibility Presets" to "Adaptive-UX Presets (accessibility variants)" and clarified the sub-feature relationship to profiles.
- **Rationale**: prior wording left ambiguous whether `simple-launcher` was a Kotlin class, a JSON file, or a logical product variant. AI agents repeatedly wrote specs that hardcoded behaviour into code instead of treating profiles as composed configuration. Owner surfaced this during TASK-7 clarify pass 2026-06-24 («нет заранее запрограммированного лаунчера, всё строится из json»). The amendment captures the architectural model so future AI sessions cannot drift.

### 1.6 — 2026-06-17

- Added **Article XIX. Organic Question Budgets in Clarification Passes** — overrides any prior numeric question caps in `speckit-clarify`, `mentor`, and analogous skills. Question count is organic, derived from spec's actual grey zones; typical range 3–7 but no hard cap. Padding to a target and trimming below organic count are both bugs.
- Aligned `.claude/skills/speckit-clarify/SKILL.md` Step 3 — removed «Hard limit: 5 questions» — and `.claude/skills/mentor/SKILL.md` step 5 — removed «5 уточняющих вопросов» — to reference Article XIX.
- Rationale: numeric limits produced two failure modes — padded questions on simple specs and silently dropped questions on complex specs. Owner explicitly surfaced this during F-CRYPTO clarification 2026-06-17.

### 1.5 — 2026-04-25

- Clarified that the product is a universal Android application, not only a launcher.
- Defined launcher functionality as one supported use case / operating mode.
- Updated architectural direction so non-launcher features are not forced through a launcher metaphor.

### 1.4 — 2026-04-01

- Added explicit required-project-context rules for `docs/**`.
- Added `Required Context Review Gate` to the mandatory Constitution Check for every `plan.md`.
- Updated Spec Kit delivery rules so `spec.md`, `plan.md`, and `tasks.md` must link or update relevant `docs/**` files when applicable.

### 1.3 — 2026-03-28

- **Article XVIII**: strengthened rule **§3** — after each **significant** step, **MUST** commit and **MUST** push to remote when work is consistent (with narrow exceptions); clarified what counts as a significant step and rationale.
- **Article XV §13**: aligned wording with the strengthened Article XVIII.

### 1.2 — 2026-03-28

- Added **Article XVIII. Version Control and Integration Rhythm** (logical commits, clear messages, pushes at validated breakpoints, alignment with Spec Kit task phases, prohibition on committing secrets and local-only config).

### 1.1 — 2026-03-28

Revision based on constitution review:

- separated non-negotiable rules from architectural defaults,
- introduced explicit normative levels (`MUST`, `SHOULD`, `MAY`),
- added precedence and exception handling,
- clarified ADR boundary,
- reduced unnecessary architectural rigidity while preserving project-specific direction.

### 1.0 — 2026-03-28

Initial project constitution created for Launcher based on:

- prior project discussions about Core event ownership,
- modular feature architecture,
- profile/configuration-driven builds,
- elderly/accessibility focus,
- GitHub Spec Kit constitutional workflow,
- official Android architecture and modularization guidance,
- mature Android reference repositories.

---

**Version**: 1.10.0 | **Ratified**: 2026-03-28 | **Last Amended**: 2026-06-25
