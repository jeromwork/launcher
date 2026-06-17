---
name: procedure-translate-spec-strings
description: Run at the end of every speckit-tasks invocation. Diffs the EN base strings file (`core/src/commonMain/composeResources/values/strings_wizard.xml` + future modules), finds new/changed keys not yet present in the 9 auto-managed locales (ES/ZH/AR/HI/PT/DE/FR/JA/KK-Latn), reads `core/strings-context/CONTEXT.json` for per-key context and `core/GLOSSARY.md` for canonical terminology + tone, and calls Claude API to translate, then writes the result back. RU is maintained manually and is NOT touched. Per FR-031a (spec 015) + C-10. Skill is idempotent — keys already present in a locale are not regenerated unless the base value changed (translation memory, FR-031d).
trigger: end of speckit-tasks orchestrator. Also: explicit user invocation "translate strings".
---

# Procedure: translate spec strings

## When to run

1. At the end of every `speckit-tasks` orchestrator run (per C-10).
2. Manually after editing `values/strings_wizard.xml` to update other locales.
3. After adding a new term to `core/GLOSSARY.md` if existing strings need to be reviewed.

## Inputs

- `core/src/commonMain/composeResources/values/strings_wizard.xml` — EN base (source of truth).
- `core/src/commonMain/composeResources/values-ru/strings_wizard.xml` — RU (manual; read-only here).
- `core/src/commonMain/composeResources/values-{es,zh,ar,hi,pt,de,fr,ja,kk-rLatn}/strings_wizard.xml` — auto-managed.
- `core/strings-context/CONTEXT.json` — per-key context (FR-031b).
- `core/GLOSSARY.md` — canonical terms and tone guidelines.
- `ANTHROPIC_API_KEY` env var.

## Outputs

- Updated `strings_wizard.xml` in the 9 auto-managed locale dirs.
- Git stages the changes.

## Algorithm

1. Parse base XML; extract `(key, value)` pairs.
2. For each auto-managed locale L:
   1. Parse current XML for L; extract existing `(key, value)`.
   2. Compute keys-to-translate = base keys whose value in L is missing OR was generated from a previous base value that no longer matches (translation memory).
   3. If empty → skip locale.
   4. Build prompt: glossary terms relevant to chosen keys, CONTEXT.json entries, tone guidelines for L, list of `(key, EN value, per-key context)`.
   5. Call Claude API (claude-opus-4-7) with system prompt instructing strict XML escaping and "do NOT translate placeholders like `{current}`".
   6. Parse response; merge into existing XML preserving keys not in the response.
   7. Write back; preserve the file's "STUB" comment as the file gets populated.
3. `git add` the modified files.
4. Print summary: locales touched, keys added, keys skipped.

## What NOT to do

- Do not regenerate RU — it is manual per A-15b.
- Do not regenerate keys whose value hasn't changed since the last run (FR-031d translation memory).
- Do not translate brand names, resource keys, or numeric literals (see GLOSSARY.md §"What NOT to translate").
- Do not call the API in CI without a key — fail gracefully with a clear "set ANTHROPIC_API_KEY" message.

## Implementation skeleton

A Kotlin script lives at `core/scripts/translate-strings.main.kts` — invoke
with `kotlin core/scripts/translate-strings.main.kts`. It uses
`HttpURLConnection` to keep dependencies zero.

## Exit ramps (server-roadmap)

- TODO(server-roadmap): when we run our own translation service (server-side
  cache + review queue), this skill calls that service instead of Claude API
  directly. The XML I/O contract stays identical.
- TODO(quality): human review pass for AR/HI/ZH/JA/KK pending in Phase 4
  (per OUT-005a). AI ships as-is for MVP.
