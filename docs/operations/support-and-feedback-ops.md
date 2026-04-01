# Support and Feedback Operations

## Цель
Организовать централизованный контур:
- ошибок,
- запросов пользователей,
- store reviews,
- feature requests,
- поддержки релизов.

## Каналы, которые должны быть предусмотрены
1. Crash/error intake
2. User support requests
3. Public store reviews and ratings
4. Product feedback / feature requests
5. Internal triage backlog

## Для каждой released feature нужно определить
- owner,
- где собираются ошибки,
- где собирается обратная связь,
- кто делает triage,
- как закрывается feedback loop,
- как это автоматизируется через AI/MCP и что остается за человеком.

## Важный принцип
Этот контур должен быть централизованным, но не обязан быть частью Core launcher architecture.

## AI/MCP note
ИИ и MCP могут помогать:
- собирать сигналы,
- классифицировать отзывы,
- группировать дубликаты,
- подготавливать triage summary,
но не должны без контроля изменять продуктовые решения, legal wording или user-facing commitments.
