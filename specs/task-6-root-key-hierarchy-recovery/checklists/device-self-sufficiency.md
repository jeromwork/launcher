# Checklist: device-self-sufficiency

Applied to `specs/task-6-root-key-hierarchy-recovery/spec.md` on 2026-06-28.

Anchor: per [decision 2026-06-15-deferred-cloud](../../../docs/product/decisions/2026-06-15-deferred-cloud/) — each device self-sufficient, cloud opt-in at first cloud action.

| ID | Item | Status | Evidence / Notes |
|---|---|---|---|
| CHK-DSS-001 | Mode declared (LOCAL / CLOUD / HYBRID) | [x] | HYBRID: US-1/US-2 cloud-enhanced; US-4 local-only baseline via `NoOpRecoveryKeyBackup` + device-key namespace. Overview, US-4, FR-002/FR-011/FR-012. |
| CHK-DSS-002 | LOCAL-only path requires no Sign-In / network | [x] | US-4 + FR-002 + FR-011: device-key namespace in Android Keystore, `NoOpRecoveryKeyBackup` no-ops; wizard completes без `AuthProvider.signIn()` (US-4 scenario 1). |
| CHK-DSS-003 | CLOUD-only justified (N/A — spec is HYBRID) | [N/A] | Not CLOUD-only. |
| CHK-DSS-004 | HYBRID local-baseline + cloud-enhancement | [x] | Local-baseline: device-key namespace encrypts config/contacts locally (FR-002, US-4). Cloud-enhancement: cross-device recovery via `RecoveryKeyBackupBlob` on Drive (US-2). Local-baseline useful on its own (encryption works, just no cross-device recover). |
| CHK-DSS-005 | Sign-In trigger at user-initiated cloud action, not at launch/wizard step 1 | [x] | Spec defers trigger to TASK-49 «first cloud-action checkpoint»; explicitly NOT at app launch / wizard step 1 (Overview, US-1 opening: «доходит до cloud-action checkpoint'а»). |
| CHK-DSS-006 | Sign-In prompt copy explains unlocked capability | [x] | FR-014 neutral copy «придумайте пароль для восстановления» (NOT «sign in to Google»). US-4 scenario 1: explainer «cloud-фичи недоступны; локальные функции работают полностью» — symmetric framing. Setup screen wording focuses on cross-device recovery value. |
| CHK-DSS-007 | Graceful degradation if user declines Sign-In | [x] | US-4 covers non-available path (continues local). For Available-but-declined: FR-014 retry-with-confirm dialog «продолжить без облачной резервной копии?» + `recoveryBackupDeferred` flag, Settings retry. App stays usable locally. |
| CHK-DSS-008 | Uses `VersionedConfigViewer` for local→cloud merge (N/A — no merge) | [N/A] | Q-F clarification: local→cloud upgrade explicitly out-of-scope on MVP. No merge UI in this spec. |
| CHK-DSS-009 | Different Google account → local stays, new starts fresh | [x] | US-2 scenario 5: «identity на B соответствует другому stableId → fetchBlob возвращает null → setup path под аккаунтом B. Старый blob аккаунта A не трогается (identity isolation)». |
| CHK-DSS-010 | Cloud→local degradation: features pause cleanly, local data preserved | [x] | FR-010 revoke handling: `BackupError.AuthRevoked` → «доступ к облачному backup отозван»; «App продолжает работать на локальном RootKey (Drive не required online)». Local-only mode trade-off documented (US-4 scenario 4). |
| CHK-DSS-011 | User shown what specifically stopped, with action button | [x] | FR-010: «доступ к облачному backup отозван; выдайте разрешение в настройках Google аккаунта» — specific cause + action. Edge case «Drive App Data quota exceeded» — explicit message «облачный backup ключа недоступен» (not generic «premium required»). |
| CHK-DSS-012 | No mandatory Sign-In at first launch | [x] | Wizard does not require Sign-In; cloud-checkpoint is deferred mid-wizard event (TASK-49 owned). US-4 confirms wizard completes без signIn(). |
| CHK-DSS-013 | No mandatory pairing at first launch | [x] | Pairing is out-of-scope of F-5 entirely (TASK-21 P-6). |
| CHK-DSS-014 | No local feature bottlenecked behind cloud without justification | [x] | Local encryption (KeyRegistry derive) works without cloud via device-key namespace (FR-002, US-4 scenario 2). Cross-device recovery is naturally cloud (needs server) — justified. |
| CHK-DSS-015 | No anonymous Firebase Auth as Sign-In skip | [x] | No mention of anonymous Firebase Auth. Local-only mode uses device-key namespace (random UUID in Keystore), NOT anonymous Firebase. |
| CHK-DSS-016 | Cross-spec: what user sees when cloud-data unavailable | [x] | US-4 + SC-003: «ни одного crash, ни одного UI прыжка на 'настроить Google'». FR-019: Drive blob not deleted on local wipe — recovery works on next install with sign-in. Documented in `docs/recovery-flow.md` (FR-020). |
| CHK-DSS-017 | Uses Outcome/Result with explicit cloud-unavailable handling | [x] | All ports return `Outcome<T, Error>` (FR-003/FR-004); `BackupError.AuthRevoked`, `BackupError.QuotaExceeded`, `RootKeyError.CorruptedBlob` explicit. `AuthAvailability.check()` returns `Available | Unavailable(reason)` (FR-005). |

## Result

17/17 PASS (2 N/A для CLOUD-only и local→cloud merge — both legitimately not applicable: spec is HYBRID with no merge UI per Q-F).

Spec explicitly respects device-self-sufficiency:
- HYBRID mode with viable local baseline (US-4, FR-002, FR-011).
- Sign-In trigger deferred to TASK-49 cloud-checkpoint (NOT first launch).
- Non-GMS device → `NoOpRecoveryKeyBackup`, no deceptive «Sign in to Google» UI.
- LOCAL→CLOUD upgrade explicit out-of-scope on MVP (Q-F), exit ramp to Phase 5+ spec.
- Identity isolation between accounts (US-2 scenario 5).
