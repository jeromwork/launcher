package com.launcher.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.launcher.api.CatalogSnapshot
import com.launcher.api.CommunicationWarningCode
import com.launcher.api.EffectiveProfile
import com.launcher.api.FlowDescriptor
import com.launcher.api.ReturnRestoreOutcome
import com.launcher.app.flow.FlowFragment
import com.launcher.app.settings.SettingsFragment
import com.launcher.app.wizard.AddFlowWizardFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var core: com.launcher.core.LauncherCore
    private var flows: List<FlowDescriptor> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        core = (application as LauncherApplication).core

        val profileLine = findViewById<TextView>(R.id.profile_line)
        val catalogLine = findViewById<TextView>(R.id.catalog_line)

        findViewById<View>(R.id.settings_button).setOnClickListener { openSettings() }

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

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                flows = withContext(Dispatchers.IO) { core.flowRepository.loadFlows() }
                buildFlowTabs()
                flows.firstOrNull()?.let { openFlow(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        when (core.actionDispatcher.restoreOnHomeEntry(HOME_SURFACE_REF)) {
            ReturnRestoreOutcome.RESTORED_NEAREST_STABLE_HOME -> showRestoredWarning()
            else -> Unit
        }
    }

    private fun buildFlowTabs() {
        val container = findViewById<LinearLayout>(R.id.flow_tabs_container)
        container.removeAllViews()

        flows.forEachIndexed { _, flow ->
            val btn = Button(this).apply {
                text = flow.name
                contentDescription = getString(R.string.flow_tab_content_description, flow.name)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    resources.getDimensionPixelSize(R.dimen.flow_tab_height),
                ).also { lp -> lp.marginEnd = dpToPx(8) }
                setOnClickListener { openFlow(flow) }
            }
            container.addView(btn)
        }

        val addBtn = Button(this).apply {
            text = getString(R.string.flow_tab_add)
            contentDescription = getString(R.string.flow_tab_add_description)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                resources.getDimensionPixelSize(R.dimen.flow_tab_height),
            )
            setOnClickListener { openAddFlowWizard() }
        }
        container.addView(addBtn)
    }

    private fun openFlow(flow: FlowDescriptor) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.flow_container, FlowFragment.newInstance(flow.id))
            .commit()
    }

    private fun openSettings() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.flow_container, SettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openAddFlowWizard() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.flow_container, AddFlowWizardFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun showRestoredWarning() {
        val warningRoot = findViewById<View>(R.id.warning_root)
        val warningTitle = findViewById<TextView>(R.id.warning_title)
        val warningBody = findViewById<TextView>(R.id.warning_body)
        val warningDismiss = findViewById<Button>(R.id.warning_dismiss_button)
        warningTitle.text = getString(R.string.comm_warning_restore_title)
        warningBody.text = getString(R.string.comm_warning_restore_body)
        warningDismiss.contentDescription = getString(R.string.comm_warning_dismiss)
        warningRoot.visibility = View.VISIBLE
        warningDismiss.setOnClickListener { warningRoot.visibility = View.GONE }
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

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    companion object {
        private const val HOME_SURFACE_REF = "home_main"
    }
}
