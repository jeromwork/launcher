# Device Self-Sufficiency Checklist: HomeActivity loading regression

**Purpose**: Verify spec respects device-self-sufficiency principle (decision 2026-06-15-deferred-cloud) — every device works locally without Google Sign-In; cloud is opt-in upgrade.
**Created**: 2026-06-26
**Feature**: [spec.md](../spec.md)

## Checks

- [x] **Fix не требует Sign-In** — HomeActivity loading работает полностью локально, без `IdentityProvider.currentIdentity()` dependency. `selfDeviceIdProvider` в HomeActivity возвращает «unsigned» pre-Sign-In — это existing behaviour, fix его не меняет.
- [x] **No cloud dependency added** — fix не вводит FCM / Firestore / Cloud reference.
- [x] **No telemetry** — FR-012 пишет logcat **локально**, не отправляет crash report.
- [x] **Local-only flow works end-to-end** — wizard → home → плитки. Никаких cloud action gates.
- [x] **Active preset hosting** — `presetRepository` хранит локально (existing). Fix не вводит cloud-sync для preset.
- [x] **No GMS check required** — `HomeComponent` работает на устройствах без GMS (Huawei without Google Services included).
- [x] **State машина обрабатывает offline** — Loading / Error не зависят от сетевого статуса (n/a — fix is local).
- [x] **LOCAL mode declaration** — fix остаётся в LOCAL mode (не вводит cloud bridge).

## Verdict

✅ **8/8 passed.**

## Notes

Bug-fix полностью совместим с device-self-sufficiency. Fix не вводит cloud surface и не добавляет Sign-In requirement. Это **wholly local** issue (config loading / lifecycle race) и **wholly local** solution.
