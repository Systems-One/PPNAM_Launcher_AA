package com.mitas.ppnam.launcheraa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The supervisor PIN gate is the only thing between an operator and kiosk exit, so its
 * counter and lockout must live outside the dialog (UI audit launcher-01: Cancel + reopen
 * reset them) and never be spent by a mis-tap on an empty field (launcher-05).
 */
class PinGateTest {

    private var now = 1_000_000L
    private val store = FlakyStore()
    private val gate = PinGate(store, correctPin = PIN, clock = { now })

    /** In-memory store whose reads can be made to throw, like a corrupted preference. */
    private class FlakyStore : PinGate.Store {
        var readsFail = false
        private var failed = 0
        private var until = 0L
        override var failedAttempts: Int
            get() = if (readsFail) throw IllegalStateException("read failed") else failed
            set(value) { failed = value }
        override var lockedOutUntilMs: Long
            get() = if (readsFail) throw IllegalStateException("read failed") else until
            set(value) { until = value }
    }

    @Test
    fun `correct PIN unlocks and clears the failure count`() {
        store.failedAttempts = 3
        assertEquals(PinGate.Result.Unlocked, gate.submit(PIN))
        assertEquals(0, store.failedAttempts)
        assertEquals(0L, store.lockedOutUntilMs)
    }

    @Test
    fun `wrong PIN counts down from five attempts`() {
        assertEquals(PinGate.Result.Wrong(4), gate.submit("111111"))
        assertEquals(PinGate.Result.Wrong(3), gate.submit("111111"))
        assertEquals(PinGate.Result.Wrong(2), gate.submit("111111"))
        assertEquals(PinGate.Result.Wrong(1), gate.submit("111111"))
    }

    @Test
    fun `fifth wrong PIN locks out for thirty seconds and resets the count`() {
        repeat(4) { gate.submit("000000") }
        assertEquals(PinGate.Result.LockedOut(30), gate.submit("000000"))
        assertTrue(gate.isLockedOut())
        assertEquals(30L, gate.lockoutSecondsLeft())
        assertEquals(0, store.failedAttempts)
        assertEquals(now + PinGate.LOCKOUT_MS, store.lockedOutUntilMs)
    }

    /** launcher-05: an empty Unlock must not eat lockout budget. */
    @Test
    fun `blank PIN is refused without spending an attempt`() {
        assertEquals(PinGate.Result.Blank, gate.submit(""))
        assertEquals(PinGate.Result.Blank, gate.submit("   "))
        assertEquals(0, store.failedAttempts)
        assertEquals(PinGate.Result.Wrong(4), gate.submit("000000"))
    }

    @Test
    fun `correct PIN during lockout is refused and the countdown keeps running`() {
        repeat(5) { gate.submit("000000") }
        now += 3_000
        assertEquals(PinGate.Result.LockedOut(27), gate.submit(PIN))
        assertEquals(27L, gate.lockoutSecondsLeft())
    }

    /** launcher-01: dismissing the dialog creates a new gate over the same store. */
    @Test
    fun `lockout survives a new gate on the same store`() {
        repeat(5) { gate.submit("000000") }
        val reopened = PinGate(store, correctPin = PIN, clock = { now })
        assertTrue(reopened.isLockedOut())
        assertEquals(PinGate.Result.LockedOut(30), reopened.submit(PIN))
    }

    @Test
    fun `failure count survives a new gate on the same store`() {
        gate.submit("000000")
        gate.submit("000000")
        val reopened = PinGate(store, correctPin = PIN, clock = { now })
        assertEquals(PinGate.Result.Wrong(2), reopened.submit("000000"))
    }

    @Test
    fun `lockout expires after thirty seconds`() {
        repeat(5) { gate.submit("000000") }
        now += 30_000
        assertFalse(gate.isLockedOut())
        assertEquals(0L, gate.lockoutSecondsLeft())
        assertEquals(PinGate.Result.Wrong(4), gate.submit("000000"))
    }

    @Test
    fun `seconds left rounds up so the ticker never shows zero while locked`() {
        repeat(5) { gate.submit("000000") }
        now += 100
        assertEquals(30L, gate.lockoutSecondsLeft())
        now += 900
        assertEquals(29L, gate.lockoutSecondsLeft())
        now += 28_999
        assertEquals(1L, gate.lockoutSecondsLeft())
    }

    /**
     * Security review "fail-open-state-drift" + branch review: a deadline further away than
     * one lockout means the clock moved backwards (or the RTC reset). The gate neither opens
     * early (the plan's original "treat as expired") nor holds until the clock catches up
     * (could be years, with the clock fix behind this very PIN): it re-imposes one full
     * lockout from now and persists that deadline.
     */
    @Test
    fun `lockout further away than its own duration is rebased to a full lockout from now`() {
        store.lockedOutUntilMs = now + PinGate.LOCKOUT_MS + 60_000
        assertTrue(gate.isLockedOut())
        assertEquals(30L, gate.lockoutSecondsLeft())
        assertEquals(now + PinGate.LOCKOUT_MS, store.lockedOutUntilMs)
        assertEquals(PinGate.Result.LockedOut(30), gate.submit(PIN))
        now += PinGate.LOCKOUT_MS
        assertFalse(gate.isLockedOut())
        assertEquals(PinGate.Result.Unlocked, gate.submit(PIN))
    }

    @Test
    fun `clock jumping back mid-lockout never shortens the lockout`() {
        repeat(5) { gate.submit("000000") }
        now += 10_000
        assertEquals(20L, gate.lockoutSecondsLeft())
        now -= 3_600_000
        assertEquals(30L, gate.lockoutSecondsLeft())
        assertEquals(PinGate.Result.LockedOut(30), gate.submit(PIN))
    }

    /** Branch review: a reopened dialog must show the persisted count, not a clean slate. */
    @Test
    fun `attempts left reflects the persisted count for a reopened dialog`() {
        assertEquals(5, gate.attemptsLeft())
        gate.submit("000000")
        gate.submit("000000")
        assertEquals(3, PinGate(store, correctPin = PIN, clock = { now }).attemptsLeft())
        gate.submit(PIN)
        assertEquals(5, gate.attemptsLeft())
    }

    /** A store that cannot be read must not hand back a fresh, zeroed attempt budget. */
    @Test
    fun `store read failure keeps the last known attempt count`() {
        gate.submit("000000")
        gate.submit("000000")
        store.readsFail = true
        assertEquals(PinGate.Result.Wrong(2), gate.submit("000000"))
        assertEquals(PinGate.Result.Wrong(1), gate.submit("000000"))
        assertEquals(PinGate.Result.LockedOut(30), gate.submit("000000"))
    }

    @Test
    fun `store read failure keeps the last known lockout`() {
        repeat(5) { gate.submit("000000") }
        store.readsFail = true
        now += 5_000
        assertTrue(gate.isLockedOut())
        assertEquals(25L, gate.lockoutSecondsLeft())
        assertEquals(PinGate.Result.LockedOut(25), gate.submit(PIN))
    }

    /** No value ever read: fail closed (one attempt, then lockout) but never lock out the PIN itself. */
    @Test
    fun `store read failure before any read fails closed`() {
        store.readsFail = true
        assertEquals(PinGate.Result.LockedOut(30), gate.submit("000000"))
        now += PinGate.LOCKOUT_MS
        assertEquals(PinGate.Result.Unlocked, gate.submit(PIN))
    }

    @Test
    fun `store write failure keeps the in-memory count authoritative`() {
        val store = object : PinGate.Store {
            override var failedAttempts: Int
                get() = 0
                set(_) { throw IllegalStateException("write failed") }
            override var lockedOutUntilMs: Long
                get() = 0L
                set(_) { throw IllegalStateException("write failed") }
        }
        val gate = PinGate(store, correctPin = PIN, clock = { now })
        assertEquals(PinGate.Result.Wrong(4), gate.submit("000000"))
        assertEquals(PinGate.Result.Wrong(3), gate.submit("000000"))
    }

    /** Review Focus 2 / launcher-03: the field only ever holds up to six digits. */
    @Test
    fun `sanitize keeps digits only and caps at six`() {
        assertEquals("", PinGate.sanitize("abc"))
        assertEquals("079545", PinGate.sanitize("0795456789"))
        assertEquals("123", PinGate.sanitize("1 2-3\n"))
        assertEquals("", PinGate.sanitize(""))
    }

    private companion object {
        const val PIN = "079545"
    }
}
