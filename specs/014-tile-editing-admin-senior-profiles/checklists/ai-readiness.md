# AI Readiness — spec 014

Generated: 2026-05-29.

Capability Registry pattern (F-2 dependency). Provider-agnostic.

## Exposable capabilities in F-014

Per §AI Affordance section:
1. `suggestTileAddition(targetId, context)` — read-only suggestion.
2. `summarizeLayoutChanges(targetId, since)` — read-only audit log.
3. `findUnusedTiles(targetId)` — read-only analytics.

## Provider-agnostic shape

- [x] **CHK001** Capability signatures use domain types (TileEditOperation, ConfigDocument, TargetIdentity) — explicit §AI Affordance "Никаких Gemini / OpenAI / Claude типов в signatures. Rule 1 (domain isolation)."
- [x] **CHK002** No vendor SDK leakage in capability signatures. PASS.
- [x] **CHK003** Domain verbs declared as ports (capability registry pattern emerging through F-014 + F-2).

## No commitment to specific LLM / MCP

- [x] **CHK004** F-014 не ships AI provider. §AI Affordance explicit "Out of scope: реальная AI provider implementation, MCP server expose, voice triggers, LLM prompt design. Всё это ship'ится в F-2".
- [x] **CHK005** No bundled prompts / templates / model-specific config. PASS.

## Read-only vs mutating boundary

- [x] **CHK006** §AI Affordance explicit: "Никаких mutating operations (`addSlot`, `removeSlot`) не могут быть инициированы AI agent'ом autonomously без user confirmation". PASS.
- [x] **CHK007** Suggestion capabilities — read-only. PASS.
- [x] **CHK008** Mutations require explicit user UI confirmation (FR-010 flows). Aligned constitution Article XV (AI dignity / user control).

## PII exfiltration risk

- [⚠️] **CHK009** `suggestTileAddition` could receive contact tile data as suggestion context. **MUST NOT** leak contact phone numbers / names to AI provider in prompts. **Policy**: any AI integration MUST sanitize through domain-side anonymization. F-2 responsibility, но F-014 must not design capability that **requires** PII for suggestion to work.
  - "Suggest adding contact" — needs to know which contact. Solution: pass opaque contact ID, AI returns "add contact <id>", presentation resolves to display.
- [⚠️] **CHK010** `summarizeLayoutChanges` could leak labels and contact references. Same anonymization rule.
- [x] **CHK011** `findUnusedTiles` — local analytics, no PII export needed.

## Capability Registry exposure (F-2 dependency)

- [x] **CHK012** F-014 exposes capabilities as **declarations**, not as direct registered MCP tools. F-2 will pick up declarations and expose. PASS.
- [x] **CHK013** Capability shape (verb + args + return type) — fits Capability Registry pattern (F-2). Aligned.

## User consent / authorization

- [⚠️] **CHK014** When AI agent calls `suggestTileAddition`, who triggers? Voice query? Cloud-side cron? **Not specified в F-014** (out of scope). F-2 will specify auth model. F-014 just declares capability shape.

## Auditing / accountability

- [⚠️] **CHK015** AI-initiated actions should be auditable. Per §AI Affordance, no mutating actions from AI directly. **Logging strategy** for read-only suggestions accessed by AI — F-2 responsibility.

## Constitution Article XV (AI dignity)

- [x] **CHK016** User remains the decider:
  - AI suggests "add contact Маша" → user confirms or dismisses.
  - AI does not autonomously modify ConfigDocument.
  - User can opt out (privacy mode flag — FR-003g (c) skip server backup).

## Open items

1. **CHK009-CHK010**: PII anonymization policy для AI access — F-2 responsibility. Document constraint in F-014.
2. **CHK014**: Auth model для AI invocation — F-2.
3. **CHK015**: Auditing strategy для AI-accessed capabilities — F-2.

**Verdict**: PASS. F-014 declares AI capabilities **in domain-agnostic shape**, defers actual AI integration to F-2 (Capability Registry Foundation). Read-only boundary explicit. PII concerns flagged for F-2 spec author. No premature commit to provider / MCP server / model.
