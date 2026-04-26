package com.kusa.weblauncher2

import com.kusa.weblauncher2.MainActivity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.kusa.weblauncher2.data.SlotInfo
import com.kusa.weblauncher2.data.SlotRepository // データ保存用
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// クラス名を変更して競合を避ける
class SlotLauncherActivity : ComponentActivity() {
    // データ保存用のRepositoryを初期化
    private val repository by lazy { SlotRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val componentName = intent.component?.className ?: ""

        // slotKey を決定。見つからない場合は null にする
        val slotKey: String? = when {
            componentName.endsWith("Slot1") -> "slot_1"
            componentName.endsWith("Slot2") -> "slot_2"
            componentName.endsWith("Slot3") -> "slot_3"
            componentName.endsWith("Slot4") -> "slot_4"
            componentName.endsWith("Slot5") -> "slot_5"
            else -> intent.getStringExtra("TARGET_SLOT") // ここで ?: "" を付けない
        }

        // slotKey が null でない、かつ空文字でない場合に処理を続行
        if (!slotKey.isNullOrEmpty()) {
            lifecycleScope.launch {
                val info = repository.getSlotInfo(slotKey).first()
                if (info.url.isNotEmpty()) {
                    launchBrowser(info)
                } else {
                    // URLが未登録なら設定画面へ
                    navigateToAdmin(slotKey)
                }
            }
        } else {
            // キー自体が不明なら設定画面（メイン）へ
            navigateToAdmin(null)
        }
    }

    private fun launchBrowser(info: SlotInfo) {
        if (info.useCustomTabs) {
            val colorInt = try {
                Color.parseColor(info.iconColor)
            } catch (e: Exception) {
                Color.BLUE
            }
            val colorParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(colorInt)
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorParams)
                .build()
            customTabsIntent.launchUrl(this, Uri.parse(info.url))
        } else {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.url))
            startActivity(intent)
        }
        finish()
    }

    private fun navigateToAdmin(slotKey: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("TARGET_SLOT", slotKey)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}