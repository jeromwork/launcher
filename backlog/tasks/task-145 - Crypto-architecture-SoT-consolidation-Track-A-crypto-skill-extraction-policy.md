---
id: TASK-145
title: >-
  Crypto architecture SoT consolidation (Track A) + crypto skill +
  extraction-policy
status: In Progress
assignee: []
created_date: '2026-07-21 14:04'
labels:
  - crypto
  - architecture
  - docs
milestone: m-1
dependencies: []
priority: high
ordinal: 145000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Крипта в проекте описана в одном файле `docs/architecture/crypto.md` на 1302 строки, куда свалено всё сразу: и построенное, и только спланированное, и туториалы «для новичка», и roadmap'ы, и устаревшие пути. Из-за этого каждую сессию приходится заново продумывать, «как у нас устроена крипта» — файл не работает как источник правды.

Мы уже решили эту проблему для конфигурационной модели (ECS): один выверенный файл `ecs.md` + skill, который заставляет агента всегда в него упираться. После этого ECS-задачи пошли без повторных обсуждений. **Эта задача повторяет тот же приём для крипты.**

**Что происходит по шагам:**
1. `crypto.md` ужимается до короткого файла-«зонтика» (~150 строк): краткая выжимка для агента (AI-TLDR) + карта зон + маршрутизация «какой вопрос → какой файл».
2. Построенная часть крипты выносится в три чистых архитектурных файла: примитивы (шифрование байтов), иерархия ключей (какой ключ зачем + восстановление), pairing (как устройства знакомятся).
3. Заводится отдельный файл про вынос крипты/версионирования в общий модуль для семейства приложений (`extraction-policy.md`).
4. Создаётся skill `crypto` — тонкий роутер, как у ECS.
5. Дубли той же информации в других файлах (`agent-context.md`, `key-hierarchy.md`) заменяются ссылками — чтобы правда жила в одном месте.

Это работа **только с документами** — ни строчки кода не меняется.

## Зачем

Убрать постоянное «перепридумывание» архитектуры крипты каждую сессию. После этой задачи любой вопрос про крипту решается через skill → нужный файл, без повторного research'а.

## Что входит технически (для AI-агента)

**Полная инструкция — `docs/dev/handoff-crypto-sot-consolidation.md`** (самодостаточная: весь research, все решения владельца, все разрешённые противоречия). Исполнять §0→§9 без повторного обдумывания.

Кратко:
- Новые файлы: `docs/architecture/crypto-primitives.md`, `crypto-key-hierarchy.md`, `crypto-pairing.md`, `extraction-policy.md`, `docs/dev/crypto-prerelease.md`.
- `crypto.md` → умбрелла-роутер с картой зон (built / designed-not-built + ссылка на владеющую Decision-задачу).
- Новый skill `.claude/skills/crypto/SKILL.md` (по образцу `ecs`).
- Дедуп: `agent-context.md` крипто-секции, `key-hierarchy.md`, регистрация в `INDEX.md`.
- Границы зон выверены по промышленным стандартам (Tink / RFC 9420+9750 / Signal / NIST 800-57 / libsodium / Wire) и реальному коду.
- Follow-up задачи (создать как Draft, не начинать): вынос `family.pairing` в свой модуль; единый дом крипто-адаптеров.

## Состояние

**In Progress.** Handoff готов, research завершён. Реализация начата 2026-07-21.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] crypto.md ужат до умбреллы (~150 строк): AI-TLDR + routing + карта зон (built / designed-not-built per zone); никакой «свалки нерешённых задач»; туториалы/roadmap/impl-sequence удалены или переехали
- [x] #2 [hand] Созданы crypto-primitives.md, crypto-key-hierarchy.md, crypto-pairing.md — каждый по рецепту эталона (precedence, AI-TLDR, инварианты, Rejected, industry grounding с URL)
- [x] #3 [hand] Создан extraction-policy.md, ссылки из crypto.md и wire-format.md; ECS явно исключён
- [x] #4 [hand] Создан skill crypto (thin router по образцу ecs), триггеры покрывают крипто-термины; для designed-not-built зон skill велит STOP → Decision task
- [x] #5 [hand] Дедуп: agent-context.md крипто-секции → ссылки; key-hierarchy.md → dev-справка со ссылкой; INDEX.md обновлён
- [x] #6 [hand] Ни одного упоминания libopenmls_ffi.so, app/adapters/openmls, cryptokit. в живых (не исторических) доках
- [x] #7 [hand] Заведены follow-up tasks T1 (вынос family.pairing), T2 (единый дом адаптеров) как Draft
- [x] #8 [hand] Zero production-code changes: git diff не содержит .kt / .rs / .gradle.kts
<!-- AC:END -->
