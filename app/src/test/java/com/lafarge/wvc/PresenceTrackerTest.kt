package com.lafarge.wvc

import org.junit.Assert.*
import org.junit.Test

class PresenceTrackerTest {
    @Test fun firstMissDoesNotApplyOutdoorVolume() {
        val tracker = PresenceTracker()
        assertNull(tracker.observe(false, 1))
        assertEquals(false, tracker.observe(false, 2))
    }
    @Test fun duplicateAndOutOfOrderResultsDoNotCountAsNewMisses() {
        val tracker = PresenceTracker()
        assertEquals(true, tracker.observe(true, 10))
        assertEquals(true, tracker.observe(false, 11))
        assertEquals(true, tracker.observe(false, 11))
        assertEquals(true, tracker.observe(false, 9))
        assertEquals(false, tracker.observe(false, 12))
    }
    @Test fun DetectionResetsConsecutiveMisses() {
        val tracker = PresenceTracker()
        tracker.observe(false, 1)
        assertEquals(true, tracker.observe(true, 2))
        assertEquals(true, tracker.observe(false, 3))
        assertEquals(false, tracker.observe(false, 4))
        assertEquals(true, tracker.observe(true, 5))
    }
}
