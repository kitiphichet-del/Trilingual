package com.trilingual.ai.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSpeakerTrackerTest {
    @Test fun stableLabelsAcrossLanguageTurns() {
        val tracker = AutoSpeakerTracker()
        assertEquals(1, tracker.assign("th"))
        assertEquals(2, tracker.assign("zh"))
        assertEquals(1, tracker.assign("th"))
        assertEquals(3, tracker.assign("en"))
        assertEquals(2, tracker.assign("zh"))
    }

    @Test fun resetStartsAtOne() {
        val tracker = AutoSpeakerTracker()
        tracker.assign("th")
        tracker.assign("zh")
        tracker.reset()
        assertEquals(1, tracker.assign("zh"))
    }
}
