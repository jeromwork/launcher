---
id: TASK-15
title: Subscription Server Timer
status: Draft
assignee: []
created_date: '2026-06-23 05:37'
updated_date: '2026-06-23 06:18'
labels:
  - phase-2
  - s-spec
  - s-10
  - billing
  - cloud-only
  - tamper-resistance
milestone: m-1
dependencies:
  - TASK-3
priority: medium
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Платная подписка для родственников-admin'ов. Только облачные функции платные; локальный режим бесплатен навсегда. Проверка подписки делается на сервере, а не на устройстве — чтобы её нельзя было «взломать» патчем приложения.

**Что происходит по шагам (нормальный сценарий):**
1. Пользователь скачал приложение → работает в local mode бесплатно (без admin app, без облачной синхронизации).
2. Решил подключить родственника как admin (TASK-8 S-2) → впервые понадобилось cloud.
3. Открывается экран подписки: «300₽/мес за семейный аккаунт».
4. Оплачивает через Google Play Billing → подтверждение от Google уходит на наш сервер (Cloudflare Worker).
5. Сервер выдаёт подписанный токен (JWT) с правом использовать cloud features.
6. Приложение получает токен → cloud features включаются.

**Что происходит каждый раз при cloud-действии:**
1. Приложение хочет вызвать cloud function (например, отправить config через TASK-5).
2. Сервер проверяет подпись токена + срок действия + статус подписки в Google Play.
3. Если ок — выполняет.
4. Если подписка кончилась → отказ + предложение продлить.

**Что бесплатно навсегда:**
- Simple Launcher (TASK-7) — настройка телефона бабушки.
- Локальные плитки контактов (TASK-9) — без admin app, бабушка сама.
- SOS на местном уровне (открытие звонилки).

**Что платное:**
- Admin app + удалённое редактирование конфига.
- Облачное хранение фото (TASK-11).
- Push-уведомления SOS родственникам.
- Phone Health Monitoring дашборд (TASK-14).

## Зачем

Это **бизнес-модель продукта**. Также защита от взлома: если бы проверка была на устройстве (`isPremium = true` в shared prefs), хакер бы пропатчил APK и получил бесплатно. Server-validated JWT — не пропатчишь.

## Что входит технически (для AI-агента)

- `BillingEntitlement` port в `core/billing/`.
- `ServerBillingAdapter`: JWT validation против Cloudflare Worker endpoint.
- Worker endpoint `/billing/check` возвращает signed entitlement (RS256).
- Все cloud features (TASK-8, TASK-11, TASK-13) проверяют entitlement перед action.
- Local mode НЕ gate'ится (TASK-7 / TASK-9 / SOS-local) — навсегда бесплатно.
- Google Play Billing client integration (Subscription with Auto-Renew).
- Server-side Google Play Subscriptions API validation (avoid client spoofing).

## Состояние

**Planned.** Зависит от TASK-3 (identity для subscription holder).

---

## Готовый промт для `/speckit.specify`

```
Реализуй S-10: Subscription Server Timer.

ЧТО СТРОИМ:
Server-validated subscription entitlement (cloud-only billing per decision 2026-06-15-deferred-cloud/03). BillingEntitlement port в core/billing/ + ServerBillingAdapter (JWT validation против Cloudflare Worker). Worker endpoint /billing/check возвращает signed entitlement (RS256). Cloud features (TASK-8/11/13) check entitlement перед action; local-only (TASK-7/9/SOS-local) — никогда не gate'ятся.

ЗАЧЕМ:
Бизнес-модель + anti-tamper protection (нельзя patch'нуть APK для бесплатного cloud).

SCOPE ВКЛЮЧАЕТ:
- BillingEntitlement port в core/billing/ (suspend fun checkEntitlement(): Entitlement).
- ServerBillingAdapter: JWT validation, RS256.
- Cloudflare Worker /billing/check endpoint:
  - input: deviceId, signed proof of Google Play subscription.
  - output: signed JWT entitlement (expires hourly).
- Google Play Billing Client интеграция (Subscription + Auto-Renew).
- Server-side Google Play Subscriptions API validation (avoid client spoofing per rule 3 tamper-resistance).
- Cloud feature gates: TASK-8 (cloud pairing path), TASK-11 (blob upload), TASK-13 (config push to other devices).
- Local-only feature explicit non-gating: TASK-7 (Simple Launcher), TASK-9 (Contact Tiles), local SOS dial.
- Grace period after cancellation (7 days read-only access).

SCOPE НЕ ВКЛЮЧАЕТ:
- Family plan splits / per-user pricing (post-MVP).
- iOS App Store IAP (TASK-26 V-1 in Phase 4).
- Tamper-resistance L2+L3 (TASK-48 L-15 in Phase 5).
- Promo codes / discounts (post-MVP).

DEPENDENCIES:
- TASK-3 (F-4 AuthProvider для subscription holder identity).
- Cloudflare Worker уже deployed (как и для TASK-5 FCM).

ACCEPTANCE CRITERIA:
- Свежий пользователь установил приложение → local mode работает без оплаты бесконечно.
- Попытался pair admin'а (TASK-8) → увидел экран подписки.
- Оплатил подписку → cloud features включились в течение 30 секунд.
- Подписка кончилась → cloud features перестали работать; local mode (TASK-7/9) продолжает.
- 7 дней grace после cancellation → cloud работает read-only (нельзя save, можно view).
- Patch APK (изменить isPremium=true в коде) → сервер всё равно отказывает (JWT не пройдёт validation).
- Поменял телефон → подписка восстанавливается через Google account.

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 с Google Play Billing testing skus.
- Mock entitlement via FakeBillingAdapter для unit-tests fea ture gates.
- E2E с реальным Worker staging.

CONSTITUTION GATES:
- Rule 1 (domain isolation): BillingEntitlement — port в core/billing/.
- Rule 2 (ACL): Google Play Billing Client не вытекает в domain.
- Rule 3 (one-way door): subscription = cloud-only решение из decision 03, exit ramp = добавить tier'ы в JWT без переписывания.
- Rule 14 (security): server-validated (rule из CLAUDE.md про не делать client-side gating для anti-tamper фич).

EFFORT: Medium (~2 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ServerBillingAdapter: JWT validation против Cloudflare Worker
- [ ] #2 Worker endpoint /billing/check возвращает signed entitlement
- [ ] #3 Cloud features (S-2 admin app, S-5 photos) проверяют entitlement before action
- [ ] #4 Local-only mode остаётся бесплатным навсегда (нет gating)
- [ ] #5 Свежий пользователь установил приложение → local mode работает без оплаты бесконечно
- [ ] #6 Попытался pair admin'а (TASK-8) → увидел экран подписки
- [ ] #7 Оплатил подписку → cloud features включились за <30 секунд
- [ ] #8 Подписка кончилась → cloud features перестали работать; local mode продолжает
- [ ] #9 7 дней grace после cancellation → cloud работает read-only (нельзя save, можно view)
- [ ] #10 Patch APK (изменить isPremium=true) → сервер всё равно отказывает (JWT не пройдёт validation)
- [ ] #11 Поменял телефон → подписка восстанавливается через Google account
<!-- AC:END -->
