package vn.autoview.usbcamera

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/** Small settings shortcut that does not intercept touches outside its button. */
class FloatingSettingsService : Service() {
    private var manager: WindowManager? = null
    private var button: TextView? = null

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val bubble = TextView(this).apply {
            text = "⚙"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xCC263746.toInt())
            contentDescription = "Mở cài đặt AutoView"
            setOnClickListener {
                startActivity(Intent(this@FloatingSettingsService, HomeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(HomeActivity.EXTRA_OPEN_SETTINGS, true)
                })
            }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            54.dp(), 54.dp(), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12.dp()
            y = 100.dp()
        }
        runCatching {
            manager?.addView(bubble, params)
            button = bubble
        }
    }

    override fun onDestroy() {
        button?.let { runCatching { manager?.removeView(it) } }
        button = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
