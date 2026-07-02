# Crypto Architecture — Current Status (Start Here)

**Purpose**: короткий "start here" файл для новой сессии AI или нового collaborator'а работающих над крипто-архитектурой проекта. Обновляется вручную после mentor-сессий.

**Last updated**: 2026-07-02.

---

## TL;DR для fresh session

1. **Модель работы**: [CLAUDE.md rule 11](../../CLAUDE.md) — architectural decisions живут как backlog-tasks в статусах `Discussion` → `Draft` → `Done` с immutable `### Decision (English, immutable) 🔒` блоками. Cross-task references — только через `dependencies:`.
2. **Skills**: `mentor` (для Discussion sessions), `backlog-task-format` (формат task'ов), `procedure-decision-drift-check` (walk dependencies).
3. **Пилотная миграция от legacy SoT-файлов запушена**: см. commit `1ed9c41` (bootstrap) + последующие. Ветка `crypto-backlog-migration-pilot`.
4. **Legacy files с deprecation banner'ами**:
   - [`crypto-mentor-overview.md`](crypto-mentor-overview.md) — 1994 строк, incremental migration on touch.
   - [`crypto-open-questions.md`](crypto-open-questions.md) — register закрытых Q-NN; priority queue актуален.
   - [`crypto-topics-handoff.md`](crypto-topics-handoff.md) — historical, may reference retired patterns.
5. **Не создавать новые `docs/dev/*-mentor-overview.md` файлы**. Все architectural decisions — decision-task'и в backlog.

---

## Recently decided (session 2026-07-02)

| Task | Title | Status | Что решено |
|---|---|---|---|
| [TASK-100](../../backlog/tasks/task-100%20-%20Decision-History-backup-strategy-for-MVP.md) | History backup strategy for MVP | Done | MVP Signal-style (нет истории после recovery); Phase-3+ WhatsApp-style opt-in backup. |
| [TASK-101](../../backlog/tasks/task-101%20-%20Decision-Peer-confirmation-on-recovery.md) | Peer confirmation on recovery | Draft | Chrome-model auto-add + post-facto notification. Multi-device как first-class. |
| [TASK-102](../../backlog/tasks/task-102%20-%20Decision-Revoke-policy.md) | MLS revoke policy | Draft | Three-tier language (owner/admin/other), MVP flat + admin, identity-level UI, no blacklist. |
| [TASK-103](../../backlog/tasks/task-103%20-%20Decision-Remote-app-lock-for-stolen-device.md) | Remote app lock for stolen device | Draft | Full logout + Keystore wipe = crypto defense (не UX). 5 preset fields. |

**Read Decision blocks (English) для machine-readable контракта** — downstream tasks должны иметь `dependencies: [TASK-N]` для этих decisions.

---

## Currently in Discussion

**Нет активных Discussion-tasks на 2026-07-02.** Все pilot тасок в Draft/Done.

Для запуска следующей mentor-сессии — создать новый task в статусе Discussion (см. Priority queue ниже).

---

## Priority queue — next candidates

**High** (recommended immediate next):

1. **KeyPackage rate limit** (создать `TASK-104` в Discussion). Server-side max 5 KeyPackages/hour/identity. Разгружает TASK-101 + TASK-103 attacker mitigation + TASK-67 abuse prevention. Small scope, ~1 short mentor-session.
2. **Cloudflare Durable Objects concrete design** (был Q-14). Блокирует TASK-67 implementation. Concrete design для quota counters + rate limits.
3. **Abuse response mechanism** (был Q-17). Legal minimum для user-reported content abuse. Блокирует TASK-11, TASK-28.
4. **Cross-platform IdentityVault** (был Q-08). iOS App Groups / Huawei ContentProvider / Android TV / Google TV. Блокирует TASK-3, 25, 26, 29.
5. **Huawei без GMS push fallback** (был Q-13). HMS Push Kit / MQTT / WebSocket. Блокирует TASK-58 Huawei smoke gates.

**Medium** — см. [crypto-open-questions.md § Priority queue](crypto-open-questions.md#priority-queue-updated-2026-07-02-после-task-100103-closure).

**Full open-questions register** — [crypto-open-questions.md](crypto-open-questions.md).

---

## Continuation prompts (для новой сессии)

**Если владелец продолжает крипто-работу с fresh session'а**, полезные starting prompts:

- **«Продолжай следующую тему из priority queue»** — AI открывает `crypto-status.md`, берёт top candidate из «High» списка, создаёт новый TASK-N в Discussion, запускает `mentor` skill.
- **«Открой [TASK-N]»** — AI читает task file, определяет статус (Discussion → продолжить mentor session; Draft → готова к /speckit.specify; Done → informational).
- **«Проверь drift»** — AI запускает `procedure-decision-drift-check` skill — walk dependencies, flag downstream tasks с superseded upstream decisions.
- **«Что уже решено?»** — AI читает эту таблицу выше + git log за последнюю неделю.

**Anti-patterns для fresh session'а** (что делать НЕ надо):

- ❌ Читать `crypto-mentor-overview.md` как authoritative SoT — там deprecation banner, legacy content.
- ❌ Считать что priority queue из `crypto-open-questions.md` актуальный только по Q-NN — некоторые Q мигрированы в TASK-100+.
- ❌ Создавать `docs/dev/*-mentor-overview.md` файлы для новых доменов (backend, UX, i18n) — violates rule 11 universality.

---

## Downstream tasks awaiting Decision integration

Эти feature-tasks должны добавить соответствующие `dependencies: [TASK-100+]` при следующем touch'е (per pilot sweep + Session decisions):

- **TASK-6** (Root Key Hierarchy) → dependencies на TASK-100 (history backup), TASK-101 (recovery flow), TASK-102 (revoke policy device), TASK-103 (logout mechanism).
- **TASK-25** (Multi-app cohabitation) → dependencies на TASK-101 (device inventory), TASK-102 (revoke), TASK-103 (per-app lock).
- **TASK-27** (Messenger Jitsi) → dependencies на TASK-100 (history backup future).
- **TASK-28** (Full family album) → dependencies на TASK-100.
- **TASK-32** (Audit log) → dependencies на TASK-100, TASK-102, TASK-103 (audit events).
- **TASK-40** (Multi-device) → **unparked by TASK-101 Decision** — multi-device теперь first-class. Update crypto-alignment: `parked` → `aligned` при следующем touch.
- **TASK-42** (Family group encryption) → dependencies на TASK-102 (revoke), TASK-58 (MLS choice research).
- **TASK-46** (Shared admin book) → dependencies на TASK-102.
- **TASK-58** (MLS research) → должен produce financials по MLS choice, потом закрыт.
- **TASK-67** (Pairing feature) → dependencies на несколько (TASK-101, 102, 103) + high-priority open questions.
- **TASK-70** (Profile sync) → dependencies на TASK-100.
- **TASK-16** (Preset Schema v2) → **должен интегрировать** новые preset fields из TASK-103 (`deviceLock` namespace).

Integration происходит **на touch каждого таск'а**, не bulk update. Rule 11 «migration by touch».

---

## Skills reminder (для новой сессии)

Для крипто work'а обычно используются:

- **`mentor`** — invoke внутри Discussion-задачи для structured session.
- **`backlog-task-format`** — формат task-файла + Discussion + Decision block.
- **`procedure-decision-drift-check`** — walk dependencies, flag downstream drift.

Все остальные skills (speckit-*, checklist-*, etc.) — стандартные workflow'ы, не крипто-специфичные.

---

## Ветка / commit trail

- **Branch**: `crypto-backlog-migration-pilot`.
- **Bootstrap commit**: `1ed9c41` (add Discussion status, decision-task frontmatter, retire alignment-sweep).
- **Session commits** (chronological): `76e19d1`, `fc06526`, `2e29a3f`, `2af8e5d`, `e9877ed`, `ddf236a`, `ba1b57a`.
- **PR** (когда готов to merge into main): https://github.com/jeromwork/launcher/pull/new/crypto-backlog-migration-pilot.

---

## Обновление этого файла

Обновляй **вручную** после каждой mentor-сессии или закрытия decision-task'а. Не auto-generated. Раздел «Recently decided» — top N последних (5-10), старее — переносится в footer «Historical decisions» (создать когда список расширится).
