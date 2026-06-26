---
id: TASK-56
title: Rename family.keys.* → cryptokit.keys.* namespace
status: Draft
assignee: []
created_date: '2026-06-26 09:44'
labels:
  - crypto
  - refactor
  - namespace
  - follow-up
milestone: m-4
dependencies:
  - TASK-51
priority: low
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
- [ ] #1 grep -rn "family\.keys" --include="*.kt" . = 0 матчей в production коде
- [ ] #2 Все ports + impls + tests из :core:keys переименованы из family.keys.* в cryptokit.keys.*
- [ ] #3 Build green, все unit + Robolectric тесты зелёные
- [ ] #4 Konsist fitness rule NoLegacyFamilyNamespaceTest расширен на cryptokit.keys.* (если не покрыт)
<!-- AC:END -->
