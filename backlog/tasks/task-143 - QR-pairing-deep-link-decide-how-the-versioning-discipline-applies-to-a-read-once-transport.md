---
id: TASK-143
title: >-
  QR pairing deep link: decide how the versioning discipline applies to a
  read-once transport
status: Done
assignee: []
created_date: '2026-07-20 13:37'
updated_date: '2026-07-21'
labels:
  - wire-format
  - pairing
  - phase-2
milestone: m-2
dependencies:
  - TASK-138
priority: medium
ordinal: 143000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

При инвентаризации TASK-138 этот формат не нашли, а он есть: **QR-код спаривания**. Устройство показывает ссылку вида `launcher://pair?token=A3KX9B&v=1`, второе устройство её сканирует. Это данные, уходящие с устройства на другое устройство, то есть полноценный формат провода — и версия у него уже есть, `v=1`, причём читатель по ней **отказывается**: `v=2` даёт `UnsupportedVersion` и подсказку «обнови приложение».

Вопрос не в том, версионировать ли его — он уже версионирован. Вопрос в том, **какую форму дисциплины к нему применить**, потому что дословное применение упирается в две вещи.

**Первая — цена в пикселях.** Три поля точечными строками добавляют к ссылке около 40 символов. QR-код от этого становится плотнее: больше модулей, мельче ячейки. Сканирует его пожилой человек, с рук, при комнатном освещении, возможно в очках. Это прямой удар по тому самому сценарию, ради которого продукт существует.

**Вторая — половина полей бессмысленна.** Три поля нужны, чтобы читатель различал «не могу прочитать» и «могу прочитать, но испорчу, если запишу обратно». QR-ссылку **никто не записывает обратно** — её прочитали один раз и выбросили. Состояние «только чтение» для неё не существует, а значит `minWriterVersion` описывает то, чего не бывает.

## Зачем

Оставить как есть — значит держать единственный формат провода вне дисциплины и получить исключение, которое строгая проверка из TASK-142 либо завалит, либо будет вынуждена молча игнорировать. Оба исхода плохие: первый ломает сборку на ровном месте, второй проделывает в проверке дыру, через которую потом проедет что угодно.

## Что входит технически (для AI-агента)

**Место**: `QrDeepLinkParser.kt:24` (`SUPPORTED_VERSION: Int = 1`, гейт на равенство в строке 62), генерация — `QrDisplayScreen.kt:57`, контракт `contracts/qr-deeplink.md`.

**Варианты**:

1. **Одно поле `v` как точечная строка, трактуемое как `minReaderVersion`.** Гейт меняется с равенства на «моя версия не ниже требуемой». Цена в размере QR — три символа. Требует записи в `wire-format.md`, что для read-once транспорта достаточно одного поля, с обоснованием (§12: правило меняется — документ правится тем же коммитом).
2. **Три поля дословно.** Дисциплина без исключений, цена — плотность QR. Нужен замер: снять QR обоих вариантов и проверить сканирование на реальном устройстве в неидеальных условиях.
3. **Версию из ссылки убрать совсем**, оставив её внутри документа спаривания, который лежит на сервере и подтягивается по токену. Ссылка становится чистым указателем. Самый чистый вариант, но меняет поток спаривания — это уже не про версии.

**Замер обязателен перед выбором** между 1 и 2: «QR станет плотнее» — предположение, пока не измерено на устройстве.

**Гейт на равенство — отдельный дефект**, он есть при любом варианте. Сейчас `v=2` отвергается, даже если ничего ломающего во второй версии нет; правильное поведение — отказ только когда документ требует читателя новее нашего (§3).

## Состояние

**Draft.** Найдено 2026-07-20 при закрытии TASK-138: конвертация эталонных QR-фикстур обнажила, что формат ссылки в инвентаризацию не попал. В TASK-138 намеренно не тронут — выбор формы имеет цену для пользователя и требует решения владельца, а не решения агента (CLAUDE.md rule 3).

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] Плотность QR замерена аналитически: вариант 1 (`&v=1.0`) не меняет версию символа QR — та же V3, 29×29 модулей (вариант 2 поднял бы до V4–V6). Физический двух-девайсный скан делегирован → **TASK-118 AC #3**.
- [x] #2 [hand] Выбран вариант 1 (одно поле = `minReaderVersion`) и записан в `wire-format.md` §3 (+ инвариант I1, §6, AI-TLDR, §11) — как признанный класс «read-once транспорт», с обоснованием на otpauth/WiFi-QR/age/EMV/CTAP.
- [x] #3 [hand] Гейт заменён с равенства на сравнение (`v <= MIN_READER_VERSION`); совместимая future-версия с unknown-параметрами больше не отвергается (тест `accepts_same_version_with_unknown_future_params`).
- [N/A] #4 [hand] Отменён владельцем 2026-07-21: pre-MVP, старых QR в поле нет — совместимость не хранится. `v=1` (целое) и ссылка без `v` теперь сознательно отвергаются (fail closed).
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->

**Decision (owner-approved 2026-07-21): read-once transports carry a single version field = `minReaderVersion`.** A format with no read-modify-write cycle (QR links, deep-link params) has no state for `minWriterVersion` (write-back never happens) or `schemaVersion` (a diagnostic no reader acts on) to describe, so it is complete with one field — and carries just the one where payload size is a cost. Recorded in `wire-format.md` §3 with invariant I1 amended; the QR pairing link is the first format under the rule. Generalised on *"no write-back"*, not on *"QR/scanning"* — push bodies already carrying three fields at no size cost are not forced to shed them (MVA).

**Implemented** in `QrDeepLinkParser` (dotted `v` read as `minReaderVersion`, gate compares instead of equates, `buildPairingDeepLink` as the one grammar source), `QrDisplayScreen` (builds via that source, no literal), tests, and the `qr-deeplink.md` contract. `fitnessCheck` + core unit tests green.

**Note — strict fitness net blind spot:** the TASK-142 checks key on `@Serializable` + `WireVersionHeader` + JSON key names, so the QR URI-param format is invisible to them (documented in `wire-format.md` §11). Its discipline is held by its roundtrip test + the no-literal builder, not the net.

**Delegated:** physical two-device scan → TASK-118 AC #3 (cross-device manual gates), per the standard hardware-gate collector pattern.

<!-- SECTION:FINAL_SUMMARY:END -->
