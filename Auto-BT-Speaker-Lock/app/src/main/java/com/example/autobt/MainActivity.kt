package com.example.autobt

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var unlocked = false
    private var changingSwitch = false
    private val found = mutableListOf<BluetoothDevice>()
    private val names = mutableListOf<String>()
    private lateinit var listAdapter: ArrayAdapter<String>
    private val refresh = object : Runnable {
        override fun run() {
            if (unlocked) {
                BluetoothHelper.isConnected(this@MainActivity) { connected ->
                    if (unlocked) runOnUiThread {
                        val status = findViewById<TextView>(R.id.statusText)
                        status.text = (if (connected) "Connected" else "Disconnected") +
                            (if (LockManager.isLocked(this@MainActivity)) " / Reconnect Mode ON" else "") + " - Hisense HS2000"
                    }
                }
                handler.postDelayed(this, 3_000)
            }
        }
    }
    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_FOUND || !BluetoothHelper.hasConnectPermission(context)) return
            val device = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            if (device != null && found.none { it.address == device.address }) {
                found.add(device)
                names.add("${device.name ?: "Unknown device"} - ${device.address}")
                listAdapter.notifyDataSetChanged()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockManager.prefs(this).edit().putString("my_speaker_name", BluetoothHelper.TARGET_DEVICE_NAME)
            .putString("my_speaker_mac", BluetoothHelper.TARGET_DEVICE_MAC).apply()
        showPinScreen()
    }
    private fun showPinScreen() {
        unlocked = false
        handler.removeCallbacks(refresh)
        setContentView(R.layout.activity_lock)
        val input = findViewById<EditText>(R.id.pinInput)
        val message = findViewById<TextView>(R.id.lockMessage)
        val button = findViewById<Button>(R.id.unlockButton)
        button.setOnClickListener {
            val remaining = LockManager.remainingSeconds(this)
            if (remaining > 0) { message.text = "Wrong Password. Try again in $remaining seconds"; return@setOnClickListener }
            if (LockManager.verifyPin(this, input.text.toString())) {
                unlocked = true
                showMainScreen()
            } else {
                val seconds = LockManager.remainingSeconds(this)
                message.text = if (seconds > 0) "Wrong Password. Locked for 60 seconds" else "Wrong Password"
                input.text.clear()
            }
        }
    }
    private fun permissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
            // ADVERTISE is not needed: this app never advertises its tablet.
        } else add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    private fun requestPermissionsIfNeeded() {
        val missing = permissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 100)
        else startMonitor()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && BluetoothHelper.hasConnectPermission(this)) startMonitor()
        else toast("Bluetooth permission required for monitoring")
    }
    private fun startMonitor() {
        try { startForegroundService(Intent(this, BluetoothReconnectService::class.java)) }
        catch (_: Exception) { toast("Could not start monitor. Check Bluetooth and notification permissions.") }
    }
    private fun showMainScreen() {
        setContentView(R.layout.activity_main)
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        findViewById<ListView>(R.id.deviceList).adapter = listAdapter
        val lockSwitch = findViewById<Switch>(R.id.lockSwitch)
        lockSwitch.isChecked = LockManager.isLocked(this)
        updateBanner()
        lockSwitch.setOnCheckedChangeListener { _, requested ->
            if (changingSwitch) return@setOnCheckedChangeListener
            askPassword("Admin password required", LockManager.ADMIN_PASSWORD, {
                LockManager.setLocked(this, requested)
                updateBanner()
                startMonitor()
            }, {
                changingSwitch = true
                lockSwitch.isChecked = LockManager.isLocked(this)
                changingSwitch = false
            })
        }
        findViewById<Button>(R.id.connectButton).setOnClickListener { connectTarget() }
        findViewById<Button>(R.id.disconnectButton).setOnClickListener {
            // The public Android SDK has no supported A2DP disconnect method.
            // Pause app retries, then let the owner disconnect in system settings.
            val action = {
                LockManager.setPaused(this, true)
                toast("Automatic reconnect paused. Disconnect in Android Bluetooth settings.")
                BluetoothHelper.openBluetoothSettings(this)
            }
            if (LockManager.isLocked(this)) askPassword("Admin password to pause", LockManager.ADMIN_PASSWORD, action)
            else action()
        }
        findViewById<Button>(R.id.scanButton).setOnClickListener { scanDevices() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            askPassword("Admin settings", LockManager.ADMIN_PASSWORD, { showSettings() })
        }
        findViewById<ListView>(R.id.deviceList).setOnItemClickListener { _, _, position, _ ->
            val device = found.getOrNull(position) ?: return@setOnItemClickListener
            if (device.address.equals(BluetoothHelper.TARGET_DEVICE_MAC, true)) connectTarget()
            else askPassword("Admin password to access another device", LockManager.ADMIN_PASSWORD, {
                if (LockManager.isLocked(this)) toast("Turn off reconnect mode first")
                else {
                    toast("Use system Bluetooth settings to pair/connect ${device.address}")
                    BluetoothHelper.openBluetoothSettings(this)
                }
            })
        }
        requestPermissionsIfNeeded()
        handler.post(refresh)
    }
    private fun updateBanner() {
        findViewById<TextView>(R.id.lockBanner).visibility = if (LockManager.isLocked(this)) View.VISIBLE else View.GONE
    }
    private fun connectTarget() {
        LockManager.setPaused(this, false)
        startMonitor()
        BluetoothHelper.requestA2dpConnection(this) { result -> runOnUiThread {
            toast(result)
            if (result.contains("blocked", true) || result.contains("rejected", true)) {
                AlertDialog.Builder(this).setTitle("Manual connection required")
                    .setMessage("Android does not permit this app to force an A2DP connection. Open Bluetooth settings and select the paired Hisense HS2000.")
                    .setPositiveButton("OPEN BLUETOOTH SETTINGS") { _, _ -> BluetoothHelper.openBluetoothSettings(this) }
                    .setNegativeButton("CANCEL", null).show()
            }
        } }
    }
    @SuppressLint("MissingPermission")
    private fun scanDevices() {
        val a = BluetoothHelper.adapter(this)
        if (a == null || !BluetoothHelper.hasScanPermission(this) || !BluetoothHelper.hasConnectPermission(this)) {
            requestPermissionsIfNeeded(); return
        }
        if (!a.isEnabled) { toast("Enable Bluetooth first"); return }
        found.clear(); names.clear(); listAdapter.notifyDataSetChanged()
        try {
            a.cancelDiscovery()
            if (!a.startDiscovery()) toast("Bluetooth scan could not start")
            else toast("Scanning for nearby Classic Bluetooth devices")
        } catch (_: Exception) { toast("Scanning unavailable") }
    }
    private fun showSettings() {
        val options = arrayOf("Resume auto reconnect", "Open system Bluetooth settings", "Master reset app preferences")
        AlertDialog.Builder(this).setTitle("Admin settings").setItems(options) { _, choice ->
            when (choice) {
                0 -> { LockManager.setPaused(this, false); startMonitor(); toast("Auto reconnect resumed") }
                1 -> BluetoothHelper.openBluetoothSettings(this)
                2 -> askPassword("Master reset password", LockManager.RESET_PASSWORD, {
                    LockManager.reset(this)
                    // Android bonding cannot be silently removed by a normal third-party app.
                    toast("App preferences reset. To unpair Hisense, use system Bluetooth settings.")
                    showPinScreen()
                })
            }
        }.show()
    }
    private fun askPassword(title: String, expected: String, onSuccess: () -> Unit, onCancel: () -> Unit = {}) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.setPadding(30, 20, 30, 20)
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("CONFIRM") { _, _ ->
                if (input.text.toString() == expected) onSuccess()
                else { toast("Wrong Password"); onCancel() }
            }.setNegativeButton("CANCEL") { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }.show()
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    override fun onStop() { super.onStop(); unlocked = false; handler.removeCallbacks(refresh) }
    override fun onStart() { super.onStart(); if (::listAdapter.isInitialized && !unlocked) showPinScreen() }
    override fun onDestroy() { handler.removeCallbacks(refresh); super.onDestroy() }
}
