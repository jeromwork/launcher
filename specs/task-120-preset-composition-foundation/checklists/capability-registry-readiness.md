# Checklist: capability-registry-readiness

Applied to: `specs/task-120-preset-composition-foundation/spec.md`
Date: 2026-07-10
Rules enforced: [CLAUDE.md](../../../CLAUDE.md) rule 4 (Minimum Viable Architecture) + roadmap reorder 2026-06-15 deferring F-2 (Capability Registry Foundation) to end of Phase 2. See [.claude/skills/checklist-capability-registry-readiness/SKILL.md](../../../.claude/skills/checklist-capability-registry-readiness/SKILL.md) and [docs/dev/capability-registry-pending.md](../../../docs/dev/capability-registry-pending.md).

Scope of "new actions" this spec introduces (found by scanning the spec):

1. `applyComponentChange(componentId, paramsOverride) -> Outcome` — AI Affordance §, explicit external-callable verb.
2. `installPreset(preset) -> List<ChangeItem>` — AI Affordance §, polymorphic install verb.
3. `listAvailableComponents() -> List<ComponentDeclaration>` — AI Affordance §, read verb.
4. `getActivePreset() / getProfile() -> ProfileSnapshot` — AI Affordance §, read verbs.
5. Domain-level per-Component `apply` / `check` verbs handed off through `Provider<T>` (FR-006). Each Provider is a dispatcher site — future AI/voice exposure will target `apply` verbs (e.g. `apply_font_size`, `trigger_sos`, `apply_time_lockdown`).
6. `ReconcileEngine.run(RunMode, InteractionSink?)` (FR-010) — external-callable engine entry with four modes.
7. `PresetDiff.diff(current, incoming, pool) -> List<ChangeItem>` (FR-011) — external-callable diff verb (admin push handling).

---

## Sewing-point bookkeeping

- [ ] CHK-CR-001 **FAIL.** Grep for `TODO(capability-registry)` across the entire spec directory returned **no matches**. Not a single sewing-point comment is declared for any of the 7 new actions enumerated above. The spec's AI Affordance § lists the verbs but does not attach the `TODO(capability-registry): объявить capability declaration для <action_name>` marker at the dispatcher site. F-2 collection (when Phase 2 finishes) will have to reverse-engineer this from prose. **Corrected shape**: at each dispatcher site the plan / code should carry:
  ```kotlin
  // TODO(capability-registry): объявить capability declaration для apply_component_change
  //   при сборке F-2 — intent, voicePhrases (Map<Locale, List<String>>),
  //   params (componentId: String, paramsOverride: JsonObject), idempotent flag,
  //   requiresConfirmation, auth scope (device-local | admin-only).
  ```
  Since implementation code does not exist yet, the spec MUST at minimum add these markers inline in FR-006 / FR-010 / FR-011 / AI Affordance § so the plan.md and eventual code inherit them.
- [ ] CHK-CR-002 **FAIL (consequence of CHK-CR-001).** No dispatcher site is nominated because no TODOs exist. When the TODOs are added they MUST live at the dispatcher: `ReconcileEngine.run` for `applyComponentChange` / `installPreset`, `Provider<T>.apply` for per-Component action verbs, `PresetDiff.diff` for `installPreset` classification. NOT at UI callers (`WizardActivity`, `SettingsActivity`, `SettingsViewModel`) — those are consumers.
- [ ] CHK-CR-003 **PARTIAL FAIL.** The four AI Affordance verbs are named camelCase (`applyComponentChange`, `installPreset`, `listAvailableComponents`, `getActivePreset`, `getProfile`) — F-2 expects **stable slug-cased** intent names (`apply_component_change`, `install_preset`, `list_available_components`, `get_active_preset`, `get_profile`). Provider-level verbs (`apply` per Component subtype) are not named at all — they need slug-cased intent names such as `apply_font_size`, `trigger_sos`, `apply_time_lockdown`, `apply_toolbar`, `apply_app_tile`. **Corrected shape**: append a mapping table under AI Affordance §: `applyComponentChange → intent apply_component_change`, plus one row per Component subtype.
- [x] CHK-CR-004 **PASS with caveat.** Domain-level params are typed: `Component` is a sealed hierarchy (FR-001), `ComponentDeclaration` is domain-typed (Key Entities), `Outcome` is sealed (FR-008), `ChangeItem` is domain-typed (FR-011), `PresetRef` is domain-typed (AI Affordance §). **Caveat**: `applyComponentChange(componentId: String, paramsOverride: JsonObject)` uses `String` id and `JsonObject` for override. Owner-directive Q4 (session 2.5 clarify) explicitly allows override on all three preset sections and JSON Schema validation at load time (FR-004). F-2 will need to derive per-Component param schemas from `Component` sealed subtypes — mechanically doable given each subtype's `@Serializable` shape, but the spec should note that `applyComponentChange` params are schema-per-`componentId` (not free-form).

## Provider neutrality (refuse signals)

- [ ] CHK-CR-005 **FAIL.** Spec explicitly mentions **MCP** in domain / feature copy at line 238: *"Exposable capabilities (для future Capability Registry / MCP exposure)"*. This names a specific external protocol / provider surface in the AI Affordance §, violating the "no specific AI/voice/MCP provider" gate. Additionally line 249 names `GeminiTool`, `OpenAIFunction`, `MCPTool` as counter-examples ("никаких ... в port'ах") — this is arguably OK because it's a negation clause forbidding them, not a commitment. **Corrected shape** for line 238: replace *"Capability Registry / MCP exposure"* with *"Capability Registry / future ExposureAdapter implementation"* (abstract, provider-agnostic). Leave line 249's negation intact — it's a refusal statement, not a commitment.
- [x] CHK-CR-006 **PASS.** No AI / voice SDK appears in the Assumptions dependency list or Local Test Path. Dependencies enumerated: kotlinx.serialization, Hilt, DataStore, WorkManager / AlarmManager / geofencing (Android platform APIs). No `google-play-services-assistant`, no `openai-kotlin`, no `mcp-sdk` — clean.
- [x] CHK-CR-007 **PASS.** Spec does not define an exposure adapter implementation. AI Affordance § "Out of scope" explicitly states: *"no provider implementation, no LLM prompt design, no telemetry. Экспозиция capability registry — future work (F-2 per constitution)"*. F-2 deferral is respected at the implementation level.

## Voice / conversational surface

- [ ] CHK-CR-008 **FAIL.** Several introduced actions are plausibly voice-triggerable — "позвони бабушке" (call target on `Sos` action), "покажи настройки" (`getProfile`), "включи блокировку в 8 утра" (`apply_time_lockdown`), "поставь шрифт побольше" (`apply_font_size`). No `TODO(capability-registry)` mentions `voicePhrases must be localised — supply per Locale`. **Corrected shape**: when CHK-CR-001 TODOs are added, actions plausibly voice-triggerable MUST carry the voice-phrases note. Owner-facing text remains Russian, but the phrase source (`Map<Locale, List<String>>`) is per rule.
- [ ] CHK-CR-009 **FAIL.** Confirmation requirement is NOT declared for any action:
  - `installPreset` — destructive-adjacent (replaces current preset, may re-run wizard). SHOULD carry `requiresConfirmation: true`.
  - `applyComponentChange` — mutates active configuration silently. SHOULD carry `requiresConfirmation: false` for reversible edits (font size), `true` for lockdown / SOS changes.
  - Per-Component `trigger_sos` (future) — non-idempotent, destructive-effect. MUST carry `requiresConfirmation: true`.
  - **Corrected shape**: extend the AI Affordance § verb list with `confirmation: yes/no` column, or bake into the TODO block.

## Auth / scope hints

- [ ] CHK-CR-010 **FAIL.** No `auth scope` hint appears for any of the introduced actions. `installPreset` from admin push — clearly `admin-only` (paired). `applyComponentChange` from local Settings — `device-local`. `listAvailableComponents` — `device-local` (harmless). Per-Component `apply` verbs vary (e.g. Lockdown might be `admin-only` if unset by owner). **Corrected shape**: when CHK-CR-001 TODOs are added, each MUST include `auth scope: device-local | admin-only | pair-authorised | caregiver-allowed`.
- [ ] CHK-CR-011 **FAIL.** Idempotency is not declared for any action:
  - `applyComponentChange` — idempotent (same params → same state).
  - `installPreset` — idempotent-if-same-content; FR-011 says same-version-different-content is rejected. But re-installing the same preset is a no-op ⇒ idempotent.
  - `listAvailableComponents`, `getActivePreset`, `getProfile` — pure reads, idempotent.
  - Per-Component `trigger_sos` — **non-idempotent** (each call fires SOS notification).
  - **Corrected shape**: add `idempotent: true/false` per action in the AI Affordance § verb list.

## F-2 collection readiness

- [ ] CHK-CR-012 **FAIL.** `docs/dev/capability-registry-pending.md` was read; it contains 5 entries all sourced from spec **015 (F-3)** (wizard, localization, tileSet, systemSettings). **Zero entries** point to `task-120-preset-composition-foundation` despite this spec introducing 4 explicit AI Affordance verbs + Provider-level verbs. F-2 enumeration pass will miss all of them. **Corrected shape**: append rows to `docs/dev/capability-registry-pending.md` for at minimum: `apply_component_change`, `install_preset`, `list_available_components`, `get_active_preset`, `get_profile`, and one row per Component subtype's `apply` (`apply_app_tile`, `apply_font_size`, `apply_sos`, `apply_toolbar`) with columns Source spec = `task-120`, auth scope, idempotent, confirmation, voice-triggerable filled in.

---

## Summary

- **Result**: **REFUSE** (per skill's "When to refuse" — a new action is added but no `TODO(capability-registry)` appears; a specific AI/MCP provider is mentioned in AI Affordance §).
- **Counts**: 3/12 PASS, 9/12 FAIL.
- **PASS**: CHK-CR-004 (params typed, with caveat), CHK-CR-006 (no SDK imports), CHK-CR-007 (no exposure adapter shipped — F-2 deferral respected at implementation level).
- **FAIL**: CHK-CR-001, CHK-CR-002, CHK-CR-003, CHK-CR-005, CHK-CR-008, CHK-CR-009, CHK-CR-010, CHK-CR-011, CHK-CR-012.

### Corrected shape (minimum set to unblock)

1. **Add sewing-point TODOs** inline in spec at FR-006 (Provider.apply dispatcher), FR-010 (ReconcileEngine.run entry), FR-011 (PresetDiff.diff entry), and in the AI Affordance § verb list. Each TODO must name intent slug + voicePhrases-locale note (if voice-plausible) + auth scope + idempotent flag + requiresConfirmation.
2. **Replace "MCP exposure"** on line 238 with "future ExposureAdapter implementation".
3. **Add slug-cased intent names** — mapping table under AI Affordance § translating camelCase API names to slug-cased intent keys.
4. **Append `task-120` rows** to `docs/dev/capability-registry-pending.md` for the 4 AI Affordance verbs + 4+ Provider-level `apply_<component>` verbs, with all five metadata columns filled.

### Notes on F-2 deferral posture

The spec's *implementation-level* F-2 deferral is clean — no exposure adapter is being built now (CHK-CR-007 PASS), no LLM prompts, no telemetry. AI Affordance § "Out of scope" explicitly cites F-2. What is missing is the **bookkeeping** — the sewing points that make F-2 collection trivial rather than archaeological. This is the specific gap this checklist exists to catch, and the current spec fails it comprehensively.
