# Checklist: meta-minimization (plan.md)

Applied to `specs/task-51-libsodium-consolidation/plan.md` + `data-model.md` + `research.md` on 2026-06-26.

TASK-51 is a **pure consolidation refactor** — it primarily *removes* abstractions (lazysodium adapters, `com.launcher.api.crypto.*` duplicate stack, `AndroidKeystoreSecureKeystore`, `Outcome<T, CryptoError>` wrapper) rather than adds them. New surface area is intentionally minimal.

---

## New abstractions

- [x] CHK001 Every new interface/port has at least one concrete consumer **in this spec** (not "in spec 008 we'll need it").
  - No new ports introduced. `cryptokit.pairing.api.*` types are **renames** of existing `com.launcher.api.crypto.*` types (15 types, byte-layout unchanged per FR-004). `DeviceIdentityRepository`, `EncryptedMediaStorage`, `RecipientResolver` already have production consumers (`PairingCryptoCoordinator`, `BackgroundReconciler`, `PairRecipientResolver`).
  - `CryptoException` 5 subclasses each have caller sites documented in data-model.md §1 ("Relationships" lists concrete vendor exceptions wrapped by each subclass).

- [x] CHK002 If a new interface has only one implementation: justified by port-shape need (DI, fakes, platform asymmetry) — not by "extensibility".
  - `SecureKeyStore` (already exists, not new) — expect/actual for Android Keystore TEE vs iOS Keychain (platform asymmetry, justified).
  - Pairing ports (`DeviceIdentityRepository` etc.) have Firestore production adapter + `FakeDeviceIdentityRepository` test adapter (DI + fake — justified per CLAUDE.md rule §6 mock-first).

- [x] CHK003 Mediator/orchestrator/manager class is justified by data transformation, not by pass-through.
  - No new orchestrator. `PairingCryptoCoordinator` exists, is being *rewritten* (not introduced) to drop `Outcome` wrapping; transforms domain inputs → libsodium calls → CryptoException flow — concrete data transformation.

- [x] CHK004 No custom DSL, registry, or plugin system unless simpler composition has been tried and documented as failing.
  - None introduced.

## New modules / packages

- [x] CHK005 New gradle module satisfies at least one of Article V §3 criteria.
  - No new gradle module. `:core:crypto` already exists (from spec 016). Plan reuses it.

- [x] CHK006 If new module is added: plan answers "Why is a package not enough?" explicitly.
  - N/A — no new module. `cryptokit.pairing.api.*` is a **package**, not a module (justified by deep-migration in R-001: 15 wire-format types architecturally distinct from crypto primitives, deserve own package within existing `:core:crypto`).

- [x] CHK007 No "utils" / "common" / "helpers" dumping ground module created.
  - None. `CryptoEnvelopeWireFormat` (constants object) is a single-purpose constants holder for spec 011 sizes (24-byte nonce, 32-byte X25519, etc.) — not a dumping ground.

## New configuration

- [x] CHK008 New config field has a current FR consuming it.
  - No new config fields. `schemaVersion` already exists (=1, unchanged). `@SerialName(...)` annotations added on existing types as binary-compat shield (FR-004 consumer).

- [x] CHK009 Config field defaults documented; backward-compat policy defined.
  - `schemaVersion: 1` documented in contracts and data-model.md. Backward-compat enforced by `EnvelopeConfigCipherRoundtripTest` golden vectors (SC-013). No migration needed (no schema change).

## CLAUDE.md rule 4 self-test

- [x] CHK010 **Test 1** applied: if abstraction were inlined, what would be lost?
  - `HashFunction` port: explicitly **rejected** in R-004 — would lose nothing if inlined (used in one debug activity), so plan uses inline `MessageDigest.SHA-256`. Correct application of Test 1.
  - 5 `CryptoException` subclasses: if inlined into single `CryptoException(category: String)`, would lose **type-based exhaustive `when` handling** (sealed hierarchy) and **per-category logging classification** (FR-017 backdoor-logging rule needs class-name discrimination). Justified.
  - Pairing ports: documented consumers already exist; inlining means coupling Firestore SDK into pairing flow — violates CLAUDE.md rule §1.

- [x] CHK011 **Test 2** applied: if dependency on the other side doubled in price / deprecated, how long to swap?
  - libsodium (ionspin) → alternative (Tink, BoringSSL): swap localised to `cryptokit.crypto.libsodium/` package; consumers depend on `cryptokit.crypto.api.*` only. Swap ≈ days, not weeks → seam justified (this is the *point* of TASK-51).
  - Firestore → own server: `DeviceIdentityRepository` / `EncryptedMediaStorage` ports already isolate Firestore SDK (rule §2 ACL). Same shape consumed by future own-backend adapter.

## Removal validation

- [x] CHK012 If spec removes existing abstractions/modules: dangling references in `docs/**`, `specs/**` audited.
  - Removals enumerated in plan §"Module map" (22 files in `com.launcher.api.crypto/` + 7 lazysodium adapters + 8 old fakes + `AndroidKeystoreSecureKeystore`) and data-model.md §3 (7 deleted types).
  - Fitness functions enforce no dangling code references: `NoLazysodiumInProductionTest`, `NoLegacyComLauncherCryptoTest`, `NoLegacyFamilyNamespaceTest`, `Spec011IsolationTest` ban-list update, `Spec014IsolationTest` ban-list update.
  - Gap: plan does not explicitly schedule a grep audit over `docs/**` / `specs/**` (only over code). Minor — historical specs (016, 011, 018) will still reference `family.*` / `com.launcher.api.crypto.*` namespaces in narrative; this is acceptable historical record, not dangling code. Not blocking.

- [x] CHK013 If spec marks code "deprecated, will remove later" — concrete removal task exists.
  - Plan does not introduce any "deprecated, will remove later" code — everything is deleted **in this task** (Phase 6). Two `TODO(post-task-6)` markers in data-model.md §4 reference TASK-6 (Root Key Hierarchy) for replacing silent migration with derive-from-root; that is a concrete task ID in backlog, not "eventually".

---

## Summary

**13/13 CHK [x]**. Plan is anti-bloat compliant:
- Zero new gradle modules.
- Zero new ports introduced (renames + 5-subclass sealed hierarchy with explicit per-class consumers).
- `HashFunction` port explicitly rejected with R-004 rationale (textbook CLAUDE.md rule §4 application).
- `AndroidKeystoreSecureKeystore` deleted, not rewritten (R-007).
- Single Koin module (R-005), no premature split.
- All "future flexibility" arguments (iOS readiness, library extraction) are tied to concrete future tasks (TASK-26, parking-lot extraction task) with `references:` paths, not vague "later".
- One minor gap: explicit `docs/**` / `specs/**` grep audit for namespace references not scheduled, but historical docs naturally retain old names and code-side enforcement is covered by Konsist.

meta-minimization-plan: 13/13 CHK [x]
