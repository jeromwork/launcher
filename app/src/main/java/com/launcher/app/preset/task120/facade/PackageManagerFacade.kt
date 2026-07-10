package com.launcher.app.preset.task120.facade

import android.content.Context
import android.content.pm.PackageManager

/**
 * ACL over Android PackageManager (CLAUDE.md rule 2). Returns Boolean / String —
 * never PackageInfo — so the domain never sees an Android type.
 */
interface PackageManagerFacade {
    fun isInstalled(pkg: String): Boolean
    fun getInstalled(): List<String>
}

class AndroidPackageManagerFacade(private val context: Context) : PackageManagerFacade {

    override fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun getInstalled(): List<String> =
        context.packageManager.getInstalledPackages(0).map { it.packageName }
}
