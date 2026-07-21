---
name: visual-explainer
description: Turn a hard or jargon-heavy topic into a self-contained visual explainer page (an Artifact) that a non-technical reader can absorb and re-read. Invoke when someone asks to "explain X from scratch / simply / for a beginner", "collect everything about X into one place", "make me a page / reference / cheat-sheet", or when a chat answer would run long and would land better as a laid-out page with real examples, before/after, and diagrams than as a wall of text. Sister to `mentor` (which talks a decision through in chat); this one produces a durable page to keep and share. Portable across projects — no project-specific dependency.
---

# Skill: visual-explainer — long explanations as a page, not a wall of chat text

The output is a **published Artifact** (a real HTML page on claude.ai), not a chat message. Use it when the value is in re-reading: onboarding a non-technical owner, a from-scratch concept explainer, a decision write-up, a variant comparison, a task briefing.

## When this fires
- "explain X from scratch / simply / for a beginner / no jargon"
- "collect everything about X in one place", "make me a page / reference / справку"
- a chat answer that would be long AND benefits from layout: real examples, before/after, side-by-side worlds, step-by-step build-up, small diagrams
- comparing options where the reader needs to see them next to each other

## When NOT to fire
- A short factual answer — just answer in chat.
- An active back-and-forth decision — that is `mentor` (chat), not a page. (You may finish a mentor thread by producing an explainer page of the conclusion.)
- Something that impersonates a real person/org, or fabricated records — never publish those (Artifact rules).

## The load-bearing rules (violating these makes it generic and untrustworthy)

1. **Real examples only. Never invent.** Pull actual values from the codebase/data — a real JSON fixture, a real config, a real error. Grep for them first. Fabricated examples are the fastest way to mislead a non-technical reader who cannot tell they are fake. Label where each example came from.

2. **Build from zero.** The reader said "beginner". Start before the first piece of jargon. Every term gets one plain sentence the first time it appears — "what it is" + "why it matters here". No assumed knowledge.

3. **Language of the audience, not the system.** For a non-developer owner, write simple everyday language and name things by what the person recognizes, not how the code is built. Match the owner's language (e.g. Russian here) for owner-facing pages; keep code identifiers as-is. This is the single biggest difference between a page that lands and one that doesn't.

4. **Sequence that teaches.** Order the sections as a genuine build-up: the problem first (why this exists at all) → the mechanism → the special cases → the actual question being decided. Number sections only because the concepts genuinely build on each other, not for decoration.

5. **Show the thing, then explain it.** Put the real example (JSON, code, diff) on the page, then unpack it in prose beside it. Highlight the two or three bytes that matter.

## Procedure

1. **Pin the subject and the reader.** One concrete topic, one audience (usually the non-technical owner), one job the page must do ("after reading this they understand X"). If unclear, ask one short question — don't guess the audience's level.

2. **Gather real material.** Grep/read the code for the actual examples you will show — fixtures, sample documents, real field names, a real failing test's output. Collect 2–5 concrete artifacts. This step is not optional; it is what makes the page trustworthy.

3. **Load `artifact-design`.** Call the built-in `artifact-design` skill to calibrate the visual treatment before writing HTML. A from-scratch explainer is usually a **utilitarian-but-polished** reference, not a flashy landing page: real typographic hierarchy, considered spacing, restrained palette. Ground the visual language in the subject's own world where you can (this repo's explainer used an "envelope / wire format" motif with monospace as the technical voice).

4. **Write the page.** Follow the load-bearing rules. Structure that works well:
   - Header: plain-language title + one-sentence "what this is / who it's for".
   - Numbered sections, each: eyebrow label → heading → prose → the real example → a short "why it matters" aside or callout.
   - Side-by-side cards when contrasting two worlds/options.
   - Small CSS/HTML diagrams for flows (boxes + arrows), not heavy hand-drawn SVG.
   - A closing "so what's actually being decided / what to do next" section.
   - Theme-aware (light+dark), responsive, a favicon, a real `<title>`.

5. **Publish with `Artifact`.** Write the HTML to a file (scratchpad or a docs location), then call `Artifact`. Pass a one-sentence `description`. The page is private until the owner shares it.

6. **In chat, hand it over briefly.** Link the page, list the sections in one line each, and state the one decision or next step you're waiting on. Do not restate the whole page in chat — the page is the artifact.

## Sharing this skill to another project
This skill is self-contained: it depends only on the built-in `artifact-design` skill and the `Artifact` tool, both available in any project. To reuse it elsewhere, copy the folder `.claude/skills/visual-explainer/` into that project's `.claude/skills/`. Nothing here is launcher-specific — adjust only the audience language if the other project's owner speaks a different one.
