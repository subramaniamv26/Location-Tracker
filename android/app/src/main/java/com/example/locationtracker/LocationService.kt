package com.example.locationtracker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Foreground service: keeps GPS updates flowing and POSTs the latest
 * location to the backend every [INTERVAL_MS] (3 seconds).
 */
class LocationService : Service() {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var scheduler: ScheduledExecutorService? = null
    @Volatile private var lastLocation: Location? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { lastLocation = it }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission") // permission is checked in MainActivity before starting
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (scheduler != null) return START_STICKY

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MS)
            .build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        fused.lastLocation.addOnSuccessListener { if (it != null && lastLocation == null) lastLocation = it }

        scheduler = Executors.newSingleThreadScheduledExecutor().also {
            it.scheduleWithFixedDelay({ sendLatest() }, 0, INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        scheduler?.shutdownNow()
        scheduler = null
        super.onDestroy()
    }

    private fun sendLatest() {
        val loc = lastLocation
        if (loc == null) {
            publish("Waiting for GPS fix...")
            return
        }
        val prefs = Prefs(this)
        val timestamp = isoNow()
        val body = JSONObject()
            .put("device_id", prefs.deviceId)
            .put("latitude", loc.latitude)
            .put("longitude", loc.longitude)
            .put("accuracy", loc.accuracy.toDouble())
            .put("timestamp", timestamp)

        try {
            val conn = URL("${prefs.serverUrl}/api/location").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            if (prefs.apiKey.isNotEmpty()) conn.setRequestProperty("X-API-Key", prefs.apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            publish(
                "HTTP $code at $timestamp\n" +
                    String.format(Locale.US, "lat=%.6f  lon=%.6f\naccuracy=%.1f m",
                        loc.latitude, loc.longitude, loc.accuracy)
            )
        } catch (e: Exception) {
            publish("Send failed: ${e.message}")
        }
    }

    private fun publish(text: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, text))
    }

    private fun startInForeground() {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Location tracking", NotificationManager.IMPORTANCE_LOW)
            )
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle("Location Tracker")
            .setContentText("Sharing location every 3 seconds")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    companion object {
        const val INTERVAL_MS = 3000L
        const val ACTION_STATUS = "com.example.locationtracker.STATUS"
        const val EXTRA_STATUS = "status"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIF_ID = 1
    }
}
