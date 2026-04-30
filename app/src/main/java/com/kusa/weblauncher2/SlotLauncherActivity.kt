package com.kusa.weblauncher2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
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

        val slotKey: String = when {
            componentName.endsWith(".Slot1") -> "root_0"
            componentName.endsWith(".Slot2") -> "root_1"
            componentName.endsWith(".Slot3") -> "root_2"
            componentName.endsWith(".Slot4") -> "root_3"
            componentName.endsWith(".Slot5") -> "root_4"
            else -> {
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
                    info.url.isNotEmpty() -> launchWithCustomTabs(info) { navigateToAdmin(null) }
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
}
