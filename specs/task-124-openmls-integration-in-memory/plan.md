# Implementation Plan: openmls integration — real MLS adapter (in-memory)

**Branch**: `task-124-openmls-integration-in-memory` | **Date**: 2026-07-24 | **Spec**: [spec.md](spec.md)
**Backlog**: TASK-124 (deps TASK-122, TASK-123) | **Arch-pack (read-truth)**: [crypto-mls.md](../../docs/architecture/crypto-mls.md)

## Summary

Replace the TASK-123 fake MLS adapters with a real `openmls` (v0.8.1) engine, bridged to Kotlin via the existing proc-macro UniFFI crate `:crypto-ffi` (TASK-122). Kotlin adapters in `family.crypto.mls` implement `CryptoPort`/`GroupPort`/`KeyPackagePort` unchanged. Storage is an in-memory `StorageProvider` (SQLCipher persistence = TASK-125). The FFI boundary is **stateless**: each call passes a serialized `StorageProvider` snapshot in/out (openmls `MlsGroup` is not serializable in 0.8.x — reconstructed via `MlsGroup::load`).

## Technical Context

**Language/Version**: Rust 1.97.0 (crypto-ffi), Kotlin (KMP, androidMain)
**Primary Dependencies**: `openmls =0.8.1`, `openmls_traits 0.5.0`, `openmls_rust_crypto 0.5.1`, `uniffi 0.28.3` (existing), `jna 5.14.0` (existing runtime for UniFFI binding)
**Storage**: in-memory `HashMap` behind openmls `StorageProvider` trait (no persistence — TASK-125)
**Testing**: `androidUnitTest` (contract + roundtrip + property, kotest-property Arb) on host-native lib; emulator smoke `pixel_5_api_34`
**Target Platform**: Android arm64-v8a (iOS = stubs, TASK-26)
**Project Type**: KMP library module (`:core:crypto`) + Rust FFI crate (`:crypto-ffi`)
**Performance Goals**: not a hot path; property test (100 sequences) must complete in CI-reasonable time (< ~60s target, not a hard gate)
**Constraints**: no openmls/uniffi type leaks above `family.crypto.mls`; commonMain stays vendor-free (fitness-gated)
**Scale/Scope**: ~10-15 Rust FFI verbs, 3 Kotlin adapters, ~30-50h effort

## Architecture

**No new module.** Reuse `:core:crypto` (ports + androidMain adapters) and `:crypto-ffi` (Rust). New link: `:core:crypto` androidMain → `:crypto-ffi`.

```
family.crypto.ports (commonMain, TASK-123)     ← unchanged, vendor-free (fitness-gated)
        ▲ implements
family.crypto.mls (androidMain, NEW)           ← OpenMlsGroupPort / OpenMlsCryptoPort / OpenMlsKeyPackagePort
        │  holds MutableMap<GroupId, ByteArray> (StorageProvider snapshot per group)
        ▼ calls uniffi.crypto_ffi (generated Kotlin)
:crypto-ffi Rust (#[uniffi::export], NEW mls.rs + storage.rs)
        ▼
openmls 0.8.1 engine + openmls_rust_crypto + InMemoryStorageProvider (HashMap)
```

**Data flow (per group op)** — stateless FFI:
1. Kotlin adapter reads `snapshot: ByteArray` for `GroupId` from its in-memory map.
2. Calls Rust verb with `(snapshot, groupIdBytes, params…)`.
3. Rust: deserialize snapshot → build `OpenMlsProvider` over in-memory storage → `MlsGroup::load(provider, gid)` → perform op (openmls writes back into storage) → merge if applicable → serialize storage → return `(result, updatedSnapshot)`.
4. Kotlin writes `updatedSnapshot` back into the map, maps result → domain type.

Arrows point downward only (rule 1 visual check): ports → adapter → FFI → openmls. No openmls type climbs above `family.crypto.mls`.

## Data model

See [data-model.md](data-model.md). No persistent or wire-format types of ours: the snapshot is an internal in-memory `HashMap<Vec<u8>, Vec<u8>>` serialization; MLS messages are external RFC 9420 bytes. Domain result/value types are already defined (TASK-123).

## Wire formats

See [contracts/mls-ffi-surface.md](contracts/mls-ffi-surface.md).

- **MLS messages** (ciphertext / commit / welcome / KeyPackage) — **external RFC 9420 wire format**, raw `MlsMessageOut` bytes, **no custom envelope, no `schemaVersion` of ours** (Clarification #4; own MLS wire format rejected by arch-pack). Roundtrip test only (FR-015).
- **StorageProvider snapshot** — internal in-memory serialization, **unversioned** (Clarification #3). Becomes the versioned at-rest format at TASK-125 (SQLCipher) behind the same trait — *that* is when `wire-format.md` discipline applies.
- **FFI verb surface** — the `:crypto-ffi` interface contract (not a persisted wire format, but a versioned interface — UniFFI lockstep 0.28.3).

## Dependency impact

New Rust crates (Cargo, `:crypto-ffi`), all MIT/permissive per arch-pack extraction policy:
- `openmls =0.8.1` (MIT) — the engine.
- `openmls_traits 0.5.0` (MIT) — provider/storage traits.
- `openmls_rust_crypto 0.5.1` (MIT) — crypto + rand provider.

No new Kotlin/Gradle deps (uniffi 0.28.3 + jna already present, TASK-122). Justified per Article XIII: these are the imported crypto core (ML1 — we write only glue), not re-implementations. Exit ramp: `openmls → mls-rs` behind `GroupPort` (~1-2 weeks, arch-pack).

## Test strategy (CLAUDE.md §6 mock-first + §7 fitness)

- **Contract tests (reuse TASK-123)** — `OpenMlsGroupPort`/`OpenMlsCryptoPort`/`OpenMlsKeyPackagePort` subclass the existing abstract `*Contract` classes from `androidUnitTest` (KMP hierarchy sees commonTest). No test code moves (SC-001/SC-005-mechanism).
- **Roundtrip** — `MlsMessageRoundtripTest`, `GroupStateRoundtripTest` (FR-015, SC-002).
- **Property-based** — kotest-property Arb, 100 random sequences create/add/remove/encrypt/processCommit (SC-004).
- **Forward secrecy assertions** — double-encrypt → different ciphertexts; prior-epoch ciphertext undecryptable after `selfUpdate+merge` (SC-003).
- **Emulator smoke** — 3-member group, 10 messages, `pixel_5_api_34` (SC-005).
- **Fitness functions (§7)** — `PortsNoVendorImportTest` (no openmls/uniffi in `ports`), `verifyUniffiVersions` (0.28.3 lockstep), `assertNoFakeCryptoInRelease`.
- **Panic contract** — UniFFI panic-catcher active (TASK-122); skill `crypto-ffi-panic-check` on any new `#[uniffi::export]` verb with a non-throwing signature.

## Risks

| Risk | Mitigation |
|------|------------|
| Stateless snapshot pattern non-idiomatic → subtle state loss | Verified from openmls-v0.8.1 source: `MlsGroup::load` reconstructs from storage; the snapshot IS the storage. Roundtrip + property tests catch state loss. |
| `IdentityKey → LeafNodeIndex` resolution wrong member removed | Adapter resolves via group roster; unknown member → deterministic error (edge case). Contract test `removeMembers`. |
| Ephemeral signing key mistaken for production identity | Documented (Assumptions + Clarification #1); TASK-124 adapter not release-shippable alone (needs TASK-125). `// TODO(task-112)`. |
| openmls panic aborts process | UniFFI panic-catcher (TASK-122); non-throwing verb signatures so panic → Kotlin exception. |
| `create_message` errors on pending proposals | FR-009: adapter guarantees clean state before encrypt. Edge case + contract. |
| Host-lib build for JVM unit tests (Windows) | Reuse TASK-122 `buildRustHostLibrary` + `generateUniffiBindings`. |
| OEM `.so` load (arm64) | Emulator smoke; arm64-only abiFilter (TASK-122). |

## Required Context Review

- [CLAUDE.md](../../CLAUDE.md) rules 1 (domain isolation), 2 (ACL), 4 (MVA — no new module), 5 (wire format), 6 (mock-first), 7 (fitness).
- [crypto-mls.md](../../docs/architecture/crypto-mls.md) — MLS zone read-truth (verified FFI shape, invariants ML1-ML6).
- [crypto-primitives.md](../../docs/architecture/crypto-primitives.md) — two-primitive-stack rule (openmls Rust prims ≠ our libsodium).
- [crypto-key-hierarchy.md](../../docs/architecture/crypto-key-hierarchy.md) — where the real signing key derives from (deferred binding).
- [crypto-pairing.md](../../docs/architecture/crypto-pairing.md) — who-may-add policy (not in this task; ML6).
- [wire-format.md](../../docs/architecture/wire-format.md) — applies at TASK-125, not here.
- [extraction-policy.md](../../docs/architecture/extraction-policy.md) — permissive-license constraint for shipped crypto.
- ADR relevance: none new (crypto stack already decided in arch-pack).

## Constitution Check

*GATE: must pass before tasks. Generated by `procedure-constitution-check` — inlined below.*

<!-- CONSTITUTION-CHECK:BEGIN -->
```
CONSTITUTION CHECK for specs/task-124-openmls-integration-in-memory/plan.md:
  Gate 1 Architecture           : PASS — no new module (reuse :core:crypto + :crypto-ffi); ports/adapter shape explicit; family.crypto.mls boundary minimal.
  Gate 2 Core/System Integration: N/A  — no system events, broadcasts, boot, or lifecycle; pure crypto lib + FFI.
  Gate 3 Configuration          : PASS — no profile/schema/migration; MLS payloads are external RFC 9420 bytes (no our schemaVersion, Clarif #4); in-memory snapshot unversioned by design (Clarif #3, versioned at TASK-125).
  Gate 4 Required Context Review : PASS — links CLAUDE.md rules 1/2/4/5/6/7, crypto-mls/primitives/key-hierarchy/pairing/wire-format/extraction-policy; no new ADR (crypto stack decided in arch-pack); no permission change → permissions-budget N/A.
  Gate 5 Accessibility          : N/A  — headless core/crypto, no UI surface.
  Gate 6 Battery/Performance    : PASS — no background work, no polling, no boot/package hooks; not a hot path; property-test time is a soft target.
  Gate 7 Testing                : PASS — contract (reuse TASK-123 abstract) + roundtrip + property + forward-secrecy + emulator smoke + fitness functions; real+fake adapters + DI wiring (FR-012).
  Gate 8 Simplicity             : PASS — no speculative abstraction; adapter implements existing ports; snapshot pattern required (not optional); ephemeral key defers rather than over-builds.

OVERALL: 6 PASS, 2 N/A, 0 FAIL — plan is COMPLETE.
```
<!-- CONSTITUTION-CHECK:END -->

## Project Structure

```text
specs/task-124-openmls-integration-in-memory/
├── spec.md
├── plan.md              # this file
├── research.md          # openmls FFI-shape decision (storage-snapshot)
├── data-model.md        # internal state shapes
├── contracts/
│   └── mls-ffi-surface.md   # the :crypto-ffi verb surface + snapshot contract
└── tasks.md             # (speckit-tasks, next)
```

### Source Code

```text
crypto-ffi/                                   # Rust (TASK-122), add MLS
├── Cargo.toml                                # + openmls / openmls_traits / openmls_rust_crypto
└── src/
    ├── lib.rs                                # + mod mls; mod storage;
    ├── mls.rs                                # NEW: #[uniffi::export] verb surface
    └── storage.rs                            # NEW: InMemoryStorageProvider

core/crypto/src/
├── commonMain/kotlin/family/crypto/ports/    # TASK-123 — UNCHANGED
├── androidMain/kotlin/family/crypto/mls/     # NEW adapters
│   ├── OpenMlsGroupPort.kt
│   ├── OpenMlsCryptoPort.kt
│   ├── OpenMlsKeyPackagePort.kt
│   └── (internal: SnapshotStore, LeafIndexResolver, mappers)
├── androidUnitTest/kotlin/family/crypto/mls/ # real-adapter contract subclasses + roundtrip + property
└── commonTest/                               # TASK-123 abstract contracts — UNCHANGED

app/.../di/                                   # backendModule (real/mock flavor) wires OpenMls*Port
```

**Structure Decision**: Extend two existing modules; **no new Gradle module** (rule 4 MVA — inlining the adapter into `:core:crypto` androidMain loses nothing; a separate `:core:crypto-openmls` module would be premature). Adapter package `family.crypto.mls` per arch-pack.

## Complexity Tracking

No constitution violations requiring justification (Constitution Check: 6 PASS, 2 N/A, 0 FAIL).

---

## TL;DR для новичка

**План простыми словами.** Не заводим новый модуль — расширяем два существующих: Rust-крипту (`:crypto-ffi`) и крипто-библиотеку (`:core:crypto`). В Rust добавляем настоящую MLS-машину `openmls`; в Kotlin — три адаптера в пакете `family.crypto.mls`, которые втыкаются в уже готовые «розетки» (порты) вместо игрушечных заглушек.

**Главное архитектурное решение** — как гонять состояние группы через мостик Rust↔Kotlin: возим **снимок хранилища** (не саму группу — её сохранить нельзя). Ровно туда потом встанет «долговременное» зашифрованное хранилище (TASK-125), ничего не ломая.

**Проверка качества пройдена**: конституция 6/8 PASS (2 неприменимы — нет UI, нет системных событий), чек-листы чистые. Тесты: старые контракты из TASK-123 гоняются на новом настоящем адаптере + forward-secrecy + property + дымовой на эмуляторе.

**Что дальше**: разбить план на конкретные задачи (`/speckit.tasks`).
