---
name: checklist-capability-registry-readiness
description: Refuses specs that introduce a new action / intent / external-callable surface without an inline `// TODO(capability-registry)` comment, and rejects any mention of concrete MCP / AI providers (Google Assistant, App Actions, Gemini, OpenAI, Claude, MCP server) inside domain or S-specs. Keeps F-2 (Capability Registry Foundation) deferred to end of Phase 2 while preserving the sewing points. Per roadmap reorder 2026-06-15 and CLAUDE.md rule 4.
---

# Checklist: capability-registry-readiness

Per roadmap reorder 2026-06-15, **F-2 (Capability Registry Foundation) is deferred to the last step of Phase 2**. Until then, every S-spec that introduces a new action or external-callable surface must leave behind a **sewing point** so F-2 can collect them later — without prematurely building the abstraction (CLAUDE.md rule 4, Minimum Viable Architecture).

This skill complements `checklist-ai-readiness` (which covers the architectural posture) by enforcing the **specific bookkeeping** that makes F-2 a collection task instead of an archaeology task.

---

## Sewing-point bookkeeping

- [ ] CHK-CR-001 For every new **action** (call, send, open, trigger, navigate, emergency, share, etc.) introduced by the spec, the spec body OR the planned code locations declare an inline TODO:
  ```kotlin
  // TODO(capability-registry): объявить capability declaration для <action_name>
  //   при сборке F-2 — intent, voicePhrases (Map<Locale, List<String>>),
  //   params, idempotent flag, requiresConfirmation, auth scope.
  ```
- [ ] CHK-CR-002 TODO location is at the **dispatcher / provider** site (where the action is handed off), NOT scattered across UI callers.
- [ ] CHK-CR-003 The action's **intent name** is stable (slug-cased, e.g. `call_contact`, `trigger_emergency`) — F-2 will use it verbatim as the capability key.
- [ ] CHK-CR-004 Action params are typed (`Phone`, `ContactId`, `AppPackage`, etc.) — NOT raw `String` / `Map<String, Any>`, so F-2 can derive param schemas mechanically.

## Provider neutrality (refuse signals)

- [ ] CHK-CR-005 Spec does NOT name a specific AI/voice/MCP provider in domain or feature copy:
  - ❌ "Google Assistant App Actions",
  - ❌ "Gemini Nano",
  - ❌ "OpenAI function call",
  - ❌ "Claude tool use",
  - ❌ "MCP server endpoint",
  - ❌ "iOS Shortcut",
  - ❌ "Alexa skill".
  - ✅ If exposure to AI is mentioned at all, phrase it as "future `ExposureAdapter` implementation" (abstract).
- [ ] CHK-CR-006 Spec does NOT import any AI/voice SDK in the dependency list.
- [ ] CHK-CR-007 Spec does NOT define an exposure adapter implementation — only the inline TODO. Real adapter implementations are **separate spec'и after F-2**.

## Voice / conversational surface

- [ ] CHK-CR-008 If the action is plausibly voice-triggerable ("call grandma", "send help"), the TODO additionally notes: `voicePhrases must be localised — supply per Locale`.
- [ ] CHK-CR-009 Confirmation requirement is declared explicitly in the spec (`requiresConfirmation: true` for destructive / irreversible actions like `trigger_emergency`, `delete_contact`).

## Auth / scope hints

- [ ] CHK-CR-010 The TODO includes the **auth scope** the action will need: `device-local` / `admin-only` / `pair-authorised` / `caregiver-allowed`. F-2 wires this into `auth` field.
- [ ] CHK-CR-011 Idempotency is declared (`idempotent: true/false`). Calling, messaging, navigation = idempotent. Payment, deletion, SOS = non-idempotent.

## F-2 collection readiness

- [ ] CHK-CR-012 The spec adds an entry (or extends one) in `docs/dev/capability-registry-pending.md` listing every new action it introduces, with a 1-line description. F-2 reads this index to enumerate work.

---

## When to refuse

Refuse the spec if **any** of:

1. A new action is added but no `TODO(capability-registry)` appears.
2. A specific AI/MCP provider is mentioned in domain code or spec scope.
3. An exposure adapter implementation is included in the spec (only the TODO + the action itself ship now).
4. Param types are untyped (`Map<String, Any>` / raw strings for structured data).

Propose corrected shape, then continue.

---

## How to apply

1. Scan the spec for action verbs: call, send, message, open, navigate, trigger, share, delete, pay, scan, record.
2. For each found, verify the inline TODO + neutral phrasing.
3. Verify the `capability-registry-pending.md` index entry.
4. Output a list of refused gates + corrected shapes.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-capability-registry-readiness: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/capability-registry-readiness.md`. Scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.

## Relationship to other skills

- **`checklist-ai-readiness`** — covers the **architectural posture** ("AI-ready, not AI-built", no provider commitment, domain-isolated ports). Always-on for any AI-touching spec.
- **`checklist-capability-registry-readiness`** (this skill) — covers the **bookkeeping** that makes the deferred F-2 collection trivial: inline TODOs, the pending-index, stable intent names, typed params. Triggered on any spec adding an action.
