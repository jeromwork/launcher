---
id: TASK-146
title: 'Extract family.pairing.* out of :core:crypto into :core:pairing'
status: In Progress
assignee: []
created_date: '2026-07-21 14:17'
labels:
  - crypto
  - refactor
  - pairing
milestone: m-1
dependencies: []
priority: medium
ordinal: 146000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Сейчас пакет `family.pairing.*` (знакомство устройств — handshake, связывание identity↔ключ) физически лежит внутри модуля крипто-примитивов `:core:crypto`. По архитектуре (подтверждено промышленными стандартами RFC 9750 / Signal) pairing — **отдельная зона** (наш Authentication Service), не часть примитивов. Плюс pairing тащит в модуль примитивов `@Serializable`-типы (`PublicKey`, `SigningPublicKey`, `DeviceId`), что нарушает инвариант TASK-141 «в крипто-модулях нет сериализации».

Задача — вынести `family.pairing.*` в собственный модуль `:core:pairing`. После этого сериализация в pairing становится легальной (это не крипто-примитив), а инвариант «нет сериализации» для `:core:crypto` очищается.

## Зачем

Убрать протекание границы зон, найденное при SoT-консолидации (TASK-145). Сохранить extractability крипто-SDK (примитивы остаются чистыми). Привести код в соответствие с архитектурным файлом [`crypto-pairing.md`](../../docs/architecture/crypto-pairing.md), который уже описывает pairing как отдельную зону с known-debt пометкой.

## Что входит технически (для AI-агента)

- Новый Gradle-модуль `:core:pairing` (namespace `family.pairing`), зависит от `:core:crypto`.
- Перенести `family.pairing.api.*` (`DeviceIdentityRepository`, `RecipientResolver`, `EncryptedMediaStorage`, value-типы `PublicKey`/`SigningPublicKey`/`DeviceId` + `ByteArrayBase64Serializer`) из `:core:crypto` в `:core:pairing`.
- Обновить fitness-правила: `verifyCryptoIsolation` (allowlist), убедиться что `:core:crypto` больше не содержит `@Serializable`; `NoLegacyFamilyNamespaceTest` зелёный.
- Обновить импортёров (`:core`, `:app` адаптеры) на новый модуль.
- Снять known-debt пометку в `crypto-pairing.md`.

## Состояние

**Draft.** Follow-up из TASK-145 (SoT-консолидация). Не начинать без явного взятия в работу.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Модуль `:core:pairing` создан, `family.pairing.*` перенесён из `:core:crypto`
- [ ] #2 [hand] В `:core:crypto` не осталось `@Serializable` / `KSerializer`; kotlinx.serialization plugin убран если больше не нужен
- [ ] #3 [hand] Fitness `verifyCryptoIsolation` + `NoLegacyFamilyNamespaceTest` зелёные
- [ ] #4 [hand] Все импортёры (`:core`, `:app`) переведены на `:core:pairing`, сборка зелёная
- [ ] #5 [hand] Known-debt пометка снята в `docs/architecture/crypto-pairing.md`
<!-- AC:END -->
