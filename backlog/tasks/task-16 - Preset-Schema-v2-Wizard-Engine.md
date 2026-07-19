---
id: TASK-16
title: 'Wire format evolution discipline — versioning convention + fitness rule + shape reference'
status: Draft
assignee: []
created_date: '2026-06-23 05:38'
updated_date: '2026-07-19'
labels:
  - decision
  - wire-format
  - fitness-rule
  - skill
  - phase-2
milestone: m-2
dependencies:
  - TASK-6
  - TASK-102
  - TASK-103
  - TASK-104
  - TASK-108
  - TASK-110
  - TASK-112
priority: high
ordinal: 16000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

**Задача переопределена 2026-07-07** — оригинальная формулировка «Preset Schema v2 + Wizard Engine» была искусственной (сгенерирована ранним roadmap'ом под предпосылку migration v1→v2). С учётом Article XX (Pre-MVP no-migration override) + CLAUDE.md rule 11 (decision-task'и владеют своими namespace'ами) — миграционная рамка отпала.

**Настоящая проблема, которую решаем**:

В проекте **7 wire format'ов** (форматов данных, пересекающих границу устройства):

1. **Profile** — синхронизируемый документ пользователя (живёт на сервере, шифрован через MLS group). Owned by TASK-70 / TASK-102.
2. **Preset** — стартовый шаблон / pool item (не то же что profile — preset = template, profile = live state). Owned by TASK-18.
3. **Recovery blob** — зашифрованный пакет для recovery. Owned by TASK-6 (F-5, `schemaVersion: 1` уже в коде).
4. **Bucket wire format** — абстрактный контейнер зашифрованных данных. Owned by TASK-66.
5. **Ciphertext envelope** — шифротекст с schemaVersion prefix. Owned by TASK-112 (KeyVault).
6. **QR pairing token** — то, что показывает одно устройство, сканирует другое. Owned by TASK-67.
7. **Push payload** — FCM data-message с encrypted body. Owned by TASK-108.

Каждый должен иметь `schemaVersion`. **Проблема**: нет **общей дисциплины** как менять `schemaVersion`. Каждый раз при bump'е нужно вспоминать правила (fixture'ы, migration, backward-compat, deprecation).

**Плюс**: MVP (Article XX активен) vs GA (real users) — правила радикально разные, но нигде **явно** не различаются в коде / артефактах.

TASK-16 закрывает три gap'а:
1. **Convention**: как выглядит `schemaVersion` (string с alpha/beta/stable suffix, копируем Kubernetes `v1alpha1` паттерн).
2. **Skill для AI**: автоматизированный checklist, применяемый при bump'е любого `schemaVersion`.
3. **Fitness rule**: build-time проверка hygiene правил (не полагаемся на code review).
4. **Preset shape reference**: короткий документ, единая точка входа «какие namespace'ы preset содержит + где decision-task'и».

**Что этот task НЕ решает** (уже owns другими):
- Preset composition (full copy vs delta) → **TASK-18** (Preset Authoring + Sharing).
- Wizard engine (mandatory/optional шаги) → **TASK-22** (Optional Step Reminder System) или TASK-1 (F-3 Wizard onboarding).
- Конкретные значения preset field'ов (family default `poolCap = 100` и т.д.) → decision-task'и (TASK-103/104/108/110).
- Migration writer'ы для конкретных wire format'ов → пишутся **на месте** при bump'е, TASK-16 создаёт правила, не migration'ы.

## Зачем

**Ongoing tax**: каждый раз при bump'е `schemaVersion` разработчик (или AI) вспоминает правила из **трёх мест** (CLAUDE.md rule 5, Article XX конституции, decision 05 preset wire format). Нет единого места «здесь всё что нужно знать».

**Fitness rules существуют для module isolation** (`Spec009IsolationTest`, `Spec011IsolationTest`), но не для wire format hygiene. Дыра.

**AI-агенты, работающие с wire format'ами**, не имеют skill'а который автоматически проверяет discipline. Расшифровывают каждый раз с нуля.

**Правильный момент запуска — сейчас**: TASK-112 (KeyVault) только что закрыт с `Ciphertext(bytes, purpose)` newtype содержащим `schemaVersion` prefix — это первое место где мы применяем **правильную** дисциплину. Стандартизировать pattern до того, как он размножится через 7 wire format'ов.

## Что входит технически (для AI-агента)

**Раздел 1 — Universal wire format evolution rules**:

Расширение CLAUDE.md rule 5 с явным различием MVP vs GA режимов.

**MVP-режим (Article XX активен, `schemaVersion` ends in `-alpha` / `-beta`)**:
- Любой breaking change допустим.
- Удалить старые fixture'ы, заменить новыми под новую schema.
- Migration writer **не пишется**.
- В любом коде который читал/писал старый формат — просто переписать под новый.
- Inline TODO **не** оставляем (нет legacy).

**GA-режим (Article XX выключен, `schemaVersion` без suffix, stable)**:
- Breaking change требует **migration writer**, написанный **до** shipping (Terraform state upgrade pattern: chain of upgraders v0→v1→v2, kept forever).
- Backward-compat reads возможны минимум на 1 major release.
- Fixture'ы старой версии **не удаляются** — они становятся регрессионным тестом.
- Field renaming → dual-name via `@JsonNames("old_name")` (kotlinx.serialization) / `@JsonAlias` (Jackson).
- Field removal → `reserved` list per Protobuf convention.
- Deprecation → удаление через 1 major version cycle.
- Unknown fields **ignored on read** (kotlinx.serialization default; sole exception: security envelopes parse strictly — Signal/MLS pattern).

**Раздел 2 — Versioning convention (Kubernetes `v1alpha1` pattern)**:

`schemaVersion: String` (не Int) с suffix'ом:
- `"1-alpha.3"` — pre-MVP, «можно ломать свободно».
- `"1-beta.1"` — feature-freeze, deprecation clock started (9 months per Kubernetes convention).
- `"1"` — stable, no breaking changes ever.
- `"2"` — breaking bump post-GA, требует migration writer chain.

**Rationale**: version identifier **inside** artifact = mode. Fresh AI-агент видит `-alpha` marker → знает правила. No side channel, no wiki, no hidden config. Копируем Kubernetes `v1alpha1` / Rust `0.y.z` / Google `/v1alpha1/` pattern.

**Pre-MVP → GA switch = разовая церемония**:
- Pick git tag (`v1.0-mvp`).
- Sweep all 7 wire formats.
- Remove `-alpha` suffixes.
- Commit `v1` fixture files.
- Enable strict mode в fitness rule.
- От этого момента migration writer mandatory для каждого field change.

**Раздел 3 — Skill: extend existing `checklist-wire-format`**:

Расширяем существующий skill (не плодим новый). Добавляем bump discipline:

1. При изменении файла с `@Serializable data class` или `schemaVersion` constant:
   - Grep `schemaVersion` value — читаем suffix.
   - **Alpha/beta**: permissive mode — проверяем только (a) `schemaVersion` field присутствует, (b) roundtrip test проходит.
   - **Stable**: strict mode — проверяем (a) checked-in fixture `fixtures/<format>-v<N>.json` существует, (b) `@JsonNames` на renamed поля, (c) removed поля в `reserved` list.
2. При bump'е `schemaVersion` `-alpha` → stable: требуем written migration note in PR description + committed fixture file.
3. Универсально:
   - `schemaVersion` явно присутствует в новой версии?
   - Round-trip test проходит?
   - Fitness test (раздел 4) не сломан?

**Раздел 4 — Fitness rule: `wire-format-hygiene`**:

Build-time автоматическая проверка (Detekt custom rule или Konsist / ArchUnit). Прямой Kotlin analog `buf breaking`.

**Rules**:
- Каждый `@Serializable data class` содержит поле `schemaVersion: String`.
- Каждый такой class имеет `<ClassName>RoundtripTest` в тестах.
- Roundtrip test импортирует хотя бы один fixture из `test/resources/fixtures/`.
- Fixture файл соответствует declared `schemaVersion`.
- Если `schemaVersion` stable (без alpha/beta suffix):
  - Field rename без `@JsonNames("old_name")` → refuse.
  - Field removal без `reserved` list или deprecated marker → refuse.
- Если `schemaVersion` alpha/beta:
  - Только базовые правила (schemaVersion присутствует + roundtrip проходит).

**Реализация**: custom Detekt rule в `build-conventions/` или подобное. Аналог существующих `Spec009IsolationTest`, `Spec011IsolationTest`.

**Second fitness rule (added 2026-07-07 per audit item #2 / CANDIDATE-3 / Q-20)** — `crypto-flows-clock-hygiene`:

Same infrastructure (custom Detekt rule или Konsist), но проверяет другое invariant. Все timestamp'ы в crypto-flow должны идти через `serverTimestamp` (Firestore) или equivalent server-authoritative source. `Clock.System.now()` (system time) **запрещён** в crypto-critical code.

**Rule**:
- Файлы в packages `core/keys/*`, `core/crypto/*`, `app/adapters/openmls/*` и любой `*.pairing.*` — `import kotlinx.datetime.Clock.System` → refuse.
- Same for `java.lang.System.currentTimeMillis()`, `System.nanoTime()`.
- Escape hatch: `@Suppress("CryptoClockHygiene")` с обязательным комментарием обоснования (напр. debug-logs, non-crypto instrumentation).

**Rationale**: time-skew attacks (attacker меняет system clock → replays sessions past expiration; attacker meint timestamp'ы для inserting old MLS commits). `serverTimestamp` = server-authoritative, не controlled attacker's device.

**Раздел 5 — E2E-encrypted content specific**:

Для 3 encrypted format'ов (Recovery blob, Bucket wire format, Ciphertext envelope):
- `schemaVersion` = **первый байт** внутри plaintext (после расшифровки). Bitwarden EncString pattern.
- Reader не узнал версию → throws `UnknownWireVersionException` — **никогда не гадать**.
- Server видит только opaque bytes — не может routing/transform по версии.
- Version negotiation происходит **вне** ciphertext (via capability exchange in group protocols) — copy MLS `Capabilities.versions<>` pattern.

Для 4 plain-JSON format'ов (Profile, Preset, Push payload metadata, QR token):
- `schemaVersion` = top-level string field.
- Different strictness:
  - **QR token** (short, size-critical): WIRE-only (renames OK if bytes align).
  - **Profile, Preset** (human-readable, long-lived): WIRE_JSON (renames = breaking, dual-name via `@JsonNames`).
  - **Push payload metadata**: WIRE_JSON.

**Раздел 6 — Preset shape reference (short doc)**:

Живёт как секция в `docs/architecture/INDEX.md` или отдельный `docs/architecture/preset.md`.

Содержит:
- Определение: Preset = именованный набор конфигов из pool'а, template для profile.
- Отличие от Profile: Preset = immutable template в pool'е, Profile = live synced state per user.
- Список namespace'ов + ссылки на decision-task'и:
  - `deviceLock` → TASK-103
  - `mls` → TASK-104
  - `privacy` / `quota` → TASK-108
  - `media` → TASK-110
- Convention: nested style (`deviceLock: { unlockMethod: ... }`), не flat.
- Composition rule: full copy (не delta), details в TASK-18.

**НЕ содержит**: конкретные значения полей (в decision-task'ах, дублировать = плохо).

## Состояние

**Draft** — Decision block закрыт 2026-07-07. Готова к `/speckit.specify`.

**Downstream impact**:
- **TASK-6** (Recovery blob) — при следующем touch: bump `schemaVersion: 1` → `"1-alpha.1"` (pre-MVP marker).
- **TASK-66** (Bucket wire format) — same discipline при implementation.
- **TASK-112** (Ciphertext envelope) — уже спроектирован с inband schemaVersion; при implementation bump `1` → `"1-alpha.1"`.
- **TASK-67** (QR token) — при implementation apply pattern.
- **TASK-102** (Profile + encrypted edit lock) — edit lock wire format получает same discipline; уже flagged в TASK-102 Decision update.
- **TASK-108** (Push payload metadata) — при implementation apply pattern.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 CLAUDE.md rule 5 расширен разделами «MVP mode (Article XX active)» + «GA mode» с явными правилами
- [ ] #2 Versioning convention documented: `schemaVersion: String` с alpha/beta/stable suffixes (Kubernetes `v1alpha1` pattern)
- [ ] #3 Skill `checklist-wire-format` расширен bump discipline (permissive vs strict mode по suffix)
- [ ] #4 Fitness rule `wire-format-hygiene` реализован (custom Detekt rule / Konsist / ArchUnit)
- [ ] #4b Fitness rule `crypto-flows-clock-hygiene` — запрет `Clock.System.now()` / `System.currentTimeMillis()` в `core/keys/*`, `core/crypto/*`, `app/adapters/openmls/*`, `*.pairing.*` (added per audit 2026-07-07 item #2 / CANDIDATE-3)
- [ ] #5 Preset shape reference написан в `docs/architecture/INDEX.md` или `docs/architecture/preset.md`
- [ ] #6 E2E-encrypted formats (Recovery blob, Bucket, Ciphertext envelope) — pattern documented (Bitwarden first-byte inband)
- [ ] #7 Plain-JSON formats (Profile, Preset, QR, Push metadata) — strictness taxonomy documented (WIRE / WIRE_JSON per format)
- [ ] #8 Pre-MVP → GA switch ceremony documented (git tag, sweep, remove `-alpha`, commit fixtures, enable strict mode)
- [ ] #9 Shipped ECS formats converted: `Preset`/`Profile` `schemaVersion: Int = 2` → `String "1-alpha.0"`; bundled-preset JSON + fixtures + roundtrip tests updated; `docs/architecture/ecs.md` `schemaVersion` line synced in same commit (ecs.md §12)
- [ ] #10 Convention lives authoritatively in `checklist-wire-format` SKILL.md (single source) + one-line pointer from `docs/architecture`
<!-- AC:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 — Original scope (2026-06-23, retracted 2026-07-07)

Original TASK-16 = «Preset Schema v2 + Wizard Engine» с миграционной рамкой (lift v1→v2, Phase 2 reader downgrade, server-side degradation). Основано на предпосылке что у нас будут pre-Phase-3 users, чью preset v1 надо мигрировать.

**Retracted** owner 2026-07-07: «нам пока никто не пользуется, мы тестируем и сами используем. Наверное это нужно по-другому выразить, чтобы увеличение версии не ломало работу. Но я не уверен, что это именно к preset schemа относится».

### Session 2 — Research + reformulation (2026-07-07)

**Research topics** (industry patterns for wire format versioning):
- Protobuf / buf CLI `buf breaking` (WIRE / WIRE_JSON / PACKAGE / FILE categories).
- Kubernetes `apiVersion: v1alpha1` / `v1beta1` / `v1` explicit stability tiers.
- Rust / Cargo `0.y.z` pre-1.0 breaking-allowed pattern.
- Google API design guide AIP-185 alpha/beta/GA channels.
- Terraform state upgraders (chain of `StateUpgraders` v0→v1→v2, kept forever).
- Stripe date-based `Stripe-Version` header + version change modules.
- Signal SignalMessage first-byte version prefix.
- Bitwarden EncString `"0."` / `"2."` version prefix inband.
- age `age-encryption.org/v1` header line.
- MLS RFC 9420 `ProtocolVersion mls10(1)` + `Capabilities.versions<>` negotiation.
- Kotlin: kotlinx.serialization `@JsonNames`, Konsist / ArchUnit / custom Detekt patterns.

**4 universal rules across all references**:
1. Version identifier = first byte/field of every payload (not buried, not optional).
2. Field numbers/names never reused (Protobuf `reserved`, universal principle).
3. Adding safe (default value); renaming/removing require ceremony (dual-name via `@JsonNames` / `@JsonAlias` / serde `alias`).
4. Unknown fields ignored on read by default (sole exception: security envelopes parse strictly).

**Pre-MVP mode is a named encoded-in-artifact pattern** (Rust `0.x.y`, Kubernetes `v1alpha1`, Google `/v1alpha1/`). Not a hack, not a hidden flag — the version string itself declares the mode.

### Decision (English)

> **Revised 2026-07-19 (pre-implementation ⇒ still mutable, rule 11 window).** Session-3 industry re-grounding (Kubernetes / buf / semver, primary-source verified) confirmed the String + per-object choice and refined the String-vs-Int question; owner approved converting the ECS formats. Becomes immutable at the first implementation commit. The earlier "🔒 immutable" marker (2026-07-07) was premature — no implementation had started.

**Scope**: TASK-16 defines wire format evolution discipline for all 7 wire formats in the project (Profile, Preset, Recovery blob, Bucket, Ciphertext envelope, QR pairing token, Push payload). Does NOT define individual format contents (owned by respective decision tasks per rule 11).

**Versioning convention**: `schemaVersion: String` (not Int), Kubernetes-style suffixes:
- `"1-alpha.N"` — pre-MVP, breaking changes allowed freely.
- `"1-beta.N"` — feature-freeze, deprecation clock started.
- `"1"` — stable, no breaking changes ever (post-GA gate).
- `"2"` — breaking bump post-GA, requires migration writer chain.

**Uniformity refinement (Session 3, 2026-07-19)**: **one encoding for ALL wire formats — String, everywhere.** No mixed String/Int scheme (no major system does this — Kubernetes/buf/semver are uniform; a heterogeneous rule forces every reader to branch on format, and a small format that grows would need a wire-breaking type switch). A born-stable / small format is simply the string **with no pre-release token** (`"1"`) — as cheap as an int, one parser/comparator, and can enter `-alpha` later without changing field type. The Int-for-size path (QR token < 200 bytes) survives **only** as the narrow exit ramp below, not as a second default. Rationale for mode-in-the-string: a wire blob travels alone with no ambient channel, so the receiver must decide "may I rely on this shape?" from the blob's own token (Kubernetes/semver model), not a side channel (Rust/Stripe model).

**Two modes, one enforcement point**:
- One fitness rule reads `schemaVersion` suffix at build time.
- Ends in `-alpha`/`-beta` → permissive mode (checks: schemaVersion present + roundtrip test passes).
- Stable (no suffix) → strict mode (checks: fixture exists + `@JsonNames` on renamed fields + removed fields in reserved list).

**E2E-encrypted formats** (Recovery blob, Bucket, Ciphertext envelope): schemaVersion = first byte inband (Bitwarden EncString pattern). Reader recognizes or throws `UnknownWireVersionException` — never guess-and-decrypt.

**Plain-JSON formats** (Profile, Preset, QR token, Push payload metadata): schemaVersion = top-level string field. Strictness taxonomy per format:
- QR token: WIRE-only (renames OK if bytes align).
- Profile / Preset / Push metadata: WIRE_JSON (renames = breaking without `@JsonNames`).

**Skill extension**: existing `checklist-wire-format` extended with bump discipline (permissive vs strict per suffix). No new skill.

**Fitness rule**: `wire-format-hygiene` implemented as custom Detekt rule / Konsist / ArchUnit — direct Kotlin analog of `buf breaking`. Prevents drift on save, not code review.

**Pre-MVP → GA switch = one-time ceremony**: git tag (`v1.0-mvp`), sweep all wire formats, remove `-alpha` suffixes, commit `v1` fixtures, enable strict mode. From that commit onward, migration writer mandatory for every field change.

**Preset shape reference**: short doc in `docs/architecture/INDEX.md` (or `preset.md`) — links to decision tasks owning namespaces (TASK-103 deviceLock, TASK-104 mls, TASK-108 privacy/quota, TASK-110 media). Does NOT duplicate field contents.

**Applies to**:
- **Already-shipped ECS formats (`Preset`, `Profile`) — CONVERT NOW (added 2026-07-19).** TASK-136 (2026-07-18) shipped these with `schemaVersion: Int = 2`, which violates this Decision. First TASK-16 implementation converts both to the String convention: `Int 2` → `String "1-alpha.0"` (pre-release ⇒ version count resets; "first public = first"; Article XX clean-in-place, no migrator, delete/rewrite fixtures). Touches `Preset.kt`, `Profile.kt`, `Pool.kt` if it carries a version, all bundled-preset JSON (`app/.../bundled-presets/*.json`, `core/.../presets/*.json`), fixtures, roundtrip tests. `ecs.md` says `schemaVersion = 2` — update it in the same commit (ecs.md §12 sync rule).
- All other existing wire formats: TASK-6 (Recovery blob v1 → `"1-alpha.1"` at next touch), TASK-66 (Bucket at implementation), TASK-112 (Ciphertext envelope), TASK-67 (QR token), TASK-102 (encrypted edit lock), TASK-108 (Push payload metadata).
- Future wire formats: all follow same discipline from commit-1.

**Convention home (single source, no re-derivation — added 2026-07-19)**: the authoritative convention (String, suffix semantics, per-object staging, promotion alpha→beta→stable, MVP-vs-GA rules, uniformity refinement) lives **in the `checklist-wire-format` SKILL.md** — it fires on every wire-format touch, so an AI/dev reads it before changing any `schemaVersion`. A one-line pointer from `docs/architecture` (e.g. INDEX or the wire-format reference) links to it. No second copy (drift risk). This is what closes "so this question never comes up again" (owner directive 2026-07-19).

**Trade-offs**:
- String schemaVersion vs Int: trades ergonomics (comparison, parsing) for machine-readable mode encoding. Fitness rule reads suffix — worth the cost.
- Single fitness rule with runtime switch vs two separate rules: trades slight complexity in one rule for single enforcement point (easier to reason about).
- Retaining `checklist-wire-format` vs creating `wire-format-bump`: trades opinionated skill name for skill inventory hygiene (rule 4 MVA).
- Bitwarden first-byte inband: trades one byte of ciphertext per record for wire-safety across storage migrations (Firestore → own-server transparent).

**Exit ramp**:
- Convention insufficient (real format needs Int schemaVersion for size reasons — QR token < 200 bytes) → allow `Int` variant with separate `alphaFlag: Boolean` field for that format. Additive rule (~0.5 day).
- Fitness rule too strict (blocks legitimate refactor) → temporarily disable per-file via `@Suppress("WireFormatHygiene")` annotation + PR justification comment. Standard escape hatch.
- Kubernetes `v1alpha1` pattern insufficient (real formats need date-based versioning like Stripe) → apply Stripe pattern (version = date) additively for specific formats. Non-breaking.

**Non-goals**:
- Individual migration writers per format — written on-site at bump time, not centrally.
- Wizard engine (mandatory/optional steps) — belongs to TASK-22 or TASK-1.
- Preset composition rules — belongs to TASK-18.
- Field-level values (family default `poolCap = 100` etc.) — belongs to respective decision tasks (TASK-103/104/108/110).

### Session 3 — Industry-source revisit (2026-07-19, pre-implementation ⇒ Decision still mutable per rule 11)

Owner reopened TASK-16 to take it into work; confirmed Q1: **mode (alpha/beta/stable) is a per-format property, not a global switch** ("otherwise features are hard to launch"). Asked to ground the String-vs-Int choice in industry primary sources.

**Research (primary-source verified, agent 2026-07-19):**
- **Kubernetes** (deprecation-policy + API-overview): API groups are *"independently versioned"* — per-object staging is explicit and canonical. Stability level is encoded IN the string (`v1alpha1`/`v1beta1`/`v1`). Promotion is one-directional alpha→beta→GA (Rule 3: never deprecate toward a less-stable version). Deprecation windows: alpha = removable any release, no notice; beta ≈ 9 months / 3 releases; GA = not removed within a major.
- **Protobuf/buf**: per-package version suffix (`foo.v1beta1`), independent lifecycles; `ignore_unstable_packages` drops alpha/beta packages from breaking-change enforcement (the maturity token in the name changes the guarantee); stable packages must not import unstable; `reserved` prevents field-id reuse.
- **SemVer**: per-artifact by construction; pre-release token lives IN the string (`1.0.0-alpha.1`), signals "unstable, may not satisfy compat"; precedence `alpha < alpha.1 < beta < rc < release`.
- **Stripe** (counter-example): global date-based version, NOT per-object — chosen so accounts pin a frozen snapshot with server-side shims. Confirms per-object is a deliberate alternative, not the only way.
- **Rust feature-gates** (secondary precedent): per-feature independent staging, but mode via side channel (nightly channel + opt-in), not in an identifier.

**Synthesis relevant to our Decision:**
1. **String is effectively mandatory for per-object staging.** Every system that stages maturity per-object uses a string with the token embedded; a bare Int encodes *revision*, not *maturity* — it cannot say "still breakable". ⇒ the existing Decision (String) is correct and industry-backed.
2. **Mode-in-the-blob is right for wire formats specifically.** A wire blob travels alone with no ambient channel, so the receiver must decide "may I rely on this shape?" from the blob's own token (Kubernetes/semver model), not a side channel (Rust/Stripe model).
3. **Mixed String/Int has NO major precedent — uniformity is the norm.** Reason: the version field is itself a wire-format contract; a heterogeneous rule forces every reader/validator to branch on which format it is, and a small format that later grows would have to switch its own field type (a wire break — exactly what versioning avoids). **A born-stable format is just the string with no pre-release token (`"1"`)** — as simple as an int, one parser, and it can enter alpha later without a type change. ⇒ owner's "strings for big, ints for small" is superseded by "uniform strings; small/stable = bare `"1"`"; the Int-for-QR path stays only as the narrow exit ramp already in the Decision.

**Live drift identified (the real open item):** the Decision was closed 2026-07-07, **before** TASK-136 (2026-07-18) shipped canonical ECS with `Preset`/`Profile` `schemaVersion: Int = 2`. So the two most-central wire formats currently **violate** this Decision (Int, not String; no `-alpha` mode token). The Decision's "Applies to" list predates the ECS reshape and does not cover converting them. Pre-release ⇒ conversion is a cheap clean-in-place edit (Article XX). **Proposed Decision revision (pending owner approval):** (a) fold in synthesis point 3 (uniform strings; stable = bare token; mixed only as exit ramp); (b) add `Preset`/`Profile` to "Applies to" with Int→String conversion (`2` → `"2-alpha.0"` or reset to `"1-alpha.N"`, TBD) as part of first implementation; (c) keep everything else as closed 2026-07-07.

<!-- SECTION:DISCUSSION:END -->
