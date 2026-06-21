# Checklist: permissions-platform — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass

## Runtime permissions

- [N/A] **CHK001** Each runtime permission justified.
  - F-5c НЕ requests new runtime permissions (per security CHK015 action).
  - POST_NOTIFICATIONS explicitly NOT requested (data-only FCM does not require it).

- [N/A] **CHK002** First-launch permission flow specified. Не applicable — no permission requested.

- [N/A] **CHK003** Re-prompt strategy. N/A.

- [N/A] **CHK004** Settings deep-link для permanent denial. N/A.

- [N/A] **CHK005** Pre-permission rationale screen. N/A.

## Manifest declarations

- [⚠️] **CHK006** Required permissions в AndroidManifest.xml listed.
  - **Уже в manifest** (через F-5b dependencies):
    - `android.permission.INTERNET` — required для HTTP/Firestore/FCM.
  - **Может появиться через FCM SDK** (transitive):
    - `android.permission.WAKE_LOCK` — Firebase Messaging SDK adds через manifest merger для wake device от doze.
    - `com.google.android.c2dm.permission.RECEIVE` — Firebase Messaging SDK adds.
  - **Не добавляет F-5c explicit** новых permissions.
  - **Action**: при implementation проверить manifest merger output — убедиться что только expected perms (INTERNET, WAKE_LOCK, RECEIVE). No broad perms.

- [N/A] **CHK007** `<uses-feature>` declarations. F-5c не вводит hardware requirements.

- [N/A] **CHK008** `<queries>` element. F-5c не inspects other apps.

## Android version specifics

- [N/A] **CHK009** Scoped storage. F-5c не делает file I/O.

- [x] **CHK010** Foreground service type.
  - `LauncherFirebaseMessagingService` extends `FirebaseMessagingService` — это **background service**, не foreground service. Android 14+ foreground service types только для foreground services.
  - Firebase Messaging SDK handles service lifecycle — не требует custom `android:foregroundServiceType` declaration.

- [N/A] **CHK011** Exact alarms. F-5c не использует AlarmManager.

- [x] **CHK012** POST_NOTIFICATIONS на Android 13+.
  - F-5c использует **data-only** FCM messages (no `notification` key в payload).
  - **Data-only messages delivered без POST_NOTIFICATIONS permission** (Google docs).
  - Per security CHK015 action: FR будет explicitly stating «F-5c MUST NOT request POST_NOTIFICATIONS».
  - **Future event types** (например SOS) которые показывают visible alert — будут requesting permission в своих specs (S-4), не в F-5c.

- [N/A] **CHK013** Predictive back gesture. F-5c не вводит UI screens с back override.

## HOME / launcher role

- [N/A] **CHK014** Feature interacts с HOME/ROLE_HOME. Не applicable.

- [N/A] **CHK015** Feature requires being default launcher. Не applicable.

## OEM quirks

- [N/A] **CHK016** Samsung KNOX AccessibilityService restrictions. F-5c не uses AccessibilityService.

- [x] **CHK017** Xiaomi MIUI aggressive battery saver / autostart.
  - F-5c background FCM message receipt **same OEM constraint** как spec 007 + spec 018 (already verified на Xiaomi 11T per roadmap.md:116).
  - **Mitigation**: setup wizard (S-1 territory) shows autostart deep-link при first run. F-5c reuses этот mitigation — не вводит new code.
  - Documented в spec.md OEM Matrix.

- [x] **CHK018** Huawei EMUI Push-to-protected-apps.
  - Same OEM matrix entry. Reuses spec 007 mitigation. Документировано.

- [N/A] **CHK019** OEM launcher-replacement quirks. F-5c не HOME-related feature.

## Package visibility

- [N/A] **CHK020** `<queries>` для inspected apps. F-5c не inspects other apps.

- [N/A] **CHK021** `QUERY_ALL_PACKAGES`. Не requested.

## Compliance docs

- [⚠️] **CHK022** `docs/compliance/permissions-and-resource-budget.md` updated.
  - **Не updated в этом spec'е** (правка не сделана).
  - **Already raised в security CHK018 action**: добавить task в tasks.md «update permissions-and-resource-budget.md: F-5c uses data-only FCM (no POST_NOTIFICATIONS), inherits INTERNET + WAKE_LOCK + Firebase Messaging perms via SDK manifest merger».

## Summary

- **Pass**: 4/22
- **Partial/Warning**: 2/22 (CHK006, CHK022)
- **Fail**: 0/22
- **N/A**: 16/22

**Big picture**: Permissions-platform — **minimal surface**:
- F-5c не requests new runtime permissions.
- Не вводит new manifest perms (relies on transitive из Firebase SDK).
- OEM concerns reused from existing spec 007 (FCM background message receipt mitigations).
- No HOME / launcher role / AccessibilityService interaction.
- Data-only FCM — не требует POST_NOTIFICATIONS.

**Concerns** (operational):
1. Verify Firebase SDK manifest merger output (CHK006) — что только INTERNET + WAKE_LOCK + Firebase-specific perms.
2. Update `permissions-and-resource-budget.md` (CHK022 — dup с security CHK018).

## Action items

1. **Низкая** (для tasks.md, dup с security CHK018): update `docs/compliance/permissions-and-resource-budget.md` с F-5c entry.
2. **Низкая** (для plan.md, при implementation): verify manifest merger output, document any transitive perms.

---

## Заметка для новичка (TL;DR)

Проверено: правильно ли работаем с Android permissions, OEM-quirks (Xiaomi/Huawei/Samsung), особенностями новых версий Android.

**Хорошо сделано** (4 pass, 16 N/A):
- F-5c **не запрашивает новых permissions** — мы умышленно используем data-only FCM, которые не требуют разрешения на уведомления (Android 13+).
- Не вводим работу с файлами / экзотическими hardware features / inspection других приложений.
- OEM проблемы (Xiaomi блокирует фоновую работу FCM по умолчанию) **уже решены** в spec 007 — F-5c переиспользует тот же fix через wizard, никакого нового кода.

**Чего не хватает** (2 «частично»):
- Когда будем имплементировать — проверить какие permissions Firebase Messaging SDK сам добавляет в manifest. Должно быть только INTERNET + WAKE_LOCK + Firebase-specific. Никаких широких permissions.
- Обновить файл `docs/compliance/permissions-and-resource-budget.md` (учётная таблица проекта по permissions) при implementation.

**Не блокирует** /speckit.plan. 2 операционных task'и для plan.md / tasks.md.
