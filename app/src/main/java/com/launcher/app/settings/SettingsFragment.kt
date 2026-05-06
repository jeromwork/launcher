package com.launcher.app.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.launcher.api.FlowPreset
import com.launcher.app.LauncherApplication
import com.launcher.app.R
import com.launcher.app.firstlaunch.FirstLaunchActivity
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val core = (requireActivity().application as LauncherApplication).core
        val presetValue = view.findViewById<TextView>(R.id.settings_preset_value)

        lifecycleScope.launch {
            val active = core.presetRepository.getActivePreset() ?: FlowPreset.WORKSPACE
            presetValue.text = getString(resolveTitleRes(active))
        }

        view.findViewById<Button>(R.id.settings_change_preset_button).setOnClickListener {
            showPresetDialog(presetValue)
        }

        view.findViewById<Button>(R.id.settings_show_qr_button).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.settings_show_qr_button))
                .setMessage(getString(R.string.settings_qr_placeholder_message))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        view.findViewById<Button>(R.id.settings_reset_button).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.settings_reset_data_confirm_title))
                .setMessage(getString(R.string.settings_reset_data_confirm_message))
                .setPositiveButton(getString(R.string.settings_reset_data_confirm_yes)) { _, _ ->
                    lifecycleScope.launch {
                        core.presetRepository.clear()
                        restartToFirstLaunch()
                    }
                }
                .setNegativeButton(getString(R.string.settings_reset_data_confirm_no), null)
                .show()
        }
    }

    private fun showPresetDialog(presetValueView: TextView) {
        val core = (requireActivity().application as LauncherApplication).core
        val presets = FlowPreset.values()
        val labels = presets.map { getString(resolveTitleRes(it)) }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_preset_dialog_title))
            .setItems(labels) { _, which ->
                val chosen = presets[which]
                lifecycleScope.launch {
                    core.presetRepository.setActivePreset(chosen)
                    presetValueView.text = getString(resolveTitleRes(chosen))
                    requireActivity().recreate()
                }
            }
            .show()
    }

    private fun restartToFirstLaunch() {
        val intent = Intent(requireContext(), FirstLaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        requireActivity().finish()
    }

    private fun resolveTitleRes(preset: FlowPreset): Int = when (preset) {
        FlowPreset.WORKSPACE -> R.string.preset_workspace_title
        FlowPreset.LAUNCHER -> R.string.preset_launcher_title
        FlowPreset.SIMPLE_LAUNCHER -> R.string.preset_simple_launcher_title
    }
}
