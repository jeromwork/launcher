# Contract: `KeyRegistry` port v1

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **FRs**: FR-004, FR-005, FR-023, FR-031

Port для управления DEK'ами (data encryption keys) под единым RootKey.

---

## Kotlin declaration (commonMain)

```kotlin
package com.launcher.api.keys.api

import com.launcher.api.auth.AuthIdentity

public interface KeyRegistry {

    /**
     * Регистрирует новый DEK под текущей identity. DEK-material передаётся plaintext,
     * хранится только в encrypted (wrapped под RootKey) форме.
     *
     * @param name стабильное глобальное имя DEK (e.g. "config-cipher-aead-v1"). Имя должно быть
     *             app-agnostic — без package prefix (FR-023).
     * @param dekMaterial 32 bytes (или другой длины — определяется потребителем DEK).
     *
     * Idempotency: повторный registerDek с тем же name под той же identity — overwrites
     * существующую запись (use-case: rotation в future spec'ах).
     */
    public suspend fun registerDek(
        name: String,
        dekMaterial: ByteArray,
    ): Outcome<Unit, KeyRegistryError>

    /**
     * Возвращает plaintext DEK material в RAM. Caller обязан zeroize ByteArray после use.
     *
     * @return Outcome.Success(material) при существующем DEK, иначе KeyRegistryError.NotFound.
     */
    public suspend fun getDek(name: String): Outcome<ByteArray, KeyRegistryError>

    /**
     * @return true если DEK с таким name зарегистрирован под текущей identity.
     */
    public suspend fun hasDek(name: String): Boolean
}

public sealed class KeyRegistryError {
    public data object NotFound : KeyRegistryError()
    public data class StorageFailure(val cause: Throwable) : KeyRegistryError()
    public data class UnknownDek(val name: String) : KeyRegistryError()
    public data object RootKeyUnavailable : KeyRegistryError() // RootKey не загружен — recovery нужен
}
```

`Outcome` — re-uses domain-level result type из existing `com.launcher.api.util.Outcome` (или эквивалент в проекте).

---

## Semantics

### registerDek
- **Pre**: RootKey доступен в Keystore (через `RootKeyManager.getOrCreate`). Иначе → `RootKeyUnavailable`.
- **Post**: WrappedDek (см. data-model.md §1.3) персистентно сохранён в local storage.
- **Idempotent**: повторный register с тем же name — overwrite.
- **Errors**: `StorageFailure` (DataStore exception), `RootKeyUnavailable`.

### getDek
- **Pre**: RootKey доступен.
- **Post**: возвращён plaintext material. Caller responsible for zeroize.
- **Errors**: `NotFound` (нет такого DEK), `StorageFailure`, `RootKeyUnavailable`.

### hasDek
- Cheap operation, без unwrap (только проверка наличия storage entry).

---

## Storage schema (internal — не часть public API)

DataStore preference `family-keys` под key `dek-${uid}-${name}` хранит **JSON-сериализованный `WrappedDek`** (data-model.md §1.3):

```json
{
  "name": "config-cipher-aead-v1",
  "ciphertext": "base64...",
  "nonce": "base64...",
  "algorithm": "xchacha20poly1305-v1",
  "schemaVersion": 1
}
```

`schemaVersion` per DEK record (rule 5).

### Backward-compat rules
- Adding new `name` — additive, не bump'ит `schemaVersion`.
- Renaming `name` или удаление — требует bump `schemaVersion` + migration code.
- Adding optional field в `WrappedDek` — additive с default value.
- Removing / renaming field в `WrappedDek` — bump `schemaVersion`.

---

## Invariants

- **INV-A**: `name` для одного UID уникален. Multiple identities (UID1, UID2) могут иметь DEKs с одинаковым именем — изолированы через UID prefix в storage key (FR-031, R-7).
- **INV-B**: DEK material plaintext **никогда** не persisted — только wrapped.
- **INV-C**: При unwrap RootKey'а через recovery — все DEKs автоматически re-accessible (User Story 3).

---

## Tests required

- `KeyRegistryTest.kt`:
  - `registerDek → getDek` returns same material (roundtrip).
  - `registerDek` с тем же name дважды — overwrites.
  - `getDek` для несуществующего name → `NotFound`.
  - `hasDek` корректно reflects state.
  - 100+ DEKs зарегистрированы без degradation (SC-008).
- Contract fixture: `multi-dek-keyregistry-v1.json` — pre-built set из 3 wrapped DEK для backward-compat read test.
- Fake implementation `FakeKeyRegistry` для consumer tests.

---

## Краткое резюме

Контракт реестра ключей — три операции (`registerDek` / `getDek` / `hasDek`) и пять видов ошибок. Хранение — JSON под защитой RootKey'а в local storage; UID — часть ключа хранения, поэтому ключи разных Google-аккаунтов не пересекаются. Поведение протестировано round-trip'ом, контрактным fixture'ом и тестом на 100+ записей (SC-008).
