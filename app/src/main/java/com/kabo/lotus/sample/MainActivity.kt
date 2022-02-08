package com.kabo.lotus.sample

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.kabo.lotus.LotusService
import com.kabo.lotus.sample.sample.R

class MainActivity : AppCompatActivity() {

    private val button: Button
        get() = findViewById(R.id.button)

    private val startIntent: Intent
        get() = Intent(applicationContext, LotusService::class.java).apply {
            action = LotusService.Action.START.value
        }

    private val stopIntent: Intent
        get() = Intent(applicationContext, LotusService::class.java).apply {
            action = LotusService.Action.STOP.value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        button.setOnClickListener {
            if (button.text == "START") {
                startForegroundService(startIntent)
                button.text = "STOP"
            } else {
                startForegroundService(stopIntent)
                button.text = "START"
            }
        }
    }

    override fun onPause() {
        super.onPause()
        startForegroundService(stopIntent)
        button.text = "START"
    }
}
