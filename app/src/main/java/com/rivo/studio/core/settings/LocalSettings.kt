package com.rivo.studio.core.settings

import android.content.Context
import android.content.SharedPreferences

class LocalSettings(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("rivo_preferences", Context.MODE_PRIVATE)

    var darkTheme: Boolean
        get() = preferences.getBoolean(KEY_DARK_THEME, false)
        set(value) { preferences.edit().putBoolean(KEY_DARK_THEME, value).apply() }

    var editorFontSize: Int
        get() = preferences.getInt(KEY_FONT_SIZE, 15).coerceIn(12, 24)
        set(value) { preferences.edit().putInt(KEY_FONT_SIZE, value.coerceIn(12, 24)).apply() }

    var language: String
        get() = preferences.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) { preferences.edit().putString(KEY_LANGUAGE, if (value == "bn") "bn" else "en").apply() }

    var defaultProviderId: String?
        get() = preferences.getString(KEY_DEFAULT_PROVIDER, null)
        set(value) {
            preferences.edit().also { editor ->
                if (value.isNullOrBlank()) editor.remove(KEY_DEFAULT_PROVIDER)
                else editor.putString(KEY_DEFAULT_PROVIDER, value)
            }.apply()
        }

    private companion object {
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_FONT_SIZE = "editor_font_size"
        const val KEY_LANGUAGE = "language"
        const val KEY_DEFAULT_PROVIDER = "default_provider_id"
    }
}
