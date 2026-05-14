# Spec 008 — Manual Smoke Test

**Task**: T143 — Manual 2-device smoke test (deferred from current
implementation session because requires physical Android devices or
emulators).

Per plan.md §Rollout / Verification §4 («Manual 2-device smoke documented
в `smoke/008/README.md`»).

---

## Pre-requisites

1. Two Android devices (or emulators) с GMS, running our app.
2. Both flavors `realBackend` install via `./gradlew :app:installRealBackendDebug`.
3. Firebase project linked (per spec 007 setup).
4. Test pairing complete (spec 007 — both devices паировались).

---

## Test scenarios

### Scenario 1: US-1 — Admin edit → Managed apply

1. On admin device, open Settings of paired Managed device.
2. Make a change (e.g., toggle preset).
3. Verify autosave (banner «Есть изменения, которые ещё не отправлены»
   appears within ~300 ms).
4. Tap «Отправить сейчас».
5. Spinner appears immediately (SC-001 / FR-015).
6. Within ~3 seconds («Отправлено ✓» appears (Firestore acked).
7. Within additional ~3 seconds, «Применено на телефоне ✓» appears
   (SC-001b — Managed wrote /state).
8. On Managed device, verify launcher screen reflects the new preset.

**Pass criterion**: 100% of edits visible on Managed within ~10 seconds
total wall-clock.

---

### Scenario 2: US-2 — Two devices concurrent edit → merge

1. From admin's phone: edit плитка «Маша», do NOT push (autosave only).
2. From admin's tablet: edit different плитка «Петя», push.
3. From admin's phone: tap «Отправить сейчас».
4. **Expected**: merge UI appears showing both changes.
5. Pick «Применить оба» (default for non-overlapping).
6. Verify both changes visible на Managed.

**Pass criterion**: merge UI appears при конфликте; user choice respected.

---

### Scenario 3: US-3 — Managed-as-editor

1. On Managed device, perform 7-tap + password to enter Settings.
2. Edit a plate.
3. Tap «Отправить сейчас».
4. Verify spinner → «Отправлено ✓».
5. On admin device, verify /state reflects the edit.

**Pass criterion**: Managed-as-editor write succeeds without errors.

---

### Scenario 4: US-4 — Pending warning across restart

1. From admin: edit и DON'T push.
2. Force-kill admin app (swipe из task switcher).
3. Re-launch admin app.
4. Verify pending badge «Не отправлено» visible на Managed device entry
   в device list (FR-046).
5. Open Settings того устройства → verify banner FR-047.
6. Tap «Отменить изменения» → confirm dialog appears (FR-057).
7. Tap «Удалить» → pending cleared.

**Pass criterion**: pending state survives process kill; banner & badge
visible; discard requires confirmation.

---

### Scenario 5: US-5 — Cold start with last-applied

1. Apply a config to Managed (через Scenario 1).
2. Force-kill Managed app.
3. Re-launch launcher.
4. Verify last-applied config rendered immediately (≤ ~1 second visible
   delay), без «загрузка...» state.
5. After ~5 seconds, network refresh fires (silent — UI doesn't blink).

**Pass criterion**: SC-004a met; no white-flash или loading spinner.

---

## Sign-off

| Scenario | Tested by | Date | Pass/Fail | Notes |
|---|---|---|---|---|
| US-1 admin edit → apply | _ | _ | _ | _ |
| US-2 merge | _ | _ | _ | _ |
| US-3 Managed-as-editor | _ | _ | _ | _ |
| US-4 pending warning | _ | _ | _ | _ |
| US-5 cold start | _ | _ | _ | _ |

Screenshots: attach to `screenshots/` subfolder before merging spec 008.

---

## Why this is a skeleton

Per implementation session note: «manual emulator/device tasks deferred to
separate session». The code paths exercising these scenarios are covered
by `commonTest` unit tests и in-process E2E (Phase 11), so the
behavior is verified offline. The smoke test confirms **real-device
integration** (Firestore HTTP latency, FCM push delivery, Android
process lifecycle on a real OEM).
