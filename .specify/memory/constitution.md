<!--
Sync Impact Report — Launcher / GitHub Spec Kit
- Version: 1.1.0 → 1.2.0
- Principles: Added Article XVIII (version control: logical commits and pushes); Article XV §13 cross-reference for AI agents
- Previous: Replaced generic [PRINCIPLE_N] placeholders with Articles I–XVII and supporting sections
- Added: Preamble, Normative Levels, Articles I–XVII, Delivery Rules for Spec Kit Artifacts,
  Project-Specific Architectural Direction, Quality Bar, Reference Patterns, Amendment History
- Removed: Template "Core Principles" / SECTION_2 / SECTION_3 / Governance placeholders
- Templates: plan-template.md ✅ | spec-template.md ✅ | tasks-template.md ✅
- Commands: .cursor/commands/*.md — verified; no CLAUDE-only guidance requiring change
- Follow-up TODOs: none
-->

# Launcher — Constitution for GitHub Spec Kit

**Status**: Adopted Draft 1.2  
**Project**: Android Launcher / accessibility-first configurable launcher platform  
**Scope**: Governs all future `spec`, `plan`, `tasks`, code generation, code review, refactoring, test design, release preparation, and architectural decisions.  
**Audience**: Human maintainers and AI agents working through GitHub Spec Kit.

---

## Preamble

This constitution defines the non-negotiable engineering rules for the Launcher project.

The project is not a demo application. It is a long-lived Android product intended to remain maintainable as functionality grows: accessibility modes, elderly-friendly UX, configurable builds, modular features, profile-driven behavior, and potentially optional downloadable modules.

All future specification, planning, implementation, review, and release work MUST optimize for:

1. **User safety and clarity** — the launcher is core UX infrastructure, not a toy.
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

## Article II. Product Reality Over Demo Thinking

1. The Launcher MUST be designed as a production Android application, not as a sample app.
2. Every feature MUST justify its operational cost in one or more of these dimensions:
   - user value,
   - accessibility impact,
   - maintainability,
   - reliability,
   - measurable performance.
3. “Nice to have”, “might need later”, and speculative extensibility are prohibited unless explicitly justified.
4. The default engineering posture SHOULD be **simple now, extensible only when evidence exists**.
5. Every new module, abstraction, event type, or configuration layer MUST have an explicit reason.

---

## Article III. Core Product Invariants

The following are non-negotiable product and engineering invariants:

1. Accessibility is a release concern, not a polish concern.
2. Battery waste, unnecessary background work, and uncontrolled listeners are product defects.
3. Launcher-critical behavior MUST remain reliable across device restarts, configuration changes, OEM variations, and partial permission states.
4. Invalid configuration MUST fail safely with deterministic fallback.
5. Optional capabilities MUST NOT compromise base launcher stability.
6. Security, privacy, and permission scope MUST remain proportionate to approved product requirements.

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

**Project-specific rule**: Because Launcher may evolve toward configurable builds and optional modules, the architecture MUST support modular features; however, the team SHOULD favor a small, disciplined module graph over micro-modules.

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

1. The Launcher MUST treat configuration as a first-class concern.
2. User-facing and distribution-facing variability MUST be modeled explicitly through profiles and structured configuration, not hidden `if/else` branches scattered across the codebase.
3. A profile or configuration system MAY use JSON or another structured schema, but it MUST define:
   - versioning,
   - validation,
   - backward-compatibility rules,
   - defaults,
   - migration policy.
4. Configuration SHOULD be declarative where practical.
5. Profiles MAY enable or disable modules, features, layouts, accessibility presets, or integration policies only through documented contracts.
6. Runtime-loaded or downloadable modules MUST remain optional enhancements, never assumptions for core launcher stability.
7. AI-generated changes MUST NOT silently widen config schema without updating:
   - schema docs,
   - validation logic,
   - migration logic,
   - example profiles,
   - tests.

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
3. For launcher-critical flows, preference SHOULD be given to:
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

1. The Launcher is a resident UX surface; therefore performance regressions are product regressions.
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
13. Align **Git** practice with **Article XVIII**: **commit** and **push** at **logical**, **validated** breakpoints (for example after a coherent task group or passing build), not as undifferentiated bulk dumps.

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
3. **Pushes** to the shared remote SHOULD occur when a **logical slice** of work is **complete and validated** (for example: builds/tests expected for that slice pass), so that history is **backed up**, **visible for review**, and **eligible for CI**—rather than accumulating large unpushed batches without a reason.
4. Work tracked in Spec Kit SHOULD align **commits** with **natural breakpoints** (for example: completed task phase, spec/plan/tasks update, or a self-contained implementation chunk), keeping branches **reviewable** and **traceable** to artifacts.
5. Secrets, credentials, signing keys, and **machine-local-only** configuration (for example paths in `local.properties`) MUST NOT be committed. Such files MUST remain excluded via `.gitignore` or equivalent policy.

**Rationale**: Predictable Git rhythm reduces lost work, eases code review, and keeps continuous integration meaningful for a long-lived product.

---

## Delivery Rules for Spec Kit Artifacts

The following rules are binding for future Spec Kit usage in this project:

### For `/speckit.constitution`

- Update this file, not an unrelated summary.
- Preserve article numbering and amendment history.

### For `/speckit.specify`

- Focus on user value, problems, acceptance criteria, and constraints.
- Do not smuggle architecture into the spec unless it is a true constraint.

### For `/speckit.plan`

- Add Constitution Check.
- Define module impact, event impact, config impact, accessibility impact, and test strategy.
- Identify risks for OEM behavior, permissions, lifecycle recreation, and battery.
- Clearly mark any exception or deviation from architectural defaults.

### For `/speckit.tasks`

- Keep tasks concrete, ordered, and traceable to plan and spec.
- Include tasks for tests, docs, schema updates, migrations, and instrumentation where required.

---

## Project-Specific Architectural Direction

This section is intentionally opinionated and specific to Launcher. It defines default direction, not an automatic ban on alternatives.

### 1. Core Platform Layer

Core owns platform-facing responsibilities such as app/package change observation, boot-related handling where approved, shared event intake, and common contracts. Core is not a junk drawer; it is a controlled boundary around Android system behavior.

### 2. Feature Modules

Features implement user-facing capabilities or isolated business logic. Features consume stable contracts from Core and shared modules. Features do not directly spread platform listeners across the codebase.

### 3. Profiles and Configurations

Profiles define curated launcher variants or user-focused operating modes. Examples may include elderly-friendly presets, simplified navigation variants, OEM-specific packaging constraints, or optional feature bundles. Profiles are data, not forks.

### 4. Optional Modules

Optional or downloadable components must fail gracefully when absent. The base launcher must remain coherent without them.

### 5. Accessibility Presets

Accessibility-related presets are treated as product features backed by configuration, validation, and acceptance tests—not as ad hoc UI overrides.

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

**Version**: 1.2.0 | **Ratified**: 2026-03-28 | **Last Amended**: 2026-03-28
