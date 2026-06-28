# Checklist: requirements-quality

Applied to: `specs/task-6-root-key-hierarchy-recovery/spec.md`
Date: 2026-06-28
Source: `.claude/skills/checklist-requirements-quality/SKILL.md`

## Results

| CHK | Status | Reasoning |
|-----|--------|-----------|
| CHK001 | ✗ | Spec leaks implementation details that belong in plan.md: Compose Composable signatures (`Modifier.semantics { contentType = ContentType.NewPassword }`), kotlinx-serialization, libsodium `crypto_kdf_hkdf_sha256_*`, Konsist/Detekt, `play-services-auth`, `SavedStateHandle`, Android `DataStore`. KDF parameters in FR-006 are justified as wire-format (rule 5), but Composable APIs and library names are not. |
| CHK002 | ✓ | USs and TL;DR frame value (recovery on new device, local-mode fallback, provider portability). "Why this priority" present per US. |
| CHK003 | ✓ | NOVICE-SUMMARY block in plain Russian; per-US plain-language narrative; recovery-flow.md mandated as senior-readable (FR-020, SC-006). |
| CHK004 | ✓ | All mandatory sections present: User Scenarios & Testing, Scope (Что строим / Что НЕ строим), Functional Requirements (FR-001..023), Success Criteria (SC-001..013). |
| CHK005 | ✓ | No `[NEEDS CLARIFICATION]` markers; clarifications resolved in `## Clarifications` table (Q-A..Q-L). |
| CHK006 | ✓ | Each FR has observable assertion: grep fitness functions (FR-001), contract tests (FR-023), roundtrip tests, byte-equal decrypt (FR-018), UI button-disabled state (FR-014). |
| CHK007 | ✓ | Quantified: passphrase ≥ 8 chars, 5 attempts threshold, Argon2id iterations=3 memoryKb=65536 parallelism=1, ≤3s P95, text ≥18sp, tap ≥56dp, contrast ≥4.5:1, salt 32 bytes, nonce 24 bytes. |
| CHK008 | ✓ | SC measurable: byte-equal ciphertext (SC-001, SC-004), 5 attempts → fallback (SC-011), ≤3s P95 (SC-010), grep returns 0 lines (SC-007), JSON schema field allow-list (SC-008). |
| CHK009 | ✗ | SC references concrete tech: SC-007 names `Konsist / Detekt`; SC-010 names emulator `pixel_5_api_34`; SC-005 names Android Autofill + Google Password Manager + `ContentType.NewPassword`. Should be reformulated as outcomes (e.g., "static-analysis rule" / "target reference device"). |
| CHK010 | ✓ | Each US has numbered Given/When/Then Acceptance Scenarios (US-1: 5, US-2: 5, US-3: 4, US-4: 4, US-5: 4, US-6: 4). |
| CHK011 | ✓ | Edge Cases section covers empty passphrase, emoji, no Keystore, logout-mid-flow, identity switch, quota exceeded, corrupted blob, 5 wrong attempts, two identities on same device. |
| CHK012 | ✓ | Scope bounded via "Что строим" + "Что НЕ строим" list (social recovery, multi-admin, key rotation, 2FA escrow, cross-provider migration, server replacement, passphrase change, local→cloud upgrade, server-side rate-limit — all explicit deferrals with target task IDs). |
| CHK013 | ✓ | Assumptions section lists upstream specs (F-4/017, TASK-49, TASK-51, TASK-2..5), platform constraints (minSdk 24, Spark plan, GMS availability, scope `drive.appdata`). |
| CHK014 | ✓ | FR→US mapping evident: FR-002/003 ↔ US-1/US-2, FR-014/015/016 ↔ US-1/US-2/US-3 screens, FR-011/012/013 ↔ US-4, FR-018 ↔ US-5, FR-001/006 ↔ US-6, FR-023 contract tests ↔ SC-013. |
| CHK015 | ✓ | Error paths per US: US-1 scenario 3 (validation), US-1 scenario 5 (no provider), US-2 scenario 2 (wrong passphrase), US-2 scenario 3 (no blob), US-3 (fallback), US-4 (local mode), Edge Cases (quota, corruption, logout). |
| CHK016 | ✓ | Each SC ties to FR producing the measurement: SC-001↔FR-002/003/004, SC-003↔FR-011/012/013, SC-004↔FR-018, SC-007↔FR-001, SC-008↔FR-006, SC-009↔US-6, SC-010↔FR-009, SC-011↔FR-015, SC-012↔FR-019, SC-013↔FR-023. |

## Summary

14/16 ✓, 2 fails (CHK001 implementation-detail leakage, CHK009 non-technology-agnostic SCs).

## Recommended remediation

- **CHK001**: Move Composable API specifics (`Modifier.semantics`, `ContentType.NewPassword`, `SavedStateHandle`, kotlinx-serialization, Konsist/Detekt, libsodium binding names) from spec.md to plan.md. Keep in spec only: behavioural requirement ("UI must declare autofill hint for new password"), wire-format JSON schema (FR-006, justified by rule 5), and KDF parameter numbers (justified as wire-format).
- **CHK009**: Reformulate SC-005 ("Autofill prefills passphrase when same provider session is shared across devices"), SC-007 ("static-analysis rule reports 0 vendor-name matches in `core/keys/`"), SC-010 ("Argon2id derivation ≤3s P95 on target reference device class"). Tech names move to plan.md test plan.
