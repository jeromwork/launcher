---
id: m-1
title: "Phase-2: MVP Vertical Slices"
---

## О чём эта фаза

Видимые продуктовые слайсы — каждая задача даёт демонстрируемую end-to-end фичу. После Phase-2 продукт можно показать пользователям, отправить в Google Play, начать blogger outreach.

**До этой фазы** — есть только фундамент Phase-1 (никаких visible фич).
**После этой фазы** — есть полноценный MVP: Simple Launcher для `primary user`, Admin App для `remote administrator`, контакты с фото, SOS, deletion flow, version history, subscription.

## Какие задачи входят

- **TASK-7** S-1 — Simple Launcher first-run + Setup Wizard (главный экран `primary user`).
- **TASK-8** S-2 — Admin App + QR Pairing (приложение для `remote administrator`).
- **TASK-9** S-3 — Contact Tiles + Handoff Calling (тайлы контактов + умное переключение).
- **TASK-10** S-4 — SOS Capability + Wizard Step (критическая safety-кнопка).
- **TASK-11** S-5 — Contact Photos / Family Album foundation (фото на тайлах через encrypted blobs).
- **TASK-12** S-6 — Account Deletion Flow (GDPR + Play Store mandatory).
- **TASK-13** S-8 — VersionedConfigViewer + Layout Editor (визуальный редактор + история версий).
- **TASK-14** S-9 — Phone Health Monitoring (admin видит battery / last_seen / network).
- **TASK-15** S-10 — Subscription Server Timer (cloud-only billing, anti-tamper).

## Какие ключевые решения зафиксированы в этой фазе

- **QR pairing primary** (decision 2026-06-15-deferred-cloud/04), remote invites через адаптер.
- **MVP app launch simplification** (decision 06): запуск apps только без параметров; provider recipe catalogue отложен в Phase-3.
- **Cloud-only billing** (decision 03): local features навсегда бесплатны, subscription только для cloud features, server-validated JWT.
- **Wire format versioning** (decision 05): `schemaVersion: 1` для всех wire formats в Phase-2.
- **MVP = Phase 2 + Phase 3** (split 2026-06-15 v3).

## Где искать решения и обсуждения

- **Decisions**: `docs/product/decisions/2026-06-15-deferred-cloud/03-billing-cloud-only.md`, `04-pairing-channel-abstraction.md`, `05-preset-wire-format-versioning.md`, `06-app-launch-mvp-simplification.md`.
- **Спеки**: будут создаваться по мере перехода каждого task'а в In Progress.

## Состояние

**0/9 Done.** Все task'и в Draft — обсуждение происходит при переводе в In Progress через `/speckit.specify`.

Старт после закрытия TASK-6.
