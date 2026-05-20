# Failure-Recovery Checklist: Setup Assistant and Launcher Bootstrap

**Purpose**: Verify spec describes failure paths, not just happy path. Per constitution Article III §4.
**Created**: 2026-05-19 (post `/speckit.clarify`)
**Feature**: [spec.md](../spec.md)

---

## External actions in spec 010 and their failure-mode coverage

| Action | FRs | Happy path | Failure path covered? |
|--------|-----|------------|------------------------|
| Read `/config/current` (ARCH-016) | FR-001..FR-006 | Room observable | ✓ (FR-005 fallback to preset; US-1 #2 offline cold-start) |
| Request `ROLE_HOME` | FR-007 | User accepts | ✓ (US-2 #2 denial → `!N` banner; US-2 #3 deep-link recovery) |
| Request `POST_NOTIFICATIONS` | FR-008 | User accepts | ✓ (US-4 #2 denied → `!N` banner + Settings deep-link; US-4 #3 < Android 13 skip) |
| Request `CALL_PHONE` | FR-012, FR-013 | Granted → `ACTION_CALL` | ✓ (FR-012 denied → `ACTION_DIAL` fallback; FR-015 invalid number → disabled button; US-3 #5 permanent deny → `!` indicator) |
| Unlink paired device | FR-032 | `LinkRegistry.deactivate(linkId)` | ⚠️ ([gap — network failure не specified](#open-items)) |
| `SetupCheck.check()` execution | FR-017..FR-020a | Returns CheckStatus | ⚠️ ([gap — exception handling не specified](#open-items)) |
| 7-tap + challenge gate | FR-021..FR-027 | Right answer → admin-mode | ✓ (FR-024 wrong → regenerate, no lockout; Edge case vibration disabled) |
| GMS detection | FR-042..FR-044 | `Available` → wizard | ✓ (FR-042 fatal → hard-block + `finishAffinity()`; FR-044 recoverable → system dialog) |

## Error categories

- [⚠️] **CHK001** — Failure modes per FR:
  - **Most FRs have explicit failure paths** (ROLE_HOME, POST_NOTIFICATIONS, CALL_PHONE, GMS).
  - **Gap 1**: FR-032 `LinkRegistry.deactivate()` — network failure during unlink? Spec говорит "запись помечается revoked, push другой стороне уходит" но не описывает что если Firestore unreachable.
  - **Gap 2**: FR-017 `SetupCheck.check()` — что если throws? Например `BatteryOptimizationCheck` использует `PowerManager.isIgnoringBatteryOptimizations(packageName)` который может throw SecurityException на каких-то OEM. **Не specified.**
- [⚠️] **CHK002** — User-visible behaviour для failure modes:
  - ROLE_HOME / POST_NOTIFICATIONS / CALL_PHONE — explicit user-visible behaviour. ✓
  - Paired unlink failure — **не specified** (toast? Retry button? Silent?). **Gap.**
  - SetupCheck exception — **не specified**. **Gap.**
- [⚠️] **CHK003** — No silent failures of user-initiated actions:
  - Tap "Прекратить помощь" → если network fail → бабушка увидит что устройство всё ещё в списке? Без feedback — silent failure. **Gap.**

## Fallbacks

- [X] **CHK004** — Fallback chains depth:
  - CALL → DIAL (single fallback, no chain). ✓
  - ARCH-016: applied → preset (single fallback). ✓
  - GMS: Available → recoverable resolution → fatal (defined chain). ✓
- [X] **CHK005** — Fallback specified by data: `Action.fallback` from спека 5; FR-012 conditional ACTION_CALL/ACTION_DIAL — runtime check based on permission, не данные. **Borderline** но это implementation pattern, не data-driven contract.
- [X] **CHK006** — Terminal behaviour: GMS fatal → `finishAffinity()` (terminal). CALL_PHONE permanent denied → `ACTION_DIAL` always works (no terminal failure). ✓

## Retries

- [⚠️] **CHK007** — Retry behaviour explicit:
  - ARCH-016: Room observable auto-picks up next push (implicit retry via subscription). **Не explicit в спеке.**
  - SetupCheck on Settings RESUMED: auto-retry on each RESUMED (effectively a retry mechanism). ✓ FR-020a.
  - Paired unlink: **не specified** — что если admin retry?
  - Challenge: no retry counter, infinite retry by design (FR-024). ✓ Explicit.
- [X] **CHK008** — No infinite retry loops without user intervention:
  - All retries either user-triggered (RESUMED, tile-tap, "Настроить" button) or bounded (single-shot per RESUMED).
- [⚠️] **CHK009** — Idempotency:
  - `LinkRegistry.deactivate(linkId)` — calling twice on already-revoked link: idempotent? Спек 7 contract предполагает идемпотентность но **спек 010 не states это explicitly**. **Soft observation.**
  - All other actions naturally idempotent (re-check setup, regenerate challenge).

## Offline / degraded modes

- [X] **CHK010** — Offline behaviour:
  - ARCH-016 offline: US-1 #2 explicit — last-applied config from Room. ✓
  - Setup checks offline: NetworkOnlineCheck reports NotConfigured. `!N` показывает. ✓
  - **Paired unlink offline**: **не specified**. Local mark as revoked + queue для sync? Block UI с message? **Gap.**
  - GMS hard-block при first launch: GMS detection не требует internet. ✓
- [X] **CHK011** — Stale data TTL:
  - SetupCheck results: refreshed на каждый RESUMED (FR-020a) — TTL по definition = lifetime of Settings screen open. ✓
  - Paired devices list: real-time Firestore listener (спек 7). ✓
  - Challenge: regenerated каждый 7-tap (FR-024). ✓

## Permissions denied

- [X] **CHK012** — First denial documented:
  - ROLE_HOME first denial → wizard «Позже» button → continues setup; later: `!` banner. ✓
  - POST_NOTIFICATIONS first denial → wizard skip → later: `!` banner. ✓
  - CALL_PHONE first denial → rationale screen → if still denied → fallback ACTION_DIAL. ✓
- [X] **CHK013** — Permanent denial recovery paths:
  - ROLE_HOME: US-2 #2/#3 — deep-link → системный RoleManager dialog. ✓
  - POST_NOTIFICATIONS: US-4 #2 — deep-link `ACTION_APP_NOTIFICATION_SETTINGS`. ✓
  - CALL_PHONE permanent: US-3 #5 — `!` indicator, fallback works without permission. ✓

## Recovery from invalid state

- [X] **CHK014** — Persistent state corruption:
  - Challenge state: in-memory only → impossible to corrupt. ✓ (FR-025 + Edge Cases explicit)
  - Local persistence спека 8 Room — inherits from спек 8 recovery model.
  - **No new persistent state introduced в спеке 010.** ✓
- [X] **CHK015** — No "crash and restart" as recovery:
  - GMS fatal `finishAffinity()` — это intentional terminal state, не crash. ✓
  - All other failures recover в-place.

## Diagnostics

- [⚠️] **CHK016** — Failures observable:
  - **Спек 10 не enumerates diagnostic events для новых failure modes** (paired unlink fail, SetupCheck exception, GMS fatal).
  - Existing `DiagnosticsEvent` infrastructure (спек 2) doesn't auto-cover. **Plan-level gap.**
- [⚠️] **CHK017** — Failures aggregated by category:
  - Plan-level: events should be enumerable (`gmsHardBlock`, `roleHomeDenied`, `unlinkFailed`), не unique error messages.

---

## Open items

1. **CHK001/CHK002/CHK003/CHK010 — Paired device unlink network failure.** Add FR-032a: «Если `LinkRegistry.deactivate(linkId)` fails из-за network unavailable, System MUST показать non-blocking toast «Не удалось отвязать — попробуйте позже», unlink action queued для retry при следующем online cycle. Бабушка видит устройство всё ещё в списке (state not lost).» **Recommendation**: добавить в спек.

2. **CHK001/CHK002 — SetupCheck exception handling.** Add FR-017a: «Если `SetupCheck.check()` throws, System MUST интерпретировать как `NotConfigured(reason = exception.message)` и логировать diagnostic event `setupCheckException(id)`. Не должно crash'ить Settings UI.» **Recommendation**: добавить в спек.

3. **CHK009 — `LinkRegistry.deactivate()` idempotency.** Cross-reference contract спека 7. Если не идемпотентен — добавить assumption в спек 010 или fix в спеке 7.

4. **CHK016/CHK017 — Diagnostic events.** Plan.md должен enumerate новые diagnostic categories: `gmsHardBlock(reason)`, `roleHomeRequested(accepted: Boolean)`, `postNotificationsRequested(accepted: Boolean)`, `unlinkAttempted(linkId, result)`, `setupCheckException(checkId, reason)`, `challengeGateAttempt(type, result)`. Add в contracts/diagnostics-events-v3.md (если будет created).

## Result

**13/17 ✓, 4 observations** (CHK001/CHK002/CHK003/CHK010 — paired unlink network fail gap; CHK016/CHK017 — diagnostic events plan-level). **Mostly addresable spec additions** (2 new FRs) + plan-level work. **Не blocker для `/speckit.plan`** но рекомендую minor FR additions перед plan.

---

## Краткое содержание (для не-разработчика)

Проверили: что происходит когда что-то идёт не так (нет интернета, permission denied, exception). **Хорошо покрыто**: ROLE_HOME / POST_NOTIFICATIONS / CALL_PHONE denied — все имеют fallback'и. **Гэп 1**: что если unlink (отвязка от admin'a) не дошёл до сервера из-за отсутствия интернета — не описано. **Гэп 2**: что если SetupCheck.check() выбросил exception — не описано. Рекомендую 2 small FR additions (FR-017a, FR-032a) перед plan.
