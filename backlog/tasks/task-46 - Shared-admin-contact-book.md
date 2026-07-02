---
id: TASK-46
title: Shared admin contact book
status: Draft
assignee: []
created_date: '2026-06-23 05:45'
updated_date: '2026-06-23 06:36'
labels:
  - phase-5
  - l-spec
  - l-13
  - contacts
  - admin
  - parking-lot
milestone: m-4
dependencies:
  - TASK-9
priority: low
ordinal: 46000
crypto-alignment: scope-reset
crypto-source: [Блок 4, Блок 5, Δ.5]
blocks-on: [Q-09, Q-11]
crypto-sweep-date: 2026-07-02
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Когда у одного primary user есть N admin'ов (например, family group из 3 родственников) — они могут шарить общий contact-list через envelope encryption + conflict resolution при concurrent edits.

## Зачем

Сейчас (TASK-9 S-3) каждый admin редактирует свой view контактов. При множественных admin'ах возникает рассинхрон. L-13 даёт shared единый contact-list.

## Состояние

**Parking lot.** FUTURE-SPEC-004. Зависит от TASK-9 (S-3 contact foundation).

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-13: Shared admin contact book.

ЧТО СТРОИМ:
Multi-admin shared contact-list для одного primary user. Envelope encryption (каждый admin видит свою copy декрипчена своим ключом, server видит blob). Conflict resolution при concurrent edits (last-write-wins / CRDT / explicit merge UI).

ЗАЧЕМ:
N admin'ов одной family group работают над общим contact-list без рассинхрона.

SCOPE ВКЛЮЧАЕТ:
- Multi-recipient envelope encryption (extends TASK-4 F-5b).
- Conflict resolution algorithm (ADR с выбором: LWW / CRDT / explicit merge).
- Sync protocol через TASK-5 F-5c push.
- UI: «who's editing now» indicator.

DEPENDENCIES:
- TASK-9 (S-3 contact foundation).
- TASK-4 (F-5b envelope).

EFFORT: TBD.
```
<!-- SECTION:DESCRIPTION:END -->
