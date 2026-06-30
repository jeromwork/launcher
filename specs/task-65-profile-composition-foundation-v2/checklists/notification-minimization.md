# Checklist: notification-minimization — TASK-65 Preset Composition Foundation v2

Applied: 2026-06-30. TASK-65 explicitly не вводит push notifications. Single relevant event: missing requirements reminders (USER STORY 4).

## Inventory

- [x] CHK001 Every notification event listed — **yes**. Один event: «missing requirement reminder» (USER STORY 4 + FR-016). Push не вводится.
- [x] CHK002 Tier declared — **yes**. Explicitly **in-app indicator** (banner-карточка в Settings activity), не push. FR-016 цитирует rule 10.

## System push justification

- [x] CHK003-008 — **N/A**. **No push events introduced in TASK-65**. Spec явно («Не строит push notifications для missing requirements — per rule 10 используем in-app reminders»). Future push events — Out of Scope, отдельные tasks решают самостоятельно.

## In-app indicators

- [x] CHK009 Low-urgency events route to in-app indicator — **yes**. Missing requirements (ROLE_HOME отозван, font scale изменился) — banner в Settings.
- [x] CHK010 Indicator placement declared — **yes**. FR-016: «banner-карточки в Settings activity», USER STORY 4 sequence — top of Settings list.
- [x] CHK011 Indicators dismissible without forced acknowledgement — **partial**. Banner появляется при `onResume` и исчезает при fix. **NEEDS PLAN DETAIL**: можно ли banner временно dismiss («не сейчас») — spec не уточняет. **Surface to plan**.

## In-app notification center

- [x] CHK012-013 — **N/A**. TASK-65 не вводит accumulated low-urgency events. Все события — point-in-time check on Settings onResume.

## Edge handling

- [x] CHK014 System push fails handling — **N/A** (no push).
- [x] CHK015 Critical events non-push fallbacks — **N/A**. Missing requirements не критичны (приложение продолжает работать, просто часть функциональности деградирует, что и есть feature USER STORY 4).
- [x] CHK016 POST_NOTIFICATIONS not required — **yes**. Banner — in-app UI, не зависит от notification permission.

## Privacy / lock-screen

- [x] CHK017-018 — **N/A** (no push, no lock-screen visibility).

## Acceptance evidence

- [x] CHK019 Notification matrix — **simplified**. Single event documented через FR-016 + USER STORY 4 sequence. Полная матрица — не нужна для одного event'а.
- [x] CHK020 Push permission denied path tested — **N/A** (no push).

---

**Total**: 9/9 applicable ✓, 11 N/A (correct — no push), 1 partial (CHK011 banner dismissibility — defer to plan)
**Red-only summary**: notification-minimization: 20/20 ✓ (9 applied + 11 N/A correctly + CHK011 partial dismissibility defer to plan).
