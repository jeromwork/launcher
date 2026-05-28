---
name: checklist-dev-experience
description: Verifies the spec is buildable, testable, and debuggable LOCALLY by a developer without depending on production accounts, paid services, real devices we don't own, or fragile manual rituals. Catches features that can only be verified after deploying to production. Triggered always — local dev-loop quality is universal — and especially on specs mentioning emulator, fixture, fake adapter, dev environment, test harness, build, debug, CI.
---

# Checklist: dev-experience

Verifies the spec lets a single developer **build, run, and verify** the feature on their own machine, in a reasonable cycle time, without depending on production accounts, paid services, physical devices we don't own, or hard-to-reproduce manual rituals.

Goal: every spec ships with a documented local test path. If a feature can only be tested in production, that's a dev-experience smell that compounds: every change becomes a deploy-and-pray cycle.

Reference: `CLAUDE.md` §6 (mock-first development), memory `reference_testing_environment.md`.

---

## Local-test path

- [ ] CHK001 Spec's "Local Test Path" section is filled in (not the template placeholder).
- [ ] CHK002 Verification command is exact (e.g., `./gradlew :core:test --tests *FamilyGroupTest`) — not "run the tests".
- [ ] CHK003 The verification command runs in under 5 minutes on a developer laptop (cold cycle).
- [ ] CHK004 At least one path of the feature is verifiable without an emulator (pure JVM unit test on domain logic).
- [ ] CHK005 If the feature requires an emulator, the spec names the preset from skill `android-emulator` (e.g., `pixel_5_api_34`).

## Fake adapters

- [ ] CHK006 Every external port the feature depends on has a fake adapter available (or the spec lists adding one as a task).
- [ ] CHK007 Fake adapters are used in tests — the spec does not require real Firebase / real Cloudflare Worker / real FCM to verify.
- [ ] CHK008 The DI wiring picks fakes for `debug` / `test` builds and reals for `release` (or the spec describes the equivalent build-flavor split).

## Fixtures

- [ ] CHK009 Test data lives in a checked-in fixture (JSON / Kotlin object) — not hand-typed in each test.
- [ ] CHK010 Fixtures are stable across runs (no `Random()`, no `now()` without a fixed clock).
- [ ] CHK011 Cross-version fixtures exist for any wire format introduced (v(N-1) sample saved for backward-compat test).

## Cannot-test-locally gaps

- [ ] CHK012 Every gap that requires a physical device, OEM-specific behavior, or real billing is **explicitly listed** in the "Local Test Path → Cannot-test-locally gaps" subsection.
- [ ] CHK013 Each gap has an inline TODO in code / spec: `TODO(physical-device): <what to verify>` or `TODO(real-account): <what to verify>`.
- [ ] CHK014 No gap is silently swept under "we'll test it in prod".

## Build cycle

- [ ] CHK015 Adding this feature does not increase clean-build time on a developer laptop by more than ~30 seconds (or the spec acknowledges the cost and justifies it).
- [ ] CHK016 The feature does not require a one-time manual setup step that is not documented (e.g., "you must enable X in Firebase console" → must be in spec or a setup script).
- [ ] CHK017 No new credential / API key / service-account file is needed for `debug` builds unless the spec lists how to obtain it.

## Crash + log diagnostics

- [ ] CHK018 The feature emits enough log signal (Logcat tag, structured log fields) for a developer to diagnose a failure without attaching a debugger.
- [ ] CHK019 Failure modes that would crash silently (background coroutine, lifecycle-detached job) have an opt-in log line.
- [ ] CHK020 If the feature has runtime feature flags or remote config, the current value is loggable on demand.

## Cross-developer reproducibility

- [ ] CHK021 The spec does not embed developer-machine-specific paths, env vars, or assumptions (e.g., "set `MY_PHONE_NUMBER=…`").
- [ ] CHK022 Onboarding a new developer to verify this feature is documented in less than 1 page (or covered by existing `docs/dev/dev-environment.md`).

---

## How to apply

1. Walk the spec looking for any "this works in prod" claim.
2. Trace each acceptance criterion to a local verification command — if none exists, that is a failure.
3. For every TODO(physical-device) / TODO(real-account), confirm the gap is intentional and the rest of the feature is still verifiable locally.

## Output

Inline into `specs/<id>/checklists/dev-experience.md`.
