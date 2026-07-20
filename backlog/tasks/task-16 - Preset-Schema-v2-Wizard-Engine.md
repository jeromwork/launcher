---
id: TASK-16
title: 'Wire format evolution discipline — versioning convention + fitness rule + shape reference'
status: In Progress
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

**Second fitness rule** — `crypto-flows-clock-hygiene` — **вынесен в [TASK-139](task-139%20-%20Fitness-rule-crypto-flows-clock-hygiene-ban-system-clock-in-crypto-code.md)** (2026-07-20). Попал сюда по аудиту 2026-07-07 (item #2 / CANDIDATE-3 / Q-20) только на том основании, что тоже реализуется как фитнес-правило; к версионированию wire-форматов не относится. Полное описание и критерии приёмки — в TASK-139.

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

**In Progress** (2026-07-20). Decision пересмотрен в Session 4, документ `docs/architecture/wire-format.md` + skill-роутер написаны, дубликаты схлопнуты в указатели. Спека через `/speckit.*` не заводилась осознанно: решения уже приняты в Decision-блоке, а spec.md был бы его пересказом — второй источник правды в задаче, которая существует ради устранения вторых источников (см. rule 11 / refuse-паттерн 18). Осталось: два fitness-правила (AC #11, #12).

**Конвертация существующих форматов вынесена в TASK-138** — ~170 файлов, отдельный ревьюируемый объём.

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
- [ ] #1 [hand] Канонический документ `docs/architecture/wire-format.md` написан по шаблону `ecs.md`: заголовок-авторитет, AI-TLDR, инварианты «решено — не переизобретать», секция отвергнутых вариантов, правило синхронизации в том же коммите
- [ ] #2 [hand] Конвенция версии зафиксирована: точечная строка `"MAJOR.MINOR"` + опциональный pre-release токен, единообразно для всех форматов, с явным отказом от семантики SemVer и собственным правилом сравнения
- [ ] #3 [hand] Модель «версия управляет поведением» описана: три поля (`schemaVersion` / `minReaderVersion` / `minWriterVersion`), три исхода у читателя (полный доступ / только чтение / отказ), процедура принятия решения на стороне того, кто вносит изменение
- [ ] #4 [hand] Skill-роутер `wire-format` создан (тонкий указатель, без копии правил); `checklist-wire-format` переписан в режим аудита спеки и больше не содержит нормативных правил
- [ ] #5 [hand] Дубликаты схлопнуты в указатели: три дословные копии правила 5 (CLAUDE.md, AGENTS.md, agent-context.md), нормативные Int-утверждения в `pool-naming.md` / `glossary.md` / `ecs.md`; `ecs.md` получил carve-out «версионирование принадлежит wire-format.md»
- [ ] #6 [hand] Исторические артефакты, противоречащие документу, помечены штампом-указателем (не переписаны): `specs/task-120/spec.md`, `specs/task-73/contracts/vendor-recipe-catalogue.md`, ADR-013
- [ ] #7 [hand] Правила для зашифрованных форматов зафиксированы: версия в открытом виде вне шифротекста, отказ вместо пробного расшифрования, must-understand список внутри аутентифицированной области
- [ ] #8 [hand] Режим pre-MVP привязан к Article XX; маркер зрелости разрешён только с объявленным сроком годности, с зафиксированным обоснованием (провалы Kubernetes / Ingress / GitHub)
- [ ] #9 [hand] Церемония перехода pre-MVP → GA описана (тег, sweep, фикстуры, строгий режим фитнес-правила)
- [ ] #10 [hand] Grep-гейт: по живым докам и скиллам не находится нормативных утверждений о версионировании вне `wire-format.md` (указатели не считаются)
- [x] #11 [hand] Fitness rule `wire-format-hygiene` реализован в `lint-rules/` с тестом (8 кейсов, зелёные), в мягком режиме: ловит захардкоженный литерал версии вместо именованной константы. **Не проверяет кодовую базу** — механизм загрузки правил в Detekt сломан с TASK-65, обнаружено здесь, вынесено в TASK-140
- [x] #12 [N/A] Fitness rule `crypto-flows-clock-hygiene` — **вынесен в TASK-139** (2026-07-20): к версионированию форматов отношения не имеет, попал сюда по аудиту только как «тоже фитнес-правило»
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

> **Revised 2026-07-20 (Session 4, pre-implementation ⇒ still mutable, rule 11 window).** Six parallel primary-source research passes overturned two Session-3 conclusions: (a) `"1-alpha.0"` is invalid under SemVer's own grammar and the 2 → 1 renumbering of a shipped monotonic counter is unattested and blocked by our own Firestore monotonicity rule — dropped; (b) the far more valuable finding, which Session 3 missed entirely, is that a version can **drive runtime behavior** (Matroska/SQLite reader-obligation model) rather than merely label a revision — adopted as the core of the Decision. The conversion sweep moved out to TASK-138. Becomes immutable at the first implementation commit.

**Scope**: TASK-16 defines wire format evolution discipline for all 7 wire formats in the project (Profile, Preset, Recovery blob, Bucket, Ciphertext envelope, QR pairing token, Push payload). Does NOT define individual format contents (owned by respective decision tasks per rule 11).

**Versioning convention (revised Session 4, 2026-07-20)**: `schemaVersion` is a **dotted string** `"MAJOR.MINOR"` with an optional pre-release token — `"2.0"`, `"3.0-beta"`. MAJOR = breaking, MINOR = additive, no patch component. **Explicitly NOT SemVer** — its grammar requires three components and its precedence describes release artifacts, not data shapes; comparison is defined in the document instead of borrowed. Uniform across all formats; a born-stable small format is `"1.0"`. No mixed String/Int scheme (no major system does this; a heterogeneous rule forces every reader to branch on format, and a small format that grows would need a wire-breaking type switch).

**The version is a runtime instrument, not a label (Session 4 — the core addition)**: three fields, three distinct reader decisions.
- `schemaVersion` — what wrote this. Diagnostics only; no reader decision depends on it.
- `minReaderVersion` — below it, the reader **MUST refuse** with a typed error.
- `minWriterVersion` — below it, the reader **MUST go read-only** and tell the user, never write and silently degrade.

Invariant `minReaderVersion ≤ minWriterVersion ≤ schemaVersion`. **The writer of a change decides whether it is breaking, judged per change rather than per release** — the old reader cannot judge, it does not know what the new data means. Model: Matroska `DocTypeReadVersion` (RFC 9559, which mandates both "MUST NOT refuse if within range" and "MUST skip unknown") + SQLite's separate read/write version bytes, which is where the third outcome comes from.

**Enforcement**: one fitness rule (`wire-format-hygiene`, custom Detekt rule in `lint-rules/` beside the three existing detectors) checks field presence and ordering, version-string parseability, and roundtrip-test existence; post-GA it additionally requires a golden fixture and `@JsonNames` on renamed fields. Golden corpus must cover **every** shipped version, not just the previous one — our documents have no retention window, so pairwise compatibility is insufficient.

**E2E-encrypted formats** (Recovery blob, Bucket, Ciphertext envelope): schemaVersion = first byte inband (Bitwarden EncString pattern). Reader recognizes or throws `UnknownWireVersionException` — never guess-and-decrypt.

**Plain-JSON formats** (Profile, Preset, QR token, Push payload metadata): schemaVersion = top-level string field. Strictness taxonomy per format:
- QR token: WIRE-only (renames OK if bytes align).
- Profile / Preset / Push metadata: WIRE_JSON (renames = breaking without `@JsonNames`).

**Skill extension**: existing `checklist-wire-format` extended with bump discipline (permissive vs strict per suffix). No new skill.

**Fitness rule**: `wire-format-hygiene` implemented as custom Detekt rule / Konsist / ArchUnit — direct Kotlin analog of `buf breaking`. Prevents drift on save, not code review.

**Pre-MVP → GA switch = one-time ceremony**: git tag (`v1.0-mvp`), sweep all wire formats, remove `-alpha` suffixes, commit `v1` fixtures, enable strict mode. From that commit onward, migration writer mandatory for every field change.

**Preset shape reference**: short doc in `docs/architecture/INDEX.md` (or `preset.md`) — links to decision tasks owning namespaces (TASK-103 deviceLock, TASK-104 mls, TASK-108 privacy/quota, TASK-110 media). Does NOT duplicate field contents.

**Applies to**:
- **Future wire formats**: all follow the discipline from commit-1.
- **Existing formats convert on next touch, not in one sweep** (revised Session 4). Converting means: integer → dotted string **at the same or higher number, never lower** (I3), add the two reader/writer fields, update fixtures and roundtrip tests, and update the Firestore security rule for that collection (rules compare `schemaVersion` numerically; string comparison is lexicographic and unsafe). Until converted, a format's integer form stays valid.
- **The full conversion sweep is TASK-138**, not this task. Inventory: ~30 Kotlin classes, ~28 constants with five different names, ~65 JSON files, ~46 tests, 2 TS files, plus Firestore rules — an order of magnitude beyond what the earlier "convert Preset/Profile now" framing assumed.

**Convention home (single source, no re-derivation — revised Session 4)**: the authoritative convention lives in **[`docs/architecture/wire-format.md`](../../docs/architecture/wire-format.md)**, built on the TASK-136 pattern (authority header, AI-TLDR, decided-invariants block, rejected-alternatives block, same-commit sync rule). Two skills point at it and never copy it: `wire-format` (thin router — "what are the rules") and `checklist-wire-format` (spec audit — "does this spec comply"). `CLAUDE.md` rule 5, `AGENTS.md`, and `agent-context.md` keep a one-line pointer each — a pointer carries no data, so the single-source property holds. This replaces the Session-3 plan of housing the convention inside `checklist-wire-format` SKILL.md: a checklist is an audit instrument, not a reference document, and the owner's requirement is one file an agent can read without token overhead.

**Trade-offs**:
- Dotted string vs Int: trades trivial parsing cost for a version the reader can *act on* — the MAJOR/MINOR split is itself a machine-readable statement about whether a difference is breaking.
- Three fields vs one: trades two extra bytes per document for a third reader outcome (read-only) that a single field cannot express. Justified by the Session-4 finding that a bare number cannot answer "may I write this back?".
- Defining our own comparison vs citing SemVer: trades the comfort of a named standard for correctness — citing SemVer while writing strings it rejects is what produced the Session-3 error.
- Two skills (`wire-format` router + `checklist-wire-format` audit) vs one: trades one extra file for a clean split between "what are the rules" and "does this spec comply". Neither restates the rules.
- Conversion deferred to TASK-138: trades a temporary doc-vs-code gap (documented in `wire-format.md` §11 as transitional) for a reviewable change of sane size.

**Exit ramp**:
- Format genuinely needs a size-minimal version (QR token < 200 bytes) → allow a bare integer for that one format plus a separate maturity flag. Additive rule, ~0.5 day, documented as an exception in `wire-format.md` §13.
- Fitness rule too strict (blocks legitimate refactor) → `@Suppress("WireFormatHygiene")` + justification comment. Standard escape hatch.
- Reader/writer gating proves unnecessary (no format ever raises the bar) → the two fields become dead weight but harmless; drop them from new formats without touching existing ones. Reversible, ~0.5 day.
- Dotted string proves insufficient (formats need date-based versioning like Stripe) → apply per-format additively. Non-breaking.

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

### Session 4 — "how does a version help the application?" (2026-07-20, pre-implementation ⇒ Decision still mutable)

Owner rejected the Session-3 direction and reframed the question: not "string or int" but **"how does a version system actively help the app behave correctly"** — a version should let the app decide whether data is usable, experimental, and so on. Six parallel primary-source research passes followed.

**What the reframe surfaced (missed by Sessions 2–3):**
- **Matroska/EBML (RFC 8794, RFC 9559)** — `DocTypeVersion` (what wrote it) vs `DocTypeReadVersion` (minimum reader needed), with normative "MUST NOT refuse within range" + "MUST skip unknown". The **writer** judges whether a change is breaking, per change, not per release — canonical example: a new element improving only seek precision does not raise the bar.
- **SQLite** — separate read-version and write-version bytes give a **third outcome**: read-only. `read ≤ 2, write > 2 → treat as read-only`.
- **PNG chunk-name case bits, X.509 `critical`, JWS/COSE `crit`, glTF `extensionsRequired`** — per-element must-understand flags as an alternative granularity; COSE additionally requires the list to sit inside the authenticated region or an attacker strips it.
- **Maturity markers genuinely drive machine behavior** — k8s alpha/beta APIs are 404 without `--runtime-config`; buf's `ignore_unstable_packages` keys off the package-name suffix; npm/cargo/pip all exclude pre-release from resolution by default (cargo needed RFC 3493 to work around the overload); Rust rejects `#![feature]` on stable (E0554).
- **But unenforced maturity markers reliably fail** — KEP-3136 (beta-by-default → 90%+ of production clusters ran it, fixable only for future APIs), Ingress frozen in beta 18 releases and replaced rather than evolved, GitHub abandoning per-feature preview markers. Azure's `-preview` works precisely because it expires (90 days / 1 year cap). ⇒ token permitted only with a declared expiry.
- **RFC 9413** — the lenient-reader trap: on-the-fly repair entrenches the other side's bug into a de-facto standard nobody can remove. Unknown-by-rule ≠ leniency; corrupt must fail loudly.

**Two Session-3 errors corrected:**
1. `"1-alpha.0"` is invalid under SemVer's own BNF (`<version core>` requires all three components). Citing SemVer while writing a string it rejects is self-defeating ⇒ we define our own comparison and explicitly disclaim SemVer.
2. The 2 → 1 renumbering has no precedent for a monotonic counter (the k8s CronJob `v2alpha1 → v1beta1` case is a track-name change), and is blocked concretely by our own Firestore rule `schemaVersion_cannot_decrease_on_update`. Owner confirmed 2026-07-20: never decrease.

**Owner corrections during the session**: (a) config does **not** sync between devices yet and there are no users — the "unknown-field preservation cannot be retrofitted" urgency was overstated and withdrawn; the rule stays in the document for when sync ships. (b) Architecture docs are written **for agents**, compact, no owner-facing prose — the owner will not read them and prefers a chat summary. (c) The document must serve every app in the ecosystem, and nothing anywhere may contradict it.

<!-- SECTION:DISCUSSION:END -->
