package com.ccpet.desktoppet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

class PetForegroundService : Service() {

    // 统一管理悬浮窗视图操作，服务本身只负责生命周期和指令分发。
    private lateinit var petWindowManager: PetWindowManager

    override fun onCreate() {
        super.onCreate()
        petWindowManager = PetWindowManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 外部控制统一通过 action 路由（启动/停止/刷新设置）。
        when (intent?.action) {
            ACTION_START_PET -> startPet()
            ACTION_STOP_PET -> stopPet()
            ACTION_REFRESH_SETTINGS -> petWindowManager.refreshBehaviorSettings()
            else -> startPet()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        petWindowManager.hidePet()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPet() {
        // 新系统要求先进入前台服务，再展示悬浮窗（需通知权限时否则会抛异常）。
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
        if (!PetSettingsStore.isDesktopPetVisibleOnDesktop(this)) {
            petWindowManager.hidePet()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (Settings.canDrawOverlays(this)) {
            petWindowManager.showPet()
        } else {
            stopSelf()
        }
    }

    private fun stopPet() {
        // 先移除悬浮窗，再停止前台与服务，避免残留窗口。
        petWindowManager.hidePet()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        // Android 8+ 必须先创建通知渠道，前台通知才能正常展示。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "PetForegroundService"
        const val ACTION_START_PET = "com.ccpet.desktoppet.action.START_PET"
        const val ACTION_STOP_PET = "com.ccpet.desktoppet.action.STOP_PET"
        const val ACTION_REFRESH_SETTINGS = "com.ccpet.desktoppet.action.REFRESH_SETTINGS"
        private const val CHANNEL_ID = "pet_service_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
