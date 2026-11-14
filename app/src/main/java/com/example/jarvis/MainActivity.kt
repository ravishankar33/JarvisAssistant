package com.example.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSend: Button
    private lateinit var etMessage: EditText
    private lateinit var tvChatHistory: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnSend = findViewById(R.id.btnSend)
        etMessage = findViewById(R.id.etMessage)
        tvChatHistory = findViewById(R.id.tvChatHistory)

        btnStart.setOnClickListener {
            if (hasPermissions()) {
                JarvisForegroundService.start(this)
                tvStatus.text = "Starting Jarvis..."
            } else {
                requestPermissions()
            }
        }

        btnStop.setOnClickListener {
            JarvisForegroundService.stop(this)
            tvStatus.text = "Jarvis stopped"
        }

        btnSend.setOnClickListener {
            val message = etMessage.text.toString().trim()

            if (message.isEmpty()) return@setOnClickListener

            tvChatHistory.append("\n\nYOU: $message")
            etMessage.text.clear()
            btnSend.isEnabled = false
            tvStatus.text = "Jarvis is thinking..."

            GeminiChat.sendMessage(message) { result ->
                runOnUiThread {
                    btnSend.isEnabled = true

                    result.onSuccess { answer ->
                        tvChatHistory.append("\n\nJARVIS: $answer")
                        tvStatus.text = "Ready"
                    }

                    result.onFailure { error ->
                        tvChatHistory.append(
                            "\n\nJARVIS ERROR: ${error.message}"
                        )
                        tvStatus.text = "AI connection failed"
                    }
                }
            }
        }
    }

    private fun hasPermissions(): Boolean {
        val mic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notifications = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return mic && notifications
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityCompat.requestPermissions(
            this,
            permissions.toTypedArray(),
            100
        )
    }
}
