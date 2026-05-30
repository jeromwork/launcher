# 02 — Unified app model: один app, два режима через preset

## Решение

**Один Android app**, который после установки работает в Standard mode (полный admin UI), после wizard'а может быть переведён в Senior mode (упрощённый фасад). 7-tap из Senior возвращает обратно в Standard mode для редактирования.

**Не существует** двух отдельных APK (Admin app + Simple Launcher). Не существует двух разных кодовых баз. Один продукт, один Play Store listing, одна установка.

## Уточнение терминологии «mode» vs «preset»

Это **не жёстко заданные моды в enum**. Это **именованные пулы конфигов** (preset'ы), которые владелец применяет / редактирует / создаёт новые.

```
Simple Launcher preset (один из дефолтных)
├─ lock_volume_buttons: true
├─ lock_system_shade: true
├─ lock_home_screen: true
├─ tile_size: large
├─ show_widgets: false
└─ ... десятки настроек

Workspace preset (другой дефолтный)
├─ lock_volume_buttons: false
├─ tile_size: medium
├─ show_widgets: true
└─ ... меньше настроек (больше про визуализацию)
```

**Preset = именованный набор конфигов**, runtime-конфигурация. Владелец может применить дефолтный preset, отредактировать его, создать новый на его основе. Это соответствует спеке 014 (named configs до 5 per Google account) и CLAUDE.md rule 9 (preset = portable shareable artifact).

## Почему один app, не два

1. **Один Google-аккаунт = один owner = один app.** Бабушка / помощник / внук — все логинятся через свой Google. Не нужны разные APK для разных ролей.
2. **Senior mode не сам по себе продукт** — это конфигурация полного app. После 7-tap можно вернуться в Standard mode и перенастроить.
3. **Меньше дистрибутивов** = меньше releases, меньше Play Store listing'ов, меньше privacy policy review.
4. **Простая логика установки**: один app устанавливается, владелец проходит wizard, при необходимости активирует Senior preset.

## Сценарий установки

```
1. Кто-то (помощник, внук, или сама бабушка) ставит app из Play Store.
2. App стартует в Standard mode (полный Android UI).
3. Google Sign-In (через стандартный Android UI) — обязательно.
4. Wizard:
   - Кто пользователь этого устройства? (default / senior / kiosk / ...)
   - Связать с другим устройством? (опционально — pair с помощником)
   - Настроить плитки / контакты / приложения.
5. После wizard'а:
   - Если выбран Senior preset → app переключается в Senior mode (фасад).
   - Если default → остаётся в Standard mode.
6. 7-tap из Senior mode → временно Standard mode → можно редактировать → exit обратно в Senior.
```

**Ключевое:** wizard и Google Sign-In **всегда** проходят в Standard mode (нормальный Android UI). Это нормально, даже если устройство потом будет «бабушкин» — помощник проходит wizard за бабушку, или бабушка сама с увеличенным шрифтом.

## Что это меняет

- **Спека 014 (interpretation α'):** `EditUiProfileSelector` принимает runtime-preset (а не build-time enum). Pure function в domain. Spec 014 **не переписывается**, но в комментарий selector'а добавляется «preset = runtime named config, может быть user-customized».
- **Form factor — отдельная история.** TV — это **другой UI module** (см. файл 06), не runtime mode. Telegram-like разделение: один codebase, разные UI-сборки для разных form factor'ов, общий домен.
- **Modular delivery** (Constitution Article V §6): TV-фичи скачиваются как dynamic feature module только на TV-устройствах. На phone — phone UI module.

## Out of scope unified model

- Multi-user Android (system user accounts) — не используем. Owner = Google-аккаунт в app'е, не Android system user.
- Profile switching без 7-tap — нет, переключение только через 7-tap (защита от случайного выхода из Senior mode).
- TV в Senior mode — TV это другой form factor, не runtime mode phone-app.
