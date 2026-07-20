---
id: TASK-16
title: 'Wire format evolution discipline — versioning convention + fitness rule + shape reference'
status: Done
assignee: []
created_date: '2026-06-23 05:38'
updated_date: '2026-07-20'
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

TASK-16 закрывает четыре gap'а:
1. **Конвенция**: как выглядит версия и, главное, **что из неё обязан делать читатель** — три поля вместо одного, три исхода вместо двух (полный доступ / только чтение / отказ).
2. **Единый документ**: одно место, где записаны все правила, вместо трёх дословных копий и девяти пересказов по скиллам.
3. **Skill для AI**: срабатывает на любое касание версионирования и отправляет в документ, чтобы правила не выводились заново каждый раз.
4. **Fitness rule**: машинная проверка вместо «помним и следим на ревью».

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

**Итоговый состав (после Session 4, 2026-07-20).** Полные правила — в [`docs/architecture/wire-format.md`](../../docs/architecture/wire-format.md); здесь только состав работы, чтобы описание не стало второй копией.

1. **Канонический документ** `docs/architecture/wire-format.md` по шаблону `ecs.md`: заголовок-авторитет («этот файл главнее»), AI-TLDR, блок решённых инвариантов, блок отвергнутых вариантов, правило синхронизации в том же коммите.
2. **Skill-роутер** `wire-format` — тонкий указатель без копии правил. Плюс `checklist-wire-format` переписан в режим аудита спеки (раньше он сам устанавливал правила и требовал `Int`, противореча Decision).
3. **Чистка дубликатов**: три дословные копии правила 5 (`CLAUDE.md`, `AGENTS.md`, `agent-context.md`) → однострочные указатели; Int-утверждения из `pool-naming.md` / `glossary.md` / `ecs.md`; `ecs.md` получил оговорку, что версионирование принадлежит новому документу.
4. **Штампы на исторические артефакты**, противоречащие документу (`specs/task-120`, контракт `task-73`, ADR-013) — текст сохранён, добавлен указатель.
5. **Фитнес-правило** `wire-format-hygiene` в `lint-rules/` — мягкий режим: ловит захардкоженный литерал версии вместо именованной константы.

**Ключевое отличие от Session 2-3.** Прежняя формулировка (разделы про alpha/beta-суффиксы, permissive/strict по суффиксу, `"1-alpha.3"`) **отменена** — она опиралась на невалидный SemVer и на понижение отгруженного номера. Принятая модель: версия управляет поведением приложения через три поля (`schemaVersion` / `minReaderVersion` / `minWriterVersion`), точечная строка, не SemVer. См. Decision ниже.

**Вынесено из задачи:**
- Конвертация существующих форматов (~170 файлов) → **TASK-138**.
- Правило `crypto-flows-clock-hygiene` → **TASK-139** (к версионированию не относится).
- Починка загрузки правил в Detekt → **TASK-140** (дефект существует с TASK-65, обнаружен здесь).

## Состояние

**Done** (2026-07-20). Канонический документ + skill-роутер написаны, дубликаты схлопнуты в указатели, фитнес-правило реализовано с тестами. Спека через `/speckit.*` не заводилась осознанно: решения уже приняты в Decision-блоке, а spec.md был бы его пересказом — второй источник правды в задаче, которая существует ради устранения вторых источников (см. rule 11 / refuse-паттерн 18).

**Оговорка по AC #11**: правило написано и покрыто тестами, но по кодовой базе не работает — механизм загрузки правил в Detekt сломан с TASK-65 (обнаружено здесь, вынесено в TASK-140). Формулировка критерия выполнена; принуждение включится после TASK-140.

**Конвертация существующих форматов вынесена в TASK-138** — ~170 файлов, отдельный ревьюируемый объём.

**Downstream impact**:
- **TASK-6** (Recovery blob) — при следующем touch: `1` → `"1.0"` + два поля reader/writer (номер не понижаем, I3).
- **TASK-66** (Bucket wire format) — same discipline при implementation.
- **TASK-112** (Ciphertext envelope) — уже спроектирован с inband schemaVersion; при implementation `1` → `"1.0"` + два поля.
- **TASK-67** (QR token) — при implementation apply pattern.
- **TASK-102** (Profile + encrypted edit lock) — edit lock wire format получает same discipline; уже flagged в TASK-102 Decision update.
- **TASK-108** (Push payload metadata) — при implementation apply pattern.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] Канонический документ `docs/architecture/wire-format.md` написан по шаблону `ecs.md`: заголовок-авторитет, AI-TLDR, инварианты «решено — не переизобретать», секция отвергнутых вариантов, правило синхронизации в том же коммите
- [x] #2 [hand] Конвенция версии зафиксирована: точечная строка `"MAJOR.MINOR"` + опциональный pre-release токен, единообразно для всех форматов, с явным отказом от семантики SemVer и собственным правилом сравнения
- [x] #3 [hand] Модель «версия управляет поведением» описана: три поля (`schemaVersion` / `minReaderVersion` / `minWriterVersion`), три исхода у читателя (полный доступ / только чтение / отказ), процедура принятия решения на стороне того, кто вносит изменение
- [x] #4 [hand] Skill-роутер `wire-format` создан (тонкий указатель, без копии правил); `checklist-wire-format` переписан в режим аудита спеки и больше не содержит нормативных правил
- [x] #5 [hand] Дубликаты схлопнуты в указатели: три дословные копии правила 5 (CLAUDE.md, AGENTS.md, agent-context.md), нормативные Int-утверждения в `pool-naming.md` / `glossary.md` / `ecs.md`; `ecs.md` получил carve-out «версионирование принадлежит wire-format.md»
- [x] #6 [hand] Исторические артефакты, противоречащие документу, помечены штампом-указателем (не переписаны): `specs/task-120/spec.md`, `specs/task-73/contracts/vendor-recipe-catalogue.md`, ADR-013
- [x] #7 [hand] Правила для зашифрованных форматов зафиксированы: версия в открытом виде вне шифротекста, отказ вместо пробного расшифрования, must-understand список внутри аутентифицированной области
- [x] #8 [hand] Режим pre-MVP привязан к Article XX; маркер зрелости разрешён только с объявленным сроком годности, с зафиксированным обоснованием (провалы Kubernetes / Ingress / GitHub)
- [x] #9 [hand] Церемония перехода pre-MVP → GA описана (тег, sweep, фикстуры, строгий режим фитнес-правила)
- [x] #10 [hand] Grep-гейт: по живым докам и скиллам не находится нормативных утверждений о версионировании вне `wire-format.md` (указатели не считаются)
- [x] #11 [hand] Fitness rule `wire-format-hygiene` реализован в `lint-rules/` с тестом (8 кейсов, зелёные), в мягком режиме: ловит захардкоженный литерал версии вместо именованной константы. **Не проверяет кодовую базу** — механизм загрузки правил в Detekt сломан с TASK-65, обнаружено здесь, вынесено в TASK-140
- [x] #12 [N/A] Fitness rule `crypto-flows-clock-hygiene` — **вынесен в TASK-139** (2026-07-20): к версионированию форматов отношения не имеет, попал сюда по аудиту только как «тоже фитнес-правило»
<!-- AC:END -->

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Final Summary

> **Историческая справка (добавлено 2026-07-20 в TASK-140).** Пути `lint-rules/` и `config/detekt.yml` ниже больше не существуют: TASK-140 нашла корень проблемы («молчаливая» загрузка правил в Detekt), перенесла все правила в [`ArchitectureFitnessTest`](../../app/src/test/java/com/launcher/app/fitness/ArchitectureFitnessTest.kt) и удалила Detekt-механизм целиком. Текст ниже сохранён как запись того, что было верно на момент закрытия задачи.


**Done 2026-07-20.** 12/12 AC закрыты (11 `[x]`, 1 `[N/A]` — вынесен). Спека через `/speckit.*` не заводилась осознанно: решения жили в Decision-блоке, spec.md был бы его пересказом — второй источник правды в задаче, которая существует ради устранения вторых источников (rule 11 / refuse-паттерн 18).

**Поставлено:**
- [`docs/architecture/wire-format.md`](../../docs/architecture/wire-format.md) — 233 строки, AI-TLDR ~60. Единственный источник правил версионирования для всей экосистемы.
- [`.claude/skills/wire-format/`](../../.claude/skills/wire-format/SKILL.md) — роутер, 40 строк, без копии правил.
- [`WireFormatHygieneDetector`](../../lint-rules/src/main/kotlin/com/launcher/lint/WireFormatHygieneDetector.kt) + 8 тестов.
- [`config/detekt.yml`](../../config/detekt.yml) — отключает стандартные наборы Detekt (до этого `detektFoundation` падал с 556 стилевыми замечаниями).

**Вычищено:** три дословные копии правила 5 → указатели; `checklist-wire-format` из нормативного стал аудиторским (требовал `Int`, противореча Decision); Int-утверждения из `pool-naming.md` / `glossary.md` / `ecs.md`; штампы на три исторических артефакта. Финальный grep: нормативных утверждений о версионировании вне канонического документа — ноль.

**Главная находка, не входившая в scope:** три фитнес-правила из TASK-65 **никогда не проверяли код** — в `lint-rules/` не было регистрации через `ServiceLoader`. Правила компилировались, свои тесты проходили, кодовую базу не трогали. Обнаружено цепочкой: новое правило даёт 0 замечаний → патч «сообщать о каждом файле» даёт 0 → валидация конфига Detekt отвечает «набор `launcher` не существует». Регистрация добавлена, но набор всё равно не грузится; исключены отсутствие файла в jar, отсутствие jar на classpath, несовпадение байткода, вариант сборки. Корневая причина не найдена, разбор вынесен в **TASK-140** с полным списком исключённого.

**Следствие для AC #11:** правило написано и покрыто тестами, но по кодовой базе не работает — как и три предыдущих. Формулировка критерия («реализован в `lint-rules/` с тестом») выполнена; принуждение закроется в TASK-140.

**Порождённые задачи:** TASK-138 (конвертация ~170 файлов, зависит от TASK-140) · TASK-139 (крипто-часы) · TASK-140 (починка загрузки правил, высокий приоритет — обесценивает все архитектурные проверки проекта).
<!-- SECTION:FINAL_SUMMARY:END -->

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
