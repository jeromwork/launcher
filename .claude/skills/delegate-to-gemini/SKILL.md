---
name: delegate-to-gemini
description: Offload read-heavy / context-heavy work to the local Gemini CLI (`gemini -p "..."`) instead of spending Claude's own context on it — web research, codebase search ("where is X defined / used", "how does subsystem Y work"), digesting huge logs / build output / generated files, indexing docs, first-pass RU↔EN translation, proofreading, second-opinion critique of a plan, bulk fixture/data extraction. Gemini reads a lot in ITS own context and returns a compact answer; Claude keeps its context clean. Invoke whenever a task means "read many files/pages and return a short conclusion", or on the words исследуй / research / найди в интернете / где определён / где используется / просканируй / summarize / переведи / второе мнение / раскритикуй / большой лог / gemini / делегируй. NOT for writing production code, architecture decisions, or anything that must apply this repo's conventions — those stay in Claude. This skill also documents setup on a fresh machine and how failures (not installed / not logged in / offline / rate-limited) are surfaced to the owner.
---

# Skill: delegate-to-gemini

**Division of labor:** Gemini CLI is the cheap **bulk reader / researcher**. Claude is the **writer / decider / reviewer**. Anything that means *read a lot → return a little* goes to Gemini, in Gemini's own context, so Claude's context stays clean and cheap. Everything that means *apply our conventions, decide, or write final output* stays in Claude.

> **Gemini's output is always UNVERIFIED INPUT, never authority.** Per this repo's stance (critical-mentor, verify against primary sources), a weak model drifts to defaults. Every Gemini result is spot-checked before it is acted on or quoted. Gemini is never cited as the source in a spec, Decision block, or arch-pack.

## When this fires

Read-heavy / context-heavy work: web research; codebase search ("where is X defined / used", "how does Y work across N files"); digesting a huge log / build output / generated file; indexing a directory (`docs/architecture/*`); first-pass RU↔EN translation or Russian proofreading; a cheap independent second opinion on a plan; bulk extraction of structured data from unstructured text. Trigger words: исследуй / research / найди в интернете / где определён / где используется / просканируй / summarize / переведи / второе мнение / раскритикуй / большой лог / gemini / делегируй.

## Step 0 — Preflight (ALWAYS run before delegating)

Probe availability first. On failure, do NOT silently fall back to burning Claude's own context — surface it to the owner (see "Failure reporting").

```bash
gemini --version    # installed?  (command-not-found ⇒ not installed)
```

If `--version` succeeds, the first real `gemini -p` call also tests **auth + network** — a failure there means logged-out / offline / rate-limited, reported the same way.

> First time on a machine, confirm exact flags with `gemini --help` — this skill uses the core `gemini -p "<prompt>"` non-interactive form; other flags (`-m` model, `@file` include, stdin pipe) are verified live, not assumed.

## Step 1 — Pick the delegation pattern

Run `gemini` **inside the project directory** so it has file access (its own read/grep/glob tools + ~1M-token context). Always tell it **"do not modify any files, read only"** and **"return file:line + one-line role, be terse"**.

### A. Codebase search & comprehension (file search)
- Where is X? → `gemini -p "Find where 'X' is defined and every place it's used in this repo. Return file:line + one-line role each. Read only, do not modify."`
- How does subsystem Y work? → `gemini -p "Explain how <Y> works across the codebase: entry points, main types, data flow. Cite file:line. Terse. Read only."`
- Map a directory → `gemini -p "Index docs/architecture/*.md: one line per file — what it covers. Read only."`
- Dead-code / unused-export heuristic, semantic diff of two files/dirs → same shape (I verify hits against the real files).

### B. Logs & build output
- `gemini -p "From this build log, list only the real errors, clustered by root cause. Ignore noise." < build.log`
- Summarize a long `git log` / large diff into themes.

### C. Web research
- `gemini -p "Research <question>. Give a compact answer WITH source URLs. Flag anything uncertain."` (Gemini has Google-Search grounding — cheaper than Claude WebFetch pulling full pages.)

### D. Translation & text (fits the repo's language-by-audience rule)
- First-pass RU↔EN of spec strings (pairs with `procedure-translate-spec-strings`) — I review the result.
- Proofread an owner-facing Russian passage; summarize a long spec / backlog task for quick reload.

### E. Second opinion / adversarial check
- `gemini -p "Critique this plan as an independent reviewer. What's wrong, risky, or missing? <plan>"` — cheap divergent perspective before Claude commits. Fits the repo's critical-mentor culture.

### F. Bulk mechanical
- Generate test fixtures / sample data; extract structured data from unstructured text into a table.

## Step 2 — Verify (MANDATORY, never skip)

- **Code/factual claims:** Read the specific `file:line` Gemini cited and confirm before acting. If it can't be confirmed, treat the claim as false.
- **Research:** prefer a primary source (RFC / spec / real code) over Gemini's paraphrase — per `procedure-architecture-sourcing`, research wins on conflict, and Gemini is not a primary source.
- **Anything touching crypto / wire-format / domain-isolation / a Decision:** Gemini is input only. The authority is the arch-pack + primary sources, never Gemini.

## Privacy guard (HARD)

Gemini CLI sends whatever you feed it to Google. **Never** pipe/`@`-include: `.env`, private keys, `google-services.json`, service-account JSON, JWT/API tokens, user PII, or any secret. (Matches the repo secret-handling rule.) Source files for search/comprehension are acceptable because the owner installed Gemini deliberately — secrets are not.

## When NOT to delegate (stays in Claude)

Writing production code; architecture / one-way-door decisions; applying this repo's conventions (rules 1–14, wire-format, backlog format); final owner-facing decisions; anything where a wrong-but-plausible answer is expensive to catch. Gemini can *gather* for these, but Claude *writes and decides*.

## Failure reporting (make it visible to the owner)

On ANY Gemini failure, emit ONE Russian status line to the owner and STOP — do not silently spend Claude context on the web instead:

```
⚠️ Gemini недоступен (<причина>). Варианты:
  [1] Исправить: <точная команда>
  [2] Сделать через Claude WebFetch/чтение файлов (дороже по контексту) — спрошу подтверждение
  [3] Пропустить
```

Cause → line to show:
- command-not-found → `не установлен на этой машине` → fix: `npm install -g @google/gemini-cli`
- auth/login error → `не задан API-ключ` → fix: get a free key at aistudio.google.com, set `GEMINI_API_KEY` (env var or `~/.gemini/.env`). NOTE: Google disabled the free OAuth "Login with Google" for individuals (migrate-to-Antigravity) — do NOT use it; API key is the path.
- network/timeout → `нет связи с API (сеть/прокси)`
- quota/429 → `превышен лимит запросов, подождать`

Never auto-fall-back to Claude's own web tools — the whole point is to not spend that context. `WebFetch`/`WebSearch` are set to **ask** in `.claude/settings.json`, so option [2] always surfaces to the owner.

## Setup on a new machine (laptop, home desktop, …)

The **skill and permissions travel via git** (`.claude/skills/delegate-to-gemini/` + `.claude/settings.json` are committed). On a fresh machine only two things are per-machine:

1. `git pull` — brings this skill + the `Bash(gemini:*)` permission + `WebFetch/WebSearch → ask`.
2. `npm install -g @google/gemini-cli` — the binary (needs Node 18+).
3. Set a Gemini API key. The free OAuth "Login with Google" for individuals is **disabled by Google** (migrate-to-Antigravity), so use an API key: get a free one at aistudio.google.com, then either `setx GEMINI_API_KEY "<key>"` (Windows user env var, new shells pick it up) or put `GEMINI_API_KEY=<key>` in `C:\Users\<you>\.gemini\.env`. The key is a **per-machine secret — never commit it, never paste it in chat** (repo secret-handling rule). The same key can be reused on every machine, but it is always set locally, never in git.
4. **Auth-type gotcha:** a prior "Login with Google" attempt leaves `~/.gemini/settings.json` with `security.auth.selectedType: "oauth-personal"`, and that path is dead (`IneligibleTierError`) — it overrides the API key. Set it to `"gemini-api-key"` explicitly:
   ```json
   { "security": { "auth": { "selectedType": "gemini-api-key" } } }
   ```
5. Verify: `gemini --version` then `gemini -m gemini-2.5-flash -p "reply OK"`.

That's it — the skill + permissions come from git; only the API key + `selectedType` are per-machine.

**Model note:** default to `-m gemini-2.5-flash` — lighter load, faster, ideal for bulk reading; `gemini-2.5-pro` often returns transient `503 "high demand"`. A 503 is a temporary Google-side spike (not our config) — retry later, do not treat it as a setup failure.

## Related
- `procedure-translate-spec-strings` — Gemini can do the first-pass, this skill governs it.
- `procedure-architecture-sourcing` — research from primary sources; Gemini sweeps, Claude verifies.
- `mentor` — discussion mode; Gemini can supply a cheap second opinion inside it.
