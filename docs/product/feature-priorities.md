# Launcher — Feature Priorities

## Текущий продуктовый приоритет

### Must-have сейчас
1. Исследование и проектирование звонков/видеозвонков через мессенджеры.
2. Foundation-решения, которые заранее не блокируют этот сценарий:
   - action architecture,
   - app availability/fallback,
   - contact/communication model,
   - platform parity analysis,
   - permission/resource discipline.

### Must-have на архитектурном уровне
1. Android-first foundation.
2. Cross-platform-aware domain and entitlement design.
3. Billing and entitlement subsystem.
4. Localization readiness.
5. Compliance-aware planning.
6. Permissions/resource minimization.
7. Support/error/feedback operational loop.

### Не must-have прямо сейчас
1. Детальная раскладка плиток.
2. Продвинутые UX-вариации home screen.
3. Глубокая кастомизация layout.
4. Второстепенные UI-эксперименты.

## Приоритет принятия решений
Если возникает конфликт между:
- красивым UX-экспериментом,
- и foundation-решением, нужным для communication, monetization, compliance, localization, cross-platform,

то foundation-решение выигрывает.
