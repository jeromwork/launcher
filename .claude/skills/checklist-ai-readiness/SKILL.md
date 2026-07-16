---
name: checklist-ai-readiness
description: Verifies that any feature exposed (or likely to be exposed) to an AI agent has provider-agnostic capability surfaces — domain verbs declared as ports, no vendor SDK leakage, no PII exfiltration, no commitment to a specific LLM or MCP server in the MVP. Enables the Capability Registry pattern without forcing AI implementation today. Triggered by mentions of AI, LLM, MCP, agent, assistant, automation, suggest, recommend, Gemini, OpenAI, Claude, Capability Registry, Exposure Adapter.
---

# Checklist: ai-readiness

Verifies the spec is **AI-ready** — capabilities are reachable through provider-agnostic surfaces that a future AI agent (or MCP server, or rule-based automation) can invoke, without committing to a specific provider today.

Aligned with the project vision (Capability Registry + Exposure Adapter pattern) and CLAUDE.md rule 1 (domain isolation) + rule 2 (Anti-Corruption Layer).

This checklist does **NOT** require shipping AI in the spec. It requires that if an AI is added later, the swap is additive — a new adapter, not a rewrite.

Reference: `docs/product/use-cases/12-ai-integration.md`.

---

## Capability shape

- [ ] CHK001 Each user-facing capability the AI could invoke is expressed as a **domain verb** (`createFamilyGroup(name)`, `inviteMember(groupId, ref)`) — not as an SDK call or HTTP endpoint.
- [ ] CHK002 Capability signatures use **domain types only** — no Gemini/OpenAI/Claude/MCP types appear in any signature reachable from domain.
- [ ] CHK003 Each capability has a one-line natural-language description that an AI could ground on (in the spec, not in code comments).
- [ ] CHK004 Capabilities are listed in a registry (or planned to be) — not scattered as ad-hoc functions on disparate ViewModels.

## Affordance contract

- [ ] CHK005 The spec declares what data each capability **reads** vs what it **writes**.
- [ ] CHK006 The spec declares whether each capability is **idempotent**, **reversible**, or **irreversible** (AI agents need this to plan).
- [ ] CHK007 Confirmation requirements are explicit for irreversible actions (e.g., "delete account" requires user confirmation independent of AI invocation).
- [ ] CHK008 Rate / quota constraints (if any) are declared as part of the capability contract.

## PII / privacy boundary

- [ ] CHK009 No capability returns raw PII (phone numbers, full names, photo blobs) by default — opaque local handles are returned, resolution happens at the UI layer.
- [ ] CHK010 If a capability MUST return PII (e.g., contact picker), the spec declares which provider is allowed to see it.
- [ ] CHK011 Default for the MVP: **no PII leaves the device** — capabilities are local-only unless explicitly marked otherwise.
- [ ] CHK012 No capability silently logs to telemetry an LLM prompt or its response containing user content.

## Provider-agnosticism

- [ ] CHK013 No spec wording assumes a specific provider ("Gemini will…", "Claude does…") — provider is named only as an example in non-normative text.
- [ ] CHK014 No package-level dependency on Gemini Nano, OpenAI SDK, Anthropic SDK, MCP server in this spec's task list (unless the spec **is** the provider adapter).
- [ ] CHK015 If on-device inference (Gemini Nano, ML Kit) is mentioned, it is wrapped behind the same port as cloud inference — call sites do not branch on provider.

## Out-of-scope discipline

- [ ] CHK016 Spec explicitly states "no provider implementation in this spec" if no AI is shipping.
- [ ] CHK017 Spec does NOT design prompts, system messages, function-call schemas, or token budgets — that belongs in a provider adapter spec, not the capability spec.
- [ ] CHK018 Spec does NOT ship a "demo AI integration" that ties the capability shape to one provider.

## Acceptance evidence

- [ ] CHK019 Spec includes a sample capability call signature in the Local Test Path section, verifiable via fake adapter.
- [ ] CHK020 Spec lists the capability in the AI Affordance section of the template — or explicitly says `no AI affordance — [reason]`.

---

## How to apply

1. Find every user-facing action in the spec.
2. For each: ask "could a future AI plausibly want to invoke this?" — if yes, walk it through CHK001-CHK020.
3. If the spec ships an AI provider adapter, also run `checklist-domain-isolation` and `checklist-security`.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-ai-readiness: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/ai-readiness.md`. Scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.
