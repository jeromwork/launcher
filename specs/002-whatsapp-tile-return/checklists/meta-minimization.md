# Meta Minimization Checklist: WhatsApp Contact Tiles via Communication Shell

**Purpose**: Проверить, что требования формулируют минимально необходимый набор действий, данных и разрешений для безопасной handoff-последовательности без лишней сложности.  
**Created**: 2026-04-02  
**Feature**: [spec.md](./spec.md)

## Scope Minimalism

- [x] CHK001 Указано ли, что плитка может инициировать только одно действие за раз (Call или Video) и не допускается расширенный список действий? [Completeness, Spec §FR-003, §FR-004]
- [x] CHK002 Подтверждено ли, что UI плитки ограничен фото+имя сверху и двумя кнопками снизу (нет дополнительных элементов, индикаторов или меню)? [Clarity, Spec §Clarifications, §FR-002, §FR-003]
- [x] CHK003 Описано ли, что confirmation flow не требует дополнительных шагов помимо выбора действия и подтверждения? [Completeness, Spec §User Story 1, §FR-005, §FR-006]

## Data Minimalism

- [x] CHK004 Уточнено ли, какие именно данные хранятся в `Return Context`, и удалены ли все лишние поля? [Clarity, Spec §FR-009, §FR-011, §Key Entities]
- [x] CHK005 Записано ли, что `Return Context` используется только ради восстановления домашнего экрана и не хранит содержимое сообщений или прочую чувствительную информацию? [Ambiguity, Spec §FR-010, §Key Entities]
- [x] CHK006 Есть ли требование, ограничивающее количество сохранённых слоёв состояния (например, только текущая плитка), чтобы не раздувать контекст? [Gap, Spec §FR-012, §FR-013]

## Permission & Integration Minimalism

- [x] CHK007 Очерчено ли, какие минимальные разрешения требуются от Launcher и вызываются ли они только во время подтверждённого handoff? [Completeness, Spec §Constraints, §FR-007, §FR-038]
- [x] CHK008 Подтверждена ли обязанность Launcher не расширять API/хендшифтов WhatsApp сверх обоснованного handoff (чтобы избежать full cross-app control)? [Constraint, Spec §Out of Scope, §FR-025, §FR-037]
- [x] CHK009 Описана ли минималистичная стратегия реакции, когда WhatsApp недоступен (откат в Launcher без дополнительных опций)? [Coverage, Spec §FR-017, §FR-018, §Warning State]

## Edge-Case Minimalism

- [x] CHK010 Уточнено ли поведение при повторных нажатиях, чтобы не запускалось несколько handoff-ов и UI не порождал лишние переходы? [Consistency, Spec §Edge Cases, §FR-020, §FR-021]
- [x] CHK011 Описано ли, что возврат в Launcher происходит только с нужным контекстом, без дополнительных шагов согласования контекста? [Consistency, Spec §User Story 2, §FR-014, §FR-015]
- [x] CHK012 Проверено ли, что out-of-scope зоны (embedded UI, device readiness, background tracking) окончательно исключены, чтобы не вводить не минимальные требования? [Gap, Spec §Out of Scope, §Constraints]

## Behavioral Minimalism

- [x] CHK013 Прописан ли простой UX (из плитки на экран подтверждения и обратно) без ветвлений или дополнительных состояний? [Clarity, Spec §User Scenarios, §FR-005, §FR-006, §FR-016]
- [x] CHK014 Описано ли, что mock-конфигурация не требует множественных отдельных опций, а всегда предоставляет минимально достаточный набор контакт+действие? [Traceability, Spec §FR-023, §FR-024, §Assumptions]
- [x] CHK015 Есть ли требование, что возвращение из WhatsApp не вызывает сложных пересмотров UX (не требуется дополнительного согласования/настройки)? [Coverage, Spec §FR-014, §FR-015, §FR-039]

## Notes

- Все пункты закрыты текущей версией спецификации.
