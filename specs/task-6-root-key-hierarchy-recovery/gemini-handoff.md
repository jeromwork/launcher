# Gemini Implementation Handoff — F-5 Root Key Hierarchy + Owner Recovery

> **You are Gemini in Antigravity, implementing this feature. Claude (the prior agent that planned this) will review every commit. Read this doc FULLY before touching any file. When unsure, STOP and ask Claude — do not guess.**

**Branch**: `task-6-root-key-hierarchy-recovery` (current; do not switch)
**Backlog**: TASK-6, status `In Progress`
**Spec set**: `specs/task-6-root-key-hierarchy-recovery/` — spec.md, plan.md, tasks.md, contracts/, data-model.md, research.md, quickstart.md, checklists/, this file.

---

## Read order (mandatory, ~30 min)

Read in this exact order. Each later doc assumes earlier docs absorbed.

1. **`CLAUDE.md`** (project root) — engineering rules 1-16. **Absolutely critical**:
   - Rule 1 (domain isolation): no vendor SDK in `core/keys/src/commonMain/`.
   - Rule 2 (ACL): every external dep wrapped in adapter.
   - Rule 5 (wire-format versioning): schemaVersion from day 1.
   - Rule 11 (tasks.md tick-sync): every implementation commit MUST add `[x]` next to closed `Tnnn` in the **same diff**. Refuse to commit without this.
   - Rule 14 (pre-PR backlog sync): NEVER run `gh pr create` yourself — STOP and ping Claude.
2. **`.specify/memory/constitution.md`** — Articles I-XIX. **Critical**: Article XIV §7 (server-side data minimization, added 2026-06-28). Don't skim — read §7 clauses (a)-(e).
3. **`docs/adr/ADR-011-ai-owner-collaboration-conventions.md`** — sequences inline in spec.md, MENTOR-DETAIL blocks for owner. Skip MENTOR-DETAIL blocks when reading spec.md unless owner asks for an explanation.
4. **`docs/dev/server-roadmap.md`** §SRV-RECOVERY-001 — Worker MVP + own-server migration path.
5. **`spec.md`** — feature requirements. Skip MENTOR-DETAIL blocks under sequences.
6. **`plan.md`** — architecture. **WARNING**: §Project Structure has minor discrepancies with actual codebase; see this handoff §«Existing code reality» below.
7. **`data-model.md`** — domain types. Authoritative for types/invariants.
8. **`contracts/recovery-key-backup-v1.md`** — JSON wire format.
9. **`contracts/worker-api-v1.md`** — HTTP API for `workers/backup/`.
10. **`research.md`** — one-way door decisions with regret conditions.
11. **`tasks.md`** — your execution plan. 86 tasks, T600-T686.
12. **`quickstart.md`** — dev commands.
13. **`analyze-report.md`** — final pre-implementation audit (96% PASS, known open items tracked).

---

## Existing code reality (where plan.md and codebase diverge)

**The plan and tasks were written without reading current `main` thoroughly. Some adjustments required.**

### 1. `core/keys/` module ALREADY EXISTS

It was shipped by spec 018 / TASK-4 (F-5b config E2E encryption) and lives on `main`. Contains:

```
core/keys/src/commonMain/kotlin/family/keys/api/
├── AuthIdentity.kt           ← KEEP — F-5 uses this same shape (local copy per CLAUDE.md rule 2 ACL)
├── CipherError.kt            ← KEEP — legacy F-5b error type
├── ConfigCipher2.kt          ← MIGRATE — FR-018 byte-equal refactor (use KeyRegistry.derive)
├── DeviceId.kt               ← KEEP
├── Envelope.kt               ← KEEP — wire format for encrypted config
├── EnvelopeBootstrap.kt      ← KEEP
├── IdentityError.kt          ← KEEP
├── IdentityProof.kt          ← KEEP — legacy F-5b port; F-5 adds AuthAvailability alongside, does NOT replace
├── internal/
│   ├── ConfigCipher2.kt      ← MIGRATE — see above
│   ├── EnvelopeStorage.kt    ← KEEP
│   ├── ... 10+ more files    ← KEEP
└── Module.kt                 ← KEEP

core/keys/src/androidMain/kotlin/family/keys/android/
├── AndroidDeviceIdentity.kt           ← KEEP
└── WorkManagerAsyncConfigPushQueue.kt ← KEEP
```

**Do tasks.md `T600` FIRST** to produce `inventory.md`. Do not touch any KEEP file. Do not delete anything.

### 2. Package conventions (actual, not plan's wording)

- **Domain ports + value types** → `family.keys.api` (Kotlin package). Path: `core/keys/src/commonMain/kotlin/family/keys/api/`.
- **Internal commonMain impls** → `family.keys.api.internal`. Path: `.../family/keys/api/internal/`.
- **Android adapters** → `family.keys.android`. Path: `core/keys/src/androidMain/kotlin/family/keys/android/`.

**Plan.md mentions `family/keys/impl/` paths — that's wrong**. New impl files go under `family.keys.android` (Android-specific) or `family.keys.api.internal` (KMP commonMain). When in doubt: if file uses libsodium / Android Keystore → `family.keys.android`. If pure-Kotlin → `family.keys.api.internal`.

### 3. `AuthProvider` lives in `:core` (main module), not `:core:auth`

Plan.md says module `core/auth` — it doesn't exist as separate module. Actual location: `core/src/commonMain/kotlin/com/launcher/api/auth/AuthProvider.kt`, package `com.launcher.api.auth`. Module: main `:core`.

For F-5, you use `family.keys.api.AuthIdentity` (local copy in `:core:keys`), NOT `com.launcher.api.auth.AuthIdentity`. The two are intentionally separate per CLAUDE.md rule 2 ACL. Mapping between them is done in `app/` adapters.

### 4. `AndroidKeystoreRegistry`, `Argon2RootKeyManager` placement

Plan.md says `core/keys/src/androidMain/kotlin/family/keys/impl/AndroidKeystoreRegistry.kt`. **Correct location**: `core/keys/src/androidMain/kotlin/family/keys/android/AndroidKeystoreRegistry.kt`. Package `family.keys.android`. Same correction for `Argon2RootKeyManager.kt` and `DeviceKeyNamespaceProvider.kt`.

---

## Danger zones — where weak models trip

These are the **non-obvious** mistakes I (Claude) predict you might make. Read each before implementing related task.

### DZ-1: Forbidden tokens in `core/keys/src/commonMain/` (Phase 1, T631)

A Konsist rule (T631) scans for these tokens in commonMain — test fails if any appear:
```
Google | Firebase | OAuth | Apple | Phone | Email | Sub | IdToken | Cloudflare | Worker | Bearer | JWT | HTTP | OkHttp | R2 | KV
```

**❌ NEVER** import or even mention these strings in `core/keys/src/commonMain/`. Even in KDoc comments? Avoid.

**✅ DO** put platform/transport details in adapter layer (`androidMain` or `app/`).

**Common pitfall**: `BackupError.AuthExpired` (data-model §10) — name uses «Auth» which is a domain word. Don't rename to «JwtExpired» / «TokenExpired» / «BearerExpired». The HTTP-origin column is adapter-internal mapping, **not** a leak.

### DZ-2: RecoveryKeyBackupBlob wire format — forbidden fields (Phase 1, T610, T624)

`RecoveryKeyBackupBlob` JSON MUST contain ONLY: `schemaVersion`, `stableId`, `salt`, `kdfParams`, `ciphertext`, `nonce`, `createdAt`. **Nothing else**.

**❌ NEVER** add «for convenience»: `googleSub`, `firebaseUid`, `providerKind`, `providerId`, `googleAccountId`, `email`, `phoneNumber`, `recipientId`, `groupId`, `userId`, `username`, `displayName`.

T624 (`RecoveryKeyBackupBlobProviderAgnosticTest`) parses JSON keys and asserts ABSENCE of all forbidden fields. If you add ANY of the above, this test fails.

**Why so strict**: constitution.md Article XIV §7 (a) — server access logs see only opaque UUIDs. Adding any of the above creates re-identification vector.

### DZ-3: `core/keys/` module already exists (Phase 1, T601)

**❌ NEVER** run `mkdir core/keys/` or write new `core/keys/build.gradle.kts` from scratch. It exists. Read it.

**✅ DO** at most: append a dependency to `dependencies { }` block in existing `core/keys/build.gradle.kts` if `kotlinx-serialization-json` is missing for the new JSON wire format. Otherwise leave alone.

### DZ-4: ConfigCipher2 byte-equal migration (Phase 5, T660-T661)

FR-018: existing ciphertext in DataStore must decrypt identically after F-5 update. **Envelope schemaVersion must NOT bump**.

**❌ NEVER** change the ciphertext bytes format, the envelope JSON layout, the AEAD nonce strategy, or the key length. Only changes allowed: **where the key comes from** (now `KeyRegistry.derive(stableId, "config")` instead of legacy direct Argon2id from passphrase).

**✅ DO** add T672 test (`KeyRegistryMigrationFromSpec018Test`) FIRST with a fixture file `config-ciphertext-spec018-sample.bin` (capture from current code BEFORE you change anything). Then refactor. Test must still pass byte-equal after refactor.

### DZ-5: Worker microservice boundary (Phase 4, T653-T665)

`workers/backup/` and `workers/identity/` are **two separate Workers**. Each has own `wrangler.toml`, own `package.json`, own `src/`, own `__tests__/`, own R2 bucket binding.

**❌ NEVER** put identity logic (firebase-admin setCustomUserClaims) inside `workers/backup/`. Even «for convenience». Even if it's «just one line». Owner explicitly rejected this in round-2 pushback 2026-06-28.

**❌ NEVER** create a `workers/backend/` or `workers/shared-server/` or any other monolithic Worker. One Worker per domain.

**✅ DO** if logic «doesn't fit either backup or identity», STOP and ask Claude. Probably means new Worker domain (e.g., `workers/family/`) but that's outside F-5 scope.

See memory `project_workers_microservice_mapping.md` (which you can't read — Gemini has no project memory). Summary: one `workers/<name>/` = one future Go microservice.

### DZ-6: `NoOpRecoveryKeyBackup` — DELETED, do not recreate (Phase 1-4)

Round-2 owner pushback 2026-06-28 removed `NoOpRecoveryKeyBackup` adapter and `RecoveryKeyBackupSelector`. Sole production adapter is `WorkerRecoveryKeyBackup`.

**❌ NEVER** add a NoOp adapter «for non-GMS devices». For non-GMS, flow doesn't reach `RecoveryKeyBackup` because `AuthAvailability` returns `Unavailable` (US-4). No backup adapter is needed.

**❌ NEVER** add a `RecoveryKeyBackupSelector` or any DI «capability detection» that picks between Worker and NoOp. DI injects single `WorkerRecoveryKeyBackup` in production, `FakeRecoveryKeyBackup` in tests.

**✅ DO** verify T648 (KeysModule.kt DI bindings) only binds the single Worker adapter.

### DZ-7: Schema version forward-compat (Phase 1, T611, T625)

`RecoveryBlobCodec` decoder MUST reject `schemaVersion > 1` with `BackupError.UnsupportedSchema`. **Do NOT** attempt partial parse.

**❌ NEVER** «try to parse what we can recognize». Old clients reading new blobs is a security risk (partial-parse can be exploited for downgrade attacks). Fail closed.

**✅ DO** T625 test must use a fixture with `"schemaVersion": 2` and assert `BackupError.UnsupportedSchema` is returned.

### DZ-8: Service-account JSON secrets (Phase 4, T662, deployment)

`workers/identity/` Worker uses Firebase Admin SDK which requires a service-account JSON.

**❌ NEVER** commit the service-account JSON to git.
**❌ NEVER** echo / log / Slack / paste the JSON content anywhere visible.
**❌ NEVER** pass it through chat messages.

**✅ DO** use `wrangler secret put FIREBASE_SERVICE_ACCOUNT_JSON` via the `secrets-cloudflare-worker` skill, where the owner pastes the secret directly into the wrangler CLI prompt. The secret never appears in repository files.

See memory `feedback_secret_handling` (you can't read it; this is the rule).

### DZ-9: Idempotency-Key semantics (Phase 4, T656)

POST `/backup` requires `Idempotency-Key: <UUID v4>` header. Server caches `(stableId, idempotencyKey) → response_hash` for 24h.

**❌ NEVER** allow same `idempotencyKey` + DIFFERENT body to succeed. Must return 409 Conflict.

**❌ NEVER** allow same body without idempotency key. Reject 400.

**✅ DO** test T658 (`idempotency.test.ts`) must cover both: replay-same-body → 200 cached; same-key-different-body → 409.

### DZ-10: Argon2id parameters in wire format (Phase 1, T603, T610)

`KdfParams` fields: `algorithm: "Argon2id"` (string, not enum), `iterations: 3`, `memoryKb: 65536`, `parallelism: 1`.

**❌ NEVER** hardcode these numbers in `Argon2RootKeyManager` adapter without reading from `KdfParams`. Future blobs may have different params (per OWASP cadence review SRV-CRYPTO-PARAMS-REVIEW); adapter must read from blob.

**❌ NEVER** treat `algorithm` as an enum. It's a string for future extension (per contract §Algorithm change policy).

**✅ DO** validate `algorithm == "Argon2id"`; if any other value, return `BackupError.UnsupportedSchema`.

### DZ-11: Tasks.md tick-sync rule (CLAUDE.md rule 11) — HARD RULE

Every commit that closes a `Tnnn` task MUST add `[ ]` → `[x]` for that task in **the same commit**.

**❌ NEVER** commit code without ticking the corresponding tasks in tasks.md.
**❌ NEVER** «I'll batch the ticks later». Drift forbidden.

**✅ DO** before `git commit`: `git diff --cached -- specs/task-6-root-key-hierarchy-recovery/tasks.md` — must show `[x]` lines if the diff contains code closing tasks. If not, STOP and tick tasks before committing.

### DZ-12: PR creation — DO NOT do it (CLAUDE.md rule 14)

**❌ NEVER** run `gh pr create`, `gh pr edit --title`, or anything that opens or modifies a PR.

**✅ DO** when all tasks of a phase are complete and tests pass: **STOP and notify Claude**. Claude will: (a) run pre-pr-backlog-sync skill, (b) review the diff, (c) create the PR or hand back changes to fix.

---

## Phase execution order

**Strict sequence. Review-gated.** After each Phase 0-6, you STOP and notify the owner. Owner / Claude reviews the full phase diff. **Do NOT start the next phase until reviewer green-lights**.

**Phases 0-6 = your work** (Gemini): coding + JVM/vitest tests. All these can run on a JVM/Node without emulator or real device. Each `[deferred-local-emulator]` test file should be **written** (so the code exists and compiles) but **not run** by Gemini. Each `[deferred-physical-device]` task = skipped entirely; only the placeholder description matters.

**Phase 7 = owner work** (after all 0-6 are reviewed and merged): emulator-based test runs + physical device smoke + peer review of docs. Not your job.

### Phase 0 — Inventory (T600) — 30 min

Single task. Produces `inventory.md`. No code changes. **Mandatory before Phase 1**.

🛑 **END-OF-PHASE-0 STOP** — notify owner. Wait for review of `inventory.md`. Do NOT proceed to Phase 1.

### Phase 1 — Foundation (T601-T631) — ~4-6 hours, JVM-only

Domain types + ports + fakes + contract tests. **No emulator needed**. Run `./gradlew :core:keys:test` after each commit.

**Suggested commit groupings** (one commit per group, each closes 2-7 tasks):
- C1: T601 (verify build.gradle), T602-T609 (value types in `family.keys.api`) — 9 tasks.
- C2: T610-T611 (wire format + codec) — 2 tasks.
- C3: T612-T615 (domain ports) — 4 tasks.
- C4: T616-T619 (fakes) — 4 tasks.
- C5: T620-T625 (wire-format contract tests) — 6 tasks.
- C6: T626-T627 (provider-agnostic + forget) — 2 tasks.
- C7: T628-T630 (fixture files) — 3 tasks.
- C8: T631 (Konsist fitness rule) — 1 task.

Total: 8 commits for Phase 1. Each verify `./gradlew :core:keys:test` green before commit.

**STOP signals** in Phase 1:
- If T620 (determinism test) fails with same inputs → STOP, KDF/HKDF implementation bug. Ask Claude.
- If T624 (provider-agnostic JSON test) fails → STOP, forbidden field leaked. Re-read DZ-2.
- If T631 (Konsist) fails → STOP, vendor token leaked into commonMain. Re-read DZ-1.

🛑 **END-OF-PHASE-1 STOP** — after C1-C8 all committed and `./gradlew :core:keys:test` green, notify owner. Wait for review. Do NOT proceed to Phase 2.

### Phase 2 — Android adapters (T632-T643) — ~3-4 hours

Real Keystore, real Argon2 via libsodium (through `core/crypto` port), real DataStore. Adapters in `family.keys.android` package.

**Suggested groupings**:
- C9: T632-T634 (AndroidKeystoreRegistry, Argon2RootKeyManager, DeviceKeyNamespaceProvider) — 3 tasks.
- C10: T635-T639 (WorkerRecoveryKeyBackup OkHttp client, DataStore counter + memory, AuthAvailability impl, BuildConfig URL) — 5 tasks.
- C11: T640-T641 (JVM unit tests for adapters with mocks — RUN these, must pass) — 2 tasks.
- C12: T642-T643 (**write test files** for `[deferred-local-emulator]` + `[deferred-physical-device]` — code that compiles, but DO NOT run). — 2 tasks.

**STOP signals** in Phase 2:
- Compile error «Unresolved reference: family.keys.android.SecureKeystore» → SecureKeystore is in `:core:crypto`, not `:core:keys`. Use `family.crypto.api.SecureKeystore`.
- If you can't find `KeyDerivation` port → it's in `:core:crypto` `family.crypto.api`. Ask Claude before guessing.

🛑 **END-OF-PHASE-2 STOP** — after C9-C12 committed, JVM tests green, deferred tests compile but not run. Notify owner.

### Phase 3 — Compose UI (T644-T652) — ~2-3 hours

3 Composables + ViewModel in `app/src/main/kotlin/com/launcher/ui/recovery/`. Material 3, senior-safe styling.

**Suggested groupings**:
- C13: T644 (RecoveryViewModel with SavedStateHandle) — 1 task.
- C14: T645-T647 (three Compose screens) — 3 tasks.
- C15: T648 (KeysModule.kt DI bindings) — 1 task.
- C16: T649-T652 (**write test files** for `[deferred-local-emulator]` UI tests — code compiles, NOT run) — 4 tasks.

**STOP signals**:
- If Compose preview fails or AVD is API 35+ → mark `[deferred-local-emulator]`, do NOT try to fix by changing Compose UI Test version (one-way door per research.md).

🛑 **END-OF-PHASE-3 STOP** — Composables compile, ViewModel state machine logic-tested (where possible without UI), DI bindings wired. Notify owner.

### Phase 4 — Workers TS implementation (T653-T670) — ~4-6 hours

**Two separate Workers**. Re-read DZ-5 before starting.

**Track A — `workers/backup/` (T653-T661)**:
- C17: T653 (scaffold) — 1 task.
- C18: T654-T656 (index.ts endpoints, ratelimit.ts, idempotency.ts) — 3 tasks.
- C19: T657-T660 (vitest tests) — 4 tasks.
- C20: T661 (README) — 1 task.

**Track B — `workers/identity/` (T662-T665)**:
- C21: T662-T663 (scaffold + init-claim endpoint) — 2 tasks.
- C22: T664-T665 (vitest test + README) — 2 tasks.

**Track C — Android wiring + deferred (T666-T670)**:
- C23: T667-T668 (BuildConfig URL + InitClaimClient Kotlin side) — 2 tasks. RUN unit tests, must pass.
- C24: T669-T670 (**write test files** for `[deferred-local-emulator]` integration tests — code compiles, NOT run) — 2 tasks.
- ⏸️ T666 (wrangler deploy) — **SKIP**. Mark `[ ]` in tasks.md with note «deferred to owner manual deploy after review». Gemini has no Cloudflare credentials.

**STOP signals** in Phase 4:
- If you find yourself wanting to put firebase-admin code in `workers/backup/` → STOP. Re-read DZ-5.
- If you find yourself wanting to share state between the two Workers → STOP. Microservice boundary forbids it.
- For T666 (deploy) — Gemini has no Cloudflare credentials. STOP and notify Claude / owner.

🛑 **END-OF-PHASE-4 STOP** — both Worker TS projects compile, vitest tests all green (run them: `cd workers/backup && npm test` + `cd workers/identity && npm test`), Android InitClaimClient unit-tested. T666 deploy left for owner. Notify owner.

### Phase 5 — Migration ConfigCipher2 (T671-T672) — ~1-2 hours

**Re-read DZ-4 before starting**. Byte-equal preservation is critical.

**Strict order**:
1. FIRST capture fixture: run current ConfigCipher2 against a sample plaintext on emulator, save the ciphertext to `core/keys/src/commonTest/resources/fixtures/config-ciphertext-spec018-sample.bin`. Commit this (T630).
2. THEN write T672 test that reads this fixture → instantiates new `AndroidKeystoreRegistry` + uses `KeyRegistry.derive(stableId, "config")` → decrypts → asserts plaintext byte-equal.
3. THEN refactor ConfigCipher2 to use `KeyRegistry.derive(...)`.
4. Test must still pass after refactor.

C26: T671 (refactor code) + write T672 test file (compiles but is `[deferred-local-emulator]` — needs real Keystore). 

⚠️ **Modified flow** since Gemini can't run instrumented tests:
1. T671 refactor ConfigCipher2 to use `KeyRegistry.derive(stableId, "config")` — JVM-runnable unit test verifies API contract.
2. T672 test file written but `[deferred-local-emulator]` — actual byte-equal verification deferred to Phase 7 owner run.
3. **Capture fixture `config-ciphertext-spec018-sample.bin` is also deferred** — needs running spec 018 ConfigCipher2 on emulator. Mark in inventory as «deferred-local-emulator pre-requisite».

**STOP signal**: if T671 refactor breaks ANY existing JVM test → STOP, do NOT continue. The ciphertext format compatibility issue. Ask Claude.

🛑 **END-OF-PHASE-5 STOP** — refactor committed, existing JVM tests still green, T672 instrumented test file written but deferred. Notify owner.

### Phase 6 — Documentation + checklist closure (T673-T680) — ~2-3 hours

Mostly mechanical. AndroidManifest tweaks, docs writing, backlog AC migration.

**Suggested groupings**:
- C27: T673 (docs/recovery-flow.md plain-Russian) — 1 task. **STOP signal**: if you can't write senior-friendly Russian, leave a `<!-- TODO(owner): rewrite in plain Russian -->` block and notify Claude/owner.
- C28: T674 (docs/dev/key-hierarchy.md) — 1 task.
- C29: T675-T677 (permissions doc + AndroidManifest allowBackup + data_extraction_rules.xml) — 3 tasks.
- C30: T678-T679 (spec cleanup — tech-agnostic FR/SC phrasing) — 2 tasks. See checklists/requirements-quality-analyze.md for the exact tokens to remove.
- C31: T680 (backlog AC hybrid format migration) — 1 task. Just mark existing 6 AC as `[hand]`; do not change content.

- C31: T680 (backlog AC hybrid format migration) — 1 task. Just mark existing 6 AC as `[hand]`; do not change content.

🛑 **END-OF-PHASE-6 STOP** — docs written, AndroidManifest tweaked, spec cleanup applied, backlog AC migrated. Notify owner. **This is your last phase**.

### Phase 7 — Owner manual gates — NOT YOUR JOB

All tasks here are `[deferred-local-emulator]` (need AVD ≤ 34) or `[deferred-physical-device]` (need Xiaomi 11T) or `[deferred-external]` (need owner peer-review):

- **T642** AndroidKeystoreRegistryTest (real Keystore)
- **T643** Argon2BenchmarkTest (Xiaomi 11T timing)
- **T649-T652** Compose UI tests (4 screens) — need AVD ≤ 34
- **T666** wrangler deploy — owner CLI access
- **T669-T670** Worker integration tests — need `wrangler dev` + emulator
- **T672** KeyRegistryMigrationFromSpec018Test (real Keystore + spec 018 fixture)
- **T681** SC-001 cross-device smoke (Xiaomi 11T)
- **T682** SC-002 Fallback smoke
- **T683** SC-005 Autofill cross-device (two physical devices)
- **T684** SC-010 Argon2 timing (Xiaomi 11T)
- **T685** Real Worker E2E (deployed *.workers.dev)
- **T686** SC-006 docs/recovery-flow.md peer review

Owner runs these post-merge. Gemini does NOT attempt any of them.

---

## Workflow per task / per commit

### Before each task

1. Re-read the task description in tasks.md.
2. Check if any DZ-N section applies to this task type.
3. Check if file already exists (per Phase 0 inventory) — if KEEP, don't touch.
4. Re-read relevant FR / SC / contract section in spec.md / contracts/.

### Implementing

1. Write code in correct location (see §«Existing code reality» for package paths).
2. Run `./gradlew :core:keys:test` (Phase 1) or appropriate test command (see quickstart.md).
3. If test fails: read the error, do not guess; if uncertain → STOP, ask Claude.

### Before each commit (mandatory self-check)

```
1. Have I added [x] for all closed Tnnn in tasks.md in THIS diff? (rule 11)
2. Have I added secrets / credentials / API keys to any file? (must be NO)
3. Have I introduced vendor SDK / transport types into core/keys/commonMain/? (must be NO — DZ-1)
4. Does the diff touch only files relevant to the tasks being closed?
5. Have I run the tests for the touched module? (must be GREEN)
6. Am I about to run `gh pr create`? (must be NO — DZ-12)
```

If any check fails → STOP, fix, redo.

### Commit message format

```
T6NN-T6MM: <imperative summary>

<2-4 lines describing what changed, referencing FR-NNN, US-NNN, SC-NNN>

Closes: T6NN, T6NN+1, ..., T6MM

[deferred-*] markers: <none / list>
```

Example:
```
T602-T609: add domain value types in family.keys.api

Add StableId type alias, KdfParams value class, RootKey/DerivedKey opaque values,
AvailabilityReason enum, AuthAvailabilityStatus sealed, RootKeyError/BackupError
sealed types per data-model.md §2-10. All pure-Kotlin, no vendor imports.

Closes: T602, T603, T604, T605, T606, T607, T608, T609.
```

### When you're stuck

Surface signals — write a short note instead of guessing. Format:

```
STOP: T6NN — <one-sentence summary of the blocker>

Context:
- What I tried: <1-2 lines>
- What I expected: <1 line>
- What I got: <1 line>
- Relevant DZ: <DZ-N or "none">

Asking Claude: <specific question>
```

Push this as an empty commit or add to a `BLOCKERS.md` file. Then idle until Claude responds.

---

## What Claude (reviewer) will check on each commit

For your information — so you can pre-empt:

1. **Tick-sync** (DZ-11): tasks.md `[x]` for all closed Tnnn.
2. **Package paths**: correct `family.keys.api` / `family.keys.api.internal` / `family.keys.android`.
3. **Forbidden tokens** (DZ-1): grep `core/keys/src/commonMain/` for vendor names.
4. **Wire format forbidden fields** (DZ-2): if blob structure touched, grep for forbidden field names.
5. **Existing file untouched**: legacy KEEP files have no diff.
6. **Tests added with code**: contract test for each new wire-format file; fake for each new port.
7. **No secrets in diff**.
8. **No hook bypass** (`--no-verify` etc).
9. **Commit message references FR/US/SC**.
10. **Article XIV §7 compliance**: if Worker code, no PII in logs, opaque IDs only.

Claude will reject (ask to fix) if any of these fail. After fixes, resubmit.

---

## Useful commands cheat-sheet

```bash
# Build domain module
./gradlew :core:keys:build

# Run domain tests
./gradlew :core:keys:test

# Run all tests (slow)
./gradlew test

# Worker dev (Phase 4)
cd workers/backup && wrangler dev --port 8787
cd workers/identity && wrangler dev --port 8788

# Worker tests
cd workers/backup && npm test
cd workers/identity && npm test

# Verify branch
git branch --show-current  # must be: task-6-root-key-hierarchy-recovery

# Pre-commit check (run before EVERY commit)
git diff --cached -- specs/task-6-root-key-hierarchy-recovery/tasks.md  # must show [x] lines
git diff --cached | grep -iE "(google_application_credentials|firebase_admin.*json|api_key|secret|password)" # must be EMPTY
```

---

## Out of scope (deferred)

Do NOT implement these in F-5:
- Social recovery (TASK-39 Phase 5).
- Multi-admin envelope (S-2 enhancement).
- Key rotation / passphrase change (TASK-41 Phase 5).
- 2FA escrow (TASK-21 Phase 3).
- Cross-provider migration.
- Own-server replacement (server-roadmap SRV-RECOVERY-001).
- Local-only → cloud upgrade.
- AI affordance / Capability Registry (F-2 Phase 5+).

If you find yourself implementing any of the above → STOP, you're outside F-5 scope.

---

## Summary

- **86 tasks across 7 phases**, T600-T686.
- **~70 closeable in your session** (Phases 0-6 minus deferred markers).
- **~16 deferred** (Phase 7 manual gates + UI tests + integration tests requiring AVD ≤34 / real device).
- **Read order**: this doc → CLAUDE.md → constitution.md Article XIV §7 → spec → plan → tasks.
- **12 danger zones** — re-read the relevant DZ before each phase.
- **Tick-sync mandatory** — every commit closes some Tnnn AND adds [x] in same diff.
- **No PR creation, no secrets, no scope creep**.
- **When stuck**: write STOP block, ask Claude.

Good luck. Read this doc fully before touching anything.

— Claude (your reviewer)
