# Quickstart — F-5: Root Key Hierarchy + ConfigDocument Encryption + Recovery

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Шаги для разработчика, который ставит проект с нуля и хочет проверить F-5 локально.

---

## 0. Prerequisites

- **JDK 21** (обязательно, per memory `project_007_operational_state`).
- **Android SDK** с platform 34 и build-tools 34.x.
- **Android Studio Iguana+** или IntelliJ IDEA 2024.x с Kotlin Multiplatform plugin.
- **Firebase CLI** ≥ 13.x (для Emulator Suite): `npm install -g firebase-tools`.
- **Google account** для smoke testing real Sign-In (test fixture для unit-tests — не нужен).
- **Эмулятор** `pixel_5_api_34` (создаётся через skill `android-emulator` или AVD Manager).
- **Network**: `NO_PROXY=10.0.2.2,localhost,127.0.0.1` обязателен для эмулятора (per memory `project_007_operational_state`).

---

## 1. Build

```bash
cd c:/work/launcher
git checkout 018-f5-config-e2e-encryption
git pull

# Сборка нового module:
./gradlew :core:keys:assemble

# Полная сборка app с F-5:
./gradlew :app:assembleDebug
```

Expected: успешная сборка без warnings о missing dependencies. Если падает на ionspin libsodium — проверить, что `:core:crypto:assemble` проходит сам по себе (F-CRYPTO должен быть merged до F-5).

---

## 2. JVM unit tests

Базовый цикл тестов F-5 — все на JVM, без эмулятора:

```bash
# Все unit-тесты модуля:
./gradlew :core:keys:jvmTest

# Точечно:
./gradlew :core:keys:jvmTest --tests "*RootKeyManagerTest"
./gradlew :core:keys:jvmTest --tests "*KeyRegistryTest"
./gradlew :core:keys:jvmTest --tests "*ConfigCipherRoundtripTest"
./gradlew :core:keys:jvmTest --tests "*SealedConfigBackwardCompatTest"
./gradlew :core:keys:jvmTest --tests "*RecoveryVaultRoundtripTest"
./gradlew :core:keys:jvmTest --tests "*RecoveryVaultBackwardCompatTest"
./gradlew :core:keys:jvmTest --tests "*MultiIdentityIsolationTest"
```

Expected: все тесты зелёные. Время прогона ≤ 30 сек на mid-tier dev machine (Argon2id в тестах использует **reduced params** через test-fixture для скорости, real params — в smoke).

---

## 3. Firebase Emulator setup

Integration-тесты (`RecoveryFlowE2ETest`) требуют Firestore Emulator:

```bash
cd c:/work/launcher/firebase
firebase emulators:start --only firestore,auth
```

Expected output: `✔ All emulators ready! View status and logs at http://127.0.0.1:4000/`

Запустить integration тесты:

```bash
# В отдельном терминале (emulators продолжают работать):
./gradlew :core:keys:jvmTest --tests "*RecoveryFlowE2ETest"
```

Firestore Emulator UI: открыть `http://127.0.0.1:4000/firestore` — должны появиться записи `users/{test-uid}/recovery-key/...`.

**Деплой обновлённых Security Rules в Emulator** (если изменили `firestore.rules`):
```bash
# Emulator перечитывает rules автоматически при старте.
# Для прямой проверки rules:
firebase deploy --only firestore:rules --project demo-launcher
```

---

## 4. Smoke test — Setup flow на эмуляторе

```bash
# Запустить эмулятор (через skill android-emulator или AVD):
emulator -avd pixel_5_api_34 &

# Установить app:
./gradlew :app:installDebug

# Запустить:
adb shell am start -n com.launcher/.MainActivity
```

**Ручная проверка**:
1. App открывается, видим экран приветствия.
2. Тапаем «Войти через Google» (F-4 flow). Выбираем test Google account.
3. Видим экран «Придумайте пароль для восстановления» с полем passphrase.
4. **Над клавиатурой должна появиться плашка** «Suggest strong password» от Google Password Manager (или Bitwarden, если установлен). Если её нет — см. troubleshooting ниже.
5. Тапаем на плашку → Google Password Manager генерирует passphrase, сохраняет в своём хранилище, подставляет в поле (звёздочки, не plaintext).
6. Тапаем «Сохранить и продолжить».
7. **Проверка облачной части**: открыть Firestore Emulator UI (`http://127.0.0.1:4000/firestore`) → найти `users/{your-test-uid}/recovery-key/` → должен быть blob с полями `schemaVersion=1`, `algorithm="argon2id-xchacha20poly1305-v1"`, `wrappedRootKey` (Base64), `kdfSalt`, `nonce`, `kdfParams`, `createdAt`.

---

## 5. Smoke test — Recovery flow на эмуляторе

```bash
# Симуляция «новый телефон»: очистить data app'а (НЕ удалять, чтобы Keystore wiped):
adb shell pm clear com.launcher

# Запустить заново:
adb shell am start -n com.launcher/.MainActivity
```

**Ручная проверка**:
1. App снова показывает приветствие.
2. Тапаем «Войти через Google» → тот же Google account.
3. **Должен появиться** экран «Восстановление: введите пароль» с полем passphrase.
4. **Над клавиатурой должна появиться плашка** «Fill from saved passwords» / «Из сохранённых паролей» — Google Password Manager подставляет ранее сохранённый passphrase.
5. Тапаем «Восстановить».
6. App снова работает, config (если был push'нут) виден.

**Negative test — неправильный passphrase**:
1. Очистить data ещё раз.
2. Sign-In, recovery screen, ввести **неправильный** passphrase.
3. Expected: ошибка «Неверный пароль, попробуйте ещё раз» (не crash, не «vault damaged»).
4. 3 раза подряд неправильно → должна появиться опция «Настроить как новое устройство» (per FR-027 + acceptance scenario 2.4).

---

## 6. Smoke test — Multi-identity isolation на эмуляторе

1. Sign-In под Google account #1, setup passphrase #1. Push small config.
2. Sign-Out.
3. Sign-In под Google account #2 (другой тестовый account). **Ожидание**: новый setup flow (новый passphrase), а не recovery prompt.
4. Sign-Out.
5. Sign-In снова под account #1. **Ожидание**: app сразу работает, **без** recovery prompt (SC-005), config #1 виден.

---

## 7. Troubleshooting

**«Suggest strong password» chip не появляется**:
- Проверить API level эмулятора: должен быть API 28+ (Android 9+). На API 26-27 chip может не показаться, но autofill всё равно работает на чтение.
- В настройках эмулятора: Settings → Passwords & accounts → Autofill service → должен быть «Google».
- В Compose UI: убедиться, что используется `ContentType.NewPassword` (или Android View interop с `setAutofillHints(View.AUTOFILL_HINT_NEW_PASSWORD)`).

**Firestore Emulator не видит записи**:
- Проверить `firebase.json` имеет `"firestore": { "rules": "firestore.rules" }`.
- App должен ходить на `10.0.2.2:8080` (host machine localhost из эмулятора), не на real Firestore. Проверить, что `FirestoreRecoveryKeyVault` использует `firestore.useEmulator("10.0.2.2", 8080)` в debug builds.

**Argon2id тесты падают по таймауту**:
- Уменьшить params в test fixtures: `memoryKiB=8192` (8 MiB), `iterations=1` — только для tests.

---

## 8. Cannot-test-locally gaps (per spec.md Local Test Path)

- Реальная Argon2id производительность на Samsung A / Redmi Note (`TODO(physical-device)`).
- Real Firestore (не Emulator) security rules + network latency (`TODO(physical-device)` перед production-release).
- Реальный Google Password Manager Autofill UX (Emulator может вести себя иначе) (`TODO(physical-device)`).

---

## Краткое резюме (для не-разработчика)

**Чтобы проверить F-5 на своей машине**:

1. **Поставить JDK 21 и Android SDK** (если ещё нет).
2. **Собрать**: `./gradlew :core:keys:assemble` и `./gradlew :app:assembleDebug`.
3. **Unit-тесты** — гоняются за 30 секунд на JVM, без эмулятора: `./gradlew :core:keys:jvmTest`.
4. **Integration-тесты** — нужен `firebase emulators:start --only firestore,auth` в отдельном терминале.
5. **Smoke на реальном UI**: запустить эмулятор `pixel_5_api_34`, поставить app, пройти Sign-In + setup passphrase, посмотреть в Firestore Emulator UI что зашифрованный blob там лежит.
6. **Recovery smoke**: `adb shell pm clear com.launcher` (как «новый телефон»), снова Sign-In, ввести passphrase — config восстановился.
7. **Изоляция UID**: два разных Google аккаунта на одном телефоне — каждый со своим passphrase, не пересекаются.

Что **нельзя проверить локально** — реальный Samsung / Huawei, реальный Google Password Manager UX (эмулятор иногда отличается), реальные сетевые задержки production Firestore. Эти проверки помечены `TODO(physical-device)` и делаются перед production-релизом.
