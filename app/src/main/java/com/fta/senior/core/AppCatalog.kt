package com.fta.senior.core

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings

data class InstalledApp(val packageName: String, val label: String, val uid: Int)

class AppCatalog(private val context: Context) {
    private val pm get() = context.packageManager
    @Suppress("DEPRECATION")
    fun userApps(): List<InstalledApp> = pm.getInstalledApplications(0)
        .filter { CleanupPolicy.permanentProtection(it.packageName, it.uid, isSystem(it), userId) == null }
        .map { InstalledApp(it.packageName, it.loadLabel(pm).toString(), it.uid) }
        .sortedBy { it.label.lowercase() }

    @Suppress("DEPRECATION")
    fun label(name: String): String = runCatching { pm.getApplicationInfo(name, 0).loadLabel(pm).toString() }.getOrDefault(name)

    @Suppress("DEPRECATION")
    fun protectedPackages(protectVpn: Boolean): Set<String> = buildSet {
        add(CleanupPolicy.OWN_PACKAGE); add(CleanupPolicy.SHIZUKU_PACKAGE)
        add("com.android.systemui"); add("android")
        // Protect all installed launchers, not just the currently selected launcher.
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .forEach { add(it.activityInfo.packageName) }
        listOf(Settings.Secure.DEFAULT_INPUT_METHOD, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).forEach { key ->
            Settings.Secure.getString(context.contentResolver, key)?.split(':')?.forEach { value ->
                ComponentName.unflattenFromString(value)?.packageName?.let { add(it) }
            }
        }
        pm.queryIntentServices(Intent("android.service.notification.NotificationListenerService"), 0)
            .filter { it.serviceInfo.permission == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE }
            .forEach { add(it.serviceInfo.packageName) }
        if (protectVpn) {
            pm.queryIntentServices(Intent("android.net.VpnService"), 0)
                .filter { it.serviceInfo.permission == Manifest.permission.BIND_VPN_SERVICE }
                .forEach { add(it.serviceInfo.packageName) }
        }
        // Expand protection across shared UIDs (whitelists are expanded separately).
        toList().forEach { name ->
            runCatching { pm.getApplicationInfo(name, 0) }.getOrNull()?.let {
                addAll(pm.getPackagesForUid(it.uid).orEmpty())
            }
        }
    }

    @Suppress("DEPRECATION")
    fun expandSharedUids(packages: Set<String>): Set<String> = buildSet {
        addAll(packages)
        packages.forEach { name ->
            runCatching { pm.getApplicationInfo(name, 0) }.getOrNull()?.let {
                addAll(pm.getPackagesForUid(it.uid).orEmpty())
            }
        }
    }

    val userId get() = android.os.Process.myUid() / 100000
    private fun isSystem(info: ApplicationInfo) =
        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}
