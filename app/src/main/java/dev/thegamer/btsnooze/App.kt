package dev.thegamer.btsnooze

import android.app.Application
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Every hidden API we touch lives under android.bluetooth.
        HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/")
    }
}
