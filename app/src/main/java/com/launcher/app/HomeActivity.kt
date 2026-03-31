package com.launcher.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.launcher.api.CatalogSnapshot
import com.launcher.api.EffectiveProfile
import kotlinx.coroutines.launch

/**
 * Thin home shell: observes profile and catalog snapshots from Core; no catalog business logic.
 */
class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val core = (application as LauncherApplication).core
        val profileLine = findViewById<TextView>(R.id.profile_line)
        val catalogLine = findViewById<TextView>(R.id.catalog_line)
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

    private fun formatProfileLine(eff: EffectiveProfile): String =
        getString(
            R.string.home_profile_line,
            eff.snapshot.id,
            eff.profileGeneration,
            eff.degradation.reasonCodes.joinToString(),
        )

    private fun formatCatalogLine(snap: CatalogSnapshot): String =
        getString(R.string.home_catalog_line, snap.generation, snap.entries.size)
}
