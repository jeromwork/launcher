package com.launcher.app.safety

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.launcher.api.CapabilityState
import com.launcher.api.ControlMode
import com.launcher.api.SafetyCapability
import com.launcher.app.LauncherApplication
import com.launcher.app.R

class PermissionHubActivity : AppCompatActivity() {
    private lateinit var app: LauncherApplication
    private lateinit var requirementsContainer: LinearLayout
    private lateinit var modeLine: TextView
    private lateinit var summaryLine: TextView
    private lateinit var openNextButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_hub)
        app = application as LauncherApplication
        requirementsContainer = findViewById(R.id.requirements_container)
        modeLine = findViewById(R.id.control_mode_line)
        summaryLine = findViewById(R.id.control_mode_summary)
        openNextButton = findViewById(R.id.open_next_required_button)

        findViewById<Button>(R.id.mode_toggle_button).setOnClickListener {
            val next = when (app.core.controlModeStore.get()) {
                ControlMode.STRICT -> ControlMode.STANDARD
                ControlMode.STANDARD -> ControlMode.STRICT
            }
            app.core.controlModeStore.set(next)
            render()
        }
        findViewById<Button>(R.id.refresh_button).setOnClickListener { render() }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val snapshot = app.core.capabilitySnapshotResolver.resolve()
        modeLine.text = getString(R.string.permission_hub_mode, snapshot.controlMode.name)
        summaryLine.text = when (snapshot.controlMode) {
            ControlMode.STRICT -> getString(R.string.permission_hub_mode_strict)
            ControlMode.STANDARD -> getString(R.string.permission_hub_mode_standard)
        }
        requirementsContainer.removeAllViews()

        val requirements = listOf(
            requirement(SafetyCapability.DEFAULT_HOME, R.string.cap_default_home, PermissionHubNavigator.homeSettingsIntent()),
            requirement(
                SafetyCapability.ACCESSIBILITY_SERVICE,
                R.string.cap_accessibility,
                PermissionHubNavigator.accessibilitySettingsIntent(),
            ),
            requirement(SafetyCapability.USAGE_ACCESS, R.string.cap_usage_access, PermissionHubNavigator.usageAccessIntent()),
            requirement(SafetyCapability.OVERLAY, R.string.cap_overlay, PermissionHubNavigator.overlayIntent(packageName)),
            requirement(
                SafetyCapability.BATTERY_EXEMPTION,
                R.string.cap_battery,
                PermissionHubNavigator.batteryIntent(packageName),
            ),
            requirement(
                SafetyCapability.DEVICE_OWNER,
                R.string.cap_device_owner,
                PermissionHubNavigator.deviceAdminIntent(),
            ),
            requirement(SafetyCapability.LOCK_TASK, R.string.cap_lock_task, PermissionHubNavigator.securityIntent()),
            requirement(
                SafetyCapability.STATUS_BAR_RESTRICTION,
                R.string.cap_status_bar,
                PermissionHubNavigator.securityIntent(),
            ),
        )
        val firstMissingRequirement = requirements.firstOrNull { req ->
            val status = snapshot.statuses.first { it.capability == req.capability }
            status.state != CapabilityState.GRANTED
        }
        openNextButton.setOnClickListener {
            if (firstMissingRequirement == null) {
                Toast.makeText(this, getString(R.string.permission_hub_all_done), Toast.LENGTH_SHORT).show()
            } else {
                startActivitySafely(firstMissingRequirement.intent)
            }
        }

        requirements.forEach { req ->
            val row = LayoutInflater.from(this).inflate(
                R.layout.item_permission_requirement,
                requirementsContainer,
                false,
            )
            val title = row.findViewById<TextView>(R.id.requirement_title)
            val state = row.findViewById<TextView>(R.id.requirement_state)
            val reason = row.findViewById<TextView>(R.id.requirement_reason)
            val button = row.findViewById<Button>(R.id.requirement_open_button)
            title.text = getString(req.titleRes)

            val status = snapshot.statuses.first { it.capability == req.capability }
            state.text = when (status.state) {
                CapabilityState.GRANTED -> getString(R.string.cap_state_granted)
                CapabilityState.MISSING -> getString(R.string.cap_state_missing)
                CapabilityState.LIMITED -> getString(R.string.cap_state_limited)
            }
            reason.text = status.reasonCode ?: getString(R.string.cap_state_ok)
            button.setOnClickListener { startActivitySafely(req.intent) }

            if (status.state != CapabilityState.GRANTED) {
                app.core.safetyDiagnostics.capabilityMissing(
                    capability = req.capability,
                    mode = snapshot.controlMode,
                    state = status.state,
                    reasonCode = status.reasonCode,
                )
            }

            requirementsContainer.addView(row)
        }
    }

    private fun startActivitySafely(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            startActivity(PermissionHubNavigator.appDetailsIntent(packageName))
        }
    }

    private fun requirement(
        capability: SafetyCapability,
        titleRes: Int,
        intent: Intent,
    ): Requirement = Requirement(capability, titleRes, intent)

    private data class Requirement(
        val capability: SafetyCapability,
        val titleRes: Int,
        val intent: Intent,
    )
}
