# Engineering rules

These are inviolable rules for designing, writing, and reviewing code in this repository.

## 1. Domain isolated from infrastructure

Domain code — the pure-logic layer that expresses what the product does — MUST NOT import:

- vendor SDKs (cloud, payment, scanning, messaging, analytics, crash-reporting, etc.),
- transport types when used as a wire format (HTTP clients, serialization framework annotations, raw JSON containers),
- platform system types of the host OS (intents, URIs, bundles, contexts, lifecycle owners) — these belong in adapters, not in domain values.

Allowed in domain: pure language standard library, coroutine and flow primitives, other domain types, plain time and identifier types.

Each external surface is exposed through a port (interface declared in the domain layer) implemented by an adapter (separate module that owns the SDK or system-call). Domain talks only to ports.

## 2. Anti-Corruption Layer for every external dependency

Wrap every external SDK or service so its types never appear in any signature visible to the domain or to other features. The wrapper exposes a small, stable, testable interface owned by us.

Test: *if the vendor disappeared tomorrow, how many files would I need to change?* If more than the files in one adapter module, the wrapping is wrong.

## 3. Decisions: one-way doors vs two-way doors

Before any non-trivial change, classify the decision:

- **Two-way door** (reversible in days, no data migration, no external impact): decide quickly, default to action, course-correct if wrong.
- **One-way door** (data migration, wire-format change, public-contract change, external announcement, store re-review, billing-provider switch, license-change, identifier choice that ties identity): slow down. Write alternatives considered, regret conditions for each, and the **exit ramp**. Require explicit user approval when the user has been part of the conversation.

For every one-way door the exit ramp is non-optional: *"if we wanted to leave this choice, what would it cost and how would we do it?"* If you cannot answer, the decision is not ready.

## 4. Minimum Viable Architecture, not Minimum Viable Product

Add an abstraction today **only** if not adding it would force a future *rewrite* (not just a future *addition*).

- Test 1: *if I removed this abstraction and inlined it, what would I lose?* If only optionality I do not currently need — remove it.
- Test 2: *if the dependency on the other side of this seam doubled in price, was deprecated, or violated a privacy rule, how long would it take to swap?* If more than a day — keep the seam.

## 5. Wire-format and contract versioning

Anything that leaves the device or persists across app versions is a wire format and behaves like a public API:

- Carries an explicit schema-version field from the first commit.
- Backward-compatible reads MUST be possible for at least one major release.
- Adding fields is fine; renaming or removing requires a versioned migration written **before** the breaking change ships.

This applies to: persisted configuration, documents in any cloud store, QR-code payloads, deep-link URLs, exported config files, persisted preferences with non-trivial structure.

## 6. Mock-first development

Build domain types and UI against in-memory or fake adapters before integrating real SDKs. Every external port has at least:

- one fake adapter for tests and dev,
- one real adapter with the actual SDK,
- dependency-injection wiring that picks the right one per build.

This forces port shapes to be domain-driven, unblocks testing without provisioning external services, and keeps tests fast.

## 7. Fitness functions where feasible

When an architectural invariant can be checked automatically, prefer that to manual review. Examples: import-restriction lint rules, module-dependency-graph checks, contract roundtrip tests, secret-leak pre-commit hooks. Manual review for things a machine cannot judge; automation for things it can.

## 8. Server migration tracking

Любое решение, использующее «бесплатный обход» вместо собственного серверного компонента, обязано быть зафиксировано в [`docs/dev/server-roadmap.md`](docs/dev/server-roadmap.md) с маршрутом миграции на собственный сервер.

Сейчас мы используем:
- **Cloudflare Worker** (бесплатный tier `*.workers.dev`) вместо собственного API.
- **Firestore Security Rules + клиентские транзакции** вместо Cloud Functions или нашего backend'а.
- **In-memory rate-limit** в Worker'е вместо persistent KV / БД.
- **Firebase Spark plan** (без Cloud Functions, без Cloud Storage at scale).
- **Client-side housekeeping** (например, retention для config history) вместо server-side cron / triggers.

Каждый раз, когда мы выбираем client-side / no-server путь там, где «правильное» решение — server-side (integrity, atomicity, no spoofing), обязательно:
1. **Записать в `docs/dev/server-roadmap.md`** конкретную задачу под «когда поедем на свой сервер».
2. **Inline-TODO в коде/спеке**: `// TODO(server-roadmap): <операция> должна перейти на сервер ради <integrity | atomicity | privacy | scale>`.

Это даёт **specific exit ramp destination** для one-way door'ов (rule 3) — куда именно мы отступаем, а не «как-нибудь потом».

## Refuse and propose alternative if you see

1. Vendor or system type embedded in a domain value.
2. Domain function returning a transport or DTO type.
3. UI calling an SDK directly.
4. Static singleton holding an external SDK instance.
5. Wire format without a schema-version field.
6. Schema field renamed without a migration written first.
7. Test that mocks the domain instead of the adapter.
8. One-way door taken without an exit ramp.
9. Premature abstraction: single-implementation interface with no port-shaped seam.
10. New external dependency added to the domain layer for one feature.
11. Public contract change without a major-version bump.

For each: surface the issue in one sentence, propose the corrected shape, then continue.

## Output discipline

- Commits represent coherent logical units, one reviewable concern each.
- Push after each significant step, not after several piled up.
- Never use hook-bypassing flags (no-verify, no-gpg-sign, etc.) without explicit user approval.
- Never commit secrets, signing keys, service-account credentials, or machine-local configuration.

## Branching

- Every feature ships in its own branch named after the feature, not on `main`. Spec-driven work uses the spec slug — for example `003-ui-skeleton`, `004-action-architecture-v2`. Non-spec work uses a short kebab-case name describing the change.
- `main` is integrated through pull requests only. Direct commits to `main` are reserved for trivial repository-level chores (CLAUDE.md, .gitignore, root README) and only when no feature branch is open for the same area.
- Open the PR as soon as the branch has a reviewable first commit; keep pushing into it as work progresses. Do not pile up local commits on a feature branch without pushing.
- One feature = one branch = one PR. If scope grows, split into a follow-up spec and a follow-up branch rather than widening the current PR.

For tests:

- Every port has at least a fake-adapter test and a contract test.
- Every wire format has a roundtrip test (write → read → assert equal) and a backward-compat test (read previous schema-version).

## Conflict resolution

When a user instruction would violate a rule above, surface the conflict in one sentence — for example: *"this would couple a vendor SDK type into the domain — proceed anyway, or wrap as a port?"* — and continue based on the answer.

## Discussion mode → invoke `mentor` skill

Когда сообщение пользователя — **обсуждение, а не исполнение**, обязательно вызвать skill `mentor` (`.claude/skills/mentor/SKILL.md`) ДО любого содержательного ответа.

Discussion-сигналы (любой достаточен):
- «я новичок в [X]», «не знаю X», «помоги разобраться с X», «X — что это?», «объясни X»;
- выбор / оценка: «что лучше — A или B?», «стоит ли...?», «можно ли заменить X на Y?», «какие альтернативы?»;
- how-it-works: «как работает X?», «почему X себя ведёт так?»;
- вставленный mentor-промт («веди себя как наставник», «составь карту темы», «задай N уточняющих вопросов»);
- **архитектурное / one-way-door решение** в середине разговора (выбор технологии, SDK, схемы данных, identity-модели, payment provider, persistence, deployment target) — даже без явного вопроса;
- **пользователь выбрал что-то из предложенных вариантов** — это сигнал перепроверить выбор, а не зафиксировать. Mentor запускается на «ок, берём A» так же, как на «что лучше?».

НЕ вызывать `mentor` для task-сообщений: «сделай X», «запусти Y», «исправь Z», «закоммить», «создай PR», «обнови файл», `/speckit.*` и иные явные slash-команды.

Если граница нечёткая или пользователь делает архитектурный выбор — предпочесть `mentor`. Лучше лишний раз обсудить, чем молча выполнить решение, которое потом дорого откатывать. Escape: пользователь может явно написать «без mentor, коротко» — тогда пропустить шаги 1-5 и дать рекомендацию сразу с пометкой best-guess.
