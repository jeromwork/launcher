# Checklist: failure-recovery — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass

## Error categories

- [⚠️] **CHK001** Each FR involving external action lists failure mode.
  - **Worker FRs**:
    - FR-002 (JWT verification): implicit 401 on failure. Не explicit FR.
    - FR-004 (unknown eventType): 400 explicit ✅.
    - FR-005 (authorisation): 403 explicit ✅.
    - FR-006 (rate limit): 429 explicit ✅.
    - FR-009 (Worker FCM retry): 429/5xx → 3 retries → 503 ✅.
    - FR-010 (idempotency dedupe): cached return on hit ✅.
    - FR-013 (schemaVersion check): 400 explicit ✅.
  - **Client FRs**:
    - FR-020 (PushTrigger.trigger): returns `Outcome<Unit, PushTriggerError>`. Sealed class имеет: `Unauthorized`, `RateLimited`, `NetworkFailure`, `Backend(message)` (per spec implied).
    - FR-025 (HttpPushTrigger): network failure handling — implicit through Outcome.
    - FR-026 (no client retry): explicit choice ✅.
  - **Receiver FRs**:
    - FR-023 (unknown eventType): silent log + ignore ✅.
    - FR-028 (parse failures): NOT explicit — covered в wire-format CHK017 action.
  - **Action**: добавить explicit FR-XXX listing PushTriggerError variants + when each emitted (Unauthorized = JWT expired, RateLimited = 429, NetworkFailure = no connectivity, Backend = 5xx).

- [x] **CHK002** User-visible behaviour specified.
  - **Push failure (Worker 5xx / network)**: silent — пользователь не уведомляется. FR-018 + FR-045 explicit: «no user-visible notification» (push events are system events, не user-facing).
  - **Save flow**: handled by F-5b. ConfigSaver.saveOwn() returns Success even if push trigger fails (fire-and-forget, FR-031).
  - **Per-event-type variation**: для будущих event types (e.g. SOS) user-visible failure may be required. F-5c foundation supports both silent и surfaced modes.
  - **Action**: добавить note в spec.md: «Failure visibility — per-event-type concern. `config-updated` = silent (cache invalidation). `sos-triggered` (future) = may require user-visible failure indicator (defined в S-4 spec)».

- [⚠️] **CHK003** No silent failures of user-initiated actions.
  - **User-initiated action**: «save config» через ConfigSaver. Save itself succeeds или fails per F-5b. **Push trigger is fire-and-forget afterward** — push failure is invisible to user.
  - **Intentional design** (per Q3 Clarification): at-most-once для cache invalidation. Acceptable because recipient'ы получат свежий config через pull-on-app-open.
  - **Borderline**: «user initiated save», «push is system action triggered by save». Push не direct user action — но user'у может казаться что «save = распространение всем». Mental model gap.
  - **Action**: документировать в spec.md: «User mental model: 'save' = local persistence + cloud upload. Push is system-level optimization — failure is transparent (recipients converge through pull). User не получает feedback о push failure».

## Fallbacks

- [x] **CHK004** Fallback chain depth defined.
  - Single fallback: push fails → next-app-foreground pull (через `ConfigSaver.loadOwn`, F-5b).
  - Pull fails → use local plaintext cache (F-5b three-tier cache model).
  - Total depth: 2 levels. No cycle.

- [x] **CHK005** Fallback specified by data vs hardcoded.
  - Pull-on-app-open — hardcoded behaviour (`ConfigSaver` initialization always reads from Firestore). F-5b territory, не data-driven per-event.
  - Acceptable: only one fallback strategy для F-5c (push → pull). Не configurable per event type.

- [x] **CHK006** Final fallback failure terminal behaviour.
  - Push fail → pull fail (Firestore down) → terminal = local plaintext cache served indefinitely (F-5b three-tier cache).
  - Senior continues работать offline неограниченно. Documented в spec 018 US-4 + FR-018.
  - No crash, no user alert (graceful degradation).

## Retries

- [x] **CHK007** Retry explicit.
  - **Worker → FCM**: 3 retries, exp backoff 500ms/2s/8s (FR-009).
  - **Client → Worker**: NO retries (FR-026).
  - **Receiver → loadOwn**: no retry — если pull fails, next foreground tries again (implicit retry via app lifecycle).
  - Explicit choices documented.

- [x] **CHK008** No infinite retry loops.
  - Worker: bounded 3 attempts. After fail → 503 → return to caller.
  - Client: no retry.
  - Receiver: no retry внутри handler — `loadOwn` failure surfaces к caller (UI may show «выезжаем синхронизировать»).

- [x] **CHK009** Idempotency.
  - Client: `Idempotency-Key: UUID v4` per push action (FR-010, FR-025).
  - Worker: dedupes in KV 10-min TTL (FR-010).
  - Receiver: idempotent через debounce 2s on `triggerId` (FR-044).
  - `ConfigSaver.loadOwn` is naturally idempotent (read-only Firestore + DataStore replace).

## Offline / degraded modes

- [x] **CHK010** Offline behaviour defined.
  - **Trigger side**: если device offline во время save — `ConfigSaver.saveOwn` сам fails first (F-5b — no network for Firestore write). Push trigger даже не вызывается.
  - Если device online for save но offline by time trigger fires — `HttpPushTrigger.trigger()` returns `Outcome.Failure(NetworkFailure)`. Caller (ConfigSaver) ignores (fire-and-forget FR-031).
  - **Receiver side**: device offline → FCM queues message (TTL 4 weeks default). Online again → push delivered.
  - **Both endpoints offline beyond FCM TTL**: pull-on-app-open fallback (FR-022).

- [x] **CHK011** Stale data TTL defined.
  - **Push payload**: contains pointer не data — TTL not applicable.
  - **FCM message TTL**: 4 weeks default (Google FCM standard). Mentioned in Trouble case 1.b.
  - **JWKS cache TTL**: dynamic per `Cache-Control: max-age` from Google (FR-003).
  - **Idempotency cache TTL**: 10 min (FR-010).
  - **Rate-limit window TTL**: per windowSeconds в EventTypeRegistry entry (60s for config-updated).

## Permissions denied

- [x] **CHK012** Permission denied behaviour documented.
  - F-5c НЕ requests new Android runtime permissions (per security CHK015 — POST_NOTIFICATIONS not needed для data-only FCM).
  - Если user has denied `POST_NOTIFICATIONS` elsewhere — F-5c still works (data messages delivered без notification permission на Android 13+).

- [N/A] **CHK013** Permanent denial recovery path. Не applicable — no permission requested.

## Recovery from invalid state

- [x] **CHK014** Persistent state corruption recovery.
  - **Client-side persistence**: F-5c не вводит local persistence (Idempotency-Key generated runtime, FCM token stored Firestore).
  - **Worker-side KV corruption**: Workers KV eventually-consistent storage. Если value corrupted — next request на тот же Idempotency-Key выглядит как cache miss → re-executes (idempotent operation).
  - **Firestore directory `fcmToken` corruption**: next app foreground → `FcmTokenPublisher.publish(currentToken)` re-writes (idempotent merge).
  - **JWKS cache corruption**: jose verification fails → Worker force-refresh JWKS (per FR-003 «на kid cache miss force-refresh»).

- [x] **CHK015** No «crash and restart» as recovery.
  - All failure modes handled gracefully:
    - Worker exception → return 5xx, client ignores, recipients pull later.
    - Client exception → Outcome.Failure, ConfigSaver ignores, recipients pull later.
    - Receiver exception в `PushHandler.handle()` → wrapped в try/catch (per dev-experience CHK019 action), Logcat warning, silent ignore.

## Diagnostics

- [⚠️] **CHK016** Failures observable: diagnostic event with category, not PII.
  - **Already raised в security CHK004 + dev-experience CHK018**: logging FR pending.
  - **PushTriggerError sealed class** даёт fixed категории (Unauthorized, RateLimited, NetworkFailure, Backend) — aggregatable.
  - **Worker side**: Cloudflare Analytics emits per-request event с `eventType` + `status` labels — aggregatable.
  - **Action** (consolidating): explicit FR — все failure paths emit diagnostic event с category (PushTriggerError variant name, не error.toString()), без PII (no full UID, no payload).

- [x] **CHK017** Failures aggregated by category.
  - `PushTriggerError` — fixed sealed set: Unauthorized / RateLimited / NetworkFailure / Backend.
  - Worker analytics labels: `eventType` + `status` (success / unauthorized / rate-limited / fcm-failed / etc.). Fixed enum, не unique message strings.
  - Cloudflare Analytics доступно для rate measurement.

## Summary

- **Pass**: 13/17
- **Partial/Warning**: 3/17 (CHK001, CHK003, CHK016 — все dup'ы с другими checklists)
- **Fail**: 0/17
- **N/A**: 1/17 (CHK013)

**Big picture**: Failure recovery clean:
- Все network failure modes покрыты (offline trigger, offline receive, FCM TTL expiry, Worker down, FCM quota exhausted).
- Bounded retries, no infinite loops.
- Single fallback chain (push → pull → local cache).
- Idempotency на всех 3 уровнях (client, Worker, receiver).
- Final terminal: local plaintext cache (F-5b three-tier model) — no user-blocking failure.

**Concerns** (все dup с другими checklists, не add new actions):
1. PushTriggerError sealed class — variants не explicit перечислены в FRs (CHK001 → dup с domain-isolation FRs).
2. User mental model «save = распространение» (CHK003) — требует note в spec.md.
3. Logging FR pending (CHK016 → dup с security CHK004, dev-experience CHK018).

## Action items (priority order)

1. **Низкая** (одна правка в spec.md): добавить note для CHK003 user mental model gap.
2. **Низкая** (FR в spec.md, consolidates 3 checklists): explicit `PushTriggerError` sealed class variants + when each emitted.

---

## Заметка для новичка (TL;DR)

Проверено: правильно ли мы продумали что будет когда что-то пойдёт не так. Сеть пропала, сервер упал, квота исчерпана, телефон в самолётном режиме, FCM глюкнул.

**Хорошо сделано** (13/17):
- Все сценарии «что-то упало» имеют запасной путь: «push не доехал → клиент сам подтянет когда откроет приложение → если и это не работает → видит последнюю версию из локального кэша».
- Никаких бесконечных циклов «попробуем ещё раз». Worker делает 3 попытки и сдаётся. Клиент вообще не пытается повторить.
- Защита от дубликатов на всех 3 уровнях (клиент → сервер → получатель).
- Бабушка может оффлайн неделями — её launcher работает из локального кэша, никаких alert'ов «нет связи».

**Чего не хватает** (3 «частично», все уже подмечены в других checklists):
- Не записан явно список ошибок которые `PushTrigger` может вернуть (Unauthorized, RateLimited, NetworkFailure, Backend).
- Не записано «User думает что save = распространение всем — но на самом деле push молча fail'ит, user'у не показываем».
- Логирование — уже подняли в Security checklist.

**Не блокирует** /speckit.plan. 2 строки правок в spec.md, остальное consolidates с предыдущими actions.
