package com.trilingual.ai.speech

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.trilingual.ai.MainActivity
import com.trilingual.ai.TriLingualApp
import com.trilingual.ai.service.SubtitleOverlayService
import com.trilingual.ai.translation.OnlineTextAi
import com.trilingual.ai.util.Language
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** Owns microphone outside Activity; rotations and screen-off do not recreate recognition. */
class RecognitionService : Service(), RecognitionListener {
    companion object {
        const val ACTION_START = "com.trilingual.ai.START"
        const val ACTION_PAUSE = "com.trilingual.ai.PAUSE"
        const val ACTION_RESUME = "com.trilingual.ai.RESUME"
        const val ACTION_STOP = "com.trilingual.ai.STOP"
        const val ACTION_SPEAKER = "com.trilingual.ai.SPEAKER"
        const val ACTION_LANGUAGE = "com.trilingual.ai.LANGUAGE"
        const val EXTRA_MODE = "mode"
        const val EXTRA_LANGUAGE = "language"
        const val EXTRA_SPEAKER = "speaker"
        private const val CHANNEL = "recording"
        private const val NOTICE_ID = 1402
    }
    private val handler = Handler(Looper.getMainLooper())
    private val sequence = AtomicInteger(0)
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var stopped = true
    private var useDevice = false
    private var systemFallbackUsed = false
    private var language = "th"
    private var detected = "th"
    private var speaker = 1
    private var meetingId = 0L
    private lateinit var app: TriLingualApp
    private lateinit var cloud: OnlineTextAi

    override fun onCreate() {
        super.onCreate()
        app = application as TriLingualApp
        cloud = OnlineTextAi(app.settings)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL, "การบันทึกและแปลเสียง", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!stopped) return START_STICKY
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    app.live.update { it.copy(status = "ต้องอนุญาตไมโครโฟนก่อน") }
                    stopSelf(); return START_NOT_STICKY
                }
                stopped = false
                listening = false
                language = intent.getStringExtra(EXTRA_LANGUAGE) ?: "th"
                detected = if (language == "auto") "th" else language
                speaker = 1
                useDevice = app.settings.onDevice
                systemFallbackUsed = false
                meetingId = app.store.newMeeting(intent.getStringExtra(EXTRA_MODE) ?: "conversation")
                app.live.update { it.copy(meetingId = meetingId, running = true, paused = false,
                    mode = intent.getStringExtra(EXTRA_MODE) ?: "conversation", language = language,
                    speaker = speaker, status = "กำลังเริ่มไมโครโฟน…", partial = "") }
                startForeground(NOTICE_ID, notification())
                createRecognizer()
                if (!stopped) startRecognition()
            }
            ACTION_PAUSE -> {
                if (!stopped) {
                    listening = false
                    handler.removeCallbacksAndMessages(null)
                    recognizer?.cancel()
                    app.live.update { it.copy(paused = true, partial = "", status = "หยุดชั่วคราว") }
                }
            }
            ACTION_RESUME -> if (!stopped) {
                app.live.update { it.copy(paused = false, status = "กำลังฟัง…") }
                recognizer?.cancel()
                scheduleRestart(500)
            }
            ACTION_LANGUAGE -> if (!stopped) {
                language = intent.getStringExtra(EXTRA_LANGUAGE) ?: "th"
                detected = if (language == "auto") "th" else language
                systemFallbackUsed = false
                useDevice = app.settings.onDevice
                app.live.update { it.copy(language = language, partial = "") }
                listening = false
                recognizer?.cancel()
                scheduleRestart(550)
            }
            ACTION_SPEAKER -> {
                speaker = intent.getIntExtra(EXTRA_SPEAKER, 1).coerceIn(1, 8)
                app.live.update { it.copy(speaker = speaker) }
            }
            ACTION_STOP -> endSession()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, RecognitionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("TriLingual AI กำลังฟังเสียง")
            .setContentText("แตะเพื่อเปิดแอป หรือหยุดการบันทึก")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "หยุด", stop)
            .build()
    }

    private fun createRecognizer() {
        try {
            recognizer?.destroy()
            if (useDevice) {
                if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                    app.live.update { it.copy(status = "อุปกรณ์ไม่มีระบบรู้จำเสียงออฟไลน์ กรุณาเลือกโหมดระบบ") }
                    endSession(); return
                }
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                    app.live.update { it.copy(status = "อุปกรณ์ไม่มีบริการรู้จำเสียง") }
                    endSession(); return
                }
                recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            }
            recognizer?.setRecognitionListener(this)
        } catch (e: Exception) {
            app.live.update { it.copy(status = "เปิดรู้จำเสียงไม่สำเร็จ: ${e.message}") }
            endSession()
        }
    }

    private fun fallbackToSystemRecognizer(reason: String): Boolean {
        if (!useDevice || systemFallbackUsed || stopped) return false
        systemFallbackUsed = true
        useDevice = false
        listening = false
        runCatching { recognizer?.cancel() }
        app.live.update { it.copy(
            partial = "",
            status = "$reason · กำลังสลับเป็นบริการรู้จำเสียงของระบบ (Hybrid)"
        ) }
        createRecognizer()
        if (stopped || recognizer == null) return false
        scheduleRestart(450)
        return true
    }

    private fun startRecognition() {
        if (stopped || app.live.view.value.paused || listening || recognizer == null) return
        listening = true
        detected = if (language == "auto") "th" else language
        val req = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Language.tag(detected))
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (Build.VERSION.SDK_INT >= 33 && app.settings.glossary.isNotBlank()) {
                putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(app.settings.glossary.split(',', '\n').map { it.trim() }.filter { it.isNotBlank() }.take(40)))
            }
            if (Build.VERSION.SDK_INT >= 34 && language == "auto") {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, arrayListOf("th-TH", "en-US", "zh-CN"))
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayListOf("th-TH", "en-US", "zh-CN"))
            }
        }
        try {
            recognizer?.startListening(req)
            app.live.update { it.copy(status = if (useDevice) "กำลังฟัง (ออฟไลน์ตามระบบอุปกรณ์)" else "กำลังฟัง (บริการระบบอาจใช้อินเทอร์เน็ต)") }
        } catch (e: Exception) {
            listening = false
            app.live.update { it.copy(status = "ไมโครโฟน: ${e.message}") }
            scheduleRestart(1800)
        }
    }

    private fun scheduleRestart(delay: Long) {
        if (stopped || app.live.view.value.paused) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (!stopped && !app.live.view.value.paused) { listening = false; startRecognition() } }, delay)
    }

    override fun onReadyForSpeech(params: Bundle?) { app.live.update { it.copy(status = "กำลังฟัง…") } }
    override fun onBeginningOfSpeech() { app.live.update { it.copy(status = "ตรวจพบเสียงพูด") } }
    override fun onRmsChanged(rmsdB: Float) { app.live.update { it.copy(loudness = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) } }
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() { app.live.update { it.copy(status = "กำลังประมวลผลประโยค…") } }
    override fun onError(error: Int) {
        if (stopped || app.live.view.value.paused) return
        listening = false
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            app.live.update { it.copy(status = "ไม่มีสิทธิ์ใช้ไมโครโฟน (ข้อผิดพลาด $error)") }
            endSession(); return
        }
        if ((error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) && useDevice) {
            val name = Language.label(if (language == "auto") detected else language)
            if (fallbackToSystemRecognizer("ชุดรู้จำเสียงออฟไลน์ $name ไม่พร้อม")) return
        }
        if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
            app.live.update { it.copy(status = "บริการรู้จำเสียงของเครื่องไม่รองรับ ${Language.label(if (language == "auto") detected else language)} (รหัส $error)") }
            endSession(); return
        }
        // No-match and end-of-speech are expected during an ongoing conversation.
        val wait = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS) 2000L else 850L
        app.live.update { it.copy(partial = "", status = "กำลังเชื่อมต่อการฟังอีกครั้ง (รหัส $error)") }
        scheduleRestart(wait)
    }
    override fun onResults(results: Bundle?) {
        listening = false
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
        app.live.update { it.copy(partial = "") }
        if (!stopped && !app.live.view.value.paused && text.isNotEmpty()) persistAndTranslate(text)
        scheduleRestart(450)
    }
    override fun onPartialResults(partialResults: Bundle?) {
        val str = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (!stopped && !app.live.view.value.paused) app.live.update { it.copy(partial = str) }
    }
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
    override fun onLanguageDetection(results: Bundle) {
        if (Build.VERSION.SDK_INT >= 34 && language == "auto") {
            val value = results.getString(SpeechRecognizer.DETECTED_LANGUAGE).orEmpty()
            val stem = value.substringBefore('-').lowercase()
            if (stem in Language.supported) detected = stem
        }
    }

    private fun persistAndTranslate(source: String) {
        val id = meetingId
        val sp = speaker
        val lang = Language.detectText(source, detected)
        // Persist before starting potentially slow translations; serialized on service main thread.
        val phraseId = app.store.addPhrase(id, sp, lang, source)
        val seq = sequence.incrementAndGet()
        app.translationScope.launch {
            val outputs = mutableMapOf(lang to source)
            for (to in Language.supported.filter { it != lang }) {
                val result = if (app.settings.onlineAi && app.settings.apiKey().isNotBlank()) {
                    runCatching { cloud.translate(source, lang, to) }.getOrNull()
                } else null
                outputs[to] = result ?: runCatching { app.deviceTranslator.translate(source, lang, to) }
                    .getOrElse { "[แปล $to ไม่สำเร็จ: ${it.message?.take(80)}]" }
            }
            app.store.updateTranslation(phraseId, outputs["th"].orEmpty(), outputs["en"].orEmpty(), outputs["zh"].orEmpty(), id)
            if (app.settings.overlayEnabled) {
                runCatching {
                    startService(Intent(this@RecognitionService, SubtitleOverlayService::class.java)
                        .setAction(SubtitleOverlayService.ACTION_UPDATE)
                        .putExtra("subtitle", outputs["th"].orEmpty()))
                }
            }
            if (seq == sequence.get() && !stopped) app.live.update { it.copy(status = "กำลังฟัง…") }
        }
    }

    private fun endSession() {
        if (stopped) { stopSelf(); return }
        stopped = true; listening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null
        app.live.update { it.copy(running = false, paused = false, partial = "", status = "บันทึกแล้ว", loudness = 0f) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
