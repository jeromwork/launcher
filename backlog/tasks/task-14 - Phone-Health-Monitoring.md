---
id: TASK-14
title: Phone Health Monitoring
status: Draft
assignee: []
created_date: '2026-06-23 05:37'
updated_date: '2026-06-23 06:17'
labels:
  - phase-2
  - s-spec
  - s-9
  - monitoring
  - senior-care
milestone: m-1
dependencies:
  - TASK-8
priority: medium
ordinal: 14000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Admin видит «состояние здоровья» телефона бабушки: когда был онлайн, сколько батареи, есть ли интернет, не закончилась ли память. Бабушке ничего настраивать не нужно — данные собираются автоматически.

**Что происходит по шагам:**
1. На устройстве бабушки раз в 4 часа собирается «снимок состояния» (HealthSnapshot): уровень батареи, последнее активное использование, есть ли сеть, сколько свободного места.
2. Снимок шифруется и отправляется в облако (через тот же канал что и config, TASK-4).
3. Admin в своём приложении видит карточку «Бабушка»: «Онлайн 5 минут назад, батарея 65%, сеть Wi-Fi, память OK».
4. Если что-то критическое (батарея <5%) — admin получает push (TASK-5) «У бабушки кончается батарея».
5. Если ничего критического — никаких push, только in-app индикатор (соблюдение CLAUDE.md rule 10 — не спамим уведомлениями).

**Что НЕ собирается (приватность):**
- Никаких клавиатурных нажатий.
- Никакой GPS-локации.
- Никаких логов звонков.
- Никакого списка кому бабушка звонила.
- Только обобщённые технические показатели.

## Зачем

Снижает тревогу семьи: «бабушка не отвечает 3 дня — она в порядке? просто без интернета? телефон разрядился?». Видя статус телефона, родственник понимает что происходит, не звонит каждые 2 часа.

## Что входит технически (для AI-агента)

- `HealthSnapshot` wire format (battery, last_seen, network_type, storage_free_percent).
- Background WorkManager job: snapshot 1x/4h.
- Encrypted upload через TASK-4 envelope.
- Admin dashboard view с health per Managed.
- Severity rules: critical (battery <5%, no_network >24h) → push; нормальный → in-app indicator.
- Privacy contract: explicit NO keystroke, NO location, NO call logs.

## Состояние

**Planned.** Зависит от TASK-8 (Admin App как UI host).

---

## Готовый промт для `/speckit.specify`

```
Реализуй S-9: Phone Health Monitoring.

ЧТО СТРОИМ:
WorkManager job 1x/4h собирает HealthSnapshot (battery, last_seen, network_type, storage_free_percent) на Managed-устройстве. Encrypted upload через TASK-4 envelope. Admin видит dashboard карточку per Managed. Severity rules: critical → push (TASK-5), normal → in-app indicator (rule 10 compliance).

ЗАЧЕМ:
Снижает тревогу семьи. Родственник видит что батарея бабушки 65% и она была онлайн 5 минут назад → не звонит каждые 2 часа волнуясь.

SCOPE ВКЛЮЧАЕТ:
- HealthSnapshot wire format schemaVersion=1 (battery / last_seen / network_type / storage_free_percent).
- WorkManager periodic job 1x/4h (configurable).
- Encrypted upload через TASK-4 F-5b envelope.
- Admin dashboard view (внутри TASK-8 Admin App).
- Severity rules: critical (battery<5%, no_network>24h) → FCM push; normal → in-app indicator only.
- Privacy contract: explicit absence of keystroke/location/call_log.

SCOPE НЕ ВКЛЮЧАЕТ:
- Wearable health metrics (TASK-30 V-5 / TASK-43 L-10).
- Continuous heart-rate (privacy red flag для MVP).
- Location tracking (privacy red flag).
- App usage telemetry (privacy red flag).

DEPENDENCIES:
- TASK-8 (Admin App).
- TASK-5 (FCM push для critical).
- TASK-4 (envelope encryption для upload).

ACCEPTANCE CRITERIA:
- Admin видит карточку Managed с временем «онлайн N минут назад», батареей, сетью.
- Бабушка зарядила телефон до 100% → карточка admin'а обновилась за <4 часа.
- Батарея бабушки упала ниже 5% → admin получил push «У бабушки кончается батарея».
- Бабушка без сети >24h → admin получил push «Связь с бабушкой потеряна сутки».
- HealthSnapshot НЕ содержит keystrokes/location/call_log (manual проверка via inspect blob).
- WorkManager job не запускается чаще раза в 4 часа (battery impact низкий).

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 — manual battery emulator change + observe WorkManager firing.
- Unit-tests severity rules с fake HealthSnapshot fixtures.
- E2E с physical device #1 (currently Xiaomi 11T) — реальная battery low simulation.

CONSTITUTION GATES:
- Rule 1 (domain isolation): HealthSnapshot — pure domain value.
- Rule 5 (wire format): schemaVersion=1 + roundtrip test.
- Rule 9 (privacy): explicit exclusion list (no keystroke/location/call_log) in spec.
- Rule 10 (notification): только critical → push, остальное in-app.

EFFORT: Medium (~2 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Уплоад snapshot 1x/4h через WorkManager
- [ ] #2 Admin dashboard view с health per Managed
- [ ] #3 Severity rules: critical (battery <5%) → push; normal → in-app indicator
- [ ] #4 Privacy: НИКАКИХ keystrokes, NO location tracking, NO call logs
- [ ] #5 Admin видит карточку Managed с временем 'онлайн N минут назад', батареей, сетью
- [ ] #6 Бабушка зарядила телефон до 100% → карточка admin'а обновилась за <4 часа
- [ ] #7 Батарея бабушки упала ниже 5% → admin получил push 'У бабушки кончается батарея'
- [ ] #8 Бабушка без сети >24h → admin получил push 'Связь с бабушкой потеряна сутки'
- [ ] #9 HealthSnapshot НЕ содержит клавиатурных нажатий, GPS-локации, логов звонков (проверка вручную)
- [ ] #10 Сбор snapshot не запускается чаще раза в 4 часа (низкий расход батареи)
<!-- AC:END -->
