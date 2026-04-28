package com.ccpet.desktoppet

import androidx.annotation.StringRes

enum class PetInteractionFallbackPolicy {
    RANDOM_ONE_SHOT,
    IDLE_LOOP
}

data class PetInteractionAction(
    val id: String,
    @StringRes val titleRes: Int? = null,
    val titleText: String? = null,
    @StringRes val toastRes: Int? = null,
    val toastText: String? = null,
    val preferredAnimationIndex: Int,
    val fallbackPolicy: PetInteractionFallbackPolicy
)

data class PetInteractionSet(
    val coreActions: List<PetInteractionAction>,
    val idleAnimationIndex: Int = 0,
    val enableRandomAmbient: Boolean = true,
    val playIdleByDefault: Boolean = true,
    val resumeIdleAfterAction: Boolean = true,
    val aliasByAnimationName: Map<String, String> = emptyMap(),
    val aliasByAnimationIndex: Map<Int, String> = emptyMap()
)

object PetInteractionsRegistry {
    private val defaultSet = PetInteractionSet(
        coreActions = listOf(
            PetInteractionAction(
                id = "hello",
                titleRes = R.string.pet_interaction_hello,
                toastRes = R.string.pet_interaction_hello_toast,
                preferredAnimationIndex = 1,
                fallbackPolicy = PetInteractionFallbackPolicy.RANDOM_ONE_SHOT
            ),
            PetInteractionAction(
                id = "pat",
                titleRes = R.string.pet_interaction_pat,
                toastRes = R.string.pet_interaction_pat_toast,
                preferredAnimationIndex = 2,
                fallbackPolicy = PetInteractionFallbackPolicy.RANDOM_ONE_SHOT
            ),
            PetInteractionAction(
                id = "cheer",
                titleRes = R.string.pet_interaction_cheer,
                toastRes = R.string.pet_interaction_cheer_toast,
                preferredAnimationIndex = 3,
                fallbackPolicy = PetInteractionFallbackPolicy.IDLE_LOOP
            )
        ),
        idleAnimationIndex = 0,
        enableRandomAmbient = true,
        playIdleByDefault = true,
        resumeIdleAfterAction = true
    )

    private val babyDragonSet = PetInteractionSet(
        coreActions = listOf(
            PetInteractionAction(
                id = "hello",
                titleRes = R.string.pet_interaction_hello,
                toastRes = R.string.pet_interaction_hello_toast,
                preferredAnimationIndex = 0,
                fallbackPolicy = PetInteractionFallbackPolicy.RANDOM_ONE_SHOT
            ),
            PetInteractionAction(
                id = "pat",
                titleRes = R.string.pet_interaction_pat,
                toastRes = R.string.pet_interaction_pat_toast,
                preferredAnimationIndex = 11,
                fallbackPolicy = PetInteractionFallbackPolicy.RANDOM_ONE_SHOT
            ),
            PetInteractionAction(
                id = "cheer",
                titleRes = R.string.pet_interaction_cheer,
                toastRes = R.string.pet_interaction_cheer_toast,
                preferredAnimationIndex = 6,
                fallbackPolicy = PetInteractionFallbackPolicy.IDLE_LOOP
            )
        ),
        // 保持默认待机为 Casual_Walk，不改动原体验。
        idleAnimationIndex = 8,
        enableRandomAmbient = false,
        // 小龙崽进入即播放默认待机（Casual_Walk）。
        playIdleByDefault = true,
        // 互动动作结束后，仍需回到默认动作，避免停在动作末帧。
        resumeIdleAfterAction = true,
        aliasByAnimationName = mapOf(
            "Agree_Gesture" to "打招呼",
            "Alert" to "警觉",
            "All_Night_Dance" to "整夜热舞",
            "Arise" to "起身",
            "Attack" to "攻击",
            "BeHit_FlyUp" to "受击飞起",
            "Boom_Dance" to "卖个萌",
            "Boxing_Practice" to "练拳",
            "Casual_Walk" to "休闲走路",
            "Dead" to "倒地",
            "running" to "跑步",
            "Skill_01" to "摸摸头",
            "Triple_Combo_Attack" to "三连击",
            "Unsteady_Walk" to "踉跄走",
            "walking_man" to "普通走路",
            "You_Groove" to "律动舞蹈"
        ),
        aliasByAnimationIndex = mapOf(
            0 to "打招呼",
            1 to "警觉",
            2 to "整夜热舞",
            3 to "起身",
            4 to "攻击",
            5 to "受击飞起",
            6 to "卖个萌",
            7 to "练拳",
            8 to "休闲走路",
            9 to "倒地",
            10 to "跑步",
            11 to "摸摸头",
            12 to "三连击",
            13 to "踉跄走",
            14 to "普通走路",
            15 to "律动舞蹈"
        )
    )

    private val setsByAssetPath = mapOf(
        "models/BabyDragon.glb" to babyDragonSet,
        // 兼容直接选择合并文件名的场景，避免落到默认交互映射。
        "models/BabyDragonMerged.glb" to babyDragonSet
    )

    fun forAsset(assetPath: String?): PetInteractionSet {
        if (assetPath.isNullOrBlank()) return defaultSet
        return setsByAssetPath[assetPath] ?: defaultSet
    }

    fun buildActions(
        set: PetInteractionSet,
        animationCount: Int,
        animationNames: List<String?>
    ): List<PetInteractionAction> {
        if (animationCount <= 0) return emptyList()

        val idleIndex = set.idleAnimationIndex.coerceIn(0, animationCount - 1)
        val actions = mutableListOf<PetInteractionAction>()
        val used = mutableSetOf<Int>()

        set.coreActions.forEach { action ->
            val idx = action.preferredAnimationIndex
            if (idx !in 0 until animationCount) return@forEach
            if (idx == idleIndex) return@forEach
            actions.add(action)
            used.add(idx)
        }

        for (idx in 0 until animationCount) {
            if (idx == idleIndex || idx in used) continue
            val animationName = animationNames.getOrNull(idx)?.takeIf { it.isNotBlank() }
            val title = animationName?.let { set.aliasByAnimationName[it] }
                ?: set.aliasByAnimationIndex[idx]
            // 缺中文别名时先不入列表，后续由你确认命名后再放开。
            if (title.isNullOrBlank()) continue
            actions.add(
                PetInteractionAction(
                    id = "auto_$idx",
                    titleText = title,
                    preferredAnimationIndex = idx,
                    fallbackPolicy = PetInteractionFallbackPolicy.RANDOM_ONE_SHOT
                )
            )
        }

        return actions
    }

    fun collectUnnamedAnimations(
        set: PetInteractionSet,
        animationCount: Int,
        animationNames: List<String?>
    ): List<Pair<Int, String?>> {
        if (animationCount <= 0) return emptyList()
        val idleIndex = set.idleAnimationIndex.coerceIn(0, animationCount - 1)
        val used = set.coreActions
            .map { it.preferredAnimationIndex }
            .filter { it in 0 until animationCount }
            .toSet()
        val unknown = mutableListOf<Pair<Int, String?>>()
        for (idx in 0 until animationCount) {
            if (idx == idleIndex || idx in used) continue
            val animationName = animationNames.getOrNull(idx)?.takeIf { it.isNotBlank() }
            val hasAlias = (animationName != null && set.aliasByAnimationName.containsKey(animationName)) ||
                set.aliasByAnimationIndex.containsKey(idx)
            if (!hasAlias) unknown.add(idx to animationName)
        }
        return unknown
    }

    val defaultActions: List<PetInteractionAction> get() = defaultSet.coreActions
}

