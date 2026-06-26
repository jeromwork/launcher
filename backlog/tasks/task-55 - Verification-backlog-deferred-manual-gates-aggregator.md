---
id: TASK-55
title: 'Verification backlog: deferred manual gates aggregator'
status: Draft
assignee: []
created_date: '2026-06-26 04:23'
labels:
  - verification
  - manual-gates
  - aggregator
milestone: m-1
dependencies: []
references:
  - specs/task-7-simple-launcher-first-run/
priority: medium
ordinal: 55000
---

## Что это простыми словами

Сборник **отложенных ручных проверок**, которые невозможно прогнать прямо сейчас — либо нет железа (Samsung, Huawei, второй девайс), либо блокирует другая задача (TASK-51 libsodium, TASK-52 HomeActivity hang), либо нужен эмулятор от владельца.

**Зачем не оставлять их в исходных task'ах:** task с merged PR и закрытым scope'ом кода зависает в `Verification` месяцами, ждёт совпадения железа и времени владельца. Это мешает Kanban'у показать «фича готова, код смержен». Гораздо честнее: закрыть исходный task в Done, а все физические гейты переложить сюда — в один сборник, где видна вся очередь верификаций по всему backlog.

**Когда гейт переходит сюда:** код merged, owner подтвердил «больше дописывать нечего», но физический прогон откладывается из-за внешнего блокера.

**Когда гейт закрывается:** соответствующий внешний блокер (TASK-N) перешёл в Done **И** прогон выполнен на железе.

## Зачем

Без этого aggregator'а получаем ровно ту ситуацию, что и до сегодняшнего разговора: TASK-7 висит в `Verification` с PR merged 4 дня назад, потому что блокирующий его TASK-52 ещё не сделан. Это создаёт ложное впечатление «TASK-7 не доделан», хотя по факту весь код merged и работает.

Идея: **Verification — про immediately runnable manual gates** (например emulator smoke за 15 минут). Если гейт ждёт другую task'у — это уже не verification, это **delegated verification** → отдельный сборник.

## Источники гейтов (по task'ам)

### Из TASK-7 (Simple Launcher first-run)

Все блокированы внешними task'ами или отсутствием железа на момент 2026-06-26:

| AC# (из TASK-7) | Описание | Блокер |
|---|---|---|
| #2 | 3 mandatory шага → HomeActivity рендерит композицию ≤ 1 сек | **TASK-52** (HomeActivity hang) |
| #4 | System locale change → app остаётся на выбранном языке после restart | **TASK-52** (cannot complete wizard E2E) |
| #5 | Pairing с admin device в wizard'е → LinkRegistry.activate() | **TASK-8** (scope moved per amendment 1.10) + **TASK-51** (libsodium arm64) |
| #6 | Перезагрузка → wizard не повторяется, HomeActivity открывается с конфигом | **TASK-52** |
| #7 | Local emulator gates: T060 senior-safe, T062 locale persistence | Ждёт owner kicks эмулятор |
| #8 (частично) | T038 locale persist, T058 PendingChecklist UI, T064 Samsung One UI, T065 MIUI battery, T066 2-device pairing | **TASK-52** (general) + отсутствие Samsung/второго устройства |

## Что входит технически

Не код. Контентная задача:

- Поддерживать актуальный список deferred gates по всему backlog'у.
- При закрытии любого blocker-task'а (TASK-51, TASK-52, и т.д.) — пробежаться по списку и пометить соответствующие гейты как runnable.
- Когда runnable гейт прогнан владельцем — `[x]` здесь.
- Когда aggregator переполнен (≥ 30 строк) — открыть TASK-NN-aggregator-2 и перевести этот в Done.

## Состояние

Draft. Создан 2026-06-26 при закрытии TASK-7 в Done.

## Acceptance Criteria
<!-- AC:BEGIN -->
### Из TASK-7
- [ ] #1 [delegated:TASK-52] AC#2 TASK-7 — 3 mandatory шага → HomeActivity рендерит композицию (classic-6 поверх 3x4-classic) ≤ 1 сек после wizard exit'а
- [ ] #2 [delegated:TASK-52] AC#4 TASK-7 — System locale change (Android Settings → Languages → English) после wizard'а с languageOverride: ru → app остаётся на русском после restart'а
- [ ] #3 [delegated:TASK-8+TASK-51] AC#5 TASK-7 — Pairing с admin device в wizard'е → LinkRegistry.activate() записал link (после возврата pair-admin как SystemSetting step в TASK-8)
- [ ] #4 [delegated:TASK-52] AC#6 TASK-7 — Перезагрузил устройство → wizard не повторяется; HomeActivity открывается с применённой композицией
- [ ] #5 [deferred-local-emulator] AC#7 TASK-7 — T060 senior-safe walkthrough на эмуляторе pixel_5_api_34
- [ ] #6 [deferred-local-emulator] AC#7 TASK-7 — T062 locale persistence на эмуляторе
- [ ] #7 [delegated:TASK-52] AC#8 TASK-7 — T038 locale persist на физическом устройстве
- [ ] #8 [delegated:TASK-52] AC#8 TASK-7 — T058 PendingChecklist UI на физическом устройстве
- [ ] #9 [deferred-physical-device] AC#8 TASK-7 — T064 Samsung One UI прогон (нет Samsung устройства)
- [ ] #10 [deferred-physical-device] AC#8 TASK-7 — T065 MIUI battery quirks (Xiaomi 11T доступен — runnable, ожидает разблокировки TASK-52)
- [ ] #11 [deferred-physical-device] AC#8 TASK-7 — T066 2-device pairing (нет второго устройства с admin app stub из TASK-8)
<!-- AC:END -->
