---
id: TASK-43
title: Wearable monitoring full
status: Draft
assignee: []
created_date: '2026-06-23 05:44'
updated_date: '2026-06-23 06:35'
labels:
  - phase-5
  - l-spec
  - l-10
  - wearable
  - health
  - parking-lot
milestone: m-4
dependencies:
  - TASK-30
priority: low
ordinal: 43000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Полноценная wearable platform: heart rate, oxygen saturation, sleep tracking, falls, location during emergency, panic button. Расширение TASK-30 V-5 (которое даёт baseline). Здесь — full health monitoring suite.

## Зачем

Если рынок wearables показывает strong interest от ageing population — full suite становится дифференциатором продукта.

## Состояние

**Parking lot.** FUTURE-SPEC-001 expanded. Зависит от TASK-30 (V-5 BLE foundation).

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-10: Wearable monitoring full.

ЧТО СТРОИМ:
Расширение TASK-30 V-5: full health suite. Heart rate continuous (с privacy boundaries), oxygen saturation, sleep stages, falls detection improved, location during emergency only, hardware panic button.

ЗАЧЕМ:
Если market signals strong — differentiation feature.

SCOPE ВКЛЮЧАЕТ:
- Heart rate continuous monitoring (with explicit consent + aggregation).
- Oxygen saturation (PulseOx via partner SDK).
- Sleep tracking (stage detection через accelerometer + heart rate).
- Improved falls detection (ML-based, on-device).
- Location only during emergency (privacy boundary).
- Hardware panic button (через accessory device).

DEPENDENCIES:
- TASK-30 (V-5 BLE foundation).

EFFORT: TBD (multi-month).
```
<!-- SECTION:DESCRIPTION:END -->
