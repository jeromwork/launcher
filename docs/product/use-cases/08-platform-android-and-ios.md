# 08. Platform — Android, OEM quirks, iOS parity

> **Status**: 🟡 partially decided (D-24 + D-14 RESOLVED 2026-05-27 evening) · **Created**: 2026-05-27

## iOS timing — decision (D-14)

**Resolved**: iOS Admin app — **post-MVP v2**. Architectural readiness уже через KMP + Compose Multiplatform (ADR-005). В MVP — Android-only.

Это значит:
- В MVP не строим iOS UI и не релизим в App Store.
- KMP `core/` уже работает cross-platform (compileKotlin Native подготовлен).
- При написании UI-слоя в MVP — inline TODO там, где Android-specific assumption: `// TODO(ios-post-mvp): этот pattern для Android, для iOS пересмотреть`.
- Post-MVP v2 — отдельная спека на iOS Admin preset, extends D-22 preset framework. UI пишется в Compose Multiplatform iosMain source set.

**Что не делаем вообще**:
- **iOS launcher** — platformatically impossible (SpringBoard замена недоступна).
- **iOS Managed companion** — out of MVP scope. Если когда-нибудь — отдельная большая спека (Guided Access / Supervised + MDM).

**Cross-platform pairing** (Android admin ↔ iOS Managed или наоборот) — отдельная concern, решается когда iOS adds entering picture.



## Android TV — decision (D-24)

**Resolved**: architectural readiness (KMP + Compose Multiplatform уже даёт), **inline TODO** в коде где relevant. **Real TV preset UI — post-MVP**.

Это значит:
- Сейчас **не строим** TV preset, но **не закрываем** future possibility.
- Preset framework (D-22) допускает добавление TV preset как третьего пакета без переписывания core.
- Inline TODO в спеках где UI завязан на phone-only assumption (touch gestures, portrait orientation): пометить «// TODO(tv-preset): пересмотреть для leanback».
- TV preset станет отдельной спекой после v1 launcher.

**iOS — отдельный D-14, остаётся открытым**.

> **Зачем читать**: всё, что выше (vision, UI, communications) предполагает, что **OS даст нам работать**. Часто не даёт — Samsung убивает background, Xiaomi отключает FCM, iOS не позволяет заменить SpringBoard. Здесь — инвентарь платформенных ограничений и решений.
> **Источник**: `user-journeys-draft.md` §7.7 + §7.8 + ADR-001 + ADR-005.

---

## Что это за документ (просто)

Android — это **не один**. Это Pixel + Samsung + Xiaomi + Huawei + ещё 50 OEM'ов, каждый со своей политикой «убивать или не убивать background apps», «доставлять или не доставлять FCM», «спрашивать или не спрашивать permission». Если мы это не учли, наш продукт работает у одних и не работает у других.

iOS — это **другой мир**. SpringBoard (launcher) нельзя заменить. Guided Access — частичный workaround. MDM — корпоративный. **Наш product roadmap'ом обязан** покрывать iOS (per ADR-005), но KAK — это open.

Этот документ — про **что OS позволяет, что не позволяет**, какие quirks у каждого OEM'a, как мы делаем cross-platform parity.

## Главные понятия (просто)

- **ROLE_HOME** — Android role, позволяющая нашему app быть «home launcher». Без неё мы не launcher. Запрашивается в спеке 010.
- **DPC (Device Policy Controller) / Device Owner** — мощный mode, при котором мы управляем устройством почти как iOS-MDM. Требует provisioning через QR / NFC при first boot. Для strict-mode (senior-safe-launcher-plan §5.1).
- **FCM (Firebase Cloud Messaging)** — Google's push delivery. На non-GMS устройствах (Huawei после 2020) не работает.
- **GMS (Google Mobile Services)** — пакет Google API. Без него FCM, Play Store, location services не работают. China-rom без GMS.
- **Doze mode** — Android идёт «спать», ограничивая background. Влияет на наш polling fallback (007).
- **OEM kill** — Samsung / Xiaomi / Huawei своими прошивками убивают background apps активнее, чем stock Android. Нужно battery-optimization whitelist.
- **Documented Platform Asymmetry** — официальный термин из ADR-005: «эта фича на iOS не работает, fallback такой-то». Не баг, а специально задокументированное ограничение.
- **expect/actual** — Kotlin Multiplatform механизм: declare in `commonMain`, implement in `androidMain` / `iosMain`. Наш способ cross-platform изоляции.

## Use case инвентарь

### Android — platform integration

| ID | Кейс | Status |
|---|---|---|
| PL-001 | ROLE_HOME grant flow | ✅ (010) |
| PL-002 | POST_NOTIFICATIONS Android 13+ | ✅ (010) |
| PL-003 | Custom call confirmation (intent intercept) | ✅ (010) |
| PL-004 | Foreground service types Android 14+ | ❓ |
| PL-005 | Package visibility Android 11+ (для 006 capabilities query) | ❓ |
| PL-006 | Scoped storage Android 10+ (для 012 photos) | ❓ |
| PL-007 | Battery optimization whitelist | 🟡 (010 SetupCheck) |
| PL-008 | OEM background restrictions (Samsung / Xiaomi / Huawei) | 🟡 (FCM polling fallback) |
| PL-009 | Doze mode handling | ✅ (007) |
| PL-010 | GMS-less device hard-block | ✅ (010) |
| PL-011 | Boot completion (launcher поднимается после reboot) | ❓ |
| PL-012 | App update / wire-format migration | 🟡 (rule 5) |
| PL-013 | OEM accessibility services compatibility | ❌ |
| PL-014 | Lock-screen widget / notifications controls | ❌ |
| PL-015 | Device Owner / DPC provisioning (strict mode) | 🔮 (senior-safe-launcher-plan §5.1) |

### iOS

| ID | Кейс | Status |
|---|---|---|
| iOS-001 | iOS launcher mode (заменить SpringBoard) | ❌ невозможно |
| iOS-002 | iOS как admin-only device (configure Android Managed) | 🔮 |
| iOS-003 | iOS как Managed (companion app, не launcher) | 🔮 |
| iOS-004 | iOS Guided Access / Supervised + MDM | 🔮 (strict mode) |
| iOS-005 | iOS contact tiles + share-intent | 🔮 |
| iOS-006 | Cross-platform pairing (admin iOS ↔ Managed Android) | 🔮 |
| iOS-007 | Documented Platform Asymmetry где живёт | 🟡 (ADR-005) |
| iOS-008 | Apple App Store policy gates | ❌ |
| iOS-009 | iCloud sync vs Firebase (storage choice) | ❌ one-way door, не решено |

## Главные открытые вопросы

### D-14. iOS — admin-only сейчас или подождать

**Контекст**: ADR-005 фиксирует iOS как **обязательный** target. Но конкретный shape — open. Launcher mode невозможен платформенно. Admin-app для iOS — большая отдельная вертикаль.

**Варианты**:
- **iOS уже в MVP (admin-only)**: admin может быть на iPhone. Управляет Android Managed. Плюс — у admin'ов часто iPhone (особенно в US). Минус — большая работа.
- **iOS в post-MVP v2**: сначала Android-admin-Android-Managed, потом добавляем iOS-admin.
- **iOS не делаем сейчас, ADR-005 пересматриваем**: rare; означало бы отказаться от обещания.

**Регрет**: iOS-admin отсутствует → admin'ы с iPhone не покупают (значимая часть US market).

**Рекомендация (best-guess)**: **post-MVP v2** (вариант 2). Architectural readiness уже сделана через KMP (ADR-005, спека 004). Полноценную iOS-admin вертикаль — следующим релизом.

### D-Plat-1. Strict mode (DPC / Device Owner) — делаем или нет

**Контекст**: senior-safe-launcher-plan §5.1 описывает strict mode (kiosk-policies, lock task). Без DPC мы не можем **полностью** запретить системные жесты, шторку, recents. Это значит: бабушка может случайно открыть settings и сломать что-то.

**Варианты**:
- **MVP without strict**: полагаемся на consumer-mode + 7-tap gate. Минус — есть способы попасть в settings.
- **MVP with strict (DPC provisioning)**: новое устройство сначала factory reset, потом enroll через QR. Сложнее для admin'а. Плюс — полная защита.
- **Optional strict**: default consumer, для тех кому надо — strict через DPC setup.

**Рекомендация**: optional strict. Большинству consumer mode достаточно. Для clinics / retirement homes — strict mode (B2B). Будущая спека.

### D-Plat-2. GMS-less (Китай, post-2020 Huawei) — в scope или нет

**Контекст**: спека 010 уже делает hard-block для non-GMS. Это **значит, что Huawei и китайский рынок отрезаны**. ARCH-005 в backlog — non-GMS support exit ramp.

**Варианты**:
- **MVP only GMS**: 90% мирового рынка покрыто. Минус — потеряли Huawei и China.
- **MVP includes non-GMS**: WorkManager polling вместо FCM. Сложнее, но шире рынок.
- **Hybrid**: GMS-default, non-GMS — отдельный build (как partner distribution для Huawei AppGallery).

**Рекомендация**: вариант 3 (hybrid через distribution channels). Связано с DIS-005 в 10-monetization-distribution-legal.

### D-Plat-3. OEM matrix testing — что обязательно

**Контекст**: spec 010 backlog SPEC010-DEV-001 — OEM matrix (Samsung One UI / Xiaomi MIUI / Pixel) — PHYSICAL DEVICE. Это значит **нужны реальные устройства**, эмулятор не покрывает.

**Варианты**:
- **Pixel only**: дешевле всех, baseline Android. Минус — не покрывает Samsung-specific issues.
- **Top 3** (Samsung + Xiaomi + Pixel): 80% global market.
- **Top 5+**: + Huawei (non-GMS) + Oppo / Vivo для Asia.

**Рекомендация**: top 3 в MVP. Top 5 — pre-release stage.

## Что в спеках уже зафиксировано

| Спек | Что фиксирует |
|---|---|
| 001 Launcher Core | ROLE_HOME basics, Android ownership |
| 004 UI Stack Migration | KMP + Compose Multiplatform (cross-platform readiness) |
| 007 Pairing | FCM + 15min polling fallback (Doze + OEM kill mitigation) |
| 010 Setup Assistant | ROLE_HOME, POST_NOTIFICATIONS, GMS-less hard-block, OEM matrix smoke (deferred) |
| ADR-001 | cross-platform strategy basics |
| ADR-005 | UI stack + Cross-Platform Implementation Gate + Documented Platform Asymmetry |
| `senior-safe-launcher-plan.md` §5.1 | DPC / Device Owner (strict mode) для Android |
| backlog ARCH-005 | non-GMS support via WorkManager polling |
| backlog ARCH-006 | enable R8 minification (PLAY-STORE-BLOCKER) |

## Связь с другими документами

- **01 Vision** — позиционирование (B2B clinic = strict mode required).
- **03 Launcher UI** — platform constraints определяют, что можно visually делать.
- **05 Pairing** — Android intent vs iOS deep linking.
- **06 Communications** — call intents — platform-specific.
- **09 Backend** — FCM зависит от GMS.
- **10 Distribution** — App Store vs Play Store vs AppGallery.

## Источники

- ADR-001 (cross-platform strategy partially superseded).
- ADR-005 (UI stack + Compose Multiplatform).
- `docs/product/senior-safe-launcher-plan.md` §5.
- `checklist-permissions-platform` skill.
- [Android Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).
- [iOS Guided Access](https://support.apple.com/en-us/HT202612).
- [Apple Configurator / MDM overview](https://support.apple.com/guide/apple-configurator-mac/welcome/mac).
- [Don't Kill My App](https://dontkillmyapp.com/) — OEM-specific quirks catalog.
- backlog `SPEC010-DEV-001`, `SPEC010-DEV-002`.

## Заметки решений

| Дата | Решение | Regret | Exit ramp |
|---|---|---|---|
| _(пусто)_ | | | |
