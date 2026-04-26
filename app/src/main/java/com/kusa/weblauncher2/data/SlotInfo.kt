package com.kusa.weblauncher2.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// [FR-03] のデータ構造
data class SlotInfo(
    val label: String = "",     // 表示名
    val url: String = "",       // URL  サブリンクなしの時は””
    val iconColor: String = "#808080",  // グレー
    val isSublink: Boolean = false,          // サブリンクあり
    val useCustomTabs: Boolean = true        // カスタムタブ使用する。
)

val Context.dataStore by preferencesDataStore(name = "slots_config")

class SlotRepository(private val context: Context) {
    private val gson = Gson()

    /**
     * 指定されたキー（階層ID + インデックス）のスロット情報を取得
     */
    fun getSlotInfo(slotKey: String): Flow<SlotInfo> {
        val prefKey = stringPreferencesKey(slotKey)
        return context.dataStore.data.map { preferences ->
            val json = preferences[prefKey]
            if (json != null) {
                try {
                    gson.fromJson(json, SlotInfo::class.java)
                } catch (e: Exception) {
                    SlotInfo()
                }
            } else {
                SlotInfo()
            }
        }
    }

    /**
     * スロット情報を保存
     */
    suspend fun saveSlotInfo(slotKey: String, info: SlotInfo) {
        val prefKey = stringPreferencesKey(slotKey)
        val json = gson.toJson(info)
        context.dataStore.edit { preferences ->
            preferences[prefKey] = json
        }
    }

    /**
     * スロット情報を削除（初期化）
     */
    suspend fun deleteSlotInfo(slotKey: String) {
        val prefKey = stringPreferencesKey(slotKey)
        context.dataStore.edit { preferences ->
            preferences.remove(prefKey)
        }
    }
}