---
id: TASK-35
title: Marketplace для config templates
status: Draft
assignee: []
created_date: '2026-06-23 05:43'
updated_date: '2026-07-17 04:06'
labels:
  - phase-5
  - l-spec
  - l-2
  - marketplace
  - sharing
  - parking-lot
  - preset-authoring
milestone: m-4
dependencies:
  - TASK-18
priority: low
ordinal: 35000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Marketplace (магазин) готовых config-templates от других пользователей. Расширение TASK-18 P-3 (sharing через файл) в curated catalog с поиском, рейтингами, категориями.

**Что строим (когда придёт время):**
- Curated server catalog (наша модерация).
- Search by category / accessibility / rating.
- Per-template ratings + comments.
- Versioning (template updates).
- Reporting abuse / removal flow.

## Зачем

Если sharing через файл (TASK-18) даёт хорошую traction и user-generated templates становятся валуэмыми — marketplace формализует это в curated experience.

## Состояние

**Parking lot.** Активируется при критической массе user-created templates через TASK-18 P-3. Зависит от TASK-18.

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-2: Marketplace для config templates.

ЧТО СТРОИМ:
Curated catalog config-templates с search, ratings, comments. Расширение TASK-18 P-3 sharing flow. MarketplaceConfigSource adapter (4-й тип ConfigSource после Bundled / FileImport / ShareIntent).

ЗАЧЕМ:
User-generated templates становятся валуэмыми → marketplace формализует в curated experience.

SCOPE ВКЛЮЧАЕТ:
- Server-side template catalog (Cloudflare Worker / R2 backend).
- MarketplaceConfigSource adapter.
- Browse UI: categories, search, filters.
- Rating + comment system.
- Author attribution (без раскрытия PII).
- Moderation flow + reporting abuse.
- Versioning.

DEPENDENCIES:
- TASK-18 (P-3 sharing foundation — для author submission flow).

ACCEPTANCE CRITERIA:
- Browse marketplace → видит categories (для тремора, для слабовидящих, для активных пожилых, ...).
- Тапает template → preview + rating + comments.
- Импортирует template в свою библиотеку (через MarketplaceConfigSource).
- Может оставить rating + comment после применения.
- Reporting abuse: 5+ reports → автоматический review → возможный takedown.

EFFORT: TBD.
```
<!-- SECTION:DESCRIPTION:END -->
