package com.fta.senior.privileged

/** Main-thread state: late results cannot revive a timed-out or disconnected session. */
internal class ConnectionAttempt {
    private var generation = 0L
    var pending = false
        private set

    fun start(): Long {
        check(!pending) { "Connection already pending" }
        pending = true
        return ++generation
    }

    fun finish(attempt: Long): Boolean {
        if (!pending || attempt != generation) return false
        pending = false
        return true
    }

    fun invalidate() { generation++; pending = false }
}
