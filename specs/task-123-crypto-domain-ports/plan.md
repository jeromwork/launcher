# Implementation Plan: F-CRYPTO — domain ports + fakes (TASK-123)

**Spec**: [spec.md](spec.md) · **Backlog**: TASK-123 · **Branch**: `task-123-crypto-domain-ports`
**Arch-packs (authoritative)**: [crypto-mls.md](../../docs/architecture/crypto-mls.md) (port shape = Wire CoreCrypto), [crypto-key-hierarchy.md](../../docs/architecture/crypto-key-hierarchy.md) (KeyVault boundary), [crypto-primitives.md](../../docs/architecture/crypto-primitives.md) (P3/P4).

## 1. Overview

Write three pure-Kotlin domain ports (`CryptoPort` / `GroupPort` / `KeyPackagePort`) + in-memory fakes + reusable contract tests + an import-ban fitness rule, all in `:core:crypto`. Zero Rust/openmls/server — the real openmls adapter and persistence are TASK-124/125 (mock-first, rule 6). The port shape mirrors Wire `CoreCrypto`/`Conversation` (two-phase MLS commit) so the future real adapter is a thin wrapper and the contract tests do not get rewritten (SC-005).

## 2. Architecture

### Module & package map (all in existing `:core:crypto`; no new module)

| Path | Source set | Contents |
|---|---|---|
| `family.crypto.ports` | `commonMain` | interfaces `CryptoPort`, `GroupPort`, `KeyPackagePort` + port value/result types (`IdentityKey`, `KeyPackage`, `Commit`, `CommitBundle`, `ProcessedMessage`, `ClaimResult`, `GroupId`, `KeyPackageId`, `LastResortKey`) |
| `family.crypto.fake` | `commonTest` | `FakeCryptoPort`, `FakeGroupPort`, `FakeKeyPackagePort` (reuse the EXISTING `family.crypto.fake` package — the spec's `family.crypto.fakes` is corrected to the singular already used by primitive fakes) |
| `family.crypto.contracts` | `commonTest` | abstract `CryptoPortContract` / `GroupPortContract` / `KeyPackagePortContract` with an abstract factory method (`abstract fun create<Port>(): <Port>`); the fake subclass provides the fake now, TASK-124's real subclass (in `androidUnitTest`, which sees `commonTest` via the KMP source-set hierarchy) provides the real adapter later — no test code moves (SC-005) |
| `family.crypto.ports` reuse | `commonMain` | `Ciphertext` is **reused** verbatim from `family.crypto.api.values.Ciphertext` (`@JvmInline value class(bytes)`, no `@Serializable`) — FR-006, no second type |

### Data flow (one layer — pure domain)

```
consumer (messenger TASK-42 / pairing TASK-67)
    │  calls
    ▼
family.crypto.ports  (interfaces + opaque value types)   ← commonMain, no vendor/serialization
    │  bound in tests to
    ▼
family.crypto.fake   (in-memory, deterministic)          ← commonTest
    ▲
    └── later: real openmls adapter (TASK-124, androidMain) — same interface, verified by the same contracts
```

Arrows point one way (consumer → port → impl); no impl type leaks upward (rule 1). openmls/uniffi/android live only in the future TASK-124 adapter, never in `family.crypto.ports`.

### Port surface (from FR-009, Wire CoreCrypto two-phase shape)

- **`GroupPort`**: `createGroup(GroupId)`; `addMembers(GroupId, List<KeyPackage>) → CommitBundle`; `removeMembers(GroupId, List<IdentityKey>) → CommitBundle`; `selfUpdate(GroupId) → CommitBundle`; `commitToPendingProposals(GroupId) → CommitBundle?`; `mergePendingCommit(GroupId)`; `processMessage(GroupId, ByteArray) → ProcessedMessage`.
- **`CryptoPort`**: `encryptMessage(GroupId, ByteArray) → Ciphertext`; `decryptMessage(GroupId, Ciphertext) → ByteArray` (thin wrapper over `processMessage`'s `ApplicationMessage`; final shape locked here).
- **`KeyPackagePort`**: `publish(List<KeyPackage>, isLastResort: Boolean)`; `claim(target: IdentityKey) → ClaimResult` (`Claimed(KeyPackage, isLastResort)` | `Empty`); `localCount() → Int` (drives client refill below `refillThreshold`).

All methods `suspend` (openmls ops are blocking; the real adapter wraps in `Dispatchers.IO` — the port stays suspend so the contract is stable). Authorization (who-may-add) is NOT a port method — application policy (ML6, crypto-pairing.md, TASK-102).

## 3. Data model

New opaque domain value/result types → [data-model.md](data-model.md). All are plain `value class` / `data class` / `sealed` over `ByteArray`/`String`, **no `@Serializable`** (FR-002; `:core:crypto` carries zero serialization since TASK-146). Wire encoding of these types is the TASK-124 adapter's job (DTOs).

## 4. Wire formats

**None in this spec.** The port value types are opaque in-memory holders; their on-wire encoding (KeyPackage/Commit bytes, DTO + `schemaVersion`) is owned by the real adapter (TASK-124) and governed by [wire-format.md](../../docs/architecture/wire-format.md). No `contracts/` file is produced here (nothing leaves the device in this spec).

## 5. Dependency impact

- **No new production dependency.** Ports are stdlib + `kotlinx.coroutines` (already present).
- **Test-only**: `konsist 0.17.3` (already in `gradle/libs.versions.toml`) for the import-ban fitness rule (FR-005). No serialization plugin re-added (stays removed per TASK-146).

## 6. Test strategy (rule 6 + rule 7)

1. **Contract tests** (`family.crypto.contracts`, `commonTest`) — 5–7 invariants per port (FR-004), abstract + factory-method pattern, green on fakes now, reused by TASK-124's real adapter. Invariants from spec §Edge Cases: empty-pool→`Empty`; unknown `GroupId`→deterministic error; double `createGroup`→error; cross-group decrypt→error; forward-secrecy (post-`encrypt`+`merge`, prior epoch key unreproducible on fake via monotonic counter); `processMessage` returns correct `ProcessedMessage` variant.
2. **Fakes** (`family.crypto.fake`, `commonTest`) — deterministic in-memory; `FakeCryptoPort` intentionally insecure (xor + counter-ratchet, spec Assumption) — satisfies the contract, not real crypto.
3. **Consumer snippet** (`core/crypto/README.md`) — minimal `createGroup → addMembers → encrypt → decrypt` on fakes, compiled as a `commonTest` example (FR-010, SC-001/SC-004).
4. **Fitness rule (konsist)** — a `commonTest` konsist test: no file in package `family.crypto.ports` imports `openmls`, `uniffi`, `cryptokit`, `android.`, `okhttp`, `firebase` (FR-005, SC-003). Template: `NoLegacyFamilyNamespaceTest` (`:core` fitness). Fails on a planted import, green without.
5. **Verification command**: `./gradlew :core:crypto:commonTest` (all of the above run on JVM, no device/network — SC-002).

## 7. Risks

| Risk | Mitigation |
|---|---|
| Fake diverges from openmls two-phase semantics → contract tests don't protect the real adapter (SC-005) | Port shape locked to Wire CoreCrypto (FR-009); fake models pending/merge + monotonic epoch; TASK-124 extends the SAME abstract contracts. |
| `family.crypto.fake` (singular) vs spec `fakes` (plural) drift | Reconciled here → use existing singular `fake` package; spec text corrected. |
| Konsist import-ban misses a source root or false-passes | Test asserts BOTH directions: planted forbidden import fails, clean tree passes (SC-003). |
| Someone re-adds `@Serializable` to a port type → reintroduces serialization plugin to `:core:crypto` (reverses TASK-146) | FR-002 explicit; `NoLegacyFamilyNamespaceTest` + review; no serialization plugin in `:core:crypto` build (compile-fails `@Serializable`). |
| `suspend` on ports over-engineered if fakes are synchronous | Kept deliberately — the REAL adapter (TASK-124) is blocking-wrapped-in-IO; a non-suspend port would force a breaking change then (rule 4). |

## 8. Required Context Review

- [crypto-mls.md](../../docs/architecture/crypto-mls.md) — port shape (Wire CoreCrypto), ML1–ML6 invariants, KeyPackage lifecycle.
- [crypto-key-hierarchy.md](../../docs/architecture/crypto-key-hierarchy.md) — KeyVault export-hatch boundary (why `KeyVault` is NOT a port param, FR-008).
- [crypto-primitives.md](../../docs/architecture/crypto-primitives.md) — P3 (no serialization), P4 (typed keys → `IdentityKey`).
- [crypto.md](../../docs/architecture/crypto.md) — zone map (this is the "designed→built" transition of the ports row).
- [CLAUDE.md](../../CLAUDE.md) — rule 1 (isolation), rule 4 (MVA), rule 6 (mock-first), rule 7 (fitness), rule 11 (crypto no-version/no-serialization exception).
- TASK-141 (`NoLegacyFamilyNamespaceTest`), TASK-146 (serialization removed from `:core:crypto`), TASK-112 (KeyVault boundary — frozen assumption).

## 9. Constitution Check

_Generated by `procedure-constitution-check` — inlined below._

<!-- CONSTITUTION-CHECK:BEGIN -->
| Gate | Verdict | Justification |
|---|---|---|
| 1 Architecture | **PASS** | Change in existing `:core:crypto`; no new module; ports are not speculative — rule 6 mock-first + consumers (TASK-42/67) blocked without them. |
| 2 Core/System Integration | **N/A** | No system events / broadcasts / lifecycle — pure `commonMain` Kotlin. |
| 3 Configuration | **N/A** | No profiles / schema / migrations. No wire format in this spec (deferred to TASK-124), so absent `schemaVersion` is correct, not a violation. |
| 4 Required Context Review | **PASS** | §8 links crypto-mls / key-hierarchy / primitives / crypto.md + CLAUDE.md rules 1/4/6/7/11 + TASK-141/146/112. |
| 5 Accessibility | **N/A** | No UI (spec §AI Affordance / §OEM Matrix declare pure-Kotlin, no device behavior). |
| 6 Battery/Performance | **N/A** | No background work / polling / boot / package-change; JVM-only tests. |
| 7 Testing | **PASS** | Contract tests (abstract+factory, reused by TASK-124), fakes, consumer snippet, konsist fitness, `./gradlew :core:crypto:commonTest`. Rule 6: fake present; real adapter + DI = TASK-124 (the mock-first split itself). |
| 8 Simplicity | **PASS** | Rule 4 Test 1: inline the ports → consumers can't build/test without openmls (blocked). Test 2: openmls→mls-rs swap behind the port ≈ 1–2 wk. `IdentityKey` (no reuse possible) and `suspend` (real adapter is blocking) both warranted, not speculative. |

**OVERALL: 4 PASS, 4 N/A, 0 FAIL — plan is COMPLETE.**
<!-- CONSTITUTION-CHECK:END -->

## 10. Rollout / verification

- **Done when**: `./gradlew :core:crypto:commonTest` green (contract + fakes + consumer snippet); konsist fitness fails-on-planted-import / passes-clean; no `@Serializable` / vendor import in `family.crypto.ports`; `Ciphertext` reused (no second type); `GroupState` never returned; `KeyVault` not imported in `commonMain`.
- **No device / emulator / network** — pure JVM (spec §Local Test Path).
- **Hands off to**: TASK-124 (real openmls adapter extends the same contracts) — the contract suite is the acceptance gate there too.

---

## Для новичка (простыми словами)

Мы пишем **три «розетки» (интерфейса)** для крипты и **их заглушки-пустышки**, работающие в памяти. Ничего настоящего (Rust, openmls, сервер) — только форма контракта. Зачем: чтобы разработчики мессенджера и pairing начали писать и тестировать свой код **уже сейчас**, не дожидаясь настоящего крипто-движка (он подключится позже, задача TASK-124, в ту же розетку).

Ключевое из плана:
- **Ничего не меняется в коде продукта, кроме нового набора файлов** в модуле `:core:crypto` (интерфейсы + тесты). Ни устройства, ни экрана, ни сети — всё проверяется одной командой `./gradlew :core:crypto:commonTest`.
- **Форма розеток скопирована с Wire** (реальный мессенджер на openmls) — чтобы настоящий движок потом лёг тонкой обёрткой, а тесты не пришлось переписывать.
- **Машина сама следит за чистотой**: специальная проверка «падает», если кто-то потащит в эти файлы запрещённое (openmls, Android, сеть).
- **Конституция проекта пройдена**: 4 гейта «применимо и ок», 4 «не относится» (нет UI/сервера/фона), ноль провалов.

Следующий шаг — `/speckit.tasks` (разбить план на конкретные задачи-шаги для написания кода).
