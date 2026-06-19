# Contract: AuthProvider Port

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Date**: 2026-06-18
**Type**: Domain port contract (Kotlin interface).
**Source**: `core/domain/auth/AuthProvider.kt`.

---

## Purpose

`AuthProvider` — единственная публичная точка входа в identity layer для всех consumer'ов (F-5 ConfigCipher, S-2 PairingFlow, S-8 Config Sync, S-9 Health Monitoring, P-6 Recovery, billing).

## Interface

```kotlin
package family.launcher.domain.auth

import kotlinx.coroutines.flow.Flow

interface AuthProvider {
    val currentUser: Flow<AuthIdentity?>
    suspend fun signIn(): Outcome<AuthIdentity, AuthError>
    suspend fun signOut()
}
```

## Semantic invariants

### `currentUser: Flow<AuthIdentity?>`

| Invariant | Description | Verification |
|-----------|-------------|--------------|
| **Initial null** | Flow MUST эмитить `null` first emission within 100ms of Application start. | `ColdStartLatencyTest` |
| **Reactive updates** | Flow MUST эмитить новое value при: sign-in success / sign-out / refresh failure. | Contract test `AuthProviderContractTest` |
| **Same identity invariant** | Если currentUser was `AuthIdentity(stableId=X, ...)`, refresh успешен, next emission MUST иметь same `stableId`. | Property test |
| **Refresh failure → null** | Если token refresh failed (network / revoked) — currentUser MUST эмитить `null` через `signOut()` semantics. | Contract test |
| **No spurious emissions** | Flow MUST NOT эмитить same value подряд (distinct until changed). | Contract test |

### `signIn(): Outcome<AuthIdentity, AuthError>`

| Invariant | Description | Verification |
|-----------|-------------|--------------|
| **Success → emit** | После `Outcome.Success(identity)` returned, `currentUser` MUST эмитить same identity (within 100ms). | Contract test |
| **Dedup parallel** | Параллельные вызовы `signIn()` MUST deduplicate: only one active provider interaction в момент. | Property test (1000 iterations, parallel coroutines) |
| **Adapter-scoped** | `signIn()` survives caller cancellation (research.md §R2). Result delivered через `currentUser` flow если caller cancelled, через return value если caller awaits. | Integration test `InFlightSignInRotationTest` |
| **No side-effects beyond identity** | `signIn()` MUST NOT trigger config-sync, push-registration, conflict-resolution, или other consumer-side logic (per clarification Q7). | Property test (mock-out all consumers, verify zero calls) |
| **Cancelled — no error toast** | `Outcome.Failure(AuthError.Cancelled)` MUST be returned silently (per clarification Q6). UI policy enforced separately. | Spec acceptance scenario US 2 #5 |

### `signOut()`

| Invariant | Description | Verification |
|-----------|-------------|--------------|
| **Idempotent** | Повторный вызов = no-op. No exceptions. | Property test |
| **Local cache untouched** | `signOut()` MUST NOT delete локальный config cache, contacts, themes, tiles (per clarification Q3). | Integration test `SignOutLocalCachePreservationTest` |
| **Emit null** | After completion, `currentUser` MUST эмитить `null`. | Contract test |
| **SessionStore cleared** | Internal: `SessionStore.current()` MUST return null after `signOut()` completes. | Internal test via adapter test boundary |
| **No background sync after** | After `signOut()`, F-4 MUST stop эмитить identity → consumer'ы видят через flow и перестают sync. F-4 не управляет consumer state directly. | Contract test |

## Consumer expectations

Consumer code MUST:
- Subscribe `currentUser` flow для observing identity changes (reactive pattern).
- Handle `AuthIdentity?` nullable: `null` = not signed in (включая cold-start initial state).
- Use `AuthIdentity.stableId` как primary key для their own data records (F-5 keys, S-2 delegations, etc.).
- **NEVER** call `signIn()` или `signOut()` programmatically as side-effect of other operations (UI-only triggers).
- **NEVER** import vendor types (`com.google.*`, `com.firebase.*`, `androidx.credentials.*`) — Detekt enforces.

Consumer code MUST NOT:
- Expect immediate non-null `currentUser` at Application start. Initial null is required (cold-start invariant FR-035).
- Cache identity locally beyond `currentUser` flow — flow is source of truth.
- Assume specific provider (Google) behind `AuthProvider` — provider-swap fitness function will fail if assumed.
- Read `SessionRecord` (internal type — Detekt blocks import).

## Provider-swap fitness function

`ProviderSwapFitnessTest` verifies port contract:

1. Build DI graph A: `AuthProvider` = `GoogleSignInAuthAdapter` (with mocked Credential Manager + Firebase).
2. Build DI graph B: `AuthProvider` = `FakeAuthAdapter` (pre-seeded users).
3. Run consumer test suite (F-5 ConfigCipher placeholder, S-2 PairingFlow placeholder, etc.) на обоих graphs.
4. Assert: identical pass rate. Behavior отличается только identity values.

Если test fails — значит:
- Consumer code зависит от vendor-specific behavior (нарушение rule 2 ACL), OR
- AuthProvider port behavior different между adapters (contract violation).

## Versioning

`AuthProvider` interface — public domain API. Breaking changes (renaming methods, removing methods, changing signatures) — major version bump consumer-spec'ам.

Additive changes (new methods, new sealed `AuthError` cases) — minor (consumer'ы могут ignore новые методы; `else` branch покрывает новые AuthError cases).

Currently: **v1** (initial release).

## Reference implementations

- **Real**: `app/androidMain/auth/GoogleSignInAuthAdapter.kt` (full Google + Firebase + identity-links flow).
- **Fake**: `core/commonTest/auth/FakeAuthAdapter.kt` (pre-seeded users + simulators for error paths).

Future implementations (additive, separate spec'и):
- `PhoneAuthAdapter` (phone OTP).
- `EmailPasswordAuthAdapter` (email/password).
- `AppleSignInAuthAdapter` (iOS).
- `OwnServerJwtAuthAdapter` (post-cutover replacement для GoogleSignInAuthAdapter).

## TL;DR для не-разработчика

`AuthProvider` — это **программный интерфейс**, через который все остальные части приложения узнают «кто пользователь». 

**Три действия**:
1. **Подписаться** на `currentUser` чтобы получать уведомления когда пользователь вошёл / вышел.
2. **Войти** — попросить F-4 показать окно Google.
3. **Выйти** — попросить F-4 отключить синхронизацию с сервером (локальные данные остаются).

**Главные правила** (которые проверяются автоматически):
- Никто кроме F-4 не должен знать про Firebase / Google в коде.
- Войти и выйти можно сколько угодно раз — будет одинаковый результат (идемпотентно).
- Если повернули экран во время входа — вход всё равно завершится (специальная архитектурная защита).
- После входа F-4 НЕ вызывает другие части приложения напрямую — они сами подписаны на уведомления и реагируют.

**Тест-доказательство**: специальный тест `ProviderSwapFitnessTest` подменяет реальный Google адаптер на заглушку и прогоняет тесты всех потребителей. Если они работают — значит правила соблюдены.
