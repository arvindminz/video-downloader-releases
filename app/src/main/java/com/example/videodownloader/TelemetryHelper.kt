package com.example.videodownloader

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Lightweight first-launch telemetry reporter.
 * Sends a single ping on first launch to your Cloudflare Worker.
 * Cloudflare automatically attaches Country and City via GeoIP.
 */
object TelemetryHelper {
    private const val TAG = "TelemetryHelper"
    private const val PREFS_NAME = "telemetry_prefs"
    private const val KEY_REPORTED = "has_reported_install"
    private const val KEY_INSTALL_ID = "install_id"

    private const val SERVER_URL = "https://video-downloader-telemetry.arvindminz158.workers.dev/api/install"

    fun checkAndReportInstall(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasReported = prefs.getBoolean(KEY_REPORTED, false)

        if (!hasReported) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    var installId = prefs.getString(KEY_INSTALL_ID, null)
                    if (installId.isNullOrBlank()) {
                        installId = UUID.randomUUID().toString()
                        prefs.edit().putString(KEY_INSTALL_ID, installId).apply()
                    }

                    val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    val model = android.os.Build.MODEL
                    val deviceModel = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"

                    val jsonPayload = JSONObject().apply {
                        put("install_id", installId)
                        put("device_model", deviceModel)
                    }.toString()

                    val url = URL(SERVER_URL)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        connectTimeout = 5000
                        readTimeout = 5000
                    }

                    conn.outputStream.use { os ->
                        os.write(jsonPayload.toByteArray(Charsets.UTF_8))
                    }

                    val responseCode = conn.responseCode
                    if (responseCode == 200) {
                        prefs.edit().putBoolean(KEY_REPORTED, true).apply()
                        Log.d(TAG, "Install reported successfully to Cloudflare Worker")
                    } else {
                        Log.w(TAG, "Server responded with code $responseCode")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not report telemetry (will retry on next launch)", e)
                }
            }
        }
    }
}
