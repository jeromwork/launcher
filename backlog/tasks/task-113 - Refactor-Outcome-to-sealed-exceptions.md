---
id: TASK-113
title: 'Refactor: Outcome<T, E> → sealed VaultException / typed exceptions across codebase'
status: Paused
assignee: []
created_date: '2026-07-07'
updated_date: '2026-07-07'
labels:
  - refactor
  - kotlin-idiom
  - phase-2
  - tech-debt
milestone: m-1
dependencies:
  - TASK-112
  - TASK-58
priority: medium
ordinal: 113000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

**Технический долг: рефакторинг существующего `Outcome<T, E>` sealed class в идиоматичные Kotlin exceptions.**

Сейчас в проекте используется паттерн `Outcome<T, E>` (Rust-style `Result<T, E>` через sealed class) — 143 файла, 358 occurrences, **три параллельные копии** `Outcome.kt` в разных модулях:
- `core/keys/src/commonMain/kotlin/family/keys/api/Outcome.kt`
- `core/push/src/commonMain/kotlin/family/push/api/Outcome.kt`
- `core/src/commonMain/kotlin/com/launcher/api/result/Outcome.kt`

**Проблема** (research в TASK-112 Session 2):
1. Kotlin stdlib — exceptions-based (Ktor / Compose / Okio / kotlinx.serialization all throw).
2. JetBrains **явно discourages** `kotlin.Result` для domain modeling.
3. Coroutines built on exception propagation + structured concurrency.
4. **UniFFI Kotlin bindings** для Rust libs (openmls, snow) генерируют throwing methods — не sealed Outcome. На FFI границе `Outcome` становится **лишним** маппингом.
5. `libsodium-kmp` (наш существующий) — throws `SodiumException`.
6. Junior Kotlin разработчики с Java-фоном — стопорятся на `Outcome` (не входит в стандартный словарь).

**Что делаем**:
- Заменяем `sealed class Outcome<T, E>` на throwing methods с `@Throws(SomeException::class)`.
- Каждый port получает **свою sealed exception hierarchy**, variants сгруппированы по природе ошибки (не по методу).
- Call sites: `outcome.getOrElse { ... }` / `when (outcome) { ... }` → `try/catch (e: TypedException) { ... }`.
- Три `Outcome.kt` — удаляются полностью.

**Что НЕ входит** (не в scope этого task'а):
- `KeyVault` port (TASK-112) — **уже проектируется с exceptions с первого коммита**, не требует миграции. Служит референсом «как правильно».
- Business logic changes — только рефакторинг error handling.
- New features — их нет в этом task'е.

## Зачем

**Ongoing tax**: каждый новый файл, использующий `Outcome`, — это ещё одна trench в паттерне, который **не соответствует** Kotlin, UniFFI, coroutines, stdlib. Чем дольше ждём — тем больше рефакторинг.

**Правильный момент для запуска** — когда **Rust FFI начнёт активно использоваться** (после TASK-58 MLS library formal Decision + начало implementation TASK-42 messenger / TASK-67 pairing v2). К тому моменту мы будем **чётко видеть**, где throw более естественен, и `Outcome` начнёт бросаться в глаза как чужеродная конструкция.

**Effort estimate**: 1-2 недели mechanical refactor:
- 143 файла touch (touch, не rewrite).
- ~1-5 строк изменить на файл (return type + call-site error handling).
- Три `Outcome.kt` консолидировать в **один момент удаления**.
- Fitness tests (`Spec009IsolationTest`, `Spec011IsolationTest`) — проверить, не проверяют ли типы через reflection.

## Что входит технически (для AI-агента)

**Список файлов для рефакторинга** (по слоям):

- **`core/keys/`** (~35 файлов): F-5 root key hierarchy, ключевой пользователь.
  - Public ports: `KeyRegistry`, `RecoveryKeyBackup`, `RootKeyManager`, `ConfigSaver`, `RemoteStorage`, `IdentityProof`, `EnvelopeBootstrap`, `AsyncConfigPushQueue`, `PassphrasePrompter`.
  - Internal ports: `RecipientResolver`, `PublicKeyDirectory`, `ConfigCipher2`, `EnvelopeStorage`.
  - Impls + fakes + tests.
- **`core/` (domain)** (~55 файлов): pairing, config, edit, link, sync, contacts, push, auth, identity ports.
- **`core/push/`** (~10 файлов): `PushTrigger`, `FcmTokenPublisher`, impls, fakes.
- **`core/crypto/`**: 1 файл (`DeviceIdentityRepository`).
- **`app/`** (~15 файлов): UI ViewModels, WorkManager, Firebase adapter'ы.
- **Tests** (~30 файлов): contract tests, roundtrip tests, isolation tests.

**Migration pattern**:

```kotlin
// BEFORE (Outcome-based)
interface KeyRegistry {
    suspend fun derive(stableId: StableId, purpose: String): Outcome<DerivedKey, RootKeyError>
}
sealed class RootKeyError {
    data object NotAvailable : RootKeyError()
    data class HardwareFailure(val cause: Throwable) : RootKeyError()
}
// Call site:
when (val result = keyRegistry.derive(stableId, "config")) {
    is Outcome.Success -> use(result.value)
    is Outcome.Failure -> handle(result.error)
}

// AFTER (sealed exception)
interface KeyRegistry {
    @Throws(RootKeyException::class)
    suspend fun derive(stableId: StableId, purpose: String): DerivedKey
}
sealed class RootKeyException(msg: String, cause: Throwable? = null)
    : Exception(msg, cause) {
    class NotAvailable : RootKeyException("root key not available")
    class HardwareFailure(cause: Throwable) : RootKeyException("hardware failure", cause)
}
// Call site:
try {
    val derived = keyRegistry.derive(stableId, "config")
    use(derived)
} catch (e: RootKeyException.NotAvailable) {
    handle(...)
}
```

**Consolidation**:
- Удалить `core/keys/src/commonMain/kotlin/family/keys/api/Outcome.kt`.
- Удалить `core/push/src/commonMain/kotlin/family/push/api/Outcome.kt`.
- Удалить `core/src/commonMain/kotlin/com/launcher/api/result/Outcome.kt`.

**Fitness rule after migration**:
- Import-lint rule: `import ... Outcome` анywhere в проекте → refuse. Prevents regression.

## Состояние

**Paused** — не начинать сейчас. Триггеры для перезапуска (любой):
1. TASK-58 MLS library Decision закрыта (openmls formal chose).
2. Начата implementation TASK-42 (Family group encryption) или TASK-67 v2 (Pairing feature) — оба тянут openmls / snow через UniFFI, там throwing methods → маппинг в Outcome становится явной болью.
3. Junior Kotlin dev onboarding — если жалуется на Outcome как первое препятствие.
4. Owner decision: «пора убрать».

**Не блокирует TASK-112** — `KeyVault` port проектируется сразу с exceptions, служит референсом «как правильно» до старта этой миграции.

**Не блокируется TASK-112** — `KeyVault` impl **внутри** может вызывать `Outcome`-based `KeyRegistry.derive(...)` (internal helper). Public API `KeyVault` = exceptions с первого коммита.

<!-- SECTION:DESCRIPTION:END -->
