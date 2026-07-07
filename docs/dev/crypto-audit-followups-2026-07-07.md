# Crypto docs audit — follow-ups (2026-07-07)

**Temporary tracking file.** Delete when all items closed.

Audit проведён 2026-07-07 после закрытия TASK-58. Прошёл `docs/dev/crypto-*.md` + `docs/architecture/crypto*.md`, cross-check против существующих task'ов. Нашёл **13 items**. Ниже — actionable list.

**Delete condition**: все items closed + shrink legacy files completed.

---

## Actionable items (require task-touch or docs update)

### ✅ #1 Encrypted co-admin display directory (CANDIDATE-5)

- **Location**: `crypto-topics-handoff.md:134`
- **Content**: UI multi-admin показывает имена ("Мама Таня редактирует") вместо identity_id. Directory encrypted (не палит metadata).
- **Existing coverage**: Нет. TASK-102 = revoke, TASK-46 = contact sharing (не directory).
- **Action**: Create **TASK-114 «Encrypted co-admin display directory»** (Draft, decision-task label).
- **Status**: [x] DONE 2026-07-07

### ✅ #2 Fitness rule `Clock.System.now()` ban in crypto-flows (CANDIDATE-3 / Q-20)

- **Location**: `crypto-topics-handoff.md:132`, `crypto-open-questions.md:240`
- **Content**: Все timestamp'ы через `serverTimestamp` (time-skew defense). Fitness rule для автоматического enforcement.
- **Existing coverage**: TASK-16 (wire format discipline) — тот же fitness infrastructure, но scope сейчас другой.
- **Action**: Extend TASK-16 Decision block — добавить `Clock.System.now` ban как второй fitness rule под тем же infrastructure (custom Detekt / Konsist).
- **Status**: [x] DONE 2026-07-07

### ✅ #3 SAS verification policy (CANDIDATE-2 / Q-19)

- **Location**: `crypto-topics-handoff.md:98,131`, `crypto-open-questions.md:230`
- **Content**: `SasRequirement = Off | Optional | Mandatory`. Family=Optional (6 emoji), clinic/B2B=Mandatory. Preset field.
- **Existing coverage**: Нет prefix field. TASK-67 pairing содержит Safety Number mermaid sequence, но policy field отсутствует.
- **Action**: Sub-note в TASK-67 Description → «preset field `pairing.sasRequirement` per TASK-16 discipline; family-default `Optional`».
- **Status**: [x] DONE 2026-07-07

### ✅ #4 Public revoke audit trail visibility

- **Location**: `crypto-topics-handoff.md:162` (Тема 4 adjacent concern)
- **Content**: «Публично видно в MLS commits, что зафиксировано?» — public trail (MLS commits) vs private audit log (TASK-32).
- **Existing coverage**: TASK-32 (Audit Log Infrastructure) — проверить покрывает ли public trail visibility.
- **Action**: Read TASK-32, если не покрывает — sub-note добавить.
- **Status**: [x] DONE 2026-07-07

### ✅ #5 Government legal request / BCP / log retention

- **Location**: `crypto-topics-handoff.md:199-201` (Тема 6 adjacent concerns)
- **Content**: (a) government legal request что можно выдать; (b) backup для нас самих (потеряли Cloudflare account); (c) log retention policy.
- **Existing coverage**: Нет. Не MVP темы, operational not engineering.
- **Action**: Три SRV entries в `docs/dev/server-roadmap.md` — SRV-LEGAL-001, SRV-BCP-001, SRV-LOG-RETENTION-001.
- **Status**: [x] DONE 2026-07-07

### ✅ #6 SOS payload > 4KB wire format

- **Location**: `crypto-topics-handoff.md:223-224` (Тема 8)
- **Content**: SOS chunked assembly, latency-critical, inline encrypted payload ≤ 2.5KB после base64.
- **Existing coverage**: TASK-10 (SOS Capability + Wizard Step) есть, но про UI wizard, не wire format.
- **Action**: Sub-note в TASK-10 Description → «wire format decision required at implementation per TASK-16 discipline».
- **Status**: [x] DONE 2026-07-07

### ✅ #7 MLS overhead scale watch (Тема 7)

- **Location**: `crypto-topics-handoff.md:203-212`
- **Content**: MLS overhead для 100-member группы (clinic). Bandwidth Welcome. Epoch counter.
- **Existing coverage**: Нет. Не MVP (family = 5-7). Phase-5+ pre-clinic gate.
- **Action**: Add to `docs/product/vision.md` § Watch list — «pre-clinic gate: MLS at 100-member scale validation».
- **Status**: [x] DONE 2026-07-07

### ✅ #8 Decentralized transport watch (CANDIDATE-9)

- **Location**: `crypto-topics-handoff.md:138`
- **Content**: Nostr/Marmot Q4 2026, Iroh + p2panda-encryption для future decentralized transport (замена Cloudflare/Firestore).
- **Existing coverage**: Нет. Watch/monitoring, не task.
- **Action**: Add to `docs/product/vision.md` § Watch list.
- **Status**: [x] DONE 2026-07-07

---

## Already covered (verified during audit)

- ✅ **CANDIDATE-1** Recovery notification + Old-device invalidation → TASK-101 Decision block.
- ✅ **CANDIDATE-4** Editing lock document → TASK-102 Decision block (updated 2026-07-07 encrypted).
- ✅ **CANDIDATE-6** External crypto audit → superseded (agent-based + bug bounty), in crypto-review.md A4/A5.
- ✅ **CANDIDATE-7** Noise XX через snow UniFFI → confirmed in crypto.md frontmatter.
- ✅ **CANDIDATE-8** MLS через UniFFI (openmls) → confirmed in crypto.md frontmatter (2026-07-07 update).
- ✅ **Q-21** Setup wizard formulation history loss → resolved via `/speckit.clarify` TASK-67.

---

## Meta: legacy file cleanup (after action items closed)

**Current sizes** (2026-07-07):
- `crypto-mentor-overview.md`: 1034 строки
- `crypto-open-questions.md`: 311 строк
- `crypto-topics-handoff.md`: 327 строк
- Total: 1672 строки, ~20k tokens for fresh AI onboarding

**Target after shrink**:
- `crypto-mentor-overview.md`: ~150 строк (keep only Часть 0 novice glossary + Блок 20 rationale + актуальные Часть Δ answers).
- `crypto-open-questions.md`: ~80 строк (keep only migration mapping table + 3 not-migrated open questions Q-05/Q-13/Q-16).
- `crypto-topics-handoff.md`: ~60 строк (keep only CANDIDATE list post-audit-close + fresh AI onboarding instructions).
- Total: ~290 строк.

**Delete criteria** for content within each file:
- Remove any block with `⚠️ Superseded` banner where all content is duplicated in TASK-100..114 Decision blocks.
- Keep blocks that provide **onboarding value** (novice glossary, key terminology).
- Keep migration mapping tables (Q-N → TASK-M) for historical traceability.
- Remove all mermaid sequences that describe superseded models.

**Add navigation section** in `crypto-status.md`:
```
## Как fresh AI использует эти файлы
1. `crypto-status.md` — start here (TL;DR + priority queue).
2. `docs/architecture/crypto.md` — current authoritative snapshot.
3. `backlog/tasks/task-100..114` Decision blocks — детали решений.
4. Compact archive (`crypto-mentor-overview.md` + `crypto-open-questions.md` + `crypto-topics-handoff.md`, ~290 lines total) — только для onboarding / historical context.
```

**Status**: [ ] Shrink not started yet.

---

## Commit plan

**Commit 1 (this session)**: findings — 8 actionable items applied (this file remains, tracking).

**Commit 2 (this session or next)**: shrink legacy files + delete this tracking file when all items closed.
