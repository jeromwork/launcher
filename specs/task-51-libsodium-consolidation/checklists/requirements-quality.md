# Checklist: requirements-quality — task-51-libsodium-consolidation

Applied: 2026-06-26
Source skill: `.claude/skills/checklist-requirements-quality/SKILL.md`
Spec under review: `specs/task-51-libsodium-consolidation/spec.md`

Legend: `[x]` covered, `[ ]` not covered (fail), `[N/A]` intentionally absent with reason.

---

## Content Quality

- [ ] CHK001 No implementation details (programming languages, frameworks, vendor APIs) appear in `spec.md`. *(architecture belongs in `plan.md`)*
  - **FAIL.** Spec is saturated with implementation specifics: `ionspin libsodium-kmp 0.9.5`, `JNA`, `lazysodium-android`, `com.goterl`, `Koin module`, `Konsist`, `Robolectric`, `java.security.MessageDigest.getInstance("SHA-256")`, `expect/actual`, `commonMain`/`androidMain`, gradle tasks (`./gradlew :app:dependencies`), package names (`cryptokit.crypto.api.*`), Android Keystore, X25519/Ed25519, XChaCha20-Poly1305. This is intentional for an infrastructure refactor spec but it does violate CHK001 strictly.

- [x] CHK002 Focus is on **user value and business need**, not on the technical "how".
  - Partially: US1 (pairing works) and US4 (smaller APK, faster cold start) tie to user value; US2 and US3 are developer-internal. Spec is candid that the feature is an infrastructure refactor — overall framing connects back to unblocking TASK-8.

- [ ] CHK003 Written so a non-technical stakeholder can read and validate.
  - **FAIL.** A non-technical stakeholder cannot validate `JNA eager-bind`, `expect/actual SecureKeyStore`, `Konsist fitness-tests`, or grep-based SCs. Some sections (US1 acceptance scenarios, SC-001/002) are readable; the bulk of FR-001..FR-016 is engineer-facing.

- [x] CHK004 All mandatory sections present: User Stories, Scope (In/Out), Functional Requirements, Success Criteria.
  - User Stories (US1–US4), Functional Requirements (FR-001..FR-016), Success Criteria (SC-001..SC-012) all present. Scope: implicit via Assumptions + Out-of-scope mentions (MLS, iOS deferred). No explicit `## Scope (In / Out)` section, but spec covers boundaries adequately via Assumptions. Marking covered with caveat.

## Requirement Completeness

- [ ] CHK005 No `[NEEDS CLARIFICATION]` markers remain.
  - **FAIL.** One residual marker found at line 212: `Test fakes for spec 011 wire-format adapters ... [NEEDS CLARIFICATION]`. The Clarifications resolution log at line 253 claims it was resolved ("удалить целиком"), but the inline marker in `## Local Test Path` was not removed.

- [x] CHK006 Every requirement is **testable** — there is at least one observable assertion that can verify it.
  - Every FR-001..FR-016 maps to a verifiable observation: grep counts, gradle task output, APK size diff, cold-start ms, smoke roundtrip on device, fitness tests. SC-001..SC-012 provide explicit verification commands.

- [x] CHK007 Every requirement is **unambiguous** — no "fast", "simple", "intuitive" without operationalisation.
  - FRs use concrete predicates ("0 matches", "BUILD SUCCESSFUL", "≥3 MB", "≤ 1330 ms", "byte-equal"). No weasel words like "fast"/"smooth"/"intuitive".

- [x] CHK008 Success criteria are **measurable** (numbers, percentages, time bounds).
  - SC-001 (no UnsatisfiedLinkError — binary observable), SC-003/004/005/012 (grep = 0), SC-008 (≥3 MB), SC-009 (≤1330 ms), SC-006 (BUILD SUCCESSFUL). Measurable.

- [ ] CHK009 Success criteria are **technology-agnostic** (no class names, protocols, framework features).
  - **FAIL.** SCs reference `com.goterl`, `lazysodium`, `JNA.register`, `SodiumAndroid`, `./gradlew`, `adb shell am start`, `Konsist`, `PairingActivity`, `HomeActivity`, `family.crypto`, `app-mockBackend-debug.apk`. This is unavoidable for an infrastructure refactor but violates CHK009 as written.

- [x] CHK010 All acceptance scenarios for each User Story are explicit (Given/When/Then or equivalent).
  - US1: 3 Given/When/Then scenarios. US2: 3 scenarios. US3: 2 scenarios. US4: 2 scenarios. All explicit.

- [x] CHK011 Edge cases are identified — at minimum: empty state, error state, retry, double-action.
  - `## Edge Cases` section covers: missing key (empty state), persisted-key migration conflict, JNI link failure (error state + fail-fast), stale test fakes. Retry/double-action are less directly addressed but the empty-state edge case ("regenerate idempotently") implies idempotence.

- [x] CHK012 Scope is clearly bounded — In Scope and Out of Scope are exhaustive for the area.
  - In-scope: 16 FRs cover crypto stack consolidation, deps removal, namespace rename, DI merge, migration strategy, fitness tests, APK/cold-start guards. Out-of-scope explicit via Assumptions: MLS (TASK-42), iOS (TASK-26), Samsung/Huawei verification (TASK-55 deferred), real 2-device handshake (TASK-8). Adequate boundary.

- [x] CHK013 Dependencies and assumptions are explicit (other specs, external services, device capabilities).
  - Assumptions section names: Xiaomi 11T as only physical device, pixel_5_api_34 emulator, ionspin 0.9.5 coverage, ristretto255 not needed, spec 011 wire format unchanged, MLS/iOS out-of-scope, TASK-6 future exit ramp, TASK-8 downstream dependency. Explicit.

## Feature Readiness

- [x] CHK014 All functional requirements have clear acceptance criteria (mapped to a US or independent).
  - FR↔SC traceability is explicit in each SC line ("Verifies FR-XXX"). Mapping: FR-001→SC-001, FR-002→SC-003/005, FR-003→SC-010, FR-005→SC-011, FR-007→SC-003/004/007, FR-008→SC-002, FR-011→SC-006, FR-012→SC-008, FR-013→SC-009, FR-016→SC-012. FR-004 (byte-equal wire format), FR-006 (deep migration impl), FR-009 (throws), FR-010 (delete AndroidKeystoreSecureKeystore), FR-014 (SHA-256 inline), FR-015 (single Koin module) have no dedicated SC — covered transitively by FR-011 (tests green) / FR-007 (fitness tests). Partial gap but acceptable.

- [x] CHK015 User scenarios cover **primary flows** — not just happy path; at minimum one error path per US.
  - US1 includes emulator-vs-device variations and smoke-roundtrip. Edge Cases section provides error paths (missing key, persisted-key conflict, JNI link failure). US2/US3/US4 are inherently verification-shaped (grep / size / cold start) — error paths less applicable.

- [x] CHK016 Feature meets **measurable outcomes** defined in Success Criteria (no SC without an FR producing the measurement).
  - All SC-001..SC-012 cite a verifying FR. Reverse direction: FR-004, FR-006, FR-009, FR-010, FR-014, FR-015 lack direct SC but inherit measurement through FR-011 (tests) and FR-007 (fitness tests). No orphan SCs.

---

## Verdict

**Status: FAIL (3 fails, 1 residual marker).**

- CHK001, CHK003, CHK009 fail due to deeply technical content. This is **partly justified** because TASK-51 is an infrastructure refactor where the "what" cannot be cleanly separated from the "how" — the user-visible outcome (PairingActivity no longer crashes) requires naming the implementation choice (drop JNA/lazysodium, adopt ionspin). However, the spec could still hide most names behind "the crypto adapter" / "the legacy native binding" without losing meaning.
- CHK005 fails on a residual `[NEEDS CLARIFICATION]` at line 212 that contradicts the Clarifications log (which says this question is resolved). Must be cleaned up.

## Open Items

1. **Line 212** — remove the `[NEEDS CLARIFICATION]` marker from `## Local Test Path` § "Fake adapters used" and replace with the resolved statement: "Test fakes live in `core/crypto/src/commonTest/kotlin/cryptokit/crypto/fake/` and `cryptokit/pairing/fake/`; old `com.launcher.fake.crypto.*` deleted." (CHK005)

2. **Technology-agnostic framing (CHK001/003/009)** — optional softening: in the lead-in to each FR, prefix the user-observable outcome before the vendor names. Example: instead of "System MUST убрать `lazysodium-android` (vendor: com.goterl)", lead with "System MUST remove the legacy native-bridge dependency (currently `lazysodium-android`)". Marks the implementation choice as today's-instance, keeps the spec readable for a stakeholder. **Recommend but not blocking** for an infra refactor spec.

3. **Missing explicit `## Scope` section (CHK004 caveat)** — consider adding a short `## Scope` block with explicit In / Out lists; currently boundary is reconstructed from Assumptions. Low priority.

4. **FR↔SC reverse trace (CHK014/016)** — FR-004 (byte-equal wire format), FR-006 (deep migration), FR-009 (uniform throws), FR-014, FR-015 have no dedicated SC. Either add SCs (e.g. "SC-013: `EnvelopeConfigCipherRoundtripTest` golden vectors pass byte-equal — verifies FR-004") or document explicitly that they are covered transitively by SC-006 (all tests green). Recommended for traceability.

---

requirements-quality: 11/16 CHK [x]
