package com.launcher.app.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.launcher.app.R

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
                .setPositiveButton(getString(R.string.settings_reset_data_confirm_yes)) { _, _ -> }
                .setNegativeButton(getString(R.string.settings_reset_data_confirm_no), null)
                .show()
        }
    }
}
