package com.launcher.app.preset.task120.facade

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * ACL over Play Store intent construction. Returns Boolean + Intent —
 * the Intent stays in the facade layer; providers receive the launch signal only.
 */
interface StoreIntentFacade {
    fun canOpenStore(): Boolean
    fun launchStoreForPackage(pkg: String): Boolean
}

class AndroidStoreIntentFacade(
    private val context: Context,
    private val storePackage: String = "com.android.vending",
) : StoreIntentFacade {

    override fun canOpenStore(): Boolean = try {
        context.packageManager.getPackageInfo(storePackage, 0)
        true
    } catch (_: Throwable) {
        false
    }

    override fun launchStoreForPackage(pkg: String): Boolean {
        if (!canOpenStore()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
            setPackage(storePackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
