package com.launcher.app.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import com.launcher.app.LauncherApplication
import com.launcher.app.R

class AddFlowWizardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_add_flow_wizard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val core = (requireActivity().application as LauncherApplication).core
        val templatesContainer = view.findViewById<LinearLayout>(R.id.wizard_templates_container)

        val templates = core.flowRepository.availableTemplates(PRESET_SENIOR)
        val templateLabels = mapOf(
            "contacts" to getString(R.string.wizard_add_flow_template_contacts),
            "admin_devices" to getString(R.string.wizard_add_flow_template_admin_devices),
        )

        for (template in templates) {
            val radio = RadioButton(requireContext()).apply {
                text = templateLabels[template.id] ?: template.id
                textSize = 18f
                minHeight = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
            }
            templatesContainer.addView(radio)
        }

        view.findViewById<Button>(R.id.wizard_add_flow_button).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    companion object {
        private const val PRESET_SENIOR = "senior-launcher"
    }
}
