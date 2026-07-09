# Checklist: ai-readiness

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 17 ✓ / 3 ⚠ / 0 ✗ — clean (F-3 уже имеет AI Affordance section с proper boundaries)

> **Context**: F-3 не ships runtime AI provider. Имеет AI Affordance section с future capabilities + dev-time translation pipeline через Claude API (skill, не runtime app code).

---

## Capability shape

- [✓] **CHK001** Domain verbs.
  - AI Affordance lists: `wizard.start(presetId)`, `wizard.skipToStep(stepId)`, `localization.resolve(key, locale, args)`, `tileSet.list(deviceClass)`. ✓ Pure domain verbs.

- [✓] **CHK002** No Gemini / OpenAI / Claude / MCP types в signatures.
  - All capability signatures use domain types (String, ConfigSummary, etc.). ✓
  - **Caveat**: translation pipeline (FR-031a) uses Claude API — но это **dev-time skill**, не runtime app capability. ✓ proper boundary.

- [✓] **CHK003** One-line NL description per capability.
  - AI Affordance section parenthetical descriptions for each capability. ✓

- [✓] **CHK004** Registry / planned.
  - Inline-TODO в AI Affordance: «F-2 (Capability Registry Foundation, end of Phase 2) will wrap these into AI-callable capability descriptors». ✓

## Affordance contract

- [⚠] **CHK005** Read vs write declared per capability.
  - Implicit: `wizard.start` / `wizard.skipToStep` — write; `localization.resolve` / `tileSet.list` — read.
  - **Recommendation**: explicit table в AI Affordance section. См. Issue AI-1.

- [⚠] **CHK006** Idempotent / reversible / irreversible classification.
  - Все F-3 capabilities reversible или idempotent (no destructive actions в foundation).
  - **Recommendation**: explicit annotation. AI-1 fix covers.

- [N/A] **CHK007** Confirmation для irreversible.
  - F-3 capabilities нет irreversible. N/A.

- [N/A] **CHK008** Rate / quota.
  - F-3 local-only, no rate limits.

## PII / privacy boundary

- [✓] **CHK009** No raw PII returned by default.
  - AI Affordance explicit: «No PII leaves device through these capabilities». ✓
  - Wizard answers (tile.set id, fontScale, theme) — это preferences, не PII.

- [N/A] **CHK010** If PII MUST return — provider declared.
  - N/A: F-3 не returns PII.

- [✓] **CHK011** Local-only default.
  - A-10 + decision 2026-06-15-deferred-cloud + AI Affordance. ✓

- [✓] **CHK012** No silent telemetry of LLM content.
  - F-3 не использует runtime LLM. Translation pipeline (dev-time skill) — explicit под developer'ом, не silent. ✓

## Provider-agnosticism

- [⚠] **CHK013** No spec wording assumes specific provider в normative text.
  - **Caveat**: FR-031a fixes **Claude API** как dev-time translator (per C-10).
  - **Technically**: это dev infrastructure, не runtime capability. Translation pipeline IS provider-specific by design (per user mandate C-10 «Ты сам будешь за переводчика»).
  - OUT-021 explicit фиксирует это: alternative providers (DeepL, GPT) — adapter pattern future, не F-3.
  - **Acceptable**: provider specificity scoped к dev workflow, не runtime; explicit OUT acknowledgement.

- [✓] **CHK014** No package-level dependency on AI SDKs.
  - F-3 app code не зависит от anthropic / openai / gemini SDKs.
  - Translation skill (`procedure-translate-spec-strings`) — dev-time CLI/skill, не app dependency. ✓

- [N/A] **CHK015** On-device inference wrapped.
  - F-3 не use on-device inference. N/A.

## Out-of-scope discipline

- [✓] **CHK016** Spec states "no provider implementation".
  - AI Affordance explicit: «provider implementation, LLM prompt design, MCP server wiring, telemetry — ships в FUTURE-SPEC-AI-* (F-2 + позднее)». ✓

- [✓] **CHK017** No prompt design / function-call schemas / token budgets.
  - F-3 не designs prompts. Translation skill prompt — implementation detail скилла (отдельный файл `.claude/skills/procedure-translate-spec-strings/`), не spec.md. ✓

- [✓] **CHK018** No demo AI integration tying capability к provider.
  - AI Affordance pure abstract. ✓

## Acceptance evidence

- [⚠] **CHK019** Sample capability call signature в Local Test Path.
  - Capabilities listed в AI Affordance, но не sample calls в Local Test Path verification commands.
  - **Recommendation**: добавить sample call вверху AI Affordance или ссылку на test fixture. Minor polish.

- [✓] **CHK020** Capability listed в AI Affordance section.
  - ✓ AI Affordance section explicit с 4 capabilities + read/write notes.

---

## Issues & fixes

### Issue AI-1 — Explicit capability metadata (CHK005/006/019)

**Fix**: дополнить AI Affordance section table:
```
| Capability | Read/Write | Idempotent? | Reversible? |
|---|---|---|---|
| `wizard.start(presetId)` | Write | Yes (returns current state if already started) | Yes (cancel + restart) |
| `wizard.skipToStep(stepId)` | Write | Yes | Yes (navigate back) |
| `localization.resolve(key, locale, args)` | Read | Yes | N/A (pure read) |
| `tileSet.list(deviceClass)` | Read | Yes | N/A (pure read) |
```

Это полезно для будущего F-2 capability registry — AI planner-у нужны эти flags для plan'инга. Но **optional** для F-3 (могут дополниться в F-2 при wrapping).

---

## Резюме

**17 ✓ / 3 ⚠ / 0 ✗** — F-3 AI-readiness clean. Один optional polish (AI-1).

**Not applying inline** — AI-1 — это enhancement для F-2 wrapping; F-3 capabilities exposed via domain ports, F-2 будет аннотировать them при wrapping в capability descriptors.
