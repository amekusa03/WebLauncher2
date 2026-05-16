package com.kusa.weblauncher2.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.ByteArrayOutputStream

data class SlotInfo(
    val label: String = "",
    val url: String = "",
    val iconColor: String = "#8B4513",
    val isSublink: Boolean = false,
    val useCustomTabs: Boolean = true,
    val iconType: IconType = IconType.COLOR,
    val iconBase64: String = ""
)

enum class IconType {
    COLOR,
    FAVICON
}

val Context.dataStore by preferencesDataStore(name = "slots_config")

class SlotRepository(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()

    private val DEFAULT_SLOTS = mapOf(
        "root_0" to SlotInfo(label = "Yahoo", url = "https://www.yahoo.co.jp/", iconColor = "#FF0033", isSublink = true),
        "root_1" to SlotInfo(label = "楽天", url = "https://www.rakuten.co.jp/", iconColor = "#FF0000", isSublink = true),
        "root_2" to SlotInfo(label = "X", url = "https://x.com", iconColor = "#000000"),
        "root_3" to SlotInfo(label = "Amazon", url = "https://www.amazon.co.jp", iconColor = "#FF9900"),
        "root_4" to SlotInfo(label = "Google", url = "https://www.google.com", iconColor = "#4285F4"),
        
        // Yahoo sub-items
        "root_0_0" to SlotInfo(label = "メール", url = "https://mail.yahoo.co.jp/", iconColor = "#FF0033"),
        "root_0_1" to SlotInfo(label = "ニュース", url = "https://news.yahoo.co.jp/", iconColor = "#FF0033"),
        "root_0_2" to SlotInfo(label = "天気", url = "https://weather.yahoo.co.jp/", iconColor = "#FF0033"),
        "root_0_3" to SlotInfo(label = "オークション", url = "https://auctions.yahoo.co.jp/", iconColor = "#FF0033"),
        "root_0_4" to SlotInfo(label = "ショッピング", url = "https://shopping.yahoo.co.jp/", iconColor = "#FF0033"),
        // 楽天 sub-items
        "root_1_0" to SlotInfo(label = "カード", url = "https://www.rakuten-card.co.jp/", iconColor = "#FF0033"),
        "root_1_1" to SlotInfo(label = "ショッピング", url = "https://shopping.yahoo.co.jp/", iconColor = "#FF0033"),
        "root_1_2" to SlotInfo(label = "ニュース", url = "https://news.yahoo.co.jp/", iconColor = "#FF0033"),
        "root_1_3" to SlotInfo(label = "天気", url = "https://weather.yahoo.co.jp/", iconColor = "#FF0033"),
        "root_1_4" to SlotInfo(label = "オークション", url = "https://auctions.yahoo.co.jp/", iconColor = "#FF0033"),
    )

    fun getSlotInfo(slotKey: String): Flow<SlotInfo> {
        val prefKey = stringPreferencesKey(slotKey)
        return appContext.dataStore.data.map { preferences ->
            val json = preferences[prefKey]
            if (json != null) {
                try {
                    gson.fromJson(json, SlotInfo::class.java)
                } catch (e: Exception) {
                    DEFAULT_SLOTS[slotKey] ?: SlotInfo()
                }
            } else {
                DEFAULT_SLOTS[slotKey] ?: SlotInfo()
            }
        }
    }

    fun getSlotsUnder(parentId: String): Flow<List<Pair<String, SlotInfo>>> {
        return appContext.dataStore.data.map { preferences ->
            (0..4).map { i ->
                val key = "${parentId}_$i"
                val json = preferences[stringPreferencesKey(key)]
                val info = if (json != null) {
                    try {
                        gson.fromJson(json, SlotInfo::class.java)
                    } catch (e: Exception) {
                        DEFAULT_SLOTS[key] ?: SlotInfo()
                    }
                } else {
                    DEFAULT_SLOTS[key] ?: SlotInfo()
                }
                key to info
            }
        }
    }

    suspend fun saveSlotInfo(slotKey: String, info: SlotInfo) {
        val prefKey = stringPreferencesKey(slotKey)
        appContext.dataStore.edit { preferences ->
            preferences[prefKey] = gson.toJson(info)
        }
    }

    /**
     * スロットを解除（削除）する。
     * デフォルト値（Google等）を消すために、単なるremoveではなく空の情報を保存して上書きする。
     */
    suspend fun deleteSlotInfo(slotKey: String) {
        val prefKey = stringPreferencesKey(slotKey)
        appContext.dataStore.edit { preferences ->
            // キー自体を消すとデフォルト値が復活してしまうので、空のSlotInfoで上書きする
            preferences[prefKey] = gson.toJson(SlotInfo())
        }
    }

    /**
     * スロットとその配下を再帰的に解除する。
     */
    suspend fun deleteSlotRecursive(slotKey: String) {
        appContext.dataStore.edit { preferences ->
            // 指定されたスロット自体を空データで上書き（デフォルト値の復活防止）
            preferences[stringPreferencesKey(slotKey)] = gson.toJson(SlotInfo())
            
            // その配下のスロット（子階層）のデータは物理削除
            val childrenToRemove = preferences.asMap().keys
                .filter { it.name.startsWith("${slotKey}_") }
                .map { stringPreferencesKey(it.name) }
            childrenToRemove.forEach { preferences.remove(it) }
        }
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        if (base64.isEmpty()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) { null }
    }
}
