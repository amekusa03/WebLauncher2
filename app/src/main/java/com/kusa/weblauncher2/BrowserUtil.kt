package com.kusa.weblauncher2

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.kusa.weblauncher2.data.SlotInfo

fun ComponentActivity.launchWithCustomTabs(info: SlotInfo, onError: (() -> Unit)? = null) {
    try {
        val colorInt = try {
            android.graphics.Color.parseColor(info.iconColor)
        } catch (e: Exception) { android.graphics.Color.BLUE }
        val colorParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(colorInt)
            .build()
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colorParams)
            .setShowTitle(true)
            .build()
            .launchUrl(this, Uri.parse(info.url))
    } catch (e: Exception) {
        onError?.invoke() ?: e.printStackTrace()
    } finally {
        finish()
    }
}
