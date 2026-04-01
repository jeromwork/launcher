# Distribution Channel Register

## Цель
Фиксировать каналы дистрибуции и их ограничения так, чтобы архитектура и фичи учитывали это заранее.

## Каналы по умолчанию
1. Google Play
2. Apple App Store

## Потенциальные каналы
1. Alternative distribution in EU regions where legally available
2. Partner distribution
3. White-label / preinstall / managed rollout
4. Direct web or marketplace-based distribution where legally and technically available

## Для каждого канала нужно хранить
- supported platforms,
- supported regions,
- billing model constraints,
- privacy/compliance constraints,
- update model,
- review/approval model,
- partner dependencies,
- support ownership,
- branding/customization implications.

## Решение проекта
Официальные магазины — baseline channel.
Все остальные каналы рассматриваются как отдельные коммерческие и compliance режимы, а не как “та же самая поставка”.

## Action for future plans
Если фича зависит от канала распространения, `plan.md` обязан:
- указать affected channel,
- указать blocked/allowed behavior,
- обновить этот реестр.
