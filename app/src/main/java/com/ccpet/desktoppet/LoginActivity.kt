package com.ccpet.desktoppet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var tvLocalUserId: TextView
    private lateinit var etLocalNickname: EditText
    private lateinit var tvNicknameCounter: TextView
    private lateinit var tvPhoneBindStatus: TextView
    private lateinit var btnLoginConfirmLocal: Button
    private lateinit var btnBindPhone: Button
    private lateinit var btnPhonePasswordLogin: Button
    private lateinit var btnBack: Button
    private lateinit var loadingMask: View
    private lateinit var tvLoginStatusHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tvLocalUserId = findViewById(R.id.tvLocalUserId)
        etLocalNickname = findViewById(R.id.etLocalNickname)
        tvNicknameCounter = findViewById(R.id.tvNicknameCounter)
        tvPhoneBindStatus = findViewById(R.id.tvPhoneBindStatus)
        btnLoginConfirmLocal = findViewById(R.id.btnLoginConfirmLocal)
        btnBindPhone = findViewById(R.id.btnBindPhone)
        btnPhonePasswordLogin = findViewById(R.id.btnPhonePasswordLogin)
        btnBack = findViewById(R.id.btnLoginBack)
        loadingMask = findViewById(R.id.loginLoadingMask)
        tvLoginStatusHint = findViewById(R.id.tvLoginStatusHint)

        PetSettingsStore.ensureLocalLogin(this)
        val localId = PetSettingsStore.localUserId(this)
        tvLocalUserId.text = getString(R.string.login_local_user_id, localId)
        tvLocalUserId.setOnClickListener {
            val cm = getSystemService(ClipboardManager::class.java)
            cm?.setPrimaryClip(ClipData.newPlainText("local_user_id", localId))
            showStatusHint(getString(R.string.login_status_local_id_copied_inline))
            Toast.makeText(this, R.string.login_local_user_id_copied, Toast.LENGTH_SHORT).show()
        }
        etLocalNickname.setText(PetSettingsStore.loginNickname(this))
        updateNicknameCounter()
        etLocalNickname.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateNicknameCounter()
                updateConfirmButtonEnabled()
                if (tvLoginStatusHint.visibility == View.VISIBLE) clearStatusHint()
            }
        })
        updateConfirmButtonEnabled()
        showStatusHint(getString(R.string.login_status_local_ready))
        bindPhoneStatus()

        btnLoginConfirmLocal.setOnClickListener {
            val nickname = etLocalNickname.text?.toString()?.trim().orEmpty()
            if (nickname.isBlank()) {
                showStatusHint(getString(R.string.login_status_local_nickname_required))
                return@setOnClickListener
            }
            setLoading(true)
            mainHandler.postDelayed({
                PetSettingsStore.saveLocalNickname(this, nickname)
                setLoading(false)
                clearStatusHint()
                Toast.makeText(this, R.string.login_local_success, Toast.LENGTH_SHORT).show()
                finish()
            }, 500L)
        }
        btnBindPhone.setOnClickListener {
            startActivity(Intent(this, BindPhoneActivity::class.java))
        }
        btnPhonePasswordLogin.setOnClickListener {
            startActivity(Intent(this, PhonePasswordLoginActivity::class.java))
        }
        btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        setLoading(false)
        bindPhoneStatus()
    }

    private fun setLoading(loading: Boolean) {
        loadingMask.visibility = if (loading) View.VISIBLE else View.GONE
        etLocalNickname.isEnabled = !loading
        btnLoginConfirmLocal.isEnabled = !loading && etLocalNickname.text?.toString()?.trim().orEmpty().isNotBlank()
        btnBindPhone.isEnabled = !loading
        btnPhonePasswordLogin.isEnabled = !loading
        btnBack.isEnabled = !loading
    }

    private fun showStatusHint(text: String) {
        tvLoginStatusHint.text = text
        tvLoginStatusHint.visibility = View.VISIBLE
    }

    private fun clearStatusHint() {
        tvLoginStatusHint.text = ""
        tvLoginStatusHint.visibility = View.GONE
    }

    private fun updateNicknameCounter() {
        val length = etLocalNickname.text?.toString()?.trim().orEmpty().length
        tvNicknameCounter.text = getString(R.string.login_nickname_counter_format, length)
    }

    private fun updateConfirmButtonEnabled() {
        btnLoginConfirmLocal.isEnabled = etLocalNickname.text?.toString()?.trim().orEmpty().isNotBlank()
    }

    private fun bindPhoneStatus() {
        if (!PetSettingsStore.isPhoneBound(this)) {
            tvPhoneBindStatus.text = getString(
                R.string.login_security_bound_status,
                getString(R.string.login_security_bound_no)
            )
            return
        }
        val masked = PetSettingsStore.boundPhoneMasked(this)
        val bound = if (masked.isNotBlank()) {
            getString(R.string.login_security_bound_yes) + " " +
                getString(R.string.login_security_bound_phone, masked)
        } else {
            getString(R.string.login_security_bound_yes)
        }
        tvPhoneBindStatus.text = getString(R.string.login_security_bound_status, bound)
    }

    override fun onDestroy() {
        // no-op
        super.onDestroy()
    }
}

