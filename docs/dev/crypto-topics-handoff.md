# Crypto Discussion Handoff — CANDIDATE Archive

> **Shrunk 2026-07-07 (327 → ~60 lines).** All Тема 4-10 content removed (либо закрыты Decision block'ами TASK-100..114, либо captured в crypto-status.md priority queue).
>
> **Start here for fresh AI**: [`crypto-status.md`](crypto-status.md) → [`docs/architecture/crypto.md`](../architecture/crypto.md) → `backlog/tasks/task-100..114`.

---

## Post-audit CANDIDATE resolution (2026-07-07)

Historical CANDIDATE-list from 2026-07-02 handoff. Kept for traceability.

| # | CANDIDATE | Resolution |
|---|---|---|
| 1 | Recovery notification + Old-device invalidation | ✅ TASK-101 Decision (Chrome-model auto-add + post-facto notification) |
| 2 | Profile-level SAS verification policy | ✅ TASK-67 preset field `pairing.sasRequirement` (added 2026-07-07 per audit item #3) |
| 3 | Fitness rule ban `Clock.System.now()` in crypto-flows | ✅ TASK-16 second fitness rule `crypto-flows-clock-hygiene` (added 2026-07-07 per audit item #2) |
| 4 | Editing lock document (20-min TTL) | ✅ TASK-102 Decision (encrypted lock added 2026-07-07 per audit clarification) |
| 5 | Encrypted co-admin display directory | ✅ TASK-114 created 2026-07-07 (audit item #1) |
| 6 | External crypto audit | ❌ superseded — replaced by post-launch community bug bounty + agent-based pre-release audit (`crypto-review.md` A4/A5) |
| 7 | Noise XX через snow (Rust) via UniFFI | ✅ confirmed in crypto.md frontmatter |
| 8 | mls-rs через UniFFI | ✅ swapped to openmls (confirmed 2026-07-07 in crypto.md frontmatter); mls-rs = exit ramp |
| 9 | Watch tasks — Nostr/Marmot Q4 2026 + Iroh | ✅ added to `docs/product/vision.md` § Watch list (2026-07-07 per audit item #8) |

---

## Themes 4-10 resolution snapshot (2026-07-07)

Original themes from 2026-07-02 handoff — closed / redirected:

- **Тема 4 (Revoke)** → [TASK-102](../../backlog/tasks/task-102%20-%20Decision-Revoke-policy.md). Audit trail visibility clarified в [TASK-32](../../backlog/tasks/task-32%20-%20Audit-Log-Infrastructure.md) (added 2026-07-07 per audit item #4).
- **Тема 5 (Multi-device одной identity)** → [TASK-101](../../backlog/tasks/task-101%20-%20Decision-Peer-confirmation-on-recovery.md) (multi-device first-class). Zombie devices Q-05 остаётся open (Phase-3+).
- **Тема 6 (Metadata + Zero-knowledge)** → [TASK-108](../../backlog/tasks/task-108%20-%20Decision-Metadata-privacy-what-server-sees.md). Adjacent concerns (legal request / BCP / log retention) → 3 SRV entries в `server-roadmap.md` (added 2026-07-07 per audit item #5).
- **Тема 7 (MLS TreeKEM vs Sender Keys)** → [TASK-58](../../backlog/tasks/task-58%20-%20Research-Signal-Sender-Keys-vs-MLS-for-family-group-E2E.md) Done (openmls chosen). Scale validation (100+ member clinic) → vision.md Watch list.
- **Тема 8 (Push payload > 4KB)** → [TASK-108](../../backlog/tasks/task-108%20-%20Decision-Metadata-privacy-what-server-sees.md) quotas (500 push/hour) + [TASK-10](../../backlog/tasks/task-10%20-%20SOS-Capability-Wizard-Step.md) SOS wire format sub-note (added 2026-07-07 per audit item #6). Huawei без GMS = Q-13 остаётся open.
- **Тема 9 (Recovery propagation)** → [TASK-101](../../backlog/tasks/task-101%20-%20Decision-Peer-confirmation-on-recovery.md).
- **Тема 10 (Key rotation)** → [TASK-41](../../backlog/tasks/task-41%20-%20Key-rotation-forward-secrecy.md) parked (MVP не поддерживает).

---

## Что уже решено про stack

- **libsodium-kmp** (ionspin) — крипто-примитивы.
- **snow** (Rust via UniFFI) — Noise_XX handshake.
- **openmls** (Rust via UniFFI) — MLS RFC 9420 group crypto. **Not mls-rs** (mls-rs = exit ramp).
- **Cloudflare Worker + Firestore** — delivery service.
- **SQLCipher** — encrypted local storage для openmls state.

Details: `docs/architecture/crypto.md` frontmatter + карта компонентов.

---

## Что не пере-обсуждать (rejected explicitly)

- SGX enclave — не строим никогда.
- Собственный ECDH handshake — используем `snow`.
- Access-grant + envelope-per-recipient — заменён MLS group membership.
- External crypto audit pre-ship — заменён на fitness tests + threat model + bug bounty + agent-audit.
- `mls-kotlin` (Traderjoe95) — hobby project, no audit, rejected.
- `com.wire:core-crypto` shortcut — GPL-3 contamination breaks commercial subscription model.
