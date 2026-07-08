---
id: TASK-10
title: SOS Capability + Wizard Step
status: Draft
assignee: []
created_date: '2026-06-23 05:36'
updated_date: '2026-06-23 06:14'
labels:
  - phase-2
  - s-spec
  - s-4
  - sos
  - safety
milestone: m-1
dependencies:
  - TASK-7
  - TASK-8
priority: high
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Большая, всегда видимая кнопка SOS на главном экране. При нажатии — уведомление всем родственникам через push (со звуком), с подтверждением, что нажатие не случайное.

**Что происходит по шагам:**
1. Бабушка плохо себя чувствует, тапает большую красную кнопку SOS.
2. Появляется экран подтверждения: «Точно отправить SOS?» с большой кнопкой «Да» и маленькой «Нет, случайно».
3. Если бабушка не отвечает 5 секунд — SOS отправляется автоматически (на случай, если плохо настолько, что нет сил подтвердить).
4. Если успела нажать «Нет, случайно» — отмена.
5. Если «Да» (или авто-таймаут) — на все paired-устройства родственников (admin) приходит push «SOS от бабушки» со звуком, даже если телефон в Silent mode (через высокий severity FCM).
6. Опционально (если разрешение location): к SOS прикладывается координата бабушки.
7. Родственник тапает push → открывается Admin App → видит карту с бабушкой и кнопку «Позвонить».

**Защита от случайных нажатий:**
- Confirm-screen (см. шаг 2) защищает от случайного тапа в кармане.
- Во время установки обновления приложения SOS работает (не блокируется update install).

## Зачем

Это **критическая safety-функция**. Один из главных reasons почему семья поставит это приложение бабушке. Без неё продукт — просто красивый launcher.

## Что входит технически (для AI-агента)

- `SOSButton` capability как mandatory step в Wizard preset (TASK-7).
- `SOSPush` event через FCM (TASK-5 F-5c) с severity = actionable + time-sensitive + relevant per CLAUDE.md rule 10.
- Confirm UI с timeout (3 sec — отмена; 5 sec без ответа — auto-send).
- Location attach (если permission granted, async fetch с timeout 5 сек — не блокирует SOS).
- App update deferral: SOS блокирует install update пока не отменён.
- Admin notification handler в TASK-8 Admin App: open map + call action.

**SOS payload wire format decision** (added 2026-07-07 per audit item #6 / Тема 8 / crypto-mentor Блок 7):
- SOS payload может превышать 4KB FCM limit при inclusion location + optional context.
- Concept: encrypted payload inline (≤ 2.5KB после base64), fallback = trigger + bucket ref для larger content.
- **Wire format decision** (`SosPayload` newtype) — required при implementation этой задачи per TASK-16 wire format discipline. Options:
  - Option A: strict cap ≤ 2.5KB inline, no chunk assembly, location as opaque token.
  - Option B: chunk assembly (2-3 FCM messages, client re-assembles by `sos_id + chunk_index`).
  - Option C: trigger-only push, actual payload via bucket sync post-trigger.
- Decision to be made at `/speckit.specify` time, recorded в spec `contracts/sos-payload-v1.md` (per TASK-16 inband schemaVersion first-byte discipline).
- Latency-critical: SOS <5s end-to-end (button → admin notification). Chunk assembly adds latency, option A preferred if payload constraints allow.

## Состояние

**Planned.** Зависит от TASK-7 (Simple Launcher как UI host), TASK-8 (Admin App для recipient), TASK-5 (FCM push transport).

---

## Готовый промт для `/speckit.specify`

```
Реализуй S-4: SOS Capability + Wizard Step.

ЧТО СТРОИМ:
SOS button capability — большая persistent кнопка на home screen Simple Launcher. Нажатие → confirm UI (anti-misclick с auto-send fallback при no-response) → push к всем paired admins через TASK-5 FCM (severity = actionable + time-sensitive + relevant per rule 10). Опциональный location attach. App update deferral — SOS не блокируется в-процессе обновления.

ЗАЧЕМ:
Критическая safety-функция. Один из главных reasons установки приложения семьёй для бабушки.

SCOPE ВКЛЮЧАЕТ:
- SOSButton capability в Wizard preset (mandatory step в TASK-7 Simple Launcher).
- Confirm UI: 3 сек cancel window, 5 сек auto-send window.
- Location attach (async, не блокирует, timeout 5 сек).
- SOSPush event через FCM (TASK-5) с high-priority.
- Admin notification handler в TASK-8 Admin App (open map + call action).
- App update deferral: SOS работает во время install update (foreground service).
- OEM-quirk testing: Samsung, OEM с custom skin (currently Xiaomi MIUI), Huawei (background restrict bypass).

SCOPE НЕ ВКЛЮЧАЕТ:
- Hardware SOS power-button (post-MVP).
- Caregiver SOS receivers (TASK-31 V-6).
- SOS via Bluetooth wearable (TASK-30 V-5).

DEPENDENCIES:
- TASK-7 (S-1 Simple Launcher как UI host).
- TASK-8 (S-2 Admin App как recipient).
- TASK-5 (F-5c FCM push transport).

ACCEPTANCE CRITERIA:
- Бабушка тапнула SOS → появился экран подтверждения с большой «Да» и маленькой «Отмена».
- Не нажала ничего 5 секунд → SOS отправился автоматически.
- Тапнула «Отмена» в 3 секунды → ничего не отправилось.
- Push пришёл admin'у в течение 5 секунд со звуком, даже когда телефон admin'а в Silent.
- Если разрешение location выдано → к push прикреплена координата.
- Если location не выдано → push приходит без координаты, без падения.
- Во время install update SOS работает (foreground service не убит).
- На OEM с custom skin (currently Xiaomi MIUI) / Samsung Knox push доходит несмотря на background restrict.

LOCAL TEST PATH:
- Два эмулятора (admin + managed) через skill android-emulator.
- Manual smoke на physical device #1 (currently physical device #1 (currently Xiaomi 11T)) для OEM background restrict.
- Fake LocationProvider для unit-tests.

CONSTITUTION GATES:
- Rule 1 (domain isolation): SOSEvent — pure domain.
- Rule 10 (notification minimization): SOS push justified explicitly (actionable + time-sensitive + relevant).
- Rule 14 (security): location attach opt-in, не отправляется без permission.

EFFORT: Medium (~2 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Push к admins через F-5c (severity: actionable + time-sensitive + relevant)
- [ ] #2 Confirm UI с timeout (anti-misclick для случайных нажатий)
- [ ] #3 Location attach (если permission granted, async с timeout)
- [ ] #4 App update deferral: SOS блокирует update install до confirm
- [ ] #5 Бабушка тапнула SOS → появился экран подтверждения с большой 'Да' и маленькой 'Отмена'
- [ ] #6 Не нажала ничего 5 секунд → SOS отправился автоматически
- [ ] #7 Тапнула 'Отмена' в 3 секунды → ничего не отправилось
- [ ] #8 Push пришёл admin'у за <5 секунд со звуком, даже когда телефон admin'а в Silent
- [ ] #9 С разрешением location → к push прикреплена координата
- [ ] #10 Без location → push без координаты, без падений
- [ ] #11 Во время install update приложения SOS всё равно работает
- [ ] #12 На OEM с custom skin (currently Xiaomi MIUI) / Samsung push доходит несмотря на background restrict
<!-- AC:END -->
