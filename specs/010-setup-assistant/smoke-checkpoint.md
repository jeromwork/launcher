# Smoke checkpoint — спек 010 setup-assistant

**Создан**: 2026-05-19 (Phase 8 T110).
**Status**: device-dependent smoke entries deferred — see «Deferred» section.

---

## Готово (автоматизировано в commonTest/androidUnitTest)

| Task | Что покрыто | Результат |
|------|-------------|-----------|
| T040 | ARCH-016 — Slot→Action mapping, ConfigBackedFlowRepository observe path | unit tests green (commits 5659664) |
| T051 | Wizard step transitions FR-007/008 (RoleHome → PostNotifications) | `WizardScreensTest` green |
| T064 | Call confirmation dialog — invalid-number guard, ACTION_DIAL fallback | `CallConfirmationDialogTest` green |
| T079 | Fresh-install Settings badge — `!N≥2` после wizard skip | `SetupChecksBadgeTest` green |
| T080 | After grant `N=0` — badge disappears | `SetupCheckEngineTest` green (FR-020 + FR-020a) |
| Phase 6 unlink path (a/b/c) | LocalLinkRevocationStore + presenter | `PairedDevicesPresenterTest` + `InMemoryLocalLinkRevocationStoreTest` green |
| Phase 7 challenge gate | SevenTapDetector window/delta/escalation | `SevenTapDetectorTest` (6 cases) + `ChallengeSaverTest` + Robolectric `ChallengeGateScreenTest` |

---

## Deferred — требует физического устройства

Memory `feedback_critical_mentor_stance.md` + `reference_testing_environment.md`
явно блокируют использование физического устройства автоматизированно;
все нижеперечисленные пункты остаются как **inline-TODO в спеке/коде** и
оформляются как manual QA-пасс перед ship.

| Task | Сценарий | Inline-TODO маркер | Ожидаемый результат |
|------|----------|---------------------|----------------------|
| T052 | Wizard fresh-install end-to-end на Pixel 4a (Android 13/14) | `physical-device:wizard-e2e` | RoleHome → PostNotifications → Home; стартовое окно ≤ 1 sec |
| T053 | Wizard skip path → Settings shows `!2 критичных` badge | `physical-device:wizard-skip` | Badge visible после skip обоих steps |
| T065 | Call flow real Pixel (CALL_PHONE granted) — 2-tap to call | `physical-device:call-flow` | tile tap → confirm → System dialer rings target |
| T093 | Unlink offline → toggle WiFi → server-side `/links/{linkId}.revoked = true` ≤ 60 sec | `physical-device:unlink-reconnect` | Firestore log entry с timestamps + WorkManager work `unlink_<linkId>` succeeds |
| T102 | TalkBack walkthrough — 7-tap → challenge read → CANCEL → home | `physical-device:talkback-gate` | TalkBack reads challenge digits aloud; CANCEL focusable первым |
| T106 (Samsung) | Samsung One UI — CALL flow с OEM-specific contact resolver | `physical-device:oem-samsung` | Call успешно, no UI regression |
| T106 (Xiaomi) | Xiaomi MIUI — `BatteryOptimizationCheckAdapter` SecurityException path FR-020b | `physical-device:oem-xiaomi-miui` | Throw в `check()` → `CheckStatus.NotConfigured(reason=…)` + `SetupCheckException` event emitted; no crash |
| T106 (Pixel emulator) | Baseline Pixel emulator end-to-end | `physical-device:oem-pixel-baseline` | All flows pass; matches CI green |
| T107 | Macrobenchmark — HomeScreen cold-start ≤ 1 sec p95 на Pixel 4a class | `physical-device:macrobenchmark-pixel4a` | p95 ≤ 1000 ms; results saved в `perf-checkpoint.md` |
| T105 walkthrough | 5 elder-user scenarios (fresh install / tile→call / accidental 7-tap+cancel / TalkBack admin entry / paired-device unlink) | `physical-device:senior-walkthrough` | See `senior-safe-walkthrough.md` |

---

## Запуск manual smoke (когда устройство появится)

```
# Wizard end-to-end:
adb install -r app-mockBackendDebug.apk
adb shell pm clear com.launcher.app.mock  # стереть DataStore чтобы wizard перезапустился

# Call flow:
adb shell pm grant com.launcher.app.mock android.permission.CALL_PHONE

# Xiaomi battery-opt path — install на MIUI device, открыть Settings →
# «Что нужно настроить» — должен показывать NotConfigured без crash.

# TalkBack:
adb shell settings put secure enabled_accessibility_services \
    com.google.android.marvin.talkback/.TalkBackService
adb shell settings put secure accessibility_enabled 1
```

Manual QA-нот сохраняется в этой же `smoke-checkpoint.md` под секцией
«Manual smoke runs» с датой + устройством + результатом.

---

## Manual smoke runs

_(пустой; добавляется по мере выполнения)_
