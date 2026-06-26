---
id: TASK-51
title: libsodium consolidation — выкинуть lazysodium, единая family.crypto.api стопка
status: In Progress
assignee: []
created_date: '2026-06-25 11:48'
updated_date: '2026-06-26 05:45'
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
> **Эволюция scope.** Изначально (2026-06-25) task создан как узкий bug-репорт: `UnsatisfiedLinkError: crypto_core_ristretto255_add` на Xiaomi 11T при открытии `PairingActivity`. После mentor-разбора 2026-06-26 выяснилось, что симптом — лишь верхушка техдолга: в проекте лежат **две параллельные crypto-стопки**, и одна из них тащит lazysodium-android без поддержки нужного нам криптопротокола. Scope расширен до полной консолидации в одну KMP-стопку (вариант B по обсуждению).

## Что это простыми словами

Представь, что у нас в проекте установлены **две разные библиотеки для шифрования** — обе делают примерно одно и то же, обе содержат внутри одну и ту же сишную библиотеку `libsodium` (промышленный стандарт криптографии, как «двигатель» для всех шифровальных операций). Это всё равно что в одной кухне держать две одинаковые кофемашины — обе работают, но одна занимает место зря.

**Старая библиотека** (`lazysodium-android`) — досталась нам от ранних задач (spec 011, шифрование контактов). Она работает только на Android. Подключается через медленный мостик-обёртку (`JNA`), из-за чего приложение запускается на полсекунды дольше. И главное — её разработчики **не включили** одну важную функцию (`ristretto255`), которая нужна для протокола привязки админ-устройства (TASK-8). Поэтому всё падает.

**Новая библиотека** (`ionspin/libsodium-kmp`) — добавлена позже (spec 016, TASK-2). Она **межплатформенная** (KMP — Kotlin Multiplatform, один код для Android + iOS + desktop + браузера), быстрее на запуске (использует прямой мостик `JNI` вместо `JNA`), легче на ~3-5 МБ в APK, и содержит **все** нужные функции включая `ristretto255`.

**Беда в том**, что обе библиотеки одновременно лежат в проекте и **обе** приносят свой файл `libsodium.so` (нативный бинарник, скомпилированный под архитектуру процессора). Чтобы они не конфликтовали, в `app/build.gradle.kts` стоит хитрая настройка `pickFirsts` — «бери первый попавшийся файл, второй игнорируй». Рядом комментарий уверяет, что оба файла «бит-в-бит идентичны». **Это оказалось ложью** — я проверил байты, размеры разные (286 КБ vs 370 КБ), и в первом файле (от lazysodium) функций `ristretto255` нет вовсе. Поэтому `PairingActivity` падает.

**Что делаем в этой задаче**: убираем старую библиотеку lazysodium совсем, оставляем только новую ionspin. Все места в коде, которые сейчас используют старую (там старый пакет называется `com.launcher.api.crypto.*`), переписываем на новую (`family.crypto.api.*`). После этого:

- `pickFirsts` становится не нужен — один файл `.so` на каждую архитектуру процессора, не два
- APK уменьшается примерно на 3-5 МБ (выкидываем `JNA`-обёртку весом ~5 МБ)
- Cold start приложения сокращается на ~200-500 мс (ionspin через JNI быстрее JNA)
- Открывается путь к iOS / Android TV / desktop — крипто-слой теперь в `commonMain`, готов к переносу
- Закрывается явный техдолг, зафиксированный TODO-комментарием в `app/build.gradle.kts:110-112` несколько месяцев назад

**Почему это не маленькая задача.** Объём работы — переписать импорты в **60+ файлах**. Большинство — простые замены `com.launcher.api.crypto.*` на `family.crypto.api.*`. Но в некоторых местах типы **называются по-разному** в двух стопках (например `DeviceKeyPair` в старой ≠ `KeyPair` в новой по полям), и там нужно чинить точечно. Реалистичная оценка — 1.5-2 дня работы в один присест.

## Зачем

**Прямой эффект**: разблокируется TASK-8 (Admin App + QR Pairing) и AC #3 в TASK-55 (verification aggregator). Без `ristretto255` протокол привязки админ-устройства не работает.

**Косвенный эффект**: закрывается техдолг, явно зафиксированный в `app/build.gradle.kts:110-112` TODO-комментарием: «когда spec 011 lazysodium-based adapters будут мигрированы на :core:crypto KMP binding, можно убрать lazysodium dependency и pickFirsts тоже». Этот TODO висел с момента ввода ionspin. Сейчас закрываем.

**Долгосрочный эффект**: один crypto-слой для всех платформ + всех будущих проектов семейства. Когда дойдёт до:
- **TASK-26** (iOS Admin Preset) — crypto-слой переносится без работы (уже в commonMain)
- **TASK-29** (Android TV Preset) — Android TV это Android, работает as-is
- **TASK-27** (Elderly-Friendly Messenger), **TASK-28** (Family Album) — crypto не дублируется, переиспользуется
- **MLS-протокол** (TASK-42, parking-lot) — добавляется как **отдельная** библиотека рядом, libsodium остаётся для примитивов

Также `:core:crypto` готов к выносу в отдельный репозиторий, когда появится второй потребитель (per CLAUDE.md memory «Library в launcher-репо до 2-го потребителя»).

## Про роли в этой задаче

Чисто инфраструктурная задача — пользовательских ролей не затрагивает. Влияет на:
- **разработчика** — один источник правды для crypto API, никакой путаницы «которое из двух?»
- **end-user** (пожилого) — быстрее cold start приложения, меньше APK на устройстве
- **future iOS / TV разработчика** — готовый commonMain crypto-слой, не нужно переписывать

## Что входит технически (для AI-агента)

**Phase A — дискавери и подготовка**:
- Полная карта расхождений между `com.launcher.api.crypto.*` (старая стопка, 22 файла в commonMain ports) и `family.crypto.api.*` (новая стопка, 13 файлов). Для каждого типа из старой — что в новой: 1:1, нужна обёртка, или нужно добавить новый тип.
- Достроить `family.crypto.api.*` недостающими типами из старой стопки: `EncryptedEnvelope`, `CryptoEnvelopeWireFormat`, `Recipient`, `RecipientResolver`, `DeviceIdentity`, `DeviceIdentityRepository`, `EncryptedMediaStorage`, `HashFunction`, `ContentEncryptionKey`, `InMemoryPrivateKeys`, `CryptoError`.

**Phase B — миграция call-sites**:
- Заменить импорты `com.launcher.api.crypto.*` → `family.crypto.api.*` во всех call-sites (по grep'у ≥40 файлов).
- Чинить компиляцию там, где сигнатуры различаются (точечно по месту).

**Phase C — удаление старой стопки**:
- Удалить файлы `core/src/commonMain/kotlin/com/launcher/api/crypto/*.kt` (22 файла).
- Удалить файлы `core/src/androidMain/kotlin/com/launcher/adapters/crypto/Libsodium*.kt` (5 файлов).
- `AndroidKeystoreSecureKeystore.kt` — содержит lazysodium-вызовы для AES-GCM unwrap: переписать на чистый Android Keystore + ionspin.

**Phase D — gradle cleanup**:
- Удалить `lazysodium = "5.1.0"` и `jna = "5.13.0"` из `gradle/libs.versions.toml`.
- Удалить lazysodium / JNA dependencies в `core/build.gradle.kts`, `core/keys/build.gradle.kts`.
- Удалить `packaging.jniLibs.pickFirsts` из `app/build.gradle.kts`.
- Удалить или адаптировать ABI splits.

**Phase E — verification**:
- Сборка debug APK + ASCII-grep на `.so` для всех 4 ABI (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) — `crypto_core_ristretto255_add`, `crypto_box_easy`, `crypto_sign_detached`, `crypto_aead_xchacha20poly1305_ietf_encrypt` должны быть везде.
- `./gradlew test` — все юнит-тесты + Robolectric зелёные.
- Обновить fitness-тесты `Spec011IsolationTest.kt` + `Spec014IsolationTest.kt` — теперь они запрещают `com.goterl.lazysodium` и `com.launcher.api.crypto` целиком.
- Установить APK на Xiaomi 11T (устройство `17f33878` подключено через adb) + запустить `Spec011SmokeDebugActivity` + `PairingActivity` — без `UnsatisfiedLinkError`.
- Замерить APK size до/после + cold start до/после.

**Phase F — backlog hygiene**:
- AC обновляются по факту прохождения.
- `pre-pr-backlog-sync` перед PR.

**Конкретные файлы под изменения** (по grep'у `com.launcher.api.crypto|LibsodiumAsymmetricCrypto|LibsodiumDigitalSignature|LibsodiumHashFunction|LibsodiumAeadCipher|AndroidKeystoreSecureKeystore|LibsodiumProvider`):
- **Production**: 5 файлов `Libsodium*.kt` + `AndroidKeystoreSecureKeystore.kt` (androidMain), 22 файла в `com.launcher.api.crypto/` (commonMain ports), `PairingCryptoCoordinator.kt`, `PairRecipientResolver.kt`, `ClearDataDetector.kt`, `BackgroundReconciler.kt`, `StorageRetryWorker.kt`, `SqlDelightBlobReferenceLedger.kt`, `WorkerEncryptedMediaStorage.kt`, `FirestoreDeviceIdentityRepository.kt`, `BackendInit.kt`, DI модули `F016CryptoModule.kt`, `CryptoModule.kt`, `PairingModule.kt`, debug `Spec011SmokeDebugActivity.kt`.
- **Tests**: `LibsodiumAdaptersTest.kt`, `PairingCryptoCoordinatorTest.kt`, `CleanupMachineryTest.kt`, `Spec011IsolationTest.kt`, `Spec014IsolationTest.kt`, 6 фейков в `core/src/commonTest/kotlin/com/launcher/fake/crypto/`, 9 файлов в `core/keys/src/commonTest/kotlin/family/keys/`.
- **Build**: `gradle/libs.versions.toml`, `core/build.gradle.kts`, `core/keys/build.gradle.kts`, `app/build.gradle.kts`.

Итого: ~60+ файлов.

**Что НЕ входит** (отдельные task'и):
- Вынос `:core:crypto` в отдельный git-репозиторий — Phase 5 parking-lot, активируется когда появится второй потребитель (messenger TASK-27, medical app). Создам Draft если нужно.
- iOS-адаптеры для `family.crypto.api.*` — часть TASK-26 (iOS Admin Preset).
- MLS-поддержка — TASK-42 parking-lot, отдельная библиотека рядом с libsodium.

## Состояние

**In Progress 2026-06-26**. Mentor-разбор завершён, scope согласован (вариант B — полная консолидация). Готов к старту в ветке `task-51-libsodium-consolidation`.

**Зависимости**: TASK-2 (F-CRYPTO `:core:crypto`) — Done. lazysodium-адаптеры из spec 011 (TASK-4) переписываются в этой задаче.

**Блокирует**: TASK-8 (Admin App + QR Pairing), AC #3 TASK-55 (pair-admin step после возврата в TASK-8).

**Не блокирует**: TASK-52 (HomeActivity hang) — отдельная история, не связана с crypto.

**Реалистичная оценка**: 1.5-2 дня работы в один присест. 60+ файлов править, большинство — простые import-замены, но есть точки где сигнатуры между стопками различаются.

## Что я уже проверил перед стартом

- Устройство Xiaomi 11T (`17f33878`) подключено через adb — физическая проверка возможна.
- ASCII-grep по `core/src/androidMain/...arm64/libsodium.so` подтвердил отсутствие `crypto_core_ristretto255_add`.
- ASCII-grep по `core/src/androidMain/...x86_64/libsodium.so` подтвердил отсутствие `crypto_core_ristretto255_add` тоже (значит баг универсальный, не arm64-specific).
- Размеры `.so` файлов разные (286 КБ arm64 vs 370 КБ x86_64) — комментарий «бит-в-бит идентичны» в `app/build.gradle.kts:106` неверный.
- Порты `family.crypto.api.*` уже в commonMain → переносить структуру не нужно, только дополнить недостающими типами.
- Порты `com.launcher.api.crypto.*` тоже в commonMain → удалить можно без перекладывания.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Открываю экран привязки админ-устройства (PairingActivity) на Xiaomi 11T — приложение **не падает** с UnsatisfiedLinkError. Это главный симптом, из-за которого task был заведён.
- [ ] #2 [hand] Прогоняю smoke-тест шифрования (Spec011SmokeDebugActivity) на Xiaomi 11T — round-trip (зашифровал → расшифровал → получил исходные байты) проходит без ошибок. Это подтверждает что после миграции старые crypto-сценарии (envelope encryption, sealed box) продолжают работать.
- [ ] #3 [hand] Внутри собранного APK файл libsodium.so для **процессора arm64** (реальные телефоны) содержит функцию ristretto255_add — проверяю поиском по ASCII-строкам внутри бинаря. Без этой функции протокол привязки админа не работает.
- [ ] #4 [hand] То же для остальных трёх архитектур (x86_64 — это эмуляторы на Intel/AMD ноутбуках, armeabi-v7a — старые 32-битные телефоны, x86 — устаревшие эмуляторы). Все четыре .so должны содержать ristretto255_add. Это страховка чтобы баг не вернулся на другом устройстве.
- [ ] #5 [hand] В исходном коде проекта **не осталось упоминаний старой библиотеки lazysodium** (grep по `lazysodium` в production-коде даёт 0 матчей, кроме исторических specs/ и docs/). Это подтверждает что мы её действительно выкинули, а не только переключили `pickFirst`.
- [ ] #6 [hand] В исходном коде **не осталось импортов** `com.goterl.*` (это пакет lazysodium от компании Terl). Подтверждение через grep.
- [ ] #7 [hand] **Старая параллельная стопка** `com.launcher.api.crypto` полностью ликвидирована — grep по проекту даёт 0 матчей. После этого в проекте остаётся только одна KMP-стопка `family.crypto.api`.
- [ ] #8 [hand] **Старые адаптеры** `com.launcher.adapters.crypto.LibsodiumXxx` полностью удалены — grep даёт 0 матчей.
- [ ] #9 [hand] **Из gradle-конфигурации** убраны зависимости `lazysodium` и `jna` (JNA — это runtime-обёртка вокруг JNI, тащилась только из-за lazysodium). Проверка: grep по `gradle/libs.versions.toml` и всем `build.gradle.kts` файлам.
- [ ] #10 [hand] **Хитрая настройка `pickFirsts`** в `app/build.gradle.kts` (которая нужна была чтобы две библиотеки не конфликтовали при упаковке .so в APK) — удалена. Теперь одна .so на ABI, никаких костылей.
- [ ] #11 [hand] **Полная пересборка APK** командой `./gradlew :app:assembleMockBackendDebug` проходит без ошибок (BUILD SUCCESSFUL). Подтверждает что после миграции импортов компиляция чистая.
- [ ] #12 [hand] **Все автотесты зелёные** — `./gradlew test` (юнит-тесты + Robolectric-тесты, которые гоняют crypto-адаптеры на JVM без устройства). Подтверждает что миграция не сломала логику.
- [ ] #13 [hand] **Архитектурные fitness-тесты** (Spec011IsolationTest + Spec014IsolationTest, которые автоматически проверяют что в коде нет запрещённых импортов) обновлены — теперь они запрещают использовать lazysodium и старую стопку `com.launcher.api.crypto`. Зелёные. Это защитная сетка чтобы случайно не вернуть удалённое.
- [ ] #14 [hand] **APK похудел** — итоговый размер debug-APK меньше предыдущего на 3+ МБ (выкинули JNA весом ~5 МБ, добавили ничего). Замер до/после через `./gradlew :app:assembleMockBackendDebug` + сравнение размера выходного APK файла.
- [ ] #15 [hand] **Cold start приложения** (от тапа на иконку до первого экрана) на Xiaomi 11T не вырос по сравнению с прошлым замером (baseline 1260-1330 мс из TASK-7 verification). В идеале — стал короче на 200-500 мс, потому что ionspin через JNI быстрее запускается чем lazysodium через JNA.
<!-- AC:END -->
