# State Management Checklist: WhatsApp Contact Tiles via Communication Shell

**Purpose**: Проверка качества требований к `Return Context`, жизненному циклу состояния и предсказуемости восстановления.  
**Created**: 2026-04-02  
**Feature**: [spec.md](./spec.md)

## State Scope

- [x] CHK001 Указано ли, что хранится только минимальный launcher-owned state? [Spec §FR-009, §Key Entities]
- [x] CHK002 Определено ли, какие поля входят в `Return Context`? [Spec §FR-011, §Key Entities]
- [x] CHK003 Явно ли исключены лишние и чувствительные данные? [Spec §FR-010]

## State Lifetime

- [x] CHK004 Указано ли, что активный контекст только один? [Spec §FR-012]
- [x] CHK005 Указано ли, что stale context должен очищаться или заменяться? [Spec §FR-013]
- [x] CHK006 Указано ли, что старый контекст не должен ломать новый action cycle? [Spec §FR-013, §FR-021]

## Restore Semantics

- [x] CHK007 Определено ли, что считается `expected state` после возврата? [Spec §FR-015]
- [x] CHK008 Определено ли fallback-поведение, если exact restore невозможно? [Spec §User Story 2, §FR-039, §Stable Home State]
- [x] CHK009 Исключены ли неожиданные прыжки между экранами или уровнями home UI? [Spec §FR-015]

## Testing & Robustness

- [x] CHK010 Покрыт ли сценарий длительной паузы до возврата? [Spec §Edge Cases, §FR-013, §FR-039]
- [x] CHK011 Покрыт ли сценарий изменения/удаления исходной плитки до возврата? [Spec §User Story 2, §Edge Cases]
- [x] CHK012 Привязано ли состояние к защите от дублей запуска? [Spec §FR-011, §FR-020, §FR-021]

## Notes

- Все пункты закрыты текущей версией спецификации.
