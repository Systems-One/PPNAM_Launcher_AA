package com.mitas.ppnam.launcheraa

/**
 * Supervisor PIN gate: five attempts, then a 30 s lockout — the same rule as every station
 * app's Settings screen (Station 1 `SettingsActivity.submitPin`).
 *
 * The counter and the lockout deadline live in [Store], not in the dialog that shows them,
 * so dismissing the dialog or restarting the process cannot reset them (UI audit
 * launcher-01). Pure Kotlin with an injectable clock so the rule is tested on the JVM.
 */
class PinGate(
    private val store: Store,
    private val clock: () -> Long = System::currentTimeMillis,
    private val correctPin: String = CORRECT_PIN,
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

    val lockedOutUntilMs: Long get() = store.lockedOutUntilMs

    /** Whole seconds until the lockout ends, rounded up; 0 when not locked out. */
    fun lockoutSecondsLeft(): Long {
        val now = clock()
        val until = store.lockedOutUntilMs
        // A deadline further away than one lockout means the clock moved backwards:
        // treat it as expired rather than locking the supervisor out indefinitely.
        if (now >= until || until - now > LOCKOUT_MS) return 0
        return (until - now + 999) / 1_000
    }

    fun isLockedOut(): Boolean = lockoutSecondsLeft() > 0

    fun submit(pin: String): Result {
        val secondsLeft = lockoutSecondsLeft()
        if (secondsLeft > 0) return Result.LockedOut(secondsLeft)
        if (pin.isBlank()) return Result.Blank
        if (pin == correctPin) {
            store.failedAttempts = 0
            store.lockedOutUntilMs = 0L
            return Result.Unlocked
        }
        val failed = store.failedAttempts + 1
        return if (failed >= MAX_ATTEMPTS) {
            store.failedAttempts = 0
            store.lockedOutUntilMs = clock() + LOCKOUT_MS
            Result.LockedOut(LOCKOUT_MS / 1_000)
        } else {
            store.failedAttempts = failed
            Result.Wrong(MAX_ATTEMPTS - failed)
        }
    }

    companion object {
        // Ported from the station apps' settings lock so every supervisor PIN matches.
        const val CORRECT_PIN = "079545"
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
        const val PIN_LENGTH = 6

        /** The only filter the PIN field applies: digits only, at most [PIN_LENGTH]. */
        fun sanitize(input: String): String = input.filter(Char::isDigit).take(PIN_LENGTH)
    }
}
