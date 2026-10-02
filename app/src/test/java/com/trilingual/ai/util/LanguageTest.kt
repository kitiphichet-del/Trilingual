package com.trilingual.ai.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageTest {
    @Test fun scripts() {
        assertEquals("th", Language.detectText("ยินดีต้อนรับ", "en"))
        assertEquals("zh", Language.detectText("欢迎大家", "th"))
        assertEquals("en", Language.detectText("Welcome everyone", "zh"))
    }
    @Test fun fallback() {
        assertEquals("zh", Language.detectText("!", "zh"))
        assertEquals("zh-CN", Language.tag("zh"))
    }
}
