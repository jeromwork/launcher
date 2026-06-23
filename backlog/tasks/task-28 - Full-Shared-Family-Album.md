---
id: TASK-28
title: Full Shared Family Album
status: Planned
assignee: []
created_date: '2026-06-23 05:40'
updated_date: '2026-06-23 06:28'
labels:
  - phase-4
  - v-spec
  - v-3
  - album
  - media
  - family
milestone: m-3
dependencies:
  - TASK-11
priority: medium
ordinal: 27000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Полноценный семейный фотоальбом, расширение TASK-11 S-5 (которая делала только фото для контактов). Здесь: видео, аудио, заметки-воспоминания, timeline, поиск, годовщины.

**Что происходит по шагам:**
1. Дочка-admin в Album App загружает 50 фото и 10 видео из отпуска.
2. Видео сжимаются (адаптивный bitrate), все файлы шифруются для каждого члена семьи отдельно.
3. Зашифрованные части загружаются на сервер chunk'ами (большие видео не одним блоком).
4. Все члены семьи (бабушка через launcher + другие через Album App) получают push «Новые фото из отпуска».
5. Бабушка тапает плитку «Альбом» в launcher → видит timeline: «Отпуск 2026, лето» → пролистывает фото.
6. На годовщину «Свадьба 25 лет назад» автоматически появляется напоминание «Сегодня годовщина свадьбы. Фото из этого дня:» с подборкой.

**Что бабушка делает (минимум):**
- Только смотрит.
- Тап → view → swipe.
- Никаких загрузок, удалений, поиска (это admin делает).

**Что admin делает:**
- Загружает.
- Тэгирует («дача», «внуки», «80-летие»).
- Удаляет.
- Создаёт «moments» (подборки).
- Настраивает кому что показывать (можно ограничить отдельные фото).

## Зачем

TASK-11 S-5 даёт только фото-аватары на плитках контактов. Этого мало для «семейного пространства». Полноценный альбом превращает приложение из коммуникации в **общую память семьи**. Это сильный engagement hook.

## Что входит технически (для AI-агента)

- Chunked upload (через `BlobStorage` adapter из TASK-11) — большие видео грузятся частями, retry на сбоях.
- Album UI: timeline (chronological), search (by tag/date), captions, anniversaries trigger.
- Anniversaries algorithm: at year boundaries surface фото из соответствующего дня прошлых лет.
- Storage budget per family group (e.g., 50GB free → upgrade tier через subscription TASK-15).
- Per-photo visibility: admin может задать «это фото только для жены и внуков».

## Состояние

**Planned.** Зависит от TASK-11 (S-5 photos foundation), TASK-15 (subscription для storage tier).

---

## Готовый промт для `/speckit.specify`

```
Реализуй V-3: Full Shared Family Album.

ЧТО СТРОИМ:
Расширение TASK-11 S-5 (photos foundation) в полноценный семейный альбом: chunked video upload, audio notes, captions, tags, timeline UI, search, anniversaries trigger, per-photo visibility, storage budget per family group.

ЗАЧЕМ:
Превращает product из коммуникации в общую память семьи. Сильный engagement hook.

SCOPE ВКЛЮЧАЕТ:
- Chunked upload через BlobStorage adapter (TASK-11) — retry on failure.
- Adaptive bitrate для видео (compress to fit storage budget).
- Audio notes (короткие голосовые комментарии к фото).
- Album UI: timeline + search + captions + tags + moments (curated collections).
- Anniversaries algorithm: surface photos from same-day past years.
- Storage budget per family group + upgrade tier через TASK-15 subscription.
- Per-photo visibility: admin restricts who sees what.
- Bandwidth-friendly: low-res thumbnail сначала, full-res по тапу.

SCOPE НЕ ВКЛЮЧАЕТ:
- AI photo enhancement / categorization (TASK-36 L-3 если AI готов).
- Public sharing вне family (privacy red flag).
- Live streaming (TASK-27 V-2 messenger покрывает).

DEPENDENCIES:
- TASK-11 (S-5 photos foundation — расширяется).
- TASK-15 (S-10 subscription — для storage tiers).
- TASK-3 / TASK-6 (identity + crypto).

ACCEPTANCE CRITERIA:
- Admin загрузил 50 фото + 10 видео → upload завершился за <5 минут, все доступны в timeline.
- Бабушка в launcher плитка «Альбом» → тап → видит timeline с группировкой по дате.
- Свайп по timeline — плавно, без задержек (thumbnails кэшируются).
- Сегодня годовщина → автоматически появляется блок «Фото из этого дня прошлых лет».
- Admin тегировал фото «дача» → search по «дача» возвращает все эти фото.
- Admin ограничил фото «только для жены» → внук не видит это фото в своём приложении.
- Storage 90% → push admin'у «Скоро закончится место, рассмотрите upgrade».

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 для Album UI.
- Mock BlobStorage с симуляцией upload failures (chunk retry).
- E2E с реальным staging R2 bucket для acceptance.

CONSTITUTION GATES:
- Rule 1 (domain isolation): Album, Photo — pure domain.
- Rule 5 (wire format): MediaBlob schemaVersion=1.
- Rule 9 (privacy): per-photo visibility encrypted per recipient.

EFFORT: Large (~3 months).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Album UI: timeline + search + captions
- [ ] #2 Anniversaries / memories surfacing
- [ ] #3 Storage budget per family group
- [ ] #4 Admin загрузил 50 фото + 10 видео → upload завершился за <5 минут, все доступны в timeline
- [ ] #5 Бабушка тапнула плитку 'Альбом' в launcher → видит timeline с группировкой по дате
- [ ] #6 Свайп по timeline — плавно, без задержек (thumbnails кэшируются)
- [ ] #7 Сегодня годовщина → автоматически появляется блок 'Фото из этого дня прошлых лет'
- [ ] #8 Admin тегировал фото 'дача' → search по 'дача' возвращает все эти фото
- [ ] #9 Admin ограничил фото 'только для жены' → внук не видит это фото в своём приложении
- [ ] #10 Storage 90% заполнено → push admin'у 'Скоро закончится место, рассмотрите upgrade'
<!-- AC:END -->
