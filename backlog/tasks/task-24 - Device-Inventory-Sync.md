---
id: TASK-24
title: Device Inventory Sync
status: Draft
assignee: []
created_date: '2026-06-23 05:39'
updated_date: '2026-06-23 06:25'
labels:
  - phase-3
  - p-spec
  - p-9
  - sync
  - privacy
  - inventory
milestone: m-2
dependencies:
  - TASK-13
  - TASK-23
priority: medium
ordinal: 24000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Telephone бабушки периодически собирает список **установленных приложений** (просто названия, без статистики использования), шифрует и отправляет admin'у. Admin при редактировании плиток видит, что у бабушки реально установлено.

**Что происходит по шагам:**
1. Бабушка установила Сбербанк-Онлайн (или удалила какое-то приложение).
2. Через несколько секунд (Android system broadcast `PACKAGE_ADDED`) → телефон собирает обновлённый список:
   - `com.sberbank.online` (Сбербанк-Онлайн), `com.whatsapp` (WhatsApp), ...
3. Список шифруется (тем же ключом что и config — TASK-4).
4. Загружается на сервер. Сервер видит **только зашифрованный blob**, не список.
5. Admin в своём приложении при редактировании плитки видит:
   - «У бабушки установлено: WhatsApp, Telegram, Сбербанк-Онлайн (новое!), Viber, …».
   - «Inventory обновлён 3 часа назад».

**Что НЕ собирается:**
- Никакой статистики использования (как часто открывает приложение).
- Никаких логов запуска.
- Никаких личных данных из приложений.
- **Только packageNames + label + versionCode** (просто список).

**Зачем admin'у нужно это видеть:**
- При создании плитки сразу понятно, какие приложения доступны.
- Warning «inventory обновилась, app X у senior больше нет — плитка будет вести в Play Store» — чтобы случайно не создать плитку для отсутствующего приложения.

## Зачем

Без этого admin создаёт плитку «Telegram» наугад, не зная установлен ли он → бабушка тапает → ошибка. С inventory — admin видит реальную картину.

## Что входит технически (для AI-агента)

- Inventory wire format `schemaVersion: 1` (soft limit ~500 apps).
- Broadcast receiver на `PACKAGE_ADDED` / `PACKAGE_REMOVED` → rebuild.
- Sanity refresh 1×/день (на случай если receiver проспал из-за low-memory kill).
- Envelope encryption тем же ключом что и config (TASK-4).
- Admin UI: пересечение `recipe-каталог × inventory senior'а` + warning при obsolete.
- Privacy: **NO analytics, NO crash reports** с этими данными.

## Состояние

**Planned.** Зависит от TASK-13 (S-8 admin editor — где это показывается), TASK-23 (P-8 recipe catalogue — для intersection).

---

## Готовый промт для `/speckit.specify`

```
Реализуй P-9: Device Inventory Sync.

ЧТО СТРОИМ:
Senior устройство периодически собирает Inventory (packageNames + labels + versionCodes установленных apps) через PackageManager. Encrypted upload тем же envelope ключом что и config (TASK-4). Admin при редактировании плиток видит пересечение recipe-каталог × inventory + warning при obsolete. Сервер видит только blob.

ЗАЧЕМ:
Admin создаёт плитки осознанно, видя реально установленные apps; бабушка не тапает на «несуществующие» плитки.

SCOPE ВКЛЮЧАЕТ:
- Inventory wire format schemaVersion=1 (apps: packageName + label + versionCode).
- Soft limit ~500 apps (Firestore document size).
- Broadcast receiver PACKAGE_ADDED/REMOVED → rebuild.
- Sanity refresh 1×/day (WorkManager) — fallback если broadcast missed.
- Envelope encryption тем же ключом что config (TASK-4 F-5b).
- Admin UI: пересечение recipe-каталог (TASK-23) × inventory.
- Warning при obsolete («inventory обновлено N часов назад, app X отсутствует → плитка ведёт в Play Store, продолжить?»).
- Privacy: NO usage stats, NO analytics, NO crash reports с этими данными.

SCOPE НЕ ВКЛЮЧАЕТ:
- Usage statistics («бабушка открывает WhatsApp 50 раз в день») — privacy red flag.
- Real-time push «бабушка установила X» — sanity refresh покрывает.
- Cross-device inventory одного admin'а (admin видит свой PackageManager напрямую).
- Cross-platform inventory (TV / wearable) — Phase 4.
- Bypass Android 11+ package visibility через QUERY_ALL_PACKAGES — НЕ делать, Play Store запретит.

DEPENDENCIES:
- TASK-13 (S-8 admin editor — UI host).
- TASK-23 (P-8 recipe catalogue — для intersection — optional, можно параллельно).
- TASK-4 (F-5b envelope encryption — для upload).

ACCEPTANCE CRITERIA:
- Бабушка установила новый app → admin увидел его в списке через <5 минут.
- Admin создал плитку для app которого нет у бабушки → warning «app X отсутствует у senior».
- Admin продолжил всё равно → плитка ведёт в Play Store при тапе.
- Inventory показывает «обновлено N часов/минут назад».
- Сервер получает только blob (manual проверка через inspect Firestore).
- НЕТ usage statistics — manual проверка содержимого payload.

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 — install/uninstall apps + observe broadcast.
- Mock PackageManager для unit-tests.
- Privacy test: assertion что payload содержит только {packageName, label, versionCode}.

CONSTITUTION GATES:
- Rule 1 (domain isolation): Inventory — pure domain value.
- Rule 5 (wire format): schemaVersion=1, roundtrip test.
- Rule 9 (privacy): explicit list NO usage / NO analytics / NO crash logs.

EFFORT: Small (~1-1.5 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Broadcast receiver на PACKAGE_ADDED/REMOVED + sanity refresh 1x/day
- [ ] #2 Envelope encryption тем же ключом что и config
- [ ] #3 Admin UI: пересечение recipe-каталог × inventory senior'а + warning при obsolete
- [ ] #4 Privacy: НЕТ analytics, НЕТ crash reports с этими данными
- [ ] #5 Бабушка установила новый app → admin увидел его в списке через <5 минут
- [ ] #6 Admin создал плитку для app которого нет у бабушки → warning 'app X отсутствует у senior'
- [ ] #7 Admin продолжил всё равно → плитка ведёт в Play Store при тапе
- [ ] #8 Inventory показывает 'обновлено N часов/минут назад'
- [ ] #9 Сервер получает только blob (manual проверка через inspect Firestore)
- [ ] #10 НЕТ usage statistics в payload — manual проверка содержимого
<!-- AC:END -->
