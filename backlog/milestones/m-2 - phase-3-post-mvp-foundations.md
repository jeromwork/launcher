---
id: m-2
title: "Phase-3: Post-MVP Foundations"
---

## О чём эта фаза

Post-MVP foundations: расширения и rough-edge fixes которые нужны до **public release** (soft launch gate). Закрывает то, чего MVP Phase-2 не покрывает: preset authoring между семьями, accessibility presets, recovery 2FA, recipe catalogue.

**Фаза разделяется на два потока**:
- **Preset / wizard engine эволюция**: schema v2, deep Android integration, authoring + sharing, adaptive UX, config copy (TASK-16..20, 22).
- **Recovery + cloud server features**: 2FA escrow, provider recipes, device inventory, multi-app cohabitation (TASK-21, 23..25).

**После Phase-3** — продукт готов к public release: soft launch gate passed, MVP scope полный.

## Какие задачи входят

- **TASK-16** P-1 — Preset Schema v2 + Wizard Engine (bump wire format v1 → v2).
- **TASK-17** P-2 — Android Deep Integration Steps (block notifications, disable swipes, hide settings).
- **TASK-18** P-3 — Preset Authoring + Sharing (admin создаёт / экспортирует preset'ы).
- **TASK-19** P-4 — Adaptive UX Presets (tremor / vision-impaired / dwell-to-activate).
- **TASK-20** P-5 — Config Copy Between Own Devices.
- **TASK-21** P-6 — Account Recovery + 2FA escrow (pair recovery на новом admin device).
- **TASK-22** P-7 — Optional Step Reminder System (badge в Settings, БЕЗ push).
- **TASK-23** P-8 — Provider Recipe Catalogue (серверный каталог deep-link templates).
- **TASK-24** P-9 — Device Inventory Sync (encrypted list установленных apps senior'а).
- **TASK-25** P-10 — Multi-app Cohabitation + Chain-of-trust Recovery (foundation для V-2 messenger).

## Какие ключевые решения зафиксированы / будут зафиксированы в этой фазе

- **Wire format bump до schemaVersion=2** — backward-compat обязательна (Phase 2 readers работают с v2 через downgrade).
- **Provider Recipe Catalogue privacy boundary**: pull всего региона, no telemetry о тапах.
- **Chain-of-trust technical choice (B / C / B+C гибрид)** — выбирается в ADR перед началом TASK-25.
- **Social recovery остаётся OUT** в Phase-3 (deferred в TASK-39 L-6 в Phase-5).

## Где искать решения и обсуждения

- **Decisions**: TBD (создаются при clarification каждого task'а через `/speckit.clarify`).
- **Спеки**: TBD.
- **P-10 research notes**: `docs/product/future/multi-app-cohabitation.md`.

## Состояние

**0/10 Done.** Все task'и в Draft.

Старт после закрытия Phase-2 (TASK-7..15).
