---
id: TASK-44
title: Security sensors integration (smart-home)
status: Planned
assignee: []
created_date: '2026-06-23 05:44'
updated_date: '2026-06-23 06:36'
labels:
  - phase-5
  - l-spec
  - l-11
  - sensors
  - iot
  - security
  - parking-lot
milestone: m-4
dependencies: []
priority: low
ordinal: 42000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Интеграция с smart-home сенсорами: датчики двери / окна / движения / дыма. Если что-то критическое (дым в квартире, нет движения 12 часов) — escalation через FCM push к admin'ам.

## Зачем

Расширяет coverage «care» с самого устройства на окружение пользователя. Подходит как для семей с пожилыми, так и для assisted living facilities.

## Состояние

**Parking lot.** FUTURE-SPEC-002.

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-11: Security sensors integration (smart-home).

ЧТО СТРОИМ:
Smart-home sensor integration: door/window/motion/smoke. Backend для consumption sensor events. Alert escalation rules: smoke = immediate push (rule 10 critical), no motion 12h = push to admin. Privacy-respecting (events на нашем backend, не на внешних provider'ах).

ЗАЧЕМ:
Care coverage за пределами phone device.

SCOPE ВКЛЮЧАЕТ:
- Sensor pairing (Matter / Zigbee / Z-Wave через partner SDK).
- Event stream ingestion.
- Severity-based escalation rules.
- Admin UI: sensor status feed.
- Privacy: events stored encrypted, deleted после 30d.

DEPENDENCIES:
- TASK-14 (S-9 health monitoring foundation для events pipeline).
- TASK-10 (S-4 SOS) — для immediate alerts.

EFFORT: TBD.
```
<!-- SECTION:DESCRIPTION:END -->
