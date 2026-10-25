package com.example.locationtracker

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

class Prefs(private val context: Context) {
    private val sp = context.getSharedPreferences("tracker", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("server_url", "") ?: ""
        set(v) = sp.edit().putString("server_url", v).apply()

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v).apply()

    /** Stable per-device, per-app identifier (Settings.Secure.ANDROID_ID). */
    val deviceId: String
        @SuppressLint("HardwareIds")
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
}
