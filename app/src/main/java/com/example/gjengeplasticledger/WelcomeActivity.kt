package com.example.gjengeplasticledger

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val startBtn = findViewById<Button>(R.id.startBtn)

        startBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}