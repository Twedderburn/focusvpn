package com.focusvpn

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.focusvpn.ui.SetupStore

/** The "Your filter is off" notification (PRD §4.4). Shown while the filter can't come back by itself. */
object FilterAlert {
    private const val CHANNEL = "filter_off"
    private const val ID = 1
    const val TEXT = "Your filter is off - tap to turn it back on"

    /**
     * Alert only when the user has to do something: Android wants its OK again (another app took over, or it was
     * disconnected in Settings), or a phone setting sends lookups around the filter. Never before setup is
     * finished, and not while the filter is merely starting, since that fixes itself.
     */
    fun needsAlert(setupDone: Boolean, hasConsent: Boolean, bypass: Boolean): Boolean =
        setupDone && (!hasConsent || bypass)

    /** Shows or clears the notification to match how things are right now. Safe to call often. */
    fun update(context: Context, removalJustOff: Boolean = false) {
        val app = context.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        updateRemoval(app, nm, removalJustOff)
        val show = try {
            needsAlert(SetupStore.isDone(app), DnsFilter.hasConsent(app), DnsFilter.privateDnsBypass(app))
        } catch (_: Exception) {
            false
        }
        if (!show) {
            nm.cancel(ID)
            return
        }
        if (!canNotify(app)) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Filter is off", NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            app, 0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Not protected")
            .setContentText(TEXT)
            .setStyle(Notification.BigTextStyle().bigText(TEXT))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .build()
        runCatching { nm.notify(ID, n) }
    }

    /** "Removal protection is off": shown when "Stop FocusVPN being removed" was on and got switched off. */
    private fun updateRemoval(app: Context, nm: NotificationManager, justOff: Boolean) {
        // During onDisabled Android still reports the admin as active, so the caller says it is off.
        val show = runCatching {
            if (justOff) RemovalGuard.needsAlert(SetupStore.isDone(app), RemovalGuard.wasOn(app), blocked = false)
            else RemovalGuard.isOffAgain(app)
        }.getOrDefault(false)
        if (!show) {
            nm.cancel(REMOVAL_ID)
            return
        }
        if (!canNotify(app)) return
        nm.createNotificationChannel(
            NotificationChannel(REMOVAL_CHANNEL, "Removal protection is off", NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            app, 1,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(app, REMOVAL_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("FocusVPN can be removed")
            .setContentText(REMOVAL_TEXT)
            .setStyle(Notification.BigTextStyle().bigText(REMOVAL_TEXT))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .build()
        runCatching { nm.notify(REMOVAL_ID, n) }
    }

    private const val REMOVAL_CHANNEL = "removal_off"
    private const val REMOVAL_ID = 2
    const val REMOVAL_TEXT = "Removal protection is off - tap to turn it back on"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
