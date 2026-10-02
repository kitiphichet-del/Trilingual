package com.trilingual.ai.service

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/** Uses the user-granted system overlay permission; never silently enables it. */
class SubtitleOverlayService : Service() {
    companion object {
        const val ACTION_SHOW = "overlay.show"
        const val ACTION_UPDATE = "overlay.update"
        const val ACTION_HIDE = "overlay.hide"
    }
    private var subtitle: TextView? = null
    private lateinit var window: WindowManager
    override fun onCreate() { super.onCreate(); window = getSystemService(WINDOW_SERVICE) as WindowManager }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> { close(); stopSelf() }
            ACTION_SHOW, ACTION_UPDATE -> {
                if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
                if (subtitle == null) show()
                subtitle?.text = intent.getStringExtra("subtitle") ?: "คำบรรยายสด • TriLingual AI"
            }
        }
        return START_NOT_STICKY
    }
    private fun show() {
        val view = TextView(this).apply {
            textSize = 19f
            setTextColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(0xD9000000.toInt()); cornerRadius = 22f }
        }
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP; y = 90 }
        window.addView(view, layout)
        subtitle = view
    }
    private fun close() { subtitle?.let { runCatching { window.removeView(it) } }; subtitle = null }
    override fun onDestroy() { close(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
