package com.ccpet.desktoppet

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PolicyDocumentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_policy_document)

        val tvTitle = findViewById<TextView>(R.id.tvPolicyTitle)
        val tvContent = findViewById<TextView>(R.id.tvPolicyContent)
        val btnBack = findViewById<TextView>(R.id.btnPolicyBack)

        val type = intent.getStringExtra(EXTRA_DOC_TYPE)
        if (type == TYPE_TERMS) {
            tvTitle.text = getString(R.string.settings_user_agreement)
            tvContent.text = getString(R.string.policy_terms_content)
        } else {
            tvTitle.text = getString(R.string.settings_privacy_policy)
            tvContent.text = getString(R.string.policy_privacy_content)
        }

        btnBack.setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_DOC_TYPE = "extra_doc_type"
        const val TYPE_PRIVACY = "privacy"
        const val TYPE_TERMS = "terms"
    }
}
