package com.example.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

data class InstalledApp(
    val appName: String,
    val packageName: String
)

class AppLauncherService(private val context: Context) {

    companion object {
        private const val TAG = "AppLauncher"
    }

    fun getInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        return resolveInfos.mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            if (pkg.isNotEmpty() && label.isNotEmpty()) {
                InstalledApp(appName = label, packageName = pkg)
            } else null
        }.distinctBy { it.packageName }
    }

    fun openAppByName(appNameQuery: String): Pair<Boolean, String> {
        val query = appNameQuery.trim().lowercase()
        val apps = getInstalledApps()

        // 1. Exact match
        var target = apps.find { it.appName.lowercase() == query }

        // 2. Starts with
        if (target == null) {
            target = apps.find { it.appName.lowercase().startsWith(query) }
        }

        // 3. Contains
        if (target == null) {
            target = apps.find { it.appName.lowercase().contains(query) }
        }

        // 4. Package contains
        if (target == null) {
            target = apps.find { it.packageName.lowercase().contains(query) }
        }

        if (target != null) {
            return launchPackage(target.packageName, target.appName)
        }

        // Common Android system fallbacks
        val fallbackPackage = when (query) {
            "settings" -> "com.android.settings"
            "camera" -> "com.google.android.GoogleCamera"
            "clock", "alarm" -> "com.google.android.deskclock"
            "calculator" -> "com.google.android.calculator"
            "chrome", "browser" -> "com.android.chrome"
            "maps" -> "com.google.android.apps.maps"
            "youtube" -> "com.google.android.youtube"
            "messages", "sms" -> "com.google.android.apps.messaging"
            "phone", "dialer" -> "com.google.android.dialer"
            else -> null
        }

        if (fallbackPackage != null) {
            val result = launchPackage(fallbackPackage, appNameQuery)
            if (result.first) return result
        }

        return Pair(false, "Could not find an installed app matching \"$appNameQuery\".")
    }

    fun launchPackage(packageName: String, labelHint: String = ""): Pair<Boolean, String> {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                val name = if (labelHint.isNotEmpty()) labelHint else packageName
                Pair(true, "Opened $name.")
            } else {
                Pair(false, "Package $packageName is not launchable or not installed.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package $packageName", e)
            Pair(false, "Failed to launch $packageName: ${e.message}")
        }
    }
}
