package vn.autoview.usbcamera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        applyPersistentLandscape(context)
        try {
            context.startActivity(
                Intent(context, HomeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
        } catch (error: Exception) {
            Log.w("AutoViewBoot", "Background launch blocked; set AutoView as the default Home app.", error)
        }
    }
}
