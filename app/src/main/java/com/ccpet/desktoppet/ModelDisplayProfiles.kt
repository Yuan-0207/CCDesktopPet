package com.ccpet.desktoppet

/**
 * 每个模型独立的一套展示参数（首页预览 + 桌面悬浮窗）。
 * 后续新增模型时，只需补一个 profile 并切换 selectedModelAssetPath 即可复用。
 */
data class ModelDisplayProfile(
    val assetPath: String,
    // Home preview
    val previewScaleToUnits: Float,
    val previewManualScale: Float,
    val previewOffsetX: Float,
    val previewOffsetY: Float,
    val previewOffsetZ: Float,
    val previewCameraZ: Float,
    val previewCameraY: Float,
    val previewCameraFov: Float,
    val previewRotationX: Float,
    val previewRotationY: Float,
    val previewRotationZ: Float,
    // Desktop overlay
    val desktopScaleToUnits: Float,
    val desktopCameraZ: Float,
    val desktopCameraY: Float,
    val desktopCameraFov: Float,
    val desktopRotationX: Float,
    val desktopRotationY: Float,
    val desktopRotationZ: Float,
    // Shop card preview
    val shopScaleToUnits: Float,
    val shopManualScale: Float,
    val shopOffsetX: Float,
    val shopOffsetY: Float,
    val shopOffsetZ: Float,
    val shopCameraZ: Float,
    val shopCameraY: Float,
    val shopCameraFov: Float,
    val shopRotationX: Float,
    val shopRotationY: Float,
    val shopRotationZ: Float,
    // Shop card 2D preview image transform
    val shopImageZoom: Float,
    val shopImageOffsetXDp: Float,
    val shopImageOffsetYDp: Float
)

object ModelDisplayProfiles {
    private val babyDragon = BabyDragonProfile.PROFILE
    private val defaultModel = DefaultModelProfile.PROFILE

    private val profilesByAsset = mapOf(
        babyDragon.assetPath to babyDragon,
        defaultModel.assetPath to defaultModel
    )

    fun forAsset(assetPath: String): ModelDisplayProfile {
        return profilesByAsset[assetPath] ?: babyDragon
    }

    fun hasAsset(assetPath: String): Boolean = profilesByAsset.containsKey(assetPath)

    fun defaultAssetPath(): String = defaultModel.assetPath
}

