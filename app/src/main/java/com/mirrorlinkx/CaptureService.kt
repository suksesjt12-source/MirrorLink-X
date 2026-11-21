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

class CaptureService : Service() {
    private var projection: MediaProjection? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1, notification())
        val code = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data != null) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(code, data)
            // Consent and foreground-service lifecycle are established. Actual encoding and
            // network transmission are intentionally not part of this MVP.
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
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
