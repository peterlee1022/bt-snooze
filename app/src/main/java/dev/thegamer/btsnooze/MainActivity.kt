package dev.thegamer.btsnooze

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

@SuppressLint("MissingPermission", "UseSwitchCompatOrMaterialCode")
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var master: Switch
    private lateinit var deviceList: LinearLayout

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.KEY_ENABLED || key?.startsWith(Prefs.KEY_STATUS) == true) runOnUiThread { render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        master = findViewById(R.id.master)
        deviceList = findViewById(R.id.devices)

        master.setOnCheckedChangeListener { _, checked ->
            if (checked == prefs.enabled) return@setOnCheckedChangeListener
            prefs.enabled = checked
            Enforcer.syncAll(this)
        }
        findViewById<Button>(R.id.grant).setOnClickListener { requestPermission() }
        findViewById<Button>(R.id.bt_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.raw.registerOnSharedPreferenceChangeListener(prefsListener)
        render()
    }

    override fun onPause() {
        prefs.raw.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

    private fun hasPermission() = Build.VERSION.SDK_INT < 31 ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= 31) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun render() {
        val granted = hasPermission()
        findViewById<View>(R.id.permission_box).visibility = if (granted) View.GONE else View.VISIBLE
        findViewById<View>(R.id.content).visibility = if (granted) View.VISIBLE else View.GONE
        if (!granted) return

        master.isChecked = prefs.enabled
        deviceList.removeAllViews()

        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val devices = runCatching { adapter?.bondedDevices }.getOrNull().orEmpty()
            .sortedBy { (it.alias ?: it.name ?: it.address).lowercase() }
        if (devices.isEmpty()) {
            deviceList.addView(TextView(this).apply {
                setText(if (adapter?.isEnabled == false) "Bluetooth is off." else getString(R.string.no_devices))
                alpha = 0.7f
            })
            return
        }
        for (device in devices) deviceList.addView(row(device))
    }

    private fun row(device: BluetoothDevice): View {
        val view = layoutInflater.inflate(R.layout.row_device, deviceList, false)
        val blocked = device.address in prefs.blocked
        view.findViewById<TextView>(R.id.name).text = device.alias ?: device.name ?: device.address
        view.findViewById<CheckBox>(R.id.check).isChecked = blocked
        val status = view.findViewById<TextView>(R.id.status)
        val last = prefs.status(device.address)
        status.text = last ?: device.address
        view.setOnClickListener {
            val nowBlocked = device.address !in prefs.blocked
            prefs.blocked = if (nowBlocked) prefs.blocked + device.address else prefs.blocked - device.address
            if (prefs.enabled) {
                Enforcer.apply(this, mapOf(device to nowBlocked))
            }
            render()
        }
        return view
    }
}
