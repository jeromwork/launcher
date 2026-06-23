---
id: TASK-42
title: Family group encryption migration к Signal-style
status: Draft
assignee: []
created_date: '2026-06-23 05:44'
updated_date: '2026-06-23 06:35'
labels:
  - phase-5
  - l-spec
  - l-9
  - crypto
  - migration
  - parking-lot
milestone: m-4
dependencies:
  - TASK-4
priority: low
ordinal: 42000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Migration с envelope encryption (TASK-4) на Signal-style group crypto. Активируется если outgrow envelope encryption (scale issue: каждый recipient в group — отдельный envelope = O(N) overhead, или security incident требующий better forward secrecy для groups).

## Зачем

Envelope encryption хорошо для small groups (5-10 members), но scales hard для огромных групп. Signal protocol (Megolm / MLS) более эффективен для big groups.

## Состояние

**Parking lot.** Двойной exit ramp от TASK-4 envelope. Зависит от TASK-4.

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-9: Family group encryption migration к Signal-style.

ЧТО СТРОИМ:
Migration от TASK-4 envelope encryption на Signal-style group crypto (Megolm / MLS protocol). Backward-compat для existing ciphertexts (envelope остаётся как legacy reader).

ЗАЧЕМ:
Outgrow envelope encryption (scale OR security incident).

SCOPE ВКЛЮЧАЕТ:
- Megolm/MLS protocol implementation в core/crypto/.
- Migration path с lazy re-encrypt.
- Backward-compat: envelope reader остаётся для legacy ciphertexts.
- Group key rotation как часть protocol.
- ADR с обоснованием выбора (Megolm vs MLS).

DEPENDENCIES:
- TASK-4 (F-5b envelope) — exit ramp.

EFFORT: TBD (big — multi-month).
```
<!-- SECTION:DESCRIPTION:END -->
