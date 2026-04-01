# Store Policy Register

Это живой реестр ограничений официальных магазинов и связанных рисков.

## Что сюда заносить
Для каждого ограничения:
- источник,
- дата проверки,
- краткое правило,
- affected features,
- impacted permissions/data,
- required action.

## Базовые направления для постоянной проверки
1. Google Play billing / subscriptions rules.
2. Google Play Data safety.
3. Restricted permissions and sensitive APIs.
4. App quality / Android vitals implications.
5. Apple subscriptions rules.
6. Apple App Privacy requirements.
7. Apple alternative distribution rules (если канал используется).
8. Review-impacting privacy/security/consent requirements.

## Процесс обновления
- Обновлять при каждом изменении feature plan, затрагивающем store/compliance.
- Обновлять минимум при пересмотре roadmap на релиз.
- Если правило меняется, затронутые ADR/spec/plan должны быть перечитаны.

## Связанные документы
- `.specify/memory/constitution.md`
- `docs/compliance/distribution-channel-register.md`
- `docs/compliance/country-legal-tax-register.md`
