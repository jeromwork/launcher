# Cross-Artifact Trace — spec 007

**Generated**: 2026-05-11 by `procedure-cross-artifact-trace` after `/speckit.tasks`.
**Purpose**: проверка consistency между spec.md ↔ plan.md ↔ tasks.md ↔ contracts/ ↔ checklists/.

## Summary

| Check | Result |
|---|---|
| 1. Spec → Tasks coverage (37 FRs) | ✅ PASS |
| 2. User stories → acceptance evidence (5 US) | ✅ PASS (1 minor warning resolved by T110 update) |
| 3. Plan → Spec ground | ✅ PASS (1 info note on TrustEdgeBootstrap) |
| 4. Contracts → tests (6 contracts) | ✅ PASS |
| 5. Checklists → spec citations (3 checklists) | ✅ PASS |
| 6. Deleted-file references | ✅ PASS (no deletions in 007) |
| 7. Tasks → ordering | ✅ PASS (after T048 split into T048a + T048b) |
| 8. Required context links | ⚠ Minor (ADR-005/006 в spec US-3 bare text — formatting only) |

**Overall: 8/8 checks resolved or PASS. No blockers for /speckit.analyze.**

## Resolved punch items

| # | Issue | Resolution |
|---|---|---|
| 1 | T048 dependency on Phase 4 while T048 is in Phase 3 | **Split** into T048a (skeleton in Phase 3) + T048b (finalize after Phase 4). Tasks.md updated 2026-05-11. |
| 2 | SC-003 (push ≤10s end-to-end) not explicitly measured | **T110 updated** — explicit timing measurement (record admin notify → managed onPush timestamps; Doze test via `adb dumpsys deviceidle force-idle`). |

## Deferred to /speckit.analyze

| # | Issue | Reason for deferral |
|---|---|---|
| 3 | ADR-005/006 references in spec.md US-3 as bare text | ADR-006 ещё не создан (T006); конвертация в markdown links имеет смысл после T006 |
| 4 | Optional FR-038 для явного grounding TrustEdgeBootstrap sealed type | Текущее grounding в research.md + project memory достаточное; FR-038 — strict-mode добавление |

## Traceability — full

### FRs → Tasks (37 FRs)

| FR(s) | Covering tasks |
|---|---|
| FR-001 (managedDeviceId UUIDv4) | T024, T053 |
| FR-002 (Anonymous Auth) | T001, T053 |
| FR-003 (pairing token create) | T015, T017, T071, T076 |
| FR-004 (QR display + countdown) | T086 |
| FR-005 (QR scan via camera) | T089 |
| FR-006 (admin claim transaction) | T078, T072 |
| FR-007 (consent screen on claim) | T087 |
| FR-008 (decline cleanup) | T080 |
| FR-009 (initial state on allow) | T079, T019 |
| FR-010..014 (RemoteSyncBackend) | T011-T013, T028, T051, T102 |
| FR-015 (FCM registration) | T053, T056 |
| FR-016 (FCM data-message receive) | T056, T021, T027 |
| FR-017 (FCM token rotation) | T056 |
| FR-018 (non-GMS polling stub) | C13 stub-only (no implementation tasks) |
| FR-019..025 (Cloudflare Worker) | T061-T070 |
| FR-026..030 (Firestore schema + security) | T031-T038, T071-T075 |
| FR-031 (Settings paired block) | T088 |
| FR-032 (double-confirm unbind) | T088 |
| FR-033 (hard-delete subtree on revoke) | T058 |
| FR-034..035 (build flavor + DI) | T045-T050, T048b |
| FR-036 (admin notify after write) | T054, T090 |
| FR-037 (Managed receiver logs) | T059 |

### Contracts → Tests (6 contracts)

| Contract | Roundtrip task | Backward-compat task | Future-version test |
|---|---|---|---|
| pairing-token.md | T034 | T034 | T034 |
| link.md | T036 | T036 | T036 |
| state-bootstrap.md | T036 | T036 | T036 |
| qr-deeplink.md | T037 | T037 | T037 |
| fcm-payload.md | T038 | T038 | T038 |
| worker-notify.md | T068 (vitest) | T068 | T068 |

### USs → Test evidence (5 USs)

| US | Unit | Integration | UI | E2E smoke |
|---|---|---|---|---|
| US-1 (Pairing QR) | T083 | T098 | T086, T089 | T110 |
| US-2 (RemoteSyncBackend) | T039-T040 | T097 | — | T101 |
| US-3 (Consent + revoke) | T080 | T097 | T087, T088, T094 | T110 |
| US-4 (Push via Worker + FCM) | — | T068, T099 | — | T110 (with SC-003 timing) |
| US-5 (FakeRemoteSyncBackend) | T039-T040 | — | — | T101 |

### Success Criteria → Measurement tasks

| SC | Task |
|---|---|
| SC-001 (pairing ≤10s) | T106 |
| SC-002 (realtime update ≤2s) | implicit в T097 |
| SC-003 (push ≤10s end-to-end) | **T110 updated** (timing measurement explicit) |
| SC-004 (polling fallback 15min) | N/A — FR-018 stub-only per C13 |
| SC-005 (Fake suite ≤500ms) | T039, T040, T101 (suite timing assertion) |
| SC-006 (APK Δ ≤+3MB) | T108 |
| SC-007 (cold start ≤650ms) | T105 |
| SC-008 (no Firebase leaks via Konsist) | T102 |
| SC-009 (roundtrip tests) | T034, T036-T038, T068 |
| SC-010 (backward-compat tests) | same as SC-009 |
| SC-011 (Worker p95 ≤500ms) | T107 |
| SC-012 (Worker rate-limit) | T068 (vitest rate_limit_triggers_429) |

## Re-run trigger

Пере-запускается в `/speckit.analyze` (Step 5) с финальным состоянием всех артефактов и кода после Phase 12.
