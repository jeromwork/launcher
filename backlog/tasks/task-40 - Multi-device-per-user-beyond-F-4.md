---
id: TASK-40
title: Multi-device per user beyond F-4
status: Draft
assignee: []
created_date: '2026-06-23 05:44'
updated_date: '2026-06-23 06:35'
labels:
  - phase-5
  - l-spec
  - l-7
  - identity
  - multi-device
  - parking-lot
milestone: m-4
dependencies:
  - TASK-3
priority: low
ordinal: 40000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Расширение F-4 (TASK-3): один пользователь может иметь N устройств в одном identity namespace (без pairing каждого). Сейчас (F-4) каждое устройство = свой identity. Multi-device per user = более глубокая модель identity.

## Зачем

Когда у одного человека несколько устройств (телефон + планшет + TV в спальне) — sync между ними сейчас идёт через cloud config push. Multi-device per user даёт более плотную интеграцию (shared session state, мгновенный sync).

## Состояние

**Parking lot.** FUTURE-SPEC-009. Зависит от TASK-3 (F-4 identity foundation).

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-7: Multi-device per user beyond F-4.

ЧТО СТРОИМ:
Identity model расширяется: один user = N devices в одном namespace. Не через pair-key (как Admin↔Managed), а через identity-level sync. Требует server-arbitrated key sharing.

ЗАЧЕМ:
Глубже sync между устройствами одного пользователя.

SCOPE ВКЛЮЧАЕТ:
- Multi-device key escrow scheme.
- Device list UI в Settings.
- Per-device permissions (этому устройству можно X, другому не давать).
- Server arbitration новых device additions (anti-takeover protection).

DEPENDENCIES:
- TASK-3 (F-4 identity) — extends.

EFFORT: TBD.
```
<!-- SECTION:DESCRIPTION:END -->
