package com.ccpet.desktoppet

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * 启动 / 停止桌面桌宠前台服务。
 * 启动应在「任务内最后一个 Activity 的 onStop」或 [android.app.Activity.onUserLeaveHint] 等仍属前台过渡的时机调用，
 * 避免 Android 12+ 在进程完全后台后再 startForegroundService 被拒。
 */
object PetDesktopLauncher {

    private const val TAG = "PetDesktopLauncher"
    private const val FGS_NOT_ALLOWED_CLASS =
        "android.app.ForegroundServiceStartNotAllowedException"

    fun requestStartDesktopPet(activityContext: Context) {
        val app = activityContext.applicationContext
        if (!PetSettingsStore.isDesktopPetVisibleOnDesktop(app)) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(activityContext)
        ) {
            return
        }
        val intent = Intent(app, PetForegroundService::class.java)
            .setAction(PetForegroundService.ACTION_START_PET)
        try {
            ContextCompat.startForegroundService(app, intent)
            Log.i(TAG, "startForegroundService dispatched")
        } catch (e: SecurityException) {
            Log.w(TAG, "startForegroundService failed", e)
            toastBlocked(activityContext)
        } catch (e: RuntimeException) {
            if (e.javaClass.name == FGS_NOT_ALLOWED_CLASS) {
                Log.w(TAG, "FGS not allowed", e)
                toastBlocked(activityContext)
            } else {
                throw e
            }
        }
    }

    fun requestStopDesktopPet(context: Context) {
        val app = context.applicationContext
        app.startService(
            Intent(app, PetForegroundService::class.java)
                .setAction(PetForegroundService.ACTION_STOP_PET)
        )
    }

    private fun toastBlocked(context: Context) {
        Toast.makeText(
            context,
            R.string.pet_desktop_start_blocked,
            Toast.LENGTH_LONG
        ).show()
    }
}
