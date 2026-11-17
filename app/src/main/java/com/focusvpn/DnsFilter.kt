package com.focusvpn

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.SystemClock
import android.os.UserManager
import com.focusvpn.blocklist.Blocklist
import com.focusvpn.blocklist.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * The web filter: a local VPN ([DnsFilterService]) that carries only DNS. Lookups for blocked domains get
 * "no such domain"; everything else is passed to the network's normal DNS server. No other traffic goes through it.
 */
object DnsFilter {
    /**
     * Always blocked, so nothing can route around the filter. Each name also blocks every subdomain.
     * (Reddit and X from Focus Home move to the opt-in Social category.)
     */
    val BYPASS_DOMAINS = setOf(
        // Encrypted DNS providers. Browsers set to use them would skip the filter; blocked, their lookups just fail.
        "cloudflare-dns.com", "one.one.one.one", "dns.google", "dns.quad9.net", "doh.opendns.com", "dns.nextdns.io",
        "doh.cleanbrowsing.org", "dns.adguard.com", "dns.adguard-dns.com", "doh.dns.sb", "dns.mullvad.net",
        // Firefox's canary: "no such domain" here tells it not to switch on its own encrypted DNS.
        "use-application-dns.net",
    )

    /**
     * Adult sites. These are built in so they're blocked even if the bundled list ([BLOCKLIST_ASSET]) can't be read.
     * "xxx", "porn", "sex" and "adult" are whole top-level domains that exist only for adult sites.
     */
    val ADULT_DOMAINS = setOf(
        "xxx", "porn", "sex", "adult",
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "xhamster.desi", "xhamsterlive.com",
        "redtube.com", "youporn.com", "tube8.com", "spankbang.com", "eporner.com", "txxx.com", "hqporner.com",
        "porntrex.com", "motherless.com", "youjizz.com", "tnaflix.com", "drtuber.com", "beeg.com", "porn.com",
        "pornone.com", "4tube.com", "fuq.com", "thumbzilla.com", "ixxx.com", "porndig.com", "porn300.com",
        "onlyfans.com", "fansly.com", "manyvids.com", "clips4sale.com", "iwantclips.com", "loyalfans.com",
        "chaturbate.com", "stripchat.com", "bongacams.com", "livejasmin.com", "cam4.com", "camsoda.com",
        "myfreecams.com", "flirt4free.com", "imlive.com", "jerkmate.com",
        "brazzers.com", "bangbros.com", "realitykings.com", "naughtyamerica.com", "mofos.com", "digitalplayground.com",
        "rule34.xxx", "rule34.paheal.net", "e-hentai.org", "exhentai.org", "nhentai.net", "hanime.tv", "hentaihaven.xxx",
        "gelbooru.com", "danbooru.donmai.us", "f95zone.to",
        "literotica.com", "imagefap.com", "erome.com", "thothub.to", "coomer.su", "kemono.su",
    )

    /**
     * The bundled blocklist (PRD §5): ~640k adult domains as sorted 56-bit hashes with category bits, built from
     * blocklists/<name>-domains.txt at build time. Memory-mapped and copied into one LongArray (~5 MB) on first use.
     */
    const val BLOCKLIST_ASSET = Blocklist.FILE_NAME

    @Volatile private var table: LongArray = LongArray(0)

    /** What the user has switched on. Adult is always on; [apply] only ever adds to this (PRD §3). */
    @Volatile var strictness: Strictness = Strictness()
        private set
    @Volatile private var enabledCategories: Int = Category.ADULT
    @Volatile private var extraBlocked: Map<String, Int> = emptyMap()

    /** Switches on whatever [s] has on. Merged with what's already applied, so nothing can be switched off. */
    @Synchronized
    fun apply(s: Strictness) {
        val merged = strictness.union(s)
        enabledCategories = Category.ADULT or (if (merged.gambling) Category.GAMBLING else 0)
        extraBlocked = merged.social.flatMap { it.domains }.associateWith { Category.SOCIAL } +
            merged.customSites.associateWith { Category.CUSTOM }
        strictness = merged
    }

    /** For tests: start again from nothing switched on. */
    @Synchronized
    internal fun resetStrictnessForTest() {
        strictness = Strictness()
        enabledCategories = Category.ADULT
        extraBlocked = emptyMap()
    }

    /** Reads the bundled blocklist once. Called by the filter before it answers its first lookup. */
    fun loadBlocklist(context: Context) {
        if (table.isNotEmpty()) return
        table = try {
            // Stored uncompressed (noCompress "bin"), so it can be mapped straight from the APK.
            context.assets.openFd(BLOCKLIST_ASSET).use { fd ->
                fd.createInputStream().use { input ->
                    Blocklist.decode(input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length))
                }
            }
        } catch (_: Exception) {
            try {
                context.assets.open(BLOCKLIST_ASSET).use { Blocklist.decode(ByteBuffer.wrap(it.readBytes())) }
            } catch (_: Exception) {
                LongArray(0) // ADULT_DOMAINS and BYPASS_DOMAINS still apply.
            }
        }
    }

    /** For tests: use this table in place of the bundled one. */
    internal fun setBlocklistForTest(t: LongArray) {
        table = t
    }

    /**
     * What blocks this name, as [Category] bits ([Category.BYPASS] for the encrypted-DNS names), or 0 if nothing
     * does. Checks the name, then each parent, so every subdomain of a listed name is blocked too.
     */
    fun blockedBy(name: String): Int {
        var n = Blocklist.normalize(name)
        val t = table
        val enabled = enabledCategories
        val extra = extraBlocked
        while (n.isNotEmpty()) {
            if (n in BYPASS_DOMAINS) return Category.BYPASS
            if (n in ADULT_DOMAINS) return Category.ADULT
            extra[n]?.let { return it }
            val bits = Blocklist.categoriesOf(t, n) and enabled
            if (bits != 0) return bits
            n = n.substringAfter('.', "")
        }
        return 0
    }

    fun isBlocked(name: String): Boolean = blockedBy(name) != 0

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    internal fun setRunning(on: Boolean) {
        _running.value = on
    }

    /** False when Android needs the user to OK the VPN again (first run, or after it was disconnected in Settings). */
    fun hasConsent(context: Context): Boolean = try {
        VpnService.prepare(context) == null
    } catch (_: Exception) {
        false
    }

    /** Starts the filter if Android allows it. Safe to call often. */
    fun start(context: Context) {
        if (_running.value || !hasConsent(context)) return
        try {
            context.startService(Intent(context, DnsFilterService::class.java))
        } catch (_: Exception) {
            // Android refused a start from the background; the app retries when it next opens.
        }
    }

    /**
     * Private DNS set to a named provider ("strict" mode) sends lookups straight to that provider over TLS,
     * around the filter, so it counts as the filter being off.
     */
    fun privateDnsBypass(context: Context): Boolean = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        cm.allNetworks.any { cm.getLinkProperties(it)?.privateDnsServerName != null }
    } catch (_: Exception) {
        false
    }

    private var checkedAt = 0L
    private var bypassCached = false

    /** Whether lookups really are being filtered right now. Re-checks Private DNS at most every 5 seconds. */
    fun isProtecting(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - checkedAt > 5_000 || checkedAt == 0L) {
            checkedAt = now
            bypassCached = privateDnsBypass(context)
        }
        return _running.value && !bypassCached
    }

    fun isDeviceOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)?.isDeviceOwnerApp(context.packageName) == true

    /**
     * Only when FocusVPN has been made device owner (an adb step, see the README): pins the filter as Android's
     * always-on VPN and takes VPN and Private DNS settings away from the user, so the filter can't be switched off.
     * "Block connections without VPN" stays off: this VPN carries only DNS, so lockdown would cut all other traffic.
     */
    fun lockIn(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, FocusAdminReceiver::class.java)
        runCatching { dpm.setAlwaysOnVpnPackage(admin, context.packageName, false) }
        runCatching { dpm.setGlobalPrivateDnsModeOpportunistic(admin) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS) }
        runCatching { dpm.setUninstallBlocked(admin, context.packageName, true) }
    }

    /** DNS servers of the real (non-VPN) networks, then public fallbacks. */
    internal fun upstreams(context: Context): List<java.net.InetAddress> {
        val found = try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            cm.allNetworks
                .filter { n ->
                    cm.getNetworkCapabilities(n)?.let {
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    } == true
                }
                .flatMap { cm.getLinkProperties(it)?.dnsServers.orEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
        return (found + FALLBACK).distinct()
    }

    private val FALLBACK = listOf("1.1.1.1", "8.8.8.8").map { java.net.InetAddress.getByName(it) }
}

/** Starts the filter after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        DnsFilter.lockIn(context)
        DnsFilter.start(context)
    }
}

/** Does nothing unless FocusVPN is made device owner with adb; then it locks the filter in. */
class FocusAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        DnsFilter.lockIn(context)
        DnsFilter.start(context)
    }
}
