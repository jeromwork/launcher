# Checklist: modular-delivery

Applied to `specs/task-120-preset-composition-foundation/spec.md` on 2026-07-10.

Anchors: constitution Article V, Article VII §8, Project-Specific Direction §6; CLAUDE.md rules 1, 2, 3, 4.

Context notes:
- Spec is foundation for 5 downstream tasks (draft-1, TASK-71, TASK-69, TASK-68, TASK-19).
- MVP wave narrowed to 4 Components (AppTile, FontSize, Sos, Toolbar). MessengerTile deferred to task-121.
- `Vendor` enum includes `GoogleTV`, `iOS` as future form-factors.
- Foundation lives in `core/preset/` KMP commonMain; Provider adapters in `androidMain/provider/`, `iosMain/provider/`.

## Scope of the feature

- [x] CHK001 The spec states whether this feature is **form-factor-agnostic** or **form-factor-specific**.
  - PASS. Spec explicitly positions foundation as form-factor-agnostic: "Foundation этого spec'а живёт в `core/preset/` KMP commonMain — pure Kotlin, zero Android" (Assumptions). Non-handheld form factors are surfaced only as future `Vendor` enum values (GoogleTV, iOS) resolved via `ProviderRegistry` fallback, not via core edits.
- [x] CHK002 If form-factor-specific: the spec names which Gradle module will own the code.
  - N/A — feature is form-factor-agnostic.
- [x] CHK003 If form-factor-agnostic: the spec demonstrates that no vendor SDK, no platform-specific API, and no form-factor UI assumption leaks into the shared code.
  - PASS. FR-001 requires `Component` sealed class "без Android-зависимостей". Assumptions clarify domain in `commonMain`, Android SDK only in `app/androidMain/provider/*`. `InteractionSink` port explicitly names `TouchInteractionSink` and future `TvRemoteInteractionSink` / `SilentInteractionSink` as separate implementations, so no D-pad or touch assumption leaks into the port shape.

## Module placement

- [ ] CHK004 No new vendor SDK is added to Core. Every external SDK lives in exactly one adapter module behind a port.
  - PASS-WITH-CAVEAT. Domain isolation is respected in the abstract: `PackageManagerFacade`, `ConditionEvaluator`, `SosDispatcher` (deferred), `AuthHandoffService` (deferred to task-121) are all declared as ports. No vendor SDK named for MVP Component wave. Caveat: spec does not explicitly name the Gradle module boundary for Provider adapters — implied `app/androidMain/provider/*` reads as a *package* inside an existing module, not a distinct Gradle module. See CHK005.
- [ ] CHK005 The spec answers Article V §7 for every new Gradle module.
  - PARTIAL FAIL. Spec introduces a new package tree `core/preset/` and mentions `androidMain/provider/`, `iosMain/provider/` but does NOT explicitly state whether `core/preset/` is (a) a new Gradle module, (b) a new source-set within existing `core`, or (c) a package inside `core`. Article V §7 questions ("why is a package not enough? what API boundary does this module protect? what complexity does it remove now?") are not answered. Given plan.md is not yet written, this decision defers to the plan phase — but the spec should at minimum flag it as a plan-level decision. Recommendation: add an Assumption line: "`core/preset/` is a package inside existing `core` module — no new Gradle module introduced at foundation; split into standalone module deferred until second consumer emerges (see CHK006 regret condition)."
- [ ] CHK006 If the spec keeps form-factor-specific code in Core or a shared module "for now", it carries an explicit **regret condition**.
  - PARTIAL FAIL. Spec has no code that is form-factor-specific in Core (good). But: several deferred seams (`ConditionEvaluator` full JsonLogic runtime, `SosDispatcher`, `Provider.rollback`) live as ports with no-op / minimal MVP adapters, and their regret conditions are described only implicitly ("deferred to first real conditional preset"). The `Vendor.GoogleTV` / `Vendor.iOS` enum values in FR-018 lack an explicit regret condition — what triggers building a real GoogleTV or iOS adapter module? Recommendation: add regret conditions per deferred seam and per future-vendor enum entry. This is soft — no core code bloat introduced, just documentation debt.

## Profile / preset declaration

- [ ] CHK007 If the feature introduces or modifies a profile, `requiredModules` and `optionalModules` are declared explicitly (Article VII §8).
  - FAIL. This is the biggest gap. Spec defines preset wire format (schemaVersion=2, three fields `wizardFlow`/`settingsMap`/`activeComponents`) but does NOT include `requiredModules` or `optionalModules` fields. Article VII §8 mandates both as part of preset wire format. Consequence: if downstream tasks (TASK-68 workspace, TASK-19 adaptive-UX) or future form-factor adapters introduce optional Gradle modules, existing bundled presets will have no way to declare them. Since this is a foundation spec establishing schemaVersion=2, adding these fields later requires a schemaVersion bump + migration writer (rule 5). Recommendation: add `requiredModules: List<String> = emptyList()` and `optionalModules: List<String> = emptyList()` to preset root now, even if the MVP wave never populates them. This is additive-safe and satisfies Article VII §8 up front.
- [ ] CHK008 The profile schema bump carries a version increment and a backward-compat plan per Article VII §3 and CLAUDE.md rule 5.
  - PASS. FR-016 requires `schemaVersion: Int` on `pool.json`, `preset.json`, `profile.json`; additive-only changes; rename/remove requires migration writer per rule 5. SC-008 restates the invariant. FR-012 rejects preset with `schemaVersion` higher than app supports (fail loud).
- [x] CHK009 The base application and every existing profile MUST still load and operate correctly when the new module is absent.
  - PASS. FR-007 defines Registry fallback chain `(type, platform, vendor) → (type, platform, null) → (type, null, null) → NoOpProvider` returning `Unsupported`; engine skips gracefully. SC-006 explicitly verifies `HomeRole` iOS absence → NoOp → `Unsupported` → engine skips without error. Edge case "activeComponents contains Component not in pool" → `Skipped`, no crash. Base app degradation is user-invisible skip.

## Form-factor expansion

- [x] CHK010 The non-handheld form factor is delivered as **profile + downloadable module(s)**, not a fork or new top-level source set in handheld module.
  - N/A at foundation. Spec does not deliver any non-handheld form factor — only reserves `Vendor.GoogleTV` and `Vendor.iOS` enum values as future-facing seams. Actual TV / Wear / voice delivery is out of scope. Provider fallback mechanism (FR-007) makes the addition additive.
- [x] CHK011 Form-factor-specific vendor SDKs appear only in their form-factor adapter module.
  - N/A at foundation. No Leanback, TIF, Wear Compose, CarAppService, Assistant SDK, Tizen SDK mentioned. Foundation is form-factor-agnostic by design. When TV or Wear ships as a downstream task, that spec must own the adapter-module ADR.
- [x] CHK012 If this is the **first non-handheld form factor** to ship, the spec links to an ADR that decides the delivery channel.
  - N/A. Foundation ships no non-handheld form factor. When first non-handheld form factor is introduced (likely GoogleTV per Vendor enum foreshadowing), that spec MUST include the Play Feature Delivery / sideload / own-server / split-APK ADR per constitution Project-Specific Direction §6 last paragraph. This foundation spec correctly declines to force that one-way door now.

## One-way doors raised by the feature

- [ ] CHK013 The feature does not introduce a dependency, identifier, or wire format that cannot be reversed in days without data migration.
  - PASS-WITH-CAVEAT. schemaVersion=2 wire format (`pool.json`, `preset.json`, `profile.json`) is a one-way door by design (rule 5 governs it). FR-016 declares additive-only rule, migration writer required for breaks. `presetId` and `poolRef` identifiers are opaque strings — swappable. FR-023 "PoolSource MUST быть additive-only" is enforced. Caveat: `classDiscriminator = "type"` for kotlinx.serialization polymorphic sealed hierarchy locks the JSON layout — swapping to another discriminator later is a break. Since this is inherited from kotlinx.serialization convention and MVP-appropriate, acceptable.
- [x] CHK014 If the feature adds a new external SDK, the spec answers "vendor disappears tomorrow" test.
  - N/A. Foundation adds no external SDK. Hilt is already a project dependency (Assumptions: "DI framework — Hilt"); kotlinx.serialization is already a project dependency. No new vendor lock-in introduced.
- [x] CHK015 If the feature relies on a "free workaround" instead of a server component, an entry exists in server-roadmap.md and inline TODO is planned.
  - N/A. Foundation is entirely on-device. Admin push (US5) is schema-only in MVP with runtime deferred to TASK-27 messenger + TASK-102 edit-lock, where server posture will be assessed under CLAUDE.md rules 12 and 13. Foundation correctly avoids server touch.

## Anti-bloat sanity

- [x] CHK016 The feature does not add a Gradle module for a single class or single-implementation interface (CLAUDE.md rule 4).
  - PASS. See CHK005 — spec does not commit to any new Gradle module at all; splits are deferred to plan phase. FR-025 anti-explosion principle + SC-012 fitness function #8 (≤3 declarations per Component subtype, warn 4-5, error ≥6) explicitly guard against Component-subtype proliferation. Nested-adapter pattern for peripherals (FR-015: `BloodPressureDeviceProvider` with `OmronBpAdapter` / `AndBpAdapter` sub-adapters instead of extending `HandlerKey` with a fourth axis) is the correct anti-bloat choice — one Provider module hosts multiple vendor adapters rather than one module per peripheral vendor.
- [x] CHK017 The feature does not pre-emptively split into modules "in case we go multi-form-factor later" without an actual second consumer.
  - PASS. `Vendor.GoogleTV` and `Vendor.iOS` are enum values, not modules. `iosMain/provider/` is mentioned as a source-set placeholder with no-op adapters — no separate Gradle module. Registry fallback (FR-007) is the seam for form-factor variance, not module split.
- [x] CHK018 If a future split is anticipated, the spec records it as a regret condition rather than implementing the split now.
  - PARTIAL. Spec anticipates future splits (task-121 messenger, TASK-27 push runtime, TV/Wear/iOS form-factor adapters) but does not enumerate explicit regret conditions for each. See CHK006 recommendation. This is documentation debt, not architectural debt.

---

## Answers to specific verification questions

**(a) Form-factor variance addressed via Provider fallback (not via new modules)?**
YES. FR-007 `ProviderRegistry.resolve` fallback chain `(type, platform, vendor) → (type, platform, null) → (type, null, null) → NoOpProvider` is the primary form-factor variance mechanism. Registered `Vendor.GoogleTV` / `Vendor.iOS` in FR-018 hook into this chain without core edits. SC-006 validates the fallback on HomeRole/iOS as concrete example.

**(b) Is `core/preset/` a new module? Own Gradle module or fold into existing `core/`?**
UNRESOLVED IN SPEC. Spec says "живёт в `core/preset/` KMP commonMain" (Assumptions) which reads as a package, but does not explicitly answer Article V §7. Recommendation: fold into existing `core/` module as a package — no new Gradle module at foundation (MVA per rule 4). Split into standalone `core-preset` module deferred until a second consumer (e.g. a form-factor adapter needing the sealed hierarchy without pulling in `core`) emerges. Add this as an Assumption or a Design Decision line in the spec (or defer explicitly to plan.md).

**(c) Modularization restraint per Article V — is nested-adapter pattern for peripheral vendors adding module bloat?**
NO. FR-015 nested-adapter pattern is the correct anti-bloat choice: `BloodPressureDeviceProvider` hosts `OmronBpAdapter` / `AndBpAdapter` as sub-adapters within one Provider module, rather than one Gradle module per peripheral vendor. This respects Article V §7 (package suffices, no module boundary needed until a peripheral vendor SDK grows large or ships independently).

**(d) Form-factor SDKs (Leanback for TV, Wear Compose) — is the Provider mechanism sufficient to add them without core edit later?**
YES. Provider adapter modules key on `Vendor.GoogleTV` (Leanback), plus future Vendor enum entries for Wear / Auto. Adding a new Provider means: (1) new adapter module in appropriate source set, (2) `@IntoMap` DI binding, (3) optionally a new `Vendor` enum entry (this IS a core edit but is additive per CLAUDE.md rule 5 — enum extension is safe for readers, provided the wire-format serializer tolerates unknown values, which spec does not explicitly address — see recommendation below). `InteractionSink` port (Key Entities) also names future `TvRemoteInteractionSink` explicitly, so D-pad interaction can be added additively.

Small recommendation: FR-018 declares `Vendor` as a "closed" enum — but form-factor addition (`GoogleTV`, `iOS`, future `WearOS`, `AndroidAuto`) requires enum extension. This is not strictly a core-file untouched invariant (SC-001 doesn't mention Vendor). Consider adding a note to FR-018 or Vendor detection code: adding a new Vendor enum entry is expected additive change, distinct from the "engine + registry untouched" core invariant.

**(e) Form-factor form assumption — this is phone-first; is TV/Wear scoped out explicitly?**
PARTIALLY. Spec's phone-first assumption is implicit ("бабушка", "выключила Wi-Fi", touch-driven Settings). Explicit scoping-out is not stated as a section. `Vendor.GoogleTV` and `Vendor.iOS` in FR-018 imply future support, and `TvRemoteInteractionSink` mentioned in Key Entities implies TV variance. But no explicit statement "TV and Wear form factors are OUT of scope for foundation; they enter through downstream tasks / adapter modules as described in Project-Specific Direction §6". Recommendation: add an Out-of-Scope line: "Non-handheld form factors (Android TV, Wear, Auto, voice) are out of MVP scope; they enter via new Provider adapter modules + new preset variants once first non-handheld form factor is prioritized (per constitution Project-Specific Direction §6). Foundation reserves seams: `Vendor` enum extension, `InteractionSink` port variants, `ProviderRegistry` fallback." This makes phone-first explicit while preserving the seams.

---

## Summary of failures (for speckit-analyze punch list)

- CHK005 (PARTIAL FAIL): spec does not answer Article V §7 for `core/preset/` — is it a new Gradle module, a source set, or a package? Recommendation: add Assumption / plan-decision line stating "package inside existing `core` module, split deferred to second-consumer trigger".
- CHK006 (PARTIAL FAIL): deferred seams (`ConditionEvaluator` runtime, `SosDispatcher`, `Provider.rollback`, `Vendor.GoogleTV` / `Vendor.iOS` future adapters) lack explicit regret conditions. Documentation debt.
- **CHK007 (FAIL, ACTION REQUIRED)**: preset wire format v2 is missing `requiredModules` and `optionalModules` fields required by Article VII §8. Add now as additive fields with `emptyList()` defaults — future form-factor and downloadable-module presets will need them, and adding after v2 ships requires a schemaVersion bump + migration writer per CLAUDE.md rule 5.
- CHK018 (PARTIAL): anticipated future splits (task-121, TV/Wear form-factor adapters) not enumerated as regret conditions. Documentation debt, shared with CHK006.

All other gates PASS or N/A. Foundation spec correctly respects modularization restraint: no premature Gradle-module split, form-factor variance handled through Provider fallback + adapter source sets, nested-adapter pattern for peripherals, anti-explosion principle (FR-025 + fitness #8) explicit.

**Blocker status**: CHK007 should be addressed before implementation begins (adding fields post-v2 is expensive). CHK005 and CHK006 are recommendations, not blockers — can be resolved during plan.md.
