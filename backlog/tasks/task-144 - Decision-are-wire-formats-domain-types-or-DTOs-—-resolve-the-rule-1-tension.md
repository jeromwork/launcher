---
id: TASK-144
title: 'Decision: are wire formats domain types or DTOs — resolve the rule-1 tension'
status: Done
assignee: []
created_date: '2026-07-20 14:20'
labels:
  - decision
  - wire-format
  - architecture
milestone: m-2
dependencies:
  - TASK-142
priority: medium
ordinal: 144000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

В `CLAUDE.md` правило 1 запрещает доменному слою — слою чистой логики — тащить в себя, среди прочего, **аннотации фреймворка сериализации**. Смысл запрета: домен не должен знать, как его записывают на диск или в сеть.

На практике **33 доменных файла несут `@Serializable`**, и так было с самого начала. Ни одно fitness-правило это не проверяет: `DomainIsolationTest` про аннотации сериализации не знает вовсе.

То есть правило записано, но не действует, и код живёт по другому укладу. Это не авария — это расхождение между текстом правила и фактической практикой, которое надо развести сознательно, а не оставлять тлеть.

## Зачем сейчас

TASK-142 добавила строгие проверки форматов. Возник резонный вопрос: не зацементируют ли они нарушение? **Проверено — нет.** Ни одно из правил не упоминает `@Serializable`, `@JsonNames` или `@SerialName`; они проверяют поля версии, их кодирование и покрытие тестами. Так что немедленной угрозы нет.

Но остаётся отложенная: `wire-format.md` §5 предписывает при переименовании поля вешать `@JsonNames("старое_имя")`. Сегодня `@JsonNames` **не используется нигде** — то есть предписание ни разу не применялось. Первое же переименование поля в доменном формате поставит вопрос ребром: выполнить §5 и углубить расхождение с правилом 1, либо выполнить правило 1 и нарушить §5.

## Варианты

**A. Признать `@Serializable` допустимым в домене — уточнить правило 1.**
Довод: `kotlinx.serialization` — официальный механизм самого языка, компилируемый, без рефлексии, мультиплатформенный, без единого вендорского типа. Запрет правила 1 нацелен на вендорские SDK, транспортные типы и типы платформы — ничего из этого `@Serializable` за собой не тянет. Правило прямо разрешает «pure language standard library».
Цена: ослабление формулировки; нужно аккуратно очертить, что именно разрешено (`@Serializable`, `@SerialName`, `@JsonNames`, `@EncodeDefault`), чтобы под тем же соусом не приехал Firebase-аннотированный класс.

**B. Развести формат и домен: DTO в адаптерном слое, чистый тип в домене.**
Довод: буква правила соблюдена полностью, домен не знает о проводе ничего.
Цена: 17 форматов × (тип + DTO + два преобразования) — умножение кода примерно вдвое на ровном месте, плюс постоянный риск рассинхронизации пары. По правилу 4 (MVA) абстракцию добавляют, только если её отсутствие потребует **переписывания**, а не дописывания. Здесь отсутствие ничего не ломает.

**C. Оставить как есть, ничего не записывая.**
Не вариант: правило, которое написано и не соблюдается, обесценивает остальные правила того же файла.

## Рекомендация

**Вариант A.** Аргумент в пользу B — чистота буквы; аргумент в пользу A — что запрет целил в вендорскую связанность, а `kotlinx.serialization` её не создаёт: убрать библиотеку из проекта можно, не тронув ни одного доменного понятия. Правило 4 прямо не советует платить за оптику удвоением кода.

Если A принят — правило 1 в `CLAUDE.md` дополняется явным перечнем допустимых аннотаций, и в `DomainIsolationTest` добавляется проверка, что **никакие другие** аннотации сериализации (Firebase, Gson, Moshi, Jackson) в домене не появляются. Тогда правило начинает действовать вместо того, чтобы просто быть написанным.

## Состояние

**Draft.** Решено 2026-07-21 с владельцем после research по industry-стандартам (см. Decision ниже). Реализация: правка `CLAUDE.md` rule 1 + fitness-правило.

<!-- SECTION:DISCUSSION:BEGIN -->

### Decision (English)

**Choice — differentiated by whether the type is crypto:**

- **Crypto formats** (`:core:crypto`, `:core:keys` — the extractable crypto SDK): the crypto type carries **no version field and no serialization annotation**. Version and wire format live in a layer *above* crypto (adapter/DTO). The crypto primitive receives already-assembled opaque bytes (AAD, ciphertext) and never learns what is in them. Enforced by TASK-141.
- **Non-crypto formats** (`Action`, `Preset`, `Pool`, `Health`, … ~17 types): `kotlinx.serialization` annotations (`@Serializable`, `@SerialName`, `@JsonNames`, `@EncodeDefault`, custom `KSerializer`) are **allowed in the domain type**. No DTO twin. Vendor serializers (Jackson, Gson, Moshi, Firebase mapping, JPA) remain **forbidden** in the domain.

**Rationale.** `kotlinx.serialization` is a compile-time Kotlin facility — compiler-generated, no runtime reflection, no vendor runtime, removable without touching a domain concept. Rule 1 bans *vendor coupling*; this creates none. A DTO twin for every non-crypto format is pure mapping-fatigue with no rewrite avoided (rule 4 MVA). Industry (Clean Architecture, hexagonal) splits domain from serialization where coupling is expensive — Java+Jackson, extractable SDKs — and tolerates serde/`@Serializable` on the domain for compile-time, language-native serialization on a stable domain. Crypto is the expensive case (extractable SDK + security + no primitive anywhere holds its own version — age, JWE, Tink, libsodium all keep version as a cleartext header above the primitive), so crypto splits; the rest does not.

**Applies to.** Rule 1 (domain isolation) wording; a new fitness rule; TASK-141 (crypto split); every future `@Serializable` domain type.

**Trade-offs.** Non-crypto domain types stay coupled to `kotlinx.serialization` (accepted — it is language-native, removable). If `kotlinx.serialization` were ever abandoned, ~17 types need the annotation stripped and a DTO layer added — a well-scoped, mechanical change, not a rewrite (this is the exit ramp).

**Exit ramp.** To move off the allowance: (1) introduce DTOs in the adapter layer per format, (2) strip annotations from domain types, (3) flip the fitness rule to forbid `kotlinx.serialization` in the domain too. Cost ≈ one type-pair + one mapper per format; no wire-format change, no data migration.

**Server note (owner, 2026-07-21).** The meta-rule "crypto knows nothing of version" cannot be enforced by a client-side fitness rule alone — the server also sees version fields (Firestore rules compare them). Record in `server-log.md` when TASK-141 touches the crypto Firestore paths that the server treats the version as an **opaque** value it never interprets (rule 13), so the zero-knowledge posture is preserved on both sides.

**Scope note.** Original card framed this as "domain types vs DTOs" broadly. The decision keeps it differentiated rather than one-size-fits-all — crypto splits, non-crypto does not.

<!-- SECTION:DISCUSSION:END -->

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] Выбран вариант и зафиксирован в Decision-блоке (дифференцированный: крипта разделяется, не-крипта легализует kotlinx.serialization)
- [x] #2 [hand] `CLAUDE.md` правило 1 приведено в соответствие с выбором — kotlinx.serialization явно разрешён в домене, вендорские явно запрещены
- [x] #3 [hand] Выбор подкреплён fitness-правилом `DomainIsolationTest.commonMain_usesOnlyKotlinxSerialization` — вендорский сериализатор в домене роняет сборку (проверено пробой)
<!-- AC:END -->
