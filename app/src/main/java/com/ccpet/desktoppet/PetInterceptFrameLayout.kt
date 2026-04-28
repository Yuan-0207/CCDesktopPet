package com.ccpet.desktoppet

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * 悬浮桌宠根布局：拦截触摸，避免子级 [io.github.sceneview.SceneView] 先消费事件导致无法拖拽。
 */
class PetInterceptFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var dragTouchHandler: ((MotionEvent) -> Boolean)? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return dragTouchHandler != null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return dragTouchHandler?.invoke(event) ?: super.onTouchEvent(event)
    }
}
