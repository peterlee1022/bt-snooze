package dev.thegamer.btsnooze

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BtEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pending = goAsync()
                Enforcer.syncAll(context) { pending.finish() }
            }
            else -> {
                if (!prefs.enabled) return
                if (intent.action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED &&
                    intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) != BluetoothProfile.STATE_CONNECTED
                ) return
                val device = deviceFrom(intent) ?: return
                if (device.address !in prefs.blocked) return
                // A blocked device got through (policy call unsupported, or it connected
                // before the policy stuck). Kick it off again.
                val pending = goAsync()
                Enforcer.apply(context, mapOf(device to true)) { pending.finish() }
            }
        }
    }

    private fun deviceFrom(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
