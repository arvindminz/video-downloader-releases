package com.example.videodownloader

import android.content.Context

/**
 * Tracks app installation time and rating status.
 * Prompts user for a rating 1-2 days after installation
 * if they have not already submitted a rating.
 */
object RatingPromptHelper {
    private const val PREFS_NAME = "rating_prompt_prefs"
    private const val KEY_FIRST_LAUNCH_TIME = "first_launch_time"
    private const val KEY_HAS_RATED = "has_submitted_rating"
    
    // 1 day threshold (24 hours in milliseconds)
    private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L

    fun initAndCheckShouldPrompt(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // If user has already submitted a rating -> NEVER prompt again
        val hasRated = prefs.getBoolean(KEY_HAS_RATED, false)
        if (hasRated) return false

        // Record first launch timestamp if not present
        var firstLaunchTime = prefs.getLong(KEY_FIRST_LAUNCH_TIME, 0L)
        if (firstLaunchTime == 0L) {
            firstLaunchTime = System.currentTimeMillis()
            prefs.edit().putLong(KEY_FIRST_LAUNCH_TIME, firstLaunchTime).apply()
            return false // Installed just now, do not prompt on day 0
        }

        // Check if 1 day (24 hours) has elapsed since installation
        val elapsedTime = System.currentTimeMillis() - firstLaunchTime
        return elapsedTime >= ONE_DAY_MILLIS
    }

    fun markUserHasRated(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HAS_RATED, true).apply()
    }
}
