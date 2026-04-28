package com.ccpet.desktoppet

import io.github.sceneview.node.ModelNode
import kotlin.random.Random

object PetAnimationRunner {

    fun extractAnimationNames(node: ModelNode): List<String?> {
        val count = node.animationCount
        if (count <= 0) return emptyList()
        val animator = node.animator
        val nameMethod = animator.javaClass.methods.firstOrNull {
            it.name == "getAnimationName" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        }
        if (nameMethod == null) {
            return List(count) { null }
        }
        return List(count) { index ->
            runCatching { nameMethod.invoke(animator, index) as? String }.getOrNull()
        }
    }

    fun resolveInteractionIndex(
        animationCount: Int,
        preferredAnimationIndex: Int,
        fallbackPolicy: PetInteractionFallbackPolicy
    ): Int? {
        if (animationCount <= 0) return null
        if (preferredAnimationIndex in 0 until animationCount) return preferredAnimationIndex
        return when (fallbackPolicy) {
            PetInteractionFallbackPolicy.RANDOM_ONE_SHOT -> {
                if (animationCount <= 1) 0 else Random.nextInt(1, animationCount)
            }
            PetInteractionFallbackPolicy.IDLE_LOOP -> idleIndex(animationCount, preferredIdleIndex = 0)
        }
    }

    fun idleIndex(animationCount: Int, preferredIdleIndex: Int = 0): Int {
        if (animationCount <= 0) return 0
        return preferredIdleIndex.coerceIn(0, animationCount - 1)
    }

    fun randomOneShotIndex(animationCount: Int): Int {
        if (animationCount <= 1) return 0
        return Random.nextInt(animationCount)
    }

    fun oneShotDurationMs(node: ModelNode, animationIndex: Int): Long {
        val durSec = node.animator.getAnimationDuration(animationIndex)
        // 某些动作（如舞蹈/长互动）时长超过 8s，不能过早解锁切换，否则可能在底层仍播放时重入导致崩溃。
        return (durSec * 1000f).toLong().coerceIn(500L, 30_000L)
    }
}

