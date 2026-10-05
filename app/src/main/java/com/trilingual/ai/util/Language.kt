package com.trilingual.ai.util

object Language {
    val supported = listOf("th", "en", "zh")
    fun tag(language: String) = when (language) { "zh" -> "zh-CN"; "en" -> "en-US"; else -> "th-TH" }
    fun label(language: String) = when (language) { "zh" -> "中文（简体）"; "en" -> "English"; "th" -> "ไทย"; else -> "อัตโนมัติ*" }
    fun promptName(language: String) = when (language) {
        "zh" -> "Simplified Chinese (zh-CN)"
        "en" -> "English (en-US)"
        else -> "Thai (th-TH)"
    }
    fun detectText(text: String, fallback: String): String = when {
        text.any { it in '\u0E00'..'\u0E7F' } -> "th"
        text.any { it in '\u3400'..'\u9FFF' } -> "zh"
        text.any { it in 'A'..'Z' || it in 'a'..'z' } -> "en"
        else -> fallback
    }
}
