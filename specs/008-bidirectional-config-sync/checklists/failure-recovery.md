# Checklist: failure-recovery

**Spec**: `spec.md` (rev. 2026-05-14, post-clarify Q1-Q10)
**Run**: 2026-05-14 — `/speckit.clarify` post-pass before `/speckit.plan`.

Verifies Article III §4 (deterministic fallback) — what happens when things go wrong.

---

## Error mode inventory

| # | Failure mode | Covered by | Recovery path |
|---|---|---|---|
| F1 | Network unavailable во время push | FR-015, US-3 scenario 3, Edge Case «App killed» | Spinner с текстом «нет сети»; pending state сохраняется; retry by user |
| F2 | Firestore PERMISSION_DENIED (auth lost, link revoked) | FR-013, Edge Case «Revoke во время apply» | Error в `/state.partialApplyReasons`; user notification (TBD plan.md) |
| F3 | Optimistic concurrency conflict | FR-013, FR-014, US-2 | Merge UI, user resolves |
| F4 | Firestore INVALID_ARGUMENT (size > 1 MiB) | OUT-008, Edge Case «Огромный список контактов» | Через общий error-path FR-013; нет proactive prevention |
| F5 | Managed application of /config fails partially (provider unavailable, contact permission revoked) | Edge Case «Partial apply», FR-033 | `/state.partialApplyReasons[]`, admin sees in UI |
| F6 | Process death между save локально и push | US-4, FR-042, Edge Case «App killed mid-edit» | Pending state survives; banner on next launch (FR-046, FR-047) |
| F7 | Schema mismatch (admin v2 ↔ Managed v1) | OUT-006, Edge Case «Schema mismatch» | **Deferred** to spec `app-version-compatibility` |
| F8 | UUID collision | Edge Case «UUID-коллизия» | Treated as normal element-conflict via merge UI |
| F9 | FCM push not delivered (FCM down, no GMS) | FR-022 T2/T3/T4 (multi-trigger) | NetworkCallback, WorkManager 15min, RESUMED 2min — three fallbacks |
| F10 | Process killed by OS низкой памятью | US-5, FR-041, FR-044, SC-004a | Read last-applied from Room; show stale UI; refetch after 5s |
| F11 | Room database corruption (rare) | **NOT EXPLICITLY COVERED** in spec.md | **Action item** для plan.md (см. CHK014) |
| F12 | Merge UI cancelled by user (без resolution) | **NOT EXPLICITLY COVERED** | **Action item** (см. CHK002) |
| F13 | Retry loop при network flaps во время push | **NOT EXPLICITLY COVERED** | **Action item** (см. CHK007) |
| F14 | Network появляется/исчезает rapidly (flapping) | Implicit in FR-022 T2 (event-driven) | OK in spec, but throttle policy plan.md |

---

## Error categories

- [x] **CHK001 — Each FR involving an external action lists at least one failure mode**

  - FR-010 (write `/config`) → может fail из-за auth, network, size (F1, F2, F4).
  - FR-013 (server check) → fails on conflict (F3) и permission (F2).
  - FR-014 (show merge UI) → следствие F3.
  - FR-020 (Worker FCM push) → fails on FCM down (F9), recovered by T2/T3/T4.
  - FR-021 (apply on FCM trigger) → может fail partially (F5).
  - FR-030 (write `/state`) → может fail из-за auth, network. **Not explicitly addressed in spec.md**. Если /state write fails — admin не увидит «применено» indicator. Что делать Managed'у? **Action item**: retry policy для /state write (per FR-046 pattern — pending для /state?).
  - FR-040 (save локально) → «всегда успешно» per spec; но Room I/O может fail (disk full, IO exception). **Edge case**: что если disk full на Managed-телефоне? **Action item для plan.md**.
  - FR-044 (read Room at start) → может fail если DB corrupt. **Action item** (F11).

  Большинство FR имеют failure modes покрытые в Edge Cases. Mining gaps: `/state` write failure, Room I/O failure.

- [ ] **CHK002 — For each failure mode: user-visible behaviour specified**

  **Finding: PARTIAL PASS.**

  ✅ Хорошо описаны:
  - F1 (no network): FR-015 — спиннер + текст «нет сети, идёт загрузка».
  - F3 (conflict): FR-014/050/051 — merge UI с diff.
  - F5 (partial apply): FR-033 `partialApplyReasons[]`, admin читает `/state`. **Но**: spec.md не специфицирует **как admin это видит в UI** — это спинне баннер? список? Action для plan.md.
  - F6 (process death): FR-046 «значок pending push», FR-047 «баннер в Settings».
  - F9 (FCM not delivered): infrastructural, transparent через fallback triggers.
  - F10 (process killed): FR-044 — graceful read from Room.

  ⚠️ Недоописанные user-visible behaviour:
  - F2 (PERMISSION_DENIED / link revoked во время edit): что admin видит? «Связь разорвана»? Или просто push fail? **Action для plan.md / spec.md**.
  - F4 (size exceeded): «через общий error-path FR-013» — но spec.md не специфицирует UI message. **Action для plan.md**: user message при size error.
  - F12 (merge UI cancelled): что происходит если admin закроет merge UI без resolution? Pending остаётся? Local discard? **Action item: add to spec.md or clarify in plan.md**. Я бы рекомендовала: pending сохраняется (можно вернуться позже к merge), баннер «у вас неразрешённый конфликт».

  **Recommendation для spec.md**: рассмотреть добавление одного FR покрывающего «merge UI cancellation» — что pending не теряется, баннер остаётся. Это уже подразумевается FR-043 («pending живёт сколь угодно долго»), но явное упоминание не повредит.

- [x] **CHK003 — No silent failures of user-initiated actions**

  ✅ FR-015: «100% push-операций имеют видимый UI-state, нет тихих процессов». SC-001: «100 push'ей → 100 видимых UI-state-transitions». SC-003: «100% push'ей приводят к /state update **или** к visible merge UI — нет тихих потерь».

  No silent failures для user-initiated actions explicitly contracted. ✅

## Fallbacks

- [x] **CHK004 — If feature has fallback chains: maximum depth defined (cycle protection)**

  - FR-022 fallback chain: T1 (FCM) → T2 (NetworkCallback) → T3 (WorkManager 15min) → T4 (RESUMED 2min throttle). Each is **independent trigger**, не каскадная цепочка — нет cycle risk by design.
  - FR-014 merge UI → FR-054 user resolves → second push. If second push конфликт снова — second round merge UI. **Potential infinite loop**: если admin делает merge, конфликт snova, merge snova... Theoretically unbounded. But: **practical bound** — каждая итерация требует user action, не auto-retry. User может cancel в любой момент.
  - **Conclusion**: ✅ нет «true» infinite loops без user intervention.

- [x] **CHK005 — Fallback is specified by the data, not hardcoded in dispatch**

  Spec 008 не имеет `Action.fallback`-style data-driven fallbacks (это паттерн спека 005 actions). 008 fallbacks — это **structural** (multi-trigger refresh, retry-by-user-on-conflict). Это OK для этого спека.

- [ ] **CHK006 — If fallback also fails: terminal behaviour defined**

  ⚠️ Watch points:
  - F1 + F4: «нет сети, идёт загрузка» → если сеть вернётся и **снова** конфликт → merge UI → resolves → push → **снова** размер превышен → **terminal**? Spec.md не специфицирует **terminal state**. Гипотетически: admin застрял с pending, который не помещается на сервер.
  - F11 (Room corruption): spec.md не покрывает; «crash + restart»? Or graceful UI «config недоступен, повторите setup»?
  - **Action для plan.md**: для каждой fallback chain определить **terminal state** — что показать user когда всё перепробовано.

## Retries

- [ ] **CHK007 — Retry behaviour explicit: who triggers (user vs auto), how many attempts, backoff**

  ⚠️ **Finding: GAP.**

  Spec.md не специфицирует retry policy:
  - **Push retry on network drop** (Edge Case «Сеть рвётся в момент push»): «retry с проверкой updatedAt» — но **кто** retry'ит? User? Auto? Сколько раз? Backoff? **Not specified**.
  - **/state write retry** (FR-030): если /state write fails, retry автоматически? Сколько раз?
  - **FCM subscription retry** (наследие 007): TODO-REL-001 в project-backlog (уже OPEN).

  **Action для plan.md** (mandatory): retry policy:
  - Push to `/config`: **auto-retry** через Firebase SDK (built-in retry with exponential backoff up to ~30s). После — user-initiated retry through UI («попробуйте снова»).
  - `/state` write: same Firebase auto-retry; если final fail — log + next trigger refresh попробует update.
  - Merge resolution second push: user-initiated, no auto-retry.

- [x] **CHK008 — No infinite retry loops without user intervention point**

  Firebase SDK retry имеет встроенный exponential backoff cap. User-initiated retries требуют user action — natural intervention point. ✅

- [x] **CHK009 — Idempotency: actions that may be retried are safe to repeat**

  - `/config` writes — **idempotent through optimistic concurrency**: повторная запись с тем же `clientSnapshotUpdatedAt` либо успешна (если ничего не менялось), либо conflict (если кто-то писал параллельно). Не приведёт к двойной applies.
  - `/state` writes — idempotent (overwrite-current). ✅
  - FCM apply: FR-023 «избежать double-apply» через `lastWriterDeviceId == self` check. ✅

## Offline / degraded modes

- [x] **CHK010 — If feature reads from network: offline behaviour defined**

  ✅ Comprehensive coverage:
  - US-3 scenario 3: Managed offline — save локально работает, баннер pending, no auto-push.
  - US-4 scenario 2: pending long-lived.
  - FR-041, FR-044, SC-004a: cold start от Room (offline-friendly).
  - FR-022 T2: NetworkCallback при возврате online — auto-refresh /config.
  - Edge Cases: «Сеть рвётся в момент push», «Pending существует месяц».

- [x] **CHK011 — Stale data: TTL / freshness requirements defined**

  - **Pending data**: «живёт сколь угодно долго» (FR-043) — **intentional no TTL**.
  - **Applied-config in Room**: freshness via FR-044 (5s post-startup refetch). No hard TTL — applied-config is the «last known good» state.
  - **`/state` on server**: freshness via Managed write (FR-030) on every apply. Admin reads latest.
  - ✅ Freshness model coherent.

## Permissions denied

- [x] **CHK012 — Each permission required: behaviour when denied first time documented**

  008 не вводит новых runtime permissions (per security checklist CHK015). Inherited permissions:
  - `INTERNET`, `ACCESS_NETWORK_STATE` — обычно auto-granted (normal-level permissions, не нужны user prompt).
  - **N/A для runtime permissions** в 008.

- [x] **CHK013 — Permanent denial: explicit recovery path**

  N/A — no runtime permissions in 008.

## Recovery from invalid state

- [ ] **CHK014 — If persistent state can become corrupt: recovery path defined**

  ⚠️ **Finding: GAP.**

  Spec.md не покрывает Room corruption scenarios:
  - Что если Room database становится corrupt (rare, но possible on disk failure, OS crash mid-write)?
  - Что если schema migration fails (future, когда будет bump)?
  - Что если local applied-config содержит invalid UUID (FR-004 requires UUID v4 — что если корруптнулся)?

  **Action для plan.md** (mandatory recovery paths):
  - Room corruption: catch `SQLiteException` at startup → wipe DB → fall back to fresh state (no last-applied, refetch from /config). User sees «загрузка...» briefly.
  - Schema mismatch при future bump: managed via `app-version-compatibility` spec (OUT-006).
  - Invalid UUID in stored config: log + drop the corrupt element; treat как «никогда не было такого slot/contact».

  Spec 008 фокус на **happy path of corruption** — Room cleanup is FR-045, но это про **legacy mock-storage**, не runtime corruption.

- [x] **CHK015 — No "crash and restart" as recovery strategy**

  ✅ Spec.md не предлагает crash как recovery. FR-021 «применить атомарно (всё или ничего на уровне локального хранилища)» — atomic transaction, не crash. F10 (process killed) — graceful via FR-044 (read Room, render).

## Diagnostics

- [ ] **CHK016 — Failures are observable: at minimum one diagnostic event emitted with category**

  ⚠️ **Finding: GAP.**

  Spec.md не специфицирует:
  - Diagnostic events для conflicts (FR-013).
  - Events для apply failures.
  - Events для FCM-not-delivered scenarios.
  - Категоризация ошибок.

  Spec 007 имел `BackendError` sealed class с categories (Offline, NotFound, PermissionDenied, ServerError) — это **уже observable** through the port. 008 inherits this.

  **Action для plan.md**: явное правило — все Outcome.Failure result'ы маршалятся в structured log events (категория, не PII). Категории:
  - `config.push.failed` (subcategory: network / permission / conflict / size).
  - `config.apply.partial` (subcategory: provider_unavailable / contact_permission).
  - `state.write.failed` (subcategory: network / permission).
  - `room.read.failed` / `room.write.failed`.

- [x] **CHK017 — Failures aggregated by category (not unique per error message string), to enable rate measurement**

  Inherited from 007: `BackendError` sealed class — categorical. ✅ But: spec 008's new errors (Room corruption, merge UI cancellation) need explicit category strings.

  **Action для plan.md**: define enum / sealed class для 008-specific failures:
  ```kotlin
  sealed class ConfigSyncError {
    data class WriteRejected(val reason: BackendError) : ConfigSyncError()
    data object Conflict : ConfigSyncError()
    data class ApplyPartial(val providerErrors: List<String>) : ConfigSyncError()
    data class LocalStorageCorrupt(val cause: Throwable) : ConfigSyncError()
    // ...
  }
  ```

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 11 | CHK001 (mostly), CHK003, CHK004, CHK005, CHK008, CHK009, CHK010, CHK011, CHK012, CHK013, CHK015 |
| ⚠️ Watch / Gap | 6 | CHK002 (F2/F4/F12 user behaviour), CHK006 (terminal fallback states), CHK007 (retry policy), CHK014 (Room corruption), CHK016 (diagnostic events) |
| ❌ Fail | 0 | — |

**Verdict: PASS with significant action items для plan.md.**

Spec.md имеет хорошее покрытие основных failure scenarios через Edge Cases + явных FR (US-2, US-3, US-4, FR-015, FR-022, FR-044). Но: некоторые «adjacent» failure modes (Room corruption, merge UI cancellation, terminal fallback states, retry policies, diagnostic events) **подразумеваются**, но не **явно специфицированы** в spec.md.

---

## Mandatory action items для plan.md

1. **Retry policy table** (CHK007): per operation (push, /state write, FCM subscribe) — кто триггерит, сколько попыток, backoff. Опираться на Firebase SDK auto-retry для baseline.

2. **Terminal fallback behavior** (CHK006): для каждой fallback chain определить «всё перепробовано» state. Особенно — что показать user когда:
   - push fails repeatedly из-за size (F4).
   - Conflict resolution looped many times.
   - Room corruption + network fail.

3. **Room corruption recovery** (CHK014): catch SQLiteException → wipe DB → fresh-state fallback. Specify in plan.md.

4. **Diagnostic events** (CHK016, CHK017): structured logging spec. Define ConfigSyncError sealed class с category enum.

5. **User-visible behaviour для under-specified failures** (CHK002):
   - F2 (revoked link): admin UI shows «связь с этим телефоном разорвана»; transition state.
   - F4 (size exceeded): user message «конфиг слишком большой, удалите что-то»; не «INVALID_ARGUMENT».
   - F12 (merge UI cancelled): pending stays, banner remains, no auto-resolution.

## Recommendation для spec.md (optional)

Добавить один short FR покрывающий F12 (merge cancellation):

> **FR-055**: Если пользователь закрывает merge UI без выбора (cancel / back button), pending-local-changes сохраняется без изменений; баннер «есть несинхронизированные изменения» остаётся (FR-047). Никакой auto-merge, никакой auto-discard.

Это **explicit** одно из подразумеваемого, и закрывает явный пробел.

**Если хотите** — могу добавить в spec.md. В противном случае закрепляется в plan.md.
