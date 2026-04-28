package com.ccpet.desktoppet

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.filament.Camera
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode

class ShopActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: ShopItemAdapter
    private lateinit var previewOverlay: View
    private lateinit var previewTitle: TextView
    private lateinit var previewPlaceholder: TextView
    private lateinit var previewPrimaryButton: MaterialButton
    private lateinit var previewCloseButton: MaterialButton
    private lateinit var previewSceneHost: FrameLayout
    private var previewSceneView: SceneView? = null
    private var previewModelNode: ModelNode? = null
    private var previewItem: ShopItem? = null
    private var previewBaseRotationX = 0f
    private var previewBaseRotationY = 0f
    private var previewBaseRotationZ = 0f
    private var previewYawDegrees = 0f
    private var previewLastTouchX = 0f

    private val categories = listOf(
        ShopCategory("owned", "已拥有"),
        ShopCategory("recommended", "推荐"),
        ShopCategory("xianxia", "仙侠"),
        ShopCategory("animal", "动物"),
        ShopCategory("car", "汽车"),
        ShopCategory("building", "建筑")
    )

    private val items = mutableListOf<ShopItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

        tabLayout = findViewById(R.id.tabShopCategory)
        recyclerView = findViewById(R.id.rvShopItems)
        emptyView = findViewById(R.id.tvShopEmpty)
        previewOverlay = findViewById(R.id.layoutShopPreviewOverlay)
        previewTitle = findViewById(R.id.tvShopPreviewTitle)
        previewPlaceholder = findViewById(R.id.tvShopPreviewPlaceholder)
        previewPrimaryButton = findViewById(R.id.btnShopPreviewPrimary)
        previewCloseButton = findViewById(R.id.btnShopPreviewClose)
        previewSceneHost = findViewById(R.id.shopPreviewSceneHost)
        findViewById<View>(R.id.btnShopBack).setOnClickListener { finish() }

        previewOverlay.setOnClickListener { hidePreviewOverlay() }
        previewCloseButton.setOnClickListener { hidePreviewOverlay() }
        previewPrimaryButton.setOnClickListener {
            previewItem?.let { onPrimaryAction(it) }
        }

        seedItems()
        syncEquippedWithSettings()

        adapter = ShopItemAdapter(
            onPrimaryAction = { item -> onPrimaryAction(item) },
            onPreview = { item -> showPreviewOverlay(item) },
            resolvePreviewResId = { item -> item.previewResId },
            resolvePreviewImageTransform = { item ->
                val path = item.assetPath
                if (path.isNullOrBlank()) {
                    ShopPreviewImageTransform.DEFAULT
                } else {
                    val profile = ModelDisplayProfiles.forAsset(path)
                    ShopPreviewImageTransform(
                        zoom = profile.shopImageZoom,
                        offsetXDp = profile.shopImageOffsetXDp,
                        offsetYDp = profile.shopImageOffsetYDp
                    )
                }
            }
        )
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter

        categories.forEach { tabLayout.addTab(tabLayout.newTab().setText(it.title)) }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = refreshBySelectedTab()
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = refreshBySelectedTab()
        })
        tabLayout.getTabAt(0)?.select()
        refreshBySelectedTab()
    }

    override fun onResume() {
        super.onResume()
        syncEquippedWithSettings()
        refreshBySelectedTab()
        if (previewOverlay.isVisible) {
            previewItem?.let { bindPreviewState(it) }
        }
    }

    private fun seedItems() {
        items.clear()
        items += ShopItem(
            id = "default_model",
            title = "默认桌宠",
            categoryId = "xianxia",
            tag = "免费",
            assetPath = "models/DefaultModel.glb",
            previewResId = R.drawable.preview_default_model,
            isFree = false,
            price = 68,
            owned = true,
            equipped = false
        )
        items += ShopItem(
            id = "baby_dragon",
            title = "小龙崽",
            categoryId = "animal",
            tag = "精品",
            assetPath = "models/BabyDragon.glb",
            previewResId = R.drawable.preview_baby_dragon,
            isFree = true,
            price = 0,
            owned = true,
            equipped = false
        )
        items += ShopItem(
            id = "xianxia_skin_1",
            title = "仙侠主题一",
            categoryId = "xianxia",
            tag = "主题",
            assetPath = null,
            previewResId = null,
            isFree = false,
            price = 88,
            owned = false,
            equipped = false
        )
        items += ShopItem(
            id = "sport_car",
            title = "跑车外观",
            categoryId = "car",
            tag = "载具",
            assetPath = null,
            previewResId = null,
            isFree = false,
            price = 128,
            owned = false,
            equipped = false
        )
        items += ShopItem(
            id = "city_tower",
            title = "城市地标",
            categoryId = "building",
            tag = "建筑",
            assetPath = null,
            previewResId = null,
            isFree = true,
            price = 0,
            owned = false,
            equipped = false
        )
    }

    private fun syncEquippedWithSettings() {
        val selected = PetSettingsStore.selectedModelAssetPath(this)
        items.forEach { it.equipped = it.assetPath != null && it.assetPath == selected }
    }

    private fun refreshBySelectedTab() {
        val category = categories.getOrNull(tabLayout.selectedTabPosition) ?: categories.first()
        val list = when (category.id) {
            "owned" -> items.filter { it.owned || it.equipped }
            "recommended" -> items.filter { it.id == "baby_dragon" || it.id == "default_model" || it.id == "xianxia_skin_1" }
            else -> items.filter { it.categoryId == category.id }
        }
        adapter.submit(list)
        emptyView.isVisible = list.isEmpty()
        recyclerView.isVisible = list.isNotEmpty()
    }

    private fun onPrimaryAction(item: ShopItem) {
        when {
            item.equipped -> Toast.makeText(this, R.string.shop_action_equipped, Toast.LENGTH_SHORT).show()
            item.owned || item.isFree -> useItem(item)
            else -> purchaseItem(item)
        }
    }

    private fun purchaseItem(item: ShopItem) {
        item.owned = true
        Toast.makeText(this, R.string.shop_buy_todo, Toast.LENGTH_SHORT).show()
        if (item.assetPath != null) {
            useItem(item)
        } else {
            refreshBySelectedTab()
        }
    }

    private fun useItem(item: ShopItem) {
        val assetPath = item.assetPath
        if (assetPath.isNullOrBlank()) {
            Toast.makeText(this, R.string.shop_preview_todo, Toast.LENGTH_SHORT).show()
            return
        }
        PetSettingsStore.setSelectedModelAssetPath(this, assetPath)
        syncEquippedWithSettings()
        refreshBySelectedTab()
        previewItem?.let { bindPreviewState(it) }
        Toast.makeText(this, getString(R.string.shop_use_success, item.title), Toast.LENGTH_SHORT).show()
    }

    private fun showPreviewOverlay(item: ShopItem) {
        previewOverlay.isVisible = true
        previewItem = item
        bindPreviewState(item)
    }

    private fun hidePreviewOverlay() {
        clearPreviewModel()
        previewPlaceholder.isVisible = true
        previewItem = null
        previewOverlay.isVisible = false
    }

    private fun bindPreviewState(item: ShopItem) {
        previewTitle.text = item.title
        updatePreviewPrimaryButton(item)
        val assetPath = item.assetPath
        if (assetPath.isNullOrBlank()) {
            clearPreviewModel()
            previewPlaceholder.isVisible = true
            return
        }
        val profile = ModelDisplayProfiles.forAsset(assetPath)
        previewPlaceholder.isVisible = false
        val sceneView = ensurePreviewSceneView()
        sceneView.modelLoader.loadModelInstanceAsync(assetPath) { instance ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (previewItem?.assetPath != assetPath) return@runOnUiThread
                if (instance == null) {
                    clearPreviewModel()
                    previewPlaceholder.isVisible = true
                    return@runOnUiThread
                }
                clearPreviewModel()
                val node = ModelNode(
                    modelInstance = instance,
                    autoAnimate = true,
                    scaleToUnits = profile.shopScaleToUnits,
                    centerOrigin = Position(x = 0f, y = 0f, z = 0f)
                )
                node.scale = Scale(profile.shopManualScale)
                node.position = Position(
                    x = profile.shopOffsetX,
                    y = profile.shopOffsetY,
                    z = profile.shopOffsetZ
                )
                node.rotation = Rotation(
                    x = profile.shopRotationX,
                    y = profile.shopRotationY,
                    z = profile.shopRotationZ
                )
                previewBaseRotationX = profile.shopRotationX
                previewBaseRotationY = profile.shopRotationY
                previewBaseRotationZ = profile.shopRotationZ
                previewYawDegrees = 0f
                sceneView.addChildNode(node)
                sceneView.cameraNode.apply {
                    position = Position(x = 0f, y = profile.shopCameraY, z = profile.shopCameraZ)
                    setProjection(
                        fovInDegrees = profile.shopCameraFov.toDouble(),
                        near = 0.01f,
                        far = 2000f,
                        direction = Camera.Fov.VERTICAL
                    )
                }
                previewModelNode = node
                previewPlaceholder.isVisible = false
            }
        }
    }

    private fun updatePreviewPrimaryButton(item: ShopItem) {
        when {
            item.equipped -> {
                previewPrimaryButton.text = getString(R.string.shop_action_equipped)
                previewPrimaryButton.isEnabled = false
                previewPrimaryButton.setBackgroundResource(R.drawable.bg_pet_menu_btn_secondary)
            }
            item.owned || item.isFree -> {
                previewPrimaryButton.text = getString(R.string.shop_action_use)
                previewPrimaryButton.isEnabled = true
                previewPrimaryButton.setBackgroundResource(R.drawable.bg_action_button)
            }
            else -> {
                previewPrimaryButton.text = getString(R.string.shop_action_buy_price, item.price)
                previewPrimaryButton.isEnabled = true
                previewPrimaryButton.setBackgroundResource(R.drawable.bg_action_button)
            }
        }
    }

    private fun clearPreviewModel() {
        previewModelNode?.let { node ->
            runCatching { previewSceneView?.removeChildNode(node) }
        }
        previewModelNode = null
    }

    private fun ensurePreviewSceneView(): SceneView {
        val existing = previewSceneView
        if (existing != null) return existing
        return SceneView(this, isOpaque = false).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            cameraManipulator = null
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        previewLastTouchX = event.x
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val node = previewModelNode ?: return@setOnTouchListener true
                        val dx = event.x - previewLastTouchX
                        previewLastTouchX = event.x
                        previewYawDegrees += dx * SHOP_PREVIEW_YAW_SENSITIVITY
                        node.rotation = Rotation(
                            x = previewBaseRotationX,
                            y = previewBaseRotationY + previewYawDegrees,
                            z = previewBaseRotationZ
                        )
                        true
                    }

                    else -> true
                }
            }
            previewSceneHost.addView(this)
            previewSceneView = this
        }
    }

    override fun onDestroy() {
        clearPreviewModel()
        super.onDestroy()
    }

    private companion object {
        const val SHOP_PREVIEW_YAW_SENSITIVITY = 0.42f
    }
}

private data class ShopCategory(
    val id: String,
    val title: String
)

private data class ShopItem(
    val id: String,
    val title: String,
    val categoryId: String,
    val tag: String,
    val assetPath: String?,
    val previewResId: Int?,
    val isFree: Boolean,
    val price: Int,
    var owned: Boolean,
    var equipped: Boolean
)

private class ShopItemAdapter(
    private val onPrimaryAction: (ShopItem) -> Unit,
    private val onPreview: (ShopItem) -> Unit,
    private val resolvePreviewResId: (ShopItem) -> Int?,
    private val resolvePreviewImageTransform: (ShopItem) -> ShopPreviewImageTransform
) : RecyclerView.Adapter<ShopItemAdapter.VH>() {

    private val data = mutableListOf<ShopItem>()

    fun submit(newData: List<ShopItem>) {
        data.clear()
        data.addAll(newData)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shop_product, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = data[position]
        holder.tvTitle.text = item.title
        holder.tvTag.text = item.tag
        val imageTransform = resolvePreviewImageTransform(item)
        val density = holder.itemView.resources.displayMetrics.density
        holder.ivPreview.scaleX = imageTransform.zoom
        holder.ivPreview.scaleY = imageTransform.zoom
        holder.ivPreview.translationX = imageTransform.offsetXDp * density
        holder.ivPreview.translationY = imageTransform.offsetYDp * density
        val resId = resolvePreviewResId(item)
        if (resId == null) {
            holder.ivPreview.setImageDrawable(null)
            holder.ivPreview.isVisible = false
        } else {
            holder.ivPreview.setImageResource(resId)
            holder.ivPreview.isVisible = true
        }
        holder.tvPlaceholder.isVisible = false
        holder.previewRoot.setOnClickListener(null)
        holder.btnPreview.setOnClickListener { onPreview(item) }
        when {
            item.equipped -> {
                holder.btnAction.text = holder.itemView.context.getString(R.string.shop_action_equipped)
                holder.btnAction.isEnabled = false
                holder.btnAction.setBackgroundResource(R.drawable.bg_pet_menu_btn_secondary)
            }
            item.owned || item.isFree -> {
                holder.btnAction.text = holder.itemView.context.getString(R.string.shop_action_use)
                holder.btnAction.isEnabled = true
                holder.btnAction.setBackgroundResource(R.drawable.bg_action_button)
            }
            else -> {
                holder.btnAction.text = holder.itemView.context.getString(R.string.shop_action_buy_price, item.price)
                holder.btnAction.isEnabled = true
                holder.btnAction.setBackgroundResource(R.drawable.bg_action_button)
            }
        }
        holder.btnAction.setOnClickListener { onPrimaryAction(item) }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvShopItemTitle)
        val tvTag: TextView = view.findViewById(R.id.tvShopItemTag)
        val previewRoot: FrameLayout = view.findViewById(R.id.viewShopModelPreview)
        val ivPreview: ImageView = view.findViewById(R.id.ivShopModelPreview)
        val tvPlaceholder: TextView = view.findViewById(R.id.tvShopModelPlaceholder)
        val btnPreview: MaterialButton = view.findViewById(R.id.btnShopItemPreview)
        val btnAction: MaterialButton = view.findViewById(R.id.btnShopItemAction)
    }
}

private data class ShopPreviewImageTransform(
    val zoom: Float,
    val offsetXDp: Float,
    val offsetYDp: Float
) {
    companion object {
        val DEFAULT = ShopPreviewImageTransform(
            zoom = 1f,
            offsetXDp = 0f,
            offsetYDp = 0f
        )
    }
}

