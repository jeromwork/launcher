# Spec 011 — Manual smoke procedure (T101 / T102)

**Goal**: убедиться, что криптофундамент работает на реальных Android-устройствах. Это gate перед merge'ом спека 011 в `main`.

**Время**: ~30 минут (включая build + install).

**Что нужно**:
- 2 реальных Android-устройства (любых, можно один телефон + один планшет; minSdk 30 = Android 11+).
- USB-кабель + `adb` access к обоим (`adb devices` показывает оба).
- Cloud Firestore + Storage Emulator (или Firebase test project; для self-roundtrip достаточно local-only).

---

## Phase A — self-roundtrip (1 device, тестирует адаптеры без сети)

Цель: подтвердить, что libsodium + Android Keystore + envelope работают на реальном железе. Не требует сети.

### A.1 Robolectric self-roundtrip (уже зелёный)

Phase 3 LibsodiumAdaptersTest (`core/src/androidUnitTest/.../LibsodiumAdaptersTest.kt`) гоняется на host JVM через Robolectric + pure JNA + libsodium. 14/14 cases passed, включая:

- `aead_encrypt_decrypt_roundtrip` — full plaintext → ciphertext → plaintext
- `asymm_sealCEK_unsealCEK_roundtrip` — X25519 + crypto_box_seal
- `sign_verify_roundtrip` — Ed25519
- `hash_*` — BLAKE2b-256

Это **уже подтверждает** правильность libsodium-биндингов на JVM-уровне. ABI splits (Phase 0 T003) гарантируют, что `.so` для armeabi-v7a + arm64-v8a + x86 + x86_64 упакованы в APK. Реальное устройство просто запустит ту же библиотеку.

### A.2 Real-device self-roundtrip (T100 — deferred)

Полная debug Activity со «Encrypt 16 random bytes (self)» / «Decrypt» buttons — отложена до Phase B. Причина: `:app` module не имеет direct classpath access к `LibsodiumAeadCipher` (Koin DI требует, чтобы adapters регистрировались через module bindings — это часть Phase B refactor вместе с T061 PairingCoordinator extension).

Schema-level smoke возможна сейчас через:
```powershell
./gradlew :core:testMockBackendDebugUnitTest --tests "com.launcher.adapters.crypto.LibsodiumAdaptersTest"
```
Все 14 кейсов используют тот же libsodium native, что и release APK.

Build APK для real-device verification (без UI smoke screen) — для проверки, что APK устанавливается, ABI splits работают, libsodium .so не падает на startup:
```powershell
./gradlew :app:installMockBackendDebug
adb logcat | findstr "lazysodium libsodium"
```
Открыть приложение, сделать любое существующее действие, убедиться, что crash логов нет.

---

## Phase B — cross-device pairing + live envelope (Phase 8 + T061 integration)

⚠️ **Phase B требует завершения T061** — PairingCoordinator extension с keygen + publishOwn после `consent.allow`. Это не часть Phase 8 schema; T061 ставится непосредственно перед этим шагом, см. tasks.md.

После T061 готов:

1. **Build + install на оба устройства**:
   ```powershell
   ./gradlew :app:installRealBackendDebug
   adb -s <device-A-serial> install -r app/build/outputs/apk/realBackend/debug/...
   adb -s <device-B-serial> install -r app/build/outputs/apk/realBackend/debug/...
   ```

2. **Pairing через QR** (существующий flow спека 007):
   - Device A — admin: `PairingActivity` → «Сгенерировать QR».
   - Device B — managed: открыть камеру → сканить QR → consent → подтвердить.
   - После `consent.allow` оба устройства должны opublishOwn'ить свои Pub'ы в `/links/{linkId}/devices/`.

3. **Verify Firestore documents** (через Firebase Console или `firebase emulator UI`):
   ```
   /links/{linkId}/devices/{deviceId-A}  → admin's X25519 + Ed25519 Pub + signature
   /links/{linkId}/devices/{deviceId-B}  → managed's X25519 + Ed25519 Pub + signature
   ```
   Обе записи должны иметь `signature` поле (64 bytes base64), `signedTimestamp` свежий.

4. **Cross-device encrypt** (admin → managed):
   - На device A открыть smoke screen.
   - Расширить smoke screen для cross-device: `PairRecipientResolver.resolveRecipients(linkId)` → seal CEK на peer's Pub → upload envelope.
   - Записать `uuid`.

5. **Cross-device decrypt** (managed):
   - На device B открыть smoke screen.
   - Ввести `uuid` в TextField.
   - Тап «Decrypt by uuid» — должно: download envelope из Storage, unseal CEK ownPair-ом, decrypt.
   - Hex должен совпасть с тем, что устройство A зашифровало.

6. **Acceptance**:
   - hex match → Phase B green.
   - End-to-end < 60 секунд на pair → encrypt → upload → download → decrypt.

---

## Phase C — revoke cleanup verification

1. На device B (managed) revoke link через Settings.
2. **Verify**:
   - `/links/{linkId}` document deleted (Firebase Console).
   - `/links/{linkId}/devices/`, `/links/{linkId}/deviceOwnership/` empty.
   - `/links/{linkId}/private-media/*` (Storage) — empty.
   - Local SQLite `blob_reference_ledger` для этого linkId — empty.

---

## Что записать в результат

После прохождения создать commit с:
- `specs/011-contacts-and-e2e-encrypted-media/smoke/run-<YYYY-MM-DD>.md` — date, обе device model+OS version, Phase A/B/C pass/fail, любые наблюдения.
- В `tasks.md` отметить T102 как `[x] done`.

---

## Troubleshooting

| Симптом | Причина | Fix |
|---|---|---|
| `NoClassDefFoundError: com/sun/jna/Structure` на устройстве | JNA aar не упакован | Verify `core/build.gradle.kts` — `androidMainImplementation(variantOf(libs.jna) { artifactType("aar") })` присутствует |
| Encrypt → silently fails | libsodium .so не загрузилась под текущей ABI | Verify `adb shell pm dump com.launcher.app | grep abiList` — устройственный ABI в списке упакованных |
| Pairing OK, publishOwn fails с PERMISSION_DENIED | Firestore rules не задеплоены / `freshSignedTimestamp` не проходит | Проверить deploy `firestore.rules` (Phase 4 T062), сравнить `signedTimestamp` с server time |
| Decrypt на peer → MacFailed | Wrong Pub key (impersonation / sync issue) | Verify peer's Pub в Firestore совпадает с тем, что использовался для seal |
