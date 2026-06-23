---
id: TASK-11
title: Contact Photos (Family Album foundation)
status: Draft
assignee: []
created_date: '2026-06-23 05:37'
updated_date: '2026-06-23 06:15'
labels:
  - phase-2
  - s-spec
  - s-5
  - photos
  - family-album
  - blob-storage
milestone: m-1
dependencies:
  - TASK-9
priority: high
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Фотографии на плитках контактов вместо абстрактных иконок. Бабушка видит лицо внука на плитке и сразу понимает, кому звонит.

**Что происходит по шагам (admin загружает фото):**
1. Admin в Admin App открывает контакт «Петя».
2. Тапает «Изменить фото» → выбирает фото из галереи телефона.
3. Приложение автоматически сжимает фото (макс 2MB после сжатия).
4. Фото шифруется специальным ключом так, чтобы прочитать его могло только устройство бабушки (через шифрование recipient-specific).
5. Зашифрованный «конверт» (envelope) загружается в облачное хранилище (Cloudflare R2 или Backblaze B2).
6. На устройство бабушки приходит push (TASK-5) с новой версией конфигурации.

**Что происходит на устройстве бабушки:**
1. Принимает push → понимает что есть новое фото для контакта Петя.
2. Скачивает зашифрованный «конверт» из облака.
3. Расшифровывает локально на устройстве (расшифровать может только это устройство, сервер видит только зашифрованный blob).
4. Сохраняет в локальный кэш для офлайн-показа.
5. Плитка Пети обновляется — теперь там его фото.

**Защита приватности:**
- Сервер видит только зашифрованные blob'ы, не сами фото.
- Никто кроме устройства бабушки не может расшифровать.
- Если бабушка офлайн — фото показывается из кэша.

## Зачем

Без фото плитки выглядят одинаково, бабушка путает контакты. С фото — узнаваемость 100%, основная UX-win. Также это **первая итерация Family Album** (расширится в TASK-28 V-3 — полный альбом с видео).

## Что входит технически (для AI-агента)

- Admin upload UI: photo per contact, сжатие до 2MB.
- Envelope encryption: per-recipient Curve25519 wrap + AEAD blob (через TASK-2 F-CRYPTO + TASK-6 KeyRegistry).
- `BlobStorage` port + R2/B2 adapter (backend-substitution-ready per CLAUDE.md rule 8).
- Managed pre-fetch on config update (триггер через TASK-5 F-5c push).
- Cache + offline rendering в ContactTile (TASK-9).
- Shareability: фото — identity-bound (NOT shareable per rule 9; шифрование запрещает share).

## Состояние

**Planned.** Зависит от TASK-9 (Contact Tiles UI).

---

## Готовый промт для `/speckit.specify`

```
Реализуй S-5: Contact Photos (Family Album foundation).

ЧТО СТРОИМ:
Admin загружает фото контакта в Admin App. Фото сжимается до 2MB, envelope-encrypted (per-recipient Curve25519 + AEAD), uploaded в Cloudflare R2 / Backblaze B2 через BlobStorage port (backend-substitution-ready). Managed получает push (TASK-5), pre-fetch'ит blob, decrypts локально, кэширует, рендерит в ContactTile (TASK-9). Сервер видит только encrypted blob.

ЗАЧЕМ:
Узнаваемость контактов 100% — основная UX-win. Первая итерация Family Album (расширится в TASK-28 V-3).

SCOPE ВКЛЮЧАЕТ:
- Admin photo upload UI: per-contact, compression до 2MB.
- Envelope encryption: Curve25519 wrap + AEAD blob (TASK-2 + TASK-6).
- BlobStorage port в core/blob/ + R2 adapter + B2 adapter (rule 8 backend-substitution).
- Managed pre-fetch on config update push (TASK-5).
- Local cache + offline rendering в ContactTile (TASK-9).
- Encryption is identity-bound (NOT shareable per rule 9 — namespace privacy).

SCOPE НЕ ВКЛЮЧАЕТ:
- Видео / аудио (TASK-28 V-3 в Phase 4).
- Album UI / timeline / search (TASK-28 V-3).
- Anniversaries / memories surfacing (TASK-28 V-3).
- Storage budget UI (post-MVP).

DEPENDENCIES:
- TASK-9 (S-3 Contact Tiles UI host).
- TASK-2 (F-CRYPTO для envelope).
- TASK-6 (F-5 KeyRegistry для DEK per recipient).
- TASK-5 (F-5c FCM push для update trigger).
- TASK-8 (S-2 Admin App для upload UI).

ACCEPTANCE CRITERIA:
- Admin загрузил фото 5MB → автоматически сжалось до <2MB.
- Push дошёл до бабушки за <5 секунд после upload.
- Бабушка увидела фото в плитке за <15 секунд после upload (включая download).
- Бабушка в офлайне → фото показывается из локального кэша.
- Сервер видит только encrypted blob (manual проверка: запрос curl без ключа возвращает unreadable bytes).
- Удалил фото в Admin → плитка вернулась к placeholder за <10 секунд.

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 для Admin + второй эмулятор для Managed.
- Fake BlobStorage adapter для unit tests шифрования.
- E2E test с реальным R2 staging bucket.

CONSTITUTION GATES:
- Rule 1 (domain isolation): BlobStorage — port в core/blob/.
- Rule 2 (ACL): R2 / B2 SDK не вытекает в domain.
- Rule 5 (wire format): EnvelopeBlob schemaVersion=1, roundtrip test.
- Rule 8 (server migration): BlobStorage adapter — substitution-ready, inline TODO в адаптере про переезд на own server.
- Rule 9 (shareability): photo — identity-bound, не shareable artefact.

EFFORT: Large (~3 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Envelope encrypt: per-recipient Curve25519 wrap + AEAD blob
- [ ] #2 Storage adapter (BlobStorage port + R2/B2 adapter — backend-substitution-ready)
- [ ] #3 Managed pre-fetch on config update (через F-5c push)
- [ ] #4 Cache + offline rendering в ContactTile
- [ ] #5 Admin загрузил фото 5MB → автоматически сжалось до <2MB
- [ ] #6 Push дошёл до бабушки за <5 секунд после upload
- [ ] #7 Бабушка увидела фото в плитке за <15 секунд после upload (включая download)
- [ ] #8 Бабушка в офлайне → фото показывается из локального кэша
- [ ] #9 Сервер видит только зашифрованный blob (проверка: curl без ключа возвращает нечитаемые байты)
- [ ] #10 Удалил фото в Admin → плитка вернулась к placeholder за <10 секунд
<!-- AC:END -->
