# Subscription Model Research

## Цель
Зафиксировать подход к подпискам до реализации billing subsystem.

## Базовые принципы проекта
1. Подписка — приоритетная модель.
2. Система должна переживать переход к другим моделям без переписывания feature logic.
3. В пределах одного продукта должны поддерживаться разные планы/уровни доступа.
4. Store-specific subscription mechanics не должны вытекать в доменную модель напрямую.

## Что нужно исследовать и обновлять
1. Android billing semantics: products, base plans, offers, lifecycle states.
2. Apple subscription groups, levels, upgrade/downgrade/crossgrade.
3. Grace, hold, retry, cancellation, restore.
4. UI для plan management.
5. Partner bundle scenarios.
6. Offline/restore/cache strategy.
