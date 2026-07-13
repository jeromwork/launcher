# Checklist: meta-minimization — TASK-122

Applied against `spec.md` + `plan.md` on 2026-07-13 during `speckit-analyze`.

## New abstractions

- [x] CHK001 UniFFI + cargo-ndk each have a concrete consumer in this spec (`hello()` / `panics()` exported functions + Kotlin androidTest). Not "for future".
- [x] CHK002 Neither abstraction is single-implementation-only for extensibility reasons; both are tooling for a real build pipeline used today.
- [x] CHK003 No mediator/orchestrator/manager class introduced. `crypto-ffi/build.gradle.kts` is a wiring file, not a pass-through class.
- [x] CHK004 No custom DSL / registry / plugin system.

## New modules / packages

- [x] CHK005 New Gradle module `:crypto-ffi` satisfies Article V §3: (a) ownership boundary (Rust FFI toolchain isolated from `:app`), (b) build isolation (cargo-ndk plugin scoped here, not global), (c) independent enable/disable (module can be commented out of `settings.gradle.kts` without touching `:app`), (d) material testability gain (androidTest for FFI runs in isolation).
- [x] CHK006 Plan §Constitution Check Gate 1 answers "why not a package inside `:app`?" — cross-compile toolchain would leak into `:app` build if inlined. Justified.
- [x] CHK007 No "utils"/"common"/"helpers" module.

## New configuration

- [x] CHK008 `rust-toolchain.toml` (single field: version=1.97.0 + target) has a current FR consuming it (NFR-001 — reproducible build).
- [x] CHK009 Config field defaults documented (Q3), bump path documented (FR-009 §UniFFI bump). Backward-compat: MSRV concept N/A per Q3.

## CLAUDE.md rule 4 self-test

- [x] CHK010 **Test 1 applied**: if UniFFI inlined → manual JNI per crypto task = 2-3 weeks each × 3 downstream tasks. Documented in Complexity Tracking. Loss > future-optionality.
- [x] CHK011 **Test 2 applied**: if UniFFI deprecated → exit ramp = 2-3 weeks manual JNI conversion per exported crate. >1 day → seam justified.

## Removal validation

- [x] CHK012 No removals in this spec. N/A.
- [x] CHK013 No "deprecated, will remove" markers. N/A.

## Findings

- **NONE**. Both introduced abstractions (UniFFI, cargo-ndk) are justified per rule 4 self-test with concrete exit-ramp cost. Complexity Tracking table in plan.md is exemplary.

**Verdict**: 13/13 PASS. No bloat detected. Two abstractions, both non-speculative.
