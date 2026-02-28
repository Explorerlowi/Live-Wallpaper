package com.example.livewallpaper.paint.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.livewallpaper.R
import com.example.livewallpaper.paint.PaintActivity

/**
 * AI 绘画前台服务
 *
 * 在图片生成期间保持前台状态，并持有 WakeLock + WifiLock，
 * 防止系统因应用退到后台而休眠 CPU、关闭 WiFi，导致网络请求中断。
 * 该服务本身不执行生成逻辑，仅作为进程保活和网络访问保障。
 */
class ImageGenerationService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var currentSessionId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentSessionId = intent?.getStringExtra(PaintActivity.EXTRA_SESSION_ID)
        acquireLocks()
        val notification = buildProgressNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LiveWallpaper:ImageGeneration"
            ).apply {
                acquire(10 * 60 * 1000L) // 最长 10 分钟自动释放
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }

        try {
            @Suppress("DEPRECATION")
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "LiveWallpaper:ImageGeneration"
            ).apply {
                acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WifiLock", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
        wakeLock = null

        try {
            wifiLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WifiLock", e)
        }
        wifiLock = null
    }

    private fun buildProgressNotification(): Notification {
        return buildBaseNotification(currentSessionId)
            .setContentTitle(getString(R.string.paint_generation_notification_title))
            .setContentText(getString(R.string.paint_generation_notification_text))
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun buildBaseNotification(sessionId: String?): NotificationCompat.Builder {
        val openIntent = Intent(this, PaintActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            sessionId?.let { putExtra(PaintActivity.EXTRA_SESSION_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
    }

    companion object {
        private const val TAG = "ImageGenService"
        private const val CHANNEL_ID = "image_generation_channel"
        private const val NOTIFICATION_ID = 9001
        private const val RESULT_NOTIFICATION_ID = 9002

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(CHANNEL_ID) != null) return
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.paint_generation_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.paint_generation_channel_desc)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }

        fun start(context: Context, sessionId: String? = null) {
            try {
                val intent = Intent(context, ImageGenerationService::class.java).apply {
                    sessionId?.let { putExtra(PaintActivity.EXTRA_SESSION_ID, it) }
                }
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
            }
        }

        /**
         * 停止服务并发送结果通知
         * @param success true 表示生成成功，false 表示失败
         * @param message 可选的额外提示信息（失败原因等）
         * @param sessionId 关联的会话 ID，用于通知跳转时恢复对应会话
         */
        fun stopWithResult(context: Context, success: Boolean, message: String? = null, sessionId: String? = null) {
            try {
                context.stopService(Intent(context, ImageGenerationService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop foreground service", e)
            }
            showResultNotification(context, success, message, sessionId)
        }

        /**
         * 仅停止服务，不显示结果通知（用于用户主动取消）
         */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ImageGenerationService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop foreground service", e)
            }
        }

        private fun showResultNotification(context: Context, success: Boolean, message: String?, sessionId: String?) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val openIntent = Intent(context, PaintActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                sessionId?.let { putExtra(PaintActivity.EXTRA_SESSION_ID, it) }
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (success) {
                context.getString(R.string.paint_generation_notification_success)
            } else {
                context.getString(R.string.paint_generation_notification_failed)
            }

            val text = if (success) {
                context.getString(R.string.paint_generation_notification_success_text)
            } else {
                message ?: context.getString(R.string.paint_generation_notification_failed_text)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setTimeoutAfter(if (success) 5_000L else 10_000L)
                .build()

            manager.notify(RESULT_NOTIFICATION_ID, notification)
        }
    }
}
