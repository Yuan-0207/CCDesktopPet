package com.ccpet.desktoppet.auth

/**
 * 账号后端接口契约。
 * 后续只需新增 RealAuthApi 实现该接口，即可替换当前本地模拟流程。
 */
interface AuthApiContract {
    suspend fun sendCode(request: SendCodeRequest): ApiResult<SendCodeData>

    suspend fun bindPhone(request: BindPhoneRequest): ApiResult<BindPhoneData>

    suspend fun phoneLogin(request: PhoneLoginRequest): ApiResult<PhoneLoginData>

    suspend fun phoneStatus(accessToken: String): ApiResult<PhoneStatusData>
}

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()

    data class Failure(
        val errorCode: String = AuthErrorCodes.UNKNOWN,
        val errorMessage: String = ""
    ) : ApiResult<Nothing>()
}
