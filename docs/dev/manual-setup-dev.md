# Manual setup — dev окружение

Накопительный runbook **ручных действий**, которые разработчик должен сделать,
чтобы dev-сборка проекта собиралась и запускалась на его машине.

**Аудитория**: разработчик, впервые клонирующий репозиторий, **или** разработчик,
которому нужно пересобрать dev-окружение после смены машины.

**Принцип**: каждая спека, требующая ручных действий в dev-окружении, добавляет
сюда секцию. Не дублируем то, что Gradle / npm уже автоматизировали — только
то, что **физически нельзя автоматизировать** (внешние сервисы, ручные клики
в браузере, физические артефакты).

**Парный файл**: [`manual-setup-production.md`](manual-setup-production.md) —
почти те же шаги, но для production project'а + release.

---

## 0. Машина + tooling

**Один раз на машину**:

1. **JDK 21+**:
   ```bash
   java -version  # должно показать 21 или выше
   ```
   Если нет — скачать [Temurin 21](https://adoptium.net/temurin/releases/?version=21).

2. **Android SDK** через Android Studio:
   - Скачать [Android Studio](https://developer.android.com/studio).
   - Через Studio установить SDK Platform 35 (минимум) + Build Tools.
   - Прописать `ANDROID_HOME` в переменных среды.

3. **Firebase CLI** (для Firestore rules deploy + emulator):
   ```bash
   npm install -g firebase-tools
   firebase login   # откроется браузер, залогиниться dev Gmail аккаунтом
   ```

4. **adb работает с устройством**:
   ```bash
   adb devices  # должен показать твой эмулятор / телефон
   ```

---

## 1. Spec 007 — Firebase backend (dev project)

Spec 007 (Firebase Auth + Firestore + FCM) — основа всех cloud features.

### 1.1. Firebase project (одноразовое создание dev project'а)

**Только для первого dev'а** проекта. Для последующих — пропускать, project уже
существует, просто получить доступ.

1. Зайти в [Firebase Console](https://console.firebase.google.com).
2. **Add project** → имя `launcher-old-dev` (или своё, если первый).
3. **Disable Google Analytics** (dev environment, не нужно).
4. Создать → подождать пока Firebase подготовит ресурсы.

### 1.2. Доступ для нового разработчика

**Owner project'а** добавляет нового dev'а:

1. Project Settings → **Users and permissions** → **Add member**.
2. Email нового developer'а → role **Editor**.
3. Отправить ему ссылку: `https://console.firebase.google.com/project/launcher-old-dev`.

### 1.3. Скачать `google-services.json` (каждый dev сам)

1. https://console.firebase.google.com/project/launcher-old-dev/settings/general
2. В блоке **Your apps** → Android app `com.launcher.app`.
3. Кнопка **Download google-services.json**.
4. Скопировать в `app/google-services.json` (файл уже в `.gitignore`, не закоммитится).

### 1.4. Зарегистрировать **свой** SHA-1 fingerprint

Каждый dev подписывает APK **своим** debug keystore'ом — поэтому каждый должен
зарегистрировать свой SHA-1.

1. Получить свой SHA-1:
   ```bash
   keytool -list -v -alias androiddebugkey \
           -keystore ~/.android/debug.keystore \
           -storepass android -keypass android | grep SHA1
   ```
   Скопировать значение `SHA1: XX:XX:...`.

2. Открыть [Project Settings](https://console.firebase.google.com/project/launcher-old-dev/settings/general).
3. В карточке Android app → блок **SHA certificate fingerprints**.
4. **Add fingerprint** → вставить SHA-1 → Save.

5. **Скачать обновлённый `google-services.json`** (после добавления fingerprint'а
   Firebase обновит файл) → положить в `app/google-services.json`.

---

## 2. Spec 017 (F-4) — Google Sign-In

После spec 007 нужно дополнительно включить **Google как auth provider**.

### 2.1. Enable Google provider

1. https://console.firebase.google.com/project/launcher-old-dev/authentication/providers
2. **Google** → **Enable**.
3. **Project support email** → твой Gmail.
4. **Save**.

### 2.2. OAuth Consent Screen (для dev — минимальная настройка)

Если Firebase Console предложит создать OAuth Consent Screen — соглашайся:
1. **App name**: `Launcher Dev`.
2. **User support email**: твой Gmail.
3. **Scopes**: оставить по умолчанию (`openid`, `email`, `profile`).
4. **Test users** (для dev'а можно пропустить, для prod — обязательно).
5. **Save**.

### 2.3. Скачать обновлённый `google-services.json`

После включения Google provider в `google-services.json` появится `oauth_client`
с `client_type: 3` (Web Application Client ID). Этот ID используется в
[`BackendInit.kt`](../../core/src/androidRealBackend/kotlin/com/launcher/di/BackendInit.kt)
как `serverClientId`.

1. Скачать новый `google-services.json` (см. §1.3).
2. Заменить `app/google-services.json`.

### 2.4. Deploy Firestore Rules

Spec 017 добавляет правила для коллекций `identity-links/*` и `users/*`.
Они должны быть deployed к dev project, иначе Sign-In не сможет создать
identity-link и вход провалится с `PERMISSION_DENIED`.

```bash
firebase use launcher-old-dev
firebase deploy --only firestore:rules
```

---

## 3. Spec 018 (F-5b) — envelope storage + rules tests

После Spec 007 + Spec 017 setup, F-5b добавляет:
- новые Firestore коллекции (`/users/{uid}/data/`, `/users/{uid}/devices/`, `/users/{uid}/access-grants/`),
- Security Rules для них (commit `8e327f9` в branch `018-f5-config-e2e-encryption`),
- TypeScript rules unit tests + опционально Android instrumented E2E.

### 3.1. Deploy F-5b Firestore Rules (если уже работаете на dev project)

Rules уже в [`firestore.rules`](../../firestore.rules) — коммит Batch 4. Deploy на dev:

```bash
firebase use launcher-old-dev   # (или ваше имя dev project'а)
firebase deploy --only firestore:rules
```

Если deploy fails с "rules compile error" — поправить syntax (Firebase CLI выводит конкретную строку) и повторить.

### 3.2. Локальный Firebase Emulator (для rules unit tests)

Установить Firebase CLI глобально (если ещё нет — см. §0.3) и тестовые npm deps:

```bash
cd firestore-tests
npm install            # одноразово, ставит @firebase/rules-unit-testing, vitest, и т.д.
npm test               # поднимает emulator на 8080/9099 → гоняет все *.test.ts → гасит
```

Ожидается зелёное на всех файлах:
- `rules.test.ts` (Spec 007 pairings — 14 tests),
- `rules.auth.test.ts` (Spec 017 identity-links — 7 tests),
- `rules.f5.recovery.test.ts` (Spec 018 F-5 recovery vault — 20 tests),
- `rules.f5b.envelope.test.ts` (Spec 018 F-5b envelope + devices + grants — 22 tests, добавлено в коммите `06af513`).

Эмулятор использует sandboxed `demo-test` project — НЕ требует google-services.json
и НЕ задевает ваш dev Firebase. Безопасно гонять на CI и локально.

### 3.3. Android instrumented E2E (CloudConfigEncryptionE2ETest)

[CloudConfigEncryptionE2ETest](../../app/src/androidTest/java/com/launcher/app/data/envelope/CloudConfigEncryptionE2ETest.kt)
гоняет два сценария на реальном Android-устройстве (emulator или физ.):

1. **roundtripOwnConfigByteEqual** — saveOwn → loadOwn → byte-equal.
2. **sc001OpacityRawFirestoreDocDoesNotLeakPlaintext** — SC-001 acceptance:
   прямое чтение Firestore document body → assert «Bobby Tables 555-1234»
   plaintext там не появляется.

**Два режима запуска**:

#### A. Против local Firebase Emulator на AVD (рекомендуется для CI / quick dev loop)

Не задевает cloud, не жжёт quota, не оставляет мусор.

```bash
# Terminal 1: поднять эмуляторы
firebase emulators:start --only firestore,auth

# Terminal 2: build с -PuseFirebaseEmulator=true → SDK routed на 10.0.2.2:8080/9099.
# Anonymous sign-in доступен в эмуляторе — тест проходит без cloud.
./gradlew :app:connectedRealBackendDebugAndroidTest -PuseFirebaseEmulator=true
```

Требует AVD Android 14+ (API 34/35 recommended).

#### A'. Против local Firebase Emulator на **реальном** USB-устройстве

Хост-`10.0.2.2` доступен только AVD. На реальном телефоне переадресуем
порты через `adb reverse` и сообщаем Wiring новый хост через
`-PfirebaseEmulatorHost=127.0.0.1`:

```bash
# Terminal 1: поднять эмуляторы на PC
firebase emulators:start --only firestore,auth

# Terminal 2: проверить, что устройство одно (если есть AVD — отключить)
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" devices
# Перенаправить порты с device:127.0.0.1 на host:127.0.0.1
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" reverse tcp:8080 tcp:8080
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" reverse tcp:9099 tcp:9099

# Запустить E2E
./gradlew :app:connectedRealBackendDebugAndroidTest \
    -PuseFirebaseEmulator=true \
    -PfirebaseEmulatorHost=127.0.0.1
```

Покрывает то же, что вариант A, плюс TEE-backed Keystore + OEM-specific
quirks (Xiaomi MIUI «Optimize» cleanup, Samsung Knox).

`adb reverse` действует только пока устройство в той же ADB-сессии
(пропадает после `adb kill-server` / reboot / отключения кабеля) — нужно
прогнать ещё раз перед следующим запуском теста.

Полезно параллельно открыть зеркало экрана:
```bash
scrcpy --serial=<device-serial> --window-title="Xiaomi 11T"
```

#### B. Против real `launcher-old-dev` Firestore (real-cloud confidence)

```bash
./gradlew :app:connectedRealBackendDebugAndroidTest    # без -P флага
```

⚠️ Anonymous sign-in **выключен** на real cloud (decision 2026-05-30 удалил
anonymous Firebase Auth). Тест будет skipped через `Assume.assumeNoException`
если на устройстве нет signed-in Google user'а. Для полного прогона:
запустить app один раз, Sign-In через Google → выйти из app → запустить
instrumented test.

Тест cleanup'ит свой namespace (`teardown()` удаляет device entry) — после
прогона dev project остаётся чистым.

---

## 4. Проверка что всё работает

После всех шагов:

```bash
./gradlew :app:assembleRealBackendDebug
adb install -r app/build/outputs/apk/realBackend/debug/app-realBackend-debug.apk
adb shell am start -n com.launcher.app/com.launcher.app.firstlaunch.FirstLaunchActivity
```

В wizard'е:
1. Выбрать preset → нажать **Войти в Google для восстановления настроек**.
2. Нажать **Войти в аккаунт** → должен появиться bottom-sheet с выбором Google аккаунта.
3. Выбрать аккаунт → wizard переходит к шагу 3 (RoleHome).

Если шаг 2 показывает «Не удалось войти» — посмотреть логи:
```bash
adb logcat -d | grep "I Auth\|W Auth\|E Auth"
```
И сверить с troubleshooting в [`auth-setup.md`](auth-setup.md).

---

## История изменений

| Дата | Что добавлено | Спека |
|------|---------------|-------|
| 2026-06-19 | Spec 007 (Firebase backend) + Spec 017 (Google Sign-In) | F-4 |
