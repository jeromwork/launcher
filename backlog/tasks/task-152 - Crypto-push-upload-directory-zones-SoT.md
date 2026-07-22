---
id: TASK-152
title: Push payload + blob upload + member directory zones SoT (research-grounded)
status: In Progress
assignee: []
created_date: '2026-07-22 14:45'
labels:
  - architecture
  - crypto
  - messaging
  - docs
dependencies: []
milestone: m-1
priority: high
ordinal: 152000
decision-supersedes:
  - TASK-60
  - TASK-111
  - TASK-114
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Три оставшиеся мелкие крипто-зоны (push-payload, blob-upload, encrypted member directory) доведены до ecs-полноты **по подтверждённому методу**: research стандартов индустрии + юзкейсов сначала, потом валидация против наших тасков. Вписаны в существующие арх-паки (messaging-delivery / gallery / messaging-features). Только документы, ноль кода.

## Зачем

Убрать последние open-крипто-зоны; дать архитектуру на индустрии, не на недорешённых тасках. Research попутно исправил ошибочное допущение TASK-111 (presigned PUT не режет размер).

## Что входит технически (для AI-агента)

- **Push** (messaging-delivery.md §Push payload): Pattern A opaque wake-ping (Signal/Matrix, стрикт до opaque токенов) default + Pattern B encrypted payload+NSE (iOS/offline); FCM 4KB data-only, opaque collapse, high-priority; PushPort; libs firebase-admin (Apache), RFC 8291. **Resolves TASK-60.**
- **Upload** (gallery.md §Blob upload): allocate→upload→confirm (Signal/Matrix); R2 presigned (aws4fetch MIT); **исправление: presigned PUT НЕ режет размер → gate = quota-reserve + HEAD-verify**; quota via DO; TTL R2 lifecycle; abuse = SHA-256(ciphertext) blocklist; ~500-750 LoC glue. **Resolves TASK-111 (с коррекцией).**
- **Directory** (messaging-features.md §Member directory): НЕТ server-directory; encrypted profile blob (profile-key, Signal) + encrypted roster blob в group state, re-keyed per MLS epoch; roles app-level; openmls (MIT) для re-key, zkgroup AGPL → reimplement pattern. **Resolves TASK-114.**

## Состояние

**In Progress.** Research (3 subagents, industry standards + use cases) проведён; три секции вписаны. Ноль кода.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] §Push payload написан (Pattern A/B, FCM constraints, PushPort, libs) — resolves TASK-60
- [x] #2 [hand] §Blob upload написан (allocate→upload→confirm + size-correction + quota/abuse + libs) — resolves TASK-111 с коррекцией presigned-PUT
- [x] #3 [hand] §Member directory написан (encrypted profile + roster blob, re-key per epoch, no server directory) — resolves TASK-114
- [x] #4 [hand] Все три grounded в industry (Signal/Matrix/RFC/R2 с URL); наши constraints (rule 13) помечены; TASK-60/111/114 superseded с реципроками
- [x] #5 [hand] Zero production-code
<!-- AC:END -->
