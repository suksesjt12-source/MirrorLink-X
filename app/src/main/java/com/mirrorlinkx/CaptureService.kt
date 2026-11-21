package com.mirrorlinkx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.os.Build
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.DisplayMetrics
import android.view.WindowManager

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1, notification())
        val code = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data != null) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(code, data)
            projection?.let { p ->
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
                // Capture is initialized as a proof-of-permission. A video encoder and LAN transport
                // will be added in the next milestone; no screen content is transmitted in this build.
                display = p.createVirtualDisplay("MirrorLinkX-preview", metrics.widthPixels, metrics.heightPixels,
                    metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, null)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        display?.release()
        projection?.stop()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("mirrorlink_capture", "MirrorLink X capture", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun notification(): Notification {
        return if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "mirrorlink_capture")
            .setContentTitle("MirrorLink X").setContentText("Screen capture permission is active")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        else @Suppress("DEPRECATION") Notification.Builder(this)
            .setContentTitle("MirrorLink X").setContentText("Screen capture active")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
    }
}
