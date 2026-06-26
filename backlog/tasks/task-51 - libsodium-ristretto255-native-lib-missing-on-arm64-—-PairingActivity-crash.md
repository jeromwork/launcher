---
id: TASK-51
title: libsodium consolidation — выкинуть lazysodium, единая family.crypto.api стопка
status: In Progress
assignee: []
created_date: '2026-06-25 11:48'
updated_date: '2026-06-26 06:30'
labels:
  - crypto
  - refactor
  - bug
milestone: m-1
dependencies:
  - TASK-2
priority: high
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Эволюция scope.** Изначально (2026-06-25) task создан как узкий bug-репорт: `UnsatisfiedLinkError: crypto_core_ristretto255_add` на Xiaomi 11T при открытии `PairingActivity`. После двух раундов mentor-разбора 2026-06-26 выяснилось:
>
> **Раунд 1**: симптом — лишь верхушка техдолга, в проекте две параллельные crypto-стопки.
>
> **Раунд 2 (полная разведка call-graph'а)**: реальный объём миграции **значительно меньше** изначальной паники. Большая часть проекта **уже мигрирована** на новый стэк `family.crypto.api.*`: recovery flow, envelope config encryption, root key management, recovery ViewModel, DI bindings для нового стэка — всё на нём. Осталось мигрировать **только pairing-side**: 1 главный orchestrator + 1 старый DI module + wire-format adapter + 3 unit-теста + smoke debug activity + gradle cleanup. Итого ~37 файлов изменить (из которых ~30 — удаление, ~7 — переписывание).
>
> **Реалистичная оценка после разведки: 10-15 часов чистой работы** (не 1.5-2 дня как казалось, и не 1.5-2 недели как пугал второй mentor-pass).

## Что это простыми словами

В проекте сейчас лежат **две параллельные crypto-стопки** — два разных пакета Kotlin-классов, делающих почти одно и то же:

1. **Старая стопка** (Android-only, через `lazysodium-android` 5.1.0). Пакет `com.launcher.api.crypto.*` (порты в commonMain) + `com.launcher.adapters.crypto.Libsodium*.kt` (адаптеры в androidMain). Досталась от ранних задач (spec 011, шифрование контактов). **Главная боль**: `lazysodium-android` собран **без поддержки ristretto255** — функции `crypto_core_ristretto255_add` нет в `.so` файле ни на одном ABI. Без неё протокол привязки админ-устройства (TASK-8) не работает.

2. **Новая стопка** (Kotlin Multiplatform, через `ionspin/libsodium-kmp` 0.9.5). Пакет `family.crypto.api.*` (всё в commonMain) + `family.crypto.libsodium.*` (реализации в commonMain). Добавлена позже (spec 016, TASK-2 F-CRYPTO). **Содержит** все нужные функции включая ristretto255. **Работает** на Android + iOS + JVM + потенциально JS.

**Беда** в том что **обе** стопки сейчас одновременно в проекте, и обе приносят свой `libsodium.so`. Чтобы они не конфликтовали при упаковке APK, в `app/build.gradle.kts` стоит костыль `pickFirsts` — «бери первый попавшийся файл». Комментарий рядом утверждает что файлы «бит-в-бит идентичны». **Это ложь** — я проверил байты, размеры разные (286 КБ vs 370 КБ), и в файле от lazysodium функций ristretto255 нет. Поэтому PairingActivity падает.

**Что делаем в этой задаче — полная консолидация (вариант B)**:

1. Переписываем код, который ещё использует старую стопку, на новую — это **~7 файлов**, главный из которых `PairingCryptoCoordinator` (паiring-логика).
2. Удаляем старую стопку целиком — **~30 файлов** (порты, адаптеры, старые fakes).
3. Удаляем `lazysodium` + `JNA` из gradle-зависимостей.
4. Удаляем костыль `pickFirsts` — теперь один `.so` на ABI, не два.

После этого в проекте **остаётся только одна crypto-стопка** — `family.crypto.api.*`. APK уменьшается на ~3-5 МБ. Cold start ускоряется на ~200-500 мс. Открывается путь к iOS / Android TV / desktop (crypto-слой уже в commonMain). Закрывается явный техдолг.

## Что значит «переписать» — детально

Это **не просто «переименовать пакеты»**. Между двумя стопками есть **четыре архитектурных различия**, которые требуют переписывания логики в нескольких местах:

### Различие №1: sync vs suspend

**Старый стиль** — функция выполняется сразу, блокирует поток:
```kotlin
val зашифровано = cipher.encrypt(данные, ключ)
```

**Новый стиль** — функция `suspend` (приостанавливаемая, часть Kotlin coroutines):
```kotlin
val зашифровано = cipher.encrypt(данные, ключ)  // только внутри coroutine scope
```

**Что меняется**: вызовы переходят в coroutine scope. **Хорошая новость**: у нас почти везде уже coroutines (Compose + suspend ViewModels). Цена миграции — низкая.

### Различие №2: Outcome vs throws

**Старый стиль** — функция возвращает либо успех, либо ошибку **как значение**:
```kotlin
val результат = cipher.encrypt(данные)
when (результат) {
    is Outcome.Success -> работаем(результат.value)
    is Outcome.Failure -> логируем(результат.error)
}
```

**Новый стиль** — функция бросает исключение при ошибке:
```kotlin
try {
    val результат = cipher.encrypt(данные)
    работаем(результат)
} catch (e: CryptoException) {
    логируем(e)
}
```

**Что меняется**: ~15-20 мест в коде, где сейчас `when (result) { is Outcome.Success ... }` — переписать на `try/catch`. Механическая работа, компилятор подскажет где забыли.

### Различие №3: alias-based vs keyId-based ключи

**Старый стиль** — ключ генерируется и сохраняется одним вызовом с человеческим именем:
```kotlin
val ключ = keystore.generateAndStoreEncryption("my-pairing-key")
```

**Новый стиль** — генерация и сохранение разделены, ID явный:
```kotlin
val байты = random.nextBytes(32)
val keyId = KeyId("encryption-${userId}")
keystore.store(keyId, байты)
```

**Что меняется**: в `PairingCryptoCoordinator` (главный файл миграции) — нужно явно генерировать ключ, потом сохранять. **Архитектурное** изменение, не механическое — придумываем какие keyId использовать.

### Различие №4: ContentEncryptionKey lifecycle

Это **security pattern** для безопасной очистки ключа в памяти. Старый стиль гарантировал zeroization через `use { }` блок; новый требует ручного `try/finally + fill(0)`. **Затрагивает только 1 место** (debug activity) — minor.

## Что **уже сделано** в проекте (большие новости)

При полной разведке call-graph'а выяснилось — большая часть кодовой базы **уже** работает на новом стэке `family.crypto.*`:

- ✅ **Recovery flow** (`family.keys.impl.RecoveryFlow`)
- ✅ **Envelope config encryption** (`family.keys.impl.EnvelopeConfigCipherImpl`)
- ✅ **Root key management** (`family.keys.impl.RootKeyManagerImpl`)
- ✅ **Recovery ViewModel** (`RecoveryViewModel`)
- ✅ **DI bindings** для нового стэка (`F016CryptoModule`, `F018KeysModule`, `F018KeysBackendModule`)
- ✅ **Envelope storage** (`FirestoreEnvelopeStorage`, `InMemoryEnvelopeStorage`)
- ✅ **Тесты для нового стэка** (`RecoveryFlowTest`, `LocalFirstConfigSaverTest`, `EnvelopeConfigCipherRoundtripTest`)

Остаётся мигрировать **только pairing-side** (spec 011 + spec 007 артефакты, которые ещё не переехали).

## Зачем

**Прямой эффект**: разблокируется TASK-8 (Admin App + QR Pairing) и AC #3 в TASK-55 (verification aggregator). Без ristretto255 протокол привязки админ-устройства не работает.

**Косвенный эффект**: закрывается явный техдолг — TODO в `app/build.gradle.kts:110-112` («когда spec 011 lazysodium-based adapters будут мигрированы на :core:crypto KMP binding, можно убрать lazysodium dependency и pickFirsts тоже»).

**Долгосрочный эффект**: один crypto-слой для всех платформ + всех будущих проектов семейства. Когда дойдёт до TASK-26 (iOS), TASK-29 (Android TV), TASK-27 (messenger), TASK-28 (Family Album) — crypto не дублируется, переиспользуется. MLS-протокол (TASK-42, parking-lot) добавляется как отдельная библиотека рядом, libsodium остаётся для примитивов.

## Про роли в этой задаче

Чисто инфраструктурная задача — пользовательских ролей не затрагивает. Влияет на:
- **разработчика** — один источник правды для crypto API
- **end-user** (пожилого) — быстрее cold start, меньше APK
- **future iOS / TV разработчика** — готовый commonMain crypto-слой

## Что входит технически (план 8 фаз)

### Phase 1: Gradle подготовка (~30 мин)
- Удалить `lazysodium = "5.1.0"` и `jna = "5.13.0"` из `gradle/libs.versions.toml`.
- Удалить lazysodium/JNA dependencies из `core/build.gradle.kts`, `core/keys/build.gradle.kts` (если есть).
- Удалить `packaging.jniLibs.pickFirsts` из `app/build.gradle.kts`.
- Удалить ABI splits если были специально для lazysodium.
- **Точка проверки**: проект **не** компилируется (это нормально — старый код ещё ссылается на lazysodium). Идём дальше.

### Phase 2: DI bindings + LibsodiumProvider (~2 часа)
- Переписать `CryptoModule.kt` (старый DI) — удалить bindings через `LibsodiumProvider`, оставить только bindings через `family.crypto.api.*`. Или удалить `CryptoModule.kt` целиком, если `F016CryptoModule` его покрывает.
- Удалить `LibsodiumProvider.kt` (singleton-фабрика lazysodium — больше не нужна).
- Проверка `Spec015DiGraphTest` — DI граф резолвится через Koin без ambiguity.

### Phase 3: PairingCryptoCoordinator (~4-6 часов) ⚠️ ГЛАВНАЯ ФАЗА
- Переписать `PairingCryptoCoordinator.kt`:
  - Убрать вызовы `keystore.generateAndStoreEncryption(alias)` и `keystore.generateAndStoreSigning(alias)` — заменить на `random.nextBytes() + keystore.store(keyId, bytes)`.
  - Убрать 6+ паттернов `when (result) { is Outcome.Success ... is Outcome.Failure ... }` — переписать на `try/catch CryptoException`.
  - Убрать `if (ensured is Outcome.Failure) return Outcome.Failure(...)` — теперь exceptions распространяются естественным образом.
  - Импорты: `com.launcher.api.crypto.*` → `family.crypto.api.*`, `com.launcher.api.result.Outcome` → стандартный try/catch.
- Обновить `PairingViewModel.kt` — callback `onLinkEstablished: suspend (linkId) -> Unit` уже suspend, проверить что обёртка корректна.
- **Точка проверки**: `PairingCryptoCoordinatorTest` зелёный (после Phase 5).

### Phase 4: Wire format слой + integration (~2 часа)
- `FirestoreDeviceIdentityRepository.kt` — переписать 4+ места с `Outcome` на `try/catch`. `DeviceIdentity` wire-format остаётся (это формат данных в Firestore, не криптография).
- `WorkerEncryptedMediaStorage.kt` — `Outcome` → `try/catch`, импорты на новые.
- `PairingModule.kt` — DI bindings для нового стэка.
- `BackendInit.kt` (real + mock backend) — обновить crypto bindings.

### Phase 5: Тесты (~3 часа)
- Переписать `PairingCryptoCoordinatorTest.kt` — использовать новые fakes из `family.crypto.fake.*` вместо `com.launcher.fake.crypto.*`.
- Переписать `LibsodiumAdaptersTest.kt` — либо удалить (старые адаптеры не существуют), либо переписать под новый стэк (тесты на `family.crypto.libsodium.*` уже есть в `core/crypto/src/jvmTest/`).
- Переписать `CryptoEnvelopeWireFormatTest.kt` — `FakeAeadCipher` → `family.crypto.fake.FakeAeadCipher`.
- Обновить fitness-тесты:
  - `Spec011IsolationTest.kt` — запретить `com.goterl.lazysodium.*` импорты + `com.launcher.api.crypto.*` (старая стопка).
  - `Spec014IsolationTest.kt` — аналогично.
  - `NoFakeCryptoInAppTest.kt` — обновить hardcoded путь от `family.crypto.fake` (если изменился).

### Phase 6: Удаление старого (~30 мин)
- Удалить `core/src/commonMain/kotlin/com/launcher/api/crypto/*.kt` (22 файла) — это уже только устаревшие порты.
- Удалить `core/src/androidMain/kotlin/com/launcher/adapters/crypto/Libsodium*.kt` (5 файлов): `LibsodiumAeadCipher`, `LibsodiumAsymmetricCrypto`, `LibsodiumDigitalSignature`, `LibsodiumHashFunction`, `LibsodiumProvider`.
- Удалить `core/src/androidMain/kotlin/com/launcher/adapters/crypto/AndroidKeystoreSecureKeystore.kt` — если он использовал lazysodium для AES-GCM unwrap. Если использовал чистый Android Keystore — оставить, но обновить чтобы возвращал `ByteArray` вместо `DeviceKeyPair`.
- Удалить старые fakes `core/src/commonTest/kotlin/com/launcher/fake/crypto/*.kt` (8 файлов).

### Phase 7: Сборка + verification на устройстве (~1 час)
- `./gradlew :app:assembleMockBackendDebug` → BUILD SUCCESSFUL.
- ASCII-grep по `libsodium.so` для всех 4 ABI (arm64-v8a, armeabi-v7a, x86, x86_64) — функции `crypto_core_ristretto255_add`, `crypto_box_easy`, `crypto_sign_detached`, `crypto_aead_xchacha20poly1305_ietf_encrypt` должны быть **во всех**.
- `./gradlew test` → все юнит-тесты + Robolectric зелёные.
- Замер APK size до/после.
- Установка APK на Xiaomi 11T (`adb install`, устройство `17f33878` подключено).
- Запуск `Spec011SmokeDebugActivity` — round-trip encrypt/decrypt без `UnsatisfiedLinkError`.
- Запуск `PairingActivity` — открывается без crash'а.
- Замер cold start.

### Phase 8: Backlog hygiene + PR (~30 мин)
- Обновить AC по факту прохождения (15 AC ниже).
- `pre-pr-backlog-sync` skill.
- PR на ветке `task-51-libsodium-consolidation` → main.

## Список изменяемых файлов (~37)

**Переписываемые (~7)**:
- `PairingCryptoCoordinator.kt` (HIGH)
- `CryptoModule.kt` (HIGH)
- `FirestoreDeviceIdentityRepository.kt` (MEDIUM)
- `WorkerEncryptedMediaStorage.kt` (LOW)
- `PairingModule.kt` (LOW)
- `BackendInit.kt` (LOW)
- `Spec011SmokeDebugActivity.kt` (LOW — debug only)

**Унит-тесты для переписывания (~3)**:
- `PairingCryptoCoordinatorTest.kt`
- `LibsodiumAdaptersTest.kt` (или удалить)
- `CryptoEnvelopeWireFormatTest.kt`

**Fitness-тесты для обновления (~3)**:
- `Spec011IsolationTest.kt`
- `Spec014IsolationTest.kt`
- `NoFakeCryptoInAppTest.kt`

**Удаляемые (~35)**:
- 22 файла в `core/src/commonMain/kotlin/com/launcher/api/crypto/`
- 5 файлов `core/src/androidMain/.../adapters/crypto/Libsodium*.kt`
- 8 файлов старых fakes в `core/src/commonTest/kotlin/com/launcher/fake/crypto/`

**Gradle (4)**:
- `gradle/libs.versions.toml`
- `core/build.gradle.kts`
- `core/keys/build.gradle.kts` (если упоминает lazysodium/JNA)
- `app/build.gradle.kts`

## Состояние

**In Progress 2026-06-26**. Mentor-разбор завершён, полная разведка call-graph'а сделана, scope согласован (вариант B — полная консолидация). Ветка `task-51-libsodium-consolidation` создана. Готов к старту Phase 1.

**Зависимости**: TASK-2 (F-CRYPTO `:core:crypto`) — Done. lazysodium-адаптеры из spec 011 (TASK-4) переписываются в этой задаче.

**Блокирует**: TASK-8 (Admin App + QR Pairing), AC #3 TASK-55 (pair-admin step после возврата в TASK-8).

**Не блокирует**: TASK-52 (HomeActivity hang) — отдельная история, не связана с crypto.

**Реалистичная оценка**: **10-15 часов чистой работы**, выполнимо за 2 рабочих дня. Главная боль — Phase 3 (PairingCryptoCoordinator, 4-6 часов).

## Что я уже проверил перед стартом

- Устройство Xiaomi 11T (`17f33878`) подключено через adb — физическая проверка возможна.
- ASCII-grep по `libsodium.so` подтвердил отсутствие `crypto_core_ristretto255_add` на arm64 **и** x86_64 (баг универсальный, не arm64-only).
- Размеры `.so` разные (286 КБ arm64 vs 370 КБ x86_64) — комментарий «бит-в-бит идентичны» в `app/build.gradle.kts:106` неверный.
- Порты `family.crypto.api.*` уже в commonMain → переносить структуру не нужно.
- Большая часть проекта уже мигрирована на новый стэк → реальный объём изменений значительно меньше изначальной оценки.
- Полный call-graph построен (отчёт sub-agent'а в conversation history). Знаю каждый файл который придётся трогать.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Открываю экран привязки админ-устройства (PairingActivity) на Xiaomi 11T — приложение **не падает** с UnsatisfiedLinkError. Это главный симптом, из-за которого task был заведён.
- [ ] #2 [hand] Прогоняю smoke-тест шифрования (Spec011SmokeDebugActivity) на Xiaomi 11T — round-trip (зашифровал → расшифровал → получил исходные байты) проходит без ошибок. Подтверждает что после миграции старые crypto-сценарии продолжают работать.
- [ ] #3 [hand] Внутри собранного APK файл libsodium.so для **процессора arm64** (реальные телефоны) содержит функцию ristretto255_add — проверяю поиском по ASCII-строкам внутри бинаря. Без этой функции протокол привязки админа не работает.
- [ ] #4 [hand] То же для остальных трёх архитектур (x86_64 — эмуляторы на Intel/AMD ноутбуках, armeabi-v7a — старые 32-битные телефоны, x86 — устаревшие эмуляторы). Все четыре .so содержат ristretto255_add. Страховка чтобы баг не вернулся на другом устройстве.
- [ ] #5 [hand] В исходном коде проекта **не осталось упоминаний старой библиотеки lazysodium** (grep по `lazysodium` в production-коде даёт 0 матчей, кроме исторических specs/ и docs/). Подтверждает что мы её действительно выкинули, а не только переключили pickFirst.
- [ ] #6 [hand] В исходном коде **не осталось импортов** `com.goterl.*` (это пакет lazysodium от компании Terl). Проверка через grep.
- [ ] #7 [hand] **Старая параллельная стопка** `com.launcher.api.crypto` полностью ликвидирована — grep по проекту даёт 0 матчей. После этого в проекте остаётся только одна KMP-стопка `family.crypto.api`.
- [ ] #8 [hand] **Старые адаптеры** `com.launcher.adapters.crypto.LibsodiumXxx` полностью удалены — grep даёт 0 матчей.
- [ ] #9 [hand] **Из gradle-конфигурации** убраны зависимости `lazysodium` и `jna` (JNA — runtime-обёртка вокруг JNI, тащилась только из-за lazysodium). Проверка: grep по `gradle/libs.versions.toml` и всем `build.gradle.kts`.
- [ ] #10 [hand] **Хитрая настройка pickFirsts** в `app/build.gradle.kts` (нужна была чтобы две библиотеки не конфликтовали при упаковке .so в APK) — удалена. Теперь одна .so на ABI, никаких костылей.
- [ ] #11 [hand] **Полная пересборка APK** командой `./gradlew :app:assembleMockBackendDebug` проходит без ошибок (BUILD SUCCESSFUL). Подтверждает что после миграции импортов компиляция чистая.
- [ ] #12 [hand] **Все автотесты зелёные** — `./gradlew test` (юнит-тесты + Robolectric, которые гоняют crypto-адаптеры на JVM без устройства). Подтверждает что миграция не сломала логику.
- [ ] #13 [hand] **Архитектурные fitness-тесты** (Spec011IsolationTest + Spec014IsolationTest + NoFakeCryptoInAppTest) обновлены — теперь запрещают использовать lazysodium и старую стопку `com.launcher.api.crypto`. Зелёные. Защитная сетка чтобы случайно не вернуть удалённое.
- [ ] #14 [hand] **APK похудел** — итоговый размер debug-APK меньше предыдущего на 3+ МБ (выкинули JNA весом ~5 МБ). Замер до/после через `./gradlew :app:assembleMockBackendDebug` + сравнение размера выходного APK файла.
- [ ] #15 [hand] **Cold start приложения** (от тапа на иконку до первого экрана) на Xiaomi 11T не вырос по сравнению с прошлым замером (baseline 1260-1330 мс из TASK-7 verification). В идеале — стал короче на 200-500 мс, потому что ionspin через JNI быстрее запускается чем lazysodium через JNA.
<!-- AC:END -->
