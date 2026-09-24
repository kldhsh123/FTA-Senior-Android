package com.fta.senior

import com.fta.senior.privileged.ConnectionAttempt
import org.junit.Assert.*
import org.junit.Test

class ConnectionAttemptTest {
    @Test fun `timeout rejects late success`() {
        val session = ConnectionAttempt()
        val id = session.start()
        session.invalidate()
        assertFalse(session.finish(id))
        assertFalse(session.pending)
    }

    @Test fun `old result does not complete newer connection`() {
        val session = ConnectionAttempt()
        val old = session.start()
        session.invalidate()
        val current = session.start()
        assertFalse(session.finish(old))
        assertTrue(session.pending)
        assertTrue(session.finish(current))
        assertFalse(session.pending)
        assertFalse(session.finish(current))
    }

    @Test(expected = IllegalStateException::class)
    fun `repeated refresh cannot start concurrent attempt`() {
        val session = ConnectionAttempt()
        session.start()
        session.start()
    }
}
