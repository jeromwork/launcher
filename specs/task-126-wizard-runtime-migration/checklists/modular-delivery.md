# Checklist: modular-delivery
# spec: specs/task-126-wizard-runtime-migration/spec.md
# generated: 2026-07-11

## Scope of the feature

- [ ] **CHK001** — FAIL: The spec does not explicitly state form-factor classification ("form-factor-agnostic" vs "form-factor-specific"). The feature is implicitly handheld Android only (mentions Xiaomi, no TV/Auto/Voice scope), and Assumptions note "iOS providers deferred — KMP-ready ports" which implies agnostic intent. The spec should add a one-liner: "This feature is form-factor-agnostic (KMP-ready domain ports; Android adapters only in this task)."
- [N/A] **CHK002** — Not form-factor-specific (no TV/Auto/Voice/Wear scope). N/A.
- [x] **CHK003** — PASS: Android platform APIs (`WindowInsetsControllerCompat`, `AppCompatDelegate`, `Intent`) are confined to `androidMain` adapter implementations (`com.launcher.preset.provider.*`). FR-018 explicitly requires ACL migration of `SystemSettingPort`/`PermissionRequestPort` into `SystemPermissionProvider` facade. Plan-level sequences show `Provider implementations (androidMain)` as distinct layer from domain. Domain ports (`Provider<T>`, `InteractionSink`, `ProfileStore`) are platform-agnostic.

## Module placement

- [x] **CHK004** — PASS: No new vendor SDK introduced. All new types use Android platform APIs already present in `androidMain`. No SDK added to Core.
- [N/A] **CHK005** — No new Gradle module introduced. Migration consolidates `Spec015Module + Task65Module → PresetModule`. N/A.
- [N/A] **CHK006** — No form-factor-specific code placed in Core. N/A.

## Profile / preset declaration

- [ ] **CHK007** — FAIL: The spec does not declare `requiredModules` / `optionalModules` for the migrated Preset format per Article VII §8. This is a migration task (not introducing a new profile from scratch), but the spec should either: (a) explicitly state "requiredModules/optionalModules not changed by this migration, inherited from TASK-120 baseline" or (b) verify TASK-120 already declared these fields. Currently silent on the topic.
- [x] **CHK008** — PASS: FR-014 explicitly specifies schemaVersion bumps (Preset v1→v2, Pool v1→v2) with backward-compat plan: "v1 readers ignore new fields; v2 readers default missing fields." Aligns with CLAUDE.md rule 5 and Article VII §3.
- [N/A] **CHK009** — No new optional module added. Base application continues to operate on the same Preset format. N/A.

## Form-factor expansion

- [N/A] **CHK010** — No non-handheld form factor introduced. N/A.
- [N/A] **CHK011** — No form-factor-specific vendor SDKs. N/A.
- [N/A] **CHK012** — Not the first non-handheld form factor. N/A.

## One-way doors raised by the feature

- [x] **CHK013** — PASS: Wire-format changes (schemaVersion v1→v2 for Preset and Pool) are backward-compatible per FR-014. Deletion of legacy `com.launcher.api.wizard.*` is a one-way door covered by design.md D1: "Zero production users = no migration beneficiaries." Alternative considered (keep migration writer) explicitly rejected with rationale. D9 documents rollback: "Rollback = close PR, no production impact."
- [N/A] **CHK014** — No new external SDK. N/A.
- [N/A] **CHK015** — No free workaround instead of server component. Pure client-side refactoring. N/A.

## Anti-bloat sanity

- [x] **CHK016** — PASS: No new Gradle module for a single class. Migration consolidates (reduces) modules.
- [x] **CHK017** — PASS: No preemptive split into modules for hypothetical future form factors.
- [N/A] **CHK018** — No anticipated future split being deferred. N/A.

---

## Summary

**Result: 2 FAIL, 7 PASS, 7 N/A** (out of 18 gates; 7 N/A not counted)

**Failures requiring action before implementation:**

1. **CHK001** — Add explicit form-factor scope statement to spec. Suggested addition in Assumptions: "This refactoring is form-factor-agnostic — domain ports (`Provider<T>`, `InteractionSink`) are platform-neutral; Android-specific adapters live in `androidMain`. iOS adapters are deferred (additive)."

2. **CHK007** — Add statement about `requiredModules`/`optionalModules` for the Preset format. If TASK-120 already established these fields, cite it. If the Preset format does not yet declare module requirements (because the launcher is a single-module app at this stage), state that explicitly: "Preset format does not use `requiredModules`/`optionalModules` at this stage — single-module delivery, revisit at TASK-127 (multi-preset) or when non-handheld form factor arrives."

**Both failures are advisory — they are documentation gaps, not architectural problems.** The underlying design is sound. Fix by adding two sentences to the spec before Phase 1 begins.
