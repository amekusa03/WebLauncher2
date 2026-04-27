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
    val iconColor: String = "#808080",
    val isSublink: Boolean = false,
    val useCustomTabs: Boolean = true,
    val iconType: IconType = IconType.COLOR,
    val iconBase64: String = ""              // ★Base64エンコードされたPNG画像
)

enum class IconType {
    COLOR,   // 色＋先頭文字で自動生成
    FAVICON  // ファビコン画像
}

val Context.dataStore by preferencesDataStore(name = "slots_config")

class SlotRepository(private val context: Context) {
    private val gson = Gson()

    fun getSlotInfo(slotKey: String): Flow<SlotInfo> {
        val prefKey = stringPreferencesKey(slotKey)
        return context.dataStore.data.map { preferences ->
            val json = preferences[prefKey]
            if (json != null) {
                try { gson.fromJson(json, SlotInfo::class.java) } catch (e: Exception) { SlotInfo() }
            } else {
                SlotInfo()
            }
        }
    }

    suspend fun saveSlotInfo(slotKey: String, info: SlotInfo) {
        val prefKey = stringPreferencesKey(slotKey)
        context.dataStore.edit { preferences ->
            preferences[prefKey] = gson.toJson(info)
        }
    }

    suspend fun deleteSlotInfo(slotKey: String) {
        val prefKey = stringPreferencesKey(slotKey)
        context.dataStore.edit { preferences ->
            preferences.remove(prefKey)
        }
    }

    /** BitmapをBase64文字列に変換 */
    fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }

    /** Base64文字列をBitmapに変換 */
    fun base64ToBitmap(base64: String): Bitmap? {
        if (base64.isEmpty()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) { null }
    }
}
