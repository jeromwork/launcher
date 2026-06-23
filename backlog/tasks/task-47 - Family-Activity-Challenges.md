---
id: TASK-47
title: Family Activity Challenges
status: Draft
assignee: []
created_date: '2026-06-23 05:45'
updated_date: '2026-06-23 06:36'
labels:
  - phase-5
  - l-spec
  - l-14
  - gamification
  - engagement
  - parking-lot
milestone: m-4
dependencies: []
priority: low
ordinal: 47000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Family challenges как opt-in engagement feature: «семья прошла суммарно 100 000 шагов на этой неделе!», «бабушка сделала daily check-in 30 дней подряд». PARK-001. Carefully balanced anti-gamification (rule 10 — никаких push, только in-app indicators).

## Зачем

Если данные покажут что non-intrusive engagement features увеличивают retention → можно build осторожно. Без gamification → теряем потенциальный engagement uplift.

## Состояние

**Parking lot.** PARK-001 если решим build. Build только если data shows positive impact AND no privacy backlash.

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-14: Family Activity Challenges.

ЧТО СТРОИМ:
Opt-in family challenges (steps competition, daily check-in, family streak). Carefully balanced anti-gamification: ZERO push reminders (rule 10), только in-app indicators. Privacy-first: aggregated data only.

ЗАЧЕМ:
Engagement uplift (если data confirms positive impact).

SCOPE ВКЛЮЧАЕТ:
- Challenge templates (preset library).
- Opt-in flow (explicit consent, в Settings).
- In-app indicator на dashboard (НЕ push).
- Privacy: aggregated only.

DEPENDENCIES:
- TASK-14 (S-9 health monitoring).
- TASK-30 (V-5 wearable) — optional steps source.

EFFORT: TBD.
```
<!-- SECTION:DESCRIPTION:END -->
