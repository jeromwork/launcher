# Checklist: dev-experience — TASK-51 libsodium consolidation

**Spec**: `specs/task-51-libsodium-consolidation/spec.md`
**Applied**: 2026-06-26
**Skill**: `.claude/skills/checklist-dev-experience/SKILL.md`

Reference: `CLAUDE.md` §6 (mock-first development).

---

## Local-test path

- [x] CHK001 Spec's "Local Test Path" section is filled in (not the template placeholder).
  - Evidence: spec lines 202-226 contain concrete emulator name, device id (`17f33878`), fake adapters list, verification commands, and explicit "Cannot-test-locally gaps" subsection.
- [x] CHK002 Verification command is exact.
  - Evidence: multiple exact commands listed — `./gradlew :app:assembleMockBackendDebug`, `./gradlew :core:testMockBackendDebugUnitTest --tests "*PairingCryptoCoordinatorTest"`, `adb shell am start -W -n com.launcher.app/.HomeActivity`, `grep -r "com.goterl" app/src/main/ core/src/{common,android}Main/`.
- [x] CHK003 The verification command runs in under 5 minutes on a developer laptop (cold cycle).
  - Evidence: focused gradle unit test (`*PairingCryptoCoordinatorTest`) and `grep` checks are seconds-scale. Full `:app:assembleMockBackendDebug` + `test` may exceed 5 min on cold cache but is not the minimal verification path — focused tests satisfy CHK003 with margin.
- [x] CHK004 At least one path of the feature is verifiable without an emulator (pure JVM unit test on domain logic).
  - Evidence: `EnvelopeConfigCipherRoundtripTest` (commonTest golden vectors), `*PairingCryptoCoordinatorTest`, and Konsist fitness-tests (`NoLazysodiumInProductionTest`, `NoLegacyComLauncherCryptoTest` per SC-007) run on JVM without emulator. KAT tests inherited from spec 016 `core/crypto/src/jvmTest/`.
- [x] CHK005 If the feature requires an emulator, the spec names the preset from skill `android-emulator`.
  - Evidence: spec line 205 names `pixel_5_api_34` (x86_64), referencing skill `android-emulator`.

## Fake adapters

- [x] CHK006 Every external port the feature depends on has a fake adapter available (or the spec lists adding one as a task).
  - Evidence: spec lines 208-212 enumerate `FakeAeadCipher`, `FakeAsymmetricCrypto`, `FakeRandomSource`, `FakeSecureKeyStore`. Resolution log (line 253) explicitly tasks creating new fakes under `core/crypto/src/commonTest/kotlin/cryptokit/crypto/fake/` and `cryptokit/pairing/fake/`.
- [x] CHK007 Fake adapters are used in tests — the spec does not require real Firebase / real Cloudflare Worker / real FCM to verify.
  - Evidence: TASK-51 is a crypto-stack refactor with no Firebase/Worker/FCM coupling. Acceptance scenarios for US1/US2/US3/US4 are local (smoke activity, grep, APK size, cold start). FR-004 byte-equal roundtrip is verifiable via JVM golden-vectors test, no network.
- [x] CHK008 The DI wiring picks fakes for `debug` / `test` builds and reals for `release` (or the spec describes the equivalent build-flavor split).
  - Evidence: FR-015 mandates single `cryptokitModule` Koin module. Existing project pattern uses `mockBackend` flavor (cf. `:app:assembleMockBackendDebug` in verification commands) — same DI split inherited. Fakes live in `commonTest`, real ionspin adapter in `commonMain`.

## Fixtures

- [x] CHK009 Test data lives in a checked-in fixture (JSON / Kotlin object) — not hand-typed in each test.
  - Evidence: spec line 214 references `EnvelopeConfigCipherRoundtripTest` (golden vectors). Spec 016 KAT tests use RFC test vectors. Seeded `FakeAsymmetricCrypto` (line 209) provides deterministic keys.
- [x] CHK010 Fixtures are stable across runs (no `Random()`, no `now()` without a fixed clock).
  - Evidence: spec line 209 explicitly states `FakeAsymmetricCrypto (seeded)` and `FakeRandomSource` (deterministic). FR-004 byte-equal-roundtrip requirement implies deterministic test inputs.
- [x] CHK011 Cross-version fixtures exist for any wire format introduced (v(N-1) sample saved for backward-compat test).
  - Evidence: spec FR-004 keeps `schemaVersion: 1` unchanged (no new wire format introduced); spec line 198 confirms "Spec 011 wire-format не меняется в этой задаче". Existing `EnvelopeConfigCipherRoundtripTest` golden vectors serve as backward-compat anchor. Per Q2 (line 19), forced re-pair migration is chosen — no persisted-key cross-version fixture needed.

## Cannot-test-locally gaps

- [x] CHK012 Every gap that requires a physical device, OEM-specific behavior, or real billing is **explicitly listed** in the "Local Test Path → Cannot-test-locally gaps" subsection.
  - Evidence: spec lines 223-226 list three gaps: Samsung One UI, Huawei EMUI, real 2-device pairing (TASK-8 dependency).
- [x] CHK013 Each gap has an inline TODO in code / spec: `TODO(physical-device): ...` or `TODO(real-account): ...`.
  - Evidence: spec lines 224-225 use `TODO(physical-device):` markers explicitly. OEM Matrix (lines 244-245) repeats `TODO(physical-device) → TASK-55`.
- [x] CHK014 No gap is silently swept under "we'll test it in prod".
  - Evidence: all OEM gaps routed to TASK-55 verification aggregator; pairing 2-device gap tied to TASK-8 in-flight. No "test in prod" claims found.

## Build cycle

- [x] CHK015 Adding this feature does not increase clean-build time on a developer laptop by more than ~30 seconds (or the spec acknowledges the cost and justifies it).
  - Evidence: TASK-51 *removes* lazysodium-android + JNA AAR (FR-002) and shrinks APK by ≥3 MB (SC-008). Net build impact is negative (faster, smaller). ionspin libsodium-kmp already on classpath via spec 016. No new heavy dependency.
- [x] CHK016 The feature does not require a one-time manual setup step that is not documented.
  - Evidence: refactor uses already-provisioned ionspin via spec 016 `:core:crypto`. No new Firebase console toggle, no new keystore alias documented as manual. Force re-pair (FR-005) executes automatically on first launch.
- [x] CHK017 No new credential / API key / service-account file is needed for `debug` builds unless the spec lists how to obtain it.
  - Evidence: spec introduces no new credential / service-account / API key. Pairing uses keys generated on-device. `mockBackend` flavor uses existing fakes.

## Crash + log diagnostics

- [ ] CHK018 The feature emits enough log signal (Logcat tag, structured log fields) for a developer to diagnose a failure without attaching a debugger.
  - Gap: spec does not specify Logcat tag for `cryptokit.*` package or structured-log fields for `CryptoException`. FR-009 introduces uniform throws with `try/catch` at top, but the spec does not state what gets logged at that catch site (tag, level, fields).
- [ ] CHK019 Failure modes that would crash silently (background coroutine, lifecycle-detached job) have an opt-in log line.
  - Gap: FR-009 mentions `CancellationException` re-throw for structured concurrency, but no log line is specified for non-cancellation failures inside coroutine adapter wrappers. Edge case "FakeAeadCipher из старого пакета" (line 118) is functional, not logging-related. Spec is silent on coroutine-detached failure logging.
- [N/A] CHK020 If the feature has runtime feature flags or remote config, the current value is loggable on demand.
  - Evidence: TASK-51 introduces no runtime feature flags or remote config. Refactor is build-time only.

## Cross-developer reproducibility

- [x] CHK021 The spec does not embed developer-machine-specific paths, env vars, or assumptions.
  - Evidence: all paths are repo-relative (`app/src/main/`, `core/crypto/src/commonMain/`). The single device id reference (`17f33878`) is documented in shared `reference_testing_environment.md` per Assumptions section (line 195) and refers to the project's shared physical-device, not a developer-local env var.
- [x] CHK022 Onboarding a new developer to verify this feature is documented in less than 1 page.
  - Evidence: Local Test Path section (lines 202-226) is ~25 lines and self-contained — emulator preset name, device id, fake adapter list, verification commands, gaps. Falls under 1 page.

---

## Pass/Fail verdict

- **Total CHK items**: 22
- **`[x]` passed**: 19
- **`[ ]` failed**: 2 (CHK018, CHK019)
- **`[N/A]` not applicable**: 1 (CHK020)

**Verdict**: **PASS WITH MINOR GAPS**.

The spec gives an excellent local-dev story (mock-first fakes, JVM-only golden vectors, explicit OEM gap routing to TASK-55, no new credentials, APK shrinks). The only weak area is **log diagnostics for the new `CryptoException` throws pattern** — the spec mandates "один отлов, один логгер, один UI error handler" (line 20) but does not pin down the Logcat tag or what fields get logged. This is a minor remediation: add a single sentence to FR-009 naming the tag (e.g. `cryptokit`) and required fields (operation, keyId-hint, exception class) — does not block implementation.

---

## Open items (to address before / during plan or implementation)

1. **CHK018 — pin the log contract for `CryptoException`** (minor):
   - Add to FR-009 (or as a new FR-009a): "On `catch (e: CryptoException)` at the top-level handler, emit a Logcat warning with tag `cryptokit`, including `operation`, `keyAliasOrNull`, and `e::class.simpleName`. Do not log raw key material or ciphertext."
   - Estimate: 1-line addition during plan-time refinement; no code-shape impact.

2. **CHK019 — name the coroutine-detached failure log site**:
   - In `PairingCryptoCoordinator` (or wherever ensure-keys / store runs inside a launched coroutine without an awaiting parent), require an explicit `CoroutineExceptionHandler` or `try/catch` around the launched block that logs to the `cryptokit` tag before swallowing.
   - Estimate: covered by FR-009 logging clarification + one explicit mention in plan.md task list.

3. **Verification-time cross-check** (not a CHK gap, but worth noting):
   - When `pre-pr-backlog-sync` runs, ensure the resulting `[auto:checklist]` line for `dev-experience.md` reads `19/22 CHK [x]` (1 N/A, 2 open). The 2 open items should be closed via plan-time spec edit, not deferred to a future task.

---

dev-experience: 19/22 CHK [x]
