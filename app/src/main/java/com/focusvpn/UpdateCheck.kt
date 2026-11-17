package com.focusvpn

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Once a day, asks GitHub which version is the latest release (PRD §4.6). It only ever tells the user; it never
 * downloads or installs anything. The request carries nothing about the user or the phone.
 */
object UpdateCheck {
    const val LATEST_API = "https://api.github.com/repos/Twedderburn/focusvpn/releases/latest"
    const val DOWNLOAD_PAGE = "https://twedderburn.github.io/focusvpn/"
    const val MESSAGE = "A new version with more blocked sites is ready"
    private const val PREFS = "update"
    private const val CHECKED_AT = "checkedAt"
    private const val LATEST = "latest"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    @Volatile private var busy = false

    /** The release's version from GitHub's JSON, e.g. "v1.2" gives "1.2". Null if there's no tag in it. */
    fun latestVersion(json: String): String? =
        Regex("\"tag_name\"\\s*:\\s*\"v?([0-9][0-9.]*)\"").find(json)?.groupValues?.get(1)

    /** True when [latest] is a higher version than [current], comparing the dotted numbers one by one. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = numbers(latest)
        val b = numbers(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(v: String) = v.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    fun isDue(now: Long, lastChecked: Long): Boolean = lastChecked == 0L || now - lastChecked >= DAY_MS || now < lastChecked

    /** Whether to show the "new version" banner, from what the last check found. */
    fun updateReady(context: Context): Boolean {
        val latest = prefs(context).getString(LATEST, null) ?: return false
        return isNewer(latest, currentVersion(context))
    }

    /** Checks in the background if a day has passed since the last try. Safe to call often. */
    fun maybeCheck(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (busy || !isDue(now, prefs(app).getLong(CHECKED_AT, 0L))) return
        busy = true
        thread(name = "focus-update", isDaemon = true) {
            try {
                val c = URL(LATEST_API).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 10_000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "FocusVPN")
                val code = c.responseCode
                // 404 means "no release yet": that is an answer too, so wait until tomorrow to ask again.
                if (code == 200 || code == 404) {
                    val e = prefs(app).edit().putLong(CHECKED_AT, now)
                    if (code == 200) latestVersion(c.inputStream.bufferedReader().use { it.readText() })?.let { e.putString(LATEST, it) }
                    e.apply()
                }
                c.disconnect()
            } catch (_: Exception) {
                // Offline or GitHub unreachable; try again next time.
            } finally {
                busy = false
            }
        }
    }

    private fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (_: Exception) {
        "0"
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
