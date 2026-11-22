package com.example.autobt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val allowed = setOf(Intent.ACTION_BOOT_COMPLETED,
            "android.bluetooth.adapter.action.STATE_CHANGED", "android.bluetooth.device.action.ACL_DISCONNECTED")
        if (intent.action !in allowed || !BluetoothHelper.hasConnectPermission(context)) return
        try {
            val service = Intent(context, BluetoothReconnectService::class.java).setAction("RETRY_NOW")
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service)
            else context.startService(service)
        } catch (_: Exception) {
            // Modern Android may disallow background foreground-service starts.
            // Opening the app again will restart monitoring.
        }
    }
}
