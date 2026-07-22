---
id: TASK-150
title: Crypto MLS-core zone SoT (crypto-mls.md) — research-grounded, ecs-complete
status: Done
updated_date: '2026-07-22 00:00'
assignee: []
created_date: '2026-07-22 13:40'
labels:
  - architecture
  - crypto
  - docs
milestone: m-1
dependencies: []
priority: high
ordinal: 150000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

crypto.md отправлял «MLS core → STOP, читай Decision-блок TASK-124/104» — это truth-in-task анти-паттерн (правда в таске, не в файле). Эта задача доводит MLS-зону до ecs-полноты: самодостаточный `crypto-mls.md`, **основанный на исследованной архитектуре целых проектов** (openmls crates + traits, Wire core-crypto module structure, matrix-sdk FFI, RFC 9750 + Signal), а НЕ на наших прошлых внутренних решениях. Наши продуктовые ограничения (zero-knowledge KeyPackage-сервер, family-пресеты) — сверху и помечены как наши.

Только документы, ноль кода.

## Зачем

Чтобы MLS-зона читалась из файла (import openmls / copy Wire structure / write glue), а не собиралась заново из тасков. Плюс — применить подтверждённый принцип «арх-паки на research'е готовых проектов, не на наших решениях».

## Что входит технически (для AI-агента)

- `docs/architecture/crypto-mls.md` — 8-слойная import/copy-design/write карта; openmls provider+StorageProvider+MlsGroup+KeyPackage контракт; Wire core-crypto структура (GPL, clean-room); UniFFI/cargo-ndk; SQLCipher StorageProvider; KeyPackage server (RFC 9750 + Signal drain-defense); инварианты ML1-ML6; наши constraints (rule 13 opaque, family presets) помечены; exit ramps (mls-rs, manual JNI, own Rust server).
- crypto.md zone map + routing: «STOP read task» → crypto-mls.md.
- crypto skill reading map + not-built section → crypto-mls.md.
- messaging-delivery.md KeyPackage → ссылка на crypto-mls.md.
- INDEX.md регистрация.

## Состояние

**In Progress.** Research по внутренней архитектуре MLS-ядра целых проектов проведён; crypto-mls.md написан на нём. Ноль кода.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] crypto-mls.md написан самодостаточно, grounded в исследованной архитектуре целых проектов (openmls/Wire/matrix/RFC/Signal с URL), НЕ recitation TASK-104/58
- [x] #2 [hand] Import/copy-design/write layer map (8 слоёв) с лицензиями (openmls MIT import, Wire GPL copy-only)
- [x] #3 [hand] Наши product-constraints (rule 13 opaque KeyPackage server, family preset numbers) помечены как НАШИ поверх research-архитектуры
- [x] #4 [hand] crypto.md / crypto skill / messaging-delivery / INDEX перепаяны на crypto-mls.md; «STOP read task» убран
- [x] #5 [hand] Zero production-code
<!-- AC:END -->
