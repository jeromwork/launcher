# ADR-011: AI-owner collaboration conventions — sequences, token discipline, language by audience

**Status**: Accepted (2026-06-28)
**Date**: 2026-06-28
**Decided in**: Workflow discussion 2026-06-28 — owner pain points (Android-novice, mental load, token cost) and research synthesis on dual-audience documentation + sequence-catalog practice.
**Linked artifacts**:
- [`CLAUDE.md`](../../CLAUDE.md) §«Output discipline», §«Sequences in spec.md»
- Memory: `feedback_language_by_audience.md`
- Research sources listed at the end.

---

## Context

### Problem

The product owner has three pain points that shape our workflow:

1. **Doesn't know Android, doesn't read code.** Architectural mistakes surface only when rework is expensive. Sequence diagrams as requirements are the only language where the owner can verify behavior without touching code.
2. **Has to keep the whole project in his head.** Specs keep re-describing the same flows (e.g. QR-pairing in spec/007 is reused in spec/011 and in planned spec on calls/multi-admin). Without explicit reuse markers the owner loses the thread.
3. **Token cost.** Each AI read of a `spec.md` drags in mentor-style explanations (only useful to the owner). Each "what I changed" chat report contains a diff (which the owner doesn't read). Each checklist run dumps a full table. Over a long session this is tens of thousands of wasted tokens.

### What the first draft proposed (and what was wrong with it)

The first plan was a separate `docs/sequences/` catalog with **two files per sequence**: `SEQ-NN.md` (compact, AI-only) plus `SEQ-NN.explained.md` (mentor-style, owner-only). An `INDEX.md` would carry two links per row.

Research (sources below) rejected the plan for two reasons:

1. **Two files per sequence is a documented anti-pattern** — "continuous context decay" / "AI docs are lying to you". One copy stays current, the other rots, the agent reads stale and "confidently wrong". Mintlify, the AGENTS.md ecosystem, JetBrains, and Aider all recommend a single source of truth.
2. **A numbered `docs/sequences/` catalog as a first-class artifact is premature abstraction.** Searching for public examples (Microsoft Engineering Playbook, Architect View Master, docsie 2026 survey) showed the dominant pattern is **inline in spec.md / ADR**. A catalog pays off only with cross-spec reuse plus external readers. For a solo developer with one PO, the costs (broken refs, sync ceremony, an extra layer) land immediately while the benefits never materialize. This is a direct violation of our own rule 4 (Minimum Viable Architecture).

### Constraints

- The convention must not require migrating existing specs (Phase 1-2 specs 001-020).
- It must work identically for Claude 4.7 (smart implementer, sometimes), Gemini in Antigravity (default implementer), and future agents.
- Section markers must compose with the existing repo convention (`<!-- AC:BEGIN -->`, `<!-- SECTION:VERIFICATION_PENDING:BEGIN -->`).

### Alternatives considered and rejected

| Alternative | Why rejected |
|---|---|
| Two files per sequence (`.md` + `.explained.md`) | Documented anti-pattern (context decay). DataHub, Mintlify, Upsun recommend single source of truth. |
| Build full `docs/sequences/` catalog upfront | Premature abstraction (rule 4). No public examples of numbered SEQ-catalogs as living artifacts. Cost > benefit for solo dev. |
| Single layered diagram (Agile Modeling: actor → UI → controller → domain) | Mixes the owner's spec-view with the implementer's plan-view. Owner finds it harder to see "his" lifelines. |
| Auto-generated sequences from code (AppMap, tracers) | Only captures implementation flow, not spec-level intent. A novice owner can't read generated artifacts without curation. |
| Inline without MENTOR-DETAIL marker | Every AI read of spec.md drags in mentor prose — pure token waste. Need a skip mechanism. |
| Translate everything (specs, ADRs, plans) to English | Owner can't verify English specs and backlog descriptions — breaks his only verification path. |
| Translate everything to Russian | English is ~30% denser for AI consumption; Russian in AI-only files wastes tokens with no offsetting value. |

---

## Decision

Six linked conventions, all recorded in `CLAUDE.md`.

### 1. Sequences live inline in spec.md

Each spec.md gets a `## Sequences` section with anchor IDs:

```markdown
## Sequences

### SEQ-1: Cold launch (first run)

Pre: first launch after install. Post: user lands on home screen.
Used-in: spec/008-first-run.

#### Spec-level (behavior)
\`\`\`mermaid
sequenceDiagram
  participant U as Owner
  participant S as System
  ...
\`\`\`

#### Plan-level (architecture)
\`\`\`mermaid
sequenceDiagram
  participant UI as MainActivity
  participant VM as MainViewModel
  participant UC as LoadFirstRunStateUseCase
  ...
\`\`\`

<!-- MENTOR-DETAIL:BEGIN -->
#### Пояснение для владельца
- **MainViewModel** — посредник между экраном и логикой. Хранит состояние экрана между поворотами.
- На шаге 3 `UseCase` спрашивает «это первый запуск?» — отвечает Repository, потому что флаг лежит в локальном хранилище.
- Ветка `else` срабатывает если хранилище повреждено (редкое, но возможное).
<!-- MENTOR-DETAIL:END -->
```

The MENTOR-DETAIL block is in Russian (owner-facing). Everything else in spec.md follows convention #6 below.

### 2. Dual projection (spec-level + plan-level lifelines)

Each sequence carries **two Mermaid diagrams**:

- **Spec-level lifelines** = `Owner / System / External (API, FCM)`. Describes behavior. Owner's zone.
- **Plan-level lifelines** = `UI / ViewModel / UseCase / Repository / Adapter` (from `architecture.md`). Describes architecture. Implementer's zone. Arrows only point downward — rule 1 (domain isolation) is verifiable visually.

This is **our convention**, not an industry pattern. We accept ~10 extra Mermaid lines per sequence as the price of separating responsibilities.

### 3. MENTOR-DETAIL marker (token discipline inside sequences)

Owner-facing prose is wrapped in:

```
<!-- MENTOR-DETAIL:BEGIN -->
...
<!-- MENTOR-DETAIL:END -->
```

**Convention for AI agents:**

- **Default: skip** the body of `MENTOR-DETAIL` blocks when reading spec.md. Pre/Post + Spec-level Mermaid + Plan-level Mermaid is enough to implement.
- **Read the block only when:** (a) owner explicitly asks for an explanation / mentor mode; (b) AI session onboards onto the spec for the first time and needs owner-intent context; (c) production documentation is being generated.
- When writing a sequence, the AI **must** fill in the MENTOR-DETAIL block (mentor-style, plain Russian) — it is part of the deliverable, not optional.

### 4. Reactive extraction into `docs/sequences/`

The `docs/sequences/` catalog is created **only when** a sequence is genuinely used in **2+ specs**.

Trigger:
1. A sequence from spec/A is needed in spec/B.
2. Extract into `docs/sequences/SEQ-N-slug.md` (same structure: Pre/Post + Spec-level + Plan-level + MENTOR-DETAIL).
3. In both specs replace the inline block with a link: `→ [SEQ-N](../../docs/sequences/SEQ-N-slug.md)`.

Until the trigger fires there is no catalog and no `INDEX.md`. `INDEX.md` is created when the directory holds **≥5 files**.

**Standing candidate for first extraction**: the QR-pairing flow (spec/007, reused by spec/011 and by planned specs on calls / multi-admin). Extract at the next touch of spec/011 or a related new spec — not preemptively.

### 5. Output discipline — token economy for the chat

**Checklists in chat — red-only summary.**

The AI emits one line per checklist:
```
checklist-domain-isolation: 14/16 ✓, FAIL: CHK-7 (vendor SDK in port signature), CHK-12 (transport type in domain DTO)
```

The full table stays in `specs/<NNN>/checklists/<name>.md`. Backlog AC holds counts via `[auto:checklist]`. Context is not lost — the owner can open the file at any time.

**Change reports — `file:line` markdown links, not diffs.**

The AI emits a list:
```
- [MainViewModel.kt:42](app/src/main/.../MainViewModel.kt#L42) — added validation for empty name
- [PairingRepository.kt:88-95](app/src/main/.../PairingRepository.kt#L88-L95) — handle 410 Gone
```

No diff dumps in chat. Owner doesn't read code; when needed he opens the link in VSCode (the IDE extension renders `[name.kt:42](path#L42)` natively).

### 6. Language by audience

Each artifact picks its language by primary audience:

- **English** (owner does NOT read these):
  - `CLAUDE.md` — confirmed 2026-06-28, owner does not read it
  - ADRs (`docs/adr/*.md`)
  - `plan.md`, `tasks.md`, `contracts/`, `research.md`, `data-model.md`, `quickstart.md`, `analyze-report.md` inside specs
  - checklists (`specs/<NNN>/checklists/*.md`)
  - skill `SKILL.md` files
  - code comments, commit bodies, PR descriptions (unless mentor-style requested)
- **Russian** (owner reads these):
  - `spec.md` (requirements source for the owner)
  - backlog task descriptions
  - `vision.md`, `docs/product/use-cases/*`
  - MENTOR-DETAIL blocks inside sequences
  - novice TL;DR summaries appended by `procedure-add-novice-summary`
  - chat replies to the owner

**Why**: token economy. English is ~30% denser than Russian for AI parsing. Russian in AI-only artifacts wastes tokens with no offsetting value (the owner won't read them). Russian where the owner reads is essential — he can't verify English text.

**How to apply**:
- New artifact: pick language by audience. If mixed — go Russian (owner-tilt).
- Existing artifact: do **not** migrate preventively. Apply at next touch.
- If audience for a new file type is unclear — ask once, then save the answer.

---

## Consequences

### Positive

- **Single source of truth per sequence** — no sync problem between `.md` and `.explained.md`.
- **No catalog until real cross-spec reuse exists** — aligns with rule 4 (MVA).
- **MENTOR-DETAIL marker saves tokens** — typical explanation block is 50-150 lines; AI skips them.
- **Red-only checklist summaries save 5-10k tokens per `/speckit.analyze` run.**
- **`file:line` change reports save 1-5k tokens per substantial commit.**
- **Dual projection** gives the owner a spec-view and the implementer a plan-view without a separate architecture brief per sequence.
- **Language-by-audience** is a recurring saving across every AI-only artifact created from now on.

### Negative

- **The "skip MENTOR-DETAIL" rule is not machine-enforced** — depends on agent discipline. Mitigation: rule is explicit in CLAUDE.md; violations are visible (agent quotes mentor prose without being asked).
- **Dual projection costs ~10 extra Mermaid lines per sequence** and doubles maintenance when the flow changes. Accepted trade-off.
- **`SEQ-N` anchor IDs are unique only within one spec.md.** Once a sequence is extracted to the catalog it gets a globally unique ID (`SEQ-N-slug`). Before extraction, spec/A `SEQ-1` and spec/B `SEQ-1` can collide. This is fine — the containing file disambiguates while inline.
- **Mixed-language repository** — readers switching between an English ADR and a Russian spec pay a small cognitive cost. Considered acceptable given the token saving.

### Neutral

- **Existing specs 001-020 are not migrated.** Their sequences stay in their current shape. The new convention applies to new specs and to old ones on next touch.
- **TDD discussion is parked** for a separate mentor session. The risk profile differs between a Gemini implementer and a Claude 4.7 implementer; that conversation deserves its own time.

---

## Exit ramps

**If the convention turns out to be over-engineered:**

- **Nobody writes MENTOR-DETAIL** (AI lazy, owner doesn't ask) → switch to a single `## Explanations` section at the end of each spec.md, no markers. Cost: one CLAUDE.md edit, no migrations (existing markers keep working).
- **Dual projection too expensive** → collapse to a single layered-lifeline diagram (Agile Modeling style). Cost: convention edit + new Mermaid discipline for new specs.
- **Catalog never materializes** (no sequence ever needs cross-spec reuse) → drop the trigger from CLAUDE.md, accept that the whole portfolio is inline. This is a successful rule-4 outcome, not a failure.

**If the convention turns out to be too thin:**

- **Sequences swell spec.md past 500 lines** → force extraction at a line threshold instead of waiting for cross-spec reuse.
- **Agents still drag in MENTOR-DETAIL** → fall back to formal split files (the original draft) and pay the sync cost. This would be a discipline-failure outcome, not a design failure.
- **Mixed-language repo becomes confusing** → pick one language project-wide, accept the token cost. Reversible at any time by re-translation.

---

## How to apply

1. **New specs**: use the convention from CLAUDE.md §«Sequences in spec.md» from day one.
2. **Existing specs**: convention applies on next touch via `/speckit.*`. No preemptive migration.
3. **`speckit-specify` template** will be updated in a follow-up task so new spec.md files start with a `## Sequences` skeleton.
4. **Skill `procedure-add-novice-summary`** continues to work as-is — it appends a TL;DR at the end of an artifact; MENTOR-DETAIL inside sequences is an independent mechanism for in-flow explanations.
5. **Language-by-audience**: applies immediately to any new artifact. ADR-011 itself is the first artifact written under this rule, and `CLAUDE.md` was reclassified as English-only the same day (owner confirmed he does not read it).

---

## Research sources (2026-06-28)

- [Mintlify — Structure documentation for AI and human readers](https://www.mintlify.com/library/structure-documentation-AI-human-readers)
- [Upsun — Why your README matters more than AI configuration files](https://developer.upsun.com/posts/insights/why-your-readme-matters-more-than-ai-configuration-files)
- [DataHub — Continuous Context: Why AI Docs Decay](https://datahub.com/blog/continuous-context/)
- [Techstrong.ai — Why Your AI Docs Are Lying to You](https://techstrong.ai/contributed-content/the-context-management-problem-why-your-ai-docs-are-lying-to-you/)
- [Microsoft agent-skills — Progressive Disclosure Pattern](https://deepwiki.com/microsoft/agent-skills/5.3-progressive-disclosure-pattern)
- [NN/g — Progressive Disclosure](https://www.nngroup.com/articles/progressive-disclosure/)
- [MindStudio — Progressive Disclosure in AI Agents (context rot)](https://www.mindstudio.ai/blog/progressive-disclosure-ai-agents-context-management)
- [JetBrains — Coding Guidelines for AI Agents](https://blog.jetbrains.com/idea/2025/05/coding-guidelines-for-your-ai-agents/)
- [elite-ai-assisted-coding — agent instructions: one file or many](https://elite-ai-assisted-coding.dev/p/agent-instructions-one-file-or-many)
- [Microsoft Engineering Playbook — Sequence Diagrams](https://microsoft.github.io/code-with-engineering-playbook/design/diagram-types/sequence-diagrams/)
- [PlantUML vs Mermaid in 2026](https://mermaideditor.com/blog/mermaid-vs-plantuml-2026)
- [adr.github.io — Architectural Decision Records](https://adr.github.io/)
- [Agile Modeling — UML Sequence Diagrams (layering)](https://agilemodeling.com/artifacts/sequencediagram.htm)
