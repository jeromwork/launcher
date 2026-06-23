---
id: TASK-41
title: Key rotation / forward secrecy
status: Planned
assignee: []
created_date: '2026-06-23 05:44'
updated_date: '2026-06-23 06:35'
labels:
  - phase-5
  - l-spec
  - l-8
  - crypto
  - security
  - parking-lot
milestone: m-4
dependencies:
  - TASK-6
priority: low
ordinal: 39000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Periodic ротация root key + forward secrecy для message threads. Сейчас root key (TASK-6 F-5) generates один раз и используется forever. Key rotation = переход на новый key периодически (раз в полгода / при security incident).

## Зачем

Standard crypto hygiene. Защита от long-term key compromise. Forward secrecy = старые messages не расшифровываются даже при будущем key leak.

## Состояние

**Parking lot.** Большая migration: затрагивает все existing ciphertexts. FUTURE-SPEC-010. Зависит от TASK-6.

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-8: Key rotation / forward secrecy.

ЧТО СТРОИМ:
Root key rotation: смена root key периодически (раз в полгода или при incident). Все subkeys в KeyRegistry перевыводятся из нового root. Migration существующих ciphertexts через re-encrypt-on-read (lazy) или batch re-encrypt (eager).

Forward secrecy для messages (если V-2 messenger у нас будет live): Double Ratchet или similar protocol.

ЗАЧЕМ:
Standard crypto hygiene. Защита от long-term key compromise.

SCOPE ВКЛЮЧАЕТ:
- KeyRotation port (currently stub).
- Rotation scheduler (manual trigger + auto every N months).
- Migration: lazy re-encrypt-on-read (recommended), eager batch (alternative).
- Forward secrecy для messages (если V-2 ready).
- ADR с trade-offs lazy vs eager.

DEPENDENCIES:
- TASK-6 (F-5 root key).

EFFORT: TBD (большая, может быть multi-month).
```
<!-- SECTION:DESCRIPTION:END -->
