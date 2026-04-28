package com.ccpet.desktoppet

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ccpet.desktoppet.auth.ApiResult
import com.ccpet.desktoppet.auth.AuthApiProvider
import com.ccpet.desktoppet.auth.PhoneLoginRequest
import kotlinx.coroutines.launch

class PhonePasswordLoginActivity : AppCompatActivity() {
    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnSubmit: Button
    private lateinit var btnBack: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_password_login)

        etPhone = findViewById(R.id.etPhoneLoginPhone)
        etPassword = findViewById(R.id.etPhoneLoginPassword)
        tvStatus = findViewById(R.id.tvPhoneLoginStatus)
        btnSubmit = findViewById(R.id.btnPhoneLoginSubmit)
        btnBack = findViewById(R.id.btnPhoneLoginBack)

        btnBack.setOnClickListener { finish() }
        btnSubmit.setOnClickListener {
            val phone = etPhone.text?.toString().orEmpty().trim()
            val password = etPassword.text?.toString().orEmpty()
            lifecycleScope.launch {
                when (val r = AuthApiProvider.provide(this@PhonePasswordLoginActivity).phoneLogin(PhoneLoginRequest(phone, password))) {
                    is ApiResult.Success -> {
                        PetSettingsStore.ensureLocalLogin(this@PhonePasswordLoginActivity)
                        tvStatus.visibility = View.GONE
                        Toast.makeText(this@PhonePasswordLoginActivity, R.string.phone_login_success, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is ApiResult.Failure -> {
                        tvStatus.visibility = View.VISIBLE
                        tvStatus.text = r.errorMessage.ifBlank { getString(R.string.phone_login_fail) }
                    }
                }
            }
        }
    }
}
