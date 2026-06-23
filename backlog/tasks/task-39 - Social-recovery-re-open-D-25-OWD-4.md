---
id: TASK-39
title: Social recovery (re-open D-25 OWD-4)
status: Draft
assignee: []
created_date: '2026-06-23 05:44'
updated_date: '2026-06-23 06:35'
labels:
  - phase-5
  - l-spec
  - l-6
  - recovery
  - social
  - parking-lot
milestone: m-4
dependencies:
  - TASK-6
  - TASK-21
priority: low
ordinal: 39000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Альтернативный recovery flow если пользователь **одновременно** забыл passphrase И потерял доступ к Google-аккаунту. Сейчас это «потерял так потерял» (accepted edge case). Активируется только если это окажется bad PR (много жалоб, плохие reviews).

**Как может работать (один из вариантов):**
- Пользователь при setup указывает 3 «друзей» (other users в нашей системе).
- Если потерял доступ — friends могут подтвердить «это он, помогите восстановить».
- 2 из 3 подтверждений → восстановление.

## Зачем

Текущее «accepted loss» — осознанный компромисс между security и UX. Re-open если данные покажут что это плохое решение.

## Состояние

**Parking lot.** Re-open D-25 OWD-4 только при сильных user complaints. Зависит от TASK-6 + TASK-21 (recovery foundations).

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-6: Social recovery (re-open D-25 OWD-4).

ЧТО СТРОИМ:
Social vouching scheme. Pre-recovery: пользователь nominates 3 friends (others in our system). При loss of passphrase + Google — friends могут подтвердить identity (2 of 3 threshold) через подписанные attestations.

ЗАЧЕМ:
Только если «потерял так потерял» проявит себя как bad PR.

SCOPE ВКЛЮЧАЕТ:
- FriendNomination UI в Settings.
- VouchingFlow: friend получает push «X хочет восстановить аккаунт, подтверждаете?».
- Threshold schemes (2 of 3, configurable per region).
- Secure attestation wire format с anti-replay.
- Re-encryption of all data with new keys after vouched recovery.

DEPENDENCIES:
- TASK-6 (F-5 baseline recovery).
- TASK-21 (P-6 2FA escrow — similar pattern).

EFFORT: TBD.
```
<!-- SECTION:DESCRIPTION:END -->
