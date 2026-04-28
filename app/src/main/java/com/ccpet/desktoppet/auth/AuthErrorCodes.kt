package com.ccpet.desktoppet.auth

/**
 * 后端统一错误码常量。
 * 后续接入真实接口时，UI 侧建议只依赖这里的常量做文案映射。
 */
object AuthErrorCodes {
    const val PHONE_INVALID = "PHONE_INVALID"
    const val CODE_INVALID = "CODE_INVALID"
    const val CODE_EXPIRED = "CODE_EXPIRED"
    const val PHONE_ALREADY_BOUND = "PHONE_ALREADY_BOUND"
    const val PASSWORD_TOO_WEAK = "PASSWORD_TOO_WEAK"
    const val PHONE_OR_PASSWORD_INCORRECT = "PHONE_OR_PASSWORD_INCORRECT"
    const val TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS"
    const val SESSION_INVALID = "SESSION_INVALID"
    const val UNKNOWN = "UNKNOWN"
}
