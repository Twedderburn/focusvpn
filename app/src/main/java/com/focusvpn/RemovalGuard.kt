package com.focusvpn

import android.content.Context

/**
 * Remembers whether "Stop FocusVPN being removed" was ever on, so that if someone switches it off in Settings
 * the app can alert and ask again. Phones where it was never on are left alone (Tyler's decision).
 */
object RemovalGuard {
    private const val PREFS = "removal"
    private const val WAS_ON = "was_on"

    /** Setup asks once more after a Cancel, then lets the user carry on without it. */
    fun canSkipInSetup(cancels: Int): Boolean = cancels >= 2

    /** Alert and ask again only after setup, only if protection was on once, and only while it's off now. */
    fun needsAlert(setupDone: Boolean, wasOn: Boolean, blocked: Boolean): Boolean = setupDone && wasOn && !blocked

    fun wasOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(WAS_ON, false)

    /** Records that protection is on, if it is. Safe to call often. */
    fun sync(context: Context) {
        if (DnsFilter.isRemovalBlocked(context) && !wasOn(context)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(WAS_ON, true).apply()
        }
    }

    fun isOffAgain(context: Context): Boolean =
        needsAlert(com.focusvpn.ui.SetupStore.isDone(context), wasOn(context), DnsFilter.isRemovalBlocked(context))
}
