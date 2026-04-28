package com.ccpet.desktoppet

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/**
 * 统计栈内 Activity：当最后一个 Activity 进入 onStop（且非配置变更）时启动桌宠。
 * 停止桌宠由各界面 [android.app.Activity.onResume] 触发（见 [PetDesktopLauncher.requestStopDesktopPet]），
 * 避免与 ProcessLifecycleOwner 竞态导致刚启动就被停掉。
 */
class CCDesktopPetApp : Application() {

    private val startedActivityCount = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                startedActivityCount.incrementAndGet()
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                val changing = activity.isChangingConfigurations
                val left = startedActivityCount.decrementAndGet()
                if (left == 0 && !changing) {
                    PetDesktopLauncher.requestStartDesktopPet(activity)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
