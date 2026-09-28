package com.lafarge.wvc

/** Pure state machine: failed, stale and duplicate scans must never mean "outside". */
class PresenceTracker {
    var indoor: Boolean? = null
        private set
    private var lastScanUs = -1L
    private var misses = 0

    fun observe(found: Boolean, scanUs: Long): Boolean? {
        if (scanUs <= lastScanUs) return indoor
        lastScanUs = scanUs
        if (found) {
            misses = 0
            indoor = true
        } else if (++misses >= 2) {
            indoor = false
        }
        return indoor
    }
}
