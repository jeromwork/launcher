---
id: TASK-56
title: Rename family.keys.* → cryptokit.keys.* namespace
status: Done
superseded-by: TASK-141
assignee: []
created_date: '2026-06-26 09:44'
updated_date: '2026-07-13'
labels:
  - crypto
  - refactor
  - namespace
  - follow-up
milestone: m-1
dependencies:
  - TASK-51
priority: medium
ordinal: 56000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

**Что это простыми словами**

После TASK-51 в проекте останется **смешанный namespace**: `:core:crypto` модуль на `cryptokit.crypto.*`, а `:core:keys` модуль всё ещё на `family.keys.*` (наследие spec 016/018, не входило в scope TASK-51). Эта задача — закрыть оставшуюся часть renaming-а, чтобы во всём crypto-семействе проекта был один единый namespace `cryptokit.*`.

**Зачем**

Owner-mandate 2026-06-26: «слово family меня смущает». TASK-51 переименовал `family.crypto.*` → `cryptokit.crypto.*`, но `family.keys.*` остался — он не был в scope TASK-51. Без TASK-56 проект на финальном состоянии будет иметь два разных namespace в crypto-зоне (`cryptokit.crypto.*` + `family.keys.*`), что путает читателя и наследует ту же проблему которую TASK-51 решал.

**Scope (тривиальный refactor)**

- Move `core/keys/src/{common,android,jvm}{Main,Test}/kotlin/family/keys/` → `cryptokit/keys/`
- Update `package family.keys.*` → `package cryptokit.keys.*`
- Update import statements в `:app` и других потребителях `:core:keys`
- Update Konsist rule `NoLegacyFamilyNamespaceTest` чтобы также банила `family.keys.*` (если не покрывает)
- Update `docs/dev/crypto-review.md` если есть references на `family.keys.*`

**Зависимости**

- TASK-51 — Done (паттерн namespace rename отработан, ban rule уже добавлен в Phase 8 для `family.crypto.*`)

**Effort**

Trivial. ~30 мин — это чистый find-replace + git mv + import updates. Никаких архитектурных изменений, никаких новых типов.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] grep -rn "family\.keys" --include="*.kt" . = 0 матчей в production коде
- [x] #2 [hand] Все ports + impls + tests из :core:keys переименованы из family.keys.* в cryptokit.keys.*
- [x] #3 [hand] Build green, все unit + Robolectric тесты зелёные
- [x] #4 [hand] Konsist fitness rule NoLegacyFamilyNamespaceTest расширен на family.keys.*
- [x] #5 [hand] Wire literal `PrimitiveSerialDescriptor("family.keys.ByteArrayBase64")` переименован в `cryptokit.keys.ByteArrayBase64` (owner authorized 2026-07-13 — no production consumers)
- [x] #6 [hand] Addendum consolidation: ByteArrayBase64Serializer унифицирован в :core:crypto (был продублирован в :core:crypto и :core:keys); :core:keys импортит из :core:crypto; SerialDescriptor name = `cryptokit.ByteArrayBase64` (stack-wide, не module-scoped)
<!-- AC:END -->

<!-- SECTION:SUPERSEDED:BEGIN -->

## Отменена TASK-141 (2026-07-20)

Это переименование **обращено вспять**. Решение владельца 2026-07-20: `family` становится единым корнем для всей выносной части экосистемы — `family.crypto`, `family.keys`, `family.push`, `family.wire`.

**Почему прежнее решение отменено.** Оно опиралось на мандат владельца от 2026-06-26 («слово family меня смущает»), где `family` читалось как **целевая аудитория** — «для семей». В 2026-07-20 владелец уточнил, что имеет в виду **семейство продуктов** (лаунчер, мессенджер, галерея), и в этом значении корень возражений не вызывает. `cryptokit` отвергнут как общий корень по существу: это **один из модулей** внутри выносимой части, а не её название — под ним не лежат ни `wire`, ни `push`.

**Что это тянет за собой** (делается в TASK-141, не здесь): правило `NoLegacyFamilyNamespaceTest` запрещает ровно те префиксы, к которым мы возвращаемся, — его список инвертируется в том же коммите, иначе переименование не соберётся.

**Работа не пропала**: TASK-51 и TASK-56 свели три разных корня к одному. Меняется само слово, не структура.

<!-- SECTION:SUPERSEDED:END -->
