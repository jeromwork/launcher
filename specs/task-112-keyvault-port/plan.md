# Implementation Plan: KeyVault Port Boundary — Cross-Platform Cryptographic Contract

**Branch**: `task-112-keyvault-port` | **Date**: 2026-07-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from [spec.md](spec.md) + [TASK-112 Decision block (revised 2026-07-14)](../../backlog/tasks/task-112%20-%20Decision-Cross-platform-IdentityVault.md)

## Summary

Introduce `KeyVault` port + `RecoveryStrategy` port in `:core:keys` domain layer as the single, misuse-resistant entry point to all cryptographic operations. Replace direct `KeyRegistry.derive(...).bytes` access with operation-on-vault API. Purpose-tagged newtypes prevent cross-purpose confusion. libsodium-kmp software layer for all crypto ops; Android Keystore only for `root_key` at rest. Cross-platform test vectors enforced as DoD.

## Technical Context

**Language/Version**: Kotlin 2.0+, JVM target 17
**Primary Dependencies**: libsodium-kmp (existing, `:core:crypto` wraps), AndroidX Security-Crypto (`EncryptedSharedPreferences` + `MasterKey` for root_key at-rest storage)
**Storage**: Android Keystore (hardware-backed where StrongBox available) for `root_key`. NO storage for derived keys — они computed on demand via libsodium `crypto_kdf`.
**Testing**: `kotlin.test` common tests, `junit4` Android instrumented tests, `roborazzi` not needed (no UI in TASK-112)
**Target Platform**: `commonMain` (KMP) → Android now, iOS via TASK-26 (parking-lot), HarmonyOS/desktop hypothetical
**Project Type**: KMP library module (`:core:keys` — pure domain + adapter subfolder)
**Performance Goals**: `aeadSeal(1KB)` ≤ 5ms JVM P95; `aeadOpen(1KB)` ≤ 5ms; `Argon2id V1` recovery ≤ 3s P95 (unchanged from TASK-6 UX expectation)
**Constraints**: Zero vendor SDK imports in `:core:keys` (rule 1); byte-equal cross-platform test vectors (Android+JVM+Fake); backward-compat с existing TASK-6 ciphertext blobs (via schema-version migration path).
**Scale/Scope**: 5 implementation phases, ~1 week estimate. ~10 new Kotlin files + 3-5 migrated call sites + 5+ test files + fixture JSON.

## Constitution Check

*GATE: Must pass before Phase 0. Re-check after Phase 1.*

Per `.specify/memory/constitution.md` Article XVI (8 mandatory gates):

| Gate | Requirement | TASK-112 Compliance | Notes |
|---|---|---|---|
| Architecture | Domain isolated (rule 1), ACL wrapping (rule 2) | ✅ | `KeyVault` port lives in `family.keys.api`; adapters in `family.keys.impl.*`; zero vendor imports in domain (fitness rule) |
| Core/System Integration | No transport types in domain, no static singletons | ✅ | `KeyVault` = DI-injected interface, no static state; blob format = internal domain type, not exposed as transport DTO |
| Configuration | Wire-format versioning (rule 5) | ✅ | Blob header carries `format_version` (currently 0x01); Argon2Params versioned (V1 frozen); test vectors JSON has version field |
| Required Context Review | Consulted CLAUDE.md rules 1-13, all Session 1-6 discussion, docs/architecture/crypto.md, TASK-6/25/100/104/115/122/124 dependencies | ✅ | Session 6 explicit — revised Decision incorporates external review, mentor deliberation, source verification |
| Accessibility | If UI touched — TalkBack, contrast, senior-safe | N/A | TASK-112 has zero UI surface. Downstream TASK-6 UI unchanged (regression test SC-005) |
| Battery/Performance | Cold start, frame budget, background work | ✅ | Sync API (Dispatchers.IO wrap by caller); no background service; Argon2id runs once at unlock, not per-op |
| Testing | Fake adapter, contract test, roundtrip test (rule 6, 7) | ✅ | FR-014 mandates `FakeKeyVault`; SC-004 cross-platform test vectors; port contract tests per method |
| Simplicity (MVA rule 4) | Only abstractions needed today | ✅ | Purpose enum = 2 variants (not 5+); no `SigningPort` separate from `KeyVault` (would be premature); `RecoveryStrategy` justified by TASK-6 evolution reality |

**Verdict**: PASS all 8 gates. No premature abstractions, no domain leakage, backward compat plan explicit.

## Project Structure

### Documentation (this feature)

```text
specs/task-112-keyvault-port/
├── plan.md              # This file
├── spec.md              # Feature specification
├── tasks.md             # Phase 2 tick-sync checklist
├── analyze-report.md    # Pre-implementation audit
└── checklists/          # (populated during clarify/analyze if triggered)
```

*No `research.md` — Session 1 research lives in [TASK-112 Discussion](../../backlog/tasks/task-112%20-%20Decision-Cross-platform-IdentityVault.md#L126-L169).*
*No `data-model.md` — Key entities documented in [spec.md § Key Entities](spec.md#key-entities).*
*No `contracts/` folder — API contract IS the Kotlin interface in Decision block; test vectors JSON is the wire-format contract.*
*No `quickstart.md` — usage documented inline in `KeyVault` KDoc.*

### Source Code (repository)

```text
core/keys/
├── build.gradle.kts                          # KMP module config (already exists)
├── src/
│   ├── commonMain/kotlin/family/keys/
│   │   ├── api/
│   │   │   ├── KeyVault.kt                   # PORT (interface + newtypes + Purpose enum)
│   │   │   ├── RecoveryStrategy.kt           # PORT (RecoveryStrategy interface)
│   │   │   ├── VaultException.kt             # Sealed exception hierarchy
│   │   │   └── Aad.kt                        # Value class + canonicalAad(...) helper
│   │   └── impl/
│   │       ├── PassphraseRecovery.kt         # Adapter (matches TASK-6)
│   │       ├── Argon2Params.kt               # Versioned KDF parameters (V1 frozen)
│   │       ├── BlobHeader.kt                 # internal — header pack/unpack (magic + format_version + purpose_id + key_epoch + nonce)
│   │       └── RootKey.kt                    # internal — 32 bytes, never crosses port
│   ├── androidMain/kotlin/family/keys/impl/
│   │   ├── AndroidKeyVault.kt                # Android adapter (Keystore + libsodium-kmp)
│   │   └── AndroidRootKeyStorage.kt          # internal — EncryptedSharedPreferences wrapper
│   ├── commonTest/kotlin/family/keys/
│   │   ├── FakeKeyVault.kt                   # Fake adapter (deterministic in-memory)
│   │   ├── KeyVaultContractTest.kt           # Port contract tests
│   │   ├── PurposeEnforcementTest.kt         # WrongPurpose exception coverage
│   │   ├── BlobHeaderTest.kt                 # Header pack/unpack roundtrip
│   │   ├── AadCanonicalTest.kt               # AAD length-prefix layout
│   │   ├── RecoveryStrategyTest.kt           # PassphraseRecovery + TestRecoveryStrategy plug-in
│   │   ├── CrossPlatformVectorTest.kt        # v1.json vectors verification
│   │   └── resources/
│   │       ├── vectors/v1.json               # Cross-platform test vectors
│   │       └── fixtures/task-6-legacy.bin    # Pre-migration ciphertext for backward compat
│   ├── androidInstrumentedTest/kotlin/family/keys/
│   │   ├── AndroidKeyVaultIntegrationTest.kt # Real Android Keystore + libsodium via JNI
│   │   └── CrossPlatformVectorAndroidTest.kt # Same vectors as commonTest, on device
│   └── androidUnitTest/                      # (existing tests continue to pass)

core/config/                                   # (existing, migrated to KeyVault)
├── ConfigCipher2.kt                          # MIGRATED: uses keyVault.aeadSeal/aeadOpen
└── EnvelopeStorage.kt                        # MIGRATED
```

**Rule 1 domain isolation** enforced via detekt custom rule (or lint rule) — `:core:keys` MUST NOT import `com.google.*`, `android.*`, feature modules. Verified via `./gradlew :core:keys:detekt`.

## Implementation Phasing (5 phases)

### Phase 1 — Port + Fakes + Contract Tests (2 days)

**Deliverable**: `KeyVault` interface + `RecoveryStrategy` + all newtypes + `Purpose` enum + sealed `VaultException` + `FakeKeyVault` + full contract test suite (WITHOUT real Android adapter).

**Files created**:
- `commonMain/api/KeyVault.kt`, `RecoveryStrategy.kt`, `VaultException.kt`, `Aad.kt`
- `commonMain/impl/PassphraseRecovery.kt`, `Argon2Params.kt`, `BlobHeader.kt`, `RootKey.kt`
- `commonTest/FakeKeyVault.kt`, `KeyVaultContractTest.kt`, `PurposeEnforcementTest.kt`, `BlobHeaderTest.kt`, `AadCanonicalTest.kt`, `RecoveryStrategyTest.kt`
- `commonTest/resources/vectors/v1.json` (fixture)

**Verification**: `./gradlew :core:keys:test` — все new tests зелёные. `FakeKeyVault` детерминистичен, `TestRecoveryStrategy` работает как plug-in.

**Blocker for phase-2**: none (phase-1 не касается Android adapter).

**Rule compliance**:
- Rule 1 (domain isolation): no Android imports.
- Rule 6 (mock-first): fake adapter first, real adapter phase-2.
- Rule 4 (MVA): Purpose enum = 2 variants only.

### Phase 2 — Android Adapter (1.5 days)

**Deliverable**: `AndroidKeyVault` adapter, wrapping libsodium-kmp for crypto ops + Android Keystore (`EncryptedSharedPreferences`) for `root_key` at rest.

**Files created**:
- `androidMain/impl/AndroidKeyVault.kt`
- `androidMain/impl/AndroidRootKeyStorage.kt` (internal)
- `androidInstrumentedTest/AndroidKeyVaultIntegrationTest.kt`
- `androidInstrumentedTest/CrossPlatformVectorAndroidTest.kt`

**Verification**: `./gradlew :core:keys:connectedAndroidTest` на `pixel_5_api_34` — real Android Keystore + libsodium через JNI. Cross-platform vector test проходит byte-equal с commonTest.

**Blocker for phase-3**: phase-2 должна закрыть SC-004 (cross-platform vectors) до старта миграции call sites.

**Rule compliance**:
- Rule 2 (ACL): `AndroidKeyVault` — единственное место где Android Keystore SDK импортируется в `:core:keys`.
- FR-010: Android Keystore используется ТОЛЬКО для root_key at rest, ни в одной другой операции.

### Phase 3 — Migrate Call Sites (1.5 days)

**Deliverable**: `ConfigCipher2` и `EnvelopeStorage` мигрированы на `KeyVault.aeadSeal/aeadOpen`. DI wired up (existing DI framework? Manual constructor per CLAUDE.md manual-DI-in-MVP).

**Files modified**:
- `core/config/ConfigCipher2.kt` — replaces direct `DerivedKey.bytes` access.
- `core/config/EnvelopeStorage.kt` — same.
- `app/src/.../MainApplication.kt` (или DI wiring point) — provides `KeyVault` singleton через `AndroidKeyVault`.
- Backward-compat test: `commonTest/BackwardCompatTest.kt` reads `fixtures/task-6-legacy.bin` blob и verify decryption works через new code path.

**Verification**: `./gradlew :app:testMockBackendDebugUnitTest` — existing TASK-6 tests зелёные. `./gradlew :core:cloud:test` — config sync тесты зелёные (используют ConfigCipher2 под капотом).

**Blocker for phase-4**: SC-002 (backward compat with TASK-6 blobs) должен пройти до начала downgrade.

### Phase 4 — Downgrade RootKey + KeyRegistry (1 day)

**Deliverable**: `RootKey` public class → `internal class family.keys.impl.RootKey`. `KeyRegistry` public port → `internal helper`. Все feature-модули не могут больше вызывать `RootKey(bytes)` или `keyRegistry.derive(...)`.

**Files modified**:
- `commonMain/impl/RootKey.kt` — `internal` visibility.
- `commonMain/impl/KeyRegistry.kt` — moved from `api/` to `impl/`, `internal` visibility.
- Возможные callers в других модулях (`:core:cloud`, `:core:push`) — grep и refactor если найдутся (spec.md SC-001).

**Verification**: `./gradlew build` — compile error где-то, если остались external callers. Fix all. `./gradlew :core:keys:detekt` — fitness rule зелёный.

### Phase 5 — Cleanup + PR (0.5 days)

**Deliverable**: PR-ready state. `pre-pr-backlog-sync` skill run. Backlog task-112 status → `Verification` или `Done` (Verification если manual gates остались; Done если всё автомат зелёное).

**Files touched**:
- Backlog task-112 file: AC secion regenerated (auto:checklist counts + hand items).
- PR-DRAFT.md: summary + verification checklist.
- `docs/architecture/crypto.md`: registry table updated (`IdentityVault port boundary` row → status Done, link to Decision block).

**Verification**: `pre-pr-backlog-sync` skill runs cleanly. `./gradlew build check` — full green. Manual verification: install app on Xiaomi 11T (if device available) — recovery flow работает.

## Complexity Tracking

| Concept | Justification (Rule 4 MVA — не абстракция ради абстракции) |
|---|---|
| `RecoveryStrategy` port | TASK-6 done, but Bip39/2FA/social recovery near-certain within 12-24 months (Session 6 owner insight). Adding pluggable port now = zero-cost future extension. Without it, each new recovery mechanism = rewriting `KeyVault`. |
| Blob header (7 bytes + nonce vs raw ciphertext) | Crypto agility — format_version enables migration without breaking existing blobs. purpose_id in header = second-line defense (verified on aeadOpen). key_epoch = rotation format ready (impl deferred). Costs 7 bytes per blob (negligible for MVP-scale storage). |
| Cross-platform test vector fixtures | iOS via TASK-26 will validate against these vectors — without them, iOS adapter development = guessing about parity. Cheap now (5 JSON entries), expensive later. |
| Sealed VaultException instead of `Outcome<T, VaultException>` | Session 2 Q3 decision — Kotlin-idiomatic + FFI-friendly. TASK-113 Outcome refactor is orthogonal. |

**Anti-patterns rejected** (would fail rule 4):
- Separate `SigningPort` from `KeyVault` — nope, `sign / verify` fit natural boundary (identity operations part of vault).
- `KeyRegistry` port kept public «for backward compat» — nope, downgrade к internal per FR-008.
- Purpose as String parameter — nope, enum for compile-time enforcement per Decision.
- `AutoCloseable` on `DerivedKeyBytes` (from Session 2) — dropped, `exportDerivedKey` removed entirely (C2).

## Dependencies

- **Blocks** implementation: none (all research done, Decision block sealed, openmls verified).
- **Blocked by** downstream: TASK-6 (Done — provides existing recovery flow to preserve), TASK-2 (Done — provides libsodium-kmp wrapper in `:core:crypto`).
- **Unblocks** downstream: TASK-26 (iOS adapter can start), TASK-124 (openmls storage encryption path via `KeyVault.aeadSeal` clarified), TASK-11/27/28 (bucket AEAD).

## Progress Tracking (updated by /speckit.tasks + implementation)

- [ ] Phase 1 — Port + Fakes + Contract Tests
- [ ] Phase 2 — Android Adapter
- [ ] Phase 3 — Migrate Call Sites
- [ ] Phase 4 — Downgrade RootKey + KeyRegistry
- [ ] Phase 5 — Cleanup + PR

**Estimated total**: ~6.5 days сверху 1-week estimate из Decision block Session 1 (Session 1 не включал `RecoveryStrategy` extension port или cross-platform vectors — они добавили ~0.5-1 day).

## Post-Design Constitution Re-check

*Trigger: after Phase 1 design (fakes + interfaces) — verify no gates broke.*

- [ ] Rule 1 domain isolation still clean (grep imports)
- [ ] Rule 6 mock-first FakeKeyVault deterministic works cross-platform
- [ ] Rule 4 MVA — no dead abstractions added during design iteration
- [ ] SC-004 cross-platform vector test set at ≥5 cases
- [ ] Test vector JSON schema versioned
