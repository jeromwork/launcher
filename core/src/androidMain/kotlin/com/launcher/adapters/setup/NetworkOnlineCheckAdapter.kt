package com.launcher.adapters.setup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.launcher.api.setup.CheckStatus
import com.launcher.api.setup.Criticality
import com.launcher.api.setup.IntentSpec
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.Surface

/**
 * Spec 010 T038 — real adapter for «есть ли сеть?» check (FR-018).
 *
 * Reads the current default network's [NetworkCapabilities]. Returns
 * [CheckStatus.Ok] when the device has an active network with INTERNET
 * capability AND it's validated (NET_CAPABILITY_VALIDATED — passes the
 * captive-portal check на Android 6+).
 *
 * The ACCESS_NETWORK_STATE permission needed for this lives in the manifest
 * since spec 006 (normal permission, auto-granted). No runtime prompt.
 *
 * «Настроить» path → system Wi-Fi settings (let the user pick a network).
 */
class NetworkOnlineCheckAdapter(
    private val context: Context,
) : SetupCheck {

    override val id: String = "network_online"
    override val criticality: Criticality = Criticality.Required
    override val surfaces: Set<Surface> = setOf(Surface.Settings)

    override suspend fun check(): CheckStatus {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return CheckStatus.NotConfigured(reason = "connectivity_manager_unavailable")
        val network = cm.activeNetwork
            ?: return CheckStatus.NotConfigured(reason = "no_active_network")
        val caps = cm.getNetworkCapabilities(network)
            ?: return CheckStatus.NotConfigured(reason = "no_network_capabilities")
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return if (hasInternet && validated) {
            CheckStatus.Ok
        } else {
            CheckStatus.NotConfigured(reason = "network_not_validated")
        }
    }

    override fun resolveIntent(): IntentSpec =
        IntentSpec(
            category = "settings.wifi",
            action = "open",
        )
}
