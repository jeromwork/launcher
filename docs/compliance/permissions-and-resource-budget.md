# Permissions and Resource Budget

## Цель
Для каждой значимой фичи фиксировать цену этой фичи в терминах:
- permissions,
- battery,
- memory,
- storage,
- network.

## Шаблон записи
### Feature
### Requested permissions
### Why each permission is needed
### Fallback if denied
### Background/runtime impact
### Startup impact
### Memory/storage impact
### Network impact
### Monitoring/observability
### Decision

## Проектный принцип
Чем меньше разрешений и ресурсов требует продукт, тем лучше для:
- store viability,
- пользовательского доверия,
- стабильности,
- продвижения,
- поддержки.

## Обязательное правило
Ни одна значимая фича не должна попадать в реализацию без явного resource-budget review.
