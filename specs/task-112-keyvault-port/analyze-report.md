# Pre-Implementation Analysis Report

**Feature**: TASK-112 KeyVault Port Boundary
**Date**: 2026-07-14 (updated after Session 7 owner review)
**Verdict**: **READY**

## Scope

Cross-artifact consistency audit + constitution re-check + risk surface scan across:
- [spec.md](spec.md)
- [plan.md](plan.md)
- [tasks.md](tasks.md)
- [TASK-112 Decision block (Session 6 revised)](../../backlog/tasks/task-112%20-%20Decision-Cross-platform-IdentityVault.md)

## Cross-artifact traceability

Each `FR-nnn` in spec → traced to task in tasks.md → traced to Decision block section.

| Requirement | Task | Decision block section | Status |
|---|---|---|---|
| FR-001 KeyVault interface | T001 | Port shape | ✅ traced |
| FR-002 Purpose enum minimal | T002 | Purpose registry | ✅ traced |
| FR-003 blob header format | T003, T007 | Blob format | ✅ traced |
| FR-004 canonical AAD | T003 | AAD contents | ✅ traced |
| FR-005 RecoveryStrategy port | T005 | Session 6 owner insight | ✅ traced |
| FR-006 PassphraseRecovery TASK-6 semantics | T006 | Revision history / RecoveryStrategy | ✅ traced |
| FR-007 no vendor imports in :core:keys | T013 | Rule 1 domain isolation | ✅ traced |
| FR-008 RootKey/KeyRegistry internal | T023, T024 | Migration plan phase 4 | ✅ traced |
| FR-009 ConfigCipher2 / EnvelopeStorage migration | T019, T020 | Migration plan phase 3 | ✅ traced |
| FR-010 storage split | T014, T015 | Storage split | ✅ traced |
| FR-011 cross-platform test vectors | T012, T017 | Cross-platform test vectors | ✅ traced |
| FR-012 sealed VaultException | T004 | Sealed exception hierarchy | ✅ traced |
| FR-013 no exportDerivedKey | (negative — verified in T001 by absence) | C2 owner decision | ✅ traced |
| FR-014 FakeKeyVault | T008 | Rule 6 mock-first | ✅ traced |
| FR-015 5-phase migration | Phase 1-5 headers | Migration plan | ✅ traced |

Success Criteria backwards trace:

| SC | Task(s) | Notes |
|---|---|---|
| SC-001 [backlog] all DerivedKey.bytes migrated | T018 (grep), T019, T020, T023, T024, T025 | Complete |
| ~~SC-002~~ DROPPED (Session 7 F2 — no TASK-6 users) | — | Removed |
| SC-003 [backlog] TASK-6 recovery flow regression | T021 (DI wiring) + T006 (PassphraseRecovery) + implicit `RecoveryUiTest` re-run | Covered |
| SC-004 cross-platform vectors byte-equal | T012, T017 | Complete |
| SC-005 [backlog] TASK-6 UI flow unchanged | T021 + existing RecoveryUiTest | Covered |
| SC-006 fitness rule domain isolation | T013, T026 | Complete |
| SC-007 AAD tamper → TamperDetected | T009 KeyVaultContractTest | Complete |
| SC-008 Purpose enforcement via header | T009, T010 | Complete |
| SC-009 [backlog] TASK-124 openmls unblocked | Non-blocking, verified via Session 6 openmls research | Complete (research proof) |
| SC-010 RootKey public API removed | T023 | Complete |
| SC-011 RecoveryStrategy extensibility | T011 RecoveryStrategyTest | Complete |

**No FR without task. No SC without coverage.**

## Constitution Re-check (Article XVI)

Re-run of 8 gates after plan design (per constitution requirement):

| Gate | Status | Notes |
|---|---|---|
| Architecture (rule 1, 2) | ✅ | Detekt fitness rule (T013) + adapter isolation |
| Core/System Integration | ✅ | No transport types in domain, no static singletons |
| Configuration (rule 5) | ✅ | Blob header carries format_version; Argon2Params versioned; test vectors JSON versioned |
| Required Context Review | ✅ | Sessions 1-6 documented; downstream dependencies enumerated in plan |
| Accessibility | N/A | Zero UI — TASK-6 UI unchanged |
| Battery/Performance | ✅ | Sync API (per libsodium precedent); Argon2id runs once at unlock; performance targets в plan.md |
| Testing (rule 6, 7) | ✅ | Mock-first FakeKeyVault; port contract tests; cross-platform vectors |
| Simplicity (rule 4) | ✅ | Purpose enum minimal (2 variants); no premature abstractions (SigningPort separate, single-impl KeyRegistry public) |

**All 8 gates pass.**

## Risk surface

### Medium risk

1. **libsodium-kmp Argon2id API surface** — plan assumes `crypto_pwhash` binding доступен для Argon2id + HKDF (`crypto_kdf_derive_from_key` для salt derivation). **Verification needed** в phase-1 T006 before locking `Argon2Params.V1` parameters. **Mitigation**: если API отсутствует — использовать `libsodium.jvm.core` napkin binding via kotlinx-crypto shim, что затянет phase-1 на ~0.5 day.

2. ~~**TASK-6 legacy blob fixture**~~ **DROPPED** — Session 7 owner confirmed no TASK-6 production users exist. Backward-compat requirement removed, T022 repurposed для wipe integration test.

3. **DI wiring `KeyVault` singleton** (T021) — CLAUDE.md says «Manual constructor injection (без DI framework в MVP)». Existing codebase may already have DI framework (Koin/Hilt) — need to grep at phase-3 start. **Mitigation**: pattern-match existing DI setup, follow it.

### Low risk

4. **Detekt fitness rule** (T013, FR-007) — если project не имеет detekt configured, добавление new rule = additional setup. **Mitigation**: fallback на lint-rules module (existing at `:lint-rules`), custom AndroidLint check for forbidden imports.

5. **Cross-platform vector iOS parity** (postponed to TASK-26) — vectors captured из Android now; iOS adapter via TASK-26 must match. If swift-sodium diverges от Android libsodium binding в edge case (unlikely — both wrap same C library) — TASK-26 discovers, potentially escalates. **Not blocking TASK-112**.

## Sanity checks

- **Package naming**: Session 7 F3 owner decision — align к existing `com.launcher.core.keys.*` pattern. All artifacts (spec, plan, tasks, Decision block) already updated. ✅.

- **Backlog task file references**: task-112 file link paths use spaces (`task-112 - Decision-Cross-platform-IdentityVault.md`). Confirmed correct via markdown link URL encoding (`%20`). ✅.

- **Dependencies chain**: TASK-2 (libsodium-kmp Done), TASK-6 (Done) — both prerequisites satisfied.

## Actionable follow-ups (fold into tasks.md if adopted)

- **F1** (still open): T006 opens phase-1 by verifying libsodium-kmp Argon2id + HKDF API. If missing → T006 blocked until shim (~0.5 day).
- ~~**F2**~~ DROPPED (Session 7 — no TASK-6 users).
- ~~**F3**~~ RESOLVED (Session 7 — `com.launcher.core.keys.*` chosen, all artifacts updated).

## Session 7 additions (post-analyze owner review)

**Salt derivation approach** — Bitwarden pattern (client-side, deterministic from stable public identifier):
- GMS device: `salt = HKDF(googleUid.toByteArray(UTF_8), info="salt-v1", 16)` — no separate salt storage needed.
- No-GMS device: `salt = deviceRandomSalt` (16 bytes CSPRNG, stored device-only в Android Keystore при first setup).
- `IdentityHint` sealed class differentiates path.
- Reference implementations: WhatsApp E2E Backup 2021 (HSM+OPAQUE overkill for MVP), Signal SVR (SGX overkill), Bitwarden (matches our scale).

**Wipe cascade** — `KeyVault.wipe()` method added to port. Called from logout event, atomically clears root_key from Android Keystore + in-memory state. Idempotent. All subsequent ops throw `NoRootKey` until re-unlock. New SC-012 covers.

**Passphrase validation** — inside `PassphraseRecovery` adapter (D1). Known-plaintext blob `"vault-init-v1"` sealed at first setup, verified via `aeadOpen` attempt on subsequent unlock. Wrong passphrase → `RecoveryFailed` (not silent). New SC-013 covers.

**Package naming** — all `family.keys.*` prose references в task-file + spec + plan + tasks replaced with `com.launcher.core.keys.*`. Aligns с existing `com.launcher.core.crypto`, `.cloud`, `.push` modules.

## Verdict

**READY for implementation**. Всё traced, все FR + SC покрыты, constitution pass, риски surfaced с mitigation'ами.

Recommended sequence for fresh implementation session:
1. Read `spec.md`, `plan.md`, `tasks.md`, `analyze-report.md` (в этом порядке).
2. Read Session 6 + Session 7 Decision block в task-112 file (current contract).
3. Verify libsodium-kmp `crypto_pwhash` (Argon2id) + HKDF (`crypto_kdf_derive_from_key`) API surface (F1) before starting T001.
4. Start Phase 1 T001-T013 with tick-sync HARD RULE.

Package naming decision (F3) — уже resolved к `com.launcher.core.keys.*`. Backward-compat (F2) — уже dropped.
