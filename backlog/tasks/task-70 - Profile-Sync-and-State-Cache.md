---
id: TASK-70
title: Profile Sync and State Cache
status: Draft
assignee: []
created_date: '2026-06-30 20:00'
updated_date: '2026-06-30 20:00'
labels:
  - phase-2
  - foundation
  - sync
  - cache
  - remote-management
  - follows-task-65
milestone: m-1
dependencies:
  - TASK-65
  - TASK-67
priority: medium
ordinal: 70000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Что это простыми словами

Profile (per-device personal data, введён в TASK-65) — это (а) применённый Preset, (б) bindings (контакты в слотах), (в) кэш применённых Android-настроек, (г) UI-overrides. TASK-70 добавляет:

1. **State cache** для (в) — чтобы не дёргать Android API каждый раз (некоторые проверки долгие). Settings отображает кэш; refresh — кнопкой или по событиям.
2. **Sync на сервер** — Profile синхронизируется (зашифрованный) на сервер, чтобы admin'ский app мог получить и редактировать.
3. **Multi-profile-capable storage** — admin'ский app хранит профили для каждого managed primary user (моя бабушка, моя мама, мой дядя), каждый со своим encryption key (через pairing TASK-67).

## Зачем

Без TASK-70:
- Settings перепроверяет всё каждый раз — slow.
- Admin не может смотреть/менять Profile primary user'а удалённо (главная фича продукта).
- Bindings, темы, UI-overrides — теряются если устройство сломалось / телефон сменили.

## Что входит технически (для AI-агента)

- `ProfileSyncService` через Cloudflare Worker (zero-knowledge: server видит только зашифрованный blob).
- Encryption через pairing keys (TASK-67).
- Differential sync (только изменения, не весь Profile целиком).
- State cache invalidation strategies (TTL, event-driven via Android broadcasts).
- Multi-profile storage shape: `Map<userId, Map<presetId, ProfileData>>`.
- Conflict resolution (если admin и primary user одновременно меняли).

## Состояние

**Planned.** Возникла в clarify-фазе TASK-65. Storage shape подготовлен в TASK-65 (Map<presetId → ProfileData>); TASK-70 добавляет user dimension + sync + encryption.

Dependencies hard:
- **TASK-65** — Profile data model + storage shape.
- **TASK-67** — pairing keys для encryption (admin → primary user).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Будут заданы при /speckit.specify запуске
<!-- AC:END -->
