# Checklists overview — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: /speckit.clarify procedure step 5 — full clarify pass post-rescope

## Checklist run summary

| # | Checklist | Pass | Partial | Fail | N/A | Critical issues? |
|---|-----------|------|---------|------|-----|------|
| 1 | [requirements-quality](requirements.md) | 11 | 5 | 0 | 0 | No |
| 2 | [meta-minimization](meta-minimization.md) | 9 | 4 | 0 | 0 | No (re-scope оправдан rule 4) |
| 3 | [dev-experience](dev-experience.md) | 8 | 13 | 0 | 1 | No (operational gaps) |
| 4 | [wire-format](wire-format.md) | 7 | 8 | 0 | 3 | No (clean design) |
| 5 | [modular-delivery](modular-delivery.md) | 8 | 3 | 0 | 7 | No |
| 6 | [domain-isolation](domain-isolation.md) | 13 | 3 | 0 | 1 | No (clean) |
| 7 | [backend-substitution](backend-substitution.md) | 11 | 3 | 0 | 0 | No (cost-of-swap documented) |
| 8 | [security](security.md) | 11 | 7 | 0 | 6 | No (operational privacy gaps) |
| 9 | [failure-recovery](failure-recovery.md) | 13 | 3 | 0 | 1 | No |
| 10 | [performance](performance.md) | 9 | 9 | 0 | 4 | No (threading FRs needed) |
| 11 | [permissions-platform](permissions-platform.md) | 4 | 2 | 0 | 16 | No |
| 12 | [notification-minimization](notification-minimization.md) | 4 | 0 | 0 | 16 | No (exemplary compliance) |
| 13 | [capability-registry-readiness](capability-registry-readiness.md) | 3 | 1 | 0 | 8 | No |
| 14 | [device-self-sufficiency](device-self-sufficiency.md) | 7 | 3 | 0 | 7 | No |
| **TOTAL** | | **118** | **64** | **0** | **70** | **No critical issues** |

**Bottom line**: 0 hard failures. 64 warnings — все resolveable через mostly cosmetic правки в spec.md (новые FRs, explicit notes) + несколько operational task'ов для plan.md / tasks.md. **Не блокирует** переход к /speckit.scenarios или /speckit.plan.

## Real issue surfaced: linkId consumers audit

**meta-minimization CHK012 action выполнен**: `grep linkId core/ app/` показал **8 файлов используют `PushPayload.linkId` или связанный wire-format**:

| File | Usage | Impact F-5c breaking change |
|---|---|---|
| `core/src/commonMain/kotlin/com/launcher/api/push/PushPayload.kt` | declares `linkId: String` field | **TO REWRITE** in F-5c per FR-024 |
| `core/src/commonMain/kotlin/com/launcher/api/push/PushPayloadWireFormat.kt` | encode/decode `KEY_LINK_ID` | **TO REWRITE** in F-5c |
| `core/src/commonTest/kotlin/com/launcher/api/push/PushPayloadWireFormatTest.kt` | roundtrip test | **TO UPDATE** in F-5c |
| `core/src/commonMain/kotlin/com/launcher/api/push/PushReceiver.kt` | interface that receives payload | **TO REWRITE** in F-5c per FR-023, FR-028 |
| `core/src/commonMain/kotlin/com/launcher/fake/push/FakePushReceiver.kt` | test fake | **TO UPDATE** in F-5c |
| `core/src/androidRealBackend/kotlin/com/launcher/adapters/push/LauncherPushReceiver.kt` | real impl — uses `payload.linkId` для `handleConfigChanged` + `handleCommandIssued` | **TO REWRITE** in F-5c — replace with PushHandlerRegistry dispatch (FR-028) |
| `core/src/commonTest/kotlin/com/launcher/api/pairing/PairingEndToEndTest.kt` | tests `PushPayload` creation с `linkId` | **TO UPDATE** in F-5c |
| `core/src/commonMain/kotlin/com/launcher/api/push/FcmReceiverContract.kt` | FCM adapter contract | **TO UPDATE** if signature changes |

**Conclusion**: F-5c scope includes rewriting **entire existing push subsystem** в `core/src/.../api/push/` + `LauncherPushReceiver`. **Не** только новый `core/push/` module — старый код тоже migration target.

**Action — обновить spec.md**: добавить explicit migration scope:
> «F-5c implementation migrates existing `core/src/.../api/push/*` (PushPayload, PushPayloadWireFormat, PushReceiver, LauncherPushReceiver) → new `core/push/` module with new wire-format + PushHandlerRegistry dispatch. 8 файлов affected. `linkId` field deprecated в новом PushPayload, finally removed в schemaVersion 2 bump после spec 008 rewrite ships».

Это **больше работы чем 5-7 дней** в roadmap'е. **Action**: пересмотреть effort estimate. Migration старого кода добавляет ~2 дня (rewrite 8 files + update 4 tests + verify no regressions).

**Updated effort estimate**: **7-9 дней** (был 5-7).

## Consolidated action items по приоритетам

### High priority (FRs / правки в spec.md перед /speckit.plan)

1. **(spec.md, scope expansion)**: explicit migration scope для 8 existing files (LauncherPushReceiver, PushPayloadWireFormat, PushReceiver, etc.) + bump effort estimate к 7-9 дней.
2. **(spec.md, новый FR)**: logging hygiene — truncated UID, no payload values, BuildConfig.DEBUG gating. (security CHK004, dev-experience CHK018, failure-recovery CHK016).
3. **(spec.md, новый FR)**: `LauncherFirebaseMessagingService` `android:exported="false"` в manifest (security CHK009).
4. **(spec.md, новый FR)**: F-5c MUST NOT request `POST_NOTIFICATIONS` (security CHK015, permissions-platform CHK012).
5. **(spec.md, новый FR)**: All push subsystem calls on IO dispatcher; `FcmTokenPublisher.publish()` + `PushTrigger.trigger()` MUST NOT block cold start (performance CHK001, CHK006).
6. **(spec.md, FR supplement к FR-031)**: Fire-and-forget = detached coroutine; save не awaits push (performance CHK007).
7. **(spec.md, новый FR)**: `PushHandler.handle()` 10s budget; long-running `loadOwn` deferred к WorkManager (performance CHK008).
8. **(spec.md, новый FR)**: Receiver MUST handle malformed payload с Logcat warning + silent ignore, never crash (wire-format CHK017).
9. **(spec.md, new block «Wire-format policy»)**: schemaVersion-first deserialization + MAX_SUPPORTED location pinned + forward-compat asymmetry (Worker fail-closed, client fail-soft) + URL versioning strategy = body-versioning + receiver malformed handling. (wire-format CHK002, CHK003, CHK008, CHK016, CHK017).
10. **(spec.md, new block «Cloud-mode integration»)**: F-5c mode = CLOUD-only; NullPushTrigger fallback в local mode; subscription expiry handling (401 → degrade to pull); no internet behaviour. (device-self-sufficiency CHK-DSS-001, CHK-DSS-007, CHK-DSS-010; modular-delivery CHK009; security CHK020).

### Medium priority (для tasks.md когда дойдём до /speckit.tasks)

11. **(tasks.md)**: create `workers/family-push/README.md` с Cloudflare setup instructions (CF account, wrangler CLI, KV namespaces, FCM_SERVER_KEY, deploy) (dev-experience CHK016, CHK022).
12. **(tasks.md)**: create `specs/019-.../contracts/` с tree files (push-trigger-request-v1, push-payload-v1, event-type-registry) (wire-format CHK018).
13. **(tasks.md)**: update `docs/compliance/permissions-and-resource-budget.md` с F-5c entry (security CHK018, permissions-platform CHK022).
14. **(tasks.md)**: perf-checkpoint.md task для SC-001, SC-002, SC-007 + APK delta (performance CHK018).
15. **(tasks.md)**: privacy policy update для FCM token publishing + event metadata transmission (S-1 wizard hook) (security CHK019, CHK021).
16. **(server-roadmap.md)**: добавить SRV-PUSH-QUOTA + SRV-PUSH-KV sub-entries для FCM Spark quota и Workers KV quota exit ramps (modular-delivery CHK015).
17. **(server-roadmap.md)**: production monitoring section — FCM latency p95, CF cold start frequency, jose failure rate (dev-experience CHK013, CHK014).

### Low priority (mostly cosmetic)

18. **(spec.md, одна строка)**: «Identity convention inherited from F-4/F-5b — ownerUid = Firebase UID directly. Exit ramp = F-4/F-5b territory» (backend-substitution CHK007, CHK008, CHK009).
19. **(spec.md, одна строка)**: «`PushTrigger` — event-level domain abstraction, not transport. FCM transport fixed behind FcmTokenPublisher» (backend-substitution CHK014).
20. **(spec.md, одна строка)**: «Why module not package: extraction-readiness preserved at near-zero cost» (meta-minimization CHK006, modular-delivery CHK005).
21. **(spec.md, одна строка)**: «Why registry over switch: switch requires foundation modification per new event type (FR-060 contradiction)» (meta-minimization CHK004).
22. **(spec.md, одна строка)**: HTTP client placement — «Ktor в commonMain для KMP portability» (modular-delivery CHK003).
23. **(spec.md, новый раздел «## Scope»)**: explicit In/Out bullets (requirements-quality CHK004, CHK012).
24. **(spec.md, новый FR)**: explicit `PushTriggerError` sealed class variants + emission conditions (failure-recovery CHK001).
25. **(spec.md, новый FR)**: `HttpPushTrigger` MUST accept `UuidGenerator` + `Clock` через constructor для test reproducibility (dev-experience CHK010).
26. **(spec.md, одна правка Local Test Path)**: flavor split (mockBackend/realBackend) для DI wiring (dev-experience CHK008, modular-delivery CHK009).
27. **(spec.md, одна правка Local Test Path)**: explicit fake adapter locations (`core/push/commonTest/fakes/`) (domain-isolation CHK010).
28. **(spec.md, одна правка Notes)**: failure visibility per-event-type (config-updated silent, future SOS may surface failure) (failure-recovery CHK002).
29. **(spec.md, одна правка Notes)**: user mental model gap — «save ≠ guaranteed propagation» (failure-recovery CHK003).
30. **(spec.md, одна правка Notes)**: FCM listener profile (source/frequency/threading/battery/fallback) (performance CHK011).
31. **(spec.md, одна правка Notes)**: WAKE_LOCK reliance disclaimer (через Firebase SDK) (performance CHK019).
32. **(plan.md, при implementation)**: measure clean-build time delta, APK delta — connect с TODO-ARCH-006 R8 milestone (performance CHK015).

### Information only (no action needed)

- F-5c is **exemplary** на notification-minimization (no user-visible alerts) — никаких action items.
- F-5c capability-registry-readiness — all critical N/A (transport infrastructure, not user-facing action).
- F-5c domain-isolation **clean** — все vendor SDKs за adapters.
- F-5c backend-substitution **cost-of-swap paragraph** написан в backend-substitution.md.

## Updated spec readiness

**Before clarify pass**: draft spec с 6 [NEEDS CLARIFICATION] markers.

**After clarify pass**:
- ✅ All 6 Clarifications resolved.
- ✅ 4 supporting artifacts updated (roadmap.md, server-roadmap.md, project-backlog.md, spec.md).
- ✅ 14 checklists run, 118 pass, 64 warnings, 0 fails.
- ⚠️ 32 action items identified (10 high priority + 7 medium + 15 low).
- 🔄 Effort estimate: 5-7 дней → **7-9 дней** (linkId migration scope expansion).

## Recommended next step

**Option A (recommended)**: Apply high-priority action items (10) в spec.md directly, затем перейти к `/speckit.scenarios` (между clarify и plan — per memory `feedback_speckit_scenarios_proactive`). High-priority правки — преимущественно новые FRs (~30-50 строк spec'а), не reшоп архитектуры.

**Option B**: Перейти сразу к `/speckit.scenarios` или `/speckit.plan` без apply high-priority items. Открытые warnings будут пойманы при `/speckit.analyze` re-run перед implementation. Риск: scenarios / plan может быть написан с неверными assumptions (например про async-ness, threading, scope migration linkId).

**Option C**: Skip checklists action items entirely, перейти к /speckit.plan. **Не recommended** — 10 high-priority items включают real FRs (logging, threading, scope expansion) которые plan.md обязан учитывать.

## TL;DR для новичка

Прогнали 14 checklists для F-5c. **0 critical fails** — спека солидная. **64 «частично»** — это в основном operational детали (логирование, threading, manual setup process) которые типично «не успевают записать» при clarify.

**Главный сюрприз**: при проверке нашёл что **8 файлов** в текущем коде используют `linkId` поле из `PushPayload`. Это значит F-5c migration scope больше чем казалось — нужно переписать существующий push subsystem (`LauncherPushReceiver`, `PushPayloadWireFormat`, и др.), не только построить новый `core/push/` module. Это добавляет ~2 дня к estimate (7-9 вместо 5-7).

**32 action items** разделены на 3 priority bucket'а:
- **10 high** — новые FRs в spec.md перед /speckit.plan (логирование, threading, scope expansion).
- **7 medium** — задачи для /speckit.tasks (README, contracts/, perf-checkpoint).
- **15 low** — одностроковые уточнения в spec.md notes.

**Recommended next step**: apply high-priority items (~30-50 строк добавить в spec.md), затем `/speckit.scenarios` (генерация user-flow sequences для finalной валидации перед planning).
