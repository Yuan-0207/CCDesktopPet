package com.ccpet.desktoppet

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PetNameSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pet_name_settings)

        val inputPetName = findViewById<EditText>(R.id.inputPetName)
        val btnSavePetName = findViewById<Button>(R.id.btnSavePetName)

        inputPetName.setText(PetSettingsStore.petName(this))

        btnSavePetName.setOnClickListener {
            PetSettingsStore.setPetName(this, inputPetName.text.toString())
            Toast.makeText(this, R.string.settings_pet_name_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        PetDesktopLauncher.requestStopDesktopPet(this)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PetDesktopLauncher.requestStartDesktopPet(this)
    }
}
