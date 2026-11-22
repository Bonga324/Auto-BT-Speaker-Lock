package com.example.autobt

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class BluetoothReconnectService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private val poll = object : Runnable {
        override fun run() {
            if (!active) return
            if (!LockManager.isPaused(this@BluetoothReconnectService) && BluetoothHelper.hasConnectPermission(this@BluetoothReconnectService)) {
                BluetoothHelper.isConnected(this@BluetoothReconnectService) { connected ->
                    if (active && !connected && !LockManager.isPaused(this@BluetoothReconnectService)) {
                        BluetoothHelper.requestA2dpConnection(this@BluetoothReconnectService) { /* best effort */ }
                    }
                }
            }
            handler.postDelayed(this, if (LockManager.isLocked(this@BluetoothReconnectService)) 5_000L else 10_000L)
        }
    }
    override fun onCreate() {
        super.onCreate()
        getSharedPreferences("speaker_lock_prefs", MODE_PRIVATE).edit()
            .putString("my_speaker_name", BluetoothHelper.TARGET_DEVICE_NAME)
            .putString("my_speaker_mac", BluetoothHelper.TARGET_DEVICE_MAC).apply()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("bt_lock", "Speaker connection monitoring", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "bt_lock")
            .setContentTitle("Auto BT Speaker Lock")
            .setContentText("Monitoring Hisense HS2000 A2DP connection")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(11, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(11, notification)
        active = true
        handler.post(poll)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "RETRY_NOW" && active) {
            handler.removeCallbacks(poll)
            handler.post(poll)
        }
        return START_STICKY
    }
    override fun onDestroy() { active = false; handler.removeCallbacks(poll); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
