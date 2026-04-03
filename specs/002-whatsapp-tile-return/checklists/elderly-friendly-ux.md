# Elderly-Friendly UX Checklist: WhatsApp Contact Tiles via Communication Shell

**Purpose**: Проверка того, что требования действительно ориентированы на пожилого пользователя: читаемость, моторика, предсказуемость, отсутствие перегрузки.  
**Created**: 2026-04-02  
**Feature**: [spec.md](./spec.md)

## Readability

- [x] CHK001 Определено ли, что ключевые элементы плитки и warning state должны быть крупными и читаемыми? [Spec §FR-030, §FR-031]
- [x] CHK002 Учитывается ли проблема длинных локализованных строк? [Spec §FR-027, §FR-028, §FR-029]
- [x] CHK003 Требуется ли нетехнический язык в предупреждениях? [Spec §FR-031]

## Motor Simplicity

- [x] CHK004 Ограничено ли число первичных действий на плитке до двух явных крупных кнопок? [Spec §FR-003, §FR-004]
- [x] CHK005 Есть ли защита от случайных повторных нажатий? [Spec §FR-020, §FR-021]
- [x] CHK006 Есть ли обязательный простой cancel path без штрафа для пользователя? [Spec §FR-006, §FR-016]

## Predictability

- [x] CHK007 Требуется ли отсутствие неожиданных прыжков после возврата? [Spec §FR-015]
- [x] CHK008 Описано ли стабильное fallback-поведение при проблемах возврата? [Spec §FR-039]
- [x] CHK009 Описано ли, что warning появляется вместо silent failure? [Spec §User Story 4, §SC-005]

## Accessibility & Assistive Support

- [x] CHK010 Учитывается ли поддержка assistive navigation на launcher-owned экранах? [Spec §FR-032]
- [x] CHK011 Требуется ли видимое подтверждение успеха, отмены и ошибки? [Spec §FR-033]
- [x] CHK012 Включены ли elderly-friendly аспекты в acceptance testing, а не отложены? [Spec §FR-040]

## Notes

- Все пункты закрыты текущей версией спецификации.
