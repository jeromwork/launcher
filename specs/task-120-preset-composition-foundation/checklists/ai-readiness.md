# Checklist: ai-readiness

Evaluated against `specs/task-120-preset-composition-foundation/spec.md` on 2026-07-10.

Reference: `.claude/skills/checklist-ai-readiness/SKILL.md`, `docs/product/use-cases/12-ai-integration.md`.

---

## Capability shape

- [x] CHK001 Each user-facing capability the AI could invoke is expressed as a **domain verb** — spec's AI Affordance section lists `listAvailableComponents()`, `getActivePreset()`, `getProfile()`, `applyComponentChange(componentId, paramsOverride)`, `installPreset(preset)`. All are domain verbs, not HTTP endpoints or SDK calls.
- [x] CHK002 Capability signatures use **domain types only** — signatures use `ComponentDeclaration`, `PresetRef`, `ProfileSnapshot`, `Outcome`, `Preset`, `List<ChangeItem>`. Spec explicitly states "no `GeminiTool`, `OpenAIFunction`, `MCPTool` in ports".
- [x] CHK003 Each capability has a one-line natural-language description — AI Affordance section carries a short gloss for each verb (e.g. `listAvailableComponents(): List<ComponentDeclaration>` — "что можно настраивать сейчас"; `installPreset` — "polymorphic install из любого source").
- [ ] CHK004 Capabilities are listed in a registry (or planned to be) — spec references "future Capability Registry / MCP exposure" and "F-2 per constitution", but does NOT declare a registry-shaped seam in the FRs or Key Entities of this foundation. The four verbs are described in AI Affordance prose only; there is no `CapabilityRegistry` port entity paralleling `ProviderRegistry`. Acceptable given "Out of scope for this spec: exposure — future work", but flagged: downstream F-2 must not need to refactor `ReconcileEngine` to introduce the registry.

## Affordance contract

- [x] CHK005 Reads vs writes are declared — AI Affordance section states "Read-only access to `activeComponents` для explanation flows" and "Write access к `activeComponents` через `applyComponentChange` verb, идёт через `ProviderRegistry.resolve` + `Provider.apply`".
- [ ] CHK006 Each capability is declared **idempotent / reversible / irreversible** — spec does NOT explicitly tag the four verbs with these properties. `applyComponentChange` inherits reconcile-engine semantics (check → apply, idempotent by design via `Outcome.Ok` if already applied), but this is implicit. `installPreset` is single-shot install per FR-011 (rejects same-version-different-content) and thus effectively non-idempotent across versions — not called out for AI planning. Fixable with a one-line property tag per verb.
- [x] CHK007 Confirmation for irreversible actions is explicit — Wizard cancel path (FR-024 `preWizardSnapshot`, US1 acceptance #4) requires explicit confirm-dialog; owner-driven undo is scoped. Admin push is out of scope of AI verbs here.
- [ ] CHK008 Rate / quota constraints declared — no rate or quota is declared for `applyComponentChange` or `installPreset`. Given MVP local-only surface, rate limiting is not required, but the spec should at least state "no rate limit at foundation; provider adapter may add". Minor omission.

## PII / privacy boundary

- [x] CHK009 No capability returns raw PII by default — `Profile` explicitly contains only Component parameters (font size, SOS target pairing-id). AI Affordance section is explicit: "No PII leaves device".
- [x] CHK010 If a capability MUST return PII, the spec declares provider — N/A at foundation (no PII-returning capability). Explicit declaration: "Identity-bound данные (pairing keys, contact PII) — в отдельном encrypted блоке, не в Profile".
- [x] CHK011 Default MVP: no PII leaves device — explicit ("No PII leaves device").
- [x] CHK012 No silent LLM prompt/response telemetry — spec explicitly declares "no telemetry" in Out-of-scope.

## Provider-agnosticism

- [x] CHK013 No specific provider assumed — spec mentions no provider by name. "Gemini/OpenAI/Claude/MCP" appear only in the negative ("no `GeminiTool`...").
- [x] CHK014 No package-level provider dependency — Assumptions list DI as Hilt, JSON as kotlinx.serialization, persistence as DataStore; no LLM SDK dependency. tasks.md (per plan) contains no provider integration task.
- [x] CHK015 On-device inference wrapped behind port — not applicable at this spec (no inference in foundation). If added later, `Provider<Component>` port already exists as the swap seam.

## Out-of-scope discipline

- [x] CHK016 Spec explicitly states "no provider implementation" — "Out of scope for this spec: no provider implementation, no LLM prompt design, no telemetry".
- [x] CHK017 Spec does NOT design prompts / function-call schemas / token budgets — confirmed absent.
- [x] CHK018 Spec does NOT ship a demo AI integration — confirmed. `applyComponentChange` and `installPreset` are the same verbs owner-driven flow uses; not AI-shaped.

## Acceptance evidence

- [ ] CHK019 Sample capability call signature in Local Test Path — Local Test Path lists fakes (`FakePoolSource`, `FakePresetSource`, `FakeInteractionSink`, `FakeProvider`, `FakeAuthHandoffService`) and gradle commands, but does NOT include a sample invocation snippet for any of the four AI Affordance verbs. Minor gap: AI-verb roundtrip is verifiable through existing engine tests (US3 acceptance #1 covers `applyComponentChange`-equivalent path), but not explicitly labelled as AI affordance evidence.
- [x] CHK020 Spec lists capability in the AI Affordance section — present and populated with four verbs plus data-access rules.

---

## Summary

- **Total**: 20 CHK
- **Passing**: 16
- **Failing**: 4 (CHK004, CHK006, CHK008, CHK019)

**Findings — all four failures are minor / additive fixes, no rewrites required**:

- **CHK004**: Capability Registry deferred to F-2 is fine, but no explicit port-shaped seam declared. Add one line to FR or Key Entities: "`CapabilityRegistry` port — future (F-2), verbs will be registered additively without changes to `ReconcileEngine` or `ProviderRegistry`."
- **CHK006**: Add per-verb property tags — `listAvailableComponents` (idempotent, read-only), `getActivePreset` / `getProfile` (idempotent, read-only), `applyComponentChange` (idempotent by reconcile semantics — repeated call with same params yields `Outcome.Ok`), `installPreset` (non-idempotent across versions — FR-011 same-version-different-content rejection).
- **CHK008**: One-line explicit "no rate/quota at foundation; may be added at exposure adapter" would close this.
- **CHK019**: Add sample call snippet to Local Test Path — e.g. `applyComponentChange("font-tile", {"scale": 1.8})` verified via `FakeProfileStore` + `FakeProvider<FontSize>` returning `Outcome.Ok`, corresponds to US3 acceptance #1.

**Context-specific notes (from caller)**:
- Vendor-SDK leakage check: **clean** — no Gemini/OpenAI/MCP types anywhere in signatures (CHK002).
- PII-off-device claim consistent with wire format: **yes** — `Profile` scope is Component params only, identity blob explicitly segregated (CHK009-CHK011).
- F-2 alignment vs implementing-now: **correct** — Out of scope explicit, no premature registry code (CHK016-CHK018), but the seam itself is not called out as a Key Entity (CHK004 failure).
- Provider-agnostic shape declared: **yes** — explicit "Provider-agnostic shape" paragraph (CHK013).
- `applyComponentChange` returning `Outcome`: partially serves AI — `Outcome.Failed(reason: String)` exposes a raw human string, not a structured error code. For AI grounding this is weak (AI cannot reason about failure categories without string parsing). No i18n concern within this spec because `Outcome.reason` is not user-facing display text — it is a developer/log-facing diagnostic. Recommendation for downstream F-2 exposure adapter: shape a machine-readable `FailureCategory` enum layered on top of `Outcome.Failed` at the Capability Registry boundary, not inside the domain `Outcome`. Not blocking for this spec.
