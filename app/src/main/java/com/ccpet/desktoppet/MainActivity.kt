package com.ccpet.desktoppet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.google.android.filament.Box
import com.google.android.filament.Camera
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import kotlin.math.tan

class MainActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var btnInteract: Button
    private lateinit var switchDesktopVisible: SwitchCompat
    private lateinit var titleText: TextView
    private lateinit var scenePetPreview: SceneView
    private lateinit var petContainer: FrameLayout
    private var petModelNode: ModelNode? = null

    /** 仅绕竖轴（Y）旋转，单位：度 */
    private var modelYawDegrees = 0f
    private var lastTouchX = 0f
    private var previewBaseRotationY = 0f
    private var previewRotationX = 0f
    private var previewRotationZ = 0f
    private var previewFitDebugShown = false
    private var currentPreviewProfile: ModelDisplayProfile? = null
    private var currentPreviewAssetPath: String? = null
    private var currentInteractionSet: PetInteractionSet = PetInteractionsRegistry.forAsset(null)
    private var currentInteractionActions: List<PetInteractionAction> = emptyList()
    private var isPerformingAction = false
    private var pendingOneShotEnd: Runnable? = null
    private var interactionPopup: PopupWindow? = null
    private var lastInteractionTriggerAtMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        titleText = findViewById(R.id.tvTitle)
        petContainer = findViewById(R.id.petContainer)
        val sceneHost = findViewById<FrameLayout>(R.id.scenePreviewHost)

        petContainer.doOnLayout {
            petContainer.clipToOutline = true
            petContainer.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }

        scenePetPreview = SceneView(
            context = this,
            isOpaque = false
        ).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        sceneHost.addView(scenePetPreview)

        scenePetPreview.cameraManipulator = null
        bindHorizontalSpinGesture()

        btnInteract = findViewById(R.id.btnInteract)
        val btnNurture = findViewById<Button>(R.id.btnNurture)
        val btnShop = findViewById<Button>(R.id.btnShop)
        val btnSettings = findViewById<Button>(R.id.btnSettings)

        setupDefaultModelPreview()

        btnInteract.setOnClickListener {
            if (isPerformingAction) return@setOnClickListener
            showInteractionDialog()
        }
        updateInteractButtonVisual()

        btnNurture.setOnClickListener {
            Toast.makeText(this, R.string.menu_nurture_todo, Toast.LENGTH_SHORT).show()
        }

        btnShop.setOnClickListener {
            startActivity(Intent(this, ShopActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        switchDesktopVisible = findViewById(R.id.switchDesktopVisible)

        maybeRequestNotificationPermission()
    }

    private fun bindDesktopVisibleSwitch() {
        val listener = object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
                if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays(this@MainActivity)
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.home_overlay_required_for_desktop,
                        Toast.LENGTH_LONG
                    ).show()
                    switchDesktopVisible.setOnCheckedChangeListener(null)
                    switchDesktopVisible.isChecked = false
                    switchDesktopVisible.setOnCheckedChangeListener(this)
                    return
                }
                PetSettingsStore.setDesktopPetVisibleOnDesktop(this@MainActivity, isChecked)
                if (!isChecked) {
                    PetDesktopLauncher.requestStopDesktopPet(this@MainActivity)
                }
            }
        }
        switchDesktopVisible.setOnCheckedChangeListener(null)
        var visible = PetSettingsStore.isDesktopPetVisibleOnDesktop(this)
        if (visible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            PetSettingsStore.setDesktopPetVisibleOnDesktop(this, false)
            visible = false
        }
        switchDesktopVisible.isChecked = visible
        switchDesktopVisible.setOnCheckedChangeListener(listener)
    }

    override fun onResume() {
        super.onResume()
        titleText.text = PetSettingsStore.petName(this)
        PetDesktopLauncher.requestStopDesktopPet(this)
        bindDesktopVisibleSwitch()
        // 前后台切换后统一从设置读取 profile，避免内存态与持久态不一致。
        rebindPreviewFromSettings()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PetDesktopLauncher.requestStartDesktopPet(this)
    }

    override fun onDestroy() {
        interactionPopup?.dismiss()
        interactionPopup = null
        pendingOneShotEnd?.let { mainHandler.removeCallbacks(it) }
        pendingOneShotEnd = null
        super.onDestroy()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            0
        )
    }

    private fun bindHorizontalSpinGesture() {
        scenePetPreview.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    lastTouchX = event.x
                    modelYawDegrees += dx * HORIZONTAL_YAW_SENSITIVITY
                    petModelNode?.rotation =
                        Rotation(
                            x = previewRotationX,
                            y = previewBaseRotationY + modelYawDegrees,
                            z = previewRotationZ
                        )
                    true
                }

                else -> true
            }
        }
    }

    private fun applyPreviewCamera(cameraZ: Float, cameraY: Float = 0f, fov: Float = PREVIEW_CAMERA_FOV) {
        scenePetPreview.cameraNode.apply {
            position = Position(x = 0f, y = cameraY, z = cameraZ)
            setProjection(
                fovInDegrees = fov.toDouble(),
                near = 0.01f,
                far = 2000f,
                direction = Camera.Fov.VERTICAL
            )
        }
    }

    private fun setupDefaultModelPreview() {
        val path = PetSettingsStore.selectedModelAssetPath(this)
        currentInteractionSet = PetInteractionsRegistry.forAsset(path)
        currentInteractionActions = currentInteractionSet.coreActions
        val profile = ModelDisplayProfiles.forAsset(path)
        currentPreviewProfile = profile
        currentPreviewAssetPath = profile.assetPath
        scenePetPreview.modelLoader.loadModelInstanceAsync(path) { instance ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (instance == null) {
                    Toast.makeText(
                        this,
                        getString(R.string.pet_model_load_failed),
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }
                petModelNode?.let { scenePetPreview.removeChildNode(it) }
                val node = ModelNode(
                    modelInstance = instance,
                    // 统一手动驱动动画，避免 autoAnimate 与手动 playAnimation 竞争导致并发崩溃。
                    autoAnimate = false,
                    scaleToUnits = profile.previewScaleToUnits,
                    centerOrigin = Position(x = 0f, y = 0f, z = 0f)
                )
                // 对 Meshy 这批模型，手动 scale 更稳定（避免依赖 glTF accessor bounds 导致缩放失效）。
                node.scale = Scale(profile.previewManualScale)
                node.position = Position(
                    x = profile.previewOffsetX,
                    y = profile.previewOffsetY,
                    z = profile.previewOffsetZ
                )
                previewBaseRotationY = profile.previewRotationY
                previewRotationX = profile.previewRotationX
                previewRotationZ = profile.previewRotationZ
                node.rotation =
                    Rotation(
                        x = previewRotationX,
                        y = previewBaseRotationY + modelYawDegrees,
                        z = previewRotationZ
                    )
                petModelNode = node
                scenePetPreview.addChildNode(node)
                refreshInteractionActions(path, node)
                if (node.animationCount > 0 && currentInteractionSet.playIdleByDefault) {
                    resumePreviewIdle()
                }
                scenePetPreview.post {
                    if (isFinishing || isDestroyed) return@post
                    applyPreviewCamera(
                        cameraZ = profile.previewCameraZ,
                        cameraY = profile.previewCameraY,
                        fov = profile.previewCameraFov
                    )
                }
            }
        }
    }

    private fun rebindPreviewFromSettings() {
        val selectedPath = PetSettingsStore.selectedModelAssetPath(this)
        currentInteractionSet = PetInteractionsRegistry.forAsset(selectedPath)
        currentInteractionActions = currentInteractionSet.coreActions
        val profile = ModelDisplayProfiles.forAsset(selectedPath)
        currentPreviewProfile = profile
        val node = petModelNode
        if (node == null || currentPreviewAssetPath != profile.assetPath) {
            setupDefaultModelPreview()
            return
        }
        scenePetPreview.post {
            if (isFinishing || isDestroyed) return@post
            reapplyCurrentPreviewProfile()
        }
    }

    private fun reapplyCurrentPreviewProfile() {
        val node = petModelNode ?: return
        val selectedPath = PetSettingsStore.selectedModelAssetPath(this)
        currentInteractionSet = PetInteractionsRegistry.forAsset(selectedPath)
        val profile = ModelDisplayProfiles.forAsset(selectedPath)
        currentPreviewProfile = profile
        currentPreviewAssetPath = profile.assetPath
        refreshInteractionActions(selectedPath, node)
        node.scale = Scale(profile.previewManualScale)
        node.position = Position(
            x = profile.previewOffsetX,
            y = profile.previewOffsetY,
            z = profile.previewOffsetZ
        )
        previewBaseRotationY = profile.previewRotationY
        previewRotationX = profile.previewRotationX
        previewRotationZ = profile.previewRotationZ
        node.rotation = Rotation(
            x = previewRotationX,
            y = previewBaseRotationY + modelYawDegrees,
            z = previewRotationZ
        )
        applyPreviewCamera(
            cameraZ = profile.previewCameraZ,
            cameraY = profile.previewCameraY,
            fov = profile.previewCameraFov
        )
    }

    private fun showInteractionDialog() {
        val anchor = findViewById<Button>(R.id.btnInteract)
        val actions = currentInteractionActions
        if (actions.isEmpty()) {
            Toast.makeText(this, R.string.pet_interaction_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        interactionPopup?.dismiss()
        val themed = ContextThemeWrapper(this, R.style.Theme_CCDesktopPet)
        val panel = LayoutInflater.from(themed).inflate(R.layout.popup_interaction_menu, null)
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
        val actionsContainer = panel.findViewById<LinearLayout>(R.id.interactionActionsContainer)
        actionsContainer.removeAllViews()
        actions.forEachIndexed { index, action ->
            val button = AppCompatButton(themed).apply {
                setBackgroundResource(R.drawable.bg_pet_menu_btn_primary)
                text = resolveActionTitle(action)
                setTextColor(android.graphics.Color.parseColor("#4A3D2E"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                minHeight = (23 * resources.displayMetrics.density).toInt()
                setPadding(
                    (8 * resources.displayMetrics.density).toInt(),
                    (2 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt(),
                    (2 * resources.displayMetrics.density).toInt()
                )
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                isAllCaps = false
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (1 * resources.displayMetrics.density).toInt()
                // 右侧为滚动条预留视觉安全区，避免按钮边框紧贴滑块。
                rightMargin = (9 * resources.displayMetrics.density).toInt()
                if (index < actions.lastIndex) {
                    bottomMargin = (4 * resources.displayMetrics.density).toInt()
                }
            }
            button.layoutParams = lp
            button.setOnClickListener {
                executeInteractionAction(action)
                popup.dismiss()
            }
            actionsContainer.addView(button)
        }
        popup.setOnDismissListener { interactionPopup = null }
        anchor.post {
            if (!anchor.isAttachedToWindow) return@post
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val pad = (4 * resources.displayMetrics.density).toInt()
            val yOff = -(panel.measuredHeight + anchor.height + pad)
            // 与互动按钮建立关联：锚定按钮左侧正上方弹出
            popup.showAsDropDown(anchor, 0, yOff)
        }
        interactionPopup = popup
    }

    private fun executeInteractionAction(action: PetInteractionAction) {
        val now = System.currentTimeMillis()
        if (now - lastInteractionTriggerAtMs < 220L) return
        lastInteractionTriggerAtMs = now
        if (isPerformingAction) return
        val node = petModelNode
        if (node == null || node.animationCount <= 0) {
            Toast.makeText(this, R.string.pet_interaction_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        val index = PetAnimationRunner.resolveInteractionIndex(
            animationCount = node.animationCount,
            preferredAnimationIndex = action.preferredAnimationIndex,
            fallbackPolicy = action.fallbackPolicy
        ) ?: run {
            Toast.makeText(this, R.string.pet_interaction_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        showInteractionToast(action)
        pendingOneShotEnd?.let { mainHandler.removeCallbacks(it) }
        isPerformingAction = true
        updateInteractButtonVisual()
        val durMs = runCatching { PetAnimationRunner.oneShotDurationMs(node, index) }.getOrElse {
            isPerformingAction = false
            updateInteractButtonVisual()
            Toast.makeText(this, R.string.pet_interaction_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        scenePetPreview.post {
            if (isFinishing || isDestroyed || petModelNode !== node) {
                isPerformingAction = false
                pendingOneShotEnd = null
                updateInteractButtonVisual()
                return@post
            }
            // 避开当前 onFrame 迭代窗口，延后一帧再切动画，规避 ModelNode 内部动画表并发修改。
            mainHandler.postDelayed({
                if (isFinishing || isDestroyed || petModelNode !== node) {
                    isPerformingAction = false
                    pendingOneShotEnd = null
                    updateInteractButtonVisual()
                    return@postDelayed
                }
                val played = runCatching {
                    node.playAnimation(index, speed = 1f, loop = false)
                }.isSuccess
                if (!played) {
                    isPerformingAction = false
                    updateInteractButtonVisual()
                    Toast.makeText(this, R.string.pet_interaction_not_supported, Toast.LENGTH_SHORT).show()
                    return@postDelayed
                }
                val end = Runnable {
                    isPerformingAction = false
                    pendingOneShotEnd = null
                    updateInteractButtonVisual()
                    resumePreviewIdle(force = currentInteractionSet.resumeIdleAfterAction)
                }
                pendingOneShotEnd = end
                mainHandler.postDelayed(end, durMs + 120L)
            }, 34L)
        }
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
            Log.w("CCPetInteraction", "未命名动作，待确认后再展示。asset=$assetPath unknown=[$summary]")
        }
    }

    private fun resolveActionTitle(action: PetInteractionAction): String {
        return action.titleText
            ?: action.titleRes?.let(::getString)
            ?: "动作${action.preferredAnimationIndex}"
    }

    private fun showInteractionToast(action: PetInteractionAction) {
        val msg = action.toastText ?: action.toastRes?.let(::getString)
        if (!msg.isNullOrBlank()) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateInteractButtonVisual() {
        if (!::btnInteract.isInitialized) return
        btnInteract.background = ContextCompat.getDrawable(this, R.drawable.bg_action_button)
        if (isPerformingAction) {
            btnInteract.text = getString(R.string.menu_interacting)
            btnInteract.isSelected = true
            btnInteract.isEnabled = false
            btnInteract.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        } else {
            btnInteract.text = getString(R.string.menu_interact)
            btnInteract.isSelected = false
            btnInteract.isEnabled = true
            btnInteract.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        btnInteract.refreshDrawableState()
        btnInteract.requestLayout()
        btnInteract.invalidate()
    }

    private fun resumePreviewIdle(force: Boolean = false) {
        if (!force && !currentInteractionSet.playIdleByDefault) return
        val node = petModelNode ?: return
        if (node.animationCount <= 0) return
        val idleIndex = PetAnimationRunner.idleIndex(
            animationCount = node.animationCount,
            preferredIdleIndex = currentInteractionSet.idleAnimationIndex
        )
        scenePetPreview.post {
            if (isFinishing || isDestroyed || petModelNode !== node) return@post
            mainHandler.postDelayed({
                if (isFinishing || isDestroyed || petModelNode !== node) return@postDelayed
                runCatching { node.playAnimation(idleIndex, speed = 1f, loop = true) }
            }, 34L)
        }
    }

    private fun scheduleFitPreview(node: ModelNode, triesLeft: Int) {
        scenePetPreview.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            if (petModelNode !== node) return@postDelayed
            val ok = fitPreviewToNode(node)
            if (!ok && triesLeft > 0) {
                scheduleFitPreview(node, triesLeft - 1)
            }
        }, 80L)
    }

    private fun fitPreviewToNode(node: ModelNode): Boolean {
        val bounds = computeWorldBounds(node) ?: run {
            // fallback：没有有效 AABB 时仍给一个保守取景
            applyPreviewCamera(cameraZ = 18f, cameraY = 0f)
            return false
        }
        // 1) 将“目标锚点”对齐到圆框中心 (0,0,0)
        // 用包围盒中心 + 一个按高度比例的下移偏置，让画面更像“圆框内视觉居中”（而非纯几何中心）。
        fun applyCenteringOnce(b: Bounds) {
            val targetX = b.center.x
            val targetY = b.center.y + (b.halfExtent.y * PREVIEW_CENTER_BIAS_Y)
            val targetZ = b.center.z
            node.worldPosition = Position(
                x = node.worldPosition.x - targetX,
                y = node.worldPosition.y - targetY,
                z = node.worldPosition.z - targetZ
            )
        }

        applyCenteringOnce(bounds)
        // 居中后再算一次 bounds，减少一次平移带来的误差（比无限迭代更稳也更便宜）
        val bounds2 = computeWorldBounds(node) ?: bounds
        applyCenteringOnce(bounds2)

        // 2) 取景：同时考虑横向/纵向（由视口宽高决定），取更远的那个，避免“还是太大”
        val b3 = computeWorldBounds(node) ?: bounds2
        val vfov = Math.toRadians(PREVIEW_CAMERA_FOV.toDouble()).toFloat()
        val viewW = scenePetPreview.width.coerceAtLeast(1)
        val viewH = scenePetPreview.height.coerceAtLeast(1)
        val aspect = viewW.toFloat() / viewH.toFloat()
        val hfov = (2f * kotlin.math.atan(tan(vfov / 2f) * aspect))
        val halfX = (b3.halfExtent.x * PREVIEW_FRAMING_PADDING).coerceAtLeast(0.05f)
        val halfY = (b3.halfExtent.y * PREVIEW_FRAMING_PADDING).coerceAtLeast(0.05f)
        val distX = halfX / tan(hfov / 2f)
        val distY = halfY / tan(vfov / 2f)
        val cameraZ = (maxOf(distX, distY) * PREVIEW_CAMERA_DISTANCE_MULT).coerceIn(6f, 2000f)
        applyPreviewCamera(cameraZ = cameraZ, cameraY = 0f)
        if (!previewFitDebugShown) {
            previewFitDebugShown = true
            Toast.makeText(
                this,
                "fit ok: z=${"%.2f".format(cameraZ)} a=${"%.2f".format(aspect)} b=${PREVIEW_CENTER_BIAS_Y} m=${PREVIEW_CAMERA_DISTANCE_MULT}",
                Toast.LENGTH_SHORT
            ).show()
        }
        return true
    }

    private data class Bounds(val center: Position, val halfExtent: Position)

    private fun computeWorldBounds(node: ModelNode): Bounds? {
        val rm = node.engine.renderableManager
        val box = Box()
        var hasAny = false
        var minX = 0f
        var minY = 0f
        var minZ = 0f
        var maxX = 0f
        var maxY = 0f
        var maxZ = 0f

        for (renderable in node.renderableNodes) {
            val inst = rm.getInstance(renderable.entity)
            if (inst == 0) continue
            rm.getAxisAlignedBoundingBox(inst, box)
            val he = box.halfExtent
            if (he[0] == 0f && he[1] == 0f && he[2] == 0f) continue
            val c = box.center

            val xs = floatArrayOf(c[0] - he[0], c[0] + he[0])
            val ys = floatArrayOf(c[1] - he[1], c[1] + he[1])
            val zs = floatArrayOf(c[2] - he[2], c[2] + he[2])

            var cx0 = 0f
            var cy0 = 0f
            var cz0 = 0f
            var cx1 = 0f
            var cy1 = 0f
            var cz1 = 0f
            var firstCorner = true

            for (x in xs) for (y in ys) for (z in zs) {
                val w = renderable.getWorldPosition(Position(x = x, y = y, z = z))
                if (firstCorner) {
                    firstCorner = false
                    cx0 = w.x; cy0 = w.y; cz0 = w.z
                    cx1 = w.x; cy1 = w.y; cz1 = w.z
                } else {
                    cx0 = minOf(cx0, w.x); cy0 = minOf(cy0, w.y); cz0 = minOf(cz0, w.z)
                    cx1 = maxOf(cx1, w.x); cy1 = maxOf(cy1, w.y); cz1 = maxOf(cz1, w.z)
                }
            }

            if (!hasAny) {
                hasAny = true
                minX = cx0; minY = cy0; minZ = cz0
                maxX = cx1; maxY = cy1; maxZ = cz1
            } else {
                minX = minOf(minX, cx0); minY = minOf(minY, cy0); minZ = minOf(minZ, cz0)
                maxX = maxOf(maxX, cx1); maxY = maxOf(maxY, cy1); maxZ = maxOf(maxZ, cz1)
            }
        }
        if (!hasAny) return null
        val center = Position(
            x = (minX + maxX) / 2f,
            y = (minY + maxY) / 2f,
            z = (minZ + maxZ) / 2f
        )
        val half = Position(
            x = (maxX - minX) / 2f,
            y = (maxY - minY) / 2f,
            z = (maxZ - minZ) / 2f
        )
        return Bounds(center = center, halfExtent = half)
    }

    private companion object {
        /**
         * 世界空间缩放（相对「塞进单位立方体」的基准）。
         * 注意：单纯调大该值却又把相机 Z 同比拉远时，透视下屏幕上的大小几乎不变；
         * 真正「变大」要靠拉近相机 / 收 FOV，而不是只堆 scale + Z。
         */
        const val MODEL_PREVIEW_SCALE_TO_UNITS = 0.082f
        // 预览最终手动缩放（确保“改了就生效”）
        const val PREVIEW_MANUAL_SCALE = 0.6f

        // 固定预览相机参数（不走动态拟合）
        const val PREVIEW_CAMERA_Z = 2.2f
        const val PREVIEW_CAMERA_Y = 0f

        const val PREVIEW_CAMERA_FOV = 56f
        // 模型缩小一半：相机距离整体 *2
        // 模型缩小一半：相机距离再 *2
        const val PREVIEW_CAMERA_DISTANCE_MULT = 52.0f
        /** 画面取景留白系数（>1 更保守、更小） */
        const val PREVIEW_FRAMING_PADDING = 1.25f
        /**
         * 视觉居中偏置（相对包围盒高度）。
         * 负值 = 模型往下放，让头顶留白更多（更符合“圆框中心”的观感）。
         */
        // 固定位置（相对圆框中心）
        const val PREVIEW_MODEL_OFFSET_X = 0f
        const val PREVIEW_MODEL_OFFSET_Y = -0.80f
        const val PREVIEW_MODEL_OFFSET_Z = -0.30f
        // 兼容旧自动拟合函数的常量（当前预览流程不再使用）
        const val PREVIEW_CENTER_BIAS_Y = 0f

        // 预览模型姿态修正（度）
        const val PREVIEW_MODEL_ROTATION_X = 0f
        const val PREVIEW_MODEL_ROTATION_Y = 180f
        const val PREVIEW_MODEL_ROTATION_Z = 0f

        const val HORIZONTAL_YAW_SENSITIVITY = 0.42f
    }
}
