# Permissions / Platform Checklist: HomeActivity loading regression

**Purpose**: Verify runtime-permission flows, OEM behaviour quirks, Android version-specific requirements, HOME role constraints.
**Created**: 2026-06-26
**Feature**: [spec.md](../spec.md)

## Checks

- [N/A] **New permissions required** — fix не добавляет permissions.
- [N/A] **Manifest changes** — fix не меняет AndroidManifest.xml (state machine — pure-Kotlin в core).
- [x] **OEM-specific behaviour** — Xiaomi MIUI разобран в OEM Matrix. Other OEMs не покрываются (see Assumption + OEM Matrix).
- [N/A] **HOME role (ROLE_HOME)** — fix не трогает role acquisition (это TASK-7 territory).
- [N/A] **Package visibility (Android 11+)** — fix не запрашивает QUERY_ALL_PACKAGES / queries в manifest.
- [N/A] **Scoped storage (Android 10+)** — fix не работает с file storage.
- [N/A] **Foreground service types (Android 14+)** — fix не запускает foreground service.
- [x] **MIUI quirks specifically called out** — OEM Matrix отмечает «aggressive process kill, deferred broadcast delivery» как possible root cause. План должен проверить.
- [x] **Verification path includes physical Xiaomi** — SC-001 + `[deferred-physical-device]` AC в backlog.
- [N/A] **Background restrictions** — fix не имеет background components.
- [x] **`runBlocking` в onCreate vs Android 13+ strict mode** — potential issue. Существующий код; план должен решить (см. performance checklist open issue).

## Verdict

✅ **6/6 applicable passed**, 5 N/A (фича не трогает permission / platform surface за исключением OEM quirks).

## Open issues

См. performance checklist — `runBlocking` в onCreate подлежит plan-стадии решению.
