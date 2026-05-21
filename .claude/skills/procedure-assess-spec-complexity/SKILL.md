---
name: procedure-assess-spec-complexity
description: Decision-maker that scans a spec.md and decides which checklist-* skills are relevant. Returns a list of recommended checklists based on what the spec actually touches (UI, persistence, permissions, external SDK, etc.). Invoke this from any speckit-* orchestrator before running checklists, so we don't burn time running irrelevant ones on small specs.
---

# Procedure: assess-spec-complexity

This is a routing skill. It does not produce artifacts. It produces a **list of checklist-skills to invoke**.

---

## Inputs

- Path to a `spec.md` (or already-loaded content).
- Optional: `plan.md` if it exists (catches things spec.md hides).

---

## How to assess

Read the spec. Match its content against this table. **Recommend a checklist if at least one signal fires.**

| Checklist | Trigger signals (any one fires) |
|-----------|-------|
| `checklist-requirements-quality` | **always** — every spec passes through this |
| `checklist-meta-minimization` | **always** — anti-bloat is universal |
| `checklist-domain-isolation` | mentions external SDK, vendor (Firebase, WhatsApp, Coil, etc.), `androidMain`, `iosMain`, ports, adapters, `commonMain` |
| `checklist-wire-format` | mentions JSON, schema, persistence, SharedPreferences, DataStore, SQLDelight, deep-link, QR, export, sync, backend, contracts |
| `checklist-state-management` | mentions Activity, Fragment, lifecycle, configuration change, recreation, savedInstanceState, process death, low-memory |
| `checklist-failure-recovery` | mentions error, failure, fallback, retry, offline, network, timeout, missing app, permission denied |
| `checklist-performance` | mentions cold start, frame, jank, scroll, animation, battery, background, WorkManager, broadcast, polling, cache |
| `checklist-security` | mentions auth, credential, token, encryption, PII, contact, payment, intent extras, deep-link payload, exported activity, content provider |
| `checklist-permissions-platform` | mentions permission, manifest, OEM, Samsung, Xiaomi, Huawei, package visibility, Android 11+, scoped storage |
| `checklist-ux-quality` | mentions screen, UI, Composable, button, tap, gesture, wizard, picker, navigation, user flow |
| `checklist-accessibility` | mentions a11y, accessibility, TalkBack, screen reader, contentDescription, contrast, tap target, focus |
| `checklist-elderly-friendly` | mentions elderly, senior, large text, simplified, cognitive load, Article VIII, senior-safe |
| `checklist-localization` | mentions string, locale, translation, i18n, RTL, plural, format, ADR-004 |
| `checklist-core-quality` | mentions release, store, Play, distribution, signing, baseline-profile, R8 |
| `checklist-modular-delivery` | **any new feature, module, preset, or profile** — also fires on form-factor mentions (Android TV, TV, leanback, smart speaker, voice assistant, Assistant SDK, Android Auto, automotive, Wear, watch, foldable, tablet) and on form-factor-specific SDKs (Leanback, TIF, Tizen, CarAppService, Wear Compose). Err toward firing: this is the gate that catches one-way doors and core-bloat before they ship. |

---

## Always-on checklists

- `checklist-requirements-quality` — runs on every spec, no exceptions.
- `checklist-meta-minimization` — runs on every spec, no exceptions (Article XI is project-wide).

---

## Output format

Return a structured list to the caller:

```
ASSESSMENT for spec <path>:
  always-on:
    - checklist-requirements-quality
    - checklist-meta-minimization
  triggered:
    - <name> — reason: "<signal that fired>"
    - ...
  skipped:
    - <name> — reason: "no signals"
```

The caller then invokes each `triggered` checklist with the Skill tool.

---

## Heuristics for spec size

- **Tiny spec** (< 50 lines, no `## Scope` subsections beyond In/Out): only run always-on. Caller may skip even constitution-check if no architecture changes.
- **Small spec** (50–200 lines, single feature): always-on + 1–3 triggered.
- **Standard spec** (200–500 lines): always-on + most triggered. Expect 5–8 checklists.
- **Large spec** (>500 lines, multiple US, contracts/, data-model): all triggered + add `checklist-core-quality` even if not signalled.

When in doubt, **err on running fewer**. A skipped checklist costs nothing; a meaningless run-through costs tokens and noise.

---

## When NOT to call this

- A spec without `## Scope` or `## User Stories` is not yet a real spec — call `clarify-spec` first.
- For a `plan.md` (not `spec.md`) call `procedure-constitution-check` instead.
