package com.launcher.core.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.launcher.api.ActionRequest
import com.launcher.api.BlockReason
import com.launcher.api.CommunicationActionType
import com.launcher.api.DispatchResult
import com.launcher.api.ReturnContextRecord
import com.launcher.api.ReturnRestoreOutcome
import com.launcher.api.SystemSettingsTarget
import com.launcher.api.WhatsAppHandoffResult
import com.launcher.core.catalog.AppIndex
import com.launcher.core.events.CommunicationDiagnostics

/**
 * Single pipeline for launch and settings intents (contracts/actions.md).
 */
class ActionDispatcher(
    private val context: Context,
    private val appIndex: AppIndex,
    private val actionCycleGuard: ActionCycleGuard = ActionCycleGuard(),
    private val returnContextStore: ReturnContextStore = ReturnContextStore(context),
    private val configValidator: CommunicationConfigValidator = CommunicationConfigValidator(context),
    private val diagnostics: CommunicationDiagnostics = CommunicationDiagnostics(),
    private val allowedAppsGate: AllowedAppsGate = AllowedAppsGate { com.launcher.api.AllowedAppsPolicy.permissive() },
    private val launchabilityResolver: WhatsAppLaunchabilityResolver = WhatsAppLaunchabilityResolver(
        context = context,
        configValidator = configValidator,
    ),
) {
    private val restoreEvaluator = RestoreOutcomeEvaluator(knownHomeSurfaceRef = DEFAULT_HOME_SURFACE_REF)

    fun dispatch(request: ActionRequest): DispatchResult {
        return when (request) {
            is ActionRequest.OpenApplication -> dispatchOpenApplication(request.catalogStableKey)
            is ActionRequest.OpenSystemSettings -> dispatchSettings(request.target)
            is ActionRequest.WhatsAppHandoff -> dispatchWhatsAppHandoff(request.request)
        }
    }

    fun loadCommunicationEntries() = configValidator.loadValidatedEntries()

    fun restoreOnHomeEntry(homeSurfaceRef: String = DEFAULT_HOME_SURFACE_REF): ReturnRestoreOutcome {
        val record = returnContextStore.load()
        val outcome = if (homeSurfaceRef == DEFAULT_HOME_SURFACE_REF) {
            restoreEvaluator.evaluate(record)
        } else {
            RestoreOutcomeEvaluator(homeSurfaceRef).evaluate(record)
        }
        when (outcome) {
            ReturnRestoreOutcome.RESTORED_EXACT_HOME -> diagnostics.restoreSuccess(record?.actionCycleRef)
            ReturnRestoreOutcome.RESTORED_NEAREST_STABLE_HOME -> diagnostics.restoreFallback(
                cycleRef = record?.actionCycleRef,
                reasonCode = "nearest_stable_home",
            )
            ReturnRestoreOutcome.NO_VALID_CONTEXT -> Unit
        }
        returnContextStore.clear()
        record?.let { actionCycleGuard.clearByCycle(it.actionCycleRef) }
        return outcome
    }

    private fun dispatchOpenApplication(stableKey: String): DispatchResult {
        if (stableKey.isBlank()) {
            return DispatchResult.BlockedByPolicy(BlockReason.INVALID_REQUEST)
        }
        val entry = appIndex.findEntry(stableKey)
            ?: return DispatchResult.BlockedByPolicy(BlockReason.NOT_IN_CATALOG)
        if (!entry.isLaunchable) {
            return DispatchResult.BlockedByPolicy(BlockReason.NOT_LAUNCHABLE)
        }
        if (!allowedAppsGate.isAllowed(stableKey)) {
            return DispatchResult.BlockedByPolicy(BlockReason.PERMISSION_OR_POLICY)
        }
        val pm = context.packageManager
        val launch = pm.getLaunchIntentForPackage(stableKey)
            ?: return DispatchResult.Failure("missing_launch_intent")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(launch)
        }.onFailure {
            return DispatchResult.BlockedByPolicy(BlockReason.PERMISSION_OR_POLICY)
        }
        return DispatchResult.Ok
    }

    private fun dispatchSettings(target: SystemSettingsTarget): DispatchResult {
        val intent = when (target) {
            SystemSettingsTarget.General -> Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                return DispatchResult.BlockedByPolicy(BlockReason.PERMISSION_OR_POLICY)
            }
        return DispatchResult.Ok
    }

    private fun dispatchWhatsAppHandoff(request: com.launcher.api.WhatsAppHandoffRequest): DispatchResult {
        if (
            request.tileId.isBlank() ||
            request.contactRef.isBlank() ||
            request.actionCycleId.isBlank() ||
            request.homeSurfaceRef.isBlank()
        ) {
            diagnostics.launchFailed(
                tileRef = request.tileId,
                actionType = request.actionType,
                cycleRef = request.actionCycleId,
                reasonCode = "invalid_request",
            )
            return DispatchResult.BlockedByPolicy(BlockReason.INVALID_REQUEST)
        }
        if (!actionCycleGuard.beginCycle(request.tileId, request.actionCycleId)) {
            diagnostics.launchFailed(
                tileRef = request.tileId,
                actionType = request.actionType,
                cycleRef = request.actionCycleId,
                reasonCode = "duplicate_cycle",
            )
            return DispatchResult.WhatsApp(WhatsAppHandoffResult.REJECTED_DUPLICATE_CYCLE)
        }

        when (launchabilityResolver.canLaunch(request.contactRef, request.actionType)) {
            WhatsAppLaunchability.WHATSAPP_UNAVAILABLE -> {
                actionCycleGuard.clearByCycle(request.actionCycleId)
                diagnostics.launchFailed(
                    tileRef = request.tileId,
                    actionType = request.actionType,
                    cycleRef = request.actionCycleId,
                    reasonCode = "whatsapp_unavailable",
                )
                return DispatchResult.WhatsApp(WhatsAppHandoffResult.WHATSAPP_UNAVAILABLE)
            }
            WhatsAppLaunchability.ACTION_NOT_SUPPORTED -> {
                actionCycleGuard.clearByCycle(request.actionCycleId)
                diagnostics.configInvalid(
                    contactRef = request.contactRef,
                    actionType = request.actionType,
                    reasonCode = "action_not_supported",
                )
                return DispatchResult.WhatsApp(WhatsAppHandoffResult.ACTION_NOT_SUPPORTED)
            }
            WhatsAppLaunchability.AVAILABLE -> Unit
        }
        if (!allowedAppsGate.isAllowed(WHATSAPP_PACKAGE)) {
            actionCycleGuard.clearByCycle(request.actionCycleId)
            diagnostics.launchFailed(
                tileRef = request.tileId,
                actionType = request.actionType,
                cycleRef = request.actionCycleId,
                reasonCode = "blocked_by_policy",
            )
            return DispatchResult.WhatsApp(WhatsAppHandoffResult.LAUNCH_BLOCKED_BY_POLICY)
        }

        returnContextStore.save(
            ReturnContextRecord(
                initiatingTileRef = request.tileId,
                homeSurfaceRef = request.homeSurfaceRef,
                actionCycleRef = request.actionCycleId,
                savedAtEpochMs = System.currentTimeMillis(),
            ),
        )

        val launch = resolveWhatsAppLaunchIntent(request) ?: run {
                actionCycleGuard.clearByCycle(request.actionCycleId)
                diagnostics.launchFailed(
                    tileRef = request.tileId,
                    actionType = request.actionType,
                    cycleRef = request.actionCycleId,
                    reasonCode = "missing_launch_intent",
                )
                return DispatchResult.WhatsApp(WhatsAppHandoffResult.LAUNCH_FAILED)
            }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launch.putExtra("launcher_contact_ref", request.contactRef)
        launch.putExtra("launcher_action_type", request.actionType.name)
        launch.putExtra("launcher_cycle_ref", request.actionCycleId)

        runCatching { context.startActivity(launch) }.onFailure {
            actionCycleGuard.clearByCycle(request.actionCycleId)
            diagnostics.launchFailed(
                tileRef = request.tileId,
                actionType = request.actionType,
                cycleRef = request.actionCycleId,
                reasonCode = "launch_failed",
            )
            return DispatchResult.WhatsApp(WhatsAppHandoffResult.LAUNCH_FAILED)
        }
        diagnostics.launchConfirmed(request.tileId, request.actionType, request.actionCycleId)
        return DispatchResult.WhatsApp(WhatsAppHandoffResult.LAUNCH_STARTED)
    }

    private fun resolveWhatsAppLaunchIntent(
        request: com.launcher.api.WhatsAppHandoffRequest,
    ): Intent? {
        val phoneDigits = request.contactRef.filter { it.isDigit() }
        if (phoneDigits.isNotEmpty()) {
            val deepLink = when (request.actionType) {
                CommunicationActionType.CALL -> "whatsapp://call?phone=$phoneDigits"
                CommunicationActionType.VIDEO -> "whatsapp://video_call?phone=$phoneDigits"
            }
            val directCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                setPackage(WHATSAPP_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (directCallIntent.resolveActivity(context.packageManager) != null) {
                return directCallIntent
            }
            val chatFallbackIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$phoneDigits"),
            ).apply {
                setPackage(WHATSAPP_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (chatFallbackIntent.resolveActivity(context.packageManager) != null) {
                return chatFallbackIntent
            }
        }

        return context.packageManager.getLaunchIntentForPackage(WHATSAPP_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("launcher_contact_ref", request.contactRef)
            putExtra("launcher_action_type", request.actionType.name)
            putExtra("launcher_cycle_ref", request.actionCycleId)
        }
    }

    companion object {
        private const val WHATSAPP_PACKAGE: String = "com.whatsapp"
        private const val DEFAULT_HOME_SURFACE_REF: String = "home_main"
    }
}
