package com.ccpet.desktoppet.auth

/**
 * 手机号账号体系 DTO 预埋定义。
 * 当前仅做契约收口，后续替换网络请求实现时保持这些结构不变即可。
 */

data class ApiEnvelope<T>(
    val success: Boolean,
    val code: String = "",
    val message: String = "",
    val data: T? = null
)

data class SendCodeRequest(
    val phone: String,
    val bizType: String
)

data class SendCodeData(
    val requestId: String,
    val expireSeconds: Int,
    // 演示模式：仅用于本地 mock/测试展示，真实后端可为空。
    val demoCode: String = ""
)

data class BindPhoneRequest(
    val userId: String,
    val phone: String,
    val code: String,
    val password: String
)

data class BindPhoneData(
    val maskedPhone: String
)

data class PhoneLoginRequest(
    val phone: String,
    val password: String
)

data class UserProfileDto(
    val userId: String,
    val nickname: String,
    val avatarUrl: String
)

data class PhoneLoginData(
    val accessToken: String,
    val refreshToken: String,
    val userProfile: UserProfileDto
)

data class PhoneStatusData(
    val bound: Boolean,
    val maskedPhone: String
)
