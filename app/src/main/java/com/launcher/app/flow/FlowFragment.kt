package com.launcher.app.flow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.launcher.api.ActionRequest
import com.launcher.api.CommunicationActionType
import com.launcher.api.CommunicationWarningCode
import com.launcher.api.DispatchResult
import com.launcher.api.FlowDescriptor
import com.launcher.api.SlotAction
import com.launcher.api.SlotDescriptor
import com.launcher.api.WhatsAppHandoffRequest
import com.launcher.api.WhatsAppHandoffResult
import com.launcher.app.LauncherApplication
import com.launcher.app.R
import com.launcher.app.communication.WarningState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class FlowFragment : Fragment() {

    private var flowId: String = ""
    private var pendingSlot: SlotDescriptor? = null
    private var pendingAction: CommunicationActionType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        flowId = arguments?.getString(ARG_FLOW_ID) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_flow, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupOverlays(view)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val core = (requireActivity().application as LauncherApplication).core
                val flow = withContext(Dispatchers.IO) {
                    core.flowRepository.loadFlows().firstOrNull { it.id == flowId }
                }
                if (flow != null) buildSlots(view, flow)
            }
        }
    }

    private fun buildSlots(root: View, flow: FlowDescriptor) {
        val container = root.findViewById<LinearLayout>(R.id.slots_container)
        container.removeAllViews()
        for (slot in flow.slots) {
            val itemView = layoutInflater.inflate(R.layout.item_slot, container, false)
            itemView.findViewById<TextView>(R.id.slot_label).text = slot.label
            itemView.contentDescription = getString(R.string.slot_content_description, slot.label)
            itemView.setOnClickListener { onSlotTapped(root, slot) }
            container.addView(itemView)
        }
    }

    private fun onSlotTapped(root: View, slot: SlotDescriptor) {
        val action = slot.action
        if (action !is SlotAction.WhatsAppCall) return
        pendingSlot = slot
        pendingAction = action.actionType
        showConfirmation(root, slot.label, action.actionType)
    }

    private fun showConfirmation(root: View, label: String, actionType: CommunicationActionType) {
        val confirmRoot = root.findViewById<View>(R.id.confirmation_root)
        val confirmBody = root.findViewById<TextView>(R.id.confirmation_body)
        val successCue = root.findViewById<TextView>(R.id.confirmation_success_cue)
        val actionLabel = when (actionType) {
            CommunicationActionType.CALL -> getString(R.string.comm_action_call)
            CommunicationActionType.VIDEO -> getString(R.string.comm_action_video)
        }
        confirmBody.text = getString(R.string.comm_confirmation_body, actionLabel, label)
        successCue.visibility = View.GONE
        confirmRoot.visibility = View.VISIBLE
    }

    private fun setupOverlays(root: View) {
        val confirmRoot = root.findViewById<View>(R.id.confirmation_root)
        val successCue = root.findViewById<TextView>(R.id.confirmation_success_cue)
        val confirmButton = root.findViewById<Button>(R.id.confirm_button)
        val cancelButton = root.findViewById<Button>(R.id.cancel_button)
        val warningRoot = root.findViewById<View>(R.id.slot_warning_root)
        val warningDismiss = root.findViewById<Button>(R.id.slot_warning_dismiss_button)

        cancelButton.setOnClickListener {
            confirmRoot.visibility = View.GONE
            successCue.visibility = View.GONE
            pendingSlot = null
            pendingAction = null
        }

        confirmButton.setOnClickListener {
            val slot = pendingSlot ?: return@setOnClickListener
            val action = slot.action as? SlotAction.WhatsAppCall ?: return@setOnClickListener
            val actionType = pendingAction ?: return@setOnClickListener
            successCue.visibility = View.VISIBLE
            dispatchWhatsApp(root, slot.id, action.contactRef, actionType)
        }

        warningDismiss.setOnClickListener {
            warningRoot.visibility = View.GONE
        }
    }

    private fun dispatchWhatsApp(
        root: View,
        slotId: String,
        contactRef: String,
        actionType: CommunicationActionType,
    ) {
        val core = (requireActivity().application as LauncherApplication).core
        val request = WhatsAppHandoffRequest(
            tileId = slotId,
            contactRef = contactRef,
            actionType = actionType,
            actionCycleId = UUID.randomUUID().toString(),
            homeSurfaceRef = HOME_SURFACE_REF,
        )
        val result = core.actionDispatcher.dispatch(ActionRequest.WhatsAppHandoff(request))
        if (result is DispatchResult.WhatsApp) {
            val confirmRoot = root.findViewById<View>(R.id.confirmation_root)
            when (result.outcome) {
                WhatsAppHandoffResult.LAUNCH_STARTED -> Unit
                WhatsAppHandoffResult.WHATSAPP_UNAVAILABLE -> {
                    confirmRoot.visibility = View.GONE
                    showWarning(
                        root,
                        WarningState(
                            code = CommunicationWarningCode.WHATSAPP_UNAVAILABLE,
                            title = getString(R.string.comm_warning_unavailable_title),
                            message = getString(R.string.comm_warning_unavailable_body),
                        ),
                    )
                }
                WhatsAppHandoffResult.ACTION_NOT_SUPPORTED -> {
                    confirmRoot.visibility = View.GONE
                    showWarning(
                        root,
                        WarningState(
                            code = CommunicationWarningCode.ACTION_NOT_SUPPORTED,
                            title = getString(R.string.comm_warning_action_title),
                            message = getString(R.string.comm_warning_action_body),
                        ),
                    )
                }
                else -> {
                    confirmRoot.visibility = View.GONE
                    showWarning(
                        root,
                        WarningState(
                            code = CommunicationWarningCode.HANDOFF_LAUNCH_FAILED,
                            title = getString(R.string.comm_warning_launch_title),
                            message = getString(R.string.comm_warning_launch_body),
                        ),
                    )
                }
            }
        }
    }

    private fun showWarning(root: View, state: WarningState) {
        root.findViewById<TextView>(R.id.slot_warning_title).text = state.title
        root.findViewById<TextView>(R.id.slot_warning_body).text = state.message
        root.findViewById<View>(R.id.slot_warning_root).visibility = View.VISIBLE
    }

    companion object {
        private const val ARG_FLOW_ID = "flow_id"
        private const val HOME_SURFACE_REF = "home_main"

        fun newInstance(flowId: String): FlowFragment = FlowFragment().apply {
            arguments = Bundle().also { it.putString(ARG_FLOW_ID, flowId) }
        }
    }
}
