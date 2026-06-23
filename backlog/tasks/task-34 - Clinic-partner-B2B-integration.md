---
id: TASK-34
title: Clinic / partner B2B integration
status: Planned
assignee: []
created_date: '2026-06-23 05:42'
updated_date: '2026-06-23 06:54'
labels:
  - phase-5
  - l-spec
  - l-1
  - b2b
  - clinic
  - parking-lot
milestone: m-4
dependencies:
  - TASK-31
priority: low
ordinal: 47000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Расширение продукта в B2B-сегмент: интеграция с клиниками, домами престарелых, медицинскими центрами как партнёрами-distributors. Foundation для этого уже есть в TASK-31 V-6 (caregiver роль с ограниченным доступом).

**Что строим (когда придёт время):**
- Server-side multi-tenant model: clinic = группа caregivers + clients.
- Billing splits: подписка частично достаётся партнёру.
- Partner branding (white-label launcher).
- Clinic dashboard для управления подопечными.

## Зачем

Один из самых сильных revenue channels для продукта. Активируется когда первый partner-clinic выразит интерес.

## Состояние

**Parking lot.** Активируется при появлении clinic partner с интересом. Зависит от TASK-31 (V-6 Restricted Caregiver — foundation).

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-1: Clinic / partner B2B integration.

ЧТО СТРОИМ:
B2B-расширение: интеграция с clinic / nursing-home / медцентром как партнёрами-distributors. Server-side multi-tenant model. Billing splits. Partner branding (white-label). Clinic dashboard.

ЗАЧЕМ:
Один из сильных revenue channels. Активируется при первом partner-clinic с интересом.

SCOPE ВКЛЮЧАЕТ:
- Multi-tenant Firestore schema (clinic → caregivers → clients).
- Partner branding configuration (logos, colors, contact info).
- Billing splits через Google Play / App Store partner programs.
- Clinic admin dashboard: список clients, активные caregivers, alerts feed.
- B2B onboarding wizard (отличается от B2C).
- Compliance: HIPAA / GDPR healthcare specific requirements.

DEPENDENCIES:
- TASK-31 (V-6 Restricted Caregiver) — foundation.
- TASK-32 (V-7 Audit Log) — для compliance.

ACCEPTANCE CRITERIA:
- Clinic admin может создать аккаунт партнёра в админ-портале (отдельный URL).
- Может пригласить caregivers (медсестёр) с ограниченным scope.
- Может пригласить clients (пациентов) через invite-link.
- Billing split работает: подписка идёт партнёру + платформе в правильной пропорции.
- White-label: client видит лого / цвета clinic'а, не наши.
- Audit log compliance-ready для HIPAA-style проверки.

EFFORT: TBD (зависит от scope конкретного partner'а).
```
<!-- SECTION:DESCRIPTION:END -->
