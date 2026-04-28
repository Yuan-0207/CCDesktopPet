package com.ccpet.desktoppet

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {
    private lateinit var tvUserNickname: TextView
    private lateinit var tvUserProvider: TextView
    private lateinit var ivUserAvatar: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        tvUserNickname = findViewById(R.id.tvUserNickname)
        tvUserProvider = findViewById(R.id.tvUserProvider)
        ivUserAvatar = findViewById(R.id.ivUserAvatar)

        val profileCard = findViewById<LinearLayout>(R.id.profileCard)
        val itemPetName = findViewById<LinearLayout>(R.id.itemPetName)
        val itemPetStyle = findViewById<LinearLayout>(R.id.itemPetStyle)
        val itemOverlayPermission = findViewById<LinearLayout>(R.id.itemOverlayPermission)
        val itemLogout = findViewById<LinearLayout>(R.id.itemLogout)
        val itemPrivacyPolicy = findViewById<LinearLayout>(R.id.itemPrivacyPolicy)
        val itemUserAgreement = findViewById<LinearLayout>(R.id.itemUserAgreement)
        val itemContactUs = findViewById<LinearLayout>(R.id.itemContactUs)
        val btnSettingsBack = findViewById<TextView>(R.id.btnSettingsBack)

        btnSettingsBack.setOnClickListener { finish() }

        profileCard.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // 功能入口先占位，后续可替换成真实业务页面。
        itemPetName.setOnClickListener {
            startActivity(Intent(this, PetNameSettingsActivity::class.java))
        }

        itemPetStyle.setOnClickListener {
            Toast.makeText(this, R.string.settings_pet_style_todo, Toast.LENGTH_SHORT).show()
        }

        itemOverlayPermission.setOnClickListener {
            openOverlayPermissionPage()
        }
        itemLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_logout_confirm_title)
                .setMessage(R.string.settings_logout_confirm_body)
                .setNegativeButton(R.string.settings_logout_confirm_cancel, null)
                .setPositiveButton(R.string.settings_logout_confirm_ok) { _, _ ->
                    PetSettingsStore.logout(this)
                    bindLoginProfile()
                    Toast.makeText(this, R.string.settings_logout_done, Toast.LENGTH_SHORT).show()
                }
                .show()
        }
        itemPrivacyPolicy.setOnClickListener {
            openPolicyPage(PolicyDocumentActivity.TYPE_PRIVACY)
        }
        itemUserAgreement.setOnClickListener {
            openPolicyPage(PolicyDocumentActivity.TYPE_TERMS)
        }
        itemContactUs.setOnClickListener {
            copyContactEmail()
        }
    }

    override fun onResume() {
        super.onResume()
        PetDesktopLauncher.requestStopDesktopPet(this)
        PetSettingsStore.ensureLocalLogin(this)
        bindLoginProfile()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PetDesktopLauncher.requestStartDesktopPet(this)
    }

    // 跳转到当前应用的悬浮窗权限设置页。
    private fun openOverlayPermissionPage() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun bindLoginProfile() {
        tvUserNickname.text = PetSettingsStore.loginNickname(this)
        val provider = when (PetSettingsStore.loginProvider(this)) {
            "local" -> getString(R.string.login_provider_local)
            "wechat" -> getString(R.string.login_provider_wechat)
            "qq" -> getString(R.string.login_provider_qq)
            else -> getString(R.string.login_guest_provider)
        }
        tvUserProvider.text = if (PetSettingsStore.isPhoneBound(this)) {
            val masked = PetSettingsStore.boundPhoneMasked(this)
            getString(R.string.settings_account_subtitle_bound, provider, masked)
        } else {
            getString(R.string.settings_account_subtitle_unbound, provider)
        }
        val avatarUrl = PetSettingsStore.loginAvatarUrl(this)
        when {
            avatarUrl == "asset://wechat" -> ivUserAvatar.setImageResource(R.drawable.ic_login_user)
            avatarUrl == "asset://qq" -> ivUserAvatar.setImageResource(R.drawable.ic_login_user)
            avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://") -> loadAvatarFromUrl(avatarUrl)
            else -> ivUserAvatar.setImageResource(R.drawable.ic_login_user)
        }
    }

    private fun openPolicyPage(type: String) {
        startActivity(
            Intent(this, PolicyDocumentActivity::class.java)
                .putExtra(PolicyDocumentActivity.EXTRA_DOC_TYPE, type)
        )
    }

    private fun copyContactEmail() {
        val text = getString(R.string.settings_contact_email_value)
        val cm = getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("contact_email", text))
        Toast.makeText(this, getString(R.string.settings_contact_copied, text), Toast.LENGTH_SHORT).show()
    }

    private fun loadAvatarFromUrl(url: String) {
        thread {
            val bitmap = runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    doInput = true
                    connect()
                }
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            runOnUiThread {
                if (bitmap != null) {
                    ivUserAvatar.setImageBitmap(bitmap)
                } else {
                    ivUserAvatar.setImageResource(R.drawable.ic_login_user)
                }
            }
        }
    }

}
