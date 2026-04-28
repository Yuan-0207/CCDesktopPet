package com.ccpet.desktoppet

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

object PetSettingsStore {
    // 行为调优相关的 SharedPreferences 键。
    private const val PREF_NAME = "pet_settings"
    private const val KEY_PET_NAME = "pet_name"
    private const val KEY_HALF_HIDE_ENABLED = "half_hide_enabled"
    private const val KEY_HALF_HIDE_DELAY_MS = "half_hide_delay_ms"
    private const val KEY_WEAK_ALPHA_PERCENT = "weak_alpha_percent"
    private const val KEY_DESKTOP_PET_VISIBLE = "desktop_pet_visible"
    private const val KEY_SELECTED_MODEL_ASSET_PATH = "selected_model_asset_path"
    private const val KEY_LOGIN_PROVIDER = "login_provider"
    private const val KEY_LOGIN_NICKNAME = "login_nickname"
    private const val KEY_LOGIN_AVATAR_URL = "login_avatar_url"
    private const val KEY_LOCAL_USER_ID = "local_user_id"
    private const val KEY_LOGGED_OUT = "login_logged_out"
    private const val KEY_PHONE_BOUND = "phone_bound"
    private const val KEY_BOUND_PHONE = "bound_phone"
    private const val KEY_BOUND_PASSWORD_HASH = "bound_password_hash"

    const val DEFAULT_HALF_HIDE_ENABLED = true
    const val DEFAULT_PET_NAME = "未命名"
    const val DEFAULT_HALF_HIDE_DELAY_MS = 2200
    const val DEFAULT_WEAK_ALPHA_PERCENT = 55
    const val DEFAULT_DESKTOP_PET_VISIBLE = false
    const val DEFAULT_LOGIN_PROVIDER = "guest"
    const val DEFAULT_USER_NICKNAME = "未登录用户"

    const val MIN_HALF_HIDE_DELAY_MS = 500
    const val MAX_HALF_HIDE_DELAY_MS = 5000
    const val MIN_WEAK_ALPHA_PERCENT = 25
    const val MAX_WEAK_ALPHA_PERCENT = 100

    // 提供给界面一键套用的内置预设。
    enum class Preset(val halfHideEnabled: Boolean, val delayMs: Int, val weakAlphaPercent: Int) {
        LIGHT_DISTRACTION(true, 3200, 80),
        BALANCED(true, 2200, 55),
        MINIMAL(true, 1200, 35)
    }

    fun isHalfHideEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_HALF_HIDE_ENABLED, DEFAULT_HALF_HIDE_ENABLED)
    }

    fun petName(context: Context): String {
        return prefs(context).getString(KEY_PET_NAME, DEFAULT_PET_NAME)?.trim().orEmpty()
            .ifBlank { DEFAULT_PET_NAME }
    }

    fun setPetName(context: Context, value: String) {
        val finalName = value.trim().ifBlank { DEFAULT_PET_NAME }
        prefs(context).edit().putString(KEY_PET_NAME, finalName).apply()
    }

    fun setHalfHideEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HALF_HIDE_ENABLED, enabled).apply()
    }

    fun halfHideDelayMs(context: Context): Int {
        val value = prefs(context).getInt(KEY_HALF_HIDE_DELAY_MS, DEFAULT_HALF_HIDE_DELAY_MS)
        return value.coerceIn(MIN_HALF_HIDE_DELAY_MS, MAX_HALF_HIDE_DELAY_MS)
    }

    fun setHalfHideDelayMs(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_HALF_HIDE_DELAY_MS, value.coerceIn(MIN_HALF_HIDE_DELAY_MS, MAX_HALF_HIDE_DELAY_MS))
            .apply()
    }

    fun weakAlphaPercent(context: Context): Int {
        val value = prefs(context).getInt(KEY_WEAK_ALPHA_PERCENT, DEFAULT_WEAK_ALPHA_PERCENT)
        return value.coerceIn(MIN_WEAK_ALPHA_PERCENT, MAX_WEAK_ALPHA_PERCENT)
    }

    fun setWeakAlphaPercent(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_WEAK_ALPHA_PERCENT, value.coerceIn(MIN_WEAK_ALPHA_PERCENT, MAX_WEAK_ALPHA_PERCENT))
            .apply()
    }

    /** 是否在离开应用后于桌面显示悬浮桌宠（关闭时不会启动前台悬浮窗服务）。 */
    fun isDesktopPetVisibleOnDesktop(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DESKTOP_PET_VISIBLE, DEFAULT_DESKTOP_PET_VISIBLE)
    }

    fun setDesktopPetVisibleOnDesktop(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_DESKTOP_PET_VISIBLE, visible).apply()
    }

    fun selectedModelAssetPath(context: Context): String {
        val p = prefs(context)
        val stored = p.getString(KEY_SELECTED_MODEL_ASSET_PATH, null)
        val defaultPath = ModelDisplayProfiles.defaultAssetPath()
        val resolved = when {
            stored.isNullOrBlank() -> defaultPath
            ModelDisplayProfiles.hasAsset(stored) -> stored
            else -> defaultPath
        }
        if (stored != resolved) {
            p.edit().putString(KEY_SELECTED_MODEL_ASSET_PATH, resolved).apply()
        }
        return resolved
    }

    fun setSelectedModelAssetPath(context: Context, assetPath: String) {
        prefs(context).edit().putString(KEY_SELECTED_MODEL_ASSET_PATH, assetPath).apply()
    }

    fun loginProvider(context: Context): String {
        return prefs(context).getString(KEY_LOGIN_PROVIDER, DEFAULT_LOGIN_PROVIDER).orEmpty()
    }

    fun loginNickname(context: Context): String {
        val stored = prefs(context).getString(KEY_LOGIN_NICKNAME, null)?.trim().orEmpty()
        return stored.ifBlank { DEFAULT_USER_NICKNAME }
    }

    fun loginAvatarUrl(context: Context): String {
        return prefs(context).getString(KEY_LOGIN_AVATAR_URL, "")?.trim().orEmpty()
    }

    fun localUserId(context: Context): String {
        val p = prefs(context)
        val existing = p.getString(KEY_LOCAL_USER_ID, null)?.trim().orEmpty()
        if (existing.isNotBlank()) return existing
        val created = UUID.randomUUID().toString().replace("-", "")
        p.edit().putString(KEY_LOCAL_USER_ID, created).apply()
        return created
    }

    fun ensureLocalLogin(context: Context) {
        // Ensure local user id exists.
        localUserId(context)
        val p = prefs(context)
        val loggedOut = p.getBoolean(KEY_LOGGED_OUT, false)
        if (loggedOut) return
        val provider = p.getString(KEY_LOGIN_PROVIDER, DEFAULT_LOGIN_PROVIDER).orEmpty()
        if (provider.isBlank() || provider == "guest") {
            p.edit().putString(KEY_LOGIN_PROVIDER, "local").apply()
        }
    }

    fun saveLocalNickname(context: Context, nickname: String) {
        val finalName = nickname.trim().ifBlank { DEFAULT_USER_NICKNAME }
        prefs(context).edit()
            .putString(KEY_LOGIN_PROVIDER, "local")
            .putString(KEY_LOGIN_NICKNAME, finalName)
            .putString(KEY_LOGIN_AVATAR_URL, "")
            .putBoolean(KEY_LOGGED_OUT, false)
            .apply()
    }

    fun saveLoginProfile(
        context: Context,
        provider: String,
        nickname: String,
        avatarUrl: String
    ) {
        val finalName = nickname.trim().ifBlank { DEFAULT_USER_NICKNAME }
        prefs(context).edit()
            .putString(KEY_LOGIN_PROVIDER, provider.ifBlank { DEFAULT_LOGIN_PROVIDER })
            .putString(KEY_LOGIN_NICKNAME, finalName)
            .putString(KEY_LOGIN_AVATAR_URL, avatarUrl.trim())
            .putBoolean(KEY_LOGGED_OUT, false)
            .apply()
    }

    fun logout(context: Context) {
        // 退出登录不清除 localUserId；手机号绑定也不清除（用于找回/换机）。
        prefs(context).edit()
            .putString(KEY_LOGIN_PROVIDER, "guest")
            .putString(KEY_LOGIN_NICKNAME, DEFAULT_USER_NICKNAME)
            .putString(KEY_LOGIN_AVATAR_URL, "")
            .putBoolean(KEY_LOGGED_OUT, true)
            .apply()
    }

    fun isPhoneBound(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_PHONE_BOUND, false)
    }

    fun boundPhoneMasked(context: Context): String {
        val phone = prefs(context).getString(KEY_BOUND_PHONE, null)?.trim().orEmpty()
        if (phone.length != 11 || !phone.all { it.isDigit() }) return ""
        return phone.substring(0, 3) + "****" + phone.substring(7)
    }

    fun bindPhoneCredential(context: Context, phone: String, password: String): Boolean {
        val normalizedPhone = phone.trim()
        if (normalizedPhone.length != 11 || !normalizedPhone.all { it.isDigit() }) return false
        if (password.length < 6) return false
        prefs(context).edit()
            .putBoolean(KEY_PHONE_BOUND, true)
            .putString(KEY_BOUND_PHONE, normalizedPhone)
            .putString(KEY_BOUND_PASSWORD_HASH, hashPassword(password))
            .apply()
        return true
    }

    fun verifyPhoneCredential(context: Context, phone: String, password: String): Boolean {
        val normalizedPhone = phone.trim()
        val savedPhone = prefs(context).getString(KEY_BOUND_PHONE, null)?.trim().orEmpty()
        val savedHash = prefs(context).getString(KEY_BOUND_PASSWORD_HASH, null).orEmpty()
        if (savedPhone.isBlank() || savedHash.isBlank()) return false
        if (normalizedPhone != savedPhone) return false
        return hashPassword(password) == savedHash
    }

    fun applyPreset(context: Context, preset: Preset) {
        // 一次事务内写入全部字段，避免只写一部分导致状态不一致。
        prefs(context).edit()
            .putBoolean(KEY_HALF_HIDE_ENABLED, preset.halfHideEnabled)
            .putInt(
                KEY_HALF_HIDE_DELAY_MS,
                preset.delayMs.coerceIn(MIN_HALF_HIDE_DELAY_MS, MAX_HALF_HIDE_DELAY_MS)
            )
            .putInt(
                KEY_WEAK_ALPHA_PERCENT,
                preset.weakAlphaPercent.coerceIn(MIN_WEAK_ALPHA_PERCENT, MAX_WEAK_ALPHA_PERCENT)
            )
            .apply()
    }

    // 统一的设置存储入口，避免散落读取造成维护困难。
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
