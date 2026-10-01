package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

class ApiKeyRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("calorielens_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
        private const val KEY_SELECTED_MODEL = "selected_gemini_model"
    }

    fun getCustomApiKey(): String {
        return prefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""
    }

    fun setCustomApiKey(key: String) {
        prefs.edit().putString(KEY_CUSTOM_API_KEY, key.trim()).apply()
    }

    fun getActiveApiKey(): String {
        val custom = getCustomApiKey()
        if (custom.isNotBlank()) {
            return custom
        }
        val buildConfigKey = runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault("")
        return if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
            buildConfigKey
        } else {
            ""
        }
    }

    fun hasApiKey(): Boolean {
        return getActiveApiKey().isNotBlank()
    }

    fun getSelectedModel(): String {
        return prefs.getString(KEY_SELECTED_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash"
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
    }
}
