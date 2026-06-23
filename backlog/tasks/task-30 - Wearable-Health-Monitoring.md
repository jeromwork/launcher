---
id: TASK-30
title: Wearable Health Monitoring
status: Draft
assignee: []
created_date: '2026-06-23 05:40'
updated_date: '2026-06-23 06:30'
labels:
  - phase-4
  - v-spec
  - v-5
  - wearable
  - bluetooth
  - health
milestone: m-3
dependencies:
  - TASK-14
priority: medium
ordinal: 30000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Интеграция со smart-часами (Wear OS, или просто часы с Bluetooth). Часы измеряют пульс, шаги, обнаруживают падение. Если случилось что-то критическое — push admin'у. Сами часы — отдельный приёмник SOS-сигнала.

**Что происходит по шагам:**
1. Бабушка купила Wear OS smartwatch (или Xiaomi Mi Band с поддержкой fall detection).
2. Дочка-admin в Admin App → «Подключить часы» → Bluetooth scan → выбирает часы → pairing.
3. Часы паркируются с устройством бабушки (телефоном).
4. Часы собирают: пульс, шаги, есть ли падение.
5. Каждые 4 часа — снимок передаётся в Admin App (как `HealthSnapshot` из TASK-14, расширенный wearable data).
6. **Если обнаружено падение** → часы вибрируют, спрашивают «Вы в порядке?» (10 сек на отклик).
7. Нет отклика → автоматический SOS (TASK-10 S-4) — push admin'у.

**Что admin видит:**
- Дашборд бабушки в Admin App: пульс 70, сегодня 3000 шагов, последняя активность 30 минут назад.
- Если пульс резко скакнул → push «Резкое изменение пульса у бабушки, проверьте».

**Privacy boundary:**
- Heart rate данные собираются только когда часы носятся.
- НЕ непрерывный stream данных в облако — только aggregated 4-часовые snapshot'ы.
- Admin не видит «секунда-по-секунде», только тренды.

## Зачем

Падения — главная причина смерти/инвалидности у пожилых. Auto-detect + auto-SOS = большая ценность. Wearable monitoring расширяет coverage когда нет launcher-устройства рядом (бабушка пошла в магазин, оставила телефон дома).

## Что входит технически (для AI-агента)

- BLE pairing через `BluetoothPairingChannel` adapter.
- Heart rate, steps, fall detection sensors data (через Wear OS Health Services API или device-specific SDK).
- Alert escalation (fall detected → SOS path в TASK-10).
- Privacy boundaries: aggregated data, no continuous stream.

## Состояние

**Planned.** Зависит от TASK-14 (S-9 health monitoring foundation — расширяется).

---

## Готовый промт для `/speckit.specify`

```
Реализуй V-5: Wearable Health Monitoring.

ЧТО СТРОИМ:
BLE pairing с smart watches (Wear OS или Xiaomi Mi Band) через BluetoothPairingChannel adapter. Heart rate, steps, fall detection через Wear OS Health Services API. Расширение TASK-14 S-9 HealthSnapshot wire format под wearable data. Alert escalation: fall detected → SOS path (TASK-10 S-4). Privacy: aggregated 4h snapshots only, NO continuous stream.

ЗАЧЕМ:
Падения — главная причина смерти/инвалидности у пожилых. Auto-detect → auto-SOS — большая ценность.

SCOPE ВКЛЮЧАЕТ:
- BluetoothPairingChannel adapter (BLE).
- Wear OS Health Services API integration.
- Расширение HealthSnapshot wire format: heart_rate_avg, steps_today, falls_detected, last_motion_at.
- Fall detection flow: вибрация на часах → 10-сек prompt → auto-SOS если нет отклика.
- Alert escalation в SOS path TASK-10.
- Admin dashboard: heart rate trend, steps, alerts feed.
- Privacy: aggregated 4h snapshots, NO real-time stream.

SCOPE НЕ ВКЛЮЧАЕТ:
- Standalone Wear OS launcher (бабушка использует часы только как датчик, не как UI).
- Sleep tracking detailed (post-MVP).
- Blood pressure / oxygen (требует другие sensors, V-x в Phase 5+).
- Apple Watch (TASK-26 V-1 iOS ecosystem extension).

DEPENDENCIES:
- TASK-14 (S-9 health monitoring — wire format расширяется).
- TASK-10 (S-4 SOS — fall detection триггерит).
- TASK-5 (F-5c FCM — для critical alerts).

ACCEPTANCE CRITERIA:
- Бабушка с Wear OS watch → admin подключил через BluetoothPairingChannel → pairing успешный.
- Admin видит heart rate trend бабушки в дашборде (обновляется 4×/день).
- Бабушка упала → часы вибрируют, спрашивают «Вы в порядке?» → нет отклика 10 сек → SOS отправился admin'у.
- Резкое изменение пульса (>30 bpm spike) → push admin'у.
- Часы сняты с руки на ночь → сбор данных приостановлен (privacy).
- НЕТ continuous stream к admin'у (manual проверка sniffer'ом).

LOCAL TEST PATH:
- Wear OS emulator (Wear OS API 34).
- Mock fall detection event для unit-tests escalation flow.
- E2E с физическим Wear OS device (если доступен).

CONSTITUTION GATES:
- Rule 1 (domain isolation): WearableSensor — port.
- Rule 2 (ACL): Wear OS Health Services API не вытекает в domain.
- Rule 9 (privacy): aggregated only, no raw stream.
- Rule 10 (notification): fall detected = critical = push justified.

EFFORT: Large (~3 months).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Heart rate + steps + fall detection
- [ ] #2 Alert escalation (fall detected → SOS path)
- [ ] #3 Privacy: aggregated data, no continuous stream к admin'у
- [ ] #4 Бабушка с Wear OS watch → admin подключил через Bluetooth → pairing успешный
- [ ] #5 Admin видит heart rate trend бабушки в дашборде (обновляется 4×/день)
- [ ] #6 Бабушка упала → часы вибрируют, спрашивают 'Вы в порядке?' → нет отклика 10 сек → SOS admin'у
- [ ] #7 Резкое изменение пульса (>30 bpm spike) → push admin'у
- [ ] #8 Часы сняты с руки на ночь → сбор данных приостановлен (privacy)
- [ ] #9 НЕТ continuous stream к admin'у (проверка sniffer'ом)
<!-- AC:END -->
