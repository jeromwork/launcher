# Crypto Q-N Migration Table + Open Questions

> **Shrunk 2026-07-07 (311 → ~80 lines).** Migration mapping preserved for traceability; individual Q-block bodies removed (duplicated content in decision-task Decision blocks).
>
> **Start here for open questions**: [`crypto-status.md`](crypto-status.md) → priority queue.

---

## Migration mapping (Q-N → TASK-N)

Snapshot 2026-07-07. Q-N never reused; closed Q remain here for history.

| Q-N | Topic | Migrated to | Status |
|---|---|---|---|
| Q-01 | Список групп на сервере (recovery через MLS state) | crypto.md § Δ.2 | 🟢 decided |
| Q-02 | Client-side compression before encryption | [TASK-110](../../backlog/tasks/task-110%20-%20Decision-Client-side-media-transformation.md) | 🟢 Draft |
| Q-03 | Signed upload tokens (quota enforcement без чтения) | [TASK-111](../../backlog/tasks/task-111%20-%20Decision-Signed-upload-tokens-quotas-abuse-response.md) | 🟢 Draft (Deferred) |
| Q-04 | Metadata privacy tier T0 → T1 adapter swap | [TASK-108](../../backlog/tasks/task-108%20-%20Decision-Metadata-privacy-what-server-sees.md) | 🟢 Draft |
| Q-05 | Zombie devices (6+ mo inactive) — auto-cleanup? | — | 🔴 open (low, Phase-3+) |
| Q-06 | Editing lock (20-min TTL, force-override) | [TASK-102](../../backlog/tasks/task-102%20-%20Decision-Revoke-policy.md) | 🟢 Draft (encrypted lock added 2026-07-07) |
| Q-07 | Preset bundle platform-scope | — | 🟡 partial (part of TASK-16 discipline + TASK-20 config-copy) |
| Q-08 | IdentityVault iOS/Huawei/TV/Google TV | **split** TASK-112 (port) + TASK-25 (cross-app) + TASK-26 (iOS) + TASK-29 (TV) | 🟢 all Draft/Discussion |
| Q-09 | History recovery (WhatsApp-style backup) | [TASK-100](../../backlog/tasks/task-100%20-%20Decision-History-backup-strategy-for-MVP.md) | 🟢 Draft |
| Q-10 | Root_key rotation — MVP не поддерживает | [TASK-41](../../backlog/tasks/task-41%20-%20Key-rotation-forward-secrecy.md) | 🟡 low (Phase-5+) |
| Q-11 | Revoke policy — только owner или policy-based | [TASK-102](../../backlog/tasks/task-102%20-%20Decision-Revoke-policy.md) | 🟢 Draft |
| Q-12 | Peer confirmation при recovery — auto vs UX | [TASK-101](../../backlog/tasks/task-101%20-%20Decision-Peer-confirmation-on-recovery.md) | 🟢 Draft |
| Q-13 | FCM fallback (Huawei без GMS) | — | 🔴 open (high, physical device dependent) |
| Q-14 | Cloudflare DO concrete design для quota | **split** [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) (baseline) + [TASK-109](../../backlog/tasks/task-109%20-%20Decision-Durable-Objects-concrete-design-security-critical-endpoints.md) (Paused) | 🟡 partial |
| Q-15 | Blob deduplication по content hash | [TASK-110](../../backlog/tasks/task-110%20-%20Decision-Client-side-media-transformation.md) (dedup impossible by design) | 🟢 resolved |
| Q-16 | Group ID visible серверу — граф связей | — | 🔴 open (medium, part of TASK-108 T2 future) |
| Q-17 | Abuse response mechanism (legal compliance) | [TASK-107](../../backlog/tasks/task-107%20-%20Decision-Abuse-response-mechanism-legal-minimum.md) | 🟡 Paused (post-MVP) |
| Q-19 | SAS emoji policy | [TASK-67](../../backlog/tasks/task-67%20-%20Pairing-Feature-And-Bucket.md) preset field (added 2026-07-07) | 🟢 in TASK-67 |
| Q-20 | Fitness rule `Clock.System.now()` ban | [TASK-16](../../backlog/tasks/task-16%20-%20Preset-Schema-v2-Wizard-Engine.md) second fitness rule (added 2026-07-07) | 🟢 in TASK-16 |
| Q-21 | Setup wizard formulation history loss | TASK-67 `/speckit.clarify` | 🟢 resolved (tactical) |

**Q-18 was renumbered/absent** — not a gap, just naming quirk from early sessions.

---

## Still-open questions (require future mentor session or task)

- **Q-05** Zombie devices auto-cleanup — edge case, Phase-3+ or later. Not blocking.
- **Q-13** FCM fallback (Huawei без GMS) — HMS Push Kit / MQTT / WebSocket. Blocks TASK-58 Huawei smoke gates. **Physical device dependent** (owner has no Huawei) — parked in crypto-status.md § Medium.
- **Q-16** Group ID visible серверу — sealed sender pattern (Signal-tier). Absorbed as TASK-108 T2 future tier, not MVP. Not standalone task.

---

**For architectural decisions**: create a new decision-task in `Discussion` per CLAUDE.md rule 11. Do NOT extend this file with new Q-NN entries — that pattern is retired.

**Retired skill note**: `procedure-crypto-alignment-sweep` skill was replaced with **`procedure-decision-drift-check`** — walks `dependencies:` graph, flags downstream tasks with superseded upstream Decision blocks.
