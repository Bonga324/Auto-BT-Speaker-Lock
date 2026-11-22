package com.example.autobt

import android.content.Context

/** Demo local access controls. Hardcoded passwords are visible to anyone inspecting the APK. */
object LockManager {
    const val APP_PIN = "7391"
    const val ADMIN_PASSWORD = "SpeakerLock2026!"
    const val RESET_PASSWORD = "UnlockMe7391"
    private const val PREFS = "speaker_lock_prefs"
    private const val LOCK_MODE = "lock_mode"
    private const val PAUSED = "manual_pause"
    private const val FAILED = "failed_attempts"
    private const val UNTIL = "locked_until"
    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isLocked(context: Context) = prefs(context).getBoolean(LOCK_MODE, false)
    fun setLocked(context: Context, value: Boolean) = prefs(context).edit().putBoolean(LOCK_MODE, value).apply()
    fun isPaused(context: Context) = prefs(context).getBoolean(PAUSED, false)
    fun setPaused(context: Context, value: Boolean) = prefs(context).edit().putBoolean(PAUSED, value).apply()
    fun remainingSeconds(context: Context): Long =
        ((prefs(context).getLong(UNTIL, 0L) - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(0L)
    fun verifyPin(context: Context, entered: String): Boolean {
        if (remainingSeconds(context) > 0) return false
        val p = prefs(context)
        if (entered == APP_PIN) {
            p.edit().putInt(FAILED, 0).remove(UNTIL).apply()
            return true
        }
        val count = p.getInt(FAILED, 0) + 1
        if (count >= 3) p.edit().putInt(FAILED, 0).putLong(UNTIL, System.currentTimeMillis() + 60_000L).apply()
        else p.edit().putInt(FAILED, count).apply()
        return false
    }
    fun reset(context: Context) { prefs(context).edit().clear().apply() }
}
