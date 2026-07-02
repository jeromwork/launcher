---
id: TASK-103
title: 'Decision: Remote app lock for stolen or lost device'
status: Discussion
assignee: []
created_date: '2026-07-02'
updated_date: '2026-07-02'
labels:
  - decision
  - crypto
  - security
  - device-management
  - phase-2
milestone: m-1
dependencies:
  - TASK-101
  - TASK-102
priority: high
ordinal: 103000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

У бабушки украли планшет. Или сама забыла в такси. Что делать?

**Не то что мы разбираем в TASK-102** (MLS group revoke) — там про «убрать участника из группы». А тут — **заблокировать конкретное физическое устройство**, чтобы вор:
- Не мог открыть app.
- Не мог читать contacts, chat history, фото.
- Даже если знает passphrase — сначала должен factory-reset, что убьёт root_key в Keystore.

Стандартный **MDM pattern** (Mobile Device Management) — Google Find My Device, iOS Find My, Samsung Knox. Мы делаем наш собственный на уровне app.

**Владелец правильно указал** (TASK-102 Session 3): при краже устройства это **главная защита**, важнее чем MLS Remove. Потому что MLS Remove можно обойти re-recovery (Session 2 в TASK-102). Remote lock — нельзя обойти без factory-reset.

## Зачем

При краже/потере устройства нужен быстрый способ его нейтрализовать без ожидания owner'а (пожилой может не сразу понять что произошло). Любой admin (Таня, Петя) должен мочь заблокировать бабушкин планшет удалённо.

Также actionable для owner'а — «мои устройства» экран с кнопкой revoke каждого своего девайса (аналог Google Account Activity).

## Что входит технически (для AI-агента)

**Layers**:
- **`core/` port `RemoteLockService`** — abstract lock/unlock operations.
- **Push channel** (FCM/HMS/MQTT) — доставка lock command'а.
- **`app/`** — lock screen composable, unlock flow.
- **`core/`** — device inventory (какие устройства у user'а есть, идентификаторы).
- **Audit log** (TASK-32) — фиксация lock events.

**Не в scope**:
- Remote wipe (crypto-erase local storage) — Phase-3+.
- Location tracking — не наш use case.
- SIM lock / phone-level lock — outside app scope, Google Find My Device делает это.

**В scope открытые вопросы** (см. SECTION:DISCUSSION):
- Кто может trigger lock (admin, owner, both)?
- Как sending device аутентифицируется для lock request?
- Что делает locked app (только показывает lock screen, wipe local state, что с SOS кнопкой)?
- Как unlock (passphrase, remote authorize, 2FA)?
- Interaction с Google Find My Device (дополняем vs заменяем)?
- Что если lock command не доходит (offline device, push потерян)?

## Состояние

Discussion open 2026-07-02. Инициирован после TASK-102 Session 3 pivot — владелец сформулировал что MLS revoke ≠ device lock. Ожидается mentor Session 1 с картой темы + уточняющими вопросами.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Session 1 mentor discussion: map + terms + clarifying questions
- [ ] #2 [hand] Owner ответил на все clarifying questions
- [ ] #3 [hand] Best path выбран с обоснованием
- [ ] #4 [hand] Decision block заполнен (English, immutable)
- [ ] #5 [hand] Status → Draft
- [ ] #6 [hand] Downstream tasks (TASK-6, 24, 25, 32) уведомлены о `dependencies: [TASK-103]`
<!-- AC:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 — ожидание

_(mentor skill будет invoked в следующей сессии для Part A: map / terms / questions)_

**Известные open questions** (для будущей Session 1):

1. **Trigger authorization**: кто может отправить lock command? Owner для своих устройств; admin для owner'ского устройства (family caretaker use case); admin для другого admin'ского устройства (dangerous?)?
2. **Locked app behavior**: что показывает locked device — full lock screen, allow emergency SOS, allow accept incoming call?
3. **Unlock mechanism**: passphrase re-entry на устройстве, remote authorize от owner'а через другое устройство, 2FA, или combo?
4. **Local state**: locked device — просто hide UI или actively wipe local encrypted state (photos, message cache, contacts)? Wipe = более безопасно, но unlock требует полного sync from scratch.
5. **Offline device**: lock command не доходит (устройство offline). Устанавливаем pending lock (при next network) или fail'им? Timeout policy?
6. **Google Find My Device interaction**: наш app lock замещает или дополняет Google's system-level lock? Владельцы могут иметь оба enabled.
7. **Audit**: что record'ится в TASK-32 audit log (who requested, when, target device, reason enum)?
8. **False lock recovery**: бабушка нажала «я потеряла планшет» когда он на самом деле дома. Unlock UX?

### Decision (English, immutable) 🔒

_(pending — заполняется когда Session 1 закончится)_

<!-- SECTION:DISCUSSION:END -->

## Implementation Plan
<!-- SECTION:PLAN:BEGIN -->
_(pending)_
<!-- SECTION:PLAN:END -->
