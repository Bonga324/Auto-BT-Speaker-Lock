package com.example.autobt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

object BluetoothHelper {
    const val TARGET_DEVICE_MAC = "F8:1B:04:AF:1F:59"
    const val TARGET_DEVICE_NAME = "Hisense HS2000"
    fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    fun hasConnectPermission(context: Context) = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    fun hasScanPermission(context: Context) = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    @SuppressLint("MissingPermission")
    fun target(context: Context): BluetoothDevice? = try {
        if (!hasConnectPermission(context)) null else adapter(context)?.getRemoteDevice(TARGET_DEVICE_MAC)
    } catch (_: Exception) { null }

    @SuppressLint("MissingPermission")
    fun isConnected(context: Context, callback: (Boolean) -> Unit) {
        val a = adapter(context)
        if (a == null || !a.isEnabled || !hasConnectPermission(context)) { callback(false); return }
        val ok = a.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val connected = try { proxy.connectedDevices.any { it.address.equals(TARGET_DEVICE_MAC, true) } }
                catch (_: Exception) { false }
                callback(connected)
                a.closeProfileProxy(BluetoothProfile.A2DP, proxy)
            }
            override fun onServiceDisconnected(profile: Int) { callback(false) }
        }, BluetoothProfile.A2DP)
        if (!ok) callback(false)
    }

    /** Hidden A2DP connect is not part of the public Android SDK. Reflection is best effort only. */
    @SuppressLint("MissingPermission", "DiscouragedPrivateApi")
    fun requestA2dpConnection(context: Context, callback: (String) -> Unit) {
        val a = adapter(context)
        val device = target(context)
        if (a == null || !hasConnectPermission(context) || device == null) {
            callback("Bluetooth permission or adapter unavailable")
            return
        }
        if (!a.isEnabled) { callback("Turn on Bluetooth first"); return }
        val ok = a.getProfileProxy(context.applicationContext, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                try {
                    if (proxy.connectedDevices.any { it.address.equals(TARGET_DEVICE_MAC, true) }) {
                        callback("Hisense already connected via A2DP")
                    } else {
                        val method = BluetoothA2dp::class.java.getDeclaredMethod("connect", BluetoothDevice::class.java)
                        method.isAccessible = true
                        val requested = method.invoke(proxy, device) as? Boolean ?: false
                        callback(if (requested) "A2DP connection requested; waiting for confirmation" else "Android rejected A2DP request; connect in Bluetooth settings")
                    }
                } catch (_: Exception) {
                    callback("Android blocked hidden A2DP connect; use Bluetooth settings to connect")
                } finally { a.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
            }
            override fun onServiceDisconnected(profile: Int) { callback("A2DP profile unavailable") }
        }, BluetoothProfile.A2DP)
        if (!ok) callback("Could not obtain A2DP profile")
    }
    fun openBluetoothSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
