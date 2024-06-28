package com.dprofe.locationalarm

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity


class SettingsActivity : AppCompatActivity() {
    private lateinit var btnBack : Button
    private lateinit var numRadius: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        btnBack = findViewById(R.id.settings_btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        numRadius = findViewById(R.id.settings_numRadius)
        numRadius.setText(MainActivity.currentRadius.toString())
        numRadius.setOnEditorActionListener { textView, i, keyEvent ->
            MainActivity.currentRadius = numRadius.text.toString().toIntOrNull() ?: 100

            return@setOnEditorActionListener false
        }
    }
}