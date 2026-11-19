package com.example.videodownloader

import android.content.Context

object SettingsRepository {
    private const val PREFS_NAME = "app_settings_prefs"
    private const val KEY_CHUNK_COUNT = "chunk_count"
    private const val KEY_DOWNLOAD_LOCATION = "download_location" // "internal" or "sdcard"

    fun getChunkCount(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CHUNK_COUNT, 4) // Default is 4 chunks
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
}
