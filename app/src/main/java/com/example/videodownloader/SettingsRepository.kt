package com.example.videodownloader

import android.content.Context

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

object SettingsRepository {
    private const val PREFS_NAME = "app_settings_prefs"
    private const val KEY_CHUNK_COUNT = "chunk_count"
    private const val KEY_DOWNLOAD_LOCATION = "download_location"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_USE_INBUILT_PLAYER = "use_inbuilt_player"

    fun getChunkCount(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CHUNK_COUNT, 4)
    }

    fun setChunkCount(context: Context, count: Int) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CHUNK_COUNT, count).apply()
    }

    fun getDownloadLocation(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DOWNLOAD_LOCATION, "internal") ?: "internal"
    }

    fun setDownloadLocation(context: Context, location: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DOWNLOAD_LOCATION, location).apply()
    }

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try { ThemeMode.valueOf(name) } catch (_: Exception) { ThemeMode.SYSTEM }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getUseInbuiltPlayer(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USE_INBUILT_PLAYER, true)
    }

    fun setUseInbuiltPlayer(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_USE_INBUILT_PLAYER, enabled).apply()
    }
}
