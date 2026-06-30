# Next Session Brief — Gemini Resume Point

> **You are Gemini in Antigravity, resuming F-5 work after a session gap. Owner ran out of quota mid-task. This doc is self-contained — read it FIRST, then `gemini-handoff.md` §Phase 2.**

**Branch**: `task-6-root-key-hierarchy-recovery` (current; do not switch)
**Backlog**: TASK-6, status `In Progress`
**Last reviewer**: Claude Opus (audit complete 2026-06-28, all findings green)

---

## Current state of the world

### What's done (do NOT redo)

| Step | Status | Commits |
|---|---|---|
| Phase 0 — T600 inventory + D4 rename | ✅ committed | `2570c87`, `9447b82`, `7be001c` |
| Pre-Phase 1 KDoc cleanup | ✅ committed | `62a8726` |
| Phase 1 — T601-T631 (domain types, ports, fakes, contract tests, Konsist) | ✅ committed | `abe77e2` |
| Phase 1.5 cleanup P0/P1/P2 (BackupError migration, passphrase validation, FNV hash, etc.) | ✅ committed | `5b5fe9c`, `023083f`, `883c147` |
| A1 docs (this doc's sibling `a1-resolution.md` + tasks.md T600.5 entry) | ✅ committed | `0fe6c6f` |
| **T600.5 — A1 wire-format alignment to F-5 contract** | ⚠️ **CODE WRITTEN, NOT COMMITTED** | — |

### What the previous Gemini session actually did (verified by Claude)

A1 implementation per [a1-resolution.md](./a1-resolution.md) — code is correct but **the commit was never made**. Files sitting in working tree:

```
Staged deletions:
  D  core/keys/src/commonMain/kotlin/family/keys/api/PassphraseKdfParams.kt
  D  core/keys/src/commonTest/kotlin/family/keys/RecoveryKeyBackupBlobBackwardCompatTest.kt

Unstaged modifications (13 files):
  M  app/src/realBackend/java/com/launcher/app/data/recovery/FirestoreRecoveryKeyBackup.kt
  M  core/keys/src/androidInstrumentedTest/kotlin/family/keys/Argon2idAndroidPerfBenchmark.kt
  M  core/keys/src/commonMain/kotlin/family/keys/api/RecoveryKeyBackupBlob.kt
  M  core/keys/src/commonMain/kotlin/family/keys/impl/Argon2idPassphraseKdf.kt
  M  core/keys/src/commonMain/kotlin/family/keys/impl/RecoveryFlow.kt
  M  core/keys/src/commonTest/kotlin/family/keys/RecoveryFlowTest.kt
  M  core/keys/src/commonTest/kotlin/family/keys/WireFormatJsonTest.kt
  M  core/keys/src/commonTest/kotlin/family/keys/contracts/RecoveryKeyBackupContractTest.kt
  M  core/keys/src/jvmTest/kotlin/family/keys/contracts/RecoveryKeyBackupBlobContractBackwardCompatTest.kt
  M  core/keys/src/jvmTest/kotlin/family/keys/contracts/RecoveryKeyBackupBlobProviderAgnosticTest.kt
  M  core/keys/src/jvmTest/kotlin/family/keys/contracts/RecoveryKeyBackupBlobRoundtripTest.kt
  M  core/keys/src/jvmTest/kotlin/family/keys/contracts/RecoveryKeyBackupBlobUnsupportedSchemaTest.kt
  M  core/keys/src/jvmTest/resources/fixtures/recovery-blob-v1-sample.json
  M  core/keys/src/jvmTest/resources/fixtures/recovery-blob-v2-sample-future.json
  M  specs/task-6-root-key-hierarchy-recovery/tasks.md  ← T600.5 already marked [x]
```

**Claude verified all 13 files**: code matches [a1-resolution.md](./a1-resolution.md) spec exactly; AAD_PREFIX preserved; `./gradlew :core:keys:jvmTest --rerun-tasks` returned **107/0/0 PASS** (was 110 pre-A1; -3 because the legacy commonTest BackwardCompat file was deleted as planned).

---

## Step 1 — IMMEDIATELY: commit the A1 work (5 min, no thinking)

Don't reinvent. Don't re-verify. Just commit what's in working tree.

```bash
# Verify pre-commit state
git status

# Should show 15 changes + tasks.md modified with T600.5 [x] line.
# Test one more time to confirm green before commit:
./gradlew :core:keys:jvmTest --rerun-tasks
# Expect: BUILD SUCCESSFUL, 107 tests, 0 failures.

# Stage all A1 changes + tasks.md tick
git add core/keys/src/ app/src/realBackend/java/com/launcher/app/data/recovery/ specs/task-6-root-key-hierarchy-recovery/tasks.md

# Confirm only A1 files staged (no stray work)
git diff --cached --stat
# Expect exactly 15 files: 13 modified + 2 deleted.

# Commit with the message template from a1-resolution.md §Commit plan
git commit -m "refactor(keys): A1 align RecoveryKeyBackupBlob with F-5 v1 contract

Variant 2 (clean rewrite) per owner decision 2026-06-28:
- Add stableId field for server-side defence-in-depth
- Rename kdfSalt->salt (32 bytes), wrappedRootKey->ciphertext
- Move algorithm from top-level into kdfParams (KDF-only)
- Drop ALGORITHM_V1 const (AEAD implicit per schemaVersion=1)
- createdAt: Long (epoch ms) -> kotlinx.datetime.Instant (ISO-8601)
- Consolidate KdfParams <-> delete legacy PassphraseKdfParams (13 usages)
- Regenerate v1 + v2 fixtures per contract §2
- Update RecoveryFlow + Argon2idPassphraseKdf + 7 tests
- Preserve AAD_PREFIX 'f5-recovery-vault-v1' (scope-excluded wire constant per D4)
- Delete superseded legacy commonTest/RecoveryKeyBackupBlobBackwardCompatTest.kt

Owner-confirmed: zero deployed spec-018 users -> no migration needed.
Firestore Rules need separate update in Phase 4 deploy (new field names + ISO-8601 createdAt) — see a1-resolution.md §'What this does NOT change'.

Closes: T600.5
[deferred-*] markers: none"
```

**🛑 STOP after this commit.** Do NOT proceed to Phase 2 in the same action. Wait for owner to push (or push yourself if `git push` is in your permission set — but DO NOT create a PR, see DZ-12).

---

## Step 2 — Start Phase 2 (only after Step 1 committed)

**Now you're cleared to start Phase 2 — Android adapters.** Re-read the existing handoff:

1. Open [gemini-handoff.md](./gemini-handoff.md) §Phase 2 (the "C9-C12" commit groupings).
2. Re-read [gemini-handoff.md DZ-1, DZ-3, DZ-5, DZ-6, DZ-7, DZ-8, DZ-10, DZ-11, DZ-12, DZ-13](./gemini-handoff.md) before touching any file in Phase 2.
3. Re-read [gemini-handoff.md §«Existing code reality» §2-§4](./gemini-handoff.md) — package conventions (`family.keys.android` for Android adapters).

### Phase 2 task list (T632-T643) — abbreviated

Source of truth: [tasks.md Phase 2](./tasks.md). Summary:

| Group | Tasks | Files (new) | Notes |
|---|---|---|---|
| C9 | T632-T634 | `androidMain/family/keys/android/AndroidKeystoreRegistry.kt`, `Argon2RootKeyManager.kt`, `DeviceKeyNamespaceProvider.kt` | Use `family.crypto.api.SecureKeystore` + `KeyDerivation` ports from `:core:crypto`. **Do NOT** import libsodium directly (DZ-1, T120 fitness rule blocks this). |
| C10 | T635-T639 | `app/src/main/.../data/recovery/WorkerRecoveryKeyBackup.kt` (OkHttp), `DataStorePassphraseAttemptCounter.kt`, `DataStoreSchemaVersionMemory.kt`, `AuthAvailabilityAndroidImpl.kt`, BuildConfig URL in `app/build.gradle.kts` | OkHttp client against `BuildConfig.RECOVERY_BACKUP_WORKER_URL`. Idempotency-Key UUID v4. 3 retries w/ exp back-off. JWT from F-4 SessionStore. |
| C11 | T640-T641 | `WorkerRecoveryKeyBackupTest.kt` (MockWebServer), `AuthAvailabilityAndroidImplTest.kt` | JVM unit tests. **RUN these — must be green.** |
| C12 | T642-T643 | `AndroidKeystoreRegistryTest.kt`, `Argon2BenchmarkTest.kt` | **Write the files** (so they compile) but **DO NOT run** — both are `[deferred-*]`. Owner runs on real device. |

### Phase 2 STOP signals (per handoff §Phase 2)

- Compile error «Unresolved reference: family.keys.android.SecureKeystore» → SecureKeystore lives in `:core:crypto`, package `family.crypto.api`. NOT in `:core:keys`. STOP and check `core/crypto/src/commonMain/kotlin/cryptokit/crypto/api/SecureKeyStore.kt` for the actual class name (note: `SecureKeyStore`, capital S — see [RootKeyManagerImpl.kt:5](../../core/keys/src/commonMain/kotlin/family/keys/impl/RootKeyManagerImpl.kt#L5) for the existing import).
- If you can't find `KeyDerivation` port → it's in `:core:crypto` `family.crypto.api` or `cryptokit.crypto.api`. Grep first, ask owner if still unsure.

### Phase 2 commit discipline

- **4 commits**, not 1 megacommit (lesson from `abe77e2` Phase 1 megacommit critique — see audit history).
- Each commit ticks the closed `Tnnn` in `tasks.md` **same diff** (CLAUDE.md rule 11 / DZ-11).
- After C12 → 🛑 **END-OF-PHASE-2 STOP**, notify owner. Wait for review before Phase 3.

---

## Hard rules — re-read every session

These are NON-negotiable:

1. **No `gh pr create`** (DZ-12, CLAUDE.md rule 14). Owner / Claude creates PRs.
2. **No secrets in git** (DZ-8). Service-account JSON via `wrangler secret put` only.
3. **No vendor tokens in `core/keys/src/commonMain/`** (DZ-1). Konsist rule [ImportRestrictionsFitnessTest.kt:256](../../core/keys/src/jvmTest/kotlin/family/keys/fitness/ImportRestrictionsFitnessTest.kt#L256) enforces `Google|Firebase|OAuth|Apple|PhoneNumber|PhoneAuth|IdToken|Cloudflare|WorkerUrl|Email|Sub`.
4. **Tick-sync MUST be in same diff as code** (DZ-11). `git diff --cached -- specs/task-6-root-key-hierarchy-recovery/tasks.md` must show `[x]` lines before commit.
5. **No mixing Vault and Backup naming** in new code (DZ-14). Use `Backup` everywhere except scope-excluded `AAD_PREFIX` + `firestore.rules`.
6. **One Worker per domain** in Phase 4 — `workers/backup/` and `workers/identity/` are separate (DZ-5). Don't bundle.
7. **When stuck**: write STOP-block per handoff §«When you're stuck», push as empty commit OR add to `BLOCKERS.md`, then **idle until reviewer responds**. Do not guess on architectural choices.

---

## Open items tracker (NOT your job, FYI)

| Item | Owner / When |
|---|---|
| Firestore Rules update (new field names `stableId`, `salt`, `ciphertext`, `createdAt: string`) | Phase 4 T654 deployment task. NOT in Phase 2 scope. |
| T630 capture spec-018 fixture for migration test (`config-ciphertext-spec018-sample.bin`) | Phase 5 deferred — needs emulator. Owner. |
| Phase 7 deferred manual gates (T681-T686) — Xiaomi 11T physical device verifications | Owner post-merge. |
| AAD_PREFIX scope-exclusion → eventual rename when no production data exists | Future, not now. Document only. |

---

## TL;DR for ultra-impatient resumption

1. `git status` → confirm 15 uncommitted files + tasks.md modified.
2. `./gradlew :core:keys:jvmTest --rerun-tasks` → confirm 107/0/0.
3. Commit A1 work with template above. **STOP.**
4. Owner / Claude reviews, gives green light.
5. Start Phase 2 C9 (T632) per [gemini-handoff.md §Phase 2](./gemini-handoff.md). 4 commits, not 1.

Good luck. Read [a1-resolution.md](./a1-resolution.md) once to know what was changed if confused about file names. Don't ask for permission to commit Step 1 — it's already specified and verified.

— Claude (your reviewer, audit complete 2026-06-28)
