---
id: TASK-149
title: Skeleton-domain SoT — identity + client-android + external-services (close dangling arch-pack refs)
status: In Progress
assignee: []
created_date: '2026-07-22 13:10'
labels:
  - architecture
  - docs
milestone: m-1
dependencies: []
priority: medium
ordinal: 149000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Три домена были заявлены в `INDEX.md` как «skeleton — full content pending», а на них ссылались `server.md` и другие файлы — то есть **висящие ссылки** (integrity-скил их поймал во время TASK-148). Эта задача закрывает их самодостаточными арх-паками по стандарту ecs.md: identity, client-android, external-services. Только документы, ноль кода.

## Зачем

Убрать dangling-ссылки, чтобы арх-пак набор был integrity-чистым и будущая сессия не упиралась в отсутствующие файлы. identity — care-важный (профиль/контакты/signup gate).

## Что входит технически (для AI-агента)

- `docs/architecture/identity.md` — LOCAL-first/CLOUD-lazy модель, Firebase Auth, AS=QR pairing (crypto-pairing), инварианты ID1-ID5, §Open (signup gate = TASK-106, preset-field), Rejected.
- `docs/architecture/client-android.md` — трёхслойный разрез (UI Compose → core/ KMP zero-Android → adapters), ports-only UI, manual DI, инварианты CA1-CA4, fitness.
- `docs/architecture/external-services.md` — FCM/Firebase Auth/Firestore за адаптерами (rule 2) + exit ramps (rule 8) + opaque (rule 13); инварианты ES1-ES4.
- INDEX.md статусы skeleton → v1.

## Состояние

**In Progress.** Файлы написаны (current-state + open questions в identity). Ноль кода.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] identity.md написан самодостаточно (модель + AS-ссылка на crypto-pairing + §Open signup gate как preset-field, не settle TASK-106)
- [x] #2 [hand] client-android.md написан (3-слойный разрез, инварианты, fitness) — current-state
- [x] #3 [hand] external-services.md написан (FCM/Auth/Firestore за адаптерами + exit ramps) — current-state
- [x] #4 [hand] INDEX.md статусы skeleton → v1; dangling-ссылки на эти 3 файла закрыты
- [x] #5 [hand] Zero production-code
<!-- AC:END -->
