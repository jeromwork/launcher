---
id: TASK-18
title: Preset Authoring + Sharing
status: Draft
assignee: []
created_date: '2026-06-23 05:38'
updated_date: '2026-07-17 04:06'
labels:
  - phase-5
  - p-spec
  - p-3
  - preset-authoring
  - sharing
  - config-source
milestone: m-4
dependencies:
  - TASK-16
  - TASK-17
priority: low
ordinal: 18000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Admin может создать свою собственную раскладку (preset) и поделиться ей с другими: экспортировать в файл и отправить другу через мессенджер, или импортировать чей-то файл.

**Что происходит по шагам (создание собственного preset):**
1. Admin в Admin App открывает редактор (TASK-13 S-8).
2. Делает копию готового preset «9 плиток с календарём».
3. Меняет: убирает 2 плитки, заменяет иконки.
4. Сохраняет под именем «Бабушка-2024-обновление».
5. У admin'а в библиотеке появляется именованный preset.
6. Может применить его к любому из своих устройств (5 named configs max в облаке).

**Что происходит при шеринге:**
1. Admin тапает «Поделиться preset».
2. Выбирает «Сохранить как файл» → получает `.json` файл.
3. Отправляет файл другу через WhatsApp / email / любой обмен.
4. Друг открывает файл в Admin App → preset импортируется в его библиотеку.
5. Друг может применить к своему устройству бабушки.

**Что в preset НЕТ (приватность):**
- Никаких реальных контактов с телефонами / email — только плейсхолдеры.
- Никаких фото — только иконки.
- Полностью обезличенный шаблон (per CLAUDE.md rule 9).

## Зачем

- Без collaborative авторинга admin'ы изобретают колесо каждый раз («какие плитки лучше для бабушки 75 лет?»).
- С collaborative авторингом — лучшие preset'ы распространяются органически между семьями.
- Foundation для будущего marketplace (TASK-35 L-2 в Phase 5).

## Что входит технически (для AI-агента)

- Authoring UI: создать preset на основе существующего.
- Export preset как `.json` файл + share через Android share intent.
- Import `.json` или принять share intent.
- 5 named configs limit per cloud namespace (Firestore document size limits).
- `ImportFromFileConfigSource` + `ShareIntentConfigSource` adapters в core/wizard/.
- Переписать spec 014 на v2 schema (закрывает divergence note из Phase 2).
- Inline TODO про marketplace adapter (TASK-35 L-2 trigger).

## Состояние

**Planned.** Зависит от TASK-16 (P-1 v2 schema) + TASK-17 (P-2 Android steps).

---

## Готовый промт для `/speckit.specify`

```
Реализуй P-3: Preset Authoring + Sharing.

ЧТО СТРОИМ:
Admin authoring UI: создание / экспорт / импорт preset'ов. ConfigSource adapter pattern расширяется: BundledConfigSource (existing) + ImportFromFileConfigSource + ShareIntentConfigSource. 5 named configs limit per cloud namespace. Spec 014 (named configs) переписывается на v2 schema.

ЗАЧЕМ:
Collaborative authoring между семьями. Foundation для будущего marketplace (TASK-35 L-2).

SCOPE ВКЛЮЧАЕТ:
- Authoring UI в Admin App: create preset from existing, rename, delete, list.
- Export: serialize preset → .json → Android share intent.
- Import: receive share intent OR file picker → deserialize → add to library.
- 5 named configs limit per cloud namespace (Firestore document size).
- ImportFromFileConfigSource adapter в core/wizard/.
- ShareIntentConfigSource adapter в core/wizard/.
- Rewrite spec 014 на v2 schema.
- Inline TODO(marketplace): MarketplaceConfigSource adapter — TASK-35 L-2 trigger.

SCOPE НЕ ВКЛЮЧАЕТ:
- Marketplace UI / curation / ratings (TASK-35 L-2 в Phase 5).
- Cross-platform copy (TASK-20 P-5).
- Identity-bound data в preset'е (запрещено per rule 9 — обезличенные только).

DEPENDENCIES:
- TASK-16 (P-1 schema v2).
- TASK-17 (P-2 Android steps как примеры для preset).

ACCEPTANCE CRITERIA:
- Admin сделал копию '9 плиток' → переименовал → сохранил → видит в библиотеке как новый preset.
- Тапнул «Поделиться» → получил .json файл → отправил другу через WhatsApp.
- Друг открыл .json в Admin App → preset импортирован в его библиотеку.
- Друг применил импортированный preset к своему устройству.
- В preset нет реальных контактов / фото — только плейсхолдеры (manual проверка содержимого .json).
- Попытка сохранить 6-й named config → понятное сообщение «лимит 5, удалите старый».

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 для Admin App.
- Manual file share через intent emulator.
- Unit-tests serialization/deserialization round-trip.
- Privacy test: assert preset не содержит phone / email / photo blob refs.

CONSTITUTION GATES:
- Rule 1 (domain isolation): ConfigSource — port.
- Rule 5 (wire format): preset .json со schemaVersion=2.
- Rule 9 (shareability): preset = обезличенный portable artefact с roundtrip + cross-device test.

EFFORT: Large (~2-3 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Export preset как .json file + share через Android share intent
- [ ] #2 Import .json или принять share intent
- [ ] #3 5 named configs limit per cloud namespace
- [ ] #4 ImportFromFileConfigSource + ShareIntentConfigSource adapters
- [ ] #5 Admin сделал копию '9 плиток' → переименовал → сохранил → видит новый preset в библиотеке
- [ ] #6 Тапнул 'Поделиться' → получил .json файл → отправил другу через WhatsApp
- [ ] #7 Друг открыл .json в Admin App → preset импортирован в его библиотеку
- [ ] #8 Друг применил импортированный preset к своему устройству
- [ ] #9 В preset нет реальных контактов / фото — только плейсхолдеры (проверка содержимого .json)
- [ ] #10 Попытка сохранить 6-й named config → понятное сообщение 'лимит 5, удалите старый'
<!-- AC:END -->
