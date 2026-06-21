package family.push.api

/**
 * T012 — Кому Worker отправляет push. Per spec 019 FR-022, data-model.md §TargetScope.
 *
 * Per CLAUDE.md rule 5: enum-as-wire-format → renaming variants = breaking change.
 * Additive — добавлять новые varianty можно (старые читатели game over старого
 * каста просто получают null → reject).
 */
enum class TargetScope(val wireValue: String) {

    /** Только устройства самого ownerUid. Используется для self-sync (когда у одного
     * пользователя два своих устройства). */
    OwnDevices("own-devices"),

    /** Own devices + grant-holders' devices. Используется по умолчанию ConfigSaver'ом
     * — после save admin'ом всем «семейным помощникам» triggers refresh. */
    OwnAndGrants("own-and-grants"),

    // Future: SpecificUid("specific-uid") для V-2 messenger direct message.
    ;

    companion object {
        fun fromWireOrNull(value: String): TargetScope? =
            values().firstOrNull { it.wireValue == value }
    }
}
