# Manual setup — production release

Накопительный runbook **ручных действий** для выпуска приложения в Google Play
Store. Парный к [`manual-setup-dev.md`](manual-setup-dev.md), но для production
project'а + дополнительные шаги.

**Аудитория**: release engineer (= owner проекта в данный момент). Делается
**один раз перед запуском** + дополняется перед каждым major release когда
появляются новые cloud features.

**Когда читать**:
- Перед первой публикацией в Play Store — пройти все секции с самого начала.
- Перед каждым major release — проверить чек-лист в конце.
- При смене owner'а проекта — передать вместе с этим файлом.

> ⚠️ **Этот файл — НЕ secret**. Реальные ключи, пароли, токены **никогда** не
> попадают сюда — только инструкции «найди их там-то». Файл коммитится в репо.

---

## 0. Защита владения проектом (читать первым!)

Прежде чем создавать production Firebase project, защити аккаунт через
который будешь им владеть. Если этот аккаунт скомпрометируют — атакующий
получит **полный контроль** над проектом в Play Store и Firebase:
сможет удалить приложение, поменять описание, экспортировать user data,
забанить тебя из твоего же проекта.

### 0.1. Три слоя владения (понять что чем защищать)

| Слой | Что | Защита |
|------|-----|--------|
| **1. Google аккаунт** (Play Console + Firebase) | Главное. Без него атакующий ничего не может. | 2FA + hardware key + Advanced Protection. |
| **2. Upload keystore** (`.jks` для подписи APK) | Технический ключ загрузки. Без аккаунта бесполезен. | 1Password / Bitwarden + зашифрованный backup. |
| **3. Release keystore** | Финальная подпись APK для юзеров. | **Включить Play App Signing** → ключ держит Google в HSM, не у тебя. |

### 0.2. Google аккаунт — Advanced Protection Program

**Делается ОДИН РАЗ перед первой публикацией в Play Store.**

1. **Купить 2-3 hardware security key** (одна модель, чтобы все работали одинаково):
   - **YubiKey 5C NFC** (~$55 каждый, https://www.yubico.com).
   - **Google Titan Security Key** (~$30, https://store.google.com/product/titan_security_key).
   - **Feitian ePass FIDO2** (~$25, AliExpress).

   Минимум — **2 ключа** (primary + backup). Рекомендую **3** (primary +
   2 backup в разных местах).

2. **Зарегистрировать каждый ключ на Google аккаунт**:
   - https://myaccount.google.com/signinoptions/two-step-verification
   - Включить 2FA → добавить Security Key → воткнуть USB → следовать инструкции.
   - Повторить для каждого ключа.

3. **Включить Google Advanced Protection Program**:
   - https://landing.google.com/advancedprotection/
   - **Enroll** → требует чтобы 2 ключа были уже зарегистрированы.
   - После включения:
     - SMS / Google Authenticator больше не работают как 2FA — **только hardware key**.
     - Phishing-сайты не смогут украсть твой аккаунт (key проверяет домен).
     - Третьи-сторонние приложения ограничены.
     - Account recovery усилен.

4. **Backup-коды**:
   - https://myaccount.google.com/signinoptions/backupcodes
   - **Generate new codes** → распечатать или сохранить в зашифрованном виде на флешке.
   - Это fail-safe: если потеряешь все hardware keys, backup-коды дадут разовый доступ.

5. **Физическое размещение ключей**:
   - **Primary** — на ключнице, всегда с тобой.
   - **Backup #1** — дома в сейфе / надёжном месте.
   - **Backup #2** — у доверенного родственника / в банковской ячейке.

**Стоимость setup**: ~$75-150 разово, защита на 5+ лет.

### 0.3. Чек-лист защиты аккаунта (пройти перед production)

- [ ] Куплено минимум 2 hardware security keys.
- [ ] Оба ключа зарегистрированы на Google аккаунте.
- [ ] Google Advanced Protection Program включена.
- [ ] Backup-коды сгенерированы и сохранены оффлайн (флешка / печать).
- [ ] Primary ключ всегда с собой; backup в надёжном физическом месте.

---

## 1. Production Firebase project

**Отдельный** project от dev! Никогда не публиковать APK который указывает
на dev Firebase project.

### 1.1. Создать prod project

1. https://console.firebase.google.com → **Add project**.
2. Name: `launcher-prod` (или fence-приличное название).
3. **Enable Google Analytics** — Yes (для production нужно для Vitals).
4. Создать.

### 1.2. Подключить Android app

1. Project Settings → **Your apps** → **Add app** → Android.
2. **Package name**: `com.launcher.app` (тот же что и в dev).
3. **App nickname**: `Launcher Production`.
4. **SHA-1 certificate fingerprint**:
   - Из **production keystore** (см. §3), команда:
     ```bash
     keytool -list -v -alias <твой-prod-alias> \
             -keystore <путь-к-prod-keystore.jks> | grep "SHA1\|SHA-256"
     ```
   - Скопировать SHA-1 и SHA-256 → добавить в Firebase Console (обе).
   - **Также добавить SHA-1 из Google Play App Signing** (получишь после §3.3).

5. **Download google-services.json** → положить в `app/google-services.json`
   **только при production сборке**. На dev-машине должен быть dev-вариант.

   Решение хранения: храним production `google-services.json` в **password
   manager** (1Password / Bitwarden) как attachment. CI/CD pipeline вытаскивает
   из secrets перед сборкой.

### 1.3. Enable Google Sign-In provider

Те же шаги что в [`manual-setup-dev.md §2.1-2.2`](manual-setup-dev.md#21-enable-google-provider),
но для prod project'а.

**Дополнительно для production OAuth Consent Screen**:
- **User type**: External.
- **Privacy Policy URL**: обязательно, рабочая ссылка. Без неё Google не
  approve'нет.
- **Terms of Service URL**: обязательно для production.
- **Scopes**: `openid`, `email`, `profile`. **Никаких** дополнительных
  (Detekt rule [`Spec017AuthIsolationTest.T795`](../../core/src/androidUnitTest/kotlin/com/launcher/test/fitness/Spec017AuthIsolationTest.kt)
  не пропустит).
- **Submit for verification** — Google review занимает **1-2 недели**. Делать
  заранее.

### 1.4. Deploy Firestore Rules к production

⚠️ **Перед deploy убедиться что rules не relaxed!** На dev сейчас правила
открыты (`allow read, create: if request.auth != null`) — для prod нужно
вернуть строгие правила с UUIDv4 validation, schemaVersion checks, immutable
guards.

См. оригинальные строгие rules в git history:
```bash
git log --all --pretty=format:"%h %s" -- firestore.rules | head
```

Восстановить строгие rules → задеплоить:
```bash
firebase use launcher-prod
firebase deploy --only firestore:rules
```

**Проверить через Firebase Console** что rules применились:
https://console.firebase.google.com/project/launcher-prod/firestore/rules

---

## 2. Privacy Policy + Data Safety

### 2.1. Privacy Policy

Обязательна для любого приложения с user data. Должна быть **публичная ссылка**.

**Минимум** что описать (для launcher с F-4 Google Sign-In):
- Какие данные собираем: email, displayName, profile photo URL (через Google).
- Третьи стороны: Google (Sign-In), Firebase (auth backend + Firestore).
- Encryption: HTTPS in transit, encrypted at rest.
- Data deletion: процесс (через S-6 spec когда ship'нется).
- Data retention: на сколько храним.
- Контакты для GDPR-запросов: email + срок ответа ≤30 дней.

Опубликовать на product website (например `https://launcher-app.com/privacy`)
**до** публикации в Play Store.

### 2.2. Play Console Data Safety form

В Play Console при первой публикации:

- **Collected**: Email address, Name. Linked to user: Yes. Purpose: app
  functionality (account, sync).
- **Shared with third parties**: Yes, Google (auth), Firebase (backend).
- **Encrypted in transit**: Yes (HTTPS via Firebase SDK).
- **User can request deletion**: Yes (через S-6 — указать «coming Q3 2026»
  если ещё не ship'нулось).

---

## 3. Release keystore + Play App Signing

### 3.1. Создать upload keystore (одноразово)

```bash
keytool -genkey -v -keystore launcher-upload-keystore.jks \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -alias launcher-upload
```

Пароль — длинный, рандомный, в password manager.

**Validity 10000 дней** (~27 лет) — стандарт для upload keystore.

### 3.2. Сохранить upload keystore безопасно

**3 копии в 3 разных местах** (3-2-1 backup rule):

1. **Primary**: 1Password / Bitwarden как attachment + пароль в той же записи.
2. **Backup #1**: зашифрованный gpg на Google Drive:
   ```bash
   gpg -c launcher-upload-keystore.jks   # запросит passphrase
   # → создаст launcher-upload-keystore.jks.gpg, upload его на Drive
   ```
   Passphrase к gpg — в том же 1Password.
3. **Backup #2**: зашифрованная флешка дома (LUKS / BitLocker / FileVault).

**НЕ коммитить в репо!** Файл в `.gitignore`.

### 3.3. Play App Signing (рекомендуется!)

При **первой** загрузке APK в Play Console:

1. Play Console → твоё приложение → **Setup** → **App signing**.
2. **Use Google Play App Signing** → Yes (рекомендуемая опция).
3. Google сгенерирует **release key** в своих HSM — этот ключ ты **никогда не видишь**.
4. Твой upload keystore нужен только для **загрузки** новых билдов.
5. Если потеряешь upload keystore — Google перевыпустит за 2-3 дня через support.
6. Release key не теряется никогда — он у Google.

**После включения**:
- В Play Console → **App signing** → скопировать **App signing key certificate
  SHA-1** → добавить в Firebase Console (см. §1.2). Это SHA-1 которым реально
  подписан APK для юзеров — Firebase должен про него знать чтобы Sign-In работал
  на устройствах конечных юзеров.

### 3.4. Чек-лист keystore

- [ ] Upload keystore создан, пароль в 1Password.
- [ ] Зашифрованный backup keystore на Google Drive (gpg).
- [ ] Зашифрованный backup keystore на флешке дома.
- [ ] Play App Signing **включён**.
- [ ] App signing SHA-1 (от Google) добавлен в Firebase Console.

---

## 4. Crashlytics + Vitals dashboards

После первого release setup'а:

1. **Crashlytics**:
   - https://console.firebase.google.com/project/launcher-prod/crashlytics
   - Custom keys: `auth.last_error_reason`, `auth.session_present`.

2. **Android Vitals** (в Play Console):
   - Anomaly detection: alert if sign-in failure rate > 5%.
   - ANR monitoring: trigger investigation если any ANR tagged with `Auth`.

3. **Email alerts**: подписаться на critical issues.

---

## 5. Pre-release checklist (перед каждым major release)

- [ ] **Безопасность аккаунта**: 2FA + hardware key работают, backup ключ на месте.
- [ ] **Firestore Rules** не relaxed (проверить `firestore.rules` через
      `git diff main` — нет временных `if request.auth != null` без validation).
- [ ] **`google-services.json` указывает на production** project (не dev).
- [ ] **`serverClientId` в `BackendInit.kt`** = production Web Client ID
      (TODO в коде заменён на реальное значение через BuildConfig).
- [ ] **APK signed** правильным production keystore.
- [ ] **SHA-1 от Play App Signing** добавлен в Firebase.
- [ ] **OAuth Consent Screen** — verified Google'ом (зелёная галочка).
- [ ] **Privacy Policy** обновлена для всех новых features.
- [ ] **Data Safety form** обновлён для всех новых типов данных.
- [ ] **Crashlytics + Vitals dashboards** настроены для новых alert thresholds.
- [ ] **Manual smoke test** на real device с real Google account (T907).
- [ ] **Backup-restore test** (T908) — verify что session blob не восстанавливается.

---

## История изменений

| Дата | Что добавлено | Спека |
|------|---------------|-------|
| 2026-06-19 | Initial: §0 account security, §1 Firebase prod, §2 Privacy/Data Safety, §3 keystore + Play App Signing, §4 Crashlytics, §5 release checklist | F-4 (spec 017) |
