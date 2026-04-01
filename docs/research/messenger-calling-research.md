# Messenger Calling Research

## Почему это важно
Звонки и видеозвонки через мессенджеры — стратегический must-have сценарий для продукта.

## Что нужно исследовать до spec
1. Какие мессенджеры обязательны для первой волны.
2. Какие механизмы запуска доступны:
   - deep links,
   - intents / URL schemes,
   - app availability checks,
   - contacts mapping.
3. Что возможно на Android и что возможно на iOS.
4. Какие ограничения магазинов и privacy из этого следуют.
5. Какие разрешения действительно нужны, а какие лучше не запрашивать.
6. Какие fallback сценарии нужны, если конкретный мессенджер не установлен.
7. Как это влияет на elderly-friendly UX.

## Foundation implications
Даже до финального feature-spec архитектура должна быть готова к:
- action dispatch,
- external app invocation,
- graceful fallback,
- parity documentation.
