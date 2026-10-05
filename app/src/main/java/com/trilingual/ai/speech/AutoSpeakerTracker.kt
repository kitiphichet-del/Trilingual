package com.trilingual.ai.speech

/**
 * Lightweight automatic speaker labels for multilingual conversations.
 *
 * v0.1.3 keeps a stable speaker number per detected language. This removes the need
 * to tap speaker buttons during Thai/English/Chinese conversations. It is not a
 * voice-fingerprint diarizer, so different people using the same language can share a label.
 */
class AutoSpeakerTracker(private val maxSpeakers: Int = 4) {
    private val byLanguage = linkedMapOf<String, Int>()
    private var next = 1

    fun reset() {
        byLanguage.clear()
        next = 1
    }

    fun assign(language: String): Int {
        byLanguage[language]?.let { return it }
        val assigned = next.coerceAtMost(maxSpeakers)
        byLanguage[language] = assigned
        if (next < maxSpeakers) next += 1
        return assigned
    }
}
