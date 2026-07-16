---
name: checklist-modular-delivery
description: Verifies that a new feature, preset, or platform integration respects the modular-delivery architecture per constitution Article V (Modularization With Restraint), Article VII (Profile-Driven and Configurable) §8, and Project-Specific Direction §6 (Form-Factor Variants). Catches features that bloat the core for one form factor only, features that add vendor SDKs without an adapter module, profiles that silently assume modules, and one-way-door decisions about module delivery without an ADR. Triggered whenever a spec mentions a new feature, a new module, a new preset/profile, a new form factor (Android TV, smart speaker, voice assistant, Android Auto, Wear, foldable, tablet), or a vendor/platform SDK specific to one device class (Leanback, TIF, Tizen, Assistant SDK, CarAppService, Wear Compose).
---

# Checklist: modular-delivery

Enforces the **modular-delivery** discipline that lets this project grow into multiple form factors (handheld, Android TV, voice, automotive, wearables) without forking the codebase or bloating the core. Anchored in:

- [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md) — Article V (Modularization With Restraint), Article VII §8 (profile module dependencies), Project-Specific Direction §6 (Form-Factor Variants).
- [`CLAUDE.md`](../../../CLAUDE.md) — rule 1 (domain isolation), rule 2 (Anti-Corruption Layer), rule 3 (one-way doors), rule 4 (Minimum Viable Architecture).

---

## Scope of the feature

- [ ] CHK001 The spec states whether this feature is **form-factor-agnostic** (works on every supported device class) or **form-factor-specific** (TV-only, voice-only, auto-only, etc.).
- [ ] CHK002 If form-factor-specific: the spec names which Gradle module will own the code. It is **not** Core, and **not** an existing handheld feature module.
- [ ] CHK003 If form-factor-agnostic: the spec demonstrates that no vendor SDK, no platform-specific API, and no form-factor UI assumption (D-pad, voice intent, head unit) leaks into the shared code.

## Module placement

- [ ] CHK004 No new vendor SDK is added to Core. Every external SDK lives in exactly one adapter module behind a port (CLAUDE.md rules 1–2).
- [ ] CHK005 The spec answers Article V §7 for every new Gradle module: *Why is a package not enough? What API boundary does it protect? What complexity does it remove now?*
- [ ] CHK006 If the spec keeps form-factor-specific code in Core or a shared module "for now", it carries an explicit **regret condition** (CLAUDE.md rule 3): the trigger that forces the split (second form-factor, second SDK, build-size threshold, OEM divergence). No vague "we'll refactor later".

## Profile / preset declaration

- [ ] CHK007 If the feature introduces or modifies a profile, the profile's `requiredModules` and `optionalModules` fields are declared explicitly (Article VII §8). No implicit "ships with the feature".
- [ ] CHK008 The profile schema bump (if any) carries a version increment and a backward-compat plan per Article VII §3 and CLAUDE.md rule 5.
- [ ] CHK009 The base application and every existing profile MUST still load and operate correctly when the new module is absent (Article VII §6, Project-Specific Direction §4). The spec states what the user-visible degradation is.

## Form-factor expansion (only if the spec touches a non-handheld form factor)

- [ ] CHK010 The non-handheld form factor is delivered as **profile + downloadable module(s)**, not as a fork, a separate app, or a new top-level source set in the handheld module (Project-Specific Direction §6).
- [ ] CHK011 Form-factor-specific vendor SDKs (Leanback, TIF, Android Auto, Wear, Assistant SDK, etc.) appear **only** in their form-factor adapter module — not in Core, not in handheld features.
- [ ] CHK012 If this is the **first non-handheld form factor** to ship, the spec links to an ADR that decides the delivery channel (Play Feature Delivery, in-app sideload, own server, split APK) with an exit ramp. If no such ADR exists yet, the spec is blocked until it is written.

## One-way doors raised by the feature

- [ ] CHK013 The feature does not introduce a dependency, identifier, or wire format that cannot be reversed in days without data migration or external announcement. If it does, the spec documents alternatives considered, regret conditions, and the exit ramp per CLAUDE.md rule 3.
- [ ] CHK014 If the feature adds a new external SDK, the spec answers the "vendor disappears tomorrow" test: which files would change, and is it bounded to one adapter module?
- [ ] CHK015 If the feature relies on a "free workaround" instead of a server component (per CLAUDE.md rule 8), an entry exists in [`docs/dev/server-roadmap.md`](../../../docs/dev/server-roadmap.md) and an inline `TODO(server-roadmap)` is planned in the code.

## Anti-bloat sanity

- [ ] CHK016 The feature does not add a Gradle module for a single class or a single-implementation interface (CLAUDE.md rule 4: Minimum Viable Architecture).
- [ ] CHK017 The feature does not pre-emptively split into modules "in case we go multi-form-factor later" without an actual second consumer.
- [ ] CHK018 If a future split is anticipated, the spec records it as a regret condition (CHK006) rather than implementing the split now.

---

## How to apply

1. Read the spec; classify the feature: agnostic, form-factor-specific, or profile/preset-only.
2. Walk the gates in order. A failure on CHK001–CHK003 stops the rest — fix scoping first.
3. For form-factor work, CHK010–CHK012 are blockers, not advisories.
4. For one-way doors (CHK013–CHK015), if any answer is "we'll figure it out later", **invoke the `mentor` skill before proceeding** — these decisions cost too much to retract.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-modular-delivery: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/modular-delivery.md`. Scratch buffer permitted, must be deleted before returning. Notable failures must surface in the `speckit-analyze` punch list; CHK012 failure (missing delivery-channel ADR) blocks implementation until resolved. Grey items land as edits to `spec.md` / `plan.md`.
