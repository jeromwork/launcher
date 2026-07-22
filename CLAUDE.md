# Engineering rules

These are inviolable rules for designing, writing, and reviewing code in this repository.

## 1. Domain isolated from infrastructure

Domain code — the pure-logic layer that expresses what the product does — MUST NOT import:

- vendor SDKs (cloud, payment, scanning, messaging, analytics, crash-reporting, etc.),
- **vendor serialization frameworks** — reflection-based or annotation-heavy mappers that pull in an external runtime: Jackson, Gson, Moshi, Firebase document mapping, JPA/`javax.persistence`. These are infrastructure; they belong in adapters.
- transport types when used as a wire format (HTTP clients, raw JSON containers),
- platform system types of the host OS (intents, URIs, bundles, contexts, lifecycle owners) — these belong in adapters, not in domain values.

Allowed in domain: pure language standard library, coroutine and flow primitives, other domain types, plain time and identifier types, **and `kotlinx.serialization` annotations** (`@Serializable`, `@SerialName`, `@JsonNames`, `@EncodeDefault`, custom `KSerializer`).

**Why `kotlinx.serialization` is allowed but Jackson is not** (decided in [TASK-144](backlog/tasks/task-144%20-%20Decision-are-wire-formats-domain-types-or-DTOs-—-resolve-the-rule-1-tension.md), 2026-07-21): `kotlinx.serialization` is a compile-time Kotlin language facility — the compiler generates the serializer, no runtime reflection, no vendor lock, removable without touching a single domain concept. The rule-1 ban targets *vendor coupling*; `kotlinx.serialization` creates none. Forcing a separate DTO twin for all ~17 non-crypto formats would be pure mapping-fatigue with no rewrite avoided (rule 4 MVA). Industry splits domain from serialization where coupling is expensive (Java+Jackson, extractable SDKs); it does not for compile-time, language-native serialization on a stable domain.

**Crypto is the exception — no version, no serialization in the crypto type at all.** Types in `:core:crypto` / `:core:keys` (the extractable crypto SDK) MUST carry neither a version field nor serialization annotations. Version and wire live in a layer *above* crypto (adapter/DTO); the crypto primitive receives already-built opaque bytes (e.g. AAD) and never learns what is in them. This is stricter than the allowance above and it wins for crypto types. Grounding: no industry AEAD (age, JWE, Tink, libsodium) puts version inside the primitive — the version is a cleartext header assembled above it. See [TASK-141](backlog/tasks/task-141%20-%20Crypto-wire-formats-move-version-handling-out-of-the-crypto-modules-retire-legacy-paths.md).

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

Anything that leaves the device or persists across app versions is a wire format and behaves like a public API.

**The rules live in one place: [`docs/architecture/wire-format.md`](docs/architecture/wire-format.md)** — version field shape, reader/writer gating, unknown-version behavior, change discipline, tests, and the pre-MVP override. Read its AI-TLDR before changing any versioned format. Do not restate those rules here or anywhere else; that file wins on conflict and must be updated in the same commit as any rule change (`wire-format.md` §12). Skill: `wire-format` (rules) / `checklist-wire-format` (spec audit).

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

## 9. Shareability-readiness for non-identity configurations

Любая user-facing конфигурация, **не зависящая** от identity / secrets / PII / device-specific state, **должна** быть спроектирована как **portable shareable artifact с первого коммита**, даже если sharing UI не строится сейчас.

Применяется к: layout templates, tile arrangements, custom action mappings, theme variants, tutorial sequences, wizard manifests, preset configurations — всё, что один пользователь может **разумно захотеть** поделиться с другим (или импортировать из community / marketplace в будущем).

Требования:
- Хранится в wire-format (JSON / Protobuf / etc.) с явным `schemaVersion` (это rule 5, применённый специфически).
- **Обезличенная форма**: никаких identity-bound значений внутри (нет UID'ов, Firebase токенов, package-specific identifiers текущего устройства, contact phone numbers, photo blob references из приватного storage).
- Загружается через **adapter pattern** (`ConfigSource` или аналог), где `BundledSource` — **одна из** реализаций. Другие (`ImportFromFileSource`, `NetworkSource`, `ShareIntentSource`, future `MarketplaceSource`) добавляются **additively**, без изменения формата config'а.
- Roundtrip-тест + cross-device-test с первого коммита.
- Inline-TODO у `BundledSource` сайта: `// TODO(shareability): future ConfigSource adapters — file import, share intent, marketplace`.

**Что это правило НЕ требует**:
- Строить sharing UI сейчас.
- Строить curated marketplace.
- Строить import-from-file UI.

**Что это правило ОБЕСПЕЧИВАЕТ**:
- Когда sharing добавляется позже — это additive change (новый `ConfigSource` adapter), **не rewrite** существующих template'ов.
- Cross-version compatibility (user X на app v1.2 шарит template'ом, который user Y на app v1.3 принимает).
- Privacy by design: невозможно случайно зашить identity в shareable artifact, если структура запрещает identity поля.

**Что НЕ применяется**:
- Identity-bound state: pairing keys, Firebase tokens, contact PII, photo blobs — это **НЕ shareable**, они **остаются на устройстве** или зашифрованы для конкретных recipient'ов.
- Application code / system permissions — не shareable through this mechanism.

Combined с rule 2 (ACL) и rule 5 (wire-format versioning), это правило делает shareability **архитектурным свойством**, а не afterthought.

## 10. Notification minimization (push hygiene)

User-facing push notifications должны быть **минимизированы**. Каждый push должен **обосновать своё существование** через explicit severity criterion. Без такого обоснования push не существует.

**Иерархия предпочтений** (от naturally preferred к last-resort):
1. **In-app indicator** (badge, banner, list item на главном экране app'а) — default. Не disrupts user'а, виден при следующем открытии app'а.
2. **In-app notification center** (collected nested notifications внутри app'а — список «что произошло пока вас не было») — для accumulated low-urgency events.
3. **System push notification** — **последняя инстанция**, только если все три критерия соблюдены (см. ниже).

**Каждая push notification обязана** соответствовать **трём критериям одновременно**:
- **Actionable**: user может (или должен) что-то сделать прямо сейчас.
- **Time-sensitive**: ждать до момента следующего открытия app'а **нельзя** (потеря value или risk).
- **User-relevant**: касается user'а лично, не aggregate state системы.

**Применение к нашему контексту** (примеры):
- ✅ SOS triggered → push admin'у (actionable + time-sensitive + relevant).
- ✅ Family member call coming → system ringtone (built-in OS mechanism, **не** наш push).
- ❌ «У вас новое фото от внука» → in-app indicator, **не** push.
- ❌ «Бабушка online» → не notification **вообще**.
- ⚠️ «Permission revoked» → in-app banner; push **только если** ROLE_HOME revoked (critical loss).
- ❌ «X выполнил активность Y» (gamification) → in-app indicator, **никогда** push.

**Spec author requirement**: при добавлении push event'а в спеку обязана быть declared **severity criterion** и **actionable destination**. Без этого refuse.

**Соответствующий skill**: `checklist-notification-minimization` (создаётся для активации через `procedure-assess-spec-complexity` при обнаружении push events в спеке).

## 11. Architecture decisions live in backlog (Discussion → Decision → Done)

Cross-cutting архитектурные решения (крипто-стек, backend-топология, UX-конвенции, i18n) ведутся как **обычные backlog-task'и** с новым статусом `Discussion`, не в отдельных `docs/dev/*-mentor-overview.md` файлах.

**Rationale** (введено 2026-07-02, revised from earlier «SoT-file» pattern): monolithic 2000-строчный mentor-overview файл не масштабируется. Backlog UI уже умеет tasks + Kanban + dependencies. Discussion = task в новом статусе. Decision = Decision block внутри task'а с **mutability window** (см. ниже).

### Mutability window (revised 2026-07-06)

Decision block **mutable до начала implementation**, **immutable после**. Точка перехода — task `In Progress` **c реальными изменениями в кодовой базе** (не просто status change).

**Rationale**: pre-implementation Decision — snapshot текущего понимания владельца/AI mentor-сессии. Mentor process включает ошибочные ранние выборы (Google Sign-In как bootstrap gate у TASK-106 — отброшено; peer-admin kick у TASK-102 — переработано). Эти ошибочные выборы не должны навсегда висеть в истории task-файлов, замусоривая context для future AI sessions и owner review. Post-implementation Decision — контракт для реального кода, изменять его = breaking change для callers.

**При revision pre-implementation task'а — редактируй Decision block напрямую**, не создавай `decision-supersedes` task (это только для post-implementation revisions). Опционально: добавь короткий «Revision note (YYYY-MM-DD): <what changed briefly>» в конец Discussion section — не для истории, а чтобы current AI session знала контекст последнего change'а.

### Workflow

Каждое архитектурное решение = один task, проходит:

```
Discussion → Draft → In Progress → Verification → Paused → Done
```

- **Discussion**: активная mentor-сессия. Task существует, обсуждение идёт (mermaid sequences, alternatives, questions), decision не зафиксирован.
- **Draft**: обсуждение завершено, `### Decision (English)` sub-блок заполнен, task готов к `/speckit.specify`. Decision block **mutable** — если revisit'нули решение перед началом implementation, редактируй напрямую.
- **In Progress + real code**: с момента когда первый implementation commit ссылается на Decision block — **Decision становится immutable**. Дальнейшие revisions требуют `decision-supersedes` task per rule 11.
- **Done**: implementation завершена, Decision block остаётся immutable как archival контракт для downstream tasks.

Формат — см. `.claude/skills/backlog-task-format/SKILL.md` секция «Discussion workflow + Decision block».

### Cross-task references — только через dependencies

Task **не должен** цитировать «внутренности» другого task'а. Только:
- `dependencies: [TASK-N]` в frontmatter — указывает зависимость.
- В prose: «See TASK-N Decision» — если действительно надо указать конкретное место.

AI при работе над dependent task **читает Decision block** dependency (English, current) чтобы понять контракт. Не читает весь discussion — это context owner'а, не AI. Если dependency ещё pre-implementation — Decision может измениться, check status и `Revision note` в конце Discussion при работе.

### Boundary rule vs ADR-011 «Sequences in spec.md»

Разграничение (чтобы не пересекались):

- **`SECTION:DISCUSSION` в backlog-task** = **«почему это решение»** (was decided). Mermaid sequences здесь описывают *варианты* или *attack scenarios* обсуждаемые.
- **`## Sequences` MENTOR-DETAIL блок в spec.md** = **«как это работает в runtime»** (behavior). Sequences здесь описывают *реализованное* поведение.

Один и тот же mermaid не должен появляться в обоих местах. Discussion sequences — гипотетические / сравнительные; spec.md sequences — финальные реализованные.

### Frontmatter поля для decision-task'ов

```yaml
decision-supersedes: []             # TASK-M списком если этот task заменяет старое решение
superseded-by: null                 # TASK-K если этот task сам заменён новым
```

Обычные feature task'и (`TASK-27` messenger, `TASK-67` pairing feature) могут пропускать эти поля — они опциональны, только для decision-task'ов.

### После mentor-сессии — три action'а в том же коммите

1. **Update task file** (Discussion → Draft если сессия завершила решение): заполнить `### Decision (English)` block + сдвинуть status. Decision block mutable до начала implementation — редактируй при revisions.
2. **Update dependency graph** downstream task'ов: если новый decision-task создан — соответствующие feature task'и получают его в `dependencies:`.
3. **Опционально запустить `procedure-decision-drift-check`** — walk dependencies, verify downstream tasks не references устаревшего Decision.

### Skills

- `.claude/skills/backlog-task-format/SKILL.md` — формат task-файла + Discussion + Decision block.
- `.claude/skills/procedure-decision-drift-check/SKILL.md` — walk `dependencies:` graph, flag downstream tasks когда upstream Decision получил `superseded-by`.
- `mentor` skill — invoke внутри Discussion-статуса task'а для structured mentor-сессии.

### Preset vs architectural invariant разделение (HARD RULE)

При написании Decision block'а — для **каждого варианта выбора** проверить:

1. Зависит ли значение от target user segment (family / clinic / self-managed / B2B)?
2. Если **ДА** — это **preset field**, не архитектурный invariant. Указать в Decision:
   - Архитектурные invariants (hardcoded, одинаковы для всех presets).
   - Preset fields с family-default values + ссылкой на TASK-16 preset schema evolution.
3. Если **НЕТ** — hardcoded в domain / adapter.

**Why**: hardcodить «family assumption» = ломать при clinic-preset adoption в Phase-3+. Rule 9 (shareability-readiness) требует preset-параметризации user-facing configuration с day 1. Wire format поддерживает — импл может hardcode family-default в MVP.

**Пример из TASK-103** (remote app lock):
- **Architectural**: lock state живёт на сервере, unlock требует authorization, client polls lock_state на network operation.
- **Preset fields**: `lockScreenBehavior` (soft/hard/none), `unlockMethod` (passphrase+remote/passphrase+2fa/physical-repair), `offlineAutoLockDays` (null для family, 30 для clinic).

**Refuse pattern**: если Decision имеет варианты выбора которые plausibly отличаются между family и clinic — эти варианты **должны** быть preset fields, не hardcoded. Иначе — refactoring в Phase-3 при добавлении clinic preset (нарушает rule 4 MVA + rule 9).

### Универсальность

Этот паттерн применим к любым архитектурным доменам (крипто, backend, UX, i18n). **Не создавать отдельный `docs/dev/*-mentor-overview.md` файл** для нового домена — использовать backlog-task'и с меткой домена (`decision + <domain>`).

Migration existing legacy SoT-документов — постепенная, по мере touch'а конкретных фичей. Крипто-legacy файлы (5 штук: `crypto-mentor-overview.md`, `crypto-open-questions.md`, `crypto-topics-handoff.md`, `crypto-status.md`, `crypto-review.md`) полностью консолидированы в `docs/architecture/crypto.md` 2026-07-08.

### Fresh session entry point для крипто

Fresh AI session или новый collaborator, продолжающий крипто-работу, начинает с **AI TL;DR block** в начале [`docs/architecture/crypto.md`](docs/architecture/crypto.md) (~60 строк, purposefully compact for AI first-read). Если TL;DR отвечает на вопрос — **дальше не читать**. Если работа над спекой / audit / crypto change — читать глубже (сценарии, validation set, известные риски). Priority queue = backlog Kanban (`backlog sequence list --plain`). Серверные endpoints и zero-trust baseline — [`docs/architecture/server.md`](docs/architecture/server.md).

**AI-TLDR pattern (обобщение)**: любой architecture domain-файл (`crypto.md`, `server.md`, `identity.md`, `client-android.md`, `external-services.md`) начинается с `<!-- AI-TLDR:BEGIN ... AI-TLDR:END -->` блока (~50-80 строк). Compact snapshot: что защищаем/делаем, stack table, ports, adapter modules, key decisions, rejected, что открыто с триггерами, «как AI должен использовать этот файл». Цель — routine AI question отвечается TL;DR-ом без чтения всего файла (сотни строк). Deeper sections — для owner explanation, spec authoring, audit.

## 12. Server endpoints are untrusted by default (zero-trust posture)

Every server endpoint (Cloudflare Worker, future own-server microservice, any HTTP handler) treats every request as potentially hostile — **even from authenticated clients with valid JWT**. Authenticated ≠ trusted:

- Rogue device inside a legitimate identity can spam under valid JWT.
- Compromised credential (leaked token) grants valid auth to attacker.
- Buggy retry loops from honest clients look identical to abuse until throttled.

Client-side hygiene is insufficient. The server must protect itself.

**Required for every endpoint** (baseline; concrete values in [TASK-105 Decision](backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md)):

1. **Authentication** — JWT verification (library, JWKS cache, expiration + clock skew, claim validation). `[public]` exception requires justification (e.g. JWT issuance itself, health check).
2. **Rate limiting** — at least one dimension (per-identity / per-device / per-IP / combo) with concrete numbers, storage tier declared (in-memory / KV / Durable Object), algorithm declared (token bucket / sliding window).
3. **Input validation** — schema-based rejection of malformed / oversized payloads; request size limit specified.
4. **Observability** — structured log format, abuse-candidate metrics (rate-limit-hit, auth-failure, malformed counters), alert threshold.
5. **Failure modes** — explicit HTTP codes and payload shape for: rate-limit hit (429 + Retry-After), auth failure (401), malformed payload (400), storage tier down (503).

Plus for state-modifying endpoints (POST/PUT/DELETE): **idempotency** approach declared (idempotency-key, dedup key, or reasoning why not needed).

**Enforcement**:
- Skill `.claude/skills/checklist-server-hardening/SKILL.md` runs on any spec introducing / modifying an endpoint (auto-triggered by `procedure-assess-spec-complexity`).
- Refuse pattern 20 (below) catches specs missing baseline properties.
- Non-goals + exit ramps for defenses NOT applied at MVP go into `docs/dev/server-roadmap.md § SRV-BASELINE-*` per rule 8.

**Rationale**: consolidates ad-hoc protection scattered across `docs/dev/server-requirements.md` (Tier 0/1/2 JWT posture), `docs/dev/server-roadmap.md` (SRV-SEC-004 persistent rate-limit), and per-spec checklists. Ensures new endpoints inherit baseline rather than reinvent partial protection.

Feature tasks introducing endpoints (TASK-104 KeyPackage, TASK-67 pairing, TASK-6 recovery, TASK-27 messenger, TASK-19 config sync) add `dependencies: [TASK-105]`.

## 13. Server sees only opaque blobs (zero-knowledge posture)

Rule 12 (zero-trust) covers **how** we defend each endpoint. Rule 13 covers **what** the server is allowed to know at all. The pair is deliberate: authenticated ≠ trusted, and even trusted requests must not reveal business meaning to the server.

Every server-side component (Cloudflare Worker today, own-server microservices later per rule 8) MUST be designed so the server sees **only opaque blobs and opaque identifiers**. It MUST NOT see plaintext content, ownership graph, event types, membership relations, or business semantics.

**Three principles** (all mandatory):

1. **Sealed Server Default** — every endpoint defaults to Tier 0 (sealed storage). Server sees: opaque namespace ID + opaque key + ciphertext + namespace-owner Ed25519 signing pubkey. Authorization = signature verification against namespace-recorded pubkey, NOT an ACL graph. Tier elevation requires explicit justification:
   - **Tier 1 (minimal directory)**: only for async pubkey discovery patterns (X3DH prekey lookup, FCM token routing). Server sees opaque ID → pubkey bytes mapping, no relationships.
   - **Tier 2 (server-required logic)**: only when client-side bypass is possible through Clear App Data / factory reset / root. Currently justified: anti-brute-force vault counter, subscription entitlement timer, JWT issuance for anti-abuse.

2. **Client Coordinates, Server Stores** — business logic lives on the client, server is a sealed storage appliance. Specifically the server MUST NOT do: group membership management (use keyring blob inside namespace), history rotation by business rule (client LIST+DELETE, server does cron-TTL only), schema transformers vN→vCurrent (client lazy migration), forward unsharing on member removal (client re-key + re-encrypt), push routing by event type (encrypted payload, server forwards opaque), audit log of user operations (client encrypted append-only blob), GDPR export of business meaning (client decrypts own blobs), recipient resolution for delivery (client provides explicit opaque token list).

3. **Opaque Identifiers Everywhere** — every ID visible to the server is an opaque UUID NOT derivable from identity-provider primary keys (no Google `sub`, no email, no phone number as server routing key). The `userUid → namespace` mapping lives only on the client. URL paths use `/namespaces/{nsId}/blobs/{key}`, never `/users/{uid}/data/{key}`. No `linkId` as a primary concept exposing pairing graph. No server-side join between `identity-links` and `data` tables.

**Reference architecture** — `docs/dev/server-requirements.md` v2 (sketch), `docs/dev/client-requirements-for-zero-knowledge-server.md` (client-side deltas), `docs/architecture/server.md` (current-state snapshot — pre-migration to full zero-knowledge). Industry patterns supporting this: Signal Sender Keys / Sealed Sender, MLS (RFC 9420), Tresorit envelope wrapping, WhatsApp E2E Encrypted Backup, Bitwarden vault sharing, Apple iCloud Advanced Data Protection.

**Server-log accumulator** — `docs/dev/server-log.md` is the growing record of confirmed patterns (Part A), open questions (Part B), and detected contradictions (Part C). Every feature-task touching the server MUST leave an entry there via the skill `checklist-zero-knowledge-server`. This replaces standalone server-research-tasks in this repo: server-thinking lives **inside** the feature-task that touched the server, but the accumulated context accrues in `server-log.md` for the future own-server repo.

**Skill enforcement** — `.claude/skills/checklist-zero-knowledge-server/SKILL.md` runs on any spec introducing / modifying a server-side component (auto-triggered by `procedure-assess-spec-complexity`). The skill (a) verifies the three principles above, (b) sanity-checks consistency against `server-log.md` Part A, (c) records new confirmed patterns or open questions, (d) blocks the spec if a Part C contradiction is detected.

**Rationale** — without this rule each feature drifts toward smart-server by convenience ("just store the eventType, it's easier"). Once shipped, zero-knowledge pivot is impossible (wire format is frozen, server state is populated). Rule 13 is the brake for convenient-but-wrong choices. Combined with rule 12 (zero-trust) it forms the full server posture: rule 12 makes each endpoint hostile-request-resistant, rule 13 keeps the server ignorant of business meaning even under valid authenticated requests.

**Cross-refs** — rule 8 (server-roadmap for exit ramps), rule 12 (zero-trust baseline), rule 5 (wire format versioning applies to every opaque blob shape), rule 9 (shareability-readiness applies to non-identity payloads).

## 14. Architecture truth lives in the arch-packs (single source of truth)

Every architectural domain has an **arch-pack** — a self-sufficient file (or set) under [`docs/architecture/`](docs/architecture/) built to the **`ecs.md` standard**: the complete, current, *researched* architecture lives IN the file (`crypto*.md`, `messaging*.md`, `gallery.md`, `safety.md`, `identity.md`, `client-android.md`, `external-services.md`, `ecs.md`, `server.md`, `wire-format.md` — mapped in [`docs/architecture/INDEX.md`](docs/architecture/INDEX.md)). The arch-pack is the **read-truth**.

- **Tasks reference, never duplicate.** A backlog task LINKS to the arch-pack (`See <domain>.md`); it MUST NOT restate the architecture. Task files hold task-specific content only (description, plan, progress, AC, discussion). A Decision block (rule 11) records the *decision process* (why / alternatives / history); the *current what/how* lives in the arch-pack, not the task.
- **Changes update the arch-pack, in the same commit.** New or changed architecture → edit the arch-pack; mark a replaced decision `> ⚠️ SUPERSEDED (YYYY-MM-DD): …` and move it to the zone's Rejected section (never silently delete, never leave a stale/dangling reference). Never scatter new truth into task files or leave the arch-pack behind.
- **Grounding = researched prior art, not our past decisions** (skill [`procedure-architecture-sourcing`](.claude/skills/procedure-architecture-sourcing/SKILL.md)): the architecture comes from **industry standards + use cases FIRST**; our tasks are secondary validation (research wins on conflict); residual *product* policy → `mentor`.
- **Discovery + audit:** every session reads this rule (CLAUDE.md); the router skills (`crypto` / `messaging` / `ecs` / `wire-format` …) fire on domain keywords and route the agent to the arch-pack; `INDEX.md` is the map; skill [`procedure-archpack-integrity`](.claude/skills/procedure-archpack-integrity/SKILL.md) audits for truth-in-task leaks, dangling refs, and stale supersede-markers.

**Reconciliation with rule 11**: rule 11's Discussion→Decision task is the **change-control / audit trail** (the decision *process*); rule 14's arch-pack is the **read-surface** (the current *architecture*). They are complementary — the Decision block explains *why*, the arch-pack states *what/how*, and the arch-pack is what an implementer reads.

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
12. User-facing non-identity configuration (template, layout, theme, tutorial) hardcoded as bundled resource без `schemaVersion` + `ConfigSource` adapter pattern (нарушает rule 9 shareability-readiness).
13. Push notification без declared severity criterion (actionable + time-sensitive + user-relevant) и без actionable destination (нарушает rule 10 notification minimization). Alternative: in-app indicator или in-app notification center.
14. PR создаётся (`gh pr create`) на feature-ветке без предварительного вызова skill [`pre-pr-backlog-sync`](.claude/skills/pre-pr-backlog-sync/SKILL.md), результат: backlog Kanban расходится с реальностью (как PR #21/#22 → TASK-3/TASK-4 ушли в Done с 0/4 AC). Alternative: STOP, вызвать `pre-pr-backlog-sync` first; статус Done разрешён только при всех AC `[x]`, иначе Verification (PR merged, ждём физических гейтов) или Paused (work suspended).
15. Backlog AC содержит **pseudo-gate** — формулировку, которую физически невозможно проверить в текущем окружении (например «Huawei без GMS работает», когда Huawei устройства нет). Alternative: либо переписать как `[hand]` DI-override test + inline-TODO `physical-device` в коде, либо удалить AC если он избыточен.
16. Backlog AC проставлен `[x]` на слово владельца без grep'а `[deferred-*]` маркеров в `tasks.md`. Alternative: STOP, выполнить шаг 4b skill'а `pre-pr-backlog-sync`; deferred-маркированные tasks автоматически блокируют соответствующие AC независимо от owner override. (Note: `[auto:checklist]` file-based AC retired per ADR-011 §5 revised 2026-07-16 — checklists chat-only, файлов нет.)

16a. Файл `specs/<NNN>/checklists/*.md` создан или ре-создан в новом коммите — нарушает ADR-011 §5 revised 2026-07-16 (чек-листы chat-only, файлы не commit'аем). Alternative: STOP, удалить файлы, применить skill в chat-only режиме, грей items вписать в `spec.md` / `plan.md`. `.gitignore` (`specs/**/checklists/`) — safety net; старые файлы под `specs/001..016/` не трогаем (historical).
17. Task в статусе `Discussion` перешёл в `Draft` **без** заполненного `### Decision (English)` sub-блока в `SECTION:DISCUSSION` — downstream tasks не получают machine-readable контракт (нарушает rule 11). Alternative: STOP, заполнить Decision block (Choice / Rationale / Applies to / Trade-offs / Exit ramp), только потом status → Draft. Decision block mutable до начала implementation per rule 11 mutability window.
18. Architecture decision дублирован inline в другом task'е вместо `dependencies: [TASK-N]` — sync сломается когда upstream Decision изменится (нарушает rule 11 cross-task references). Alternative: удалить дублирование, добавить в `dependencies:`, prose ссылку «See TASK-N Decision».
19. Новый `docs/dev/*-mentor-overview.md` файл создан для нового архитектурного домена (backend, UX, i18n) вместо backlog-tasks в статусе Discussion — повторяет ошибку старой модели, ведёт к desync (нарушает rule 11 universality). Alternative: создать decision-task'и с меткой домена (`decision + <domain>`), использовать backlog Kanban как Discussion queue.
20. Server endpoint defined in spec без всех пяти required properties (auth / rate limit / input validation / observability / failure modes) и без явного `[public]` reasoning для missing JWT — нарушает rule 12 (zero-trust posture). Downstream: authenticated ≠ trusted, endpoint становится abuse vector под valid JWT. Alternative: STOP, вызвать skill [`checklist-server-hardening`](.claude/skills/checklist-server-hardening/SKILL.md), заполнить missing properties concrete values (rate dimensions + numbers, JWT library + JWKS caching, input schema, observability wiring, failure HTTP codes), либо `[public]` justification. State-modifying endpoints также требуют declared idempotency approach.
21. Server endpoint accepts `userUid` (Google `sub`, email, phone number, Firebase Auth UID) as routing identifier or storage key — нарушает rule 13 (opaque identifiers everywhere). Downstream: server learns ownership graph, DB dump reveals who owns what. Alternative: opaque UUID / `nsId` in URL path, `userUid → nsId` mapping stays client-side.
22. Server endpoint understands `eventType` — reads a `type` field to route push, decide storage bucket, apply business rule — нарушает rule 13 (client coordinates, server stores). Downstream: server learns which events are happening for whom. Alternative: encrypt eventType inside opaque payload, use opaque routing key (target token ID) that server treats as bytes.
23. Server-side ACL graph / membership table / access-grant relation — нарушает rule 13 principle 2. Downstream: server becomes single source of truth for group membership, kick/unshare requires server round-trip, DB dump reveals social graph. Alternative: client-coordinated keyring blob inside namespace, MLS group state on client, server verifies signature not membership.
24. Server-side business-rule retention («keep last 10 configs», «delete when refcount=0», «purge inactive namespaces») — нарушает rule 13 principle 2. Alternative: cron-time-based TTL header (client specifies at PUT), business retention on client via LIST+DELETE.
25. Tier 2 endpoint (server-side stateful counter / timer / entitlement check) без explicit justification что client-side bypass возможен (Clear App Data / factory reset / root) — нарушает rule 13 principle 1. Downstream: unnecessary server-side state, hard to migrate to own-server, metadata leak. Alternative: prove bypass possibility with concrete scenario OR downgrade to Tier 0 (opaque blob with client-side check).
26. Push endpoint routes by content — server reads decrypted body or event type to decide fanout / priority / template — нарушает rule 13 principle 2. Alternative: encrypted opaque payload + opaque target token list + collapse key as opaque hash.
27. Feature spec introduces new server touch point without leaving an entry in `docs/dev/server-log.md` (Part A confirmed pattern OR Part B open question) — нарушает rule 13 skill-enforcement. Downstream: cross-feature drift, future own-server repo loses accumulated context. Alternative: STOP, run skill `checklist-zero-knowledge-server`, record an entry, then proceed.
28. Standalone server-research-task created in this repo (like the retired TASK-59 / TASK-60 pattern) instead of leaving an entry in `server-log.md` under the feature-task that surfaced it — нарушает rule 13 accumulator model. Alternative: fold the research question into `server-log.md` Part B under the source feature-task; deep investigation happens when that feature-task reaches implementation.
29. Architecture restated inside a backlog task (or any non-arch-pack file) instead of linked to its arch-pack, OR an arch-pack left stale after an architectural change — нарушает rule 14 (truth-in-task leak / drift). Downstream: the arch-pack and the task diverge, a future agent reads the wrong truth. Alternative: move the truth INTO the arch-pack (same commit), leave a `See <domain>.md` link in the task, mark the replaced part `⚠️ SUPERSEDED`, run skill `procedure-archpack-integrity`.

For each: surface the issue in one sentence, propose the corrected shape, then continue.

## Output discipline

- Commits represent coherent logical units, one reviewable concern each.
- Push after each significant step, not after several piled up.
- Never use hook-bypassing flags (no-verify, no-gpg-sign, etc.) without explicit user approval.
- Never commit secrets, signing keys, service-account credentials, or machine-local configuration.
- **Checklists in chat — red-only summary, no persisted files** (ADR-011 §5, revised 2026-07-16). Emit one line per checklist: `checklist-X: N/Total ✓, FAIL: CHK-A (short why), CHK-B (short why)`. Chat line = only artefact. Do NOT create `specs/<NNN>/checklists/<name>.md` files; if a skill needs a scratch buffer during evaluation it must delete it before returning. `.gitignore` covers `specs/**/checklists/` as safety net. Grey items surfaced during the run land as edits to `spec.md` / `plan.md` (project truth), not in a separate checklist file. Backlog AC no longer carries new `[auto:checklist]` file-count entries; existing entries in already-committed tasks stay as-is.
- **Change reports — `file:line` markdown links, not diffs** (ADR-011). Emit a list: `- [Foo.kt:42](path/Foo.kt#L42) — one-line description`. Owner does not read code; on demand he opens the link in VSCode (the IDE extension renders `[name.kt:42](path#L42)` natively). Only paste a diff block when the owner explicitly asks for one.
- **Language by audience** (ADR-011, HARD RULE): write each new artifact in the language of its primary audience. **English** for AI-only files: `CLAUDE.md`, ADRs, `plan.md`, `tasks.md`, `contracts/`, skill `SKILL.md`, code comments, commit bodies, PR descriptions. **Russian** for owner-facing files: `spec.md`, backlog task descriptions, `vision.md`, `docs/product/use-cases/*`, MENTOR-DETAIL blocks inside sequences, novice TL;DR summaries, chat replies to owner. Reason: English is ~30% denser for AI parsing; Russian in AI-only files wastes tokens with no offsetting value. Do **not** preemptively migrate existing files — apply on next touch.
- **Tasks.md tick-sync (HARD RULE)**: каждый implementation commit, закрывающий один или несколько `Tnnn` из spec-kit `tasks.md`, ОБЯЗАН в том же diff'е проставить `- [x]` напротив этих task'ов. Refuse to commit без этого.
  - **Один commit может покрывать несколько `Tnnn`** (`phase-N: Tnnn-Tmmm`) — все они должны быть `[x]` в этом же diff'е.
  - **Запрещено**: «потом догонит», «в конце фазы», «ticks отдельным commit'ом». Это создаёт desync, наблюдавшийся на TASK-49 phase 1-4.
  - **Self-check перед commit'ом**: `git diff --cached -- specs/**/tasks.md` должен содержать `[x]` строки, если в diff'е есть код, реализующий task. Иначе stop и проставить tick'и.
  - **Частично сделанный task** — `[ ]` остаётся, в commit message описать что не закрыто. Не ставить `[x]` авансом.
  - **Why**: tasks.md — единственный machine-readable источник правды о прогрессе для `/speckit.analyze`, для будущих сессий Claude и для onboarding. Drift = потеря контекста.

## Sequences in spec.md (ADR-011)

Sequence diagrams live **inline** in `spec.md` under a `## Sequences` heading. Each sequence carries an anchor ID `### SEQ-N: <title>`, two Mermaid diagrams (spec-level and plan-level lifelines), and a MENTOR-DETAIL block for the owner.

### Required structure per sequence

```markdown
### SEQ-N: <short title>

Pre: <preconditions>. Post: <postconditions>.
Used-in: spec/<NNN>-slug[, spec/<MMM>-slug ...].

#### Spec-level (behavior)
\`\`\`mermaid
sequenceDiagram
  participant U as Owner
  participant S as System
  participant X as External
  ...
\`\`\`

#### Plan-level (architecture)
\`\`\`mermaid
sequenceDiagram
  participant UI as <Composable/Activity>
  participant VM as <ViewModel>
  participant UC as <UseCase>
  participant R as <Repository>
  participant A as <Adapter>
  ...
\`\`\`

<!-- MENTOR-DETAIL:BEGIN -->
#### Пояснение для владельца
- <plain-Russian explanation: what each participant is, why each branch exists, what the owner should see on screen>
<!-- MENTOR-DETAIL:END -->
```

### Hard rules

- **Dual projection mandatory.** Both Mermaid diagrams are required. Spec-level lifelines = `Owner / System / External (API, FCM, ...)`. Plan-level lifelines = architectural layers from `architecture.md` (arrows only point downward — visual check on rule 1, domain isolation).
- **MENTOR-DETAIL block mandatory.** Must be present and filled when the sequence is created. Owner-facing — Russian only (per language-by-audience rule).
- **AI reads MENTOR-DETAIL only on demand.** Default behavior: skip the body of `<!-- MENTOR-DETAIL:BEGIN/END -->` blocks when reading `spec.md`. Read only when (a) owner explicitly asks for an explanation; (b) AI onboards onto the spec for the first time; (c) generating production documentation.
- **Anchor IDs are spec-local.** `SEQ-1` in spec/A and `SEQ-1` in spec/B may coexist while inline. The containing file disambiguates.

### Reactive extraction into `docs/sequences/`

Do **not** create a sequence catalog preemptively. Trigger for extraction:

1. The same sequence is genuinely needed in **2+ specs**.
2. Extract into `docs/sequences/SEQ-N-slug.md` (same structure: Pre/Post + Used-in + Spec-level + Plan-level + MENTOR-DETAIL).
3. Replace the inline block in both specs with a link: `→ [SEQ-N](../../docs/sequences/SEQ-N-slug.md)`.
4. Once extracted, the SEQ-N ID becomes globally unique (carries the slug).

`docs/sequences/INDEX.md` is created only when the directory holds **≥5 files**.

**Standing candidate**: QR-pairing flow (spec/007 — reused in spec/011 and planned specs on calls / multi-admin). Extract at next touch of those specs, not preemptively.

### Migration

Existing specs (001-020) are **not migrated**. The convention applies to new specs and to old ones on next `/speckit.*` touch.

## Branching

- Every feature ships in its own branch named after the feature, not on `main`. Spec-driven work uses the spec slug — for example `003-ui-skeleton`, `004-action-architecture-v2`. Non-spec work uses a short kebab-case name describing the change.
- `main` is integrated through pull requests only. Direct commits to `main` are reserved for trivial repository-level chores (CLAUDE.md, .gitignore, root README) and only when no feature branch is open for the same area.
- Open the PR as soon as the branch has a reviewable first commit; keep pushing into it as work progresses. Do not pile up local commits on a feature branch without pushing.
- One feature = one branch = one PR. If scope grows, split into a follow-up spec and a follow-up branch rather than widening the current PR.

For tests:

- Every port has at least a fake-adapter test and a contract test.
- Every wire format has a roundtrip test (write → read → assert equal) and a backward-compat test (read previous schema-version).

## Portfolio tracker (Backlog.md)

Кросс-спек portfolio состояние («что сделано / что в работе / что следующее») живёт в **папке `backlog/`** (инструмент [Backlog.md](https://github.com/MrLesk/Backlog.md), MCP-сервер `backlog`).

- **Одна фича = один backlog-task.** Поле `references` указывает на `specs/NNN-slug/`. Mini-tasks из `specs/NNN/tasks.md` — территория Spec Kit, в backlog не дублируются.
- **Источник правды по AC — `spec.md`.** В секции `## Success Criteria` помечать высокоуровневые user-visible критерии маркером `[backlog]`. Skill `procedure-sync-backlog-ac` автоматически (через MCP) переносит их в `## Acceptance Criteria` соответствующего backlog-task'а. Технические SC (тайминги, fitness functions, contract tests) НЕ помечаются — они остаются только в spec.md.
- **AC sync вызывается** в конце `speckit-clarify` (Step 5c) и `speckit-tasks` (Step 4c). Руками — после правки `## Success Criteria` мимо speckit-команд.
- **Description sync (HARD RULE для AI, добавлено 2026-06-24):** AC sync покрывает только `## Acceptance Criteria` секцию backlog-task'а; описание (`## Что это простыми словами`, `## Зачем`, `## Что входит технически`, `## Состояние`) **застывает** в момент написания task'а. За время clarify → scenarios → plan → tasks → analyze scope / архитектура / effort часто меняются (как было с TASK-7 2026-06-24: исходная модель «hardcoded launcher» → финальная «profile composition + 3 архитектурные правки в F-3»). Если backlog description не sync'ить — Kanban-читатель видит устаревшую модель. Skill [`procedure-sync-backlog-description`](.claude/skills/procedure-sync-backlog-description/SKILL.md) — pair к `procedure-sync-backlog-ac`. **Вызывается автоматически в конце `speckit-analyze` Step 5d** (только если verdict PASS / READY-WITH-CAVEATS). Skill читает spec.md + plan.md + tasks.md + analyze-report.md, projection финальный scope обратно в description, preserve'ит archival sections (старый «Готовый промт для /speckit.specify» с пометкой «historical»). Backlog становится производным от speckit-набора, не отдельным источником правды. **Refusal pattern**: если PR создан без `procedure-sync-backlog-description` sync после full speckit cycle → backlog description устарел → owner / future AI reading task'у работает по неактуальной модели.
- **Создание backlog-task'а для новой спеки** — явное решение владельца (skill не создаёт автоматически). Команда: `backlog task create '<title>' -s Draft --priority high -l 'phase-N,F-feature' -m m-N --ref specs/NNN-slug/`.
- **Status workflow (5 статусов, обновлено 2026-06-24):**
  - `Draft` — task существует как идея / в roadmap'е, но ещё не обсуждалась. Дефолт для новых task'ов. Ничего не написано (нет spec.md, нет частичного кода).
  - `In Progress` — task взят в работу прямо сейчас. Внутри `In Progress` идёт весь Spec Kit pipeline: `/speckit.specify` → `/speckit.clarify` → `/speckit.scenarios` → `/speckit.plan` → `/speckit.tasks` → `/speckit.analyze` → `/speckit.implement` → review → merge. Промежуточные стадии Spec Kit'а **не выделяются в отдельные backlog-статусы** (владелец работает по одной задаче за раз).
  - `Verification` — **код смержен в `main` (PR closed)**, но физические/manual гейты ещё не пройдены (emulator smoke, real-device verification, OEM check, manual document review). AC секции `[hand]` зелёные, но `[auto:deferred-*]` остаются `[ ]`. Note в `<!-- SECTION:VERIFICATION_PENDING:BEGIN -->`: «PR #X merged YYYY-MM-DD, pending AC: #N (deferred-local-emulator), #M (deferred-physical-device)». Снимается, когда отложенные AC закрываются — переход в Done через повторный `pre-pr-backlog-sync`.
  - `Paused` — работа была начата (есть spec.md / частичный код в untracked / в stash / в ветке), но **владелец временно переключился на другую задачу**. Note: «причина паузы», «куда положили частичную работу» (stash/ветка/spec-папка). Отличается от `Draft` тем, что **что-то уже написано**; отличается от `Verification` тем, что **ещё нет merged PR** или есть зависимость от другой in-flight задачи. Возврат в `In Progress` подразумевает recovery работы.
  - `Done` — merged в `main` **И все AC `[x]`** (или `[N/A]`). Если merged без всех зелёных AC → `Verification`, не Done. Никакого «merged = Done» автомата.

  **Различие Verification vs Paused** (важно):
  - `Verification` — pipeline почти завершён, ждём прогона на железе. AI session НЕ может закрыть оставшиеся гейты сам.
  - `Paused` — pipeline остановлен на полпути, ждёт человеческого решения / другой задачи.
  - Когда сомнения — `Paused` если ещё нет merged PR; `Verification` если PR merged.

- **Acceptance Criteria — hybrid model (обновлено 2026-07-16):**
  AC в `backlog/tasks/*.md` имеют **два источника**, помеченные inline-маркерами:
  ```
  - [x] #1 [hand] CloudAvailability port + Android adapter
  - [x] #2 [hand] FcmTokenRegistrationGuard откладывает FCM до cloudAvailable=true
  - [ ] #3 [auto:deferred-local-emulator] Emulator smoke pixel_5_api_34 (T043, T031-T036)
  - [ ] #4 [auto:deferred-physical-device] Physical device verification Xiaomi 11T (T041)
  ```
  - **`[hand]`** — author-written user-visible criterion (5±2 пункта, project-specific behaviour). Pre-PR sync **не переписывает**, только проставляет `[x]`.
  - **`[auto:deferred-<type>]`** — auto-generated, по одной строке на каждый уникальный `[deferred-<type>]` маркер в `tasks.md`. Pre-PR sync **переписывает полностью**.
  - Marker types для deferred: `local-emulator` (нужен AVD ≤ API 34 или другой), `physical-device` (нужен реальный девайс), `firebase-emulator` (нужна Firebase emulator suite), `external` (third-party provisioning).
  - **AC без маркера** — legacy формат, постепенно мигрируется в hand/auto при следующем pre-PR sync.
  - **Retired**: `[auto:checklist]` file-based counts (существовавшие до 2026-07-16). Concept выведен из употребления вместе с persisted checklist files per ADR-011 §5 revised. На уже committed tasks legacy `[auto:checklist]` строки **остаются как исторические** — не регенерируем, не удаляем, не пересчитываем. На новых task'ах не создаём.

  **Pseudo-gates запрещены** в AC: формулировки, которые невозможно физически проверить (например «Huawei без GMS работает», когда Huawei устройства нет и не будет). Альтернатива: `[hand]` DI-override test + inline-TODO `physical-device` в коде.
- **Автопереход `Draft → In Progress` (HARD RULE для AI):**
  - **Любая команда `/speckit.*` или начало работы над фичей требует явного указания `task-N`.** AI **обязан** перед запуском убедиться, что у текущего действия есть связанный backlog-task. Если в сообщении владельца task-N **не назван явно** и не определяется однозначно из контекста (нет открытой ветки `NNN-slug` с совпадающим `references:` полем в каком-либо task'е) — AI **STOP, REFUSE to proceed** и задаёт владельцу один вопрос: «Какой `task-N` берём в работу? (или создаём новый?)».
  - **Когда task-N назван** — AI **первым шагом** (до любых других tool calls) вызывает MCP `editTask task-N -s "In Progress"`. Только после успешного перехода — запускает speckit-команды / делает правки кода.
  - **Никаких "догадок"**: AI не выбирает task-N сам из похожих, даже если есть очевидный кандидат. Всегда явное подтверждение владельца.
  - **Множественные task'и одновременно — запрещены** (по решению владельца «работаю только над одной за раз»). Если владелец просит начать работу над task-X пока task-Y уже `In Progress` — AI обязан спросить: «task-Y сейчас In Progress. Закрыть её (Done) / поставить на `Paused` (с записью где лежит частичная работа) / оставить и взять обе?».
- **Pre-PR sync (HARD RULE для AI, обновлено 2026-07-16):**
  - **Перед `gh pr create`** на любой ветке, привязанной к spec'у или к backlog-task'у, AI **обязан** вызвать skill [`pre-pr-backlog-sync`](.claude/skills/pre-pr-backlog-sync/SKILL.md). Skill:
    1. Находит соответствующий task через `references:` поле.
    2. **Refuse gate**: проверяет что в рабочем дереве нет файлов `specs/<NNN>/checklists/*.md` (per ADR-011 §5 revised — чек-листы chat-only). Если есть — STOP, попросить владельца удалить.
    3. Регенерирует `[auto:deferred-*]` строки из `specs/<NNN>/tasks.md` (grep `\[deferred-(local-emulator|physical-device|firebase-emulator|external)\]`).
    4. Для `[hand]` AC: по grep'у кода/тестов помечает кандидаты `[x]`, спрашивает владельца про manual gates.
    5. Решает статус: **все `[x]` или `[N/A]` → Done** (+ Final Summary); **есть `[ ]` среди `[auto:deferred-*]` → Verification**; **есть `[ ]` среди `[hand]` → In Progress** (PR open не должен, фича не закончена); специальный case **Paused** — только по явному owner-решению «переключаюсь на другое».
    6. Коммитит изменения в `backlog/tasks/` отдельным коммитом, затем возвращает control для `gh pr create`.
  - PR description **должен** содержать строку `Backlog: task-N → <new status>` (и `pending AC: #X (auto:deferred-physical-device), #Y (auto:deferred-local-emulator)` если Verification).
  - Если AI пропускает этот шаг и сразу делает `gh pr create` — это **regression** (повторение incident'а PR #21 / #22, где TASK-3 и TASK-4 ушли в Done с 0/4 AC).
- **`In Progress → Verification → Done` только через `pre-pr-backlog-sync`**:
  - In Progress → Verification: PR merged, `[hand]` AC `[x]`, есть `[ ]` среди `[auto:deferred-*]`.
  - Verification → Done: все AC закрыты (включая manual прогоны на эмуляторе / физическом устройстве).
  - Никакого «merged = Done» автомата.
- **Cleanup Done-карточек:** когда колонка Done разрастается (30+ задач) — `backlog cleanup` перемещает старые Done в `backlog/completed/`. Файлы остаются в git, доступны через Read tool / git log / grep. Контекст не теряется никогда.

### Spec naming convention (обновлено 2026-06-23)

- **Старые спеки `001..020`** — исторические артефакты предыдущей нумерации; не трогать, не переименовывать. Их связь с backlog-task'ом — через поле `references:` в task'е.
- **Все новые спеки** (начиная с TASK-49 и далее) используют конвенцию `specs/task-N-slug/`, где `N` — id соответствующего backlog-task'а. Это **жёсткая конвенция**: имя папки явно содержит task-N, чтобы spec ↔ task связь была видна сразу в файловой структуре.
- **Пример:** TASK-49 → `specs/task-49-cloud-feature-inventory-offline-first/`.
- **При создании новой спеки** AI обязан: (а) сначала убедиться что backlog-task существует и в `In Progress`; (б) создать папку с правильным именем; (в) в backlog-task поле `references:` поставить путь к этой папке.
- **При смене slug** (если фича переформулировалась через clarify) — переименовать папку через `git mv`, обновить `references:` в backlog-task.

### Как AI-агент восстанавливает порядок разработки (navigation through history)

Backlog хранит **три измерения порядка**, и AI-агент должен уметь читать каждое:

1. **Snapshot (что сейчас):** `backlog overview` или `backlog task list --plain`. Показывает текущее состояние всех task'ов.
2. **Logical order (что можно делать дальше):** `backlog sequence list --plain`. Вычисляется из графа `dependencies:` — показывает «волны» (Sequence 1 = не блокированы, Sequence 2 = разблокированы после Sequence 1, и т.д.).
3. **Historical order (как реально было):** `git log --oneline -20 -- backlog/tasks/`. Показывает хронологию изменений task'ов.
4. **Полная история конкретного task'а:** `git log -p backlog/tasks/task-N*` — каждое изменение этого task'а с diff.
5. **Когда task реально закрылся (status → Done):** `git log --diff-filter=M -- backlog/tasks/task-N*` + grep'нуть commit с переходом на `status: Done`. Альтернативно — поле `updated_date` в frontmatter (но оно сдвигается при любом edit).

**Принцип**: **TASK-N — стабильный identifier** (не меняется никогда), spec — артефакт под task'ом (через конвенцию `task-N-slug`). Если порядок исполнения «6 → 49 → 6 → 7» (с паузами / возвратами) — это **нормальная история**, а не рассинхрон. Git log + статусы дают полную картину.
- **`docs/product/vision.md`** — стратегический документ (vision, главный фильтр фич, exit ramps, soft launch gate). Старый `docs/product/roadmap.md` удалён 2026-06-23: операционный план перенесён в Backlog, стратегия — в vision.md. Если в исторических документах (decisions/, specs/) встретятся ссылки `docs/product/roadmap.md` — они исторические; используй vision.md + `backlog overview`.
- **Стиль описания backlog-task'ов (mentor-style, обязательно для всех новых task'ов).** Description пишется на простом русском без жаргона, владелец проекта (не разработчик) должен понимать беглым взглядом. Шаблон:
  ```
  ## Что это простыми словами
  Краткое объяснение + **нумерованные последовательности**:
  «Что происходит по шагам (нормальный сценарий)», «Что происходит при <edge case>».
  Все технические термины расшифровываются в скобках при первом упоминании.

  ## Зачем
  Какую боль закрывает + какой результат пользователь получает.

  ## Что входит технически (для AI-агента)
  Bullet-list портов / адаптеров / wire-форматов / тестов. Здесь жаргон допустим.

  ## Состояние
  Текущий статус по существу (в работе / planned / blocked + что сделано / что осталось).

  ---

  ## Готовый промт для `/speckit.specify`
  Copy-paste блок в ```…``` с секциями: ЧТО СТРОИМ / ЗАЧЕМ /
  SCOPE ВКЛЮЧАЕТ / SCOPE НЕ ВКЛЮЧАЕТ / DEPENDENCIES / ACCEPTANCE CRITERIA /
  LOCAL TEST PATH / CONSTITUTION GATES / EFFORT.
  ```
  AC (Acceptance Criteria через `--ac`) пишутся как **проверяемые человеком шаги**: «зашёл → увидел → получил», не «KeyRegistry per-identity namespacing работает». Образец — TASK-6 (Root Key Hierarchy).
- **Personas vs domain roles (ВАЖНО).** Слова «бабушка», «дочка», «admin-родственник», «внук», «семья» — это **иллюстративные персоны** для понимания use-case, **НЕ доменная модель**. Продукт не зацикливается на one семья-пожилой scenario; должен работать для других конфигураций (clinic / nursing-home / корпоративный contract-phone management / self-care). В **доменных формулировках** (Description core fields, technical scope, AC) использовать **обобщённые роли**:
  - `primary user` (или `end-user` / `device owner`) — основной пользователь устройства, тот кого настраивают (бабушка как example, но также пациент / сотрудник / self-care user).
  - `remote administrator` (или просто `admin`) — пользователь, имеющий полный remote доступ к настройкам primary user'а (родственник / врач / IT-support / сам primary user в self-managed варианте).
  - `restricted caregiver` (или просто `caregiver`) — пользователь с ограниченным доступом (сиделка / медсестра / hourly помощник).
  - `family group` / `care group` / `shared space` — abstract группа (семья / clinic patient circle / company team).
  Конкретные персонажи допустимы **только** в:
  - Опциональной секции `## Пример сценария (use-case)` — для иллюстрации того же abstract scenario на конкретных примерах: family / clinic / B2B.
  - Внутри `Готовый промт для /speckit.specify` — если spec.md явно ориентирован на конкретный сегмент.

### Workflow: как добавлять новые backlog-task'и (для AI-агентов)

Когда владелец говорит «давай добавим N task'ов» / «есть новые идеи, занеси их» — AI должен:

1. **Уточнить параметры** (если не очевидно из контекста):
   - **Фаза** → milestone (`m-0` Phase-1, `m-1` Phase-2, `m-2` Phase-3, `m-3` Phase-4, `m-4` Phase-5/parking-lot). По умолчанию для новых идей **Phase-5 parking-lot** если не подразумевается срочность.
   - **Priority** (`high` / `medium` / `low`). Default для draft-идей — `medium`; для parking-lot — `low`.
   - **Dependencies** (`--dep TASK-X,TASK-Y`) — какие task'и должны быть Done до этого.
   - **Labels** — phase, feature-area (`crypto`, `ui`, `auth`, `cloud`, ...), и опциональный исторический префикс если есть (`f-N`, `s-N`, `p-N`, `v-N`, `l-N`).
   - **References** — если уже есть `specs/NNN-slug/` папка, поле `--ref`.

2. **Создать через CLI** (или MCP `createTask`):
   ```
   backlog task create '<short imperative title без F/S/P/V/L префиксов>' \
     -s Draft --priority medium -l 'phase-N,area-1,area-2' -m m-N \
     [--dep TASK-X,TASK-Y] [--ref specs/NNN-slug/]
   ```

3. **Заполнить description в mentor-style** (см. шаблон выше). Обязательно:
   - Если фича пересекает несколько сегментов (family / clinic / B2B) — добавить блок «Про роли в этой задаче».
   - В техническом разделе использовать обобщённые роли (`primary user`, `remote administrator`, `restricted caregiver`), не персонажей.
   - **Готовый промт для `/speckit.specify`** обязателен — заполнить блоком ```…``` с разделами ЧТО / ЗАЧЕМ / SCOPE / DEPENDENCIES / ACCEPTANCE / LOCAL TEST PATH / CONSTITUTION GATES / EFFORT.

4. **Заполнить AC через `--ac`** (или после создания через `--ac` в edit). AC = проверяемые человеком шаги, не технические утверждения.

5. **Установить ordinal** через `--ordinal N*1000` чтобы Kanban сортировал по id (например, для TASK-49: `--ordinal 49000`).

6. **Показать результат** через `backlog task view task-N --plain` и спросить подтверждения у владельца.

7. **НЕ коммитить автоматически** — только при явной просьбе. Изменения в `backlog/` попадают в обычные коммиты вместе с другими изменениями (`auto_commit: false` в config.yml).
- **autoCommit выключен**: изменения task-файлов попадают в обычные осмысленные коммиты, не плодят микро-коммиты.

Просмотр: `backlog browser` (http://localhost:6420), `backlog overview` (текстовая сводка), `backlog sequence list --plain` (граф зависимостей).

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
