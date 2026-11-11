package com.dotos.launcher

import android.content.Context

/**
 * Crash resilience: a launcher that crash-loops makes the phone unusable. If the process dies twice
 * within a minute of starting, the next start enters safe mode (built-in widgets become placeholders),
 * and the home screen still works. Safe mode clears itself after a clean minute.
 */
internal object CrashGuard {
    private const val PREFS = "crash_guard"

    fun install(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val recent = prefs.getInt("recent_crashes", 0)
        val safe = recent >= 2
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val started = prefs.getLong("started_at", 0L)
            val quick = System.currentTimeMillis() - started < 60_000L
            prefs.edit().putInt("recent_crashes", if (quick) recent + 1 else 1).commit()
            previous?.uncaughtException(thread, error)
        }
        prefs.edit().putLong("started_at", now).apply()
        return safe
    }

    /** Called after the home screen has been stable for a while. */
    fun markHealthy(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("recent_crashes", 0).apply()
    }
}
