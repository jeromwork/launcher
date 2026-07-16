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

**Note (ADR-011 §5 revised 2026-07-16)**: each `checklist-*` skill this routing decision returns is now **chat-only** — no persisted file under `specs/<id>/checklists/`. Grey items surfaced by a checklist land as edits to `spec.md` / `plan.md`. See individual skill Output sections.

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
| `checklist-localization-ui` | **any spec with UI** — Composables, screens, tiles, buttons, dialogs, wizard steps, labels, edit forms. Complements `checklist-localization` by checking layout robustness (length expansion, RTL, plural width, line-height). Per roadmap reorder 2026-06-15: localization is mandatory from day 1 even if translations come later. |
| `checklist-core-quality` | mentions release, store, Play, distribution, signing, baseline-profile, R8 |
| `checklist-modular-delivery` | **any new feature, module, preset, or profile** — also fires on form-factor mentions (Android TV, TV, leanback, smart speaker, voice assistant, Assistant SDK, Android Auto, automotive, Wear, watch, foldable, tablet) and on form-factor-specific SDKs (Leanback, TIF, Tizen, CarAppService, Wear Compose). Err toward firing: this is the gate that catches one-way doors and core-bloat before they ship. |
| `checklist-backend-substitution` | **any backend touch** — mentions of backend, server, Firebase, Firestore, Realtime DB, Cloud Storage, Cloud Functions, Cloudflare, Worker, Spark plan, sync, remote config, remote command, auth, login, sign-in, user identity, UID, session, token, persisted user data, shared storage, security rules, transactions, document, collection. Also fires on any phrasing that implies a third-party backend choice. Purpose: keep the eventual swap to own-server visible in design discussions; produces a cost-of-swap paragraph, does not block. |
| `checklist-preset-readiness` | mentions of preset, template, layout, tile arrangement, theme variant, tutorial sequence, wizard manifest, action mapping, bundled config, `ConfigSource`, default profile — any user-facing configuration that is NOT identity-bound. Per CLAUDE.md rule 9 (shareability-readiness) and refuse pattern #12. |
| `checklist-ai-readiness` | mentions of AI, LLM, MCP, agent, assistant, automation, suggest, recommend, Gemini, OpenAI, Claude, Capability Registry, Exposure Adapter. Also fires when a spec adds a user-facing capability an AI agent could plausibly invoke later, even if no AI ships in this spec. Enforces provider-agnostic capability shape. |
| `checklist-capability-registry-readiness` | **any spec adding a new action / intent / external-callable surface** — call, send, message, open, navigate, trigger, share, delete, scan, record. Per roadmap reorder 2026-06-15: F-2 (Capability Registry Foundation) is deferred to the last step of Phase 2; until then every S-spec must leave inline `// TODO(capability-registry)` sewing points and avoid mentioning concrete MCP/AI providers (Google Assistant, App Actions, Gemini, OpenAI, Claude, MCP server). |
| `checklist-device-self-sufficiency` | **any spec touching launcher, wizard, config, contacts, themes, OR introducing a cloud dependency**. Per decision 2026-06-15-deferred-cloud: device is self-sufficient, Sign-In appears at first cloud action (not first launch), spec must declare LOCAL/CLOUD/HYBRID mode and document the local→cloud upgrade path. |
| `checklist-tamper-resistance` | mentions of subscription, billing, entitlement, premium, paid, trial, license, Play Billing, Stripe, server-validated, Play Integrity. Per decision 2026-06-15-deferred-cloud/03 — billing is cloud-only; cloud features must validate entitlement server-side via JWT, NOT via client-computed flag. |
| `checklist-notification-minimization` | mentions of push, notification, FCM, system tray, banner, badge, alert, ping, reminder, in-app indicator, notification center. Per CLAUDE.md rule 10 and refuse pattern #13. |
| `checklist-server-hardening` | **any server touch** — mentions of endpoint, route, handler, Cloudflare Worker, Go microservice, `push-worker/`, `workers/`, POST/GET/PUT/DELETE HTTP method, API. Per CLAUDE.md rule 12 (zero-trust posture) and refuse pattern #20. Verifies auth / rate limit / input validation / observability / failure modes per TASK-105 baseline. |
| `checklist-zero-knowledge-server` | **any server touch** (same trigger as server-hardening) — additionally fires on mentions of ACL, membership, group state, ownership, `userUid`, `sub`, `eventType`, `type` field routing, retention, Firestore document ownership, opaque ID, namespace. Per CLAUDE.md rule 13 (zero-knowledge posture) and refuse patterns #21-28. Orthogonal to server-hardening: rule 12 = "how we defend", rule 13 = "what the server may know". Both must pass on any server-side spec. |
| `checklist-dev-experience` | **always** — local-test-path and dev-loop quality is universal |

---

## Always-on checklists

- `checklist-requirements-quality` — runs on every spec, no exceptions.
- `checklist-meta-minimization` — runs on every spec, no exceptions (Article XI is project-wide).
- `checklist-dev-experience` — runs on every spec; local test path is mandatory (spec-template).

---

## Output format

Return a structured list to the caller:

```
ASSESSMENT for spec <path>:
  always-on:
    - checklist-requirements-quality
    - checklist-meta-minimization
    - checklist-dev-experience
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
