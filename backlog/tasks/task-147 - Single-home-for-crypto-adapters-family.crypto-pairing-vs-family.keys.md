---
id: TASK-147
title: Single home for crypto adapters (family.crypto/pairing vs family.keys)
status: Draft
assignee: []
created_date: '2026-07-21 14:17'
labels:
  - crypto
  - refactor
milestone: m-1
dependencies: []
priority: low
ordinal: 147000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Крипто-адаптеры (реализации портов с реальными SDK) сейчас живут в **двух местах**: адаптеры `family.keys` — в `:app` (`com.launcher.app.data.envelope|recovery`), а адаптеры `family.crypto`/`family.pairing` — в `:core` (`com.launcher.adapters.crypto.*`). Из-за этого любое утверждение «крипто-адаптеры живут в X» неверно, и новичку непонятно, куда класть новый адаптер.

Задача — свести к **единому правилу**: все крипто-адаптеры в одном доме. Какой именно дом (`:app` или выделенный adapter-модуль) — решается внутри этой задачи.

## Зачем

Убрать неоднозначность размещения, найденную при SoT-консолидации (TASK-145). Один предсказуемый дом = меньше вопросов «куда положить адаптер» каждую сессию.

## Что входит технически (для AI-агента)

- Решить целевой дом: `:app` vs выделенный adapter-модуль (взвесить: `:core:crypto`/`:core:keys` должны оставаться SDK без app-зависимостей; адаптеры тянут Firestore/Android).
- Мигрировать `com.launcher.adapters.crypto.*` (`FileKeyBlobStore`, `PairingCryptoCoordinator`, `PairRecipientResolver`, `FirestoreDeviceIdentityRepository`, `WorkerEncryptedMediaStorage`) в целевой дом.
- Обновить DI-wiring и fitness-правила.
- Обновить пометку про «два дома» в crypto-файлах (сейчас честно зафиксирована как known debt).

## Состояние

**Draft.** Follow-up из TASK-145. Low-priority — это чистка консистентности, не блокер. Взвесить цену рефакторинга рабочего кода (rule 4 MVA) перед началом.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Выбран и зафиксирован целевой единый дом для крипто-адаптеров с обоснованием
- [ ] #2 [hand] Все крипто-адаптеры (`family.crypto`/`family.pairing`/`family.keys`) живут в одном доме, сборка зелёная
- [ ] #3 [hand] DI-wiring обновлён, fitness-правила зелёные
- [ ] #4 [hand] Пометка «два дома» в crypto-файлах заменена на актуальное единое правило
<!-- AC:END -->

