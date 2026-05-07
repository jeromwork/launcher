# Contract: Diagnostics Events v2

**Version**: 2.0.0 (replaces `CommunicationDiagnostics` from spec 002)
**Owner**: `:core/commonMain` — `ProjectEvent` extension
**Emitter**: `AndroidActionDispatcher` (`androidMain`).
**Consumer**: `EventRouter`; future `docs/operations/support-and-feedback-ops.md` triage path.

---

## Why "v2"?

Spec 002's `CommunicationDiagnostics` was WhatsApp-specific (handoff success/failure, return restore outcome). Removed in spec 005. This contract replaces it with a **provider-agnostic** event taxonomy.

---

## Event: `ProjectEvent.ActionDispatched`

```kotlin
data class ActionDispatched(
    val providerId: ProviderId,
    val resultKind: ResultKind,
    val fallbackUsed: Boolean,
    val timestampMs: Long,
) : ProjectEvent()

enum class ResultKind { Ok, BlockedByPolicy, ProviderUnavailable, Failure }
```

### Field whitelist (per security CHK-004 / CHK-022)

These four fields are the **complete** set. **Adding fields requires a contract major-bump and explicit security review.**

| Field | Type | Allowed because |
|-------|------|-----------------|
| `providerId` | `ProviderId` (string-backed) | Provider category — not PII; useful for aggregating dispatch rates per provider. |
| `resultKind` | enum | Outcome category — not PII. |
| `fallbackUsed` | boolean | Whether fallback chain was traversed — useful for measuring "primary unavailable" rate. |
| `timestampMs` | `Long` | Wall-clock time for ordering and rate computation. |

### Field blacklist

These fields **must never** appear in `ActionDispatched`:

- Phone numbers, contact refs, contact display names.
- URLs, deep-link payloads.
- `Custom.params` keys or values.
- `sourceModuleId` (per Article XIV §3 — feature-level identifier could be re-identifying in small populations).
- User-readable `Failure.reason` strings if they could quote payload content.

**Why**: per Article XIV §3 (no hidden behavioural data collection) and OWASP MASVS-STORAGE-2 (no PII in logs).

---

## Emission rules

- **Triggered**: at the end of `AndroidActionDispatcher.dispatch()`, exactly once per top-level `dispatch()` call (not per fallback recursion).
- **Threading**: emitted on the dispatcher's coroutine — same context as the original `dispatch()` call. Not posted to a separate thread.
- **Frequency**: at most one event per user-initiated action; UI debounce (§7.6) ensures rapid duplicate taps don't multiply events.
- **Battery cost**: negligible — synchronous in-memory emit to `EventRouter` SharedFlow.

---

## Consumer rules

Consumers of `ActionDispatched`:

1. **Must not** persist event payload to disk (this contract guarantees no PII; persistence layer unaffected).
2. **Must not** forward events outside the device process — until a future spec (007? 008?) introduces explicit remote-telemetry contract with user opt-in.
3. **May** aggregate counts in-memory for diagnostic surfaces (e.g., "5 actions dispatched this session, 1 fallback used").

---

## Versioning

- **2.0.0** — initial v2 contract (this document). Major bump from v1's `CommunicationDiagnostics` because event shape and emitter are completely different.
- **Future minor bumps**: only adding new event kinds (e.g., `ProjectEvent.ProviderRegistryUpdated`). Field-set of existing events is **frozen** unless major-bumped.
- **Future major bumps**: changing field set or types. Requires explicit security re-review for each new field per the whitelist rules above.

---

## Test coverage requirement

- `AndroidActionDispatcherIntegrationTest.emitsActionDispatchedOnSuccess` — fake registry returns Available; dispatch happens; exactly one event emitted with correct `(providerId, Ok, fallbackUsed=false)`.
- `AndroidActionDispatcherIntegrationTest.emitsActionDispatchedOnFallback` — primary fails; fallback succeeds; one event emitted with `(providerId=primary, Ok, fallbackUsed=true)`.
- `AndroidActionDispatcherIntegrationTest.emitsActionDispatchedOnUnavailable` — primary unavailable, no fallback; one event with `(providerId, ProviderUnavailable, fallbackUsed=false)`.
- `EventTaxonomyTest.actionDispatchedFieldSetIsFrozen` — Konsist test asserting `ActionDispatched` has exactly 4 properties with exactly the declared types. Catches accidental field additions in PRs.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Контракт `ProjectEvent.ActionDispatched` v2.0.0 — событие, которое эмитится один раз в конце каждого `dispatch()`. Заменяет `CommunicationDiagnostics` от spec 002 (она была WhatsApp-specific). Provider-агностичное.

**Конкретика, которую стоит запомнить:**
- Whitelist полей — ровно 4: `providerId: ProviderId`, `resultKind: ResultKind`, `fallbackUsed: Boolean`, `timestampMs: Long`. Точка.
- Blacklist (никогда): телефонные номера, contactRef'ы, имена контактов, URL, deep-link payload, `Custom.params`, `sourceModuleId`, текст `Failure.reason` если он может цитировать payload.
- Эмит ровно один раз на top-level `dispatch()` (не на каждую fallback-рекурсию). На том же coroutine-контексте, не на отдельном потоке.
- Consumer rules: не персистить на диск, не отправлять с устройства (до отдельного спека с opt-in пользователя).
- Защита: `EventTaxonomyTest.actionDispatchedFieldSetIsFrozen` — Konsist-тест, ловит добавление 5-го поля в PR.

**На что смотреть с осторожностью:**
- Любое предложение добавить поле (например, `presetId`, `contactCount`) = новый major + security review. Обычно — отказ.
- Когда дойдёт до spec 007 (backend) — помнить правило «не отправлять с устройства без opt-in».
