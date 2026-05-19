# Senior-safe walkthrough — спек 010 setup-assistant

**Цель**: ручной QA-пасс с пожилым тестовым пользователем (или его симуляцией)
перед ship. 5 сценариев покрывают golden path + критические edge cases
(US-1..US-7 + CHK-elderly-022).

**Когда выполнять**: после Phase 7 complete, до открытия PR. Запускать
повторно перед каждым release-candidate.

---

## Scenario 1 — Fresh install + wizard (US-1)

**Cast**: бабушка получает новый телефон, запускает приложение впервые.

**Setup**:
```
adb shell pm clear com.launcher.app.mock
adb shell am start -n com.launcher.app.mock/.firstlaunch.FirstLaunchActivity
```

**Steps**:
1. App запускается → preset picker (FR-007a).
2. Тап на Workspace → переход на RoleHomeStep (FR-008).
3. Тап «Сделать главным» → системный chooser «Открыть с помощью» (FR-009).
4. Выбрать Launcher → возврат на PostNotificationsStep.
5. Тап «Разрешить» → системный prompt POST_NOTIFICATIONS (FR-008a; Android 13+).
6. Allow → переход на Home (FR-010).

**Expected**:
- Каждое поле читаемо при 50 см от глаза (≥ 24 sp body, ≥ 56 dp tap targets).
- Wizard прогресс «Шаг 1 из 2» / «Шаг 2 из 2» visible.
- На Home Settings badge `!N=0` (всё критическое настроено).

**Failure modes**:
- Если на шаге 3 chooser не появляется → ROLE_HOME путь не работает; fallback
  `Intent.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` per FR-009a.
- Android 13- (`<33`): шаг 5 не возникает (PostNotifications не нужен);
  badge `?M` для recommended не показан.

---

## Scenario 2 — Tile→call в 2 таpа (US-2 + SC-003)

**Cast**: бабушка хочет позвонить внуку Мише (single tile labelled «Миша»).

**Setup**: один Workspace flow с одним tile, заранее установлен Misha contact.

**Steps**:
1. Home screen → бабушка видит «Миша» tile.
2. Тап на tile → `CallConfirmationDialog` (FR-014).
3. Тап «Позвонить» → System dialer рингует Misha.

**Expected**:
- ≤ 2 таpа от Home до dialer ring (SC-003).
- Диалог senior-safe: title «Позвонить Миша?», большая зелёная кнопка
  «Позвонить» + большая нейтральная «Отмена».
- ACTION_CALL fires если CALL_PHONE granted; иначе ACTION_DIAL (system
  dialer открывается с pre-filled number; one extra tap to call).

**Failure modes**:
- Если number Миши некорректен (`+` без цифр) → диалог показывает
  «Номер некорректен» (`call_confirm_invalid_number`) и call не fires.

---

## Scenario 3 — Accidental 7-tap + CANCEL (US-7)

**Cast**: бабушка случайно тапнула 7 раз на пустую область Home (что
бывает при чистке экрана от пыли).

**Steps**:
1. Home — бабушка касается пустой области ниже tiles много раз.
2. После 7 тапов в радиусе 48 dp в течение 5 sec → challenge screen
   появляется (FR-021 + FR-022).
3. Бабушка видит мелкий шрифт + клавиатуру → тап «Отмена» (большая кнопка
   сверху, FR-026b).
4. Возврат на Home без side effects.

**Expected**:
- На каждом тапе бабушка чувствует vibration (FR-021 escalation: light на
  1-3, medium на 4-7).
- CANCEL fires onCancel → pop() → Home unchanged.
- Бабушка не догадывается «что это было» — challenge text intentionally
  мелкий (≤ 14 sp).

**Failure modes**:
- Тапы > 48 dp apart → counter resets (FR-021b); 7-tap chain не достигается.
- > 5 sec window → counter resets (FR-021c).
- Тап на tile не считается (Compose pointerInput consumption).

---

## Scenario 4 — TalkBack admin entry (US-7 #7 + FR-027)

**Cast**: бабушка с включённым TalkBack (или admin с TalkBack).

**Setup**:
```
adb shell settings put secure enabled_accessibility_services \
    com.google.android.marvin.talkback/.TalkBackService
adb shell settings put secure accessibility_enabled 1
```

**Steps**:
1. Бабушка тапает 7 раз → challenge screen.
2. TalkBack zвучит «8472» (или sequence numbers).
3. Бабушка может ввести число через TalkBack double-tap navigation.
4. На правильный ответ → переход в admin-mode (AdminDevices).

**Expected**:
- TalkBack reads challenge text out loud (`contentDescription =
  challenge.answer`).
- CANCEL button focusable first by TalkBack swipe (FR-027).
- Admin entry workable for accessibility users — accepted edge per US-7
  Acceptance #7.

**Failure modes**:
- TalkBack disabled → challenge text visible only visually; не читается.
- Real accessibility-aware admin entry — future-spec
  `accessibility-admin-entry` (см. plan.md TODO).

---

## Scenario 5 — Paired-device unlink offline (US-5 + FR-032)

**Cast**: бабушка хочет «прекратить помощь» от Маши, но WiFi выключен.

**Setup**:
- Pre-paired link с admin «Маша» (mockBackend seed initial или real Firebase
  pairing).
- Airplane mode ON.

**Steps**:
1. Home → 7-tap → admin-mode (Scenario 3-4 path) → Settings →
   «Сопряжённые устройства».
2. Бабушка видит «Маша» в «Кто помогает мне».
3. Тап «Прекратить помощь» → `UnlinkConfirmationDialog` («Прекратить
   помощь от Маша? Маша больше не сможет менять твою раскладку»).
4. Тап «Да» → Маша **сразу** исчезает из списка (FR-032 (c) local UX
   guarantee).
5. Airplane mode OFF → wait ≤ 60 sec.

**Expected**:
- Между шагами 4 и 5: Маша **не возвращается** в список даже после app
  restart (DataStore persistence).
- Шаг 5: WorkManager `unlink_<linkId>` worker fires → `LinkRegistry.revoke()`
  → Firestore `/links/{linkId}` subtree deleted.
- Если Маша тоже delete'нула из своего admin-UI параллельно — worker
  idempotent no-op (FR-032a (c)).

**Failure modes**:
- WorkManager не fires из-за Doze mode — wake by user interaction → retries.
- Если LinkRegistry.revoke fails по network error → exponential backoff
  retry (FR-032a (d)).

---

## Failure log template

Каждый run сохранять как append-block:

```
### Run YYYY-MM-DD device=<device-model> os=<android-version>

Scenario 1: PASS / FAIL — <one-line note>
Scenario 2: PASS / FAIL — <one-line note>
Scenario 3: PASS / FAIL — <one-line note>
Scenario 4: PASS / FAIL — <one-line note>
Scenario 5: PASS / FAIL — <one-line note>

Notes:
- <observed issue>
- <captured screenshot link>
```

---

## Test runs

_(пустой; добавляется по мере выполнения; physical-device runs deferred
per memory `feedback_critical_mentor_stance.md` + `reference_testing_environment.md`)_
