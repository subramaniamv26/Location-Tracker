package com.example.locationtracker

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var serverUrl: EditText
    private lateinit var apiKey: EditText
    private lateinit var status: TextView

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            status.text = intent.getStringExtra(LocationService.EXTRA_STATUS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverUrl = findViewById(R.id.serverUrl)
        apiKey = findViewById(R.id.apiKey)
        status = findViewById(R.id.status)

        val prefs = Prefs(this)
        serverUrl.setText(prefs.serverUrl)
        apiKey.setText(prefs.apiKey)
        findViewById<TextView>(R.id.deviceId).text = "Device ID: ${prefs.deviceId}"

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            val url = serverUrl.text.toString().trim().trimEnd('/')
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(this, "Enter a valid server URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.serverUrl = url
            prefs.apiKey = apiKey.text.toString().trim()
            if (hasLocationPermission()) startTracking() else requestNeededPermissions()
        }

        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            stopService(Intent(this, LocationService::class.java))
            status.text = "Stopped"
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(LocationService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(statusReceiver)
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestNeededPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(perms.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != REQ_PERMS) return
        if (hasLocationPermission()) {
            startTracking()
        } else {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show()
        }
    }

    private fun startTracking() {
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        status.text = "Starting..."
    }

    companion object {
        private const val REQ_PERMS = 1
    }
}
