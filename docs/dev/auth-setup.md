# Auth setup guide (Spec 017 F-4)

Onboarding-инструкция для разработчика, который **впервые** запускает
project'у с Google Sign-In + Firebase Auth (`realBackend` flavor).

**Время на setup**: 15-30 минут.

**Если работаешь с `mockBackend` flavor** — этот гид можно пропустить.
`FakeAuthProvider` ничего не требует, signIn возвращает hardcoded fake
identity.

---

## 1. Firebase project

Если у тебя ещё нет dev Firebase project:

1. Идёшь на https://console.firebase.google.com
2. «Add project» → даёшь имя (например, `launcher-dev-<твоё-имя>`).
3. Google Analytics — выключить (для dev не нужно).
4. Создаётся проект.

В созданном проекте:

1. Project Settings → General → Your apps → «Add app» → Android.
2. Package name: `com.launcher.app`.
3. App nickname: что угодно (например, "Launcher Dev").
4. **SHA-1 certificate fingerprint** — обязательно (без него Google
   Sign-In не работает на твоём устройстве):
   ```
   keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore \
           -storepass android -keypass android | grep SHA1
   ```
   Скопируй hex после "SHA1:" → вставь в Firebase Console.
5. Download `google-services.json` → положи в `app/google-services.json`
   (он в `.gitignore`).

---

## 2. Enable Google Sign-In provider

В Firebase Console:

1. Authentication → Sign-in method → Google → Enable.
2. Project support email — твой gmail.
3. Sохрани.

После Enable в Firebase Console появится **Web client ID** (формат
`<long-number>-<long-hash>.apps.googleusercontent.com`). Этот ID —
параметр `serverClientId` в `GoogleSignInAuthAdapter` (см.
[BackendInit.kt][backend-init]). На dev этапе TODO-placeholder, перед
production T901 admin task поменяет на реальный.

[backend-init]: ../../core/src/androidRealBackend/kotlin/com/launcher/di/BackendInit.kt

---

## 3. OAuth Consent Screen

Только для **production** Firebase project (T902 admin task). На dev
этапе пропустить.

Когда дойдёт черёд:

1. Google Cloud Console (https://console.cloud.google.com) → выбрать проект,
   связанный с Firebase project.
2. APIs & Services → OAuth Consent Screen.
3. User type: External.
4. App name, support email.
5. **Scopes**: только `openid`, `email`, `profile`. **Никаких**
   `calendar`/`contacts`/`drive` — Detekt rule `Spec017AuthIsolationTest.T795`
   будет ругаться при попытке добавить.
6. Submit for Google verification (1-2 недели до production approval).

---

## 4. Firestore Security Rules — local emulator setup

Для запуска `firestore-tests/rules.auth.test.ts` локально:

1. Установи Java 21+ (firebase-tools 14+ требует).
2. `npm install -g firebase-tools` (если не установлен).
3. Из корня репо:
   ```
   firebase emulators:start --only firestore,auth
   ```
4. В отдельном терминале:
   ```
   cd firestore-tests
   npm install
   npm test
   ```

---

## 5. Common issues

### «PackageManager.NameNotFoundException com.google.android.gms»

Эмулятор без Google Play Services. Используй **Google Play** Image
(не "Google APIs"). При создании AVD выбирай target с пометкой
"Google Play".

### "InvalidIdTokenException" в logcat

SHA-1 не зарегистрирован в Firebase Console (или это release SHA-1, а ты
запускаешь debug build). Проверь:
- SHA-1 совпадает с твоим debug keystore (см. §1).
- `google-services.json` в `app/` — последняя версия после добавления SHA-1.

### "Sign-in cancelled" сразу после открытия bottom-sheet

`serverClientId` placeholder `TODO_REPLACE_BEFORE_RELEASE_T901` не
заменён. В dev этапе нужно подставить **dev** Web Client ID — это
обычно делается локальным override в `BackendInit.kt` (не коммитить).

### Backup-restore возвращает пустую сессию

Это **by design**. EncryptedLocalSessionStore хранит сессию в
`auth_session_v1.preferences.xml`, который исключён из Auto Backup +
Device Transfer (см. `app/src/main/res/xml/data_extraction_rules.xml`).
После restore Google инвалидирует refresh token, поэтому даже если
бы blob восстановился — он был бы useless. Безопаснее заставить
заново signIn.

---

## 6. Что делать дальше

После того как Google Sign-In работает:

- Spec 017 готов к Phase 4-7 instrumentation тестам (требуют эмулятор).
- Smoke test: открой приложение, тапни кнопку «Войти в аккаунт»
  (`SignInTrigger`), выбери Google account → должен показаться экран
  "Вошли как <твой email>".

Если что-то не так — изучи logcat (`adb logcat -s Auth`) и сравни с
expected log events в [research.md §R8 (Logging policy)][r8].

[r8]: ../../specs/017-f4-auth-provider/research.md#r8-logging-policy
