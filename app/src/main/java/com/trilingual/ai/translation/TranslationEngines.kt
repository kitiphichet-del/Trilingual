package com.trilingual.ai.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.trilingual.ai.data.UserSettings
import com.trilingual.ai.util.Language
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Offline translation after the required ML Kit language packs have been downloaded. */
class DeviceTranslator {
    private val clients = mutableMapOf<String, Translator>()
    @Synchronized private fun client(from: String, to: String): Translator {
        val key = "$from:$to"
        return clients.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(when (from) { "zh" -> TranslateLanguage.CHINESE; "en" -> TranslateLanguage.ENGLISH; else -> TranslateLanguage.THAI })
                .setTargetLanguage(when (to) { "zh" -> TranslateLanguage.CHINESE; "en" -> TranslateLanguage.ENGLISH; else -> TranslateLanguage.THAI })
                .build()
            Translation.getClient(options)
        }
    }
    suspend fun translate(text: String, source: String, target: String): String {
        if (source == target) return text
        val engine = client(source, target)
        // First run may download a model; afterwards this executes on-device.
        engine.downloadModelIfNeeded().await()
        return engine.translate(text).await()
    }
    suspend fun prepareAll(onProgress: (String) -> Unit) {
        for (lang in listOf("th", "en", "zh")) for (other in listOf("th", "en", "zh")) {
            if (lang != other) {
                onProgress("กำลังเตรียม $lang → $other")
                client(lang, other).downloadModelIfNeeded().await()
            }
        }
        onProgress("ดาวน์โหลดโมเดลคำแปลเสร็จแล้ว")
    }
    @Synchronized fun close() { clients.values.forEach { it.close() }; clients.clear() }
}

/** Sends text ONLY and ONLY when the user enables a configured HTTPS AI service. */
class OnlineTextAi(private val settings: UserSettings) {
    private suspend fun request(system: String, user: String, maxTokens: Int): String = withContext(Dispatchers.IO) {
        val endpoint = settings.onlineUrl
        require(endpoint.startsWith("https://", ignoreCase = true)) { "ต้องใช้ HTTPS" }
        val secret = settings.apiKey()
        require(secret.isNotBlank()) { "กรุณากำหนด API key ก่อน" }
        val json = JSONObject()
            .put("model", settings.onlineModel)
            .put("temperature", 0.2)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
        val conn = URL(endpoint).openConnection() as HttpsURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Authorization", "Bearer $secret")
        try {
            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("AI HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        } finally { conn.disconnect() }
    }

    suspend fun translate(text: String, from: String, to: String): String {
        val sourceName = Language.promptName(from)
        val targetName = Language.promptName(to)
        val outputRule = when (to) {
            "th" -> "Output natural Thai script only. Never output pinyin, romanization, Chinese characters, explanations, or labels unless a proper name must be preserved."
            "zh" -> "Output Simplified Chinese characters only. Never output Traditional Chinese or pinyin unless the source explicitly asks for pinyin."
            else -> "Output natural English only. Do not add explanations or labels."
        }
        return request(
            "You are a precise real-time interpreter. Source language: $sourceName. Target language: $targetName. " +
                "$outputRule Return only the translation. Preserve names, numbers, and terminology. " +
                "Terminology hints: ${settings.glossary.take(1000)}",
            text.take(4000), 1400
        )
    }
    suspend fun summarize(transcript: String): String = request(
        "Summarize a multilingual meeting in Thai. Include main topics, decisions, and next actions. " +
            "Do not invent decisions. If uncertain, say so. Use clear bullet points.",
        transcript.take(16000), 1600
    )
}
