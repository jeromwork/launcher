---
id: TASK-9
title: Contact Tiles + Handoff Calling
status: Draft
assignee: []
created_date: '2026-06-23 05:36'
updated_date: '2026-06-23 06:13'
labels:
  - phase-2
  - s-spec
  - s-3
  - contacts
  - call
milestone: m-1
dependencies:
  - TASK-7
  - TASK-8
priority: high
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Реальные контакты на плитках главного экрана + умное переключение между WhatsApp / звонком / резервным контактом, если основной не отвечает.

**Что происходит по шагам (нормальный звонок):**
1. Бабушка видит на главном экране плитку с фото внука «Петя».
2. Тапает на плитку.
3. Запускается WhatsApp-звонок Пете (порядок действий настроен admin'ом).
4. Если Петя берёт — нормальный разговор.

**Что происходит, если не дозвонилась (handoff = передача):**
1. Бабушка тапнула Петю → WhatsApp звонит.
2. Петя не берёт 30 секунд.
3. Автоматически: попытка через обычный звонок (sim-card).
4. Петя всё ещё не берёт.
5. Автоматически: звонок маме Пети (резервный контакт в той же «группе»).
6. Бабушке не нужно ничего нажимать — система пробует сама.

**Как admin настраивает:**
1. В Admin App (TASK-8) выбирает Managed-устройство бабушки.
2. Открывает редактор контактов.
3. Добавляет «Петя», задаёт цепочку: WhatsApp → Phone → мама Пети.
4. Сохраняет → конфигурация шифруется и отправляется на устройство бабушки.

## Зачем

Без этого плитки на главном экране — мёртвая декорация. С этим — основная функция продукта (бабушка дозванивается до семьи в один тап, без блужданий по приложениям).

## Что входит технически (для AI-агента)

- `ContactTile` Composable: рендерит контакт из config (placeholder фото + имя + действие-цепочка).
- Tap → primary action (WhatsApp / Phone / Telegram / Viber) с fallback chain (расширение spec 005).
- Handoff timer: при недозвоне N секунд → автоматически следующий контакт в группе.
- Admin UI редактирования contact list через TASK-8 admin app.
- Contact data хранится зашифрованным в config через TASK-4 envelope (E2E — сервер не видит имена и телефоны).

## Состояние

**Planned.** Зависит от TASK-7 (Simple Launcher как UI host) + TASK-8 (admin edit UI).

---

## Готовый промт для `/speckit.specify`

```
Реализуй S-3: Contact Tiles + Handoff Calling.

ЧТО СТРОИМ:
ContactTile Composable рендерит контакт из config бабушки. Tap → primary action (WhatsApp / Phone / Telegram / Viber) с fallback chain (расширение spec 005). При недозвоне в N секунд → автоматически следующий контакт в группе («handoff»). Admin редактирует contact list через UI TASK-8.

ЗАЧЕМ:
Без этого плитки — мёртвая декорация. С этим — основная функция продукта (бабушка дозванивается в один тап).

SCOPE ВКЛЮЧАЕТ:
- ContactTile Composable (фото-placeholder + имя + action-chain visualization).
- Tap handler с fallback chain (расширяет spec 005 provider system).
- Handoff timer (configurable per group, default 30 сек).
- Admin contact editor UI (внутри TASK-8 admin app).
- Contact data encrypted в config через F-5b envelope (TASK-4).
- Group concept: контакты могут быть сгруппированы для handoff.

SCOPE НЕ ВКЛЮЧАЕТ:
- Реальные фото контактов (плitka-placeholder, фото — TASK-11 S-5).
- SOS button (TASK-10 S-4).
- Voice / video group calls (TASK-27 V-2 messenger).

DEPENDENCIES:
- TASK-7 (S-1 Simple Launcher как UI host) — required.
- TASK-8 (S-2 Admin App + Pairing) — required для edit flow.
- spec 005 (provider system) — existing.

ACCEPTANCE CRITERIA:
- Бабушка тапает плитку контакта → запускается WhatsApp-звонок.
- Контакт не ответил 30 сек → автоматически запускается обычный звонок.
- Резервный контакт в группе → при недозвоне основного → звонит резервному.
- Admin изменил порядок действий → бабушка получила обновлённый config за <10 секунд.
- Admin изменил имя контакта → плитка обновилась в течение 10 секунд.
- Если ни одно действие не доступно (нет WhatsApp + нет sim) → понятное сообщение, не падение.

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 — fake contact list через ConfigSource.
- Mock WhatsApp adapter — симуляция «не ответил».
- E2E с двумя эмуляторами (admin + managed).

CONSTITUTION GATES:
- Rule 1 (domain isolation): Contact, Group — pure-data в domain.
- Rule 5 (wire format): Contact внутри ConfigDocumentV1 (schemaVersion уже зафиксирован).
- Rule 10 (notification minimization): handoff progress — in-app indicator, не push.

EFFORT: Medium (~2-3 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Tap → primary action (call WhatsApp / Phone) с fallback chain
- [ ] #2 Handoff: при недозвоне в N секунд → автоматически следующий контакт в группе
- [ ] #3 Admin может редактировать contact list через S-2 admin UI
- [ ] #4 Contact data — encrypted в config через F-5b envelope
- [ ] #5 Бабушка тапает плитку контакта → запускается WhatsApp-звонок
- [ ] #6 Контакт не ответил 30 сек → автоматически запускается обычный звонок (sim)
- [ ] #7 Резервный контакт в группе → при недозвоне основного → звонит резервному
- [ ] #8 Admin изменил порядок действий → бабушка получила обновлённый config за <10 секунд
- [ ] #9 Admin изменил имя контакта → плитка обновилась за <10 секунд
- [ ] #10 Нет WhatsApp + нет sim → понятное сообщение бабушке, приложение не падает
<!-- AC:END -->
