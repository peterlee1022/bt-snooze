package dev.thegamer.btsnooze

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Applies or lifts a block on paired devices.
 *
 * Blocking sets the per-profile connection policy to FORBIDDEN (the same switch as unticking
 * "Media audio" / "Phone calls" in the device's system settings), which stops auto-connect while
 * keeping the bond. It also disconnects the device in case it's already connected. Both calls are
 * hidden APIs, so we go through reflection and record whether they worked.
 */
@SuppressLint("MissingPermission")
object Enforcer {
    private const val TAG = "BtSnooze"
    private const val POLICY_ALLOWED = 100
    private const val POLICY_FORBIDDEN = 0
    private const val HID_HOST = 4
    private const val TIMEOUT_MS = 6000L

    private val profiles: List<Int> = buildList {
        add(BluetoothProfile.HEADSET)
        add(BluetoothProfile.A2DP)
        add(HID_HOST)
        add(BluetoothProfile.HEARING_AID)
        if (Build.VERSION.SDK_INT >= 33) add(BluetoothProfile.LE_AUDIO)
    }

    /** Brings every paired device in line with the current prefs. */
    fun syncAll(context: Context, onDone: () -> Unit = {}) {
        val prefs = Prefs(context)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val bonded = runCatching { adapter?.bondedDevices }.getOrNull()
        if (adapter == null || !adapter.isEnabled || bonded == null) {
            onDone()
            return
        }
        val target = if (prefs.enabled) prefs.blocked else emptySet()
        val changes = bonded
            .filter { it.address in target || it.address in prefs.forbidden }
            .associateWith { it.address in target }
        apply(context, changes, onDone)
    }

    /** [changes] maps each device to true (block) or false (unblock). */
    fun apply(context: Context, changes: Map<BluetoothDevice, Boolean>, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || changes.isEmpty()) {
            onDone()
            return
        }

        val results = changes.keys.associate { it.address to Result() }
        val finished = AtomicBoolean(false)
        val pending = AtomicInteger(profiles.size)
        val handler = Handler(Looper.getMainLooper())

        val finish = Runnable {
            if (!finished.compareAndSet(false, true)) return@Runnable
            val prefs = Prefs(app)
            val forbidden = prefs.forbidden.toMutableSet()
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
            for ((device, block) in changes) {
                val r = results.getValue(device.address)
                if (block) forbidden += device.address else forbidden -= device.address
                prefs.setStatus(device.address, r.describe(block, time))
            }
            prefs.forbidden = forbidden
            onDone()
        }
        val profileDone = { if (pending.decrementAndGet() == 0) handler.post(finish) }
        handler.postDelayed(finish, TIMEOUT_MS)

        for (profile in profiles) {
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                    try {
                        for ((device, block) in changes) {
                            val r = results.getValue(device.address)
                            r.record(setPolicy(proxy, device, if (block) POLICY_FORBIDDEN else POLICY_ALLOWED), policy = true)
                            r.record(invoke(proxy, if (block) "disconnect" else "connect", device), policy = false)
                        }
                    } finally {
                        runCatching { adapter.closeProfileProxy(p, proxy) }
                        profileDone()
                    }
                }

                override fun onServiceDisconnected(p: Int) {}
            }
            val bound = runCatching { adapter.getProfileProxy(app, listener, profile) }.getOrDefault(false)
            if (!bound) profileDone()
        }
    }

    private fun setPolicy(proxy: BluetoothProfile, device: BluetoothDevice, policy: Int): Outcome {
        val viaPolicy = invoke(proxy, "setConnectionPolicy", device, policy)
        if (viaPolicy is Outcome.Ok) return viaPolicy
        // Pre-Android 11 name for the same thing; also worth a try if the newer one is locked down.
        val viaPriority = invoke(proxy, "setPriority", device, policy)
        return if (viaPriority is Outcome.Ok) viaPriority else viaPolicy
    }

    private fun invoke(proxy: BluetoothProfile, name: String, device: BluetoothDevice, arg: Int? = null): Outcome {
        return try {
            val result = if (arg == null) {
                proxy.javaClass.getMethod(name, BluetoothDevice::class.java).invoke(proxy, device)
            } else {
                proxy.javaClass.getMethod(name, BluetoothDevice::class.java, Int::class.javaPrimitiveType)
                    .invoke(proxy, device, arg)
            }
            if (result == false) Outcome.Refused else Outcome.Ok
        } catch (e: NoSuchMethodException) {
            Outcome.Missing
        } catch (e: Throwable) {
            val cause = (e as? InvocationTargetException)?.targetException ?: e
            Log.w(TAG, "$name failed on ${proxy.javaClass.simpleName}", cause)
            Outcome.Error(cause.javaClass.simpleName)
        }
    }

    private sealed interface Outcome {
        data object Ok : Outcome
        /** Call went through but returned false, usually because the device doesn't use that profile. */
        data object Refused : Outcome
        data object Missing : Outcome
        data class Error(val type: String) : Outcome
    }

    private class Result {
        var policyOk = false
        var actionOk = false
        var policyError: String? = null
        var actionError: String? = null

        @Synchronized
        fun record(outcome: Outcome, policy: Boolean) {
            if (outcome is Outcome.Ok) {
                if (policy) policyOk = true else actionOk = true
            } else if (outcome is Outcome.Error) {
                if (policy) policyError = outcome.type else actionError = outcome.type
            }
        }

        @Synchronized
        fun describe(block: Boolean, time: String): String {
            val verb = if (block) "Blocked" else "Unblocked"
            val policy = when {
                policyOk -> "auto-connect ${if (block) "off" else "restored"}"
                else -> "couldn't change auto-connect (${policyError ?: "no profile accepted it"})"
            }
            val action = when {
                actionOk -> if (block) "disconnected" else "reconnecting"
                actionError != null -> "${if (block) "disconnect" else "connect"} denied ($actionError)"
                else -> null
            }
            return listOfNotNull("$verb $time", policy, action).joinToString(" · ")
        }
    }
}
