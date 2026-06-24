---
id: TASK-49
title: Cloud Feature Inventory + Offline-First Architecture
status: Done
assignee: []
created_date: '2026-06-23 09:42'
updated_date: '2026-06-24 13:45'
labels:
  - phase-1
  - architecture
  - cloud
  - offline-first
  - one-way-door
milestone: m-0
dependencies: []
references:
  - specs/task-49-cloud-feature-inventory-offline-first/
priority: high
ordinal: 5500
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic, B2B, self-care.

## Что это простыми словами

Архитектурное разделение приложения на **функционал, работающий локально без сервера**, и **функционал, требующий облака**. Плюс простой механизм проверки «работаем сейчас с облаком или нет» для всего приложения.

**Что происходит по шагам (нормальный сценарий — local-only):**
1. Пользователь устанавливает приложение, **никакого Google-входа не требуется**.
2. Приложение работает: главный экран, плитки контактов (только локальные), SOS-кнопка (вызывает 112), темы, размер шрифта, wizard настройки.
3. Никакие данные на сервер не уходят. FCM token не регистрируется. Telemetry / health snapshots не собираются.
4. Если пользователь захочет cloud-features — мягкое предложение «зарегистрируйтесь через Google, получите больше возможностей» (без давления, без блокировок).

**Что происходит при включении cloud (явное opt-in):**
1. Пользователь тапает явное cloud-action (например, «подключить родственника как admin» / «синхронизировать настройки» / «загрузить фото контактов»).
2. Запускается Google Sign-In flow.
3. После Sign-In запускается **TASK-6** (Root Key Hierarchy + Recovery Backup) — настройка пароля восстановления.
4. После setup — изначально запрошенное cloud-action продолжается автоматически.
5. С этого момента cloud-features активны: FCM token регистрируется, sync работает, admin может управлять.

**Что делает приложение во время работы:**
- Внутри есть единый `CloudAvailability` сервис, который знает «доступно облако или нет» (есть Sign-In + сеть + GMS).
- Каждый Composable / Service / WorkManager-job проверяет этот сервис перед cloud-операцией.
- Если cloud недоступен — feature работает в local mode (если возможен) ИЛИ показывает «эта функция требует подключения к интернету».

**Что НЕ требует cloud (local-only forever):**
- Главный экран, плитки контактов (локально).
- SOS-кнопка → fallback на dialer с 112 (или другим emergency-номером по локали).
- Темы, размер шрифта, базовый wizard настройки.
- Локальные конфиги.

**Что требует cloud:**
- Pairing с admin (TASK-8).
- Sync конфигов между устройствами (TASK-4/5/13).
- Фотографии на плитках (TASK-11).
- Health monitoring admin'у (TASK-14).
- Subscription billing (TASK-15).
- Push-уведомления через FCM (TASK-5).

## Зачем

**Главный архитектурный принцип проекта**: каждое устройство **самодостаточно**, cloud — это **upgrade**, не requirement.

Без этой задачи приложение неявно требует Google Sign-In при первом запуске (FCM token регистрируется автоматически, как сейчас в TASK-5). Это нарушает device-self-sufficiency principle (decision 2026-06-15-deferred-cloud/01) и блокирует pure-local use case (Huawei без GMS, пользователи без Google-аккаунта, регионы где Google недоступен).

Также блокирует TASK-6 (Root Key + Recovery Backup): без явного определения «что считать первым cloud-action» TASK-6 не может правильно тригерить Setup screen.

## Что входит технически (для AI-агента)

- `CloudAvailability` port в `core/cloud/` (KMP — common). `StateFlow<CloudState>` с состояниями `Unknown / Offline / Available / Disabled(reason)`.
- `CloudFeatureRegistry` — declarative список всех cloud-фич с пометками `local-only` / `cloud-required` / `cloud-augmented` (с local fallback). Аналог `WizardManifest`.
- `LocalOnlyFallback` interface для cloud-augmented фич с обязательным local fallback (например SOS).
- Android adapter `CloudAvailabilityImpl` — проверяет GMS availability + network + Sign-In status.
- DI wiring: каждая cloud-фича получает `CloudAvailability` через injection.
- Регрессионный fix для TASK-5 (F-5c): отложить FCM token registration до первого явного cloud-action.
- Документация `docs/dev/offline-online-architecture.md` — какие фичи local, какие cloud, что считать cloud-action, как работает CloudAvailability.

## Состояние

**Draft.** Создан 2026-06-23. Блокирует TASK-6 — TASK-6 ставится на Paused, ждёт закрытия TASK-49.

---

## Готовый промт для /speckit.specify

> Можно скопировать целиком и вставить в `/speckit.specify`.

````
Реализуй TASK-49: Cloud Feature Inventory + Offline-First Architecture.

ЧТО СТРОИМ:
Архитектурное разделение приложения на local-only и cloud-required функционал. CloudAvailability port в core/cloud/ (KMP, StateFlow). CloudFeatureRegistry — declarative manifest cloud-фич с пометками local-only / cloud-required / cloud-augmented + LocalOnlyFallback для последних. Android adapter проверяет GMS + network + Sign-In. Регрессионный fix TASK-5: FCM token registration отложен до первого явного cloud-action.

ЗАЧЕМ:
Реализует device-self-sufficiency principle (decision 2026-06-15-deferred-cloud/01). Без этого приложение неявно требует Sign-In при первом запуске. Блокирует TASK-6 (нужен trigger для Recovery Backup Setup screen).

SCOPE ВКЛЮЧАЕТ:
- CloudAvailability port в core/cloud/commonMain (KMP).
- CloudState sealed class: Unknown / Offline / Available / Disabled(reason).
- CloudFeatureRegistry — manifest всех cloud-фич Phase 1-2.
- CloudMode enum: LocalOnly / CloudRequired / CloudAugmented.
- LocalOnlyFallback interface для CloudAugmented (обязательно для каждой такой фичи).
- Android CloudAvailabilityImpl adapter (GMS + ConnectivityManager + FirebaseAuth status).
- DI wiring через Koin (или текущий DI стек).
- Регрессионный fix TASK-5: FCM token регистрируется только после первого явного cloud-action.
- Inventory всех фич Phase 1 + Phase 2: для каждой пометка local-only / cloud-required / cloud-augmented + fallback path.
- SOS как явный example cloud-augmented с local fallback (dialer 112 / locale emergency number).
- Documentation: docs/dev/offline-online-architecture.md на простом русском.

SCOPE НЕ ВКЛЮЧАЕТ:
- Изменение существующего Recovery Backup flow (TASK-6 territory, ждёт нас).
- Implementation конкретных fallback'ов кроме SOS (каждая S-задача сама делает свой fallback).
- iOS adapter для CloudAvailability (Phase 4 V-1).
- Subscription billing gates (TASK-15 — отдельная задача).
- Прозрачное переключение online/offline в realtime UI (post-MVP, может быть отдельная задача).

DEPENDENCIES:
- TASK-3 (F-4 AuthProvider) — для проверки Sign-In status.
- TASK-2 (F-CRYPTO) — нужен для будущего использования через TASK-6.

(TASK-49 НЕ зависит от TASK-6 — она его блокирует, а не наоборот.)

ACCEPTANCE CRITERIA:
- Свежий пользователь установил приложение → главный экран показывается без Google-вход.
- Тапнул SOS → dialer с 112 открывается за <1 секунду, никаких cloud-проверок.
- Тапнул «синхронизировать настройки в облако» → запускается Google Sign-In → потом TASK-6 Setup → потом sync.
- FCM token НЕ регистрируется в Firestore до первого явного cloud-action (проверка через inspect Firestore).
- В local-only режиме нет background-запросов к Google / Firebase (проверка через packet capture).
- CloudAvailability корректно переключается на Offline при отключении сети, на Available — при возврате.
- Huawei без GMS → CloudAvailability навсегда показывает Disabled(reason="GMS unavailable"), все cloud-фичи скрыты или local fallback.

LOCAL TEST PATH:
- JVM unit tests для CloudAvailability state machine + CloudFeatureRegistry contract.
- pixel_5_api_34 emulator: install → home screen без Sign-In → local features работают.
- pixel_5_api_34 emulator: тап cloud-action → Sign-In flow → проверка что setup идёт правильно.
- physical device #1 (currently Xiaomi 11T): regression test — FCM token не уходит до явного cloud-action.
- Emulator без GMS (или DI-override): all cloud-features показывают local fallback / hidden.

CONSTITUTION GATES:
- Rule 1 (domain isolation): CloudAvailability — port в core/cloud/commonMain, никаких Android / Firebase типов.
- Rule 2 (ACL): GMS / FirebaseAuth / ConnectivityManager — в CloudAvailabilityImpl adapter.
- Rule 4 (MVA): CloudFeatureRegistry — declarative, без preemptive абстракций.
- Rule 5 (wire-format): n/a — задача без wire-format изменений.
- Rule 6 (mock-first): FakeCloudAvailability + FakeCloudFeatureRegistry для тестов.

EFFORT: Medium (~1-2 weeks).
````
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Свежее установленное приложение запускается и показывает функциональный главный экран без Google Sign-In за <3 секунды на эмуляторе
- [x] #2 SOS открывает dialer с emergency-номером за <1 секунду независимо от cloud-state
- [x] #3 В local-mode (без Sign-In) packet capture 5 минут показывает 0 запросов к Firebase / Firestore / FCM
- [x] #4 Sign-In success → cloudAvailable=true в DataStore за <500ms (push через AuthProvider)
- [x] #5 Sign-Out → cloudAvailable=false за <500ms (push через AuthProvider)
- [x] #6 SignInExplanationScreen показывается одинаково из wizard и Settings (визуальное соответствие)
- [x] #7 Huawei без GMS — приложение запускается, проходит wizard, локальные features работают, без crashes
- [x] #8 Documentation cloud-availability.md читается non-developer владельцем за <10 минут
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
All 8 AC closed (confirmed by owner 2026-06-24). Status → Done.
<!-- SECTION:FINAL_SUMMARY:END -->
