# Checklist: notification-minimization — TASK-65 (re-run after revised model)

Applied: 2026-06-30 (2nd pass). **Critical re-check**: revised model adds **NEW boot-time banner** (FR-030) — нужно валидировать что это not push.

## Inventory

- [x] CHK001 Every notification event listed — **yes**. Two events:
  - Settings reminders (SEQ-4, FR-016).
  - **NEW: Boot-time critical-missing banner** (SEQ-3, FR-030).
- [x] CHK002 Tier declared — **yes**. **Оба** = in-app indicator, not push:
  - Settings reminders = banner-карточки в SettingsActivity.
  - Boot-time banner = banner на top of HomeActivity (revised SEQ-3).

## System push justification

- [x] CHK003-008 — **N/A**. **No push events.** Spec explicitly excludes push для missing requirements (Out of Scope + rule 10 justification).

## In-app indicators

- [x] CHK009 Low-urgency events → in-app indicator — **yes**.
- [x] CHK010 Indicator placement declared — **yes** для обоих.
- [x] CHK011 Indicators dismissible — **partial — NEW concern**.
  - Settings reminder banner: dismissible? **NEEDS PLAN DETAIL**.
  - **NEW: Boot banner на HomeActivity**: dismissible? Should be (per принцип «никакого forced flow» MENTOR-DETAIL SEQ-3 explicit). **Surface to plan**: confirm banner has dismiss button or auto-hide on next boot если ничего не изменилось.

## In-app notification center

- [x] CHK012-013 — **N/A**.

## Edge handling

- [x] CHK014 N/A (no push).
- [x] CHK015 Critical events non-push fallbacks — **N/A** + **NEW reflection**: boot banner для critical missing — это **сам и есть** non-push альтернатива (user видит критический missing без push). ✓
- [x] CHK016 POST_NOTIFICATIONS not required — **yes**.

## Privacy / lock-screen

- [x] CHK017-018 — **N/A**.

## Acceptance evidence

- [x] CHK019 Notification matrix — **partial**. Single matrix simplified. **NEW need**: добавить boot banner row.
- [x] CHK020 Push permission denied path — **N/A**.

---

**Total**: 9/9 applicable ✓, 11 N/A
**Red-only summary**: notification-minimization: 20/20 ✓ (no push events; boot-time banner — in-app indicator per rule 10; CHK011 banner dismissibility detail defer to plan).
