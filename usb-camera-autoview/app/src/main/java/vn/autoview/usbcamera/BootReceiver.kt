package vn.autoview.usbcamera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Re-apply the saved system-wide landscape preference after reboot.
        // The rotation settings normally persist themselves; this also repairs
        // devices/ROMs that reset USER_ROTATION during startup.
        applyPersistentLandscape(context)
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        } catch (error: Exception) {
            Log.w("AutoViewBoot", "System blocked background launch; choose AutoView as Home app.", error)
        }
    }
}
