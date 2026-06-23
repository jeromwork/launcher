package com.launcher.cloud.impl

import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.launcher.cloud.api.EmergencyNumberResolver
import java.util.Locale

/**
 * Android implementation. На API 29+ спрашивает у [TelephonyManager]
 * (`getCurrentEmergencyNumberList`) — учитывает SIM-карту, network operator.
 * На API < 29 (или если system вернул пустой список) — fallback на
 * hardcoded country-code map.
 */
class EmergencyNumberResolverImpl(
    private val telephonyManager: TelephonyManager,
    private val localeProvider: () -> Locale,
) : EmergencyNumberResolver {

    override suspend fun getEmergencyNumber(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val systemNumber = readSystemNumber(telephonyManager)
            if (!systemNumber.isNullOrEmpty()) {
                return systemNumber
            }
        }
        return fallbackForLocale(localeProvider())
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readSystemNumber(tm: TelephonyManager): String? = runCatching {
        val list: Map<Int, List<android.telephony.emergency.EmergencyNumber>>? =
            tm.emergencyNumberList
        list?.values
            ?.flatten()
            ?.firstOrNull()
            ?.number
    }.getOrNull()

    private fun fallbackForLocale(locale: Locale): String {
        val country = locale.country.uppercase(Locale.ROOT)
        return COUNTRY_MAP[country] ?: DEFAULT_NUMBER
    }

    companion object {
        private const val DEFAULT_NUMBER = "112"

        private val COUNTRY_MAP: Map<String, String> = mapOf(
            "RU" to "102",
            "BY" to "102",
            "KZ" to "102",
            "US" to "911",
            "CA" to "911",
            "JP" to "110",
            "AU" to "000",
            "CN" to "110",
        )
    }
}
