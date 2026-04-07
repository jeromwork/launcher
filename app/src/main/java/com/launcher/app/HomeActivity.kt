package com.launcher.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.launcher.api.CatalogSnapshot
import com.launcher.api.CommunicationActionType
import com.launcher.api.CommunicationWarningCode
import com.launcher.api.DispatchResult
import com.launcher.api.EffectiveProfile
import com.launcher.api.ReturnRestoreOutcome
import com.launcher.api.WhatsAppHandoffRequest
import com.launcher.app.communication.CommunicationTileUiModel
import com.launcher.app.communication.WarningState
import com.launcher.app.communication.toUiModel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Thin home shell: observes profile and catalog snapshots from Core; no catalog business logic.
 */
class HomeActivity : AppCompatActivity() {
    private lateinit var core: com.launcher.core.LauncherCore
    private var activeTile: CommunicationTileUiModel? = null
    private var selectedAction: CommunicationActionType? = null

    private val telegramContactName: String by lazy { getString(R.string.telegram_contact_name_value) }
    private val telegramUsernameRaw: String by lazy { getString(R.string.telegram_contact_username_value) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        core = (application as LauncherApplication).core
        val profileLine = findViewById<TextView>(R.id.profile_line)
        val catalogLine = findViewById<TextView>(R.id.catalog_line)
        setupCommunicationUi()
        setupTelegramUi()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    core.profileEngine.effectiveProfile.collect { eff ->
                        profileLine.text = formatProfileLine(eff)
                    }
                }
                launch {
                    core.appIndex.snapshot.collect { snap ->
                        catalogLine.text = formatCatalogLine(snap)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        when (core.actionDispatcher.restoreOnHomeEntry(HOME_SURFACE_REF)) {
            ReturnRestoreOutcome.RESTORED_NEAREST_STABLE_HOME -> {
                showWarning(
                    WarningState(
                        code = CommunicationWarningCode.RESTORE_FALLBACK_USED,
                        title = getString(R.string.comm_warning_restore_title),
                        message = getString(R.string.comm_warning_restore_body),
                    ),
                )
            }
            else -> Unit
        }
    }

    private fun formatProfileLine(eff: EffectiveProfile): String =
        getString(
            R.string.home_profile_line,
            eff.snapshot.id,
            eff.profileGeneration,
            eff.degradation.reasonCodes.joinToString(),
        )

    private fun formatCatalogLine(snap: CatalogSnapshot): String =
        getString(R.string.home_catalog_line, snap.generation, snap.entries.size)

    private fun setupCommunicationUi() {
        val contactName = findViewById<TextView>(R.id.contact_name)
        val callButton = findViewById<Button>(R.id.call_button)
        val videoButton = findViewById<Button>(R.id.video_button)
        val confirmationRoot = findViewById<View>(R.id.confirmation_root)
        val confirmationBody = findViewById<TextView>(R.id.confirmation_body)
        val confirmationSuccessCue = findViewById<TextView>(R.id.confirmation_success_cue)
        val confirmButton = findViewById<Button>(R.id.confirm_button)
        val cancelButton = findViewById<Button>(R.id.cancel_button)
        val warningDismiss = findViewById<Button>(R.id.warning_dismiss_button)
        callButton.contentDescription = getString(R.string.comm_action_call)
        videoButton.contentDescription = getString(R.string.comm_action_video)
        confirmButton.contentDescription = getString(R.string.comm_confirm)
        cancelButton.contentDescription = getString(R.string.comm_cancel)
        warningDismiss.contentDescription = getString(R.string.comm_warning_dismiss)

        val firstEntry = core.actionDispatcher.loadCommunicationEntries().firstOrNull()
        if (firstEntry != null) {
            val nameRes = resources.getIdentifier(
                firstEntry.displayNameKey,
                "string",
                packageName,
            )
            val displayName = if (nameRes != 0) getString(nameRes) else firstEntry.contactRef
            activeTile = firstEntry.toUiModel(displayName = displayName)
            contactName.text = displayName
        }

        callButton.setOnClickListener {
            openConfirmation(CommunicationActionType.CALL)
        }
        videoButton.setOnClickListener {
            openConfirmation(CommunicationActionType.VIDEO)
        }
        cancelButton.setOnClickListener {
            confirmationRoot.visibility = View.GONE
            confirmationSuccessCue.visibility = View.GONE
            selectedAction = null
        }
        confirmButton.setOnClickListener {
            val tile = activeTile ?: return@setOnClickListener
            val action = selectedAction ?: return@setOnClickListener
            confirmationSuccessCue.visibility = View.VISIBLE
            val request = WhatsAppHandoffRequest(
                tileId = tile.tileId,
                contactRef = tile.contactRef,
                actionType = action,
                actionCycleId = UUID.randomUUID().toString(),
                homeSurfaceRef = HOME_SURFACE_REF,
            )
            val result = core.actionDispatcher.dispatch(
                com.launcher.api.ActionRequest.WhatsAppHandoff(request),
            )
            if (result is DispatchResult.WhatsApp) {
                when (result.outcome) {
                    com.launcher.api.WhatsAppHandoffResult.LAUNCH_STARTED -> {
                        // Success cue is visible before external transition.
                    }
                    com.launcher.api.WhatsAppHandoffResult.WHATSAPP_UNAVAILABLE -> showWarning(
                        WarningState(
                            code = CommunicationWarningCode.WHATSAPP_UNAVAILABLE,
                            title = getString(R.string.comm_warning_unavailable_title),
                            message = getString(R.string.comm_warning_unavailable_body),
                        ),
                    )
                    com.launcher.api.WhatsAppHandoffResult.ACTION_NOT_SUPPORTED -> showWarning(
                        WarningState(
                            code = CommunicationWarningCode.ACTION_NOT_SUPPORTED,
                            title = getString(R.string.comm_warning_action_title),
                            message = getString(R.string.comm_warning_action_body),
                        ),
                    )
                    else -> showWarning(
                        WarningState(
                            code = CommunicationWarningCode.HANDOFF_LAUNCH_FAILED,
                            title = getString(R.string.comm_warning_launch_title),
                            message = getString(R.string.comm_warning_launch_body),
                        ),
                    )
                }
            }
        }
        warningDismiss.setOnClickListener {
            hideWarning()
        }
        confirmationBody.text = getString(
            R.string.comm_confirmation_body,
            getString(R.string.comm_action_call),
            activeTile?.displayName ?: "",
        )
    }

    private fun setupTelegramUi() {
        val telegramContactNameView = findViewById<TextView>(R.id.telegram_contact_name)
        val telegramContactUsernameView = findViewById<TextView>(R.id.telegram_contact_username)
        val telegramButton = findViewById<Button>(R.id.telegram_button)
        val handoffStatus = findViewById<TextView>(R.id.handoff_status)

        telegramContactNameView.text = telegramContactName
        telegramContactUsernameView.text = telegramUsernameRaw
        telegramButton.setOnClickListener {
            handoffStatus.text = openTelegramChat(rawUsername = telegramUsernameRaw)
        }
    }

    private fun openConfirmation(action: CommunicationActionType) {
        val tile = activeTile ?: return
        selectedAction = action
        val confirmationRoot = findViewById<View>(R.id.confirmation_root)
        val confirmationBody = findViewById<TextView>(R.id.confirmation_body)
        val actionLabel = when (action) {
            CommunicationActionType.CALL -> getString(R.string.comm_action_call)
            CommunicationActionType.VIDEO -> getString(R.string.comm_action_video)
        }
        confirmationBody.text = getString(
            R.string.comm_confirmation_body,
            actionLabel,
            tile.displayName,
        )
        findViewById<TextView>(R.id.confirmation_success_cue).visibility = View.GONE
        confirmationRoot.visibility = View.VISIBLE
    }

    private fun showWarning(state: WarningState) {
        findViewById<TextView>(R.id.warning_title).text = state.title
        findViewById<TextView>(R.id.warning_body).text = state.message
        findViewById<View>(R.id.warning_root).visibility = View.VISIBLE
    }

    private fun hideWarning() {
        findViewById<View>(R.id.warning_root).visibility = View.GONE
    }

    private fun openTelegramChat(rawUsername: String): String {
        val username = normalizeTelegramUsername(rawUsername)
        if (!isValidTelegramUsername(username)) {
            return getString(R.string.status_invalid_telegram_contact)
        }

        val tgIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("tg://resolve?domain=$username"),
        ).apply { setPackage(TELEGRAM_PACKAGE) }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://t.me/$username"),
        ).apply { setPackage(TELEGRAM_PACKAGE) }

        val intentToLaunch = when {
            tgIntent.resolveActivity(packageManager) != null -> tgIntent
            webIntent.resolveActivity(packageManager) != null -> webIntent
            else -> return getString(R.string.status_telegram_not_found)
        }

        return runCatching {
            startActivity(intentToLaunch)
            getString(R.string.status_opening_telegram)
        }.getOrElse {
            getString(R.string.status_telegram_not_found)
        }
    }

    private fun normalizeTelegramUsername(rawUsername: String): String =
        rawUsername
            .trim()
            .removePrefix("@")

    private fun isValidTelegramUsername(username: String): Boolean {
        if (username.length !in TELEGRAM_USERNAME_MIN_LENGTH..TELEGRAM_USERNAME_MAX_LENGTH) {
            return false
        }
        return username.all { it.isLetterOrDigit() || it == '_' }
    }

    companion object {
        private const val HOME_SURFACE_REF = "home_main"
        private const val TELEGRAM_PACKAGE = "org.telegram.messenger"
        private const val TELEGRAM_USERNAME_MIN_LENGTH = 5
        private const val TELEGRAM_USERNAME_MAX_LENGTH = 32
    }
}
