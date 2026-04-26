package com.kusa.weblauncher2

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.kusa.weblauncher2.data.SlotInfo
import com.kusa.weblauncher2.data.SlotRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SlotLauncherActivity : ComponentActivity() {
    private val repository by lazy { SlotRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val componentName = intent.component?.className ?: ""
        Log.d("SlotLauncher", "componentName = $componentName")

        // ★バグ②修正: endsWith で完全一致判定（contains だと誤マッチの可能性）
        val slotKey: String = when {
            componentName.endsWith(".Slot1") -> "root_0"
            componentName.endsWith(".Slot2") -> "root_1"
            componentName.endsWith(".Slot3") -> "root_2"
            componentName.endsWith(".Slot4") -> "root_3"
            componentName.endsWith(".Slot5") -> "root_4"
            else -> {
                // ショートカット経由の場合は TARGET_SLOT を使う
                val fromExtra = intent.getStringExtra("TARGET_SLOT")
                Log.d("SlotLauncher", "No alias match. TARGET_SLOT = $fromExtra")
                fromExtra ?: ""
            }
        }

        Log.d("SlotLauncher", "slotKey = $slotKey")

        if (slotKey.isNotEmpty()) {
            lifecycleScope.launch {
                val info = repository.getSlotInfo(slotKey).first()
                Log.d("SlotLauncher", "info = $info")
                when {
                    info.isSublink        -> navigateToAdmin(slotKey)
                    info.url.isNotEmpty() -> launchBrowser(info)
                    else                  -> navigateToAdmin(slotKey)
                }
            }
        } else {
            navigateToAdmin(null)
        }
    }

    private fun navigateToAdmin(slotKey: String?) {
        val i = Intent(this, MainActivity::class.java).apply {
            if (slotKey != null) putExtra("TARGET_SLOT", slotKey)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(i)
        finish()
    }

    private fun launchBrowser(info: SlotInfo) {
        try {
            val colorInt = try {
                Color.parseColor(info.iconColor)
            } catch (e: Exception) { Color.BLUE }

            val colorParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(colorInt)
                .build()
            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorParams)
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(this, Uri.parse(info.url))
        } catch (e: Exception) {
            Log.e("SlotLauncher", "launchBrowser failed", e)
            navigateToAdmin(null)
        } finally {
            finish()
        }
    }
}
