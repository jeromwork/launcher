---
id: TASK-13
title: VersionedConfigViewer + Layout Editor
status: Draft
assignee: []
created_date: '2026-06-23 05:37'
updated_date: '2026-06-23 06:16'
labels:
  - phase-2
  - s-spec
  - s-8
  - admin
  - editor
milestone: m-1
dependencies:
  - TASK-8
priority: high
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Визуальный редактор раскладки плиток в Admin App + история всех изменений конфигурации, чтобы можно было «откатить назад» если что-то сломалось.

**Что происходит по шагам (admin редактирует):**
1. Admin в Admin App открывает paired-устройство бабушки.
2. Видит визуальную сетку плиток (как у бабушки на экране).
3. Перетаскивает плитку «Петя» из позиции 1 в позицию 3 (drag-and-drop).
4. Меняет иконку «Магазин» — выбирает другую из библиотеки.
5. Сохраняет → конфигурация шифруется, отправляется push (TASK-5) бабушке.
6. У бабушки экран обновляется за <10 секунд.

**Что происходит при ошибке:**
1. Admin случайно удалил все контакты, сохранил.
2. Бабушка получила обновлённый (пустой) экран.
3. Admin открывает «История версий» → видит список последних 10 версий с датами.
4. Выбирает версию «3 часа назад» → нажимает «Откатить».
5. Эта версия становится текущей → бабушке отправляется снова → старые контакты вернулись.

**Что хранится в истории:**
- Последние 10 версий (configurable).
- Хранятся на устройстве admin'а + в облаке (зашифровано).
- Старые версии автоматически удаляются (client-side housekeeping per CLAUDE.md rule 8).

## Зачем

Без визуального редактора admin не может комфортно настраивать. Без истории — один неосторожный клик может «убить» конфигурацию без возможности восстановить.

## Что входит технически (для AI-агента)

- Layout editor: drag-and-drop tiles на сетке 2×3 / 3×4 / 4×5.
- Contact list editor: add/remove/reorder + photo upload (пересечение с TASK-11 S-5).
- Version history viewer: последние 10 версий, diff между ними.
- Rollback button: восстанавливает выбранную version.
- Push update через TASK-5 F-5c после save.
- Client-side housekeeping версий (per rule 8 + inline TODO про server-side cleanup).

## Состояние

**Planned.** Зависит от TASK-8 (Admin App).

---

## Готовый промт для `/speckit.specify`

```
Реализуй S-8: VersionedConfigViewer + Layout Editor.

ЧТО СТРОИМ:
Admin tool — visual config editor с version history. Drag-and-drop layout editor (tiles на 2×3 / 3×4 / 4×5 grid) + contact list editor (с photo upload через TASK-11) + version history viewer (last 10 versions, diff) + rollback. Save → push через TASK-5 → Managed применяет за <10 сек. Client-side version housekeeping per rule 8.

ЗАЧЕМ:
Без визуального редактора admin не может комфортно настраивать. Без history — один неосторожный save без возможности восстановить.

SCOPE ВКЛЮЧАЕТ:
- Layout editor: drag-and-drop tiles, grid sizes 2×3 / 3×4 / 4×5.
- Contact list editor: add/remove/reorder, photo upload (пересечение с TASK-11).
- Version history viewer: last 10 versions, side-by-side diff.
- Rollback button: восстанавливает выбранную version в текущую.
- Push update через TASK-5 F-5c после save.
- Client-side cleanup: keep last 10, delete older.
- Inline TODO про SRV-CONFIG-002 (server-side cleanup migration target).

SCOPE НЕ ВКЛЮЧАЕТ:
- Multi-admin merge conflicts (TASK-46 L-13 shared admin contact book).
- Real-time co-editing (post-MVP).
- Export config as file (TASK-18 P-3 в Phase 3).

DEPENDENCIES:
- TASK-8 (S-2 Admin App).
- TASK-11 (S-5 photos) для photo upload integration.

ACCEPTANCE CRITERIA:
- Admin перетащил плитку с позиции 1 на позицию 3 → save → бабушка увидела обновление за <10 секунд.
- Admin удалил все контакты случайно → открыл version history → откатил версию 3 часа назад → восстановлено.
- В history показано минимум 10 последних версий с датами.
- После 11-й save самая старая версия автоматически удалена.
- Diff между двумя версиями показывает добавленные / удалённые / изменённые плитки и контакты.
- Save на medium-load (5MB config с 10 photos) — не вешает UI, прогресс-индикатор виден.

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 для Admin App.
- E2E с двумя эмуляторами (Admin + Managed) — save → check propagation.
- Unit-tests version housekeeping (push 15 раз → остаётся 10).

CONSTITUTION GATES:
- Rule 1 (domain isolation): ConfigVersion — pure domain.
- Rule 5 (wire format): ConfigDocument schemaVersion=1 (uses existing).
- Rule 8 (server migration): client-side housekeeping с inline TODO(server-roadmap) про SRV-CONFIG-002.

EFFORT: Large (~3 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Contact list editor: add/remove/reorder + photo upload (intersection с S-5)
- [ ] #2 Version history viewer: last 10 versions, diff между ними
- [ ] #3 Rollback button: восстанавливает выбранную version
- [ ] #4 Push update через F-5c после save
- [ ] #5 Admin перетащил плитку с позиции 1 на позицию 3 → save → бабушка увидела обновление за <10 секунд
- [ ] #6 Admin удалил все контакты случайно → открыл version history → откатил версию 3 часа назад → восстановлено
- [ ] #7 В history показано минимум 10 последних версий с датами
- [ ] #8 После 11-й save самая старая версия автоматически удалена
- [ ] #9 Diff между двумя версиями показывает добавленные / удалённые / изменённые плитки
- [ ] #10 Save на medium-load (5MB config с 10 photos) не вешает UI, виден прогресс-индикатор
<!-- AC:END -->
