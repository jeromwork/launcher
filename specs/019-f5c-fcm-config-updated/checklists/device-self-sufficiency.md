# Checklist: device-self-sufficiency — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass

## Local viability

- [⚠️] **CHK-DSS-001** Spec explicitly declares feature mode (LOCAL-only / CLOUD-only / HYBRID).
  - **Implicit CLOUD-only**: F-5c — push channel infrastructure, inherently requires server intermediary (FCM + Cloudflare Worker + Firestore directory).
  - **НЕ explicit** в spec.md.
  - **Action** (dup с security CHK020): добавить FR-XXX: «F-5c mode = CLOUD-only. Активируется только when Sign-In completed (F-4 AuthIdentity present). Local mode (no Sign-In, no cloud) — F-5c disabled, DI provides `NullPushTrigger` no-op».

- [N/A] **CHK-DSS-002** LOCAL-only viability. F-5c не LOCAL-only.

- [x] **CHK-DSS-003** CLOUD-only justification.
  - **Cross-device sync inherently requires server**: push notification — это server-mediated transport. No way to do «device A → server → device B» without server.
  - **Cloud-action trigger**: F-5c invoked внутри `ConfigSaver.saveOwn/saveForOther(...)` — это уже cloud action (writes к Firestore), Sign-In уже completed by that point (F-4 AuthIdentity available).
  - F-5c **не triggers Sign-In itself** — relies on existing Sign-In state established by F-4 + cloud actions.

- [N/A] **CHK-DSS-004** HYBRID local-baseline + cloud-enhanced. Не applicable — pure cloud.

## Sign-In trigger point

- [x] **CHK-DSS-005** Sign-In prompt timing.
  - F-5c **не triggers** Sign-In prompt. Sign-In triggered earlier (at first cloud action — например, when user attempts `ConfigSaver.saveOwn(...)` in cloud namespace).
  - F-4 (spec 017) territory.
  - F-5c only activates **after** Sign-In completed.

- [N/A] **CHK-DSS-006** Sign-In prompt copy. Не F-5c's responsibility (F-4 territory).

- [⚠️] **CHK-DSS-007** Decline Sign-In graceful degradation.
  - If user declined Sign-In: ConfigSaver не invoked (local mode uses different code path для local persistence). PushTrigger never invoked.
  - DI wiring: realBackend flavor wires `HttpPushTrigger`, mockBackend/local-only wires `NullPushTrigger` (no-op).
  - **Не explicit в spec** (dup с modular-delivery CHK009).
  - **Action** (consolidated): explicit FR — «DI provides `NullPushTrigger` implementation для local mode (no Sign-In) — silent no-op, не throws. Production cloud mode wires `HttpPushTrigger`».

## Local→cloud promotion

- [N/A] **CHK-DSS-008** Local→cloud merge via VersionedConfigViewer.
  - F-5c **не вводит local state** который требует merge. Push subsystem stateless (Idempotency-Key runtime UUID, FCM token external, KV server-side).

- [N/A] **CHK-DSS-009** Different Google account case.
  - Не applicable — F-5c does not own user state.

## Cloud→local downgrade

- [⚠️] **CHK-DSS-010** Subscription expiry / no internet behaviour.
  - **No internet**: `HttpPushTrigger.trigger()` returns `Outcome.Failure(NetworkFailure)`. Caller (ConfigSaver) ignores (fire-and-forget FR-031). User saves succeed locally (через F-5b cache) если возможно; cloud sync resumes when online.
  - **Subscription expiry** (S-10 future): cloud features paused. PushTrigger would be no-op or fail with Unauthorized (server-side entitlement check). F-5c не enforces entitlement, S-10 territory.
  - **Не explicit в spec**.
  - **Action**: добавить note в spec.md: «Subscription gating: F-5c не enforces entitlement. S-10 (Subscription Server Timer) controls Worker's authorisation per UID. Если entitlement expired → Worker returns 401, client gracefully degrades к pull-on-app-open. Locally-cached config remains available indefinitely».

- [N/A] **CHK-DSS-011** User shown WHAT stopped working.
  - F-5c invisible to user — no UI notice. S-10 (entitlement UX) handles user-facing «subscription expired» messaging.

## Anti-patterns to refuse

- [x] **CHK-DSS-012** No mandatory Sign-In at first launch.
  - F-5c не triggers Sign-In. Sign-In timing controlled by F-4 / first cloud action.

- [x] **CHK-DSS-013** No mandatory pairing at first launch.
  - F-5c не requires pairing. Cross-UID delegation (для `saveForOther`) requires grant, но это уже existing pairing flow F-5b/S-2.

- [x] **CHK-DSS-014** No bottleneck local feature behind cloud dependency.
  - F-5c IS the cloud feature. Не bottlenecks local features.
  - **Module dependency**: `core/push/` becomes hard dependency для realBackend flavor (cloud), но не для local mode (mockBackend doesn't compile push real impl, uses NullPushTrigger).

- [x] **CHK-DSS-015** No anonymous Firebase Auth fallback.
  - F-5c использует real Firebase ID-token (named Google Sign-In per F-4 spec 017). Anonymous auth removed per decision 2026-05-30.

## Cross-spec consistency

- [N/A] **CHK-DSS-016** Cross-spec interaction — local device sees cloud data unavailable.
  - F-5c invisible to user. Если push not delivered (network failure, subscription expired) — UI doesn't reflect F-5c failure directly. ConfigSaver.loadOwn pull fallback gives user fresh state when возможно; otherwise local cache served.
  - F-5c integration into other spec'ы: consumer (ConfigSaver, future SosService) handles Outcome.Failure от PushTrigger как fire-and-forget.

- [x] **CHK-DSS-017** Outcome/Result types с explicit cloud-unavailable handling.
  - `PushTrigger.trigger()` returns `Outcome<Unit, PushTriggerError>`.
  - `PushTriggerError` sealed: `Unauthorized`, `RateLimited`, `NetworkFailure`, `Backend(message)`.
  - Cloud-unavailable explicitly modeled (NetworkFailure).
  - Consumers handle gracefully (FR-026 — client no retries, FR-031 — fire-and-forget).

## Summary

- **Pass**: 7/17
- **Partial/Warning**: 3/17 (CHK-DSS-001, CHK-DSS-007, CHK-DSS-010)
- **Fail**: 0/17
- **N/A**: 7/17

**Big picture**: F-5c respects device-self-sufficiency principle:
- **CLOUD-only justified**: push channel inherently server-mediated.
- **No mandatory Sign-In at first launch** — F-5c only activates after F-4 Sign-In completed.
- **Graceful degradation** через `Outcome<Unit, PushTriggerError>` + caller fire-and-forget + pull fallback.
- **Local mode supported** через DI flavor split (NullPushTrigger).
- **Subscription pause** handled by S-10 territory, F-5c just respects 401.

**Concerns** (all consolidated с предыдущими checklists — same actions):
1. Mode declaration «CLOUD-only» не explicit (dup с modular-delivery, security CHK020).
2. NullPushTrigger fallback не explicit FR (dup с modular-delivery, security).
3. Subscription expiry / cloud-unavailable behaviour не explicit (новый action).

## Action items (priority order)

1. **Высокая** (consolidates 3 checks across 4 checklists, **одна** правка в spec.md): добавить «Cloud-mode integration» block:
   - «F-5c mode: CLOUD-only. Активируется after Sign-In (F-4 AuthIdentity present)».
   - «Local mode (no Sign-In): DI wires `NullPushTrigger` no-op. Push subsystem silent».
   - «Subscription expiry (S-10): Worker returns 401, client degrades к pull-on-app-open. Local cache preserved».
   - «No internet: `Outcome.Failure(NetworkFailure)`, fire-and-forget by caller, recipient pulls fresh on next foreground».

---

## Заметка для новичка (TL;DR)

Проверено: соблюдает ли F-5c принцип «каждый телефон самодостаточен» (важная политика проекта 2026-06-15) — приложение должно работать **полностью** без Google Sign-In и интернета, бессрочно. Только когда пользователь сам начинает облачную операцию (например, привязать второй телефон) — тогда просим Sign-In.

**F-5c — это CLOUD-only фича** (push нужен сервер для доставки), но это **OK** потому что:
- F-5c **не требует** Sign-In при первом запуске.
- F-5c **активируется только** когда пользователь уже сделал Sign-In для других cloud features (например ConfigSaver).
- В local mode (без Sign-In) F-5c **не существует** — DI просто подставляет «заглушку» (NullPushTrigger), которая ничего не делает. Никаких crash'ей, никаких alert'ов «нужен Sign-In».
- Если сеть пропала или подписка истекла — fallback на «подтянуть свежие данные при открытии приложения». Local cache работает всегда.

**Чего не хватает** (3 «частично» — все consolidated в одну правку):
- Не записано явно в spec.md что F-5c — CLOUD-only.
- Не записано явно про NullPushTrigger fallback в local mode.
- Не записано явно что происходит при subscription expiry (Worker вернёт 401, клиент graceful degrade).

**Не блокирует** /speckit.plan. Закрывается **одним** блоком «Cloud-mode integration» в spec.md (~10 строк).
