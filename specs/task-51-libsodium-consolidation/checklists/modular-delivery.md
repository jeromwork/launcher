# Checklist: modular-delivery — TASK-51 libsodium consolidation

**Spec**: `specs/task-51-libsodium-consolidation/spec.md`
**Applied**: 2026-06-26
**Skill**: `.claude/skills/checklist-modular-delivery/SKILL.md`
**Anchors**: constitution Article V (Modularization With Restraint), Article VII §8 (profile module dependencies), Project-Specific Direction §6 (Form-Factor Variants); CLAUDE.md rules 1–4.

**Feature classification**: form-factor-agnostic infrastructural refactor of the crypto stack — consolidates two parallel crypto APIs (`com.launcher.api.crypto.*` legacy + `family.crypto.*` KMP) into a single `cryptokit.*` namespace inside the existing `:core:crypto` Gradle module. No new module, no new form factor, no new profile, no new preset, no new vendor SDK (lazysodium/JNA are being **removed**; ionspin libsodium-kmp already exists in `:core:crypto` from spec 016).

---

## Scope of the feature

- [x] CHK001 The spec states whether this feature is form-factor-agnostic or form-factor-specific.
  - **Evidence**: spec.md §AI Affordance ("инфраструктурный рефакторинг crypto-слоя"); §OEM Matrix declares uniform behaviour across all OEM/ABIs; US3 explicitly requires `commonMain` placement so iOS migration does not rewrite crypto. → **form-factor-agnostic**.

- [x] CHK002 If form-factor-specific: names the owning Gradle module (not Core, not handheld feature).
  - **N/A** — feature is form-factor-agnostic. Module ownership (`:core:crypto`) is nonetheless declared explicitly in FR-006 / FR-016 / Key Entities.

- [x] CHK003 If form-factor-agnostic: spec demonstrates no vendor SDK / platform API / form-factor UI assumption leaks into shared code.
  - **Evidence**: FR-006 places all production crypto API in `cryptokit.crypto.api.*` (KMP `commonMain`). FR-010 keeps Android Keystore behind `expect/actual SecureKeyStore`. US3 Acceptance Scenario 2 mandates a grep-check that `commonMain` contains no `android.*`/`androidx.*` imports. FR-007 fitness-tests ban `com.goterl.*`, `net.java.dev.jna.*`, `JNA.register(...)` in production.

## Module placement

- [x] CHK004 No new vendor SDK in Core; every external SDK behind one adapter module + port.
  - **Evidence**: FR-002 + FR-007 strip lazysodium/JNA entirely. ionspin libsodium-kmp (the sole remaining vendor) is consumed inside `cryptokit.crypto.libsodium.*` (Architectural decisions §1), which sits behind `cryptokit.crypto.api.*` ports (FR-006). Adapter shape preserved per CLAUDE.md rules 1–2.

- [x] CHK005 Article V §7 answered for every new Gradle module (why not a package?).
  - **N/A — no new Gradle module is introduced.** All work lands inside the existing `:core:crypto` module (created in spec 016). Spec explicitly states "новый pakage в том же `core/crypto/` модуле" (FR-006). Article V §7 does not trigger.

- [x] CHK006 Form-factor-specific code in Core / shared "for now" carries an explicit regret condition.
  - **N/A — no form-factor-specific code introduced or retained.** All retained code is form-factor-agnostic crypto primitives + pairing wire format. The Android Keystore `expect/actual` already obeys the rule via `androidMain`.

## Profile / preset declaration

- [x] CHK007 Profile `requiredModules` / `optionalModules` declared if profile is introduced or modified.
  - **N/A** — feature touches neither profile schema nor any preset. Pairing flow (consumer of crypto) is profile-independent.

- [x] CHK008 Profile schema bump carries version increment + backward-compat plan.
  - **N/A** — no profile schema change.

- [x] CHK009 Base app + existing profiles still load and operate when the new module is absent; user-visible degradation stated.
  - **N/A — no new module is introduced**, so "module absent" case does not arise. The `:core:crypto` module is a mandatory dependency from spec 016 and remains so; no new optionality is created.

## Form-factor expansion

- [x] CHK010 Non-handheld form factor delivered as profile + downloadable module(s), not fork/separate app/new source set.
  - **N/A** — feature does not touch any non-handheld form factor. US3 mentions future iOS (TASK-26) only as a **non-breakage requirement** for the architectural seam (commonMain placement), not as an in-scope deliverable.

- [x] CHK011 Form-factor-specific vendor SDKs appear only in their form-factor adapter module.
  - **N/A** — no form-factor-specific SDK in scope. Android Keystore (only platform API touched) is already correctly localised in `androidMain` via `expect/actual SecureKeyStore` (FR-010).

- [x] CHK012 First non-handheld form factor: ADR for delivery channel with exit ramp.
  - **N/A** — not a form-factor expansion.

## One-way doors raised by the feature

- [x] CHK013 No irreversible dependency / identifier / wire format without documented alternatives + regret + exit ramp.
  - **Evidence**: The two one-way-door candidates raised in clarify (Q1 deep migration, Q2 force re-pair) are explicitly resolved with rationale and exit ramps:
    - Q1 deep migration: documented in Clarifications table with rationale ("Owner-mandate, чище архитектурно"). Reversal cost = re-introducing ~25 call-site translations; bounded inside `:core:crypto` + `:core:keys` + `:app`.
    - Q2 force re-pair: FR-005 carries the **inline-TODO exit ramp** `// TODO(post-task-6): replace nuke-and-re-pair with derive-from-root after Root Key Hierarchy lands`, naming the post-condition that lets us leave this choice.
  - FR-004 + Assumptions guarantee spec 011 wire format (`schemaVersion: 1`, byte-equal round-trip) is unchanged — no wire-format one-way door is opened.

- [x] CHK014 New external SDK: "vendor disappears tomorrow" test answered, bounded to one adapter.
  - **Evidence**: The only external SDK in play (ionspin libsodium-kmp 0.9.5) is wrapped behind `cryptokit.crypto.api.*` ports; implementation isolated to `cryptokit.crypto.libsodium.*`. Vendor-disappearance blast radius = one package inside one module (`:core:crypto`). Strictly, ionspin is **not new** — it was introduced in spec 016 (Done); TASK-51 merely removes the parallel lazysodium stack.

- [x] CHK015 "Free workaround" instead of server: tracked in `docs/dev/server-roadmap.md` + inline `TODO(server-roadmap)`.
  - **N/A** — pure client-side crypto refactor. No server substitution decision is made or deferred here. Cloud-side spec 011 envelope storage is untouched (still Firestore/B2, governed by other backlog tasks).

## Anti-bloat sanity

- [x] CHK016 No Gradle module added for a single class / single-impl interface.
  - **Evidence**: zero new Gradle modules. Article V §7 is honoured by reuse of the existing `:core:crypto`.

- [x] CHK017 No pre-emptive multi-form-factor split without an actual second consumer.
  - **Evidence**: `commonMain` placement (FR-006, US3) is **not** speculative — it directly serves the existing consumer (`:core:keys`, `:app`) and matches the spec 016 architecture already committed. iOS support (TASK-26) is named as a non-regression seam (already true today), not as the justification for the split.

- [x] CHK018 Future split anticipated → recorded as regret condition (CHK006), not implemented now.
  - **Evidence**: The only forward-looking lever — Root Key Hierarchy (TASK-6) — is captured as an inline `TODO(post-task-6)` (FR-005) rather than implemented now. No premature abstraction (CLAUDE.md rule 4 honoured).

---

## Verdict

| Bucket | Result |
|---|---|
| Scope (CHK001–003) | **PASS** — form-factor-agnostic, no cross-form-factor leakage. |
| Module placement (CHK004–006) | **PASS** — no new module; vendor SDK swap stays behind existing port. |
| Profile / preset (CHK007–009) | **N/A** — no profile or preset touched. |
| Form-factor expansion (CHK010–012) | **N/A** — no non-handheld form factor. |
| One-way doors (CHK013–015) | **PASS** — exit ramps documented; no server-substitution debt. |
| Anti-bloat (CHK016–018) | **PASS** — no new module, no speculative split. |

**Overall**: **PASS**. No blockers. The refactor stays inside one existing module, removes vendor SDKs from the dependency graph, preserves the wire format, and carries a documented exit ramp for the only one-way door (force re-pair → TASK-6 derive-from-root).

## Open items

None. All checklist items resolved (relevant items PASS; irrelevant items marked N/A with justification). No CHK012 blocker (no form-factor expansion), no missing ADR, no premature abstraction flagged for `speckit-analyze` punch list.
