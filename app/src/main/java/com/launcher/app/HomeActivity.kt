package com.launcher.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
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

    private val contactName: String by lazy { getString(R.string.contact_name_value) }
    private val contactPhoneRaw: String by lazy { getString(R.string.contact_number_value) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val core = (application as LauncherApplication).core
        val profileLine = findViewById<TextView>(R.id.profile_line)
        val catalogLine = findViewById<TextView>(R.id.catalog_line)
        val contactNameView = findViewById<TextView>(R.id.contact_name)
        val contactNumberView = findViewById<TextView>(R.id.contact_number)
        val callButton = findViewById<Button>(R.id.call_button)
        val handoffStatus = findViewById<TextView>(R.id.handoff_status)

        contactNameView.text = contactName
        contactNumberView.text = contactPhoneRaw
        callButton.setOnClickListener {
            handoffStatus.text = openWhatsAppChat(rawPhone = contactPhoneRaw)
        }

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

    private fun openWhatsAppChat(rawPhone: String): String {
        val normalized = normalizeE164(rawPhone)
        if (!isSupportedRussianMobile(normalized)) {
            return getString(R.string.status_invalid_contact)
        }
        val phoneDigits = normalized.removePrefix("+")
        val jid = "$phoneDigits@s.whatsapp.net"

        val jidChatIntent = Intent(Intent.ACTION_MAIN).apply {
            setPackage(WHATSAPP_PACKAGE)
            putExtra("jid", jid)
        }
        val uriChatIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("whatsapp://send?phone=$phoneDigits"),
        ).apply { setPackage(WHATSAPP_PACKAGE) }
        val webChatIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/$phoneDigits"),
        ).apply { setPackage(WHATSAPP_PACKAGE) }

        val intentToLaunch = when {
            jidChatIntent.resolveActivity(packageManager) != null -> jidChatIntent
            uriChatIntent.resolveActivity(packageManager) != null -> uriChatIntent
            webChatIntent.resolveActivity(packageManager) != null -> webChatIntent
            else -> return getString(R.string.status_whatsapp_not_found)
        }

        return runCatching {
            startActivity(intentToLaunch)
            getString(R.string.status_opening_whatsapp)
        }.getOrElse {
            getString(R.string.status_whatsapp_not_found)
        }
    }

    private fun normalizeE164(rawPhone: String): String {
        val sb = StringBuilder()
        rawPhone.forEachIndexed { index, c ->
            when {
                c.isDigit() -> sb.append(c)
                c == '+' && index == 0 -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun isSupportedRussianMobile(phone: String): Boolean {
        return phone.startsWith("+79") && phone.length == 12
    }

    private companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
    }
}
