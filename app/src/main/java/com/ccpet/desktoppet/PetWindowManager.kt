package com.ccpet.desktoppet

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.android.filament.Camera
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import kotlin.math.abs
import kotlin.random.Random

class PetWindowManager(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var petView: View? = null
    private var petSceneView: SceneView? = null
    /** SceneView 依赖 Lifecycle 才会注册 Choreographer 帧回调；悬浮窗无 Activity 生命周期，需自建。 */
    private var petSceneLifecycle: PetSceneOverlayLifecycle? = null
    private var petModelNode: ModelNode? = null
    private var isPetShowing = false
    private var isPerformingAction = false
    private var snapAnimator: ValueAnimator? = null
    private var isHalfHidden = false
    private var snappedEdge = SnapEdge.LEFT
    private var currentPetLayoutParams: WindowManager.LayoutParams? = null
    private var lastRandomAnimIndex = -1
    private var pendingOneShotEnd: Runnable? = null
    private var petMenuPopup: PopupWindow? = null
    private var currentInteractionSet: PetInteractionSet = PetInteractionsRegistry.forAsset(null)
    private var currentInteractionActions: List<PetInteractionAction> = emptyList()
    private var lastInteractionTriggerAtMs: Long = 0L

    private val delayedHalfHideRunnable = Runnable {
        val view = petView ?: return@Runnable
        val layoutParams = currentPetLayoutParams ?: return@Runnable
        performHalfHide(view, layoutParams)
    }

    /** 周期性随机切换 GLB 内嵌动画（若有多段）。 */
    private val randomGlRunnable = object : Runnable {
        override fun run() {
            if (!isPetShowing) return
            val node = petModelNode ?: return
            if (!isPerformingAction && currentInteractionSet.enableRandomAmbient && node.animationCount > 1) {
                var idx = Random.nextInt(node.animationCount)
                if (idx == lastRandomAnimIndex) {
                    idx = (idx + 1) % node.animationCount
                }
                lastRandomAnimIndex = idx
                petSceneView?.post {
                    if (!isPetShowing || petModelNode !== node) return@post
                    mainHandler.postDelayed({
                        if (!isPetShowing || petModelNode !== node) return@postDelayed
                        runCatching { node.playAnimation(idx, speed = 1f, loop = true) }
                    }, 34L)
                }
            }
            mainHandler.postDelayed(
                this,
                Random.nextLong(RANDOM_GL_MIN_INTERVAL_MS, RANDOM_GL_MAX_INTERVAL_MS)
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showPet() {
        if (isPetShowing) return

        val root = LayoutInflater.from(context).inflate(R.layout.view_pet, null) as PetInterceptFrameLayout
        val sceneHost = root.findViewById<FrameLayout>(R.id.petSceneHost)

        val sceneLifecycle = PetSceneOverlayLifecycle().also { petSceneLifecycle = it }
        val sceneView = SceneView(
            context = context.applicationContext,
            isOpaque = false,
            sharedLifecycle = sceneLifecycle.lifecycle
        ).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            cameraManipulator = null
            isClickable = false
            isFocusable = false
        }
        sceneHost.addView(sceneView)
        petSceneView = sceneView

        val dm = context.resources.displayMetrics
        val density = dm.density
        // WRAP_CONTENT + SurfaceView 在悬浮窗里常测出 0 尺寸，改为固定像素保证可见。
        val windowW = ((148 + 8 + 8) * density).toInt().coerceAtLeast(120)
        val windowH = ((176 + 8 + 8) * density).toInt().coerceAtLeast(160)

        val layoutParams = WindowManager.LayoutParams(
            windowW,
            windowH,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 120
            y = 280
        }
        currentPetLayoutParams = layoutParams

        var downRawX = 0f
        var downRawY = 0f
        var downX = 0
        var downY = 0
        var dragging = false
        var velocityTracker: VelocityTracker? = null

        val gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    if (!dragging) {
                        if (isHalfHidden) {
                            revealFromEdge(root, layoutParams)
                        }
                        showPetMenu(root)
                    }
                }
            }
        )

        root.dragTouchHandler = { event ->
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    cancelDelayedHalfHide()
                    restoreFromWeakInterference(root)
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downX = layoutParams.x
                    downY = layoutParams.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val moveX = (event.rawX - downRawX).toInt()
                    val moveY = (event.rawY - downRawY).toInt()
                    if (abs(moveX) > 6 || abs(moveY) > 6) {
                        dragging = true
                    }
                    val petW = petWidthPx(root, layoutParams)
                    val petH = maxOf(layoutParams.height, root.height.coerceAtLeast(1))
                    val screenW = dm.widthPixels
                    val screenH = dm.heightPixels
                    val minX = -petW / 2
                    val maxX = (screenW - petW / 2).coerceAtLeast(minX)
                    layoutParams.x = (downX + moveX).coerceIn(minX, maxX)
                    layoutParams.y = (downY + moveY).coerceIn(0, (screenH - petH).coerceAtLeast(0))
                    windowManager.updateViewLayout(root, layoutParams)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null
                    if (!dragging) {
                        if (isHalfHidden) {
                            revealFromEdge(root, layoutParams)
                        } else {
                            Toast.makeText(context, R.string.pet_click_hint, Toast.LENGTH_SHORT).show()
                            playRandomOneShotFromTap()
                        }
                    } else {
                        finishDragRelease(root, layoutParams, vx)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null
                    if (dragging) {
                        finishDragRelease(root, layoutParams, vx)
                    }
                    true
                }

                else -> false
            }
        }

        val modelAssetPath = PetSettingsStore.selectedModelAssetPath(context)
        currentInteractionSet = PetInteractionsRegistry.forAsset(modelAssetPath)
        currentInteractionActions = currentInteractionSet.coreActions
        val modelProfile = ModelDisplayProfiles.forAsset(modelAssetPath)
        sceneView.modelLoader.loadModelInstanceAsync(modelAssetPath) { instance ->
            mainHandler.post {
                if (!isPetShowing) return@post
                if (instance == null) {
                    Toast.makeText(context, R.string.pet_model_load_failed, Toast.LENGTH_LONG).show()
                    return@post
                }
                petModelNode?.let { sceneView.removeChildNode(it) }
                val node = ModelNode(
                    modelInstance = instance,
                    // 统一手动驱动动画，避免 autoAnimate 与手动 playAnimation 竞争导致并发崩溃。
                    autoAnimate = false,
                    scaleToUnits = modelProfile.desktopScaleToUnits,
                    centerOrigin = Position(x = 0f, y = 0f, z = 0f)
                )
                // 不同来源的 GLB 前向/立向可能不一致，这里做一次朝向修正，保证“正面朝镜头、站直”。如仍不正，再调常量。
                node.rotation = io.github.sceneview.math.Rotation(
                    x = modelProfile.desktopRotationX,
                    y = modelProfile.desktopRotationY,
                    z = modelProfile.desktopRotationZ
                )
                petModelNode = node
                sceneView.addChildNode(node)
                refreshInteractionActions(modelAssetPath, node)
                if (node.animationCount > 0 && currentInteractionSet.playIdleByDefault) {
                    val idleIdx = PetAnimationRunner.idleIndex(
                        animationCount = node.animationCount,
                        preferredIdleIndex = currentInteractionSet.idleAnimationIndex
                    )
                    petSceneView?.post idleStart@{
                        if (!isPetShowing || petModelNode !== node) return@idleStart
                        mainHandler.postDelayed({
                            if (!isPetShowing || petModelNode !== node) return@postDelayed
                            runCatching { node.playAnimation(idleIdx, speed = 1f, loop = true) }
                        }, 34L)
                    }
                    lastRandomAnimIndex = idleIdx
                }
                sceneView.post {
                    sceneView.cameraNode.apply {
                        position = Position(x = 0f, y = modelProfile.desktopCameraY, z = modelProfile.desktopCameraZ)
                        setProjection(
                            fovInDegrees = modelProfile.desktopCameraFov.toDouble(),
                            near = 0.01f,
                            far = 200f,
                            direction = Camera.Fov.VERTICAL
                        )
                    }
                }
                scheduleRandomGlAction()
            }
        }

        windowManager.addView(root, layoutParams)
        petView = root
        isPetShowing = true
        // 无 Activity ViewTree 时 SceneView 的 lifecycle 为 null 则永不 postFrameCallback，必须手动推到 RESUMED。
        sceneLifecycle.moveToResumed()
    }

    fun hidePet() {
        val view = petView ?: return
        if (isPetShowing) {
            petMenuPopup?.dismiss()
            petMenuPopup = null
            snapAnimator?.cancel()
            cancelDelayedHalfHide()
            mainHandler.removeCallbacks(randomGlRunnable)
            pendingOneShotEnd?.let { mainHandler.removeCallbacks(it) }
            pendingOneShotEnd = null
            petSceneLifecycle?.moveToDestroyed()
            petSceneLifecycle = null
            windowManager.removeView(view)
            petView = null
            petSceneView = null
            petModelNode = null
            isPetShowing = false
            isPerformingAction = false
            isHalfHidden = false
            currentPetLayoutParams = null
            lastRandomAnimIndex = -1
            currentInteractionSet = PetInteractionsRegistry.forAsset(null)
            currentInteractionActions = emptyList()
        }
    }

    fun refreshBehaviorSettings() {
        val view = petView ?: return
        if (!PetSettingsStore.isHalfHideEnabled(context)) {
            cancelDelayedHalfHide()
            if (isHalfHidden) {
                val layoutParams = currentPetLayoutParams ?: return
                revealFromEdge(view, layoutParams)
            } else {
                restoreFromWeakInterference(view)
            }
            return
        }

        if (isHalfHidden) {
            applyWeakInterference(view)
        } else {
            restoreFromWeakInterference(view)
        }
    }

    private fun scheduleRandomGlAction() {
        mainHandler.removeCallbacks(randomGlRunnable)
        mainHandler.postDelayed(
            randomGlRunnable,
            Random.nextLong(RANDOM_GL_MIN_INTERVAL_MS, RANDOM_GL_MAX_INTERVAL_MS)
        )
    }

    private fun playRandomOneShotFromTap() {
        val node = petModelNode ?: return
        if (node.animationCount <= 0) return
        pendingOneShotEnd?.let { mainHandler.removeCallbacks(it) }
        isPerformingAction = true
        val idx = PetAnimationRunner.randomOneShotIndex(node.animationCount)
        val durMs = PetAnimationRunner.oneShotDurationMs(node, idx)
        val sceneView = petSceneView
        if (sceneView == null) {
            isPerformingAction = false
            return
        }
        sceneView.post {
            if (!isPetShowing || petModelNode !== node) {
                isPerformingAction = false
                pendingOneShotEnd = null
                return@post
            }
            mainHandler.postDelayed({
                if (!isPetShowing || petModelNode !== node) {
                    isPerformingAction = false
                    pendingOneShotEnd = null
                    return@postDelayed
                }
                runCatching { node.playAnimation(idx, speed = 1f, loop = false) }
                    .onFailure {
                        isPerformingAction = false
                        return@postDelayed
                    }
                val end = Runnable {
                    isPerformingAction = false
                    pendingOneShotEnd = null
                    resumeLoopedAmbient(force = currentInteractionSet.resumeIdleAfterAction)
                }
                pendingOneShotEnd = end
                mainHandler.postDelayed(end, durMs + 120L)
            }, 34L)
        }
    }

    private fun resumeLoopedAmbient(force: Boolean = false) {
        if (!force && !currentInteractionSet.playIdleByDefault) return
        val node = petModelNode ?: return
        if (node.animationCount <= 0) return
        val idx = PetAnimationRunner.idleIndex(
            animationCount = node.animationCount,
            preferredIdleIndex = currentInteractionSet.idleAnimationIndex
        )
        petSceneView?.post {
            if (!isPetShowing || petModelNode !== node) return@post
            mainHandler.postDelayed({
                if (!isPetShowing || petModelNode !== node) return@postDelayed
                runCatching { node.playAnimation(idx, speed = 1f, loop = true) }
            }, 34L)
        }
        lastRandomAnimIndex = idx
    }

    private fun executeInteractionAction(action: PetInteractionAction) {
        val now = System.currentTimeMillis()
        if (now - lastInteractionTriggerAtMs < 220L) return
        lastInteractionTriggerAtMs = now
        if (isPerformingAction) return
        val node = petModelNode
        if (node == null || node.animationCount <= 0) {
            Toast.makeText(context, R.string.pet_interaction_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        val index = PetAnimationRunner.resolveInteractionIndex(
            animationCount = node.animationCount,
            preferredAnimationIndex = action.preferredAnimationIndex,
            fallbackPolicy = action.fallbackPolicy
        ) ?: run {
            Toast.makeText(context, R.string.pet_interaction_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        showInteractionToast(action)
        pendingOneShotEnd?.let { mainHandler.removeCallbacks(it) }
        isPerformingAction = true
        val durMs = runCatching { PetAnimationRunner.oneShotDurationMs(node, index) }.getOrElse {
            isPerformingAction = false
            Toast.makeText(context, R.string.pet_interaction_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        val sceneView = petSceneView
        if (sceneView == null) {
            isPerformingAction = false
            return
        }
        sceneView.post {
            if (!isPetShowing || petModelNode !== node) {
                isPerformingAction = false
                pendingOneShotEnd = null
                return@post
            }
            mainHandler.postDelayed({
                if (!isPetShowing || petModelNode !== node) {
                    isPerformingAction = false
                    pendingOneShotEnd = null
                    return@postDelayed
                }
                val played = runCatching {
                    node.playAnimation(index, speed = 1f, loop = false)
                }.isSuccess
                if (!played) {
                    isPerformingAction = false
                    Toast.makeText(context, R.string.pet_interaction_not_supported, Toast.LENGTH_SHORT).show()
                    return@postDelayed
                }
                val end = Runnable {
                    isPerformingAction = false
                    pendingOneShotEnd = null
                    resumeLoopedAmbient(force = currentInteractionSet.resumeIdleAfterAction)
                }
                pendingOneShotEnd = end
                mainHandler.postDelayed(end, durMs + 120L)
            }, 34L)
        }
    }

    private fun showPetMenu(anchor: View) {
        petMenuPopup?.dismiss()
        val themed = ContextThemeWrapper(context, R.style.Theme_CCDesktopPet)
        val panel = LayoutInflater.from(themed).inflate(R.layout.popup_pet_menu, null)

        val popup = PopupWindow(
            panel,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isTouchable = true
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = 24f
            }
        }

        val actions = currentInteractionActions
        val actionButtons = listOf(
            panel.findViewById<AppCompatButton>(R.id.petMenuBtnAction1),
            panel.findViewById<AppCompatButton>(R.id.petMenuBtnAction2),
            panel.findViewById<AppCompatButton>(R.id.petMenuBtnAction3)
        )
        actionButtons.forEachIndexed { index, button ->
            val action = actions.getOrNull(index)
            if (action == null) {
                button.visibility = View.GONE
                return@forEachIndexed
            }
            button.visibility = View.VISIBLE
            button.text = resolveActionTitle(action)
            button.setOnClickListener {
                executeInteractionAction(action)
                popup.dismiss()
            }
        }
        panel.findViewById<AppCompatButton>(R.id.petMenuBtnIdle).setOnClickListener {
            pendingOneShotEnd?.let { mainHandler.removeCallbacks(it) }
            pendingOneShotEnd = null
            isPerformingAction = false
            resumeLoopedAmbient()
            popup.dismiss()
        }
        panel.findViewById<AppCompatButton>(R.id.petMenuBtnHide).setOnClickListener {
            hideDesktopPet()
            popup.dismiss()
        }

        popup.setOnDismissListener { petMenuPopup = null }

        anchor.post {
            if (petView == null || !anchor.isAttachedToWindow) return@post
            val anchorW = if (anchor.width > 0) anchor.width else (anchor.layoutParams?.width ?: 0)
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val pw = panel.measuredWidth
            val pad = (4 * context.resources.displayMetrics.density).toInt()
            val xOff = if (anchorW > 0 && pw > 0) (anchorW - pw) / 2 else 0
            val yOff = -(panel.measuredHeight + pad)
            popup.showAsDropDown(anchor, xOff, yOff)
        }
        petMenuPopup = popup
    }

    private fun refreshInteractionActions(assetPath: String?, node: ModelNode) {
        val animationNames = PetAnimationRunner.extractAnimationNames(node)
        currentInteractionActions = PetInteractionsRegistry.buildActions(
            set = currentInteractionSet,
            animationCount = node.animationCount,
            animationNames = animationNames
        )
        val unnamed = PetInteractionsRegistry.collectUnnamedAnimations(
            set = currentInteractionSet,
            animationCount = node.animationCount,
            animationNames = animationNames
        )
        if (unnamed.isNotEmpty()) {
            val summary = unnamed.joinToString { (idx, name) -> "$idx:${name ?: "UNKNOWN"}" }
            Log.w("CCPetInteraction", "悬浮窗未命名动作，待确认后再展示。asset=$assetPath unknown=[$summary]")
        }
    }

    private fun resolveActionTitle(action: PetInteractionAction): String {
        return action.titleText
            ?: action.titleRes?.let(context::getString)
            ?: "动作${action.preferredAnimationIndex}"
    }

    private fun showInteractionToast(action: PetInteractionAction) {
        val msg = action.toastText ?: action.toastRes?.let(context::getString)
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideDesktopPet() {
        PetSettingsStore.setDesktopPetVisibleOnDesktop(context.applicationContext, false)
        context.startService(
            Intent(context, PetForegroundService::class.java)
                .setAction(PetForegroundService.ACTION_STOP_PET)
        )
    }

    private fun finishDragRelease(root: View, layoutParams: WindowManager.LayoutParams, velocityX: Float) {
        val petW = petWidthPx(root, layoutParams)
        val dm = context.resources.displayMetrics
        if (shouldFlingDismissToHide(velocityX, layoutParams.x, petW, dm.widthPixels, dm.density)) {
            hideDesktopPet()
            return
        }
        clampPetToScreen(root, layoutParams)
        windowManager.updateViewLayout(root, layoutParams)
        snapAnimator?.cancel()
        cancelDelayedHalfHide()
        restoreFromWeakInterference(root)
        isHalfHidden = false
        scheduleDelayedHalfHide()
    }

    private fun petWidthPx(view: View, layoutParams: WindowManager.LayoutParams): Int {
        if (view.width > 0) return view.width
        return layoutParams.width.coerceAtLeast(120)
    }

    private fun clampPetToScreen(view: View, layoutParams: WindowManager.LayoutParams) {
        val dm = context.resources.displayMetrics
        val petW = petWidthPx(view, layoutParams)
        val petH = maxOf(layoutParams.height, view.height.coerceAtLeast(1))
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        layoutParams.x = layoutParams.x.coerceIn(0, (screenW - petW).coerceAtLeast(0))
        layoutParams.y = layoutParams.y.coerceIn(0, (screenH - petH).coerceAtLeast(0))
    }

    private fun shouldFlingDismissToHide(
        velocityX: Float,
        x: Int,
        petW: Int,
        screenW: Int,
        density: Float
    ): Boolean {
        if (abs(velocityX) < FLING_DISMISS_MIN_VELOCITY_PX_S) return false
        val slop = (40f * density).toInt().coerceIn(12, 120)
        val nearLeft = x <= slop
        val nearRight = x >= screenW - petW - slop
        return (velocityX < 0f && nearLeft) || (velocityX > 0f && nearRight)
    }

    private fun revealFromEdge(view: View, layoutParams: WindowManager.LayoutParams) {
        cancelDelayedHalfHide()
        val screenWidth = context.resources.displayMetrics.widthPixels
        val petWidth = if (view.width > 0) view.width else (148 * context.resources.displayMetrics.density).toInt()
        val maxX = (screenWidth - petWidth).coerceAtLeast(0)
        val targetX = if (snappedEdge == SnapEdge.LEFT) 0 else maxX
        restoreFromWeakInterference(view)
        animateX(view, layoutParams, targetX, REVEAL_ANIM_DURATION_MS) {
            isHalfHidden = false
        }
    }

    private fun performHalfHide(view: View, layoutParams: WindowManager.LayoutParams) {
        updateSnappedEdgeFromLayout(view, layoutParams)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val petWidth = if (view.width > 0) view.width else (148 * context.resources.displayMetrics.density).toInt()
        val maxX = (screenWidth - petWidth).coerceAtLeast(0)
        val hiddenOffset = (petWidth - edgePeekWidthPx()).coerceAtLeast(0)
        val hiddenEdgeX = if (snappedEdge == SnapEdge.LEFT) -hiddenOffset else maxX + hiddenOffset
        animateX(view, layoutParams, hiddenEdgeX, HALF_HIDE_ANIM_DURATION_MS) {
            isHalfHidden = true
            applyWeakInterference(view)
        }
    }

    private fun scheduleDelayedHalfHide() {
        if (!PetSettingsStore.isHalfHideEnabled(context)) return
        mainHandler.removeCallbacks(delayedHalfHideRunnable)
        mainHandler.postDelayed(
            delayedHalfHideRunnable,
            PetSettingsStore.halfHideDelayMs(context).toLong()
        )
    }

    private fun cancelDelayedHalfHide() {
        mainHandler.removeCallbacks(delayedHalfHideRunnable)
    }

    private fun applyWeakInterference(view: View) {
        val weakAlpha = PetSettingsStore.weakAlphaPercent(context) / 100f
        view.animate()
            .alpha(weakAlpha)
            .setDuration(ALPHA_ANIM_DURATION_MS)
            .start()
    }

    private fun restoreFromWeakInterference(view: View) {
        view.animate()
            .alpha(1f)
            .setDuration(ALPHA_ANIM_DURATION_MS)
            .start()
    }

    private fun animateX(
        view: View,
        layoutParams: WindowManager.LayoutParams,
        targetX: Int,
        durationMs: Long,
        onEnd: (() -> Unit)? = null
    ) {
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = durationMs
            addUpdateListener { animator ->
                layoutParams.x = animator.animatedValue as Int
                windowManager.updateViewLayout(view, layoutParams)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    private fun edgePeekWidthPx(): Int {
        return (EDGE_PEEK_DP * context.resources.displayMetrics.density).toInt()
    }

    private fun updateSnappedEdgeFromLayout(view: View, layoutParams: WindowManager.LayoutParams) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val petWidth = petWidthPx(view, layoutParams)
        snappedEdge = if (layoutParams.x + petWidth / 2 < screenWidth / 2) {
            SnapEdge.LEFT
        } else {
            SnapEdge.RIGHT
        }
    }

    private enum class SnapEdge {
        LEFT, RIGHT
    }

    /**
     * 专供悬浮窗 [SceneView]：驱动其内部 [androidx.lifecycle.DefaultLifecycleObserver] 开始/停止渲染循环。
     */
    private class PetSceneOverlayLifecycle : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry

        fun moveToResumed() {
            if (registry.currentState == Lifecycle.State.DESTROYED) return
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun moveToDestroyed() {
            if (registry.currentState == Lifecycle.State.INITIALIZED) return
            if (registry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
            if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
            if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
        }
    }

    private companion object {
        private const val DESKTOP_MODEL_ASSET = "models/BabyDragon.glb"
        private const val DESKTOP_MODEL_SCALE_TO_UNITS = 0.082f
        private const val DESKTOP_CAMERA_Z = 18.0f
        private const val DESKTOP_CAMERA_Y = 0.06f
        private const val DESKTOP_CAMERA_FOV = 56f

        // 模型姿态修正（度）。若模型侧躺/背对镜头，可在这里微调。
        private const val DESKTOP_MODEL_ROTATION_X = 0f
        private const val DESKTOP_MODEL_ROTATION_Y = 0f
        private const val DESKTOP_MODEL_ROTATION_Z = 0f

        private const val RANDOM_GL_MIN_INTERVAL_MS = 4_000L
        private const val RANDOM_GL_MAX_INTERVAL_MS = 10_000L

        /** [VelocityTracker.computeCurrentVelocity] 单位 1000 时的横向速度下限（约 px/s），贴边用力甩动可关闭桌宠。 */
        private const val FLING_DISMISS_MIN_VELOCITY_PX_S = 1200f
        private const val HALF_HIDE_ANIM_DURATION_MS = 180L
        private const val REVEAL_ANIM_DURATION_MS = 180L
        private const val ALPHA_ANIM_DURATION_MS = 180L
        private const val EDGE_PEEK_DP = 28
    }
}
