# Feature Specification: Launcher Core Foundation

**Feature Branch**: `001-launcher-core-foundation`  
**Created**: 2026-03-28  
**Status**: Draft  
**Input**: User description: Core Foundation — minimal Core architecture, ownership boundaries, MVP module boundaries, extension rules, and non-goals for an accessibility-first, elderly-friendly Android launcher platform (additive evolution, View/XML UI, platform events only via Core or explicit platform boundary, contracts/profiles/modules over Core bloat).

**Constitution**: `.specify/memory/constitution.md` is binding. This specification is product-facing and technology-agnostic except where a technical constraint is itself a requirement under the constitution, including the UI stack and platform integration boundaries. Where implementation detail is intentionally deferred, this specification defines the product-level requirement, ownership rule, or acceptance condition that planning and implementation must honor.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Elderly user gets a stable home experience (Priority: P1)

An elderly or accessibility-sensitive user relies on the launcher as primary navigation. The platform MUST behave predictably after restarts, configuration changes, and partial permission grants, without hidden background cost, silent feature collapse, or launcher-critical behavior being dependent on unrelated subsystems.

**Why this priority**: Without a disciplined Core, reliability, recoverability, and battery regressions become normal. That directly violates the product promise for vulnerable users who depend on a predictable launcher.

**Independent Test**: Reviewers can verify from this specification and future plans that launcher-critical flows do not depend on scattered OS listeners, unjustified polling, or heavy background services, and that fallback behavior for invalid, incompatible, missing, or partial configuration is defined at the platform level.

**Acceptance Scenarios**:

1. **Given** the device has restarted, **When** the user opens the home experience, **Then** essential navigation and content defined for MVP remain available without requiring ad hoc fixes in unrelated areas of the codebase.
2. **Given** a profile or configuration is invalid, partially applied, missing required keys, or incompatible with the running build, **When** the launcher loads, **Then** the user sees a safe, deterministic fallback consistent with documented platform rules rather than a crash, blank shell, or undefined partial state.
3. **Given** one or more optional capabilities are unavailable because a module is absent, a permission is denied, or a contract dependency is not satisfiable, **When** the launcher starts, **Then** the base home experience remains coherent and the unavailable capability degrades in a documented way without breaking primary navigation.

---

### User Story 2 - Team adds a capability without rewriting the base (Priority: P2)

The product team wants to ship new launcher capabilities over time. New work MUST attach through declared contracts, profiles, and modules instead of growing Core or sprinkling conditional logic across unrelated layers.

**Why this priority**: This is the main economic reason for Core Foundation. Without it, each new capability creates rewrite tax, spreads coupling, and makes the launcher less stable with every iteration.

**Independent Test**: The specification’s extension rules and concrete extension example can be walked through by a reviewer: a new capability is described as registering against documented boundaries without requiring invasive edits to every layer or expanding Core into a feature bucket.

**Acceptance Scenarios**:

1. **Given** a new user-visible capability is approved, **When** architects map it to the platform, **Then** it is placed in a module or extension path with a documented contract, not by expanding Core with feature-specific rules.
2. **Given** a profile enables or disables a capability, **When** the launcher runs, **Then** behavior follows profile and contract rules without silent schema drift, undocumented branches, or hidden fallback logic.
3. **Given** a future feature requires an already-known platform signal, **When** it is added to the product, **Then** it reuses the existing project-level event contract or an approved extension of that contract rather than creating parallel listener ownership.

---

### User Story 3 - Maintainer audits platform integration (Priority: P3)

A maintainer or reviewer MUST be able to see where Android platform signals enter the product and how they propagate, in order to control battery impact, OEM variance, and lifecycle correctness.

**Why this priority**: Uncontrolled platform integration is a recurring failure mode for launchers. Central visibility is therefore a release-quality concern, not an internal code-style preference.

**Independent Test**: From the specification alone, a reviewer can name the single default platform-integration boundary for OS-level events and list which named components own intake, translation, deduplication, fallback policy, and internal routing.

**Acceptance Scenarios**:

1. **Given** a new OS-level listener is proposed, **When** the team checks Core Foundation rules, **Then** it is clear whether it belongs in Core or the explicitly named platform boundary, what documentation is required, and what is forbidden elsewhere.
2. **Given** multiple capabilities need similar system signals, **When** the solution is reviewed, **Then** the event strategy routes through a shared normalization path instead of duplicate listeners per feature.
3. **Given** a maintainer inspects a launcher-critical event flow, **When** they trace ownership, **Then** they can identify the intake owner, normalization path, internal routing contract, and fallback behavior without reading unrelated feature code.

---

### Edge Cases

- Profile or configuration version mismatch, missing keys, corrupt values, or schema incompatible with the running build — MUST degrade safely using the documented fallback hierarchy.
- Optional module absent at runtime — base launcher MUST remain coherent; no hard dependency on optional code paths for core navigation.
- Permission denied or restricted (for example package visibility or launch-related permissions) — app catalog and actions MUST remain consistent with documented behavior, not undefined failure.
- Platform rules differ by **API level** within the supported range (for example visibility and query APIs introduced or tightened on newer versions) — Core MUST normalize behavior so users on **minSdk** and on **targetSdk** receive **documented**, non-crashing outcomes, not divergent undefined states.
- Multiple features request similar system signals — Core or the explicit platform boundary MUST deduplicate and expose project-level events, not duplicate listeners per feature.
- Low memory or process death — state ownership and recreation rules MUST avoid “only works until killed” assumptions for MVP-critical paths.
- Contract version incompatibility between shell, Core, and module — the incompatible capability MUST be disabled or downgraded according to documented rules without compromising home-shell stability.
- A profile requests a capability that is unavailable because the module is absent or permission state does not allow it — runtime safety and deterministic behavior MUST take precedence over profile intent.

## Requirements *(mandatory)*

### Platform and engineering constraints *(binding)*

- The product is an **Android launcher** with **elderly- and accessibility-first** UX expectations. Large targets, clarity, predictable navigation, reduced surprise, and robust recovery are release concerns, not polish.
- **UI stack**: classic **Android View + XML layouts**. **Jetpack Compose MUST NOT** be used for this product line unless the constitution is amended and future plans document an explicit exception.
- **OS-level signals** (broadcasts, package changes, boot-related hooks where approved, permission result orchestration, and similar): **only Core** or **one other explicitly named platform-integration boundary** MAY register and own them. Feature modules MUST NOT attach arbitrary global listeners without an approved exception documented in a future plan.
- **Extension model**: new capabilities MUST extend via **contracts**, **profiles**, and **modules**, not by enlarging Core with ad hoc branches, hidden flags, or feature-owned global hooks.
- **Resource discipline**: heavy background services, continuous polling, and unjustified event listeners are **forbidden** as default platform patterns. Any exception MUST be justified in a future plan with explicit cost, frequency, lifecycle, fallback, and battery impact.
- **Evolution**: the base MUST be **extended in place**. Designs that imply periodic **full rewrites** of Core for routine features are out of scope for acceptable architecture.
- **Contract publication discipline**: shared contracts exposed by Core MUST be minimal, intentional, and stable enough for cross-module use. Core MUST NOT become a dumping ground for speculative abstractions or convenience APIs that only serve one feature.

### Device and API compatibility *(binding)*

- **FR-029 (minimum supported API)**: The product MUST support installation and correct launcher-critical behavior on devices from **Android 8.0 (API level 26)** through the **target API** chosen for release. This floor is a **product and architecture requirement**, not only a Gradle default; `minSdk` in build files MUST match this specification unless this spec is amended first.
- **FR-030 (tradeoff record)**: Engineering rationale for choosing API 26 over older floors (e.g., API 24) MUST remain documented in **planning/research** so stakeholders can revisit reach vs. complexity without re-deriving the decision.
- **FR-031 (API-gated features)**: Any capability that **requires APIs above the stated minSdk** MUST either: (a) **degrade** on older APIs with behavior documented in the relevant feature spec or plan, or (b) drive a **minSdk increase** with explicit user-reach impact analysis and an update to this spec and `plan.md`.
- **FR-032 (behavior across API range)**: **AppIndex**, **ActionDispatcher**, and permission-related behaviors MUST be **deterministic** across the full span from **minSdk through targetSdk**. Differences in platform rules (for example package visibility and package queries) MUST be handled with **runtime checks** where the product retains a lower minSdk, rather than leaving behavior undefined.
- **FR-033 (SDK roles)**: **compileSdk** and **targetSdk** updates track build tooling, new platform behaviors, and store policy; they do **not** by themselves change which devices can install the app. Only **minSdk** changes the install base. Plans MUST treat **minSdk** changes as **product-visible** decisions.
- **FR-034 (distribution)**: The primary distribution assumption is **Google Play** (or sideloading the same binary). **minSdk** at release MUST satisfy **then-current** Play requirements; if policy shifts, this spec or the plan MUST be updated before shipping.
- **FR-035 (changing minSdk during development)**: **minSdk MAY change** during development. Any change MUST update **this specification** and **`plan.md`**, include a short **rationale** (ADR or plan revision), and trigger review of **published contracts** and **module descriptors** for incompatible platform assumptions.
- **FR-036 (lowering minSdk)**: If **minSdk is lowered** after implementation has targeted a higher floor, the plan MUST describe **backporting, desugaring, or alternative code paths**, or MUST explicitly state that lowering is **out of scope** for the current milestone.
- **FR-037 (contracts and platform APIs)**: A published contract whose semantics depend on specific **platform API levels** MUST document those assumptions; if platform assumptions change under the same `contractId`, **FR-010** compatibility and migration rules apply, including **majorVersion** bump when breaking.
- **FR-038 (modules vs minSdk)**: Feature modules MUST NOT rely on APIs **above the product minSdk** without **guarded** call sites. **ModuleRegistry** MAY treat violations as **degraded** or **unsupported** module load per the fallback hierarchy.

### Functional Requirements

#### Core responsibilities (minimal, mandatory)

- **FR-001**: The specification MUST define a **closed list of responsibilities** that belong to Core, including owning the **platform event intake** (SystemEventBridge role), translating OS signals into **project-level events**, and providing **internal routing** (EventRouter role) so features do not subscribe to raw OS callbacks.
- **FR-002**: Core MUST own **orchestration of modular composition**: which modules exist, how they are registered, and how they receive shared services (ModuleRegistry role), without features reaching into each other’s internals.
- **FR-003**: Core MUST own **interpretation and validation hooks** for profile-driven behavior (ProfileEngine role): applying the active profile, applying defaults, validating compatibility, resolving safe fallback, and ensuring that invalid configuration cannot produce undefined launcher state.
- **FR-004**: Core MUST provide a **single logical catalog of launchable items** (installed and supported applications and related metadata the launcher needs) with a stable consumer-facing contract (AppIndex role). Features read through that contract rather than re-implementing discovery, filtering, or launcher-facing metadata semantics.
- **FR-005**: Core MUST route **user- and system-initiated actions** to the correct handler through a documented dispatch path (ActionDispatcher role), avoiding duplicate “who handles this?” logic across modules.
- **FR-006**: The **primary home host** (HomeActivity role) MUST have a single documented ownership: it is the **application shell entry** for the home experience — lifecycle host and composition root for the home UI — not a repository for business rules that belong in Core services or feature modules.
- **FR-007**: Core MUST define and own the **fallback hierarchy** for launcher startup and capability degradation. At minimum, this hierarchy MUST cover: invalid profile, incompatible profile version, missing optional module, denied permission, unavailable capability contract, and degraded app-catalog state.
- **FR-008**: Core MUST define a single **conflict-resolution order** for runtime composition. Where profile intent, module availability, contract compatibility, permission state, and runtime safety disagree, the resolution order MUST be deterministic, documented, and consistent across the product.
- **FR-009**: Core MUST expose **published contracts** separately from private implementation concerns. A contract intended for cross-module use MUST be explicitly designated as public API for the product architecture; private helper types, internal orchestration details, and feature-specific convenience layers MUST remain non-contract implementation details.
- **FR-010**: Versioned contracts used by modules and profiles MUST have documented compatibility expectations. Within a single MVP line, contract evolution SHOULD preserve backward compatibility unless a future spec explicitly introduces a breaking change and its required migration path.

#### Forbidden inside Core (explicit)

- **FR-011**: Core MUST NOT contain **feature-specific UI flows** beyond the minimal shell needed to host the home experience and shared scaffolding.
- **FR-012**: Core MUST NOT own **product experiments** unrelated to platform integration, profiles, routing, catalog, fallback policy, or module lifecycle.
- **FR-013**: Core MUST NOT implement **cloud sync, analytics pipelines, remote configuration, caregiver dashboards, or network-backed product subsystems** as part of this foundation.
- **FR-014**: Core MUST NOT implement a **marketplace, third-party plugin store, or arbitrary plugin execution model**. Optional modules are **first-party, contract-bound** extensions only.
- **FR-015**: Core MUST NOT introduce **Compose-based UI** for MVP. UI in the Core shell remains View/XML consistent with the product constraint above.
- **FR-016**: Core MUST NOT add **persistent background services, periodic work loops, or polling loops by default**. Any such mechanism requires a future spec with constitution-compliant justification.
- **FR-017**: Core MUST NOT accumulate **feature-owned branching logic** driven by scattered flags, one-off settings, or per-feature exceptions that bypass the profile and module model.
- **FR-018**: Core MUST NOT expose broad “god interfaces” that let features bypass AppIndex, ActionDispatcher, EventRouter, or ModuleRegistry ownership just for convenience.

#### MVP module boundaries (named)

- **FR-019**: The specification MUST name and describe these **initial build-time module boundaries** for MVP:
  - **`app` (application shell)**: process entry, `HomeActivity`, wiring of Core and feature modules, and the user-visible home container; keeps **thin orchestration only**.
  - **`core`**: SystemEventBridge, EventRouter, ModuleRegistry, ProfileEngine, AppIndex, ActionDispatcher, fallback policy, compatibility resolution, and shared **published contract** types consumed by features.
  - **`feature-*` (per vertical capability)**: isolated user-facing or domain features; no direct OS listener registration; consumes only published Core contracts and exposes only its declared capability surface.
  - **`shared-contracts` (optional split)**: MAY exist as a separate lightweight module if needed to break circular dependencies or keep published contract surfaces tighter. If not split, its role is fulfilled by a clearly marked API package space inside `core`. The exact split is deferred to planning without blocking this specification.

#### Ownership of named components

| Component | Owner (module / layer) | Accountability (system terms) |
|---|---|---|
| HomeActivity | `app` (shell) | Hosts home lifecycle and composes home UI; delegates to Core and feature contracts; does not own catalog, fallback policy, or OS listeners. |
| ProfileEngine | `core` | Loads, validates, interprets, and applies profile semantics; resolves defaults, compatibility, migration semantics, and safe fallback. |
| ModuleRegistry | `core` | Registers modules, resolves capabilities, controls lifecycle-level composition, and prevents cross-feature coupling except through published contracts. |
| EventRouter | `core` | Distributes project-level events internally after normalization; features consume routed events rather than raw Android callbacks. |
| AppIndex | `core` | Maintains the logical app catalog and query contract for consumers; provides consistent launcher-facing item semantics and shields consumers from OEM and package-visibility quirks. |
| ActionDispatcher | `core` | Resolves and dispatches actions such as opening apps, settings intents, and internal commands through one documented pipeline. |
| SystemEventBridge | `core` | Sole default owner of OS-level registration strategy, normalization, throttling, deduplication, and translation to internal events. |

#### Extension and coupling rules

- **FR-020**: New capabilities MUST ship as **new or extended modules** behind **versioned contracts**. Core changes for routine features are limited to contract additions, compatibility rules, or registry hooks — not open-ended edits across unrelated Core packages.
- **FR-021**: Cross-feature dependency is **prohibited** except through **published contracts** in `core` API or `shared-contracts`.
- **FR-022**: Profiles MUST remain the **explicit switch** for enabling and disabling modules and major UX modes. Hidden global flags scattered in code are **forbidden** for feature toggles.
- **FR-023**: Any proposal that adds OS listeners, services, periodic work, or long-lived background processing MUST pass the battery and integration gates described in the constitution and documented in a future `plan.md`.
- **FR-024**: A capability requested by profile but not satisfiable at runtime because of absent module, denied permission, incompatible contract, or missing dependency MUST enter a documented degraded mode. Runtime safety and home-shell continuity take precedence over profile intent.
- **FR-025**: Core-published contracts MUST be introduced only when they support cross-module composition, launcher-critical shell behavior, or a stable system boundary. A feature-specific abstraction that is not shared at the architectural level MUST remain outside Core’s public contract surface.

#### Concrete extension example (normative narrative)

- **FR-026**: The following narrative is normative as an example of acceptable extension:
  - A new capability called **Favorites** is approved for MVP evolution.
  - The capability is introduced as a new `feature-favorites` module with a module descriptor registered through ModuleRegistry.
  - The module reads launcher-visible apps only through the AppIndex contract.
  - The module reacts to relevant launcher events only through EventRouter project-level events, such as normalized package or profile-change signals.
  - The module triggers launch or settings behavior only through ActionDispatcher contracts.
  - ProfileEngine determines whether Favorites is enabled for the active profile.
  - No raw OS listener is registered by `feature-favorites`.
  - No unrelated Core package is rewritten to embed Favorites-specific business logic.
  - Any Core change is limited to an explicit contract addition, registry hook, or compatibility rule needed for general platform composition rather than a feature-specific shortcut.

#### Documentation obligations for implementability

- **FR-027**: The foundation MUST be **testable in principle**. Acceptance criteria below MUST be verifiable by architecture review and by future automated tests specified in planning, including contract tests for events and profiles, integration tests for catalog and dispatch, and startup/fallback tests for degraded runtime conditions.
- **FR-028**: Any future plan implementing this specification MUST identify which aspects are tested by contract tests, which by integration tests, and which by end-to-end launcher behavior tests, so that responsibility boundaries remain reviewable over time.

#### Enumerated Core responsibilities (acceptance roll-up)

1. Own platform event intake and OS listener strategy (SystemEventBridge).
2. Translate OS signals into project-level events.
3. Distribute project-level events internally (EventRouter).
4. Register and compose feature modules (ModuleRegistry).
5. Apply profiles with validation, compatibility checks, and safe fallback (ProfileEngine).
6. Expose a stable launch catalog contract with consistent launcher-facing item semantics (AppIndex).
7. Route actions through one documented pipeline (ActionDispatcher).
8. Define deterministic startup and degradation fallback hierarchy.
9. Define contract publication boundaries and compatibility expectations.

#### Enumerated forbidden-in-Core patterns (acceptance roll-up)

1. Feature-specific UI flows beyond the minimal home shell.
2. Cloud sync, analytics, remote config, caregiver dashboard, or other network-backed product subsystems.
3. Marketplace or third-party plugin execution.
4. Jetpack Compose for MVP UI in this product line.
5. Default persistent background services or polling loops without approved future specification.
6. Feature-owned hidden flags and branching that bypass profiles and module contracts.
7. “God interfaces” that bypass documented ownership boundaries.

### Key Entities

- **Profile**: Describes an active launcher variant, including accessibility presets, enabled modules, and layout-related preferences. A Profile is versioned, validated, and interpreted by ProfileEngine, and it drives runtime composition without overriding runtime safety.
- **Module descriptor**: Declares a feature module’s identity, contract surfaces, compatibility requirements, and dependencies on Core services. It is consumed by ModuleRegistry.
- **Published contract**: A versioned architectural interface or schema intentionally exposed for cross-module composition. It is stable by design relative to private implementation details.
- **Project-level event**: A normalized signal derived from OS or internal sources, consumed via EventRouter rather than raw Android callbacks in features.
- **Catalog entry**: A logical item in AppIndex, such as a launchable application, with stable attributes needed for accessibility, display, and actions.
- **Action**: A user- or system-initiated request routed through ActionDispatcher to a handler implementing a documented contract.
- **Safe fallback**: A deterministic degraded launcher state in which the base home shell remains usable for MVP-critical navigation even when profile, module, permission, or compatibility conditions are partially unavailable.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The specification contains an **enumerated list of at least six** distinct Core responsibilities and **at least five** explicit **forbidden-in-Core** rules, each independently reviewable.
- **SC-002**: **MVP module boundaries** name **at least three** concrete boundaries — `app`, `core`, and the `feature-*` pattern — with a one-sentence role statement each, without prescribing build tool details.
- **SC-003**: **100%** of the seven named components — HomeActivity, ProfileEngine, ModuleRegistry, EventRouter, AppIndex, ActionDispatcher, and SystemEventBridge — appear in the ownership table with **exactly one** primary owner each.
- **SC-004**: **Extension rules** include **at least one concrete example scenario** showing how a new capability is added without rewriting Core beyond contracts, registry hooks, or compatibility rules.
- **SC-005**: **Non-goals** list **at least four** scope exclusions drawn from product direction, reviewable by a non-implementer stakeholder.
- **SC-006**: A reviewer can confirm there is **no dependency** on cloud sync, remote config, marketplace plugins, analytics, or third-party execution as part of this foundation specification’s scope.
- **SC-007**: A reviewer can identify the documented fallback hierarchy and conflict-resolution rule for profile intent, module availability, contract compatibility, permission state, and runtime safety directly from the specification text.
- **SC-008**: A reviewer can distinguish published architectural contracts from private implementation details without needing a code-level blueprint.
- **SC-009**: A reviewer can state the **minimum supported Android API level** from this specification alone, without opening build files.
- **SC-010**: A reviewer can identify the rule for **APIs above minSdk** (degrade vs. raise minSdk) from this specification alone.

## Non-goals

- Class-by-class or file-by-file implementation blueprints.
- Full downloadable module delivery pipeline, dynamic loading stores, or binary distribution beyond first-party, build-time modules for MVP.
- Marketplace, third-party plugin ecosystems, or untrusted code execution.
- Cloud sync, analytics, remote configuration, or caregiver dashboard.
- Jetpack Compose as the UI toolkit for this product line in MVP.
- Exact JSON schema fields for profiles, which are deferred to a dedicated profile/configuration specification or plan.
- Detailed battery-budget numbers, scheduler parameters, or thread-model specifics, which belong in planning and implementation so long as they comply with this specification and the constitution.
- Feature-specific UX behavior for future verticals beyond the minimal shell and module-boundary rules defined here.

## Assumptions

- MVP targets **first-party modules** built with the main app. “Optional module absent at runtime” is a **design constraint** for future optional packaging and compatibility behavior, not a requirement to ship dynamic feature delivery in MVP.
- **One other explicitly named platform boundary** aside from Core is **not required for MVP**. If introduced later, it MUST be named in a plan and satisfy the same listener-ownership and documentation rules as Core.
- Elderly and accessibility priorities are **product defaults**. Specific screen-level metrics will be defined in feature specifications, but Core MUST not block large-type, high-contrast, simplified-navigation, or other profile-driven accessibility variants.
- The team is small, so **module count stays minimal**. Prefer a few coarse modules over many micro-modules unless a future plan justifies a split under the constitution.
- **OEM variance** (vendor-specific lifecycle and integration quirks) is a **separate** concern from **API level**; both MUST be handled under User Story 3 and platform integration rules, not collapsed into “API only.”
- **Google Play** (or equivalent store policy) MAY impose a **higher effective floor** than this spec’s **minSdk**; release planning MUST reconcile store minimums with the documented product floor before shipping.
- Safe fallback for MVP means the user can still reach and use the primary home shell with deterministic navigation even if optional capabilities are disabled, degraded, or unavailable.
- When profile intent conflicts with runtime reality, the governing precedence is: **runtime safety and shell continuity first**, then **contract compatibility**, then **module availability**, then **permission-constrained capability state**, and finally **profile intent** for optional behavior. A future plan may refine implementation strategy but MUST NOT invert this priority without a new specification.
