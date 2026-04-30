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

    fun getSlotInfo(slotKey: String): Flow<SlotInfo> {
        val prefKey = stringPreferencesKey(slotKey)
        return appContext.dataStore.data.map { preferences ->
            val json = preferences[prefKey]
            if (json != null) {
                try { gson.fromJson(json, SlotInfo::class.java) } catch (e: Exception) { SlotInfo() }
            } else {
                SlotInfo()
            }
        }
    }

    fun getSlotsUnder(parentId: String): Flow<List<Pair<String, SlotInfo>>> {
        return appContext.dataStore.data.map { preferences ->
            (0..4).map { i ->
                val key = "${parentId}_$i"
                val json = preferences[stringPreferencesKey(key)]
                val info = if (json != null) {
                    try { gson.fromJson(json, SlotInfo::class.java) } catch (e: Exception) { SlotInfo() }
                } else SlotInfo()
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

    suspend fun deleteSlotInfo(slotKey: String) {
        val prefKey = stringPreferencesKey(slotKey)
        appContext.dataStore.edit { preferences ->
            preferences.remove(prefKey)
        }
    }

    suspend fun deleteSlotRecursive(slotKey: String) {
        appContext.dataStore.edit { preferences ->
            preferences.asMap().keys
                .filter { it.name == slotKey || it.name.startsWith("${slotKey}_") }
                .map { stringPreferencesKey(it.name) }
                .forEach { preferences.remove(it) }
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
