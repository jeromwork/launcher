package com.launcher.preset.adapter

import android.os.Build
import com.launcher.preset.model.Vendor
import com.launcher.preset.port.VendorDetector

/**
 * TASK-73 (FR-001, Clarifications #2) — reads `Build.MANUFACTURER`, the only
 * place this project reads it (CLAUDE.md rule 1). Explicit alias table for
 * sub-brands that would otherwise silently degrade to [Vendor.GenericAndroid]
 * — Redmi/POCO are the most common sub-brands among the target audience.
 */
class AndroidVendorDetector : VendorDetector {

    private val aliasTable: Map<String, Vendor> = mapOf(
        "redmi" to Vendor.Xiaomi,
        "poco" to Vendor.Xiaomi,
    )

    override fun detect(): Vendor {
        val manufacturer = Build.MANUFACTURER.lowercase()
        aliasTable[manufacturer]?.let { return it }
        return Vendor.entries.find { it.name.equals(manufacturer, ignoreCase = true) }
            ?: Vendor.GenericAndroid
    }
}
