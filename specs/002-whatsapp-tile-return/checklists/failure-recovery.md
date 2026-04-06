# Failure & Recovery Checklist: WhatsApp Contact Tiles via Communication Shell

**Purpose**: Проверка качества требований по отказам, предупреждениям и восстановлению пользовательского пути.  
**Created**: 2026-04-02  
**Feature**: [spec.md](./spec.md)

## Failure Detection

- [x] CHK001 Указано ли, что отсутствие WhatsApp после первоначальной настройки не приводит к silent failure? [Spec §User Story 4, §FR-018]
- [x] CHK002 Указано ли, что недоступность конкретного действия (`Call` или `Video`) не приводит к неверному fallback-переходу? [Spec §FR-019]
- [x] CHK003 Указано ли, что ошибка handoff обнаруживается в момент действия пользователя, а не маскируется? [Spec §FR-017, §FR-033]

## Warning UX

- [x] CHK004 Определено ли, что warning state должен быть крупным и читаемым для пожилого пользователя? [Spec §FR-031]
- [x] CHK005 Определено ли, что warning text должен быть нетехническим и объяснять следующий шаг? [Spec §FR-017, §FR-031]
- [x] CHK006 Определено ли, что после warning пользователь остаётся в Launcher, а не попадает в неожиданный экран? [Spec §User Story 4, §FR-017, §FR-018, §FR-019]

## Recovery Behavior

- [x] CHK007 Зафиксировано ли, что после dismiss warning пользователь возвращается в стабильное домашнее состояние? [Spec §User Story 4]
- [x] CHK008 Описано ли поведение, если точное восстановление предыдущего состояния невозможно? [Spec §User Story 2, §FR-039, §Stable Home State]
- [x] CHK009 Описано ли, что возврат из WhatsApp должен быть согласован независимо от способа возврата? [Spec §FR-022]

## Testing Coverage

- [x] CHK010 Можно ли отдельно протестировать missing app, invalid action, stale context и fallback restore? [Spec §User Stories 2 and 4, §Edge Cases]
- [x] CHK011 Есть ли измеримые критерии того, что failures отрабатываются прозрачно, а не скрыто? [Spec §SC-005]
- [x] CHK012 Учитываются ли failure/recovery сценарии в feature-level acceptance testing? [Spec §FR-040]

## Notes

- Все пункты закрыты текущей версией спецификации.
