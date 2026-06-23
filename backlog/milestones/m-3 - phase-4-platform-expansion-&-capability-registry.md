---
id: m-3
title: "Phase-4: Platform Expansion & Capability Registry"
---

## О чём эта фаза

Платформенное расширение продукта на новые form-factors и сегменты + foundation для AI-providers (Capability Registry). После Phase-4 продукт работает не только на Android phones; покрывает iOS / TV / Wear / messenger / caregiver, и готов к интеграции с AI-помощниками.

**Не fixed sequence** — порядок выбирается по сигналам рынка (какой сегмент даёт сильный pull первым).

## Какие задачи входят

- **TASK-26** V-1 — iOS Admin Preset (Apple Sign-In, Compose Multiplatform iosMain).
- **TASK-27** V-2 — Elderly-Friendly Messenger (отдельное приложение на Jitsi).
- **TASK-28** V-3 — Full Shared Family Album (расширение S-5: видео, timeline, anniversaries).
- **TASK-29** V-4 — Android TV Preset (Leanback / Compose for TV, DPad navigation).
- **TASK-30** V-5 — Wearable Health Monitoring (BLE, heart rate, fall detection).
- **TASK-31** V-6 — Restricted Caregiver Remote Invite + LinkInvitePairingChannel.
- **TASK-32** V-7 — Audit Log Infrastructure (two-tier: public metadata + encrypted payload).
- **TASK-33** F-2 — Capability Registry Foundation (port + Fake adapter для будущих AI-providers).

## Какие ключевые решения зафиксированы в этой фазе

- **F-2 Capability Registry отложен в Phase-4** (decision 2026-06-15 v3): преждевременная абстракция без consumer'а. Активируется когда появляется первый AI-integration target.
- **V-6 Caregiver перенесён из Phase-2** (требует TASK-31 LinkInvitePairingChannel + TASK-32 audit log + server role-based access).
- **V-2 Messenger = отдельный APK**, не embedded в launcher (Universal Preset Architecture: elderly + adult preset).
- **Cohabitation через TASK-25 P-10** — мандаторное предусловие для V-2 (без него UX «логиниться в каждое приложение» возвращается).

## Где искать решения и обсуждения

- **Decisions**: TBD (создаются при clarification).
- **Спеки**: TBD.

## Состояние

**0/8 Done.** Все task'и в Draft.

Старт после public release MVP (закрытие Phase-2 + Phase-3 + soft launch gate).
