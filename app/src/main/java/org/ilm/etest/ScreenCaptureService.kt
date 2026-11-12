package org.ilm.etest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopCapture(); return START_NOT_STICKY }
            ACTION_START -> {
                createChannel()
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else startForeground(NOTIFICATION_ID, notification)

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                    else @Suppress("DEPRECATION") (intent.getParcelableExtra(EXTRA_DATA) as? Intent)
                if (data != null) {
                    val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    projection = manager.getMediaProjection(resultCode, data)
                    projection?.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() { stopCapture() }
                    }, android.os.Handler(mainLooper))
                } else stopCapture()
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "ILM screen sharing", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("ILM ETest screen sharing")
            .setContentText("Screen-capture permission session is active. Nothing is being transmitted.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true).build()
    }

    private fun stopCapture() {
        projection?.stop()
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "org.ilm.etest.START_CAPTURE"
        const val ACTION_STOP = "org.ilm.etest.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        private const val CHANNEL_ID = "ilm_screen_capture"
        private const val NOTIFICATION_ID = 4102
    }
}
