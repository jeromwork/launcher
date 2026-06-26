---
id: TASK-51
title: libsodium consolidation — выкинуть lazysodium, единая cryptokit стопка
status: In Progress
assignee: []
created_date: '2026-06-25 11:48'
updated_date: '2026-06-26 12:30'
labels:
  - crypto
  - refactor
  - bug
milestone: m-1
dependencies:
  - TASK-2
references:
  - specs/task-51-libsodium-consolidation/
priority: high
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Эволюция scope.** Изначально (2026-06-25) task создан как узкий bug-репорт: `UnsatisfiedLinkError: crypto_core_ristretto255_add` на Xiaomi 11T при открытии `PairingActivity`. После трёх раундов разбора 2026-06-26 выяснилось:
>
> **Раунд 1**: симптом — лишь верхушка техдолга, в проекте две параллельные crypto-стопки.
>
> **Раунд 2** (полная разведка call-graph'а): реальный объём миграции — ~37 файлов (большая часть проекта уже на новом стэке).
>
> **Раунд 3 — РЕАЛЬНЫЙ ROOT CAUSE** (через stacktrace с устройства Xiaomi 11T 2026-06-26): нам **не нужна функция ristretto255** в `.so` вовсе. **Никакой Kotlin-код в проекте не вызывает Ristretto255**. Crash происходит на **`SodiumAndroid.<init>`** в lazysodium-android 5.1.0, который через `JNA.register()` делает **eager-bind всех ~600 функций** интерфейса `Sodium` — включая декларации `crypto_core_ristretto255_add`, которых нет в поставляемом ими `libsodium.so` 1.0.20 (собран без `--enable-ristretto255` публичного API). Crash возникает при первом резолве `AndroidKeystoreSecureKeystore` через Koin (он зависит от `LibsodiumProvider.getSodium`).
>
> **Архитектурно**: lazysodium-android **архитектурно сломан** для libsodium 1.0.20 — JNA-eager-bind несовместим с partial libsodium build. Это **именно та причина, почему ionspin лучше**: ionspin использует **JNI lazy-bind**, биндит только то, что реально вызывается. Так как нашей кодовой базе Ristretto255 не нужен — после миграции JNI никогда не пытается найти `crypto_core_ristretto255_add`, crash исчезает.
>
> **Реалистичная оценка после разведки: 10-15 часов чистой работы.**

## Что это простыми словами

В проекте сейчас лежат **две параллельные crypto-стопки** — два разных пакета Kotlin-классов, делающих почти одно и то же:

1. **Старая стопка** (Android-only, через `lazysodium-android` 5.1.0). Пакет `com.launcher.api.crypto.*` (порты в commonMain) + `com.launcher.adapters.crypto.Libsodium*.kt` (адаптеры в androidMain). Досталась от ранних задач (spec 011, шифрование контактов). **Главная боль**: `lazysodium-android` собран **без поддержки ristretto255** — функции `crypto_core_ristretto255_add` нет в `.so` файле ни на одном ABI. Без неё протокол привязки админ-устройства (TASK-8) не работает.

2. **Новая стопка** (Kotlin Multiplatform, через `ionspin/libsodium-kmp` 0.9.5). Пакет `family.crypto.api.*` (всё в commonMain) + `family.crypto.libsodium.*` (реализации в commonMain). Добавлена позже (spec 016, TASK-2 F-CRYPTO). **Содержит** все нужные функции включая ristretto255. **Работает** на Android + iOS + JVM + потенциально JS.

**Беда** в том что **обе** стопки сейчас одновременно в проекте, и обе приносят свой `libsodium.so`. Чтобы они не конфликтовали при упаковке APK, в `app/build.gradle.kts` стоит костыль `pickFirsts` — «бери первый попавшийся файл».

## Реальный root cause (раунд 3 разбора)

**Stacktrace с Xiaomi 11T 2026-06-26** показал, что crash **не из-за того, что нашему коду нужна функция `crypto_core_ristretto255_add`**. Никакой Kotlin-код в проекте Ristretto255 **не вызывает** — это проверено grep'ом по всему проекту (0 матчей).

Crash происходит **на этапе инициализации Koin DI** при открытии PairingActivity, в этой цепочке:

```
PairingActivity (открывается)
  → Koin резолвит SecureKeystore из старого CryptoModule.kt
    → создаёт AndroidKeystoreSecureKeystore
      → его конструктор вызывает LibsodiumProvider.getSodium
        → инициализирует com.goterl.lazysodium.SodiumAndroid
          → SodiumAndroid.<init> вызывает JNA.register()
            → JNA пытается eager-bind ВСЕХ ~600 функций интерфейса Sodium
              → не находит crypto_core_ristretto255_add в libsodium.so → CRASH
```

**Архитектурный изъян lazysodium**: библиотека интерфейс декларирует **все** функции libsodium включая ristretto255, но поставляемый ею `libsodium.so` (1.0.20) собран **без публичного ristretto255 API**. JNA при `register()` проверяет наличие всех символов **сразу** (eager-bind) — отсутствие любого = crash.

**Почему ionspin не упадёт**: ionspin использует JNI напрямую, не JNA. JNI биндит функции **lazy** — только когда они реально вызываются. Раз наш код Ristretto255 не вызывает — JNI никогда не пытается найти этот символ. Никакого crash'а.

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
- **Воспроизвёл crash на устройстве 2026-06-26**: установил APK с веткой до Phase 1, запустил PairingActivity, поймал stacktrace:
  ```
  java.lang.UnsatisfiedLinkError: Error looking up function 'crypto_core_ristretto255_add'
    at com.sun.jna.Native.register(Native.java:1900)
    at com.goterl.lazysodium.SodiumAndroid.<init>(SodiumAndroid.java:36)
    at com.launcher.adapters.crypto.LibsodiumProvider.sodium_delegate$lambda$0
    at com.launcher.adapters.crypto.AndroidKeystoreSecureKeystore.<init>
    at com.launcher.app.di.CryptoModuleKt.cryptoModule$lambda$11$lambda$4
  ```
  Это **JNA eager-bind** при инициализации SodiumAndroid — не наш код, а внутрянка lazysodium.
- **Grep по всему проекту**: `Ristretto255|ristretto255\.|crypto_core_ristretto` → **0 матчей** в Kotlin-коде. То есть наш код Ristretto255 **никогда не вызывает**. Это окончательно подтверждает: после миграции на ionspin (JNI lazy-bind) crash исчезнет сам, потому что JNI не пытается найти функции, которые никто не использует.
- ASCII-grep по `libsodium.so` показал что **в обеих библиотеках** (lazysodium и ionspin) публичные ristretto255 функции **отсутствуют** в `.so`. Внутренние helpers (`ristretto255_elligator`, `_from_hash`, и т.д.) присутствуют, но `crypto_core_ristretto255_*` и `crypto_scalarmult_ristretto255_*` нет ни в одной. libsodium внутри обеих библиотек — **1.0.20**, собран без публичного ristretto255 API.
- Порты `family.crypto.api.*` уже в commonMain → переносить структуру не нужно.
- Большая часть проекта уже мигрирована на новый стэк → реальный объём изменений значительно меньше изначальной оценки.
- Полный call-graph построен (отчёт sub-agent'а в conversation history). Знаю каждый файл который придётся трогать.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Открываю экран привязки админ-устройства (PairingActivity) на Xiaomi 11T — приложение **не падает** с UnsatisfiedLinkError. Это главный симптом, из-за которого task был заведён.
- [ ] #2 [hand] Прогоняю smoke-тест шифрования (Spec011SmokeDebugActivity) на Xiaomi 11T — round-trip (зашифровал → расшифровал → получил исходные байты) проходит без ошибок. Подтверждает что после миграции старые crypto-сценарии продолжают работать.
- [ ] #3 [hand] В APK остался **только один** `libsodium.so` на ABI (от ionspin через `:core:crypto`). Проверяю через `unzip -l APK | grep libsodium.so` — на каждый ABI ровно одна запись. Раньше было две (из lazysodium и ionspin) с костылём `pickFirsts`.
- [ ] #4 [hand] В коде проекта не осталось **никаких JNA `register()` вызовов** через lazysodium — root cause исходного crash'а устранён. Проверка через grep `JNA\.register\|SodiumAndroid\|lazysodium.SodiumJava` = 0 матчей. ionspin использует JNI lazy-bind, который не падает на отсутствующих функциях, потому что не пытается их eager-биндить.
- [ ] #5 [hand] В исходном коде проекта **не осталось упоминаний старой библиотеки lazysodium** (grep по `lazysodium` в production-коде даёт 0 матчей, кроме исторических specs/ и docs/). Подтверждает что мы её действительно выкинули, а не только переключили pickFirst.
- [ ] #6 [hand] В исходном коде **не осталось импортов** `com.goterl.*` (это пакет lazysodium от компании Terl). Проверка через grep.
- [ ] #7 [hand] **Старая параллельная стопка** `com.launcher.api.crypto` полностью ликвидирована — grep по проекту даёт 0 матчей. После этого в проекте остаётся только одна KMP-стопка `family.crypto.api`.
- [ ] #8 [hand] **Старые адаптеры** `com.launcher.adapters.crypto.LibsodiumXxx` полностью удалены — grep даёт 0 матчей.
- [ ] #9 [hand] **Из gradle-конфигурации** убраны зависимости `lazysodium` и `jna` (JNA — runtime-обёртка вокруг JNI, тащилась только из-за lazysodium). Проверка: grep по `gradle/libs.versions.toml` и всем `build.gradle.kts`.
- [ ] #10 [hand] **Хитрая настройка pickFirsts** в `app/build.gradle.kts` (нужна была чтобы две библиотеки не конфликтовали при упаковке .so в APK) — удалена. Теперь одна .so на ABI, никаких костылей.
- [ ] #11 [hand] **Полная пересборка APK** командой `./gradlew :app:assembleMockBackendDebug` проходит без ошибок (BUILD SUCCESSFUL). Подтверждает что после миграции импортов компиляция чистая.
- [ ] #12 [hand] **Все автотесты зелёные** — `./gradlew test` (юнит-тесты + Robolectric, которые гоняют crypto-адаптеры на JVM без устройства). Подтверждает что миграция не сломала логику.
- [ ] #13 [hand] **Архитектурные fitness-тесты** (Spec011IsolationTest + Spec014IsolationTest + NoFakeCryptoInAppTest) обновлены — теперь запрещают использовать lazysodium и старую стопку `com.launcher.api.crypto`. Зелёные. Защитная сетка чтобы случайно не вернуть удалённое.
- [N/A] #14 ~~APK размер~~ — removed 2026-06-26 per owner-mandate: APK size meaningful только при выборе **внешних** библиотек (lazysodium vs ionspin choice — уже зафиксирован в clarifications Q1), а не как acceptance criterion на собственный refactor. См. memory `feedback_apk_size_only_for_external_libs`.
- [N/A] #15 ~~Cold start~~ — removed 2026-06-26 per owner-mandate: cold start measurement как acceptance не нужен для своего кода (как телефон отрабатывает, так и будет запускаться). Сравнение с baseline неприемлемо для собственного code refactor.
- [ ] #16 [spec:SC-011] **Silent auto-migration проверен на Xiaomi 11T**: после установки нового APK поверх старой версии при первом обращении к ключу — старая запись прочитана через AndroidKeystore TEE, перешифрована под новым именем, старая удалена. **Никаких UI-шагов**, существующий pairing продолжает работать (FR-005).
- [ ] #17 [spec:SC-012] **Namespace `family.*` полностью отсутствует** — `grep -r "family\.crypto" --include="*.kt"` по проекту даёт 0 матчей. Новый namespace `cryptokit.*` единственный.
- [ ] #18 [spec:SC-013] **Golden vectors roundtrip** — `./gradlew :core:keys:jvmTest --tests "*EnvelopeConfigCipherRoundtripTest"` проходит байт-в-байт после namespace rename (FR-004 serialization compatibility).
- [ ] #19 [spec:SC-014] **Logcat tag `cryptokit`** появляется при искусственно вызванной CryptoException (negative test): `adb logcat -s cryptokit` + ручное triggering неправильного ключа (FR-017 logging contract).
<!-- AC:END -->
