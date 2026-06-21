# Checklist: capability-registry-readiness — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass

> **Big picture upfront**: F-5c — это **transport infrastructure** (cache invalidation push channel), не user-facing capability layer. `PushTrigger.trigger(...)` — internal RPC между нашим client и нашим Worker, не action которое user или AI agent invoke напрямую. AI Affordance section spec'а explicitly declares «No AI affordance — internal infrastructure capability only». Therefore этот checklist в основном **N/A**.
>
> Будущие event types (SOS в S-4, message-send в V-2 messenger) — **их** spec'и обязаны проходить этот checklist (они expose user-facing actions которые AI agent может voice-trigger). F-5c foundation **поддерживает** их, но сам не expose'ит actions.

## Sewing-point bookkeeping

- [N/A] **CHK-CR-001** TODO(capability-registry) для каждого нового action.
  - F-5c **не вводит user-facing action**. PushTrigger.trigger() — internal infrastructure verb, не user action.
  - Конкретные actions которые USE F-5c (e.g. «save config and propagate», «trigger SOS») — defined в other spec'ах (008 rewrite, S-4).
  - **Когда S-4 SOS spec будет written** — он добавит `TODO(capability-registry): declare capability для trigger_emergency` в SosService.kt. F-5c не его место.

- [N/A] **CHK-CR-002** TODO location at dispatcher / provider site.
  - Не applicable — F-5c doesn't dispatch user-facing actions.

- [N/A] **CHK-CR-003** Action intent name stable.
  - **EventType wire-values** (`config-updated`, `sos-triggered`, ...) — stable slug-cased, will serve as event-type keys. Если future capability registry maps capability → event-type → push, эти wire-values уже стабильны.
  - Не strictly capability intent names (это actions, не events), но similar discipline applied.

- [N/A] **CHK-CR-004** Action params typed.
  - F-5c payload — `Map<String, String>` (generic для extensibility across event types).
  - **Это NOT capability params** (это event payload).
  - Каждый специфический event type должен иметь typed payload в своём consumer (e.g. SosService.reportSos(latitude: Double, longitude: Double) — typed). Per-event-type typing — consumer responsibility.

## Provider neutrality

- [x] **CHK-CR-005** Spec не names AI/voice/MCP provider.
  - AI Affordance section: «No AI affordance — internal infrastructure capability only».
  - Никаких упоминаний Google Assistant / Gemini / OpenAI / Claude / MCP server / iOS Shortcut / Alexa.
  - Future AI features упоминаются только abstractly: «Future AI features will read DataStore directly post-refresh — they do not invoke or observe F-5c push channel itself».

- [x] **CHK-CR-006** Spec не imports AI/voice SDK.
  - Dependency list (implicit через `core/push/build.gradle.kts`): kotlinx-coroutines, kotlinx-serialization, Ktor client. **Никаких AI SDK**.

- [x] **CHK-CR-007** Spec не defines exposure adapter implementation.
  - Нет ExposureAdapter implementation в scope F-5c.
  - Future Capability Registry F-2 (Phase 4+) будет collecting capabilities; F-5c не предjudицирует.

## Voice / conversational surface

- [N/A] **CHK-CR-008** Voice-triggerable action — voicePhrases TODO.
  - F-5c сам по себе не voice-triggerable. Cache invalidation — system event, не user voice command.
  - Future event types may be voice-triggerable (например SOS «помощь!» voice phrase) — это S-4 territory.

- [N/A] **CHK-CR-009** Confirmation requirement.
  - F-5c trigger — implicit user action (после save). Не destructive/irreversible (re-pushing same config — idempotent through Idempotency-Key).
  - Future destructive events (e.g. delete album in V-3) — declarations в их specs.

## Auth / scope hints

- [N/A] **CHK-CR-010** Auth scope в TODO.
  - F-5c сам имеет auth — per-event-type authorisation rules в EventTypeRegistry. Это **server-side authorisation**, не capability-registry auth scope (которое о user permissions).
  - Future event types (например SOS = `device-local`, config-updated = `pair-authorised`) — declarations в Worker EventTypeRegistry entries уже capture это.
  - **Pre-existing**: spec.md Reuse pattern table даёт «Authorisation» column для всех 9 known event types. Это пре-emptive capability auth mapping для future F-2 collection.

- [N/A] **CHK-CR-011** Idempotency declared.
  - F-5c **strongly идемпотентен** через Idempotency-Key UUID v4 + Worker KV dedupe + receiver debounce.
  - Per-event-type идемпотентность — определяется в каждом event semantics:
    - `config-updated` = idempotent (re-save same content = same state).
    - `sos-triggered` = **non-idempotent** technically (каждый SOS — отдельный alert), но receiver-side acknowledgement makes it effectively idempotent within window.
    - `entitlement-expired` = idempotent (state transition).

## F-2 collection readiness

- [⚠️] **CHK-CR-012** Entry в `docs/dev/capability-registry-pending.md`.
  - **Не addressed в этом spec'е**. Файл может не существовать вообще (F-2 deferred к Phase 4).
  - F-5c **не вводит user-facing capability** — нечего добавлять в registry.
  - **Однако**: F-5c **enables** future event-types через PushTrigger foundation. Когда S-4 SOS spec будет written — он добавит SOS capability в `capability-registry-pending.md` (если файл существует к тому моменту), не F-5c.
  - **Action** (informational): future spec authors (S-4, S-9, V-2) — make sure to add their user-facing capabilities в `capability-registry-pending.md` при их написании. F-5c spec.md уже содержит Reuse pattern table как «pre-emptive registry data».

## Summary

- **Pass**: 3/12
- **Partial/Warning**: 1/12 (CHK-CR-012 — informational future concern)
- **Fail**: 0/12
- **N/A**: 8/12

**Big picture**: F-5c is **infrastructure transport** (not user-facing action surface). Capability-registry checklist в основном N/A. Critical compliance:
- ✅ No AI/voice/MCP provider naming (provider-neutral).
- ✅ No AI SDK in dependencies.
- ✅ No ExposureAdapter implementation.

F-5c foundation **enables** future capability-bearing event types (S-4 SOS, V-2 messenger) через generic event dispatch. Каждый из них должен пройти capability-registry-readiness checklist в своём spec'е.

## Action items

**None для F-5c сам по себе.**

**Future guidance** (informational, не блокирует F-5c):
- When S-4, S-9, V-2 specs write user-facing actions which trigger F-5c events — those specs add inline `TODO(capability-registry)` в consumer code + entry в `capability-registry-pending.md`.
- F-5c spec.md уже содержит Reuse pattern table (event type × authorisation × rate-limit × collapse × priority) — это pre-emptive data для future F-2 collection.

---

## Заметка для новичка (TL;DR)

Проверено: оставили ли мы «крючки» для будущего Capability Registry (F-2 — это система, которая в Phase 4 соберёт все «команды/действия» приложения и сделает их доступными для AI/voice ассистентов).

**F-5c здесь не при чём — это транспорт, не команда.** Когда пользователь говорит «сохрани конфиг» — это команда (живёт в spec 008). Когда система говорит «эй, разошли всем что обновилось» — это transport (живёт в F-5c). Capability Registry собирает первое, не второе.

**Что важно для F-5c** (всё ✅):
- Не упомянули ни одного конкретного AI-провайдера (Google Assistant, Gemini, OpenAI, Claude) в коде/спеке.
- Не подключили AI SDK в зависимости.
- Не реализовали никакого «exposure adapter» — это будущая работа в Phase 4.

**Что делать когда придёт время** (информационно, не сейчас):
- Когда будет писаться spec S-4 SOS — там должна быть `TODO(capability-registry)` про команду «trigger_emergency».
- Когда будет писаться spec V-2 Messenger — то же про «send_message».
- F-5c foundation им предоставляет транспорт, capability-registry собирает уже их user-facing команды.

**Не блокирует** ничего — F-5c compliant. Никаких action items.
