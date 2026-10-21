package dev.thegamer.btsnooze

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    val raw: SharedPreferences =
        context.applicationContext.getSharedPreferences("btsnooze", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = raw.getBoolean(KEY_ENABLED, false)
        set(value) = raw.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Devices the user has checked. */
    var blocked: Set<String>
        get() = raw.getStringSet(KEY_BLOCKED, emptySet())!!.toSet()
        set(value) = raw.edit().putStringSet(KEY_BLOCKED, value).apply()

    /** Devices we've actually set to FORBIDDEN, so we only ever restore what we changed. */
    var forbidden: Set<String>
        get() = raw.getStringSet(KEY_FORBIDDEN, emptySet())!!.toSet()
        set(value) = raw.edit().putStringSet(KEY_FORBIDDEN, value).apply()

    fun status(address: String): String? = raw.getString(KEY_STATUS + address, null)

    fun setStatus(address: String, status: String) =
        raw.edit().putString(KEY_STATUS + address, status).apply()

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_BLOCKED = "blocked"
        const val KEY_FORBIDDEN = "forbidden"
        const val KEY_STATUS = "status_"
    }
}
