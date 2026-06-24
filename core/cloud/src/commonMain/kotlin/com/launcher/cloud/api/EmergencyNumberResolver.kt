package com.launcher.cloud.api

/**
 * Port «дай номер службы спасения для текущей страны / SIM».
 *
 * ## Invariants (из contracts/emergency-number-resolver-port.md)
 *
 *  - INV-1: Возвращает non-empty string.
 *  - INV-2: Returned string — valid telephone format (digits, possibly `+`, `*`, `#`).
 *  - INV-3: RU locale → один из `[102, 103, 112]`.
 *  - INV-4: US locale → `911`.
 *  - INV-5: EU locale → `112`.
 *  - INV-6: API < 29 (`getCurrentEmergencyNumberList` недоступен) → fallback на
 *    hardcoded map по country code.
 *  - INV-7: Не требует runtime permissions для fallback path.
 */
interface EmergencyNumberResolver {
    suspend fun getEmergencyNumber(): String
}
