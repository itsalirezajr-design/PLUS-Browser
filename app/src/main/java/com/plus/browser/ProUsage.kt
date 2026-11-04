package com.plus.browser

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class BrowserDefaultStatus { DEFAULT, NOT_DEFAULT, UNAVAILABLE }

// The debug override can never grant access in a release build.
internal fun proAccessAllowed(defaultBrowser:Boolean,debugBuild:Boolean,debugOverride:Boolean):Boolean =
    defaultBrowser || (debugBuild && debugOverride)

internal class ProUsage(private val activity:Activity) {
    private val prefs=activity.getSharedPreferences("plus_pro",Context.MODE_PRIVATE)
    var status by mutableStateOf(BrowserDefaultStatus.UNAVAILABLE); private set
    var developer by mutableStateOf(DebugAccess.enabled && prefs.getBoolean("developer",false)); private set
    val isDefault get()=status==BrowserDefaultStatus.DEFAULT
    val unlocked get()=proAccessAllowed(isDefault,DebugAccess.enabled,developer)

    init { refresh() }

    /** Query the system every time; old stored timer/unlock values are deliberately ignored. */
    fun refresh() {
        status=try {
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) {
                val roles=activity.getSystemService(RoleManager::class.java)
                when {
                    roles==null || !roles.isRoleAvailable(RoleManager.ROLE_BROWSER) -> BrowserDefaultStatus.UNAVAILABLE
                    roles.isRoleHeld(RoleManager.ROLE_BROWSER) -> BrowserDefaultStatus.DEFAULT
                    else -> BrowserDefaultStatus.NOT_DEFAULT
                }
            } else {
                // On Android 8–9, the default ACTION_VIEW handler represents the selected browser.
                val intent=Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE)
                val resolved=activity.packageManager.resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY)
                when {
                    resolved==null -> BrowserDefaultStatus.UNAVAILABLE
                    resolved.activityInfo?.packageName==activity.packageName -> BrowserDefaultStatus.DEFAULT
                    else -> BrowserDefaultStatus.NOT_DEFAULT
                }
            }
        } catch (_:Exception) { BrowserDefaultStatus.UNAVAILABLE }
    }
    fun developerUnlock() { if(DebugAccess.enabled) { developer=true;prefs.edit().putBoolean("developer",true).apply() } }
    fun revokeDeveloper(){developer=false;prefs.edit().remove("developer").apply()}
}
