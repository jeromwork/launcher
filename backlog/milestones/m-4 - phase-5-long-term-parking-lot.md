---
id: m-4
title: "Phase-5: Long-term Parking Lot"
---

## О чём эта фаза

**Не fixed roadmap.** Это «parking lot» — идеи и направления на годы вперёд. Каждая задача активируется **только если** появится сильный сигнал из рынка / pull от пользователей / реальная necessity.

Эти task'и существуют не для выполнения, а для **фиксации направлений**: чтобы не забыть, что мы об этом думали; чтобы при появлении триггера сразу понимать что строить.

## Какие задачи входят (15 направлений)

**B2B / monetization extensions:**
- **TASK-34** L-1 — Clinic / partner B2B integration.
- **TASK-35** L-2 — Marketplace для config templates.

**AI integrations** (после TASK-33 F-2):
- **TASK-36** L-3 — AI provider implementations (L-3a Google App Actions, L-3b MCP server, L-3c Gemini Nano).

**Infrastructure / resilience:**
- **TASK-37** L-4 — Self-hosted Sentry migration.
- **TASK-38** L-5 — Backup / disaster recovery.

**Identity / crypto evolution:**
- **TASK-39** L-6 — Social recovery (re-open D-25 OWD-4, только если «потерял так потерял» окажется bad PR).
- **TASK-40** L-7 — Multi-device per user beyond F-4.
- **TASK-41** L-8 — Key rotation / forward secrecy.
- **TASK-42** L-9 — Family group encryption migration к Signal-style.

**Wearables / sensors:**
- **TASK-43** L-10 — Wearable monitoring full (расширение V-5).
- **TASK-44** L-11 — Security sensors integration (smart-home).

**Regional / market expansion:**
- **TASK-45** L-12 — Closed messengers integration (LINE / WeChat / KakaoTalk).
- **TASK-46** L-13 — Shared admin contact book.

**Engagement / anti-tamper:**
- **TASK-47** L-14 — Family Activity Challenges (PARK-001).
- **TASK-48** L-15 — Tamper-resistance escalation L1+L2+L3.

## Какие ключевые решения зафиксированы в этой фазе

- **L-3 (AI providers)** — defer pattern: F-2 (TASK-33) даёт абстракцию сейчас, концретные providers — additive здесь.
- **L-6 (social recovery)** — текущее «accepted loss» осознанный компромисс; re-open только при evidence «много жалоб».
- **L-15 (tamper-resistance)** — escalation активируется только при measurable abuse (не speculative).
- **L-9 (Signal-style migration)** — двойной exit ramp от TASK-4 envelope (rule 3 one-way door документация).

## Где искать решения и обсуждения

- **Decisions**: каждая L-* задача со временем породит свои decisions, если активируется.
- **Триггеры активации**: записаны в description каждого task'а в секции «Состояние».

## Состояние

**0/15 Done.** Все task'и в Draft с priority=LOW и label `parking-lot`.

**Эти task'и НЕ блокируют ничего.** Они существуют как памятка направлений.
