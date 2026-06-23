---
id: TASK-45
title: Closed messengers integration (LINE/WeChat/Kakao)
status: Draft
assignee: []
created_date: '2026-06-23 05:45'
updated_date: '2026-06-23 06:36'
labels:
  - phase-5
  - l-spec
  - l-12
  - messenger
  - regional
  - parking-lot
milestone: m-4
dependencies:
  - TASK-23
priority: low
ordinal: 45000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Региональные closed messengers: LINE (Япония), WeChat (Китай), KakaoTalk (Корея). Каждый — отдельный provider adapter в TASK-23 P-8 recipe catalogue ИЛИ полноценный handoff partner (depends on regional priorities).

## Зачем

Region-specific monetization. WhatsApp/Telegram не работают в Китае (нужен WeChat). LINE доминирует в Японии. Без региональной поддержки — продукт ограничен Европой+Россией.

## Состояние

**Parking lot.** FUTURE-SPEC-003. Зависит от TASK-23 (P-8 recipe catalogue).

---

## Готовый промт для `/speckit.specify` (когда придёт время)

```
Реализуй L-12: Closed messengers integration (LINE / WeChat / KakaoTalk).

ЧТО СТРОИМ:
Per-region messenger adapters: LINE (Japan), WeChat (China), KakaoTalk (Korea). Каждый — отдельный entry в TASK-23 P-8 recipe catalogue. Если нужен deeper integration (group call через WeChat API) — full provider adapter.

ЗАЧЕМ:
Regional markets без правильного messenger — недоступны для продукта.

SCOPE ВКЛЮЧАЕТ:
- Per-region recipes для TASK-23 catalogue.
- Optional: provider adapters (depends on regional priority).
- Region detection и proper messenger surfacing.
- Compliance per region (China: data residency requirements).

DEPENDENCIES:
- TASK-23 (P-8 recipe catalogue).

EFFORT: TBD per region.
```
<!-- SECTION:DESCRIPTION:END -->
