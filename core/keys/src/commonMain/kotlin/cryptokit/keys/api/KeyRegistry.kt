package cryptokit.keys.api

/**
 * Порт для derivation DEK'ов из root key (FR-002, data-model.md §6).
 *
 * **Derivation pattern**: `DerivedKey = HKDF(rootKey.bytes, salt=stableId.encodeToByteArray(), info=purpose.encodeToByteArray())`
 * Разные `purpose` → разные ключи, изолированные друг от друга.
 *
 * **Namespace isolation** (FR-031): все операции namespace'ированы по [stableId].
 * Wipe одного stableId НЕ затрагивает namespace'ы других identity.
 *
 * **Purpose registry** (FR-007 inline TODO):
 * // TODO(FR-007, future-spec): когда N>5 purposes — ввести sealed class Purpose вместо
 * //   строкового purpose, чтобы compiler enforced exhaustiveness. До 5 purposes —
 * //   строковые константы в caller code достаточны.
 *
 * **Exit ramp** (TASK-41):
 * // TODO(TASK-41, server-roadmap): при переходе на Go microservice — этот порт
 * //   адаптируется под remote derivation API без изменения domain interface.
 *
 * @see DerivedKey
 * @see StableId
 */
interface KeyRegistry {
    /**
     * Выводит [DerivedKey] для пары ([stableId], [purpose]).
     * Детерминировано: те же входы → тот же ключ.
     *
     * @param stableId Provider-агностичный UUID идентификатор пользователя.
     * @param purpose Строковое имя цели (например `"config"`, `"contacts"`, `"media"`).
     * @return Derived key, содержащий 32 байта HKDF output.
     */
    suspend fun derive(stableId: StableId, purpose: String): Outcome<DerivedKey, RootKeyError>

    /**
     * Удаляет все производные ключи для данного [stableId] (cascade wipe при Sign-Out / Fallback).
     * После вызова [list] для того же stableId должен вернуть пустой список (SC-012).
     */
    suspend fun wipeAll(stableId: StableId): Outcome<Unit, RootKeyError>

    /**
     * Перечисляет все зарегистрированные purpose'ы для данного [stableId].
     * Используется для аудита и проверки корректности wipe (SC-012).
     *
     * @return Список purpose строк, может быть пустым если stableId не известен или wipe выполнен.
     */
    suspend fun list(stableId: StableId): Outcome<List<String>, RootKeyError>
}
