package com.ccpet.desktoppet.auth

import android.content.Context

/**
 * 后续接真后端时，只需要把这里切到 RealAuthApi 即可。
 */
object AuthApiProvider {
    fun provide(context: Context): AuthApiContract {
        return LocalMockAuthApi(context.applicationContext)
    }
}

