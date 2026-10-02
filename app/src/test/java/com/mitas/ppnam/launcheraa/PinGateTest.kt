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
    private val store = PinGate.MemoryStore()
    private val gate = PinGate(store, clock = { now })

    @Test
    fun `correct PIN unlocks and clears the failure count`() {
        store.failedAttempts = 3
        assertEquals(PinGate.Result.Unlocked, gate.submit("079545"))
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
        assertEquals(PinGate.Result.LockedOut(27), gate.submit("079545"))
        assertEquals(27L, gate.lockoutSecondsLeft())
    }

    /** launcher-01: dismissing the dialog creates a new gate over the same store. */
    @Test
    fun `lockout survives a new gate on the same store`() {
        repeat(5) { gate.submit("000000") }
        val reopened = PinGate(store, clock = { now })
        assertTrue(reopened.isLockedOut())
        assertEquals(PinGate.Result.LockedOut(30), reopened.submit("079545"))
    }

    @Test
    fun `failure count survives a new gate on the same store`() {
        gate.submit("000000")
        gate.submit("000000")
        val reopened = PinGate(store, clock = { now })
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

    /** Review Focus 1: a clock set backwards must not extend the lockout past 30 s. */
    @Test
    fun `lockout further away than its own duration is treated as expired`() {
        store.lockedOutUntilMs = now + PinGate.LOCKOUT_MS + 1
        assertFalse(gate.isLockedOut())
        assertEquals(0L, gate.lockoutSecondsLeft())
        assertEquals(PinGate.Result.Wrong(4), gate.submit("000000"))
    }

    /** Review Focus 2 / launcher-03: the field only ever holds up to six digits. */
    @Test
    fun `sanitize keeps digits only and caps at six`() {
        assertEquals("", PinGate.sanitize("abc"))
        assertEquals("079545", PinGate.sanitize("0795456789"))
        assertEquals("123", PinGate.sanitize("1 2-3\n"))
        assertEquals("", PinGate.sanitize(""))
    }
}
