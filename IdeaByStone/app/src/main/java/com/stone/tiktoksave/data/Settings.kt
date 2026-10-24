package com.stone.tiktoksave.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeChoice(val key: String, val label: String) {
    WHITE("white", "Trắng"),
    BLACK("black", "Đen"),
    YELLOW_WHITE("yellow_white", "Vàng-trắng");

    companion object {
        fun fromKey(key: String?): ThemeChoice? = entries.firstOrNull { it.key == key }
    }
}

/** A: mở app và dán link. B: tải ngầm, chất lượng cao nhất, MP4. */
enum class ShareMode(val key: String) {
    OPEN_APP("A"),
    BACKGROUND("B");

    companion object {
        fun fromKey(key: String?): ShareMode? = entries.firstOrNull { it.key == key }
    }
}

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val shareModeKey = stringPreferencesKey("share_mode")

    private val data: Flow<Preferences> = context.settingsStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    /** null = chưa chọn, theo giao diện hệ thống. */
    val theme: Flow<ThemeChoice?> = data.map { ThemeChoice.fromKey(it[themeKey]) }

    /** null = chưa chọn, hiện hộp thoại ở lần chia sẻ đầu tiên. */
    val shareMode: Flow<ShareMode?> = data.map { ShareMode.fromKey(it[shareModeKey]) }

    suspend fun setTheme(choice: ThemeChoice) {
        context.settingsStore.edit { it[themeKey] = choice.key }
    }

    suspend fun setShareMode(mode: ShareMode) {
        context.settingsStore.edit { it[shareModeKey] = mode.key }
    }
}
