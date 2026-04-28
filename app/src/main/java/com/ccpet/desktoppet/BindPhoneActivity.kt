package com.ccpet.desktoppet

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ccpet.desktoppet.auth.ApiResult
import com.ccpet.desktoppet.auth.AuthApiProvider
import com.ccpet.desktoppet.auth.BindPhoneRequest
import com.ccpet.desktoppet.auth.SendCodeRequest
import kotlinx.coroutines.launch

class BindPhoneActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var etPhone: EditText
    private lateinit var etCode: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPasswordConfirm: EditText
    private lateinit var btnSendCode: Button
    private lateinit var btnSubmit: Button
    private lateinit var tvStatus: TextView
    private lateinit var btnBack: TextView

    private var requestBizType: String = "bind"
    private var countdown = 0

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (countdown <= 0) {
                btnSendCode.isEnabled = true
                btnSendCode.text = getString(R.string.bind_phone_send_code)
                return
            }
            btnSendCode.isEnabled = false
            btnSendCode.text = getString(R.string.bind_phone_send_code_countdown, countdown)
            countdown -= 1
            mainHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bind_phone)

        etPhone = findViewById(R.id.etBindPhone)
        etCode = findViewById(R.id.etBindCode)
        etPassword = findViewById(R.id.etBindPassword)
        etPasswordConfirm = findViewById(R.id.etBindPasswordConfirm)
        btnSendCode = findViewById(R.id.btnBindSendCode)
        btnSubmit = findViewById(R.id.btnBindSubmit)
        tvStatus = findViewById(R.id.tvBindStatus)
        btnBack = findViewById(R.id.btnBindBack)

        btnBack.setOnClickListener { finish() }
        btnSendCode.setOnClickListener { handleSendCode() }
        btnSubmit.setOnClickListener { handleSubmit() }
    }

    private fun handleSendCode() {
        val phone = etPhone.text?.toString().orEmpty().trim()
        if (!isPhoneValid(phone)) {
            showStatus(getString(R.string.bind_phone_status_invalid_phone))
            return
        }
        countdown = 60
        mainHandler.removeCallbacks(countdownRunnable)
        mainHandler.post(countdownRunnable)
        lifecycleScope.launch {
            when (val r = AuthApiProvider.provide(this@BindPhoneActivity).sendCode(SendCodeRequest(phone, requestBizType))) {
                is ApiResult.Success -> {
                    val code = r.data.demoCode
                    showStatus(
                        if (code.isNotBlank()) getString(R.string.bind_phone_status_code_demo, code)
                        else getString(R.string.bind_phone_send_code)
                    )
                }
                is ApiResult.Failure -> {
                    showStatus(r.errorMessage.ifBlank { getString(R.string.bind_phone_status_invalid_phone) })
                }
            }
        }
    }

    private fun handleSubmit() {
        val phone = etPhone.text?.toString().orEmpty().trim()
        val code = etCode.text?.toString().orEmpty().trim()
        val password = etPassword.text?.toString().orEmpty()
        val confirm = etPasswordConfirm.text?.toString().orEmpty()
        if (!isPhoneValid(phone)) {
            showStatus(getString(R.string.bind_phone_status_invalid_phone))
            return
        }
        if (password.length < 6) {
            showStatus(getString(R.string.bind_phone_status_password_short))
            return
        }
        if (password != confirm) {
            showStatus(getString(R.string.bind_phone_status_password_mismatch))
            return
        }
        val userId = PetSettingsStore.localUserId(this)
        lifecycleScope.launch {
            when (
                val r = AuthApiProvider.provide(this@BindPhoneActivity).bindPhone(
                    BindPhoneRequest(
                        userId = userId,
                        phone = phone,
                        code = code,
                        password = password
                    )
                )
            ) {
                is ApiResult.Success -> {
                    Toast.makeText(this@BindPhoneActivity, R.string.bind_phone_success, Toast.LENGTH_SHORT).show()
                    finish()
                }
                is ApiResult.Failure -> {
                    showStatus(r.errorMessage.ifBlank { getString(R.string.bind_phone_status_invalid_code) })
                }
            }
        }
    }

    private fun showStatus(text: String) {
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = text
    }

    private fun isPhoneValid(phone: String): Boolean {
        return phone.length == 11 && phone.all { it.isDigit() }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(countdownRunnable)
        super.onDestroy()
    }
}
