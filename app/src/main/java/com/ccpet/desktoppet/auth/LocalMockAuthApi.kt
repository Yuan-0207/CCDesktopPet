package com.ccpet.desktoppet.auth

import android.content.Context
import com.ccpet.desktoppet.PetSettingsStore
import java.util.UUID

class LocalMockAuthApi(private val context: Context) : AuthApiContract {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    override suspend fun sendCode(request: SendCodeRequest): ApiResult<SendCodeData> {
        val phone = request.phone.trim()
        if (!isPhoneValid(phone)) {
            return ApiResult.Failure(AuthErrorCodes.PHONE_INVALID, "手机号格式不正确")
        }
        val code = ((100000..999999).random()).toString()
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val expireSeconds = 300
        val expireAtMs = System.currentTimeMillis() + expireSeconds * 1000L
        prefs.edit()
            .putString(keyForCode(phone, request.bizType), code)
            .putLong(keyForExpire(phone, request.bizType), expireAtMs)
            .apply()
        return ApiResult.Success(SendCodeData(requestId = requestId, expireSeconds = expireSeconds, demoCode = code))
    }

    override suspend fun bindPhone(request: BindPhoneRequest): ApiResult<BindPhoneData> {
        val phone = request.phone.trim()
        if (!isPhoneValid(phone)) {
            return ApiResult.Failure(AuthErrorCodes.PHONE_INVALID, "手机号格式不正确")
        }
        val codeKey = keyForCode(phone, BIZ_BIND)
        val expireKey = keyForExpire(phone, BIZ_BIND)
        val savedCode = prefs.getString(codeKey, null).orEmpty()
        val expireAt = prefs.getLong(expireKey, 0L)
        if (savedCode.isBlank() || expireAt <= 0L) {
            return ApiResult.Failure(AuthErrorCodes.CODE_INVALID, "请先获取验证码")
        }
        if (System.currentTimeMillis() > expireAt) {
            return ApiResult.Failure(AuthErrorCodes.CODE_EXPIRED, "验证码已过期")
        }
        if (request.code.trim() != savedCode) {
            return ApiResult.Failure(AuthErrorCodes.CODE_INVALID, "验证码不正确")
        }
        if (request.password.length < 6) {
            return ApiResult.Failure(AuthErrorCodes.PASSWORD_TOO_WEAK, "密码至少6位")
        }

        // 绑定：本地保存手机号+密码hash（与当前 demo 绑定页行为一致）
        val ok = PetSettingsStore.bindPhoneCredential(context, phone, request.password)
        if (!ok) {
            return ApiResult.Failure(AuthErrorCodes.UNKNOWN, "绑定失败")
        }

        // 绑定成功后清除验证码
        prefs.edit().remove(codeKey).remove(expireKey).apply()
        return ApiResult.Success(BindPhoneData(maskedPhone = PetSettingsStore.boundPhoneMasked(context)))
    }

    override suspend fun phoneLogin(request: PhoneLoginRequest): ApiResult<PhoneLoginData> {
        val phone = request.phone.trim()
        if (!isPhoneValid(phone)) {
            return ApiResult.Failure(AuthErrorCodes.PHONE_INVALID, "手机号格式不正确")
        }
        val ok = PetSettingsStore.verifyPhoneCredential(context, phone, request.password)
        if (!ok) {
            return ApiResult.Failure(AuthErrorCodes.PHONE_OR_PASSWORD_INCORRECT, "手机号或密码错误，或当前设备尚未绑定")
        }

        // 演示：返回虚拟 token，并带回本地 user profile
        val userId = PetSettingsStore.localUserId(context)
        val nickname = PetSettingsStore.loginNickname(context)
        val avatarUrl = PetSettingsStore.loginAvatarUrl(context)
        return ApiResult.Success(
            PhoneLoginData(
                accessToken = "local_mock_access_${UUID.randomUUID()}",
                refreshToken = "local_mock_refresh_${UUID.randomUUID()}",
                userProfile = UserProfileDto(
                    userId = userId,
                    nickname = nickname,
                    avatarUrl = avatarUrl
                )
            )
        )
    }

    override suspend fun phoneStatus(accessToken: String): ApiResult<PhoneStatusData> {
        val bound = PetSettingsStore.isPhoneBound(context)
        val masked = PetSettingsStore.boundPhoneMasked(context)
        return ApiResult.Success(PhoneStatusData(bound = bound, maskedPhone = masked))
    }

    private fun isPhoneValid(phone: String): Boolean {
        return phone.length == 11 && phone.all { it.isDigit() }
    }

    private fun keyForCode(phone: String, bizType: String) = "code_${bizType}_$phone"
    private fun keyForExpire(phone: String, bizType: String) = "expire_${bizType}_$phone"

    private companion object {
        private const val PREF_NAME = "mock_auth"
        private const val BIZ_BIND = "bind"
    }
}

