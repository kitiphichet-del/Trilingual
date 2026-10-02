package com.trilingual.ai.translation

import com.trilingual.ai.data.Phrase

/** A deterministic fallback, NOT an AI-generated summary. */
object Notes {
    fun offline(phrases: List<Phrase>): String {
        if (phrases.isEmpty()) return "ยังไม่มีบทสนทนาที่บันทึก"
        val selected = if (phrases.size <= 5) phrases else listOf(phrases.first(), phrases[phrases.size / 4], phrases[phrases.size / 2], phrases[(phrases.size * 3) / 4], phrases.last())
        return "สรุปเบื้องต้นจากข้อความที่บันทึก (ไม่ใช่ AI สรุปความ):\n" +
            selected.joinToString("\n") { "• ผู้พูด ${it.speaker}: ${it.thai.ifBlank { it.source }}" }
    }
    fun export(meetingTitle: String, phrases: List<Phrase>, summary: String): String = buildString {
        appendLine("# $meetingTitle\n")
        if (summary.isNotBlank()) appendLine("## สรุป\n$summary\n")
        appendLine("## บทสนทนา")
        phrases.forEach { item ->
            appendLine("\n### ผู้พูด ${item.speaker} · ${item.sourceLanguage}")
            appendLine("ต้นฉบับ: ${item.source}")
            if (item.thai.isNotBlank()) appendLine("ไทย: ${item.thai}")
            if (item.english.isNotBlank()) appendLine("English: ${item.english}")
            if (item.chinese.isNotBlank()) appendLine("简体中文: ${item.chinese}")
        }
    }
}
