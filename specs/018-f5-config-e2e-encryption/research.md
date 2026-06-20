# Research — F-5: Root Key Hierarchy + ConfigDocument Encryption + Recovery

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-06-19

Phase 0 — разрешение всех «NEEDS CLARIFICATION» из Technical Context. Каждая запись отвечает на конкретный вопрос реализации и фиксирует выбранный путь.

---

## R-1: Параметры Argon2id для passphrase KDF на Android

**Вопрос**: какие interactive-параметры Argon2id оптимальны для mid-tier Android устройства (Samsung A-series, Redmi Note), чтобы попасть в SC-002 «derivation < 500 мс» и при этом сохранить security?

**Решение**:
- `memory = 64 MiB`
- `iterations = 3`
- `parallelism = 1`
- `saltLength = 16` bytes (random per setup)
- `outputLength = 32` bytes (для 256-bit AEAD key)

**Обоснование**:
- OWASP Argon2 Cheat Sheet (2024): рекомендует minimum `m=46MiB, t=1, p=1` для serverless / mobile. Мы поднимаем `m=64MiB` (запас прочности) и `t=3` (компенсация низкого `p` на mobile — однопоточный JNI вызов libsodium).
- libsodium-kmp ionspin поддерживает constants `crypto_pwhash_OPSLIMIT_INTERACTIVE` (≈ 2-3) и `crypto_pwhash_MEMLIMIT_INTERACTIVE` (≈ 64 MiB) — наши параметры соответствуют этому профилю.
- Замеры F-CRYPTO (spec 016 quickstart) на `pixel_5_api_34`: 64MiB / 3 iter — около 350-400 ms. Запас под SC-002 (500ms cap).
- На low-end (Redmi Note 11, Samsung A13) ожидаем 800-1200 ms — accepted UX tradeoff на редкое событие (setup или recovery, не каждый push).

**Action**: записать как константы в `Argon2idPassphraseKdf.kt`. Хранить значения **внутри** `RecoveryVaultBlob.kdfParams` — клиент с future-устройства использует те, что записаны при setup, не свои дефолты (forward-compat).

---

## R-2: Wrap pattern для root key в Android Keystore

**Вопрос**: как именно root key (random 256-bit) хранится в Android Keystore? Mы не можем «положить» raw symmetric key — Keystore хранит только hardware-bound keys.

**Решение**: использовать **F-CRYPTO `SecureKeystore` adapter** as-is. Pattern:
1. При первом setup `SecureKeystore.createOrGet(alias = "rootkey-${uid}")` создаёт hardware-bound AES-GCM wrap key (никогда не покидает TEE).
2. Random 256-bit root key генерируется в RAM через `Random.nextBytes(32)`.
3. Root key wrapped через `SecureKeystore.encrypt(rootKeyMaterial)` → opaque ciphertext.
4. Wrapped ciphertext сохраняется в обычный app storage (DataStore / file) как `wrapped-root-${uid}`.
5. На каждый use: read wrapped → `SecureKeystore.decrypt(...)` → root key в RAM → используется для unwrap DEK → root key обнуляется.

**Обоснование**:
- Решение унаследовано из F-CRYPTO (decision 2026-06-17 в memory `project_f_crypto_decisions`): wrap pattern для Curve25519 в TEE. Тот же подход применим к 256-bit symmetric key.
- Хард-bound key не уходит из TEE → даже root-доступ к device storage не извлекает raw root key (только wrapped form, который без TEE бесполезен).
- Если Android Keystore key invalidated (после OS update / биометрия добавлена) — `SecureKeystore.decrypt` вернёт `KeyInvalidated` ошибку. Восстановление через recovery flow.

**Action**: `RootKeyManagerImpl` использует `SecureKeystore` напрямую через DI; никакой собственной Keystore-обвязки в F-5.

---

## R-3: Firestore Security Rules для `users/{uid}/recovery-key`

**Вопрос**: какое именно правило закрывает recovery vault от всех, кроме owner'а?

**Решение**:

```javascript
// firebase/firestore.rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // F-5: Recovery key vault — strict owner-only
    match /users/{uid}/recovery-key/{docId} {
      allow read: if request.auth != null && request.auth.uid == uid;
      allow write: if request.auth != null && request.auth.uid == uid
                   && request.resource.data.schemaVersion is int
                   && request.resource.data.schemaVersion >= 1
                   && request.resource.data.algorithm is string
                   && request.resource.data.wrappedRootKey is bytes
                   && request.resource.data.wrappedRootKey.size() <= 1024
                   && request.resource.data.kdfSalt is bytes
                   && request.resource.data.kdfSalt.size() == 16
                   && request.resource.data.nonce is bytes;
      allow delete: if request.auth != null && request.auth.uid == uid;
    }

    // F-5: Encrypted config (territory spec 008 — здесь только rule для consistency)
    match /users/{uid}/config/{docId} {
      allow read, write, delete: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

**Обоснование**:
- Read/Write/Delete только при `request.auth.uid == uid` (FR-009).
- Шейп blob'а валидируется на write — защищает от случайной записи мусора со стороны клиента и от accidental schema-version 0.
- `wrappedRootKey.size() <= 1024` bytes — sanity check (root key 32 bytes + AEAD overhead < 100 bytes; запас на metadata).
- `kdfSalt.size() == 16` ровно — соответствует R-1.
- Третьи стороны (Google инженеры с infrastructure access, утечка project credentials) могут видеть **запись существует** для UID, но не могут расшифровать без passphrase. Это accepted threat model (decision 2026-06-19 «server тупой»).

**Action**: добавить блок в `firebase/firestore.rules`. Деплой через `firebase deploy --only firestore:rules` (есть в quickstart).

---

## R-4: Android Autofill hints — Google Password Manager «Suggest strong password»

**Вопрос**: как именно UI-поле активирует «Suggest strong password» chip над клавиатурой? На каких устройствах работает out-of-the-box?

**Решение**:
- Setup screen: `OutlinedTextField` (Compose Material 3) с modifier `.semantics { contentType = ContentType.NewPassword }` (Compose 1.7+ API) или legacy `BasicTextField` с `KeyboardOptions(autoCorrect = false)` + Android View interop с `setAutofillHints(View.AUTOFILL_HINT_NEW_PASSWORD)`.
- Recovery screen: тот же подход с `ContentType.Password` / `View.AUTOFILL_HINT_PASSWORD`.
- Дополнительно: `KeyboardType.Password` и `visualTransformation = PasswordVisualTransformation()` — passphrase не показывается plaintext (FR-013a).

**Обоснование**:
- Android Autofill Framework доступен с API 26 (Android 8.0). До API 26 — autofill ignored, recovery работает manually.
- Google Password Manager (default Autofill provider) поддерживает «Suggest strong password» chip с Android 9+ (API 28). На API 26-27 — fallback к ручному вводу, но Autofill всё ещё работает на чтение сохранённых паролей.
- На устройствах с Samsung Pass / Bitwarden / 1Password — те же `autofillHints` понимаются всеми этими провайдерами (стандарт Android Autofill API).
- Огр.: Compose до 1.7 не имел нативной поддержки contentType — fallback через Android View interop. С Compose 1.7 (BOM 2025.x) — нативно. Проект использует Compose BOM из существующих spec'ов, проверить версию на tasks-этапе.

**Action**: использовать `ContentType.NewPassword` / `ContentType.Password` в Compose. Fallback через `androidx.compose.ui.viewinterop.AndroidView` если BOM < 2025.

---

## R-5: KMP module structure — `expect/actual` vs interface-only

**Вопрос**: как организовать platform-specific код в `core/keys/`? Использовать `expect/actual` декларации или держать всё в common через interface + DI?

**Решение**: **interface-only через port + DI**. Никаких `expect/actual`.

**Обоснование**:
- Решение унаследовано из F-CRYPTO (decision 2026-06-17): F-CRYPTO `SecureKeystore` — interface в `commonMain`, Android impl через `AndroidSecureKeystore` в `androidMain`. F-5 наследует pattern.
- `expect/actual` имеет цену: компилятор требует `actual` для каждой target платформы. Если iOS targets declared но inactive — `expect` всё равно требует stub `actual` в `iosMain`. Это лишний boilerplate.
- Interface + DI: `RootKeyManagerImpl` принимает `SecureKeystore` через конструктор, тестируется с `FakeSecureKeystore` без касания platform-кода.
- iOS-readiness preserved: когда iOS targets activate (Phase 5 L-x), нужен только `IosSecureKeystore` adapter — domain не меняется.

**Action**: все API в `commonMain`. `androidMain` содержит ТОЛЬКО storage helpers, если KMP common DataStore недостаточен. App-слой owns DI wiring.

---

## R-6: ClipboardManager auto-clear behavior на Android 13+ (FR-013a)

**Вопрос**: как именно работает system auto-clear буфера обмена на Android 13+? Нужна ли наша обвязка?

**Решение**:
- Android 13+ (API 33+): system автоматически очищает clipboard через **60 секунд** после копирования **если** `ClipData` помечен sensitive через `ClipDescription` extras: `ClipDescription.EXTRA_IS_SENSITIVE = true` (или legacy `"android.content.extra.IS_SENSITIVE"`).
- Без флага — clipboard живёт до следующего копирования или reboot.
- Pre-Android 13 — нет system auto-clear. Наш код **должен** вызывать `ClipboardManager.clearPrimaryClip()` (API 28+) при navigation away from setup screen или через `Handler.postDelayed(60_000)` cleanup.

**Реализация**:
```kotlin
fun copyPassphraseToClipboard(passphrase: CharArray, context: Context) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText("passphrase", String(passphrase))
    val extras = PersistableBundle().apply {
        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) // Android 13+
    }
    clip.description.extras = extras
    cm.setPrimaryClip(clip)
    // Best-effort manual clear через 60s для pre-13
    scheduleClipboardClear(cm, content = String(passphrase), delayMs = 60_000)
}
```

**Обоснование**:
- Reference: [developer.android.com/about/versions/13/features/sensitive-clipboard](https://developer.android.com/about/versions/13/features/sensitive-clipboard).
- Pre-13 manual schedule — best-effort. Если user закрыл app — наш scheduled clear не выполнится. Это accepted (FR-013a называет это «short-lived storage, accepted tradeoff»).
- Passphrase передаётся как `CharArray` чтобы Kotlin string interning не оставил копию в string pool. После copy — zeroize `CharArray` (`for (i in indices) passphrase[i] = ' '`).

**Action**: helper в `RecoveryPassphraseSetupScreen.kt` или `ClipboardHelper.kt` в `data/recovery/`.

---

## R-7: Identity isolation через Keystore alias namespacing (FR-031)

**Вопрос**: как обеспечить, что ключи UID1 не пересекаются с ключами UID2 на одном устройстве, без явного «container» abstraction?

**Решение**: **alias namespacing** с UID в составе.
- Root key wrap: alias = `"rootkey-${authIdentity.stableId}"`
- KeyRegistry storage prefix: `"dek-${authIdentity.stableId}-${dekName}"`
- Wrapped root key file: `wrapped-root-${authIdentity.stableId}` в DataStore / file

**Свойства**:
- При sign-in под UID2 → новый alias `rootkey-${uid2}` → новая запись в Keystore, **отдельная** от `rootkey-${uid1}`.
- Sign-out не wipe'ит ничего (alias остаётся в Keystore).
- Re-sign-in под UID1 → `SecureKeystore.exists("rootkey-${uid1}")` → true → re-use → recovery flow НЕ запускается (SC-005).
- Sign-in под UID2 первый раз → `SecureKeystore.exists("rootkey-${uid2}")` → false → попытка recovery через Firestore → если recovery doc для UID2 нет → генерируется новый root key.

**Обоснование**:
- Android Keystore aliases — flat namespace per package. UID prefix даёт логическую партиционность без extra layers.
- `AuthIdentity.stableId` — стабильный Google UID (F-4 spec 017 уже валидирует).
- Если UID содержит нелегальные символы для Keystore alias (Keystore принимает most ASCII) — на tasks-этапе добавить SHA-256 hash UID'а в alias.

**Action**: `RootKeyManagerImpl.getOrCreate(identity)` строит alias через helper `keystoreAliasFor(identity)`. Тест `MultiIdentityIsolationTest` проверяет SC-005, SC-006.

---

## Сводная таблица решений

| ID  | Тема                            | Решение                                                                                |
|-----|---------------------------------|----------------------------------------------------------------------------------------|
| R-1 | Argon2id params                 | `m=64MiB, t=3, p=1, saltLen=16, outLen=32`; параметры записываются в blob              |
| R-2 | Root key wrap pattern           | F-CRYPTO `SecureKeystore.encrypt(rootKey)` → wrapped в DataStore                       |
| R-3 | Firestore Security Rules        | strict `request.auth.uid == uid` + shape validation                                    |
| R-4 | Autofill hints                  | Compose `ContentType.NewPassword/Password` или Android View interop fallback           |
| R-5 | KMP module pattern              | interface-only + DI; никаких `expect/actual`                                           |
| R-6 | Clipboard auto-clear            | `EXTRA_IS_SENSITIVE` + manual schedule fallback для pre-13                             |
| R-7 | Identity isolation              | alias prefix `rootkey-${uid}` + `dek-${uid}-${dekName}`                                |

Все «NEEDS CLARIFICATION» из Technical Context разрешены. План готов к Phase 1.

---

## Краткое резюме (для не-разработчика)

**Что решено в этом файле** (7 технических решений R-1..R-7):

1. **Параметры пароля** — Argon2id с 64 МБ памяти, 3 итерации. На обычном телефоне ≈400 мс, нагрузка только при setup'е или recovery (один-два раза за жизнь устройства), не на каждое сохранение.
2. **Как ключ хранится** — переиспользуем уже готовый `SecureKeystore` из F-CRYPTO; не пишем собственную обвязку Android Keystore.
3. **Что разрешено серверу Firestore** — только владелец UID может читать/писать свой recovery-blob; написаны правила, которые не пропустят даже наш собственный код, если он попробует читать чужое.
4. **Как работает «Suggest strong password»** — стандартный Android Autofill API; Google Password Manager сам появится с подсказкой на API 28+, на API 26-27 — manual fallback.
5. **Архитектура KMP-модуля** — interfaces в commonMain, никаких `expect/actual` (то же решение, что в F-CRYPTO).
6. **Очистка буфера обмена** — Android 13+ сам очистит через 60 секунд, на старых версиях наш код запускает таймер.
7. **Изоляция UID'ов** — у каждого Google-аккаунта свой namespace в Keystore (`rootkey-${uid}`); UID1 и UID2 на одном телефоне не пересекаются, sign-out / sign-in не теряют ключи.
