package com.example.jarvis

import android.net.Uri
import android.speech.RecognizerIntent
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSend: Button
    private lateinit var btnAttach: Button
    private lateinit var btnMic: Button
    private lateinit var btnVoice: Button
    private lateinit var etMessage: EditText
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var tvStatus: TextView

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceMode = "MALE"
    private var selectedAttachmentUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnSend = findViewById(R.id.btnSend)
        btnAttach = findViewById(R.id.btnAttach)
        btnMic = findViewById(R.id.btnMic)
        btnVoice = findViewById(R.id.btnVoice)
        etMessage = findViewById(R.id.etMessage)
        chatContainer = findViewById(R.id.chatContainer)
        chatScroll = findViewById(R.id.chatScroll)

        voiceMode = getSharedPreferences("jarvis_settings", MODE_PRIVATE)
            .getString("voice_mode", "MALE") ?: "MALE"

        updateVoiceButton()
        tts = TextToSpeech(this, this)

        btnVoice.setOnClickListener {
            voiceMode = if (voiceMode == "MALE") "FEMALE" else "MALE"
            getSharedPreferences("jarvis_settings", MODE_PRIVATE)
                .edit().putString("voice_mode", voiceMode).apply()

            updateVoiceButton()
            if (ttsReady) applyVoice()
        }

        btnStart.setOnClickListener {
            if (hasPermissions()) {
                JarvisForegroundService.start(this)
                tvStatus.text = "● Starting JARVIS..."
            } else {
                requestPermissions()
            }
        }

        btnStop.setOnClickListener {
            JarvisForegroundService.stop(this)
            tvStatus.text = "● JARVIS stopped"
        }

        btnAttach.setOnClickListener { openAttachmentPicker() }

        btnMic.setOnClickListener {
            startVoiceInput()
        }

        btnSend.setOnClickListener {
            sendChatMessage()
        }
    }

    private fun openAttachmentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("image/*", "video/*")
            )
        }

        try {
            startActivityForResult(intent, 201)
        } catch (e: Exception) {
            tvStatus.text = "● File picker unavailable"
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
        }

        try {
            startActivityForResult(intent, 200)
        } catch (e: Exception) {
            tvStatus.text = "● Speech recognition unavailable"
        }
    }

    @Deprecated("Using activity result for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 201 && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                selectedAttachmentUri = uri
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Temporary read permission may still be available.
                }

                val mimeType = contentResolver.getType(uri) ?: ""
                tvStatus.text = if (mimeType.startsWith("video/")) {
                    "● Video selected"
                } else {
                    "● Photo selected"
                }
            }
            return
        }

        if (requestCode == 200 && resultCode == RESULT_OK) {
            val matches = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )
            val spokenText = matches?.firstOrNull()

            if (!spokenText.isNullOrBlank()) {
                etMessage.setText(spokenText)
                etMessage.setSelection(etMessage.text.length)
                tvStatus.text = "● Voice captured"
            }
        }
    }

    private fun updateVoiceButton() {
        btnVoice.text = "VOICE: $voiceMode"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.language = Locale.US
            applyVoice()
        }
    }

    private fun applyVoice() {
        val engine = tts ?: return
        engine.setSpeechRate(0.88f)

        val keyword = voiceMode.lowercase(Locale.ROOT)
        val matchingVoice = engine.voices?.firstOrNull {
            it.name.lowercase(Locale.ROOT).contains(keyword)
        }

        if (matchingVoice != null) {
            engine.voice = matchingVoice
        } else {
            // Fallback when the installed TTS engine has no named gender voice.
            engine.setPitch(if (voiceMode == "FEMALE") 1.12f else 0.88f)
        }
    }

    private fun speakAnswer(answer: String) {
        if (ttsReady) {
            tts?.speak(
                answer,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis_reply_${System.currentTimeMillis()}"
            )
        }
    }

    private fun sendChatMessage() {
        val typedMessage = etMessage.text.toString().trim()
        val attachment = selectedAttachmentUri

        if (typedMessage.isEmpty() && attachment == null) return

        val message = typedMessage.ifEmpty {
            "Please analyze this image and describe what you see."
        }

        val displayMessage = if (attachment != null) {
            "[Media attached] $message"
        } else {
            message
        }

        addMessage(displayMessage, true)
        etMessage.text.clear()

        btnSend.isEnabled = false
        tvStatus.text = "● JARVIS is thinking..."

        GeminiChat.sendMessage(
            message,
            context = this,
            attachmentUri = attachment
        ) { result ->
            runOnUiThread {
                btnSend.isEnabled = true

                result.onSuccess { answer ->
                    addMessage(answer, false)
                    if (selectedAttachmentUri == attachment) {
                        selectedAttachmentUri = null
                    }
                    speakAnswer(answer)
                    tvStatus.text = "● Ready"
                }

                result.onFailure { error ->
                    addMessage(
                        "Sorry, I couldn't connect. ${error.message}",
                        false
                    )
                    tvStatus.text = "● AI connection failed"
                }
            }
        }
    }

    private fun addMessage(message: String, fromUser: Boolean) {
        val label = TextView(this).apply {
            text = if (fromUser) "YOU" else "JARVIS"
            textSize = 11f
            setTextColor(
                Color.parseColor(if (fromUser) "#7F9DB5" else "#45D9FF")
            )
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = if (fromUser) Gravity.END else Gravity.START
        }

        val labelParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 10
            bottomMargin = 4
        }

        chatContainer.addView(label, labelParams)

        val bubble = TextView(this).apply {
            text = message
            textSize = 15f
            setTextColor(Color.parseColor("#EDF5FC"))
            setPadding(15, 12, 15, 12)
            setTextIsSelectable(true)

            background = GradientDrawable().apply {
                setColor(
                    Color.parseColor(
                        if (fromUser) "#12394A" else "#19232E"
                    )
                )
                cornerRadius = 22f
            }
        }

        bubble.maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()

        val bubbleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (fromUser) Gravity.END else Gravity.START
            bottomMargin = 5
        }

        chatContainer.addView(bubble, bubbleParams)
        chatScroll.post {
            chatScroll.fullScroll(View.FOCUS_DOWN)
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
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityCompat.requestPermissions(
            this,
            permissions.toTypedArray(),
            100
        )
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
