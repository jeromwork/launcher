# Research: Spec 008 — Bidirectional Config Sync

**Date**: 2026-05-14
**Spec**: [spec.md](spec.md)
**Plan**: [plan.md](plan.md)

Research-level analysis of one-way doors and significant choices in spec 008.

---

## §1. Optimistic concurrency on `serverUpdatedAt` — one-way door

**Context**: spec 008 introduces collaborative editing (admin-phone + admin-tablet + Managed-phone as equal editors of `/config/current`). Conflict-detection mechanism is **one-way door** per CLAUDE.md §3 — changing it later requires wire-format migration and re-education of users about merge UX.

### Decision

**Approach C** — Optimistic concurrency on server-set `serverUpdatedAt` timestamp via Firestore transactions:
1. Client reads `/config/current`, captures `clientSnapshotUpdatedAt = snapshot.serverUpdatedAt`.
2. User edits → autosaves to local pending.
3. On push: `RemoteSyncBackend.runTransaction { read /config/current → check serverUpdatedAt == clientSnapshotUpdatedAt → if match, write with new serverTimestamp → else fail with TransactionConflict }`.
4. On conflict: compute `ConfigDiff(localPending, currentServer)` → present merge UI.

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | Last-write-wins (LWW) | Silent data loss; spec.md US-2 «потерять плитку вызов скорой недопустимо». Article XIV §1 (user safety). |
| B | Client-side `optimisticVersion: Int` counter | Requires client coordination для monotonic increment; first writer races → both think they're v=2; LWW edge cases still possible. `serverUpdatedAt` is server-authored, immune to clock skew. |
| C | **Optimistic concurrency on `serverUpdatedAt` (chosen)** | — |
| D | Pessimistic locking (`/config/current.lockedBy = uid` field) | Stale locks (editor app killed mid-edit); requires lock-TTL infrastructure; bad UX (other editors see «занято кем-то»). Firestore doesn't natively support row-locking; would require Cloud Function + Firestore transactions. |
| E | CRDT (e.g. Yjs-style) | Massive complexity for our shape: nested arrays of typed structs (flows → slots → args). Need bespoke CRDT type per element kind. Library size (Yjs ~50 KiB), learning curve, fitness-function cost. CLAUDE.md §4: «if abstraction were inlined, what would be lost?» — answer: optionality для future use cases that don't exist. |
| F | Vector clocks | Same complexity ballpark as CRDT; less mature library support; users still see merge UI when clocks diverge. |

### Why `serverUpdatedAt` not `optimisticVersion: Int`?

- Server-authored timestamp via `FieldValue.serverTimestamp()` is monotonic per-document and **server-controlled** — no client clock skew.
- Single source of truth: Firestore already maintains this; we just expose it as a field.
- Firestore-native: transactions on read-set are designed exactly for this use case.
- Wire-format simpler: one Timestamp, not Int + atomic update logic.

### Exit ramp (per CLAUDE.md §3)

If `serverUpdatedAt`-based concurrency proves insufficient (e.g. we add a feature requiring sub-second concurrent edits, or Firestore deprecates transactions):

1. **Cost**: change wire format to include `optimisticVersion: Int` (or vector-clock), bump `schemaVersion` 1 → 2.
2. **Migration path**: дополнительное reader-migration в Phase 0 of replacement spec — read v1 documents, derive `optimisticVersion = 1` for legacy, treat as if always conflict-free until first write at v2.
3. **UI changes**: merge UI shape stays the same (per-element diff); concurrency mechanism is invisible to users; only internal logic changes.
4. **Time estimate**: ~1 sprint to fully migrate.

This is an acceptable exit ramp — wire-format bump is well-understood pattern per CLAUDE.md §5.

### Risks

- **R1 (plan.md)**: One-way door на serverUpdatedAt. Mitigation: documented exit ramp above; spec 011 (contacts) and 009 (admin-mode-flows) will pressure-test the concurrency model with realistic UX before we commit production.
- **Edge case**: clock-skew handling (Firestore Server timestamps are eventually consistent — could `serverUpdatedAt` go backwards? Per Firestore docs, no — it's monotonic per document write). Confirmed Firestore guarantee, no mitigation needed.

---

## §2. UUID v4 for element id — client-generated

**Context**: FR-004 requires stable `id` per element (Flow / Slot / Contact). Diff/merge correctness depends on element identity surviving rewrites.

### Decision

**Client-generated UUID v4** via `kotlin.uuid.Uuid` (stdlib 2.0.20+).

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | Server-allocated id (via Cloud Function on create) | Cloud Function = Blaze plan (cost). Round-trip latency. **Doesn't work offline** — Managed can be offline, editor needs to add new element. |
| B | Client UUIDv4 (chosen) | — |
| C | Hash-based id (e.g. SHA of content) | Same content → same id; rename plate «Маша» → «Маша 2» creates new id, loses identity in diff. |
| D | Auto-increment Int | Race condition on first writers; cannot work offline. |

### Implementation

```kotlin
// commonMain
@OptIn(ExperimentalUuidApi::class)
fun newElementId(): ElementId = ElementId(kotlin.uuid.Uuid.random().toString())
```

No expect/actual needed — `kotlin.uuid.Uuid` is pure-Kotlin in stdlib since 2.0.20 (project Kotlin version OK).

### Collision probability

UUID v4 = 122 random bits. Birthday paradox: 50% collision at ~2.7 × 10^18 IDs. **Effectively zero** for our scale (< 1000 elements per link, < 10⁶ users). Per spec.md Edge Cases — treated as normal element-conflict via merge UI if it ever happens.

### Risks

None significant.

---

## §3. Save granularity — continuous autosave (FR-056)

**Context**: spec.md FR-040 originally said «раздельные действия save локально / push на сервер». State-management checklist found ambiguity: is «save локально» a button или automatic? Project owner answered (in clarify): autosave.

### Decision

**Continuous autosave**: every user edit triggers Room write through ConfigEditor, debounced 300ms for UX smoothness. No explicit «save» button. «Push на сервер» remains explicit user action.

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | Explicit save button → Room write | Risk of data loss on rotation / process death between edit and save. Senior-safe (Article VIII): пожилой пользователь забывает нажать. Project owner explicitly chose autosave. |
| B | Autosave on every edit, no debounce | Excessive writes during typing (every char). Possible flash wear (Article IX §7 — battery/storage discipline). |
| C | Continuous autosave with 300ms debounce (chosen) | — |
| D | Autosave on focus-loss | Doesn't survive process death mid-edit. |

### Implementation

```kotlin
// ConfigEditor.kt (commonMain port)
interface ConfigEditor {
    /** Updates local pending state. Debounced internally to ~300ms. */
    suspend fun updateDraft(linkId: LinkId, mutator: (ConfigDocument) -> ConfigDocument)

    /** Push to server with optimistic concurrency. May fail with Conflict. */
    suspend fun pushPending(linkId: LinkId): Outcome<Unit, ConfigSyncError>
}
```

### Storage cost

Room writes during edit session: typical session = 10-50 user actions × 1 row (single PendingLocalChanges per linkId, upsert), debounced → ~10-50 SQLite writes over session. Negligible.

---

## §4. Cleanup of legacy mock-storage (FR-045)

**Context**: spec 003 introduced in-memory and/or JSON-file mock-storage for launcher rendering. Spec 008 introduces real Room storage. What to do with legacy?

### Decision

**Cleanup at first launch** (Variant A from clarify Q6). At Managed app first start after 008-code upgrade:
1. Detect presence of legacy mock-storage files (specific paths from spec 003).
2. Delete them.
3. Continue normal startup → empty Room → wait for first `/config/current` from server.

### Alternatives considered

| # | Approach | Rejected because |
|---|---|---|
| A | Cleanup-on-first-launch (chosen) | Spec 003 не reached production (per memory `project_007_operational_state`); mock data is not valid /config; migration would create fake applied-config without server pair → admin UI shows nothing. CLAUDE.md §4: no abstraction for non-existing use case. |
| B | Migration of mock-data to Room | Pointless — mock data lacks UUID ids, server timestamps, linkId binding. |
| C | Leave legacy files in place | Disk waste; potential reader confusion in future. |

### Implementation

```kotlin
// androidMain — runs once on Application.onCreate before Koin init
class LegacyMockStorageCleanup(private val context: Context) {
    /**
     * CLEANUP-008: legacy spec 003 mock-JSON storage paths. Safe to remove after 2026-12-31
     * (by then all dev devices upgraded past 008). Per FR-045 + Q6 clarify.
     */
    fun cleanupOnce() {
        listOf(
            "presets/simple-launcher.json",
            "presets/mock-flows.json",
            // ...other 003-era files
        ).forEach { path ->
            val file = File(context.filesDir, path)
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
```

**Action for tasks.md**: Phase 3 task — explicit inventory of 003-era files (run grep on spec 003 source code for hardcoded paths), encode in `LegacyMockStorageCleanup`.

---

## §5. Background task budget (Article IX §2)

**Context**: spec 008 introduces 4 refresh triggers (FR-022 T1-T4) + push + state write + autosave. Article IX §2 requires each background activity to be justified.

| # | Task | Justification | Frequency | Battery cost |
|---|---|---|---|---|
| T1 | FCM listener (`config.updated`) | User-impact: admin's changes appear immediately. Reused from spec 007 infrastructure. | Per admin push event | ~0 (OS-managed) |
| T2 | ConnectivityManager.NetworkCallback | Catch newly-online state after offline period → apply backlog. | Per network state change (system event) | ~0 (OS-driven) |
| T3 | WorkManager periodic 15min | Fallback for no-FCM (no-GMS) and missed-NetworkCallback. Polls `/config/current` if `serverUpdatedAt` advanced. | 96 times/day | <0.1% battery/day (single Firestore read) |
| T4 | Activity#onResume throttled 2min | UX: launcher just became visible → user may notice change quickly. | Per user-RESUME of launcher with >2min idle | ~0 (user-bound, no background) |
| Push to /config | User-initiated | n/a (one-shot) | n/a |
| /state write | After apply | n/a (one-shot after T1-T4) | n/a |
| Room autosave | User-edit (debounced 300ms) | Senior-safe + no data loss (FR-056) | During edit sessions only | Negligible |

**Aggregate**: < 10 wakeups/hour (Article IX §3 cap). Well within budget.

---

## §6. R8 minification interaction (TODO-ARCH-006)

**Context**: spec 007 SC-006 failed by 0.99 MiB (delta 3.99 MiB vs target 3 MiB) → TODO-ARCH-006 created для R8 enable. Spec 008 adds Room (~150-300 KiB).

### Decision

**TODO-ARCH-006 MUST land before or during spec 008** to prevent APK delta from exceeding 4 MiB.

### Plan

- **Phase 0 of spec 008** (env prep): verify TODO-ARCH-006 status. If 🔴 OPEN — coordinate with spec owner to enable R8 in Phase 0.
- **Phase 12 of spec 008** (perf-checkpoint): measure APK delta. Gate: delta < 4 MiB.
- If gate fails: block merge until TODO-ARCH-006 done.

### Risk

R8 may reveal reflection-heavy Firebase classes needing keep-rules. Spec 007 R8 evaluation noted «consumer ProGuard rules from Firebase ship automatically» — should work. If unforeseen breakage → spec 008 ships without R8, accept ~4.3 MiB delta, escalate to TODO-ARCH-006 owner.

---

## §7. NetworkCallback port wrapping decision

**Context**: domain-isolation checklist CHK006 flagged `ConnectivityManager.NetworkCallback` as platform-specific. Decision needed: port or inline single use site.

### Decision

**Port** — `NetworkAvailability: Flow<Unit>` in commonMain, with `ConnectivityManagerNetworkAvailability` adapter in androidMain.

### Why port (not inline)

- **Fake adapter has test value** — many tests need to assert «trigger T2 fired apply» without touching Android system.
- Consistent with existing `RemoteSyncBackend` / `PushReceiver` pattern (commonMain ports + androidMain adapters).
- One-way door cost-analysis (CLAUDE.md §4 Test 2): if `ConnectivityManager` deprecated → swap adapter only (not domain logic). Estimate: 3-5 days.

### Alternative considered

Inline in single Android adapter (e.g. inside `ConfigRefreshWorker.kt`): rejected because (a) fakes can't test the trigger logic, (b) Android-platform leakage into config-sync flow.

---

## §8. WorkManager port wrapping decision

**Context**: Same CHK006 question, different answer.

### Decision

**No port** — direct Android-only `ConfigRefreshWorker` in `androidMain/adapters/lifecycle/`.

### Why no port

- **Single use site** (only T3 trigger uses WorkManager).
- **Fakes have minimal value** — WorkManager-specific behaviors (Doze-awareness, system scheduling) are not faithfully simulatable; tests would assert toy behavior, not real.
- T3 fallback redundancy with T1/T2/T4 means «WorkManager doesn't fire» is not a critical test scenario.

### Risk acceptance

If WorkManager API breaks (rare for AndroidX stable libs): replace single adapter. Estimate: 1-2 days.

---

## Summary

- **§1 (concurrency on serverUpdatedAt)**: one-way door, documented exit ramp.
- **§2 (UUID v4 client-side)**: trivial choice, no risk.
- **§3 (autosave 300ms debounce)**: senior-safe, fits FR-056.
- **§4 (cleanup not migration)**: 003 not in production, simplest correct path.
- **§5 (background budget)**: under Article IX cap with 4 triggers + 1 polling.
- **§6 (R8 dependency)**: must coordinate with TODO-ARCH-006.
- **§7 (NetworkAvailability port)**: yes, has fake-test value.
- **§8 (WorkManager no port)**: no, single use site.

No additional ADRs needed (none of these rise to «protocol or organization-wide» level per Article XV).

---

<!-- novice summary -->

## TL;DR

Эта папка — рабочая записка инженеров: какие развилки в дизайне 008 могут «закрыть двери» и сделать переделку дорогой. Главная развилка — как ловить параллельное редактирование (см. §1: используем «временную метку сервера», это надёжнее чем счётчик и проще чем CRDT). Остальные пункты — мелкие, без сюрпризов. Главный технический риск — APK размер: Room добавит 150-300 KB, и нам нужно включить R8 (это уже в backlog), чтобы не превысить бюджет.
