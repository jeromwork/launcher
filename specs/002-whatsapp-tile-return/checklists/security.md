# Security Checklist: WhatsApp Contact Tiles via Communication Shell

**Purpose**: Проверка качества security-требований в спецификации (полнота, ясность, согласованность, измеримость), а не проверка реализации.
**Created**: 2026-04-02
**Feature**: [spec.md](../spec.md)

**Note**: Чеклист оценивает, достаточно ли четко и полно в требованиях описаны вопросы безопасности и приватности для handoff-потока Launcher -> WhatsApp -> Launcher.

## Полнота требований безопасности

- [ ] CHK001 Определены ли требования к защите данных `Return Context` при хранении и чтении? [Completeness, Spec §Key Entities, Spec §FR-007, Spec §FR-008]
- [ ] CHK002 Зафиксировано ли, какие данные контакта допускаются в `Return Context`, а какие должны быть исключены? [Gap, Spec §Key Entities, Spec §Constraints]
- [ ] CHK003 Описаны ли требования безопасности для обоих действий `Call` и `Video` одинаково полно? [Completeness, Spec §FR-003, Spec §User Story 1]
- [ ] CHK004 Указаны ли требования к безопасному поведению при неуспешном handoff в WhatsApp? [Completeness, Spec §FR-010]

## Ясность и однозначность

- [ ] CHK005 Ясно ли определено, что означает "minimal permissions" в контексте этой фичи? [Ambiguity, Spec §Constraints]
- [ ] CHK006 Однозначно ли разделены зоны ответственности безопасности между Launcher и внешним мессенджером? [Clarity, Spec §Constraints, Spec §FR-014]
- [ ] CHK007 Уточнено ли, какие security-ограничения подразумеваются под "controlled launch into WhatsApp scenario"? [Ambiguity, Spec §In Scope, Spec §FR-006]
- [ ] CHK008 Исключены ли двусмысленные формулировки про "honest cross-platform promises" с точки зрения security-ожиданий? [Clarity, Spec §Constraints, Spec §FR-014]

## Согласованность требований

- [ ] CHK009 Согласованы ли Out of Scope ограничения (нет full cross-app control) с security-требованиями handoff-потока? [Consistency, Spec §Out of Scope, Spec §FR-014]
- [ ] CHK010 Не конфликтуют ли требования минимальных разрешений с необходимостью восстановления состояния после возврата? [Consistency, Spec §Constraints, Spec §FR-007, Spec §FR-008]
- [ ] CHK011 Согласованы ли требования защиты от дублей запуска с требованиями явного подтверждения действия? [Consistency, Spec §FR-005, Spec §FR-011]

## Покрытие негативных и атакующих сценариев

- [ ] CHK012 Описано ли требуемое поведение при попытке многократных быстрых нажатий как потенциальном злоупотреблении? [Coverage, Spec §Edge Cases, Spec §FR-011]
- [ ] CHK013 Определены ли требования при возврате в Launcher после длительной паузы с точки зрения целостности контекста? [Coverage, Spec §Edge Cases, Spec §FR-008]
- [ ] CHK014 Указаны ли требования для сценария, когда целевой action недоступен, чтобы исключить небезопасные fallback-переходы? [Coverage, Spec §Edge Cases, Spec §FR-010]
- [ ] CHK015 Описано ли поведение при возвращении разными способами (Back/Home/App Switcher) с одинаковыми security-гарантиями? [Coverage, Spec §Edge Cases, Spec §FR-012]

## Нефункциональные security-требования

- [ ] CHK016 Зафиксированы ли требования к аудиту/логированию security-событий без нарушения ограничения "No heavy background observers"? [Gap, Non-Functional, Spec §Constraints]
- [ ] CHK017 Определены ли измеримые критерии по инцидентам безопасности или privacy-утечкам в Success Criteria? [Gap, Spec §Success Criteria]
- [ ] CHK018 Указаны ли требования по времени жизни/сбросу `Return Context`, чтобы снизить риск несанкционированного повторного использования? [Gap, Spec §Key Entities, Spec §FR-007]

## Зависимости, допущения и приватность

- [ ] CHK019 Явно ли отражены security-риски зависимости от внешнего приложения (WhatsApp) и их границы ответственности? [Dependency, Spec §Assumptions, Spec §Constraints]
- [ ] CHK020 Определено ли, какие допущения о наличии WhatsApp влияют на security-поведение при отсутствии/ошибках приложения? [Assumption, Spec §Assumptions, Spec §FR-010]
- [ ] CHK021 Зафиксировано ли, что mock-конфигурация не должна ослаблять требования к защите данных контактов? [Assumption, Spec §FR-013, Spec §Assumptions]
- [ ] CHK022 Описаны ли требования к минимизации персональных данных в UI и переходных состояниях? [Gap, Privacy, Spec §FR-002, Spec §FR-004, Spec §Key Entities]

## Notes

- Отмечайте пункт как выполненный: `[x]`.
- При выявлении пробела добавляйте ссылку на раздел спецификации, который нужно уточнить.
- Чеклист оценивает качество требований, а не поведение приложения в рантайме.
