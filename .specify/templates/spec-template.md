# Feature Specification: [FEATURE NAME]

**Feature Branch**: `[###-feature-name]`  
**Created**: [DATE]  
**Status**: Draft  
**Input**: User description: "$ARGUMENTS"

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - [Brief Title] (Priority: P1)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently - e.g., "Can be fully tested by [specific action] and delivers [specific value]"]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]
2. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 2 - [Brief Title] (Priority: P2)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 3 - [Brief Title] (Priority: P3)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

[Add more user stories as needed, each with an assigned priority]

### Edge Cases

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right edge cases.
-->

- What happens when [boundary condition]?
- How does system handle [error scenario]?

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: System MUST [specific capability, e.g., "allow users to create accounts"]
- **FR-002**: System MUST [specific capability, e.g., "validate email addresses"]  
- **FR-003**: Users MUST be able to [key interaction, e.g., "reset their password"]
- **FR-004**: System MUST [data requirement, e.g., "persist user preferences"]
- **FR-005**: System MUST [behavior, e.g., "log all security events"]

*Example of marking unclear requirements:*

- **FR-006**: System MUST authenticate users via [NEEDS CLARIFICATION: auth method not specified - email/password, SSO, OAuth?]
- **FR-007**: System MUST retain user data for [NEEDS CLARIFICATION: retention period not specified]

### Key Entities *(include if feature involves data)*

- **[Entity 1]**: [What it represents, key attributes without implementation]
- **[Entity 2]**: [What it represents, relationships to other entities]

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.

  BACKLOG MARKER `[backlog]`:
  Пометить маркером `[backlog]` те SC, которые должны попасть в portfolio-tracker
  (Acceptance Criteria backlog-task'а для этой фичи). Это 4-7 высокоуровневых,
  user-visible критериев готовности. Технические детали (тайминги, fitness functions,
  internal contract tests) НЕ помечаются — они остаются только в spec.md.

  Skill `procedure-sync-backlog-ac` автоматически вызывается в конце
  speckit-specify / speckit-clarify / speckit-tasks и синхронизирует помеченные
  SC в `backlog/tasks/task-N - <title>.md` через MCP.
-->

### Measurable Outcomes

- **SC-001 [backlog]**: [Высокоуровневый user-visible критерий — пример: "Recovery работает на втором устройстве за <60с"]
- **SC-002**: [Технический критерий — пример: "Argon2id timing ≤3s P95 на эмуляторе"]
- **SC-003 [backlog]**: [User-visible — пример: "90% пользователей успешно завершают setup с первой попытки"]
- **SC-004**: [Internal contract — пример: "Identity isolation contract test проходит для двух Google UID"]

## Assumptions

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right assumptions based on reasonable defaults
  chosen when the feature description did not specify certain details.
-->

- [Assumption about target users, e.g., "Users have stable internet connectivity"]
- [Assumption about scope boundaries, e.g., "Mobile support is out of scope for v1"]
- [Assumption about data/environment, e.g., "Existing authentication system will be reused"]
- [Dependency on existing system/service, e.g., "Requires access to the existing user profile API"]

## Local Test Path *(mandatory)*

<!--
  How can a developer verify this feature locally — without depending on real users,
  external services that cost money, or physical hardware we don't own?
  Be concrete: which emulator preset, which fake adapter, which fixture, which command.
  If a critical path can ONLY be tested on a real device, say so explicitly and add an
  inline-TODO(physical-device) where the gap is — do NOT pretend it can be tested when it can't.
  Reference: memory `reference_testing_environment.md`.
-->

- **Emulator / device**: [e.g., `pixel_5_api_34` via skill `android-emulator`, or "logic only — JVM unit tests"]
- **Fake adapters used**: [list every port whose real adapter is replaced for this test — e.g., `FakeAuthProvider`, `FakeContactsRepository`]
- **Fixtures / seed data**: [where the test data lives — e.g., `core/src/test/resources/fixtures/family-group-v1.json`]
- **Verification command**: [exact command(s) — e.g., `./gradlew :core:test --tests *FamilyGroupTest`, or `./gradlew :app:connectedDebugAndroidTest`]
- **Cannot-test-locally gaps**: [each gap explicit, e.g., "OEM-specific background-restrict behavior on Xiaomi MIUI → inline TODO(physical-device)"; or `none`]

## AI Affordance *(mandatory)*

<!--
  Even if no AI provider ships in this spec, declare how an AI agent could later operate on this feature.
  Goal: Capability Registry readiness without committing to a provider today.
  If the feature has zero AI surface (e.g., crypto primitives, build infrastructure), explicitly state
  "no AI affordance — internal capability only" and explain why.
-->

- **Exposable capabilities**: [verbs an AI could invoke later — e.g., `createFamilyGroup(name, locale)`, `inviteMember(groupId, contactRef)`. Domain verbs, NOT SDK calls.]
- **Required affordances on data**: [what an AI would need to inspect or change — e.g., "read-only access to `FamilyGroup.members` for suggestion flows; no PII leaves device"]
- **Provider-agnostic shape**: [confirm: capability is expressed as domain port, no Gemini/OpenAI/Claude types in signatures. Reference CLAUDE.md rule 1.]
- **Out of scope for this spec**: [explicit: "no provider implementation, no LLM prompt design, no telemetry — that ships in FUTURE-SPEC-AI-***"]
- **Or**: `no AI affordance — [reason]`

## OEM Matrix *(mandatory if feature touches device behavior)*

<!--
  Anything that runs on real Android devices may behave differently across OEMs.
  Required when the feature touches: background work, permissions, launcher role, notifications,
  battery optimization, autostart, doze, app standby, foreground service, content provider exposure,
  storage scoping, telephony, SMS, contacts, accessibility services.
  Skip ONLY if the feature is purely server-side / build-time / pure-Kotlin domain logic — state so explicitly.
-->

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---------------|------------------|-------------------------|---------------------|
| Stock Android (Pixel) | baseline | — | emulator `pixel_5_api_34` |
| Samsung One UI | [e.g., aggressive background kill of non-system launchers] | [e.g., guide user through "Battery → Allow background activity"] | [device matrix entry / TODO(physical-device)] |
| Xiaomi MIUI | [e.g., autostart manager blocks foreground service] | [e.g., post-onboarding deep-link to autostart settings] | [TODO(physical-device)] |
| Huawei EMUI | [e.g., protected apps list] | [e.g., user instruction page] | [TODO(physical-device)] |

If OEM matrix does not apply: state `not applicable — [reason]` and remove the table.
