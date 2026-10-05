package com.trilingual.ai.speech

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveView(
    val meetingId: Long = 0L, val running: Boolean = false, val paused: Boolean = false,
    val mode: String = "conversation", val language: String = "th", val speaker: Int = 1,
    val autoSpeaker: Boolean = true,
    val partial: String = "", val status: String = "พร้อมเริ่ม", val loudness: Float = 0f
)
class LiveSession {
    private val _view = MutableStateFlow(LiveView())
    val view = _view.asStateFlow()
    fun update(change: (LiveView) -> LiveView) { _view.value = change(_view.value) }
}
