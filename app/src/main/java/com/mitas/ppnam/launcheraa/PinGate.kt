package com.mitas.ppnam.launcheraa

/**
 * Supervisor PIN gate: five attempts, then a 30 s lockout — the same rule as every station
 * app's Settings screen (Station 1 `SettingsActivity.submitPin`).
 *
 * The counter and the lockout deadline live in [Store], not in the dialog that shows them,
 * so dismissing the dialog or restarting the process cannot reset them (UI audit
 * launcher-01). Pure Kotlin with an injectable clock so the rule is tested on the JVM.
 *
 * Fails closed: a store that cannot be read keeps the last value this gate saw (or, before
 * any read succeeded, a budget of one attempt), a store that cannot be written keeps the
 * in-memory value authoritative, and a deadline that sits further away than one lockout
 * (the clock moved backwards) stays locked until the clock catches up.
 */
class PinGate(
    private val store: Store,
    private val correctPin: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    interface Store {
        var failedAttempts: Int
        var lockedOutUntilMs: Long
    }

    /** In-memory store for tests and previews. */
    class MemoryStore : Store {
        override var failedAttempts: Int = 0
        override var lockedOutUntilMs: Long = 0L
    }

    sealed class Result {
        object Unlocked : Result()
        /** Nothing typed: refused without spending an attempt (launcher-05). */
        object Blank : Result()
        data class Wrong(val attemptsLeft: Int) : Result()
        data class LockedOut(val secondsLeft: Long) : Result()
    }

    // Last values successfully exchanged with the store. Until the store has answered once
    // the budget is a single attempt, so a broken store cannot hand out five fresh tries;
    // the correct PIN still unlocks, so a broken store never bricks the device either.
    private var knownFailed = MAX_ATTEMPTS - 1
    private var knownUntil = 0L
    private var storeTrusted = true

    private var failedAttempts: Int
        get() {
            if (storeTrusted) {
                try { knownFailed = store.failedAttempts } catch (e: RuntimeException) { storeTrusted = false }
            }
            return knownFailed
        }
        set(value) {
            knownFailed = value
            try { store.failedAttempts = value } catch (e: RuntimeException) { storeTrusted = false }
        }

    var lockedOutUntilMs: Long
        get() {
            if (storeTrusted) {
                try { knownUntil = store.lockedOutUntilMs } catch (e: RuntimeException) { storeTrusted = false }
            }
            return knownUntil
        }
        private set(value) {
            knownUntil = value
            try { store.lockedOutUntilMs = value } catch (e: RuntimeException) { storeTrusted = false }
        }

    /** Whole seconds until the lockout ends, rounded up; 0 when not locked out. */
    fun lockoutSecondsLeft(): Long {
        val now = clock()
        val until = lockedOutUntilMs
        if (now >= until) return 0
        return (until - now + 999) / 1_000
    }

    fun isLockedOut(): Boolean = lockoutSecondsLeft() > 0

    fun submit(pin: String): Result {
        val secondsLeft = lockoutSecondsLeft()
        if (secondsLeft > 0) return Result.LockedOut(secondsLeft)
        if (pin.isBlank()) return Result.Blank
        if (pin == correctPin) {
            failedAttempts = 0
            lockedOutUntilMs = 0L
            return Result.Unlocked
        }
        val failed = failedAttempts + 1
        return if (failed >= MAX_ATTEMPTS) {
            failedAttempts = 0
            lockedOutUntilMs = clock() + LOCKOUT_MS
            Result.LockedOut(LOCKOUT_MS / 1_000)
        } else {
            failedAttempts = failed
            Result.Wrong(MAX_ATTEMPTS - failed)
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
        const val PIN_LENGTH = 6

        /** The only filter the PIN field applies: digits only, at most [PIN_LENGTH]. */
        fun sanitize(input: String): String = input.filter(Char::isDigit).take(PIN_LENGTH)
    }
}
