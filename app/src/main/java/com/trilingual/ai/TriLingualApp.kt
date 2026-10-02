package com.trilingual.ai

import android.app.Application
import com.trilingual.ai.data.LocalStore
import com.trilingual.ai.data.UserSettings
import com.trilingual.ai.speech.LiveSession
import com.trilingual.ai.translation.DeviceTranslator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TriLingualApp : Application() {
    lateinit var store: LocalStore
        private set
    lateinit var settings: UserSettings
        private set
    val live = LiveSession()
    // Translation jobs outlive the microphone service, so Stop does not discard results.
    val translationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val deviceTranslator = DeviceTranslator()

    override fun onCreate() {
        super.onCreate()
        store = LocalStore(this)
        settings = UserSettings(this)
        store.refreshSessions()
    }
}
