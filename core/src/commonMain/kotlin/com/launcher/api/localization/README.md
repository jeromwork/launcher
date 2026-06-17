# `com.launcher.api.localization` — Localization domain

**Spec**: [015 — Wizard + Localization + Senior UI](../../../../../../specs/015-wizard-localization-senior-ui/spec.md)

**Status**: EXTRACT CANDIDATE per FR-042. Today it's a package in `:core`.

## What's inside

- `StringResolver` — synchronous string lookup port. Real Android adapter
  is `com.launcher.adapters.wizard.AndroidStringResolver` (uses
  `Resources.getIdentifier`).
- `LocaleProvider` — returns current BCP-47 tag.
- `readingDirectionFor(localeTag)` — RTL helper. Returns
  `ReadingDirection.Rtl` for AR/HE/FA/UR/PS/YI, `Ltr` otherwise. Per FR-032.

## String tables

The canonical source of truth lives in
`core/src/commonMain/composeResources/values{-locale}/strings_wizard.xml`.
Mirror copies in `core/src/androidMain/res/values{-locale}/strings_wizard.xml`
serve the Android runtime resolver.

**Base language: EN** (overrides ADR-004's RU-first default per spec 015
clarification C-6 + A-15b). EN is the canonical input to the translation
pipeline targeting AR/HI/ZH/JA/KK where Russian is not a reasonable source.

## Translation pipeline setup

Per FR-031a, the `procedure-translate-spec-strings` skill runs at the end of
every `speckit-tasks` invocation, diffs the EN base, and refreshes the 9
auto-managed locale stubs (ES/ZH/AR/HI/PT/DE/FR/JA/KK-Latn). RU is
maintained manually.

Required env var: `ANTHROPIC_API_KEY`. See
[.claude/skills/procedure-translate-spec-strings/SKILL.md](../../../../../../.claude/skills/procedure-translate-spec-strings/SKILL.md)
for the workflow.

Per-key context lives in `core/strings-context/CONTEXT.json` (FR-031b).
Canonical terminology lives in `core/GLOSSARY.md` (FR-031c).

## Exit ramps

- TODO(server-roadmap): when SRV-TRANSLATE-001 ships — translation skill
  calls our own translation service (server-cached + review queue) rather
  than the Anthropic API directly. The XML I/O contract stays identical.
- TODO(quality): human review pass for AR/HI/ZH/JA/KK pending in Phase 4
  per OUT-005a. AI ships as-is for MVP.
