
package com.example.jarvis

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Recognizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class JarvisForegroundService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val CHANNEL_ID = "jarvis_channel"
        private const val NOTIFICATION_ID = 101
        private const val ACTION_START = "JARVIS_START"
        private const val ACTION_STOP = "JARVIS_STOP"

        fun start(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java)
            intent.action = ACTION_START
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
        }
    }

    private val running = AtomicBoolean(false)
    private val speaking = AtomicBoolean(false)

    private var recorder: AudioRecord? = null
    private var model: org.vosk.Model? = null
    private var recognizer: Recognizer? = null
    private var tts: TextToSpeech? = null
    private var armedUntil = 0L

    private val sampleRate = 16000

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopAssistant()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_START && !running.get()) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                stopSelf()
                return START_NOT_STICKY
            }

            startForeground(
                NOTIFICATION_ID,
                buildNotification("Jarvis is starting")
            )

            running.set(true)
            JarvisStateBus.update(
                isRunning = true,
                statusText = "Loading voice model"
            )

            Thread { startListening() }.start()
        }

        return START_STICKY
    }

    private fun startListening() {
        try {
            model = VoskModelLoader.loadModel(this)

            val phrases = listOf(
                "hey jarvis",
                "open instagram",
                "open whatsapp",
                "open youtube",
                "open chrome",
                "open snapchat",
                "open snap",
                "open spotify",
                "open camera",
                "open gallery",
                "open bluetooth",
                "turn on bluetooth",
                "turn off bluetooth",
                "open wifi",
                "open wi fi",
                "turn on wifi",
                "turn off wifi",
                "open settings",
                "what time is it",
                "stop jarvis",
                "[unk]"
            )

            recognizer = Recognizer(
                model,
                sampleRate.toFloat(),
                JSONArray(phrases).toString()
            )

            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBuffer <= 0) {
                throw IllegalStateException("Audio buffer error")
            }

            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )

            recorder?.startRecording()

            JarvisStateBus.update(statusText = "Listening for Hey Jarvis")
            updateNotification("Listening for Hey Jarvis")

            val buffer = ShortArray(4096)

            while (running.get()) {
                val count = recorder?.read(buffer, 0, buffer.size) ?: -1

                if (count > 0 && !speaking.get()) {
                    val activeRecognizer = recognizer ?: break

                    if (activeRecognizer.acceptWaveForm(buffer, count)) {
                        val text = JSONObject(activeRecognizer.result)
                            .optString("text")
                            .lowercase(Locale.ROOT)
                            .trim()

                        if (text.isNotBlank()) {
                            handleCommand(text)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            JarvisStateBus.update(
                statusText = "Error: ${e.message}"
            )
            speak("Voice system error")
        } finally {
            releaseAudio()
        }
    }

    private fun handleCommand(command: String) {
        JarvisStateBus.update(lastHeard = command)

        if (command.contains("hey jarvis")) {
            armedUntil = Long.MAX_VALUE
            speak("Yes sir. I'm listening")
            JarvisStateBus.update(statusText = "Ready for command")
            return
        }

        if (command.contains("stop jarvis")) {
            speak("Stopping Jarvis")
            stopAssistant()
            return
        }

        if (System.currentTimeMillis() > armedUntil) {
            return
        }

        when {
            command.contains("instagram") -> {
                speak("Opening Instagram")
                openApp("com.instagram.android")
            }

            command.contains("whatsapp") -> {
                speak("Opening WhatsApp")
                openApp("com.whatsapp")
            }

            command.contains("youtube") -> {
                speak("Opening YouTube")
                openApp("com.google.android.youtube")
            }

            command.contains("chrome") -> {
                speak("Opening Chrome")
                openApp("com.android.chrome")
            }

            command.contains("snapchat") || command.contains("snap") -> {
                speak("Opening Snapchat")
                openApp("com.snapchat.android")
            }

            command.contains("spotify") -> {
                speak("Opening Spotify")
                openApp("com.spotify.music")
            }

            command.contains("bluetooth") -> {
                speak("Opening Bluetooth settings")
                openBluetoothSettings()
            }

            command.contains("wifi") || command.contains("wi fi") -> {
                speak("Opening internet settings")
                openInternetSettings()
            }

            command.contains("settings") -> {
                speak("Opening settings")
                openSettings()
            }

            command.contains("camera") -> {
                speak("Opening camera")
                openCamera()
            }

            command.contains("gallery") -> {
                speak("Opening gallery")
                openGallery()
            }

            command.contains("time") -> {
                val time = java.text.SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
                ).format(java.util.Date())

                speak("The time is $time")
            }

            else -> speak("Command not recognized")
        }

        JarvisStateBus.update(statusText = "Listening for your command")
    }

    private fun openBluetoothSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            speak("Bluetooth settings not available")
        }
    }

    private fun openInternetSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            speak("Internet settings not available")
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            speak("Settings not available")
        }
    }

    private fun openApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            speak("App not found")
        }
    }

    private fun openCamera() {
        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            startActivity(intent)
        } catch (e: Exception) {
            speak("Camera not available")
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_APP_GALLERY)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            startActivity(intent)
        } catch (e: Exception) {
            speak("Gallery not available")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US

            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        speaking.set(true)
                    }

                    override fun onDone(id: String?) {
                        speaking.set(false)
                    }

                    override fun onError(id: String?) {
                        speaking.set(false)
                    }
                }
            )
        }
    }

    private fun speak(message: String) {
        val engine = tts ?: return

        speaking.set(true)

        engine.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "jarvis_${System.currentTimeMillis()}"
        )
    }

    private fun stopAssistant() {
        running.set(false)

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        JarvisStateBus.update(
            isRunning = false,
            statusText = "Jarvis stopped"
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseAudio() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        recorder?.release()
        recorder = null

        recognizer?.close()
        recognizer = null

        model?.close()
        model = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Assistant")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        running.set(false)

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

