# Manual Setup: Sandbox Firebase project + Android instrumented E2E

**Created**: 2026-06-20
**Owner**: project maintainer
**Time estimate**: 30-45 minutes (Шаги 1-4) + ~30 minutes когда я добавлю Шаги 5-7 кодом
**Status**: pending owner action на Шаги 1-4

---

## Зачем нужен отдельный sandbox project

Production / dev project (`launcher-old-dev`) сейчас содержит реальные test
данные от Spec 007 / 017 pairing'а и Sign-In smoke runs. Использовать его
для **mutating** instrumented tests (которые seedят/удаляют документы)
небезопасно — могут перезаписать существующий dev state.

**Sandbox** = отдельный Firebase project (`launcher-sandbox-{your-id}`) с
такими же config'ами, который instrumented tests могут безопасно
ломать / сбрасывать. Альтернатива — Firebase Emulator в local-only mode,
но он не покрывает real device test surface (TEE-backed Keystore,
GMS-based FCM, OEM специфика).

**Где это поможет**:
- F-5b SC-001 acceptance: «admin push config → grep по `Bobby Tables
  555-1234` в Firestore возвращает 0» — нельзя проверить unit-test'ом
  one-hop, нужен real Firestore write.
- F-5b multi-device demo: phone seal'ит config → tablet pulls → opens →
  byte-equal. Нужно 2 устройства (или 2 эмулятора) с разными DeviceId'ами.
- Recovery E2E: setup на phone-A → wipe Keystore → recovery на phone-B
  через passphrase → byte-equal config.
- OEM matrix: Xiaomi MIUI, Samsung One UI Keystore quirks — нужны real
  устройства.

---

## Шаги 1-4 — настраивает owner вручную

### Шаг 1 — создать Firebase sandbox project

1. Зайти на https://console.firebase.google.com.
2. **Add project**:
   - Name: `launcher-sandbox-jeromwork` (или ваше — главное чтобы НЕ конфликтовало с `launcher-old-dev` и НЕ упоминало production).
   - Disable Google Analytics (для sandbox не нужен).
3. **Project Settings → General → Your apps → Add Android app**:
   - Package name: `com.launcher.app` (точно тот, что в `app/build.gradle.kts`).
   - App nickname: `launcher-sandbox-debug`.
   - SHA-1 fingerprint: ваш debug-keystore SHA-1.
     ```powershell
     keytool -list -v -keystore $env:USERPROFILE\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
     Скопировать значение `SHA1:` → вставить в Firebase.
4. **Download** `google-services.json` → **НЕ** клади в `app/google-services.json`
   (там лежит dev project). Положи в `app/src/realBackendSandbox/google-services.json`
   (новая sandbox build variant — я её создам в Шагах 5-7).

   На время Шагов 1-4 можно сохранить файл вне репо, я его перенесу
   в нужное место в Шаге 5.

### Шаг 2 — включить Firestore в sandbox project

1. Firebase Console → **Build → Firestore Database → Create database**.
2. Start in **test mode** (для sandbox OK; rules перезаписываются deploy'ем).
3. Region: ближайший к вам (для local emulator не важно).

### Шаг 3 — включить Google Sign-In provider

1. Firebase Console → **Build → Authentication → Get started**.
2. **Sign-in method → Google → Enable**.
3. **NOT** включать Anonymous (мы её удалили в архитектуре).
4. Скачать обновленный `google-services.json` (после Google provider enable
   в нём появляется `oauth_client` блок). Заменить файл из Шага 1.4.

### Шаг 4 — добавить sandbox project как Firebase CLI alias

```powershell
cd C:\work\launcher
firebase use --add
# В диалоге выберите ваш sandbox project (launcher-sandbox-jeromwork)
# Alias: sandbox
```

Проверить:
```powershell
firebase use
# должно показать оба alias'а: default (launcher-old-dev) и sandbox
```

Переключиться:
```powershell
firebase use sandbox
firebase deploy --only firestore:rules   # rules деплоятся на sandbox project
firebase use default                       # вернуться на dev
```

---

## Шаги 5-7 — делаю я кодом после ваших Шагов 1-4

### Шаг 5 — Firebase emulator wiring в LauncherApplication (debug-only)

В `LauncherApplication.onCreate()` добавлю condition:
```kotlin
if (BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATOR) {
    FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
    FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
}
```
- `10.0.2.2` — host loopback от Android emulator.
- На реальном устройстве нужно использовать IP вашего компа в локальной
  сети (или USB tethering); этот вариант приложение не упрощает (real device
  тестируется против sandbox-Firebase, не local emulator).
- BuildConfig flag `USE_FIREBASE_EMULATOR` добавлю в `app/build.gradle.kts`,
  включать через `-PuseFirebaseEmulator=true` Gradle property.

### Шаг 6 — sandbox build variant + флавор

Создам gradle flavor `realBackendSandbox` рядом с `realBackend` / `mockBackend`:
- `app/src/realBackendSandbox/google-services.json` — ваш sandbox config (из Шага 1.4).
- Same DI bindings, same code path — отличается только Firebase config.

Запускать:
```powershell
.\gradlew :app:assembleRealBackendSandboxDebug
adb install -r app\build\outputs\apk\realBackendSandbox\debug\app-realBackendSandbox-debug.apk
```

### Шаг 7 — Android instrumented E2E tests

Создам `app/src/androidTest/java/com/launcher/app/data/envelope/CloudConfigEncryptionE2ETest.kt`:
1. **SC-001 opacity test**:
   - Sign-in test admin (Firebase Auth emulator).
   - `EnvelopeBootstrap.bootstrap()` → публикует pub key.
   - `ConfigSaver.saveOwn("default", '{"contact":"Bobby Tables 555-1234"}'.toByteArray())`.
   - WorkManager flush queue.
   - **Прямое чтение Firestore document** через Admin SDK.
   - **Assert**: raw document bytes не содержат substring "Bobby Tables 555-1234".

2. **Roundtrip test**:
   - Save → pull through `ConfigSaver.loadOwn("default")` → byte-equal.

3. **Multi-device test** (если есть 2 эмулятора):
   - Phone-A bootstrap + saveOwn → phone-B (с тем же UID) bootstrap → loadOwn → byte-equal.

4. **Cross-user delegation test**:
   - Owner-uid + helper-uid setup + grant в Firestore.
   - Helper saveForOther(ownerUid, "shared-config", payload).
   - Owner loadOwn("shared-config") → byte-equal.

Запускать:
```powershell
firebase emulators:start --only firestore,auth                   # terminal 1
.\gradlew :app:connectedRealBackendSandboxDebugAndroidTest      # terminal 2
```

---

## Нужно ли проверять на эмуляторе vs реальном телефоне?

### TL;DR

**И то, и другое** — но в разном объёме и с разными целями.

| Что проверяем | Эмулятор | Real device | Когда нужен real |
|---|---|---|---|
| Envelope crypto roundtrip | ✅ enough | optional | — |
| SC-001 opacity grep | ✅ enough | optional | — |
| Multi-device + cross-user delegation | ✅ 2 эмулятора | optional | — |
| Recovery flow setup + восстановление | ✅ enough | recommended | edge case: Xiaomi MIUI |
| Android Keystore TEE behavior | ⚠️ partial (software TEE) | ✅ нужно | StrongBox attestation, hardware-backed keys |
| FCM real network behavior | ⚠️ wifi-only sim | ✅ нужно | F-5c integration when ready |
| OEM-specific Keystore quirks | ❌ нет | ✅ нужно | Xiaomi (Optimize MIUI cleanup), Samsung Knox, Huawei EMUI |
| Process death recovery (App killed mid-WorkManager) | ⚠️ можно симулировать | ✅ realistic | confidence boost |
| Doze + background WorkManager в реальных условиях | ❌ нет | ✅ нужно | retry policy validation |

### Минимум для merge F-5b:

1. **Эмулятор** API 34/35 — все 4 E2E tests из Шага 7 → должны pass.
2. **Один real device** (Xiaomi 11T у вас уже использовался в Phase 7) — тот же набор tests + recovery flow + check, что app survives Keystore wipe (`Optimize MIUI` button or factory reset).

### Что в backlog после merge:

- Полная OEM matrix (Samsung One UI, Huawei EMUI, OnePlus, Pixel — 4-6 устройств) — отдельная спайка для production hardening.
- StrongBox attestation verification — отдельная спайка для billing gate.
- Doze + battery saver behavior — отдельная спайка для F-5c integration.

---

## Чеклист «когда готовы Шаги 5-7»

Когда выполните Шаги 1-4, скажите мне:
- ✅ Sandbox project создан, имя: `launcher-sandbox-jeromwork` (или ваше)
- ✅ `google-services.json` для sandbox скачан (мне передать через локальный
  файл, не chat — see memory `feedback_secret_handling`)
- ✅ Firestore включён в sandbox
- ✅ Google Sign-In провайдер включён в sandbox
- ✅ Firebase CLI alias `sandbox` добавлен

Тогда я делаю Шаги 5-7 одним атомарным коммитом + push.

---

## Cross-references

- [manual-setup-dev.md](manual-setup-dev.md) §3 — F-5b rules tests на local emulator (уже работают).
- [project-backlog.md](project-backlog.md) — backlog для OEM matrix + StrongBox + Doze.
- Spec 018 [data-model.md §0](../../specs/018-f5-config-e2e-encryption/data-model.md) — F-5b architecture summary.
- Spec 018 [contracts/envelope-v1.md](../../specs/018-f5-config-e2e-encryption/contracts/envelope-v1.md) — wire format.
