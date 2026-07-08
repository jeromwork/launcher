---
id: TASK-100
title: 'Decision: History backup strategy for MVP'
status: Done
assignee: []
created_date: '2026-07-02'
updated_date: '2026-07-02'
labels:
  - decision
  - crypto
  - recovery
  - phase-2
milestone: m-1
dependencies: []
priority: high
ordinal: 100000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Когда бабушка сменит телефон и восстановит доступ через passphrase, что она увидит на новом устройстве:
- **Профиль** (контакты, тайлы, темы) — восстановится автоматически.
- **Историю чатов и старые фото** — **нет**, они недоступны на новом устройстве.

Это принципиальное свойство MLS forward secrecy (математика протокола) — старые ключи удалены, новое устройство их не может восстановить. WhatsApp обошёл это в 2021 через отдельный E2E backup mechanism. Signal принципиально не показывает историю после recovery.

**MVP выбор: как Signal — истории нет.** Wizard явно предупредит пользователя.

**Phase-3+**: продумаем backup отдельной задачей (по аналогии с WhatsApp) когда будем строить messenger и full family album.

## Зачем

Разрешить блокирующий вопрос Q-09 — история сообщений/фото/действий после recovery. Без решения все content-storage задачи (TASK-27, 28, 32, 70) висят в неопределённости о том, нужен ли backup слой сегодня.

Article XX (Pre-MVP no-migration override) снимает обычные exit-ramp обязательства — до первых реальных пользователей ничего не нужно закладывать в фундамент.

## Что входит технически (для AI-агента)

**MVP scope (что делаем сейчас)**:
- Profile state восстанавливается через существующий MLS bucket sync + recovery envelope (см. TASK-6, TASK-66).
- Setup wizard содержит явное предупреждение о потере истории (тактическая формулировка — Q-21 или дочерний task).

**MVP exclusions (что явно НЕ делаем)**:
- Нет local encrypted DB (SQLCipher).
- Нет backup adapter (`HistoryBackup` port).
- Нет отдельного backup encryption key.
- Нет reserved HKDF slot в root_key hierarchy (Article XX — можно добавить позже без миграции).
- Нет signed timestamp binding в message wire format.

**Phase-3+ scope (deferred)**:
- WhatsApp-style pattern: local encrypted DB (SQLCipher) → periodic encrypted backup → iCloud / Google Drive / наш R2 → restore playback на новом устройстве.
- Backup encryption key: derived от passphrase (simpler) или отдельный 64-digit code (safer, WhatsApp 2021+).
- Decision о конкретной модели — отдельный task в тот момент.

## Состояние

Decided 2026-07-02. Downstream tasks (TASK-27 messenger, TASK-28 family album, TASK-32 audit log, TASK-70 profile sync) добавляют этот task в `dependencies:` при работе над своими спеками. Читают Decision block ниже.

Exit ramp записан в `docs/dev/server-roadmap.md` — пункт `HIST-BACKUP-001`.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] Decision block ниже заполнен на English, immutable
- [x] #2 [hand] Downstream tasks (TASK-27, 28, 32, 70) уведомлены о необходимости `dependencies: [TASK-100]` при своём speckit-cycle
- [x] #3 [hand] Exit ramp HIST-BACKUP-001 записан в docs/dev/server-roadmap.md (или project-backlog.md как inline TODO)
<!-- AC:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 (2026-07-02, mentor skill invoked)

#### A.1 Что за область

Историю сообщений/фото/действий после смены устройства сохранять или нет. MLS forward secrecy делает это архитектурным решением, не тривиальным «включим backup позже».

#### A.2 Карта темы

| Сущность | Где живёт сейчас | Восстанавливается? |
|---|---|---|
| **Profile state** (контакты, тайлы, layout, темы) | Firestore bucket — encrypted latest snapshot | ✅ Работает через MLS bucket sync recovery |
| **Message history** (будущий messenger TASK-27) | Только MLS application messages; forward secrecy убивает при recovery | ❌ Без отдельного backup |
| **Photo history** (family album TASK-28) | R2 encrypted blobs + указатели в MLS сообщениях | ⚠️ Blob'ы на R2, но `photo_key` утерян с MLS |
| **Audit log** (TASK-32) | Append-only bucket в MLS | ⚠️ Аналогично photo |

#### A.3 Ключевые термины

- **Forward secrecy (FS)** — свойство MLS: старые эпоха-ключи удаляются, новое устройство не может расшифровать прошлое. Дизайн-фича, не bug.
- **Signal pattern**: истории нет после recovery, явное предупреждение в UI, backup только device-to-device sync.
- **WhatsApp pattern** (с 2021): local encrypted SQLCipher DB + backup в iCloud/Google Drive, ключ backup'а — либо derived от passphrase, либо отдельный 64-digit code.

#### A.4 Уточняющие вопросы + ответы владельца

**Q1**: Обязательно ли MVP-пожилой должен увидеть после смены телефона (а) историю сообщений, (б) старые фото внуков, (в) что-то ещё? Или достаточно чтобы вернулись контакты + тайлы + текущие фото контактов (это уже работает)?

**A1**: MVP не включает messenger (Phase-3+) и full family album (Phase-3+). Достаточно Profile state. История не нужна в MVP.

**Q2**: Можно ли отложить как WhatsApp — сейчас как Signal, потом надстройка?

**A2**: Да, отложить.

**Q3 (follow-up)**: Что тогда нужно зафиксировать сегодня как exit-ramp? (Я ошибочно предлагал 3 архитектурных lock'а — HKDF slot, KeyPackage TTL, signed timestamp.)

**A3**: Ничего в код не добавлять. Article XX (Pre-MVP no-migration override) даёт полную свободу до первых реальных пользователей. HKDF strings — cheap, добавляются позже. Wire format сообщений — greenfield когда будем строить messenger. Retention policy — конфиг. **Единственная non-technical дверь — user expectation management** в setup wizard'е (обещать backup — нельзя, молчать — плохо, явно предупреждать — правильный путь).

#### B: Best path + альтернативы + adjacent concerns

**Best path**: MVP = Signal-style (истории нет после recovery). Phase-3+ = продумываем backup отдельной задачей аналогично WhatsApp. Никаких сегодняшних exit-ramp lock'ов не требуется.

**Альтернативы рассмотренные**:
- **A. WhatsApp full E2E backup сейчас** — overkill для MVP scope (нет messenger'а). Скинуто на Phase-3+.
- **B. Hybrid opt-in** — добавляет complexity без ясной необходимости в MVP.

**Adjacent concerns** (записаны отдельными вопросами / task'ами):
- Onboarding UX формулировка предупреждения — новый task (Q-21 register) или /speckit.clarify decision в TASK-67.
- Q-12 (peer confirmation on recovery) остаётся open, влияет на attacker-with-passphrase risk.
- Photos в family album (TASK-28) при отсутствии backup — приемлемо для MVP или нет? Отвечено: MVP TASK-28 может обходиться без backup (owner accepted risk).

### Decision (English, immutable) 🔒

**Choice**: MVP — Signal-style (no message/photo/audit history restored after device recovery). Phase-3+ — WhatsApp-style E2E backup will be designed as a separate task alongside messenger (TASK-27) and full family album (TASK-28).

**Rationale**: Article XX (Pre-MVP no-migration override) removes obligation to pre-emptively lock foundation for future backup. Elderly launcher's MVP primary content is Profile state (contacts + tiles + themes), already covered by MLS bucket sync recovery. Messenger and photo history are Phase-3+ features. Adding backup infrastructure now = premature abstraction (Article XI, CLAUDE.md rule 4). HKDF context strings, wire format schema, and retention policies remain modifiable without cost until first real users.

**Applies to**: TASK-27 (messenger), TASK-28 (family album), TASK-32 (audit log), TASK-6 (root key rotation policy) — none require backup infrastructure in MVP scope. TASK-70 (profile sync) already covered by MLS bucket recovery.

**Trade-offs accepted**: Users on new device cannot see past messages, past photos beyond current Profile snapshot, or historic audit log. Explicit setup wizard warning is mandatory (see follow-up in TASK-67 speckit-clarify or new dedicated wizard-formulation task). Users onboarded pre-Phase-3 lose message/photo history from that era permanently; users onboarded post-Phase-3 get full backup coverage. Symmetric to WhatsApp before 2021.

**Exit ramp**: Add `HistoryBackup` port + SQLCipher local DB adapter + backup encryption key derivation (via new HKDF slot `history-backup-v1`) + backup upload to iCloud/Drive/R2. Estimated effort: 4-6 weeks implementation + 2 weeks UX. Server-roadmap entry: `HIST-BACKUP-001`. Additive change — no migration of existing MLS/bucket code required.

<!-- SECTION:DISCUSSION:END -->

## Implementation Plan
<!-- SECTION:PLAN:BEGIN -->
Not applicable — decision task, no implementation. Downstream feature tasks implement their behaviour under this Decision's constraints.
<!-- SECTION:PLAN:END -->

## Final Summary
<!-- SECTION:FINAL_SUMMARY:BEGIN -->

**Decided**: 2026-07-02 (single mentor session).

**Follow-ups extracted**:
- Q-21 (wizard formulation) → tactical, resolves in /speckit.clarify TASK-67 or new dedicated task.
- Q-12 (peer confirmation on recovery) → separate discussion task (TASK-101).
- Exit ramp HIST-BACKUP-001 → add to `docs/dev/server-roadmap.md`.

**Downstream tasks that now depend on this**: TASK-27, TASK-28, TASK-32, TASK-70. They should add `dependencies: [TASK-100]` when entering speckit-cycle.

<!-- SECTION:FINAL_SUMMARY:END -->
